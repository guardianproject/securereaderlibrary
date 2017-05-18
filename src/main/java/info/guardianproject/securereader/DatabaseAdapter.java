package info.guardianproject.securereader;

import info.guardianproject.cacheword.CacheWordHandler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteConstraintException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDoneException;
import net.sqlcipher.database.SQLiteTransactionListener;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.provider.ContactsContract;
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
	private final Context context;

	public DatabaseAdapter(CacheWordHandler _cacheword, Context _context)
	{
		cacheword = _cacheword;
		context = _context;
		SQLiteDatabase.loadLibs(_context);
		this.databaseHelper = new DatabaseHelper(cacheword, _context);
		open();
		//dumpDatabase(_context);
	}

	public void close()
	{
		databaseHelper.close();
	}

	public void open() throws SQLException
	{
		db = databaseHelper.getWritableDatabase();
		db.execSQL("PRAGMA foreign_keys=ON;");
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

	private long getSimpleLong(String table, String columnRet, String columnWhere, String where) {
		long retVal = -1;
		if (databaseReady()) {
			String query = "SELECT " + columnRet
					+ " FROM " + table
					+ " WHERE " + columnWhere + " = ?;";
			if (LOGGING)
				Log.d(LOGTAG, query);
			try {
				retVal = DatabaseUtils.longForQuery(db, query, new String[]{where});
			} catch (SQLiteDoneException e) {
				// Not found, this is an insert of a new row!
			}
		}
		return retVal;
	}

	private String getSimpleString(String table, String columnRet, String columnWhere, String where) {
		String retVal = "";
		if (databaseReady()) {
			String query = "SELECT " + columnRet
					+ " FROM " + table
					+ " WHERE " + columnWhere + " = ?;";
			if (LOGGING)
				Log.d(LOGTAG, query);
			try {
				retVal = DatabaseUtils.stringForQuery(db, query, new String[]{where});
			} catch (SQLiteDoneException e) {
				// Not found, this is an insert of a new row!
			}
		}
		return retVal;
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
			values.put(DatabaseHelper.FEEDS_TABLE_CATEGORY, feed.getCategory());
			
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
				+ DatabaseHelper.FEEDS_TABLE_FEED_URL + ", "
				+ DatabaseHelper.FEEDS_TABLE_CATEGORY
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
					String category = queryCursor.getString(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_CATEGORY));

					feed.setDatabaseId(feedId);
					feed.setTitle(title);
					feed.setFeedURL(url);
					feed.setDescription(description);
					feed.setLink(link);
					feed.setCategory(category);
				}
			}
			queryCursor.close();
		}
		return feed;
	}

	public String getFeedTitle(long feedId) {
		return getSimpleString(DatabaseHelper.FEEDS_TABLE, DatabaseHelper.FEEDS_TABLE_TITLE, DatabaseHelper.FEEDS_TABLE_COLUMN_ID, String.valueOf(feedId));
	}

	public long addOrUpdateFeedAndItems(Feed feed, int itemLimit) {
		long returnValue = -1;
		try {
			final long time1 = System.currentTimeMillis();
			db.beginTransactionWithListener(new SQLiteTransactionListener() {
				@Override
				public void onBegin() {
					if (LOGGING)
						Log.d(LOGTAG, "onBegin: " + (System.currentTimeMillis() - time1) + "ms");
				}

				@Override
				public void onCommit() {
					if (LOGGING)
						Log.d(LOGTAG, "onCommit: " + (System.currentTimeMillis() - time1) + "ms");
				}

				@Override
				public void onRollback() {
					if (LOGGING)
						Log.d(LOGTAG, "onRollback: " + (System.currentTimeMillis() - time1) + "ms");
				}
			});
			if (addOrUpdateFeed(feed) != -1) {
				for (Item item : feed.getItems()) {
					item.setFeedId(feed.getDatabaseId());
					item.setSource(feed.getTitle());
					addOrUpdateItem(item, itemLimit);
				}
			}
			db.setTransactionSuccessful();
			db.endTransaction();
			long time2 = System.currentTimeMillis();
		} catch (Exception e) {
			if (LOGGING)
				e.printStackTrace();
		}
		return returnValue;
	}

	/**
	 * Adds or updates a feed object in the DB.
	 * @param feed The feed to insert/update
	 * @return -1 on error, >= 0 on success
     */
	public long addOrUpdateFeed(Feed feed) {
		long returnValue = -1;
		if (databaseReady()) {
			try {
				if (feed.getDatabaseId() == Feed.DEFAULT_DATABASE_ID) {
					long id = getSimpleLong(DatabaseHelper.FEEDS_TABLE, DatabaseHelper.FEEDS_TABLE_COLUMN_ID, DatabaseHelper.FEEDS_TABLE_FEED_URL, feed.getFeedURL());
					if (id != -1) {
						feed.setDatabaseId(id);
					}
				}

				ContentValues values = new ContentValues();
				values.put(DatabaseHelper.FEEDS_TABLE_TITLE, feed.getTitle());
				values.put(DatabaseHelper.FEEDS_TABLE_FEED_URL, feed.getFeedURL());
				values.put(DatabaseHelper.FEEDS_TABLE_LANGUAGE, feed.getLanguage());
				values.put(DatabaseHelper.FEEDS_TABLE_DESCRIPTION, feed.getDescription());
				values.put(DatabaseHelper.FEEDS_TABLE_LINK, feed.getLink());
				values.put(DatabaseHelper.FEEDS_TABLE_STATUS, feed.getStatus());
				values.put(DatabaseHelper.FEEDS_TABLE_CATEGORY, feed.getCategory());
				values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, feed.isSubscribed() ? 1 : 0);
				if (feed.getNetworkPullDate() != null) {
					values.put(DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE, dateFormat.format(feed.getNetworkPullDate()));
				}
				if (feed.getLastBuildDate() != null) {
					values.put(DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE, dateFormat.format(feed.getLastBuildDate()));
				}
				if (feed.getPubDate() != null) {
					values.put(DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE, dateFormat.format(feed.getPubDate()));
				}

				if (feed.getDatabaseId() != Feed.DEFAULT_DATABASE_ID) {
					// Update existing
					returnValue = db.update(DatabaseHelper.FEEDS_TABLE, values, DatabaseHelper.FEEDS_TABLE_COLUMN_ID + "=?", new String[]{String.valueOf(feed.getDatabaseId())});
				} else {
					returnValue = db.insertOrThrow(DatabaseHelper.FEEDS_TABLE, null, values);
					feed.setDatabaseId(returnValue);
				}
			} catch (Exception e) {
				if (LOGGING)
					e.printStackTrace();
			}
		}
		return returnValue;
	}

	public long addFeed(String title, String feedUrl)
	{
		Feed feed = new Feed(title, feedUrl);
		feed.setStatus(Feed.STATUS_NOT_SYNCED);
		feed.setSubscribed(true);
		return addOrUpdateFeed(feed);
	}

	public boolean isFeedUnfollowed(String feedUrl)
	{
		long subscribed = getSimpleLong(DatabaseHelper.FEEDS_TABLE, DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, DatabaseHelper.FEEDS_TABLE_FEED_URL, feedUrl);
		return subscribed == 0;
	}

	/**
	 * Delete the given feed. The delete will cascade, i.e. all items, media, tags and comments that
	 * belong to the feed are also deleted.
	 * @param feedDatabaseId The database id of the feed to delete.
	 * @return true if the feed was deleted successfully.
	 */
	public boolean deleteFeed(long feedDatabaseId)
	{
		boolean returnValue = false;

		try
		{
			if (databaseReady()) {
				returnValue = db.delete(DatabaseHelper.FEEDS_TABLE, DatabaseHelper.FEEDS_TABLE_COLUMN_ID + "=?", new String[]{String.valueOf(feedDatabaseId)}) > 0;
				if (returnValue) {
					// Delete any media files we have downloaded
					cleanupMediaItemsAndFiles(SocialReader.getInstance(context));
				}
			}
		}
		catch(Exception e)
		{
			if (LOGGING)
				e.printStackTrace();
		}
		return returnValue;
	}

	public void deleteExpiredItems(Date expirationDate) {
		boolean createdTransaction = false;
		Cursor queryCursor = null;
		
		String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", "
				+ DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " 
				+ DatabaseHelper.ITEMS_TABLE_FEED_ID + " from " + DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " < ?"
				+ " and " + DatabaseHelper.ITEMS_TABLE_FAVORITE + " != ? and " + DatabaseHelper.ITEMS_TABLE_SHARED + " != ?;";

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
					if (!db.inTransaction()) {
						db.beginTransaction();
						createdTransaction = true;
					}
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
					if (createdTransaction) {
						db.setTransactionSuccessful();
						db.endTransaction();
					}
				}
	
				queryCursor.close();
			}
			catch (Exception e)
			{
				if (LOGGING)
					e.printStackTrace();
				if (createdTransaction) {
					try {
						db.endTransaction();
					} catch (Exception ignored){}
				}
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
	
	
	// TODO - don't call this every time an item is inserted. Use some kind of timer?
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

	/**
	 * Delete the item with the given database id. This will cascase, i.e. delete all related media,
	 * comments and tags.
	 * @param itemDatabaseId The database id of the item to delete.
	 * @return true if the item was deleted successfully.
	 */
	public boolean deleteItem(long itemDatabaseId)
	{
		boolean returnValue = false;

		try
		{
			if (databaseReady()) {
				returnValue = db.delete(DatabaseHelper.ITEMS_TABLE, DatabaseHelper.ITEMS_TABLE_COLUMN_ID + "=?", new String[]{String.valueOf(itemDatabaseId)}) > 0;
				if (returnValue) {
					// Delete any media files we have downloaded
					cleanupMediaItemsAndFiles(SocialReader.getInstance(context));
				}
			}
		}
		catch (Exception e)
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

	public long addOrUpdateItem(Item item, int limit) {
		long returnValue = -1;
		if (databaseReady()) {
			try {
				if (item.getDatabaseId() == Item.DEFAULT_DATABASE_ID) {
					// Get existing database ID
					String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID
							+ " from " + DatabaseHelper.ITEMS_TABLE
							+ " where " + DatabaseHelper.ITEMS_TABLE_GUID + " = ?"
							+ " and " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = ?;";
					try {
						long id = DatabaseUtils.longForQuery(db, query, new String[]{item.getGuid(), String.valueOf(item.getFeedId())});
						item.setDatabaseId(id);
					} catch (SQLiteDoneException e) {
						// Not found, this is an insert of a new row!
					}
				}

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

				if (item.getPubDate() != null) {
					values.put(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE, dateFormat.format(item.getPubDate()));
				}

				values.put(DatabaseHelper.ITEMS_TABLE_VIEWCOUNT, item.getViewCount());

				if (item.getDatabaseId() != Item.DEFAULT_DATABASE_ID) {
					// Update existing
					returnValue = db.update(DatabaseHelper.ITEMS_TABLE, values, DatabaseHelper.ITEMS_TABLE_COLUMN_ID + "=?", new String[]{String.valueOf(item.getDatabaseId())});
				} else {
					returnValue = db.insertOrThrow(DatabaseHelper.ITEMS_TABLE, null, values);
					item.setDatabaseId(returnValue);
					if (limit != -1) {
						deleteOverLimitItems(limit);
					}
				}

				addOrUpdateItemMedia(item, item.getMediaContent());
				addOrUpdateItemTags(item);
			} catch (Exception e) {
				if (LOGGING)
					e.printStackTrace();
			}
		}
		return returnValue;
	}

	public ArrayList<MediaContent> getItemMedia(Item item) {
		return getItemMedia(item.getDatabaseId());
	}

	public ArrayList<MediaContent> getItemMedia(long itemDatabaseId)
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
				queryCursor = db.rawQuery(query, new String[] {String.valueOf(itemDatabaseId)});
	
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
			catch (Exception e)
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

		if (databaseReady()) {
			boolean createdTransaction = false;
			if (!db.inTransaction()) {
				db.beginTransaction();
				createdTransaction = true;
			}

			// Three stage process:
			// 1. Mark the "media type" of all media belonging to this item in the DB as "invalid"
			// 2. Insert/update all media from the given list
			// 3. Delete all media with type "invalid", since these are stale media items
			//
			ContentValues values = new ContentValues(1);
			values.put(DatabaseHelper.ITEM_MEDIA_TYPE, "invalid");
			int tempMarkedInvalid = db.update(DatabaseHelper.ITEM_MEDIA_TABLE, values, DatabaseHelper.ITEM_MEDIA_ITEM_ID + " = ?", new String[] { String.valueOf(item.getDatabaseId()) });
			if (LOGGING)
				Log.v(LOGTAG, String.valueOf(tempMarkedInvalid) + " itemMedia marked invalid for " + item.getDatabaseId());

			// Insert/update
			for (MediaContent itemMedia : itemMediaList)
			{
				itemMedia.setItemDatabaseId(item.getDatabaseId());
				addOrUpdateItemMedia(itemMedia);
				if (LOGGING)
					Log.v(LOGTAG,"itemMedia added or updated: " + itemMedia.getDatabaseId());
			}

			// Remove stale items
			int removed = db.delete(DatabaseHelper.ITEM_MEDIA_TABLE, DatabaseHelper.ITEM_MEDIA_TYPE + " = ?", new String[]{ "invalid" });
			if (LOGGING)
				Log.v(LOGTAG, String.valueOf(removed) + " itemMedia removed for " + item.getDatabaseId());

			if (createdTransaction) {
				db.setTransactionSuccessful();
				db.endTransaction();
			}
		}
	}

	public long addOrUpdateItemMedia(MediaContent itemMedia) {
		long returnValue = -1;
		if (databaseReady()) {
			try {
				if (itemMedia.getItemDatabaseId() == 0) {
					throw new Exception("MEDIA CONTENT ITEM ID NOT SET!");
				}
				if (itemMedia.getDatabaseId() == MediaContent.DEFAULT_DATABASE_ID) {
					// Get existing database ID
					String query = "select " + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID
							+ " from " + DatabaseHelper.ITEM_MEDIA_TABLE
							+ " where " + DatabaseHelper.ITEM_MEDIA_ITEM_ID + " = ?"
							+ " and " + DatabaseHelper.ITEM_MEDIA_URL + " = ?;";
 					try {
						long id = DatabaseUtils.longForQuery(db, query, new String[]{String.valueOf(itemMedia.getItemDatabaseId()), itemMedia.getUrl()});
						itemMedia.setDatabaseId(id);
					} catch (SQLiteDoneException e) {
						// Not found, this is an insert of a new row!
					}
				}

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
				values.put(DatabaseHelper.ITEM_MEDIA_DOWNLOADED, itemMedia.getDownloaded() ? 1 : 0);

				if (itemMedia.getDatabaseId() != Feed.DEFAULT_DATABASE_ID) {
					// Update existing
					returnValue = db.update(DatabaseHelper.ITEM_MEDIA_TABLE, values, DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + "=?", new String[]{String.valueOf(itemMedia.getDatabaseId())});
				} else {
					returnValue = db.insertOrThrow(DatabaseHelper.ITEM_MEDIA_TABLE, null, values);
					itemMedia.setDatabaseId(returnValue);
				}
			} catch (Exception e) {
				if (LOGGING)
					e.printStackTrace();
			}
		}
		return returnValue;
	}

	public void addOrUpdateItemComments(Item item, ArrayList<Comment> itemCommentList)
	{
		//TODO refactoring
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


	public void dumpDatabase(Context context)
	{
		try {
			if (databaseReady()) {
				String path = context.getFilesDir().getAbsolutePath();
				db.rawExecSQL("ATTACH DATABASE '" + path + "/dump.sqlite' AS plaintext KEY '';");
				db.rawExecSQL("SELECT sqlcipher_export('plaintext');");
				db.rawExecSQL("DETACH DATABASE plaintext;");
			}
		} catch (SQLException e) {
			if (LOGGING)
				e.printStackTrace();
		}
		finally
		{
		}
	}

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
			catch (Exception e)
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

	private int cleanupMediaItemsAndFiles(SocialReader socialReader) {
		if (LOGGING)
			Log.v(LOGTAG, "cleanupMediaItemsAndFiles");

		int numMediaFilesDeleted = 0;

		Cursor queryCursor = null;
		String query = "SELECT " + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID
				+ " FROM " + DatabaseHelper.ITEM_MEDIA_TABLE
				+ " WHERE " + DatabaseHelper.ITEM_MEDIA_ITEM_ID + " IS NULL;";
		if (LOGGING)
			Log.v(LOGTAG, query);

		if (databaseReady()) {
			try
			{
				db.beginTransaction();

				queryCursor = db.rawQuery(query, null);
				int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID);
				if (queryCursor.moveToFirst())
				{
					do
					{
						int mediaId = queryCursor.getInt(idColumn);
						socialReader.deleteMediaContentFileNow(mediaId);
						numMediaFilesDeleted += db.delete(DatabaseHelper.ITEM_MEDIA_TABLE, DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + "=?", new String[]{String.valueOf(mediaId)});
					}
					while (queryCursor.moveToNext());
				}
				else {
					if (LOGGING)
						Log.v(LOGTAG,"Couldn't move to first?");
				}

				queryCursor.close();
				db.setTransactionSuccessful();
			}
			catch (Exception e)
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
				db.endTransaction();
			}
		}
		if (LOGGING)
			Log.d(LOGTAG, "Cleaned up "+ numMediaFilesDeleted + " media files");
		return numMediaFilesDeleted;
	}
}