package info.guardianproject.securereader;

import java.util.Locale;

import ch.boye.httpclientandroidlib.util.TextUtils;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class Settings
{
	public static final String LOGTAG = "Settings";
	public static final boolean LOGGING = false;
	
	protected final SharedPreferences mPrefs;
	private final boolean mIsFirstRun;
	private final Context context;

	// Use these constants when listening to changes, to see what property has
	// changed!
	//
	public static final String KEY_NETWORK_OPML_LOADED = "network_opml_loaded";
	public static final String KEY_LOCAL_OPML_LOADED = "local_opml_loaded";
	public static final String KEY_LAST_ITEM_EXPIRATION_CHECK_TIME = "last_item_expiration_check_time";
	public static final String KEY_LAST_OPML_CHECK_TIME = "last_opml_check_time";
	public static final String KEY_REQUIRE_TOR = "require_tor";
	public static final String KEY_PASSPHRASE_TIMEOUT = "passphrase_timeout";
	public static final String KEY_CONTENT_FONT_SIZE_ADJUSTMENT = "content_font_size_adjustment";
	public static final String KEY_WIPE_APP = "wipe_app";
	public static final String KEY_ARTICLE_EXPIRATION = "article_expiration";
	public static final String KEY_SYNC_MODE = "sync_mode";
	public static final String KEY_SYNC_FREQUENCY = "sync_frequency";
	public static final String KEY_SYNC_NETWORK = "sync_network";
	public static final String KEY_READER_SWIPE_DIRECTION = "reader_swipe_direction";
	public static final String KEY_UI_LANGUAGE = "ui_language";
	public static final String KEY_PASSWORD_ATTEMPTS = "num_password_attempts";
	public static final String KEY_CURRENT_PASSWORD_ATTEMPTS = "num_current_password_attempts";
	public static final String KEY_ACCEPTED_POST_PERMISSION = "accepted_post_permission";
	public static final String KEY_XMLRPC_USERNAME = "xmlrpc_username";
	public static final String KEY_XMLRPC_PASSWORD = "xmlrpc_password";
	public static final String KEY_USE_KILL_PASSPHRASE = "use_passphrase";
	public static final String KEY_KILL_PASSPHRASE = "passphrase";
	public static final String KEY_CHAT_SECURE_DIALOG_SHOWN = "chat_secure_dialog_shown";
	public static final String KEY_CHAT_SECURE_INFO_SHOWN = "chat_secure_info_shown";
	public static final String KEY_USERNAME_PASSWORD_CHAT_REGISTERED = "chat_username_password_registered";
	public static final String KEY_DOWNLOAD_EPUB_READER_DIALOG_SHOWN = "download_epub_reader_dialog_shown";
	public static final String KEY_REQUIRE_PROXY = "require_proxy";
	public static final String KEY_PROXY_TYPE = "proxy_type"; 
	
	public Settings(Context _context)
	{
		context = _context;
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		mIsFirstRun = mPrefs.getBoolean("firstrunkey", true);

		// If this is first run, set the flag for next time
		if (mIsFirstRun)
			mPrefs.edit().putBoolean("firstrunkey", false).commit();
	}
	
	public void resetSettings() {		
		// Reset all settings?

		/*
		boolean torRequiredDefault = context.getResources().getBoolean(R.bool.require_tor_default);
		mPrefs.edit().putBoolean(KEY_REQUIRE_TOR, torRequiredDefault).commit();

		mPrefs.edit().putInt(KEY_PASSPHRASE_TIMEOUT, 1440).commit();
		*/


		this.setHasShownHelp(false);
		this.setHasShownMenuHint(false);
		this.setHasShownSwipeUpHint(false);
		
		mPrefs.edit().putBoolean("firstrunkey", true).commit();
	}
	
	public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener)
	{
		mPrefs.registerOnSharedPreferenceChangeListener(listener);
	}

	public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener)
	{
		mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
	}

	
	/**
	 * @return returns true if this is the first time we start the app
	 * 
	 */
	public boolean isFirstRun()
	{
		return mIsFirstRun;
	}

	public void setFirstRun(boolean value) {
		mPrefs.edit().putBoolean("firstrunkey", value).commit();
	}
	
	/**
	 * @return Gets whether or not a TOR connection is required
	 * 
	 */
	public boolean requireProxy()
	{
		boolean proxyRequiredDefault = context.getResources().getBoolean(R.bool.require_proxy_default);
		return mPrefs.getBoolean(KEY_REQUIRE_PROXY, proxyRequiredDefault);
	}

	/**
	 * @return Sets whether a TOR connection is required
	 * 
	 */
	public void setRequireProxy(boolean require)
	{
		mPrefs.edit().putBoolean(KEY_REQUIRE_PROXY, require).commit();
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
		String defaultProxyType = context.getResources().getString(R.string.default_proxy_type);
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
		mPrefs.edit().putString(KEY_PROXY_TYPE, proxyType.name()).commit();
	}
	
	
	
	
	/**
	 * @return Gets the timeout before lock screen is shown
	 * 
	 */
	public int passphraseTimeout()
	{
		// 1 day by default 60 * 60 * 24 = 86400
		return mPrefs.getInt(KEY_PASSPHRASE_TIMEOUT, 86400);
	}

	/**
	 * @return Sets timeout before lock screen is shown
	 * 
	 */
	public void setPassphraseTimeout(int seconds)
	{
		if (LOGGING)
			Log.v(LOGTAG,"setPassphraseTimeout: " + seconds);
		
		mPrefs.edit().putInt(KEY_PASSPHRASE_TIMEOUT, seconds).commit();
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

	/**
	 * @return returns true if we have shown the "swipe up to close story" hint
	 *         dialog
	 * 
	 */
	public boolean hasShownSwipeUpHint()
	{
		return mPrefs.getBoolean("hasshownswipeuphint", false);
	}

	/**
	 * Set (or reset) whether we have shown the "swipe up to close story" hint
	 * dialog.
	 * 
	 */
	public void setHasShownSwipeUpHint(boolean shown)
	{
		mPrefs.edit().putBoolean("hasshownswipeuphint", shown).commit();
	}

	/**
	 * @return returns true if we have shown the menu hint (bounced it out)
	 * 
	 */
	public boolean hasShownMenuHint()
	{
		return mPrefs.getBoolean("hasshownmenuhint", false);
	}

	/**
	 * Set (or reset) whether we have shown the menu hint (bounced out)
	 * 
	 */
	public void setHasShownMenuHint(boolean shown)
	{
		mPrefs.edit().putBoolean("hasshownmenuhint", shown).commit();
	}

	/**
	 * @return returns true if we have shown the help items
	 * 
	 */
	public boolean hasShownHelp()
	{
		return mPrefs.getBoolean("hasshownhelpitems", false);
	}
	
	/**
	 * Set (or reset) whether we have shown the help items
	 * 
	 */
	public void setHasShownHelp(boolean shown)
	{
		mPrefs.edit().putBoolean("hasshownhelpitems", shown).commit();
	}

	/**
	 * If we have made content text larger or smaller store the adjustment here
	 * (we don't store the absolute size here since we might change the default
	 * font and therefore need to change the default font size as well).
	 * 
	 * @return adjustment we have made to default font size (in sp units)
	 * 
	 */
	public int getContentFontSizeAdjustment()
	{
		return mPrefs.getInt(KEY_CONTENT_FONT_SIZE_ADJUSTMENT, 0);
	}

	/**
	 * Set the adjustment for default font size in sp units (positive means
	 * larger, negative means smaller)
	 * 
	 */
	public void setContentFontSizeAdjustment(int adjustment)
	{
		mPrefs.edit().putInt(KEY_CONTENT_FONT_SIZE_ADJUSTMENT, adjustment).commit();
	}

	/**
	 * @return Gets whether we should wipe the entire app (as opposed to only
	 *         the user data)
	 * 
	 */
	public boolean wipeApp()
	{
		boolean wipeAppDefault = context.getResources().getBoolean(R.bool.wipe_app_default);
		return mPrefs.getBoolean(KEY_WIPE_APP, wipeAppDefault);
	}

	/**
	 * @return Sets whether we should wipe the entire app (as opposed to only
	 *         the user data)
	 * 
	 */
	public void setWipeApp(boolean wipeApp)
	{
		mPrefs.edit().putBoolean(KEY_WIPE_APP, wipeApp).commit();
	}

	public enum ArticleExpiration
	{
		Never, OneDay, OneWeek, OneMonth
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
		mPrefs.edit().putString(KEY_ARTICLE_EXPIRATION, articleExpiration.name()).commit();
	}

	public enum SyncFrequency
	{
		Manual, WhenRunning, InBackground
	}

	/**
	 * @return Gets when to sync feeds
	 * 
	 */
	public SyncFrequency syncFrequency()
	{
		// return Enum.valueOf(SyncFrequency.class,
		// mPrefs.getString(KEY_SYNC_FREQUENCY, SyncFrequency.Manual.name()));
		return Enum.valueOf(SyncFrequency.class, mPrefs.getString(KEY_SYNC_FREQUENCY, context.getResources().getString(R.string.sync_frequency_default)));
	}

	/**
	 * @return Sets when to sync feeds. Can be manual, when app is running or
	 *         when in background.
	 * 
	 */
	public void setSyncFrequency(SyncFrequency syncFreqency)
	{
		mPrefs.edit().putString(KEY_SYNC_FREQUENCY, syncFreqency.name()).commit();
	}

	public enum SyncMode
	{
		BitWise, LetItFlow
	}

	/**
	 * @return Gets whether or not to download media automatically or only on
	 *         demand
	 * 
	 */
	public SyncMode syncMode()
	{
		// return Enum.valueOf(SyncMode.class, mPrefs.getString(KEY_SYNC_MODE,
		// SyncMode.LetItFlow.name()));
		String syncModeDefault = context.getResources().getString(R.string.sync_mode_default);
		return Enum.valueOf(SyncMode.class, mPrefs.getString(KEY_SYNC_MODE, syncModeDefault));
	}

	/**
	 * @return Sets whether or not to download media automatically or only on
	 *         demand
	 * 
	 */
	public void setSyncMode(SyncMode syncMode)
	{
		mPrefs.edit().putString(KEY_SYNC_MODE, syncMode.name()).commit();
	}

	public enum SyncNetwork
	{
		WifiAndMobile, WifiOnly
	}

	/**
	 * @return Gets over which networks that sync is available
	 * 
	 */
	public SyncNetwork syncNetwork()
	{
		String syncNetworkDefault = context.getResources().getString(R.string.sync_network_default);
		return Enum.valueOf(SyncNetwork.class, mPrefs.getString(KEY_SYNC_NETWORK, syncNetworkDefault));
	}

	/**
	 * @return Sets over which networks that sync is available
	 * 
	 */
	public void setSyncNetwork(SyncNetwork syncNetwork)
	{
		mPrefs.edit().putString(KEY_SYNC_NETWORK, syncNetwork.name()).commit();
	}

	public enum ReaderSwipeDirection
	{
		Rtl, Ltr, Automatic
	}

	/**
	 * @return Gets how swipes are handled in full screen story view
	 * 
	 */
	public ReaderSwipeDirection readerSwipeDirection()
	{
		return Enum.valueOf(ReaderSwipeDirection.class, mPrefs.getString(KEY_READER_SWIPE_DIRECTION, ReaderSwipeDirection.Automatic.name()));
	}

	/**
	 * @return Sets how swipes are handled in full screen story view
	 * 
	 */
	public void setReaderSwipeDirection(ReaderSwipeDirection readerSwipeDirection)
	{
		mPrefs.edit().putString(KEY_READER_SWIPE_DIRECTION, readerSwipeDirection.name()).commit();
	}

	public enum UiLanguage
	{
		English, Chinese, Japanese, Norwegian, Spanish, Tibetan, Turkish, Russian, Ukrainian, Farsi, Arabic,
		German
		//Italian, Swedish, Dutch, Korean, Brazilian Portuguese
		// I added Arabic, perhaps prematurely
	}

	/**
	 * @return gets the app ui language
	 * 
	 */
	public UiLanguage uiLanguage()
	{
		String ret = mPrefs.getString(KEY_UI_LANGUAGE, null);
		if (ret != null)
		{
			return Enum.valueOf(UiLanguage.class, ret);
		}
		
		// Is default system language arabic?
		String defaultLanguage = Locale.getDefault().getLanguage();
		
		if (defaultLanguage.equals("ar"))
			return UiLanguage.Arabic;
		else if (defaultLanguage.equals("fa"))
			return UiLanguage.Farsi;
		else if (defaultLanguage.equals("bo"))
			return UiLanguage.Tibetan;
		else if (defaultLanguage.equals("es"))
			return UiLanguage.Spanish;
		else if (defaultLanguage.equals("ja"))
			return UiLanguage.Japanese;
		else if (defaultLanguage.equals("nb"))
			return UiLanguage.Norwegian;
		else if (defaultLanguage.equals("tr"))
			return UiLanguage.Turkish;
		else if (defaultLanguage.equals("zh"))
			return UiLanguage.Chinese;
		else if (defaultLanguage.equals("uk"))
			return UiLanguage.Ukrainian;
		else if (defaultLanguage.equals("ru"))
			return UiLanguage.Russian;
		else if (defaultLanguage.equals("de"))
			return UiLanguage.German;
		return UiLanguage.English;
	}

	/**
	 * @return Sets the app ui language
	 * 
	 */
	public void setUiLanguage(UiLanguage language)
	{
		mPrefs.edit().putString(KEY_UI_LANGUAGE, language.name()).commit();
	}

	/**
	 * @return Get number of failed password attempts before content is wiped!
	 * 
	 */
	public int numberOfPasswordAttempts()
	{
		return mPrefs.getInt(KEY_PASSWORD_ATTEMPTS, 3);
	}

	/**
	 * Set number of failed password attempts before content is wiped!
	 * 
	 */
	public void setNumberOfPasswordAttempts(int attempts)
	{
		mPrefs.edit().putInt(KEY_PASSWORD_ATTEMPTS, attempts).commit();
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
		mPrefs.edit().putBoolean(KEY_USE_KILL_PASSPHRASE, use).commit();
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
		mPrefs.edit().putString(KEY_KILL_PASSPHRASE, passphrase).commit();
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
	
	
	
}