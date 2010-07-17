// Copyright 2010 Google Inc. All Rights Reserved.

package com.android.contacts.widget;

import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.os.Bundle;

import java.util.LinkedHashMap;

import junit.framework.Assert;

/**
 * A {@link LoaderManager} that performs synchronous loading on demand for unit
 * testing.
 */
public class TestLoaderManager implements LoaderManager {

    // Using a linked hash map to get all loading done in a predictable order.
    private LinkedHashMap<Integer, Loader<?>> mStartedLoaders = new LinkedHashMap<
            Integer, Loader<?>>();

    @Override
    @SuppressWarnings("unchecked")
    public <D> Loader<D> getLoader(int id) {
        return (Loader<D>)mStartedLoaders.get(id);
    }

    @Override
    public <D> Loader<D> initLoader(int id, Bundle args, final LoaderCallbacks<D> callbacks) {
        Loader<D> loader = callbacks.onCreateLoader(id, args);
        loader.registerListener(id, new OnLoadCompleteListener<D>() {
            @Override
            public void onLoadComplete(Loader<D> loader, D data) {
                callbacks.onLoadFinished(loader, data);
            }
        });

        mStartedLoaders.put(id, loader);
        return loader;
    }

    @Override
    public <D> Loader<D> restartLoader(int id, Bundle args, LoaderCallbacks<D> callbacks) {
        return initLoader(id, args, callbacks);
    }

    @Override
    public void stopLoader(int id) {
        mStartedLoaders.get(id).stopLoading();
    }

    /**
     * Synchronously runs all started loaders.
     */
    public void executeLoaders() {
        for (Loader<?> loader : mStartedLoaders.values()) {
            executeLoader(loader);
        }
    }

    /**
     * Synchronously runs the specified loader.
     */
    public void executeLoader(int id) {
        Loader<?> loader = mStartedLoaders.get(id);
        if (loader == null) {
            Assert.fail("Loader not started: " + id);
        }
        executeLoader(loader);
    }

    @SuppressWarnings("unchecked")
    private <D> void executeLoader(final Loader<D> loader) {
        if (loader instanceof AsyncTaskLoader) {
            AsyncTaskLoader<D> atLoader = (AsyncTaskLoader<D>)loader;
            D data = atLoader.loadInBackground();
            loader.deliverResult(data);
        } else {
            loader.forceLoad();
        }
    }
}
