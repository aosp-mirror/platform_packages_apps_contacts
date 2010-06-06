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
package com.android.contacts;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

/**
 * The class responsible for importing vCard from one ore multiple Uris.
 */
public class ImportVCardService extends Service {
    private final static String LOG_TAG = "ImportVCardService";

    /* package */ static final int MSG_IMPORT_REQUEST = 1;

    /* package */ static final int NOTIFICATION_ID = 1000;

    /**
     * Small vCard file is imported soon, so any meassage saying "vCard import started" is
     * not needed. We show the message when the size of vCard is larger than this constant. 
     */
    private static final int IMPORT_NOTIFICATION_THRESHOLD = 10; 

    public class ImportRequestHandler extends Handler {
        private final ImportRequestProcessor mRequestProcessor =
                new ImportRequestProcessor(ImportVCardService.this);
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_IMPORT_REQUEST: {
                    final ImportRequest parameter = (ImportRequest)msg.obj;
                    if (parameter.entryCount > IMPORT_NOTIFICATION_THRESHOLD) {
                        Toast.makeText(ImportVCardService.this,
                                getString(R.string.vcard_importer_start_message),
                                Toast.LENGTH_LONG).show();
                    }
                    mRequestProcessor.pushRequest(parameter);
                    break;
                }
                default:
                    Log.e(LOG_TAG, "Unknown request type: " + msg.what);
                    super.hasMessages(msg.what);
            }
        }
    }

    private Messenger mMessenger = new Messenger(new ImportRequestHandler());

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }
}
