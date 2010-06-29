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
package com.android.contacts.widget;

import android.app.LoaderManagingFragment;
import android.content.Loader;
import android.os.Bundle;

/**
 * A modification of the {@link LoaderManagingFragment} class that supports testing of
 * loader-based fragments using synchronous data loading.
 */
public abstract class InstrumentedLoaderManagingFragment<D> extends LoaderManagingFragment<D> {

    public interface Delegate<D> {
        void onStartLoading(Loader<D> loader);
    }

    private Delegate<D> mDelegate;

    public void setDelegate(Delegate<D> listener) {
        this.mDelegate = listener;
    }

    @Override
    public Loader<D> startLoading(int id, Bundle args) {
        if (mDelegate != null) {
            Loader<D> loader = onCreateLoader(id, args);
            loader.registerListener(id, this);
            mDelegate.onStartLoading(loader);
            return loader;
        } else {
            return super.startLoading(id, args);
        }
    }
}
