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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Handler;
import android.os.PowerManager;
import android.pim.vcard.VCardComposer;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

public class VCardExporter {
    private static final String LOG_TAG = "VCardExporter";

    // If true, VCardExporter is able to emits files longer than 8.3 format.
    private static final boolean ALLOW_LONG_FILE_NAME = false;
    private final String mTargetDirectory;
    private final String mFileNamePrefix;
    private final String mFileNameSuffix;
    private final int mFileIndexMinimum;
    private final int mFileIndexMaximum;
    private final String mFileNameExtension;
    private final String mVCardTypeStr;
    private final Set<String> mExtensionsToConsider;

    private ContactsListActivity mParentActivity;
    private ProgressDialog mProgressDialog;

    // Used temporaly when asking users to confirm the file name
    private String mTargetFileName;

    // String for storing error reason temporaly.
    private String mErrorReason;

    private ActualExportThread mActualExportThread;

    private class ConfirmListener implements DialogInterface.OnClickListener {
        private String mFileName;

        public ConfirmListener(String fileName) {
            mFileName = fileName;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                mActualExportThread = new ActualExportThread(mFileName);
                String title = getString(R.string.exporting_contact_list_title);
                String message = getString(R.string.exporting_contact_list_message, mFileName);
                mProgressDialog = new ProgressDialog(mParentActivity);
                mProgressDialog.setTitle(title);
                mProgressDialog.setMessage(message);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setOnCancelListener(mActualExportThread);
                mParentActivity.showDialog(R.id.dialog_exporting_vcard);
                mActualExportThread.start();
            }
        }
    }

    private class ActualExportThread extends Thread
            implements DialogInterface.OnCancelListener {
        private PowerManager.WakeLock mWakeLock;
        private String mFileName;
        private boolean mCanceled = false;

        public ActualExportThread(String fileName) {
            mFileName = fileName;
            PowerManager powerManager = (PowerManager)mParentActivity.getSystemService(
                    Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        }

        @Override
        public void run() {
            mWakeLock.acquire();
            VCardComposer composer = null;
            try {
                OutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(mFileName);
                } catch (FileNotFoundException e) {
                    mErrorReason = getString(R.string.fail_reason_could_not_open_file,
                            mFileName, e.getMessage());
                    mParentActivity.showDialog(R.id.dialog_fail_to_export_with_reason);
                    return;
                }

                composer = new VCardComposer(mParentActivity, mVCardTypeStr, true);
                // composer = new VCardComposer(mParentContext,
                // VCardConfig.VCARD_TYPE_V30_JAPANESE_UTF8, true);
                composer.addHandler(composer.new HandlerForOutputStream(outputStream));

                if (!composer.init()) {
                    mErrorReason = getString(R.string.fail_reason_could_not_initialize_exporter,
                            composer.getErrorReason());
                    mParentActivity.showDialog(R.id.dialog_fail_to_export_with_reason);
                    return;
                }

                int size = composer.getCount();

                mProgressDialog.setProgressNumberFormat(
                        getString(R.string.exporting_contact_list_progress));
                mProgressDialog.setMax(size);
                mProgressDialog.setProgress(0);

                while (!composer.isAfterLast()) {
                    if (mCanceled) {
                        return;
                    }
                    if (!composer.createOneEntry()) {
                        Log.e(LOG_TAG, "Failed to read a contact.");
                        mErrorReason = getString(R.string.fail_reason_error_occurred_during_export,
                                composer.getErrorReason());
                        mParentActivity.showDialog(R.id.dialog_fail_to_export_with_reason);
                        return;
                    }
                    mProgressDialog.incrementProgressBy(1);
                }
            } finally {
                if (composer != null) {
                    composer.terminate();
                }
                mWakeLock.release();
                mProgressDialog.dismiss();
                mParentActivity.removeReferenceToVCardExporter();
            }
        }

        @Override
        public void finalize() {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }

        public void cancel() {
            mCanceled = true;
        }

        public void onCancel(DialogInterface dialog) {
            cancel();
        }
    }

    /**
     * @param parentActivity must not be null
     */
    public VCardExporter(ContactsListActivity parentActivity) {
        mParentActivity = parentActivity;
        mTargetDirectory = getString(R.string.config_export_dir);
        mFileNamePrefix = getString(R.string.config_export_file_prefix);
        mFileNameSuffix = getString(R.string.config_export_file_suffix);
        mFileNameExtension = getString(R.string.config_export_file_extension);
        mVCardTypeStr = getString(R.string.config_export_vcard_type);

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

        Resources resources = parentActivity.getResources();
        mFileIndexMinimum = resources.getInteger(R.integer.config_export_file_min_index);
        mFileIndexMaximum = resources.getInteger(R.integer.config_export_file_max_index);
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
            mParentActivity.showDialog(R.id.dialog_sdcard_not_found);
        } else {
            mTargetFileName = getAppropriateFileName(mTargetDirectory);
            if (TextUtils.isEmpty(mTargetFileName)) {
                mTargetFileName = null;
                return;
            }

            mParentActivity.showDialog(R.id.dialog_confirm_export_vcard);
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
                mParentActivity.showDialog(R.id.dialog_fail_to_export_with_reason);
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
        mParentActivity.showDialog(R.string.fail_reason_too_many_vcard);
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

        return new AlertDialog.Builder(mParentActivity)
            .setTitle(R.string.confirm_export_title)
            .setMessage(getString(R.string.confirm_export_message, mTargetFileName))
            .setPositiveButton(android.R.string.ok, new ConfirmListener(mTargetFileName))
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    }

    public Dialog getExportingVCardDialog() {
        return mProgressDialog;
    }

    public Dialog getErrorDialogWithReason() {
        if (mErrorReason == null) {
            Log.e(LOG_TAG, "Error reason must have been set.");
            mErrorReason = getString(R.string.fail_reason_unknown);
        }
        return new AlertDialog.Builder(mParentActivity)
            .setTitle(R.string.exporting_contact_failed_title)
                .setMessage(getString(R.string.exporting_contact_failed_message, mErrorReason))
            .setPositiveButton(android.R.string.ok, null)
            .create();
    }

    public void cancelExport() {
        if (mActualExportThread != null) {
            mActualExportThread.cancel();
            mActualExportThread = null;
        }
    }

    private String getString(int resId, Object... formatArgs) {
        return mParentActivity.getString(resId, formatArgs);
    }

    private String getString(int resId) {
        return mParentActivity.getString(resId);
    }
}