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

package com.android.contacts.tests.streamitems;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.StreamItemPhotos;
import android.provider.ContactsContract.StreamItems;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.contacts.common.model.account.GoogleAccountType;
import com.android.contacts.tests.R;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;

/**
 * Testing activity that will populate stream items and stream item photos to selected
 * entries in the user's contacts list.
 *
 * The contact selected must have at least one raw contact that was provided by Google.
 */
public class StreamItemPopulatorActivity extends Activity {

    // Test data to randomly select from.
    private String[] snippetStrings = new String[]{
            "Just got back from a vacation in %1$s - what a great place!  Can't wait to go back.",
            "If I never see %1$s again it will be too soon.",
            "This is a public service announcement.  If you were even close to considering visiting"
            + " %1$s, I strongly advise you to reconsider.  The food was terrible, the people were "
            + "rude, the hygiene of the bus and taxi drivers was positively <i>barbaric</i>.  I "
            + "feared for my life almost the entire time I was there, and feel lucky to be back "
            + "<b>home</b>.",
            "Check out these pictures!  I took them in %1$s"
    };

    private String[] placeNames = new String[]{
            "the Google campus in Mountain View",
            "the deserts on Arrakis",
            "Iceland",
            "Japan",
            "Sydney",
            "San Francisco",
            "Munich",
            "Istanbul",
            "Tanagra",
            "the restricted section of Area 51",
            "the middle of nowhere"
    };

    private String[] commentStrings = new String[]{
            "3 retweets",
            "5 shares",
            "4 likes",
            "4 +1s",
            "<i>24567</i> <font color='blue' size='+1'><b>likes</b></font>"
    };

    private String[] labelResources = new String[] {
            "attribution_google_plus",
            "attribution_google_talk",
            "attribution_flicker",
            "attribution_twitter"
    };

    public String[] iconResources = new String[] {
            "default_icon"
    };

    // Photos to randomly select from.
    private Integer[] imageIds = new Integer[]{
            R.drawable.android,
            R.drawable.goldengate,
            R.drawable.iceland,
            R.drawable.japan,
            R.drawable.sydney,
            R.drawable.wharf,
            R.drawable.whiskey
    };

    // Only some photos have actions.
    private String[] imageStrings = new String[]{
            "android",
            "goldengate",
            "iceland",
            "japan",
    };

    // The contact ID that was picked.
    private long mContactId = -1;

    private Random mRandom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRandom = new Random(System.currentTimeMillis());

        setContentView(R.layout.stream_item_populator);
        Button pickButton = (Button) findViewById(R.id.add);
        pickButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                // Reset the contact ID.
                mContactId = -1;

