// Copyright 2010 Google Inc. All Rights Reserved.

package com.android.contacts.widget;

import android.content.AsyncTaskLoader;
import android.content.Loader;

import java.util.LinkedHashMap;

import junit.framework.Assert;

/**
 * A delegate of {@link InstrumentedLoaderManagingFragment} that performs
 * synchronous loading on demand for unit testing.
 */
public class LoaderManagingFragmentTestDelegate<D> implements
        InstrumentedLoaderManagingFragment.Delegate<D> {

    // Using a linked hash map to get all loading done in a predictable order.
    private LinkedHashMap<Integer, Loader<D>> mStartedLoaders =
            new LinkedHashMap<Integer, Loader<D>>();

    public void onStartLoading(Loader<D> loader) {
        int id = loader.getId();
        mStartedLoaders.put(id, loader);
    }

    /**
     * Synchronously runs all started loaders.
     */
    public void executeLoaders() {
        for (Loader<D> loader : mStartedLoaders.values()) {
            executeLoader(loader);
        }
    }

    /**
     * Synchronously runs the specified loader.
     */
    public void executeLoader(int id) {
        Loader<D> loader = mStartedLoaders.get(id);
        if (loader == null) {
            Assert.fail("Loader not started: " + id);
        }
        executeLoader(loader);
    }

    private void executeLoader(Loader<D> loader) {
        if (loader instanceof AsyncTaskLoader) {
            AsyncTaskLoader<D> atLoader = (AsyncTaskLoader<D>)loader;
            D data = atLoader.loadInBackground();
            atLoader.deliverResult(data);
        } else {
            loader.forceLoad();
        }
    }
}