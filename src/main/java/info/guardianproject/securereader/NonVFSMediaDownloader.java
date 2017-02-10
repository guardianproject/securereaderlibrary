package info.guardianproject.securereader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.tinymission.rss.MediaContent;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.client.ClientProtocolException;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import info.guardianproject.netcipher.client.StrongBuilder;
import info.guardianproject.netcipher.client.StrongHttpClientBuilder;

public class NonVFSMediaDownloader extends AsyncTask<MediaContent, Integer, File>
{
	public final static String LOGTAG = "NonVFSMediaDownloader";
	public final static boolean LOGGING = false;
	
	SocialReader socialReader;
	MediaDownloaderCallback callback;
	File savedFile;		

	public NonVFSMediaDownloader(SocialReader _socialReader, File locationToSave)
	{
		super();
		socialReader = _socialReader;
		savedFile = locationToSave;
	}

	public interface MediaDownloaderCallback
	{
		public void mediaDownloaded(java.io.File mediaFile);
	}

	public void setMediaDownloaderCallback(MediaDownloaderCallback mdc)
	{
		callback = mdc;
	}

	@Override
	protected File doInBackground(MediaContent... params)
	{
		if (LOGGING) 
			Log.v(LOGTAG, "doInBackground");

		if (params.length == 0)
			return null;

		final MediaContent mediaContent = params[0];

		try {
			StrongHttpClientBuilder builder = StrongHttpClientBuilder.forMaxSecurity(socialReader.applicationContext);
			if (socialReader.useProxy()) {

                if (socialReader.getProxyType().equalsIgnoreCase("socks"))
				    builder.withSocksProxy();
                else
                    builder.withHttpProxy();

				//				    httpClient.useProxy(true, socialReader.getProxyType(), socialReader.getProxyHost(), socialReader.getProxyPort());

			}

			builder.build(new StrongBuilder.Callback<HttpClient>() {
				@Override
				public void onConnected(HttpClient httpClient) {


					doGet (httpClient, mediaContent);
				}

				@Override
				public void onConnectionException(Exception e) {

				}

				@Override
				public void onTimeout() {

				}

				@Override
				public void onInvalid() {

				}
			});
		}
		catch (Exception e)
		{
			Log.e(LOGTAG,"error fetching feed",e);
		}



		return savedFile;
	}

	@Override
	protected void onProgressUpdate(Integer... progress)
	{
		//if (LOGGING)
		// Log.v(LOGTAG, progress[0].toString());
	}

	@Override
	protected void onPostExecute(File cachedFile)
	{
		if (callback != null)
		{
			callback.mediaDownloaded(cachedFile);
		}
	}

	private File doGet (HttpClient httpClient, MediaContent mediaContent)
	{


		if (mediaContent.getUrl() != null && !(mediaContent.getUrl().isEmpty()))
		{
			try
			{
				Uri uriMedia = Uri.parse(mediaContent.getUrl());

				HttpGet httpGet = new HttpGet(mediaContent.getUrl());
				httpGet.setHeader("User-Agent", SocialReader.USERAGENT);

				HttpResponse response = httpClient.execute(httpGet);

				int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode != HttpStatus.SC_OK)
				{
					if (LOGGING)
						Log.w(LOGTAG, "Error " + statusCode + " while retrieving file");
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

				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(savedFile));
				InputStream inputStream = entity.getContent();
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

			}
			catch (ClientProtocolException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
			catch (IOException e)
			{
				if (LOGGING)
					e.printStackTrace();
			}
		}

        return savedFile;
	}
}
