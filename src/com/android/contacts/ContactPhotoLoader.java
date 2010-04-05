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

import com.google.android.collect.Lists;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Handler.Callback;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Contacts.Photo;
import android.widget.ImageView;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronously loads contact photos and maintains cache of photos.  The class is
 * mostly single-threaded.  The only two methods accessed by the loader thread are
 * {@link #cacheBitmap} and {@link #obtainPhotoIdsToLoad}. Those methods access concurrent
 * hash maps shared with the main thread.
 */
public class ContactPhotoLoader implements Callback {

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

    private final String[] COLUMNS = new String[] { Photo._ID, Photo.PHOTO };

    /**
     * The resource ID of the image to be used when the photo is unavailable or being
     * loaded.
     */
    private final int mDefaultResourceId;

    /**
     * Maintains the state of a particular photo.
     */
    private static class BitmapHolder {
        private static final int NEEDED = 0;
        private static final int LOADING = 1;
        private static final int LOADED = 2;

        int state;
        SoftReference<Bitmap> bitmapRef;
    }

    /**
     * A soft cache for photos.
     */
    private final ConcurrentHashMap<Long, BitmapHolder> mBitmapCache =
            new ConcurrentHashMap<Long, BitmapHolder>();

    /**
     * A map from ImageView to the corresponding photo ID. Please note that this
     * photo ID may change before the photo loading request is started.
     */
    private final ConcurrentHashMap<ImageView, Long> mPendingRequests =
            new ConcurrentHashMap<ImageView, Long>();

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

    private final Context mContext;

    /**
     * Constructor.
     *
     * @param context content context
     * @param defaultResourceId the image resource ID to be used when there is
     *            no photo for a contact
     */
    public ContactPhotoLoader(Context context, int defaultResourceId) {
        mDefaultResourceId = defaultResourceId;
        mContext = context;
    }

    /**
     * Load photo into the supplied image view.  If the photo is already cached,
     * it is displayed immediately.  Otherwise a request is sent to load the photo
     * from the database.
     */
    public void loadPhoto(ImageView view, long photoId) {
        if (photoId == 0) {
            // No photo is needed
            view.setImageResource(mDefaultResourceId);
            mPendingRequests.remove(view);
        } else {
            boolean loaded = loadCachedPhoto(view, photoId);
            if (loaded) {
                mPendingRequests.remove(view);
            } else {
                mPendingRequests.put(view, photoId);
                if (!mPaused) {
                    // Send a request to start loading photos
                    requestLoading();
                }
            }
        }
    }

    /**
     * Checks if the photo is present in cache.  If so, sets the photo on the view,
     * otherwise sets the state of the photo to {@link BitmapHolder#NEEDED} and
     * temporarily set the image to the default resource ID.
     */
    private boolean loadCachedPhoto(ImageView view, long photoId) {
        BitmapHolder holder = mBitmapCache.get(photoId);
        if (holder == null) {
            holder = new BitmapHolder();
            mBitmapCache.put(photoId, holder);
        } else if (holder.state == BitmapHolder.LOADED) {
            // Null bitmap reference means that database contains no bytes for the photo
            if (holder.bitmapRef == null) {
                view.setImageResource(mDefaultResourceId);
                return true;
            }

            Bitmap bitmap = holder.bitmapRef.get();
            if (bitmap != null) {
                view.setImageBitmap(bitmap);
                return true;
            }

            // Null bitmap means that the soft reference was released by the GC
            // and we need to reload the photo.
            holder.bitmapRef = null;
        }

        // The bitmap has not been loaded - should display the placeholder image.
        view.setImageResource(mDefaultResourceId);
        holder.state = BitmapHolder.NEEDED;
        return false;
    }

    /**
     * Stops loading images, kills the image loader thread and clears all caches.
     */
    public void stop() {
        pause();

        if (mLoaderThread != null) {
            mLoaderThread.quit();
            mLoaderThread = null;
        }

        mPendingRequests.clear();
        mBitmapCache.clear();
    }

    public void clear() {
        mPendingRequests.clear();
        mBitmapCache.clear();
    }

    /**
     * Temporarily stops loading photos from the database.
     */
    public void pause() {
        mPaused = true;
    }

    /**
     * Resumes loading photos from the database.
     */
    public void resume() {
        mPaused = false;
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
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_REQUEST_LOADING: {
                mLoadingRequested = false;
                if (!mPaused) {
                    if (mLoaderThread == null) {
                        mLoaderThread = new LoaderThread(mContext.getContentResolver());
                        mLoaderThread.start();
                    }

                    mLoaderThread.requestLoading();
                }
                return true;
            }

            case MESSAGE_PHOTOS_LOADED: {
                if (!mPaused) {
                    processLoadedImages();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Goes over pending loading requests and displays loaded photos.  If some of the
     * photos still haven't been loaded, sends another request for image loading.
     */
    private void processLoadedImages() {
        Iterator<ImageView> iterator = mPendingRequests.keySet().iterator();
        while (iterator.hasNext()) {
            ImageView view = iterator.next();
            long photoId = mPendingRequests.get(view);
            boolean loaded = loadCachedPhoto(view, photoId);
            if (loaded) {
                iterator.remove();
            }
        }

        if (!mPendingRequests.isEmpty()) {
            requestLoading();
        }
    }

    /**
     * Stores the supplied bitmap in cache.
     */
    private void cacheBitmap(long id, byte[] bytes) {
        if (mPaused) {
            return;
        }

        BitmapHolder holder = new BitmapHolder();
        holder.state = BitmapHolder.LOADED;
        if (bytes != null) {
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                holder.bitmapRef = new SoftReference<Bitmap>(bitmap);
            } catch (OutOfMemoryError e) {
                // Do nothing - the photo will appear to be missing
            }
        }
        mBitmapCache.put(id, holder);
    }

    /**
     * Populates an array of photo IDs that need to be loaded.
     */
    private void obtainPhotoIdsToLoad(ArrayList<Long> photoIds,
            ArrayList<String> photoIdsAsStrings) {
        photoIds.clear();
        photoIdsAsStrings.clear();

        /*
         * Since the call is made from the loader thread, the map could be
         * changing during the iteration. That's not really a problem:
         * ConcurrentHashMap will allow those changes to happen without throwing
         * exceptions. Since we may miss some requests in the situation of
         * concurrent change, we will need to check the map again once loading
         * is complete.
         */
        Iterator<Long> iterator = mPendingRequests.values().iterator();
        while (iterator.hasNext()) {
            Long id = iterator.next();
            BitmapHolder holder = mBitmapCache.get(id);
            if (holder != null && holder.state == BitmapHolder.NEEDED) {
                // Assuming atomic behavior
                holder.state = BitmapHolder.LOADING;
                photoIds.add(id);
                photoIdsAsStrings.add(id.toString());
            }
        }
    }

    /**
     * The thread that performs loading of photos from the database.
     */
    private class LoaderThread extends HandlerThread implements Callback {
        private final ContentResolver mResolver;
        private final StringBuilder mStringBuilder = new StringBuilder();
        private final ArrayList<Long> mPhotoIds = Lists.newArrayList();
        private final ArrayList<String> mPhotoIdsAsStrings = Lists.newArrayList();
        private Handler mLoaderThreadHandler;

        public LoaderThread(ContentResolver resolver) {
            super(LOADER_THREAD_NAME);
            mResolver = resolver;
        }

        /**
         * Sends a message to this thread to load requested photos.
         */
        public void requestLoading() {
            if (mLoaderThreadHandler == null) {
                mLoaderThreadHandler = new Handler(getLooper(), this);
            }
            mLoaderThreadHandler.sendEmptyMessage(0);
        }

        /**
         * Receives the above message, loads photos and then sends a message
         * to the main thread to process them.
         */
        public boolean handleMessage(Message msg) {
            loadPhotosFromDatabase();
            mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
            return true;
        }

        private void loadPhotosFromDatabase() {
            obtainPhotoIdsToLoad(mPhotoIds, mPhotoIdsAsStrings);

            int count = mPhotoIds.size();
            if (count == 0) {
                return;
            }

            mStringBuilder.setLength(0);
            mStringBuilder.append(Photo._ID + " IN(");
            for (int i = 0; i < count; i++) {
                if (i != 0) {
                    mStringBuilder.append(',');
                }
                mStringBuilder.append('?');
            }
            mStringBuilder.append(')');

            Cursor cursor = null;
            try {
                cursor = mResolver.query(Data.CONTENT_URI,
                        COLUMNS,
                        mStringBuilder.toString(),
                        mPhotoIdsAsStrings.toArray(EMPTY_STRING_ARRAY),
                        null);

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        Long id = cursor.getLong(0);
                        byte[] bytes = cursor.getBlob(1);
                        cacheBitmap(id, bytes);
                        mPhotoIds.remove(id);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            // Remaining photos were not found in the database - mark the cache accordingly.
            count = mPhotoIds.size();
            for (int i = 0; i < count; i++) {
                cacheBitmap(mPhotoIds.get(i), null);
            }
        }
    }
}
