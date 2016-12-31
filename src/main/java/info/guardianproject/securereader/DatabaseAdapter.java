package info.guardianproject.securereader;

import info.guardianproject.cacheword.CacheWordHandler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.tinymission.rss.Comment;
import com.tinymission.rss.Feed;
import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

public class DatabaseAdapter
{
	public static final boolean LOGGING = false;
	public static final String LOGTAG = "DatabaseAdapter";
	
	private final DatabaseHelper databaseHelper;

	private SQLiteDatabase db;

	public SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

	private final CacheWordHandler cacheword;

	public DatabaseAdapter(CacheWordHandler _cacheword, Context _context)
	{
		cacheword = _cacheword;
		SQLiteDatabase.loadLibs(_context);
		this.databaseHelper = new DatabaseHelper(cacheword, _context);
		open();
		//dumpDatabase();
	}

	public void close()
	{
		databaseHelper.close();
	}

	public void open() throws SQLException
	{
		db = databaseHelper.getWritableDatabase();
	}

	public boolean databaseReady()
	{
		if (db.isOpen())
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public long addFeed(Feed feed)
	{
		long returnValue = -1;

		try
		{
			ContentValues values = new ContentValues();

			values.put(DatabaseHelper.FEEDS_TABLE_TITLE, feed.getTitle());
			values.put(DatabaseHelper.FEEDS_TABLE_FEED_URL, feed.getFeedURL());
			values.put(DatabaseHelper.FEEDS_TABLE_LANGUAGE, feed.getLanguage());
			values.put(DatabaseHelper.FEEDS_TABLE_DESCRIPTION, feed.getDescription());
			values.put(DatabaseHelper.FEEDS_TABLE_LINK, feed.getLink());
			values.put(DatabaseHelper.FEEDS_TABLE_STATUS, feed.getStatus());
			
			if (feed.isSubscribed())
			{
				values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 1);
			}
			else
			{
				values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 0);
			}

			if (feed.getNetworkPullDate() != null)
			{
				values.put(DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE, dateFormat.format(feed.getNetworkPullDate()));
			}

			if (feed.getLastBuildDate() != null)
			{
				values.put(DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE, dateFormat.format(feed.getLastBuildDate()));
			}

			if (feed.getPubDate() != null)
			{
				values.put(DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE, dateFormat.format(feed.getPubDate()));
			}
			
			if (databaseReady())
				returnValue = db.insert(DatabaseHelper.FEEDS_TABLE, null, values);
			// close();
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}


