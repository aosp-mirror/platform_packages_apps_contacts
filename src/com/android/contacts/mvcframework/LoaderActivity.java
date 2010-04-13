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

package com.android.contacts.mvcframework;


import android.app.Activity;
import android.os.Bundle;

import java.util.HashMap;

/**
 * The idea here was to abstract the generic life cycle junk needed to properly keep loaders going.
 * It didn't work out as-is because registering the callbacks post config change didn't work.
 */
public abstract class LoaderActivity<D> extends Activity implements
        Loader.OnLoadCompleteListener<D> {
    static final class LoaderInfo {
        public Bundle args;
        public Loader loader;
    }
    private HashMap<Integer, LoaderInfo> mLoaders;

    /**
     * Registers a loader with this activity, registers the callbacks on it, and starts it loading.
     */
    protected void startLoading(int id, Bundle args) {
        LoaderInfo info = mLoaders.get(id);
        Loader loader;
        if (info != null) {
            loader = info.loader;
            if (loader != null) {
                loader.unregisterListener(this);
                loader.destroy();
                info.loader = null;
            }
        } else {
            info = new LoaderInfo();
            info.args = args;
        }
        mLoaders.put(id, info);
        loader = onCreateLoader(id, args);
        loader.registerListener(id, this);
        loader.startLoading();
        info.loader = loader;
    }

    protected abstract Loader onCreateLoader(int id, Bundle args);
    protected abstract void onInitializeLoaders();

    public abstract void onLoadComplete(int id, D data);

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
            } else {
                loader.registerListener(id, this);
            }
            loader.startLoading();
        }
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
