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
 * limitations under the License
 */
package com.android.contacts.group;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.list.MultiSelectContactsListFragment;

/** Displays the members of a group. */
public class GroupMembersListFragment extends MultiSelectContactsListFragment {

    private static final String KEY_GROUP_METADATA = "groupMetadata";

    private static final String ARG_GROUP_METADATA = "groupMetadata";

    /** Callbacks for hosts of {@link GroupMembersListFragment}. */
    public interface GroupMembersListListener {

        /** Invoked when a group member in the list is clicked. */
        void onGroupMemberListItemClicked(Uri contactLookupUri);
    }

    private GroupMembersListListener mListener;

    private GroupMetadata mGroupMetadata;

    public static GroupMembersListFragment newInstance(GroupMetadata groupMetadata) {
        final Bundle args = new Bundle();
        args.putParcelable(ARG_GROUP_METADATA, groupMetadata);

        final GroupMembersListFragment fragment = new GroupMembersListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public GroupMembersListFragment() {
        setHasOptionsMenu(true);

        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        // Don't show the scrollbar until after group members have been loaded
        setVisibleScrollbarEnabled(false);
        setQuickContactEnabled(false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (GroupMembersListListener) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity() + " must implement " +
                    GroupMembersListListener.class.getSimpleName());
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (savedState == null) {
            mGroupMetadata = getArguments().getParcelable(ARG_GROUP_METADATA);
        } else {
            mGroupMetadata = savedState.getParcelable(KEY_GROUP_METADATA);
        }

        // Don't attach the multi select check box listener if we can't edit the group
        if (mGroupMetadata.editable) {
            try {
                setCheckBoxListListener((OnCheckBoxListActionListener) getActivity());
            } catch (ClassCastException e) {
                throw new ClassCastException(getActivity() + " must implement " +
                        OnCheckBoxListActionListener.class.getSimpleName());
            }
        }
    }

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        bindMembersCount(view);
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_GROUP_METADATA, mGroupMetadata);
    }

    private void bindMembersCount(View view) {
        final View accountFilterContainer = view.findViewById(
                R.id.account_filter_header_container);
        if (mGroupMetadata.memberCount >= 0) {
            accountFilterContainer.setVisibility(View.VISIBLE);

            final TextView accountFilterHeader = (TextView) accountFilterContainer.findViewById(
                    R.id.account_filter_header);
            accountFilterHeader.setText(getResources().getQuantityString(
                    R.plurals.group_members_count, mGroupMetadata.memberCount,
                    mGroupMetadata.memberCount));
        } else {
            accountFilterContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected GroupMembersListAdapter createListAdapter() {
        final GroupMembersListAdapter adapter = new GroupMembersListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    public GroupMembersListAdapter getAdapter() {
        return (GroupMembersListAdapter) super.getAdapter();
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        getAdapter().setGroupId(mGroupMetadata.groupId);
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_list_content, /* root */ null);
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri uri = getAdapter().getContactUri(position);
        if (uri == null) {
            return;
        }
        if (getAdapter().isDisplayingCheckBoxes()) {
            super.onItemClick(position, id);
            return;
        }
        if (mListener != null) {
            final Uri contactLookupUri = getAdapter().getContactLookupUri(position);
            mListener.onGroupMemberListItemClicked(contactLookupUri);
        }
    }
}
