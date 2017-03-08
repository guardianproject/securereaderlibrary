package info.guardianproject.securereader;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.tinymission.rss.Feed;
import com.tinymission.rss.MediaContent;
import com.tinymission.rss.Reader;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.URI;

/**
 * Class for fetching the feed content in the background.
 * 
 */
public class SyncTaskFeedFetcher extends SyncTask
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "SyncTaskFeedFetcher";

	public Feed feed;
	private final SyncServiceFeedFetchedCallback callback;
	Reader reader;
	
	public interface SyncServiceFeedFetchedCallback
	{
		public void feedFetched(Feed _feed);
	}

	public SyncTaskFeedFetcher(Context context, int priority, Feed feed, SyncServiceFeedFetchedCallback callback)
	{
		super(context, priority);
		this.feed = feed;
		this.callback = callback;
	}

	public void stop() {
		if (reader != null) reader.stop();
	}
	
	@Override
	public SyncTask call() {
		SocialReader socialReader = SocialReader.getInstance(getContext());
		reader = new Reader(socialReader, feed);
		feed = reader.fetchFeed();

		// Download feed image?
		try {
			if (feed != null && feed.getNumberOfMediaContent() > 0) {
				File feedIconFile = new File(socialReader.getFileSystemDir(), SocialReader.FEED_ICON_FILE_PREFIX + feed.getDatabaseId());
				if (!feedIconFile.exists()) {
					MediaContent mediaContent = feed.getMediaContent(0);
					HttpClient httpClient = socialReader.getHttpClient();

					// TODO, use favicon or image from feed?
					//String urlString = mediaContent.getUrl();
					String urlString = feed.getFeedURL();
					URI uri = URI.create(urlString);
					StringBuilder sb = new StringBuilder();
					sb.append(uri.getScheme());
					sb.append("://");
					sb.append(uri.getHost());
					if (uri.getPort() != -1) {
						sb.append(":");
						sb.append("" + uri.getPort());
					}
					sb.append("/favicon.ico");
					urlString = sb.toString();

					HttpGet httpGet = new HttpGet(urlString);
					httpGet.setHeader("User-Agent", SocialReader.USERAGENT);
					if (LOGGING)
						Log.v(LOGTAG, "Downloading: " + mediaContent.getUrl());
					HttpResponse response = httpClient.execute(httpGet);
					int statusCode = response.getStatusLine().getStatusCode();
					if (statusCode != HttpStatus.SC_OK) {
						if (LOGGING)
							Log.w(LOGTAG, "Error " + statusCode + " while retrieving bitmap");
					} else {
						HttpEntity entity = response.getEntity();
						if (entity == null) {
							if (LOGGING)
								Log.v(LOGTAG, "MediaDownloader: no response");
						} else {
							BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(feedIconFile));
							InputStream inputStream = entity.getContent();
							long size = entity.getContentLength();

							byte data[] = new byte[8192];
							int count;
							long total = 0;
							while ((count = inputStream.read(data)) != -1) {
								total += count;
								bos.write(data, 0, count);
							}

							inputStream.close();
							bos.close();
							entity.consumeContent();

							if (size != total) {
								if (LOGGING)
									Log.e(LOGTAG, "File length mismatch!!!!!!!!!");
							}
							mediaContent.setFileSize(size);
							socialReader.getStoreBitmapDimensions(mediaContent);
							mediaContent.setDownloaded(true);
						}
					}
				}
			}
		}
		catch (Exception ignored) {}


		
		if (LOGGING)
			Log.v(LOGTAG,"syncTask.feed should be complete");
		return this;
	}
}
