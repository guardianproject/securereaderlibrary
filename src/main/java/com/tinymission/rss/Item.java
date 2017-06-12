package com.tinymission.rss;

import info.guardianproject.securereader.HTMLToPlainTextFormatter;
import info.guardianproject.securereader.SyncStatus;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.xml.sax.Attributes;

import android.util.Log;

/**
 * Encapsulates one RSS item.
 * 
 */
public class Item extends FeedEntity implements Serializable
{
	public static final long serialVersionUID = 133702L;

	public static final String LOGTAG = "rss.item";
	public static final boolean LOGGGING = false;

	public static final int DEFAULT_DATABASE_ID = -1;
	public static final int DEFAULT_REMOTE_POST_ID = -1;

	// Item fields for us
	private boolean _favorite = false;
	private boolean _shared = false;
	private int _viewCount = 0;
	private long _databaseId = DEFAULT_DATABASE_ID;
	private long _feedId;
	private int _remotePostId = DEFAULT_REMOTE_POST_ID;  // <extrarss:id>365</extrarss:id>
	
	// Item elements part of RSS
	private String _title;
	private String _link;
	private String _description;
	private Date _pubDate;
	private String _guid;
	private String _author;
	private String _source;
	//private String _category;
	private String _contentEncoded;

	ArrayList<String> categories = new ArrayList<String>();
	private ArrayList<MediaContent> _mediaContent;
	// Should this be here??
	private MediaThumbnail _mediaThumbnail;
	
	private ArrayList<Comment> comments = new ArrayList<Comment>();
	private String _commentsUrl;

	// This all relates to comment fetching
	private SyncStatus _status = SyncStatus.OK;

	public SyncStatus getStatus() {
		return _status;
	}
	
