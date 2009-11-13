/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.ui;

import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.GoogleSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.util.EmptyService;
import com.android.contacts.util.WeakAsyncTask;
import com.google.android.collect.Lists;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ExpandableListActivity;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.ContentProviderOperation.Builder;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Settings;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Shows a list of all available {@link Groups} available, letting the user
 * select which ones they want to be visible.
 */
public final class DisplayGroupsActivity extends ExpandableListActivity implements
        AdapterView.OnItemClickListener, View.OnClickListener {
    private static final String TAG = "DisplayGroupsActivity";

    public interface Prefs {
        public static final String DISPLAY_ONLY_PHONES = "only_phones";
        public static final boolean DISPLAY_ONLY_PHONES_DEFAULT = false;

    }

    private ExpandableListView mList;
    private DisplayAdapter mAdapter;

    private SharedPreferences mPrefs;

    private CheckBox mDisplayPhones;

    private View mHeaderPhones;
    private View mHeaderSeparator;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.act_display_groups);

        mList = getExpandableListView();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        final LayoutInflater inflater = getLayoutInflater();

        // Add the "Only contacts with phones" header modifier.
        mHeaderPhones = inflater.inflate(R.layout.display_header, mList, false);
        mHeaderPhones.setId(R.id.header_phones);
        mDisplayPhones = (CheckBox) mHeaderPhones.findViewById(android.R.id.checkbox);
        mDisplayPhones.setChecked(mPrefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                Prefs.DISPLAY_ONLY_PHONES_DEFAULT));
        {
            final TextView text1 = (TextView)mHeaderPhones.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)mHeaderPhones.findViewById(android.R.id.text2);
            text1.setText(R.string.showFilterPhones);
            text2.setText(R.string.showFilterPhonesDescrip);
        }
        mList.addHeaderView(mHeaderPhones, null, true);

        // Add the separator before showing the detailed group list.
        mHeaderSeparator = inflater.inflate(R.layout.list_separator, mList, false);
        {
            final TextView text1 = (TextView)mHeaderSeparator;
            text1.setText(R.string.headerContactGroups);
        }
        mList.addHeaderView(mHeaderSeparator, null, false);

        findViewById(R.id.btn_done).setOnClickListener(this);
        findViewById(R.id.btn_discard).setOnClickListener(this);

        // Catch clicks on the header views
        mList.setOnItemClickListener(this);
        mList.setOnCreateContextMenuListener(this);

        // Start background query to find account details
        new QueryGroupsTask(this).execute();
    }

    /**
     * Background operation to build set of {@link AccountDisplay} for each
     * {@link Sources#getAccounts(boolean)} that provides groups.
     */
    private static class QueryGroupsTask extends
            WeakAsyncTask<Void, Void, AccountSet, DisplayGroupsActivity> {
        public QueryGroupsTask(DisplayGroupsActivity target) {
            super(target);
        }

        @Override
        protected AccountSet doInBackground(DisplayGroupsActivity target,
                Void... params) {
            final Context context = target;
            final Sources sources = Sources.getInstance(context);
            final ContentResolver resolver = context.getContentResolver();

            // Inflate groups entry for each account
            final AccountSet accounts = new AccountSet();
            for (Account account : sources.getAccounts(false)) {
                accounts.add(new AccountDisplay(resolver, account.name, account.type));
            }

            return accounts;
        }

        @Override
        protected void onPostExecute(DisplayGroupsActivity target, AccountSet result) {
            // Build adapter to show available groups
            final Context context = target;
            final DisplayAdapter adapter = new DisplayAdapter(context, result);
            target.setListAdapter(adapter);
        }
    }

    public void setListAdapter(DisplayAdapter adapter) {
        mAdapter = adapter;
        mAdapter.setChildDescripWithPhones(mDisplayPhones.isChecked());
        super.setListAdapter(mAdapter);
    }

    private static final int DEFAULT_SHOULD_SYNC = 1;
    private static final int DEFAULT_VISIBLE = 0;

    /**
     * Entry holding any changes to {@link Groups} or {@link Settings} rows,
     * such as {@link Groups#SHOULD_SYNC} or {@link Groups#GROUP_VISIBLE}.
     */
    protected static class GroupDelta extends ValuesDelta {
        private boolean mUngrouped = false;
        private boolean mAccountHasGroups;

        private GroupDelta() {
            super();
        }

        /**
         * Build {@link GroupDelta} from the {@link Settings} row for the given
         * {@link Settings#ACCOUNT_NAME} and {@link Settings#ACCOUNT_TYPE}.
         */
        public static GroupDelta fromSettings(ContentResolver resolver, String accountName,
                String accountType, boolean accountHasGroups) {
            final Uri settingsUri = Settings.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Settings.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(Settings.ACCOUNT_TYPE, accountType).build();
            final Cursor cursor = resolver.query(settingsUri, new String[] {
                    Settings.SHOULD_SYNC, Settings.UNGROUPED_VISIBLE
            }, null, null, null);

            try {
                final ContentValues values = new ContentValues();
                values.put(Settings.ACCOUNT_NAME, accountName);
                values.put(Settings.ACCOUNT_TYPE, accountType);

                if (cursor != null && cursor.moveToFirst()) {
                    // Read existing values when present
                    values.put(Settings.SHOULD_SYNC, cursor.getInt(0));
                    values.put(Settings.UNGROUPED_VISIBLE, cursor.getInt(1));
                    return fromBefore(values).setUngrouped(accountHasGroups);
                } else {
                    // Nothing found, so treat as create
                    values.put(Settings.SHOULD_SYNC, DEFAULT_SHOULD_SYNC);
                    values.put(Settings.UNGROUPED_VISIBLE, DEFAULT_VISIBLE);
                    return fromAfter(values).setUngrouped(accountHasGroups);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        public static GroupDelta fromBefore(ContentValues before) {
            final GroupDelta entry = new GroupDelta();
            entry.mBefore = before;
            entry.mAfter = new ContentValues();
            return entry;
        }

        public static GroupDelta fromAfter(ContentValues after) {
            final GroupDelta entry = new GroupDelta();
            entry.mBefore = null;
            entry.mAfter = after;
            return entry;
        }

        protected GroupDelta setUngrouped(boolean accountHasGroups) {
            mUngrouped = true;
            mAccountHasGroups = accountHasGroups;
            return this;
        }

        @Override
        public boolean beforeExists() {
            return mBefore != null;
        }

        public boolean getShouldSync() {
            return getAsInteger(mUngrouped ? Settings.SHOULD_SYNC : Groups.SHOULD_SYNC,
                    DEFAULT_SHOULD_SYNC) != 0;
        }

        public boolean getVisible() {
            return getAsInteger(mUngrouped ? Settings.UNGROUPED_VISIBLE : Groups.GROUP_VISIBLE,
                    DEFAULT_VISIBLE) != 0;
        }

        public void putShouldSync(boolean shouldSync) {
            put(mUngrouped ? Settings.SHOULD_SYNC : Groups.SHOULD_SYNC, shouldSync ? 1 : 0);
        }

        public void putVisible(boolean visible) {
            put(mUngrouped ? Settings.UNGROUPED_VISIBLE : Groups.GROUP_VISIBLE, visible ? 1 : 0);
        }

        public CharSequence getTitle(Context context) {
            if (mUngrouped) {
                if (mAccountHasGroups) {
                    return context.getText(R.string.display_ungrouped);
                } else {
                    return context.getText(R.string.display_all_contacts);
                }
            } else {
                final Integer titleRes = getAsInteger(Groups.TITLE_RES);
                if (titleRes != null) {
                    final String packageName = getAsString(Groups.RES_PACKAGE);
                    return context.getPackageManager().getText(packageName, titleRes, null);
                } else {
                    return getAsString(Groups.TITLE);
                }
            }
        }

        /**
         * Build a possible {@link ContentProviderOperation} to persist any
         * changes to the {@link Groups} or {@link Settings} row described by
         * this {@link GroupDelta}.
         */
        public ContentProviderOperation buildDiff() {
            if (isNoop()) {
                return null;
            } else if (isUpdate()) {
                // When has changes and "before" exists, then "update"
                final Builder builder = ContentProviderOperation
                        .newUpdate(mUngrouped ? Settings.CONTENT_URI : addCallerIsSyncAdapterParameter(Groups.CONTENT_URI));
                if (mUngrouped) {
                    builder.withSelection(Settings.ACCOUNT_NAME + "=? AND " + Settings.ACCOUNT_TYPE
                            + "=?", new String[] {
                            this.getAsString(Settings.ACCOUNT_NAME),
                            this.getAsString(Settings.ACCOUNT_TYPE)
                    });
                } else {
                    builder.withSelection(Groups._ID + "=" + this.getId(), null);
                }
                builder.withValues(mAfter);
                return builder.build();
            } else if (isInsert() && mUngrouped) {
                // Only allow inserts for Settings
                mAfter.remove(mIdColumn);
                final Builder builder = ContentProviderOperation.newInsert(Settings.CONTENT_URI);
                builder.withValues(mAfter);
                return builder.build();
            } else {
                throw new IllegalStateException("Unexpected delete or insert");
            }
        }
    }

    private static Uri addCallerIsSyncAdapterParameter(Uri uri) {
        return uri.buildUpon()
	        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
		.build();
    }

    /**
     * {@link Comparator} to sort by {@link Groups#_ID}.
     */
    private static Comparator<GroupDelta> sIdComparator = new Comparator<GroupDelta>() {
        public int compare(GroupDelta object1, GroupDelta object2) {
            return object1.getViewId() - object2.getViewId();
        }
    };

    /**
     * Set of all {@link AccountDisplay} entries, one for each source.
     */
    protected static class AccountSet extends ArrayList<AccountDisplay> {
        public ArrayList<ContentProviderOperation> buildDiff() {
            final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
            for (AccountDisplay account : this) {
                account.buildDiff(diff);
            }
            return diff;
        }
    }

    /**
     * {@link GroupDelta} details for a single {@link Account}, usually shown as
     * children under a single expandable group.
     */
    protected static class AccountDisplay {
        public String mName;
        public String mType;

        public GroupDelta mUngrouped;
        public ArrayList<GroupDelta> mSyncedGroups = Lists.newArrayList();
        public ArrayList<GroupDelta> mUnsyncedGroups = Lists.newArrayList();

        /**
         * Build an {@link AccountDisplay} covering all {@link Groups} under the
         * given {@link Account}.
         */
        public AccountDisplay(ContentResolver resolver, String accountName, String accountType) {
            mName = accountName;
            mType = accountType;

            boolean hasGroups = false;

            final Uri groupsUri = Groups.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Groups.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(Groups.ACCOUNT_TYPE, accountType).build();
            EntityIterator iterator = null;
            try {
                // Create entries for each known group
                iterator = resolver.queryEntities(groupsUri, null, null, null);
                while (iterator.hasNext()) {
                    final ContentValues values = iterator.next().getEntityValues();
                    final GroupDelta group = GroupDelta.fromBefore(values);
                    addGroup(group);
                    hasGroups = true;
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Problem reading groups: " + e.toString());
            } finally {
                if (iterator != null) iterator.close();
            }

            // Create single entry handling ungrouped status
            mUngrouped = GroupDelta.fromSettings(resolver, accountName, accountType, hasGroups);
            addGroup(mUngrouped);
        }

        /**
         * Add the given {@link GroupDelta} internally, filing based on its
         * {@link GroupDelta#getShouldSync()} status.
         */
        private void addGroup(GroupDelta group) {
            if (group.getShouldSync()) {
                mSyncedGroups.add(group);
            } else {
                mUnsyncedGroups.add(group);
            }
        }

        /**
         * Set the {@link GroupDelta#putShouldSync(boolean)} value for all
         * children {@link GroupDelta} rows.
         */
        public void setShouldSync(boolean shouldSync) {
            final Iterator<GroupDelta> oppositeChildren = shouldSync ?
                    mUnsyncedGroups.iterator() : mSyncedGroups.iterator();
            while (oppositeChildren.hasNext()) {
                final GroupDelta child = oppositeChildren.next();
                setShouldSync(child, shouldSync, false);
                oppositeChildren.remove();
            }
        }

        public void setShouldSync(GroupDelta child, boolean shouldSync) {
            setShouldSync(child, shouldSync, true);
        }

        /**
         * Set {@link GroupDelta#putShouldSync(boolean)}, and file internally
         * based on updated state.
         */
        public void setShouldSync(GroupDelta child, boolean shouldSync, boolean attemptRemove) {
            child.putShouldSync(shouldSync);
            if (shouldSync) {
                if (attemptRemove) {
                    mUnsyncedGroups.remove(child);
                }
                mSyncedGroups.add(child);
                Collections.sort(mSyncedGroups, sIdComparator);
            } else {
                if (attemptRemove) {
                    mSyncedGroups.remove(child);
                }
                mUnsyncedGroups.add(child);
            }
        }

        /**
         * Build set of {@link ContentProviderOperation} to persist any user
         * changes to {@link GroupDelta} rows under this {@link Account}.
         */
        public void buildDiff(ArrayList<ContentProviderOperation> diff) {
            for (GroupDelta group : mSyncedGroups) {
                final ContentProviderOperation oper = group.buildDiff();
                if (oper != null) diff.add(oper);
            }
            for (GroupDelta group : mUnsyncedGroups) {
                final ContentProviderOperation oper = group.buildDiff();
                if (oper != null) diff.add(oper);
            }
        }
    }

    /**
     * {@link ExpandableListAdapter} that shows {@link GroupDelta} settings,
     * grouped by {@link Account} source. Shows footer row when any groups are
     * unsynced, as determined through {@link AccountDisplay#mUnsyncedGroups}.
     */
    protected static class DisplayAdapter extends BaseExpandableListAdapter {
        private Context mContext;
        private LayoutInflater mInflater;
        private Sources mSources;

        private AccountSet mAccounts;

        private boolean mChildWithPhones = false;

        public DisplayAdapter(Context context, AccountSet accounts) {
            mContext = context;
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mSources = Sources.getInstance(context);

            mAccounts = accounts;
        }

        /**
         * In group descriptions, show the number of contacts with phone
         * numbers, in addition to the total contacts.
         */
        public void setChildDescripWithPhones(boolean withPhones) {
            mChildWithPhones = withPhones;
        }

        /** {@inheritDoc} */
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.display_child, parent, false);
            }

            final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);
            final CheckBox checkbox = (CheckBox)convertView.findViewById(android.R.id.checkbox);

            final AccountDisplay account = mAccounts.get(groupPosition);
            final GroupDelta child = (GroupDelta)this.getChild(groupPosition, childPosition);
            if (child != null) {
                // Handle normal group, with title and checkbox
                final boolean groupVisible = child.getVisible();
                checkbox.setVisibility(View.VISIBLE);
                checkbox.setChecked(groupVisible);

                final CharSequence groupTitle = child.getTitle(mContext);
                text1.setText(groupTitle);

//              final int count = cursor.getInt(GroupsQuery.SUMMARY_COUNT);
//              final int withPhones = cursor.getInt(GroupsQuery.SUMMARY_WITH_PHONES);

//              final CharSequence descrip = mContext.getResources().getQuantityString(
//                      mChildWithPhones ? R.plurals.groupDescripPhones : R.plurals.groupDescrip,
//                      count, count, withPhones);

//              text2.setText(descrip);
                text2.setVisibility(View.GONE);
            } else {
                // When unknown child, this is "more" footer view
                checkbox.setVisibility(View.GONE);
                text1.setText(R.string.display_more_groups);
                text2.setVisibility(View.GONE);
            }

            return convertView;
        }

        /** {@inheritDoc} */
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.display_group, parent, false);
            }

            final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);

            final AccountDisplay account = (AccountDisplay)this.getGroup(groupPosition);

            final ContactsSource source = mSources.getInflatedSource(account.mType,
                    ContactsSource.LEVEL_SUMMARY);

            text1.setText(account.mName);
            text2.setText(source.getDisplayLabel(mContext));
            text2.setVisibility(account.mName == null ? View.GONE : View.VISIBLE);

            return convertView;
        }

        /** {@inheritDoc} */
        public Object getChild(int groupPosition, int childPosition) {
            final AccountDisplay account = mAccounts.get(groupPosition);
            final boolean validChild = childPosition >= 0
                    && childPosition < account.mSyncedGroups.size();
            if (validChild) {
                return account.mSyncedGroups.get(childPosition);
            } else {
                return null;
            }
        }

        /** {@inheritDoc} */
        public long getChildId(int groupPosition, int childPosition) {
            final GroupDelta child = (GroupDelta)getChild(groupPosition, childPosition);
            if (child != null) {
                final Long childId = child.getId();
                return childId != null ? childId : Long.MIN_VALUE;
            } else {
                return Long.MIN_VALUE;
            }
        }

        /** {@inheritDoc} */
        public int getChildrenCount(int groupPosition) {
            // Count is any synced groups, plus possible footer
            final AccountDisplay account = mAccounts.get(groupPosition);
            final boolean anyHidden = account.mUnsyncedGroups.size() > 0;
            return account.mSyncedGroups.size() + (anyHidden ? 1 : 0);
        }

        /** {@inheritDoc} */
        public Object getGroup(int groupPosition) {
            return mAccounts.get(groupPosition);
        }

        /** {@inheritDoc} */
        public int getGroupCount() {
            return mAccounts.size();
        }

        /** {@inheritDoc} */
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        /** {@inheritDoc} */
        public boolean hasStableIds() {
            return true;
        }

        /** {@inheritDoc} */
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }

    /**
     * Handle any clicks on header views added to our {@link #mAdapter}, which
     * are usually the global modifier checkboxes.
     */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (view.getId()) {
            case R.id.header_phones: {
                mDisplayPhones.toggle();
                break;
            }
        }
    }

    /** {@inheritDoc} */
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_done: {
                this.doSaveAction();
                break;
            }
            case R.id.btn_discard: {
                this.finish();
                break;
            }
        }
    }

    /**
     * Assign a specific value to {@link Prefs#DISPLAY_ONLY_PHONES}, refreshing
     * the visible list as needed.
     */
    protected void setDisplayOnlyPhones(boolean displayOnlyPhones) {
        mDisplayPhones.setChecked(displayOnlyPhones);

        Editor editor = mPrefs.edit();
        editor.putBoolean(Prefs.DISPLAY_ONLY_PHONES, displayOnlyPhones);
        editor.commit();

        mAdapter.setChildDescripWithPhones(displayOnlyPhones);
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Handle any clicks on {@link ExpandableListAdapter} children, which
     * usually mean toggling its visible state.
     */
    @Override
    public boolean onChildClick(ExpandableListView parent, View view, int groupPosition,
            int childPosition, long id) {
        final CheckBox checkbox = (CheckBox)view.findViewById(android.R.id.checkbox);

        final AccountDisplay account = (AccountDisplay)mAdapter.getGroup(groupPosition);
        final GroupDelta child = (GroupDelta)mAdapter.getChild(groupPosition, childPosition);
        if (child != null) {
            checkbox.toggle();
            child.putVisible(checkbox.isChecked());
        } else {
            // Open context menu for bringing back unsynced
            this.openContextMenu(view);
        }
        return true;
    }

    // TODO: move these definitions to framework constants when we begin
    // defining this mode through <sync-adapter> tags
    private static final int SYNC_MODE_UNSUPPORTED = 0;
    private static final int SYNC_MODE_UNGROUPED = 1;
    private static final int SYNC_MODE_EVERYTHING = 2;

    protected int getSyncMode(AccountDisplay account) {
        // TODO: read sync mode through <sync-adapter> definition
        if (GoogleSource.ACCOUNT_TYPE.equals(account.mType)) {
            return SYNC_MODE_EVERYTHING;
        } else {
            return SYNC_MODE_UNSUPPORTED;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        // Bail if not working with expandable long-press, or if not child
        if (!(menuInfo instanceof ExpandableListContextMenuInfo)) return;

        final ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
        final int groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        final int childPosition = ExpandableListView.getPackedPositionChild(info.packedPosition);

        // Skip long-press on expandable parents
        if (childPosition == -1) return;

        final AccountDisplay account = (AccountDisplay)mAdapter.getGroup(groupPosition);
        final GroupDelta child = (GroupDelta)mAdapter.getChild(groupPosition, childPosition);

        // Ignore when selective syncing unsupported
        final int syncMode = getSyncMode(account);
        if (syncMode == SYNC_MODE_UNSUPPORTED) return;

        if (child != null) {
            showRemoveSync(menu, account, child, syncMode);
        } else {
            showAddSync(menu, account, syncMode);
        }
    }

    protected void showRemoveSync(ContextMenu menu, final AccountDisplay account,
            final GroupDelta child, final int syncMode) {
        final CharSequence title = child.getTitle(this);

        menu.setHeaderTitle(title);
        menu.add(R.string.menu_sync_remove).setOnMenuItemClickListener(
                new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        handleRemoveSync(account, child, syncMode, title);
                        return true;
                    }
                });
    }

    protected void handleRemoveSync(final AccountDisplay account, final GroupDelta child,
            final int syncMode, CharSequence title) {
        final boolean shouldSyncUngrouped = account.mUngrouped.getShouldSync();
        if (syncMode == SYNC_MODE_EVERYTHING && shouldSyncUngrouped
                && !child.equals(account.mUngrouped)) {
            // Warn before removing this group when it would cause ungrouped to stop syncing
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final CharSequence removeMessage = this.getString(
                    R.string.display_warn_remove_ungrouped, title);
            builder.setTitle(R.string.menu_sync_remove);
            builder.setMessage(removeMessage);
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // Mark both this group and ungrouped to stop syncing
                    account.setShouldSync(account.mUngrouped, false);
                    account.setShouldSync(child, false);
                    mAdapter.notifyDataSetChanged();
                }
            });
            builder.show();
        } else {
            // Mark this group to not sync
            account.setShouldSync(child, false);
            mAdapter.notifyDataSetChanged();
        }
    }

    protected void showAddSync(ContextMenu menu, final AccountDisplay account, final int syncMode) {
        menu.setHeaderTitle(R.string.dialog_sync_add);

        // Create item for each available, unsynced group
        for (final GroupDelta child : account.mUnsyncedGroups) {
            if (!child.getShouldSync()) {
                final CharSequence title = child.getTitle(this);
                menu.add(title).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        // Adding specific group for syncing
                        if (child.mUngrouped && syncMode == SYNC_MODE_EVERYTHING) {
                            account.setShouldSync(true);
                        } else {
                            account.setShouldSync(child, true);
                        }
                        mAdapter.notifyDataSetChanged();
                        return true;
                    }
                });
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        doSaveAction();
    }

    private void doSaveAction() {
        if (mAdapter == null) return;
        setDisplayOnlyPhones(mDisplayPhones.isChecked());
        new UpdateTask(this).execute(mAdapter.mAccounts);
    }

    /**
     * Background task that persists changes to {@link Groups#GROUP_VISIBLE},
     * showing spinner dialog to user while updating.
     */
    public static class UpdateTask extends
            WeakAsyncTask<AccountSet, Void, Void, Activity> {
        private WeakReference<ProgressDialog> mProgress;

        public UpdateTask(Activity target) {
            super(target);
        }

        /** {@inheritDoc} */
        @Override
        protected void onPreExecute(Activity target) {
            final Context context = target;

            mProgress = new WeakReference<ProgressDialog>(ProgressDialog.show(context, null,
                    context.getText(R.string.savingDisplayGroups)));

            // Before starting this task, start an empty service to protect our
            // process from being reclaimed by the system.
            context.startService(new Intent(context, EmptyService.class));
        }

        /** {@inheritDoc} */
        @Override
        protected Void doInBackground(Activity target, AccountSet... params) {
            final Context context = target;
            final ContentValues values = new ContentValues();
            final ContentResolver resolver = context.getContentResolver();

            try {
                // Build changes and persist in transaction
                final AccountSet set = params[0];
                final ArrayList<ContentProviderOperation> diff = set.buildDiff();
                resolver.applyBatch(ContactsContract.AUTHORITY, diff);
            } catch (RemoteException e) {
                Log.e(TAG, "Problem saving display groups", e);
            } catch (OperationApplicationException e) {
                Log.e(TAG, "Problem saving display groups", e);
            }

            return null;
        }

        /** {@inheritDoc} */
        @Override
        protected void onPostExecute(Activity target, Void result) {
            final Context context = target;

            final ProgressDialog dialog = mProgress.get();
            if (dialog != null) dialog.dismiss();

            target.finish();

            // Stop the service that was protecting us
            context.stopService(new Intent(context, EmptyService.class));
        }
    }
}
