package info.guardianproject.securereader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.tinymission.rss.Feed;
import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class SyncService {

	public static final String BROADCAST_SYNCSERVICE_FEED_DOWNLOADED = "syncservice_feed_downloaded";
	public static final String BROADCAST_SYNCSERVICE_FEED_ICON_DOWNLOADED = "syncservice_feed_icon_downloaded";
	public static final String BROADCAST_SYNCSERVICE_MEDIA_DOWNLOADED = "syncservice_media_downloaded";
	public static final String EXTRA_SYNCSERVICE_FEED = "syncservice_extras_feed";

	private static final int TASK_FEED_PRIORITY = 2;
	private static final int TASK_FEED_ICON_PRIORITY = 1;

	public static final String LOGTAG = "SyncService";
	public static final boolean LOGGING = true;

	private static SyncService instance;

	public static SyncService getInstance(Context context) {
		if (instance == null) {
			instance = new SyncService(context.getApplicationContext());
		}
		return instance;
	}

	private final Context context;
	private final Handler handler;
	private final BlockingQueue<Runnable> syncServiceExecutorQueue;
	private final SyncServiceExecutorService syncServiceExecutorService;

	private SyncService(Context context) {
		this.context = context;
		handler = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message inputMessage) {
			}
		};

		syncServiceExecutorQueue = new PriorityBlockingQueue<Runnable>(100) {
			@Override
			public Runnable take() throws InterruptedException {
				if (LOGGING)
					Log.d(LOGTAG, "TAKE: " + this.size());
				return super.take();
			}
		};
		final ThreadFactory threadFactory = new ThreadFactoryBuilder()
				.setNameFormat("SyncThread-%d")
				.setDaemon(true)
				.setPriority(Thread.NORM_PRIORITY)
				.build();
		syncServiceExecutorService = new SyncServiceExecutorService(4, 4,
				5000L, TimeUnit.MILLISECONDS, syncServiceExecutorQueue, threadFactory);
	}

	public void cancelAll() {
		Runnable[] syncList = new Runnable[syncServiceExecutorQueue.size()];
		syncServiceExecutorQueue.toArray(syncList);
		synchronized (syncServiceExecutorService) {
			for (Runnable syncListItem : syncList) {
				((PrioritizedListenableFutureTask)syncListItem).cancel(true);
			}
			syncServiceExecutorService.purge();
		}
	}

