/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.provider.ContactsContract.Aggregates;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

public abstract class ContactEntryAdapter<E extends ContactEntryAdapter.Entry>
        extends BaseAdapter {

    public static final String[] AGGREGATE_PROJECTION = new String[] {
        Aggregates.DISPLAY_NAME, // 0
        Aggregates.STARRED, //1
        Data._ID, //2
        Data.CONTACT_ID, //3
        Contacts.PACKAGE, //4
        Data.MIMETYPE, //5
        Data.IS_PRIMARY, //6
        Data.IS_SUPER_PRIMARY, //7
        Data.DATA1, //8
        Data.DATA2, //9
        Data.DATA3, //10
        Data.DATA4, //11
        Data.DATA5, //12
        Data.DATA6, //13
        Data.DATA7, //14
        Data.DATA8, //15
        Data.DATA9, //16
        Data.DATA10, //17
    };
    public static final int AGGREGATE_DISPLAY_NAME_COLUMN = 0;
    public static final int AGGREGATE_STARRED_COLUMN = 1;
    public static final int DATA_ID_COLUMN = 2;
    public static final int DATA_CONTACT_ID_COLUMN = 3;
    public static final int DATA_PACKAGE_COLUMN = 4;
    public static final int DATA_MIMETYPE_COLUMN = 5;
    public static final int DATA_IS_PRIMARY_COLUMN = 6;
    public static final int DATA_IS_SUPER_PRIMARY_COLUMN = 7;
    public static final int DATA_1_COLUMN = 8;
    public static final int DATA_2_COLUMN = 9;
    public static final int DATA_3_COLUMN = 10;
    public static final int DATA_4_COLUMN = 11;
    public static final int DATA_5_COLUMN = 12;
    public static final int DATA_6_COLUMN = 13;
    public static final int DATA_7_COLUMN = 14;
    public static final int DATA_8_COLUMN = 15;
    public static final int DATA_9_COLUMN = 16;
    public static final int DATA_10_COLUMN = 17;

    protected ArrayList<ArrayList<E>> mSections;
    protected LayoutInflater mInflater;
    protected Context mContext;
    protected boolean mSeparators;

    /**
     * Base class for adapter entries.
     */
    public static class Entry {
        public int type = -1;
        public String label;
        public String data;
        public Uri uri;
        public long id = 0;
        public long contactId;
        public int maxLines = 1;
        public String mimetype;

        /**
         * Helper for making subclasses parcelable.
         */
        protected void writeToParcel(Parcel p) {
            p.writeInt(type);
            p.writeString(label);
            p.writeString(data);
            p.writeParcelable(uri, 0);
            p.writeLong(id);
            p.writeInt(maxLines);
            p.writeString(mimetype);
        }

        /**
         * Helper for making subclasses parcelable.
         */
        protected void readFromParcel(Parcel p) {
            type = p.readInt();
            label = p.readString();
            data = p.readString();
            uri = p.readParcelable(null);
            id = p.readLong();
            maxLines = p.readInt();
            mimetype = p.readString();
        }
    }

    ContactEntryAdapter(Context context, ArrayList<ArrayList<E>> sections, boolean separators) {
        mContext = context;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mSections = sections;
        mSeparators = separators;
    }

    /**
     * Resets the section data.
     *
     * @param sections the section data
     */
    public final void setSections(ArrayList<ArrayList<E>> sections, boolean separators) {
        mSections = sections;
        mSeparators = separators;
        notifyDataSetChanged();
    }

    /**
     * Resets the section data and returns the position of the given entry.
     *
     * @param sections the section data
     * @param entry the entry to return the position for
     * @return the position of entry, or -1 if it isn't found
     */
    public final int setSections(ArrayList<ArrayList<E>> sections, E entry) {
        mSections = sections;
        notifyDataSetChanged();

        int numSections = mSections.size();
        int position = 0;
        for (int i = 0; i < numSections; i++) {
            ArrayList<E> section = mSections.get(i);
            int sectionSize = section.size();
            for (int j = 0; j < sectionSize; j++) {
                E e = section.get(j);
                if (e.equals(entry)) {
                    position += j;
                    return position;
                }
            }
            position += sectionSize;
        }
        return -1;
    }

    /**
     * @see android.widget.ListAdapter#getCount()
     */
    public final int getCount() {
        return countEntries(mSections, mSeparators);
    }

    /**
     * @see android.widget.ListAdapter#hasSeparators()
     */
    @Override
    public final boolean areAllItemsEnabled() {
        return mSeparators == false;
    }

    /**
     * @see android.widget.ListAdapter#isSeparator(int)
     */
    @Override
    public final boolean isEnabled(int position) {
        if (!mSeparators) {
            return true;
        }

        int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            ArrayList<E> section = mSections.get(i);
            int sectionSize = section.size();
            if (sectionSize == 1) {
                // The section only contains a separator and nothing else, skip it
                continue;
            }
            if (position == 0) {
                // The first item in a section is always the separator
                return false;
            }
            position -= sectionSize;
        }
        return true;
    }

    /**
     * @see android.widget.ListAdapter#getItem(int)
     */
    public final Object getItem(int position) {
        return getEntry(mSections, position, mSeparators);
    }

    /**
     * Get the entry for the given position.
     *
     * @param sections the list of sections
     * @param position the position for the desired entry
     * @return the ContactEntry for the given position
     */
    public final static <T extends Entry> T getEntry(ArrayList<ArrayList<T>> sections,
            int position, boolean separators) {
        int numSections = sections.size();
        for (int i = 0; i < numSections; i++) {
            ArrayList<T> section = sections.get(i);
            int sectionSize = section.size();
            if (separators && sectionSize == 1) {
                // The section only contains a separator and nothing else, skip it
                continue;
            }
            if (position < section.size()) {
                return section.get(position);
            }
            position -= section.size();
        }
        return null;
    }

    /**
     * Get the count of entries in all sections
     *
     * @param sections the list of sections
     * @return the count of entries in all sections
     */
    public static <T extends Entry> int countEntries(ArrayList<ArrayList<T>> sections,
            boolean separators) {
        int count = 0;
        int numSections = sections.size();
        for (int i = 0; i < numSections; i++) {
            ArrayList<T> section = sections.get(i);
            int sectionSize = section.size();
            if (separators && sectionSize == 1) {
                // The section only contains a separator and nothing else, skip it
                continue;
            }
            count += sections.get(i).size();
        }
        return count;
    }

    /**
     * @see android.widget.ListAdapter#getItemId(int)
     */
    public final long getItemId(int position) {
        Entry entry = getEntry(mSections, position, mSeparators);
        if (entry != null) {
            return entry.id;
        } else {
            return -1;
        }
    }

    /**
     * @see android.widget.ListAdapter#getView(int, View, ViewGroup)
     */
    public View getView(int position, View convertView, ViewGroup parent) {
        View v;
        if (convertView == null) {
            v = newView(position, parent);
        } else {
            v = convertView;
        }
        bindView(v, getEntry(mSections, position, mSeparators));
        return v;
    }

    /**
     * Create a new view for an entry.
     *
     * @parent the parent ViewGroup
     * @return the newly created view
     */
    protected abstract View newView(int position, ViewGroup parent);

    /**
     * Binds the data from an entry to a view.
     *
     * @param view the view to display the entry in
     * @param entry the data to bind
     */
    protected abstract void bindView(View view, E entry);
}
