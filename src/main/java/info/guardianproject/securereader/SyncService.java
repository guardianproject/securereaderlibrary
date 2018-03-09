package info.guardianproject.securereader;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

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

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SyncService {

	public static final String BROADCAST_SYNCSERVICE_FEED_STATUS = "syncservice_feed_status";
	public static final String BROADCAST_SYNCSERVICE_FEED_ICON_STATUS = "syncservice_feed_icon_status";
	public static final String BROADCAST_SYNCSERVICE_MEDIA_STATUS = "syncservice_media_status";
	public static final String BROADCAST_SYNCSERVICE_COMMENTS_STATUS = "syncservice_comments_status";
	public static final String EXTRA_SYNCSERVICE_STATUS = "syncservice_extras_status";
	public static final String EXTRA_SYNCSERVICE_FEED = "syncservice_extras_feed";
	public static final String EXTRA_SYNCSERVICE_MEDIA = "syncservice_extras_media";
	public static final String EXTRA_SYNCSERVICE_ITEM = "syncservice_extras_item";

	private enum DownloadType {
		Feed,
		FeedIcon,
		ItemComments,
		ItemText,
		ItemMedia
	}

	private enum PriorityComponent {
		FEED_NUMBER(10),
		ITEM_NUMBER(10),
		DATE_RANGE(4),
		TYPE(4),
		USER_INITIATED(1);

		public final int bits;
		PriorityComponent(int bits) {
			this.bits = bits;
		}

	}
	private static PriorityComponent[] priorityScheme = new PriorityComponent[] { PriorityComponent.USER_INITIATED, PriorityComponent.DATE_RANGE, PriorityComponent.TYPE, PriorityComponent.ITEM_NUMBER, PriorityComponent.FEED_NUMBER };
	private static DownloadType[] prioritySchemeTypes = new DownloadType[] { DownloadType.Feed, DownloadType.FeedIcon, DownloadType.ItemText, DownloadType.ItemMedia, DownloadType.ItemComments };

	long getPriorityForItem(int feedNumber, int itemNumber, Date itemDate, DownloadType type, boolean userInitiated) {

		long priority = 0;

		for (PriorityComponent component : priorityScheme) {
			int bitlength = component.bits;
			priority = priority << bitlength;
			long mask = (1 << bitlength) - 1;
			switch (component) {
				case FEED_NUMBER:
					priority = priority | (mask & feedNumber);
					break;
				case ITEM_NUMBER:
					priority = priority | (mask & itemNumber);
					break;
				case DATE_RANGE:
					if (itemDate != null) {
						long now = System.currentTimeMillis();
						long diff = itemDate.getTime() - now;
						if (diff < 1000 * 60 * 60 * 4) {
							// 0
						} else if (diff < 1000 * 60 * 60 * 8) {
							priority = priority | (mask & 1);
						} else if (diff < 1000 * 60 * 60 * 24) {
							priority = priority | (mask & 2);
						} else {
							priority = priority | (mask & 3);
						}
					}
					break;
				case TYPE: {
					int value = Arrays.asList(prioritySchemeTypes).indexOf(type);
					if (value == -1) {
						value = 0;
					}
					priority = priority | (mask & value);
					}
					break;
				case USER_INITIATED:
					priority = priority | (mask & (userInitiated ? 0 : 1)); // Negate the flag, user initiated has higher prio (= lower number)
					break;
				default:break;
			}
		}

		return priority;
	}

	private long getPriorityForFeed(Feed feed, boolean userInitiated) {
		return getPriorityForItem(getFeedIndexNumber(feed), 0, null, DownloadType.Feed, userInitiated);
	}

	private long getPriorityForItem(Item item, DownloadType type, boolean userInitiated) {
		long feedId = item.getFeedId();
		Feed feed = socialReader.getFeedById(feedId);
		return getPriorityForItem(getFeedIndexNumber(feed), getItemIndexNumber(feed, item), item.getPubDate(), type, userInitiated);
	}

	private long getPriorityForFeed(Feed feed, DownloadType type, boolean userInitiated) {
		return getPriorityForItem(getFeedIndexNumber(feed), 0, null, type, userInitiated);
	}

	private int getFeedIndexNumber(Feed feed) {
		if (feed == null) {
			return 0;
		}
		List<Feed> feeds = socialReader.getSubscribedFeedsList();
		if (feeds != null) {
			for (int i = 0; i < feeds.size(); i++) {
				if (feeds.get(i).getDatabaseId() == feed.getDatabaseId()) {
					return i;
				}
			}
		}
		return 0;
	}

	private int getItemIndexNumber(Feed feed, Item item) {
		return 0; //TODO
	}

	//private static final int TASK_FEED_PRIORITY = 5;
	//private static final int TASK_FEED_ICON_PRIORITY = 4;
	//private static final int TASK_MEDIA_PRIORITY = 3;
	//private static final int TASK_MEDIA_UI_PRIORITY = 10;
	//private static final int TASK_COMMENTS_PRIORITY = 5;

	public static final String LOGTAG = "SyncService";
	public static final boolean LOGGING = false;

	// Backoff times in seconds
	private static final int[] SYNC_ERROR_BACKOFF_TIMES = {120, 300, 3600, 3600 * 24};

	private static SyncService instance;

	public static SyncService getInstance(Context context, SocialReader socialReader) {
		if (instance == null) {
			instance = new SyncService(context.getApplicationContext(), socialReader);
		}
		return instance;
	}

	private final Context context;
	private final SocialReader socialReader;
	private final Handler handler;
	private final BlockingQueue<Runnable> syncServiceExecutorQueue;
	private final SyncServiceExecutorService syncServiceExecutorService;

	private SyncService(Context context, SocialReader socialReader) {
		this.context = context;
		this.socialReader = socialReader;
		handler = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message inputMessage) {
			}
		};

		syncServiceExecutorQueue = new PriorityBlockingQueue<Runnable>(100) {
			@Override
			public Runnable take() throws InterruptedException {
				if (LOGGING)
					Log.d(LOGTAG, "Sync queue take: " + this.size());
				return super.take();
			}
		};
		final ThreadFactory threadFactory = new ThreadFactoryBuilder()
				.setNameFormat("SyncThread-%d")
				.setDaemon(true)
				.setPriority(Thread.NORM_PRIORITY - 1)
				.build();
		syncServiceExecutorService = new SyncServiceExecutorService(4, 4,
				5000L, TimeUnit.MILLISECONDS, syncServiceExecutorQueue, threadFactory);
	}

	public void cancelAll() {
		Runnable[] syncList = new Runnable[syncServiceExecutorQueue.size()];
		syncServiceExecutorQueue.toArray(syncList);
		synchronized (syncServiceExecutorService) {
			for (Runnable syncListItem : syncList) {
				((PrioritizedListenableFutureTask) syncListItem).cancel(true);
			}
			syncServiceExecutorService.purge();
		}
	}

	private boolean overTime(SyncTask syncTask) {
		return syncTask.status == SyncTask.SyncTaskStatus.STARTED &&
				System.currentTimeMillis() - syncTask.startTime > SyncTask.MAXTIME;
	}

	private PrioritizedListenableFutureTask getExistingTask(Class<? extends SyncTask> type, Object identifier) {
		synchronized (syncServiceExecutorService) {
			Runnable[] syncList = syncServiceExecutorQueue.toArray(new Runnable[0]);
			for (Runnable syncListItem : syncList) {
				if (syncListItem instanceof PrioritizedListenableFutureTask) {
					PrioritizedListenableFutureTask listenableFutureTask = (PrioritizedListenableFutureTask) syncListItem;
					if (type.isInstance(listenableFutureTask.getTask())) {
						SyncTask task = listenableFutureTask.getTask();
						if (task.getIdentifier().equals(identifier)) {
							if (overTime(task)) {
								listenableFutureTask.cancel(true);
								if (LOGGING)
									Log.v(LOGTAG, "Task was already in queue but over time");
							} else {
								if (LOGGING)
									Log.v(LOGTAG, "Task already in queue, ignoring");
								return listenableFutureTask;
							}
						}
					}
				}
			}
		}
		return null;
	}

	public PrioritizedListenableFutureTask<SyncTaskFeedIconFetcher> addFeedIconSyncTask(Feed feed, boolean userInitiated) {
				synchronized (syncServiceExecutorService) {
					PrioritizedListenableFutureTask<SyncTaskFeedIconFetcher> task = getExistingTask(SyncTaskFeedIconFetcher.class, feed.getFeedURL());
					if (task != null) {
						return task; // Already in queue
					}
					long priority = getPriorityForFeed(feed, DownloadType.FeedIcon, userInitiated);
			task = (PrioritizedListenableFutureTask<SyncTaskFeedIconFetcher>) syncServiceExecutorService.submit(new SyncTaskFeedIconFetcher(context, priority, feed));
			task.addListener(new PrioritizedTaskListener<SyncTaskFeedIconFetcher>(task) {

				private void sendBroadcast(Feed feed, SyncStatus status) {
					Intent statusIntent = new Intent(BROADCAST_SYNCSERVICE_FEED_ICON_STATUS);
					statusIntent.putExtra(EXTRA_SYNCSERVICE_FEED, feed);
					statusIntent.putExtra(EXTRA_SYNCSERVICE_STATUS, status);
					LocalBroadcastManager.getInstance(context).sendBroadcast(statusIntent);
				}

				@Override
				protected void onSuccess(SyncTaskFeedIconFetcher task) {
					super.onSuccess(task);
					sendBroadcast(task.feed, SyncStatus.OK);
				}

				@Override
				protected void onFailure(SyncTaskFeedIconFetcher task) {
					super.onFailure(task);
					sendBroadcast(task.feed, SyncStatus.ERROR_UNKNOWN);
				}
			}, MoreExecutors.directExecutor());
			return task;
		}
	}

	public PrioritizedListenableFutureTask<SyncTaskFeedFetcher> addFeedSyncTask(Feed feed, final boolean userInitiated, final SyncTaskFeedFetcher.SyncTaskFeedFetcherCallback callback) {
		synchronized (syncServiceExecutorService) {
			PrioritizedListenableFutureTask<SyncTaskFeedFetcher> task = getExistingTask(SyncTaskFeedFetcher.class, feed.getFeedURL());
			if (task != null) {
				return task;
			}

			// If not user initiated, respect error back-off
			if (!userInitiated && !shouldAutoResync(feed)) {
				return null; // Wait a bit longer...
			}

			long priority = getPriorityForFeed(feed, userInitiated);
			final SyncTaskFeedFetcher feedSyncTask = new SyncTaskFeedFetcher(context, priority, feed);
			task = (PrioritizedListenableFutureTask<SyncTaskFeedFetcher>) syncServiceExecutorService.submit(feedSyncTask);

			// Add a listener for the future
			PrioritizedTaskListener<SyncTaskFeedFetcher> listener = new PrioritizedTaskListener<SyncTaskFeedFetcher>(task) {

				private void sendBroadcast(Feed feed, SyncStatus status) {
					Intent statusIntent = new Intent(BROADCAST_SYNCSERVICE_FEED_STATUS);
					statusIntent.putExtra(EXTRA_SYNCSERVICE_FEED, feed);
					statusIntent.putExtra(EXTRA_SYNCSERVICE_STATUS, status);
					LocalBroadcastManager.getInstance(context).sendBroadcast(statusIntent);
				}

				@Override
				protected void onSuccess(final SyncTaskFeedFetcher task) {
					super.onSuccess(task);
					// Tell our listeners we are done
					sendBroadcast(task.feed, task.feed.getStatus());
					addFeedIconSyncTask(task.feed, userInitiated);
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
					super.onFailure(task);
					sendBroadcast(task.feed, task.feed.getStatus());
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
			task.addListener(listener, MoreExecutors.directExecutor());
			return task;
		}
	}

	public ListenableFuture addFeedsSyncTask(List<Feed> feeds, boolean userInitiated, final SyncTaskFeedFetcher.SyncTaskFeedFetcherCallback callback) {
		if (feeds == null || feeds.size() == 0) {
			if (callback != null) {
				callback.feedFetched(new Feed());
			}
			return null;
		}

		List<PrioritizedListenableFutureTask<SyncTaskFeedFetcher>> futures = Lists.newArrayList();
		for (final Feed feed : feeds) {
			PrioritizedListenableFutureTask<SyncTaskFeedFetcher> future = addFeedSyncTask(feed, userInitiated, null);
			if (future != null) {
				futures.add(future);
			}
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

	public PrioritizedListenableFutureTask<SyncTaskCommentsFetcher> addCommentsSyncTask(Item item, boolean userInitiated, final SyncTaskCommentsFetcher.SyncServiceCommentsFeedFetchedCallback callback) {
		synchronized (syncServiceExecutorService) {
			PrioritizedListenableFutureTask<SyncTaskCommentsFetcher> task = getExistingTask(SyncTaskCommentsFetcher.class, item.getCommentsUrl());
			if (task != null) {
				return task;
			}

			// If not user initiated, respect error back-off
			if (!userInitiated && !shouldAutoResync(item)) {
				return null; // Wait a bit longer...
			}

			long priority = getPriorityForItem(item, DownloadType.ItemComments, userInitiated);
			final SyncTaskCommentsFetcher commentsSyncTask = new SyncTaskCommentsFetcher(context, priority, item);
			task = (PrioritizedListenableFutureTask<SyncTaskCommentsFetcher>) syncServiceExecutorService.submit(commentsSyncTask);

			// Add a listener for the future
			PrioritizedTaskListener<SyncTaskCommentsFetcher> listener = new PrioritizedTaskListener<SyncTaskCommentsFetcher>(task) {

				private void sendBroadcast(Item item, SyncStatus status) {
					Intent statusIntent = new Intent(BROADCAST_SYNCSERVICE_COMMENTS_STATUS);
					statusIntent.putExtra(EXTRA_SYNCSERVICE_ITEM, item);
					statusIntent.putExtra(EXTRA_SYNCSERVICE_STATUS, status);
					LocalBroadcastManager.getInstance(context).sendBroadcast(statusIntent);
				}

				@Override
				protected void onSuccess(final SyncTaskCommentsFetcher task) {
					super.onSuccess(task);
					sendBroadcast(task.item, task.item.getStatus());
					handler.post(new Runnable() {
						@Override
						public void run() {
							if (callback != null) {
								callback.commentsFeedFetched(task.item);
							}
						}
					});
				}

				@Override
				protected void onFailure(final SyncTaskCommentsFetcher task) {
					super.onFailure(task);
					sendBroadcast(task.item, task.item.getStatus());
					handler.post(new Runnable() {
						@Override
						public void run() {
							if (callback != null) {
								callback.commentsFeedFetchError(task.item);
							}
						}
					});
				}
			};
			task.addListener(listener, MoreExecutors.directExecutor());
			return task;
		}
	}

	public PrioritizedListenableFutureTask<SyncTaskMediaFetcher> addMediaContentSyncTask(Item item, MediaContent mediaContent, boolean userInitiated, final SyncTaskMediaFetcher.SyncTaskMediaFetcherCallback callback) {
		synchronized (syncServiceExecutorService) {
			long priority = getPriorityForItem(item, DownloadType.ItemMedia, userInitiated);
			PrioritizedListenableFutureTask<SyncTaskMediaFetcher> task = getExistingTask(SyncTaskMediaFetcher.class, mediaContent.getUrl());
			if (task != null) {
				// Need to change priority?
				if (task.getPriority() != priority) {
					syncServiceExecutorQueue.remove(task);
					task.setPriority(priority);
					syncServiceExecutorQueue.offer(task);
				}
				return task; // Already in queue
			}

			// If not user initiated, respect error back-off
			if (!userInitiated && !shouldAutoResync(mediaContent)) {
				return null; // Wait a bit longer...
			}

			task = (PrioritizedListenableFutureTask<SyncTaskMediaFetcher>) syncServiceExecutorService.submit(new SyncTaskMediaFetcher(context, priority, mediaContent));
			task.addListener(new PrioritizedTaskListener<SyncTaskMediaFetcher>(task) {

				private void sendBroadcast(MediaContent mediaContent, SyncStatus status) {
					Intent statusIntent = new Intent(BROADCAST_SYNCSERVICE_MEDIA_STATUS);
					statusIntent.putExtra(EXTRA_SYNCSERVICE_MEDIA, mediaContent);
					statusIntent.putExtra(EXTRA_SYNCSERVICE_STATUS, status);
					LocalBroadcastManager.getInstance(context).sendBroadcast(statusIntent);
				}

				@Override
				protected void onSuccess(final SyncTaskMediaFetcher task) {
					super.onSuccess(task);
					sendBroadcast(task.mediaContent, SyncStatus.OK);
					if (callback != null) {
						handler.post(new Runnable() {
							@Override
							public void run() {
								callback.mediaDownloaded(task.mediaContent, task.targetFile);
							}
						});
					}
				}

				@Override
				protected void onFailure(SyncTaskMediaFetcher task) {
					super.onFailure(task);
					sendBroadcast(task.mediaContent, SyncStatus.ERROR_UNKNOWN);
					if (callback != null) {
						handler.post(new Runnable() {
							@Override
							public void run() {
								callback.mediaDownloadError(null);
							}
						});
					}
				}
			}, MoreExecutors.directExecutor());
			return task;
		}
	}

	public int getNumWaitingToSync() {
		return syncServiceExecutorQueue.size();
	}

	private class SyncServiceExecutorService extends ThreadPoolExecutor implements ListeningExecutorService {
		SyncServiceExecutorService(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
		}

		@Override
		protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
			return new PrioritizedListenableFutureTask<>(callable, ((SyncTask) callable).priority);
		}

		@Override
		protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
			return new PrioritizedListenableFutureTask<>(runnable, value, ((SyncTask) runnable).priority);
		}

		@Override
		public @NonNull
		<T> ListenableFuture<T> submit(Callable<T> task) {
			final ListenableFuture<T> ret = (ListenableFuture<T>) super.submit(task);
			if (LOGGING)
				Log.d(LOGTAG, "Adding " + task.toString() + " to sync. Queue length " + syncServiceExecutorQueue.size());
			Futures.addCallback(ret, new FutureCallback<T>() {
				@Override
				public void onSuccess(Object o) {
					SyncTask syncTask = ((PrioritizedListenableFutureTask) ret).getTask();
					if (LOGGING)
						Log.d(LOGTAG, syncTask.toString() + " succeeded");
					syncTask.status = SyncTask.SyncTaskStatus.FINISHED;
				}

				@Override
				public void onFailure(Throwable throwable) {
					SyncTask syncTask = ((PrioritizedListenableFutureTask) ret).getTask();
					if (throwable instanceof CancellationException) {
						if (LOGGING)
							Log.d(LOGTAG, syncTask.toString() + " was cancelled");
						syncTask.status = SyncTask.SyncTaskStatus.CANCELLED;
					} else {
						if (LOGGING)
							Log.d(LOGTAG, syncTask.toString() + " failed");
						syncTask.status = SyncTask.SyncTaskStatus.ERROR;
					}
				}
			});
			debugQueue();
			return ret;
		}

		@Override
		public @NonNull
		ListenableFuture<?> submit(Runnable task) {
			throw new RejectedExecutionException("This overload of submit not supported!");
		}

		@Override
		public @NonNull
		<T> ListenableFuture<T> submit(Runnable task, T result) {
			throw new RejectedExecutionException("This overload of submit not supported!");
		}

		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			super.afterExecute(r, t);
			debugQueue();
		}
	}

	private class PrioritizedListenableFutureTask<V> extends FutureTask<V> implements ListenableFuture<V>, Comparable<PrioritizedListenableFutureTask<V>> {
		private final ExecutionList executionList = new ExecutionList();
		private long priority;
		private final Callable<V> callable;

		PrioritizedListenableFutureTask(final Callable<V> callable, long priority) {
			super(new Callable<V>() {
				@Override
				public V call() throws Exception {
					String threadName = Thread.currentThread().getName();
					Thread.currentThread().setName(threadName + "-Processing");
					try {
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
						Thread.currentThread().setName(threadName);
						return result;
					} catch (Exception e) {
						Thread.currentThread().setName(threadName);
						throw e;
					}
				}
			});
			this.callable = callable;
			this.priority = priority;
			getTask().startTime = System.currentTimeMillis();
			getTask().status = SyncTask.SyncTaskStatus.QUEUED;
		}

		PrioritizedListenableFutureTask(final Runnable runnable, final V result, long priority) {
			this(new Callable<V>() {
				@Override
				public V call() throws Exception {
					runnable.run();
					return result;
				}
			}, priority);
		}

		SyncTask getTask() {
			return (SyncTask) callable;
		}

		@Override
		public void addListener(Runnable listener, Executor exec) {
			this.executionList.add(listener, exec);
		}

		@Override
		protected void done() {
			this.executionList.execute();
		}

		long getPriority() {
			return priority;
		}

		void setPriority(long priority) {
			this.priority = priority;
		}

		@Override
		public int compareTo(@NonNull PrioritizedListenableFutureTask<V> another) {
			if (this.getPriority() < another.getPriority()) {
				return -1;
			} else if (this.getPriority() > another.getPriority()) {
				return 1;
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
				onFailure((V) future.getTask());
				return;
			}
			onSuccess((V) future.getTask());
		}

		protected void onSuccess(V task) {
		}

		protected void onFailure(V task) {
		}
	}

	public boolean isFeedSyncing(Feed feed) {
		PrioritizedListenableFutureTask<SyncTaskFeedFetcher> task = getExistingTask(SyncTaskFeedFetcher.class, feed.getFeedURL());
		return (task != null);
	}

	public void cancelAllForFeed(Feed feed) {
		// TODO - implement a feedId in SyncTaskMediaFetcher so we can cancel all those. Also,
		// we should cancel icon fetch etc. here as well.
		Runnable[] syncList = new Runnable[syncServiceExecutorQueue.size()];
		synchronized (syncServiceExecutorService) {
			syncServiceExecutorQueue.toArray(syncList);
			for (Runnable syncListItem : syncList) {
				PrioritizedListenableFutureTask task = (PrioritizedListenableFutureTask) syncListItem;
				if (task.getTask() instanceof SyncTaskFeedFetcher) {
					SyncTaskFeedFetcher fetcher = (SyncTaskFeedFetcher) task.getTask();
					if (fetcher.feed.getFeedURL().equals(feed.getFeedURL())) {
						task.cancel(true);
					}
				}
			}
		}
	}

	private boolean shouldAutoResync(Object object) {
		SyncStatus status = socialReader.syncStatus(object);
		if (!status.equals(SyncStatus.OK) && status.tryCount > 0) {
			int idxBackoff = (int) status.tryCount - 1;
			idxBackoff = Math.min(idxBackoff, SYNC_ERROR_BACKOFF_TIMES.length - 1);
			int backoffTime = SYNC_ERROR_BACKOFF_TIMES[idxBackoff] * 1000; // milliseconds
			if (status.lastTry != null && (status.lastTry.getTime() + backoffTime) > new Date().getTime()) {
				if (LOGGING)
					Log.d(LOGTAG, "shouldAutoResync - false. Item " + object.toString() + " try count " + status.tryCount + " status " + status.Value + " last try at " + status.lastTry);
				return false; // Wait a bit longer...
			}
		}
		return true;
	}

	private void debugQueue() {
		if (LOGGING) {
			Runnable[] syncList = new Runnable[syncServiceExecutorQueue.size()];
			synchronized (syncServiceExecutorService) {
				syncServiceExecutorQueue.toArray(syncList);
				if (syncList.length == 0) {
					Log.d(LOGTAG, "SYNC LIST IS EMPTY");
				} else {
					Log.d(LOGTAG, "SYNC LIST ---");
					for (Runnable syncListItem : syncList) {
						if (syncListItem instanceof PrioritizedListenableFutureTask) {
							PrioritizedListenableFutureTask listenableFutureTask = (PrioritizedListenableFutureTask) syncListItem;
							Log.d(LOGTAG, listenableFutureTask.getTask().toString());
						}
					}
					Log.d(LOGTAG, "----");
				}
			}
		}
	}
}