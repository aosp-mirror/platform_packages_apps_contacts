/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.util;

import android.database.Cursor;
import android.provider.ContactsContract.StreamItems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data object for a social stream item.  Social stream items may contain multiple
 * mPhotos.  Social stream item entries are comparable; entries with more recent
 * timestamps will be displayed on top.
 */
public class StreamItemEntry implements Comparable<StreamItemEntry> {

    // Basic stream item fields.
    private final long mId;
    private final String mText;
    private final String mComments;
    private final long mTimestamp;
    private final String mAction;
    private final String mActionUri;

    // Package references for label and icon resources.
    private final String mResPackage;
    private final int mIconRes;
    private final int mLabelRes;

    // Photos associated with this stream item.
    private List<StreamItemPhotoEntry> mPhotos;

    public StreamItemEntry(long id, String text, String comments, long timestamp, String action,
            String actionUri, String resPackage, int iconRes, int labelRes) {
        mId = id;
        mText = text;
        mComments = comments;
        mTimestamp = timestamp;
        mAction = action;
        mActionUri = actionUri;
        mResPackage = resPackage;
        mIconRes = iconRes;
        mLabelRes = labelRes;
        mPhotos = new ArrayList<StreamItemPhotoEntry>();
    }

    public StreamItemEntry(Cursor cursor) {
        // This is expected to be populated via a cursor containing all StreamItems columns in
        // its projection.
        mId = getLong(cursor, StreamItems._ID);
        mText = getString(cursor, StreamItems.TEXT);
        mComments = getString(cursor, StreamItems.COMMENTS);
        mTimestamp = getLong(cursor, StreamItems.TIMESTAMP);
        mAction = getString(cursor, StreamItems.ACTION);
        mActionUri = getString(cursor, StreamItems.ACTION_URI);
        mResPackage = getString(cursor, StreamItems.RES_PACKAGE);
        mIconRes = getInt(cursor, StreamItems.RES_ICON, -1);
        mLabelRes = getInt(cursor, StreamItems.RES_LABEL, -1);
        mPhotos = new ArrayList<StreamItemPhotoEntry>();
    }

    public void addPhoto(StreamItemPhotoEntry photoEntry) {
        mPhotos.add(photoEntry);
    }

    @Override
    public int compareTo(StreamItemEntry other) {
        return mTimestamp == other.mTimestamp ? 0 : mTimestamp > other.mTimestamp ? -1 : 1;
    }

    public long getId() {
        return mId;
    }

    public String getText() {
        return mText;
    }

    public String getComments() {
        return mComments;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public String getAction() {
        return mAction;
    }

    public String getActionUri() {
        return mActionUri;
    }

    public String getResPackage() {
        return mResPackage;
    }

    public int getIconRes() {
        return mIconRes;
    }

    public int getLabelRes() {
        return mLabelRes;
    }

    public List<StreamItemPhotoEntry> getPhotos() {
        Collections.sort(mPhotos);
        return mPhotos;
    }

    private static String getString(Cursor cursor, String columnName) {
        return cursor.getString(cursor.getColumnIndex(columnName));
    }

    private static int getInt(Cursor cursor, String columnName, int missingValue) {
        final int columnIndex = cursor.getColumnIndex(columnName);
        return cursor.isNull(columnIndex) ? missingValue : cursor.getInt(columnIndex);
    }

    private static long getLong(Cursor cursor, String columnName) {
        final int columnIndex = cursor.getColumnIndex(columnName);
        return cursor.getLong(columnIndex);
    }
}
