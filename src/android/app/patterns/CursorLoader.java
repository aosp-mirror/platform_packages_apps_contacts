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
import android.database.Cursor;
import android.net.Uri;

public class CursorLoader extends AsyncTaskLoader<Cursor> {
    Cursor mCursor;
    ForceLoadContentObserver mObserver;
    boolean mStopped;
    Uri mUri;
    String[] mProjection;
    String mSelection;
    String[] mSelectionArgs;
    String mSortOrder;

    /* Runs on a worker thread */
    @Override
    protected Cursor loadInBackground() {
        Cursor cursor = getContext().getContentResolver().query(mUri, mProjection, mSelection,
                mSelectionArgs, mSortOrder);
        // Ensure the cursor window is filled
        if (cursor != null) {
            cursor.getCount();
            cursor.registerContentObserver(mObserver);
        }
        return cursor;
    }

    /* Runs on the UI thread */
    @Override
    protected void onLoadComplete(Cursor cursor) {
        if (mStopped) {
            // An async query came in while the loader is stopped
            cursor.close();
            return;
        }
        mCursor = cursor;
        deliverResult(cursor);
    }

    public CursorLoader(Context context, Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        super(context);
        mObserver = new ForceLoadContentObserver();
        mUri = uri;
        mProjection = projection;
        mSelection = selection;
        mSelectionArgs = selectionArgs;
        mSortOrder = sortOrder;
    }

    /**
     * Starts an asynchronous load of the contacts list data. When the result is ready the callbacks
     * will be called on the UI thread. If a previous load has been completed and is still valid
     * the result may be passed to the callbacks immediately. 
     *
     * Must be called from the UI thread
     */
    @Override
    public void startLoading() {
        mStopped = false;

        if (mCursor != null) {
            deliverResult(mCursor);
        } else {
            forceLoad();
        }
    }

    /**
     * Force an asynchronous load. Unlike {@link #startLoading()} this will ignore a previously
     * loaded data set and load a new one.
     */
    @Override
    public void forceLoad() {
        new LoadListTask().execute((Void[]) null);
    }

    /**
     * Must be called from the UI thread
     */
    @Override
    public void stopLoading() {
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
            mCursor = null;
        }

        // Make sure that any outstanding loads clean themselves up properly
        mStopped = true;
    }

    @Override
    public void destroy() {
        // Ensure the loader is stopped
        stopLoading();
    }
}