//	public class MediaContentSyncTask {
//		public MediaContent mediaContent;
//
//		MediaContentSyncTask(MediaContent _mediaContent) {
//			super();
//			mediaContent = _mediaContent;
//		}
//	}
//
//	public class ItemCommentsSyncTask {
//		public Item item;
//
//		ItemCommentsSyncTask(Item _item) {
//			super();
//			item = _item;
//		}
//	}
//
//
//
//
//
//    	private void startMediaDownloader() {
//			ssMediaDownloader = new SyncTaskMediaFetcher(SyncService.this,this);
//
//
//    		if (LOGGING)
//    			Log.v(LOGTAG,"Create and start ssMediaDownloader ");
//    		syncThread = new Thread(ssMediaDownloader);
//    		syncThread.start();
//    		updateStatus(SyncTask.STARTED);
//    		startTime = System.currentTimeMillis();
//    	}
//
//		private void stopMediaDownloader() {
//			if (ssMediaDownloader != null) {
//				ssMediaDownloader.stop();
//			}
//		}
//
//    	private void startFeedFetcher() {
//    		//SyncTaskFeedFetcher feedFetcher = new SyncTaskFeedFetcher(SyncService.this,feed);
//    		//feedFetcher.setFeedUpdatedCallback(callback);
//
//    		if (LOGGING)
//    			Log.v(LOGTAG,"Create SyncTaskFeedFetcher");
//			feedFetcher = new SyncTaskFeedFetcher(SyncService.this,this);
//
//    		if (LOGGING)
//    			Log.v(LOGTAG,"Create and start fetcherThread ");
//    		syncThread = new Thread(feedFetcher);
//    		syncThread.start();
//    		updateStatus(SyncTask.STARTED);
//    		startTime = System.currentTimeMillis();
//    	}
//
//		private void stopFeedFetcher() {
//			if (feedFetcher != null) {
//				feedFetcher.stop();
//			}
//		}
//
//    	private void startCommentsFeedFetcher() {
//    		if (LOGGING)
//    			Log.v(LOGTAG,"Create SyncServiceCommentsFeedFetcher");
//			commentsFeedFetcher = new SyncServiceCommentsFeedFetcher(SyncService.this,this);
//
//    		if (LOGGING)
//    			Log.v(LOGTAG,"Create and start fetcherThread ");
//    		syncThread = new Thread(commentsFeedFetcher);
//    		syncThread.start();
//    		updateStatus(SyncTask.STARTED);
//    		startTime = System.currentTimeMillis();
//    	}
//
//		private void stopCommentsFeedFetcher() {
//			if (commentsFeedFetcher != null) {
//				commentsFeedFetcher.stop();
//			}
//		}
//
//    	void taskComplete(int status) {
//    		if (status == FINISHED) {
//    			if (type == TYPE_FEED) {
//    				//((App) getApplicationContext()).socialReader.setFeedAndItemData(feed);
//    				//SocialReader.getInstance(getApplicationContext()).backgroundDownloadFeedItemMedia(feed);
//    			}
//    			if (callback != null && type == TYPE_FEED) {
//    				callback.feedFetched(feed);
//    			}
//        		updateStatus(status);
//    		}
//    	}
//    }

	private boolean overTime(SyncTask syncTask) {
		return syncTask.status == SyncTask.SyncTaskStatus.STARTED &&
				System.currentTimeMillis() - syncTask.startTime > SyncTask.MAXTIME;
	}

	private PrioritizedListenableFutureTask getFeedSyncTask(Feed feed) {
		synchronized (syncServiceExecutorService) {
			Runnable[] syncList = syncServiceExecutorQueue.toArray(new Runnable[0]);
			for (Runnable syncListItem : syncList) {
				if (syncListItem instanceof PrioritizedListenableFutureTask) {
					PrioritizedListenableFutureTask listenableFutureTask = (PrioritizedListenableFutureTask) syncListItem;
					if (listenableFutureTask.getTask() instanceof SyncTaskFeedFetcher) {
						SyncTaskFeedFetcher feedTask = (SyncTaskFeedFetcher) listenableFutureTask.getTask();
						if (feedTask.feed != null && feedTask.feed.getDatabaseId() == feed.getDatabaseId()) {
							if (overTime(feedTask)) {
								listenableFutureTask.cancel(true);
								if (LOGGING)
									Log.v(LOGTAG, "Feed was already in queue but over time");
							} else {
								if (LOGGING)
									Log.v(LOGTAG, "Feed already in queue, ignoring");
								return listenableFutureTask;
							}
						}
					}
				}
			}
		}
		return null;
	}

	public void addFeedIconSyncTask(Feed feed) {
		synchronized (syncServiceExecutorService) {
			if (LOGGING)
				Log.d(LOGTAG, "Adding icon " + feed.getFeedURL() + " to sync. Queue length " + syncServiceExecutorQueue.size());
			PrioritizedListenableFutureTask<SyncTaskFeedIconFetcher> feedIconFutureTask =
					(PrioritizedListenableFutureTask<SyncTaskFeedIconFetcher>) syncServiceExecutorService.submit(new SyncTaskFeedIconFetcher(context, TASK_FEED_ICON_PRIORITY, task.feed));
			feedIconFutureTask.addListener(new PrioritizedTaskListener<SyncTaskFeedIconFetcher>(feedIconFutureTask) {
				@Override
				protected void onSuccess(SyncTaskFeedIconFetcher task) {
					// Notify listeners
					Intent feedDownloadedIntent = new Intent(BROADCAST_SYNCSERVICE_FEED_ICON_DOWNLOADED);
					feedDownloadedIntent.putExtra(EXTRA_SYNCSERVICE_FEED, task.feed);
					LocalBroadcastManager.getInstance(context).sendBroadcast(feedDownloadedIntent);
				}
			}, MoreExecutors.directExecutor());
		}
	}

	public PrioritizedListenableFutureTask<SyncTaskFeedFetcher> addFeedSyncTask(Feed feed, final SyncTaskFeedFetcher.SyncServiceFeedFetchedCallback callback) {
		synchronized (syncServiceExecutorService) {
			PrioritizedListenableFutureTask<SyncTaskFeedFetcher> feedSyncFutureTask = (PrioritizedListenableFutureTask<SyncTaskFeedFetcher>) getFeedSyncTask(feed);
			if (feedSyncFutureTask != null) {
				return feedSyncFutureTask;
			}
			if (LOGGING)
				Log.d(LOGTAG, "Adding feed " + feed.getFeedURL() + " to sync. Queue length " + syncServiceExecutorQueue.size());

			final SyncTaskFeedFetcher feedSyncTask = new SyncTaskFeedFetcher(context, TASK_FEED_PRIORITY, feed);
			feedSyncFutureTask = (PrioritizedListenableFutureTask<SyncTaskFeedFetcher>) syncServiceExecutorService.submit(feedSyncTask);

			// Add a listener for the future
			PrioritizedTaskListener<SyncTaskFeedFetcher> listener = new PrioritizedTaskListener<SyncTaskFeedFetcher>(feedSyncFutureTask) {
				@Override
				protected void onSuccess(final SyncTaskFeedFetcher task) {
					// Tell out listeners we are done
					Intent feedDownloadedIntent = new Intent(BROADCAST_SYNCSERVICE_FEED_DOWNLOADED);
					feedDownloadedIntent.putExtra(EXTRA_SYNCSERVICE_FEED, task.feed);
					LocalBroadcastManager.getInstance(context).sendBroadcast(feedDownloadedIntent);

					addFeedIconSyncTask(task.feed);
					SocialReader.getInstance(context).backgroundDownloadFeedItemMedia(task.feed);

					// Call callback on main thread
					handler.post(new Runnable() {
						@Override
						public void run() {
							if (callback != null) {
								callback.feedFetched(task.feed);
							}
						}
					});
				}

				@Override
				protected void onFailure(final SyncTaskFeedFetcher task) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							if (callback != null) {
								callback.feedFetchError(task.feed);
							}
						}
					});
				}
			};
			feedSyncFutureTask.addListener(listener, MoreExecutors.directExecutor());
			return feedSyncFutureTask;
		}
	}

	public ListenableFuture addFeedsSyncTask(List<Feed> feeds, final SyncTaskFeedFetcher.SyncServiceFeedFetchedCallback callback) {
		if (feeds == null || feeds.size() == 0) {
			if (callback != null) {
				callback.feedFetched(new Feed());
			}
			return null;
		}

		List<PrioritizedListenableFutureTask<SyncTaskFeedFetcher>> futures = Lists.newArrayList();
		for (final Feed feed : feeds) {
			PrioritizedListenableFutureTask<SyncTaskFeedFetcher> future = addFeedSyncTask(feed, null);
			futures.add(future);
		}

		// Create a future to listen to when they ALL are done
		//
		final ListenableFuture<List<SyncTaskFeedFetcher>> resultsFuture = Futures.allAsList(futures);

		Futures.addCallback(resultsFuture, new FutureCallback<List<SyncTaskFeedFetcher>>() {
			@Override
			public void onSuccess(List<SyncTaskFeedFetcher> tasks) {
				if (callback != null) {
					final Feed composite = new Feed();
					for (SyncTask task : tasks) {
						SyncTaskFeedFetcher feedTask = (SyncTaskFeedFetcher) task;
						composite.addItems(feedTask.feed.getItems());
					}
					handler.post(new Runnable() {
						@Override
						public void run() {
							callback.feedFetched(composite);
						}
					});
				}
			}

			@Override
			public void onFailure(Throwable throwable) {
				if (LOGGING) {
					Log.d(LOGTAG, "addFeedsSyncTask batch failure!");
				}
				if (callback != null) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							callback.feedFetchError(null);
						}
					});
				}
			}
		});
		return resultsFuture;
	}

		public void addCommentsSyncTask(Item item) {
//    	if (LOGGING)
//    		Log.v(LOGTAG,"addCommentSyncTask " + item.getCommentsUrl());
//    	for (int i = 0; i < syncList.size(); i++) {
//    		Item itemTask = syncList.get(i).item;
//    		if (itemTask != null && itemTask.getDatabaseId() == item.getDatabaseId()) {
//    			if (overTime(syncList.get(i))) {
//    				syncList.get(i).status = SyncTask.ERROR;
//    	    		syncList.remove(syncList.get(i));
//    				if (LOGGING)
//    					Log.v(LOGTAG, "Item was already in queue but over time");
//    			}
//    			else {
//	        		if (LOGGING)
//	        			Log.v(LOGTAG,"Item already in queue, ignoring " + itemTask.getDatabaseId());
//	    			return;
//    			}
//    		}
//    	}
//    	SyncTask newSyncTask = new SyncTask(item);
//    	syncList.add(newSyncTask);
//    	newSyncTask.updateStatus(SyncTask.QUEUED);
//
//		syncServiceEvent(newSyncTask);
	}

