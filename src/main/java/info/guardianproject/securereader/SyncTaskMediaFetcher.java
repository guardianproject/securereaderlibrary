package info.guardianproject.securereader;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.text.TextUtils;

import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import cz.msebera.android.httpclient.HttpStatus;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;

public class SyncTaskMediaFetcher extends SyncTask<SyncTaskMediaFetcher> {
	private final static boolean LOGGING = false;
	private final static String LOGTAG = "SyncTaskMediaFetcher";

	public interface SyncTaskMediaFetcherCallback
	{
		void mediaAddedToQueue(MediaContent mediaContent);
		void mediaDownloadStarted(MediaContent mediaContent);
		void mediaDownloaded(MediaContent mediaContent, File file);
		void mediaDownloadError(MediaContent mediaContent);
	}

	public final Item item;
	public final MediaContent mediaContent;
    public File targetFile;
	private final Handler handler; // The handler to use when calling the callbacks from in here
	private final List<SyncTaskMediaFetcherCallback> callbacks;

    public SyncTaskMediaFetcher(Context context, Handler handler, String identifier, long priority, Item item, MediaContent mediaContent)
	{
		super(context, identifier, priority);
		this.item = item;
		this.mediaContent = mediaContent;
		this.handler = handler;
		this.callbacks = new ArrayList<>();
	}

	public void addCallback(SyncTaskMediaFetcherCallback callback) {
		if (!callbacks.contains(callback)) {
			callbacks.add(callback);
		}
	}

	public List<SyncTaskMediaFetcherCallback> getCallbacks() {
    	return callbacks;
	}

	@Override
	public SyncTaskMediaFetcher call() throws Exception {
		SocialReader socialReader = SocialReader.getInstance(getContext());
		if (mediaContent != null && !TextUtils.isEmpty(mediaContent.getUrl())) {
            targetFile = new File(socialReader.getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
			if (!targetFile.exists()) {

				// Tell listeners we are downloading
				for (final SyncTaskMediaFetcherCallback callback : getCallbacks()) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							callback.mediaDownloadStarted(mediaContent);
						}
					});
				}

				int statusCode = downloadToFile(mediaContent.getUrl(), targetFile);
				if (statusCode == HttpStatus.SC_OK) {
					mediaContent.setFileSize(targetFile.length());
					mediaContent.setDownloaded(true);
					mediaContent.setSyncStatus(SyncStatus.OK);
					if (mediaContent.getMediaContentType() == MediaContent.MediaContentType.FULLTEXT) {
						processFullText(targetFile);
					} else {
						getBitmapDimensions(targetFile);
					}
					SocialReader.getInstance(getContext()).databaseAdapter.addOrUpdateItemMedia(mediaContent);
				} else {
					mediaContent.setSyncStatus(SyncStatus.ERROR_UNKNOWN);
					SocialReader.getInstance(getContext()).databaseAdapter.addOrUpdateItemMedia(mediaContent);
					throw new Exception("Error downloading");
				}
			} else if (!mediaContent.getDownloaded()) {
				// Exists, but not marked as downloaded. Do that now.
				mediaContent.setFileSize(targetFile.length());
				mediaContent.setDownloaded(true);
				mediaContent.setSyncStatus(SyncStatus.OK);
				getBitmapDimensions(targetFile);
				SocialReader.getInstance(getContext()).databaseAdapter.addOrUpdateItemMedia(mediaContent);
			}
		} else {
			mediaContent.setSyncStatus(SyncStatus.ERROR_BAD_URL);
			SocialReader.getInstance(getContext()).databaseAdapter.addOrUpdateItemMedia(mediaContent);
			throw new Exception("Invalid URL");
		}
		return this;
	}

	private void getBitmapDimensions(File mediaFile)
	{
		try
		{
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(mediaFile));
			BitmapFactory.decodeStream(bis, null, o);
			bis.close();
			if (o.outWidth > 0 && o.outHeight > 0)
			{
				mediaContent.setWidth(o.outWidth);
				mediaContent.setHeight(o.outHeight);
			}
		}
		catch (Exception ignored)
		{
		}
	}

	private void processFullText(File targetFile) {
		try {
			SocialReader socialReader = SocialReader.getInstance(getContext());
			if (socialReader.getFullTextPreprocessor() != null) {
				String result = socialReader.getFullTextPreprocessor().onFullTextDownloaded(item, mediaContent, targetFile);
				if (result != null) {
					// Data seems to have been processed, save back to file
					BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFile));
					bos.write(result.getBytes());
					bos.close();
				}
			}
		} catch (Exception ignored) {}
	}

	@Override
	public String toString() {
		return LOGTAG + " " + (mediaContent == null ? "" : mediaContent.getUrl());
	}
}
