package info.guardianproject.securereader;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;

import com.tinymission.rss.Comment;
import com.tinymission.rss.Item;

import info.guardianproject.securereader.XMLRPCPublisher.XMLRPCPublisherCallback;
import info.guardianproject.securereader.XMLRPCCommentPublisher.XMLRPCCommentPublisherCallback;

import net.bican.wordpress.Wordpress;

import java.util.ArrayList;
import java.util.Date;

//import com.tinymission.rss.MediaContent.MediaContentType;

// Lots of borrowed code from:
//https://github.com/guardianproject/mrapp/blob/master/app/src/info/guardianproject/mrapp/server/ServerManager.java

// To Do:  Deal with media, Get Lists working

public class SocialReporter
{
	public static final String LOGTAG = "SocialReporter";
	public static final boolean LOGGING = false;
	
	public static boolean REQUIRE_PROXY = true;
	
	SocialReader socialReader;
	Context applicationContext;
	Wordpress wordpress;
	
	public String xmlrpcEndpoint;

	public String[] xmlrpcEndpointPinnedCert = null;

	public SocialReporter(SocialReader _socialReader)
	{
		socialReader = _socialReader;
		applicationContext = socialReader.applicationContext;
		
		xmlrpcEndpoint = applicationContext.getResources().getString(R.string.xmlrpc_endpoint);

		String certPin = applicationContext.getResources().getString(R.string.xmlrpc_endpoint_cert_pin);

		if (!TextUtils.isEmpty(certPin)) {
			xmlrpcEndpointPinnedCert = new String[1];
			xmlrpcEndpointPinnedCert[0] = applicationContext.getResources().getString(R.string.xmlrpc_endpoint_cert_pin);
		}
	}

	public boolean useProxy() 
	{
		if (REQUIRE_PROXY) { 
			return true;
		} else {
			return socialReader.useProxy();
		}
	}

	public ArrayList<Item> getPosts()
	{
		if (LOGGING)
			Log.v(LOGTAG, "getPosts()");
		
		ArrayList<Item> posts = new ArrayList<Item>();
		
		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady()) {
			posts = socialReader.databaseAdapter.getFeedItems(DatabaseHelper.POSTS_FEED_ID, -1);
		} else {
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready");
		}
		
		return posts;
	}

	public ArrayList<Item> getDrafts()
	{
		if (LOGGING)
			Log.v(LOGTAG, "getDrafts()");
		ArrayList<Item> drafts =  new ArrayList<Item>();
		
		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady())
		{
			drafts = socialReader.databaseAdapter.getFeedItems(DatabaseHelper.DRAFTS_FEED_ID, -1);
			
			// Debugging
			for (int i = 0; i < drafts.size(); i++)
			{
				Item draft = drafts.get(i);
				if (LOGGING)
					Log.v("DRAFT", draft.getTitle());
			}
		} else {
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready");
		}
		
		return drafts;
	}

	public Item createDraft(String title, String content, ArrayList<String> tags, ArrayList<Bitmap> mediaItems)
	{
		if (LOGGING)
			Log.v(LOGTAG, "createDraft");
		
		Item item = new Item("BigBuffalo_" + new Date().getTime() + "" + (int) (Math.random() * 1000), title, new Date(), "SocialReporter", content,
				DatabaseHelper.DRAFTS_FEED_ID);

		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady()) {
			// Add the tags
			if (tags != null)
			{
				for (String tag : tags)
					item.addTag(tag);
			}
	
			socialReader.databaseAdapter.addOrUpdateItem(item, false, -1);
		} else {
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready");
		}
		return item;
	}

	public void saveDraft(Item story)
	{
		if (LOGGING)
			Log.v(LOGTAG, "saveDraft");
		
		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady())
		{
			story.setPubDate(new Date());
			socialReader.databaseAdapter.addOrUpdateItem(story, false, -1);
		}
		else 
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready");
		}
	}

	public void deleteDraft(Item story)
	{
		socialReader.deleteItem(story);
	}

	public void publish(Item story, XMLRPCPublisherCallback callback)
	{
		if (LOGGING)
			Log.v(LOGTAG, "publish");

		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady())
		{
			// Do the actual publishing in a background thread
			XMLRPCPublisher publisher = new XMLRPCPublisher(this);
			publisher.setXMLRPCPublisherCallback(callback);
			publisher.execute(story);
		} else {
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready");
		}
	}

	public void deletePost(Item story, XMLRPCDeleter.XMLRPCDeleterCallback callback)
	{
		if (LOGGING)
			Log.v(LOGTAG, "deletePost");

		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady())
		{
			// Do the actual publishing in a background thread
			XMLRPCDeleter deleter = new XMLRPCDeleter(this);
			deleter.setXMLRPCDeleterCallback(callback);
			deleter.execute(story);
		} else {
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready");
		}
	}

	public void postComment(Comment comment, XMLRPCCommentPublisherCallback callback)
	{
		if (LOGGING)
			Log.v(LOGTAG, "postComment");
		
		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady())
		{
			// Do the actual publishing in a background thread
			XMLRPCCommentPublisher publisher = new XMLRPCCommentPublisher(this);
			publisher.setXMLRPCCommentPublisherCallback(callback);
			publisher.execute(comment);
		} else {
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready");
		}
	}
		
	/*
	public boolean isSignedIn()
	{
		Log.v(LOGTAG, "isSignedIn");
		return true;
	}
	*/
	
	public String getAuthorName()
	{		
		// Might have to check for null
		if (socialReader.ssettings != null) {
			return socialReader.ssettings.nickname();
		} else {
			return null;
		}
	}

	public void createAuthorName(String authorName)
	{
		// Might have to check for null
		if (socialReader.ssettings != null) {
			socialReader.ssettings.setNickname(authorName);
		} else {
			if (LOGGING)
				Log.e(LOGTAG,"Can't set nickname, SecureSettings object not created");
		}
	}
}
