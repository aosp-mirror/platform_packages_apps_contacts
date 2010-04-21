/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.app.patterns;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;

public abstract class Loader<D> {
    private int mId;
    private OnLoadCompleteListener<D> mListener;
    private Context mContext;

    protected final class ForceLoadContentObserver extends ContentObserver {
        public ForceLoadContentObserver() {
            super(new Handler());
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            forceLoad();
        }
    }

    public interface OnLoadCompleteListener<D> {
        /**
         * Called on the thread that created the Loader when the load is complete.
         *
         * @param loader the loader that completed the load
         * @param data the result of the load
         */
        public void onLoadComplete(Loader loader, D data);
    }

    /**
     * Sends the result of the load to the register listener.
     *
     * @param data the result of the load
     */
    protected void deliverResult(D data) {
        if (mListener != null) {
            mListener.onLoadComplete(this, data);
        }
    }

    /**
     * Stores away the application context associated with context. Since Loaders can be used
     * across multiple activities it's dangerous to store the context directly.
     *
     * @param context used to retrieve the application context.
     */
    public Loader(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * @return an application context retrieved from the Context passed to the constructor.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * @return the ID of this loader
     */
    public int getId() {
        return mId;
    }

    /**
     * Registers a class that will receive callbacks when a load is complete. The callbacks will
     * be called on the UI thread so it's safe to pass the results to widgets.
     * 
     * Must be called from the UI thread
     */
    public void registerListener(int id, OnLoadCompleteListener<D> listener) {
        if (mListener != null) {
            throw new IllegalStateException("There is already a listener registered");
        }
        mListener = listener;
        mId = id;
    }

    /**
     * Must be called from the UI thread
     */
    public void unregisterListener(OnLoadCompleteListener<D> listener) {
        if (mListener == null) {
            throw new IllegalStateException("No listener register");
        }
        if (mListener != listener) {
            throw new IllegalArgumentException("Attempting to unregister the wrong listener");
        }
        mListener = null;
    }

    /**
     * Starts an asynchronous load of the contacts list data. When the result is ready the callbacks
     * will be called on the UI thread. If a previous load has been completed and is still valid
     * the result may be passed to the callbacks immediately. The loader will monitor the source of
     * the data set and may deliver future callbacks if the source changes. Calling
     * {@link #stopLoading} will stop the delivery of callbacks. 
     *
     * Must be called from the UI thread
     */
    public abstract void startLoading();

    /**
     * Force an asynchronous load. Unlike {@link #startLoading()} this will ignore a previously
     * loaded data set and load a new one.
     */
    public abstract void forceLoad();

    /**
     * Stops delivery of updates until the next time {@link #startLoading()} is called
     *
     * Must be called from the UI thread
     */
    public abstract void stopLoading();

    /**
     * Destroys the loader and frees it's resources, making it unusable.
     *
     * Must be called from the UI thread
     */
    public abstract void destroy();
}