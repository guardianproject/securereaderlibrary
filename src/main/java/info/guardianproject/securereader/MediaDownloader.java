package info.guardianproject.securereader;

import info.guardianproject.netcipher.client.StrongHttpsClient;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;

import com.tinymission.rss.MediaContent;

public class MediaDownloader extends AsyncTask<MediaContent, Integer, File>
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "MediaDownloader";
	
	SocialReader socialReader;
	MediaDownloaderCallback callback;
	
	public MediaDownloader(SocialReader _socialReader)
	{
		super();
		socialReader = _socialReader;
	}

	public interface MediaDownloaderCallback
	{
		public void mediaDownloaded(File mediaFile);
		public void mediaDownloadedNonVFS(java.io.File mediaFile);		
	}

	public void setMediaDownloaderCallback(MediaDownloaderCallback mdc)
	{
		callback = mdc;
	}

	private void copyFileFromFStoAppFS(java.io.File src, info.guardianproject.iocipher.File dst) throws IOException
	{
		InputStream in = new java.io.FileInputStream(src);
		OutputStream out = new info.guardianproject.iocipher.FileOutputStream(dst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0)
		{
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}	

	@Override
	protected File doInBackground(MediaContent... params)
	{
		if (LOGGING)
			Log.v(LOGTAG, "MediaDownloader: doInBackground");

		File savedFile = null;
		java.io.File nonVFSSavedFile = null;
		
		InputStream inputStream = null;

		if (params.length == 0)
			return null;

		MediaContent mediaContent = params[0];
		StrongHttpsClient httpClient = new StrongHttpsClient(socialReader.applicationContext);

		if (socialReader.relaxedHTTPS) {
			httpClient.enableSSLCompatibilityMode();
		}

		if (socialReader.useProxy())
		{
			if (LOGGING) 
				Log.v(LOGTAG, "MediaDownloader: USE_PROXY");

			httpClient.useProxy(true, socialReader.getProxyType(), socialReader.getProxyHost(), socialReader.getProxyPort());
		}
				
		if (mediaContent.getUrl() != null && !(mediaContent.getUrl().isEmpty()))
		{
			try
			{
				Uri uriMedia = Uri.parse(mediaContent.getUrl());
				if (uriMedia != null && ContentResolver.SCHEME_CONTENT.equals(uriMedia.getScheme()))
				{
					BufferedOutputStream bos = null;
					
					savedFile = new File(socialReader.getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
					bos = new BufferedOutputStream(new FileOutputStream(savedFile));
					
					inputStream = socialReader.applicationContext.getContentResolver().openInputStream(uriMedia);

					byte data[] = new byte[1024];
					int count;
					while ((count = inputStream.read(data)) != -1)
					{
						bos.write(data, 0, count);
					}
					inputStream.close();
					bos.close();

					socialReader.getStoreBitmapDimensions(mediaContent);
					return savedFile;
				}

				if (mediaContent.getUrl().startsWith("file:///"))
				{
					if (LOGGING)
						Log.v(LOGTAG, "Have a file:/// url, we probably don't need to do anything but let's check");

					savedFile = new File(socialReader.getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
					
					if (LOGGING) 
						Log.v(LOGTAG, "Does " + socialReader.getFileSystemDir() + SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId() + " exist?");
					
					if (!savedFile.exists()) {
						if (LOGGING) 
							Log.v(LOGTAG, "Saved File Doesn't Exist");
						
						URI existingFileUri = new URI(mediaContent.getUrl());
						java.io.File existingFile = new java.io.File(existingFileUri);
						copyFileFromFStoAppFS(existingFile, savedFile);
					}
					
					if (LOGGING) 
						Log.v(LOGTAG, "Copy should have worked: " + savedFile.getAbsolutePath());
					
					socialReader.getStoreBitmapDimensions(mediaContent);
					return savedFile;
				}

				HttpGet httpGet = new HttpGet(mediaContent.getUrl());
				httpGet.setHeader("User-Agent", SocialReader.USERAGENT);
				
				HttpResponse response = httpClient.execute(httpGet);

				int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode != HttpStatus.SC_OK)
				{
					if (LOGGING) 
						Log.w(LOGTAG, "Error " + statusCode + " while retrieving file from " + mediaContent.getUrl());
					return null;
				}

				HttpEntity entity = response.getEntity();
				if (entity == null)
				{
					if (LOGGING) 
						Log.v(LOGTAG, "MediaDownloader: no response");

					return null;
				}

				if (LOGGING) 
					Log.v(LOGTAG, "MediaDownloader: " + mediaContent.getType().toString());

				savedFile = new File(socialReader.getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());

				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(savedFile));
				inputStream = entity.getContent();
				long size = entity.getContentLength();

				byte data[] = new byte[1024];
				int count;
				long total = 0;
				while ((count = inputStream.read(data)) != -1)
				{
					total += count;
					bos.write(data, 0, count);
					publishProgress((int) (total / size * 100));
				}

				inputStream.close();
				bos.close();
				entity.consumeContent();

				socialReader.getStoreBitmapDimensions(mediaContent);
			}
			catch (Exception e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
		}

		return savedFile;
	}

	@Override
	protected void onProgressUpdate(Integer... progress)
	{
		//if (LOGGING)
			//Log.v(LOGTAG, progress[0].toString());
	}

	@Override
	protected void onPostExecute(File cachedFile)
	{
		if (callback != null)
		{
			callback.mediaDownloaded(cachedFile);
		}
	}
	
	@Override
	protected void onCancelled () {
		if (LOGGING) 
			Log.v(LOGTAG, "****onCancelled****");
	}
}