		return returnValue;
	}

	public Feed getFeedById(long feedId) {
		Feed feed = new Feed();

		String query = "select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", "
				+ DatabaseHelper.FEEDS_TABLE_TITLE + ", "
				+ DatabaseHelper.FEEDS_TABLE_LINK + ", "
				+ DatabaseHelper.FEEDS_TABLE_DESCRIPTION + ", "
				+ DatabaseHelper.FEEDS_TABLE_FEED_URL
				+ " from " + DatabaseHelper.FEEDS_TABLE
				+ " where " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID
				+ " = ?;";

		if (LOGGING)
			Log.v(LOGTAG, query);

		if (databaseReady()) {
			Cursor queryCursor = db.rawQuery(query, new String[] {String.valueOf(feedId)});

			if (queryCursor.getCount() > 0)
			{
				if (queryCursor.moveToFirst()) {
					String title = queryCursor.getString(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_TITLE));
					String url = queryCursor.getString(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_FEED_URL));
					String link = queryCursor.getString(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LINK));
					String description = queryCursor.getString(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_DESCRIPTION));

					feed.setDatabaseId(feedId);
					feed.setTitle(title);
					feed.setFeedURL(url);
					feed.setDescription(description);
					feed.setLink(link);
				}
			}
			queryCursor.close();
		}
		return feed;
	}

	public String getFeedTitle(long feedId) {
		String title = "";
		
		String query = "select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.FEEDS_TABLE_TITLE + ", "
				+ DatabaseHelper.FEEDS_TABLE_FEED_URL + " from " + DatabaseHelper.FEEDS_TABLE + " where " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID
				+ " = ?;";
		
		if (LOGGING) 
			Log.v(LOGTAG, query);

		if (databaseReady()) {
			Cursor queryCursor = db.rawQuery(query, new String[] {String.valueOf(feedId)});

			if (queryCursor.getCount() > 0)
			{
				if (queryCursor.moveToFirst()) {
					title = queryCursor.getString(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_TITLE));
				}
			}
			queryCursor.close();
		}
		return title;
	}
	
	public long addOrUpdateFeed(Feed feed)
	{
		long returnValue = -1;

		try
		{
			if (feed.getDatabaseId() != Feed.DEFAULT_DATABASE_ID)
			{
				int columnsUpdated = updateFeed(feed);
				if (columnsUpdated == 1)
				{
					returnValue = feed.getDatabaseId();
				}
			}
			else
			{
				String query = "select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.FEEDS_TABLE_TITLE + ", "
						+ DatabaseHelper.FEEDS_TABLE_FEED_URL + " from " + DatabaseHelper.FEEDS_TABLE + " where " + DatabaseHelper.FEEDS_TABLE_FEED_URL
						+ " = ?;";

				if (LOGGING) 
					Log.v(LOGTAG, query);
				
				if (databaseReady()) {
					Cursor queryCursor = db.rawQuery(query, new String[] {feed.getFeedURL()});

					if (queryCursor.getCount() == 0)
					{
						returnValue = addFeed(feed);
					}
					else
					{
						queryCursor.moveToFirst();
						returnValue = queryCursor.getLong(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID));

						int columnsUpdated = updateFeed(feed);
						if (columnsUpdated == 1)
						{
							returnValue = feed.getDatabaseId();
						}
						else
						{
							returnValue = -1;
						}
					}
					
					if (LOGGING)
						Log.v(LOGTAG, "returnValue: " + returnValue);

					queryCursor.close();
				}
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}


		return returnValue;
	}

	public long addFeed(String title, String feedUrl)
	{
		long returnValue = -1;

		try
		{
			ContentValues values = new ContentValues();
			values.put(DatabaseHelper.FEEDS_TABLE_TITLE, title);
			values.put(DatabaseHelper.FEEDS_TABLE_FEED_URL, feedUrl);
			values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 1);
			values.put(DatabaseHelper.FEEDS_TABLE_STATUS, Feed.STATUS_NOT_SYNCED);
			
			if (databaseReady())
				returnValue = db.insert(DatabaseHelper.FEEDS_TABLE, null, values);
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}

		return returnValue;
	}

	public boolean isFeedUnfollowed(String feedUrl)
	{
		boolean returnValue = false;

		try
		{
			String query = "select " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + " from " + DatabaseHelper.FEEDS_TABLE + " where " + DatabaseHelper.FEEDS_TABLE_FEED_URL + " = ?;";

			if (LOGGING) 
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				Cursor queryCursor = db.rawQuery(query, new String[] {feedUrl});
	
				if (queryCursor.getCount() > 0)
				{
					queryCursor.moveToFirst();
					if (queryCursor.getInt(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED)) == 0)
						returnValue = true;
				}
	
				queryCursor.close();
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}


		return returnValue;

	}
	
	public long addFeedIfNotExisting(String title, String feedUrl)
	{
		long returnValue = -1;

		try
		{
			String query = "select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.FEEDS_TABLE_TITLE + ", "
					+ DatabaseHelper.FEEDS_TABLE_FEED_URL + " from " + DatabaseHelper.FEEDS_TABLE + " where " + DatabaseHelper.FEEDS_TABLE_FEED_URL + " = ?;";

			if (LOGGING) 
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				Cursor queryCursor = db.rawQuery(query, new String[] {feedUrl});
	
				if (queryCursor.getCount() == 0)
				{
					returnValue = addFeed(title, feedUrl);
				}
				else
				{
					queryCursor.moveToFirst();
					returnValue = queryCursor.getLong(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID));
				}
	
				queryCursor.close();
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}


		return returnValue;
	}

	public boolean deleteFeed(long feedDatabaseId)
	{
		boolean returnValue = false;

		try
		{
			deleteFeedItems(feedDatabaseId);

			if (databaseReady())
				returnValue = db.delete(DatabaseHelper.FEEDS_TABLE, DatabaseHelper.FEEDS_TABLE_COLUMN_ID + "=?", new String[]{String.valueOf(feedDatabaseId)}) > 0;
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}


		return returnValue;
	}

	public int updateFeed(Feed feed)
	{
		ContentValues values = new ContentValues();
		values.put(DatabaseHelper.FEEDS_TABLE_TITLE, feed.getTitle());
		values.put(DatabaseHelper.FEEDS_TABLE_FEED_URL, feed.getFeedURL());
		values.put(DatabaseHelper.FEEDS_TABLE_LANGUAGE, feed.getLanguage());
		values.put(DatabaseHelper.FEEDS_TABLE_DESCRIPTION, feed.getDescription());
		values.put(DatabaseHelper.FEEDS_TABLE_LINK, feed.getLink());
		values.put(DatabaseHelper.FEEDS_TABLE_STATUS, feed.getStatus());
		
		if (feed.isSubscribed())
		{
			values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 1);
		}
		else
		{
			values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 0);
		}

		if (feed.getNetworkPullDate() != null)
		{
			values.put(DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE, dateFormat.format(feed.getNetworkPullDate()));
		}

		if (feed.getLastBuildDate() != null)
		{
			values.put(DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE, dateFormat.format(feed.getLastBuildDate()));
		}

		if (feed.getPubDate() != null)
		{
			values.put(DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE, dateFormat.format(feed.getPubDate()));
		}

		int returnValue = -1;
		if (databaseReady())
			returnValue = db
				.update(DatabaseHelper.FEEDS_TABLE, values, DatabaseHelper.FEEDS_TABLE_COLUMN_ID + "=?", new String[] { String.valueOf(feed.getDatabaseId()) });

		return returnValue;
	}
	
	public void deleteExpiredItems(Date expirationDate) {
		Cursor queryCursor = null;
		
		String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", "
				+ DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " 
				+ DatabaseHelper.ITEMS_TABLE_FEED_ID + " from " + DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " < ?"
				+ " and " + DatabaseHelper.ITEMS_TABLE_FAVORITE + " != ? and " + DatabaseHelper.ITEMS_TABLE_SHARED + " != ? order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";

		if (LOGGING)
			Log.v(LOGTAG, query);
			
		if (databaseReady()) {
			try
			{
				queryCursor = db.rawQuery(query, new String[] {dateFormat.format(expirationDate), String.valueOf(1), String.valueOf(1)});
			
				if (LOGGING)
					Log.v(LOGTAG,"Count " + queryCursor.getCount());
	
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
	
				if (queryCursor.moveToFirst())
				{
					do
					{
						int id = queryCursor.getInt(idColumn);
						String title = queryCursor.getString(titleColumn);
						String publishDate = queryCursor.getString(publishDateColumn);
						
						if (LOGGING)
							Log.v(LOGTAG,"Going to delete " + id + " " + publishDate);
						this.deleteItem(id);
					}
					while (queryCursor.moveToNext());
				}
	
				queryCursor.close();
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			finally
			{
				if (queryCursor != null)
				{
					try
					{
						queryCursor.close();					
					}
					catch(Exception e) {
						if (LOGGING)
							e.printStackTrace();
					}
				}
			}
		}
	}
	
	

	public void deleteOverLimitItems(int limit) {
		Cursor queryCursor = null;
				
		String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", "
				+ DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " 
				+ DatabaseHelper.ITEMS_TABLE_FEED_ID + " from " + DatabaseHelper.ITEMS_TABLE + " where "
				+ DatabaseHelper.ITEMS_TABLE_COLUMN_ID + " NOT IN (select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + " from " + DatabaseHelper.ITEMS_TABLE + " order by " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + " DESC LIMIT ?) and "
				+ DatabaseHelper.ITEMS_TABLE_FAVORITE + " != ? and " + DatabaseHelper.ITEMS_TABLE_SHARED + " != ? order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";
		
		if (LOGGING)
			Log.v(LOGTAG, query);
			
		if (databaseReady()) {
			try
			{
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(limit), String.valueOf(1), String.valueOf(1)});
			
				if (LOGGING)
					Log.v(LOGTAG,"Count " + queryCursor.getCount());
	
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
	
				if (queryCursor.moveToFirst())
				{
					do
					{
						int id = queryCursor.getInt(idColumn);
						String title = queryCursor.getString(titleColumn);
						String publishDate = queryCursor.getString(publishDateColumn);
						
						if (LOGGING)
							Log.v(LOGTAG,"Going to delete " + id + " " + publishDate);
						this.deleteItem(id);
					}
					while (queryCursor.moveToNext());
				}
	
				queryCursor.close();
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			finally
			{
				if (queryCursor != null)
				{
					try
					{
						queryCursor.close();					
					}
					catch(Exception e) {
						if (LOGGING)
							e.printStackTrace();
					}
				}
			}
		}
	}
	
	public Feed fillFeedObject(Feed feed)
	{
		try
		{

			String query = "select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.FEEDS_TABLE_TITLE + ", "
					+ DatabaseHelper.FEEDS_TABLE_FEED_URL + ", " + DatabaseHelper.FEEDS_TABLE_DESCRIPTION + ", " + DatabaseHelper.FEEDS_TABLE_LANGUAGE + ", "
					+ DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE + ", " + DatabaseHelper.FEEDS_TABLE_LINK + ", " + DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE
					+ ", " + DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + ", " + DatabaseHelper.FEEDS_TABLE_STATUS + " from " + DatabaseHelper.FEEDS_TABLE
					+ " where " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + "=?;";

			if (LOGGING)
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				Cursor queryCursor = db.rawQuery(query, new String[] {String.valueOf(feed.getDatabaseId())});

				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_TITLE);
				int feedURLColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_FEED_URL);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_DESCRIPTION);
				int languageColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LANGUAGE);
				int lastBuildDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LINK);
				int networkPullDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE);
				int subscribedColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED);
				int statusColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_STATUS);
				
				if (queryCursor.moveToFirst())
				{
					int id = queryCursor.getInt(idColumn);
					feed.setDatabaseId(id);
	
					String title = queryCursor.getString(titleColumn);
					feed.setTitle(title);
	
					String feedUrl = queryCursor.getString(feedURLColumn);
					feed.setFeedURL(feedUrl);
	
					if (queryCursor.getString(descriptionColumn) != null)
					{
						feed.setDescription(queryCursor.getString(descriptionColumn));
					}
	
					if (queryCursor.getString(languageColumn) != null)
					{
						feed.setLanguage(queryCursor.getString(languageColumn));
					}
	
					if (queryCursor.getString(lastBuildDateColumn) != null)
					{
						feed.setLastBuildDate(queryCursor.getString(lastBuildDateColumn));
					}
	
					if (queryCursor.getString(networkPullDateColumn) != null)
					{
						feed.setNetworkPullDate(queryCursor.getString(networkPullDateColumn));
					}
	
					if (queryCursor.getString(linkColumn) != null)
					{
						feed.setLink(queryCursor.getString(linkColumn));
					}
	
					if (queryCursor.getString(publishDateColumn) != null)
					{
						feed.setPubDate(queryCursor.getString(publishDateColumn));
					}
	
					if (queryCursor.getInt(subscribedColumn) == 1)
					{
						feed.setSubscribed(true);
					}
					else
					{
						feed.setSubscribed(false);
					}
					
					feed.setStatus(queryCursor.getInt(statusColumn));
				}
	
				queryCursor.close();
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}

		return feed;
	}

	public ArrayList<Feed> getSubscribedFeeds()
	{
		return getAllFeeds(true,true);
		//return getAllFeeds();
	}

	public ArrayList<Feed> getUnSubscribedFeeds()
	{
		return getAllFeeds(true,false);
	}

	public Feed getSubscribedFeedItems(int numItems)
	{
		Cursor queryCursor = null;
		Feed feed = new Feed();
		
		try
		{
			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", " + DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED
					+ ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", "
					+ DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", "
					+ DatabaseHelper.FEEDS_TABLE_TITLE + ", " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + ", " + DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_COMMENTS_URL
					+ " from " + DatabaseHelper.ITEMS_TABLE + ", " + DatabaseHelper.FEEDS_TABLE
					+ " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID
					+ " and " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + " = ?"
					+ " order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " DESC"
					+ " limit " + numItems + ";";
	
			if (LOGGING)
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(1)});
	
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
				int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
				int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
				int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
				int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
				int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
				int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int feedIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
				int remotePostIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID);
				int commentsUrlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL);
				
				int feedTableFeedIdColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID);
				int feedTableFeedTitle = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_TITLE);
				int feedTableFeedSubscribe = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED);
						
	
				if (queryCursor.moveToFirst())
				{
					do
					{
						int id = queryCursor.getInt(idColumn);
						String description = queryCursor.getString(descriptionColumn);
						String contentEncoded = queryCursor.getString(contentEncodedColumn);
						String title = queryCursor.getString(titleColumn);
						long feedId = queryCursor.getLong(feedIdColumn);
						String publishDate = queryCursor.getString(publishDateColumn);
						String guid = queryCursor.getString(guidColumn);
	
						String author = queryCursor.getString(authorColumn);
						String category = queryCursor.getString(categoryColumn);
						int favorite = queryCursor.getInt(favoriteColumn);
						int shared = queryCursor.getInt(sharedColumn);
						String link = queryCursor.getString(linkColumn);
						String commentsUrl = queryCursor.getString(commentsUrlColumn);
						
						String feedTitle = queryCursor.getString(feedTableFeedTitle);
						int remotePostId = queryCursor.getInt(remotePostIdColumn);
	
						Item item = new Item(guid, title, publishDate, feedTitle, description, feedId);
						item.setDatabaseId(id);
						item.setAuthor(author);
						item.setCategory(category);
						item.setContentEncoded(contentEncoded);
						item.dbsetRemotePostId(remotePostId);
						item.setCommentsUrl(commentsUrl);
						if (favorite == 1)
						{
							item.setFavorite(true);
						}
						else
						{
							item.setFavorite(false);
						}
						if (shared == 1) {
							item.setShared(true);
						} else {
							item.setShared(false);
						}
						
						item.setGuid(guid);
						item.setLink(link);
	
						feed.addItem(item);
						
						if (LOGGING)
							Log.v(LOGTAG, "Added " + item.getFeedId() + " " + item.getDatabaseId() + " " + item.getTitle() + " " + item.getPubDate());
					}
					while (queryCursor.moveToNext());
				}
	
				queryCursor.close();
				
				for(Item item : feed.getItems())      
				    item.setMediaContent(getItemMedia(item));
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return feed;		
	}	
	
	public ArrayList<Feed> getAllFeeds()
	{
		return getAllFeeds(false,false);
	}

	public ArrayList<Feed> getAllFeeds(boolean checkSubscribed, boolean subscribed)
	{
		ArrayList<Feed> feeds = new ArrayList<Feed>();

		try
		{
			StringBuilder query = new StringBuilder("select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.FEEDS_TABLE_TITLE + ", "
					+ DatabaseHelper.FEEDS_TABLE_FEED_URL + ", " + DatabaseHelper.FEEDS_TABLE_DESCRIPTION + ", " + DatabaseHelper.FEEDS_TABLE_LANGUAGE + ", "
					+ DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE + ", " + DatabaseHelper.FEEDS_TABLE_LINK + ", " + DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE
					+ ", " + DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + ", " + DatabaseHelper.FEEDS_TABLE_STATUS + " from " + DatabaseHelper.FEEDS_TABLE);

			if (checkSubscribed)
			{
				query.append(" where " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + " = ?");
			}

			query.append(";");

			if (LOGGING)
				Log.v(LOGTAG, query.toString());

			if (databaseReady()) {
				Cursor queryCursor = null;
				if (checkSubscribed && subscribed) {
					queryCursor = db.rawQuery(query.toString(), new String[] {String.valueOf(1)});
				} else if (checkSubscribed && !subscribed) {
					queryCursor = db.rawQuery(query.toString(), new String[] {String.valueOf(0)}); 
				} else {
					queryCursor = db.rawQuery(query.toString(), new String[] {});
				}
					
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_TITLE);
				int feedURLColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_FEED_URL);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_DESCRIPTION);
				int languageColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LANGUAGE);
				int lastBuildDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LINK);
				int networkPullDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE);
				int subscribedColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED);
				int statusColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_STATUS);
	
				if (queryCursor.moveToFirst())
				{
					do
					{
						int id = queryCursor.getInt(idColumn);
						String title = queryCursor.getString(titleColumn);
						String feedUrl = queryCursor.getString(feedURLColumn);
	
						Feed tempFeed = new Feed(id, title, feedUrl);
	
						if (queryCursor.getString(descriptionColumn) != null)
						{
							tempFeed.setDescription(queryCursor.getString(descriptionColumn));
						}
	
						if (queryCursor.getString(languageColumn) != null)
						{
							tempFeed.setLanguage(queryCursor.getString(languageColumn));
						}
	
						if (queryCursor.getString(lastBuildDateColumn) != null)
						{
							tempFeed.setLastBuildDate(queryCursor.getString(lastBuildDateColumn));
						}
	
						if (queryCursor.getString(networkPullDateColumn) != null)
						{
							tempFeed.setNetworkPullDate(queryCursor.getString(networkPullDateColumn));
						}
	
						if (queryCursor.getString(linkColumn) != null)
						{
							tempFeed.setLink(queryCursor.getString(linkColumn));
						}
	
						if (queryCursor.getString(publishDateColumn) != null)
						{
							tempFeed.setPubDate(queryCursor.getString(publishDateColumn));
						}
	
						if (queryCursor.getInt(subscribedColumn) == 1)
						{
							tempFeed.setSubscribed(true);
						}
						else
						{
							tempFeed.setSubscribed(false);
						}
						
						tempFeed.setStatus(queryCursor.getInt(statusColumn));
	
						feeds.add(tempFeed);
					}
					while (queryCursor.moveToNext());
	
				}
	
				queryCursor.close();
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}

		return feeds;
	}

	public void deleteFeedItems(long feedDatabaseId)
	{
		ArrayList<Item> feedItems = getFeedItems(feedDatabaseId, -1);
		for (Item item : feedItems)
		{
			deleteItem(item.getDatabaseId());
		}
	}

	public boolean deleteItem(long itemDatabaseId)
	{
		deleteItemMedia(itemDatabaseId);
		deleteItemTags(itemDatabaseId);
		deleteItemComments(itemDatabaseId);
		
		boolean returnValue = false;

		try
		{
			if (databaseReady())
				returnValue = db.delete(DatabaseHelper.ITEMS_TABLE, DatabaseHelper.ITEMS_TABLE_COLUMN_ID + "=?", new String[]{String.valueOf(itemDatabaseId)}) > 0;
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}

		return returnValue;
	}

	public int updateItem(Item item)
	{
		int returnValue = -1;

		try
		{

			// private final ArrayList<Comment> _comments;
			// private final ArrayList<MediaContent> _mediaContent;
			// private final ArrayList<String> _tags;

			// Should this be here??
			// private MediaThumbnail _mediaThumbnail;

			ContentValues values = new ContentValues();
			values.put(DatabaseHelper.ITEMS_TABLE_AUTHOR, item.getAuthor());
			values.put(DatabaseHelper.ITEMS_TABLE_CATEGORY, item.getCategory());
			values.put(DatabaseHelper.ITEMS_TABLE_DESCRIPTION, item.getDescription());
			values.put(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED, item.getContentEncoded());
			if (item.getFavorite()) {
				values.put(DatabaseHelper.ITEMS_TABLE_FAVORITE, 1);
			} else {
				values.put(DatabaseHelper.ITEMS_TABLE_FAVORITE, 0);
			}
			
			if (item.getShared()) {
				values.put(DatabaseHelper.ITEMS_TABLE_SHARED, 1);	
			} else {
				values.put(DatabaseHelper.ITEMS_TABLE_SHARED, 0);
			}			
			
			values.put(DatabaseHelper.ITEMS_TABLE_GUID, item.getGuid());
			values.put(DatabaseHelper.ITEMS_TABLE_LINK, item.getLink());
			values.put(DatabaseHelper.ITEMS_TABLE_SOURCE, item.getSource());
			values.put(DatabaseHelper.ITEMS_TABLE_TITLE, item.getTitle());
			values.put(DatabaseHelper.ITEMS_TABLE_FEED_ID, item.getFeedId());
			values.put(DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID, item.getRemotePostId());
			values.put(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL, item.getCommentsUrl());

			if (item.getPubDate() != null)
			{
				values.put(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE, dateFormat.format(item.getPubDate()));
			}
			
			values.put(DatabaseHelper.ITEMS_TABLE_VIEWCOUNT, item.getViewCount());

			if (databaseReady())
				returnValue = db
					.update(DatabaseHelper.ITEMS_TABLE, values, DatabaseHelper.ITEMS_TABLE_COLUMN_ID + "=?", new String[] { String.valueOf(item.getDatabaseId()) });
						
			addOrUpdateItemMedia(item, item.getMediaContent());
			addOrUpdateItemTags(item);
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}

		return returnValue;
	}

	public Feed getAllFavoriteItems() {
		Cursor queryCursor = null;
		Feed feed = new Feed();
		
		try
		{
			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", " + DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED
					+ ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", "
					+ DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_COMMENTS_URL
					+ " from " + DatabaseHelper.ITEMS_TABLE + " where "
					+ DatabaseHelper.ITEMS_TABLE_FAVORITE + " = ? order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";

			if (LOGGING)
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(1)});

				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
				int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
				int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
				int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
				int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
				int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
				int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
				int remotePostIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID);
				int commentsUrlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL);
				
				if (queryCursor.moveToFirst())
				{
					do
					{
						int id = queryCursor.getInt(idColumn);
						String description = queryCursor.getString(descriptionColumn);
						String contentEncoded = queryCursor.getString(contentEncodedColumn);
						String title = queryCursor.getString(titleColumn);
						long feedId = queryCursor.getLong(feedIdColunn);
						String publishDate = queryCursor.getString(publishDateColumn);
						String guid = queryCursor.getString(guidColumn);
	
						String author = queryCursor.getString(authorColumn);
						String category = queryCursor.getString(categoryColumn);
						int favorite = queryCursor.getInt(favoriteColumn);
						int shared = queryCursor.getInt(sharedColumn);
						String link = queryCursor.getString(linkColumn);
						String commentsUrl = queryCursor.getString(commentsUrlColumn);
						int remotePostId = queryCursor.getInt(remotePostIdColumn);
						
						Item item = new Item(guid, title, publishDate, getFeedTitle(feedId), description, feedId);
						item.setDatabaseId(id);
						item.setAuthor(author);
						item.setCategory(category);
						item.setContentEncoded(contentEncoded);
						if (favorite == 1)
						{
							item.setFavorite(true);
						}
						else
						{
							item.setFavorite(false);
						}
						if (shared == 1) {
							item.setShared(true);
						} else {
							item.setShared(false);
						}
						
						item.setGuid(guid);
						item.setLink(link);
						item.setCommentsUrl(commentsUrl);
						
						item.dbsetRemotePostId(remotePostId);
	
						feed.addItem(item);
					}
					while (queryCursor.moveToNext());
				}
	
				queryCursor.close();
				
				for(Item item : feed.getItems())      
				    item.setMediaContent(getItemMedia(item));
			}
			
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return feed;		
	}	
	
	public Feed getFavoriteFeedItems(Feed feed)
	{
		Cursor queryCursor = null;

		try
		{
			feed.clearItems();

			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", " + DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED
					+ ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", "
					+ DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_COMMENTS_URL + 
					" from " + DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = ?"
					+ " and " + DatabaseHelper.ITEMS_TABLE_FAVORITE + " = ? order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";

			if (LOGGING)
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(feed.getDatabaseId()), String.valueOf(1)});

				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
				int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
				int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
				int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
				int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
				int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
				int remotePostIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID);
				int commentsUrlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL);
				
				if (queryCursor.moveToFirst())
				{
					do
					{
						int id = queryCursor.getInt(idColumn);
						String description = queryCursor.getString(descriptionColumn);
						String contentEncoded = queryCursor.getString(contentEncodedColumn);
						String title = queryCursor.getString(titleColumn);
						long feedId = queryCursor.getLong(feedIdColunn);
						String publishDate = queryCursor.getString(publishDateColumn);
						String guid = queryCursor.getString(guidColumn);
	
						String author = queryCursor.getString(authorColumn);
						String category = queryCursor.getString(categoryColumn);
						int favorite = queryCursor.getInt(favoriteColumn);
						String link = queryCursor.getString(linkColumn);
						int remotePostId = queryCursor.getInt(remotePostIdColumn);
						String commentsUrl = queryCursor.getString(commentsUrlColumn);
						
						Item item = new Item(guid, title, publishDate, feed.getTitle(), description, feed.getDatabaseId());
						item.setDatabaseId(id);
						item.setAuthor(author);
						item.setCategory(category);
						item.setContentEncoded(contentEncoded);
						if (favorite == 1)
						{
							item.setFavorite(true);
						}
						else
						{
							item.setFavorite(false);
						}
						item.setGuid(guid);
						item.setLink(link);
						item.dbsetRemotePostId(remotePostId);
						item.setCommentsUrl(commentsUrl);
	
						feed.addItem(item);
					}
					while (queryCursor.moveToNext());
				}
	
				queryCursor.close();
	
				for(Item item : feed.getItems())      
				    item.setMediaContent(getItemMedia(item));
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}		

		return feed;

	}
	
	public Feed getAllSharedItems() {
		Cursor queryCursor = null;
		Feed feed = new Feed();
		
		try
		{
			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", " + DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED
					+ ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", "
					+ DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID + ", " 
					+ DatabaseHelper.ITEMS_TABLE_COMMENTS_URL 
					+ " from " + DatabaseHelper.ITEMS_TABLE + " where "
					+ DatabaseHelper.ITEMS_TABLE_SHARED + " = ? order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";

			if (LOGGING)
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(1)});

				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
				int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
				int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
				int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
				int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
				int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
				int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
				int remotePostIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID);
				int commentsUrlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL);
				
				if (queryCursor.moveToFirst())
				{
					do
					{
						int id = queryCursor.getInt(idColumn);
						String description = queryCursor.getString(descriptionColumn);
						String contentEncoded = queryCursor.getString(contentEncodedColumn);
						String title = queryCursor.getString(titleColumn);
						long feedId = queryCursor.getLong(feedIdColunn);
						String publishDate = queryCursor.getString(publishDateColumn);
						String guid = queryCursor.getString(guidColumn);
	
						String author = queryCursor.getString(authorColumn);
						String category = queryCursor.getString(categoryColumn);
						int favorite = queryCursor.getInt(favoriteColumn);
						int shared = queryCursor.getInt(sharedColumn);
						String link = queryCursor.getString(linkColumn);
						int remotePostId = queryCursor.getInt(remotePostIdColumn);
						String commentsUrl = queryCursor.getString(commentsUrlColumn);
						
						Item item = new Item(guid, title, publishDate, feed.getTitle(), description, feed.getDatabaseId());
						item.setDatabaseId(id);
						item.setAuthor(author);
						item.setCategory(category);
						item.setContentEncoded(contentEncoded);
						if (favorite == 1)
						{
							item.setFavorite(true);
						}
						else
						{
							item.setFavorite(false);
						}
						if (shared == 1) {
							item.setShared(true);
						} else {
							item.setShared(false);
						}
						
						item.setGuid(guid);
						item.setLink(link);
						item.dbsetRemotePostId(remotePostId);
						item.setCommentsUrl(commentsUrl);
						
						feed.addItem(item);
					}
					while (queryCursor.moveToNext());
				}
	
				queryCursor.close();
				
				for(Item item : feed.getItems())      
				    item.setMediaContent(getItemMedia(item));
			}
			
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return feed;		
	}

	public Feed getSharedFeedItems(Feed feed)
	{
		Cursor queryCursor = null;
		
		try
		{
			feed.clearItems();

			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", " + DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED
					+ ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", "
					+ DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_COMMENTS_URL 
					+ " from " + DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = ?"
					+ " and " + DatabaseHelper.ITEMS_TABLE_SHARED + " = ? order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";

			if (LOGGING)
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(feed.getDatabaseId()), String.valueOf(1)});

				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
				int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
				int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
				int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
				int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
				int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
				int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
				int remotePostIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID);
				int commentsUrlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL);
				
				
				if (queryCursor.moveToFirst())
				{
					do
					{
						int id = queryCursor.getInt(idColumn);
						String description = queryCursor.getString(descriptionColumn);
						String contentEncoded = queryCursor.getString(contentEncodedColumn);
						String title = queryCursor.getString(titleColumn);
						long feedId = queryCursor.getLong(feedIdColunn);
						String publishDate = queryCursor.getString(publishDateColumn);
						String guid = queryCursor.getString(guidColumn);
	
						String author = queryCursor.getString(authorColumn);
						String category = queryCursor.getString(categoryColumn);
						int favorite = queryCursor.getInt(favoriteColumn);
						int shared = queryCursor.getInt(sharedColumn);
						String link = queryCursor.getString(linkColumn);
						int remotePostId = queryCursor.getInt(remotePostIdColumn);
						String commentsUrl = queryCursor.getString(commentsUrlColumn);
						
						Item item = new Item(guid, title, publishDate, feed.getTitle(), description, feed.getDatabaseId());
						item.setDatabaseId(id);
						item.setAuthor(author);
						item.setCategory(category);
						item.setContentEncoded(contentEncoded);
						if (favorite == 1)
						{
							item.setFavorite(true);
						}
						else
						{
							item.setFavorite(false);
						}
						if (shared == 1) {
							item.setShared(true);
						} else {
							item.setShared(false);
						}
						
						item.setGuid(guid);
						item.setLink(link);
						item.dbsetRemotePostId(remotePostId);
						item.setCommentsUrl(commentsUrl);
						
						feed.addItem(item);
					}
					while (queryCursor.moveToNext());
				}
	
				queryCursor.close();
				
				for(Item item : feed.getItems())      
				    item.setMediaContent(getItemMedia(item));
			}
			
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return feed;

	}	
	
	/*
	 * This returns an arraylist of items given a feed id It creates a temp feed
	 * with that ID to use the below method to do the query
	 */
	public ArrayList<Item> getFeedItems(long feedId, int numItems)
	{
		Feed tempFeed = new Feed();
		tempFeed.setDatabaseId(feedId);
		tempFeed = getFeedItems(tempFeed, numItems);
		ArrayList<Item> items = tempFeed.getItems();
		return items;
	}

	public Item getItemById(long itemId) {
		Item returnItem = null;
		Cursor queryCursor = null;
		
		try
		{
			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", "
					+ DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED + ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", " 
					+ DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", " + DatabaseHelper.ITEMS_TABLE_SOURCE + ", " 
					+ DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", "
					+ DatabaseHelper.ITEMS_TABLE_VIEWCOUNT + ", " + DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_COMMENTS_URL
					+ " from " + DatabaseHelper.ITEMS_TABLE
					+ " where " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + " = ?;";
			
			if (LOGGING)	
				Log.v(LOGTAG,query);
			
			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(itemId)});
	
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
				int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
				int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
				int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
				int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
				int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
				int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
				int viewCountColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_VIEWCOUNT);
				int remotePostIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID);
				int commentsUrlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL);
				
				if (queryCursor.moveToFirst())
				{
					int id = queryCursor.getInt(idColumn);
					String description = queryCursor.getString(descriptionColumn);
					String contentEncoded = queryCursor.getString(contentEncodedColumn);
					String title = queryCursor.getString(titleColumn);
					long feedId = queryCursor.getLong(feedIdColunn);
					String publishDate = queryCursor.getString(publishDateColumn);
					String guid = queryCursor.getString(guidColumn);
		
					String author = queryCursor.getString(authorColumn);
					String category = queryCursor.getString(categoryColumn);
					int favorite = queryCursor.getInt(favoriteColumn);
					int shared = queryCursor.getInt(sharedColumn);
					int viewCount = queryCursor.getInt(viewCountColumn);
					
					String source = queryCursor.getString(sourceColumn);
					String link = queryCursor.getString(linkColumn);
					String commentsUrl = queryCursor.getString(commentsUrlColumn);
					
					int remotePostId = queryCursor.getInt(remotePostIdColumn);
					
					returnItem = new Item(guid, title, publishDate, source, description, feedId);
					returnItem.setDatabaseId(id);
					returnItem.setAuthor(author);
					returnItem.setCategory(category);
					returnItem.setContentEncoded(contentEncoded);
		
					if (favorite == 1)
					{
						returnItem.setFavorite(true);
					}
					else
					{
						returnItem.setFavorite(false);
					}
					if (shared == 1) {
						returnItem.setShared(true);
					} else {
						returnItem.setShared(false);
					}
					returnItem.setViewCount(viewCount);
					returnItem.setGuid(guid);
					returnItem.setLink(link);
					returnItem.setCommentsUrl(commentsUrl);
					returnItem.dbsetRemotePostId(remotePostId);

					returnItem.setMediaContent(this.getItemMedia(returnItem));
					returnItem.setCategories(getItemTags(returnItem));
					
				}
				queryCursor.close();
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return returnItem;
	}
	
	public Feed getFeedItems(Feed feed, int numItems)
	{
		Cursor queryCursor = null;
		
		try
		{
			feed.clearItems();
			String query = "";
			if (numItems > 0)
			{
				query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
						+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", "
						+ DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED + ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " 
						+ DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_GUID
						+ ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", " + DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", "
						+ DatabaseHelper.ITEMS_TABLE_FEED_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", "
						+ DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID + ", " + DatabaseHelper.ITEMS_TABLE_COMMENTS_URL
						+ " from " + DatabaseHelper.ITEMS_TABLE
						+ " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = ? order by "
						+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " DESC LIMIT " + numItems + ";";
			}
			else
			{
				query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
						+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", "
						+ DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED + ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " 
						+ DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_GUID
						+ ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", " + DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", "
						+ DatabaseHelper.ITEMS_TABLE_FEED_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE 
						+ ", " + DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID + ", " + DatabaseHelper.ITEMS_TABLE_COMMENTS_URL
						+ " from " + DatabaseHelper.ITEMS_TABLE
						+ " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = ? order by "
						+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " DESC;";
			}
			
			if (LOGGING)
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] { String.valueOf(feed.getDatabaseId())});

				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
				int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
				int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
				int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
				int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
				int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
				int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
				int remotePostIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID);
				int commentsUrlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL);
	
				if (queryCursor.moveToFirst())
				{
					do
					{
						int id = queryCursor.getInt(idColumn);
						String description = queryCursor.getString(descriptionColumn);
						String contentEncoded = queryCursor.getString(contentEncodedColumn);
						String title = queryCursor.getString(titleColumn);
						long feedId = queryCursor.getLong(feedIdColunn);
						String publishDate = queryCursor.getString(publishDateColumn);
						String guid = queryCursor.getString(guidColumn);
	
						String author = queryCursor.getString(authorColumn);
						String category = queryCursor.getString(categoryColumn);
						int favorite = queryCursor.getInt(favoriteColumn);
						int shared = queryCursor.getInt(sharedColumn);
	
						String link = queryCursor.getString(linkColumn);
						String commentsUrl = queryCursor.getString(commentsUrlColumn);
						int remotePostId = queryCursor.getInt(remotePostIdColumn);
						
						Item item = new Item(guid, title, publishDate, feed.getTitle(), description, feed.getDatabaseId());
						item.setDatabaseId(id);
						item.setAuthor(author);
						item.setCategory(category);
						item.setContentEncoded(contentEncoded);
						if (favorite == 1)
						{
							item.setFavorite(true);
						}
						else
						{
							item.setFavorite(false);
						}
						
						if (shared == 1) {
							item.setShared(true);
						} else {
							item.setShared(false);
						}
	
						item.setGuid(guid);
						item.setLink(link);
						item.setCommentsUrl(commentsUrl);
						item.dbsetRemotePostId(remotePostId);
	
						feed.addItem(item);
					}
					while (queryCursor.moveToNext());
					
					queryCursor.close();
					
					for(Item item : feed.getItems()) {
					    item.setMediaContent(getItemMedia(item));
					    item.setCategories(getItemTags(item));
					}
				}
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}

		return feed;
	}

	public long addItemWithLimit(Item item, int limit) {
		long returnValue = addItem(item);

		deleteOverLimitItems(limit);
			
		return returnValue;
	}
	
	public long addItem(Item item)
	{
		long returnValue = -1;

		try
		{
			ContentValues values = new ContentValues();
			values.put(DatabaseHelper.ITEMS_TABLE_AUTHOR, item.getAuthor());
			values.put(DatabaseHelper.ITEMS_TABLE_TITLE, item.getTitle());
			values.put(DatabaseHelper.ITEMS_TABLE_FEED_ID, item.getFeedId());
			values.put(DatabaseHelper.ITEMS_TABLE_CATEGORY, item.getCategory());
			values.put(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL, item.getCommentsUrl());
			values.put(DatabaseHelper.ITEMS_TABLE_DESCRIPTION, item.getDescription());
			values.put(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED, item.getContentEncoded());
			values.put(DatabaseHelper.ITEMS_TABLE_GUID, item.getGuid());
			values.put(DatabaseHelper.ITEMS_TABLE_LINK, item.getLink());
			values.put(DatabaseHelper.ITEMS_TABLE_SOURCE, item.getSource());
			values.put(DatabaseHelper.ITEMS_TABLE_SHARED, item.getShared());
			values.put(DatabaseHelper.ITEMS_TABLE_FAVORITE, item.getFavorite());
			values.put(DatabaseHelper.ITEMS_TABLE_REMOTE_POST_ID, item.getRemotePostId());

			if (item.getPubDate() != null)
			{
				values.put(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE, dateFormat.format(item.getPubDate()));
			}

			values.put(DatabaseHelper.ITEMS_TABLE_VIEWCOUNT, item.getViewCount());
			
			if (databaseReady()) {
				returnValue = db.insert(DatabaseHelper.ITEMS_TABLE, null, values);
	
				item.setDatabaseId(returnValue);
	
				this.addOrUpdateItemMedia(item, item.getMediaContent());
				addOrUpdateItemTags(item);
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		return returnValue;
	}

	public long addOrUpdateItem(Item item, int limit)
	{
		long returnValue = -1;
		Cursor queryCursor = null;
		
		try
		{
			if (item.getDatabaseId() == Item.DEFAULT_DATABASE_ID)
			{
				String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", "
						+ DatabaseHelper.ITEMS_TABLE_FEED_ID + " from " + DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEMS_TABLE_GUID + " = ?"
					    + " and " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = ?;";

				if (LOGGING)
					Log.v(LOGTAG, query);

				if (databaseReady()) {
					queryCursor = db.rawQuery(query, new String[] { item.getGuid(), String.valueOf(item.getFeedId()) });
	
					if (LOGGING)
						Log.v(LOGTAG, "Got " + queryCursor.getCount() + " results");
	
					if (queryCursor.getCount() == 0)
					{
						returnValue = addItem(item);
						
						if (limit != -1) {
							this.deleteOverLimitItems(limit);
						}
					}
					else
					{
						queryCursor.moveToFirst();
	
						returnValue = queryCursor.getLong(queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID));
	
						item.setDatabaseId(returnValue);
						int columnCount = updateItem(item);
						if (columnCount != 1)
						{
							returnValue = -1;
						}
	
					}
					queryCursor.close();
				}
			}
			else
			{
				int columnCount = updateItem(item);

				if (columnCount != 1)
				{
					returnValue = -1;
				}
				else
				{
					returnValue = item.getDatabaseId();
				}
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}

		return returnValue;
	}

	public ArrayList<MediaContent> getItemMedia(Item item)
	{
		ArrayList<MediaContent> mediaContent = new ArrayList<MediaContent>();
		
		//Log.v(LOGTAG,"Skipping getItemMedia");
		if (LOGGING)
			Log.v(LOGTAG,"getItemMedia");
		
		String query = "select " + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEM_MEDIA_ITEM_ID + ", "
				+ DatabaseHelper.ITEM_MEDIA_URL + ", " + DatabaseHelper.ITEM_MEDIA_TYPE + ", " + DatabaseHelper.ITEM_MEDIA_MEDIUM + ", "
				+ DatabaseHelper.ITEM_MEDIA_HEIGHT + ", " + DatabaseHelper.ITEM_MEDIA_WIDTH + ", " + DatabaseHelper.ITEM_MEDIA_FILESIZE + ", "
				+ DatabaseHelper.ITEM_MEDIA_DURATION + ", " + DatabaseHelper.ITEM_MEDIA_DEFAULT + ", " + DatabaseHelper.ITEM_MEDIA_EXPRESSION + ", "
				+ DatabaseHelper.ITEM_MEDIA_BITRATE + ", " + DatabaseHelper.ITEM_MEDIA_FRAMERATE + ", " + DatabaseHelper.ITEM_MEDIA_LANG + ", "
				+ DatabaseHelper.ITEM_MEDIA_DOWNLOADED + ", " + DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE
				+ " from " + DatabaseHelper.ITEM_MEDIA_TABLE + " where " + DatabaseHelper.ITEM_MEDIA_ITEM_ID + " = "
				+ "?;";

		if (LOGGING)
			Log.v(LOGTAG, query);

		Cursor queryCursor = null;
		
		if (databaseReady()) {
			
			try
			{
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(item.getDatabaseId())});
	
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID);
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_ITEM_ID);
				int urlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_URL);
				int typeColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TYPE);
				int mediumColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_MEDIUM);
				int heightColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_HEIGHT);
				int widthColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_WIDTH);
				int filesizeColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_FILESIZE);
				int durationColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_DURATION);
				int defaultColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_DEFAULT);
				int expressionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_EXPRESSION);
				int bitrateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_BITRATE);
				int framerateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_FRAMERATE);
				int langColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_LANG);
				int samplerateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE);
				int itemMediaDownloadedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_DOWNLOADED);
				
				if (queryCursor.moveToFirst())
				{
					do {
						long id = queryCursor.getLong(idColumn);
						long itemId = queryCursor.getLong(itemIdColumn);
						String url = queryCursor.getString(urlColumn);
						String type = queryCursor.getString(typeColumn);
						String medium = queryCursor.getString(mediumColumn);
						int height = queryCursor.getInt(heightColumn);
						int width = queryCursor.getInt(widthColumn);
						int filesize = queryCursor.getInt(filesizeColumn);
						int duration = queryCursor.getInt(durationColumn);
						boolean isDefault = false;
						if (queryCursor.getInt(defaultColumn) == 1)
						{
							isDefault = true;
						}
						String expression = queryCursor.getString(expressionColumn);
						int bitrate = queryCursor.getInt(bitrateColumn);
						int framerate = queryCursor.getInt(framerateColumn);
						String lang = queryCursor.getString(langColumn);
						String samplerate = queryCursor.getString(samplerateColumn);
		
						if (LOGGING)
							Log.v(LOGTAG,"new MediaContent");
						
						MediaContent mc = new MediaContent(itemId, url, type);
						mc.setDatabaseId(id);
						mc.setMedium(medium);
						mc.setHeight(height);
						mc.setWidth(width);
						mc.setFileSize(filesize);
						mc.setDuration(duration);
						mc.setIsDefault(isDefault);
						mc.setExpression(expression);
						mc.setBitrate(bitrate);
						mc.setFramerate(framerate);
						mc.setLang(lang);
						mc.setSampligRate(samplerate);
						
						boolean downloaded = false;
						if (queryCursor.getInt(itemMediaDownloadedColumn) == 1)
						{
							downloaded = true;
						}	
						mc.setDownloaded(downloaded);
						
						mediaContent.add(mc);
					} while (queryCursor.moveToNext());
				}
	
				queryCursor.close();
	
				if (LOGGING)
					Log.v(LOGTAG, "There is " + mediaContent.size() + " media for the item");
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			finally
			{
				if (queryCursor != null)
				{
					try
					{
						queryCursor.close();					
					}
					catch(Exception e) {
						if (LOGGING) 
							e.printStackTrace();
					}
				}
			}
		}
		return mediaContent;
	}
	
	public MediaContent getMediaContentById(int mediaContentId) {
		
		MediaContent returnMC = null;
		
		if (LOGGING)
			Log.v(LOGTAG,"getMediaContentById");
		
		String query = "select " + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEM_MEDIA_ITEM_ID + ", "
				+ DatabaseHelper.ITEM_MEDIA_URL + ", " + DatabaseHelper.ITEM_MEDIA_TYPE + ", " + DatabaseHelper.ITEM_MEDIA_MEDIUM + ", "
				+ DatabaseHelper.ITEM_MEDIA_HEIGHT + ", " + DatabaseHelper.ITEM_MEDIA_WIDTH + ", " + DatabaseHelper.ITEM_MEDIA_FILESIZE + ", "
				+ DatabaseHelper.ITEM_MEDIA_DURATION + ", " + DatabaseHelper.ITEM_MEDIA_DEFAULT + ", " + DatabaseHelper.ITEM_MEDIA_EXPRESSION + ", "
				+ DatabaseHelper.ITEM_MEDIA_BITRATE + ", " + DatabaseHelper.ITEM_MEDIA_FRAMERATE + ", " + DatabaseHelper.ITEM_MEDIA_LANG + ", "
				+ DatabaseHelper.ITEM_MEDIA_DOWNLOADED + ", " + DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE
				+ " from " + DatabaseHelper.ITEM_MEDIA_TABLE + " where " + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + " = "
				+ "?;";

		if (LOGGING)
			Log.v(LOGTAG, query);

		Cursor queryCursor = null;
		
		if (databaseReady()) {
			
			try
			{
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(mediaContentId)});
	
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID);
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_ITEM_ID);
				int urlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_URL);
				int typeColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TYPE);
				int mediumColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_MEDIUM);
				int heightColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_HEIGHT);
				int widthColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_WIDTH);
				int filesizeColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_FILESIZE);
				int durationColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_DURATION);
				int defaultColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_DEFAULT);
				int expressionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_EXPRESSION);
				int bitrateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_BITRATE);
				int framerateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_FRAMERATE);
				int langColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_LANG);
				int samplerateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE);
				int itemMediaDownloadedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_DOWNLOADED);
				
				if (queryCursor.moveToFirst())
				{
					long id = queryCursor.getLong(idColumn);
					long itemId = queryCursor.getLong(itemIdColumn);
					String url = queryCursor.getString(urlColumn);
					String type = queryCursor.getString(typeColumn);
					String medium = queryCursor.getString(mediumColumn);
					int height = queryCursor.getInt(heightColumn);
					int width = queryCursor.getInt(widthColumn);
					int filesize = queryCursor.getInt(filesizeColumn);
					int duration = queryCursor.getInt(durationColumn);
					boolean isDefault = false;
					if (queryCursor.getInt(defaultColumn) == 1)
					{
						isDefault = true;
					}
					String expression = queryCursor.getString(expressionColumn);
					int bitrate = queryCursor.getInt(bitrateColumn);
					int framerate = queryCursor.getInt(framerateColumn);
					String lang = queryCursor.getString(langColumn);
					String samplerate = queryCursor.getString(samplerateColumn);
	
					if (LOGGING)
						Log.v(LOGTAG,"new MediaContent");
					
					MediaContent mc = new MediaContent(itemId, url, type);
					mc.setDatabaseId(id);
					mc.setMedium(medium);
					mc.setHeight(height);
					mc.setWidth(width);
					mc.setFileSize(filesize);
					mc.setDuration(duration);
					mc.setIsDefault(isDefault);
					mc.setExpression(expression);
					mc.setBitrate(bitrate);
					mc.setFramerate(framerate);
					mc.setLang(lang);
					mc.setSampligRate(samplerate);
					
					boolean downloaded = false;
					if (queryCursor.getInt(itemMediaDownloadedColumn) == 1)
					{
						downloaded = true;
					}	
					mc.setDownloaded(downloaded);
					
					returnMC  = mc;
				}
	
				queryCursor.close();
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			finally
			{
				if (queryCursor != null)
				{
					try
					{
						queryCursor.close();					
					}
					catch(Exception e) {
						if (LOGGING)
							e.printStackTrace();
					}
				}
			}
		}
		return returnMC;		
	}

	public void addOrUpdateItemMedia(Item item, ArrayList<MediaContent> itemMediaList)
	{
		if (LOGGING)
			Log.v(LOGTAG,"addOrUpdateItemMedia");
		
		for (MediaContent itemMedia : itemMediaList)
		{
			addOrUpdateItemMedia(item, itemMedia);
			if (LOGGING)
				Log.v(LOGTAG,"itemMedia added or updated: " + itemMedia.getDatabaseId());
		}
		
		// Delete stale entries (do this after addOrUpdateItemMedia above, to ensure all have valid id:s)
		deleteOldItemMedia(item, itemMediaList);
	}

	public long addOrUpdateItemMedia(Item item, MediaContent itemMedia)
	{
		long returnValue = -1;
		// Check that we have a valid url
		if (itemMedia == null || itemMedia.getUrl() == null) {
			return returnValue; // Abort
		}
		if (itemMedia.getDatabaseId() == MediaContent.DEFAULT_DATABASE_ID)
		{
			String query = "select " + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEM_MEDIA_URL + ", "
					+ DatabaseHelper.ITEM_MEDIA_ITEM_ID + " from " + DatabaseHelper.ITEM_MEDIA_TABLE + " where " + DatabaseHelper.ITEM_MEDIA_URL + " =? and " 
					+ DatabaseHelper.ITEM_MEDIA_ITEM_ID + " =?;";

			if (LOGGING)
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				Cursor queryCursor = db.rawQuery(query, new String[] {itemMedia.getUrl(), String.valueOf(item.getDatabaseId())});
	
				if (queryCursor.getCount() == 0)
				{
					queryCursor.close();
					
					if (LOGGING)
						Log.v(LOGTAG,"Default database id and nothing related there so creating new");
				
					ContentValues values = new ContentValues();
					values.put(DatabaseHelper.ITEM_MEDIA_ITEM_ID, item.getDatabaseId());
					values.put(DatabaseHelper.ITEM_MEDIA_URL, itemMedia.getUrl());
					values.put(DatabaseHelper.ITEM_MEDIA_TYPE, itemMedia.getType());
					values.put(DatabaseHelper.ITEM_MEDIA_MEDIUM, itemMedia.getMedium());
					values.put(DatabaseHelper.ITEM_MEDIA_HEIGHT, itemMedia.getHeight());
					values.put(DatabaseHelper.ITEM_MEDIA_WIDTH, itemMedia.getWidth());
					values.put(DatabaseHelper.ITEM_MEDIA_FILESIZE, itemMedia.getFileSize());
					values.put(DatabaseHelper.ITEM_MEDIA_DURATION, itemMedia.getDuration());
					values.put(DatabaseHelper.ITEM_MEDIA_DEFAULT, itemMedia.getIsDefault());
					values.put(DatabaseHelper.ITEM_MEDIA_EXPRESSION, itemMedia.getExpression());
					values.put(DatabaseHelper.ITEM_MEDIA_BITRATE, itemMedia.getBitrate());
					values.put(DatabaseHelper.ITEM_MEDIA_FRAMERATE, itemMedia.getFramerate());
					values.put(DatabaseHelper.ITEM_MEDIA_LANG, itemMedia.getLang());
					values.put(DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE, itemMedia.getSampligRate());
			
					if (itemMedia.getDownloaded()) {
						values.put(DatabaseHelper.ITEM_MEDIA_DOWNLOADED, 1);
					} else {
						values.put(DatabaseHelper.ITEM_MEDIA_DOWNLOADED, 0);
					}
					
					try
					{
						returnValue = db.insert(DatabaseHelper.ITEM_MEDIA_TABLE, null, values);
						itemMedia.setDatabaseId(returnValue);
						if (LOGGING)
							Log.v(LOGTAG,"Created itemMedia: " + itemMedia.getDatabaseId());
			
						//if (LOGGING) 
						//Log.v(LOGTAG, "Added Item Media Content: " + returnValue + " item id: " + item.getDatabaseId());
					}
					catch (SQLException e)
					{
						if (LOGGING)
							e.printStackTrace();
					}
					catch(IllegalStateException e)
					{
						if (LOGGING)
							e.printStackTrace();
					}

				} else {
					// else, it is already in the database, let's update the database id
	
					int databaseIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID);
			
					if (queryCursor.moveToFirst())
					{
						long databaseId = queryCursor.getLong(databaseIdColumn);					
						itemMedia.setDatabaseId(databaseId);
						returnValue = databaseId;
						
					} else {
						if (LOGGING) 
							Log.e(LOGTAG,"Couldn't move to first row");
					}
	
					queryCursor.close();
					
				}
			}
			
		} else {
			int columnsUpdated = updateItemMedia(itemMedia);
			if (columnsUpdated == 1)
			{
				returnValue = itemMedia.getDatabaseId();
			}		
		}
		return returnValue;
	}

	public void addOrUpdateItemComments(Item item, ArrayList<Comment> itemCommentList)
	{
		if (LOGGING)
			Log.v(LOGTAG,"addOrUpdateItemComments adding " + itemCommentList.size());
		
		for (Comment itemComment : itemCommentList)
		{
			addOrUpdateItemComment(item, itemComment);
			
			if (LOGGING)
				Log.v(LOGTAG,"item comment added or updated: " + itemComment.getDatabaseId());
		}
	}

	public long addOrUpdateItemComment(Item item, Comment itemComment)
	{
		if (LOGGING)
			Log.v(LOGTAG, "addOrUpdateItemComment");
		
		long returnValue = -1;
		if (itemComment.getDatabaseId() == Comment.DEFAULT_DATABASE_ID)
		{
			String query = "select " + DatabaseHelper.COMMENTS_TABLE_COLUMN_ID + ", "
					+ DatabaseHelper.COMMENTS_TABLE_ITEM_ID + " from " + DatabaseHelper.COMMENTS_TABLE + " where " + DatabaseHelper.COMMENTS_TABLE_GUID + " =?;";

			if (LOGGING)
				Log.v(LOGTAG, query);

			if (databaseReady()) {
				Cursor queryCursor = db.rawQuery(query, new String[] {itemComment.getGuid()});
	
				if (queryCursor.getCount() == 0)
				{
					queryCursor.close();
					
					if (LOGGING)
						Log.v(LOGTAG,"Default database id and nothing related there so creating new");
				
					ContentValues values = new ContentValues();
					values.put(DatabaseHelper.COMMENTS_TABLE_ITEM_ID, item.getDatabaseId());
					values.put(DatabaseHelper.COMMENTS_TABLE_TITLE, itemComment.getTitle());
					values.put(DatabaseHelper.COMMENTS_TABLE_LINK, itemComment.getLink());
					values.put(DatabaseHelper.COMMENTS_TABLE_AUTHOR, itemComment.getAuthor());
					values.put(DatabaseHelper.COMMENTS_TABLE_DESCRIPTION, itemComment.getDescription());
					values.put(DatabaseHelper.COMMENTS_TABLE_CONTENT_ENCODED, itemComment.getContentEncoded());
					values.put(DatabaseHelper.COMMENTS_TABLE_GUID, itemComment.getGuid());
					
					if (item.getPubDate() != null)
					{
						values.put(DatabaseHelper.COMMENTS_TABLE_PUBLISH_DATE, dateFormat.format(itemComment.getPubDate()));
					}					
					
					try
					{
						returnValue = db.insert(DatabaseHelper.COMMENTS_TABLE, null, values);
						itemComment.setDatabaseId(returnValue);
						if (LOGGING)
							Log.v(LOGTAG,"Created comments: " + itemComment.getDatabaseId());
			
					}
					catch (SQLException e)
					{
						if (LOGGING)
							e.printStackTrace();
					}
					catch(IllegalStateException e)
					{
						if (LOGGING)
							e.printStackTrace();
					}

				} else {
					// else, it is already in the database, let's update the database id
	
					int databaseIdColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_COLUMN_ID);
			
					if (queryCursor.moveToFirst())
					{
						long databaseId = queryCursor.getLong(databaseIdColumn);					
						itemComment.setDatabaseId(databaseId);
						returnValue = databaseId;
						
					} else {
						if (LOGGING) 
							Log.e(LOGTAG,"Couldn't move to first row");
					}
	
					queryCursor.close();
					
				}
			}
			
		} else {
			int columnsUpdated = updateItemComment(item, itemComment);
			if (columnsUpdated == 1)
			{
				returnValue = itemComment.getDatabaseId();
			}		
		}
		return returnValue;
	}	
	
	public int updateItemComment(Item item, Comment itemComment)
	{
		int returnValue = -1;
		
		ContentValues values = new ContentValues();
		values.put(DatabaseHelper.COMMENTS_TABLE_ITEM_ID, item.getDatabaseId());
		values.put(DatabaseHelper.COMMENTS_TABLE_TITLE, itemComment.getTitle());
		values.put(DatabaseHelper.COMMENTS_TABLE_LINK, itemComment.getLink());
		values.put(DatabaseHelper.COMMENTS_TABLE_AUTHOR, itemComment.getAuthor());
		values.put(DatabaseHelper.COMMENTS_TABLE_DESCRIPTION, itemComment.getDescription());
		values.put(DatabaseHelper.COMMENTS_TABLE_CONTENT_ENCODED, itemComment.getContentEncoded());
		values.put(DatabaseHelper.COMMENTS_TABLE_GUID, itemComment.getGuid());

		if (item.getPubDate() != null)
		{
			values.put(DatabaseHelper.COMMENTS_TABLE_PUBLISH_DATE, dateFormat.format(itemComment.getPubDate()));
		}					

		
		if (databaseReady()) {
			try
			{
				returnValue = db.update(DatabaseHelper.COMMENTS_TABLE, values, DatabaseHelper.COMMENTS_TABLE_COLUMN_ID + "=?", new String[] { String.valueOf(itemComment.getDatabaseId()) });
				if (LOGGING)
					Log.v(LOGTAG, "updateItemComment database query returnValue: " + returnValue);
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}	
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}

		}
		return returnValue;
	}	
	
	public long addOrUpdateSetting(String key, String value) {
		int returnValue = -1;

		ContentValues values = new ContentValues();
		values.put(DatabaseHelper.SETTINGS_TABLE_KEY, key);
		values.put(DatabaseHelper.SETTINGS_TABLE_VALUE, value);
			
		if (getSettingValue(key) != null) {
			// Update
			if (databaseReady()) {
				int rowsAffected = db.update(DatabaseHelper.SETTINGS_TABLE, values, DatabaseHelper.SETTINGS_TABLE_KEY + "=?", new String[] {key});
				if (rowsAffected == 1) {
					returnValue = 1;
				}
			}
			
			if (LOGGING)
				Log.v(LOGTAG,"update " + key + " " + value + " result:" + returnValue);
		}
		else {
			// Insert
			if (databaseReady()) {
				long id = db.insert(DatabaseHelper.SETTINGS_TABLE, null, values);
				if (id > -1) {
					returnValue = 1;
				}
			}
			if (LOGGING)
				Log.v(LOGTAG,"insert " + key + " " + value + " result:" + returnValue);
			
		}
		
		return returnValue;		
	}
	
	public ArrayList<Comment> getItemComments(Item item)
	{
		ArrayList<Comment> itemComments = new ArrayList<Comment>();
		
		if (LOGGING)
			Log.v(LOGTAG,"getItemComments");
				
		String query = "select " + DatabaseHelper.COMMENTS_TABLE_COLUMN_ID + ", " + DatabaseHelper.COMMENTS_TABLE_ITEM_ID + ", "
				+ DatabaseHelper.COMMENTS_TABLE_TITLE + ", " + DatabaseHelper.COMMENTS_TABLE_LINK + ", " + DatabaseHelper.COMMENTS_TABLE_AUTHOR + ", "
				+ DatabaseHelper.COMMENTS_TABLE_DESCRIPTION + ", " + DatabaseHelper.COMMENTS_TABLE_CONTENT_ENCODED + ", " + DatabaseHelper.COMMENTS_TABLE_GUID + ", "
				+ DatabaseHelper.COMMENTS_TABLE_PUBLISH_DATE
				+ " from " + DatabaseHelper.COMMENTS_TABLE + " where " + DatabaseHelper.COMMENTS_TABLE_ITEM_ID + " = "
				+ "?;";

		if (LOGGING)
			Log.v(LOGTAG, query);

		Cursor queryCursor = null;
		
		if (databaseReady()) {
			
			try
			{
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(item.getDatabaseId())});
					
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_COLUMN_ID);
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_ITEM_ID);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_TITLE);
				int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_LINK);
				int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_AUTHOR);
				int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_DESCRIPTION);
				int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_CONTENT_ENCODED);
				int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_GUID);
				int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.COMMENTS_TABLE_PUBLISH_DATE);
				
				if (queryCursor.moveToFirst())
				{
					do {
						long id = queryCursor.getLong(idColumn);
						long itemId = queryCursor.getLong(itemIdColumn);
						String title = queryCursor.getString(titleColumn);
						String link =  queryCursor.getString(linkColumn);
						String author =  queryCursor.getString(authorColumn);
						String description =  queryCursor.getString(descriptionColumn);
						String contentEncoded = queryCursor.getString(contentEncodedColumn);
						String guid = queryCursor.getString(guidColumn);
						String publishDate = queryCursor.getString(publishDateColumn);
						
						if (LOGGING)
							Log.v(LOGTAG,"new Item Comment");

						Comment c = new Comment(guid, title, publishDate, description, itemId);
						c.setDatabaseId(id);
						c.setLink(link);
						c.setAuthor(author);
						c.setContentEncoded(contentEncoded);

						itemComments.add(c);
						
					} while (queryCursor.moveToNext());
				}
	
				queryCursor.close();
	
				if (LOGGING)
					Log.v(LOGTAG, "There are " + itemComments.size() + " comments for the item");
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			finally
			{
				if (queryCursor != null)
				{
					try
					{
						queryCursor.close();					
					}
					catch(Exception e) {
						if (LOGGING)
							e.printStackTrace();
					}
				}
			}
		}
		return itemComments;
	}	
	
	public String getSettingValue(String key) {
		
		String returnValue = null;
		Cursor queryCursor = null;
		
		try {
			
			String query = "select " + DatabaseHelper.SETTINGS_TABLE_ID + ", " + DatabaseHelper.SETTINGS_TABLE_KEY + ", "
					+ DatabaseHelper.SETTINGS_TABLE_VALUE + " from " + DatabaseHelper.SETTINGS_TABLE + " where " + DatabaseHelper.SETTINGS_TABLE_KEY + " =? LIMIT 1;";
			
			if (LOGGING)
				Log.v(LOGTAG,query);
			
			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] {key});
			
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.SETTINGS_TABLE_ID);
				int keyColumn = queryCursor.getColumnIndex(DatabaseHelper.SETTINGS_TABLE_KEY);
				int valueColumn = queryCursor.getColumnIndex(DatabaseHelper.SETTINGS_TABLE_VALUE);
		
				if (queryCursor.moveToFirst())
				{
					long returnId = queryCursor.getLong(idColumn);
					String returnKey = queryCursor.getString(keyColumn);
					returnValue = queryCursor.getString(valueColumn);
					
					if (LOGGING) {
						Log.v(LOGTAG,"returnValue: " + returnValue);
						Log.v(LOGTAG,"returnid: " + returnId);
						Log.v(LOGTAG,"returnKey: " + returnKey);
					}
					
				} else {
					if (LOGGING)
						Log.v(LOGTAG,"Couldn't move to first");
				}
			
				queryCursor.close();	
			}
		} catch (SQLException e) {
			if (LOGGING)
				e.printStackTrace();
		} 	
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}

		return returnValue;
	}
	
	public int updateItemMedia(MediaContent itemMedia) {
		int returnValue = -1;
		
		ContentValues values = new ContentValues();
		values.put(DatabaseHelper.ITEM_MEDIA_ITEM_ID, itemMedia.getItemDatabaseId());
		values.put(DatabaseHelper.ITEM_MEDIA_URL, itemMedia.getUrl());
		values.put(DatabaseHelper.ITEM_MEDIA_TYPE, itemMedia.getType());
		values.put(DatabaseHelper.ITEM_MEDIA_MEDIUM, itemMedia.getMedium());
		values.put(DatabaseHelper.ITEM_MEDIA_HEIGHT, itemMedia.getHeight());
		values.put(DatabaseHelper.ITEM_MEDIA_WIDTH, itemMedia.getWidth());
		values.put(DatabaseHelper.ITEM_MEDIA_FILESIZE, itemMedia.getFileSize());
		values.put(DatabaseHelper.ITEM_MEDIA_DURATION, itemMedia.getDuration());
		values.put(DatabaseHelper.ITEM_MEDIA_DEFAULT, itemMedia.getIsDefault());
		values.put(DatabaseHelper.ITEM_MEDIA_EXPRESSION, itemMedia.getExpression());
		values.put(DatabaseHelper.ITEM_MEDIA_BITRATE, itemMedia.getBitrate());
		values.put(DatabaseHelper.ITEM_MEDIA_FRAMERATE, itemMedia.getFramerate());
		values.put(DatabaseHelper.ITEM_MEDIA_LANG, itemMedia.getLang());
		values.put(DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE, itemMedia.getSampligRate());

		if (itemMedia.getDownloaded()) {
			if (LOGGING) 
				Log.v(LOGTAG, "itemMedia Downlaoded is true");
			values.put(DatabaseHelper.ITEM_MEDIA_DOWNLOADED, 1);
		} else {
			if (LOGGING)
				Log.v(LOGTAG, "itemMedia Downlaoded is false");
			values.put(DatabaseHelper.ITEM_MEDIA_DOWNLOADED, 0);
		}
		
		if (databaseReady()) {
			try
			{
				returnValue = db.update(DatabaseHelper.ITEM_MEDIA_TABLE, values, DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + "=?", new String[] { String.valueOf(itemMedia.getDatabaseId()) });
				if (LOGGING)
					Log.v(LOGTAG, "updateItemMedia database query returnValue: " + returnValue);
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}	
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}

		}
		return returnValue;
	}	
	
	public ArrayList<Item> getFeedItemsWithMediaTags(Feed feed, ArrayList<String> tags, String mediaMimeType, boolean randomize, int limit) {
		
		// If no specific feed is given we search through all subscribed ones!
		ArrayList<Feed> feeds;
		if (feed != null)
		{
			feeds = new ArrayList<Feed>();
			feeds.add(feed);
		}
		else
		{
			feeds = getSubscribedFeeds();
		}
		
		ArrayList<String> requiredTags = new ArrayList<String>();
		ArrayList<String> ignoredTags = new ArrayList<String>();
		for (String tag : tags)
		{
			if (tag.startsWith("!"))
				ignoredTags.add(tag.substring(1));
			else
				requiredTags.add(tag);
		}
		
		ArrayList<Item> items = new ArrayList<Item>();
		items.addAll(getFeedItemsWithMediaTagsInternal(feeds, ignoredTags, mediaMimeType));
		if (items.size() == 0)
			items.addAll(getFallbackItems(feeds, ignoredTags, mediaMimeType));
		return items;
	}

	public ArrayList<Item> getFeedItemsWithMediaTagsInternal(ArrayList<Feed> feeds, ArrayList<String> ignoredTags, String mediaMimeType) {
		ArrayList<Item> items = new ArrayList<Item>();
		
		Cursor queryCursor = null;

		try {

			StringBuilder feedsArray = new StringBuilder();
			if (feeds != null)
			{
				for (int a = 0; a < feeds.size(); a++) 
				{
					if (feedsArray.length() != 0)
						feedsArray.append(",");
					feedsArray.append("'" + feeds.get(a).getDatabaseId() + "'");
				}
			}
			
			String ignoredTagsSubquery = "";
			if (ignoredTags != null && ignoredTags.size() > 0)
			{
				StringBuilder s = new StringBuilder();
				for (int i = 0; i < ignoredTags.size(); i++) 
				{
					if (s.length() != 0)
						s.append(",");
					s.append("'" + ignoredTags.get(i) + "'");
				}				
				ignoredTagsSubquery = s.toString();
			}
			
			if (databaseReady()) {
				ArrayList<String> queryParams = new ArrayList<String>();
				
				db.execSQL("CREATE TEMP TABLE IF NOT EXISTS filtered_items AS"
						+ " SELECT * FROM items WHERE item_id NOT IN (SELECT item_id FROM item_tags WHERE tag IN ("+ ignoredTagsSubquery + "));");
				db.execSQL(" CREATE TEMP TABLE IF NOT EXISTS playable_items AS"
						+ " SELECT * FROM"
						+ " (SELECT i.* FROM filtered_items i JOIN item_media m ON i.item_id = m.item_media_item_id WHERE"
						+ " i.item_feed_id IN (" + feedsArray.toString() + ") AND" 
						+ " m.item_media_downloaded = 1 AND"
						+ " i.item_favorite = 0 AND"
						+ " m.item_media_type LIKE \"%" + mediaMimeType + "%\" ORDER BY RANDOM())"
						+ " ORDER BY item_viewcount ASC;");
				db.execSQL(" CREATE TEMP TABLE IF NOT EXISTS playable_favs AS"
						+ " SELECT * FROM items WHERE item_favorite = 1 ORDER BY RANDOM() LIMIT MAX(1, 1 + (SELECT COUNT(*) FROM playable_items) / 10);");
				db.execSQL(" CREATE TEMP TABLE IF NOT EXISTS playlist AS"
						+ " SELECT * FROM (SELECT *, 2 * rowid AS 'sorting' FROM playable_items) UNION SELECT * FROM (SELECT *, 1 + (2 * ABS(RANDOM() % (SELECT 1 + COUNT(*) FROM playable_items))) FROM playable_favs);");
				
				queryCursor = db.rawQuery("SELECT * FROM playlist ORDER BY sorting, RANDOM();", queryParams.toArray(new String[0]));
				
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID);
		
				if (LOGGING)
					Log.v(LOGTAG,"Got " + queryCursor.getCount() + " items");
				
				if (queryCursor.moveToFirst())
				{
					do {
						int itemId = queryCursor.getInt(itemIdColumn);
						Item item = this.getItemById(itemId);
						items.add(item);
					} while (queryCursor.moveToNext());
						
				} else {
					if (LOGGING)
						Log.v(LOGTAG,"Couldn't move to first");
				}
			
				queryCursor.close();
				
				db.execSQL("DROP TABLE IF EXISTS playlist");
				db.execSQL("DROP TABLE IF EXISTS playable_favs");
				db.execSQL("DROP TABLE IF EXISTS playable_items");
				db.execSQL("DROP TABLE IF EXISTS filtered_items");
			}
		} catch (SQLException e) {
			if (LOGGING) 
				e.printStackTrace();
		} 	
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return items;		
	}
	
	public ArrayList<Item> getFallbackItems(ArrayList<Feed> feeds, ArrayList<String> ignoredTags, String mediaMimeType) {
		ArrayList<Item> items = new ArrayList<Item>();
		
		Cursor queryCursor = null;

		try {

			StringBuilder feedsArray = new StringBuilder();
			if (feeds != null)
			{
				for (int a = 0; a < feeds.size(); a++) 
				{
					if (feedsArray.length() != 0)
						feedsArray.append(",");
					feedsArray.append("'" + feeds.get(a).getDatabaseId() + "'");
				}
			}
			
			String ignoredTagsSubquery = "";
			if (ignoredTags != null && ignoredTags.size() > 0)
			{
				StringBuilder s = new StringBuilder();
				for (int i = 0; i < ignoredTags.size(); i++) 
				{
					if (s.length() != 0)
						s.append(",");
					s.append("'" + ignoredTags.get(i) + "'");
				}				
				ignoredTagsSubquery = s.toString();
			}
			
			if (databaseReady()) {	
				ArrayList<String> queryParams = new ArrayList<String>();
				
				db.execSQL("CREATE TEMP TABLE IF NOT EXISTS filtered_items AS"
						+ " SELECT * FROM items WHERE item_id NOT IN (SELECT item_id FROM item_tags WHERE tag IN ("+ ignoredTagsSubquery + "));");
				
				queryCursor = db.rawQuery("SELECT * FROM"
						+ " (SELECT i.*,m.item_media_downloaded AS 'sorting' FROM filtered_items i JOIN item_media m ON i.item_id = m.item_media_item_id WHERE"
						+ " m.item_media_downloaded = 1 AND"
						+ " m.item_media_type LIKE \"%" + mediaMimeType + "%\" ORDER BY RANDOM() LIMIT 1)"
						+ " UNION"
						+ " SELECT * FROM"
						+ " (SELECT i.*, m.item_media_downloaded AS 'sorting' FROM filtered_items i JOIN item_media m ON i.item_id = m.item_media_item_id WHERE"
						+ " i.item_feed_id IN ("+ feedsArray.toString() +") AND" 
						+ " m.item_media_downloaded = 0 AND"
						+ " m.item_media_type LIKE \"%" + mediaMimeType + "%\" ORDER BY RANDOM() LIMIT 1)"
						+ " ORDER BY sorting DESC"
						+ " LIMIT 1;", queryParams.toArray(new String[0]));
				
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID);
		
				if (LOGGING)
					Log.v(LOGTAG,"Got " + queryCursor.getCount() + " items");
				
				if (queryCursor.moveToFirst())
				{
					do {
						int itemId = queryCursor.getInt(itemIdColumn);
						Item item = this.getItemById(itemId);
						items.add(item);
					} while (queryCursor.moveToNext());
						
				} else {
					if (LOGGING)
						Log.v(LOGTAG,"Couldn't move to first");
				}
			
				queryCursor.close();
				
				db.execSQL("DROP TABLE IF EXISTS filtered_items");
			}
		} catch (SQLException e) {
			if (LOGGING) 
				e.printStackTrace();
		} 
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return items;		
	}


