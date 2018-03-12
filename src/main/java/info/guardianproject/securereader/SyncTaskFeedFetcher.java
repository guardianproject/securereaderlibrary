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

	public SyncTaskFeedFetcher(Context context, String identifier, long priority, Feed feed)
	{
		super(context, identifier, priority);
		this.feed = feed;
	}

	@Override
	public SyncTaskFeedFetcher call() throws Exception {
		SocialReader socialReader = SocialReader.getInstance(getContext());
		Reader reader = new Reader(socialReader, feed);
		Feed feed = reader.fetchFeed();
		if (feed != null) {
			this.feed = feed;
			SocialReader.getInstance(getContext()).setFeedAndItemData(feed);
		}
		return this;
	}

	@Override
	public String toString() {
		return LOGTAG + " " + (feed == null ? "" : feed.getFeedURL());
	}
}
