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

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ExpandableListActivity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Settings;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CursorTreeAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;

import com.google.android.collect.Sets;

import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.util.WeakAsyncTask;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Shows a list of all available {@link Groups} available, letting the user
 * select which ones they want to be visible.
 */
public final class DisplayGroupsActivity extends ExpandableListActivity implements
        AdapterView.OnItemClickListener {
    private static final String TAG = "DisplayGroupsActivity";

    private static final int UNGROUPED_ID = -2;

    public interface Prefs {
        public static final String DISPLAY_ONLY_PHONES = "only_phones";
        public static final boolean DISPLAY_ONLY_PHONES_DEFAULT = false;

    }

    private ExpandableListView mList;
    private DisplayGroupsAdapter mAdapter;

    private SharedPreferences mPrefs;

    private CheckBox mDisplayPhones;

    private View mHeaderPhones;
    private View mHeaderSeparator;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(android.R.layout.expandable_list_content);

        mList = getExpandableListView();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        final LayoutInflater inflater = getLayoutInflater();

        // Add the "Only contacts with phones" header modifier.
        mHeaderPhones = inflater.inflate(R.layout.display_header, mList, false);
        mHeaderPhones.setId(R.id.header_phones);
        mDisplayPhones = (CheckBox) mHeaderPhones.findViewById(android.R.id.checkbox);
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

        mAdapter = new DisplayGroupsAdapter(null, this, this);

        boolean displayOnlyPhones = mPrefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                Prefs.DISPLAY_ONLY_PHONES_DEFAULT);

        mDisplayPhones.setChecked(displayOnlyPhones);

        mAdapter.setChildDescripWithPhones(displayOnlyPhones);

        setListAdapter(mAdapter);

        // Catch clicks on the header views
        mList.setOnItemClickListener(this);
        mList.setOnCreateContextMenuListener(this);

        // Start background query to find account details
        new QuerySettingsTask(this).execute();
    }

    private static class QuerySettingsTask extends
            WeakAsyncTask<Void, Void, Cursor, DisplayGroupsActivity> {
        public QuerySettingsTask(DisplayGroupsActivity target) {
            super(target);
        }

        @Override
        protected Cursor doInBackground(DisplayGroupsActivity target, Void... params) {
            final Context context = target;
            final Sources sources = Sources.getInstance(context);

            // Query to find Settings for all data sources
            final ContentResolver resolver = context.getContentResolver();
            final Cursor cursor = resolver.query(ContactsContract.Settings.CONTENT_URI,
                    SettingsQuery.PROJECTION, null, null, null);
            target.startManagingCursor(cursor);

            // Make records for each account known by Settings
            final HashSet<Account> knownAccounts = Sets.newHashSet();
            while (cursor.moveToNext()) {
                final String accountName = cursor.getString(SettingsQuery.ACCOUNT_NAME);
                final String accountType = cursor.getString(SettingsQuery.ACCOUNT_TYPE);
                final Account account = new Account(accountName, accountType);
                knownAccounts.add(account);
            }

            // Assert that Settings exist for each data source
            boolean changedSettings = false;
            final ArrayList<Account> expectedAccounts = sources.getAccounts(false);
            for (Account account : expectedAccounts) {
                if (!knownAccounts.contains(account)) {
                    // Expected account that doesn't exist yet in Settings
                    final ContentValues values = new ContentValues();
                    values.put(Settings.ACCOUNT_NAME, account.name);
                    values.put(Settings.ACCOUNT_TYPE, account.type);
                    resolver.insert(Settings.CONTENT_URI, values);

                    // Make sure we requery to catch this insert
                    changedSettings = true;
                }
            }

            if (changedSettings) {
                // Catch any new sources discovered above
                cursor.requery();
            }

            // Wrap cursor to provide _id column
            final Cursor settingsCursor = new CursorWrapper(cursor) {
                @Override
                public long getLong(int columnIndex) {
                    if (columnIndex == -1) {
                        return this.getPosition();
                    } else {
                        return super.getLong(columnIndex);
                    }
                }
            };

            return settingsCursor;
        }

        @Override
        protected void onPostExecute(DisplayGroupsActivity target, Cursor result) {
            // Update cursor for data sources
            target.mAdapter.setGroupCursor(result);
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
                setDisplayOnlyPhones(mDisplayPhones.isChecked());
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
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
            int childPosition, long id) {
        final CheckBox checkbox = (CheckBox)v.findViewById(android.R.id.checkbox);
        checkbox.toggle();

        // Build visibility update and send down to database
        final ContentResolver resolver = getContentResolver();
        final ContentValues values = new ContentValues();

        // TODO: heavy update, perhaps push to background query
        if (id != UNGROUPED_ID) {
            // Handle persisting for normal group
            values.put(Groups.GROUP_VISIBLE, checkbox.isChecked() ? 1 : 0);

            final Uri groupUri = ContentUris.withAppendedId(Groups.CONTENT_URI, id);
            final int count = resolver.update(groupUri, values, null, null);
        } else {
            // Handle persisting for ungrouped through Settings
            values.put(Settings.UNGROUPED_VISIBLE, checkbox.isChecked() ? 1 : 0);

            final Cursor settings = mAdapter.getGroup(groupPosition);
            final int count = resolver.update(Settings.CONTENT_URI, values, Groups.ACCOUNT_NAME
                    + "=? AND " + Groups.ACCOUNT_TYPE + "=?", new String[] {
                    settings.getString(SettingsQuery.ACCOUNT_NAME),
                    settings.getString(SettingsQuery.ACCOUNT_TYPE)
            });
        }

        return true;
    }

    // TODO: move these definitions to framework constants when we begin
    // defining this mode through <sync-adapter> tags
    private static final int SYNC_MODE_UNSUPPORTED = 0;
    private static final int SYNC_MODE_UNGROUPED = 1;
    private static final int SYNC_MODE_EVERYTHING = 2;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        // Bail if not working with expandable long-press, or if not child
        if (!(menuInfo instanceof ExpandableListContextMenuInfo)) return;

        final ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
        final int groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        final int childPosition = ExpandableListView.getPackedPositionChild(info.packedPosition);

        final Cursor groupCursor = mAdapter.getGroup(groupPosition);
        // TODO: read sync mode through <sync-adapter> definition
        final int syncMode = SYNC_MODE_EVERYTHING;

        // Ignore when selective syncing unsupported
        if (syncMode == SYNC_MODE_UNSUPPORTED) return;

        final String accountName = groupCursor.getString(SettingsQuery.ACCOUNT_NAME);
        final String accountType = groupCursor.getString(SettingsQuery.ACCOUNT_TYPE);
        final Account account = new Account(accountName, accountType);

        if (childPosition == -1) {
            // Show add dialog for this overall source
            showAddSync(menu, groupCursor, account, syncMode);

        } else {
            // Show remove dialog for this specific group
            final Cursor childCursor = mAdapter.getChild(groupPosition, childPosition);
            showRemoveSync(menu, account, childCursor, syncMode);
        }
    }

    protected void showRemoveSync(ContextMenu menu, final Account account, Cursor childCursor,
            final int syncMode) {
        final long groupId = childCursor.getLong(GroupsQuery._ID);
        final CharSequence title = getGroupTitle(this, childCursor);

        menu.setHeaderTitle(title);
        menu.add(R.string.menu_sync_remove).setOnMenuItemClickListener(
                new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        handleRemoveSync(groupId, account, syncMode, title);
                        return true;
                    }
                });
    }

    protected void handleRemoveSync(final long groupId, final Account account, final int syncMode,
            CharSequence title) {
        if (syncMode == SYNC_MODE_EVERYTHING && groupId != UNGROUPED_ID) {
            // Warn before removing this group when it would cause ungrouped to stop syncing
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final CharSequence removeMessage = this.getString(
                    R.string.display_warn_remove_ungrouped, title);
            builder.setMessage(removeMessage);
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // Mark this group to not sync
                    setGroupShouldSync(groupId, account, syncMode, false);
                }
            });
            builder.show();
        } else {
            // Mark this group to not sync
            setGroupShouldSync(groupId, account, syncMode, false);
        }
    }

    protected void showAddSync(ContextMenu menu, Cursor groupCursor, final Account account, final int syncMode) {
        menu.setHeaderTitle(R.string.menu_sync_add);

        // Create single "Ungrouped" item when not synced
        final boolean ungroupedAvailable = groupCursor.getInt(SettingsQuery.SHOULD_SYNC) == 0;
        if (ungroupedAvailable) {
            menu.add(R.string.display_ungrouped).setOnMenuItemClickListener(
                    new OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            // Adding specific group for syncing
                            setGroupShouldSync(UNGROUPED_ID, account, syncMode, true);
                            return true;
                        }
                    });
        }

        // Create item for each available, unsynced group
        final Cursor availableGroups = this.managedQuery(Groups.CONTENT_SUMMARY_URI,
                GroupsQuery.PROJECTION, Groups.SHOULD_SYNC + "=0", null);
        while (availableGroups.moveToNext()) {
            // Create item this unsynced group
            final long groupId = availableGroups.getLong(GroupsQuery._ID);
            final CharSequence title = getGroupTitle(this, availableGroups);
            menu.add(title).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    // Adding specific group for syncing
                    setGroupShouldSync(groupId, account, syncMode, true);
                    return true;
                }
            });
        }
    }

    /**
     * Mark the {@link Groups#SHOULD_SYNC} state of the given group.
     */
    protected void setGroupShouldSync(long groupId, Account account, int syncMode, boolean shouldSync) {
        final ContentResolver resolver = getContentResolver();
        final ContentValues values = new ContentValues();

        if (syncMode == SYNC_MODE_UNSUPPORTED) {
            // Ignore changes when source doesn't support syncing
            return;
        }

        if (groupId == UNGROUPED_ID) {
            // Updating the overall syncing flag for this account
            values.put(Settings.SHOULD_SYNC, shouldSync ? 1 : 0);
            resolver.update(Settings.CONTENT_URI, values, Settings.ACCOUNT_NAME + "=? AND "
                    + Settings.ACCOUNT_TYPE + "=?", new String[] {
                    account.name, account.type
            });

            if (syncMode == SYNC_MODE_EVERYTHING && shouldSync) {
                // If syncing mode is everything, force-enable all children groups
                values.clear();
                values.put(Groups.SHOULD_SYNC, shouldSync ? 1 : 0);
                resolver.update(Groups.CONTENT_URI, values, Groups.ACCOUNT_NAME + "=? AND "
                        + Groups.ACCOUNT_TYPE + "=?", new String[] {
                        account.name, account.type
                });
            }
        } else {
            // Treat as normal group
            values.put(Groups.SHOULD_SYNC, shouldSync ? 1 : 0);
            resolver.update(Groups.CONTENT_URI, values, Groups._ID + "=" + groupId, null);

            if (syncMode == SYNC_MODE_EVERYTHING && !shouldSync) {
                // Remove "everything" from sync, user has already been warned
                values.clear();
                values.put(Settings.SHOULD_SYNC, shouldSync ? 1 : 0);
                resolver.update(Settings.CONTENT_URI, values, Settings.ACCOUNT_NAME + "=? AND "
                        + Settings.ACCOUNT_TYPE + "=?", new String[] {
                        account.name, account.type
                });
            }
        }
    }

    /**
     * Return the best title for the {@link Groups} entry at the current
     * {@link Cursor} position.
     */
    protected static CharSequence getGroupTitle(Context context, Cursor cursor) {
        final PackageManager pm = context.getPackageManager();
        if (!cursor.isNull(GroupsQuery.TITLE_RES)) {
            final String packageName = cursor.getString(GroupsQuery.RES_PACKAGE);
            final int titleRes = cursor.getInt(GroupsQuery.TITLE_RES);
            return pm.getText(packageName, titleRes, null);
        } else {
            return cursor.getString(GroupsQuery.TITLE);
        }
    }

    /**
     * Special {@link Cursor} that shows zero or one items based on
     * {@link Settings#SHOULD_SYNC} value. This header only supports
     * {@link #SYNC_MODE_UNGROUPED} and {@link #SYNC_MODE_UNSUPPORTED}.
     */
    private static class HeaderCursor extends AbstractCursor {
        private Context mContext;
        private Cursor mCursor;
        private int mPosition;

        public HeaderCursor(Context context, Cursor cursor, int position) {
            mContext = context;
            mCursor = cursor;
            mPosition = position;
        }

        @Override
        public int getCount() {
            assertParent();

            final boolean shouldSync = mCursor.getInt(SettingsQuery.SHOULD_SYNC) != 0;
            return shouldSync ? 1 : 0;
        }

        @Override
        public String[] getColumnNames() {
            return GroupsQuery.PROJECTION;
        }

        protected void assertParent() {
            mCursor.moveToPosition(mPosition);
        }

        @Override
        public String getString(int column) {
            assertParent();
            switch(column) {
                case GroupsQuery.ACCOUNT_NAME:
                    return mCursor.getString(SettingsQuery.ACCOUNT_NAME);
                case GroupsQuery.ACCOUNT_TYPE:
                    return mCursor.getString(SettingsQuery.ACCOUNT_TYPE);
                case GroupsQuery.TITLE:
                    return null;
                case GroupsQuery.RES_PACKAGE:
                    return mContext.getPackageName();
                case GroupsQuery.TITLE_RES:
                    return Integer.toString(UNGROUPED_ID);
            }
            throw new IllegalArgumentException("Requested column not available as string");
        }

        @Override
        public short getShort(int column) {
            throw new IllegalArgumentException("Requested column not available as short");
        }

        @Override
        public int getInt(int column) {
            assertParent();
            switch(column) {
                case GroupsQuery._ID:
                    return UNGROUPED_ID;
                case GroupsQuery.TITLE_RES:
                    return R.string.display_ungrouped;
                case GroupsQuery.GROUP_VISIBLE:
                    return mCursor.getInt(SettingsQuery.UNGROUPED_VISIBLE);
//                case GroupsQuery.SUMMARY_COUNT:
//                    return mCursor.getInt(SettingsQuery.UNGROUPED_COUNT);
//                case GroupsQuery.SUMMARY_WITH_PHONES:
//                    return mCursor.getInt(SettingsQuery.UNGROUPED_WITH_PHONES);
            }
            throw new IllegalArgumentException("Requested column not available as int");
        }

        @Override
        public long getLong(int column) {
            return getInt(column);
        }

        @Override
        public float getFloat(int column) {
            throw new IllegalArgumentException("Requested column not available as float");
        }

        @Override
        public double getDouble(int column) {
            throw new IllegalArgumentException("Requested column not available as double");
        }

        @Override
        public boolean isNull(int column) {
            return getString(column) == null;
        }
    }

    /**
     * Adapter that shows all display groups as returned by a {@link Cursor}
     * over {@link Groups#CONTENT_SUMMARY_URI}, along with their current visible
     * status. Splits groups into sections based on {@link Account}.
     */
    private static class DisplayGroupsAdapter extends CursorTreeAdapter {
        private Context mContext;
        private Activity mActivity;
        private LayoutInflater mInflater;
        private Sources mSources;

        private boolean mChildWithPhones = false;

        public DisplayGroupsAdapter(Cursor cursor, Context context, Activity activity) {
            super(cursor, context, true);

            mContext = context;
            mActivity = activity;
            mSources = Sources.getInstance(mContext);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        /**
         * In group descriptions, show the number of contacts with phone
         * numbers, in addition to the total contacts.
         */
        public void setChildDescripWithPhones(boolean withPhones) {
            mChildWithPhones = withPhones;
        }

        @Override
        protected View newGroupView(Context context, Cursor cursor, boolean isExpanded,
                ViewGroup parent) {
            return mInflater.inflate(R.layout.display_group, parent, false);
        }

        @Override
        protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
            final TextView text1 = (TextView)view.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)view.findViewById(android.R.id.text2);

            final String accountName = cursor.getString(SettingsQuery.ACCOUNT_NAME);
            final String accountType = cursor.getString(SettingsQuery.ACCOUNT_TYPE);

            final ContactsSource source = mSources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_SUMMARY);

            text1.setText(source.getDisplayLabel(mContext));
            text2.setText(accountName);
            text2.setVisibility(accountName == null ? View.GONE : View.VISIBLE);
        }

        @Override
        protected Cursor getChildrenCursor(Cursor groupCursor) {
            final String selection = Groups.ACCOUNT_NAME + "=? AND " + Groups.ACCOUNT_TYPE
                    + "=? AND " + Groups.SHOULD_SYNC + "=1";
            final String[] selectionArgs = new String[] {
                    groupCursor.getString(SettingsQuery.ACCOUNT_NAME),
                    groupCursor.getString(SettingsQuery.ACCOUNT_TYPE)
            };

            final int position = groupCursor.getPosition();
            final Cursor ungroupedCursor = new HeaderCursor(mContext, groupCursor, position);

            final ContentResolver resolver = mContext.getContentResolver();
            final Cursor groupsCursor = resolver.query(Groups.CONTENT_SUMMARY_URI,
                    GroupsQuery.PROJECTION, selection, selectionArgs, null);
            mActivity.startManagingCursor(groupsCursor);

            return new MergeCursor(new Cursor[] { ungroupedCursor, groupsCursor });
        }

        @Override
        protected View newChildView(Context context, Cursor cursor, boolean isLastChild,
                ViewGroup parent) {
            return mInflater.inflate(R.layout.display_child, parent, false);
        }

        @Override
        protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
            final TextView text1 = (TextView)view.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)view.findViewById(android.R.id.text2);
            final CheckBox checkbox = (CheckBox)view.findViewById(android.R.id.checkbox);

