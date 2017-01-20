package info.guardianproject.securereader;

import android.os.AsyncTask;
import android.util.Log;

import com.tinymission.rss.Feed;
import com.tinymission.rss.Reader;

/**
 * Class for fetching the feed content in the background.
 * 
 */
public class FeedFetcher extends AsyncTask<Feed, Integer, Feed>
{
	public final static String LOGTAG = "FeedFetcher";
	public final static boolean LOGGING = true;

	SocialReader socialReader;

	FeedFetchedCallback feedFetchedCallback;

	Feed originalFeed;

	public void setFeedUpdatedCallback(FeedFetchedCallback _feedFetchedCallback)
	{
		feedFetchedCallback = _feedFetchedCallback;
	}

	public interface FeedFetchedCallback
	{
		public void feedFetched(Feed _feed);
	}

	public FeedFetcher(SocialReader _socialReader)
	{
		super();
		socialReader = _socialReader;
	}

	@Override
	protected Feed doInBackground(Feed... params)
	{
		Feed feed = new Feed();
		if (params.length > 0)
		{
			feed = params[0];
			originalFeed = feed;

			Reader reader = new Reader(socialReader, feed);
			feed = reader.fetchFeed();
		}

		socialReader.setFeedAndItemData(feed);

		return feed;
	}

	@Override
	protected void onPostExecute(Feed feed)
	{		
		if (feedFetchedCallback != null)
		{
			feedFetchedCallback.feedFetched(feed);
		}
	}
}
