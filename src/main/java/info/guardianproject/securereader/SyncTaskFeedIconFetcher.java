package info.guardianproject.securereader;

import android.content.Context;

import com.tinymission.rss.Feed;
import com.tinymission.rss.MediaContent;

import java.net.URI;

import cz.msebera.android.httpclient.HttpStatus;
import info.guardianproject.iocipher.File;

/**
 * Class for fetching the feed icon in the background.
 * 
 */
public class SyncTaskFeedIconFetcher extends SyncTask<SyncTaskFeedIconFetcher>
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "SyncTaskFeedIconFetcher";

	public Feed feed;

	public SyncTaskFeedIconFetcher(Context context, String identifier, long priority, Feed feed)
	{
		super(context, identifier, priority);
		this.feed = feed;
	}

	@Override
	public SyncTaskFeedIconFetcher call() throws Exception {
		SocialReader socialReader = SocialReader.getInstance(getContext());
		if (feed != null && feed.getNumberOfMediaContent() > 0) {
			File targetFile = new File(socialReader.getFileSystemDir(), SocialReader.FEED_ICON_FILE_PREFIX + feed.getDatabaseId());
			if (!targetFile.exists()) {
				MediaContent mediaContent = feed.getMediaContent(0);

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
					sb.append("").append(uri.getPort());
				}
				sb.append("/favicon.ico");
				urlString = sb.toString();

				int statusCode = downloadToFile(urlString, targetFile);
				if (statusCode == HttpStatus.SC_OK) {
					mediaContent.setFileSize(targetFile.length());
					//socialReader.getStoreBitmapDimensions(mediaContent);
					mediaContent.setDownloaded(true);
				} else {
					throw new Exception("Error downloading");
				}
			}
		}
		return this;
	}

	@Override
	public String toString() {
		return LOGTAG + " " + (feed == null ? "" : feed.getFeedURL());
	}
}
