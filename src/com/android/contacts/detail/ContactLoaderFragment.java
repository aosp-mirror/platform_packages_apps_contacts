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

package com.android.contacts.detail;

import com.android.contacts.ContactLoader;
import com.android.contacts.R;
import com.android.internal.util.Objects;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * This is an invisible worker {@link Fragment} that loads the contact details for the contact card.
 * The data is then passed to the listener, who can then pass the data to other {@link View}s.
 */
public class ContactLoaderFragment extends Fragment {

    private static final String TAG = ContactLoaderFragment.class.getSimpleName();

    /**
     * This is a listener to the {@link ContactLoaderFragment} and will be notified when the
     * contact details have finished loading.
     */
    public static interface ContactLoaderFragmentListener {
        public void onDetailsLoaded(ContactLoader.Result result);
    }

    private static final int LOADER_DETAILS = 1;

    private static final String KEY_CONTACT_URI = "contactUri";
    private static final String LOADER_ARG_CONTACT_URI = "contactUri";

    private Context mContext;
    private Uri mLookupUri;
    private ContactLoaderFragmentListener mListener;

    private ContactLoader.Result mContactData;

    public ContactLoaderFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLookupUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_CONTACT_URI, mLookupUri);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        // This is an empty view that is set to visibility gone.
        return inflater.inflate(R.layout.contact_detail_loader_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mLookupUri != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().initLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    public void loadUri(Uri lookupUri) {
        if (Objects.equal(lookupUri, mLookupUri)) {
            // Same URI, no need to load the data again
            return;
        }

        mLookupUri = lookupUri;
        if (mLookupUri == null) {
            getLoaderManager().destroyLoader(LOADER_DETAILS);
            mContactData = null;
            if (mListener != null) {
                mListener.onDetailsLoaded(mContactData);
            }
        } else if (getActivity() != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().restartLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    public void setListener(ContactLoaderFragmentListener value) {
        mListener = value;
    }

    /**
     * The listener for the detail loader
     */
    private final LoaderManager.LoaderCallbacks<ContactLoader.Result> mDetailLoaderListener =
            new LoaderCallbacks<ContactLoader.Result>() {
        @Override
        public Loader<ContactLoader.Result> onCreateLoader(int id, Bundle args) {
            Uri lookupUri = args.getParcelable(LOADER_ARG_CONTACT_URI);
            return new ContactLoader(mContext, lookupUri, true /* loadGroupMetaData */);
        }

        @Override
        public void onLoadFinished(Loader<ContactLoader.Result> loader, ContactLoader.Result data) {
            if (!mLookupUri.equals(data.getUri())) {
                return;
            }

            if (data != ContactLoader.Result.NOT_FOUND && data != ContactLoader.Result.ERROR) {
                mContactData = data;
            } else {
                Log.i(TAG, "No contact found: " + ((ContactLoader)loader).getLookupUri());
                mContactData = null;
            }

            if (mListener != null) {
                mListener.onDetailsLoaded(mContactData);
            }
        }

        public void onLoaderReset(Loader<ContactLoader.Result> loader) {
            mContactData = null;
            if (mListener != null) {
                mListener.onDetailsLoaded(mContactData);
            }
        }
    };
}
