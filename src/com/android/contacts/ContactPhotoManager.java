/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.util.MemoryUtils;
import com.android.contacts.util.UriUtils;
import com.google.android.collect.Lists;
import com.google.android.collect.Sets;

import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronously loads contact photos and maintains a cache of photos.
 */
public abstract class ContactPhotoManager implements ComponentCallbacks2 {
    static final String TAG = "ContactPhotoManager";
    static final boolean DEBUG = false; // Don't submit with true

    public static final String CONTACT_PHOTO_SERVICE = "contactPhotos";

    public static int getDefaultAvatarResId(boolean hires, boolean darkTheme) {
        if (hires && darkTheme) return R.drawable.ic_contact_picture_180_holo_dark;
        if (hires) return R.drawable.ic_contact_picture_180_holo_light;
        if (darkTheme) return R.drawable.ic_contact_picture_holo_dark;
        return R.drawable.ic_contact_picture_holo_light;
    }

    public static abstract class DefaultImageProvider {
        public abstract void applyDefaultImage(ImageView view, boolean hires, boolean darkTheme);
    }

    private static class AvatarDefaultImageProvider extends DefaultImageProvider {
        @Override
        public void applyDefaultImage(ImageView view, boolean hires, boolean darkTheme) {
            view.setImageResource(getDefaultAvatarResId(hires, darkTheme));
        }
    }

    private static class BlankDefaultImageProvider extends DefaultImageProvider {
        private static Drawable sDrawable;

        @Override
        public void applyDefaultImage(ImageView view, boolean hires, boolean darkTheme) {
            if (sDrawable == null) {
                Context context = view.getContext();
                sDrawable = new ColorDrawable(context.getResources().getColor(
                        R.color.image_placeholder));
            }
            view.setImageDrawable(sDrawable);
        }
    }

    public static final DefaultImageProvider DEFAULT_AVATER = new AvatarDefaultImageProvider();

    public static final DefaultImageProvider DEFAULT_BLANK = new BlankDefaultImageProvider();

    /**
     * Requests the singleton instance of {@link AccountTypeManager} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static ContactPhotoManager getInstance(Context context) {
        Context applicationContext = context.getApplicationContext();
        ContactPhotoManager service =
                (ContactPhotoManager) applicationContext.getSystemService(CONTACT_PHOTO_SERVICE);
        if (service == null) {
            service = createContactPhotoManager(applicationContext);
            Log.e(TAG, "No contact photo service in context: " + applicationContext);
        }
        return service;
    }

    public static synchronized ContactPhotoManager createContactPhotoManager(Context context) {
        return new ContactPhotoManagerImpl(context);
    }

    /**
     * Load photo into the supplied image view.  If the photo is already cached,
     * it is displayed immediately.  Otherwise a request is sent to load the photo
     * from the database.
     */
    public abstract void loadPhoto(ImageView view, long photoId, boolean hires, boolean darkTheme,
            DefaultImageProvider defaultProvider);

    /**
     * Calls {@link #loadPhoto(ImageView, long, boolean, boolean, DefaultImageProvider)} with
     * {@link #DEFAULT_AVATER}.
     */
    public final void loadPhoto(ImageView view, long photoId, boolean hires, boolean darkTheme) {
        loadPhoto(view, photoId, hires, darkTheme, DEFAULT_AVATER);
    }

    /**
     * Load photo into the supplied image view.  If the photo is already cached,
     * it is displayed immediately.  Otherwise a request is sent to load the photo
     * from the location specified by the URI.
     */
    public abstract void loadPhoto(ImageView view, Uri photoUri, boolean hires, boolean darkTheme,
            DefaultImageProvider defaultProvider);

    /**
     * Calls {@link #loadPhoto(ImageView, Uri, boolean, boolean, DefaultImageProvider)} with
     * {@link #DEFAULT_AVATER}.
     */
    public final void loadPhoto(ImageView view, Uri photoUri, boolean hires, boolean darkTheme) {
        loadPhoto(view, photoUri, hires, darkTheme, DEFAULT_AVATER);
    }

