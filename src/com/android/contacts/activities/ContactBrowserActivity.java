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

package com.android.contacts.activities;

import com.android.contacts.R;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.interactions.ImportExportInteraction;
import com.android.contacts.interactions.PhoneNumberInteraction;
import com.android.contacts.list.ContactBrowseListContextMenuAdapter;
import com.android.contacts.list.ContactBrowseListFragment;
import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.StrequentContactListFragment;
import com.android.contacts.ui.ContactsPreferencesActivity;
import com.android.contacts.util.DialogManager;
import com.android.contacts.views.detail.ContactDetailFragment;
import com.android.contacts.views.detail.ContactNoneFragment;
import com.android.contacts.views.editor.ContactEditorFragment;
import com.android.contacts.widget.ContextMenuAdapter;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

/**
 * Displays a list to browse contacts. For xlarge screens, this also displays a detail-pane on
 * the right
 */
public class ContactBrowserActivity extends Activity
        implements View.OnCreateContextMenuListener, ActionBarAdapter.Listener,
        DialogManager.DialogShowingViewActivity {

    private static final String TAG = "ContactBrowserActivity";

    private static final String KEY_MODE = "mode";

    private static final int SUBACTIVITY_NEW_CONTACT = 2;
    private static final int SUBACTIVITY_DISPLAY_GROUP = 3;

    private DialogManager mDialogManager = new DialogManager(this);

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;

    private boolean mHasActionBar;
    private ActionBarAdapter mActionBarAdapter;

    /**
     * Contact browser mode, see {@link ContactBrowserMode}.
     */
    private int mMode = -1;

    private ContactBrowseListFragment mListFragment;
    private ContactNoneFragment mEmptyFragment;

    private boolean mContactContentDisplayed;
    private ContactDetailFragment mDetailFragment;
    private DetailFragmentListener mDetailFragmentListener = new DetailFragmentListener();

    private ContactEditorFragment mEditorFragment;
    private EditorFragmentListener mEditorFragmentListener = new EditorFragmentListener();

    private PhoneNumberInteraction mPhoneNumberCallInteraction;
    private PhoneNumberInteraction mSendTextMessageInteraction;
    private ContactDeletionInteraction mContactDeletionInteraction;
    private ImportExportInteraction mImportExportInteraction;

    private boolean mSearchInitiated;

    public ContactBrowserActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactBrowseListFragment) {
            mListFragment = (ContactBrowseListFragment)fragment;
            mListFragment.setOnContactListActionListener(new ContactBrowserActionListener());
        } else if (fragment instanceof ContactNoneFragment) {
            mEmptyFragment = (ContactNoneFragment)fragment;
        } else if (fragment instanceof ContactDetailFragment) {
            mDetailFragment = (ContactDetailFragment)fragment;
            mDetailFragment.setListener(mDetailFragmentListener);
        } else if (fragment instanceof ContactEditorFragment) {
            mEditorFragment = (ContactEditorFragment)fragment;
            mEditorFragment.setListener(mEditorFragmentListener);
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            mMode = savedState.getInt(KEY_MODE);
        }

        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());
        if (!mRequest.isValid()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        Intent redirect = mRequest.getRedirectIntent();
        if (redirect != null) {
            // Need to start a different activity
            startActivity(redirect);
            finish();
            return;
        }

        setTitle(mRequest.getActivityTitle());
        setContentView(R.layout.contact_browser);

        mHasActionBar = getWindow().hasFeature(Window.FEATURE_ACTION_BAR);
        mContactContentDisplayed = findViewById(R.id.detail_container) != null;

        if (mHasActionBar) {
            mActionBarAdapter = new ActionBarAdapter(this);
            mActionBarAdapter.onCreate(savedState, mRequest);
            mActionBarAdapter.setListener(this);

            ActionBar actionBar = getActionBar();
            View navBarView = mActionBarAdapter.onCreateView(getLayoutInflater());
            actionBar.setCustomNavigationMode(navBarView);
        }

        configureListFragment();

        if (mContactContentDisplayed) {
            setupContactDetailFragment(mListFragment.getSelectedContactUri());
        }
    }

    private void configureListFragment() {
        int mode = -1;
        if (mHasActionBar) {
            mode = mActionBarAdapter.getMode();
            if (mode == ContactBrowserMode.MODE_SEARCH
                    && TextUtils.isEmpty(mActionBarAdapter.getQueryString())) {
                mode = mActionBarAdapter.getDefaultMode();
            }
        } else {
            int actionCode = mRequest.getActionCode();
            if (actionCode == ContactsRequest.ACTION_FREQUENT ||
                    actionCode == ContactsRequest.ACTION_STARRED ||
                    actionCode == ContactsRequest.ACTION_STREQUENT) {
                mode = ContactBrowserMode.MODE_FAVORITES;
            } else {
                mode = ContactBrowserMode.MODE_CONTACTS;
            }
        }

        boolean replaceList = (mode != mMode);
        if (replaceList) {
            closeListFragment();
            mMode = mode;
            switch (mMode) {
                case ContactBrowserMode.MODE_CONTACTS: {
                    mListFragment = createListFragment(ContactsRequest.ACTION_DEFAULT);
                    break;
                }
                case ContactBrowserMode.MODE_FAVORITES: {
                    int favoritesAction = mRequest.getActionCode();
                    if (favoritesAction == ContactsRequest.ACTION_DEFAULT) {
                        favoritesAction = ContactsRequest.ACTION_STREQUENT;
                    }
                    mListFragment = createListFragment(favoritesAction);
                    break;
                }
                case ContactBrowserMode.MODE_SEARCH: {
                    mListFragment = createContactSearchFragment();
                    break;
                }
            }
        }

        if (mHasActionBar) {
            Bundle savedStateForMode = mActionBarAdapter.getSavedStateForMode(mMode);
            if (savedStateForMode != null) {
                mListFragment.restoreSavedState(savedStateForMode);
                mActionBarAdapter.clearSavedState(mMode);
            }
            if (mMode == ContactBrowserMode.MODE_SEARCH) {
                mListFragment.setQueryString(mActionBarAdapter.getQueryString());
            }
        }

        if (replaceList) {
            getFragmentManager().openTransaction()
                    .replace(R.id.list_container, mListFragment)
                    .commit();
        }
    }

    private void closeListFragment() {
        if (mListFragment != null) {
            mListFragment.setOnContactListActionListener(null);

            if (mHasActionBar) {
                Bundle state = new Bundle();
                mListFragment.onSaveInstanceState(state);
                mActionBarAdapter.saveStateForMode(mMode, state);
            }

            mListFragment = null;
        }
    }

    private void setupContactDetailFragment(Uri contactLookupUri) {

        // If we are already editing this URI - just continue editing
        if (mEditorFragment != null && contactLookupUri != null
                && contactLookupUri.equals(mEditorFragment.getLookupUri())) {
            return;
        }

        if (mDetailFragment != null && contactLookupUri != null
                && contactLookupUri.equals(mDetailFragment.getUri())) {
            return;
        }

        // No editor here
        closeEditorFragment(true);

        if (contactLookupUri != null) {
            // Already showing? Nothing to do
            if (mDetailFragment != null) {
                mDetailFragment.loadUri(contactLookupUri);
                return;
            }

            closeEmptyFragment();

            mDetailFragment = new ContactDetailFragment();
            mDetailFragment.loadUri(contactLookupUri);

            // Nothing showing yet? Create (this happens during Activity-Startup)
            getFragmentManager().openTransaction()
                    .replace(R.id.detail_container, mDetailFragment)
                    .commit();
        } else {
            closeDetailFragment();

            mEmptyFragment = new ContactNoneFragment();
            getFragmentManager().openTransaction()
                    .replace(R.id.detail_container, mEmptyFragment)
                    .commit();
        }
    }

    private void setupContactEditorFragment(Uri contactLookupUri) {
        // No detail view here
        closeDetailFragment();
        closeEmptyFragment();

        // Already showing? Nothing to do
        if (mEditorFragment != null) return;

        mEditorFragment = new ContactEditorFragment();
        mEditorFragment.load(Intent.ACTION_EDIT, contactLookupUri,
                Contacts.CONTENT_ITEM_TYPE, new Bundle());

        // Nothing showing yet? Create (this happens during Activity-Startup)
        getFragmentManager().openTransaction()
                .replace(R.id.detail_container, mEditorFragment)
                .commit();
    }

    private void closeDetailFragment() {
        if (mDetailFragment != null) {
            mDetailFragment.setListener(null);
            mDetailFragment = null;
        }
    }

    /**
     * Closes the editor, if it is currently open
     * @param save Whether the changes should be saved. This should always be true, unless
     * this is called from a Revert/Undo button
     */
    private void closeEditorFragment(boolean save) {
        Log.d(TAG, "closeEditorFragment(" + save + ")");

        if (mEditorFragment != null) {
            // Remove the listener before saving, because it will be used to forward the onClose
            // after save
            mEditorFragment.setListener(null);
            if (save) mEditorFragment.save(true);
            mEditorFragment = null;
        }
    }

    private void closeEmptyFragment() {
        mEmptyFragment = null;
    }

    /**
     * Handler for action bar actions.
     */
    @Override
    public void onAction() {
        configureListFragment();
        setupContactDetailFragment(mListFragment.getSelectedContactUri());
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        // If anything was left unsaved, save it now but keep the editor open.
        // Don't do this if we are only rotating the screen
        if (mEditorFragment != null && !isChangingConfigurations()) {
            mEditorFragment.save(false);
        }
    }

    /**
     * Creates the list fragment for the specified mode.
     */
    private ContactBrowseListFragment createListFragment(int actionCode) {
        switch (actionCode) {
            case ContactsRequest.ACTION_DEFAULT: {
                DefaultContactBrowseListFragment fragment = new DefaultContactBrowseListFragment();
                fragment.setContactsRequest(mRequest);
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setDisplayWithPhonesOnlyOption(mRequest.getDisplayWithPhonesOnlyOption());
                fragment.setVisibleContactsRestrictionEnabled(mRequest.getDisplayOnlyVisible());
                fragment.setContextMenuAdapter(new ContactBrowseListContextMenuAdapter(fragment));
                fragment.setSearchMode(mRequest.isSearchMode());
                fragment.setQueryString(mRequest.getQueryString());
                fragment.setDirectorySearchEnabled(
                        mRequest.isSearchMode() && mRequest.isDirectorySearchEnabled());
                fragment.setAizyEnabled(!mRequest.isSearchMode());
                fragment.setSelectionVisible(mContactContentDisplayed);
                return fragment;
            }

            case ContactsRequest.ACTION_GROUP: {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            case ContactsRequest.ACTION_STARRED: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(false);
                fragment.setStarredContactsIncluded(true);
                fragment.setSelectionVisible(mContactContentDisplayed);
                return fragment;
            }

            case ContactsRequest.ACTION_FREQUENT: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(true);
                fragment.setStarredContactsIncluded(false);
                fragment.setSelectionVisible(mContactContentDisplayed);
                return fragment;
            }

            case ContactsRequest.ACTION_STREQUENT: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(true);
                fragment.setStarredContactsIncluded(true);
                fragment.setSelectionVisible(mContactContentDisplayed);
                return fragment;
            }

            default:
                throw new IllegalStateException("Invalid action code: " + actionCode);
        }
    }

    private ContactBrowseListFragment createContactSearchFragment() {
        DefaultContactBrowseListFragment fragment = new DefaultContactBrowseListFragment();
        fragment.setOnContactListActionListener(new ContactBrowserActionListener());
        fragment.setDisplayWithPhonesOnlyOption(ContactsRequest.DISPLAY_ONLY_WITH_PHONES_DISABLED);
        fragment.setVisibleContactsRestrictionEnabled(true);
        fragment.setContextMenuAdapter(new ContactBrowseListContextMenuAdapter(fragment));
        fragment.setSearchMode(true);
        fragment.setDirectorySearchEnabled(true);
        fragment.setAizyEnabled(false);
        fragment.setSelectionVisible(true);
        return fragment;
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {
        public void onViewContactAction(Uri contactLookupUri, boolean force) {
            if (mContactContentDisplayed) {
                if (force) closeEditorFragment(true);
                mListFragment.setSelectedContactUri(contactLookupUri);
                setupContactDetailFragment(contactLookupUri);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, contactLookupUri));
            }
        }

        public void onCreateNewContactAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
        }

        public void onEditContactAction(Uri contactLookupUri) {
            if (mContactContentDisplayed) {
                closeEditorFragment(true);
                mListFragment.setSelectedContactUri(contactLookupUri);
                setupContactEditorFragment(contactLookupUri);
            } else {
                Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
                Bundle extras = getIntent().getExtras();
                if (extras != null) {
                    intent.putExtras(extras);
                }
                startActivity(intent);
            }
        }

        public void onAddToFavoritesAction(Uri contactUri) {
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 1);
            getContentResolver().update(contactUri, values, null, null);
        }

        public void onRemoveFromFavoritesAction(Uri contactUri) {
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 0);
            getContentResolver().update(contactUri, values, null, null);
        }

        public void onCallContactAction(Uri contactUri) {
            getPhoneNumberCallInteraction().startInteraction(contactUri);
        }

        public void onSmsContactAction(Uri contactUri) {
            getSendTextMessageInteraction().startInteraction(contactUri);
        }

        public void onDeleteContactAction(Uri contactUri) {
            getContactDeletionInteraction().deleteContact(contactUri);
        }

        public void onFinishAction() {
            onBackPressed();
        }
    }

    private class DetailFragmentListener implements ContactDetailFragment.Listener {
        @Override
        public void onContactNotFound() {
            setupContactDetailFragment(null);
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            setupContactEditorFragment(contactLookupUri);
        }

        @Override
        public void onItemClicked(Intent intent) {
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }

        @Override
        public void onDeleteRequested(Uri contactLookupUri) {
            getContactDeletionInteraction().deleteContact(contactLookupUri);
        }
    }

    private class EditorFragmentListener implements ContactEditorFragment.Listener {
        @Override
        public void onReverted() {
            final Uri uri = mEditorFragment.getLookupUri();
            closeEditorFragment(false);
            setupContactDetailFragment(uri);
        }

        @Override
        public void onSaveFinished(int resultCode, Intent resultIntent) {
            Log.d(TAG, "onSaveFinished(" + resultCode + "," + resultIntent + ")");
            // it is already saved, so no need to save again here
            final Uri uri = mEditorFragment.getLookupUri();
            closeEditorFragment(false);
            setupContactDetailFragment(uri);
        }

        @Override
        public void onAggregationChangeFinished(Uri newLookupUri) {
            // We have already saved. Close the editor so that we can open again with the
            // new contact
            Log.d(TAG, "onAggregationChangeFinished(" + newLookupUri + ")");
            closeEditorFragment(false);
            mListFragment.setSelectedContactUri(newLookupUri);
            setupContactDetailFragment(newLookupUri);
        }

        @Override
        public void onAccountSelectorAborted() {
            Toast.makeText(ContactBrowserActivity.this, "closeBecauseAccountSelectorAborted",
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void onContactNotFound() {
            setupContactDetailFragment(null);
        }

        @Override
        public void setTitleTo(int resourceId) {
        }

        @Override
        public void onDeleteRequested(Uri contactLookupUri) {
            getContactDeletionInteraction().deleteContact(contactLookupUri);
        }
    }

    public void startActivityAndForwardResult(final Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Forward extras to the new activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        if (mHasActionBar) {
            inflater.inflate(R.menu.actions, menu);
            return true;
        } else if (mRequest.getActionCode() == ContactsRequest.ACTION_DEFAULT ||
                mRequest.getActionCode() == ContactsRequest.ACTION_STREQUENT) {
            inflater.inflate(R.menu.list, menu);
            return true;
        } else if (!mListFragment.isSearchMode()) {
            inflater.inflate(R.menu.search, menu);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem displayGroups = menu.findItem(R.id.menu_display_groups);
        if (displayGroups != null) {
            displayGroups.setVisible(
                    mRequest.getActionCode() == ContactsRequest.ACTION_DEFAULT);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_display_groups: {
                final Intent intent = new Intent(this, ContactsPreferencesActivity.class);
                startActivityForResult(intent, SUBACTIVITY_DISPLAY_GROUP);
                return true;
            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_add: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                startActivityForResult(intent, SUBACTIVITY_NEW_CONTACT);
                return true;
            }
            case R.id.menu_import_export: {
                getImportExportInteraction().startInteraction();
                return true;
            }
            case R.id.menu_accounts: {
                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.putExtra(Settings.EXTRA_AUTHORITIES, new String[] {
                    ContactsContract.AUTHORITY
                });
                startActivity(intent);
                return true;
            }
        }
        return false;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
// TODO
//        if (mProviderStatus != ProviderStatus.STATUS_NORMAL) {
//            return;
//        }

        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            mListFragment.startSearch(initialQuery);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, bundle);

        Dialog dialog = getContactDeletionInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        dialog = getPhoneNumberCallInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        dialog = getSendTextMessageInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        dialog = getImportExportInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        return super.onCreateDialog(id, bundle);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        if (getContactDeletionInteraction().onPrepareDialog(id, dialog, bundle)) {
            return;
        }

        if (getPhoneNumberCallInteraction().onPrepareDialog(id, dialog, bundle)) {
            return;
        }

        if (getSendTextMessageInteraction().onPrepareDialog(id, dialog, bundle)) {
            return;
        }

        super.onPrepareDialog(id, dialog, bundle);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SUBACTIVITY_NEW_CONTACT: {
                if (resultCode == RESULT_OK && mContactContentDisplayed) {
                    final Uri newContactUri = data.getData();
                    mListFragment.setSelectedContactUri(newContactUri);
                    setupContactDetailFragment(newContactUri);
                }
                break;
            }

            case SUBACTIVITY_DISPLAY_GROUP:
                // TODO: Force the ListFragment to reload its setting and update (don't lookup
                // directories here)
                break;

            // TODO: Using the new startActivityWithResultFromFragment API this should not be needed
            // anymore
            case ContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER:
                if (resultCode == RESULT_OK) {
                    mListFragment.onPickerResult(data);
                }

// TODO fix or remove multipicker code
//                else if (resultCode == RESULT_CANCELED && mMode == MODE_PICK_MULTIPLE_PHONES) {
//                    // Finish the activity if the sub activity was canceled as back key is used
//                    // to confirm user selection in MODE_PICK_MULTIPLE_PHONES.
//                    finish();
//                }
//                break;
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenuAdapter menuAdapter = mListFragment.getContextMenuAdapter();
        if (menuAdapter != null) {
            return menuAdapter.onContextItemSelected(item);
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO move to the fragment
        switch (keyCode) {
//            case KeyEvent.KEYCODE_CALL: {
//                if (callSelection()) {
//                    return true;
//                }
//                break;
//            }

            case KeyEvent.KEYCODE_DEL: {
                if (deleteSelection()) {
                    return true;
                }
                break;
            }
            default: {
                // Bring up the search UI if the user starts typing
                final int unicodeChar = event.getUnicodeChar();

                if (unicodeChar != 0) {
                    String query = new String(new int[]{ unicodeChar }, 0, 1);
                    if (mHasActionBar) {
                        if (mActionBarAdapter.getMode() != ContactBrowserMode.MODE_SEARCH) {
                            mActionBarAdapter.setQueryString(query);
                            mActionBarAdapter.setMode(ContactBrowserMode.MODE_SEARCH);
                            return true;
                        }
                    } else if (!mRequest.isSearchMode()) {
                        if (!mSearchInitiated) {
                            mSearchInitiated = true;
                            startSearch(query, false, null, false);
                            return true;
                        }
                    }
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private boolean deleteSelection() {
        // TODO move to the fragment
//        if (mActionCode == ContactsRequest.ACTION_DEFAULT) {
//            final int position = mListView.getSelectedItemPosition();
//            if (position != ListView.INVALID_POSITION) {
//                Uri contactUri = getContactUri(position);
//                if (contactUri != null) {
//                    doContactDelete(contactUri);
//                    return true;
//                }
//            }
//        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_MODE, mMode);
        if (mActionBarAdapter != null) {
            mActionBarAdapter.onSaveInstanceState(outState);
        }
    }

    private PhoneNumberInteraction getPhoneNumberCallInteraction() {
        if (mPhoneNumberCallInteraction == null) {
            mPhoneNumberCallInteraction = new PhoneNumberInteraction(this, false, null);
        }
        return mPhoneNumberCallInteraction;
    }

    private PhoneNumberInteraction getSendTextMessageInteraction() {
        if (mSendTextMessageInteraction == null) {
            mSendTextMessageInteraction = new PhoneNumberInteraction(this, true, null);
        }
        return mSendTextMessageInteraction;
    }

    private ContactDeletionInteraction getContactDeletionInteraction() {
        if (mContactDeletionInteraction == null) {
            mContactDeletionInteraction = new ContactDeletionInteraction();
            mContactDeletionInteraction.attachToActivity(this);
        }
        return mContactDeletionInteraction;
    }

    private ImportExportInteraction getImportExportInteraction() {
        if (mImportExportInteraction == null) {
            mImportExportInteraction = new ImportExportInteraction(this);
        }
        return mImportExportInteraction;
    }

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }
}