//    void syncServiceEvent(SyncTask _syncTask) {
//
//    	if (_syncTask.status == SyncTask.FINISHED) {
//    		syncList.remove(_syncTask);
//    	} else if (_syncTask.status == SyncTask.ERROR) {
//    		syncList.remove(_syncTask);
//    	} else if (_syncTask.status == SyncTask.CANCELLED) {
//      		syncList.remove(_syncTask);
//    	}
//
//    	boolean startNewTask = true;
//    	for (int i = 0; i < syncList.size() && startNewTask; i++) {
//    		if (syncList.get(i).status == SyncTask.STARTED) {
//    			startNewTask = false;
//    		}
//    	}
//
//    	if (startNewTask) {
//	    	for (int i = 0; i < syncList.size(); i++) {
//	    		if (syncList.get(i).status == SyncTask.QUEUED) {
//	    			syncList.get(i).start();
//	    			break;
//	    		}
//	    	}
//    	}
//
//    	if (syncServiceCallback != null && SocialReader.getInstance(getApplicationContext()).appStatus == SocialReader.APP_IN_FOREGROUND) {
//    		syncServiceCallback.syncEvent(_syncTask);
//    	}
//    }

	public void addMediaContentSyncTask(MediaContent mediaContent) {
		addMediaContentSyncTask(mediaContent, false);
	}

	public void addMediaContentSyncTask(MediaContent mediaContent, boolean toFront) {
//    	for (int i = 0; i < syncList.size(); i++) {
//    		MediaContent mediaTask = syncList.get(i).mediaContent;
//    		if (mediaTask != null && mediaTask.getDatabaseId() == mediaContent.getDatabaseId()) {
//    			if (overTime(syncList.get(i))) {
//    				syncList.get(i).status = SyncTask.ERROR;
//    	    		syncList.remove(syncList.get(i));
//    				if (LOGGING)
//    					Log.v(LOGTAG, "MediaContent was already in queue but over time");
//    				break;
//    			}
//    			else
//    			{
//    				if (LOGGING)
//    					Log.v(LOGTAG,"MediaContent already in queue, ignoring");
//    				return;
//    			}
//    		}
//    	}
//    	SyncTask newSyncTask = new SyncTask(mediaContent);
//    	if (toFront) {
//    		syncList.add(0, newSyncTask);
//    	} else {
//    		syncList.add(newSyncTask);
//    	}
//    	newSyncTask.updateStatus(SyncTask.QUEUED);
//
//		syncServiceEvent(newSyncTask);
	}

	public void addMediaContentSyncTaskToFront(MediaContent mediaContent) {
		addMediaContentSyncTask(mediaContent, true);
	}

    /*
    public void addMediaContentSyncTaskToFront(MediaContent mediaContent) {
    	boolean addTask = true;
    	for (int i = 0; i < syncList.size(); i++) {
    		MediaContent mediaTask = syncList.get(i).mediaContent;
    		if (mediaTask != null && mediaTask.getDatabaseId() == mediaContent.getDatabaseId()) {
    			if (syncList.get(i).status == SyncTask.QUEUED)
    				syncList.get(i).status = SyncTask.CANCELLED;
    			else if (syncList.get(i).status == SyncTask.STARTED)
    				addTask = false;
        		if (LOGGING)
        			Log.v(LOGTAG,"MediaContent already in queue");
    			break;
    		}
    	}

    	if (addTask)
    	{
    		SyncTask newSyncTask = new SyncTask(mediaContent);
    		newSyncTask.updateStatus(SyncTask.QUEUED);
    		if (LOGGING) {
    			Log.v(LOGTAG, "addMediaContentSyncTaskToFront " + mediaContent.getUrl());
    		}
    		syncList.add(0, newSyncTask);
    	
    		syncServiceEvent(newSyncTask);    
    	}
    }
    */

	public void addMediaContentSyncTasksToFront(ArrayList<MediaContent> mediaContents, boolean forceStart) {
//
//    	if (mediaContents != null)
//    	{
//    		for (int idxMediaContent = mediaContents.size() - 1; idxMediaContent >= 0; idxMediaContent--)
//    		{
//    			MediaContent mediaContent = mediaContents.get(idxMediaContent);
//    	    	for (int i = 0; i < syncList.size(); i++) {
//    	    		MediaContent mediaTask = syncList.get(i).mediaContent;
//    	    		if (mediaTask != null && mediaTask.getDatabaseId() == mediaContent.getDatabaseId()) {
//    	    			if (syncList.get(i).status == SyncTask.QUEUED)
//    	    				syncList.get(i).status = SyncTask.CANCELLED;
//    	    			else if (syncList.get(i).status == SyncTask.STARTED)
//    	    				mediaContents.remove(idxMediaContent); // Remove it!
//    	        		if (LOGGING)
//    	        			Log.v(LOGTAG,"MediaContent already in queue");
//    	    			break;
//    	    		}
//    	    	}
//    		}
//
//    		// All remaining should be added
//    		if (mediaContents.size() > 0)
//    		{
//    			ArrayList<SyncTask> newTasks = new ArrayList<SyncTask>();
//    			for (int idxMediaContent = 0; idxMediaContent < mediaContents.size(); idxMediaContent++)
//    			{
//    				MediaContent mediaContent = mediaContents.get(idxMediaContent);
//    				SyncTask newSyncTask = new SyncTask(mediaContent);
//    				if (LOGGING) {
//    					Log.v(LOGTAG, "addMediaContentSyncTaskToFrontIndex " + idxMediaContent + " "+ mediaContent.getUrl());
//    				}
//    				newTasks.add(newSyncTask);
//    				syncList.add(idxMediaContent, newSyncTask);
//    			}
//
//    			// Update them all to queued (which might start the first one of them, if nothing is currently running
//    			for (SyncTask task : newTasks)
//    			{
//    				if (forceStart)
//    				{
//    					task.start();
//    				}
//    				else
//    				{
//    					task.updateStatus(SyncTask.QUEUED);
//    				}
//    			}
//    		}
//    	}
	}

