package info.guardianproject.securereader;

import android.content.Context;
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
public class SyncTaskCommentsFetcher extends SyncTask<SyncTaskCommentsFetcher>
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "SyncTaskCommentsFetcher";

	public Item item;
	private CommentReader reader;

	public interface SyncServiceCommentsFeedFetchedCallback
	{
		void commentsFeedFetched(Item _item);
		void commentsFeedFetchError(Item _item);
	}

	public SyncTaskCommentsFetcher(Context context, long priority, Item item)
	{
		super(context, priority);
		this.item = item;
	}

	public Object getIdentifier() {
		return item.getCommentsUrl();
	}

	@Override
	public SyncTaskCommentsFetcher call() throws Exception {
		SocialReader socialReader = SocialReader.getInstance(getContext());
		reader = new CommentReader(socialReader, item);
		Feed tempFeed = reader.fetchCommentFeed();
		socialReader.setItemComments(item, tempFeed.getComments());
		return this;
	}

	@Override
	public String toString() {
		return LOGTAG + " " + (item == null ? "" : item.getTitle());
	}
}
