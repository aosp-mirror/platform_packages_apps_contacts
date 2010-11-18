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
package com.android.contacts.list;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.ContactsContract.ProviderStatus;

/**
 * Checks provider status and configures a list adapter accordingly.
 */
public class ProviderStatusLoader extends ContentObserver {

    /**
     * Callback interface invoked when the provider status changes.
     */
    public interface ProviderStatusListener {
        public void onProviderStatusChange();
    }

    private static final String[] PROJECTION = new String[] {
        ProviderStatus.STATUS,
        ProviderStatus.DATA1
    };

    private static final int UNKNOWN = -1;

    private final Context mContext;
    private int mProviderStatus = UNKNOWN;
    private String mProviderData;
    private ProviderStatusListener mListener;
    private Handler mHandler = new Handler();

    public ProviderStatusLoader(Context context) {
        super(null);
        this.mContext = context;
    }

    public int getProviderStatus() {
        if (mProviderStatus == UNKNOWN) {
            loadProviderStatus();
        }

        return mProviderStatus;
    }

    public String getProviderStatusData() {
        if (mProviderStatus == UNKNOWN) {
            loadProviderStatus();
        }

        return mProviderData;
    }

    protected void loadProviderStatus() {

        // Default to normal status
        mProviderStatus = ProviderStatus.STATUS_NORMAL;

        // This query can be performed on the UI thread because
        // the API explicitly allows such use.
        Cursor cursor = mContext.getContentResolver().query(ProviderStatus.CONTENT_URI,
                PROJECTION, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    mProviderStatus = cursor.getInt(0);
                    mProviderData = cursor.getString(1);
                }
            } finally {
                cursor.close();
            }
        }
    }

    public void setProviderStatusListener(ProviderStatusListener listener) {
        mListener = listener;

        ContentResolver resolver = mContext.getContentResolver();
        if (listener != null) {
            mProviderStatus = UNKNOWN;
            resolver.registerContentObserver(ProviderStatus.CONTENT_URI, false, this);
        } else {
            resolver.unregisterContentObserver(this);
        }
    }

    @Override
    public void onChange(boolean selfChange) {
        // Deliver a notification on the UI thread
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    mProviderStatus = UNKNOWN;
                    mListener.onProviderStatusChange();
                }
            }
        });
    }

    /**
     * Sends a provider status update, which will trigger a retry of database upgrade
     */
    public void retryUpgrade() {
        ContentValues values = new ContentValues();
        values.put(ProviderStatus.STATUS, ProviderStatus.STATUS_UPGRADING);
        mContext.getContentResolver().update(ProviderStatus.CONTENT_URI, values, null, null);
    }
}