//	public void dumpDatabase()
//	{
//		try {
//			if (databaseReady()) {
//				db.rawExecSQL("ATTACH DATABASE '/mnt/sdcard/dump.sqlite' AS plaintext KEY '';");
//				db.rawExecSQL("SELECT sqlcipher_export('plaintext');");
//				db.rawExecSQL("DETACH DATABASE plaintext;");
//			}
//		} catch (SQLException e) {
//			if (LOGGING) 
//				e.printStackTrace();
//		} 		
//		finally
//		{
//		}
//	}

	public ArrayList<Item> getItemsWithMediaNotDownloaded(int limit) {
		ArrayList<Item> items = new ArrayList<Item>();
		
		Cursor queryCursor = null;
			
		String query = "SELECT i." + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + " FROM "
				+ DatabaseHelper.ITEMS_TABLE + " i, " + DatabaseHelper.ITEM_MEDIA_TABLE + " m, " + DatabaseHelper.FEEDS_TABLE + " f"
				+ " WHERE " + "f." + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + " =  " + "i." + DatabaseHelper.ITEMS_TABLE_FEED_ID 
				+ " AND " + "f." + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + " = ? "
				+ " AND " + " m." + DatabaseHelper.ITEM_MEDIA_ITEM_ID + "=i." + DatabaseHelper.ITEMS_TABLE_COLUMN_ID
				+ " AND m." + DatabaseHelper.ITEM_MEDIA_DOWNLOADED + " = ?";
			
		query = query + " order by RANDOM()";
		query = query + " limit " + limit + ";";
		
		if (LOGGING)
			Log.v(LOGTAG,query);
			
		if (databaseReady()) {
			try {
				ArrayList<String> queryParams = new ArrayList<String>();
				queryParams.add("1");
				queryParams.add("0");
				queryCursor = db.rawQuery(query, queryParams.toArray(new String[0]));
					
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
			
				if (LOGGING)
					Log.v(LOGTAG,"Got " + queryCursor.getCount() + " items");
					
				if (queryCursor.moveToFirst())
				{
					do {
						int itemId = queryCursor.getInt(itemIdColumn);
						Item item = this.getItemById(itemId);
						items.add(item);
					} while (queryCursor.moveToNext());
							
				} else {
					if (LOGGING)
						Log.v(LOGTAG,"Couldn't move to first");
				}
				
				queryCursor.close();
				
			} catch (SQLException e) {
				if (LOGGING) 
					e.printStackTrace();
			} 
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			finally
			{
				if (queryCursor != null)
				{
					try
					{
						queryCursor.close();					
					}
					catch(Exception e) {
						if (LOGGING)
							e.printStackTrace();
					}
				}
			}
		}
		return items;		
	}
	
	
	public ArrayList<Item> getFeedItemsWithTag(Feed feed, String tag) {
		ArrayList<Item> items = new ArrayList<Item>();
		
		Cursor queryCursor = null;
		
		try {
			
			String query = "select " + DatabaseHelper.ITEM_TAGS_TABLE_ID + ", " + DatabaseHelper.ITEM_TAG + ", t."
					+ DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + " from " + DatabaseHelper.ITEM_TAGS_TABLE + " t, " 
					+ DatabaseHelper.ITEMS_TABLE + " i where " + DatabaseHelper.ITEM_TAG + " LIKE ? and t." 
					+ DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + "= i." + DatabaseHelper.ITEMS_TABLE_COLUMN_ID 
					+ " and " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " =? order by "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " DESC;";
			
			if (LOGGING)
				Log.v(LOGTAG,query);
			
			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] { "%" + tag + "%", String.valueOf(feed.getDatabaseId())});
				
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID);
		
				if (queryCursor.moveToFirst())
				{
					do {
						int itemId = queryCursor.getInt(itemIdColumn);
						Item item = this.getItemById(itemId);
						items.add(item);
					} while (queryCursor.moveToNext());
						
				} else {
					if (LOGGING)
						Log.v(LOGTAG,"Couldn't move to first");
				}
			
				queryCursor.close();
			}
		} catch (SQLException e) {
			if (LOGGING)
				e.printStackTrace();
		} 
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return items;
	}		
	
	public ArrayList<Item> getItemsWithTag(String tag) {
		ArrayList<Item> items = new ArrayList<Item>();
		
		Cursor queryCursor = null;
		
		try {
			
			String query = "select " + DatabaseHelper.ITEM_TAGS_TABLE_ID + ", " + DatabaseHelper.ITEM_TAG + ", "
					+ DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + " from " + DatabaseHelper.ITEM_TAGS_TABLE + " where " + DatabaseHelper.ITEM_TAG + " =?;";
			
			if (LOGGING)
				Log.v(LOGTAG,query);
			
			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] {tag});
				
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID);
		
				if (queryCursor.moveToFirst())
				{
					do {
						int itemId = queryCursor.getInt(itemIdColumn);
						Item item = this.getItemById(itemId);
						items.add(item);
					} while (queryCursor.moveToNext());
						
				} else {
					if (LOGGING)
						Log.v(LOGTAG,"Couldn't move to first");
				}
			
				queryCursor.close();
			}
		} catch (SQLException e) {
			if (LOGGING)
				e.printStackTrace();
		} 
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return items;
	}		
	
	public ArrayList<String> getItemTags(Item item) {

		ArrayList<String> itemTags = new ArrayList<String>();
		Cursor queryCursor = null;
		
		try {
			
			String query = "select " + DatabaseHelper.ITEM_TAGS_TABLE_ID + ", " + DatabaseHelper.ITEM_TAG + ", "
					+ DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + " from " + DatabaseHelper.ITEM_TAGS_TABLE + " where " + DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + " =?;";
			
			if (LOGGING)
				Log.v(LOGTAG,query);
			
			if (databaseReady()) {
				queryCursor = db.rawQuery(query, new String[] { String.valueOf(item.getDatabaseId())});
				
				int tagColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_TAG);
		
				if (queryCursor.moveToFirst())
				{
					do {
						String tag = queryCursor.getString(tagColumn);
						itemTags.add(tag);
						if (LOGGING)
							Log.v(LOGTAG,"tag: " + tag);
					} while (queryCursor.moveToNext());
						
				} else {
					if (LOGGING)
						Log.v(LOGTAG,"Couldn't move to first");
				}
			
				queryCursor.close();
			}
		} catch (SQLException e) {
			if (LOGGING)
				e.printStackTrace();
		} 	
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {
					if (LOGGING)
						e.printStackTrace();
				}
			}
		}
		return itemTags;
	}
	
	public void addOrUpdateItemTags(Item item) {

		deleteItemTags(item.getDatabaseId());
		
		for (String tag : item.getCategories()) {
			try
			{
				ContentValues values = new ContentValues();
				values.put(DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID, item.getDatabaseId());
				values.put(DatabaseHelper.ITEM_TAG, tag);
		
				if (databaseReady()) {
					db.insert(DatabaseHelper.ITEM_TAGS_TABLE, null, values);
				}
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}		
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
		}
	}
	
	public void deleteItemTags(long itemDatabaseId) {
		if (databaseReady()) {
			try
			{
				long returnValue = db.delete(DatabaseHelper.ITEM_TAGS_TABLE, DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + "=?", new String[] { String.valueOf(itemDatabaseId) });
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
		}
	}
	
	public void deleteItemTags(Item item) {
		deleteItemTags(item.getDatabaseId());
	}
	
	
	public void deleteItemMedia(Item item)
	{
		deleteItemMedia(item.getDatabaseId());
	}

	public void deleteItemMedia(long itemId)
	{
		if (databaseReady()) {
			try
			{
				//ArrayList<String> itemMediaFiles = getItemMediaFiles(itemId);
				long returnValue = db.delete(DatabaseHelper.ITEM_MEDIA_TABLE, DatabaseHelper.ITEM_MEDIA_ITEM_ID + "=?", new String[] { String.valueOf(itemId) });
			}
			catch (SQLException e)
			{
				if (LOGGING) 
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
		}
	}

	public void deleteItemComments(Item item) {
		deleteItemComments(item.getDatabaseId());
	}
	
	public void deleteItemComments(long itemId) {
		if (databaseReady()) {
			try {
				long returnValue = db.delete(DatabaseHelper.COMMENTS_TABLE, DatabaseHelper.COMMENTS_TABLE_ITEM_ID + "=?", new String[] { String.valueOf(itemId) });
			} catch (SQLException e) {
				if (LOGGING)
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}

		}
	}
	
	/*
	public ArrayList<String> getItemMediaFiles(long itemId) {
		if (databaseReady()) {

		}
	}
	*/
	
	private long deleteOldItemMedia(Item item, ArrayList<MediaContent> itemMediaList)
	{
		long numDeleted = 0;
		try
		{
			String databaseIds = Joiner.on(",").join(Iterables.transform(itemMediaList, new Function<MediaContent, String>()
					{
				@Override
				public String apply(MediaContent mc) {
					return Long.toString(mc.getDatabaseId());
				}
			}));
			if (databaseReady()) {
				numDeleted = db.delete(DatabaseHelper.ITEM_MEDIA_TABLE, DatabaseHelper.ITEM_MEDIA_ITEM_ID + "=? AND " + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + " NOT IN (" + databaseIds + ")", new String[] { String.valueOf(item.getDatabaseId()) });
			}
		}
		catch (SQLException e)
		{
			if (LOGGING) 
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		return numDeleted;
	}
	
	public long mediaFileSize() {
		long totalFileSize = 0;

		Cursor queryCursor = null;
		
		String query = "select " + "i." + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + "m." + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + ", " + "i." + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + "m." + DatabaseHelper.ITEM_MEDIA_FILESIZE + ", " 
				+ "i." + DatabaseHelper.ITEMS_TABLE_VIEWCOUNT + ", " + "i." + DatabaseHelper.ITEMS_TABLE_FAVORITE 
				+ " from " + DatabaseHelper.ITEM_MEDIA_TABLE + " m, " + DatabaseHelper.ITEMS_TABLE + " i"
				+ " where " + "m." + DatabaseHelper.ITEM_MEDIA_DOWNLOADED + " = ? and " + "m." + DatabaseHelper.ITEM_MEDIA_ITEM_ID + " = " + "i." + DatabaseHelper.ITEMS_TABLE_COLUMN_ID 
				+ " order by RANDOM()";; 
						
		if (LOGGING)
			Log.v(LOGTAG, query);
			
		if (databaseReady()) {
			try
			{
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(1)});
			
				if (LOGGING)
					Log.v(LOGTAG,"Count " + queryCursor.getCount());
		
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int mediaIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int mediaFileSizeColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_FILESIZE);
						
				if (queryCursor.moveToFirst())
				{
					do
					{
						int itemId = queryCursor.getInt(itemIdColumn);
						int mediaId = queryCursor.getInt(mediaIdColumn);
						String title = queryCursor.getString(titleColumn);
						long mediaFileSize = queryCursor.getLong(mediaFileSizeColumn);
						
						totalFileSize += mediaFileSize;

						//if (LOGGING)
						//	Log.v(LOGTAG,"Size: " + itemId + " " + title + " " + mediaFileSize + " " + totalFileSize);
						
					}
					while (queryCursor.moveToNext());
				}
		
				queryCursor.close();
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			finally
			{
				if (queryCursor != null)
				{
					try
					{
						queryCursor.close();					
					}
					catch(Exception e) {
						if (LOGGING)
							e.printStackTrace();
					}
				}
			}
		}
		
		return totalFileSize;
	}	
	
	public int deleteOverLimitMedia(long mediaLimit, SocialReader socialReader) {
		if (LOGGING) 
			Log.v(LOGTAG, "deleteOverLimitMedia");
		
		// mediaLimit in bytes
		int numMediaFilesDeleted = 0;
		
		Cursor queryCursor = null;
		
		String query = "select " + "i." + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + "m." + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + ", " 
						+ "i." + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + "m." + DatabaseHelper.ITEM_MEDIA_FILESIZE + ", " 
						+ "i." + DatabaseHelper.ITEMS_TABLE_VIEWCOUNT + ", " + "i." + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", "
						+ "f." + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED
						+ " from " + DatabaseHelper.ITEM_MEDIA_TABLE + " m, " + DatabaseHelper.ITEMS_TABLE + " i, " 
						+ DatabaseHelper.FEEDS_TABLE + " f"
						+ " where " 
						+ "m." + DatabaseHelper.ITEM_MEDIA_DOWNLOADED + " = ? and " + "m." + DatabaseHelper.ITEM_MEDIA_ITEM_ID + " = " 
						+ "i." + DatabaseHelper.ITEMS_TABLE_COLUMN_ID 
						+ " AND i." + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = f." + DatabaseHelper.FEEDS_TABLE_COLUMN_ID 
						+ " order by RANDOM()";
								
		if (LOGGING)
			Log.v(LOGTAG, query);
			
		if (databaseReady()) {
			try
			{
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(1)});
			
				//if (LOGGING)
				//	Log.v(LOGTAG,"Count " + queryCursor.getCount());
	
				int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
				int mediaIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID);
				int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
				int mediaFileSizeColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_FILESIZE);
				int viewCountColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_VIEWCOUNT);
				int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
				int subscribedColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED);
				
				long totalFileSize = 0;
				
				if (queryCursor.moveToFirst())
				{
					do
					{
						int itemId = queryCursor.getInt(itemIdColumn);
						int mediaId = queryCursor.getInt(mediaIdColumn);
						String title = queryCursor.getString(titleColumn);
						long mediaFileSize = queryCursor.getLong(mediaFileSizeColumn);
					
						boolean favorite = false;
						if (queryCursor.getInt(favoriteColumn) == 1) {
							favorite = true;
						}
						int viewCount = queryCursor.getInt(viewCountColumn);

						boolean subscribed = false;
						if (queryCursor.getInt(subscribedColumn) == 1) {
							subscribed = true;
						}
						
						if (totalFileSize + mediaFileSize > mediaLimit && !favorite && (!subscribed || viewCount > 0)) {
							if (LOGGING)
								Log.v(LOGTAG,"Going to delete media files for " + itemId + " " + title + " " + mediaFileSize);
							
							socialReader.deleteMediaContentFile(mediaId);
							numMediaFilesDeleted++;
						} else {
							totalFileSize = totalFileSize + mediaFileSize;
							
							/*
							if (LOGGING) {
								if (viewCount == 0) {
									Log.v(LOGTAG, "viewCount == 0");
								} else if (favorite) {
									Log.v(LOGTAG, "favorite");
								} else {
									Log.v(LOGTAG, "not sure");
								}
							}
							*/
							
						}
					}
					while (queryCursor.moveToNext());
				}
				else {
					if (LOGGING)
						Log.v(LOGTAG,"Couldn't move to first?");
				}
	
				queryCursor.close();
			}
			catch (SQLException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			catch(IllegalStateException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			finally
			{
				if (queryCursor != null)
				{
					try
					{
						queryCursor.close();					
					}
					catch(Exception e) {
						if (LOGGING)
							e.printStackTrace();
					}
				}
			}
		}
		
		return numMediaFilesDeleted;
	}		
	
	public void deleteAll()
	{
		try
		{
			if (!databaseReady())
				open();
			
			if (databaseReady()) {
				db.delete(DatabaseHelper.ITEMS_TABLE, "1", null);
				db.delete(DatabaseHelper.FEEDS_TABLE, "1", null);
				db.delete(DatabaseHelper.ITEM_MEDIA_TABLE, "1", null);
				db.delete(DatabaseHelper.SETTINGS_TABLE, "1", null);
				db.delete(DatabaseHelper.ITEM_TAGS_TABLE, "1", null);
				db.delete(DatabaseHelper.COMMENTS_TABLE, "1", null);
				//db.delete(DatabaseHelper.TAGS_TABLE, "1", null);
			}
		}
		catch (SQLException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			if (LOGGING)
				e.printStackTrace();
		}

	}
}