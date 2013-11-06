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

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.StreamItems;
import android.text.Html;

import com.android.contacts.detail.ContactDetailDisplayUtils;
import com.android.contacts.common.test.NeededForTesting;

import com.google.common.annotations.VisibleForTesting;

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
    private final String mAccountType;
    private final String mAccountName;
    private final String mDataSet;

    private boolean mDecoded;
    private CharSequence mDecodedText;
    private CharSequence mDecodedComments;

    // Package references for label and icon resources.
    private final String mResPackage;
    private final String mIconRes;
    private final String mLabelRes;

    // Photos associated with this stream item.
    private List<StreamItemPhotoEntry> mPhotos;

    @NeededForTesting
    public static StreamItemEntry createForTest(long id, String text, String comments,
            long timestamp, String accountType, String accountName, String dataSet,
            String resPackage, String iconRes, String labelRes) {
        return new StreamItemEntry(id, text, comments, timestamp, accountType, accountName, dataSet,
                resPackage, iconRes, labelRes);
    }

    private StreamItemEntry(long id, String text, String comments, long timestamp,
            String accountType, String accountName, String dataSet, String resPackage,
            String iconRes, String labelRes) {
        mId = id;
        mText = text;
        mComments = comments;
        mTimestamp = timestamp;
        mAccountType = accountType;
        mAccountName = accountName;
        mDataSet = dataSet;
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
        mAccountType = getString(cursor, StreamItems.ACCOUNT_TYPE);
        mAccountName = getString(cursor, StreamItems.ACCOUNT_NAME);
        mDataSet = getString(cursor, StreamItems.DATA_SET);
        mResPackage = getString(cursor, StreamItems.RES_PACKAGE);
        mIconRes = getString(cursor, StreamItems.RES_ICON);
        mLabelRes = getString(cursor, StreamItems.RES_LABEL);
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

    public String getAccountType() {
        return mAccountType;
    }

    public String getAccountName() {
        return mAccountName;
    }

    public String getDataSet() {
        return mDataSet;
    }

    public String getResPackage() {
        return mResPackage;
    }

    public String getIconRes() {
        return mIconRes;
    }

    public String getLabelRes() {
        return mLabelRes;
    }

    public List<StreamItemPhotoEntry> getPhotos() {
        Collections.sort(mPhotos);
        return mPhotos;
    }

    /**
     * Make {@link #getDecodedText} and {@link #getDecodedComments} available.  Must be called
     * before calling those.
     *
     * We can't do this automatically in the getters, because it'll require a {@link Context}.
     */
    @VisibleForTesting
    public void decodeHtml(Context context) {
        final Html.ImageGetter imageGetter = ContactDetailDisplayUtils.getImageGetter(context);
        if (mText != null) {
            mDecodedText = HtmlUtils.fromHtml(context, mText, imageGetter, null);
        }
        if (mComments != null) {
            mDecodedComments = HtmlUtils.fromHtml(context, mComments, imageGetter, null);
        }
        mDecoded = true;
    }

    public CharSequence getDecodedText() {
        checkDecoded();
        return mDecodedText;
    }

    public CharSequence getDecodedComments() {
        checkDecoded();
        return mDecodedComments;
    }

    private void checkDecoded() {
        if (!mDecoded) {
            throw new IllegalStateException("decodeHtml must have been called");
        }
    }

    private static String getString(Cursor cursor, String columnName) {
        return cursor.getString(cursor.getColumnIndex(columnName));
    }

    private static long getLong(Cursor cursor, String columnName) {
        final int columnIndex = cursor.getColumnIndex(columnName);
        return cursor.getLong(columnIndex);
    }
}
