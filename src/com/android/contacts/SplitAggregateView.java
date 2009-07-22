/*
 * Copyright (C) 2006 The Android Open Source Project
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

import com.google.common.util.text.TextUtil;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Aggregates.Data;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * A list view for constituent contacts of an aggregate.  Shows the contact name, source icon
 * and additional data such as a nickname, email address or phone number, whichever
 * is available.
 */
public class SplitAggregateView extends ListView {

    private static final String[] AGGREGATE_DATA_PROJECTION = new String[] {
            Data.MIMETYPE, Data.RES_PACKAGE, Data.CONTACT_ID, Data.DATA1, Data.DATA2,
            Data.IS_PRIMARY, StructuredName.DISPLAY_NAME
    };

    private static final int COL_MIMETYPE = 0;
    private static final int COL_RES_PACKAGE = 1;
    private static final int COL_CONTACT_ID = 2;
    private static final int COL_DATA1 = 3;
    private static final int COL_DATA2 = 4;
    private static final int COL_IS_PRIMARY = 5;
    private static final int COL_DISPLAY_NAME = 6;


    private final Uri mAggregateUri;
    private OnContactSelectedListener mListener;

    /**
     * Listener interface that gets the contact ID of the user-selected contact.
     */
    public interface OnContactSelectedListener {
        void onContactSelected(long contactId);
    }

    /**
     * Constructor.
     */
    public SplitAggregateView(Context context, Uri aggregateUri) {
        super(context);

        mAggregateUri = aggregateUri;

        final List<ContactInfo> list = loadData();

        setAdapter(new SplitAggregateAdapter(context, list));
        setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mListener.onContactSelected(list.get(position).contactId);
            }
        });
    }

    /**
     * Sets a contact selection listener.
     */
    public void setOnContactSelectedListener(OnContactSelectedListener listener) {
        mListener = listener;
    }

    /**
     * Contact information loaded from the content provider.
     */
    private static class ContactInfo implements Comparable<ContactInfo> {
        final long contactId;
        String resPackage;
        String name;
        String phone;
        String email;
        String nickname;

        public ContactInfo(long contactId) {
            this.contactId = contactId;
        }

        public String getAdditionalData() {
            if (nickname != null) {
                return nickname;
            }

            if (email != null) {
                return email;
            }

            if (phone != null) {
                return phone;
            }

            return "";
        }

        public int compareTo(ContactInfo another) {
            return resPackage.compareTo(another.resPackage);
        }
    }

    /**
     * Loads data from the content provider, organizes it into {@link ContactInfo} objects
     * and returns a sorted list of {@link ContactInfo}'s.
     */
    private List<ContactInfo> loadData() {
        HashMap<Long, ContactInfo> contactInfos = new HashMap<Long, ContactInfo>();
        Uri dataUri = Uri.withAppendedPath(mAggregateUri, Data.CONTENT_DIRECTORY);
        Cursor cursor = getContext().getContentResolver().query(dataUri,
                AGGREGATE_DATA_PROJECTION, null, null, null);
        try {
            while (cursor.moveToNext()) {
                long contactId = cursor.getLong(COL_CONTACT_ID);
                ContactInfo info = contactInfos.get(contactId);
                if (info == null) {
                    info = new ContactInfo(contactId);
                    contactInfos.put(contactId, info);
                    info.resPackage = cursor.getString(COL_RES_PACKAGE);
                }

                String mimetype = cursor.getString(COL_MIMETYPE);
                if (StructuredName.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    loadStructuredName(cursor, info);
                } else if (Phone.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    loadPhoneNumber(cursor, info);
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    loadEmail(cursor, info);
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    loadNickname(cursor, info);
                }
            }
        } finally {
            cursor.close();
        }

        List<ContactInfo> list = new ArrayList<ContactInfo>(contactInfos.values());
        Collections.sort(list);
        return list;
    }

    private void loadStructuredName(Cursor cursor, ContactInfo info) {
        info.name = cursor.getString(COL_DISPLAY_NAME);
        if (info.name != null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        String firstName = cursor.getString(COL_DATA1);
        String lastName = cursor.getString(COL_DATA2);
        if (!TextUtil.isEmpty(firstName)) {
            sb.append(firstName);
        }
        if  (!TextUtil.isEmpty(firstName) && !TextUtil.isEmpty(lastName)) {
            sb.append(" ");
        }
        if (!TextUtil.isEmpty(lastName)) {
            sb.append(lastName);
        }

        if (sb.length() != 0) {
            info.name = sb.toString();
        }
    }

    private void loadNickname(Cursor cursor, ContactInfo info) {
        if (info.nickname == null || cursor.getInt(COL_IS_PRIMARY) != 0) {
            info.nickname = cursor.getString(COL_DATA2);
        }
    }

    private void loadEmail(Cursor cursor, ContactInfo info) {
        if (info.email == null || cursor.getInt(COL_IS_PRIMARY) != 0) {
            info.email = cursor.getString(COL_DATA2);
        }
    }

    private void loadPhoneNumber(Cursor cursor, ContactInfo info) {
        if (info.phone == null || cursor.getInt(COL_IS_PRIMARY) != 0) {
            info.phone = cursor.getString(COL_DATA2);
        }
    }

    /**
     * List adapter for the list of {@link ContactInfo} objects.
     */
    private static class SplitAggregateAdapter extends ArrayAdapter<ContactInfo> {

        private static class SplitAggregateItemCache  {
            TextView name;
            TextView additionalData;
            ImageView sourceIcon;
        }

        private LayoutInflater mInflater;

        public SplitAggregateAdapter(Context context, List<ContactInfo> sources) {
            super(context, 0, sources);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.split_aggregate_list_item, parent, false);
            }

            SplitAggregateItemCache cache = (SplitAggregateItemCache)convertView.getTag();
            if (cache == null) {
                cache = new SplitAggregateItemCache();
                cache.name = (TextView)convertView.findViewById(R.id.name);
                cache.additionalData = (TextView)convertView.findViewById(R.id.additionalData);
                cache.sourceIcon = (ImageView)convertView.findViewById(R.id.sourceIcon);
                convertView.setTag(cache);
            }

            final ContactInfo info = getItem(position);
            cache.name.setText(info.name);
            cache.additionalData.setText(info.getAdditionalData());

            Bitmap sourceBitmap = getSourceIcon(info.resPackage);
            if (sourceBitmap != null) {
                cache.sourceIcon.setImageBitmap(sourceBitmap);
            } else {
                cache.sourceIcon.setImageResource(R.drawable.unknown_source);
            }
            return convertView;
        }

        /**
         * Returns the icon corresponding to the source of contact information.
         */
        private Bitmap getSourceIcon(String packageName) {

            // TODO: obtain from PackageManager
            return null;
        }
    }
}