    /**
     * Remove photo from the supplied image view. This also cancels current pending load request
     * inside this photo manager.
     */
    public abstract void removePhoto(ImageView view);

    /**
     * Temporarily stops loading photos from the database.
     */
    public abstract void pause();

    /**
     * Resumes loading photos from the database.
     */
    public abstract void resume();

    /**
     * Marks all cached photos for reloading.  We can continue using cache but should
     * also make sure the photos haven't changed in the background and notify the views
     * if so.
     */
    public abstract void refreshCache();

    /**
     * Initiates a background process that over time will fill up cache with
     * preload photos.
     */
    public abstract void preloadPhotosInBackground();

    // ComponentCallbacks2
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    // ComponentCallbacks2
    @Override
    public void onLowMemory() {
    }

    // ComponentCallbacks2
    @Override
    public void onTrimMemory(int level) {
    }
}

class ContactPhotoManagerImpl extends ContactPhotoManager implements Callback {
    private static final String LOADER_THREAD_NAME = "ContactPhotoLoader";

    /**
     * Type of message sent by the UI thread to itself to indicate that some photos
     * need to be loaded.
     */
    private static final int MESSAGE_REQUEST_LOADING = 1;

    /**
     * Type of message sent by the loader thread to indicate that some photos have
     * been loaded.
     */
    private static final int MESSAGE_PHOTOS_LOADED = 2;

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final String[] COLUMNS = new String[] { Photo._ID, Photo.PHOTO };

    /**
     * Maintains the state of a particular photo.
     */
    private static class BitmapHolder {
        final byte[] bytes;

        volatile boolean fresh;
        Bitmap bitmap;
        Reference<Bitmap> bitmapRef;

        public BitmapHolder(byte[] bytes) {
            this.bytes = bytes;
            this.fresh = true;
        }
    }

    private final Context mContext;

    /**
     * An LRU cache for bitmap holders. The cache contains bytes for photos just
     * as they come from the database. Each holder has a soft reference to the
     * actual bitmap.
     */
    private final LruCache<Object, BitmapHolder> mBitmapHolderCache;

    /**
     * Cache size threshold at which bitmaps will not be preloaded.
     */
    private final int mBitmapHolderCacheRedZoneBytes;

    /**
     * Level 2 LRU cache for bitmaps. This is a smaller cache that holds
     * the most recently used bitmaps to save time on decoding
     * them from bytes (the bytes are stored in {@link #mBitmapHolderCache}.
     */
    private final LruCache<Object, Bitmap> mBitmapCache;

    /**
     * A map from ImageView to the corresponding photo ID or uri, encapsulated in a request.
     * The request may swapped out before the photo loading request is started.
     */
    private final ConcurrentHashMap<ImageView, Request> mPendingRequests =
            new ConcurrentHashMap<ImageView, Request>();

    /**
     * Handler for messages sent to the UI thread.
     */
    private final Handler mMainThreadHandler = new Handler(this);

    /**
     * Thread responsible for loading photos from the database. Created upon
     * the first request.
     */
    private LoaderThread mLoaderThread;

    /**
     * A gate to make sure we only send one instance of MESSAGE_PHOTOS_NEEDED at a time.
     */
    private boolean mLoadingRequested;

    /**
     * Flag indicating if the image loading is paused.
     */
    private boolean mPaused;

    /** Cache size for {@link #mBitmapHolderCache} for devices with "large" RAM. */
    private static final int HOLDER_CACHE_SIZE = 2000000;

    /** Cache size for {@link #mBitmapCache} for devices with "large" RAM. */
    private static final int BITMAP_CACHE_SIZE = 36864 * 48; // 1728K

    private static final int LARGE_RAM_THRESHOLD = 640 * 1024 * 1024;

    /** For debug: How many times we had to reload cached photo for a stale entry */
    private final AtomicInteger mStaleCacheOverwrite = new AtomicInteger();

    /** For debug: How many times we had to reload cached photo for a fresh entry.  Should be 0. */
    private final AtomicInteger mFreshCacheOverwrite = new AtomicInteger();

