package info.guardianproject.securereader;

import info.guardianproject.securereader.SyncService.SyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.tinymission.rss.Comment;
import com.tinymission.rss.CommentReader;
import com.tinymission.rss.Feed;
import com.tinymission.rss.Item;
import com.tinymission.rss.Reader;

/**
 * Class for fetching the feed content in the background.
 * 
 */
public class SyncServiceCommentsFeedFetcher implements Runnable
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "SyncServiceComments";

	SyncService syncService;
	SyncService.SyncTask syncTask;
	
	Messenger messenger;
	Handler runHandler;

	CommentReader reader;

	public interface SyncServiceCommentsFeedFetchedCallback
	{
		public void commentsFeedFetched(Item _item);
	}

	public SyncServiceCommentsFeedFetcher(SyncService _syncService, SyncService.SyncTask _syncTask)
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

	public void stop() {
		reader.stop();
	}
	
	@Override
	public void run() {		
		reader = new CommentReader(SocialReader.getInstance(syncService.getApplicationContext()), syncTask.item);
		Feed tempFeed = reader.fetchCommentFeed();

		SocialReader.getInstance(syncService.getApplicationContext()).setItemComments(syncTask.item, tempFeed.getComments());			
		
		if (LOGGING)
			Log.v(LOGTAG,"syncTask.item " + syncTask.item.getDatabaseId() + ": " + tempFeed.getCommentCount() + " comments");
		
		// Go back to the main thread
		Message m = Message.obtain();            
        try {
			messenger.send(m);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

	}
}
