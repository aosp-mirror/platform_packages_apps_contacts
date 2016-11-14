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
import android.support.design.widget.Snackbar;
import android.support.v4.util.ArrayMap;
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
import com.android.contacts.common.model.SimCard;
import com.android.contacts.common.model.SimContact;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.editor.AccountHeaderPresenter;
import com.google.common.primitives.Longs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Dialog that presents a list of contacts from a SIM card that can be imported into a selected
 * account
 */
public class SimImportFragment extends DialogFragment
        implements LoaderManager.LoaderCallbacks<SimImportFragment.LoaderResult>,
        MultiSelectEntryContactListAdapter.SelectedContactsListener {

    private static final String KEY_SUFFIX_SELECTED_IDS = "_selectedIds";
    private static final String ARG_SUBSCRIPTION_ID = "subscriptionId";

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

        // This needs to be set even though photos aren't loaded because the adapter assumes it
        // will be non-null
        mAdapter.setPhotoLoader(ContactPhotoManager.getInstance(getActivity()));
        mAdapter.setDisplayCheckBoxes(true);
        mAdapter.setHasHeader(0, false);

        final Bundle args = getArguments();
        mSubscriptionId = args == null ? SimCard.NO_SUBSCRIPTION_ID :
                args.getInt(ARG_SUBSCRIPTION_ID, SimCard.NO_SUBSCRIPTION_ID);
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
            mAccountHeaderPresenter.onRestoreInstanceState(savedInstanceState);
        } else {
            final AccountWithDataSet currentDefaultAccount = AccountWithDataSet
                    .getDefaultOrBestFallback(mPreferences, mAccountTypeManager);
            mAccountHeaderPresenter.setCurrentAccount(currentDefaultAccount);
        }
        mAccountHeaderPresenter.setObserver(new AccountHeaderPresenter.Observer() {
            @Override
            public void onChange(AccountHeaderPresenter sender) {
                mAdapter.setAccount(sender.getCurrentAccount());
            }
        });
        mAdapter.setAccount(mAccountHeaderPresenter.getCurrentAccount());
        restoreAdapterSelectedStates(savedInstanceState);

        mListView = (ListView) view.findViewById(R.id.list);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mAdapter.existsInCurrentAccount(position)) {
                    Snackbar.make(getView(), R.string.sim_import_contact_exists_toast,
                            Snackbar.LENGTH_LONG).show();
                } else {
                    mAdapter.toggleSelectionOfContactId(id);
                }
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
        mAccountHeaderPresenter.onSaveInstanceState(outState);
        saveAdapterSelectedStates(outState);
    }

    @Override
    public SimContactLoader onCreateLoader(int id, Bundle args) {
        return new SimContactLoader(getContext(), mSubscriptionId);
    }

    @Override
    public void onLoadFinished(Loader<LoaderResult> loader,
            LoaderResult data) {
        mLoadingIndicator.hide();
        mListView.setEmptyView(getView().findViewById(R.id.empty_message));
        if (data == null) {
            return;
        }
        mAdapter.setData(data);
    }

    @Override
    public void onLoaderReset(Loader<LoaderResult> loader) {
    }

    private void restoreAdapterSelectedStates(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }

        final List<AccountWithDataSet> accounts = mAccountTypeManager.getAccounts(true);
        for (AccountWithDataSet account : accounts) {
            final long[] selections = savedInstanceState.getLongArray(
                    account.stringify() + KEY_SUFFIX_SELECTED_IDS);
            if (selections != null) {
                mAdapter.setSelectionsForAccount(account, selections);
            }
        }
    }

    private void saveAdapterSelectedStates(Bundle outState) {
        if (mAdapter == null) {
            return;
        }

        // Make sure the selections are up-to-date
        mAdapter.storeCurrentSelections();
        for (Map.Entry<AccountWithDataSet, TreeSet<Long>> entry :
                mAdapter.getSelectedIds().entrySet()) {
            final long[] ids = Longs.toArray(entry.getValue());
            outState.putLongArray(entry.getKey().stringify() + KEY_SUFFIX_SELECTED_IDS, ids);
        }
    }

    private void importCurrentSelections() {
        ContactSaveService.startService(getContext(), ContactSaveService
                .createImportFromSimIntent(getContext(), mSubscriptionId,
                        mAdapter.getSelectedContacts(),
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
        private AccountWithDataSet mSelectedAccount;
        private Map<AccountWithDataSet, Set<SimContact>> mExistingMap;
        private Map<AccountWithDataSet, TreeSet<Long>> mPerAccountCheckedIds = new ArrayMap<>();

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

            // For accessibility. Tapping the item checks this so we don't need it to be separately
            // clickable
            contactView.getCheckBox().setFocusable(false);
            contactView.getCheckBox().setClickable(false);
            setViewEnabled(contactView, !existsInCurrentAccount(position));
        }

        public void setData(LoaderResult result) {
            mContacts = result.contacts;
            mExistingMap = result.accountsMap;
            changeCursor(SimContact.convertToContactsCursor(mContacts,
                    ContactQuery.CONTACT_PROJECTION_PRIMARY));
            updateDisplayedSelections();
        }

        public void setAccount(AccountWithDataSet account) {
            if (mContacts == null) {
                mSelectedAccount = account;
                return;
            }

            // Save the checked state for the current account.
            storeCurrentSelections();
            mSelectedAccount = account;
            updateDisplayedSelections();
        }

        public void storeCurrentSelections() {
            if (mSelectedAccount != null) {
                mPerAccountCheckedIds.put(mSelectedAccount, getSelectedContactIds());
            }
        }

        public Map<AccountWithDataSet, TreeSet<Long>> getSelectedIds() {
            return mPerAccountCheckedIds;
        }

        private void updateDisplayedSelections() {
            if (mContacts == null) {
                return;
            }

            TreeSet<Long> checked = mPerAccountCheckedIds.get(mSelectedAccount);
            if (checked == null) {
                checked = getEnabledIdsForCurrentAccount();
                mPerAccountCheckedIds.put(mSelectedAccount, checked);
            }
            setSelectedContactIds(checked);

            notifyDataSetChanged();
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

        public void setSelectionsForAccount(AccountWithDataSet account, long[] contacts) {
            final TreeSet<Long> selected = new TreeSet<>(Longs.asList(contacts));
            mPerAccountCheckedIds.put(account, selected);
            if (account.equals(mSelectedAccount)) {
                updateDisplayedSelections();
            }
        }

        public boolean existsInCurrentAccount(int position) {
            return existsInCurrentAccount(mContacts.get(position));
        }

        public boolean existsInCurrentAccount(SimContact contact) {
            if (mSelectedAccount == null || !mExistingMap.containsKey(mSelectedAccount)) {
                return false;
            }
            return mExistingMap.get(mSelectedAccount).contains(contact);
        }

        private TreeSet<Long> getEnabledIdsForCurrentAccount() {
            final TreeSet<Long> result = new TreeSet<>();
            for (SimContact contact : mContacts) {
                if (!existsInCurrentAccount(contact)) {
                    result.add(contact.getId());
                }
            }
            return result;
        }

        private void setViewEnabled(ContactListItemView itemView, boolean enabled) {
            itemView.getCheckBox().setEnabled(enabled);
            itemView.getNameTextView().setEnabled(enabled);
            // If the checkbox is left to default it's "unchecked" state will be announced when
            // it is clicked on instead of the snackbar which is not useful.
            int accessibilityMode = enabled ?
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES :
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO;
            itemView.getCheckBox().setImportantForAccessibility(accessibilityMode);
        }
    }


    private static class SimContactLoader extends AsyncTaskLoader<LoaderResult> {
        private SimContactDao mDao;
        private final int mSubscriptionId;
        LoaderResult mResult;

        public SimContactLoader(Context context, int subscriptionId) {
            super(context);
            mDao = SimContactDao.create(context);
            mSubscriptionId = subscriptionId;
        }

        @Override
        protected void onStartLoading() {
            if (mResult != null) {
                deliverResult(mResult);
            } else {
                forceLoad();
            }
        }

        @Override
        public void deliverResult(LoaderResult result) {
            mResult = result;
            super.deliverResult(result);
        }

        @Override
        public LoaderResult loadInBackground() {
            final SimCard sim = mDao.getSimBySubscriptionId(mSubscriptionId);
            LoaderResult result = new LoaderResult();
            if (sim == null) {
                result.contacts = new ArrayList<>();
                result.accountsMap = Collections.emptyMap();
                return result;
            }
            result.contacts = mDao.loadContactsForSim(sim);
            result.accountsMap = mDao.findAccountsOfExistingSimContacts(result.contacts);
            return result;
        }

        @Override
        protected void onReset() {
            mResult = null;
        }

    }

    public static class LoaderResult {
        public ArrayList<SimContact> contacts;
        public Map<AccountWithDataSet, Set<SimContact>> accountsMap;
    }
}
