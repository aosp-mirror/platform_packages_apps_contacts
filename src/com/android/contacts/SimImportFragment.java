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
package com.android.contacts;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.database.SimContactDao;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.MultiSelectEntryContactListAdapter;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.SimContact;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.editor.AccountHeaderPresenter;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/**
 * Dialog that presents a list of contacts from a SIM card that can be imported into a selected
 * account
 */
public class SimImportFragment extends DialogFragment
        implements LoaderManager.LoaderCallbacks<ArrayList<SimContact>>,
        MultiSelectEntryContactListAdapter.SelectedContactsListener {

    private static final String KEY_ACCOUNT = "account";
    private static final String ARG_SUBSCRIPTION_ID = "subscriptionId";
    public static final int NO_SUBSCRIPTION_ID = -1;

    private ContactsPreferences mPreferences;
    private AccountTypeManager mAccountTypeManager;
    private SimContactAdapter mAdapter;
    private AccountHeaderPresenter mAccountHeaderPresenter;
    private ContentLoadingProgressBar mLoadingIndicator;
    private Toolbar mToolbar;
    private ListView mListView;
    private View mImportButton;

    private int mSubscriptionId;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.PeopleThemeAppCompat_FullScreenDialog);
        mPreferences = new ContactsPreferences(getContext());
        mAccountTypeManager = AccountTypeManager.getInstance(getActivity());
        mAdapter = new SimContactAdapter(getActivity());

        mAdapter.setPhotoLoader(ContactPhotoManager.getInstance(getActivity()));
        mAdapter.setDisplayCheckBoxes(true);
        mAdapter.setHasHeader(0, false);

        final Bundle args = getArguments();
        mSubscriptionId = args == null ? NO_SUBSCRIPTION_ID : args.getInt(ARG_SUBSCRIPTION_ID,
                NO_SUBSCRIPTION_ID);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // Set the title for accessibility. It isn't displayed but will get announced when the
        // window is shown
        dialog.setTitle(R.string.sim_import_dialog_title);
        return dialog;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_sim_import, container, false);

        mAccountHeaderPresenter = new AccountHeaderPresenter(
                view.findViewById(R.id.account_header_container));
        if (savedInstanceState != null) {
            AccountWithDataSet account = savedInstanceState.getParcelable(KEY_ACCOUNT);
            mAccountHeaderPresenter.setCurrentAccount(account);
        } else {
            final AccountWithDataSet currentDefaultAccount = AccountWithDataSet
                    .getDefaultOrBestFallback(mPreferences, mAccountTypeManager);
            mAccountHeaderPresenter.setCurrentAccount(currentDefaultAccount);
        }

        mListView = (ListView) view.findViewById(R.id.list);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mAdapter.toggleSelectionOfContactId(id);
            }
        });
        mImportButton = view.findViewById(R.id.import_button);
        mImportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importCurrentSelections();
                // Do we wait for import to finish?
                dismiss();
            }
        });
        mImportButton.setEnabled(mAdapter.getSelectedContactIds().size() > 0);

        mToolbar = (Toolbar) view.findViewById(R.id.toolbar);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        mLoadingIndicator = (ContentLoadingProgressBar) view.findViewById(R.id.loading_progress);
        mAdapter.setSelectedContactsListener(this);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mAdapter.isEmpty() && getLoaderManager().getLoader(0).isStarted()) {
            mLoadingIndicator.show();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_ACCOUNT, mAccountHeaderPresenter.getCurrentAccount());
    }

    @Override
    public SimContactLoader onCreateLoader(int id, Bundle args) {
        return new SimContactLoader(getContext(), mSubscriptionId);
    }

    @Override
    public void onLoadFinished(Loader<ArrayList<SimContact>> loader,
            ArrayList<SimContact> data) {
        mListView.setEmptyView(getView().findViewById(R.id.empty_message));
        mAdapter.setContacts(data);
        // we default to selecting all contacts.
        mAdapter.selectAll();
        mLoadingIndicator.hide();
    }

    @Override
    public void onLoaderReset(Loader<ArrayList<SimContact>> loader) {
    }

    private void importCurrentSelections() {
        ContactSaveService.startService(getContext(), ContactSaveService
                .createImportFromSimIntent(getContext(), mAdapter.getSelectedContacts(),
                mAccountHeaderPresenter.getCurrentAccount()));
    }

    @Override
    public void onSelectedContactsChanged() {
        updateSelectedCount();
    }

    @Override
    public void onSelectedContactsChangedViaCheckBox() {
        updateSelectedCount();
    }

    private void updateSelectedCount() {
        final int selectedCount = mAdapter.getSelectedContactIds().size();
        if (selectedCount == 0) {
            mToolbar.setTitle(R.string.sim_import_title_none_selected);
        } else {
            mToolbar.setTitle(getString(R.string.sim_import_title_some_selected_fmt,
                    selectedCount));
        }
        if (mImportButton != null) {
            mImportButton.setEnabled(selectedCount > 0);
        }
    }

    public Context getContext() {
        if (CompatUtils.isMarshmallowCompatible()) {
            return super.getContext();
        }
        return getActivity();
    }

    /**
     * Creates a fragment that will display contacts stored on the default SIM card
     */
    public static SimImportFragment newInstance() {
        return new SimImportFragment();
    }

    /**
     * Creates a fragment that will display the contacts stored on the SIM card that has the
     * provided subscriptionId
     */
    public static SimImportFragment newInstance(int subscriptionId) {
        final SimImportFragment fragment = new SimImportFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_SUBSCRIPTION_ID, subscriptionId);
        fragment.setArguments(args);
        return fragment;
    }

    private static class SimContactAdapter extends ContactListAdapter {
        private ArrayList<SimContact> mContacts;

        public SimContactAdapter(Context context) {
            super(context);
        }

        @Override
        public void configureLoader(CursorLoader loader, long directoryId) {
        }

        @Override
        protected void bindView(View itemView, int partition, Cursor cursor, int position) {
            super.bindView(itemView, partition, cursor, position);
            ContactListItemView contactView = (ContactListItemView) itemView;
            bindNameAndViewId(contactView, cursor);
            bindPhoto(contactView, partition, cursor);

            // For accessibility. Tapping the item checks this so we don't need it to be separately
            // clickable
            contactView.getCheckBox().setFocusable(false);
            contactView.getCheckBox().setClickable(false);
        }

        public void setContacts(ArrayList<SimContact> contacts) {
            mContacts = contacts;
            changeCursor(SimContact.convertToContactsCursor(mContacts,
                    ContactQuery.CONTACT_PROJECTION_PRIMARY));
        }

        public ArrayList<SimContact> getSelectedContacts() {
            if (mContacts == null) return null;

            final Set<Long> selectedIds = getSelectedContactIds();
            final ArrayList<SimContact> selected = new ArrayList<>();
            for (SimContact contact : mContacts) {
                if (selectedIds.contains(contact.getId())) {
                    selected.add(contact);
                }
            }
            return selected;
        }

        public void selectAll() {
            if (mContacts == null) return;

            final TreeSet<Long> selected = new TreeSet<>();
            for (SimContact contact : mContacts) {
                selected.add(contact.getId());
            }
            setSelectedContactIds(selected);
        }
    }

    public static class SimContactLoader extends AsyncTaskLoader<ArrayList<SimContact>> {
        private SimContactDao mDao;
        private final int mSubscriptionId;
        private ArrayList<SimContact> mData;

        public SimContactLoader(Context context, int subscriptionId) {
            super(context);
            mDao = new SimContactDao(context);
            mSubscriptionId = subscriptionId;
        }

        @Override
        protected void onStartLoading() {
            if (mData != null) {
                deliverResult(mData);
            } else {
                forceLoad();
            }
        }

        @Override
        public void deliverResult(ArrayList<SimContact> data) {
            mData = data;
            super.deliverResult(data);
        }

        @Override
        public ArrayList<SimContact> loadInBackground() {
            if (mSubscriptionId != NO_SUBSCRIPTION_ID) {
                return mDao.loadSimContacts(mSubscriptionId);
            } else {
                return mDao.loadSimContacts();
            }
        }

        @Override
        protected void onReset() {
            mData = null;
        }
    }
}
