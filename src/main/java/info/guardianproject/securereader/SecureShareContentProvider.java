//https://dev.guardianproject.info/projects/gibberbot/wiki/Intent_API
//http://www.grokkingandroid.com/handling-binary-data-with-contentproviders/
//http://www.grokkingandroid.com/android-tutorial-writing-your-own-content-provider/

package info.guardianproject.securereader;

import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

public class SecureShareContentProvider extends ContentProvider {

	public static final String LOGTAG = "SecureShareContentProvider";
	public static final boolean LOGGING = false;
	
	public static final int ITEM_ID = 0;
	
	@Override
	public boolean onCreate() {
		return true;
	}
	
	private java.io.File getNonVirtualFileSystemInternalDir()
	{
		java.io.File filesDir;
		filesDir = this.getContext().getDir(SocialReader.FILES_DIR_NAME, Context.MODE_PRIVATE);
		return filesDir;
	}	
	
	@Override
	public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
		// Should return a real type here
		return null;
	}
	
	// Check: openPipeHelper in 
	//https://github.com/android/platform_frameworks_base/blob/master/core/java/android/content/ContentProvider.java
	// for example to use IOCipher
	@Override 
	public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
	     return ParcelFileDescriptor.open(new java.io.File(getNonVirtualFileSystemInternalDir(),SocialReader.CONTENT_BUNDLE_FILE_PREFIX + uri.getLastPathSegment() + "." + SocialReader.CONTENT_SHARING_EXTENSION), ParcelFileDescriptor.MODE_READ_ONLY);
    }	
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// Match any authority. The app decides and restricts the authorities this provider responds to
		// see http://stackoverflow.com/a/10791144/844882.
		UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
		matcher.addURI(uri.getAuthority(), "item/#", ITEM_ID);
		int match = matcher.match(uri);
        switch (match)
        {
            case ITEM_ID:
                return "vnd.android.cursor.item/item";
        }
        return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}
}
