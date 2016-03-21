package info.guardianproject.securereader;

import android.util.Log;


public class SecureSettings {
	
	public static final String LOGTAG = "SecureSettings";
	public static final boolean LOGGING = false;
	
	public static final String KEY_XMLRPC_USERNAME = "xmlrpc_username";
	public static final String KEY_XMLRPC_PASSWORD = "xmlrpc_password";	
	public static final String KEY_CHATSECURE_USERNAME = "chatsecure_username";
	public static final String KEY_CHATSECURE_PASSWORD = "chatsecure_password";
	public static final String KEY_NICKNAME = "nickname";
	
	private SecureSharedPrefs mPrefs;

	public SecureSettings(DatabaseAdapter _databaseAdapter)
	{
		mPrefs = new SecureSharedPrefs(_databaseAdapter);
	}
	
	public void setChatSecureUsername(String username) {
		mPrefs.putValue(KEY_CHATSECURE_USERNAME, username);
	}
	public void setChatSecurePassword(String password) {
		mPrefs.putValue(KEY_CHATSECURE_PASSWORD, password);
	}
	public String getChatSecureUsername()
	{
		return mPrefs.getValue(KEY_CHATSECURE_USERNAME, null);
	}
	public String getChatSecurePassword()
	{
		return mPrefs.getValue(KEY_CHATSECURE_PASSWORD, null);
	}	
	
	public void setXMLRPCUsername(String username) {
		mPrefs.putValue(KEY_XMLRPC_USERNAME, username);
	}
	public void setXMLRPCPassword(String password) {
		mPrefs.putValue(KEY_XMLRPC_PASSWORD, password);
	}
	public String getXMLRPCUsername()
	{
		return mPrefs.getValue(KEY_XMLRPC_USERNAME, null);
	}
	public String getXMLRPCPassword()
	{
		return mPrefs.getValue(KEY_XMLRPC_PASSWORD, null);
	}
	/**
	 * @return gets the nickname used in chat and post
	 * 
	 */
	public String nickname()
	{
		if (LOGGING)
			Log.v(LOGTAG,"Nickname: " + mPrefs.getValue(KEY_NICKNAME, "none set"));
		return mPrefs.getValue(KEY_NICKNAME, null);
	}

	/**
	 * @return sets the nickname used in chat and post
	 * 
	 */
	public void setNickname(String nickname)
	{
		mPrefs.putValue(KEY_NICKNAME, nickname);
	}
	
	
	
	private class SecureSharedPrefs 
	{
		DatabaseAdapter databaseAdapter;
		//ArrayList<SecureSharedPreferenceChangeListener> secureSharedPreferenceChangeListeners = new ArrayList<SecureSharedPreferenceChangeListener>();
		
		SecureSharedPrefs(DatabaseAdapter _databaseAdapter) {
			databaseAdapter = _databaseAdapter;
		}
		
		/*
		public void registerChangeListener(SecureSharedPreferenceChangeListener _secureSharedPreferenceChangeListener) {
			secureSharedPreferenceChangeListeners.add(_secureSharedPreferenceChangeListener);
		}
		
		public void unregisterChangeListener(SecureSharedPreferenceChangeListener _secureSharedPreferenceChangeListener) {
			secureSharedPreferenceChangeListeners.remove(_secureSharedPreferenceChangeListener);
		}
		*/
		
		/*
		public boolean getBoolean(String key, boolean defaultValue) {
			String stringValue = databaseAdapter.getSettingValue(key);
			if (stringValue == null) { return defaultValue; }
			else {
				return Boolean.parseBoolean(stringValue);
			}
		}
		
		public void putBoolean(String key, boolean value) {
			databaseAdapter.addOrUpdateSetting(key, Boolean.toString(value));
		}
		*/
		
		public String getValue(String key, String defaultValue) {
			String stringValue = databaseAdapter.getSettingValue(key);
			if (stringValue == null) { 
				return defaultValue; 
			} else {
				return stringValue;
			}
		}
		
		public String getValue(String key) {
			return getValue(key,null);
		}
		
		public void putValue(String key, String value) {
			databaseAdapter.addOrUpdateSetting(key,value);	
		}
	}
	
	/*
	class SecureSharedPreferenceChangeListener {
		
	}
	*/
}
