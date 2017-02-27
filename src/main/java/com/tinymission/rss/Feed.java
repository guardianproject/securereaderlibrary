package com.tinymission.rss;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.xml.sax.Attributes;

import android.util.Log;

/**
 * Encapsulation of a collection rss items.
 * 
 */
public class Feed extends FeedEntity
{
	public static final int DEFAULT_DATABASE_ID = -1;

	public static final boolean LOGGING = false;
	public static final String LOGTAG = "rss.Feed";

	static SimpleDateFormat[] dateFormats = { new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
			new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH), 
			new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH), 
			new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ssz", Locale.ENGLISH) 
	};
	// <dc:date>2013-10-03T23:34:30Z</dc:date>
    //<pubDate>Thu, 03 Oct 2013 23:34:30 GMT</pubDate>
    
	
	
	Date dateParser(String dateString)
	{
		Date returnDate = null;
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

	public Feed(Attributes attributes)
	{
		super(attributes);
		
		_mediaContent = new ArrayList<MediaContent>();
	}

	public Feed()
	{
		super(null);
	}

	// A feed coming from an input stream, unparsed so not for real usage other than handing to the parser
	private InputStream feedInputStream = null;
	public Feed(InputStream _feedInputStream) {
		super(null);
		feedInputStream = _feedInputStream;
	}
	public InputStream getInputStream() {
		return feedInputStream;
	}
	
	// Copy important bits from previously created feed
	public Feed(Feed originalFeed)
	{
		super(null);
		
		// Reset some values from original feed
		setTitle(originalFeed.getTitle());
		setDatabaseId(originalFeed.getDatabaseId());
		setFeedURL(originalFeed.getFeedURL());
		setSubscribed(originalFeed.isSubscribed());
	}

	public Feed(long _databaseId, String _title, String _url)
	{
		super(null);
		setDatabaseId(_databaseId);
		setTitle(_title);
		setFeedURL(_url);
	}

	public Feed(String _title, String _url)
	{
		this(DEFAULT_DATABASE_ID, _title, _url);
	}

	private String _title;
	private String _link;
	private String _description;
	private Date _pubDate;
	private Date _lastBuildDate;
	private String _language;
	private String _category;

	// These fields are ours, not in the RSS spec
	private String _feedURL;
	private long _databaseId = DEFAULT_DATABASE_ID;
	private Date _networkPullDate;
	private boolean _subscribed = false;
	
	public static int STATUS_NOT_SYNCED = 0;
	public static int STATUS_LAST_SYNC_GOOD = 1;
	public static int STATUS_LAST_SYNC_FAILED_404 = 2;
	public static int STATUS_LAST_SYNC_FAILED_UNKNOWN = 3;
	public static int STATUS_LAST_SYNC_FAILED_BAD_URL = 4;
	public static int STATUS_SYNC_IN_PROGRESS = 5;
	public static int STATUS_LAST_SYNC_PARSE_ERROR = 6;
	private int _status = 0;

	private ArrayList<MediaContent> _mediaContent;

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
		if (_mediaContent.size() > pos)
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
		if (_mediaContent == null) {
			_mediaContent = new ArrayList<MediaContent>();
		}
		
		if (mediaToAdd != null) {
			_mediaContent.add(mediaToAdd);	
		}
	}

	public int getNumberOfMediaContent()
	{
		return _mediaContent.size();
	}

	public ArrayList<MediaContent> getMediaContent()
	{
		return _mediaContent;
	}
	
	
	public boolean isSubscribed()
	{
		return _subscribed;
	}

	public void setSubscribed(boolean _subscribed)
	{
		this._subscribed = _subscribed;
	}

	public Date getNetworkPullDate()
	{
		return _networkPullDate;
	}

	public void setNetworkPullDate(String _networkPullDate)
	{
		this._networkPullDate = dateParser(_networkPullDate);
	}

	public void setNetworkPullDate(Date _networkPullDate)
	{
		this._networkPullDate = _networkPullDate;
	}

	public long getDatabaseId()
	{
		return _databaseId;
	}

	public void setDatabaseId(long _databaseId)
	{
		this._databaseId = _databaseId;
	}

	public String getFeedURL()
	{
		return _feedURL;
	}

	public void setFeedURL(String _feedURL)
	{
		this._feedURL = _feedURL;
	}

	public int getStatus() {
		return _status;
	}
	
	public void setStatus(int _status) {
		this._status = _status;
	}
	
	/**
	 * @return The title of the feed
	 */
	public String getTitle()
	{
		return _title;
	}

	/**
	 * @param The
	 *            title of the feed to set
	 */
	public void setTitle(String _title)
	{
		this._title = _title;
	}

	/**
	 * @return The URL of the feed
	 */
	public String getLink()
	{
		return _link;
	}

	/**
	 * @param The
	 *            URL of the feed to set
	 */
	public void setLink(String _link)
	{
		this._link = _link;
	}

	/**
	 * @return The feed synopsis
	 */
	public String getDescription()
	{
		return _description;
	}

	/**
	 * @param The
	 *            feed synopsis to set
	 */
	public void setDescription(String _description)
	{
		this._description = _description;
	}

	/**
	 * @return Indicates when the feed was published
	 */
	public Date getPubDate()
	{
		return _pubDate;
	}

	/**
	 * @param Indicates
	 *            when the feed was published
	 */
	public void setPubDate(Date _pubDate)
	{
		this._pubDate = _pubDate;
	}

	/**
	 * @param Indicates
	 *            when the feed was published
	 */
	public void setPubDate(String _pubDate)
	{
		this._pubDate = dateParser(_pubDate);
	}

	/**
	 * @return Indicates when the feed was built
	 */
	public Date getLastBuildDate()
	{
		return _lastBuildDate;
	}

	/**
	 * @param Indicates
	 *            when the feed was built
	 */
	public void setLastBuildDate(Date lastBuildDate)
	{
		this._lastBuildDate = _pubDate;
	}

	/**
	 * @param Indicates
	 *            when the feed was built
	 */
	public void setLastBuildDate(String lastBuildDate)
	{
		this._lastBuildDate = dateParser(lastBuildDate);
	}

	/**
	 * @return The feed language
	 */
	public String getLanguage()
	{
		return _language;
	}

	/**
	 * @param The
	 *            feed language
	 */
	public void setLanguage(String language)
	{
		this._language = language;
	}

	private final ArrayList<Item> _items = new ArrayList<Item>();

	public void clearItems()
	{
		_items.clear();
	}

	/**
	 * @return the items in the feed
	 */
	public ArrayList<Item> getItems()
	{
		return _items;
	}

	/**
	 * @param item
	 *            an item to add to the feed
	 */
	public void addItem(Item item)
	{
		_items.add(item);
	}

	/**
	 * @param items
	 *            add arraylist of items to a feed
	 */
	public void addItems(ArrayList<Item> items)
	{
		for (int i = 0; i < items.size(); i++)
		{
			_items.add(items.get(i));
		}
	}

	/**
	 * @return the number of items in the feed.
	 */
	public int getItemCount()
	{
		return _items.size();
	}

	/**
	 * @param position
	 *            the item index you wish to retrieve
	 * @return the item at position
	 */
	public Item getItem(int position)
	{
		return _items.get(position);
	}
	
	// For feeds of comments (which are essentially items)
	
	private final ArrayList<Comment> _comments = new ArrayList<Comment>();

	public void clearComments()
	{
		_comments.clear();
	}

	/**
	 * @return the items in the feed
	 */
	public ArrayList<Comment> getComments()
	{
		return _comments;
	}

	/**
	 * @param item
	 *            an item to add to the feed
	 */
	public void addComment(Comment comment)
	{
		_comments.add(comment);
	}

	/**
	 * @param items
	 *            add arraylist of items to a feed
	 */
	public void addComments(ArrayList<Comment> comments)
	{
		for (int i = 0; i < comments.size(); i++)
		{
			_comments.add(comments.get(i));
		}
	}

	/**
	 * @return the number of items in the feed.
	 */
	public int getCommentCount()
	{
		return _comments.size();
	}

	/**
	 * @param position
	 *            the item index you wish to retrieve
	 * @return the item at position
	 */
	public Comment getComment(int position)
	{
		return _comments.get(position);
	}

	/**
	 * @return The category of the feed
	 */
	public String getCategory()
	{
		return _category;
	}

	/**
	 * @param The category of the feed to set
	 */
	public void setCategory(String _category)
	{
		this._category = _category;
	}

}
