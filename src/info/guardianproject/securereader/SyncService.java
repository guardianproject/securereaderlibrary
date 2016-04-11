package info.guardianproject.securereader;

import java.util.ArrayList;

import com.tinymission.rss.Feed;
import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class SyncService extends Service {

	public static final String LOGTAG = "SyncService";
	public static final boolean LOGGING = false;
	
	// ArrayList of SyncTask objects, basically a queue of work to be done
	public ArrayList<SyncTask> syncList = new ArrayList<SyncTask>();

	// Single thread for background syncing
	Thread syncThread;

	public ArrayList<SyncTask> getSyncList() {
		return syncList;
	}
	
	// Service starting..
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    		
    	if (LOGGING)
    		Log.v(LOGTAG,"onStartCommand");
    	
    	// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    // Service ending
    @Override
    public void onDestroy() {
    	if (LOGGING)
    		Log.v(LOGTAG,"onDestroy");
    }

    // Bind with Activities
    @Override
    public IBinder onBind(Intent intent) {
    	if (LOGGING)
    		Log.v(LOGTAG,"onBind");
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        SyncService getService() {
            return SyncService.this;
        }
    }
    
    // Register callbacks that track what the SyncService is doing
    // This can be used for the sidebar display
    SyncServiceListener syncServiceCallback;
	public void setSyncServiceListener(SyncServiceListener _syncServiceCallback) {
		syncServiceCallback = _syncServiceCallback;
	}
    
	// This is the interface that must be implemented to get the callbacks
    public interface SyncServiceListener {
    	/*
    	 * Do these exist anywhere?
    	int SYNC_EVENT_TYPE_FEED_UPADTED = 0;
    	int SYNC_EVENT_TYPE_FEED_ADDED = 1;
    	int SYNC_EVENT_TYPE_FEED_QUEUED = 2;
    	int SYNC_EVENT_TYPE_MEDIA_QUEUED = 3;
    	int SYNC_EVENT_TYPE_MEDIA_UPDATED = 4;
    	int SYNC_EVENT_TYPE_NOOP = -1; // This one definitely does not 
    	*/

    	public void syncEvent(SyncTask syncTask);    	
    }
    
    // The actual SyncTask class, defines each action to take
    // Right now only supports feed syncing
    public class SyncTask {
    	public static final int TYPE_FEED = 0;
    	public static final int TYPE_MEDIA = 1;
    	public static final int TYPE_COMMENTS = 2;
    	
    	public static final int ERROR = -1;
    	public static final int CREATED = 0;
    	public static final int QUEUED = 1;
    	public static final int STARTED = 2;
    	public static final int FINISHED = 3;
    	public static final int CANCELLED = 4;
    	
    	public Feed feed;
    	public MediaContent mediaContent;
    	public Item item;
    	
    	SyncServiceFeedFetcher.SyncServiceFeedFetchedCallback callback;
    	public int status = CREATED;
    	public int type = 0;
    	
    	public static final long MAXTIME = 3600000; // 60 * 60 * 1000 = 1 hour;  
    	public long startTime = -1;
    	
    	SyncTask(Feed _feed, SyncServiceFeedFetcher.SyncServiceFeedFetchedCallback _callback) {
    		feed = _feed;
    		type = TYPE_FEED;
    		callback = _callback;
    	}
    	
    	SyncTask(MediaContent _mediaContent) {
    		mediaContent = _mediaContent;
    		type = TYPE_MEDIA;
    	}
    	
    	SyncTask(Item _item) {
    		item = _item;
    		type = TYPE_COMMENTS;
    	}
    	
    	void updateStatus(int newStatus) {
    		status = newStatus;
    		syncServiceEvent(this);
    	}
    	
    	int getStatus() {
    		return status;
    	}
    	
    	void start() {
    		if (type == TYPE_FEED) {
    			startFeedFetcher();
    		} else if (type == TYPE_MEDIA) {
    			startMediaDownloader();
    		} else if (type == TYPE_COMMENTS) {
    			startCommentsFeedFetcher();
    		}
    	}
    	
    	private void startMediaDownloader() {
    		SyncServiceMediaDownloader ssMediaDownloader = new SyncServiceMediaDownloader(SyncService.this,this);
    		
    		if (LOGGING)
    			Log.v(LOGTAG,"Create and start ssMediaDownloader ");
    		syncThread = new Thread(ssMediaDownloader);
    		syncThread.start();
    		updateStatus(SyncTask.STARTED);
    		startTime = System.currentTimeMillis();
    	}
    	    	
    	private void startFeedFetcher() {
    		//SyncServiceFeedFetcher feedFetcher = new SyncServiceFeedFetcher(SyncService.this,feed);
    		//feedFetcher.setFeedUpdatedCallback(callback);
    		
    		if (LOGGING)
    			Log.v(LOGTAG,"Create SyncServiceFeedFetcher");
    		SyncServiceFeedFetcher feedFetcher = new SyncServiceFeedFetcher(SyncService.this,this);
    		
    		if (LOGGING)
    			Log.v(LOGTAG,"Create and start fetcherThread ");
    		syncThread = new Thread(feedFetcher);
    		syncThread.start();    	
    		updateStatus(SyncTask.STARTED);
    		startTime = System.currentTimeMillis();
    	}
    	
    	private void startCommentsFeedFetcher() {    		
    		if (LOGGING)
    			Log.v(LOGTAG,"Create SyncServiceCommentsFeedFetcher");
    		SyncServiceCommentsFeedFetcher commentsFeedFetcher = new SyncServiceCommentsFeedFetcher(SyncService.this,this);
    		
    		if (LOGGING)
    			Log.v(LOGTAG,"Create and start fetcherThread ");
    		syncThread = new Thread(commentsFeedFetcher);
    		syncThread.start();    	
    		updateStatus(SyncTask.STARTED);
    		startTime = System.currentTimeMillis();
    	}    	
    	
    	void taskComplete(int status) {
    		if (status == FINISHED) {
    			if (type == TYPE_FEED) {
    				//((App) getApplicationContext()).socialReader.setFeedAndItemData(feed);
    				//SocialReader.getInstance(getApplicationContext()).backgroundDownloadFeedItemMedia(feed);
    			}
    			if (callback != null && type == TYPE_FEED) {
    				callback.feedFetched(feed);
    			}
        		updateStatus(status);
    		}
    	}
    }
    
    private boolean overTime(SyncTask syncTask) {
    	if (syncTask.status == SyncTask.STARTED && 
    			System.currentTimeMillis() - syncTask.startTime > SyncTask.MAXTIME) 
    	{
    			return true;
    	}
    	return false;
    }
    
    public void addFeedSyncTask(Feed feed) {
    	addFeedSyncTask(feed, null);
    }
    
    public void addFeedSyncTask(Feed feed, SyncServiceFeedFetcher.SyncServiceFeedFetchedCallback callback) {
    	for (int i = 0; i < syncList.size(); i++) {
    		Feed feedTask = syncList.get(i).feed;
    		if (feedTask != null && feedTask.getDatabaseId() == feed.getDatabaseId()) {
    			if (overTime(syncList.get(i))) {
    				syncList.get(i).status = SyncTask.ERROR;
    	    		syncList.remove(syncList.get(i));
    				if (LOGGING) 
    					Log.v(LOGTAG, "Feed was already in queue but over time");
    			}
    			else {
	        		if (LOGGING)
	        			Log.v(LOGTAG,"Feed already in queue, ignoring");
	    			return;
    			}
    		}
    	}
    	SyncTask newSyncTask = new SyncTask(feed,callback);
    	
    	syncList.add(newSyncTask);
    	newSyncTask.updateStatus(SyncTask.QUEUED);
    	
		syncServiceEvent(newSyncTask);
    }
    
    public void addCommentsSyncTask(Item item) {
    	if (LOGGING)
    		Log.v(LOGTAG,"addCommentSyncTask " + item.getCommentsUrl());
    	for (int i = 0; i < syncList.size(); i++) {
    		Item itemTask = syncList.get(i).item;
    		if (itemTask != null && itemTask.getDatabaseId() == item.getDatabaseId()) {
    			if (overTime(syncList.get(i))) {
    				syncList.get(i).status = SyncTask.ERROR;
    	    		syncList.remove(syncList.get(i));
    				if (LOGGING) 
    					Log.v(LOGTAG, "Item was already in queue but over time");
    			}
    			else {
	        		if (LOGGING)
	        			Log.v(LOGTAG,"Item already in queue, ignoring " + itemTask.getDatabaseId());
	    			return;
    			}
    		}
    	}
    	SyncTask newSyncTask = new SyncTask(item);
    	syncList.add(newSyncTask);
    	newSyncTask.updateStatus(SyncTask.QUEUED);
    	
		syncServiceEvent(newSyncTask);
    }    
    
    void syncServiceEvent(SyncTask _syncTask) {
    	
    	if (_syncTask.status == SyncTask.FINISHED) {
    		syncList.remove(_syncTask);
    	} else if (_syncTask.status == SyncTask.ERROR) {
    		syncList.remove(_syncTask);
    	} else if (_syncTask.status == SyncTask.CANCELLED) {
      		syncList.remove(_syncTask);  		
    	}
    	
    	boolean startNewTask = true;
    	for (int i = 0; i < syncList.size() && startNewTask; i++) {
    		if (syncList.get(i).status == SyncTask.STARTED) {
    			startNewTask = false;
    		}
    	}
    	
    	if (startNewTask) {
	    	for (int i = 0; i < syncList.size(); i++) {
	    		if (syncList.get(i).status == SyncTask.QUEUED) {
	    			syncList.get(i).start();	
	    			break;
	    		}
	    	}
    	}
    	
    	if (syncServiceCallback != null && SocialReader.getInstance(getApplicationContext()).appStatus == SocialReader.APP_IN_FOREGROUND) {
    		syncServiceCallback.syncEvent(_syncTask);
    	}
    }
    
    public void addMediaContentSyncTask(MediaContent mediaContent) {
    	addMediaContentSyncTask(mediaContent, false);
    }
    
    public void addMediaContentSyncTask(MediaContent mediaContent, boolean toFront) {
    	for (int i = 0; i < syncList.size(); i++) {
    		MediaContent mediaTask = syncList.get(i).mediaContent;
    		if (mediaTask != null && mediaTask.getDatabaseId() == mediaContent.getDatabaseId()) {
    			if (overTime(syncList.get(i))) {
    				syncList.get(i).status = SyncTask.ERROR;
    	    		syncList.remove(syncList.get(i));
    				if (LOGGING) 
    					Log.v(LOGTAG, "MediaContent was already in queue but over time");
    				break;
    			}
    			else 
    			{
    				if (LOGGING)
    					Log.v(LOGTAG,"MediaContent already in queue, ignoring");
    				return;
    			}
    		}
    	}
    	SyncTask newSyncTask = new SyncTask(mediaContent);
    	if (toFront) {
    		syncList.add(0, newSyncTask);
    	} else {
    		syncList.add(newSyncTask);
    	}
    	newSyncTask.updateStatus(SyncTask.QUEUED);
    	
		syncServiceEvent(newSyncTask);
    }
    
    public void addMediaContentSyncTaskToFront(MediaContent mediaContent) {
    	addMediaContentSyncTask(mediaContent, true);
    }

    /*
    public void addMediaContentSyncTaskToFront(MediaContent mediaContent) {
    	boolean addTask = true;
    	for (int i = 0; i < syncList.size(); i++) {
    		MediaContent mediaTask = syncList.get(i).mediaContent;
    		if (mediaTask != null && mediaTask.getDatabaseId() == mediaContent.getDatabaseId()) {
    			if (syncList.get(i).status == SyncTask.QUEUED)
    				syncList.get(i).status = SyncTask.CANCELLED;
    			else if (syncList.get(i).status == SyncTask.STARTED)
    				addTask = false;
        		if (LOGGING)
        			Log.v(LOGTAG,"MediaContent already in queue");
    			break;
    		}
    	}

    	if (addTask)
    	{
    		SyncTask newSyncTask = new SyncTask(mediaContent);
    		newSyncTask.updateStatus(SyncTask.QUEUED);
    		if (LOGGING) {
    			Log.v(LOGTAG, "addMediaContentSyncTaskToFront " + mediaContent.getUrl());
    		}
    		syncList.add(0, newSyncTask);
    	
    		syncServiceEvent(newSyncTask);    
    	}
    }
    */

    public void addMediaContentSyncTasksToFront(ArrayList<MediaContent> mediaContents, boolean forceStart) {

    	if (mediaContents != null)
    	{
    		for (int idxMediaContent = mediaContents.size() - 1; idxMediaContent >= 0; idxMediaContent--)
    		{
    			MediaContent mediaContent = mediaContents.get(idxMediaContent);
    	    	for (int i = 0; i < syncList.size(); i++) {
    	    		MediaContent mediaTask = syncList.get(i).mediaContent;
    	    		if (mediaTask != null && mediaTask.getDatabaseId() == mediaContent.getDatabaseId()) {
    	    			if (syncList.get(i).status == SyncTask.QUEUED)
    	    				syncList.get(i).status = SyncTask.CANCELLED;
    	    			else if (syncList.get(i).status == SyncTask.STARTED)
    	    				mediaContents.remove(idxMediaContent); // Remove it!
    	        		if (LOGGING)
    	        			Log.v(LOGTAG,"MediaContent already in queue");
    	    			break;
    	    		}
    	    	}    			
    		}
    		
    		// All remaining should be added
    		if (mediaContents.size() > 0)
    		{
    			ArrayList<SyncTask> newTasks = new ArrayList<SyncTask>();
    			for (int idxMediaContent = 0; idxMediaContent < mediaContents.size(); idxMediaContent++)
    			{
    				MediaContent mediaContent = mediaContents.get(idxMediaContent);
    				SyncTask newSyncTask = new SyncTask(mediaContent);
    				if (LOGGING) {
    					Log.v(LOGTAG, "addMediaContentSyncTaskToFrontIndex " + idxMediaContent + " "+ mediaContent.getUrl());
    				}
    				newTasks.add(newSyncTask);
    				syncList.add(idxMediaContent, newSyncTask);
    			}
    			
    			// Update them all to queued (which might start the first one of them, if nothing is currently running
    			for (SyncTask task : newTasks)
    			{
    				if (forceStart)
    				{
    					task.start();
    				}
    				else
    				{
    					task.updateStatus(SyncTask.QUEUED);
    				}
    			}
    		}
    	}
    }
    
    public void clearSyncList() {
    	for (int i = 0; i < syncList.size(); i++) {
    		if (syncList.get(i).status == SyncTask.QUEUED) {
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask QUEUED");
    			syncList.get(i).status = SyncTask.CANCELLED;
    		} else if (syncList.get(i).status == SyncTask.CREATED) {
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask CREATED");
    			syncList.get(i).status = SyncTask.CANCELLED;
    		} else if (syncList.get(i).status == SyncTask.ERROR) {
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask ERROR");
    		} else if (syncList.get(i).status == SyncTask.FINISHED) {
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask FINISHED");
    		} else if (syncList.get(i).status == SyncTask.STARTED) {
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask STARTED");
    		}
    	}    	
    }
    
    public int getNumWaitingToSync() {
    	int count = 0;
    	for (int i = 0; i < syncList.size(); i++) {
    		if (syncList.get(i).status == SyncTask.QUEUED) {
    			count++;
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask QUEUED");
    		} else if (syncList.get(i).status == SyncTask.CREATED) {
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask CREATED");
    		} else if (syncList.get(i).status == SyncTask.ERROR) {
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask ERROR");
    		} else if (syncList.get(i).status == SyncTask.FINISHED) {
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask FINISHED");
    		} else if (syncList.get(i).status == SyncTask.STARTED) {
    			if (overTime(syncList.get(i))) {
    				syncList.get(i).status = SyncTask.ERROR;
    	    		syncList.remove(syncList.get(i));
    				if (LOGGING) 
    					Log.v(LOGTAG, "syncTask STARTED but over time");
    			} else {
    				count++;
    				if (LOGGING)
    					Log.v(LOGTAG, "syncTask STARTED");
    			}
    		} else if (syncList.get(i).status == SyncTask.CANCELLED) {
    			if (LOGGING)
    				Log.v(LOGTAG, "syncTask CANCELLED");
    		}
    	}
    	return count;
    }
}
