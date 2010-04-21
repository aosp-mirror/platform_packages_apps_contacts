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

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Log;

import com.android.contacts.model.Sources;
import com.android.contacts.util.AccountSelectionUtil;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;



/**
 * The class letting users to import vCard. This includes the UI part for letting them select
 * an Account and posssibly a file if there's no Uri is given from its caller Activity.
 *
 * Note that this Activity assumes that the instance is a "one-shot Activity", which will be
 * finished (with the method {@link Activity#finish()}) after the import and never reuse
 * any Dialog in the instance. So this code is careless about the management around managed
 * dialogs stuffs (like how onCreateDialog() is used).
 */
public class ImportVCardActivity extends Activity {
    private static final String LOG_TAG = "ImportVCardActivity";

    private static final int SELECT_ACCOUNT = 0;

    /* package */ static final String VCARD_URI_ARRAY = "vcard_uri_array";

    // Run on the UI thread. Must not be null except after onDestroy().
    private Handler mHandler = new Handler();

    private AccountSelectionUtil.AccountSelectedListener mAccountSelectionListener;
    private String mAccountName;
    private String mAccountType;

    private String mAction;
    private Uri mUri;

    private ProgressDialog mProgressDialogForScanVCard;

    private List<VCardFile> mAllVCardFileList;
    private VCardScanThread mVCardScanThread;

    private String mErrorMessage;

    private static class VCardFile {
        private final String mName;
        private final String mCanonicalPath;
        private final long mLastModified;

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

    // Runs on the UI thread.
    private class DialogDisplayer implements Runnable {
        private final int mResId;
        public DialogDisplayer(int resId) {
            mResId = resId;
        }
        public DialogDisplayer(String errorMessage) {
            mResId = R.id.dialog_error_with_message;
            mErrorMessage = errorMessage;
        }
        public void run() {
            showDialog(mResId);
        }
    }

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

