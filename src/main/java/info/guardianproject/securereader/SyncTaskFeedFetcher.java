package info.guardianproject.securereader;

import android.content.Context;

import com.tinymission.rss.Feed;
import com.tinymission.rss.Reader;

/**
 * Class for fetching the feed content in the background.
 * 
 */
public class SyncTaskFeedFetcher extends SyncTask<SyncTaskFeedFetcher>
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "SyncTaskFeedFetcher";

	public Feed feed;

	public interface SyncTaskFeedFetcherCallback
	{
		void feedFetched(Feed _feed);
		void feedFetchError(Feed _feed);
	}

	public SyncTaskFeedFetcher(Context context, int priority, Feed feed)
	{
		super(context, priority);
		this.feed = feed;
	}

	public Object getIdentifier() {
		return feed.getFeedURL();
	}

	@Override
	public SyncTaskFeedFetcher call() throws Exception {
		SocialReader socialReader = SocialReader.getInstance(getContext());
		Reader reader = new Reader(socialReader, feed);
		feed = reader.fetchFeed();
		if (feed != null) {
			SocialReader.getInstance(getContext()).setFeedAndItemData(feed);
		}
		return this;
	}

	@Override
	public String toString() {
		return LOGTAG + " " + (feed == null ? "" : feed.getFeedURL());
	}
}