                // Forward the Intent to the picker
                final Intent pickerIntent =
                        new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                pickerIntent.setFlags(
                        Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivityForResult(pickerIntent, 0);
            }
        });

        Button exitButton = (Button) findViewById(R.id.exit);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Uri contactUri = data.getData();
            mContactId = ContentUris.parseId(contactUri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mContactId != -1) {
            long rawContactId = -1;
            String accountType = null;
            String accountName = null;
            String dataSet = null;

            // Lookup the com.google raw contact for the contact.
            Cursor c = getContentResolver().query(RawContacts.CONTENT_URI,
                    new String[]{
                            RawContacts._ID,
                            RawContacts.ACCOUNT_TYPE,
                            RawContacts.ACCOUNT_NAME
                    },
                    RawContacts.CONTACT_ID + "=? AND " + RawContacts.ACCOUNT_TYPE + "=?",
                    new String[]{String.valueOf(mContactId), GoogleAccountType.ACCOUNT_TYPE}, null);
            try {
                c.moveToFirst();
                rawContactId = c.getLong(0);
                accountType = c.getString(1);
                accountName = c.getString(2);
            } finally {
                c.close();
            }
            if (rawContactId != -1) {
                addStreamItemsToRawContact(rawContactId, accountType, accountName);
            } else {
                Toast.makeText(this,
                        "Failed to find raw contact ID for contact ID " + mContactId, 5).show();
            }
        }
    }

    protected byte[] loadPhotoFromResource(int resourceId) {
        InputStream is = getResources().openRawResource(resourceId);
        return readInputStreamFully(is);
    }

    protected byte[] readInputStreamFully(InputStream is) {
        try {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addStreamItemsToRawContact(long rawContactId, String accountType,
            String accountName) {
        ArrayList<ContentProviderOperation> ops = Lists.newArrayList();

        // Add from 1-5 stream items.
        int itemsToAdd = randInt(5) + 1;
        int opCount = 0;
        for (int i = 0; i < itemsToAdd; i++) {
            ContentValues streamItemValues = buildStreamItemValues(accountType, accountName);
            ops.add(ContentProviderOperation.newInsert(
                    Uri.withAppendedPath(
                            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,
                                    rawContactId),
                            ContactsContract.RawContacts.StreamItems.CONTENT_DIRECTORY))
                    .withValues(streamItemValues).build());

            // Maybe add photos - 30% chance per stream item.
            boolean includePhotos = randInt(100) < 30;
            if (includePhotos) {
                // Add 1-5 photos if we're including any.
                int numPhotos = randInt(5) + 1;
                for (int j = 0; j < numPhotos; j++) {
                    ContentValues streamItemPhotoValues =
                            buildStreamItemPhotoValues(j, accountType, accountName);
                    ops.add(ContentProviderOperation.newInsert(StreamItems.CONTENT_PHOTO_URI)
                            .withValues(streamItemPhotoValues)
                            .withValueBackReference(StreamItemPhotos.STREAM_ITEM_ID, opCount)
                            .build());
                }
                opCount += numPhotos;
            }
            opCount++;
        }
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            // We don't care.  This is just for test purposes.
            throw new RuntimeException(e);
        }
        Toast.makeText(this, "Added " + itemsToAdd + " stream item(s) and "
                + (opCount - itemsToAdd) + " photos", 5).show();
    }

    private ContentValues buildStreamItemValues(String accountType, String accountName) {
        boolean includeAttribution = randInt(100) < 70;
        boolean includeComments = randInt(100) < 30;
        boolean includeAction = randInt(100) < 30;
        ContentValues values = new ContentValues();
        String place = pickRandom(placeNames);
        values.put(StreamItems.TEXT,
                String.format(pickRandom(snippetStrings) , place)
                + (includeComments ? " [c]" : "")
                + (includeAction ? " [a]" : ""));
        if (includeAttribution) {
            values.put(StreamItems.RES_PACKAGE, "com.android.contacts.tests");
            int sourceIndex = randInt(labelResources.length);
            values.put(StreamItems.RES_LABEL, labelResources[sourceIndex]);
            if (sourceIndex < iconResources.length) {
                values.put(StreamItems.RES_ICON, iconResources[sourceIndex]);
            }
        }
        if (includeComments) {
            values.put(StreamItems.COMMENTS, pickRandom(commentStrings));
        } else {
            values.put(StreamItems.COMMENTS, "");
        }
        // Set the timestamp to some point in the past.
        values.put(StreamItems.TIMESTAMP,
                System.currentTimeMillis() - randInt(360000000));
        values.put(RawContacts.ACCOUNT_TYPE, accountType);
        values.put(RawContacts.ACCOUNT_NAME, accountName);
        return values;
    }

    private ContentValues buildStreamItemPhotoValues(int index, String accountType,
            String accountName) {
        Integer imageIndex = pickRandom(imageIds);
        ContentValues values = new ContentValues();
        values.put(StreamItemPhotos.SORT_INDEX, index);
        values.put(StreamItemPhotos.PHOTO, loadPhotoFromResource(imageIndex));
        values.put(RawContacts.ACCOUNT_TYPE, accountType);
        values.put(RawContacts.ACCOUNT_NAME, accountName);
        return values;
    }

    private <T> T pickRandom(T[] from) {
        return from[randInt(from.length)];
    }

    private int randInt(int max) {
        return Math.abs(mRandom.nextInt()) % max;
    }
}
