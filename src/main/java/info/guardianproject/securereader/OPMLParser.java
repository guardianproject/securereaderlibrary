package info.guardianproject.securereader;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import info.guardianproject.iocipher.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;

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
		public String category = null;
	}
	
	ArrayList<OPMLOutline> outlines;
	
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
				HttpClient httpClient = socialReader.getHttpClient();
		
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
			XPathFactory factory = XPathFactory.newInstance();
			XPath xPath = factory.newXPath();
			NodeList outlines = (NodeList) xPath.evaluate("//outline[not(parent::outline)]",
					new InputSource(streamToParse),
					XPathConstants.NODESET);
			for (int i = 0; i < outlines.getLength(); i++) {
				Element outline = (Element) outlines.item(i);
				if (!TextUtils.isEmpty(outline.getAttribute("xmlUrl")))
				{
					parseOutlineNode(outline, null);
				}
				else
				{
					String category = outline.getAttribute("text");
					NodeList categoryOutlines = (NodeList) xPath.evaluate(".//outline[@xmlUrl]", outline, XPathConstants.NODESET);
					for (int j = 0; j < categoryOutlines.getLength(); j++) {
						parseOutlineNode((Element)categoryOutlines.item(j), category);
					}
				}
			}
		} catch (XPathExpressionException e) {
			if (LOGGING) {
				e.printStackTrace();
			}
		}
	}

	private void parseOutlineNode(Element outline, String category)
	{
		OPMLOutline currentOutline = new OPMLOutline();
		currentOutline.text = outline.getAttribute(TEXT_ATTRIBUTE);
		currentOutline.htmlUrl = outline.getAttribute(HTMLURL_ATTRIBUTE);
		currentOutline.xmlUrl = outline.getAttribute(XMLURL_ATTRIBUTE);
		currentOutline.description = outline.getAttribute(DESCRIPTION_ATTRIBUTE);
		currentOutline.category = category;
		String subscribe = outline.getAttribute("subscribe");
		if (TextUtils.isEmpty(subscribe) || "true".equalsIgnoreCase(subscribe)) {
			currentOutline.subscribe = true;
		}
		if (!TextUtils.isEmpty(currentOutline.xmlUrl)) {
			if (outlines == null) {
				outlines = new ArrayList<>();
			}
			outlines.add(currentOutline);
		}
	}
}
