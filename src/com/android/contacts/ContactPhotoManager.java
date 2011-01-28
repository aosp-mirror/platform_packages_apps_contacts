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
import com.google.android.collect.Lists;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.ContactsContract.Contacts.Photo;
import android.provider.ContactsContract.Data;
import android.util.Log;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronously loads contact photos and maintains a cache of photos.
 */
public abstract class ContactPhotoManager {

    static final String TAG = "ContactPhotoManager";

    public static final String CONTACT_PHOTO_SERVICE = "contactPhotos";

    /**
     * The resource ID of the image to be used when the photo is unavailable or being
     * loaded.
     */
    protected final int mDefaultResourceId = R.drawable.ic_contact_picture;

    /**
     * Requests the singleton instance of {@link AccountTypeManager} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static ContactPhotoManager getInstance(Context context) {
        ContactPhotoManager service =
                (ContactPhotoManager) context.getSystemService(CONTACT_PHOTO_SERVICE);
        if (service == null) {
            service = createContactPhotoManager(context);
            Log.e(TAG, "No contact photo service in context: " + context);
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
    public abstract void loadPhoto(ImageView view, long photoId);

    /**
     * Load photo into the supplied image view.  If the photo is already cached,
     * it is displayed immediately.  Otherwise a request is sent to load the photo
     * from the location specified by the URI.
     */
    public abstract void loadPhoto(ImageView view, Uri photoUri);

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

    private final String[] COLUMNS = new String[] { Photo._ID, Photo.PHOTO };

    /**
     * Maintains the state of a particular photo.
     */
    private static class BitmapHolder {
        private static final int NEEDED = 0;
        private static final int LOADING = 1;
        private static final int LOADED = 2;
        private static final int LOADED_NEEDS_RELOAD = 3;

        int state;
        Bitmap bitmap;
        SoftReference<Bitmap> bitmapRef;
    }

    private final Context mContext;

    /**
     * A soft cache for photos.
     */
    private final ConcurrentHashMap<Object, BitmapHolder> mBitmapCache =
            new ConcurrentHashMap<Object, BitmapHolder>();

    /**
     * A map from ImageView to the corresponding photo ID. Please note that this
     * photo ID may change before the photo loading request is started.
     */
    private final ConcurrentHashMap<ImageView, Object> mPendingRequests =
            new ConcurrentHashMap<ImageView, Object>();

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

    public ContactPhotoManagerImpl(Context context) {
        mContext = context;
    }

    @Override
    public void loadPhoto(ImageView view, long photoId) {
        if (photoId == 0) {
            // No photo is needed
            view.setImageResource(mDefaultResourceId);
            mPendingRequests.remove(view);
        } else {
            loadPhotoByIdOrUri(view, photoId);
        }
    }

    @Override
    public void loadPhoto(ImageView view, Uri photoUri) {
        if (photoUri == null) {
            // No photo is needed
            view.setImageResource(mDefaultResourceId);
            mPendingRequests.remove(view);
        } else {
            loadPhotoByIdOrUri(view, photoUri);
        }
    }

    private void loadPhotoByIdOrUri(ImageView view, Object key) {
        boolean loaded = loadCachedPhoto(view, key);
        if (loaded) {
            mPendingRequests.remove(view);
        } else {
            mPendingRequests.put(view, key);
            if (!mPaused) {
                // Send a request to start loading photos
                requestLoading();
            }
        }
    }

    @Override
    public void refreshCache() {
        for (BitmapHolder holder : mBitmapCache.values()) {
            if (holder.state == BitmapHolder.LOADED) {
                holder.state = BitmapHolder.LOADED_NEEDS_RELOAD;
            }
        }
    }

