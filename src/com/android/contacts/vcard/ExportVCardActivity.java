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
package com.android.contacts.vcard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.R;
import com.android.vcard.VCardComposer;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Class for exporting vCard.
 *
 * Note that this Activity assumes that the instance is a "one-shot Activity", which will be
 * finished (with the method {@link Activity#finish()}) after the export and never reuse
 * any Dialog in the instance. So this code is careless about the management around managed
 * dialogs stuffs (like how onCreateDialog() is used).
 */
public class ExportVCardActivity extends Activity {
    private static final String LOG_TAG = "ExportVCardActivity";

    // If true, VCardExporter is able to emits files longer than 8.3 format.
    private static final boolean ALLOW_LONG_FILE_NAME = false;
    private String mTargetDirectory;
    private String mFileNamePrefix;
    private String mFileNameSuffix;
    private int mFileIndexMinimum;
    private int mFileIndexMaximum;
    private String mFileNameExtension;
    private Set<String> mExtensionsToConsider;

    // Used temporarily when asking users to confirm the file name
    private String mTargetFileName;

    // String for storing error reason temporarily.
    private String mErrorReason;


    private class CustomConnection implements ServiceConnection {
        private Messenger mMessenger;
        private Queue<ExportRequest> mPendingRequests = new LinkedList<ExportRequest>();

        public void doBindService() {
            bindService(new Intent(ExportVCardActivity.this,
                    VCardService.class), this, Context.BIND_AUTO_CREATE);
        }

        public synchronized void requestSend(final ExportRequest parameter) {
            if (mMessenger != null) {
                sendMessage(parameter);
            } else {
                mPendingRequests.add(parameter);
            }
        }

        private void sendMessage(final ExportRequest request) {
            try {
                mMessenger.send(Message.obtain(null,
                        VCardService.MSG_EXPORT_REQUEST,
                        request));
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "RemoteException is thrown when trying to send request");
                runOnUiThread(new ErrorReasonDisplayer(
                        getString(R.string.fail_reason_unknown)));
            }
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (this) {
                mMessenger = new Messenger(service);
                // Send pending requests thrown from this Activity before an actual connection
                // is established.
                while (!mPendingRequests.isEmpty()) {
                    final ExportRequest parameter = mPendingRequests.poll();
                    if (parameter == null) {
                        throw new NullPointerException();
                    }
                    sendMessage(parameter);
                }

                unbindService(this);
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            synchronized (this) {
                if (!mPendingRequests.isEmpty()) {
                    Log.w(LOG_TAG, "Some request(s) are dropped.");
                }
                // Set to null so that we can detect inappropriate re-connection toward
                // the Service via NullPointerException;
                mPendingRequests = null;
                mMessenger = null;
            }
        }
    }

    private final CustomConnection mConnection = new CustomConnection();

    private class CancelListener
            implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }
        public void onCancel(DialogInterface dialog) {
            finish();
        }
    }

    private CancelListener mCancelListener = new CancelListener();

    private class ErrorReasonDisplayer implements Runnable {
        private final int mResId;
        public ErrorReasonDisplayer(int resId) {
            mResId = resId;
        }
        public ErrorReasonDisplayer(String errorReason) {
            mResId = R.id.dialog_fail_to_export_with_reason;
            mErrorReason = errorReason;
        }
        public void run() {
            // Show the Dialog only when the parent Activity is still alive.
            if (!ExportVCardActivity.this.isFinishing()) {
                showDialog(mResId);
            }
        }
    }

    private class ExportConfirmationListener implements DialogInterface.OnClickListener {
        private final Uri mDestUri;

        public ExportConfirmationListener(String fileName) {
            this(Uri.parse("file://" + fileName));
        }

        public ExportConfirmationListener(Uri uri) {
            mDestUri = uri;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                mConnection.doBindService();

                final ExportRequest request = new ExportRequest(mDestUri);

                // The connection object will call finish().
                mConnection.requestSend(request);
            }
        }
    }

    private String translateComposerError(String errorMessage) {
        Resources resources = getResources();
        if (VCardComposer.FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO.equals(errorMessage)) {
            return resources.getString(R.string.composer_failed_to_get_database_infomation);
        } else if (VCardComposer.FAILURE_REASON_NO_ENTRY.equals(errorMessage)) {
            return resources.getString(R.string.composer_has_no_exportable_contact);
        } else if (VCardComposer.FAILURE_REASON_NOT_INITIALIZED.equals(errorMessage)) {
            return resources.getString(R.string.composer_not_initialized);
        } else {
            return errorMessage;
        }
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        mTargetDirectory = getString(R.string.config_export_dir);
        mFileNamePrefix = getString(R.string.config_export_file_prefix);
        mFileNameSuffix = getString(R.string.config_export_file_suffix);
        mFileNameExtension = getString(R.string.config_export_file_extension);

        mExtensionsToConsider = new HashSet<String>();
        mExtensionsToConsider.add(mFileNameExtension);

        final String additionalExtensions =
            getString(R.string.config_export_extensions_to_consider);
        if (!TextUtils.isEmpty(additionalExtensions)) {
            for (String extension : additionalExtensions.split(",")) {
                String trimed = extension.trim();
                if (trimed.length() > 0) {
                    mExtensionsToConsider.add(trimed);
                }
            }
        }

        final Resources resources = getResources();
        mFileIndexMinimum = resources.getInteger(R.integer.config_export_file_min_index);
        mFileIndexMaximum = resources.getInteger(R.integer.config_export_file_max_index);

        startExportVCardToSdCard();
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.id.dialog_export_confirmation: {
                return getExportConfirmationDialog();
            }
            case R.string.fail_reason_too_many_vcard: {
                return new AlertDialog.Builder(this)
                    .setTitle(R.string.exporting_contact_failed_title)
                    .setMessage(getString(R.string.exporting_contact_failed_message,
                                getString(R.string.fail_reason_too_many_vcard)))
                                .setPositiveButton(android.R.string.ok, mCancelListener)
                                .create();
            }
            case R.id.dialog_fail_to_export_with_reason: {
                return getErrorDialogWithReason();
            }
            case R.id.dialog_sdcard_not_found: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.no_sdcard_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(R.string.no_sdcard_message)
                .setPositiveButton(android.R.string.ok, mCancelListener);
                return builder.create();
            }
        }
        return super.onCreateDialog(id, bundle);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        if (id == R.id.dialog_fail_to_export_with_reason) {
            ((AlertDialog)dialog).setMessage(getErrorReason());
        } else if (id == R.id.dialog_export_confirmation) {
            ((AlertDialog)dialog).setMessage(
                    getString(R.string.confirm_export_message, mTargetFileName));
        } else {
            super.onPrepareDialog(id, dialog, args);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!isFinishing()) {
            finish();
        }
    }

    /**
     * Tries to start exporting VCard. If there's no SDCard available,
     * an error dialog is shown.
     */
    public void startExportVCardToSdCard() {
        File targetDirectory = new File(mTargetDirectory);

        if (!(targetDirectory.exists() &&
                targetDirectory.isDirectory() &&
                targetDirectory.canRead()) &&
                !targetDirectory.mkdirs()) {
            showDialog(R.id.dialog_sdcard_not_found);
        } else {
            mTargetFileName = getAppropriateFileName(mTargetDirectory);
            if (TextUtils.isEmpty(mTargetFileName)) {
                mTargetFileName = null;
                // finish() is called via the error dialog. Do not call the method here.
                return;
            }

            showDialog(R.id.dialog_export_confirmation);
        }
    }

    /**
     * Tries to get an appropriate filename. Returns null if it fails.
     */
    private String getAppropriateFileName(final String destDirectory) {
        int fileNumberStringLength = 0;
        {
            // Calling Math.Log10() is costly.
            int tmp;
            for (fileNumberStringLength = 0, tmp = mFileIndexMaximum; tmp > 0;
                fileNumberStringLength++, tmp /= 10) {
            }
        }
        String bodyFormat = "%s%0" + fileNumberStringLength + "d%s";

        if (!ALLOW_LONG_FILE_NAME) {
            String possibleBody = String.format(bodyFormat,mFileNamePrefix, 1, mFileNameSuffix);
            if (possibleBody.length() > 8 || mFileNameExtension.length() > 3) {
                Log.e(LOG_TAG, "This code does not allow any long file name.");
                mErrorReason = getString(R.string.fail_reason_too_long_filename,
                        String.format("%s.%s", possibleBody, mFileNameExtension));
                showDialog(R.id.dialog_fail_to_export_with_reason);
                // finish() is called via the error dialog. Do not call the method here.
                return null;
            }
        }

        // Note that this logic assumes that the target directory is case insensitive.
        // As of 2009-07-16, it is true since the external storage is only sdcard, and
        // it is formated as FAT/VFAT.
        // TODO: fix this.
        for (int i = mFileIndexMinimum; i <= mFileIndexMaximum; i++) {
            boolean numberIsAvailable = true;
            // SD Association's specification seems to require this feature, though we cannot
            // have the specification since it is proprietary...
            String body = null;
            for (String possibleExtension : mExtensionsToConsider) {
                body = String.format(bodyFormat, mFileNamePrefix, i, mFileNameSuffix);
                File file = new File(String.format("%s/%s.%s",
                        destDirectory, body, possibleExtension));
                if (file.exists()) {
                    numberIsAvailable = false;
                    break;
                }
            }
            if (numberIsAvailable) {
                return String.format("%s/%s.%s", destDirectory, body, mFileNameExtension);
            }
        }
        showDialog(R.string.fail_reason_too_many_vcard);
        return null;
    }

    public Dialog getExportConfirmationDialog() {
        if (TextUtils.isEmpty(mTargetFileName)) {
            Log.e(LOG_TAG, "Target file name is empty, which must not be!");
            // This situation is not acceptable (probably a bug!), but we don't have no reason to
            // show...
            mErrorReason = null;
            return getErrorDialogWithReason();
        }

        return new AlertDialog.Builder(this)
            .setTitle(R.string.confirm_export_title)
            .setMessage(getString(R.string.confirm_export_message, mTargetFileName))
            .setPositiveButton(android.R.string.ok,
                    new ExportConfirmationListener(mTargetFileName))
            .setNegativeButton(android.R.string.cancel, mCancelListener)
            .setOnCancelListener(mCancelListener)
            .create();
    }

    public Dialog getErrorDialogWithReason() {
        if (mErrorReason == null) {
            Log.e(LOG_TAG, "Error reason must have been set.");
            mErrorReason = getString(R.string.fail_reason_unknown);
        }
        return new AlertDialog.Builder(this)
            .setTitle(R.string.exporting_contact_failed_title)
                .setMessage(getString(R.string.exporting_contact_failed_message, mErrorReason))
            .setPositiveButton(android.R.string.ok, mCancelListener)
            .setOnCancelListener(mCancelListener)
            .create();
    }

    public String getErrorReason() {
        return mErrorReason;
    }
}