package info.guardianproject.securereader;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.collect.Lists;

public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
	public static final String LOGTAG = "Settings";
	public static final boolean LOGGING = false;

	private static final int CURRENT_SETTINGS_VERSION = 1;
	private static final String KEY_SETTINGS_VERSION = "settings_version";

	protected final Context context;
	protected final SharedPreferences mPrefs;
	protected ModeSettings currentMode;
	protected ModeSettings modeOptimized;
	private long modeOptimizedDefaultSize; // Size on disk when default settings are set
	protected ModeSettings modeEverything;
	private long modeEverythingDefaultSize; // Size on disk when default settings are set
	protected ModeSettings modeOffline;

	// Use these constants when listening to changes, to see what property has
	// changed!
	//
	public static final String KEY_NETWORK_OPML_LOADED = "network_opml_loaded";
	public static final String KEY_LOCAL_OPML_LOADED = "local_opml_loaded";
	public static final String KEY_LAST_ITEM_EXPIRATION_CHECK_TIME = "last_item_expiration_check_time";
	public static final String KEY_LAST_OPML_CHECK_TIME = "last_opml_check_time";
	public static final String KEY_CURRENT_PASSWORD_ATTEMPTS = "num_current_password_attempts";
	public static final String KEY_ACCEPTED_POST_PERMISSION = "accepted_post_permission";
	public static final String KEY_XMLRPC_USERNAME = "xmlrpc_username";
	public static final String KEY_XMLRPC_PASSWORD = "xmlrpc_password";
	public static final String KEY_CHAT_SECURE_DIALOG_SHOWN = "chat_secure_dialog_shown";
	public static final String KEY_CHAT_SECURE_INFO_SHOWN = "chat_secure_info_shown";
	public static final String KEY_USERNAME_PASSWORD_CHAT_REGISTERED = "chat_username_password_registered";
	public static final String KEY_DOWNLOAD_EPUB_READER_DIALOG_SHOWN = "download_epub_reader_dialog_shown";

	public static final String KEY_REQUIRE_TOR = "require_tor";
	public static final String KEY_ARTICLE_EXPIRATION = "article_expiration";
	public static final String KEY_SYNC_NETWORK = "sync_network";

	public static String KEY_MODE;
	public static String KEY_PROXY_TYPE;
	public static String KEY_PANIC_ACTION;
	public static String KEY_UI_LANGUAGE;
	public static String KEY_AUTOLOCK;
	public static String KEY_WRONG_PASSWORD_ACTION;
	public static String KEY_WRONG_PASSWORD_LIMIT;
	public static String KEY_USE_KILL_PASSPHRASE;
	public static String KEY_KILL_PASSPHRASE;

	public Settings(Context context)
	{
		this.context = context;

		KEY_MODE = context.getString(R.string.pref_key_mode);
		KEY_PROXY_TYPE = context.getString(R.string.pref_key_security_proxy);
		KEY_PANIC_ACTION = context.getString(R.string.pref_key_panic_action);
		KEY_UI_LANGUAGE = context.getString(R.string.pref_key_language);
		KEY_AUTOLOCK = context.getString(R.string.pref_key_security_autolock);
		KEY_WRONG_PASSWORD_ACTION = context.getString(R.string.pref_key_wrong_password_action);
		KEY_WRONG_PASSWORD_LIMIT = context.getString(R.string.pref_key_wrong_password_limit);
		KEY_USE_KILL_PASSPHRASE = context.getString(R.string.pref_key_use_kill_passphrase);
		KEY_KILL_PASSPHRASE = context.getString(R.string.pref_key_kill_passphrase);

		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		mPrefs.registerOnSharedPreferenceChangeListener(this);
		modeOptimized = new ModeSettings(context, getModeFilename(Mode.Optimized));
		modeEverything = new ModeSettings(context, getModeFilename(Mode.Everything));
		modeOffline = new ModeSettings(context, getModeFilename(Mode.Offline));

		int fileVersion = mPrefs.getInt(KEY_SETTINGS_VERSION, 0);
		initializeIfNeeded();
		setCurrentMode();

		if (fileVersion == 0) {
			boolean uninstall = mPrefs.getBoolean("wipe_app", false);
			setPanicAction(uninstall ? PanicAction.Uninstall : PanicAction.WipeData);
		}
	}
	
	@SuppressLint("ApplySharedPref")
	public void resetSettings() {
		mPrefs.edit().clear().commit();
		modeOptimized.mPrefs.edit().clear().commit();
		modeEverything.mPrefs.edit().clear().commit();
		modeOffline.mPrefs.edit().clear().commit();
		initializeIfNeeded();
	}

	public void resetModeSettings(Mode mode) {
		if (mode == Mode.Optimized) {
			modeOptimized.mPrefs.edit()
					.clear()
					.putString(ModeSettings.KEY_SYNC_FREQUENCY, context.getString(R.string.pref_default_optimized_sync_frequency))
					.putStringSet(ModeSettings.KEY_SYNC_OVER_WIFI, new HashSet<>(Arrays.asList(context.getResources().getStringArray(R.array.pref_default_optimized_sync_over_wifi))))
					.putStringSet(ModeSettings.KEY_SYNC_OVER_DATA, new HashSet<>(Arrays.asList(context.getResources().getStringArray(R.array.pref_default_optimized_sync_over_data))))
					.putBoolean(ModeSettings.KEY_SYNC_MEDIA_RICH, context.getResources().getBoolean(R.bool.pref_default_optimized_media_rich))
					.putString(ModeSettings.KEY_ARTICLE_EXPIRATION, context.getString(R.string.pref_default_optimized_article_expiration))
					.putBoolean(ModeSettings.KEY_POWERSAVE_ENABLED, context.getResources().getBoolean(R.bool.pref_default_optimized_save_power_enabled))
					.putInt(ModeSettings.KEY_POWERSAVE_PERCENTAGE, context.getResources().getInteger(R.integer.pref_default_optimized_save_power_percentage))
					.apply();
			onResetOptimizedMode(modeOptimized);
			modeOptimizedDefaultSize = modeOptimized.mPrefs.getAll().hashCode();
		} else if (mode == Mode.Everything) {
			modeEverything.mPrefs.edit()
					.clear()
					.putString(ModeSettings.KEY_SYNC_FREQUENCY, context.getString(R.string.pref_default_everything_sync_frequency))
					.putStringSet(ModeSettings.KEY_SYNC_OVER_WIFI, new HashSet<>(Arrays.asList(context.getResources().getStringArray(R.array.pref_default_everything_sync_over_wifi))))
					.putStringSet(ModeSettings.KEY_SYNC_OVER_DATA, new HashSet<>(Arrays.asList(context.getResources().getStringArray(R.array.pref_default_everything_sync_over_data))))
					.putBoolean(ModeSettings.KEY_SYNC_MEDIA_RICH, context.getResources().getBoolean(R.bool.pref_default_everything_media_rich))
					.putString(ModeSettings.KEY_ARTICLE_EXPIRATION, context.getString(R.string.pref_default_everything_article_expiration))
					.putBoolean(ModeSettings.KEY_POWERSAVE_ENABLED, context.getResources().getBoolean(R.bool.pref_default_everything_save_power_enabled))
					.putInt(ModeSettings.KEY_POWERSAVE_PERCENTAGE, context.getResources().getInteger(R.integer.pref_default_everything_save_power_percentage))
					.apply();
			modeEverythingDefaultSize = modeEverything.mPrefs.getAll().hashCode();
		}
	}

	protected void onResetOptimizedMode(ModeSettings modeOptimized) {
		// Override if needed
	}

	public boolean hasChangedModeSettings(Mode mode) {
		if (mode == Mode.Optimized) {
			return modeOptimizedDefaultSize != modeOptimized.mPrefs.getAll().hashCode();
		} else if (mode == Mode.Everything) {
			return modeEverythingDefaultSize != modeEverything.mPrefs.getAll().hashCode();
		}
		return false;
	}

	private void initializeIfNeeded() {
		if (!mPrefs.contains(KEY_SETTINGS_VERSION)) {
			initialize();
		}
	}

	private void initialize() {
		// Set defaults
		mPrefs.edit()
				.putString(KEY_MODE, context.getString(R.string.pref_default_mode))
				.putString(KEY_PROXY_TYPE, context.getString(R.string.pref_default_security_proxy))
				.putString(KEY_PANIC_ACTION, context.getString(R.string.pref_default_panic_action))
				.putInt(KEY_AUTOLOCK, 0)
				.putString(KEY_WRONG_PASSWORD_ACTION, context.getString(R.string.pref_default_wrong_password_action))
				.putInt(KEY_WRONG_PASSWORD_LIMIT, context.getResources().getInteger(R.integer.pref_default_wrong_password_limit))
				.putBoolean(KEY_USE_KILL_PASSPHRASE, false)

				.apply();

		resetModeSettings(Mode.Optimized);
		resetModeSettings(Mode.Everything);
		mPrefs.edit()
				.putInt(KEY_SETTINGS_VERSION, CURRENT_SETTINGS_VERSION).apply();
	}

	public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener)
	{
		mPrefs.registerOnSharedPreferenceChangeListener(listener);
		modeOptimized.registerChangeListener(listener);
		modeEverything.registerChangeListener(listener);
	}

	public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener)
	{
		mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
		modeOptimized.unregisterChangeListener(listener);
		modeEverything.unregisterChangeListener(listener);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (KEY_MODE.equalsIgnoreCase(key)) {
			setCurrentMode();
		}
	}

	public enum Mode
	{
		Optimized, Everything, Offline
	}

	/**
	 * @return Gets required current mode
	 *
	 */
	public Mode mode()
	{
		String defaultValue = context.getResources().getString(R.string.pref_default_mode);
		if (TextUtils.isEmpty(defaultValue))
			defaultValue = Mode.Optimized.name();
		return Enum.valueOf(Mode.class, mPrefs.getString(KEY_MODE, defaultValue));
	}

	/**
	 * @return Sets mode
	 *
	 */
	public void setMode(Mode mode)
	{
		mPrefs.edit().putString(KEY_MODE, mode.name()).apply();
		setCurrentMode();
	}

	public String getModeFilename(Mode mode) {
		switch (mode) {
			case Optimized:
				return "optimized";
			case Everything:
				return "everything";
			default:
				return "offline";
		}
	}

	public ModeSettings getCurrentMode() {
		return currentMode;
	}

	private void setCurrentMode() {
		switch (mode()) {
			case Optimized:
				currentMode = modeOptimized;
				break;
			case Everything:
				currentMode = modeEverything;
				break;
			default:
				currentMode = modeOffline;
				break;
		}
	}

	public enum ProxyType
	{
		None, Tor, Psiphon
	}
	
	/**
	 * @return Gets required proxy type
	 * 
	 */
	public ProxyType proxyType()
	{
		String defaultProxyType = context.getResources().getString(R.string.pref_default_security_proxy);
		if (TextUtils.isEmpty(defaultProxyType))
			defaultProxyType = ProxyType.None.name();
		return Enum.valueOf(ProxyType.class, mPrefs.getString(KEY_PROXY_TYPE, defaultProxyType));
	}

	/**
	 * @return Sets required proxy type
	 * 
	 */
	public void setProxyType(ProxyType proxyType)
	{
		mPrefs.edit().putString(KEY_PROXY_TYPE, proxyType.name()).apply();
	}
	

	/**
	 * @return Gets the timeout before lock screen is shown
	 * 
	 */
	public int autoLock()
	{
		// 1 day by default 60 * 60 * 24 = 86400
		return mPrefs.getInt(KEY_AUTOLOCK, 86400);
	}

	/**
	 * @return Sets timeout before lock screen is shown
	 * 
	 */
	public void setAutolock(int seconds)
	{
		if (LOGGING)
			Log.v(LOGTAG,"setAutolock: " + seconds);
		
		mPrefs.edit().putInt(KEY_AUTOLOCK, seconds).apply();
	}

	/**
	 * @return gets the passphrase to unlock app
	 * 
	 */
	public String launchPassphrase()
	{
		return mPrefs.getString("launch_passphrase", null);
	}

	/**
	 * @return Sets the passphrase to unlock app
	 * 
	 */
	public void setLaunchPassphrase(String passphrase)
	{
		mPrefs.edit().putString("launch_passphrase", passphrase).commit();
	}

	public PanicAction wrongPasswordAction()
	{
		return Enum.valueOf(PanicAction.class, mPrefs.getString(KEY_WRONG_PASSWORD_ACTION,
				context.getResources().getString(R.string.pref_default_wrong_password_action)));
	}

	public void setWrongPasswordAction(PanicAction wrongPasswordAction)
	{
		mPrefs.edit().putString(KEY_WRONG_PASSWORD_ACTION, wrongPasswordAction.name()).apply();
	}

	public enum PanicAction
	{
		WipeData, Uninstall, Nothing
	}

	public PanicAction panicAction()
	{
		return Enum.valueOf(PanicAction.class, mPrefs.getString(KEY_PANIC_ACTION,
				context.getResources().getString(R.string.pref_default_panic_action)));
	}

	public void setPanicAction(PanicAction panicAction)
	{
		mPrefs.edit().putString(KEY_PANIC_ACTION, panicAction.name()).apply();
	}


	/**
	 * @return gets the app ui language
	 * 
	 */
	public String uiLanguage()
	{
		String ret = mPrefs.getString(KEY_UI_LANGUAGE, null);
		if (ret != null)
		{
			return ret;
		}

		String defaultLanguage = Locale.getDefault().getLanguage();
		List<String> langs = Lists.newArrayList("ar", "fa", "bo", "ja", "nb", "tr", "zh", "uk", "ru", "de", "fr");
		if (langs.contains(defaultLanguage)) {
			return defaultLanguage;
		} else if (defaultLanguage.equals("es")) {
			String country = Locale.getDefault().getCountry();
			if (!TextUtils.isEmpty(country) && country.startsWith("US")) {
				return "en_US";
			}
			return "es";
		}
		return "en";
	}

	/**
	 * @return Sets the app ui language
	 * 
	 */
	public void setUiLanguage(String language)
	{
		mPrefs.edit().putString(KEY_UI_LANGUAGE, language).apply();
	}

	/**
	 * @return Get number of failed password attempts before content is wiped, 0 to disable the feature!
	 * 
	 */
	public int numberOfPasswordAttempts()
	{
		return mPrefs.getInt(KEY_WRONG_PASSWORD_LIMIT, context.getResources().getInteger(R.integer.pref_default_wrong_password_limit));
	}

	/**
	 * Set number of failed password attempts before content is wiped!
	 * 
	 */
	public void setNumberOfPasswordAttempts(int attempts)
	{
		mPrefs.edit().putInt(KEY_WRONG_PASSWORD_LIMIT, attempts).apply();
	}

	/**
	 * @return Get current number of failed password attempts
	 * 
	 */
	public int currentNumberOfPasswordAttempts()
	{
		return mPrefs.getInt(KEY_CURRENT_PASSWORD_ATTEMPTS, 0);
	}

	/**
	 * Set current number of failed password attempts
	 * 
	 */
	public void setCurrentNumberOfPasswordAttempts(int attempts)
	{
		mPrefs.edit().putInt(KEY_CURRENT_PASSWORD_ATTEMPTS, attempts).commit();
	}

	/**
	 * @return Gets whether or not we have accepted post permission
	 * 
	 */
	public boolean acceptedPostPermission()
	{
		return mPrefs.getBoolean(KEY_ACCEPTED_POST_PERMISSION, false);
	}

	/**
	 * @return Sets whether or not we have accepted post permission
	 * 
	 */
	public void setAcceptedPostPermission(boolean accepted)
	{
		mPrefs.edit().putBoolean(KEY_ACCEPTED_POST_PERMISSION, accepted).commit();
	}
	
	/**
	 * @return Gets whether or not to use a kill passphrase that will wipe the app on login
	 * 
	 */
	public boolean useKillPassphrase()
	{
		return mPrefs.getBoolean(KEY_USE_KILL_PASSPHRASE, false);
	}

	/**
	 * @return Sets whether or not to use a kill passphrase that will wipe the app on login
	 * 
	 */
	public void setUseKillPassphrase(boolean use)
	{
		mPrefs.edit().putBoolean(KEY_USE_KILL_PASSPHRASE, use).apply();
	}

	/**
	 * @return gets the kill passphrase that will wipe the app on login
	 * 
	 */
	public String killPassphrase()
	{
		return mPrefs.getString(KEY_KILL_PASSPHRASE, null);
	}

	/**
	 * @return Sets the kill passphrase that will wipe the app on login
	 * 
	 */
	public void setKillPassphrase(String passphrase)
	{
		mPrefs.edit().putString(KEY_KILL_PASSPHRASE, passphrase).apply();
	}
	
	public long lastOPMLCheckTime() {
		return mPrefs.getLong(KEY_LAST_OPML_CHECK_TIME, 0);
	}
	
	public void setLastOPMLCheckTime(long lastCheckTime) {
		mPrefs.edit().putLong(KEY_LAST_OPML_CHECK_TIME, lastCheckTime).commit();
	}
	
	public long lastItemExpirationCheckTime() {
		return mPrefs.getLong(KEY_LAST_ITEM_EXPIRATION_CHECK_TIME, 0);		
	}
	
	public void setLastItemExpirationCheckTime(long lastCheckTime) {
		mPrefs.edit().putLong(KEY_LAST_ITEM_EXPIRATION_CHECK_TIME, lastCheckTime).commit();
	}

	/**
	 * @return number of times we have shown ChatSecure download dialog
	 * 
	 */
	public int chatSecureDialogShown()
	{
		return mPrefs.getInt(KEY_CHAT_SECURE_DIALOG_SHOWN, 0);
	}
	
	/**
	 * Set number of times we have shown ShatSecure download dialog
	 * 
	 */
	public void setChatSecureDialogShown(int numTimes)
	{
		mPrefs.edit().putInt(KEY_CHAT_SECURE_DIALOG_SHOWN, numTimes).commit();
	}
	
	/**
	 * @return number of times we have shown ChatSecure information dialog
	 * 
	 */
	public int chatSecureInfoShown()
	{
		return mPrefs.getInt(KEY_CHAT_SECURE_INFO_SHOWN, 0);
	}
	
	/**
	 * Set number of times we have shown ShatSecure information dialog
	 * 
	 */
	public void setChatSecureInfoShown(int numTimes)
	{
		mPrefs.edit().putInt(KEY_CHAT_SECURE_INFO_SHOWN, numTimes).commit();
	}

	public boolean chatUsernamePasswordSet() {
		return mPrefs.getBoolean(KEY_USERNAME_PASSWORD_CHAT_REGISTERED, false);
	}
	
	public void setChatUsernamePasswordSet() {
		mPrefs.edit().putBoolean(KEY_USERNAME_PASSWORD_CHAT_REGISTERED, true).commit();
	}
	
	/**
	 * @return number of times we have shown DownloadEpubReader dialog
	 * 
	 */
	public int downloadEpubReaderDialogShown()
	{
		return mPrefs.getInt(KEY_DOWNLOAD_EPUB_READER_DIALOG_SHOWN, 0);
	}
	
	/**
	 * Set number of times we have shown DownloadEpubReader dialog
	 * 
	 */
	public void setDownloadEpubReaderDialogShown(int numTimes)
	{
		mPrefs.edit().putInt(KEY_DOWNLOAD_EPUB_READER_DIALOG_SHOWN, numTimes).commit();
	}
	
	public boolean networkOpmlLoaded() {
		return mPrefs.getBoolean(KEY_NETWORK_OPML_LOADED, false);
	}
	public void setNetworkOpmlLoaded() {
		mPrefs.edit().putBoolean(KEY_NETWORK_OPML_LOADED, true).commit();
	}
	public boolean localOpmlLoaded() {
		return mPrefs.getBoolean(KEY_LOCAL_OPML_LOADED, false);
	}
	public void setLocalOpmlLoaded() {
		mPrefs.edit().putBoolean(KEY_LOCAL_OPML_LOADED, true).commit();
	}

	private Set<Enum> arrayResourceToEnumSet(Class enumClass, int idRes) {
		Set<Enum> res = new HashSet<>();
		String[] values = context.getResources().getStringArray(idRes);
		for (String val : values) {
			res.add(Enum.valueOf(enumClass, val));
		}
		return res;
	}
	
}