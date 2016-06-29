package info.guardianproject.securereader;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.netcipher.client.StrongHttpsClient;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.os.AsyncTask;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;

public class HTMLRSSFeedFinder {
	
	//<link href="http://webdesign.about.com/library/z_whats_new.rss" rel="alternate" type="application/rss+xml" title="What's New on About.com Web Design / HTML" />

	public boolean isRSSFeed = false;
	public String urlToParse;
	
	//<rss xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:sy="http://purl.org/rss/1.0/modules/syndication/" xmlns:admin="http://webns.net/mvcb/" xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" version="2.0">
	//<channel>
	//<title>
	
	public static final String RSS_ELEMENT = "rss";
	public static final String RSS_TYPE = "application/rss+xml";
	
	public static final String RSS_CHANNEL = "channel";
	public static final String RSS_CHANNEL_TITLE = "title";
	
	public boolean inRSSChannel = false;
	
	
	public static final String LINK_ELEMENT = "link";
	public static final String HREF_ATTRIBUTE = "href";
	public static final String REL_ATTRIBUTE = "rel";
	public static final String TYPE_ATTRIBUTE = "type";
	public static final String TITLE_ATTRIBUTE = "title";
	
	public static final String LOGTAG = "HTMLRSSFeedFinder";
	public final static boolean LOGGING = false;

	public class RSSFeed {
		public String href = "";
		public String rel = "";
		public String type = "";
		public String title = "";
	}
	
	ArrayList<RSSFeed> rssfeeds;
	
	RSSFeed currentRSSFeed;
	
	public interface HTMLRSSFeedFinderListener {
		public void feedFinderComplete(ArrayList<RSSFeed> rssFeeds);
	}

	public HTMLRSSFeedFinderListener feedFinderListener;
	
	public SocialReader socialReader;
	
	public void setHTMLRSSFeedFinderListener(HTMLRSSFeedFinderListener listener) {
		feedFinderListener = listener;
	}
	
	public HTMLRSSFeedFinder(final SocialReader socialReader, final String _urlToParse, final HTMLRSSFeedFinderListener listener) {
		urlToParse = _urlToParse;
		
		setHTMLRSSFeedFinderListener(listener);

		AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params)
			{
				StrongHttpsClient httpClient = new StrongHttpsClient(socialReader.applicationContext);

				if (socialReader.relaxedHTTPS) {
					httpClient.enableSSLCompatibilityMode();
				}

				if (socialReader.useProxy())
				{
					if (LOGGING)
						Log.v(LOGTAG,"Using Proxy for HTML Retrieval");

					httpClient.useProxy(true, socialReader.getProxyType(), socialReader.getProxyHost(), socialReader.getProxyPort());
				}
						
				HttpGet httpGet = new HttpGet(urlToParse);
				httpGet.setHeader("User-Agent", SocialReader.USERAGENT);
				
				HttpResponse response;
				try {
					response = httpClient.execute(httpGet);
				
					if (response.getStatusLine().getStatusCode() == 200) {
						if (LOGGING)
							Log.v(LOGTAG,"Response Code is good for HTML");
						
						InputStream	is = response.getEntity().getContent();
						parse(is);
						is.close();
					}
				} catch (IllegalStateException e) {
					if (LOGGING)
						e.printStackTrace();
				} catch (IOException e) {
					if (LOGGING)
						e.printStackTrace();
				}
				
				return null;
			}

			@Override
			protected void onPostExecute(Void nothing)
			{
				if (feedFinderListener != null) {
					feedFinderListener.feedFinderComplete(rssfeeds);
				}
			}
		};
		
		asyncTask.execute();
	}
	
	public HTMLRSSFeedFinder(InputStream streamToParse, HTMLRSSFeedFinderListener listener) {
		setHTMLRSSFeedFinderListener(listener);
		parse(streamToParse);
		
		if (feedFinderListener != null) {
			feedFinderListener.feedFinderComplete(rssfeeds);
    	}		
	}
	
	public HTMLRSSFeedFinder(File fileToParse, HTMLRSSFeedFinderListener listener) {
		try {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileToParse));			
			parse(bis);
			bis.close();	
			
			if (feedFinderListener != null) {
				feedFinderListener.feedFinderComplete(rssfeeds);
	    	}			
		} catch (FileNotFoundException e) {
			if (LOGGING)
				e.printStackTrace();
		} catch (IOException e) {
			if (LOGGING)
				e.printStackTrace();
		}
	}
	
	private void parse(InputStream streamToParse) {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();
			HTMLRSSFeedFinderParserHandler handler = new HTMLRSSFeedFinderParserHandler();
			
			saxParser.parse(streamToParse, handler);			
		} catch (ParserConfigurationException e) {
			if (LOGGING)
				e.printStackTrace();
		} catch (SAXException e) {
			if (LOGGING)
				e.printStackTrace();
		} catch (IOException e) {
			if (LOGGING)
				e.printStackTrace();
		}
	}
	
	public class HTMLRSSFeedFinderParserHandler extends DefaultHandler {
		
	    public void startDocument() throws SAXException {
	    	rssfeeds = new ArrayList<RSSFeed>();
			if (LOGGING)
				Log.v(LOGTAG,"startDocument");
	    }
	
	    public void endDocument() throws SAXException {	    	
			if (LOGGING)
				Log.v(LOGTAG,"endDocument");
	    }
	
	    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
	        if (!isRSSFeed && qName.equalsIgnoreCase(LINK_ELEMENT)) 
	        {
	        	currentRSSFeed = new RSSFeed();
	        	currentRSSFeed.href = atts.getValue(HREF_ATTRIBUTE);
	        	currentRSSFeed.rel = atts.getValue(REL_ATTRIBUTE);
	        	currentRSSFeed.type = atts.getValue(TYPE_ATTRIBUTE);
	        	currentRSSFeed.title = atts.getValue(TITLE_ATTRIBUTE);
	        	
				if (LOGGING)
					Log.v(LOGTAG,"startElement LINK_ELEMENT");
	        }
	        else if (qName.equalsIgnoreCase(RSS_ELEMENT)) 
	        {
	        	currentRSSFeed = new RSSFeed();
	        	currentRSSFeed.href = atts.getValue(urlToParse);
	        	currentRSSFeed.type = RSS_TYPE;	  
	        	
	        	isRSSFeed = true;
	        } 
	        else if (qName.equalsIgnoreCase(RSS_CHANNEL) && isRSSFeed) 
	        {
	        	inRSSChannel = true;
	        } 
	        else if (qName.equalsIgnoreCase(RSS_CHANNEL_TITLE) && inRSSChannel == true) 
	        {
	        	currentRSSFeed.title = "";
	        	inRSSChannel = false;
	        }
	    }
	
	    public void endElement(String uri, String localName, String qName) throws SAXException {
	        if (qName.equalsIgnoreCase(LINK_ELEMENT)) {
	        	rssfeeds.add(currentRSSFeed);
				if (LOGGING)
					Log.v(LOGTAG,"endElement LINK_ELEMENT");
	        } else if (qName.equalsIgnoreCase(RSS_ELEMENT)) {
	        	rssfeeds.add(currentRSSFeed);	        	
				if (LOGGING)
					Log.v(LOGTAG,"endElement RSS_ELEMENT");	        	
	        }
	    }
	}
}