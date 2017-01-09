/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.contacts.drawer;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity.ContactsView;
import com.android.contacts.group.GroupListItem;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.group.GroupsFragment;
import com.android.contacts.group.GroupsFragment.GroupsListener;
import com.android.contacts.interactions.AccountFiltersFragment;
import com.android.contacts.interactions.AccountFiltersFragment.AccountFiltersListener;
import com.android.contacts.list.ContactListFilter;
import com.android.contactsbind.ObjectFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DrawerFragment extends Fragment implements AccountFiltersListener, GroupsListener {

    private WelcomeContentObserver mObserver;
    private RecyclerView mDrawerRecyclerView;
    private DrawerAdapter mDrawerAdapter;
    private ContactsView mCurrentContactsView;
    private DrawerFragmentListener mListener;
    private GroupsFragment mGroupsFragment;
    private AccountFiltersFragment mAccountFiltersFragment;

    private static final String TAG_GROUPS = "groups";
    private static final String TAG_FILTERS = "filters";
    private static final String KEY_CONTACTS_VIEW = "contactsView";
    private static final String KEY_SELECTED_GROUP = "selectedGroup";
    private static final String KEY_SELECTED_ACCOUNT = "selectedAccount";

    private final class WelcomeContentObserver extends ContentObserver {
        private WelcomeContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            mDrawerAdapter.notifyDataSetChanged();
        }
    }

    public DrawerFragment() {}

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof DrawerFragmentListener) {
            mListener = (DrawerFragmentListener) activity;
        } else {
            throw new IllegalArgumentException(
                    "Activity must implement " + DrawerFragmentListener.class.getName());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View contentView = inflater.inflate(R.layout.drawer_fragment, null);
        mDrawerRecyclerView = (RecyclerView) contentView.findViewById(R.id.list);
        mDrawerRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mDrawerAdapter = new DrawerAdapter(getActivity());
        mDrawerAdapter.setSelectedContactsView(mCurrentContactsView);
        mDrawerAdapter.setItemOnClickListener(mOnDrawerItemClickListener);
        loadGroupsAndFilters();
        mDrawerRecyclerView.setAdapter(mDrawerAdapter);

        if (savedInstanceState != null) {
            final ContactsView contactsView =
                    ContactsView.values()[savedInstanceState.getInt(KEY_CONTACTS_VIEW)];
            setNavigationItemChecked(contactsView);
            final long groupId = savedInstanceState.getLong(KEY_SELECTED_GROUP);
            mDrawerAdapter.setSelectedGroupId(groupId);
            final ContactListFilter filter = savedInstanceState.getParcelable(KEY_SELECTED_ACCOUNT);
            mDrawerAdapter.setSelectedAccount(filter);
        } else {
            setNavigationItemChecked(ContactsView.ALL_CONTACTS);
        }

        return contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // todo double check on the new Handler() thing
        final Uri uri = ObjectFactory.getWelcomeUri();
        if (uri != null) {
            mObserver = new WelcomeContentObserver(new Handler());
            getActivity().getContentResolver().registerContentObserver(uri, false, mObserver);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CONTACTS_VIEW, mCurrentContactsView.ordinal());
        outState.putLong(KEY_SELECTED_GROUP, mDrawerAdapter.getSelectedGroupId());
        outState.putParcelable(KEY_SELECTED_ACCOUNT, mDrawerAdapter.getSelectedAccount());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mObserver != null) {
            getActivity().getContentResolver().unregisterContentObserver(mObserver);
        }
    }

    // TODO create loaders in this fragment instead of having separate fragments that just kick off
    // some data loading.
    private void loadGroupsAndFilters() {
        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        mGroupsFragment = (GroupsFragment) fragmentManager.findFragmentByTag(TAG_GROUPS);
        if (mGroupsFragment == null) {
            mGroupsFragment = new GroupsFragment();
            transaction.add(mGroupsFragment, TAG_GROUPS);
        }
        mGroupsFragment.setListener(this);

        mAccountFiltersFragment = (AccountFiltersFragment)
                fragmentManager.findFragmentByTag(TAG_FILTERS);
        if (mAccountFiltersFragment == null) {
            mAccountFiltersFragment = new AccountFiltersFragment();
            transaction.add(mAccountFiltersFragment, TAG_FILTERS);
        }
        mAccountFiltersFragment.setListener(this);
        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private final View.OnClickListener mOnDrawerItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mListener == null) {
                return;
            }
            mListener.onDrawerItemClicked();
            final int viewId = v.getId();
            if (viewId == R.id.nav_all_contacts) {
                mListener.onContactsViewSelected(ContactsView.ALL_CONTACTS);
                setNavigationItemChecked(ContactsView.ALL_CONTACTS);
            } else if (viewId == R.id.nav_assistant) {
                mListener.onContactsViewSelected(ContactsView.ASSISTANT);
                setNavigationItemChecked(ContactsView.ASSISTANT);
            } else if (viewId == R.id.nav_group) {
                final GroupListItem groupListItem = (GroupListItem) v.getTag();
                mListener.onGroupViewSelected(groupListItem);
                mDrawerAdapter.setSelectedGroupId(groupListItem.getGroupId());
                setNavigationItemChecked(ContactsView.GROUP_VIEW);
            } else if (viewId == R.id.nav_filter) {
                final ContactListFilter filter = (ContactListFilter) v.getTag();
                mListener.onAccountViewSelected(filter);
                mDrawerAdapter.setSelectedAccount(filter);
                setNavigationItemChecked(ContactsView.ACCOUNT_VIEW);
            } else if (viewId == R.id.nav_create_label) {
                mListener.onCreateLabelButtonClicked();
            } else if (viewId == R.id.nav_settings) {
                mListener.onOpenSettings();
            } else if (viewId == R.id.nav_help) {
                mListener.onLaunchHelpFeedback();
            } else {
                throw new IllegalStateException("Unknown view");
            }
        }
    };

    public void setNavigationItemChecked(ContactsView contactsView) {
        mCurrentContactsView = contactsView;
        if (mDrawerAdapter != null) {
            mDrawerAdapter.setSelectedContactsView(contactsView);
        }
    }

    @Override
    public void onGroupsLoaded(List<GroupListItem> groupListItems, boolean areGroupWritable) {
        final Iterator<GroupListItem> iterator = groupListItems.iterator();
        while (iterator.hasNext()) {
            final GroupListItem groupListItem = iterator.next();
            if (GroupUtil.isEmptyFFCGroup(groupListItem)) {
                iterator.remove();
            }
        }
        mDrawerAdapter.setGroups(groupListItems, areGroupWritable);
    }

    public void updateGroupMenu(long groupId) {
        mDrawerAdapter.setSelectedGroupId(groupId);
        setNavigationItemChecked(ContactsView.GROUP_VIEW);
    }

    @Override
    public void onFiltersLoaded(List<ContactListFilter> accountFilterItems) {
        if (accountFilterItems == null || accountFilterItems.size() < 2) {
            mDrawerAdapter.setAccounts(new ArrayList<ContactListFilter>());
        } else {
            mDrawerAdapter.setAccounts(accountFilterItems);
        }
    }

    public interface DrawerFragmentListener {
        void onDrawerItemClicked();
        void onContactsViewSelected(ContactsView mode);
        void onGroupViewSelected(GroupListItem groupListItem);
        void onAccountViewSelected(ContactListFilter filter);
        void onCreateLabelButtonClicked();
        void onOpenSettings();
        void onLaunchHelpFeedback();
    }
}
