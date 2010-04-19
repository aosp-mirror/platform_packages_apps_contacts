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

package com.android.contacts.tests.mocks;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

import junit.framework.Assert;

/**
 * A programmable mock content provider.
 */
public class MockContentProvider extends ContentProvider {

    public static class Query {

        private final Uri mUri;
        private String[] mProjection;
        private String[] mDefaultProjection;
        private String mSelection;
        private String[] mSelectionArgs;
        private String mSortOrder;
        private ArrayList<Object[]> mRows = new ArrayList<Object[]>();

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

        public Query withSelection(String selection, String... selectionArgs) {
            mSelection = selection;
            mSelectionArgs = selectionArgs;
            return this;
        }

        public Query withSortOrder(String sortOrder) {
            mSortOrder = sortOrder;
            return this;
        }

        public Query returnRow(Object... row) {
            mRows.add(row);
            return this;
        }

        public boolean equals(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            if (!uri.equals(mUri)) {
                return false;
            }

            if (!equals(projection, mProjection)) {
                return false;
            }

            if (!TextUtils.equals(selection, mSelection)) {
                return false;
            }

            if (!equals(selectionArgs, mSelectionArgs)) {
                return false;
            }

            if (!TextUtils.equals(sortOrder, mSortOrder)) {
                return false;
            }

            return true;
        }

        private static boolean equals(String[] array1, String[] array2) {
            boolean empty1 = array1 == null || array1.length == 0;
            boolean empty2 = array2 == null || array2.length == 0;
            if (empty1 && empty2) {
                return true;
            }
            if (empty1) {
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

        public Cursor getResult() {
            String[] columnNames = mProjection != null ? mProjection : mDefaultProjection;
            MatrixCursor cursor = new MatrixCursor(columnNames);
            for (Object[] row : mRows) {
                cursor.addRow(row);
            }
            return cursor;
        }
    }

    private LinkedList<Query> mExpectedQueries = new LinkedList<Query>();

    @Override
    public boolean onCreate() {
        return true;
    }

    public Query expectQuery(Uri contentUri) {
        Query query = new Query(contentUri);
        mExpectedQueries.offer(query);
        return query;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (mExpectedQueries.isEmpty()) {
            Assert.fail("Unexpected query: "
                    + queryToString(uri, projection, selection, selectionArgs, sortOrder));
        }

        Query query = mExpectedQueries.remove();
        if (!query.equals(uri, projection, selection, selectionArgs, sortOrder)) {
            Assert.fail("Incorrect query.\n    Expected: " + query + "\n      Actual: " +
                    queryToString(uri, projection, selection, selectionArgs, sortOrder));
        }

        return query.getResult();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
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
        Assert.assertTrue("Not all expected queries have been called: " +
                mExpectedQueries, mExpectedQueries.isEmpty());
    }
}
