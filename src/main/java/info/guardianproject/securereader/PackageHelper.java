package info.guardianproject.securereader;

import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

public class PackageHelper {

	public static boolean isAppInstalled(Context context, String uri) {
		PackageManager pm = context.getPackageManager();
		boolean installed = false;
		try {
			pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
			installed = true;
		} catch (PackageManager.NameNotFoundException e) {
			installed = false;
		}
		return installed;
	}

	public static boolean canIntentBeHandled(Context context, Intent intent) {
		PackageManager manager = context.getPackageManager();
		List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
		return (infos.size() > 0);
	}

	public static AlertDialog showDownloadDialog(final Context context,
			int title, int prompt, int ok,
			int cancel, final String uriString) {
		AlertDialog.Builder downloadDialog = new AlertDialog.Builder(context);
		downloadDialog.setTitle(title);
		downloadDialog.setMessage(prompt);
		downloadDialog.setPositiveButton(ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialogInterface, int i) {
						Uri uri = Uri.parse(uriString);
						Intent intent = new Intent(Intent.ACTION_VIEW, uri);
						context.startActivity(intent);
					}
				});
		downloadDialog.setNegativeButton(cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialogInterface, int i) {
					}
				});
		return downloadDialog.show();
	}
}
