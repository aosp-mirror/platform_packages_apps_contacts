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
package com.android.contacts.vcard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.android.contacts.R;

/**
 * The Activity for canceling ongoing vCard import.
 *
 * Currently we ignore tha case where there are more than one import requests
 * with a same Uri in the queue.
 */
public class CancelImportActivity extends Activity {
    private final String LOG_TAG = "VCardImporter";

    /* package */ final String EXTRA_TARGET_URI = "extra_target_uri";

    private class CustomConnection implements ServiceConnection {
        private Messenger mMessenger;
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMessenger = new Messenger(service);

            try {
                mMessenger.send(Message.obtain(null,
                        VCardService.MSG_CANCEL_IMPORT_REQUEST,
                        null));
                finish();
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "RemoteException is thrown when trying to send request");
                CancelImportActivity.this.showDialog(R.string.fail_reason_unknown);
            } finally {
                CancelImportActivity.this.unbindService(this);
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMessenger = null;
        }
    }

    private class RequestCancelImportListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            bindService(new Intent(CancelImportActivity.this,
                    VCardService.class), mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private class CancelListener
            implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }
        @Override
        public void onCancel(DialogInterface dialog) {
            finish();
        }
    }

    private final CancelListener mCancelListener = new CancelListener();
    private final CustomConnection mConnection = new CustomConnection();
    // private String mTargetUri;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showDialog(R.id.dialog_cancel_import_confirmation);
    }

    @Override
    protected Dialog onCreateDialog(int resId, Bundle bundle) {
        switch (resId) {

        case R.id.dialog_cancel_import_confirmation: {
            return getConfirmationDialog();
        }
        case R.string.fail_reason_unknown:
            final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.reading_vcard_failed_title))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(getString(resId))
                .setOnCancelListener(mCancelListener)
                .setPositiveButton(android.R.string.ok, mCancelListener);
            return builder.create();
        }
        return super.onCreateDialog(resId, bundle);
    }

    private Dialog getConfirmationDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_import_confirmation_title)
                .setMessage(R.string.cancel_import_confirmation_message)
                .setPositiveButton(android.R.string.ok, new RequestCancelImportListener())
                .setOnCancelListener(mCancelListener)
                .setNegativeButton(android.R.string.cancel, mCancelListener);
        return builder.create();
    }
}