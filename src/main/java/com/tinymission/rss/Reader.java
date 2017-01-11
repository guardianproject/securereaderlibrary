package com.tinymission.rss;

import ch.boye.httpclientandroidlib.Header;
import info.guardianproject.netcipher.client.StrongHttpsClient;
import info.guardianproject.securereader.SocialReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang.StringEscapeUtils;
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
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;

/**
 * Reads an RSS feed and creates and RssFeed object.
 */
public class Reader
{

	public final static String LOGTAG = "TinyRSS Reader";
	public final static boolean LOGGING = false;

	private Feed feed;

	private SocialReader socialReader;

	InputStream is;

	/**
	 * The allowed tags to parse content from (everything else gets lumped into
	 * its parent content, which allows for embedding html content.
	 * 
	 */
	public final static String[] CONTENT_TAGS = {
			"title", "link", "language", "pubDate", "lastBuildDate",
			"docs", "generator", "managingEditor", "webMaster", "guid",
			"author", "dc:creator", "category", "content:encoded", "description", "url",
			"extrss:id", "wfw:commentRss",
			"id", "updated", "summary", "content", "name", "email"
	};
	// title, link,

	/**
	 * The tags that should be parsed into separate entities, not just
	 * properties of existing entities.
	 * 
	 */
	public final static String[] ENTITY_TAGS = { "channel", "item", "media:content", "media:thumbnail", "enclosure", "image",
		"feed", "entry", "author", "link"
	};

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
	public Reader(SocialReader _socialReader, Feed _feed)
	{
		socialReader = _socialReader;
		_feed.setStatus(Feed.STATUS_SYNC_IN_PROGRESS);
		feed = _feed;
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
	 * @return The feed object containing all the feed data.
	 */
	public Feed fetchFeed()
	{
		try
		{
			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setNamespaceAware(true);
			SAXParser sp = spf.newSAXParser();

			XMLReader xr = sp.getXMLReader();
			
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

			String feedUrl = "";
			// If it's not an input stream, check the preprocessor
			if (feed.getInputStream() == null) {

				feedUrl = feed.getFeedURL();
				if (socialReader.getFeedPreprocessor() != null) {
					String newFeedUrl = socialReader.getFeedPreprocessor().onGetFeedURL(feed);
					if (newFeedUrl != null) {
						if (LOGGING)
							Log.v(LOGTAG, "Feed URL changed by callback: " + feedUrl + " -> " + newFeedUrl);
						feedUrl = newFeedUrl;
					}
				}
			}

			final String PREFIX = "file:///android_asset/";

			// If it's an input stream or a file url then we are dealing with an input stream
			if (feed.getInputStream() != null || feedUrl.startsWith("file:///")) {

				// If it's not an input stream, get the inputstream
				if (feed.getInputStream() == null) {
					if (LOGGING)
						Log.v(LOGTAG, "Opening: " + feedUrl.substring(PREFIX.length()));

					AssetManager assetManager = socialReader.applicationContext.getAssets();

					is = assetManager.open(feedUrl.substring(PREFIX.length()));
					if (socialReader.getFeedPreprocessor() != null) {
						InputStream newIs = socialReader.getFeedPreprocessor().onFeedDownloaded(feed, is, null);
						if (newIs != null) {
							if (LOGGING)
								Log.v(LOGTAG, "Feed content was changed by callback");
							is = newIs;
						}
					}
				} else {
					// Otherwise get the input stream directly
					is = feed.getInputStream();
				}

				// Parse it
				xr.parse(new InputSource(is));

				is.close();

				Date currentDate = new Date();
				feed.setNetworkPullDate(currentDate);
				feed.setStatus(Feed.STATUS_LAST_SYNC_GOOD);

			} else {

				StrongHttpsClient httpClient = new StrongHttpsClient(socialReader.applicationContext);

				if (socialReader.relaxedHTTPS) {
					httpClient.enableSSLCompatibilityMode();
				}

				if (socialReader.useProxy())
				{
				    httpClient.useProxy(true, socialReader.getProxyType(), socialReader.getProxyHost(), socialReader.getProxyPort());
					//httpClient.useProxy(true, "HTTP", "127.0.0.1", 8080);

				    if (LOGGING)
				    	Log.v(LOGTAG,"Using Proxy: " + socialReader.getProxyType() + socialReader.getProxyHost() + socialReader.getProxyPort());
				}

				if (feedUrl != null && !(feedUrl.isEmpty()))
				{
					HttpGet httpGet = new HttpGet(feedUrl);
					httpGet.setHeader("User-Agent", SocialReader.USERAGENT);

					if (LOGGING)
						Log.v(LOGTAG,"Hitting: " + feedUrl);

					HttpResponse response = httpClient.execute(httpGet);

					if (LOGGING)
						Log.v(LOGTAG,"Response: " + response.toString());

					if (response.getStatusLine().getStatusCode() == 200) {
						if (LOGGING)
							Log.v(LOGTAG,"Response Code is good");

						InputStream is = response.getEntity().getContent();
						if (socialReader.getFeedPreprocessor() != null) {
							HashMap<String, String> headers = new HashMap<String, String>();
							for (Header h : response.getAllHeaders()) {
								headers.put(h.getName(), h.getValue());
							}
							InputStream newIs = socialReader.getFeedPreprocessor().onFeedDownloaded(feed, response.getEntity().getContent(), headers);
							if (newIs != null) {
								if (LOGGING)
									Log.v(LOGTAG, "Feed content was changed by callback");
								is = newIs;
							}
						}

						xr.parse(new InputSource(is));

						is.close();

						Date currentDate = new Date();
						feed.setNetworkPullDate(currentDate);
						feed.setStatus(Feed.STATUS_LAST_SYNC_GOOD);

					} else {
						if (LOGGING)
							Log.v(LOGTAG,"Response Code: " + response.getStatusLine().getStatusCode());
						if (response.getStatusLine().getStatusCode() == 404) {
							feed.setStatus(Feed.STATUS_LAST_SYNC_FAILED_404);
						} else {
							feed.setStatus(Feed.STATUS_LAST_SYNC_FAILED_UNKNOWN);
						}
					}
				} else {
					if (LOGGING)
						Log.e(LOGTAG, "Failed to sync feed, bad URL");

					feed.setStatus(Feed.STATUS_LAST_SYNC_FAILED_BAD_URL);
				}
			}
		}
		catch (ParserConfigurationException pce)
		{
			if (LOGGING) 
				Log.e("SAX XML", "sax parse error", pce);
			feed.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);

		}
		catch (SAXException se)
		{
			if (LOGGING)
				Log.e("SAX XML", "sax error", se);
			feed.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);

		}
		catch (IOException ioe)
		{
			if (LOGGING) 
				Log.e("SAX XML", "sax parse io error", ioe);
			feed.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);

		}
		catch (IllegalStateException ise)
		{
			if (LOGGING)
				ise.printStackTrace();
			feed.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);
		}
		catch (Exception e)
		{
			if (LOGGING)
				e.printStackTrace();
			feed.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);
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
			if (isEntityTag(qName))
			{
				if (qName.equals("link")) {
					FeedEntity lastEntity = _entityStack.lastElement();

					// Link in ATOM is an entity, in RSS a content tag
					if (attributes != null)
					{
						for (int i = 0; i < attributes.getLength(); i++)
						{
							String name = attributes.getLocalName(i);
							if (name.equalsIgnoreCase("href")) {
								String value = attributes.getValue(i);
								if (lastEntity.getClass() == Item.class)
								{
									((Item) lastEntity).setLink(value);
								}
							}
						}
					} else {
						// It's content tag
						_contentBuilder = new StringBuilder();
					}
				}
				else if (qName.equals("item") || qName.equals("entry"))
				{
					Item item = new Item(attributes);
					_entityStack.add(item);
					if (LOGGING)
						Log.v(LOGTAG,"Found item");
					feed.addItem(item);
				}
				else if (qName.equals("enclosure"))
				{
					MediaContent mediaContent = new MediaContent(attributes);
					FeedEntity lastEntity = _entityStack.lastElement();
					if (lastEntity.getClass() == Item.class)
					{
						((Item) lastEntity).setMediaContent(mediaContent);
					}
					if (LOGGING)
						Log.v(LOGTAG,"Found enclosure");
					_entityStack.add(mediaContent);
				}
				else if (qName.equals("media:content"))
				{
					MediaContent mediaContent = new MediaContent(attributes);
					FeedEntity lastEntity = _entityStack.lastElement();
					if (lastEntity.getClass() == Item.class)
					{
						((Item) lastEntity).setMediaContent(mediaContent);
					}
					if (LOGGING)
						Log.v(LOGTAG,"Found media content");
					_entityStack.add(mediaContent);
				}
				else if (qName.equals("media:thumbnail"))
				{
					MediaThumbnail mediaThumbnail = new MediaThumbnail(attributes);
					FeedEntity lastEntity = _entityStack.lastElement();
					if (lastEntity.getClass() == Item.class)
					{
						Item item = (Item) lastEntity;
						MediaThumbnail existingMt = item.getMediaThumbnail();
						if (existingMt == null)
						{
							item.setMediaThumbnail(mediaThumbnail);
						}
					}
					if (LOGGING)
						Log.v(LOGTAG,"Found media thumbnail");
					_entityStack.add(mediaThumbnail);
				}
				else if (qName.equals("channel") || qName.equals("feed") || qName.equals("entry"))
				{
					// this is just the start of the feed
				}
				else if (qName.equals("image"))
				{
					MediaContent mediaContent = new MediaContent(attributes);
										
					FeedEntity lastEntity = _entityStack.lastElement();
					if (lastEntity.getClass() == Feed.class)
					{						
						((Feed) lastEntity).setMediaContent(mediaContent);
					}
					if (LOGGING)
						Log.v(LOGTAG,"Found image");
					_entityStack.add(mediaContent);
				}
				else
				{
					if (LOGGING)
						Log.v(LOGTAG, "Don't know how to create an entity from tag " + qName);
					//throw new RuntimeException("Don't know how to create an entity from tag " + qName);
				}				
			}
			else if (isContentTag(localName) || isContentTag(qName))
			{
				_contentBuilder = new StringBuilder();
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException
		{
			if (LOGGING)
				Log.v(LOGTAG,"endElement " + localName + ":" + qName + ":" +  StringEscapeUtils.unescapeXml(_contentBuilder.toString().trim()));
			// get the latest parsed content, if there is any
			String content = "";
			if (isContentTag(qName))
			{
				//"id", "updated", "summary", "content", "name", "email"
				// title, link,

				content = StringEscapeUtils.unescapeXml(_contentBuilder.toString().trim());

				if (qName.equalsIgnoreCase("content:encoded"))
				{
					qName = "contentEncoded";
				}
				else if (qName.equalsIgnoreCase("id"))
				{
					qName = "guid";
				}
				else if (qName.equalsIgnoreCase("updated"))
				{
					qName = "pubDate";
				}
				else if (qName.equalsIgnoreCase("summary"))
				{
					qName = "description";
				}
				else if (qName.equalsIgnoreCase("content"))
				{
					qName = "contentEncoded";
				}
				else if (qName.equalsIgnoreCase("extrss:id"))
				{
					if (LOGGING)
						Log.v(LOGTAG,"Got a remotePostId:" + content);
					qName = "remotePostId";
				}
				else if (qName.equalsIgnoreCase("dc:creator"))
				{
					qName = "author";
				}
				else if (qName.equalsIgnoreCase("wfw:commentRss")) 
				{
					if (LOGGING)
						Log.v(LOGTAG,"Got a wfw:commentRss: " + content);
					qName = "commentsUrl";
				}
				_entityStack.lastElement().setProperty(qName, content);
			}
			else if (isEntityTag(qName))
			{
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