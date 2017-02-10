package com.tinymission.rss;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import info.guardianproject.netcipher.client.StrongBuilder;
import info.guardianproject.netcipher.client.StrongConnectionBuilder;
import info.guardianproject.netcipher.client.StrongHttpClientBuilder;
import info.guardianproject.securereader.SocialReader;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;

/**
 * Reads an RSS feed and creates and RssFeed object.
 */
public class CommentReader
{

	public final static String LOGTAG = "TinyRSS Comment Reader";
	public final static boolean LOGGING = false;

	private Feed feed = new Feed(); // for the parsing
	
	private Item item;

	private SocialReader socialReader;

	InputStream is;

	/**
	 * The allowed tags to parse content from (everything else gets lumped into
	 * its parent content, which allows for embedding html content.
	 * 
	 */
	public final static String[] CONTENT_TAGS = { "title", "link", "language", "pubDate", "lastBuildDate", "docs", "generator", "managingEditor", "webMaster",
			"guid", "author", "category", "content:encoded", "description", "url", "extrss:id", "dc:creator" };

	/**
	 * The tags that should be parsed into separate entities, not just
	 * properties of existing entities.
	 * 
	 */
	public final static String[] ENTITY_TAGS = { "channel", "item", "media:content", "media:thumbnail", "enclosure", "image" };