//    public void clearSyncList() {
//    	for (int i = 0; i < syncList.size(); i++) {
//    		if (syncList.get(i).status == SyncTask.QUEUED) {
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask QUEUED");
//    			syncList.get(i).status = SyncTask.CANCELLED;
//    		} else if (syncList.get(i).status == SyncTask.CREATED) {
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask CREATED");
//    			syncList.get(i).status = SyncTask.CANCELLED;
//    		} else if (syncList.get(i).status == SyncTask.ERROR) {
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask ERROR");
//    		} else if (syncList.get(i).status == SyncTask.FINISHED) {
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask FINISHED");
//    		} else if (syncList.get(i).status == SyncTask.STARTED) {
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask STARTED");
//    		}
//    	}
//    }

	public int getNumWaitingToSync() {
		int count = 0;
//    	for (int i = 0; i < syncList.size(); i++) {
//    		if (syncList.get(i).status == SyncTask.QUEUED) {
//    			count++;
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask QUEUED");
//    		} else if (syncList.get(i).status == SyncTask.CREATED) {
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask CREATED");
//    		} else if (syncList.get(i).status == SyncTask.ERROR) {
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask ERROR");
//    		} else if (syncList.get(i).status == SyncTask.FINISHED) {
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask FINISHED");
//    		} else if (syncList.get(i).status == SyncTask.STARTED) {
//    			if (overTime(syncList.get(i))) {
//    				syncList.get(i).status = SyncTask.ERROR;
//    	    		syncList.remove(syncList.get(i));
//    				if (LOGGING)
//    					Log.v(LOGTAG, "syncTask STARTED but over time");
//    			} else {
//    				count++;
//    				if (LOGGING)
//    					Log.v(LOGTAG, "syncTask STARTED");
//    			}
//    		} else if (syncList.get(i).status == SyncTask.CANCELLED) {
//    			if (LOGGING)
//    				Log.v(LOGTAG, "syncTask CANCELLED");
//    		}
//    	}
		return count;
	}

	private class SyncServiceExecutorService extends ThreadPoolExecutor implements ListeningExecutorService {
		SyncServiceExecutorService(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
		}

		@Override
		public @NonNull <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
			return super.invokeAll(tasks);
		}

		@Override
		public @NonNull <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
			return super.invokeAll(tasks, timeout, unit);
		}

		@Override
		public @NonNull <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
			return super.invokeAny(tasks);
		}

		@Override
		public <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks, long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			return super.invokeAny(tasks, timeout, unit);
		}

		@Override
		protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
			return new PrioritizedListenableFutureTask<T>(callable, ((SyncTask)callable).priority);
		}

		@Override
		protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
			return new PrioritizedListenableFutureTask<T>(runnable, value, ((SyncTask)runnable).priority);
		}

		@Override
		public @NonNull <T> ListenableFuture<T> submit(Callable<T> task) {
			final ListenableFuture<T> ret = (ListenableFuture<T>) super.submit(task);
			Futures.addCallback(ret, new FutureCallback<T>() {
				@Override
				public void onSuccess(Object o) {
					SyncTask syncTask = ((PrioritizedListenableFutureTask)ret).getTask();
					syncTask.status = SyncTask.SyncTaskStatus.FINISHED;
				}

				@Override
				public void onFailure(Throwable throwable) {
					SyncTask syncTask = ((PrioritizedListenableFutureTask)ret).getTask();
					if (throwable instanceof CancellationException) {
						if (LOGGING)
							Log.d(LOGTAG, syncTask.toString() + " was cancelled");
						syncTask.status = SyncTask.SyncTaskStatus.CANCELLED;
					} else {
						syncTask.status = SyncTask.SyncTaskStatus.ERROR;
					}
				}
			});
			return ret;
		}

		@Override
		public @NonNull ListenableFuture<?> submit(Runnable task) {
			throw new RejectedExecutionException("This overload of submit not supported!");
		}

		@Override
		public @NonNull <T> ListenableFuture<T> submit(Runnable task, T result) {
			throw new RejectedExecutionException("This overload of submit not supported!");
		}
	}

	private class PrioritizedListenableFutureTask<V> extends FutureTask<V> implements ListenableFuture<V>, Comparable<PrioritizedListenableFutureTask<V>> {
		private final ExecutionList executionList = new ExecutionList();
		private final int priority;
		private final Callable<V> callable;

		PrioritizedListenableFutureTask(final Callable<V> callable, int priority) {
			super(new Callable<V>() {
				@Override
				public V call() throws Exception {
					final SyncTask syncTask = (SyncTask) callable;
					long now = System.currentTimeMillis();
					final long queueDuration = now - syncTask.startTime;
					syncTask.status = SyncTask.SyncTaskStatus.STARTED;
					syncTask.startTime = now;
					if (LOGGING)
						Log.d(LOGTAG, "Task {" + syncTask + "} spent " + queueDuration + "ms in queue");
					V result = callable.call();
					now = System.currentTimeMillis();
					final long runDuration = now - syncTask.startTime;
					if (LOGGING)
						Log.d(LOGTAG, "Task {" + syncTask + "} spent " + runDuration + "ms running");
					syncTask.status = SyncTask.SyncTaskStatus.FINISHED;
					return result;
				}
			});
			this.callable = callable;
			this.priority = priority;
			getTask().startTime = System.currentTimeMillis();
			getTask().status = SyncTask.SyncTaskStatus.QUEUED;
		}

		PrioritizedListenableFutureTask(final Runnable runnable, final V result, int priority) {
			this(new Callable<V>() {
				@Override
				public V call() throws Exception {
					runnable.run();
					return result;
				}
			}, priority);
		}

		SyncTask getTask() {
			return (SyncTask)callable;
		}

		@Override
		public void addListener(Runnable listener, Executor exec) {
			this.executionList.add(listener, exec);
		}

		@Override
		protected void done() {
			this.executionList.execute();
		}

		int getPriority() {
			return priority;
		}

		@Override
		public int compareTo(@NonNull PrioritizedListenableFutureTask<V> another) {
			if (this.getPriority() < another.getPriority()) {
				return 1;
			} else if (this.getPriority() > another.getPriority()) {
				return -1;
			}
			return 0;
		}
	}

	private class PrioritizedTaskListener<V extends SyncTask> implements Runnable {

		private final PrioritizedListenableFutureTask<V> future;

		PrioritizedTaskListener(PrioritizedListenableFutureTask<V> future) {
			this.future = future;
		}

		@Override
		public void run() {
			V result;
			try {
				result = Futures.getDone(future);
			} catch (Exception e) {
				onFailure((V)future.getTask());
				return;
			}
			onSuccess((V)future.getTask());
		}

		protected void onSuccess(V task) {
		}

		protected void onFailure(V task) {
		}
	}
}
