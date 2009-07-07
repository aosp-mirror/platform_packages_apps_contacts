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
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.syncml.pim.VBuilder;
import android.syncml.pim.VBuilderCollection;
import android.syncml.pim.VParser;
import android.syncml.pim.vcard.VCardDataBuilder;
import android.syncml.pim.vcard.VCardEntryCounter;
import android.syncml.pim.vcard.VCardException;
import android.syncml.pim.vcard.VCardNestedException;
import android.syncml.pim.vcard.VCardParser_V21;
import android.syncml.pim.vcard.VCardParser_V30;
import android.syncml.pim.vcard.VCardSourceDetector;
import android.syncml.pim.vcard.VCardVersionException;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

class VCardFile {
    private String mName;
    private String mCanonicalPath;
    private long mLastModified;

    public VCardFile(String name, String canonicalPath, long lastModified) {
        mName = name;
        mCanonicalPath = canonicalPath;
        mLastModified = lastModified;
    }

    public String getName() {
        return mName;
    }

    public String getCanonicalPath() {
        return mCanonicalPath;
    }

    public long getLastModified() {
        return mLastModified;
    }
}

/**
 * Class for importing vCard. Several user interaction will be required while reading
 * (selecting a file, waiting a moment, etc.)
 */
public class ImportVCardActivity extends Activity {
    private static final String LOG_TAG = "ImportVCardActivity";
    private static final boolean DO_PERFORMANCE_PROFILE = false;

    private ProgressDialog mProgressDialog;
    private Handler mHandler = new Handler();
    private boolean mLastNameComesBeforeFirstName;

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

    private class ErrorDisplayer implements Runnable {
        private String mErrorMessage;

        public ErrorDisplayer(String errorMessage) {
            mErrorMessage = errorMessage;
        }

        public void run() {
            String message =
                getString(R.string.reading_vcard_failed_message, mErrorMessage);
            AlertDialog.Builder builder =
                new AlertDialog.Builder(ImportVCardActivity.this)
                    .setTitle(getString(R.string.reading_vcard_failed_title))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
            builder.show();
        }
    }

