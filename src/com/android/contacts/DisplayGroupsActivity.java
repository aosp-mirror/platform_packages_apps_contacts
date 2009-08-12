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

package com.android.contacts;

import com.android.contacts.util.NotifyingAsyncQueryHandler;

import android.app.ExpandableListActivity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.EntityIterator;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.GroupsColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Shows a list of all available {@link Groups} available, letting the user
 * select which ones they want to be visible.
 */
public final class DisplayGroupsActivity extends ExpandableListActivity implements
        NotifyingAsyncQueryHandler.AsyncQueryListener, OnItemClickListener {
    private static final String TAG = "DisplayGroupsActivity";

    public interface Prefs {
        public static final String DISPLAY_ALL = "display_all";
        public static final boolean DISPLAY_ALL_DEFAULT = true;

        public static final String DISPLAY_ONLY_PHONES = "only_phones";
        public static final boolean DISPLAY_ONLY_PHONES_DEFAULT = true;

    }

    private ExpandableListView mList;
    private DisplayGroupsAdapter mAdapter;

    private SharedPreferences mPrefs;
    private NotifyingAsyncQueryHandler mHandler;

    private static final int QUERY_TOKEN = 42;

    private View mHeaderAll;
    private View mHeaderPhones;
    private View mHeaderSeparator;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(android.R.layout.expandable_list_content);

        mList = getExpandableListView();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean displayAll = mPrefs.getBoolean(Prefs.DISPLAY_ALL, Prefs.DISPLAY_ALL_DEFAULT);
        boolean displayOnlyPhones = mPrefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                Prefs.DISPLAY_ONLY_PHONES_DEFAULT);

        final LayoutInflater inflater = getLayoutInflater();

        // Add the "All contacts" header modifier.
        mHeaderAll = inflater.inflate(R.layout.display_header, mList, false);
        mHeaderAll.setId(R.id.header_all);
        {
            CheckBox checkbox = (CheckBox)mHeaderAll.findViewById(android.R.id.checkbox);
            TextView text1 = (TextView)mHeaderAll.findViewById(android.R.id.text1);
            checkbox.setChecked(displayAll);
            text1.setText(R.string.showAllGroups);
        }
        mList.addHeaderView(mHeaderAll, null, true);


        // Add the "Only contacts with phones" header modifier.
        mHeaderPhones = inflater.inflate(R.layout.display_header, mList, false);
        mHeaderPhones.setId(R.id.header_phones);
        {
            CheckBox checkbox = (CheckBox)mHeaderPhones.findViewById(android.R.id.checkbox);
            TextView text1 = (TextView)mHeaderPhones.findViewById(android.R.id.text1);
            TextView text2 = (TextView)mHeaderPhones.findViewById(android.R.id.text2);
            checkbox.setChecked(displayOnlyPhones);
            text1.setText(R.string.showFilterPhones);
            text2.setText(R.string.showFilterPhonesDescrip);
        }
        mList.addHeaderView(mHeaderPhones, null, true);


        // Add the separator before showing the detailed group list.
        mHeaderSeparator = inflater.inflate(R.layout.list_separator, mList, false);
        {
            TextView text1 = (TextView)mHeaderSeparator;
            text1.setText(R.string.headerContactGroups);
        }
        mList.addHeaderView(mHeaderSeparator, null, false);


        final TextView allContactsView = (TextView)mHeaderAll.findViewById(android.R.id.text2);

        mAdapter = new DisplayGroupsAdapter(this);
        mAdapter.setAllContactsView(allContactsView);

        mAdapter.setEnabled(!displayAll);
        mAdapter.setChildDescripWithPhones(displayOnlyPhones);

        setListAdapter(mAdapter);

        // Catch clicks on the header views
        mList.setOnItemClickListener(this);

        mHandler = new NotifyingAsyncQueryHandler(this, this);
        startQuery();

    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startQuery();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHandler.cancelOperation(QUERY_TOKEN);
    }


    private void startQuery() {
        mHandler.cancelOperation(QUERY_TOKEN);
        mHandler.startQuery(QUERY_TOKEN, null, Groups.CONTENT_SUMMARY_URI,
                Projections.PROJ_SUMMARY, null, null, Projections.SORT_ORDER);
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        mAdapter.changeCursor(cursor);

        // Expand all data sources
        final int groupCount = mAdapter.getGroupCount();
        for (int i = 0; i < groupCount; i++) {
            mList.expandGroup(i);
        }
    }

    /** {@inheritDoc} */
    public void onQueryEntitiesComplete(int token, Object cookie, EntityIterator iterator) {
        // No actions
    }

    /**
     * Handle any clicks on header views added to our {@link #mAdapter}, which
     * are usually the global modifier checkboxes.
     */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final CheckBox checkbox = (CheckBox)view.findViewById(android.R.id.checkbox);
        switch (view.getId()) {
            case R.id.header_all: {
                checkbox.toggle();
                final boolean displayAll = checkbox.isChecked();

                Editor editor = mPrefs.edit();
                editor.putBoolean(Prefs.DISPLAY_ALL, displayAll);
                editor.commit();

                mAdapter.setEnabled(!displayAll);
                mAdapter.notifyDataSetChanged();

                break;
            }
            case R.id.header_phones: {
                checkbox.toggle();
                final boolean displayOnlyPhones = checkbox.isChecked();

                Editor editor = mPrefs.edit();
                editor.putBoolean(Prefs.DISPLAY_ONLY_PHONES, displayOnlyPhones);
                editor.commit();

                mAdapter.setChildDescripWithPhones(displayOnlyPhones);
                mAdapter.notifyDataSetChanged();

                break;
            }
        }
    }

    /**
     * Handle any clicks on {@link ExpandableListAdapter} children, which
     * usually mean toggling its visible state.
     */
    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
            int childPosition, long id) {
        if (!mAdapter.isEnabled()) {
            return false;
        }

        final CheckBox checkbox = (CheckBox)v.findViewById(android.R.id.checkbox);
        checkbox.toggle();

        // Build visibility update and send down to database
        final ContentResolver resolver = getContentResolver();
        final ContentValues values = new ContentValues();

        values.put(Groups.GROUP_VISIBLE, checkbox.isChecked() ? 1 : 0);

        final long groupId = mAdapter.getChildId(groupPosition, childPosition);
        final Uri groupUri = ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);

        resolver.update(groupUri, values, null, null);

        return true;
    }

    /**
     * Helper for obtaining {@link Resources} instances that are based in an
     * external package. Maintains internal cache to remain fast.
     */
    private static class ExternalResources {
        private Context mContext;
        private HashMap<String, Context> mCache = new HashMap<String, Context>();

        public ExternalResources(Context context) {
            mContext = context;
        }

        private Context getPackageContext(String packageName) throws NameNotFoundException {
            Context theirContext = mCache.get(packageName);
            if (theirContext == null) {
                theirContext = mContext.createPackageContext(packageName, 0);
                mCache.put(packageName, theirContext);
            }
            return theirContext;
        }

        public Resources getResources(String packageName) throws NameNotFoundException {
            return getPackageContext(packageName).getResources();
        }

        public CharSequence getText(String packageName, int stringRes)
                throws NameNotFoundException {
            return getResources(packageName).getText(stringRes);
        }
    }

    /**
     * Adapter that shows all display groups as returned by a {@link Cursor}
     * over {@link Groups#CONTENT_SUMMARY_URI}, along with their current visible
     * status. Splits groups into sections based on {@link Groups#PACKAGE}.
     */
    private static class DisplayGroupsAdapter extends BaseExpandableListAdapter {
        private boolean mDataValid;
        private Cursor mCursor;
        private Context mContext;
        private Resources mResources;
        private ExternalResources mExternalRes;
        private LayoutInflater mInflater;
        private int mRowIDColumn;

        private TextView mAllContactsView;

        private boolean mEnabled = true;
        private boolean mChildWithPhones = false;

        private ContentObserver mContentObserver = new MyChangeObserver();
        private DataSetObserver mDataSetObserver = new MyDataSetObserver();

        /**
         * A single group in our expandable list.
         */
        private static class Group {
            public long packageId = -1;
            public String packageName = null;
            public int firstPos;
            public int lastPos;
            public CharSequence label;
        }

        /**
         * Maintain a list of all groups that need to be displayed by this
         * adapter, usually built by walking across a single {@link Cursor} and
         * finding the {@link Groups#PACKAGE} boundaries.
         */
        private static final ArrayList<Group> mGroups = new ArrayList<Group>();

        public DisplayGroupsAdapter(Context context) {
            mContext = context;
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mResources = context.getResources();
            mExternalRes = new ExternalResources(mContext);
        }

        /**
         * In group descriptions, show the number of contacts with phone
         * numbers, in addition to the total contacts.
         */
        public void setChildDescripWithPhones(boolean withPhones) {
            mChildWithPhones = withPhones;
        }

        /**
         * Set a {@link TextView} to be filled with the total number of contacts
         * across all available groups.
         */
        public void setAllContactsView(TextView allContactsView) {
            mAllContactsView = allContactsView;
        }

        /**
         * Set the {@link View#setEnabled(boolean)} state of any views
         * constructed by this adapter.
         */
        public void setEnabled(boolean enabled) {
            mEnabled = enabled;
        }

        /**
         * Returns the {@link View#setEnabled(boolean)} value being set for any
         * children views of this adapter.
         */
        public boolean isEnabled() {
            return mEnabled;
        }

        /**
         * Used internally to build the {@link #mGroups} mapping. Call when you
         * have a valid cursor and are ready to rebuild the mapping.
         */
        private void buildInternalMapping() {
            final PackageManager pm = mContext.getPackageManager();
            int totalContacts = 0;
            Group group = null;

            mGroups.clear();
            mCursor.moveToPosition(-1);
            while (mCursor.moveToNext()) {
                final int position = mCursor.getPosition();
                final long packageId = mCursor.getLong(Projections.COL_ID);
                totalContacts += mCursor.getInt(Projections.COL_SUMMARY_COUNT);
                if (group == null || packageId != group.packageId) {
                    group = new Group();
                    group.packageId = packageId;
                    group.packageName = mCursor.getString(Projections.COL_RES_PACKAGE);
                    group.firstPos = position;
                    group.label = group.packageName;

                    try {
                        group.label = pm.getApplicationInfo(group.packageName, 0).loadLabel(pm);
                    } catch (NameNotFoundException e) {
                        Log.w(TAG, "couldn't find label for package " + group.packageName);
                    }

                    mGroups.add(group);
                }
                group.lastPos = position;
            }

            if (mAllContactsView != null) {
                mAllContactsView.setText(mResources.getQuantityString(R.plurals.groupDescrip,
                        totalContacts, totalContacts));
            }

        }

        /**
         * Map the given group and child position into a flattened position on
         * our single {@link Cursor}.
         */
        public int getCursorPosition(int groupPosition, int childPosition) {
            // The actual cursor position for a child is simply stepping from
            // the first position for that group.
            final Group group = mGroups.get(groupPosition);
            final int position = group.firstPos + childPosition;
            return position;
        }

        public boolean hasStableIds() {
            return true;
        }

        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        public Object getChild(int groupPosition, int childPosition) {
            if (mDataValid && mCursor != null) {
                final int position = getCursorPosition(groupPosition, childPosition);
                mCursor.moveToPosition(position);
                return mCursor;
            } else {
                return null;
            }
        }

        public long getChildId(int groupPosition, int childPosition) {
            if (mDataValid && mCursor != null) {
                final int position = getCursorPosition(groupPosition, childPosition);
                if (mCursor.moveToPosition(position)) {
                    return mCursor.getLong(mRowIDColumn);
                } else {
                    return 0;
                }
            } else {
                return 0;
            }
        }

        public int getChildrenCount(int groupPosition) {
            if (mDataValid && mCursor != null) {
                final Group group = mGroups.get(groupPosition);
                final int size = group.lastPos - group.firstPos + 1;
                return size;
            } else {
                return 0;
            }
        }

        public Object getGroup(int groupPosition) {
            if (mDataValid && mCursor != null) {
                return mGroups.get(groupPosition);
            } else {
                return null;
            }
        }

        public int getGroupCount() {
            if (mDataValid && mCursor != null) {
                return mGroups.size();
            } else {
                return 0;
            }
        }

        public long getGroupId(int groupPosition) {
            if (mDataValid && mCursor != null) {
                final Group group = mGroups.get(groupPosition);
                return group.packageId;
            } else {
                return 0;
            }
        }

        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent) {
            if (!mDataValid) {
                throw new IllegalStateException("called with invalid cursor");
            }

            final Group group = mGroups.get(groupPosition);

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.display_group, parent, false);
            }

            final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);

            text1.setText(group.label);

            convertView.setEnabled(mEnabled);

            return convertView;
        }

        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            if (!mDataValid) {
                throw new IllegalStateException("called with invalid cursor");
            }

            final int position = getCursorPosition(groupPosition, childPosition);
            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.display_child, parent, false);
            }

            final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);
            final CheckBox checkbox = (CheckBox)convertView.findViewById(android.R.id.checkbox);

            final int count = mCursor.getInt(Projections.COL_SUMMARY_COUNT);
            final int withPhones = mCursor.getInt(Projections.COL_SUMMARY_WITH_PHONES);
            final int membersVisible = mCursor.getInt(Projections.COL_GROUP_VISIBLE);

            // Read title, but override with string resource when present
            CharSequence title = mCursor.getString(Projections.COL_TITLE);
            if (!mCursor.isNull(Projections.COL_RES_TITLE)) {
                final String packageName = mCursor.getString(Projections.COL_RES_PACKAGE);
                final int titleRes = mCursor.getInt(Projections.COL_RES_TITLE);
                try {
                    title = mExternalRes.getText(packageName, titleRes);
                } catch (NameNotFoundException e) {
                    Log.w(TAG, "couldn't load group title resource for " + packageName);
                }
            }

            final int descripString = mChildWithPhones ? R.plurals.groupDescripPhones
                    : R.plurals.groupDescrip;

            text1.setText(title);
            text2.setText(mResources.getQuantityString(descripString, count, count, withPhones));
            checkbox.setChecked((membersVisible == 1));

            convertView.setEnabled(mEnabled);

            return convertView;
        }

        public void changeCursor(Cursor cursor) {
            if (cursor == mCursor) {
                return;
            }
            if (mCursor != null) {
                mCursor.unregisterContentObserver(mContentObserver);
                mCursor.unregisterDataSetObserver(mDataSetObserver);
                mCursor.close();
            }
            mCursor = cursor;
            if (cursor != null) {
                cursor.registerContentObserver(mContentObserver);
                cursor.registerDataSetObserver(mDataSetObserver);
                mRowIDColumn = cursor.getColumnIndexOrThrow("_id");
                mDataValid = true;
                buildInternalMapping();
                // notify the observers about the new cursor
                notifyDataSetChanged();
            } else {
                mRowIDColumn = -1;
                mDataValid = false;
                // notify the observers about the lack of a data set
                notifyDataSetInvalidated();
            }
        }

        protected void onContentChanged() {
            if (mCursor != null && !mCursor.isClosed()) {
                mDataValid = mCursor.requery();
            }
        }

        private class MyChangeObserver extends ContentObserver {
            public MyChangeObserver() {
                super(new Handler());
            }

            @Override
            public boolean deliverSelfNotifications() {
                return true;
            }

            @Override
            public void onChange(boolean selfChange) {
                onContentChanged();
            }
        }

        private class MyDataSetObserver extends DataSetObserver {
            @Override
            public void onChanged() {
                mDataValid = true;
                notifyDataSetChanged();
            }

            @Override
            public void onInvalidated() {
                mDataValid = false;
                notifyDataSetInvalidated();
            }
        }

    }

    /**
     * Database projections used locally.
     */
    private interface Projections {

        public static final String[] PROJ_SUMMARY = new String[] {
            Groups._ID,
            Groups.TITLE,
            Groups.RES_PACKAGE,
            Groups.TITLE_RES,
            Groups.GROUP_VISIBLE,
            Groups.SUMMARY_COUNT,
            Groups.SUMMARY_WITH_PHONES,
        };

        public static final String SORT_ORDER = Groups.ACCOUNT_TYPE + " ASC, "
                + Groups.ACCOUNT_NAME + " ASC";

        public static final int COL_ID = 0;
        public static final int COL_TITLE = 1;
        public static final int COL_RES_PACKAGE = 2;
        public static final int COL_RES_TITLE = 3;
        public static final int COL_GROUP_VISIBLE = 4;
        public static final int COL_SUMMARY_COUNT = 5;
        public static final int COL_SUMMARY_WITH_PHONES = 6;

    }
}
