/*
 *   This is the main class of the SocialReader portion of the application.
 *   It contains the management of online vs. offline
 *   It manages the database and tor connections
 *   It interfaces with the UI but doesn't contain any of the UI code itself
 *   It is therefore meant to allow the SocialReader to be pluggable with RSS
 *   API and UI and so on
 */

package info.guardianproject.securereader;

//import info.guardianproject.bigbuffalo.adapters.DownloadsAdapter;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import info.guardianproject.cacheword.CacheWordHandler;
import info.guardianproject.cacheword.Constants;
import info.guardianproject.cacheword.ICacheWordSubscriber;
import info.guardianproject.cacheword.IOCipherMountHelper;
import info.guardianproject.netcipher.client.StrongBuilder;
import info.guardianproject.netcipher.client.StrongHttpClientBuilder;
import info.guardianproject.netcipher.proxy.OrbotHelper;
import info.guardianproject.netcipher.proxy.ProxyHelper;
import info.guardianproject.netcipher.proxy.PsiphonHelper;
import info.guardianproject.iocipher.*;
import info.guardianproject.securereader.HTMLRSSFeedFinder.RSSFeed;
import info.guardianproject.securereader.Settings.ProxyType;
import info.guardianproject.securereader.Settings.UiLanguage;
import info.guardianproject.securereader.SyncTaskFeedFetcher.SyncTaskFeedFetcherCallback;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.tinymission.rss.Feed;
import com.tinymission.rss.Item;
import com.tinymission.rss.ItemToRSS;
import com.tinymission.rss.MediaContent;
import com.tinymission.rss.Comment;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class SocialReader implements ICacheWordSubscriber, SharedPreferences.OnSharedPreferenceChangeListener {

	public interface SocialReaderLockListener
	{
		void onLocked();
		void onUnlocked();
	}

	public interface SocialReaderFeedPreprocessor
	{
		/**
		 * Return non-null to override the feed URL.
		 * @param feed The feed to get the URL for
		 * @return URL for feed, or null to use default URL
		 */
		String onGetFeedURL(Feed feed);
		InputStream onFeedDownloaded(Feed feed, InputStream content, Map<String, String> headers);
		void onFeedParsed(Feed feed);
	}

	// Change this when building for release
	public static final boolean TESTING = false;
	
	public static final String LOGTAG = "SocialReader";
	public static final boolean LOGGING = false;
	
	//public static final boolean REPEATEDLY_LOAD_NETWORK_OPML = true;
	
	public static final boolean REPORT_METRICS = false;
	
	public static final String CONTENT_SHARING_MIME_TYPE = "application/x-bigbuffalo-bundle";
	public static final String CONTENT_SHARING_EXTENSION = "bbb";
	public static final String CONTENT_ITEM_EXTENSION = "bbi";
	
	public static final int APP_IN_FOREGROUND = 1;
	public static final int APP_IN_BACKGROUND = 0;
	public int appStatus = 0;

	public static final int FULL_APP_WIPE = 100;
	public static final int DATA_WIPE = 101;

	public final static String TOR_PROXY_TYPE = "SOCKS";
	public final static String TOR_PROXY_HOST = "127.0.0.1";
	public int TOR_PROXY_PORT = 9050; // default for SOCKS Orbot/Tor
	public int TOR_PROXY_PORT_HTTP = 8118; // default for SOCKS Orbot/Tor

	public final static String PSIPHON_PROXY_HOST = "127.0.0.1";
	public final static String PSIPHON_PROXY_TYPE = "HTTP";
	public int PSIPHON_PROXY_PORT = -1;
	
	private boolean torRunning = false;
	private boolean psiphonRunning = false;
	
	public final static String USERAGENT = "Mozilla/5.0 (Linux; Android 4.0.4; Galaxy Nexus Build/IMM76B) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.133 Mobile Safari/535.19";

	public final static boolean RESET_DATABASE = false;

	public static final String MEDIA_CONTENT_FILE_PREFIX = "mc_";
	public static final String FEED_ICON_FILE_PREFIX = "fi_";
	public static final String CONTENT_BUNDLE_FILE_PREFIX = "bundle_";
	
	public static final String TEMP_ITEM_CONTENT_FILE_NAME = "temp" + "." + CONTENT_ITEM_EXTENSION;
	
	public static final String VFS_SHARE_DIRECTORY = "share";
	public static final String NON_VFS_SHARE_DIRECTORY = "share";

	public final static String FILES_DIR_NAME = "bbfiles";
	public final static String IOCIPHER_FILE_NAME = "vfs.db";

	public static String[] EXTERNAL_STORAGE_POSSIBLE_LOCATIONS = {"/sdcard/external_sdcard", "/sdcard/ext_sd", "/externalSdCard", "/extSdCard", "/external"};

	private String ioCipherFilePath;
	private VirtualFileSystem vfs;

	private HttpClient httpClient = null;

	public static final int DEFAULT_NUM_FEED_ITEMS = 200;
	
	public static final int MEDIA_ITEM_DOWNLOAD_LIMIT_PER_FEED_PER_SESSION = 5;
	//mediaItemDownloadLimitPerFeedPerSession

	public long defaultFeedId = -1;
	public final int feedRefreshAge;
	public final int expirationCheckFrequency;
	public final int opmlCheckFrequency;
	public final boolean repeatedlyLoadNetworkOPML;
	public final String opmlUrl;
	public String[] feedsWithComments;
	
	public static final int TIMER_PERIOD = 30000;  //TODO - remove this crazyness! 30 seconds
	
	public final int itemLimit;
	public final int mediaCacheSize;
	public final long mediaCacheSizeLimitInBytes;

	public final boolean relaxedHTTPS;
	
	// Constant to use when passing an item to be shared to the
	// securebluetoothsender as an extra in the intent
	public static final String SHARE_ITEM_ID = "SHARE_ITEM_ID";

	public Context applicationContext;
	DatabaseAdapter databaseAdapter;
	CacheWordHandler cacheWord;
	public SecureSettings ssettings;
	Settings settings;
	//SyncServiceConnection syncServiceConnection;
	SocialReaderLockListener lockListener;
	SocialReaderFeedPreprocessor feedPreprocessor;

	public static final int ONLINE = 1;
	public static final int NOT_ONLINE_NO_PROXY = -1;
	public static final int NOT_ONLINE_NO_WIFI = -2;
	public static final int NOT_ONLINE_NO_WIFI_OR_NETWORK = -3;
	
//	// WORK ON THIS
//	public int syncStatus = 0;
//	// Status indication
//	public static final int NO_SYNC_IN_PROGRESS = 0;
//	public static final int MANUAL_SYNC_IN_PROGRESS = 2; 
//	public static final int BACKGROUND_SYNC_IN_PROGRESS = 1;
	
	//OrbotHelper orbotHelper;
	PsiphonHelper psiphonHelper;
	
	Item talkItem = null;

	private SocialReader(Context _context) {
		
		this.applicationContext = _context;
		psiphonHelper = new PsiphonHelper();

		relaxedHTTPS = applicationContext.getResources().getBoolean(R.bool.relaxed_https);

		feedRefreshAge = applicationContext.getResources().getInteger(R.integer.feed_refresh_age);
		expirationCheckFrequency = applicationContext.getResources().getInteger(R.integer.expiration_check_frequency);
		opmlCheckFrequency = applicationContext.getResources().getInteger(R.integer.opml_check_frequency);
		opmlUrl = applicationContext.getResources().getString(R.string.opml_url);
		repeatedlyLoadNetworkOPML = applicationContext.getResources().getBoolean(R.bool.repeatedly_load_network_opml);
		
		itemLimit = applicationContext.getResources().getInteger(R.integer.item_limit);
		mediaCacheSize = applicationContext.getResources().getInteger(R.integer.media_cache_size);
		mediaCacheSizeLimitInBytes = mediaCacheSize * 1000 * 1000;
		
		feedsWithComments = applicationContext.getResources().getStringArray(R.array.feed_urls_with_comments);
		
		this.settings = new Settings(applicationContext);
		this.settings.registerChangeListener(this);

		this.cacheWord = new CacheWordHandler(applicationContext, this);
		cacheWord.connectToService();
		
	    BroadcastReceiver torStatusReceiver = new BroadcastReceiver() {
	        @Override
	        public void onReceive(Context context, Intent intent) {
	            if (TextUtils.equals(intent.getAction(), OrbotHelper.ACTION_STATUS)) {
	                String status = intent.getStringExtra(OrbotHelper.EXTRA_STATUS);
	                torRunning = (status.equals(OrbotHelper.STATUS_ON));
	                
	                if(torRunning){
                        if (TOR_PROXY_TYPE.equals("HTTP") && intent.hasExtra(OrbotHelper.EXTRA_PROXY_PORT_HTTP))
                        	TOR_PROXY_PORT = intent.getIntExtra(OrbotHelper.EXTRA_PROXY_PORT_HTTP, -1);
                        
                        if (TOR_PROXY_TYPE.equals("SOCKS") && intent.hasExtra(OrbotHelper.EXTRA_PROXY_PORT_SOCKS))
                        	TOR_PROXY_PORT = intent.getIntExtra(OrbotHelper.EXTRA_PROXY_PORT_SOCKS, -1);
	                  
	                } else if (status.equals(OrbotHelper.STATUS_STARTS_DISABLED)) {
						if (LOGGING)
							Log.v(LOGTAG, "Not allowed to start Tor automatically");
					}
	            }
	        }
	    };	
		applicationContext.registerReceiver(torStatusReceiver, new IntentFilter(OrbotHelper.ACTION_STATUS));
		
		BroadcastReceiver psiphonHelperReceiver = new BroadcastReceiver(){
			 
		    @Override
		    public void onReceive(Context context, Intent intent) {
		    	if (LOGGING)
	        		Log.v(LOGTAG,"onReceive " + intent.getAction());
	        	
	        		if (intent.getExtras().containsKey(ProxyHelper.EXTRA_PACKAGE_NAME)
	        				&& intent.getStringExtra(ProxyHelper.EXTRA_PACKAGE_NAME).equals(PsiphonHelper.PACKAGE_NAME)) {
	        			gotPsiphonInfo(intent);
	        		}
	        	
		    }
		     
		};
		applicationContext.registerReceiver(psiphonHelperReceiver, new IntentFilter(ProxyHelper.ACTION_STATUS));
		
		LocalBroadcastManager.getInstance(_context).registerReceiver(
				new BroadcastReceiver() {
			        @Override
			        public void onReceive(Context context, Intent intent) {
			        	if (LOGGING)
			        		Log.v(LOGTAG,"****INTENT_NEW_SECRETS*****");
			        	if (intent.getAction().equals(Constants.INTENT_NEW_SECRETS)) {
			            	// Locked because of timeout
			            	if (initialized && cacheWord.getCachedSecrets() == null)
			            		SocialReader.this.onCacheWordLocked();
			            }
			        }
			    }, new IntentFilter(Constants.INTENT_NEW_SECRETS));


	}
		
    private static SocialReader socialReader = null;
    public static SocialReader getInstance(Context _context) {
    	if (socialReader == null) {
    		socialReader = new SocialReader(_context);
    	}
    	return socialReader;
    }

    public HttpClient getHttpClient ()
    {
        return httpClient;
    }

	private void initHttpClient (Context context)
	{
		OrbotHelper.get(context).init();

		try {
			StrongHttpClientBuilder builder = new StrongHttpClientBuilder(context);

			//RSS feeds and blog platforms often have redirects, that we need to handle automatically
			builder.setRedirectStrategy(new LaxRedirectStrategy());

			if (socialReader.useProxy()) {
                if (socialReader.getProxyType().equalsIgnoreCase("socks"))
                    builder = builder.withSocksProxy();
                else
                    builder = builder.withHttpProxy();
                //				    httpClient.useProxy(true, socialReader.getProxyType(), socialReader.getProxyHost(), socialReader.getProxyPort());

                builder.build(new StrongBuilder.Callback<HttpClient>() {
                    @Override
                    public void onConnected(HttpClient httpClient) {

                        SocialReader.this.httpClient = httpClient;
                    }

                    @Override
                    public void onConnectionException(Exception e) {
                        Log.w(LOGTAG, "Couldn't connet httpclient", e);
                    }

                    @Override
                    public void onTimeout() {
                        Log.w(LOGTAG, "build httpclient timeout");
                    }

                    @Override
                    public void onInvalid() {
                        Log.w(LOGTAG, "build httpclient invalid");
                    }
                });
            }
            else
            {
                Intent intent = new Intent();
                intent.putExtra("org.torproject.android.intent.extra.STATUS","OFF");
                SocialReader.this.httpClient = builder.build(intent);
            }
		}
		catch (Exception e)
		{
			Log.e(LOGTAG,"error fetching feed",e);
		}

	}

	Timer periodicTimer;
	TimerTask periodicTask;
	
	class TimerHandler extends Handler {
        @Override
        public void dispatchMessage(Message msg) {
        	
        	if (LOGGING)
        		Log.v(LOGTAG,"Timer Expired");

    		if (settings.syncFrequency() != Settings.SyncFrequency.Manual) {
    			
    			if (LOGGING)
    				Log.v(LOGTAG, "Sync Frequency not manual");
    			
    			if ((appStatus == SocialReader.APP_IN_BACKGROUND && settings.syncFrequency() == Settings.SyncFrequency.InBackground)
    				|| appStatus == SocialReader.APP_IN_FOREGROUND) {
    				
    				if (LOGGING)
    					Log.v(LOGTAG, "App in background and sync frequency set to in background OR App in foreground");
    				
    	        	checkOPML();
    				backgroundSyncSubscribedFeeds();
    				checkMediaDownloadQueue();
    			} else {
    				if (LOGGING)
    					Log.v(LOGTAG, "App in background and sync frequency not set to background");
    			}
    		} else {
    			if (LOGGING)
    				Log.v(LOGTAG, "Sync Frequency manual, not taking action");
    		}
			expireOldContent();
        }
	}
	TimerHandler timerHandler = new TimerHandler();
	
    private SyncService syncService;

    public SyncService getSyncService()
    {
    	return syncService;
    }
    
    public void setLockListener(SocialReaderLockListener lockListener)
    {
    	this.lockListener = lockListener;
    }

	public SocialReaderFeedPreprocessor getFeedPreprocessor()
	{
		return this.feedPreprocessor;
	}

	public void setFeedPreprocessor(SocialReaderFeedPreprocessor feedPreprocessor)
	{
		this.feedPreprocessor = feedPreprocessor;
	}

	private boolean initialized = false;
	public void initialize() {
		if (LOGGING)
			Log.v(LOGTAG,"initialize");

	    if (!initialized) {

            initializeFileSystemCache();
            initializeDatabase();
            
            ssettings = new SecureSettings(databaseAdapter);
            if (LOGGING)
            	Log.v(LOGTAG,"SecureSettings initialized");

			initHttpClient(applicationContext);

			syncService = SyncService.getInstance(applicationContext, this);

            periodicTask = new TimerTask() {
                @Override
                public void run() {
                	timerHandler.sendEmptyMessage(0);
                }
            };

            periodicTimer = new Timer();
            periodicTimer.schedule(periodicTask, 0, TIMER_PERIOD);            
            
            initialized = true;
            if (lockListener != null)
            	lockListener.onUnlocked();
	    } else {
	    	if (LOGGING)
	    		Log.v(LOGTAG,"Already initialized!");
	    }
	}

	public void uninitialize() {
		if (syncService != null) {
			syncService.cancelAll();
			syncService = null;
		}

		// If we aren't going to do any background syncing, stop the service
		// Cacheword's locked, we can't do any background work
		//if (settings.syncFrequency() != Settings.SyncFrequency.InBackground)
		//{
		//	if (LOGGING)
		//		Log.v(LOGTAG,"settings.syncFrequency() != Settings.SyncFrequency.InBackground so we are stopping the service");
			applicationContext.stopService(new Intent(applicationContext, SyncService.class));

			if (databaseAdapter != null && databaseAdapter.databaseReady()) {
				if (LOGGING) 
					Log.v(LOGTAG,"database needs closing, doing that now");
	        	databaseAdapter.close();
	        } else {
	        	if (LOGGING) 
					Log.v(LOGTAG,"database doesn't needs closing, strange...");
	        }

			/* unmount is a noop in iocipher 
	        if (vfs != null && vfs.isMounted()) {
	        	if (LOGGING)
	        		Log.v(LOGTAG,"file system mounted, unmounting now");
	        	vfs.unmount();
	        	
	        	for (int i = 0; i < 100; i++) {
	        		if (vfs.isMounted()) {
	        			if (LOGGING)
	        				Log.v(LOGTAG,"file system is still mounted, what's up?!");
	        		} else {
	        			if (LOGGING)
	        				Log.v(LOGTAG,"All is well!");
	        		}
	        	}
	        }
	        else {
	        	if (LOGGING)
	        		Log.v(LOGTAG,"file system not mounted, no need to unmount");
	        }
	        */
		//}
		
		if (periodicTimer != null)
			periodicTimer.cancel();
		
		initialized = false;
        if (lockListener != null)
        	lockListener.onLocked();
	}
	
	public void loadOPMLFile() {
		if (LOGGING)
			Log.v(LOGTAG,"loadOPMLFile()");
		
		logStatus();
		
		if (!settings.localOpmlLoaded()) {
			if (LOGGING)
				Log.v(LOGTAG, "Local OPML Not previously loaded, loading now");
			Resources res = applicationContext.getResources();
			InputStream inputStream = res.openRawResource(R.raw.bigbuffalo_opml);
			
			OPMLParser oParser = new OPMLParser(inputStream,
					new OPMLParser.OPMLParserListener() {
						@Override
						public void opmlParsed(ArrayList<OPMLParser.OPMLOutline> outlines) {
							if (LOGGING)
								Log.v(LOGTAG,"Finished Parsing OPML Feed");
							
							if (outlines != null) {
								for (int i = 0; i < outlines.size(); i++) {
									OPMLParser.OPMLOutline outlineElement = outlines.get(i);
										
									Feed newFeed = new Feed(outlineElement.text, outlineElement.xmlUrl);
									newFeed.setDescription(outlineElement.description);
									newFeed.setSubscribed(outlineElement.subscribe);
									
									databaseAdapter.addOrUpdateFeed(newFeed);
									if (LOGGING)
										Log.v(LOGTAG,"May have added feed");
								}
							} else {
								if (LOGGING)
									Log.e(LOGTAG,"Received null after OPML Parsed");
							}
							settings.setLocalOpmlLoaded();
							backgroundSyncSubscribedFeeds();
						}
					}
				);
		}
	}
	
	public void feedSubscriptionsChanged() {
		clearMediaDownloadQueue();
		checkMediaDownloadQueue();
	}
	
	private void expireOldContent() {
		if (LOGGING)
			Log.v(LOGTAG,"expireOldContent");
		if (settings.articleExpiration() != Settings.ArticleExpiration.Never) {
			if (settings.lastItemExpirationCheckTime() < System.currentTimeMillis() - expirationCheckFrequency) {
				if (LOGGING)
					Log.v(LOGTAG,"Checking Article Expirations");
				settings.setLastItemExpirationCheckTime(System.currentTimeMillis());
				Date expirationDate = new Date(System.currentTimeMillis() - settings.articleExpirationMillis());
				if (databaseAdapter != null && databaseAdapter.databaseReady())
					databaseAdapter.deleteExpiredItems(expirationDate);
			}
		} else {
			if (LOGGING)
				Log.v(LOGTAG,"Settings set to never expire");
		}
	}
	
	public void checkForRSSFeed(String url) {
		if (databaseAdapter != null && databaseAdapter.databaseReady() && isOnline() == ONLINE) {
			HTMLRSSFeedFinder htmlParser = new HTMLRSSFeedFinder(SocialReader.this, url,
				new HTMLRSSFeedFinder.HTMLRSSFeedFinderListener() {
					@Override
					public void feedFinderComplete(ArrayList<RSSFeed> rssFeeds) {
						if (LOGGING)
							Log.v(LOGTAG,"Finished Parsing HTML File");
						if (rssFeeds != null) {
							for (int i = 0; i < rssFeeds.size(); i++) {
								Feed newFeed = new Feed(rssFeeds.get(i).title, rssFeeds.get(i).href);
								newFeed.setSubscribed(true);
								databaseAdapter.addOrUpdateFeed(newFeed);
							}
						} else {
							if (LOGGING)
								Log.e(LOGTAG,"Received null after HTML Parsed");
						}	
					}
				}
			);
		} else {
			// Not online
			if (LOGGING)
				Log.v(LOGTAG, "Can't check feed, not online");
		}
	}

	private void checkOPML() {
		if (LOGGING)
			Log.v(LOGTAG,"checkOPML");
		logStatus();
		
		if (LOGGING) 
			Log.v(LOGTAG, settings.lastOPMLCheckTime() + " < " +  System.currentTimeMillis() + " - " + opmlCheckFrequency);
		
		if ((repeatedlyLoadNetworkOPML || !settings.networkOpmlLoaded()) && databaseAdapter != null && databaseAdapter.databaseReady() && httpClient != null && !cacheWord.isLocked() && isOnline() == ONLINE && settings.lastOPMLCheckTime() < System.currentTimeMillis() - opmlCheckFrequency) {
			
			if (LOGGING)
				Log.v(LOGTAG,"Checking network OPML");

			UiLanguage lang = settings.uiLanguage();
			String finalOpmlUrl = opmlUrl + "?lang=";
						
			if (lang == UiLanguage.Farsi) {
				finalOpmlUrl = finalOpmlUrl + "fa_IR";
			} else if (lang == UiLanguage.English) {
				finalOpmlUrl = finalOpmlUrl + "en_US";
			} else if (lang == UiLanguage.Tibetan) {
				finalOpmlUrl = finalOpmlUrl + "bo_CN";
			} else if (lang == UiLanguage.Chinese) {
				finalOpmlUrl = finalOpmlUrl + "zh_CN";
			} else if (lang == UiLanguage.Russian) {
				finalOpmlUrl = finalOpmlUrl + "ru_RU";
			} else if (lang == UiLanguage.Ukrainian) {
				finalOpmlUrl = finalOpmlUrl + "uk_UA";
			} else if (lang == UiLanguage.Spanish) {
				finalOpmlUrl = finalOpmlUrl + "es";
			} else if (lang == UiLanguage.Spanish_US) {
				finalOpmlUrl = finalOpmlUrl + "es_US";
			} else if (lang == UiLanguage.Japanese) {
				finalOpmlUrl = finalOpmlUrl + "ja";
			} else if (lang == UiLanguage.Norwegian) {
				finalOpmlUrl = finalOpmlUrl + "nb";
			} else if (lang == UiLanguage.Turkish) {
				finalOpmlUrl = finalOpmlUrl + "tr";
			} else if (lang == UiLanguage.German) {
				finalOpmlUrl = finalOpmlUrl + "de";
			}
			
			if (!settings.networkOpmlLoaded()) {
				finalOpmlUrl += "&first=true";
			}
			
			if (applicationContext.getResources().getBoolean(R.bool.fulltextfeeds)) {
				finalOpmlUrl += "&fulltext=true";
			}
			
			if (REPORT_METRICS) {
				ConnectivityManager connectivityManager = (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo networkInfo;

				int connectionType = -1;
				// Check WiFi
				networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
				if (networkInfo != null && networkInfo.isConnected())
				{
					connectionType = 1;
				} 
				else if (settings.syncNetwork() != Settings.SyncNetwork.WifiOnly) 
				{
					// Check any network type
					networkInfo = connectivityManager.getActiveNetworkInfo();
					if (networkInfo != null && networkInfo.isConnected())
					{
						connectionType = 2;
					}
				}
				String connectionTypeParam = "&nct=" + connectionType;
				finalOpmlUrl += connectionTypeParam;
						
				String proxyTypeParam = "&p=";
				if (settings.requireProxy() && settings.proxyType() == Settings.ProxyType.Tor) {
					
					if (torRunning) {
						proxyTypeParam += "1";
					} else {
						proxyTypeParam += "-1";
					}
				} 
				else if (settings.requireProxy() && settings.proxyType() == Settings.ProxyType.Psiphon) 
				{
					if (psiphonRunning) {
						proxyTypeParam += "2";
					} else {
						proxyTypeParam += "-2";
					}
				} 
				else
				{
					proxyTypeParam += 0;
				}
				finalOpmlUrl += proxyTypeParam;
				
				String apiLevelParam = "&a=" + android.os.Build.VERSION.SDK_INT;
				finalOpmlUrl += apiLevelParam;
				
				try {
					String deviceNameParam = "&dn=" + URLEncoder.encode(android.os.Build.DEVICE, "UTF-8");
					finalOpmlUrl += deviceNameParam;
				} catch (UnsupportedEncodingException e) {
					if (LOGGING)
						e.printStackTrace();
				}
				
				String numFeedsParam = "&nf=" + getSubscribedFeedsList().size();
				finalOpmlUrl += numFeedsParam;
			}
			
			if (TESTING) 
				finalOpmlUrl += "&testing=1";
			
			if (LOGGING)
				Log.v(LOGTAG, "OPML Feed Url: " + finalOpmlUrl);
			
			settings.setLastOPMLCheckTime(System.currentTimeMillis());
			
				OPMLParser oParser = new OPMLParser(SocialReader.this, finalOpmlUrl,
					new OPMLParser.OPMLParserListener() {
						@Override
						public void opmlParsed(ArrayList<OPMLParser.OPMLOutline> outlines) {
							if (LOGGING)
								Log.v(LOGTAG,"Finished Parsing OPML Feed");
									
							if (outlines != null) {
								for (int i = 0; i < outlines.size(); i++) {
									OPMLParser.OPMLOutline outlineElement = outlines.get(i);
									if (outlineElement.xmlUrl != null) {
										Feed newFeed = new Feed(outlineElement.text, outlineElement.xmlUrl.trim());
										newFeed.setDescription(outlineElement.description);
										newFeed.setSubscribed(outlineElement.subscribe && !databaseAdapter.isFeedUnfollowed(outlineElement.xmlUrl));

										if (LOGGING)
											Log.v(LOGTAG, "**New Feed: " + newFeed.getFeedURL() + " " + newFeed.isSubscribed());

										databaseAdapter.addOrUpdateFeed(newFeed);
									}
								}
							} else {
								if (LOGGING)
									Log.e(LOGTAG,"Received null after OPML Parsed");
							}
							settings.setNetworkOpmlLoaded();
							backgroundSyncSubscribedFeeds();
						}
					}
				);
			} else {
				if (LOGGING)
					Log.v(LOGTAG,"Not checking OPML at this time");
			}
	}

	public File exportSubscribedFeedsAsOPML(String fileName) {

		try {
			File opmlDir = new File(getVFSSharingDir(), "opml");
			if (!opmlDir.exists() && !opmlDir.mkdir()) {
				return null;
			}
			File savedFile = new File(opmlDir, fileName);
			if (!savedFile.exists() && !savedFile.createNewFile()) {
				return null;
			}

			ArrayList<Feed> subscribedFeeds = getSubscribedFeedsList();
			StringBuilder sb = new StringBuilder();
			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
			sb.append("<opml version=\"1.1\">");
			sb.append("<head>");
			sb.append("<title>");
			sb.append(applicationContext.getString(R.string.app_name));
			sb.append("</title>");
			sb.append("<dateCreated>");
			sb.append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date()));
			sb.append("</dateCreated>");
			sb.append("</head>");
			sb.append("<body>");
			for (Feed feed : subscribedFeeds) {
				sb.append(String.format("<outline text=\"%1$s\" xmlUrl=\"%2$s\" />", TextUtils.htmlEncode(feed.getTitle()), TextUtils.htmlEncode(feed.getFeedURL())));
			}
			sb.append("</body>");
			sb.append("</opml>");

			Document document = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder()
					.parse(new InputSource(new ByteArrayInputStream(sb.toString().getBytes("utf-8"))));

			XPath xPath = XPathFactory.newInstance().newXPath();
			NodeList nodeList = (NodeList) xPath.evaluate("//text()[normalize-space()='']",
					document,
					XPathConstants.NODESET);

			for (int i = 0; i < nodeList.getLength(); ++i) {
				Node node = nodeList.item(i);
				node.getParentNode().removeChild(node);
			}

			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

			FileWriter fileWriter = new FileWriter(savedFile);
			StreamResult streamResult = new StreamResult(fileWriter);
			transformer.transform(new DOMSource(document), streamResult);
			return savedFile;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}


	boolean cacheWordAttached = true;
	
	// When the foreground app is paused
	public void onPause() {
		if (LOGGING)
			Log.v(LOGTAG, "SocialReader onPause");
		appStatus = SocialReader.APP_IN_BACKGROUND;
		
		if (settings.passphraseTimeout() == 1) {
			cacheWord.lock();
		}
		
		cacheWord.detach();
		cacheWordAttached = false;
		
		//cacheWord.disconnectFromService();
	}

	// When the foreground app is unpaused
	public void onResume() {
		if (LOGGING)
			Log.v(LOGTAG, "SocialReader onResume");
        appStatus = SocialReader.APP_IN_FOREGROUND;
        
        checkTorStatus();
		checkPsiphonStatus();
        
		if (cacheWordAttached == false)
			cacheWord.reattach();
	}
	
	public void setCacheWordTimeout(int seconds)
	{
		if (LOGGING)
			Log.v(LOGTAG,"setCacheWordTimeout " + seconds);
		
		try {
			cacheWord.setTimeout(seconds);
		} catch (IllegalStateException e) {
			if (LOGGING)
				Log.e(LOGTAG, e.getMessage());	
		}
	}

	public void checkTorStatus() {
		if (LOGGING)
			Log.v(LOGTAG,"checkTorStatus");
		if (useProxy() && settings.proxyType() == ProxyType.Tor && OrbotHelper.isOrbotInstalled(applicationContext)) {
			OrbotHelper.requestStartTor(applicationContext);
		}
	}
	
	public void checkPsiphonStatus() {
		if (LOGGING)
			Log.v(LOGTAG,"checkPsiphonStatus");
		if (psiphonHelper.isInstalled(applicationContext)) {
			psiphonHelper.requestStatus(applicationContext);
		}
	}	
	
	// When we get data from Psiphon Intent
	public void gotPsiphonInfo(Intent psiphonHelperIntent) {
		if (LOGGING)
			Log.v(LOGTAG,"gotPsiphonInfo");
		
		if (psiphonHelperIntent.getExtras().containsKey(PsiphonHelper.EXTRA_STATUS))
		{
			if (psiphonHelperIntent.getStringExtra(PsiphonHelper.EXTRA_STATUS).equals(PsiphonHelper.STATUS_ON)) 
			{
				if (LOGGING)
					Log.v(LOGTAG,"psiphonRunning = true");
				
				psiphonRunning = true;
				
				if (psiphonHelperIntent.getExtras().containsKey(PsiphonHelper.EXTRA_PROXY_PORT_SOCKS))
				{
					//PSIPHON_PROXY_PORT = psiphonHelperIntent.getIntExtra(PsiphonHelper.EXTRA_PROXY_PORT_SOCKS,-1);
					PSIPHON_PROXY_PORT = psiphonHelperIntent.getIntExtra(PsiphonHelper.EXTRA_PROXY_PORT_HTTP,-1);
					
					if (LOGGING)
						Log.v(LOGTAG,"PSIPHON_PROXY_PORT = " + PSIPHON_PROXY_PORT);
					
				}
			} else {
				psiphonRunning = false;
				PSIPHON_PROXY_PORT = -1;
			}
		}
	}
	
	public boolean isTorOnline() {
		if (OrbotHelper.isOrbotInstalled(applicationContext) && torRunning)
		{
			return true;
		} 
		else 
		{
			return false;
		}
	}
	
	public boolean isPsiphonOnline() {
		if (psiphonHelper.isInstalled(applicationContext) && psiphonRunning) {
			return true;
		} else {
			return false;
		}
	}
	
	public boolean isProxyOnline() 
	{
		if (useProxy() && settings.proxyType() == ProxyType.Tor 
				&& isTorOnline()) 
		{
			if (LOGGING) 
				Log.v(LOGTAG, "Tor is running");
			
			return true;
		} 
		else if (useProxy() && settings.proxyType() == ProxyType.Psiphon 
				&& isPsiphonOnline())
		{
			
			if (LOGGING)
				Log.v(LOGTAG, "Psiphon is running");
			
			return true;
		}
		else {
			return false;
		}
	}
	
	private void logStatus() {
		if (LOGGING)
			Log.v(LOGTAG, "Status Check: ");
		
		if (databaseAdapter != null) {
			if (LOGGING) {
				Log.v(LOGTAG, "databaseAdapter != null");
				Log.v(LOGTAG, "databaseAdapter.databaseReady() " + databaseAdapter.databaseReady());
			}
		} else {
			if (LOGGING)
				Log.v(LOGTAG, "databaseAdapter == null");			
		}
		if (LOGGING) {
			Log.v(LOGTAG, "cacheWord.isLocked() " + cacheWord.isLocked());
			Log.v(LOGTAG, "isOnline() " + isOnline());
		}
	}

	// This public method will indicate whether or not the application is online
	// it takes into account whether or not the application should be online (connectionMode)
	// as well as the physical network connection and proxy status
	public int isOnline()
	{
		ConnectivityManager connectivityManager = (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo;

		if (settings.syncNetwork() == Settings.SyncNetwork.WifiOnly) {
			networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		} else {
			networkInfo = connectivityManager.getActiveNetworkInfo();
		}

		if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
			if (settings.requireProxy() && isProxyOnline()) {
				return ONLINE;
			}
			else if (settings.requireProxy())
			{
				// Either the proxy isn't connected or they haven't selected one yet which happens during onboarding
				return NOT_ONLINE_NO_PROXY;
			}
			else {
				// Network is connected and we don't use a proxy
				return ONLINE;
			}
		} else {
			// Network not connected
			if (settings.syncNetwork() == Settings.SyncNetwork.WifiOnly) {
				return NOT_ONLINE_NO_WIFI;
			}
			else {
				return NOT_ONLINE_NO_WIFI_OR_NETWORK;
			}
		}
	}

	// Working hand in hand with isOnline this tells other classes whether or not they should use a proxy when connecting
	public boolean useProxy() {
		if (settings.requireProxy()) {
			if (LOGGING)
				Log.v(LOGTAG, "USE Proxy");
			return true;
		} else {
			if (LOGGING)
				Log.v(LOGTAG, "DON'T USE Proxy");
			return false;
		}
	}

	public String getProxyType() {
		if (settings.proxyType() == ProxyType.Psiphon) {
			return PSIPHON_PROXY_TYPE;
		} else {
			return TOR_PROXY_TYPE;
		}			
	}
	
	public String getProxyHost() {
		if (settings.proxyType() == ProxyType.Psiphon) {
			return PSIPHON_PROXY_HOST;
		} else {
			return TOR_PROXY_HOST;
		}	
	}
	
	public int getProxyPort() {
		if (settings.proxyType() == ProxyType.Psiphon) {
			return PSIPHON_PROXY_PORT;
		} else {
			return TOR_PROXY_PORT;
		}
	}
	
	public boolean connectProxy(Activity _activity)
	{
		if (LOGGING) {
			Log.v(LOGTAG, "Checking Proxy");
			
			if (settings.requireProxy()) {
				Log.v(LOGTAG, "Require Proxy is True");
			} else {
				Log.v(LOGTAG, "Require Proxy is False");
			}
		
			if (settings.proxyType() == ProxyType.Tor) {
				Log.v(LOGTAG, "Proxy Type Tor is selected");
				Log.v(LOGTAG, "isOrbotInstalled: " + OrbotHelper.isOrbotInstalled(applicationContext));
				Log.v(LOGTAG, "isOrbotRunning: " + torRunning);
			} else if (settings.proxyType() == ProxyType.Psiphon) {
				Log.v(LOGTAG, "Proxy Type Psiphon is selected");
				Log.v(LOGTAG, "isInstalled: " + psiphonHelper.isInstalled(applicationContext));
				Log.v(LOGTAG, "psiphonRunning: " + psiphonRunning);
			}
		}

		if (settings.proxyType() == ProxyType.Tor) 
		{
			
			if (!OrbotHelper.isOrbotInstalled(applicationContext))
			{				
				Intent intentInstall = OrbotHelper.getOrbotInstallIntent(applicationContext);
				intentInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				_activity.startActivity(intentInstall);
			}
			else if (!torRunning)
			{
				OrbotHelper.requestShowOrbotStart(_activity);
			}				
			
		}
		else if (settings.proxyType() == ProxyType.Psiphon) 
		{
			if (!psiphonHelper.isInstalled(applicationContext))
			{
				Intent intentInstall = psiphonHelper.getInstallIntent(applicationContext);
				intentInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				_activity.startActivity(intentInstall);
			}
			else if (!psiphonRunning)
			{
				Intent startIntent = psiphonHelper.getStartIntent(applicationContext);
				startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				_activity.startActivityForResult(startIntent,-1);
			}				
		}		

        initHttpClient(applicationContext);

		return true;
	}
	
	public long getDefaultFeedId() {
		return defaultFeedId;
	}

	/*
	 * Return ArrayList of all Feeds in the database, these feed objects will
	 * not contain item data
	 */
	public ArrayList<Feed> getFeedsList()
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.getAllFeeds();
		}
		else
		{
			return new ArrayList<Feed>();
		}
	}

	/*
	 * Return ArrayList of all Feeds that the user is subscribed to in the
	 * database, these feed objects will not contain item data
	 */
	public ArrayList<Feed> getSubscribedFeedsList()
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.getSubscribedFeeds();
		}
		else
		{
			return new ArrayList<Feed>();
		}
	}

	/*
	 * Return ArrayList of all Feeds in the database that the user is NOT
	 * subscribed to, these feed objects will not contain item data
	 */
	public ArrayList<Feed> getUnsubscibedFeedsList()
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.getUnSubscribedFeeds();
		}
		else
		{
			return new ArrayList<Feed>();
		}
	}

	public Feed getFeedById(long feedDatabaseId) {
		if (databaseAdapter != null && databaseAdapter.databaseReady()) {
			return databaseAdapter.getFeedById(feedDatabaseId);
		} else {
			return null;
		}
	}

	/*
	 * Utilizes the SyncService to Requests feed and feed items to be pulled from the network
	 */
	private void backgroundRequestFeedNetwork(Feed feed, SyncTaskFeedFetcherCallback callback)
	{
		if (LOGGING)
			Log.v(LOGTAG,"requestFeedNetwork");

		if (syncService != null) {
			if (LOGGING)
				Log.v(LOGTAG,"syncService != null");
			// TODO - Check errors and backoff info
			syncService.addFeedSyncTask(feed, false, callback);
		} else {
			if (LOGGING)
				Log.v(LOGTAG,"syncService is null!");
		}
	}

	/*
	 * Requests feed and feed items to be pulled from the network returns false
	 * if feed cannot be requested from the network
	 */
	private boolean foregroundRequestFeedNetwork(Feed feed, FeedFetcher.FeedFetchedCallback callback)
	{
		FeedFetcher feedFetcher = new FeedFetcher(this);
		feedFetcher.setFeedUpdatedCallback(callback);

		if (isOnline() == ONLINE || feed.getFeedURL().startsWith("file:///"))
		{
			if (LOGGING)
				Log.v(LOGTAG, "Calling feedFetcher.execute: " + feed.getFeedURL());
			feedFetcher.execute(feed);
			return true;
		}
		else
		{
			return false;
		}
	}

	// Do network feed refreshing in the background
	private void backgroundSyncSubscribedFeeds()
	{
		if (LOGGING)
			Log.v(LOGTAG,"backgroundSyncSubscribedFeeds()");

		if (!cacheWord.isLocked()) {
			final ArrayList<Feed> feeds = getSubscribedFeedsList();
			
			if (LOGGING) 
				Log.v(LOGTAG,"Num Subscribed feeds:" + feeds.size());
			
			for (Feed feed : feeds)
			{
				if (LOGGING) 
					Log.v(LOGTAG,"Checking: " + feed.getFeedURL());
				
				if (feed.isSubscribed() && shouldRefresh(feed) && isOnline() == ONLINE) {
					if (LOGGING)
						Log.v(LOGTAG,"It should be refreshed");

					backgroundRequestFeedNetwork(feed, new SyncTaskFeedFetcherCallback() {
						@Override
						public void feedFetched(Feed _feed) {
							if (feedsWithComments != null)
							{
								if (LOGGING) 
									Log.v(LOGTAG,"Does " + _feed.getFeedURL() + " = " + feedsWithComments[0]);
								
								if (Arrays.asList(feedsWithComments).contains(_feed.getFeedURL())) {
									if (LOGGING)
										Log.v(LOGTAG, "Checking Comments on " + _feed.getDatabaseId());
									networkCheckCommentFeeds(_feed);
								}
							}
							else if (LOGGING)
								Log.e(LOGTAG,"feedsWithComments is null!!!");
						}

						@Override
						public void feedFetchError(Feed _feed) {
						}
					});
				} else if (isOnline() != ONLINE) {
					if (LOGGING)
						Log.v(LOGTAG,"not refreshing, not online: " + isOnline());
				} else {
					if (LOGGING)
						Log.v(LOGTAG,"doesn't need refreshing");
				}
			}
			
			// Check Talk Feed
			if (isOnline() == ONLINE && syncService != null && talkItem != null && !TextUtils.isEmpty(talkItem.getCommentsUrl())) {
				if (LOGGING)
					Log.v(LOGTAG,"Adding talkItem to syncService");
				syncService.addCommentsSyncTask(talkItem, false, null);
			} else if (LOGGING) {
				Log.v(LOGTAG,"Unable to add talkItem to syncService");
			}
			
		} else {
			if (LOGGING)
				Log.v(LOGTAG, "Can't sync feeds, cacheword locked");
		}
	}
	
	public boolean isFeedCommentable(Feed _feed) {
		if (feedsWithComments != null)
		{
			if (_feed.getFeedURL() == null) {
				databaseAdapter.fillFeedObject(_feed);
			}
					
			if (Arrays.asList(feedsWithComments).contains(_feed.getFeedURL())) {
				if (LOGGING)
					Log.v(LOGTAG, "Checking Comments on " + _feed.getDatabaseId());
				return true;
			} else {
				return false;
			}			
		}
		else 
		{
			if (LOGGING)	
				Log.e(LOGTAG,"feedsWithComments is null!!!");
			return false;			
		}
	}
	
	public void clearMediaDownloadQueue() {
		if (LOGGING) 
			Log.v(LOGTAG, "clearMediaDownloadQueue");		
		
		if (!cacheWord.isLocked() && isOnline() == ONLINE && 
				settings.syncMode() != Settings.SyncMode.BitWise
				&& syncService != null) {
				
			syncService.cancelAll();
			backgroundSyncSubscribedFeeds();
		}
		
	}	

	public void checkMediaDownloadQueue() {
		if (LOGGING) 
			Log.v(LOGTAG, "checkMediaDownloadQueue");		
		
		if (!cacheWord.isLocked() && 
				settings.syncMode() != Settings.SyncMode.BitWise
				&& syncService != null) {
			
			if (LOGGING) 
				Log.v(LOGTAG, "In right state, definitely checkMediaDownloadQueue");
				
			int numWaiting = syncService.getNumWaitingToSync();
			
			if (LOGGING) 
				Log.v(LOGTAG, "Num Waiting TO Sync: " + numWaiting);
			
			if (numWaiting > 0) {
				// Send a no-op to get any going that should be going?
				
			} else {
				
				// Check database for new items to sync
				if (databaseAdapter != null && databaseAdapter.databaseReady())
				{
					// Delete over limit media
					int numDeleted = databaseAdapter.deleteOverLimitMedia(mediaCacheSizeLimitInBytes, this);
					
					if (LOGGING)
						Log.v(LOGTAG,"Deleted " + numDeleted + " over limit media items");
					
					long mediaFileSize = databaseAdapter.mediaFileSize();
					
					if (LOGGING)
						Log.v(LOGTAG,"Media File Size: " + mediaFileSize + " limit is " + mediaCacheSizeLimitInBytes);
					
					if (mediaFileSize < mediaCacheSizeLimitInBytes) {
						
						ArrayList<Item> itemsToDownload = databaseAdapter.getItemsWithMediaNotDownloaded(MEDIA_ITEM_DOWNLOAD_LIMIT_PER_FEED_PER_SESSION);
						if (LOGGING) 
							Log.v(LOGTAG,"Got " + itemsToDownload.size() + " items to download from database");
						
						for (Item item : itemsToDownload)
						{
							ArrayList<MediaContent> mc = item.getMediaContent();
							for (MediaContent m : mc) {
								
								if (LOGGING)
									Log.v(LOGTAG, "Adding to sync " + m.getUrl());
								
								syncService.addMediaContentSyncTask(m, false, null);
							}
						}
					}
				}	
			}
		}
		
	}

	boolean _manualSyncInProgress = false;
	public boolean manualSyncInProgress() {
		return _manualSyncInProgress;
	}

	public boolean manualSyncSubscribedFeeds(final FeedFetcher.FeedFetchedCallback _finalCallback)
	{
		if (!_manualSyncInProgress && isOnline() == ONLINE)
		{
			final ArrayList<Feed> feeds = getSubscribedFeedsList();
			if (feeds.size() > 0)
			{
				_manualSyncInProgress = true;
				SyncTaskFeedFetcher.SyncTaskFeedFetcherCallback callback = new SyncTaskFeedFetcher.SyncTaskFeedFetcherCallback() {
					@Override
					public void feedFetched(Feed _feed) {
						if (LOGGING)
							Log.v(LOGTAG, "Manual resync done!");
						_manualSyncInProgress = false;
						if (_finalCallback != null) {
							_finalCallback.feedFetched(_feed);
						}
					}

					@Override
					public void feedFetchError(Feed _feed) {
						if (LOGGING)
							Log.v(LOGTAG, "Manual resync failed!");
						_manualSyncInProgress = false;
						if (_finalCallback != null) {
							_finalCallback.feedError(_feed);
						}
					}
				};
				syncService.addFeedsSyncTask(feeds, true, callback);
				return true;
			}
		}
		return false;
	}
	
	/*
	 * This is to manually sync a specific feed. It takes a callback that will
	 * be used to notify the listener that the network process is complete. This
	 * will override the default syncing behavior forcing an immediate network
	 * sync.
	 */
	public boolean manualSyncFeed(Feed feed, final FeedFetcher.FeedFetchedCallback _finalCallback) {
		//TODO refacoring
		if (isOnline() == ONLINE) {
			SyncTaskFeedFetcher.SyncTaskFeedFetcherCallback callback = new SyncTaskFeedFetcher.SyncTaskFeedFetcherCallback() {
				@Override
				public void feedFetched(Feed _feed) {
					if (LOGGING)
						Log.v(LOGTAG, "Manual resync done!");
					_manualSyncInProgress = false;
					if (_finalCallback != null) {
						_finalCallback.feedFetched(_feed);
					}
				}

				@Override
				public void feedFetchError(Feed _feed) {
					if (LOGGING)
						Log.v(LOGTAG, "Manual resync failed!");
					_manualSyncInProgress = false;
					if (_finalCallback != null) {
						_finalCallback.feedError(_feed);
					}
				}
			};
			syncService.addFeedSyncTask(feed, true, callback);
			return true;
		}
		return false;
	}

	/*
	 * This will get a feed's items from the database.
	 */
	public Feed getFeed(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			if (LOGGING)
				Log.v(LOGTAG, "Feed from Database");
			feed = databaseAdapter.getFeedItems(feed, DEFAULT_NUM_FEED_ITEMS);
		}
		return feed;
	}

	Feed manualCompositeFeed = new Feed();

	/*
	public Feed getSubscribedFeedItems()
	{
		Feed returnFeed = new Feed();
		ArrayList<Feed> feeds = getSubscribedFeedsList();

		for (Feed feed : feeds)
		{
			returnFeed.addItems(getFeed(feed).getItems());
		}

		return returnFeed;
	}
	*/
	
	public Feed getSubscribedFeedItems()
	{
		Feed returnFeed = new Feed();
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			try
			{
				returnFeed = databaseAdapter.getSubscribedFeedItems(DEFAULT_NUM_FEED_ITEMS);
			}
			catch(IllegalStateException e)
			{
				e.printStackTrace();
			}
		}
		return returnFeed;
	}

	public Cursor getItemsCursor(long feedId, Boolean subscribed, Boolean favorited, Boolean shared, String searchPhrase, boolean randomized, int limit)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			try
			{
				return databaseAdapter.getItemsCursor(feedId, subscribed, favorited, shared, searchPhrase, randomized, limit);
			}
			catch(IllegalStateException e)
			{
				e.printStackTrace();
			}
		}
		return null;
	}

	public Item itemFromCursor(Cursor cursor, int position) {
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			try
			{
				return databaseAdapter.itemFromCursor(cursor, position);
			}
			catch(IllegalStateException e)
			{
				e.printStackTrace();
			}
		}
		return null;
	}

	public Feed getFeedItemsWithTag(Feed feed, String tag) {
		Feed returnFeed = new Feed();
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			returnFeed.addItems(databaseAdapter.getFeedItemsWithTag(feed, tag));
		}
		return returnFeed;
	}

	public Feed getPlaylist(Feed feed) {
		
		ArrayList<String> tags = new ArrayList<String>();
		// Tempo
		tags.add("slow"); // -1
		tags.add("medium"); // 0
		tags.add("fast"); // +1
		
		// Whatever
		tags.add("nothing");
		tags.add("thing");
		tags.add("something");
		
		return getFeedItemsWithMediaTags(feed, tags, "audio", true, 20);
	}
	
	public Feed getFeedItemsWithMediaTags(Feed feed, ArrayList<String> tags, String mediaMimeType, boolean randomize, int limit) {
		Feed returnFeed = new Feed();
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			returnFeed.addItems(databaseAdapter.getFeedItemsWithMediaTags(feed, tags, mediaMimeType, randomize, limit));
		}
		return returnFeed;
	}	
	
	private void initializeDatabase()
	{
		if (LOGGING)
			Log.v(LOGTAG,"initializeDatabase()");

		if (RESET_DATABASE) {
			applicationContext.deleteDatabase(DatabaseHelper.DATABASE_NAME);
		}

		databaseAdapter = new DatabaseAdapter(cacheWord, applicationContext);

		if (databaseAdapter.getAllFeeds().size() == 0) {
			
			// How come I can't put an array of objects in the XML?
			// You can, sort of: http://stackoverflow.com/questions/4326037/android-resource-array-of-arrays
			String[] builtInFeedNames = applicationContext.getResources().getStringArray(R.array.built_in_feed_names);
			String[] builtInFeedUrls = applicationContext.getResources().getStringArray(R.array.built_in_feed_urls);
			
			
			if (builtInFeedNames.length == builtInFeedUrls.length) {
				for (int i = 0; i < builtInFeedNames.length; i++) {
					Feed newFeed = new Feed(builtInFeedNames[i], builtInFeedUrls[i]);
					newFeed.setSubscribed(true);
					databaseAdapter.addOrUpdateFeed(newFeed);
				}
			}
			
			loadOPMLFile();
		} else {
			if (LOGGING)
				Log.v(LOGTAG,"Database not empty, not inserting default feeds");
		}

		int talkItemId = applicationContext.getResources().getInteger(R.integer.talk_item_remote_id);
		if (talkItemId != -1) {
			// Create talk item
			talkItem = new Item();
			talkItem.setFavorite(true); // So it doesn't delete
			talkItem.setGuid(applicationContext.getResources().getString(R.string.talk_item_feed_url));
			talkItem.setTitle("Example Favorite");
			talkItem.setFeedId(-1);
			talkItem.setDescription("This is an example favorite.  Anything you mark as a favorite will show up in this section and won't be automatically deleted");
			talkItem.dbsetRemotePostId(talkItemId);
			talkItem.setCommentsUrl(applicationContext.getResources().getString(R.string.talk_item_feed_url));
			this.databaseAdapter.addOrUpdateItem(talkItem, itemLimit);
			if (LOGGING)
				Log.v(LOGTAG, "talkItem has database ID " + talkItem.getDatabaseId());
		}
		if (LOGGING)
			Log.v(LOGTAG,"databaseAdapter initialized");
	}

	private boolean testExternalStorage(java.io.File dirToTest) {
		if (LOGGING) 
			Log.v(LOGTAG, "testExternalStorage: " + dirToTest);
		if (dirToTest.exists() && dirToTest.isDirectory()) {
			try {
				java.io.File.createTempFile("test", null, dirToTest);
				
				if (LOGGING) 
					Log.v(LOGTAG, "testExternalStorage: " + dirToTest + " is good");

				return true;
			} catch (IOException ioe) {
				
				if (LOGGING) 
					Log.v(LOGTAG, "testExternalStorage: " + dirToTest + " is NOT good");
				
				return false;
			}
		}
		
		if (LOGGING) 
			Log.v(LOGTAG, "testExternalStorage: " + dirToTest + " is NOT good");
		
		return false;
	}
		
	@SuppressLint("NewApi")
	private java.io.File getNonVirtualFileSystemDir()
	{
		java.io.File filesDir = null;

		boolean done = false;
	    
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			if (LOGGING) {
				Log.v(LOGTAG, "Running on KITKAT or greater");
			}
			java.io.File[] possibleLocations = applicationContext.getExternalFilesDirs(null);
			if (LOGGING) {

				Log.v(LOGTAG, "Got " + possibleLocations.length + " locations");
			}
			long largestSize = 0;
			for (int l = 0; l < possibleLocations.length; l++) {
				if (possibleLocations[l] != null && possibleLocations[l].getAbsolutePath() != null) {	
					long curSize = new StatFs(possibleLocations[l].getAbsolutePath()).getTotalBytes();
					if (LOGGING) {
						Log.v(LOGTAG, "Checking " + possibleLocations[l].getAbsolutePath() + " size: " + curSize);
					}
					if (curSize > largestSize) {
						largestSize = curSize;
						filesDir = possibleLocations[l];
						done = true;
						
						if (LOGGING) {
							Log.v(LOGTAG, "using it");
						}
					}
				}
			}
		} else {
			if (LOGGING) {
				Log.v(LOGTAG, "Below kitkat, checking other SDCard Locations");
			}
			
			for (int p = 0; p < EXTERNAL_STORAGE_POSSIBLE_LOCATIONS.length; p++) {
				if (testExternalStorage(new java.io.File(EXTERNAL_STORAGE_POSSIBLE_LOCATIONS[p]))) {
					filesDir = new java.io.File(EXTERNAL_STORAGE_POSSIBLE_LOCATIONS[p] + "/" + FILES_DIR_NAME);
					if (!filesDir.exists())
					{
						filesDir.mkdirs();
					}
					done = true;
					break;
				}					
			}	    	
	    }
	    

		
		if (!done) {
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && (filesDir = applicationContext.getExternalFilesDir(null)) != null)
			{
				if (LOGGING)
					Log.v(LOGTAG,"sdcard mounted");

				if (!filesDir.exists())
				{
					if (LOGGING) 
						Log.v(LOGTAG, "filesDir doesn't exist, making it");
					
					filesDir.mkdirs();
					
					if (LOGGING && !filesDir.exists())
						Log.v(LOGTAG, "still doesn't exist, error!");		
					
					testExternalStorage(filesDir);
				}
				else 
				{
					if (LOGGING) 
						Log.v(LOGTAG, "filesDir exists");

				}
				
				if (LOGGING) 
					Log.v(LOGTAG,"filesDir:" + filesDir.getAbsolutePath());

			}
			else
			{
				if (LOGGING) 
					Log.v(LOGTAG,"on internal storage");
				
				filesDir = applicationContext.getDir(FILES_DIR_NAME, Context.MODE_PRIVATE);
			}
		}
	
		return filesDir;
	}
	
	
	private java.io.File getNonVirtualFileSystemInternalDir()
	{
		java.io.File filesDir;

		// Slightly more secure?
		filesDir = applicationContext.getDir(FILES_DIR_NAME, Context.MODE_PRIVATE);
		
		return filesDir;
	}
	
	private void initializeFileSystemCache()
	{
		if (LOGGING)
			Log.v(LOGTAG,"initializeFileSystemCache");

		java.io.File filesDir = getNonVirtualFileSystemDir();

		ioCipherFilePath = filesDir.getAbsolutePath() + "/" + IOCIPHER_FILE_NAME;
		
		if (LOGGING)
			Log.v(LOGTAG, "Creating ioCipher at: " + ioCipherFilePath);
		
		IOCipherMountHelper ioHelper = new IOCipherMountHelper(cacheWord);
		try {
			if (vfs == null) {
				vfs = ioHelper.mount(ioCipherFilePath);
			}
		} catch ( IOException e ) {
			if (LOGGING) {
				Log.e(LOGTAG,"IOCipher open failure");
				e.printStackTrace();
			}
			
			java.io.File existingVFS = new java.io.File(ioCipherFilePath);
			
			if (existingVFS.exists()) {
				existingVFS.delete();

				if (LOGGING) {
					Log.v(LOGTAG,"Deleted existing VFS " + ioCipherFilePath);
				}
			}
		
			try {
				ioHelper = new IOCipherMountHelper(cacheWord);
				vfs = ioHelper.mount(ioCipherFilePath);
			} catch (IOException e1) {
				if (LOGGING) {
					Log.e(LOGTAG, "Still didn't work, IOCipher open failure, giving up");
					//e1.printStackTrace();
				}
			}			
		}

		// Test it
		/*
		File testFile = new File(getFileSystemDir(),"test.txt");
		try {
	        BufferedWriter out = new BufferedWriter(new FileWriter(testFile));
	        out.write("test");
	        out.close();
		} catch (IOException e) {
			Log.e(LOGTAG,"FAILED TEST");			
		}
		*/
		if (LOGGING)
			Log.v(LOGTAG,"***Filesystem Initialized***");
	}

	private void deleteFileSystem()
	{
		if (vfs != null && vfs.isMounted()) {
			try {
				vfs.unmount();
			} catch (IllegalStateException ise) {
				if (LOGGING) ise.printStackTrace();
				ise.printStackTrace();
			}
			vfs.deleteContainer();
			vfs = null;
		}

		// Delete all possible locations
		
		// This will use the removeable external if it is there
		// otherwise it will return the external sd card
		// otherwise it will do internal
		java.io.File possibleDir = getNonVirtualFileSystemDir();
		if (possibleDir.exists()) {
			java.io.File[] possibleDirFiles = possibleDir.listFiles();
			for (int i = 0; i < possibleDirFiles.length; i++)
			{
				possibleDirFiles[i].delete();
			}
			possibleDir.delete();
		}
		
		// This is a backup, just in case they have a removable sd card inserted but also have
		// files on normal storage
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
		{
			// getExternalFilesDir() These persist
			java.io.File externalFilesDir = applicationContext.getExternalFilesDir(null);
			if (externalFilesDir != null && externalFilesDir.exists())
			{
				java.io.File[] externalFiles = externalFilesDir.listFiles();
				for (int i = 0; i < externalFiles.length; i++)
				{
					externalFiles[i].delete();
				}
				externalFilesDir.delete();
			}
		}

		// Final backup, remove from internal storage
		java.io.File internalDir = applicationContext.getDir(FILES_DIR_NAME, Context.MODE_PRIVATE);
		java.io.File[] internalFiles = internalDir.listFiles();
		for (int i = 0; i < internalFiles.length; i++)
		{
			internalFiles[i].delete();
		}
		internalDir.delete();
	}

	public File getFileSystemDir()
	{
		// returns the root of the VFS
		return new File("/");
	}
	
	/*
	 * Checks to see if a feed should be refreshed from the network or not based
	 * upon the the last sync date/time
	 */
	public boolean shouldRefresh(Feed feed)
	{
		long refreshDate = new Date().getTime() - feedRefreshAge;

		if (LOGGING)
			Log.v(LOGTAG, "Feed Databae Id " + feed.getDatabaseId());
		feed = databaseAdapter.fillFeedObject(feed);

		if (feed.getNetworkPullDate() != null)
		{
			if (LOGGING)
				Log.v(LOGTAG, "Feed pull date: " + feed.getNetworkPullDate().getTime());
		}
		else
		{
			if (LOGGING)
				Log.v(LOGTAG, "Feed pull date: NULL");
		}
		if (LOGGING)
			Log.v(LOGTAG, "Feed refresh date: " + refreshDate);

		if (feed.getNetworkPullDate() == null || feed.getNetworkPullDate().getTime() < refreshDate)
		{
			if (LOGGING)
				Log.v(LOGTAG, "Should refresh feed");
			return true;
		}
		else
		{
			if (LOGGING)
				Log.v(LOGTAG, "Get feeed from database");
			return false;
		}
	}

	public String getFeedTitle(long feedId) {
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.getFeedTitle(feedId);
		}
		return "";
	}
	
	/*
	 * Returns feed/list of favorite items for a specific feed
	 */
	public Feed getFeedFavorites(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			Feed favorites = databaseAdapter.getFavoriteFeedItems(feed);
			return favorites;
		}
		else
		{
			return new Feed();
		}
	}

	public Feed getAllShared() 
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.getAllSharedItems();
		}
		else
		{
			return new Feed();
		}
	}

	/**
	 * Get number of received items.
	 *
	 * @return Number of items received.
	 */
	public int getAllSharedCount()
	{
		return getAllShared().getItemCount();
	}
	
	/*
	 * Returns ArrayList of Feeds containing only the favorites
	 */
	public Feed getAllFavorites()
	{		
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.getAllFavoriteItems(); 
		}
		else
		{
			return new Feed();
		}
	}

	/**
	 * Get number of favorite items.
	 *
	 * @return Number of items marked as favorite.
	 */
	public int getAllFavoritesCount()
	{
		return getAllFavorites().getItemCount();
	}

	public void markItemAsFavorite(Item item, boolean favorite)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			// Pass in the item that will be marked as a favorite
			// Take a boolean so we can "unmark" a favorite as well.
			item.setFavorite(favorite);
			setItemData(item);
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready: markItemAsFavorite");
		}
	}

	public void addToItemViewCount(Item item) {
		
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			// Pass in the item that will be marked as a favorite
			// Take a boolean so we can "unmark" a favorite as well.
			item.incrementViewCount();
			setItemData(item);
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready: addToItemViewCount");
		}		
	}
	
	public void setMediaContentDownloaded(MediaContent mc) {
		mc.setDownloaded(true);
		if (databaseAdapter != null && databaseAdapter.databaseReady()) {
			databaseAdapter.addOrUpdateItemMedia(mc);
		} else {
			if (LOGGING)
				Log.v(LOGTAG, "Can't update database, not ready");
		}
	}
	
	public void unsetMediaContentDownloaded(MediaContent mc) {
		mc.setDownloaded(false);
		if (databaseAdapter != null && databaseAdapter.databaseReady()) {
			databaseAdapter.addOrUpdateItemMedia(mc);
		} else {
			if (LOGGING)	
				Log.v(LOGTAG, "Can't update database, not ready");
		}
	}
	
	public long setItemData(Item item)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.addOrUpdateItem(item, itemLimit);
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready: setItemData");
		}
		return -1;
	}

	/*
	 * Updates the feed data matching the feed object in the database. This
	 * includes any items that are referenced in the feed object.
	 */
	public void setFeedAndItemData(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			databaseAdapter.addOrUpdateFeedAndItems(feed, itemLimit);
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready: setFeedAndItemData");
		}
	}

	public void backgroundDownloadFeedItemMedia(Feed feed)
	{
		int count = 0;
		feed = getFeed(feed);
		for (Item item : feed.getItems())
		{
			if (count >= MEDIA_ITEM_DOWNLOAD_LIMIT_PER_FEED_PER_SESSION) {
				
				if (LOGGING)
					Log.v(LOGTAG, "!!! " + count + " above limit of " + MEDIA_ITEM_DOWNLOAD_LIMIT_PER_FEED_PER_SESSION);
				
				break;
			}
							
				if (LOGGING)
					Log.v(LOGTAG, "Adding " + count + " media item to background feed download");
				
				backgroundDownloadItemMedia(item);
				count++;
		}
	}
	
	
	public void backgroundDownloadItemMedia(Item item)
	{
		if (settings.syncMode() != Settings.SyncMode.BitWise) {
			for (MediaContent contentItem : item.getMediaContent()) {
				syncService.addMediaContentSyncTask(contentItem, false, null);
			}
		}
	}

	/*
	 * Adds a new feed to the database, this is used when the user manually
	 * subscribes to a feed
	 */
	public void addFeedByURL(String url, FeedFetcher.FeedFetchedCallback callback)
	{
		//checkForRSSFeed(url);
		
		
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			
			Feed newFeed = new Feed("", url);
			databaseAdapter.addOrUpdateFeed(newFeed);
			if (callback != null)
			{
				foregroundRequestFeedNetwork(newFeed,callback);
			}
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready: addFeedByURL");
		}
	}

	public void subscribeFeed(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			feed.setSubscribed(true);
			databaseAdapter.addOrUpdateFeed(feed);
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready: subscribeFeed");
		}
	}

	// Remove this feed from the ones we are listening to. Do we need an
	// "addFeed(Feed...)" as well
	// or do we use the URL-form that's already there, i.e. addFeed(String url)?
	public void unsubscribeFeed(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			feed.setSubscribed(false);
			databaseAdapter.addOrUpdateFeed(feed);
			// databaseAdapter.deleteFeed(feed.getDatabaseId());
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready: unsubscribeFeed");
		}
	}

	public void removeFeed(Feed feed) {
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			feed.setSubscribed(false);
			databaseAdapter.addOrUpdateFeed(feed);
			databaseAdapter.deleteFeed(feed.getDatabaseId());
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready: removeFeed");
		}
	}

	public String getDebugLog()
	{
		StringBuffer debugLog = new StringBuffer();

		java.util.Date date= new java.util.Date();
		debugLog.append("Timestamp: " + date.getTime() + "\n");

		debugLog.append("OS Version: " + System.getProperty("os.version") + "(" + android.os.Build.VERSION.INCREMENTAL + ")\n");
		debugLog.append("OS API Level: " + android.os.Build.VERSION.SDK_INT + "\n");
		debugLog.append("Device: " + android.os.Build.DEVICE + "\n");
		debugLog.append("Model (and Product): " + android.os.Build.MODEL + " ("+ android.os.Build.PRODUCT + ")\n");
		
		debugLog.append("File Path: " + ioCipherFilePath + "\n");
		
		debugLog.append("Online: ");
		int isOnline = isOnline();
		if (isOnline == ONLINE) {
			debugLog.append("Online\n");
		} else if (isOnline == NOT_ONLINE_NO_PROXY) {
			debugLog.append("Not Online, No Proxy\n");
		} else if (isOnline == NOT_ONLINE_NO_WIFI) {
			debugLog.append("Not Online, No Wifi\n");
		} else if (isOnline == NOT_ONLINE_NO_WIFI_OR_NETWORK) {
			debugLog.append("Not Online, No Wifi or Netowrk\n");
		}
		
		debugLog.append("Feed Info\n");
		ArrayList<Feed> subscribedFeeds = getSubscribedFeedsList();
		for (Feed feed : subscribedFeeds) {
			debugLog.append(feed.getDatabaseId() + ", " 
					+ databaseAdapter.getFeedItems(feed.getDatabaseId(), -1).size() + ", " +  "\n");
		}

		// TODO - append sync status

		debugLog.append("\n");
		
		debugLog.append("Key:\n"
				+ "STATUS_NOT_SYNCED = 0\n"
				+ "STATUS_LAST_SYNC_GOOD = 1\n"
				+ "STATUS_LAST_SYNC_FAILED_404 = 2\n"
				+ "STATUS_LAST_SYNC_FAILED_UNKNOWN = 3\n"
				+ "STATUS_LAST_SYNC_FAILED_BAD_URL = 4\n"
				+ "STATUS_SYNC_IN_PROGRESS = 5\n"
				+ "STATUS_LAST_SYNC_PARSE_ERROR = 6\n");
		
		
		return debugLog.toString();
	}
	
	public Intent getDebugIntent()
	{		
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Debug Log");
		sendIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"debug@guardianproject.info"});
		sendIntent.putExtra(Intent.EXTRA_TEXT, getDebugLog());
		sendIntent.setType("text/plain");

		return sendIntent;
	}	
	
	// Stub for Intent.. We don't start an activity here since we are doing a
	// custom chooser in FragmentActivityWithMenu. We could though use a generic
	// chooser
	// Mikael, what do you think?
	// Since the share list is kind of a UI component I guess we shouldn't use a
	// generic chooser.
	// We could perhaps introduce a
	// "doShare(Intent shareInten, ResolveInfo chosenWayToShare)"?
	public Intent getShareIntent(Item item)
	{
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, item.getTitle());
		sendIntent.putExtra(Intent.EXTRA_TEXT, item.getTitle() + "\n" + item.getLink() + "\n" + item.getCleanMainContent());

		sendIntent.putExtra(SocialReader.SHARE_ITEM_ID, item.getDatabaseId());
		sendIntent.setType("text/plain");
		sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		// applicationContext.startActivity(Intent.createChooser(sendIntent,"Share via: "));

		return sendIntent;
	}
	
	public Intent getSecureShareIntent(Item item, boolean onlyPrototype) {
			java.io.File sharingFile = new java.io.File("/test");
			if (!onlyPrototype)
			sharingFile = packageItemNonVFS(item.getDatabaseId());
		
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		//sendIntent.setDataAndType(Uri.parse(SecureShareContentProvider.CONTENT_URI + "item/" + item.getDatabaseId()),CONTENT_SHARING_MIME_TYPE);
		sendIntent.setDataAndType(Uri.fromFile(sharingFile), CONTENT_SHARING_MIME_TYPE);
		sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		//Log.v(LOGTAG,"Secure Share Intent: " + sendIntent.getDataString());
		
		return sendIntent;
	}

	// Stub for Intent
	public Intent getShareIntent(Feed feed)
	{
	    if (databaseAdapter == null || !databaseAdapter.databaseReady())
	    {
	    	if (LOGGING)
	    		Log.e(LOGTAG,"Database not ready: getShareIntent");
	    	return new Intent();
	    }

		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);

		if (feed != null)
		{
			sendIntent.putExtra(Intent.EXTRA_TEXT, feed.getTitle() + "\n" + feed.getLink() + "\n" + feed.getFeedURL() + "\n" + feed.getDescription());
		}
		else
		{
			ArrayList<Feed> subscribed = getSubscribedFeedsList();
			StringBuilder builder = new StringBuilder();

			for (Feed subscribedFeed : subscribed)
			{
				if (builder.length() > 0)
					builder.append("\n\n");
				builder.append(subscribedFeed.getTitle() + "\n" + subscribedFeed.getLink() + "\n" + subscribedFeed.getFeedURL() + "\n" + subscribedFeed.getDescription());
			}
			sendIntent.putExtra(Intent.EXTRA_TEXT, builder.toString());
		}
		sendIntent.setType("text/plain");
		sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return sendIntent;
	}

	public void doWipe(int wipeMethod)
	{
		if (LOGGING)
			Log.v(LOGTAG, "doing doWipe()");

		if (wipeMethod == DATA_WIPE)
		{
			dataWipe();
		}
		else if (wipeMethod == FULL_APP_WIPE)
		{
			dataWipe();
			deleteApp();
		}
		else
		{
			if (LOGGING)
				Log.v(LOGTAG, "This shouldn't happen");
		}

		//applicationContext.finish();
	}

	public void lockApp()
	{
		if (LOGGING)
			Log.v(LOGTAG, "Locking app");
		cacheWord.lock();
	}
	
	private void deleteApp()
	{
		Uri packageURI = Uri.parse("package:" + applicationContext.getPackageName());
		Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
		uninstallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		applicationContext.startActivity(uninstallIntent);
	}

	private void dataWipe()
	{
		if (LOGGING)
			Log.v(LOGTAG, "dataWipe");
		//http://code.google.com/p/android/issues/detail?id=13727
		
		if (syncService != null)
		{
			//syncService.stopSelf();
			applicationContext.stopService(new Intent(applicationContext, SyncService.class));
			initialized = false;
		}

		if (databaseAdapter != null && databaseAdapter.databaseReady()) {
			if (LOGGING)
				Log.v(LOGTAG, "databaseAdapter.deleteAll(), databaseAdapter.close() databaseAdapter = null;");

			databaseAdapter.deleteAll();
			databaseAdapter.close();
			databaseAdapter = null;
		}

		/* Trying this
		if (vfs != null && vfs.isMounted()) {
			if (LOGGING)
				Log.v(LOGTAG, "vfs.unmount(); vfs = null;");



			try {
				vfs.unmount();
			} catch (IllegalStateException ise) {
				if (LOGGING) ise.printStackTrace();
				ise.printStackTrace();
			}
			//vfs.deleteContainer();
			vfs = null;
		}
		*/
		
		if (LOGGING)
			Log.v(LOGTAG, "applicationContext.deleteDatabase(DatabaseHelper.DATABASE_NAME);");

		applicationContext.deleteDatabase(DatabaseHelper.DATABASE_NAME);

		if (LOGGING)
			Log.v(LOGTAG, "NOT deleteFileSystem()");
		deleteFileSystem();
		
		// Reset Prefs to initial state
		if (LOGGING)
			Log.v(LOGTAG, "settings.resetSettings();");
		settings.resetSettings();
		
		// Change Password
		/*
		String defaultPassword = "password";
		char[] p = defaultPassword.toCharArray();
		try {
			cacheWord.setPassphrase(p);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}
		*/

		if (LOGGING)
			Log.v(LOGTAG, "cacheWord.lock(); cacheWord.deinitialize();");
		
		cacheWord.lock();
		cacheWord.deinitialize();
		
		
	}

	/*
	public void loadDisplayImageMediaContent(MediaContent mc, ImageView imageView)
	{
		loadDisplayImageMediaContent(mc, imageView, false);
	}

	// This should really be a GUI widget but it is here for now
	// Load the media for a specific item.
	public void loadDisplayImageMediaContent(MediaContent mc, ImageView imageView, boolean forceBitwiseDownload)
	{
		final ImageView finalImageView = imageView;

		SyncTaskMediaFetcherCallback mdc = new SyncTaskMediaFetcherCallback()
		{
			@Override
			public void mediaDownloaded(File mediaFile)
			{
					//Log.v(LOGTAG, "mediaDownloaded: " + mediaFile.getAbsolutePath());
				try {

					BufferedInputStream bis = new BufferedInputStream(new FileInputStream(mediaFile));

					BitmapFactory.Options bmpFactoryOptions = new BitmapFactory.Options();
					// Should take into account view size?
					if (finalImageView.getWidth() > 0 && finalImageView.getHeight() > 0)
					{
						//Log.v(LOGTAG, "ImageView dimensions " + finalImageView.getWidth() + " " + finalImageView.getHeight());

						bmpFactoryOptions.inJustDecodeBounds = true;

						//BitmapFactory.decodeFile(mediaFile.getAbsolutePath(), bmpFactoryOptions);
						BitmapFactory.decodeStream(bis, null, bmpFactoryOptions);
						bis.close();

						int heightRatio = (int) Math.ceil(bmpFactoryOptions.outHeight / (float) finalImageView.getHeight());
						int widthRatio = (int) Math.ceil(bmpFactoryOptions.outWidth / (float) finalImageView.getWidth());

						if (heightRatio > 1 && widthRatio > 1)
						{
							if (heightRatio > widthRatio)
							{
								bmpFactoryOptions.inSampleSize = heightRatio;
							}
							else
							{
								bmpFactoryOptions.inSampleSize = widthRatio;
							}
						}

						// Decode it for real
						bmpFactoryOptions.inJustDecodeBounds = false;
					}
					else
					{
						//Log.v(LOGTAG, "ImageView dimensions aren't set");
						bmpFactoryOptions.inSampleSize = 2;
					}

					//Bitmap bmp = BitmapFactory.decodeFile(mediaFile.getAbsolutePath(), bmpFactoryOptions);
					BufferedInputStream bis2 = new BufferedInputStream(new FileInputStream(mediaFile));
					Bitmap bmp = BitmapFactory.decodeStream(bis2, null, bmpFactoryOptions);
					bis2.close();

					finalImageView.setImageBitmap(bmp);
					finalImageView.invalidate();
					//Log.v(LOGTAG, "Should have set bitmap");

				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		};
		loadImageMediaContent(mc, mdc, forceBitwiseDownload);
	}
	*/

	public boolean isMediaContentLoaded(MediaContent mc)
	{
		return loadMediaContent(mc, null, false, false);
	}
	
	public boolean loadMediaContent(MediaContent mc, SyncTaskMediaFetcher.SyncTaskMediaFetcherCallback mdc) {
		return loadMediaContent(mc, mdc, false);
	}

	public boolean loadMediaContent(MediaContent mc, SyncTaskMediaFetcher.SyncTaskMediaFetcherCallback mdc, boolean forceBitwiseDownload)
	{
		return loadMediaContent(mc, mdc, true, forceBitwiseDownload);
	}
	
	public boolean loadMediaContent(MediaContent mc, final SyncTaskMediaFetcher.SyncTaskMediaFetcherCallback mdc, boolean download, boolean forceBitwiseDownload) {
		switch (mc.getMediaContentType()) {
			case IMAGE:
				forceBitwiseDownload = forceBitwiseDownload || (settings.syncMode() != Settings.SyncMode.BitWise);
				// Allow to fall through
			case EPUB:
			case VIDEO:
			case AUDIO:
				File possibleFile = new File(getFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mc.getDatabaseId());
				if (possibleFile.exists()) {
					if (LOGGING)
						Log.v(LOGTAG, "Already downloaded: " + possibleFile.getAbsolutePath());
					if (mdc != null)
						mdc.mediaDownloaded(mc, possibleFile);
					return true;
				} else if (download && forceBitwiseDownload && isOnline() == ONLINE) {
					if (LOGGING)
						Log.v(LOGTAG, "File doesn't exist, downloading");
					syncService.addMediaContentSyncTask(mc, true, mdc);
					return true;
				}
				break;
		}
		if (LOGGING)
			Log.v(LOGTAG, "Not a media type we support: " + mc.getType());
		return false;
	}

	public void deleteMediaContentFileNow(final long mediaContentDatabaseId) {
		final File possibleFile = new File(getFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mediaContentDatabaseId);
		if (possibleFile.exists())
		{
			boolean ignored = possibleFile.delete();
		}
	}

	public void deleteMediaContentFile(final int mediaContentDatabaseId) {
		final File possibleFile = new File(getFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mediaContentDatabaseId);
		if (possibleFile.exists())
		{
			new Thread() {
				public void run() {
					if (possibleFile.delete()) {

						if (LOGGING)
							Log.v(LOGTAG, "Deleted: " + possibleFile.getAbsolutePath());

						// Update the database
						MediaContent mc = databaseAdapter.getMediaContentById(mediaContentDatabaseId);
						unsetMediaContentDownloaded(mc);

					} else {
						if (LOGGING) 
							Log.v(LOGTAG, "NOT DELETED " + possibleFile.getAbsolutePath());
					}
				}				
			}.start();
		}		
	}

	/*
	public long sizeOfMediaContent() {
		long totalSize = 0;
		String[] possibleMediaFiles = getFileSystemDir().list();
		for (int i = 0; i < possibleMediaFiles.length; i++) {
			if (possibleMediaFiles[i].contains(MEDIA_CONTENT_FILE_PREFIX)) {
				totalSize += new File(possibleMediaFiles[i]).length();
			}
		}
		return totalSize;
	}
	
	public void checkMediaContent() {
		while (sizeOfMediaContent() > mediaCacheSize * 1024 * 1024) {
			
		}
	}
	*/
	
	public File vfsTempItemBundle() {
		File tempContentFile = new File(getVFSSharingDir(), CONTENT_BUNDLE_FILE_PREFIX + System.currentTimeMillis() + Item.DEFAULT_DATABASE_ID + "." + SocialReader.CONTENT_SHARING_EXTENSION);
		return tempContentFile;
	}
	
	public java.io.File nonVfsTempItemBundle() {
		return new java.io.File(getNonVFSSharingDir(), CONTENT_BUNDLE_FILE_PREFIX + Item.DEFAULT_DATABASE_ID + "." + SocialReader.CONTENT_SHARING_EXTENSION);
	}
	
	private java.io.File getNonVFSSharingDir() {
		//java.io.File sharingDir = new java.io.File(getNonVirtualFileSystemInternalDir(), NON_VFS_SHARE_DIRECTORY);
		java.io.File sharingDir = new java.io.File(getNonVirtualFileSystemDir(), NON_VFS_SHARE_DIRECTORY);

		sharingDir.mkdirs();
		return sharingDir;
	}
	
	private File getVFSSharingDir() {
		File sharingDir = new File(getFileSystemDir(), VFS_SHARE_DIRECTORY);
		sharingDir.mkdirs();
		return sharingDir;
	}
	
	public java.io.File packageItemNonVFS(long itemId) {
		
		java.io.File possibleFile = new java.io.File(getNonVFSSharingDir(), CONTENT_BUNDLE_FILE_PREFIX + itemId + "." + SocialReader.CONTENT_SHARING_EXTENSION);
		
		if (LOGGING)
			Log.v(LOGTAG,"Going to package as: " + possibleFile.toString());
		
		if (possibleFile.exists())
		{
			if (LOGGING)
				Log.v(LOGTAG, "item already packaged " + possibleFile.getAbsolutePath());
		}
		else
		{
			if (LOGGING)
				Log.v(LOGTAG, "item not already packaged, going to do so now " + possibleFile.getAbsolutePath());
			
			try {
				
				if (databaseAdapter != null && databaseAdapter.databaseReady())
				{
					byte[] buf = new byte[1024]; 
			        int len; 
					
					Item itemToShare = databaseAdapter.getItemById(itemId);
					
					if (LOGGING)
						Log.v(LOGTAG,"Going to package " + itemToShare.toString());
					
					ZipOutputStream zipOutputStream = new ZipOutputStream(new java.io.FileOutputStream(possibleFile)); 
			        
					// Package content
					File tempItemContentFile = new File(this.getFileSystemDir(), SocialReader.TEMP_ITEM_CONTENT_FILE_NAME);
			        ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tempItemContentFile)));
			        output.writeObject(itemToShare);
			        output.flush();
			        output.close();
			        
			        zipOutputStream.putNextEntry(new ZipEntry(tempItemContentFile.getName()));
			        FileInputStream in = new FileInputStream(tempItemContentFile);
			        
			        while ((len = in.read(buf)) > 0) { 
			        	zipOutputStream.write(buf, 0, len); 
			        } 
			        zipOutputStream.closeEntry(); 
			        in.close(); 
			        // Finished content

			        // Now do media
					ArrayList<MediaContent> mc = itemToShare.getMediaContent();
					for (MediaContent mediaContent : mc) {
						File mediaFile = new File(getFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
						if (LOGGING)
							Log.v(LOGTAG,"Checking for " + mediaFile.getAbsolutePath());
						
						if (mediaFile.exists())
						{
							if (LOGGING)
								Log.v(LOGTAG, "Media exists, adding it: " + mediaFile.getAbsolutePath());
							zipOutputStream.putNextEntry(new ZipEntry(mediaFile.getName()));
							FileInputStream mIn = new FileInputStream(mediaFile);
					        while ((len = mIn.read(buf)) > 0) { 
					        	zipOutputStream.write(buf, 0, len); 
					        } 
					        zipOutputStream.closeEntry(); 
					        mIn.close(); 
						} else {
							if (LOGGING)
								Log.v(LOGTAG, "Media doesn't exist, not adding it");
						}
					}
					
					zipOutputStream.close();
				}
				else
				{
					if (LOGGING)
						Log.e(LOGTAG,"Database not ready: packageItem");
				}
			} catch (FileNotFoundException fnfe) {
				if (LOGGING)
					Log.e(LOGTAG,"Can't write package file, not found");
			} catch (IOException e) {
				if (LOGGING)
					e.printStackTrace();

			}

		}
		possibleFile.setReadable(true, false);
		return possibleFile;		
	}

	public Item getItemFromId(long itemId)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			Item item = databaseAdapter.getItemById(itemId);
			return item;
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG, "Database not ready: getItemFromId");
		}
		return null;
	}

	public File packageItem(long itemId)
	{
		// IOCipher File
		File possibleFile = new File(getVFSSharingDir(), CONTENT_BUNDLE_FILE_PREFIX + itemId + "." + SocialReader.CONTENT_SHARING_EXTENSION);
		if (LOGGING)
			Log.v(LOGTAG,"possibleFile: " + possibleFile.getAbsolutePath());
		
		if (possibleFile.exists())
		{
			if (LOGGING)
				Log.v(LOGTAG, "item already packaged " + possibleFile.getAbsolutePath());
		}
		else
		{
			Log.v(LOGTAG, "item not already packaged, going to do so now " + possibleFile.getAbsolutePath());
			
			try {

				if (databaseAdapter != null && databaseAdapter.databaseReady())
				{
					byte[] buf = new byte[1024]; 
			        int len; 
					
					Item itemToShare = databaseAdapter.getItemById(itemId);
					
					if (LOGGING)
						Log.v(LOGTAG,"Going to package " + itemToShare.toString());
					
					ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(possibleFile)); 
			        
					// Package content
					File tempItemContentFile = new File(this.getFileSystemDir(), SocialReader.TEMP_ITEM_CONTENT_FILE_NAME);

					OutputStream out = new FileOutputStream(tempItemContentFile);
					Writer w = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
					w.write(ItemToRSS.toRSS(itemToShare, socialReader.databaseAdapter.getFeedById(itemToShare.getFeedId())));
					w.close();

			        zipOutputStream.putNextEntry(new ZipEntry(tempItemContentFile.getName()));
			        FileInputStream in = new FileInputStream(tempItemContentFile);
			        
			        while ((len = in.read(buf)) > 0) { 
			        	zipOutputStream.write(buf, 0, len); 
			        } 
			        zipOutputStream.closeEntry(); 
			        in.close(); 
			        // Finished content

			        // Now do media
					ArrayList<MediaContent> mc = itemToShare.getMediaContent();
					for (MediaContent mediaContent : mc) {
						File mediaFile = new File(getFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
						if (LOGGING)
							Log.v(LOGTAG,"Checking for " + mediaFile.getAbsolutePath());
						if (mediaFile.exists())
						{
							if (LOGGING)
								Log.v(LOGTAG, "Media exists, adding it: " + mediaFile.getAbsolutePath());
							zipOutputStream.putNextEntry(new ZipEntry(mediaFile.getName()));
							FileInputStream mIn = new FileInputStream(mediaFile);
					        while ((len = mIn.read(buf)) > 0) { 
					        	zipOutputStream.write(buf, 0, len); 
					        } 
					        zipOutputStream.closeEntry(); 
					        mIn.close(); 
						} else {
							if (LOGGING)
								Log.v(LOGTAG, "Media doesn't exist, not adding it");
						}
					}
					
					zipOutputStream.close();
				}
				else
				{
					if (LOGGING)
						Log.e(LOGTAG,"Database not ready: packageItem");
				}
			} catch (FileNotFoundException fnfe) {
				fnfe.printStackTrace();
				if (LOGGING)
					Log.e(LOGTAG,"Can't write package file, not found");
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		return possibleFile;
	}

    @Override
    public void onCacheWordUninitialized() {
    	if (LOGGING)
    		Log.v(LOGTAG,"onCacheWordUninitialized");

    	uninitialize();
    }

    @Override
    public void onCacheWordLocked() {
    	if (LOGGING)
    		Log.v(LOGTAG, "onCacheWordLocked");

    	uninitialize();
    }

    @Override
    public void onCacheWordOpened() {
    	if (LOGGING)
    		Log.v(LOGTAG,"onCacheWordOpened");
    	
		socialReader.setCacheWordTimeout(settings.passphraseTimeout());

        initialize();
    }
    
	public void getStoreBitmapDimensions(MediaContent mediaContent)
	{
		try
		{
			File mediaFile = new File(getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());

			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(mediaFile));
			BitmapFactory.decodeStream(bis, null, o);
			bis.close();
			if (o.outWidth > 0 && o.outHeight > 0)
			{
				mediaContent.setWidth(o.outWidth);
				mediaContent.setHeight(o.outHeight);
				if (databaseAdapter != null && databaseAdapter.databaseReady())
					databaseAdapter.addOrUpdateItemMedia(mediaContent); // TODO refactoring remove this here!
			}
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public ArrayList<Comment> getItemComments(Item item) {
		ArrayList<Comment> itemComments = new ArrayList<Comment>();
		
		// Get from database;
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			itemComments = databaseAdapter.getItemComments(item);
			if (LOGGING)
				Log.v(LOGTAG,"Got item comments: " + item.getTitle() + " " + itemComments.size());
			return itemComments;
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG, "Database not ready: getItemComments");
		}
		return itemComments;		
	}
	
	public void networkCheckCommentFeeds(Feed _feed) {
		// Loop through items that we should check and save updates to database
		if (LOGGING)
			Log.v(LOGTAG,"networkCheckCommentFeeds");

		if (syncService != null) {
			if (LOGGING)
				Log.v(LOGTAG,"syncService != null");

			// Get from database;
			if (databaseAdapter != null && databaseAdapter.databaseReady())
			{
				Feed dbFeed = databaseAdapter.getFeedItems(_feed, DEFAULT_NUM_FEED_ITEMS);			
				for (Item item : dbFeed.getItems()) {
					syncService.addCommentsSyncTask(item, false, null);
				}
			}
			else
			{
				if (LOGGING)
					Log.e(LOGTAG, "Database not ready: networkCheckCommentFeeds");
			}

		} else {
			if (LOGGING)
				Log.v(LOGTAG,"syncService is null!");
		}
	}
	
	public void setItemComments(Item item, ArrayList<Comment> comments) {
		if (LOGGING)
			Log.v(LOGTAG, "setItemComments");
		
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			databaseAdapter.addOrUpdateItemComments(item, comments);
			
			if (LOGGING)
				Log.v(LOGTAG, "Database has: " + getItemComments(item).size() + " comments for this item");
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG, "Database not ready: setComment");
		}
	}
	
	public Item getTalkItem() {
		if (LOGGING) 
			Log.v(LOGTAG,"getTalkItem");
		
		if (LOGGING && talkItem == null) {
			Log.e(LOGTAG,"talkItem is NULL!!!!");
		}
		else if (LOGGING)
		{
			Log.v(LOGTAG,"talkItem has " + getItemComments(talkItem).size() + " comments");
		}
		
		return talkItem;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// If we enable a proxy, make sure to update status
		//
		if ((Settings.KEY_REQUIRE_PROXY.equals(key) || Settings.KEY_PROXY_TYPE.equals(key)) && settings.requireProxy()) {
			if (settings.proxyType() == ProxyType.Tor)
				checkTorStatus();
			else if (settings.proxyType() == ProxyType.Psiphon)
				checkPsiphonStatus();
		}
	}

	public void deleteItem(Item story)
	{
		if (LOGGING)
			Log.v(LOGTAG, "deleteItem");
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			databaseAdapter.deleteItem(story.getDatabaseId());
		}
		else
		{
			if (LOGGING)
				Log.e(LOGTAG,"Database not ready");
		}
	}

	public Uri addFileToSecureShare(File vfsFile, String type, String fileName) {
		try {
			if (!vfsFile.exists() || !Arrays.asList(SecureShareContentProvider.SUPPORTED_TYPES).contains(type)) {
				return null;
			}

			// Make sure share dir exists
			File shareDir = new File(getVFSSharingDir(), type);
			if (!shareDir.exists() && !shareDir.mkdir()) {
				return null;
			}
			File sharedFile = new File(shareDir, fileName);
			if (!sharedFile.exists() && !sharedFile.createNewFile()) {
				return null;
			}

			// Copy to export dir
			InputStream in = new FileInputStream(vfsFile);
			OutputStream out = new FileOutputStream(sharedFile);

			// Transfer bytes from in to out
			byte[] buf = new byte[8096];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();

			return Uri.parse(SecureShareContentProvider.CONTENT_URI + type + "/" + fileName);
		} catch (Exception ignored) {
			return null;
		}
	}

	/**
	 * Get last sync status for database object
	 * @param dbObject	May be an instance of Item, Feed ...
	 * @return the status of the object
	 */
	public SyncStatus syncStatus(Object dbObject) {
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.syncStatus(dbObject);
		}
		return SyncStatus.OK;
	}

	/**
	 * Set last sync status for a database object
	 * @param dbObject	May be an instance of Item, Feed ...
	 * @param error	The status to set
	 */
	public void setSyncStatus(Object dbObject, SyncStatus error) {
		if (LOGGING)
			Log.v(LOGTAG, "setSyncStatus for object " + dbObject + " : " + error.toString());
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			databaseAdapter.setSyncStatus(dbObject, error);
		}
	}
}