	public void setStatus(SyncStatus _status) {
		this._status = _status;
	}

	
	static SimpleDateFormat[] dateFormats = { new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
			new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH), 
			new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH), 
			new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ssz", Locale.ENGLISH) // <dc:date>2013-10-03T23:34:30Z</dc:date>
	};
	
	Date dateParser(String dateString)
	{
		Date returnDate = new Date();
		for (SimpleDateFormat format : dateFormats)
		{
			try
			{
				returnDate = format.parse(dateString);
				break;
			}
			catch (ParseException e)
			{
			}
		}

		return returnDate;
	}
	
	public Item()
	{
		super(null);
		_mediaContent = new ArrayList<MediaContent>();
		setPubDate(new Date());
	}

	public Item(Attributes attributes)
	{
		super(attributes);
		if (_pubDate == null) {
			setPubDate(new Date());
		}
		_mediaContent = new ArrayList<MediaContent>();
	}

	public Item(String guid, String title, String publicationTime, String source, String content, long feedId)
	{
		this();
		_guid = guid;
		_feedId = feedId;
		_title = title;
		_source = source;
		_description = content;
		setPubDate(publicationTime);
	}

	public Item(String guid, String title, Date publicationTime, String source, String content, long feedId)
	{
		this();
		_guid = guid;
		_feedId = feedId;
		_title = title;
		_source = source;
		_description = content;
		setPubDate(publicationTime);
	}

	public long getFeedId()
	{
		return this._feedId;
	}

	public void setFeedId(long _feedId)
	{
		this._feedId = _feedId;
	}
	
	public int getRemotePostId() {
		return this._remotePostId;
	}
	
	public void setRemotePostId(String _postId)
	{
		if (LOGGING) 
			Log.v(LOGTAG,"Pre Setting remotePostId:*" + _postId + "*");
		this._remotePostId = Integer.valueOf(_postId);

		if (LOGGING) 
			Log.v(LOGTAG,"Post Setting remotePostId:" + _remotePostId);
	}

	
	public void dbsetRemotePostId(int _postId)
	{
		if (LOGGING) 
			Log.v(LOGTAG,"Setting remotePostId:" + _postId);
		this._remotePostId = _postId;
	}
	

	public long getDatabaseId()
	{
		return _databaseId;
	}

	public void setDatabaseId(long _databaseId)
	{
		this._databaseId = _databaseId;
	}

	public String getContentEncoded()
	{
		return _contentEncoded;
	}

	private CharSequence getCleanContentEncoded()
	{
		HTMLToPlainTextFormatter formatter = new HTMLToPlainTextFormatter();
        return formatter.getPlainText(Jsoup.parse(getContentEncoded()));
	}
	
	private CharSequence getFormattedContentEncoded(HTMLToPlainTextFormatter formatter)
	{
		//HtmlToPlainText formatter = new HtmlToPlainText();
        return formatter.getPlainText(Jsoup.parse(getContentEncoded()));
	}

	public void setContentEncoded(String _contentEncoded)
	{
		this._contentEncoded = _contentEncoded;
	}

	private String getMainContent()
	{
		if (_contentEncoded != null && !_contentEncoded.isEmpty())
		{
			return getContentEncoded();
		}
		else if (_description != null)
		{
			return getDescription();
		}
		else
		{
			return null;
		}
	}
	
	public CharSequence getFormattedMainContent(HTMLToPlainTextFormatter formatter)
	{
		if (_contentEncoded != null && !_contentEncoded.isEmpty())
		{
			return getFormattedContentEncoded(formatter);
		}
		else if (_description != null)
		{
			return getCleanDescription();
		}
		else
		{
			return null;
		}		
	}
	
	public CharSequence getCleanMainContent()
	{
		if (_contentEncoded != null && !_contentEncoded.isEmpty())
		{
			return getCleanContentEncoded();
		}
		else if (_description != null)
		{
			return getCleanDescription();
		}
		else
		{
			return null;
		}
	}

	public boolean getFavorite()
	{
		return _favorite;
	}

	public void setFavorite(boolean _favorite)
	{
		this._favorite = _favorite;
	}
	
	public boolean getShared() {
		return _shared;
	}
	
	public void setShared(boolean _shared) {
		this._shared = _shared;
	}

	/**
	 * @return The title of the item
	 */
	public String getTitle()
	{
		return _title;
	}

	public String getCleanTitle()
	{
		return Jsoup.parse(_title).text();
	}

	/**
	 * @param The
	 *            title of the item to set
	 */
	public void setTitle(String title)
	{
		this._title = title;
	}

	/**
	 * @return The URL of the item
	 */
	public String getLink()
	{
		return _link;
	}

	/**
	 * @param The
	 *            URL of the item to set
	 */
	public void setLink(String link)
	{
		this._link = link;
	}

	/**
	 * @return The item synopsis
	 */
	public String getDescription()
	{
		return _description;
	}

	/**
	 * @return the description without any html tags.
	 */
	public String getCleanDescription()
	{
		try
		{
			return Jsoup.parse(_description).text();
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	/**
	 * @param The
	 *            item synopsis to set
	 */
	public void setDescription(String description)
	{
		this._description = description;
	}

	public Date getPublicationTime()
	{
		return getPubDate();
	}

	/**
	 * @return Indicates when the item was published
	 */
	public Date getPubDate()
	{
		return _pubDate;
	}

	/**
	 * @param Indicates
	 *            when the item was published
	 */
	public void setPubDate(Date pubDate)
	{
		this._pubDate = pubDate;
	}

	/**
	 * @param Indicates
	 *            when the item was published
	 */
	public void setPubDate(String pubDate)
	{
		this._pubDate = dateParser(pubDate);
	}

	/**
	 * @return the _guid
	 */
	public String getGuid()
	{
		if (_guid != null) {
			return _guid;
		} else if (_link != null) {
			return _link;
		} else {
			return null;
		}
	}

	/**
	 * @param _guid
	 *            the _guid to set
	 */
	public void setGuid(String guid)
	{
		this._guid = guid;
	}

	/**
	 * @return Email address of the author of the item
	 */
	public String getAuthor()
	{
		return _author;
	}

	public String getCleanAuthor()
	{
		try
		{
			return Jsoup.parse(_author).text();
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	/**
	 * @param Email
	 *            address of the author of the item to set
	 */
	public void setAuthor(String author)
	{
		this._author = author;
	}

	/**
	 * @return URL of a page for comments relating to the item
	 */
	public String getCommentsUrl()
	{
		return _commentsUrl;
	}

	/**
	 * @param URL
	 *            of a page for comments relating to the item to set
	 */
	public void setCommentsUrl(String commentsUrl)
	{
		if (LOGGING) 
			Log.v(LOGTAG, "Setting Comments URL: " + commentsUrl);

		this._commentsUrl = commentsUrl;
	}

	public String getCategory()
	{
		if (categories.size() > 0) {
			return categories.get(0);
		} else {
			return "";
		}
		//return _category;
	}
	
	public void setCategories(ArrayList<String> _categories) {
		categories = _categories;
	}
	
	public ArrayList<String> getCategories() {
		return categories;
	}
	
	public void addCategory(String _category) {
		categories.add(_category);
	}
	
	/**
	 * @param Category
	 *            of the item to set
	 */
	public void setCategory(String _category)
	{
		//this._category = _category;
		addCategory(_category);
	}

	/**
	 * @return The RSS channel that the item came from
	 */
	public String getSource()
	{
		return _source;
	}

	/**
	 * @param The
	 *            RSS channel that the item came from to set
	 */
	public void setSource(String source)
	{
		this._source = source;
	}

	/**
	 * @return the media content for this item
	 */
	/*
	 * public MediaContent getMediaContent(int pos) { return _mediaContent; }
	 */
	/**
	 * @param mc
	 *            the media content for this item
	 */
	/*
	 * public void setMediaContent(MediaContent mc) { _mediaContent = mc; }
	 */
	/**
	 * @return the media thumbnail for this item
	 */
	public MediaThumbnail getMediaThumbnail()
	{
		return _mediaThumbnail;
	}

	/**
	 * @param mc
	 *            the media thumbnail for this item
	 */
	public void setMediaThumbnail(MediaThumbnail mt)
	{
		_mediaThumbnail = mt;
	}

	public void setMediaContent(MediaContent mediaToAdd)
	{
		addMediaContent(mediaToAdd);
	}

	public void setMediaContent(ArrayList<MediaContent> newMediaContent)
	{
		_mediaContent = newMediaContent;
	}

	public MediaContent getMediaContent(int pos)
	{
		if (_mediaContent != null && _mediaContent.size() > pos)
		{
			return _mediaContent.get(pos);
		}
		else
		{
			return null;
		}
	}

	public void addMediaContent(MediaContent mediaToAdd)
	{
		if (_mediaContent == null)
			_mediaContent = new ArrayList<>(1);
		_mediaContent.add(mediaToAdd);
	}

	public int getNumberOfMediaContent()
	{
		if (_mediaContent != null)
			return _mediaContent.size();
		return 0;
	}

	public ArrayList<MediaContent> getMediaContent()
	{
		return _mediaContent;
	}

	public void addTag(String tag)
	{
		addCategory(tag);
	}

	public int getNumberOfTags()
	{
		return categories.size();
	}

	public ArrayList<String> getTags()
	{
		return categories;
	}

	@Override
	public String toString()
	{
		return (getTitle());
	}
	
	public void setViewCount(int viewCount) {
		_viewCount = viewCount;
	}
	
	public void incrementViewCount() {
		_viewCount++;
	}
	
	public int getViewCount() {
		return _viewCount;
	}
}