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

package com.android.contacts.widget;

import android.app.ListActivity;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.list.PinnedHeaderListAdapter;

/**
 * An activity that demonstrates various use cases for the {@link com.android.contacts.common.list.PinnedHeaderListView}.
 * If we decide to move PinnedHeaderListView to the framework, this class could go
 * to API demos.
 */
public class PinnedHeaderListDemoActivity extends ListActivity {

    public final static class TestPinnedHeaderListAdapter extends PinnedHeaderListAdapter {

        public TestPinnedHeaderListAdapter(Context context) {
            super(context);
            setPinnedPartitionHeadersEnabled(true);
        }

        private String[] mHeaders;
        private int mPinnedHeaderCount;

        public void setHeaders(String[] headers) {
            this.mHeaders = headers;
        }

        @Override
        protected View newHeaderView(Context context, int partition, Cursor cursor,
                ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(R.layout.list_section, null);
        }

        @Override
        protected void bindHeaderView(View view, int parition, Cursor cursor) {
            TextView headerText = (TextView)view.findViewById(R.id.header_text);
            headerText.setText(mHeaders[parition]);
        }

        @Override
        protected View newView(Context context, int partition, Cursor cursor, int position,
                ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(android.R.layout.simple_list_item_1, null);
        }

        @Override
        protected void bindView(View v, int partition, Cursor cursor, int position) {
            TextView text = (TextView)v.findViewById(android.R.id.text1);
            text.setText(cursor.getString(1));
        }

        @Override
        public View getPinnedHeaderView(int viewIndex, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            View view = inflater.inflate(R.layout.list_section, parent, false);
            view.setFocusable(false);
            view.setEnabled(false);
            bindHeaderView(view, viewIndex, null);
            return view;
        }

        @Override
        public int getPinnedHeaderCount() {
            return mPinnedHeaderCount;
        }
    }

    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.pinned_header_list_demo);

        final TestPinnedHeaderListAdapter adapter = new TestPinnedHeaderListAdapter(this);

        Bundle extras = getIntent().getExtras();
        int[] counts = extras.getIntArray("counts");
        String[] names = extras.getStringArray("names");
        boolean[] showIfEmpty = extras.getBooleanArray("showIfEmpty");
        boolean[] hasHeader = extras.getBooleanArray("headers");
        int[] delays = extras.getIntArray("delays");

        if (counts == null || names == null || showIfEmpty == null || delays == null) {
            throw new IllegalArgumentException("Missing required extras");
        }

        adapter.setHeaders(names);
        for (int i = 0; i < counts.length; i++) {
            adapter.addPartition(showIfEmpty[i], names[i] != null);
            adapter.mPinnedHeaderCount = names.length;
        }
        setListAdapter(adapter);
        for (int i = 0; i < counts.length; i++) {
            final int sectionId = i;
            final Cursor cursor = makeCursor(names[i], counts[i]);
            mHandler.postDelayed(new Runnable() {

                public void run() {
                    adapter.changeCursor(sectionId, cursor);

                }
            }, delays[i]);
        }
    }

    private Cursor makeCursor(String name, int count) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", name});
        for (int i = 0; i < count; i++) {
            cursor.addRow(new Object[]{i, name + "[" + i + "]"});
        }
        return cursor;
    }
}
