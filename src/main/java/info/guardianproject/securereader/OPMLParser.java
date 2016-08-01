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

public class OPMLParser {

	public static final String OUTLINE_ELEMENT = "outline";
	public static final String TEXT_ATTRIBUTE = "text";
	public static final String HTMLURL_ATTRIBUTE = "htmlUrl";
	public static final String XMLURL_ATTRIBUTE = "xmlUrl";
	public static final String SUBSCRIBE_ATTRIBUTE = "subscribe";
	public static final String DESCRIPTION_ATTRIBUTE = "description";
	
	private static final String LOGTAG = "OPMLPARSER";
	public static final boolean LOGGING = false;
			
	public class OPMLOutline {
		public String text = "";
		public String htmlUrl = "";
		public String xmlUrl = "";
		public boolean subscribe = false;
		public String description = "";
	}
	
	ArrayList<OPMLOutline> outlines;
	
	OPMLOutline currentOutline;
	
	public interface OPMLParserListener {
		public void opmlParsed(ArrayList<OPMLOutline> outlines);
	}

	public OPMLParserListener opmlParserListener;
	
	public SocialReader socialReader;
	
	public void setOPMLParserListener(OPMLParserListener listener) {
		opmlParserListener = listener;
	}
	
	public OPMLParser(final SocialReader socialReader, final String urlToParse, final OPMLParserListener listener) {
		setOPMLParserListener(listener);

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
						Log.v(LOGTAG, "Using Proxy for OPML Retrieval");

					httpClient.useProxy(true, socialReader.getProxyType(), socialReader.getProxyHost(), socialReader.getProxyPort());
				}
		
				HttpGet httpGet = new HttpGet(urlToParse);
				httpGet.setHeader("User-Agent", SocialReader.USERAGENT);
				
				HttpResponse response;
				try {
					response = httpClient.execute(httpGet);
				
					if (response.getStatusLine().getStatusCode() == 200) {
						if (LOGGING) 
							Log.v(LOGTAG,"Response Code is good for OPML feed");
						
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
				if (LOGGING)
					Log.v(LOGTAG, "Should be calling opmlParsed on opmlParserListener");
				if (opmlParserListener != null) {
					if (LOGGING) 
						Log.v(LOGTAG, "Actually calling opmlParsed on opmlParserListener");
					opmlParserListener.opmlParsed(outlines);
				}
			}
		};
		
		asyncTask.execute();
	}
	
	public OPMLParser(InputStream streamToParse, OPMLParserListener listener) {
		setOPMLParserListener(listener);
		parse(streamToParse);
		
		if (opmlParserListener != null) {
    		opmlParserListener.opmlParsed(outlines);
    	}		
	}
	
	public OPMLParser(File fileToParse, OPMLParserListener listener) {
		try {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileToParse));			
			parse(bis);
			bis.close();	
			
			if (opmlParserListener != null) {
	    		opmlParserListener.opmlParsed(outlines);
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
			OPMLParserHandler handler = new OPMLParserHandler();
			
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
	
	public class OPMLParserHandler extends DefaultHandler {
		
	    public void startDocument() throws SAXException {
	    	outlines = new ArrayList<OPMLOutline>();
	    	if (LOGGING)
	    		Log.v(LOGTAG,"startDocument");
	    }
	
	    public void endDocument() throws SAXException {
	    	/*if (opmlParserListener != null) {
	    		opmlParserListener.opmlParsed(outlines);
	    	}*/
	    	
	    	if (LOGGING)
	    		Log.v(LOGTAG,"endDocument");

	    }
	
	    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
	        if (qName.equalsIgnoreCase(OUTLINE_ELEMENT)) {
	        	currentOutline = new OPMLOutline();
	        	currentOutline.text = atts.getValue(TEXT_ATTRIBUTE);
	        	currentOutline.htmlUrl = atts.getValue(HTMLURL_ATTRIBUTE);
	        	currentOutline.xmlUrl = atts.getValue(XMLURL_ATTRIBUTE);
	        	currentOutline.description = atts.getValue(DESCRIPTION_ATTRIBUTE);
	        	if (atts.getValue(SUBSCRIBE_ATTRIBUTE) == null || atts.getValue(SUBSCRIBE_ATTRIBUTE).equals("true")) {
	        		currentOutline.subscribe = true;
	        	}
	        	
	        	if (LOGGING)
	        		Log.v(LOGTAG,"startElement OUTLINE_ELEMENT");
	        }
	    }
	
	    public void endElement(String uri, String localName, String qName) throws SAXException {
	        if (qName.equalsIgnoreCase(OUTLINE_ELEMENT)) {
	        	outlines.add(currentOutline);
	        	
	        	if (LOGGING)
	        		Log.v(LOGTAG,"endElement OUTLINE_ELEMENT");
	        }
	        
	    }
	}
}