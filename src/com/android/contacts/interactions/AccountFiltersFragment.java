/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.interactions;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Loader;
import android.os.Bundle;

import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.util.AccountFilterUtil;

import java.util.List;

/**
 * Loads account filters.
 */
public class AccountFiltersFragment extends Fragment {

    private static final int LOADER_FILTERS = 1;

    /**
     * Callbacks for hosts of the {@link AccountFiltersFragment}.
     */
    public interface AccountFiltersListener  {

        /**
         * Invoked after account filters have been loaded.
         */
        void onFiltersLoaded(List<ContactListFilter> accountFilterItems);
    }

    private final LoaderManager.LoaderCallbacks<List<ContactListFilter>> mFiltersLoaderListener =
            new LoaderManager.LoaderCallbacks<List<ContactListFilter>> () {
                @Override
                public Loader<List<ContactListFilter>> onCreateLoader(int id, Bundle args) {
                    return new AccountFilterUtil.FilterLoader(getActivity());
                }

                @Override
                public void onLoadFinished(
                        Loader<List<ContactListFilter>> loader, List<ContactListFilter> data) {
                    if (mListener != null) {
                        mListener.onFiltersLoaded(data);
                    }
                }

                public void onLoaderReset(Loader<List<ContactListFilter>> loader) {
                }
            };

    private AccountFiltersListener mListener;

    @Override
    public void onStart() {
        getLoaderManager().initLoader(LOADER_FILTERS, null, mFiltersLoaderListener);
        super.onStart();
    }

    public void setListener(AccountFiltersListener listener) {
        mListener = listener;
    }
}
