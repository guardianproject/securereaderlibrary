package info.guardianproject.securereader;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.Callable;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.client.utils.HttpClientUtils;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

/**
 * Created by N-Pex on 2017-03-03.
 */

abstract public class SyncTask<T> implements Callable<T> {
    public static final long MAXTIME = 3600000; // 60 * 60 * 1000 = 1 hour;
    public static String LOGTAG = "SyncTask";
    public final static boolean LOGGING = false;

    public enum SyncTaskStatus {
        ERROR, CREATED, QUEUED, STARTED, FINISHED, CANCELLED
    };
    public SyncTaskStatus status = SyncTaskStatus.CREATED;
    public long startTime = -1;

    private final Context context;
    public long priority;
    public String identifier;

    public SyncTask(Context context, String identifier, long priority) {
        this.context = context;
        this.identifier = identifier;
        this.priority = priority;
        if (LOGGING) {
            Log.d(LOGTAG, "Create sync task " + identifier + " prio " + String.valueOf(priority));
        }
    }

    protected Context getContext() {
        return context;
    }

    protected int downloadToFile(String urlString, File targetFile) {
        int statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        try {
            if (urlString.startsWith("file:///android_asset/")) {
                if (LOGGING)
                    Log.v(toString(), "Downloading " + urlString + " from assets");

                BufferedOutputStream bos = null;
                bos = new BufferedOutputStream(new FileOutputStream(targetFile));

                InputStream inputStream = context.getResources().getAssets().open(urlString.substring(22));

                byte data[] = new byte[8096];
                int count;
                long total = 0;
                while ((count = inputStream.read(data)) != -1) {
                    total += count;
                    bos.write(data, 0, count);
                }
                inputStream.close();
                bos.close();
                statusCode = HttpStatus.SC_OK;
            } else if (urlString.startsWith("file:///")) {
                if (LOGGING)
                    Log.v(toString(), "Have a file:/// url");
                URI existingFileUri = new URI(urlString);
                java.io.File existingFile = new java.io.File(existingFileUri);
                copyFileFromFStoVFS(existingFile, targetFile);
            } else {
                statusCode = downloadToFileHttp(urlString, targetFile);
            }
        } catch (Exception ignored) {
        }
        return statusCode;
    }

    private int downloadToFileHttp(String urlString, File targetFile) {
        int statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        HttpResponse response = null;
        try {
            SocialReader socialReader = SocialReader.getInstance(getContext());
            HttpClient httpClient = socialReader.getHttpClient();
            if (httpClient == null || TextUtils.isEmpty(urlString)) {
                return HttpStatus.SC_INTERNAL_SERVER_ERROR;
            }
            HttpClientContext httpClientContext = HttpClientContext.create();
            HttpGet httpGet = new HttpGet(urlString);
            httpGet.setHeader("User-Agent", SocialReader.USERAGENT);
            if (LOGGING)
                Log.v(toString(), "Downloading: " + urlString);
            response = httpClient.execute(httpGet, httpClientContext);
            statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    if (LOGGING)
                        Log.v(toString(), "Downloading: no response from " + urlString);
                    statusCode = HttpStatus.SC_NO_CONTENT;
                } else {
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFile));
                    InputStream inputStream = entity.getContent();

                    byte data[] = new byte[8192];
                    int count;
                    long total = 0;
                    while ((count = inputStream.read(data)) != -1) {
                        total += count;
                        bos.write(data, 0, count);
                    }

                    inputStream.close();
                    bos.close();
                }
            }
        } catch (Exception ignored) {
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
        return statusCode;
    }

    private void copyFileFromFStoVFS(java.io.File src, info.guardianproject.iocipher.File dst) throws IOException
    {
        InputStream in = new java.io.FileInputStream(src);
        OutputStream out = new info.guardianproject.iocipher.FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[8096];
        int len;
        while ((len = in.read(buf)) > 0)
        {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }
}