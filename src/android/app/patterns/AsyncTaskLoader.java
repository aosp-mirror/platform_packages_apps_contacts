/*
 * Copyright (C) 2010 The Android Open Source Project.
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

package android.app.patterns;

import android.content.Context;
import android.os.AsyncTask;

/**
 * Abstract Loader that provides an {@link AsyncTask} to do the work.
 * 
 * @param <D> the data type to be loaded.
 */
public abstract class AsyncTaskLoader<D> extends Loader<D> {
    final class LoadListTask extends AsyncTask<Void, Void, D> {
        /* Runs on a worker thread */
        @Override
        protected D doInBackground(Void... params) {
            return AsyncTaskLoader.this.loadInBackground();
        }

        /* Runs on the UI thread */
        @Override
        protected void onPostExecute(D data) {
            AsyncTaskLoader.this.onLoadComplete(data);
        }
    }

    public AsyncTaskLoader(Context context) {
        super(context);
    }

    /**
     * Called on a worker thread to perform the actual load. Implementions should not deliver the
     * results directly, but should return them from this this method and deliver them from
     * {@link #onPostExecute()}
     *
     * @return the result of the load
     */
    protected abstract D loadInBackground();

    /**
     * Called on the UI thread with the result of the load.
     *
     * @param data the result of the load
     */
    protected abstract void onLoadComplete(D data);
}
