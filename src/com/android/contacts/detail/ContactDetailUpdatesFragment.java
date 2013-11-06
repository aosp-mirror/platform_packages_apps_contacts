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

import android.app.ListFragment;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.StreamItems;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;

import com.android.contacts.R;
import com.android.contacts.activities.ContactDetailActivity.FragmentKeyListener;
import com.android.contacts.detail.ContactDetailDisplayUtils.StreamPhotoTag;
import com.android.contacts.util.StreamItemEntry;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.account.AccountType;

public class ContactDetailUpdatesFragment extends ListFragment implements FragmentKeyListener {

    private static final String TAG = "ContactDetailUpdatesFragment";

    private Contact mContactData;
    private Uri mLookupUri;

    private LayoutInflater mInflater;
    private StreamItemAdapter mStreamItemAdapter;

    private OnScrollListener mVerticalScrollListener;

    /**
     * Listener on clicks on a stream item.
     * <p>
     * It assumes the view has a tag of type {@link StreamItemEntry} associated with it.
     */
    private final View.OnClickListener mStreamItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            StreamItemEntry streamItemEntry = (StreamItemEntry) view.getTag();
            if (streamItemEntry == null) {
                // Ignore if this item does not have a stream item associated with it.
                return;
            }
            final AccountType accountType = getAccountTypeForStreamItemEntry(streamItemEntry);

            final Uri uri = ContentUris.withAppendedId(StreamItems.CONTENT_URI,
                    streamItemEntry.getId());
            final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setClassName(accountType.syncAdapterPackageName,
                    accountType.getViewStreamItemActivity());
            startActivity(intent);
        }
    };

    private final View.OnClickListener mStreamItemPhotoItemClickListener
            = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            StreamPhotoTag tag = (StreamPhotoTag) view.getTag();
            if (tag == null) {
                return;
            }
            final AccountType accountType = getAccountTypeForStreamItemEntry(tag.streamItem);

            final Intent intent = new Intent(Intent.ACTION_VIEW, tag.getStreamItemPhotoUri());
            intent.setClassName(accountType.syncAdapterPackageName,
                    accountType.getViewStreamItemPhotoActivity());
            startActivity(intent);
        }
    };

    private AccountType getAccountTypeForStreamItemEntry(StreamItemEntry streamItemEntry) {
        return AccountTypeManager.getInstance(getActivity()).getAccountType(
                streamItemEntry.getAccountType(), streamItemEntry.getDataSet());
    }

    public ContactDetailUpdatesFragment() {
        // Explicit constructor for inflation
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mInflater = inflater;
        return mInflater.inflate(R.layout.contact_detail_updates_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStreamItemAdapter = new StreamItemAdapter(getActivity(), mStreamItemClickListener,
                mStreamItemPhotoItemClickListener);
        setListAdapter(mStreamItemAdapter);
        getListView().setOnScrollListener(mVerticalScrollListener);
    }

    public void setData(Uri lookupUri, Contact result) {
        if (result == null) {
            return;
        }
        mLookupUri = lookupUri;
        mContactData = result;
    }

    /**
     * Reset the list adapter in this {@link Fragment} to get rid of any saved scroll position
     * from a previous contact.
     */
    public void resetAdapter() {
        setListAdapter(mStreamItemAdapter);
    }

    @Override
    public boolean handleKeyDown(int keyCode) {
        return false;
    }

    public void setVerticalScrollListener(OnScrollListener listener) {
        mVerticalScrollListener = listener;
    }

    /**
     * Returns the top coordinate of the first item in the {@link ListView}. If the first item
     * in the {@link ListView} is not visible or there are no children in the list, then return
     * Integer.MIN_VALUE. Note that the returned value will be <= 0 because the first item in the
     * list cannot have a positive offset.
     */
    public int getFirstListItemOffset() {
        return ContactDetailDisplayUtils.getFirstListItemOffset(getListView());
    }

    /**
     * Tries to scroll the first item to the given offset (this can be a no-op if the list is
     * already in the correct position).
     * @param offset which should be <= 0
     */
    public void requestToMoveToOffset(int offset) {
        ContactDetailDisplayUtils.requestToMoveToOffset(getListView(), offset);
    }
}