    /**
     * Checks if the photo is present in cache.  If so, sets the photo on the view,
     * otherwise sets the state of the photo to {@link BitmapHolder#NEEDED} and
     * temporarily set the image to the default resource ID.
     */
    private boolean loadCachedPhoto(ImageView view, Object key) {
        BitmapHolder holder = mBitmapCache.get(key);
        if (holder == null) {
            holder = new BitmapHolder();
            mBitmapCache.put(key, holder);
        } else {
            boolean loaded = (holder.state == BitmapHolder.LOADED);
            boolean loadedNeedsReload = (holder.state == BitmapHolder.LOADED_NEEDS_RELOAD);
            if (loadedNeedsReload) {
                holder.state = BitmapHolder.NEEDED;
            }

            // Null bitmap reference means that database contains no bytes for the photo
            if ((loaded || loadedNeedsReload) && holder.bitmapRef == null) {
                view.setImageResource(mDefaultResourceId);
                return loaded;
            }

            if (holder.bitmapRef != null) {
                Bitmap bitmap = holder.bitmapRef.get();
                if (bitmap != null) {
                    view.setImageBitmap(bitmap);
                    return loaded;
                }

                // Null bitmap means that the soft reference was released by the GC
                // and we need to reload the photo.
                holder.bitmapRef = null;
            }
        }

        // The bitmap has not been loaded - should display the placeholder image.
        view.setImageResource(mDefaultResourceId);
        holder.state = BitmapHolder.NEEDED;
        return false;
    }

    public void clear() {
        mPendingRequests.clear();
        mBitmapCache.clear();
    }

    @Override
    public void pause() {
        mPaused = true;
    }

    @Override
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
            Object key = mPendingRequests.get(view);
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
     * if needed.
     */
    private void softenCache() {
        for (BitmapHolder holder : mBitmapCache.values()) {
            holder.bitmap = null;
        }
    }

    /**
     * Stores the supplied bitmap in cache.
     */
    private void cacheBitmap(Object key, byte[] bytes) {
        if (mPaused) {
            return;
        }

        BitmapHolder holder = new BitmapHolder();
        holder.state = BitmapHolder.LOADED;
        if (bytes != null) {
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                holder.bitmap = bitmap;
                holder.bitmapRef = new SoftReference<Bitmap>(bitmap);
            } catch (OutOfMemoryError e) {
                // Do nothing - the photo will appear to be missing
            }
        }
        mBitmapCache.put(key, holder);
    }

    /**
     * Populates an array of photo IDs that need to be loaded.
     */
    private void obtainPhotoIdsAndUrisToLoad(ArrayList<Long> photoIds,
            ArrayList<String> photoIdsAsStrings, ArrayList<Uri> uris) {
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
        Iterator<Object> iterator = mPendingRequests.values().iterator();
        while (iterator.hasNext()) {
            Object key = iterator.next();
            BitmapHolder holder = mBitmapCache.get(key);
            if (holder != null && holder.state == BitmapHolder.NEEDED) {
                // Assuming atomic behavior
                holder.state = BitmapHolder.LOADING;
                if (key instanceof Long) {
                    photoIds.add((Long)key);
                    photoIdsAsStrings.add(key.toString());
                } else {
                    uris.add((Uri)key);
                }
            }
        }
    }

    /**
     * The thread that performs loading of photos from the database.
     */
    private class LoaderThread extends HandlerThread implements Callback {
        private static final int BUFFER_SIZE = 1024*16;

        private final ContentResolver mResolver;
        private final StringBuilder mStringBuilder = new StringBuilder();
        private final ArrayList<Long> mPhotoIds = Lists.newArrayList();
        private final ArrayList<String> mPhotoIdsAsStrings = Lists.newArrayList();
        private final ArrayList<Uri> mPhotoUris = Lists.newArrayList();
        private Handler mLoaderThreadHandler;
        private byte mBuffer[];

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
            return true;
        }

        private void loadPhotosFromDatabase() {
            obtainPhotoIdsAndUrisToLoad(mPhotoIds, mPhotoIdsAsStrings, mPhotoUris);

            int count = mPhotoIds.size();
            if (count != 0) {
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
                mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
            }

            count = mPhotoUris.size();
            for (int i = 0; i < count; i++) {
                Uri uri = mPhotoUris.get(i);
                if (mBuffer == null) {
                    mBuffer = new byte[BUFFER_SIZE];
                }
                try {
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
                        cacheBitmap(uri, baos.toByteArray());
                        mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
                    } else {
                        Log.v(TAG, "Cannot load photo " + uri);
                        cacheBitmap(uri, null);
                    }
                } catch (Exception ex) {
                    Log.v(TAG, "Cannot load photo " + uri, ex);
                    cacheBitmap(uri, null);
                }
            }
        }
    }
}
