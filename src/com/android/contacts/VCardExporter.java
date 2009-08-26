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

import android.app.AlertDialog;
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

    private Context mParentContext;
    private Handler mParentHandler;
    private ProgressDialog mProgressDialog;

    private class ErrorMessageDisplayRunnable implements Runnable {
        private String mReason;
        public ErrorMessageDisplayRunnable(String reason) {
            mReason = reason;
        }

        public void run() {
            new AlertDialog.Builder(mParentContext)
                .setTitle(getString(R.string.exporting_contact_failed_title))
                .setMessage(getString(R.string.exporting_contact_failed_message, mReason))
                .setPositiveButton(android.R.string.ok, null)
                .show();
        }
    }

    private class ConfirmListener implements DialogInterface.OnClickListener {
        private String mFileName;

        public ConfirmListener(String fileName) {
            mFileName = fileName;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                startExport(mFileName);
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
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
            PowerManager powerManager = (PowerManager)mParentContext.getSystemService(
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
                    String reason = getString(R.string.fail_reason_could_not_open_file,
                            mFileName, e.getMessage());
                    mParentHandler.post(new ErrorMessageDisplayRunnable(reason));
                    return;
                }

                composer = new VCardComposer(mParentContext, mVCardTypeStr, true);
                // composer = new VCardComposer(mParentContext,
                // VCardConfig.VCARD_TYPE_V30_JAPANESE_UTF8, true);
                composer.addHandler(composer.new HandlerForOutputStream(outputStream));

                if (!composer.init()) {
                    String reason = getString(R.string.fail_reason_could_not_initialize_exporter,
                            composer.getErrorReason());
                    mParentHandler.post(new ErrorMessageDisplayRunnable(reason));
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
                        String reason = getString(R.string.fail_reason_error_occurred_during_export,
                                composer.getErrorReason());
                        mParentHandler.post(new ErrorMessageDisplayRunnable(reason));
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
            }
        }

        @Override
        public void finalize() {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
        }
    }

    /**
     * @param parentContext must not be null
     * @param parentHandler must not be null
     */
    public VCardExporter(Context parentContext, Handler parentHandler) {
        mParentContext = parentContext;
        mParentHandler = parentHandler;
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

        Resources resources = parentContext.getResources();
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
            new AlertDialog.Builder(mParentContext)
                    .setTitle(R.string.no_sdcard_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.no_sdcard_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            String fileName = getAppropriateFileName(mTargetDirectory);
            if (TextUtils.isEmpty(fileName)) {
                return;
            }

            new AlertDialog.Builder(mParentContext)
                .setTitle(R.string.confirm_export_title)
                .setMessage(getString(R.string.confirm_export_message, fileName))
                .setPositiveButton(android.R.string.ok, new ConfirmListener(fileName))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
                displayErrorMessage(getString(R.string.fail_reason_too_long_filename,
                        String.format("%s.%s", possibleBody, mFileNameExtension)));
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
        displayErrorMessage(getString(R.string.fail_reason_too_many_vcard));
        return null;
    }

    private void startExport(String fileName) {
        ActualExportThread thread = new ActualExportThread(fileName);
        displayReadingVCardDialog(thread, fileName);
        thread.start();
    }

    private void displayReadingVCardDialog(DialogInterface.OnCancelListener listener,
            String fileName) {
        String title = getString(R.string.exporting_contact_list_title);
        String message = getString(R.string.exporting_contact_list_message, fileName);
        mProgressDialog = new ProgressDialog(mParentContext);
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(message);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setOnCancelListener(listener);
        mProgressDialog.show();
    }

    private void displayErrorMessage(String failureReason) {
        new AlertDialog.Builder(mParentContext)
            .setTitle(R.string.exporting_contact_failed_title)
            .setMessage(getString(R.string.exporting_contact_failed_message,
                    failureReason))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private String getString(int resId, Object... formatArgs) {
        return mParentContext.getString(resId, formatArgs);
    }

    private String getString(int resId) {
        return mParentContext.getString(resId);
    }
}