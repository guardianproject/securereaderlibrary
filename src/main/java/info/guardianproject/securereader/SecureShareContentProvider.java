package info.guardianproject.securereader;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;

public class SecureShareContentProvider extends ContentProvider {

	public static final String LOGTAG = "SecureShareContentProvider";
	public static final boolean LOGGING = false;

	private static final String[] DEFAULT_COLUMNS = {
			OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
	};

	public static final String CONTENT_URI = "content://" + BuildConfig.APPLICATION_ID + "/";

	@Override
	public boolean onCreate() {
		return true;
	}

	@Nullable
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

		File sharedFile = getFileFromUri(uri);
		if (sharedFile == null) {
			return null;
		}

		if (projection == null) {
			projection = DEFAULT_COLUMNS;
		}
		ArrayList<String> cols = new ArrayList<>();
		ArrayList<Object> values = new ArrayList<>();
		int i = 0;
		for (String col : projection) {
			if (OpenableColumns.DISPLAY_NAME.equals(col)) {
				cols.add(col);
				values.add(sharedFile.getName());
			} else if (OpenableColumns.SIZE.equals(col)) {
				cols.add(col);
				values.add(sharedFile.length());
			}
		}
		final MatrixCursor cursor = new MatrixCursor(cols.toArray(new String[0]), 1);
		cursor.addRow(values);
		return cursor;
	}

	private File getFileFromUri(Uri uri) {
		// Check URI starts with our content_uri
		if (uri == null || !uri.toString().startsWith(CONTENT_URI)) {
			return null;
		}
		String path = uri.toString().substring(CONTENT_URI.length());
		if (TextUtils.isEmpty(path)) {
			return null;
		}
		if (!path.startsWith("opml/")) {
			return null; // currently only support OPML export share
		}
		String fileName = path.substring(path.indexOf("/") + 1);
		if (TextUtils.isEmpty(fileName)) {
			return null;
		}
		File file = new File("/" + SocialReader.VFS_SHARE_DIRECTORY, path);
		if (!file.exists()) {
			return null;
		}
		return file;
	}

	@Nullable
	@Override
	public String getType(Uri uri) {
		if (uri == null || !uri.toString().startsWith(CONTENT_URI)) {
			return null;
		}
		String path = uri.toString().substring(CONTENT_URI.length());
		if (TextUtils.isEmpty(path)) {
			return null;
		}
		if (!path.startsWith("opml/")) {
			return null; // currently only support OPML export share
		}
		return "text/x-opml";
	}

	@Nullable
	@Override
	public Uri insert(Uri uri, ContentValues contentValues) {
		throw new RuntimeException("Not supported");
	}

	@Override
	public int delete(Uri uri, String s, String[] strings) {
		throw new RuntimeException("Not supported");
	}

	@Override
	public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
		throw new RuntimeException("Not supported");
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		File sharedFile = getFileFromUri(uri);
		if (sharedFile == null) {
			return null;
		}

		try {
			ParcelFileDescriptor[] pipes = ParcelFileDescriptor.createPipe();
			ParcelFileDescriptor readSide = pipes[0];
			ParcelFileDescriptor writeSide = pipes[1];

			InputStream is = new FileInputStream(sharedFile);

			// start the transfer thread
			new TransferThread(is, new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)).start();

			return readSide;
		} catch (IOException e) {
			throw new FileNotFoundException("Failed to open " + uri.toString());
		}
	}

	static class TransferThread extends Thread {
		final InputStream mIn;
		final OutputStream mOut;

		TransferThread(InputStream in, OutputStream out) {
			super("ParcelFileDescriptor Transfer Thread");
			mIn = in;
			mOut = out;
			setDaemon(true);
		}

		@Override
		public void run() {
			byte[] buf = new byte[4096];
			int len;

			try {
				while ((len = mIn.read(buf)) > 0) {
					mOut.write(buf, 0, len);
				}
				mOut.flush(); // just to be safe
			} catch (IOException e) {
				Log.e("TransferThread", e.toString());
			}
			finally {
				try {
					mIn.close();
				} catch (IOException e) {
				}
				try {
					mOut.close();
				} catch (IOException e) {
				}
			}
		}
	}
}