//            final int count = cursor.getInt(GroupsQuery.SUMMARY_COUNT);
//            final int withPhones = cursor.getInt(GroupsQuery.SUMMARY_WITH_PHONES);
            final int membersVisible = cursor.getInt(GroupsQuery.GROUP_VISIBLE);

            // Read title, but override with string resource when present
            final CharSequence title = getGroupTitle(mContext, cursor);
//            final CharSequence descrip = mContext.getResources().getQuantityString(
//                    mChildWithPhones ? R.plurals.groupDescripPhones : R.plurals.groupDescrip,
//                    count, count, withPhones);

            text1.setText(title);
//            text2.setText(descrip);
            checkbox.setChecked((membersVisible == 1));
        }
    }

    private interface SettingsQuery {
        final String[] PROJECTION = new String[] {
                Settings.ACCOUNT_NAME,
                Settings.ACCOUNT_TYPE,
                Settings.SHOULD_SYNC,
                Settings.UNGROUPED_VISIBLE,
//                Settings.UNGROUPED_COUNT,
//                Settings.UNGROUPED_WITH_PHONES,
        };

        final int ACCOUNT_NAME = 0;
        final int ACCOUNT_TYPE = 1;
        final int SHOULD_SYNC = 2;
        final int UNGROUPED_VISIBLE = 3;
//        final int UNGROUPED_COUNT = 4;
//        final int UNGROUPED_WITH_PHONES = 5;
    }

    private interface GroupsQuery {
        final String[] PROJECTION = new String[] {
            Groups._ID,
            Groups.TITLE,
            Groups.RES_PACKAGE,
            Groups.TITLE_RES,
            Groups.GROUP_VISIBLE,
//            Groups.SUMMARY_COUNT,
//            Groups.SUMMARY_WITH_PHONES,
            Groups.ACCOUNT_NAME,
            Groups.ACCOUNT_TYPE,
        };

        final int _ID = 0;
        final int TITLE = 1;
        final int RES_PACKAGE = 2;
        final int TITLE_RES = 3;
        final int GROUP_VISIBLE = 4;
//        final int SUMMARY_COUNT = 5;
//        final int SUMMARY_WITH_PHONES = 6;
        final int ACCOUNT_NAME = 5;
        final int ACCOUNT_TYPE = 6;
    }
}
