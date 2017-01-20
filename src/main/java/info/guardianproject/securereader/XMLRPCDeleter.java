package info.guardianproject.securereader;

import android.os.AsyncTask;
import android.util.Log;

import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

import net.bican.wordpress.MediaItemUploadResult;
import net.bican.wordpress.Post;
import net.bican.wordpress.Term;
import net.bican.wordpress.Wordpress;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;

import info.guardianproject.iocipher.File;
import redstone.xmlrpc.XmlRpcClient;

/**
 * Class for fetching the feed content in the background.
 * 
 */
public class XMLRPCDeleter extends AsyncTask<Item, Integer, Integer>
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "XMLRPC DELETER";

	public static final int FAILURE_REASON_NO_PRIVACY_PROXY = -1;
	public static final int FAILURE_REASON_NO_CONNECTION = -2;
	public static final int FAILURE_USERNAME_PASSWORD = -3;
	public static final int FAILURE_UNKNOWN = -4;

	SocialReporter socialReporter;

	XMLRPCDeleterCallback itemDeletedCallback;

	public void setXMLRPCDeleterCallback(XMLRPCDeleterCallback _itemDeletedCallback)
	{
		itemDeletedCallback = _itemDeletedCallback;
	}

	public interface XMLRPCDeleterCallback
	{
		public void itemDelete(int remotePostId);
		public void itemDeleted(int itemId);
		public void deletionFailed(int reason);
	}

	public XMLRPCDeleter(SocialReporter _socialReporter)
	{
		super();
		socialReporter = _socialReporter;
	}

	@Override
	protected Integer doInBackground(Item... params)
	{
		Item item;
		if (params.length == 0)
		{
			if (LOGGING)
				Log.v(LOGTAG, "doInBackground params length is 0");
			
			return FAILURE_UNKNOWN;
		}
		else
		{
			item = params[0];

			if (item.getRemotePostId() == -1)
			{
				//there is no remote post, so let's just say success!
				return 1;
			}

			try
			{
				XmlRpcClient.setContext(socialReporter.applicationContext);		
				
				if (socialReporter.useProxy())
				{
					if (!socialReporter.socialReader.useProxy()) {
						// Gotta enable that proxy
						return FAILURE_REASON_NO_PRIVACY_PROXY;
					} else if (!socialReporter.socialReader.isProxyOnline()) {
						return FAILURE_REASON_NO_CONNECTION;
					}
					XmlRpcClient.setProxy(true, socialReporter.socialReader.getProxyType(), socialReporter.socialReader.getProxyHost(), socialReporter.socialReader.getProxyPort());
				}
				else
				{
					XmlRpcClient.setProxy(false, null, null, -1);
				}

				String xmlRPCUsername = socialReporter.socialReader.ssettings.getXMLRPCUsername();
				String xmlRPCPassword = socialReporter.socialReader.ssettings.getXMLRPCPassword();
				if (xmlRPCUsername != null && xmlRPCPassword != null)
				{
	
					if (LOGGING) 
						Log.v(LOGTAG, "Logging into XMLRPC Interface: " + xmlRPCUsername + '@' + socialReporter.xmlrpcEndpoint);
					Wordpress wordpress = new Wordpress(xmlRPCUsername, xmlRPCPassword, socialReporter.xmlrpcEndpoint);
					boolean success = wordpress.deletePost(item.getRemotePostId());
					if (success && itemDeletedCallback != null) {
						itemDeletedCallback.itemDelete(item.getRemotePostId());
					}
					return success ? 1 : 0;
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
		if (itemDeletedCallback != null)
		{
			if (status >= 0) {
				itemDeletedCallback.itemDeleted(status);
			} else {
				itemDeletedCallback.deletionFailed(status);
			}
		}
	}
}
