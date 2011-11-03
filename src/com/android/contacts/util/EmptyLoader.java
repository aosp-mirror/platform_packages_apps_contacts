/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.util;

import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;

/**
 * A {@link Loader} only used to make use of the {@link android.app.Fragment#setStartDeferred}
 * feature from an old-style fragment which doesn't use {@link Loader}s to load data.
 *
 * This loader never delivers results.  A caller fragment must destroy it when deferred fragments
 * should be started.
 */
public class EmptyLoader extends Loader<Object> {
    public EmptyLoader(Context context) {
        super(context);
    }

    /**
     * {@link LoaderCallbacks} which just generates {@link EmptyLoader}.  {@link #onLoadFinished}
     * and {@link #onLoaderReset} are no-op.
     */
    public static class Callback implements LoaderCallbacks<Object> {
        private final Context mContext;

        public Callback(Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public Loader<Object> onCreateLoader(int id, Bundle args) {
            return new EmptyLoader(mContext);
        }

        @Override
        public void onLoadFinished(Loader<Object> loader, Object data) {
        }

        @Override
        public void onLoaderReset(Loader<Object> loader) {
        }
    }
}