	/**
	 * @return whether tag is a valid content tag
	 */
	public static boolean isContentTag(String tag)
	{
		for (String t : CONTENT_TAGS)
		{
			if (t.equalsIgnoreCase(tag))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return whether tag is a valid entity tag
	 */
	public static boolean isEntityTag(String tag)
	{
		for (String t : ENTITY_TAGS)
		{
			if (t.equals(tag))
			{
				return true;
			}
		}
		return false;
	}

	// In this case, we preserve the feed object?
	public CommentReader(SocialReader _socialReader, Item _item)
	{
		socialReader = _socialReader;
		_item.setStatus(Item.STATUS_SYNC_IN_PROGRESS);
		item = _item;
	}


	public void stop() {
		if (is != null) {
			try {
				is.close();
			} catch (IOException ioe) {
				if (LOGGING) ioe.printStackTrace();
			}
		}
	}

	/**
	 * Actually grabs the feed from the URL and parses it into java objects.
	 * 
	 * @return An array of Items with the comments
	 */
	public Feed fetchCommentFeed()
	{		
		try
		{
			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setNamespaceAware(true);
			SAXParser sp = spf.newSAXParser();

			final XMLReader xr = sp.getXMLReader();
			
			xr.setErrorHandler(new ErrorHandler() { 
				public void error(SAXParseException exception) throws SAXException {
					if (LOGGING) { 
						Log.v(LOGTAG, "ErrorHandler: SAXParseException error: " + exception.getMessage()); 
						exception.printStackTrace();
					}
				}
			  
				public void fatalError(SAXParseException exception) throws SAXException { 
					if (LOGGING) {
						Log.v(LOGTAG, "ErrorHandler: SAXParseException fatalError: " + exception.getMessage()); 
						exception.printStackTrace();
					}
				}
			  
				public void warning(SAXParseException exception) throws SAXException {
					if (LOGGING) {
						Log.v(LOGTAG, "ErrorHandler: SAXParseException warning: " + exception.getMessage());
						exception.printStackTrace(); 
					}
				} 
			});
			

			Handler handler = new Handler();
			xr.setContentHandler(handler);

			StrongHttpClientBuilder builder = StrongHttpClientBuilder.forMaxSecurity(socialReader.applicationContext);
			if (socialReader.useProxy())
			{
				builder.withSocksProxy();
				//				    httpClient.useProxy(true, socialReader.getProxyType(), socialReader.getProxyHost(), socialReader.getProxyPort());

			}

			builder.build(new StrongBuilder.Callback<HttpClient>() {
				@Override
				public void onConnected(HttpClient httpClient) {
                    if (item.getCommentsUrl() != null && !(item.getCommentsUrl().isEmpty()))
                    {
                        try {
                            HttpGet httpGet = new HttpGet(item.getCommentsUrl());
                            httpGet.setHeader("User-Agent", SocialReader.USERAGENT);

                            HttpResponse response = httpClient.execute(httpGet);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                if (LOGGING)
                                    Log.v(LOGTAG, "Response Code is good");

                                is = response.getEntity().getContent();
                                xr.parse(new InputSource(is));

                                is.close();

                                Date currentDate = new Date();
                                item.setStatus(Feed.STATUS_LAST_SYNC_GOOD);

                            } else {
                                Log.v(LOGTAG, "Response Code: " + response.getStatusLine().getStatusCode());
                                if (response.getStatusLine().getStatusCode() == 404) {
                                    item.setStatus(Feed.STATUS_LAST_SYNC_FAILED_404);
                                } else {
                                    item.setStatus(Feed.STATUS_LAST_SYNC_FAILED_UNKNOWN);
                                }
                            }

                        }
                        catch (Exception ioe)
                        {
                            if (LOGGING)
                                Log.e("SAX XML", "sax parse io error", ioe);
                            item.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);

                        }
                    } else {
                        if (LOGGING)
                            Log.e(LOGTAG, "Failed to sync feed, bad URL " + item.getCommentsUrl());

                        item.setStatus(Feed.STATUS_LAST_SYNC_FAILED_BAD_URL);
                    }
				}

				@Override
				public void onConnectionException(Exception e) {

				}

				@Override
				public void onTimeout() {

				}

				@Override
				public void onInvalid() {

				}
			});


	

		}
		catch (ParserConfigurationException pce)
		{
			if (LOGGING) 
				Log.e("SAX XML", "sax parse error", pce);
			item.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);

		}
		catch (SAXException se)
		{
			if (LOGGING)
				Log.e("SAX XML", "sax error", se);
			item.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);

		}
		catch (IllegalStateException ise)
		{
			if (LOGGING)
				ise.printStackTrace();
			item.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);
		}
		catch (Exception ise)
		{
			if (LOGGING)
				ise.printStackTrace();
			item.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);
		}

		return feed;
	}

	/**
	 * SAX handler for parsing RSS feeds.
	 * 
	 */
	public class Handler extends DefaultHandler
	{

		// Keep track of which entity we're currently assigning properties to
		private final Stack<FeedEntity> _entityStack;

		public Handler()
		{
			_entityStack = new Stack<FeedEntity>();
			_entityStack.add(feed);
		}

		private StringBuilder _contentBuilder;

		@Override
		public void startDocument() throws SAXException
		{
			_contentBuilder = new StringBuilder();
		}

		@Override
		public void endDocument() throws SAXException
		{
			super.endDocument();
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
		{
			if (isContentTag(localName) || isContentTag(qName))
			{
				_contentBuilder = new StringBuilder();
			}
			else if (isEntityTag(qName))
			{
				if (qName.equals("item"))
				{
					Comment comment = new Comment(attributes);
					_entityStack.add(comment);
					feed.addComment(comment);
				}
				else if (qName.equals("channel"))
				{
					// this is just the start of the feed
				}
				else
				{
					if (LOGGING)
						Log.v(LOGTAG,"Don't know how to create an entity from tag " + qName);
						//throw new RuntimeException("Don't know how to create an entity from tag " + qName);
				}
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException
		{
			// get the latest parsed content, if there is any
			String content = "";
			if (isContentTag(qName))
			{
				content = _contentBuilder.toString().trim();
				if (qName.equalsIgnoreCase("content:encoded"))
				{
					qName = "contentEncoded";
				}
				else if (qName.equalsIgnoreCase("dc:creator"))
				{
					qName = "author";
				}
				else if (qName.equalsIgnoreCase("extrss:id"))
				{
					qName = "remotePostId";
				}
				_entityStack.lastElement().setProperty(qName, content);
			}
			else if (isEntityTag(qName))
			{
				if (!_entityStack.isEmpty())
					_entityStack.pop();
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException
		{
			String content = new String(ch, start, length);
			_contentBuilder.append(content);
		}
	}
}