    private class VCardReadThread extends Thread
            implements DialogInterface.OnCancelListener {
        private String mCanonicalPath;
        private List<VCardFile> mVCardFileList;
        private ContentResolver mResolver;
        private VCardParser_V21 mVCardParser;
        private boolean mCanceled;
        private PowerManager.WakeLock mWakeLock;

        public VCardReadThread(String canonicalPath) {
            mCanonicalPath = canonicalPath;
            mVCardFileList = null;
            init();
        }

        public VCardReadThread(List<VCardFile> vcardFileList) {
            mCanonicalPath = null;
            mVCardFileList = vcardFileList;
            init();
        }

        private void init() {
            Context context = ImportVCardActivity.this;
            mResolver = context.getContentResolver();
            PowerManager powerManager = (PowerManager)context.getSystemService(
                    Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        }

        @Override
        public void finalize() {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }

        @Override
        public void run() {
            mWakeLock.acquire();
            // Some malicious vCard data may make this thread broken
            // (e.g. OutOfMemoryError).
            // Even in such cases, some should be done.
            try {
                if (mCanonicalPath != null) {
                    mProgressDialog.setProgressNumberFormat("");
                    mProgressDialog.setProgress(0);

                    // Count the number of VCard entries
                    mProgressDialog.setIndeterminate(true);
                    long start;
                    if (DO_PERFORMANCE_PROFILE) {
                        start = System.currentTimeMillis();
                    }
                    VCardEntryCounter counter = new VCardEntryCounter();
                    VCardSourceDetector detector = new VCardSourceDetector();
                    VBuilderCollection builderCollection = new VBuilderCollection(
                            Arrays.asList(counter, detector));
                    boolean result;
                    try {
                        result = readOneVCard(mCanonicalPath,
                                VParser.DEFAULT_CHARSET, builderCollection, null, true);
                    } catch (VCardNestedException e) {
                        try {
                            // Assume that VCardSourceDetector was able to detect the source.
                            // Try again with the detector.
                            result = readOneVCard(mCanonicalPath,
                                    VParser.DEFAULT_CHARSET, counter, detector, false);
                        } catch (VCardNestedException e2) {
                            result = false;
                            Log.e(LOG_TAG, "Must not reach here. " + e2);
                        }
                    }
                    if (DO_PERFORMANCE_PROFILE) {
                        long time = System.currentTimeMillis() - start;
                        Log.d(LOG_TAG, "time for counting the number of vCard entries: " +
                                time + " ms");
                    }
                    if (!result) {
                        return;
                    }

                    mProgressDialog.setProgressNumberFormat(
                            getString(R.string.reading_vcard_contacts));
                    mProgressDialog.setIndeterminate(false);
                    mProgressDialog.setMax(counter.getCount());
                    String charset = detector.getEstimatedCharset();
                    doActuallyReadOneVCard(charset, true, detector);
                } else {
                    mProgressDialog.setProgressNumberFormat(
                            getString(R.string.reading_vcard_files));
                    mProgressDialog.setMax(mVCardFileList.size());
                    mProgressDialog.setProgress(0);
                    for (VCardFile vcardFile : mVCardFileList) {
                        if (mCanceled) {
                            return;
                        }
                        String canonicalPath = vcardFile.getCanonicalPath();

                        VCardSourceDetector detector = new VCardSourceDetector();
                        try {
                            if (!readOneVCard(canonicalPath, VParser.DEFAULT_CHARSET, detector,
                                    null, true)) {
                                continue;
                            }
                        } catch (VCardNestedException e) {
                            // Assume that VCardSourceDetector was able to detect the source.
                        }
                        String charset = detector.getEstimatedCharset();
                        doActuallyReadOneVCard(charset, false, detector);
                        mProgressDialog.incrementProgressBy(1);
                    }
                }
            } finally {
                mWakeLock.release();
                mProgressDialog.dismiss();
                finish();
            }
        }

        private void doActuallyReadOneVCard(String charset, boolean doIncrementProgress,
                VCardSourceDetector detector) {
            VCardDataBuilder builder;
            final Context context = ImportVCardActivity.this;
            if (charset != null) {
                builder = new VCardDataBuilder(mResolver,
                        mProgressDialog,
                        context.getString(R.string.reading_vcard_message),
                        mHandler,
                        charset,
                        charset,
                        false,
                        mLastNameComesBeforeFirstName);
            } else {
                builder = new VCardDataBuilder(mResolver,
                        mProgressDialog,
                        context.getString(R.string.reading_vcard_message),
                        mHandler,
                        null,
                        null,
                        false,
                        mLastNameComesBeforeFirstName);
                charset = VParser.DEFAULT_CHARSET;
            }
            if (doIncrementProgress) {
                builder.setOnProgressRunnable(new Runnable() {
                    public void run() {
                        mProgressDialog.incrementProgressBy(1);
                    }
                });
            }
            try {
                readOneVCard(mCanonicalPath, charset, builder, detector, false);
            } catch (VCardNestedException e) {
                Log.e(LOG_TAG, "Must not reach here.");
            }
            builder.showDebugInfo();
        }

        private boolean readOneVCard(String canonicalPath, String charset, VBuilder builder,
                VCardSourceDetector detector, boolean throwNestedException)
                throws VCardNestedException {
            FileInputStream is;
            try {
                is = new FileInputStream(canonicalPath);
                mVCardParser = new VCardParser_V21(detector);

                try {
                    mVCardParser.parse(is, charset, builder, mCanceled);
                } catch (VCardVersionException e1) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                    is = new FileInputStream(canonicalPath);

                    try {
                        mVCardParser = new VCardParser_V30();
                        mVCardParser.parse(is, charset, builder, mCanceled);
                    } catch (VCardVersionException e2) {
                        throw new VCardException("vCard with unspported version.");
                    }
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                        }
                    }
                }
                mVCardParser.showDebugInfo();
            } catch (IOException e) {
                Log.e(LOG_TAG, "IOException was emitted: " + e);

                mProgressDialog.dismiss();

                mHandler.post(new ErrorDisplayer(
                        getString(R.string.fail_reason_io_error) +
                        " (" + e.getMessage() + ")"));
                return false;
            } catch (VCardNestedException e) {
                if (throwNestedException) {
                    throw e;
                } else {
                    Log.e(LOG_TAG, "VCardNestedException was emitted: " + e);
                    mHandler.post(new ErrorDisplayer(
                            getString(R.string.fail_reason_vcard_parse_error) +
                            " (" + e.getMessage() + ")"));
                    return false;
                }
            } catch (VCardException e) {
                Log.e(LOG_TAG, "VCardException was emitted: " + e);

                mHandler.post(new ErrorDisplayer(
                        getString(R.string.fail_reason_vcard_parse_error) +
                        " (" + e.getMessage() + ")"));
                return false;
            }
            return true;
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
            if (mVCardParser != null) {
                mVCardParser.cancel();
            }
        }
    }

    private class ImportTypeSelectedListener implements
            DialogInterface.OnClickListener {
        public static final int IMPORT_ALL = 0;
        public static final int IMPORT_ONE = 1;

        private List<VCardFile> mVCardFileList;
        private int mCurrentIndex;

        public ImportTypeSelectedListener(List<VCardFile> vcardFileList) {
            mVCardFileList = vcardFileList;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                if (mCurrentIndex == IMPORT_ALL) {
                    importAllVCardFromSDCard(mVCardFileList);
                } else {
                    showVCardFileSelectDialog(mVCardFileList);
                }
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                finish();
            } else {
                mCurrentIndex = which;
            }
        }
    }

    private class VCardSelectedListener implements DialogInterface.OnClickListener {
        private List<VCardFile> mVCardFileList;
        private int mCurrentIndex;

        public VCardSelectedListener(List<VCardFile> vcardFileList) {
            mVCardFileList = vcardFileList;
            mCurrentIndex = 0;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                importOneVCardFromSDCard(mVCardFileList.get(mCurrentIndex).getCanonicalPath());
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                finish();
            } else {
                // Some file is selected.
                mCurrentIndex = which;
            }
        }
    }

    /**
     * Thread scanning VCard from SDCard. After scanning, the dialog which lets a user select
     * a vCard file is shown. After the choice, VCardReadThread starts running.
     */
    private class VCardScanThread extends Thread implements OnCancelListener, OnClickListener {
        private boolean mCanceled;
        private boolean mGotIOException;
        private File mRootDirectory;

        // null when search operation is canceled.
        private List<VCardFile> mVCardFiles;

        // To avoid recursive link.
        private Set<String> mCheckedPaths;
        private PowerManager.WakeLock mWakeLock;

        private class CanceledException extends Exception {
        }

        public VCardScanThread(File sdcardDirectory) {
            mCanceled = false;
            mGotIOException = false;
            mRootDirectory = sdcardDirectory;
            mCheckedPaths = new HashSet<String>();
            mVCardFiles = new Vector<VCardFile>();
            PowerManager powerManager = (PowerManager)ImportVCardActivity.this.getSystemService(
                    Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        }

        @Override
        public void run() {
            try {
                mWakeLock.acquire();
                getVCardFileRecursively(mRootDirectory);
            } catch (CanceledException e) {
                mCanceled = true;
            } catch (IOException e) {
                mGotIOException = true;
            } finally {
                mWakeLock.release();
            }

            if (mCanceled) {
                mVCardFiles = null;
            }

            mProgressDialog.dismiss();

            if (mGotIOException) {
                mHandler.post(new Runnable() {
                    public void run() {
                        String message = (getString(R.string.scanning_sdcard_failed_message,
                                getString(R.string.fail_reason_io_error)));

                        AlertDialog.Builder builder =
                            new AlertDialog.Builder(ImportVCardActivity.this)
                                .setTitle(R.string.scanning_sdcard_failed_title)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setMessage(message)
                                .setOnCancelListener(mCancelListener)
                                .setPositiveButton(android.R.string.ok, mCancelListener);
                        builder.show();
                    }
                });
            } else if (mCanceled) {
                finish();
            } else {
                mHandler.post(new Runnable() {
                    public void run() {
                        int size = mVCardFiles.size();
                        final Context context = ImportVCardActivity.this;
                        if (size == 0) {
                            String message = (getString(R.string.scanning_sdcard_failed_message,
                                    getString(R.string.fail_reason_no_vcard_file)));

                            AlertDialog.Builder builder =
                                new AlertDialog.Builder(context)
                                    .setTitle(R.string.scanning_sdcard_failed_title)
                                    .setMessage(message)
                                    .setOnCancelListener(mCancelListener)
                                    .setPositiveButton(android.R.string.ok, mCancelListener);
                            builder.show();
                            return;
                        } else if (context.getResources().getBoolean(
                                R.bool.config_import_all_vcard_from_sdcard_automatically)) {
                            importAllVCardFromSDCard(mVCardFiles);
                        } else if (size == 1) {
                            importOneVCardFromSDCard(mVCardFiles.get(0).getCanonicalPath());
                        } else if (context.getResources().getBoolean(
                                R.bool.config_allow_users_select_all_vcard_import)) {
                            showSelectImportTypeDialog(mVCardFiles);
                        } else {
                            showVCardFileSelectDialog(mVCardFiles);
                        }
                    }
                });
            }
        }

        private void getVCardFileRecursively(File directory)
                throws CanceledException, IOException {
            if (mCanceled) {
                throw new CanceledException();
            }

            for (File file : directory.listFiles()) {
                if (mCanceled) {
                    throw new CanceledException();
                }
                String canonicalPath = file.getCanonicalPath();
                if (mCheckedPaths.contains(canonicalPath)) {
                    continue;
                }

                mCheckedPaths.add(canonicalPath);

                if (file.isDirectory()) {
                    getVCardFileRecursively(file);
                } else if (canonicalPath.toLowerCase().endsWith(".vcf") &&
                        file.canRead()){
                    String fileName = file.getName();
                    VCardFile vcardFile = new VCardFile(
                            fileName, canonicalPath, file.lastModified());
                    mVCardFiles.add(vcardFile);
                }
            }
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
            }
        }
    }

    
    private void importOneVCardFromSDCard(String canonicalPath) {
        VCardReadThread thread = new VCardReadThread(canonicalPath);
        showReadingVCardDialog(thread);
        thread.start();
    }

    private void importAllVCardFromSDCard(List<VCardFile> vcardFileList) {
        VCardReadThread thread = new VCardReadThread(vcardFileList);
        showReadingVCardDialog(thread);
        thread.start();
    }

    private void showSelectImportTypeDialog(List<VCardFile> vcardFileList) {
        DialogInterface.OnClickListener listener =
            new ImportTypeSelectedListener(vcardFileList);
        AlertDialog.Builder builder =
            new AlertDialog.Builder(ImportVCardActivity.this)
                .setTitle(R.string.select_vcard_title)
                .setPositiveButton(android.R.string.ok, listener)
                .setOnCancelListener(mCancelListener)
                .setNegativeButton(android.R.string.cancel, mCancelListener);

        String[] items = new String[2];
        items[ImportTypeSelectedListener.IMPORT_ALL] =
            getString(R.string.import_all_vcard_string);
        items[ImportTypeSelectedListener.IMPORT_ONE] =
            getString(R.string.import_one_vcard_string);
        builder.setSingleChoiceItems(items,
                ImportTypeSelectedListener.IMPORT_ALL, listener);
        builder.show();
    }

    private void showVCardFileSelectDialog(List<VCardFile> vcardFileList) {
        int size = vcardFileList.size();
        DialogInterface.OnClickListener listener =
            new VCardSelectedListener(vcardFileList);
        AlertDialog.Builder builder =
            new AlertDialog.Builder(this)
                .setTitle(R.string.select_vcard_title)
                .setPositiveButton(android.R.string.ok, listener)
                .setOnCancelListener(mCancelListener)
                .setNegativeButton(android.R.string.cancel, mCancelListener);

        CharSequence[] items = new CharSequence[size];
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < size; i++) {
            VCardFile vcardFile = vcardFileList.get(i);
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
            stringBuilder.append(vcardFile.getName());
            stringBuilder.append('\n');
            int indexToBeSpanned = stringBuilder.length();
            // Smaller date text looks better, since each file name becomes easier to read.
            // The value set to RelativeSizeSpan is arbitrary. You can change it to any other
            // value (but the value bigger than 1.0f would not make nice appearance :)
            stringBuilder.append(
                        "(" + dateFormat.format(new Date(vcardFile.getLastModified())) + ")");
            stringBuilder.setSpan(
                    new RelativeSizeSpan(0.7f), indexToBeSpanned, stringBuilder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            items[i] = stringBuilder;
        }
        builder.setSingleChoiceItems(items, 0, listener);
        builder.show();
    }

    private void showReadingVCardDialog(DialogInterface.OnCancelListener listener) {
        String title = getString(R.string.reading_vcard_title);
        String message = getString(R.string.reading_vcard_message);
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(message);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setOnCancelListener(listener);
        mProgressDialog.show();
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        mLastNameComesBeforeFirstName = getResources().getBoolean(
                com.android.internal.R.bool.config_lastname_comes_before_firstname);

        startImportVCardFromSdCard();
    }

    /**
     * Tries to start importing VCard. If there's no SDCard available,
     * an error dialog is shown. If there is, start scanning using another thread
     * and shows a progress dialog. Several interactions will occur.
     * This method should be called from a thread with a looper (like Activity).
     */
    public void startImportVCardFromSdCard() {
        File file = new File("/sdcard");
        if (!file.exists() || !file.isDirectory() || !file.canRead()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.no_sdcard_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.no_sdcard_message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener)
                    .show();
        } else {
            String title = getString(R.string.searching_vcard_title);
            String message = getString(R.string.searching_vcard_message);

            mProgressDialog = ProgressDialog.show(this, title, message, true, false);
            VCardScanThread thread = new VCardScanThread(file);
            mProgressDialog.setOnCancelListener(thread);
            thread.start();
        }
    }
}