    public ContactPhotoManagerImpl(Context context) {
        mContext = context;

        final float cacheSizeAdjustment =
                (MemoryUtils.getTotalMemorySize() >= LARGE_RAM_THRESHOLD) ? 1.0f : 0.5f;
        final int bitmapCacheSize = (int) (cacheSizeAdjustment * BITMAP_CACHE_SIZE);
        mBitmapCache = new LruCache<Object, Bitmap>(bitmapCacheSize) {
            @Override protected int sizeOf(Object key, Bitmap value) {
                return value.getByteCount();
            }

            @Override protected void entryRemoved(
                    boolean evicted, Object key, Bitmap oldValue, Bitmap newValue) {
                if (DEBUG) dumpStats();
            }
        };
        final int holderCacheSize = (int) (cacheSizeAdjustment * HOLDER_CACHE_SIZE);
        mBitmapHolderCache = new LruCache<Object, BitmapHolder>(holderCacheSize) {
            @Override protected int sizeOf(Object key, BitmapHolder value) {
                return value.bytes != null ? value.bytes.length : 0;
            }

            @Override protected void entryRemoved(
                    boolean evicted, Object key, BitmapHolder oldValue, BitmapHolder newValue) {
                if (DEBUG) dumpStats();
            }
        };
        mBitmapHolderCacheRedZoneBytes = (int) (holderCacheSize * 0.75);
        Log.i(TAG, "Cache adj: " + cacheSizeAdjustment);
        if (DEBUG) {
            Log.d(TAG, "Cache size: " + btk(mBitmapHolderCache.maxSize())
                    + " + " + btk(mBitmapCache.maxSize()));
        }
    }

    /** Converts bytes to K bytes, rounding up.  Used only for debug log. */
    private static String btk(int bytes) {
        return ((bytes + 1023) / 1024) + "K";
    }

    private static final int safeDiv(int dividend, int divisor) {
        return (divisor  == 0) ? 0 : (dividend / divisor);
    }

    /**
     * Dump cache stats on logcat.
     */
    private void dumpStats() {
        if (!DEBUG) return;
        {
            int numHolders = 0;
            int rawBytes = 0;
            int bitmapBytes = 0;
            int numBitmaps = 0;
            for (BitmapHolder h : mBitmapHolderCache.snapshot().values()) {
                numHolders++;
                if (h.bytes != null) {
                    rawBytes += h.bytes.length;
                }
                Bitmap b = h.bitmapRef != null ? h.bitmapRef.get() : null;
                if (b != null) {
                    numBitmaps++;
                    bitmapBytes += b.getByteCount();
                }
            }
            Log.d(TAG, "L1: " + btk(rawBytes) + " + " + btk(bitmapBytes) + " = "
                    + btk(rawBytes + bitmapBytes) + ", " + numHolders + " holders, "
                    + numBitmaps + " bitmaps, avg: "
                    + btk(safeDiv(rawBytes, numHolders))
                    + "," + btk(safeDiv(bitmapBytes,numBitmaps)));
            Log.d(TAG, "L1 Stats: " + mBitmapHolderCache.toString()
                    + ", overwrite: fresh=" + mFreshCacheOverwrite.get()
                    + " stale=" + mStaleCacheOverwrite.get());
        }

        {
            int numBitmaps = 0;
            int bitmapBytes = 0;
            for (Bitmap b : mBitmapCache.snapshot().values()) {
                numBitmaps++;
                bitmapBytes += b.getByteCount();
            }
            Log.d(TAG, "L2: " + btk(bitmapBytes) + ", " + numBitmaps + " bitmaps"
                    + ", avg: " + btk(safeDiv(bitmapBytes, numBitmaps)));
            // We don't get from L2 cache, so L2 stats is meaningless.
        }
    }

    @Override
    public void onTrimMemory(int level) {
        if (DEBUG) Log.d(TAG, "onTrimMemory: " + level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            // Clear the caches.  Note all pending requests will be removed too.
            clear();
        }
    }

