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

package com.android.contacts.common.test.mocks;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.text.TextUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

/**
 * A programmable mock content provider.
 */
public class MockContentProvider extends ContentProvider {
    private static final String TAG = "MockContentProvider";

    public static class Query {

        private final Uri mUri;
        private String[] mProjection;
        private String[] mDefaultProjection;
        private String mSelection;
        private String[] mSelectionArgs;
        private String mSortOrder;
        private ArrayList<Object> mRows = new ArrayList<Object>();
        private boolean mAnyProjection;
        private boolean mAnySelection;
        private boolean mAnySortOrder;
        private boolean mAnyNumberOfTimes;

        private boolean mExecuted;

        public Query(Uri uri) {
            mUri = uri;
        }

        @Override
        public String toString() {
            return queryToString(mUri, mProjection, mSelection, mSelectionArgs, mSortOrder);
        }

        public Query withProjection(String... projection) {
            mProjection = projection;
            return this;
        }

        public Query withDefaultProjection(String... projection) {
            mDefaultProjection = projection;
            return this;
        }

        public Query withAnyProjection() {
            mAnyProjection = true;
            return this;
        }

        public Query withSelection(String selection, String... selectionArgs) {
            mSelection = selection;
            mSelectionArgs = selectionArgs;
            return this;
        }

        public Query withAnySelection() {
            mAnySelection = true;
            return this;
        }

        public Query withSortOrder(String sortOrder) {
            mSortOrder = sortOrder;
            return this;
        }

        public Query withAnySortOrder() {
            mAnySortOrder = true;
            return this;
        }

        public Query returnRow(ContentValues values) {
            mRows.add(values);
            return this;
        }

        public Query returnRow(Object... row) {
            mRows.add(row);
            return this;
        }

        public Query returnEmptyCursor() {
            mRows.clear();
            return this;
        }

        public Query anyNumberOfTimes() {
            mAnyNumberOfTimes = true;
            return this;
        }

        public boolean equals(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            if (!uri.equals(mUri)) {
                return false;
            }

            if (!mAnyProjection && !equals(projection, mProjection)) {
                return false;
            }

            if (!mAnySelection && !equals(selection, mSelection)) {
                return false;
            }

            if (!mAnySelection && !equals(selectionArgs, mSelectionArgs)) {
                return false;
            }

            if (!mAnySortOrder && !equals(sortOrder, mSortOrder)) {
                return false;
            }

            return true;
        }

        private boolean equals(String string1, String string2) {
            if (TextUtils.isEmpty(string1)) {
                string1 = null;
            }
            if (TextUtils.isEmpty(string2)) {
                string2 = null;
            }
            return TextUtils.equals(string1, string2);
        }

        private static boolean equals(String[] array1, String[] array2) {
            boolean empty1 = array1 == null || array1.length == 0;
            boolean empty2 = array2 == null || array2.length == 0;
            if (empty1 && empty2) {
                return true;
            }
            if (empty1 != empty2 && (empty1 || empty2)) {
                return false;
            }

            if (array1.length != array2.length) return false;

            for (int i = 0; i < array1.length; i++) {
                if (!array1[i].equals(array2[i])) {
                    return false;
                }
            }
            return true;
        }

        public Cursor getResult(String[] projection) {
            String[] columnNames;
            if (mAnyProjection) {
                columnNames = projection;
            } else {
                columnNames = mProjection != null ? mProjection : mDefaultProjection;
            }

            MatrixCursor cursor = new MatrixCursor(columnNames);
            for (Object row : mRows) {
                if (row instanceof Object[]) {
                    cursor.addRow((Object[]) row);
                } else {
                    ContentValues values = (ContentValues) row;
                    Object[] columns = new Object[projection.length];
                    for (int i = 0; i < projection.length; i++) {
                        columns[i] = values.get(projection[i]);
                    }
                    cursor.addRow(columns);
                }
            }
            return cursor;
        }
    }

    public static class TypeQuery {
        private final Uri mUri;
        private final String mType;

        public TypeQuery(Uri uri, String type) {
            mUri = uri;
            mType = type;
        }

        public Uri getUri() {
            return mUri;
        }

        public String getType() {
            return mType;
        }

        @Override
        public String toString() {
            return mUri + " --> " + mType;
        }

        public boolean equals(Uri uri) {
            return getUri().equals(uri);
        }
    }

    private ArrayList<Query> mExpectedQueries = new ArrayList<Query>();
    private HashMap<Uri, String> mExpectedTypeQueries = Maps.newHashMap();

    @Override
    public boolean onCreate() {
        return true;
    }

    public Query expectQuery(Uri contentUri) {
        Query query = new Query(contentUri);
        mExpectedQueries.add(query);
        return query;
    }

    public void expectTypeQuery(Uri uri, String type) {
        mExpectedTypeQueries.put(uri, type);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        for (Iterator<Query> iterator = mExpectedQueries.iterator(); iterator.hasNext();) {
            Query query = iterator.next();
            if (query.equals(uri, projection, selection, selectionArgs, sortOrder)) {
                query.mExecuted = true;
                if (!query.mAnyNumberOfTimes) {
                    iterator.remove();
                }
                return query.getResult(projection);
            }
        }

        if (mExpectedQueries.isEmpty()) {
            Assert.fail("Unexpected query: "
                    + queryToString(uri, projection, selection, selectionArgs, sortOrder));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(mExpectedQueries.get(0));
            for (int i = 1; i < mExpectedQueries.size(); i++) {
                sb.append("\n              ").append(mExpectedQueries.get(i));
            }
            Assert.fail("Incorrect query.\n    Expected: " + sb + "\n      Actual: " +
                    queryToString(uri, projection, selection, selectionArgs, sortOrder));
        }
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        if (mExpectedTypeQueries.isEmpty()) {
            Assert.fail("Unexpected getType query: " + uri);
        }

        String mimeType = mExpectedTypeQueries.get(uri);
        if (mimeType != null) {
            return mimeType;
        }

        Assert.fail("Unknown mime type for: " + uri);
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private static String queryToString(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        StringBuilder sb = new StringBuilder();
        sb.append(uri).append(" ");
        if (projection != null) {
            sb.append(Arrays.toString(projection));
        } else {
            sb.append("[]");
        }
        if (selection != null) {
            sb.append(" selection: '").append(selection).append("'");
            if (selectionArgs != null) {
                sb.append(Arrays.toString(selectionArgs));
            } else {
                sb.append("[]");
            }
        }
        if (sortOrder != null) {
            sb.append(" sort: '").append(sortOrder).append("'");
        }
        return sb.toString();
    }

    public void verify() {
        ArrayList<Query> mMissedQueries = Lists.newArrayList();
        for (Query query : mExpectedQueries) {
            if (!query.mExecuted) {
                mMissedQueries.add(query);
            }
        }
        Assert.assertTrue("Not all expected queries have been called: " +
                mMissedQueries, mMissedQueries.isEmpty());
    }
}
