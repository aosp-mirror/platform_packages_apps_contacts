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

package com.android.contacts.tests.allintents;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.android.contacts.tests.R;

import java.util.Arrays;

/**
 * An activity that shows the result of a contacts activity invocation.
 */
public class ResultActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.result);

        Intent intent = getIntent();
        addRowsForIntent((Intent)intent.getExtras().get("data"));
    }

    private void addRowsForIntent(Intent intent) {
        if (intent == null) {
            addRow("", "No data intent returned");
        } else {
            addRow("INTENT", intent.toString());
            addSeparator(3);

            Bundle extras = intent.getExtras();
            if (extras != null && !extras.isEmpty()) {
                for (String key : extras.keySet()) {
                    Object value = extras.get(key);
                    addRow("EXTRA", key);
                    addRowForValue("", value);
                }

                addSeparator(3);
            }

            String dataUri = intent.getDataString();
            if (dataUri != null) {
                addRowsForQuery(Uri.parse(dataUri));
            }
        }
    }

    private void addRowForValue(String label, Object value) {
        if (value == null) {
            addRow(label, "null");
        } else if (value instanceof Bitmap) {
            addRowWithBitmap(label, (Bitmap)value);
        } else if (value instanceof Intent) {
            addRow(label, "INTENT");
            addRowsForIntent((Intent)value);
        } else if (value instanceof Uri) {
            addRow(label, "DATA");
            addRowsForQuery((Uri)value);
        } else if (value.getClass().isArray()) {
            addRow(label, "ARRAY");
            Parcelable[] array = (Parcelable[])value;
            for (int i = 0; i < array.length; i++) {
                addRowForValue("[" + i + "]", String.valueOf(array[i]));
            }
        } else {
            addRow(label, String.valueOf(value));
        }
    }

    private void addRowsForQuery(Uri dataUri) {
        Cursor cursor = getContentResolver().query(dataUri, null, null, null, null);
        if (cursor == null) {
            addRow("", "No data for this URI");
        } else {
            try {
                while (cursor.moveToNext()) {
                    addRow("", "DATA");
                    String[] columnNames = cursor.getColumnNames();
                    String[] names = new String[columnNames.length];
                    System.arraycopy(columnNames, 0, names, 0, columnNames.length);
                    Arrays.sort(names);
                    for (int i = 0; i < names.length; i++) {
                        int index = cursor.getColumnIndex(names[i]);
                        String value = cursor.getString(index);
                        addRow(names[i], value);

                        if (names[i].equals(Contacts.PHOTO_ID) && !TextUtils.isEmpty(value)) {
                            addRowWithPhoto(Long.parseLong(value));
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

    private void addRow(String column0, String column1) {
        TextView label = new TextView(this);
        label.setPadding(4, 4, 4, 4);
        label.setText(column0);
        TextView value = new TextView(this);
        value.setPadding(4, 4, 4, 4);
        value.setText(column1);
        addRow(label, value);
    }

    private void addRowWithPhoto(long photoId) {
        byte[] data = null;
        Cursor cursor = getContentResolver().query(
                ContentUris.withAppendedId(Data.CONTENT_URI, photoId),
                new String[]{Photo.PHOTO}, null, null, null);
        try {
            if (cursor.moveToNext()) {
                data = cursor.getBlob(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (data == null) {
            return;
        }

        addRowWithBitmap("Photo", BitmapFactory.decodeByteArray(data, 0, data.length));
    }

    private void addRowWithBitmap(String label, Bitmap bitmap) {
        TextView labelView = new TextView(this);
        labelView.setPadding(4, 4, 4, 4);
        labelView.setText(label);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setPadding(4, 4, 4, 4);
        imageView.setScaleType(ScaleType.FIT_START);
        addRow(labelView, imageView);
    }

    private void addRow(View column0, View column1) {
        TableLayout table = (TableLayout)findViewById(R.id.table);
        TableRow row = new TableRow(this);
        row.addView(column0);
        row.addView(column1);
        table.addView(row);

        addSeparator(1);
    }

    private void addSeparator(int height) {
        TableLayout table = (TableLayout)findViewById(R.id.table);
        View separator = new View(this);
        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.height = height;
        separator.setLayoutParams(params);
        separator.setBackgroundColor(Color.rgb(33, 66, 33));
        table.addView(separator);
    }
}
