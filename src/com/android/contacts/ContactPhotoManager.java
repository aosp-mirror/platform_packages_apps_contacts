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

import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
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
import android.util.TypedValue;
import android.widget.ImageView;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.util.BitmapUtil;
import com.android.contacts.util.MemoryUtils;
import com.android.contacts.util.UriUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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
    static final boolean DEBUG_SIZES = false; // Don't submit with true

    /** Caches 180dip in pixel. This is used to detect whether to show the hires or lores version
     * of the default avatar */
    private static int s180DipInPixel = -1;

    public static final String CONTACT_PHOTO_SERVICE = "contactPhotos";

    /**
     * Returns the resource id of the default avatar. Tries to find a resource that is bigger
     * than the given extent (width or height). If extent=-1, a thumbnail avatar is returned
     */
    public static int getDefaultAvatarResId(Context context, int extent, boolean darkTheme) {
        // TODO: Is it worth finding a nicer way to do hires/lores here? In practice, the
        // default avatar doesn't look too different when stretched
        if (s180DipInPixel == -1) {
            Resources r = context.getResources();
            s180DipInPixel = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 180,
                    r.getDisplayMetrics());
        }

        final boolean hires = (extent != -1) && (extent > s180DipInPixel);
        return getDefaultAvatarResId(hires, darkTheme);
    }

    public static int getDefaultAvatarResId(boolean hires, boolean darkTheme) {
        if (hires && darkTheme) return R.drawable.ic_contact_picture_180_holo_dark;
        if (hires) return R.drawable.ic_contact_picture_180_holo_light;
        if (darkTheme) return R.drawable.ic_contact_picture_holo_dark;
        return R.drawable.ic_contact_picture_holo_light;
    }

    public static abstract class DefaultImageProvider {
        /**
         * Applies the default avatar to the ImageView. Extent is an indicator for the size (width
         * or height). If darkTheme is set, the avatar is one that looks better on dark background
         */
        public abstract void applyDefaultImage(ImageView view, int extent, boolean darkTheme);
    }

    private static class AvatarDefaultImageProvider extends DefaultImageProvider {
        @Override
        public void applyDefaultImage(ImageView view, int extent, boolean darkTheme) {
            view.setImageResource(getDefaultAvatarResId(view.getContext(), extent, darkTheme));
        }
    }

    private static class BlankDefaultImageProvider extends DefaultImageProvider {
        private static Drawable sDrawable;

        @Override
        public void applyDefaultImage(ImageView view, int extent, boolean darkTheme) {
            if (sDrawable == null) {
                Context context = view.getContext();
                sDrawable = new ColorDrawable(context.getResources().getColor(
                        R.color.image_placeholder));
            }
            view.setImageDrawable(sDrawable);
        }
    }

    public static final DefaultImageProvider DEFAULT_AVATAR = new AvatarDefaultImageProvider();

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
     * Load thumbnail image into the supplied image view. If the photo is already cached,
     * it is displayed immediately.  Otherwise a request is sent to load the photo
     * from the database.
     */
    public abstract void loadThumbnail(ImageView view, long photoId, boolean darkTheme,
            DefaultImageProvider defaultProvider);

    /**
     * Calls {@link #loadThumbnail(ImageView, long, boolean, DefaultImageProvider)} with
     * {@link #DEFAULT_AVATAR}.
     */
    public final void loadThumbnail(ImageView view, long photoId, boolean darkTheme) {
        loadThumbnail(view, photoId, darkTheme, DEFAULT_AVATAR);
    }

    /**
     * Load photo into the supplied image view. If the photo is already cached,
     * it is displayed immediately. Otherwise a request is sent to load the photo
     * from the location specified by the URI.
     * @param view The target view
     * @param photoUri The uri of the photo to load
     * @param requestedExtent Specifies an approximate Max(width, height) of the targetView.
     * This is useful if the source image can be a lot bigger that the target, so that the decoding
     * is done using efficient sampling. If requestedExtent is specified, no sampling of the image
     * is performed
     * @param darkTheme Whether the background is dark. This is used for default avatars
     * @param defaultProvider The provider of default avatars (this is used if photoUri doesn't
     * refer to an existing image)
     */
    public abstract void loadPhoto(ImageView view, Uri photoUri, int requestedExtent,
            boolean darkTheme, DefaultImageProvider defaultProvider);

    /**
     * Calls {@link #loadPhoto(ImageView, Uri, boolean, boolean, DefaultImageProvider)} with
     * {@link #DEFAULT_AVATAR}.
     */
    public final void loadPhoto(ImageView view, Uri photoUri, int requestedExtent,
            boolean darkTheme) {
        loadPhoto(view, photoUri, requestedExtent, darkTheme, DEFAULT_AVATAR);
    }

    /**
     * Calls {@link #loadPhoto(ImageView, Uri, boolean, boolean, DefaultImageProvider)} with
     * {@link #DEFAULT_AVATAR} and with the assumption, that the image is a thumbnail
     */
    public final void loadDirectoryPhoto(ImageView view, Uri photoUri, boolean darkTheme) {
        loadPhoto(view, photoUri, -1, darkTheme, DEFAULT_AVATAR);
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
     * Stores the given bitmap directly in the LRU bitmap cache.
     * @param photoUri The URI of the photo (for future requests).
     * @param bitmap The bitmap.
     * @param photoBytes The bytes that were parsed to create the bitmap.
     */
    public abstract void cacheBitmap(Uri photoUri, Bitmap bitmap, byte[] photoBytes);

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

    private static final int FADE_TRANSITION_DURATION = 200;

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
        final int originalSmallerExtent;

        volatile boolean fresh;
        Bitmap bitmap;
        Reference<Bitmap> bitmapRef;
        int decodedSampleSize;

        public BitmapHolder(byte[] bytes, int originalSmallerExtent) {
            this.bytes = bytes;
            this.fresh = true;
            this.originalSmallerExtent = originalSmallerExtent;
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
     * {@code true} if ALL entries in {@link #mBitmapHolderCache} are NOT fresh.
     */
    private volatile boolean mBitmapHolderCacheAllUnfresh = true;

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
    public void loadThumbnail(ImageView view, long photoId, boolean darkTheme,
            DefaultImageProvider defaultProvider) {
        if (photoId == 0) {
            // No photo is needed
            defaultProvider.applyDefaultImage(view, -1, darkTheme);
            mPendingRequests.remove(view);
        } else {
            if (DEBUG) Log.d(TAG, "loadPhoto request: " + photoId);
            loadPhotoByIdOrUri(view, Request.createFromThumbnailId(photoId, darkTheme,
                    defaultProvider));
        }
    }

    @Override
    public void loadPhoto(ImageView view, Uri photoUri, int requestedExtent, boolean darkTheme,
            DefaultImageProvider defaultProvider) {
        if (photoUri == null) {
            // No photo is needed
            defaultProvider.applyDefaultImage(view, requestedExtent, darkTheme);
            mPendingRequests.remove(view);
        } else {
            if (DEBUG) Log.d(TAG, "loadPhoto request: " + photoUri);
            loadPhotoByIdOrUri(view, Request.createFromUri(photoUri, requestedExtent, darkTheme,
                    defaultProvider));
        }
    }

    private void loadPhotoByIdOrUri(ImageView view, Request request) {
        boolean loaded = loadCachedPhoto(view, request, false);
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
        if (mBitmapHolderCacheAllUnfresh) {
            if (DEBUG) Log.d(TAG, "refreshCache -- no fresh entries.");
            return;
        }
        if (DEBUG) Log.d(TAG, "refreshCache");
        mBitmapHolderCacheAllUnfresh = true;
        for (BitmapHolder holder : mBitmapHolderCache.snapshot().values()) {
            holder.fresh = false;
        }
    }

    /**
     * Checks if the photo is present in cache.  If so, sets the photo on the view.
     *
     * @return false if the photo needs to be (re)loaded from the provider.
     */
    private boolean loadCachedPhoto(ImageView view, Request request, boolean fadeIn) {
        BitmapHolder holder = mBitmapHolderCache.get(request.getKey());
        if (holder == null) {
            // The bitmap has not been loaded ==> show default avatar
            request.applyDefaultImage(view);
            return false;
        }

        if (holder.bytes == null) {
            request.applyDefaultImage(view);
            return holder.fresh;
        }

        Bitmap cachedBitmap = holder.bitmapRef == null ? null : holder.bitmapRef.get();
        if (cachedBitmap == null) {
            if (holder.bytes.length < 8 * 1024) {
                // Small thumbnails are usually quick to inflate. Let's do that on the UI thread
                inflateBitmap(holder, request.getRequestedExtent());
                cachedBitmap = holder.bitmap;
                if (cachedBitmap == null) return false;
            } else {
                // This is bigger data. Let's send that back to the Loader so that we can
                // inflate this in the background
                request.applyDefaultImage(view);
                return false;
            }
        }

        final Drawable previousDrawable = view.getDrawable();
        if (fadeIn && previousDrawable != null) {
            final Drawable[] layers = new Drawable[2];
            // Prevent cascade of TransitionDrawables.
            if (previousDrawable instanceof TransitionDrawable) {
                final TransitionDrawable previousTransitionDrawable =
                        (TransitionDrawable) previousDrawable;
                layers[0] = previousTransitionDrawable.getDrawable(
                        previousTransitionDrawable.getNumberOfLayers() - 1);
            } else {
                layers[0] = previousDrawable;
            }
            layers[1] = new BitmapDrawable(mContext.getResources(), cachedBitmap);
            TransitionDrawable drawable = new TransitionDrawable(layers);
            view.setImageDrawable(drawable);
            drawable.startTransition(FADE_TRANSITION_DURATION);
        } else {
            view.setImageBitmap(cachedBitmap);
        }

        // Put the bitmap in the LRU cache. But only do this for images that are small enough
        // (we require that at least six of those can be cached at the same time)
        if (cachedBitmap.getByteCount() < mBitmapCache.maxSize() / 6) {
            mBitmapCache.put(request.getKey(), cachedBitmap);
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
    private static void inflateBitmap(BitmapHolder holder, int requestedExtent) {
        final int sampleSize =
                BitmapUtil.findOptimalSampleSize(holder.originalSmallerExtent, requestedExtent);
        byte[] bytes = holder.bytes;
        if (bytes == null || bytes.length == 0) {
            return;
        }

        if (sampleSize == holder.decodedSampleSize) {
            // Check the soft reference.  If will be retained if the bitmap is also
            // in the LRU cache, so we don't need to check the LRU cache explicitly.
            if (holder.bitmapRef != null) {
                holder.bitmap = holder.bitmapRef.get();
                if (holder.bitmap != null) {
                    return;
                }
            }
        }

        try {
            Bitmap bitmap = BitmapUtil.decodeBitmapFromBytes(bytes, sampleSize);

            // make bitmap mutable and draw size onto it
            if (DEBUG_SIZES) {
                Bitmap original = bitmap;
                bitmap = bitmap.copy(bitmap.getConfig(), true);
                original.recycle();
                Canvas canvas = new Canvas(bitmap);
                Paint paint = new Paint();
                paint.setTextSize(16);
                paint.setColor(Color.BLUE);
                paint.setStyle(Style.FILL);
                canvas.drawRect(0.0f, 0.0f, 50.0f, 20.0f, paint);
                paint.setColor(Color.WHITE);
                paint.setAntiAlias(true);
                canvas.drawText(bitmap.getWidth() + "/" + sampleSize, 0, 15, paint);
            }

            holder.decodedSampleSize = sampleSize;
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
            boolean loaded = loadCachedPhoto(view, key, true);
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
    private void cacheBitmap(Object key, byte[] bytes, boolean preloading, int requestedExtent) {
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
            Log.d(TAG, "Caching data: key=" + key + ", " +
                    (bytes == null ? "<null>" : btk(bytes.length)));
        }
        BitmapHolder holder = new BitmapHolder(bytes,
                bytes == null ? -1 : BitmapUtil.getSmallerExtentFromBytes(bytes));

        // Unless this image is being preloaded, decode it right away while
        // we are still on the background thread.
        if (!preloading) {
            inflateBitmap(holder, requestedExtent);
        }

        mBitmapHolderCache.put(key, holder);
        mBitmapHolderCacheAllUnfresh = false;
    }

    @Override
    public void cacheBitmap(Uri photoUri, Bitmap bitmap, byte[] photoBytes) {
        final int smallerExtent = Math.min(bitmap.getWidth(), bitmap.getHeight());
        // We can pretend here that the extent of the photo was the size that we originally
        // requested
        Request request = Request.createFromUri(photoUri, smallerExtent, false, DEFAULT_AVATAR);
        BitmapHolder holder = new BitmapHolder(photoBytes, smallerExtent);
        holder.bitmapRef = new SoftReference<Bitmap>(bitmap);
        mBitmapHolderCache.put(request.getKey(), holder);
        mBitmapHolderCacheAllUnfresh = false;
        mBitmapCache.put(request.getKey(), bitmap);
    }

    /**
     * Populates an array of photo IDs that need to be loaded. Also decodes bitmaps that we have
     * already loaded
     */
    private void obtainPhotoIdsAndUrisToLoad(Set<Long> photoIds,
            Set<String> photoIdsAsStrings, Set<Request> uris) {
        photoIds.clear();
        photoIdsAsStrings.clear();
        uris.clear();

        boolean jpegsDecoded = false;

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
            final BitmapHolder holder = mBitmapHolderCache.get(request.getKey());
            if (holder != null && holder.bytes != null && holder.fresh &&
                    (holder.bitmapRef == null || holder.bitmapRef.get() == null)) {
                // This was previously loaded but we don't currently have the inflated Bitmap
                inflateBitmap(holder, request.getRequestedExtent());
                jpegsDecoded = true;
            } else {
                if (holder == null || !holder.fresh) {
                    if (request.isUriRequest()) {
                        uris.add(request);
                    } else {
                        photoIds.add(request.getId());
                        photoIdsAsStrings.add(String.valueOf(request.mId));
                    }
                }
            }
        }

        if (jpegsDecoded) mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
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
        private final Set<Request> mPhotoUris = Sets.newHashSet();
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

            loadThumbnails(true);

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
            loadThumbnails(false);
            loadUriBasedPhotos();
            requestPreloading();
        }

        /** Loads thumbnail photos with ids */
        private void loadThumbnails(boolean preloading) {
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
                        cacheBitmap(id, bytes, preloading, -1);
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
                                    preloading, -1);
                        } else {
                            // Couldn't load a photo this way either.
                            cacheBitmap(id, null, preloading, -1);
                        }
                    } finally {
                        if (profileCursor != null) {
                            profileCursor.close();
                        }
                    }
                } else {
                    // Not a profile photo and not found - mark the cache accordingly
                    cacheBitmap(id, null, preloading, -1);
                }
            }

            mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
        }

        /**
         * Loads photos referenced with Uris. Those can be remote thumbnails
         * (from directory searches), display photos etc
         */
        private void loadUriBasedPhotos() {
            for (Request uriRequest : mPhotoUris) {
                Uri uri = uriRequest.getUri();
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
                        cacheBitmap(uri, baos.toByteArray(), false,
                                uriRequest.getRequestedExtent());
                        mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
                    } else {
                        Log.v(TAG, "Cannot load photo " + uri);
                        cacheBitmap(uri, null, false, uriRequest.getRequestedExtent());
                    }
                } catch (Exception ex) {
                    Log.v(TAG, "Cannot load photo " + uri, ex);
                    cacheBitmap(uri, null, false, uriRequest.getRequestedExtent());
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
        private final int mRequestedExtent;
        private final DefaultImageProvider mDefaultProvider;

        private Request(long id, Uri uri, int requestedExtent, boolean darkTheme,
                DefaultImageProvider defaultProvider) {
            mId = id;
            mUri = uri;
            mDarkTheme = darkTheme;
            mRequestedExtent = requestedExtent;
            mDefaultProvider = defaultProvider;
        }

        public static Request createFromThumbnailId(long id, boolean darkTheme,
                DefaultImageProvider defaultProvider) {
            return new Request(id, null /* no URI */, -1, darkTheme, defaultProvider);
        }

        public static Request createFromUri(Uri uri, int requestedExtent, boolean darkTheme,
                DefaultImageProvider defaultProvider) {
            return new Request(0 /* no ID */, uri, requestedExtent, darkTheme, defaultProvider);
        }

        public boolean isUriRequest() {
            return mUri != null;
        }

        public Uri getUri() {
            return mUri;
        }

        public long getId() {
            return mId;
        }

        public int getRequestedExtent() {
            return mRequestedExtent;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (mId ^ (mId >>> 32));
            result = prime * result + mRequestedExtent;
            result = prime * result + ((mUri == null) ? 0 : mUri.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final Request that = (Request) obj;
            if (mId != that.mId) return false;
            if (mRequestedExtent != that.mRequestedExtent) return false;
            if (!UriUtils.areEqual(mUri, that.mUri)) return false;
            // Don't compare equality of mDarkTheme because it is only used in the default contact
            // photo case. When the contact does have a photo, the contact photo is the same
            // regardless of mDarkTheme, so we shouldn't need to put the photo request on the queue
            // twice.
            return true;
        }

        public Object getKey() {
            return mUri == null ? mId : mUri;
        }

        public void applyDefaultImage(ImageView view) {
            mDefaultProvider.applyDefaultImage(view, mRequestedExtent, mDarkTheme);
        }
    }
}
