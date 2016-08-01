package info.guardianproject.securereader;

import info.guardianproject.iocipher.File;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;

import net.bican.wordpress.MediaObject;
import net.bican.wordpress.Page;
import net.bican.wordpress.Wordpress;
import redstone.xmlrpc.XmlRpcClient;
import redstone.xmlrpc.XmlRpcStruct;
import android.os.AsyncTask;
import android.util.Log;

import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

/**
 * Class for fetching the feed content in the background.
 * 
 */
public class XMLRPCPublisher extends AsyncTask<Item, Integer, Integer>
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "XMLRPC PUBLISHER";

	public static final int FAILURE_REASON_NO_PRIVACY_PROXY = -1;
	public static final int FAILURE_REASON_NO_CONNECTION = -2;
	public static final int FAILURE_USERNAME_PASSWORD = -3;
	public static final int FAILURE_UNKNOWN = -4;
	
	SocialReporter socialReporter;

	XMLRPCPublisherCallback itemPublishedCallback;

	public void setXMLRPCPublisherCallback(XMLRPCPublisherCallback _itemPublishedCallback)
	{
		itemPublishedCallback = _itemPublishedCallback;
	}

	public interface XMLRPCPublisherCallback
	{
		public void itemPublished(int itemId);
		public void publishingFailed(int reason);
	}

	public XMLRPCPublisher(SocialReporter _socialReporter)
	{
		super();
		socialReporter = _socialReporter;
	}

	@Override
	protected Integer doInBackground(Item... params)
	{
		Item item = new Item();
		if (params.length == 0)
		{
			if (LOGGING)
				Log.v(LOGTAG, "doInBackground params length is 0");
			
			return FAILURE_UNKNOWN;
		}
		else
		{
			item = params[0];

			try
			{
				XmlRpcClient.setContext(socialReporter.applicationContext);		
				
				if (socialReporter.useProxy())
				{
					if (!socialReporter.socialReader.useProxy()) {
						// Gotta enable that proxy
						return FAILURE_REASON_NO_PRIVACY_PROXY;
					}
					XmlRpcClient.setProxy(true, socialReporter.socialReader.getProxyType(), socialReporter.socialReader.getProxyHost(), socialReporter.socialReader.getProxyPort());
				}
				else
				{
					XmlRpcClient.setProxy(false, null, null, -1);
				}

				String xmlRPCUsername = socialReporter.socialReader.ssettings.getXMLRPCUsername();
				String xmlRPCPassword = socialReporter.socialReader.ssettings.getXMLRPCPassword();
								
				if (xmlRPCUsername == null || xmlRPCPassword == null) {
					
					String nickname = socialReporter.socialReader.ssettings.nickname();
					if (nickname == null) {
						nickname = "";
					}
					
					// acxu.createUser
					ArrayList<String> arguments = new ArrayList<String>();
					arguments.add(nickname);
					XmlRpcClient xpc = new XmlRpcClient(new URL(socialReporter.xmlrpcEndpoint));
					String result = (String) xpc.invoke("acxu.createUser", arguments);
					if (LOGGING) 
						Log.v(LOGTAG,"From wordpress: " + result);
					String[] sresult = result.split(" ");
					if (sresult.length == 2) {
						xmlRPCUsername = sresult[0];
						xmlRPCPassword = sresult[1];
						
						socialReporter.socialReader.ssettings.setXMLRPCUsername(xmlRPCUsername);
						socialReporter.socialReader.ssettings.setXMLRPCPassword(xmlRPCPassword);
					}
				}
				
				if (xmlRPCUsername != null && xmlRPCPassword != null) 
				{
	
					if (LOGGING) 
						Log.v(LOGTAG, "Logging into XMLRPC Interface: " + xmlRPCUsername + '@' + socialReporter.xmlrpcEndpoint);
					Wordpress wordpress = new Wordpress(xmlRPCUsername, xmlRPCPassword, socialReporter.xmlrpcEndpoint);
	
					Page page = new Page();
					page.setTitle(item.getTitle());
	
					StringBuffer sbBody = new StringBuffer();
					sbBody.append(item.getDescription());
	
					ArrayList<MediaContent> mediaContent = item.getMediaContent();
					for (MediaContent mc : mediaContent)
					{
						//String filePath = mc.getFilePathFromLocalUri(socialReporter.applicationContext);
						//String filePath = mc.getUrl();
						URI fileUri = new URI(mc.getUrl());
						if (LOGGING)
							Log.v(LOGTAG,"filePath: "+fileUri.getPath());
						if (fileUri != null)
						{
							File f = new File(fileUri.getPath());
							MediaObject mObj = wordpress.newMediaObject("image/jpeg", f, false);
	
							if (mObj != null)
							{
		
								sbBody.append("\n\n<a href=\"" + mObj.getUrl() + "\">Link to media</a>");
	
								// This should
								XmlRpcStruct enclosureStruct = new XmlRpcStruct();
								enclosureStruct.put("url", mObj.getUrl());
								enclosureStruct.put("length", f.length());
								enclosureStruct.put("type", mObj.getType());
								page.setEnclosure(enclosureStruct);
	
							}
						}
					}
	
					page.setDescription(sbBody.toString());
					boolean publish = true;
	
					String postId = wordpress.newPost(page, publish);
					if (LOGGING)
						Log.v(LOGTAG, "Posted: " + postId);
					
					return Integer.valueOf(postId);
					
				} else {
					if (LOGGING)
						Log.e(LOGTAG,"Can't publish, no username/password");
					
					return FAILURE_USERNAME_PASSWORD; 
				}

			}
			catch (MalformedURLException e)
			{
				if (LOGGING)
					e.printStackTrace();
					
				return FAILURE_REASON_NO_CONNECTION;
			}
			catch (Exception e)
			{
				if (LOGGING)
					e.printStackTrace();
				
				return FAILURE_UNKNOWN;
			}
		}
	}

	@Override
	protected void onPostExecute(Integer status)
	{
		if (itemPublishedCallback != null)
		{
			if (status >= 0) {
				itemPublishedCallback.itemPublished(status);
			} else {
				itemPublishedCallback.publishingFailed(status);
			}
		}
	}
}
