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
import android.provider.Contacts.Organizations;
import android.provider.Contacts.People;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

public abstract class ContactEntryAdapter<E extends ContactEntryAdapter.Entry>
        extends BaseAdapter {

    public static final String[] CONTACT_PROJECTION = new String[] {
        People._ID, // 0
        People.NAME, // 1
        People.NOTES, // 2
        People.PRIMARY_PHONE_ID, // 3
        People.PRESENCE_STATUS, // 4
        People.STARRED, // 5
        People.CUSTOM_RINGTONE, // 6
        People.SEND_TO_VOICEMAIL, // 7
        People.PHONETIC_NAME, // 8
    };
    public static final int CONTACT_ID_COLUMN = 0;
    public static final int CONTACT_NAME_COLUMN = 1;
    public static final int CONTACT_NOTES_COLUMN = 2;
    public static final int CONTACT_PREFERRED_PHONE_COLUMN = 3;
    public static final int CONTACT_SERVER_STATUS_COLUMN = 4;
    public static final int CONTACT_STARRED_COLUMN = 5;
    public static final int CONTACT_CUSTOM_RINGTONE_COLUMN = 6;
    public static final int CONTACT_SEND_TO_VOICEMAIL_COLUMN = 7;
    public static final int CONTACT_PHONETIC_NAME_COLUMN = 8;

    public static final String[] PHONES_PROJECTION = new String[] {
        People.Phones._ID, // 0
        People.Phones.NUMBER, // 1
        People.Phones.TYPE, // 2
        People.Phones.LABEL, // 3
        People.Phones.ISPRIMARY, // 4
    };
    public static final int PHONES_ID_COLUMN = 0;
    public static final int PHONES_NUMBER_COLUMN = 1;
    public static final int PHONES_TYPE_COLUMN = 2;
    public static final int PHONES_LABEL_COLUMN = 3;
    public static final int PHONES_ISPRIMARY_COLUMN = 4;

    public static final String[] METHODS_PROJECTION = new String[] {
        People.ContactMethods._ID, // 0
        People.ContactMethods.KIND, // 1
        People.ContactMethods.DATA, // 2
        People.ContactMethods.TYPE, // 3
        People.ContactMethods.LABEL, // 4
        People.ContactMethods.ISPRIMARY, // 5
        People.ContactMethods.AUX_DATA, // 6
    };
    public static final String[] METHODS_WITH_PRESENCE_PROJECTION = new String[] {
        People.ContactMethods._ID, // 0
        People.ContactMethods.KIND, // 1
        People.ContactMethods.DATA, // 2
        People.ContactMethods.TYPE, // 3
        People.ContactMethods.LABEL, // 4
        People.ContactMethods.ISPRIMARY, // 5
        People.ContactMethods.AUX_DATA, // 6
        People.PRESENCE_STATUS, // 7
    };
    public static final int METHODS_ID_COLUMN = 0;
    public static final int METHODS_KIND_COLUMN = 1;
    public static final int METHODS_DATA_COLUMN = 2;
    public static final int METHODS_TYPE_COLUMN = 3;
    public static final int METHODS_LABEL_COLUMN = 4;
    public static final int METHODS_ISPRIMARY_COLUMN = 5;
    public static final int METHODS_AUX_DATA_COLUMN = 6;
    public static final int METHODS_STATUS_COLUMN = 7;

    public static final String[] ORGANIZATIONS_PROJECTION = new String[] {
        Organizations._ID, // 0
        Organizations.TYPE, // 1
        Organizations.LABEL, // 2
        Organizations.COMPANY, // 3
        Organizations.TITLE, // 4
        Organizations.ISPRIMARY, // 5
    };
    public static final int ORGANIZATIONS_ID_COLUMN = 0;
    public static final int ORGANIZATIONS_TYPE_COLUMN = 1;
    public static final int ORGANIZATIONS_LABEL_COLUMN = 2;
    public static final int ORGANIZATIONS_COMPANY_COLUMN = 3;
    public static final int ORGANIZATIONS_TITLE_COLUMN = 4;
    public static final int ORGANIZATIONS_ISPRIMARY_COLUMN = 5;
    
    protected ArrayList<ArrayList<E>> mSections;
    protected LayoutInflater mInflater;
    protected Context mContext;
    protected boolean mSeparators;

    /**
     * Base class for adapter entries.
     */
    public static class Entry {
        /** Details from the person table */
        public static final int KIND_CONTACT = -1;
        /** Synthesized phone entry that will send an SMS instead of call the number */
        public static final int KIND_SMS = -2;
        /** A section separator */
        public static final int KIND_SEPARATOR = -3; 

        public String label;
        public String data;
        public Uri uri;
        public long id = 0;
        public int maxLines = 1;
        public int kind;
        
        /**
         * Helper for making subclasses parcelable.
         */
        protected void writeToParcel(Parcel p) {
            p.writeString(label);
            p.writeString(data);
            p.writeParcelable(uri, 0);
            p.writeLong(id);
            p.writeInt(maxLines);
            p.writeInt(kind);
        }
        
        /**
         * Helper for making subclasses parcelable.
         */
        protected void readFromParcel(Parcel p) {
            label = p.readString();
            data = p.readString();
            uri = p.readParcelable(null);
            id = p.readLong();
            maxLines = p.readInt();
            kind = p.readInt();
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
