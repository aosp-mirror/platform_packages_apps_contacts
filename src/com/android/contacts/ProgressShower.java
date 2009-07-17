/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.ProgressDialog;
import android.os.Handler;
import android.pim.vcard.ContactStruct;
import android.pim.vcard.EntryHandler;
import android.pim.vcard.VCardConfig;
import android.util.Log;

public class ProgressShower implements EntryHandler {
    public static final String LOG_TAG = "vcard.ProgressShower"; 

    private final Handler mHandler;
    private final ProgressDialog mProgressDialog;
    private final String mProgressMessage;
    private final boolean mIncrementProgress;

    private long mTime;
    
    private class ShowProgressRunnable implements Runnable {
        private ContactStruct mContact;
        
        public ShowProgressRunnable(ContactStruct contact) {
            mContact = contact;
        }
        
        public void run() {
            mProgressDialog.setMessage(mProgressMessage + "\n" + 
                    mContact.displayString());
            if (mIncrementProgress) {
                mProgressDialog.incrementProgressBy(1);
            }
        }
    }
    
    public ProgressShower(ProgressDialog progressDialog,
            String progressMessage,
            Handler handler, 
            boolean incrementProgress) {
        mHandler = handler;
        mProgressDialog = progressDialog;
        mProgressMessage = progressMessage;
        mIncrementProgress = incrementProgress;
    }
    
    public void onEntryCreated(ContactStruct contactStruct) {
        long start = System.currentTimeMillis();
        
        if (!contactStruct.isIgnorable()) {
            if (mProgressDialog != null && mProgressMessage != null) {
                if (mHandler != null) {
                    mHandler.post(new ShowProgressRunnable(contactStruct));
                } else {
                    mProgressDialog.setMessage(mProgressMessage + "\n" + 
                            contactStruct.displayString());
                }
            }
        }
        
        mTime += System.currentTimeMillis() - start;
    }

    public void onFinal() {
        if (VCardConfig.showPerformanceLog()) {
            Log.d(LOG_TAG,
                    String.format("Time to progress a dialog: %ld ms", mTime));
        }
    }
}