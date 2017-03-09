package info.guardianproject.securereader;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import com.tinymission.rss.MediaContent;

import java.io.BufferedInputStream;

import cz.msebera.android.httpclient.HttpStatus;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;

public class SyncTaskMediaFetcher extends SyncTask<SyncTaskMediaFetcher> {
	private final static boolean LOGGING = false;
	private final static String LOGTAG = "SyncTaskMediaFetcher";

	public final MediaContent mediaContent;

	public SyncTaskMediaFetcher(Context context, int priority, MediaContent mediaContent)
	{
		super(context, priority);
		this.mediaContent = mediaContent;
	}

	public Object getIdentifier() {
		return mediaContent.getUrl();
	}

	@Override
	public SyncTaskMediaFetcher call() throws Exception {
		SocialReader socialReader = SocialReader.getInstance(getContext());
		if (mediaContent != null && !TextUtils.isEmpty(mediaContent.getUrl())) {
			File targetFile = new File(socialReader.getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
			if (!targetFile.exists()) {
				int statusCode = downloadToFile(mediaContent.getUrl(), targetFile);
				if (statusCode == HttpStatus.SC_OK) {
					mediaContent.setFileSize(targetFile.length());
					mediaContent.setDownloaded(true);
					getBitmapDimensions(targetFile);
					SocialReader.getInstance(getContext()).databaseAdapter.addOrUpdateItemMedia(mediaContent);
				} else {
					throw new Exception("Error downloading");
				}
			}
		} else {
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

	@Override
	public String toString() {
		return LOGTAG + " " + (mediaContent == null ? "" : mediaContent.getUrl());
	}
}
