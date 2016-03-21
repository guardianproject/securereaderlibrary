package com.tinymission.rss;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.examples.HtmlToPlainText;
import org.xml.sax.Attributes;

import android.util.Log;

public class Comment extends FeedEntity implements Serializable
{
	public static final long serialVersionUID = 133702L;

	public static final String LOGTAG = "rss.comment";
	public static final boolean LOGGING = false;
	
	public static final int DEFAULT_DATABASE_ID = -1;

/*
 * <item>
<title>
Comment on Something by The Nightly One
</title>
<link>
http://securereader.guardianproject.info/wordpress/?p=1#comment-11
</link>
<dc:creator>
<![CDATA[ The Nightly One ]]>
</dc:creator>
<pubDate>Mon, 13 Jul 2015 20:15:19 +0000</pubDate>
<guid isPermaLink="false">
http://securereader.guardianproject.info/wordpress/?p=1#comment-11
</guid>
<description>
<![CDATA[ This is from the nightly one ]]>
</description>
<content:encoded>
<![CDATA[ <p>This is from the nightly one</p> ]]>
</content:encoded>
</item>
 * 
 */
	private long _databaseId = DEFAULT_DATABASE_ID;
	private long _itemId;

	private String _title;
	private String _link;
	private String _author;  // dc:creator
	private String _guid;
	private String _description;
	private Date _pubDate;
	private String _contentEncoded;
	
	static SimpleDateFormat[] dateFormats = { new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
		new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH), 
		new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH), 
		new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ssz", Locale.ENGLISH) 
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

	public Comment()
	{
		super(null);
		setPubDate(new Date());
	}

	public Comment(Attributes attributes)
	{
		super(attributes);
		if (_pubDate == null) {
			setPubDate(new Date());
		}
		if (LOGGING) 
			Log.v(LOGTAG,"New Comment from Network!");
	}

	public Comment(String guid, String title, String publicationTime, String description, long itemId)
	{
		this();
		_guid = guid;
		_title = title;
		_description = description;
		_itemId = itemId;
		setPubDate(publicationTime);
	}

	public Comment(String guid, String title, Date publicationTime, String description, long itemId)
	{
		this();
		_guid = guid;
		_title = title;
		_description = description;
		_itemId = itemId;
		setPubDate(publicationTime);
	}

	public long getItemId()
	{
		return this._itemId;
	}

	public void setItemId(long _itemId)
	{
		this._itemId = _itemId;
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

	private String getCleanContentEncoded()
	{
		HtmlToPlainText formatter = new HtmlToPlainText();
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

	public String getCleanMainContent()
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

	@Override
	public String toString()
	{
		return (getTitle());
	}
}
