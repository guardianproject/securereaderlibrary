package info.guardianproject.securereader;

import info.guardianproject.securereader.SyncService.SyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.tinymission.rss.Feed;
import com.tinymission.rss.Reader;

/**
 * Class for fetching the feed content in the background.
 * 
 */
public class SyncServiceFeedFetcher implements Runnable
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "SyncServiceFeedFetcher";

	SyncService syncService;
	SyncService.SyncTask syncTask;
	
	Messenger messenger;
	Handler runHandler;
	
	public interface SyncServiceFeedFetchedCallback
	{
		public void feedFetched(Feed _feed);
	}

	public SyncServiceFeedFetcher(SyncService _syncService, SyncService.SyncTask _syncTask)
	{
		syncService = _syncService;
		syncTask = _syncTask;

		runHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				// Just assuming this means the thread is done
				syncTask.taskComplete(SyncTask.FINISHED);
			}
		};
		
		messenger = new Messenger(runHandler);
	}
	
	
	@Override
	public void run() {		
		Reader reader = new Reader(SocialReader.getInstance(syncService.getApplicationContext()), syncTask.feed);
		syncTask.feed = reader.fetchFeed();

		SocialReader.getInstance(syncService.getApplicationContext()).setFeedAndItemData(syncTask.feed);
		SocialReader.getInstance(syncService.getApplicationContext()).backgroundDownloadFeedItemMedia(syncTask.feed);

		
		if (LOGGING)
			Log.v(LOGTAG,"syncTask.feed should be complete");
		
		// Go back to the main thread
		Message m = Message.obtain();            
        try {
			messenger.send(m);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

	}
}
