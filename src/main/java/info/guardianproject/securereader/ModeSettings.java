package info.guardianproject.securereader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.collect.Lists;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class ModeSettings
{
	public static final String LOGTAG = "ModeSettings";
	public static final boolean LOGGING = false;

	protected final Context context;
	protected final SharedPreferences mPrefs;

	// Use these constants when listening to changes, to see what property has
	// changed!
	//
	public static String KEY_SYNC_FREQUENCY;
	public static String KEY_SYNC_OVER_WIFI;
	public static String KEY_SYNC_OVER_DATA;
	public static String KEY_SYNC_MEDIA_RICH;
	public static String KEY_ARTICLE_EXPIRATION;
	public static String KEY_POWERSAVE_ENABLED;
	public static String KEY_POWERSAVE_PERCENTAGE;

	public ModeSettings(Context context, String fileName)
	{
		this.context = context;

		KEY_SYNC_FREQUENCY = context.getString(R.string.pref_key_sync_frequency);
		KEY_SYNC_OVER_WIFI = context.getString(R.string.pref_key_sync_over_wifi);
		KEY_SYNC_OVER_DATA = context.getString(R.string.pref_key_sync_over_data);
		KEY_SYNC_MEDIA_RICH = context.getString(R.string.pref_key_sync_media_rich);
		KEY_ARTICLE_EXPIRATION = context.getString(R.string.pref_key_article_expiration);
		KEY_POWERSAVE_ENABLED = context.getString(R.string.pref_key_save_power_enabled);
		KEY_POWERSAVE_PERCENTAGE = context.getString(R.string.pref_key_save_power_percentage);

		mPrefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
	}

	public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener)
	{
		mPrefs.registerOnSharedPreferenceChangeListener(listener);
	}

	public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener)
	{
		mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
	}

	public enum SyncFrequency
	{
		Manual, WhenRunning, InBackground
	}

	public SyncFrequency syncFrequency()
	{
		return Enum.valueOf(SyncFrequency.class, mPrefs.getString(KEY_SYNC_FREQUENCY, SyncFrequency.Manual.name()));
	}

	public void setSyncFrequency(SyncFrequency syncFreqency)
	{
		mPrefs.edit().putString(KEY_SYNC_FREQUENCY, syncFreqency.name()).apply();
	}

	public enum Sync
	{
		Summary, FullText, Media
	}

	public Set<Sync> syncWifi()
	{
		Set<Sync> ret = new HashSet<>();
		try {
			Set<String> values = mPrefs.getStringSet(KEY_SYNC_OVER_WIFI, null);
			if (values != null) {
				for (String s : values) {
					ret.add(Enum.valueOf(Sync.class, s));
				}
			}
		} catch (Exception ignored) {
			Log.e(LOGTAG, "Error: invalid setting for syncWifi");
		}
		return ret;
	}

	public void setSyncWifi(Set<Sync> settings) {
		Set<String> val = new HashSet<>();
		if (settings != null) {
			for (Sync s : settings) {
				val.add(s.name());
			}
		}
		mPrefs.edit().putStringSet(KEY_SYNC_OVER_WIFI, val).apply();
	}

	public Set<Sync> syncData()
	{
		Set<Sync> ret = new HashSet<>();
		try {
			Set<String> values = mPrefs.getStringSet(KEY_SYNC_OVER_DATA, null);
			if (values != null) {
				for (String s : values) {
					ret.add(Enum.valueOf(Sync.class, s));
				}
			}
		} catch (Exception ignored) {
			Log.e(LOGTAG, "Error: invalid setting for syncData");
		}
		return ret;
	}

	public void setSyncData(Set<Sync> settings) {
		Set<String> val = new HashSet<>();
		if (settings != null) {
			for (Sync s : settings) {
				val.add(s.name());
			}
		}
		mPrefs.edit().putStringSet(KEY_SYNC_OVER_DATA, val).apply();
	}

	public boolean mediaRich()
	{
		return mPrefs.getBoolean(KEY_SYNC_MEDIA_RICH, false);
	}

	public void setMediaRich(boolean mediaRich)
	{
		mPrefs.edit().putBoolean(KEY_SYNC_MEDIA_RICH, mediaRich).apply();
	}



	public enum ArticleExpiration
	{
		AfterRead, OneDay, OneWeek, OneMonth
	}

	/**
	 * @return Gets when articles are expired
	 *
	 */
	public ArticleExpiration articleExpiration()
	{
		return Enum.valueOf(ArticleExpiration.class, mPrefs.getString(KEY_ARTICLE_EXPIRATION, context.getResources().getString(R.string.article_expiration_default)));
	}

	public long articleExpirationMillis() {
		if (articleExpiration() == ArticleExpiration.OneDay) {
			return   86400000L;
		} else if (articleExpiration() == ArticleExpiration.OneWeek) {
			return  604800000L;
		} else if (articleExpiration() == ArticleExpiration.OneMonth) {
			return 2592000000L;
		} else {
			return -1L;
		}
	}

	/**
	 * @return Sets when articles are expired
	 *
	 */
	public void setArticleExpiration(ArticleExpiration articleExpiration)
	{
		mPrefs.edit().putString(KEY_ARTICLE_EXPIRATION, articleExpiration.name()).apply();
	}

	public boolean powerSaveEnabled()
	{
		return mPrefs.getBoolean(KEY_POWERSAVE_ENABLED, true);
	}

	public void setPowerSaveEnabled(boolean enabled)
	{
		mPrefs.edit().putBoolean(KEY_POWERSAVE_ENABLED, enabled).apply();
	}

	public int powersavePercentage()
	{
		return mPrefs.getInt(KEY_POWERSAVE_PERCENTAGE, 30);
	}

	public void setPowersavePercentage(int percentage)
	{
		mPrefs.edit().putInt(KEY_POWERSAVE_PERCENTAGE, percentage).apply();
	}

/*	public enum SyncMode
	{
		BitWise, LetItFlow
	}

	*//**
 * @return Gets whether or not to download media automatically or only on
 *         demand
 *
 *//*
	public SyncMode syncMode()
	{
		// return Enum.valueOf(SyncMode.class, mPrefs.getString(KEY_SYNC_MODE,
		// SyncMode.LetItFlow.name()));
		String syncModeDefault = context.getResources().getString(R.string.sync_mode_default);
		return Enum.valueOf(SyncMode.class, mPrefs.getString(KEY_SYNC_MODE, syncModeDefault));
	}

	*//**
 * @return Sets whether or not to download media automatically or only on
 *         demand
 *
 *//*
	public void setSyncMode(SyncMode syncMode)
	{
		mPrefs.edit().putString(KEY_SYNC_MODE, syncMode.name()).commit();
	}*/

}