package info.guardianproject.securereader;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import info.guardianproject.securereader.SyncService.SyncTask;
import info.guardianproject.iocipher.*;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.tinymission.rss.MediaContent;

public class SyncServiceMediaDownloader implements Runnable
{
	public final static boolean LOGGING = false;
	public final static String LOGTAG = "SyncServiceMdDwnlder";

	SyncService syncService;
	SyncService.SyncTask syncTask;
	
	Messenger messenger;
	Handler runHandler;

	InputStream inputStream;
		
	public SyncServiceMediaDownloader(SyncService _syncService, SyncService.SyncTask _syncTask)
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

	private void copyFile(File src, File dst) throws IOException
	{
		InputStream in = new FileInputStream(src);
		OutputStream out = new FileOutputStream(dst);

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

	public void stop() {
		try {
			inputStream.close();
		} catch (IOException ioe) {
			if (LOGGING) ioe.printStackTrace();
		}
	}

	@Override
	public void run() 
	{		
		if (LOGGING)
			Log.v(LOGTAG, "SyncServiceMediaDownloader: run");

		File savedFile = null;
		inputStream = null;

		MediaContent mediaContent = syncTask.mediaContent;
		
		if (mediaContent.getUrl() != null && !(mediaContent.getUrl().isEmpty()))
		{
			try
			{
				File possibleFile = new File(SocialReader.getInstance(syncService.getApplicationContext()).getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
				if (possibleFile.exists())
				{
					if (LOGGING)
						Log.v(LOGTAG, "Image already downloaded: " + possibleFile.getAbsolutePath());
				}
				else if (mediaContent.getUrl().startsWith("file:///android_asset/"))
				{
					if (LOGGING)
						Log.v(LOGTAG, "Downloading " + mediaContent.getUrl());
					
					BufferedOutputStream bos = null;
					
					savedFile = new File(SocialReader.getInstance(syncService.getApplicationContext()).getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
					bos = new BufferedOutputStream(new FileOutputStream(savedFile));

					inputStream = syncService.getApplicationContext().getResources().getAssets().open(mediaContent.getUrl().substring(22));

					byte data[] = new byte[1024];
					int count;
					long total = 0;
					while ((count = inputStream.read(data)) != -1)
					{
						total += count;
						bos.write(data, 0, count);
					}
					inputStream.close();
					bos.close();
					
					mediaContent.setFileSize(total);
					mediaContent.setDownloaded(true);
				}
				else if (mediaContent.getUrl().startsWith("file:///"))
				{
					if (LOGGING)
						Log.v(LOGTAG, "Have a file:/// url");
					URI existingFileUri = new URI(mediaContent.getUrl());

					File existingFile = new File(existingFileUri);

					//savedFile = new File(((App)syncService.getApplication()).socialReader.getFileSystemCache(), mediaContent.getDatabaseId() + ".jpg");
					savedFile = new File(SocialReader.getInstance(syncService.getApplicationContext()).getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
					copyFile(existingFile, savedFile);
					if (LOGGING)
						Log.v(LOGTAG, "Copy should have worked: " + savedFile.getAbsolutePath());
				}
				else 
				{
					/*
					// Replacement
					HttpURLConnection connection = null;
					if (SocialReader.getInstance(syncService.getApplicationContext()).useTor())
					{
						java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 8118));
						if (mediaContent.getUrl().startsWith("https")) {
							connection = (HttpsURLConnection) new URL(mediaContent.getUrl()).openConnection(proxy);
						} else {
							connection = (HttpURLConnection) new URL(mediaContent.getUrl()).openConnection(proxy);
						}
					}
					else {
						if (mediaContent.getUrl().startsWith("https")) {
							connection = (HttpsURLConnection) new URL(mediaContent.getUrl()).openConnection();
						} else {
							connection = (HttpURLConnection) new URL(mediaContent.getUrl()).openConnection();
						}
					}		
					
					inputStream = connection.getInputStream();
					savedFile = new File(SocialReader.getInstance(syncService.getApplicationContext()).getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
					BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(savedFile));
					byte data[] = new byte[1024];
					int count;
					long total = 0;
					while ((count = inputStream.read(data)) != -1)
					{
						total += count;
						bos.write(data, 0, count);
						//publishProgress((int) (total / size * 100));
					}

					inputStream.close();
					bos.close();
					mediaContent.setFileSize(savedFile.length());
					mediaContent.setDownloaded(true);
					// End Replacement
					*/
					SocialReader socialReader = SocialReader.getInstance(syncService.getApplicationContext());

					HttpClient httpClient = socialReader.getHttpClient();


					HttpGet httpGet = new HttpGet(mediaContent.getUrl());
					httpGet.setHeader("User-Agent", SocialReader.USERAGENT);
					
					if (LOGGING) 
						Log.v(LOGTAG,"Downloading: "+mediaContent.getUrl());
					HttpResponse response = httpClient.execute(httpGet);

					int statusCode = response.getStatusLine().getStatusCode();
					if (statusCode != HttpStatus.SC_OK)
					{
						if (LOGGING)
							Log.w(LOGTAG, "Error " + statusCode + " while retrieving bitmap");
					} 
					else 
					{	
						HttpEntity entity = response.getEntity();
						if (entity == null)
						{
							if (LOGGING)
								Log.v(LOGTAG, "MediaDownloader: no response");
						}
						else 
						{
							if (mediaContent.getType() != null) { 
								if (LOGGING)
									Log.v(LOGTAG, "MediaDownloader: " + mediaContent.getType().toString());
							}
							
							savedFile = new File(SocialReader.getInstance(syncService.getApplicationContext()).getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());

							BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(savedFile));
							inputStream = entity.getContent();
							long size = entity.getContentLength();

							byte data[] = new byte[8192];
							int count;
							long total = 0;
							while ((count = inputStream.read(data)) != -1)
							{
								total += count;
								bos.write(data, 0, count);
								//publishProgress((int) (total / size * 100));
							}

							inputStream.close();
							bos.close();
							entity.consumeContent();
							
							if (size != total)
							{
								if (LOGGING)
									Log.e(LOGTAG, "File length mismatch!!!!!!!!!");
							}
							
							mediaContent.setFileSize(size);
							mediaContent.setDownloaded(true);
						}
					}					
				}

				SocialReader sr = SocialReader.getInstance(syncService.getApplicationContext());
				// Should make sure this an image before calling getStoreBitmapDimensions
				sr.getStoreBitmapDimensions(mediaContent);
				sr.setMediaContentDownloaded(mediaContent);
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				if (LOGGING)
					e.printStackTrace();
			}
		}

		// Go back to the main thread
		Message m = Message.obtain();            
        //Bundle b = new Bundle();
        //b.putLong("databaseid", feed.getDatabaseId());
		//m.setData(b);
		
        try {
			messenger.send(m);
		} catch (RemoteException e) {
			if (LOGGING)
				e.printStackTrace();
		}
	}
}
