package info.guardianproject.securereader;

import info.guardianproject.iocipher.File;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import redstone.xmlrpc.XmlRpcArray;
import redstone.xmlrpc.XmlRpcClient;
import redstone.xmlrpc.XmlRpcStruct;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

import net.bican.wordpress.Enclosure;
import net.bican.wordpress.MediaItem;
import net.bican.wordpress.MediaItemUploadResult;
import net.bican.wordpress.Post;
import net.bican.wordpress.Term;
import net.bican.wordpress.Wordpress;

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

					Post post = new Post();
					post.setPost_type("post");
					post.setPost_title(item.getTitle());
					post.setPost_status("publish");

					StringBuffer sbBody = new StringBuffer();
					sbBody.append(item.getDescription());

					boolean hasSetHeading = false;
					ArrayList<MediaItemUploadResult> mediaObjects = new ArrayList<>();
					int thumbnail = -1;

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
							String mimeType = "image/jpeg";
							if (mc.getType() != null)
								mimeType = mc.getType();
							MediaItemUploadResult res = wordpress.uploadFile(mimeType, f);
							if (res != null) {
								if (LOGGING)
									Log.d(LOGTAG, "Uploaded " + res.getUrl() + " with id " + res.getId());
								mediaObjects.add(res);
								if (mc.getMediaContentType() == MediaContent.MediaContentType.IMAGE) {
									if (thumbnail == -1)
										thumbnail = res.getId();
									//sbBody.append("\n\n[gallery ids=\"" + res.getId() + "\"]");
									sbBody.append("\n\n<img src=\"" + res.getUrl() + "\"/>");
									//sbBody.append("\n\n[embed]" + res.getUrl() + "[/embed]");
								} else {
									sbBody.append("\n\n[embed]" + res.getUrl() + "[/embed]");
								}
//								if (mc.getMediaContentType() == MediaContent.MediaContentType.IMAGE) {
//									if (!hasSetHeading) {
//										hasSetHeading = true;
//										sbBody.insert(0, "<img src=\"" + res.getUrl() + "\"/>\n\n");
//									} else {
//										sbBody.append("\n\n<img src=\"" + res.getUrl() + "\"/>");
//									}
//								} else {
//									sbBody.append("\n\n[embed]" + res.getUrl() + "[/embed]");
//								}

								// This should
								//Enclosure enclosureStruct = new Enclosure();
								//enclosureStruct.setLength((int)f.length());
								//enclosureStruct.setType(res.getType());
								//enclosureStruct.setUrl(res.getUrl());
								//post.setEnclosure(enclosureStruct);
							}
						}
					}

					post.setPost_content(sbBody.toString());
					post.setComment_status("open");
					if (item.getTags() != null && item.getTags().size() > 0) {
						ArrayList<Term> tags = new ArrayList<>();
						for (String tag : item.getTags()) {
							Term newTerm = new Term();
							newTerm.setTaxonomy("post_tag");
							newTerm.setName(tag);
							try {
								if (LOGGING)
									Log.d(LOGTAG, "Creating tag for " + newTerm.getName());
								Integer id = wordpress.newTerm(newTerm);
								if (LOGGING)
									Log.d(LOGTAG, "Got id " + id);
								newTerm.setTerm_id(id);
								tags.add(newTerm);
							} catch(Exception e) {
								e.printStackTrace();
								if (LOGGING)
									Log.d(LOGTAG, "Got error " + e.toString());
							}
						}
						if (tags.size() > 0) {
							post.setTerms(tags);
						}
					}
					if (thumbnail != -1) {
						// Set featured image
						post.setPost_thumbnail(thumbnail);
					}
					int postId = wordpress.newPost(post);
					if (LOGGING)
						Log.v(LOGTAG, "Posted: " + postId);

					if (postId != 0) {
						item.dbsetRemotePostId(postId);
						// Link media to post
						for (MediaItemUploadResult mediaUpload : mediaObjects) {
							Post mediaUpdate = new Post();
							mediaUpdate.setPost_parent(postId);
							wordpress.editPost(mediaUpload.getId(), mediaUpdate);
						}
					}
					return postId;
					
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