    @Override
    public void preloadPhotosInBackground() {
        ensureLoaderThread();
        mLoaderThread.requestPreloading();
    }

    @Override
    public void loadPhoto(ImageView view, long photoId, boolean hires, boolean darkTheme,
            DefaultImageProvider defaultProvider) {
        if (photoId == 0) {
            // No photo is needed
            defaultProvider.applyDefaultImage(view, hires, darkTheme);
            mPendingRequests.remove(view);
        } else {
            if (DEBUG) Log.d(TAG, "loadPhoto request: " + photoId);
            loadPhotoByIdOrUri(view, Request.createFromId(photoId, hires, darkTheme,
                    defaultProvider));
        }
    }

    @Override
    public void loadPhoto(ImageView view, Uri photoUri, boolean hires, boolean darkTheme,
            DefaultImageProvider defaultProvider) {
        if (photoUri == null) {
            // No photo is needed
            defaultProvider.applyDefaultImage(view, hires, darkTheme);
            mPendingRequests.remove(view);
        } else {
            if (DEBUG) Log.d(TAG, "loadPhoto request: " + photoUri);
            loadPhotoByIdOrUri(view, Request.createFromUri(photoUri, hires, darkTheme,
                    defaultProvider));
        }
    }

    private void loadPhotoByIdOrUri(ImageView view, Request request) {
        boolean loaded = loadCachedPhoto(view, request);
        if (loaded) {
            mPendingRequests.remove(view);
        } else {
            mPendingRequests.put(view, request);
            if (!mPaused) {
                // Send a request to start loading photos
                requestLoading();
            }
        }
    }

    @Override
    public void removePhoto(ImageView view) {
        view.setImageDrawable(null);
        mPendingRequests.remove(view);
    }

    @Override
    public void refreshCache() {
        if (DEBUG) Log.d(TAG, "refreshCache");
        for (BitmapHolder holder : mBitmapHolderCache.snapshot().values()) {
            holder.fresh = false;
        }
    }

    /**
     * Checks if the photo is present in cache.  If so, sets the photo on the view.
     *
     * @return false if the photo needs to be (re)loaded from the provider.
     */
    private boolean loadCachedPhoto(ImageView view, Request request) {
        BitmapHolder holder = mBitmapHolderCache.get(request.getKey());
        if (holder == null) {
            // The bitmap has not been loaded - should display the placeholder image.
            request.applyDefaultImage(view);
            return false;
        }

        if (holder.bytes == null) {
            request.applyDefaultImage(view);
            return holder.fresh;
        }

        // Optionally decode bytes into a bitmap
        inflateBitmap(holder);

        view.setImageBitmap(holder.bitmap);

        if (holder.bitmap != null) {
            // Put the bitmap in the LRU cache
            mBitmapCache.put(request, holder.bitmap);
        }

        // Soften the reference
        holder.bitmap = null;

        return holder.fresh;
    }

    /**
     * If necessary, decodes bytes stored in the holder to Bitmap.  As long as the
     * bitmap is held either by {@link #mBitmapCache} or by a soft reference in
     * the holder, it will not be necessary to decode the bitmap.
     */
    private static void inflateBitmap(BitmapHolder holder) {
        byte[] bytes = holder.bytes;
        if (bytes == null || bytes.length == 0) {
            return;
        }

        // Check the soft reference.  If will be retained if the bitmap is also
        // in the LRU cache, so we don't need to check the LRU cache explicitly.
        if (holder.bitmapRef != null) {
            holder.bitmap = holder.bitmapRef.get();
            if (holder.bitmap != null) {
                return;
            }
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
            holder.bitmap = bitmap;
            holder.bitmapRef = new SoftReference<Bitmap>(bitmap);
            if (DEBUG) {
                Log.d(TAG, "inflateBitmap " + btk(bytes.length) + " -> "
                        + bitmap.getWidth() + "x" + bitmap.getHeight()
                        + ", " + btk(bitmap.getByteCount()));
            }
        } catch (OutOfMemoryError e) {
            // Do nothing - the photo will appear to be missing
        }
    }