    private class ImportTypeSelectedListener implements
            DialogInterface.OnClickListener {
        public static final int IMPORT_ONE = 0;
        public static final int IMPORT_MULTIPLE = 1;
        public static final int IMPORT_ALL = 2;
        public static final int IMPORT_TYPE_SIZE = 3;

        private int mCurrentIndex;

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                switch (mCurrentIndex) {
                case IMPORT_ALL:
                    importVCardFromSDCard(mAllVCardFileList);
                    break;
                case IMPORT_MULTIPLE:
                    showDialog(R.id.dialog_select_multiple_vcard);
                    break;
                default:
                    showDialog(R.id.dialog_select_one_vcard);
                    break;
                }
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                finish();
            } else {
                mCurrentIndex = which;
            }
        }
    }

    private class VCardSelectedListener implements
            DialogInterface.OnClickListener, DialogInterface.OnMultiChoiceClickListener {
        private int mCurrentIndex;
        private Set<Integer> mSelectedIndexSet;

        public VCardSelectedListener(boolean multipleSelect) {
            mCurrentIndex = 0;
            if (multipleSelect) {
                mSelectedIndexSet = new HashSet<Integer>();
            }
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                if (mSelectedIndexSet != null) {
                    List<VCardFile> selectedVCardFileList = new ArrayList<VCardFile>();
                    final int size = mAllVCardFileList.size();
                    // We'd like to sort the files by its index, so we do not use Set iterator.
                    for (int i = 0; i < size; i++) {
                        if (mSelectedIndexSet.contains(i)) {
                            selectedVCardFileList.add(mAllVCardFileList.get(i));
                        }
                    }
                    importVCardFromSDCard(selectedVCardFileList);
                } else {
                    importVCardFromSDCard(mAllVCardFileList.get(mCurrentIndex));
                }
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                finish();
            } else {
                // Some file is selected.
                mCurrentIndex = which;
                if (mSelectedIndexSet != null) {
                    if (mSelectedIndexSet.contains(which)) {
                        mSelectedIndexSet.remove(which);
                    } else {
                        mSelectedIndexSet.add(which);
                    }
                }
            }
        }

        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
            if (mSelectedIndexSet == null || (mSelectedIndexSet.contains(which) == isChecked)) {
                Log.e(LOG_TAG, String.format("Inconsist state in index %d (%s)", which,
                        mAllVCardFileList.get(which).getCanonicalPath()));
            } else {
                onClick(dialog, which);
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
            PowerManager powerManager = (PowerManager)ImportVCardActivity.this.getSystemService(
                    Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        }

        @Override
        public void run() {
            mAllVCardFileList = new Vector<VCardFile>();
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
                mAllVCardFileList = null;
            }

            mProgressDialogForScanVCard.dismiss();
            mProgressDialogForScanVCard = null;

            if (mGotIOException) {
                runOnUIThread(new DialogDisplayer(R.id.dialog_io_exception));
            } else if (mCanceled) {
                finish();
            } else {
                int size = mAllVCardFileList.size();
                final Context context = ImportVCardActivity.this;
                if (size == 0) {
                    runOnUIThread(new DialogDisplayer(R.id.dialog_vcard_not_found));
                } else {
                    startVCardSelectAndImport();
                }
            }
        }

        private void getVCardFileRecursively(File directory)
                throws CanceledException, IOException {
            if (mCanceled) {
                throw new CanceledException();
            }

            // e.g. secured directory may return null toward listFiles().
            final File[] files = directory.listFiles();
            if (files == null) {
                Log.w(LOG_TAG, "listFiles() returned null (directory: " + directory + ")");
                return;
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
                    mAllVCardFileList.add(vcardFile);
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

    private void startVCardSelectAndImport() {
        int size = mAllVCardFileList.size();
        if (getResources().getBoolean(R.bool.config_import_all_vcard_from_sdcard_automatically) ||
                size == 1) {
            importVCardFromSDCard(mAllVCardFileList);
        } else if (getResources().getBoolean(R.bool.config_allow_users_select_all_vcard_import)) {
            runOnUIThread(new DialogDisplayer(R.id.dialog_select_import_type));
        } else {
            runOnUIThread(new DialogDisplayer(R.id.dialog_select_one_vcard));
        }
    }

    private void importVCardFromSDCard(final List<VCardFile> selectedVCardFileList) {
        final int size = selectedVCardFileList.size();
        String[] uriStrings = new String[size];
        int i = 0;
        for (VCardFile vcardFile : selectedVCardFileList) {
            uriStrings[i] = "file://" + vcardFile.getCanonicalPath();
            i++;
        }
        importVCard(uriStrings);
    }
    
    private void importVCardFromSDCard(final VCardFile vcardFile) {
        String[] uriStrings = new String[1];
        uriStrings[0] = "file://" + vcardFile.getCanonicalPath();
        importVCard(uriStrings);
    }

    private void importVCard(final String uriString) {
        String[] uriStrings = new String[1];
        uriStrings[0] = uriString;
        importVCard(uriStrings);
    }

    private void importVCard(final String[] uriStrings) {
        final Intent intent = new Intent(this, ImportVCardService.class);
        intent.putExtra(VCARD_URI_ARRAY, uriStrings);
        intent.putExtra(SelectAccountActivity.ACCOUNT_NAME, mAccountName);
        intent.putExtra(SelectAccountActivity.ACCOUNT_TYPE, mAccountType);

        // TODO: permission is not migrated to ImportVCardService, so some exception is
        // thrown when reading some Uri, permission of which is temporarily guaranteed
        // to ImportVCardActivity, not ImportVCardService.
        startService(intent);
        finish();
    }

    private Dialog getSelectImportTypeDialog() {
        final DialogInterface.OnClickListener listener = new ImportTypeSelectedListener();
        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.select_vcard_title)
                .setPositiveButton(android.R.string.ok, listener)
                .setOnCancelListener(mCancelListener)
                .setNegativeButton(android.R.string.cancel, mCancelListener);

        final String[] items = new String[ImportTypeSelectedListener.IMPORT_TYPE_SIZE];
        items[ImportTypeSelectedListener.IMPORT_ONE] =
                getString(R.string.import_one_vcard_string);
        items[ImportTypeSelectedListener.IMPORT_MULTIPLE] =
                getString(R.string.import_multiple_vcard_string);
        items[ImportTypeSelectedListener.IMPORT_ALL] =
                getString(R.string.import_all_vcard_string);
        builder.setSingleChoiceItems(items, ImportTypeSelectedListener.IMPORT_ONE, listener);
        return builder.create();
    }

    private Dialog getVCardFileSelectDialog(boolean multipleSelect) {
        final int size = mAllVCardFileList.size();
        final VCardSelectedListener listener = new VCardSelectedListener(multipleSelect);
        final AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.select_vcard_title)
                        .setPositiveButton(android.R.string.ok, listener)
                        .setOnCancelListener(mCancelListener)
                        .setNegativeButton(android.R.string.cancel, mCancelListener);

        CharSequence[] items = new CharSequence[size];
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < size; i++) {
            VCardFile vcardFile = mAllVCardFileList.get(i);
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
        if (multipleSelect) {
            builder.setMultiChoiceItems(items, (boolean[])null, listener);
        } else {
            builder.setSingleChoiceItems(items, 0, listener);
        }
        return builder.create();
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        final Intent intent = getIntent();
        if (intent != null) {
            mAccountName = intent.getStringExtra(SelectAccountActivity.ACCOUNT_NAME);
            mAccountType = intent.getStringExtra(SelectAccountActivity.ACCOUNT_TYPE);
            mAction = intent.getAction();
            mUri = intent.getData();
        } else {
            Log.e(LOG_TAG, "intent does not exist");
        }

        // The caller may not know account information at all, so we show the UI instead.
        if (TextUtils.isEmpty(mAccountName) || TextUtils.isEmpty(mAccountType)) {
            final Sources sources = Sources.getInstance(this);
            final List<Account> accountList = sources.getAccounts(true);
            if (accountList.size() == 0) {
                mAccountName = null;
                mAccountType = null;
            } else if (accountList.size() == 1) {
                final Account account = accountList.get(0);
                mAccountName = account.name;
                mAccountType = account.type;
            } else {
                startActivityForResult(new Intent(this, SelectAccountActivity.class),
                        SELECT_ACCOUNT);
                return;
            }
        }
        startImport(mAction, mUri);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SELECT_ACCOUNT) {
            if (resultCode == RESULT_OK) {
                mAccountName = intent.getStringExtra(SelectAccountActivity.ACCOUNT_NAME);
                mAccountType = intent.getStringExtra(SelectAccountActivity.ACCOUNT_TYPE);
                startImport(mAction, mUri);
            } else {
                if (resultCode != RESULT_CANCELED) {
                    Log.w(LOG_TAG, "Result code was not OK nor CANCELED: " + resultCode);
                }
                finish();
            }
        }
    }

    private void startImport(String action, Uri uri) {
        Log.d(LOG_TAG, "action = " + action + " ; path = " + uri);

        if (uri != null) {
            importVCard(uri.toString());
        } else {
            doScanExternalStorageAndImportVCard();
        }
    }

    @Override
    protected Dialog onCreateDialog(int resId, Bundle bundle) {
        switch (resId) {
            case R.string.import_from_sdcard: {
                if (mAccountSelectionListener == null) {
                    throw new NullPointerException(
                            "mAccountSelectionListener must not be null.");
                }
                return AccountSelectionUtil.getSelectAccountDialog(this, resId,
                        mAccountSelectionListener, mCancelListener);
            }
            case R.id.dialog_searching_vcard: {
                if (mProgressDialogForScanVCard == null) {
                    String title = getString(R.string.searching_vcard_title);
                    String message = getString(R.string.searching_vcard_message);
                    mProgressDialogForScanVCard =
                        ProgressDialog.show(this, title, message, true, false);
                    mProgressDialogForScanVCard.setOnCancelListener(mVCardScanThread);
                    mVCardScanThread.start();
                }
                return mProgressDialogForScanVCard;
            }
            case R.id.dialog_sdcard_not_found: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.no_sdcard_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.no_sdcard_message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
                return builder.create();
            }
            case R.id.dialog_vcard_not_found: {
                String message = (getString(R.string.scanning_sdcard_failed_message,
                        getString(R.string.fail_reason_no_vcard_file)));
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.scanning_sdcard_failed_title)
                    .setMessage(message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
                return builder.create();
            }
            case R.id.dialog_select_import_type: {
                return getSelectImportTypeDialog();
            }
            case R.id.dialog_select_multiple_vcard: {
                return getVCardFileSelectDialog(true);
            }
            case R.id.dialog_select_one_vcard: {
                return getVCardFileSelectDialog(false);
            }
            case R.id.dialog_io_exception: {
                String message = (getString(R.string.scanning_sdcard_failed_message,
                        getString(R.string.fail_reason_io_error)));
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.scanning_sdcard_failed_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
                return builder.create();
            }
            case R.id.dialog_error_with_message: {
                String message = mErrorMessage;
                if (TextUtils.isEmpty(message)) {
                    Log.e(LOG_TAG, "Error message is null while it must not.");
                    message = getString(R.string.fail_reason_unknown);
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.reading_vcard_failed_title))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
                return builder.create();
            }
        }

        return super.onCreateDialog(resId, bundle);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // ImportVCardActivity should not be persistent. In other words, if there's some
        // event calling onPause(), this Activity should finish its work and give the main
        // screen back to the caller Activity.
        if (!isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        // The code assumes the handler runs on the UI thread. If not,
        // clearing the message queue is not enough, one would have to
        // make sure that the handler does not run any callback when
        // this activity isFinishing().

        // Callbacks messages have what == 0.
        if (mHandler.hasMessages(0)) {
            mHandler.removeMessages(0);
        }

        mHandler = null;  // Prevents memory leaks by breaking any circular dependency.
        super.onDestroy();
    }

    /**
     * Tries to run a given Runnable object when the UI thread can. Ignore it otherwise
     */
    private void runOnUIThread(Runnable runnable) {
        if (mHandler == null) {
            Log.w(LOG_TAG, "Handler object is null. No dialog is shown.");
        } else {
            mHandler.post(runnable);
        }
    }

    /**
     * Scans vCard in external storage (typically SDCard) and tries to import it.
     * - When there's no SDCard available, an error dialog is shown.
     * - When multiple vCard files are available, asks a user to select one.
     */
    private void doScanExternalStorageAndImportVCard() {
        // TODO: should use getExternalStorageState().
        final File file = Environment.getExternalStorageDirectory();
        if (!file.exists() || !file.isDirectory() || !file.canRead()) {
            showDialog(R.id.dialog_sdcard_not_found);
        } else {
            mVCardScanThread = new VCardScanThread(file);
            showDialog(R.id.dialog_searching_vcard);
        }
    }
}
