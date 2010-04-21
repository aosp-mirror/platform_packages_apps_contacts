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


import android.app.Activity;
import android.os.Bundle;

import java.util.HashMap;

/**
 * The idea here was to abstract the generic life cycle junk needed to properly keep loaders going.
 * It didn't work out as-is because registering the callbacks post config change didn't work.
 */
public abstract class LoaderActivity<D> extends Activity implements
        Loader.OnLoadCompleteListener<D> {
    private boolean mStarted = false;

    static final class LoaderInfo {
        public Bundle args;
        public Loader loader;
    }
    private HashMap<Integer, LoaderInfo> mLoaders;
    private HashMap<Integer, LoaderInfo> mInactiveLoaders;

    /**
     * Registers a loader with this activity, registers the callbacks on it, and starts it loading.
     * If a loader with the same id has previously been started it will automatically be destroyed
     * when the new loader completes it's work. The callback will be delivered before the old loader
     * is destroyed.
     */
    protected void startLoading(int id, Bundle args) {
        LoaderInfo info = mLoaders.get(id);
        if (info != null) {
            // Keep track of the previous instance of this loader so we can destroy
            // it when the new one completes.
            mInactiveLoaders.put(id, info);
        }
        info = new LoaderInfo();
        info.args = args;
        mLoaders.put(id, info);
        Loader loader = onCreateLoader(id, args);
        info.loader = loader;
        if (mStarted) {
            // The activity will start all existing loaders in it's onStart(), so only start them
            // here if we're past that point of the activitiy's life cycle
            loader.registerListener(id, this);
            loader.startLoading();
        }
    }

    protected abstract Loader onCreateLoader(int id, Bundle args);
    protected abstract void onInitializeLoaders();
    protected abstract void onLoadFinished(Loader loader, D data);

    public final void onLoadComplete(Loader loader, D data) {
        // Notify of the new data so the app can switch out the old data before
        // we try to destroy it.
        onLoadFinished(loader, data);

        // Look for an inactive loader and destroy it if found
        int id = loader.getId();
        LoaderInfo info = mInactiveLoaders.get(id);
        if (info != null) {
            Loader oldLoader = info.loader;
            if (oldLoader != null) {
                oldLoader.destroy();
            }
            mInactiveLoaders.remove(id);
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (mLoaders == null) {
            // Look for a passed along loader and create a new one if it's not there
            mLoaders = (HashMap<Integer, LoaderInfo>) getLastNonConfigurationInstance();
            if (mLoaders == null) {
                mLoaders = new HashMap<Integer, LoaderInfo>();
                onInitializeLoaders();
            }
        }
        if (mInactiveLoaders == null) {
            mInactiveLoaders = new HashMap<Integer, LoaderInfo>();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        // Call out to sub classes so they can start their loaders
        // Let the existing loaders know that we want to be notified when a load is complete
        for (HashMap.Entry<Integer, LoaderInfo> entry : mLoaders.entrySet()) {
            LoaderInfo info = entry.getValue();
            Loader loader = info.loader;
            int id = entry.getKey();
            if (loader == null) {
               loader = onCreateLoader(id, info.args);
               info.loader = loader;
            }
            loader.registerListener(id, this);
            loader.startLoading();
        }

        mStarted = true;
    }

    @Override
    public void onStop() {
        super.onStop();

        for (HashMap.Entry<Integer, LoaderInfo> entry : mLoaders.entrySet()) {
            LoaderInfo info = entry.getValue();
            Loader loader = info.loader;
            if (loader == null) {
                continue;
            }

            // Let the loader know we're done with it
            loader.unregisterListener(this);

            // The loader isn't getting passed along to the next instance so ask it to stop loading
//            if (!isChangingConfigurations()) {
//                loader.stopLoading();
//            }
        }

        mStarted = false;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Pass the loader along to the next guy
        Object result = mLoaders;
        mLoaders = null;
        return result;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mLoaders != null) {
            for (HashMap.Entry<Integer, LoaderInfo> entry : mLoaders.entrySet()) {
                LoaderInfo info = entry.getValue();
                Loader loader = info.loader;
                if (loader == null) {
                    continue;
                }
                loader.destroy();
            }
        }
    }
}