    public void clear() {
        if (DEBUG) Log.d(TAG, "clear");
        mPendingRequests.clear();
        mBitmapHolderCache.evictAll();
        mBitmapCache.evictAll();
    }

    @Override
    public void pause() {
        mPaused = true;
    }

    @Override
    public void resume() {
        mPaused = false;
        if (DEBUG) dumpStats();
        if (!mPendingRequests.isEmpty()) {
            requestLoading();
        }
    }

    /**
     * Sends a message to this thread itself to start loading images.  If the current
     * view contains multiple image views, all of those image views will get a chance
     * to request their respective photos before any of those requests are executed.
     * This allows us to load images in bulk.
     */
    private void requestLoading() {
        if (!mLoadingRequested) {
            mLoadingRequested = true;
            mMainThreadHandler.sendEmptyMessage(MESSAGE_REQUEST_LOADING);
        }
    }

    /**
     * Processes requests on the main thread.
     */
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_REQUEST_LOADING: {
                mLoadingRequested = false;
                if (!mPaused) {
                    ensureLoaderThread();
                    mLoaderThread.requestLoading();
                }
                return true;
            }

            case MESSAGE_PHOTOS_LOADED: {
                if (!mPaused) {
                    processLoadedImages();
                }
                if (DEBUG) dumpStats();
                return true;
            }
        }
        return false;
    }

    public void ensureLoaderThread() {
        if (mLoaderThread == null) {
            mLoaderThread = new LoaderThread(mContext.getContentResolver());
            mLoaderThread.start();
        }
    }

    /**
     * Goes over pending loading requests and displays loaded photos.  If some of the
     * photos still haven't been loaded, sends another request for image loading.
     */
    private void processLoadedImages() {
        Iterator<ImageView> iterator = mPendingRequests.keySet().iterator();
        while (iterator.hasNext()) {
            ImageView view = iterator.next();
            Request key = mPendingRequests.get(view);
            boolean loaded = loadCachedPhoto(view, key);
            if (loaded) {
                iterator.remove();
            }
        }

        softenCache();

        if (!mPendingRequests.isEmpty()) {
            requestLoading();
        }
    }

    /**
     * Removes strong references to loaded bitmaps to allow them to be garbage collected
     * if needed.  Some of the bitmaps will still be retained by {@link #mBitmapCache}.
     */
    private void softenCache() {
        for (BitmapHolder holder : mBitmapHolderCache.snapshot().values()) {
            holder.bitmap = null;
        }
    }

    /**
     * Stores the supplied bitmap in cache.
     */
    private void cacheBitmap(Object key, byte[] bytes, boolean preloading) {
        if (DEBUG) {
            BitmapHolder prev = mBitmapHolderCache.get(key);
            if (prev != null && prev.bytes != null) {
                Log.d(TAG, "Overwriting cache: key=" + key + (prev.fresh ? " FRESH" : " stale"));
                if (prev.fresh) {
                    mFreshCacheOverwrite.incrementAndGet();
                } else {
                    mStaleCacheOverwrite.incrementAndGet();
                }
            }
            Log.d(TAG, "Caching data: key=" + key + ", " + btk(bytes.length));
        }
        BitmapHolder holder = new BitmapHolder(bytes);
        holder.fresh = true;

        // Unless this image is being preloaded, decode it right away while
        // we are still on the background thread.
        if (!preloading) {
            inflateBitmap(holder);
        }

        mBitmapHolderCache.put(key, holder);
    }

    /**
     * Populates an array of photo IDs that need to be loaded.
     */
    private void obtainPhotoIdsAndUrisToLoad(Set<Long> photoIds,
            Set<String> photoIdsAsStrings, Set<Uri> uris) {
        photoIds.clear();
        photoIdsAsStrings.clear();
        uris.clear();

        /*
         * Since the call is made from the loader thread, the map could be
         * changing during the iteration. That's not really a problem:
         * ConcurrentHashMap will allow those changes to happen without throwing
         * exceptions. Since we may miss some requests in the situation of
         * concurrent change, we will need to check the map again once loading
         * is complete.
         */
        Iterator<Request> iterator = mPendingRequests.values().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            BitmapHolder holder = mBitmapHolderCache.get(request);
            if (holder == null || !holder.fresh) {
                if (request.isUriRequest()) {
                    uris.add(request.mUri);
                } else {
                    photoIds.add(request.mId);
                    photoIdsAsStrings.add(String.valueOf(request.mId));
                }
            }
        }
    }

    /**
     * The thread that performs loading of photos from the database.
     */
    private class LoaderThread extends HandlerThread implements Callback {
        private static final int BUFFER_SIZE = 1024*16;
        private static final int MESSAGE_PRELOAD_PHOTOS = 0;
        private static final int MESSAGE_LOAD_PHOTOS = 1;

        /**
         * A pause between preload batches that yields to the UI thread.
         */
        private static final int PHOTO_PRELOAD_DELAY = 1000;

        /**
         * Number of photos to preload per batch.
         */
        private static final int PRELOAD_BATCH = 25;

        /**
         * Maximum number of photos to preload.  If the cache size is 2Mb and
         * the expected average size of a photo is 4kb, then this number should be 2Mb/4kb = 500.
         */
        private static final int MAX_PHOTOS_TO_PRELOAD = 100;

        private final ContentResolver mResolver;
        private final StringBuilder mStringBuilder = new StringBuilder();
        private final Set<Long> mPhotoIds = Sets.newHashSet();
        private final Set<String> mPhotoIdsAsStrings = Sets.newHashSet();
        private final Set<Uri> mPhotoUris = Sets.newHashSet();
        private final List<Long> mPreloadPhotoIds = Lists.newArrayList();

        private Handler mLoaderThreadHandler;
        private byte mBuffer[];

        private static final int PRELOAD_STATUS_NOT_STARTED = 0;
        private static final int PRELOAD_STATUS_IN_PROGRESS = 1;
        private static final int PRELOAD_STATUS_DONE = 2;

        private int mPreloadStatus = PRELOAD_STATUS_NOT_STARTED;

        public LoaderThread(ContentResolver resolver) {
            super(LOADER_THREAD_NAME);
            mResolver = resolver;
        }

        public void ensureHandler() {
            if (mLoaderThreadHandler == null) {
                mLoaderThreadHandler = new Handler(getLooper(), this);
            }
        }

        /**
         * Kicks off preloading of the next batch of photos on the background thread.
         * Preloading will happen after a delay: we want to yield to the UI thread
         * as much as possible.
         * <p>
         * If preloading is already complete, does nothing.
         */
        public void requestPreloading() {
            if (mPreloadStatus == PRELOAD_STATUS_DONE) {
                return;
            }

            ensureHandler();
            if (mLoaderThreadHandler.hasMessages(MESSAGE_LOAD_PHOTOS)) {
                return;
            }

            mLoaderThreadHandler.sendEmptyMessageDelayed(
                    MESSAGE_PRELOAD_PHOTOS, PHOTO_PRELOAD_DELAY);
        }

        /**
         * Sends a message to this thread to load requested photos.  Cancels a preloading
         * request, if any: we don't want preloading to impede loading of the photos
         * we need to display now.
         */
        public void requestLoading() {
            ensureHandler();
            mLoaderThreadHandler.removeMessages(MESSAGE_PRELOAD_PHOTOS);
            mLoaderThreadHandler.sendEmptyMessage(MESSAGE_LOAD_PHOTOS);
        }

        /**
         * Receives the above message, loads photos and then sends a message
         * to the main thread to process them.
         */
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_PRELOAD_PHOTOS:
                    preloadPhotosInBackground();
                    break;
                case MESSAGE_LOAD_PHOTOS:
                    loadPhotosInBackground();
                    break;
            }
            return true;
        }

        /**
         * The first time it is called, figures out which photos need to be preloaded.
         * Each subsequent call preloads the next batch of photos and requests
         * another cycle of preloading after a delay.  The whole process ends when
         * we either run out of photos to preload or fill up cache.
         */
        private void preloadPhotosInBackground() {
            if (mPreloadStatus == PRELOAD_STATUS_DONE) {
                return;
            }

            if (mPreloadStatus == PRELOAD_STATUS_NOT_STARTED) {
                queryPhotosForPreload();
                if (mPreloadPhotoIds.isEmpty()) {
                    mPreloadStatus = PRELOAD_STATUS_DONE;
                } else {
                    mPreloadStatus = PRELOAD_STATUS_IN_PROGRESS;
                }
                requestPreloading();
                return;
            }

            if (mBitmapHolderCache.size() > mBitmapHolderCacheRedZoneBytes) {
                mPreloadStatus = PRELOAD_STATUS_DONE;
                return;
            }

            mPhotoIds.clear();
            mPhotoIdsAsStrings.clear();

            int count = 0;
            int preloadSize = mPreloadPhotoIds.size();
            while(preloadSize > 0 && mPhotoIds.size() < PRELOAD_BATCH) {
                preloadSize--;
                count++;
                Long photoId = mPreloadPhotoIds.get(preloadSize);
                mPhotoIds.add(photoId);
                mPhotoIdsAsStrings.add(photoId.toString());
                mPreloadPhotoIds.remove(preloadSize);
            }

            loadPhotosFromDatabase(true);

            if (preloadSize == 0) {
                mPreloadStatus = PRELOAD_STATUS_DONE;
            }

            Log.v(TAG, "Preloaded " + count + " photos.  Cached bytes: "
                    + mBitmapHolderCache.size());

            requestPreloading();
        }

        private void queryPhotosForPreload() {
            Cursor cursor = null;
            try {
                Uri uri = Contacts.CONTENT_URI.buildUpon().appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                                String.valueOf(MAX_PHOTOS_TO_PRELOAD))
                        .build();
                cursor = mResolver.query(uri, new String[] { Contacts.PHOTO_ID },
                        Contacts.PHOTO_ID + " NOT NULL AND " + Contacts.PHOTO_ID + "!=0",
                        null,
                        Contacts.STARRED + " DESC, " + Contacts.LAST_TIME_CONTACTED + " DESC");

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        // Insert them in reverse order, because we will be taking
                        // them from the end of the list for loading.
                        mPreloadPhotoIds.add(0, cursor.getLong(0));
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        private void loadPhotosInBackground() {
            obtainPhotoIdsAndUrisToLoad(mPhotoIds, mPhotoIdsAsStrings, mPhotoUris);
            loadPhotosFromDatabase(false);
            loadRemotePhotos();
            requestPreloading();
        }

        private void loadPhotosFromDatabase(boolean preloading) {
            if (mPhotoIds.isEmpty()) {
                return;
            }

            // Remove loaded photos from the preload queue: we don't want
            // the preloading process to load them again.
            if (!preloading && mPreloadStatus == PRELOAD_STATUS_IN_PROGRESS) {
                for (Long id : mPhotoIds) {
                    mPreloadPhotoIds.remove(id);
                }
                if (mPreloadPhotoIds.isEmpty()) {
                    mPreloadStatus = PRELOAD_STATUS_DONE;
                }
            }

            mStringBuilder.setLength(0);
            mStringBuilder.append(Photo._ID + " IN(");
            for (int i = 0; i < mPhotoIds.size(); i++) {
                if (i != 0) {
                    mStringBuilder.append(',');
                }
                mStringBuilder.append('?');
            }
            mStringBuilder.append(')');

            Cursor cursor = null;
            try {
                if (DEBUG) Log.d(TAG, "Loading " + TextUtils.join(",", mPhotoIdsAsStrings));
                cursor = mResolver.query(Data.CONTENT_URI,
                        COLUMNS,
                        mStringBuilder.toString(),
                        mPhotoIdsAsStrings.toArray(EMPTY_STRING_ARRAY),
                        null);

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        Long id = cursor.getLong(0);
                        byte[] bytes = cursor.getBlob(1);
                        cacheBitmap(id, bytes, preloading);
                        mPhotoIds.remove(id);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            // Remaining photos were not found in the contacts database (but might be in profile).
            for (Long id : mPhotoIds) {
                if (ContactsContract.isProfileId(id)) {
                    Cursor profileCursor = null;
                    try {
                        profileCursor = mResolver.query(
                                ContentUris.withAppendedId(Data.CONTENT_URI, id),
                                COLUMNS, null, null, null);
                        if (profileCursor != null && profileCursor.moveToFirst()) {
                            cacheBitmap(profileCursor.getLong(0), profileCursor.getBlob(1),
                                    preloading);
                        } else {
                            // Couldn't load a photo this way either.
                            cacheBitmap(id, null, preloading);
                        }
                    } finally {
                        if (profileCursor != null) {
                            profileCursor.close();
                        }
                    }
                } else {
                    // Not a profile photo and not found - mark the cache accordingly
                    cacheBitmap(id, null, preloading);
                }
            }

            mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
        }

        private void loadRemotePhotos() {
            for (Uri uri : mPhotoUris) {
                if (mBuffer == null) {
                    mBuffer = new byte[BUFFER_SIZE];
                }
                try {
                    if (DEBUG) Log.d(TAG, "Loading " + uri);
                    InputStream is = mResolver.openInputStream(uri);
                    if (is != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try {
                            int size;
                            while ((size = is.read(mBuffer)) != -1) {
                                baos.write(mBuffer, 0, size);
                            }
                        } finally {
                            is.close();
                        }
                        cacheBitmap(uri, baos.toByteArray(), false);
                        mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
                    } else {
                        Log.v(TAG, "Cannot load photo " + uri);
                        cacheBitmap(uri, null, false);
                    }
                } catch (Exception ex) {
                    Log.v(TAG, "Cannot load photo " + uri, ex);
                    cacheBitmap(uri, null, false);
                }
            }
        }
    }

    /**
     * A holder for either a Uri or an id and a flag whether this was requested for the dark or
     * light theme
     */
    private static final class Request {
        private final long mId;
        private final Uri mUri;
        private final boolean mDarkTheme;
        private final boolean mHires;
        private final DefaultImageProvider mDefaultProvider;

        private Request(long id, Uri uri, boolean hires, boolean darkTheme,
                DefaultImageProvider defaultProvider) {
            mId = id;
            mUri = uri;
            mDarkTheme = darkTheme;
            mHires = hires;
            mDefaultProvider = defaultProvider;
        }

        public static Request createFromId(long id, boolean hires, boolean darkTheme,
                DefaultImageProvider defaultProvider) {
            return new Request(id, null /* no URI */, hires, darkTheme, defaultProvider);
        }

        public static Request createFromUri(Uri uri, boolean hires, boolean darkTheme,
                DefaultImageProvider defaultProvider) {
            return new Request(0 /* no ID */, uri, hires, darkTheme, defaultProvider);
        }

        public boolean isDarkTheme() {
            return mDarkTheme;
        }

        public boolean isHires() {
            return mHires;
        }

        public boolean isUriRequest() {
            return mUri != null;
        }

        @Override
        public int hashCode() {
            if (mUri != null) return mUri.hashCode();

            // copied over from Long.hashCode()
            return (int) (mId ^ (mId >>> 32));
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Request)) return false;
            final Request that = (Request) o;
            // Don't compare equality of mHires and mDarkTheme fields because these are only used
            // in the default contact photo case. When the contact does have a photo, the contact
            // photo is the same regardless of mHires and mDarkTheme, so we shouldn't need to put
            // the photo request on the queue twice.
            return mId == that.mId && UriUtils.areEqual(mUri, that.mUri);
        }

        public Object getKey() {
            return mUri == null ? mId : mUri;
        }

        public void applyDefaultImage(ImageView view) {
            mDefaultProvider.applyDefaultImage(view, mHires, mDarkTheme);
        }
    }
}
