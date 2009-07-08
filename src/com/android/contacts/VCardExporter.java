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
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.syncml.pim.PropertyNode;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.CharsetUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final String mVCardType;
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
            VCardExporterImpl exporterImpl = null;
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

                TelephonyManager telephonyManager =
                    (TelephonyManager)mParentContext.getSystemService(
                            Context.TELEPHONY_SERVICE);

                exporterImpl = new VCardExporterImpl(mParentContext.getContentResolver(),
                        outputStream, mVCardType);

                if (!exporterImpl.init()) {
                    String reason = getString(R.string.fail_reason_could_not_initialize_exporter,
                            exporterImpl.getErrorReason());
                    mParentHandler.post(new ErrorMessageDisplayRunnable(reason));
                    return;
                }

                int size = exporterImpl.getCount();

                mProgressDialog.setProgressNumberFormat(
                        getString(R.string.exporting_contact_list_progress));
                mProgressDialog.setMax(size);
                mProgressDialog.setProgress(0);

                while (!exporterImpl.isAfterLast()) {
                    if (mCanceled) {
                        return;
                    }
                    if (!exporterImpl.exportOneContactData()) {
                        Log.e(LOG_TAG, "Failed to read a contact.");
                        String reason = getString(R.string.fail_reason_error_occurred_during_export,
                                exporterImpl.getErrorReason());
                        mParentHandler.post(new ErrorMessageDisplayRunnable(reason));
                        return;
                    }
                    mProgressDialog.incrementProgressBy(1);
                }
            } finally {
                if (exporterImpl != null) {
                    exporterImpl.terminate();
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
        mVCardType = getString(R.string.config_export_vcard_type);

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

// TODO: This class should be splitted into two parts; exporter part and composer part.
class VCardExporterImpl {
    private static final String LOG_TAG = "VCardExporterImpl";

    /* Type of exporting VCard. */
    // General VCard. Use UTF-8 and do not care vendor specific things.
    public static int VCARD_TYPE_GENERIC = 0;
    // VCard format used in DoCoMo. Shift_Jis is used as charset.
    public static int VCARD_TYPE_DOCOMO = 1;
    public static final String VCARD_TYPE_STRING_DOCOMO = "docomo";

    private static final String VCARD_PROPERTY_ADR = "ADR";
    private static final String VCARD_PROPERTY_BEGIN = "BEGIN";
    private static final String VCARD_PROPERTY_EMAIL = "EMAIL";
    private static final String VCARD_PROPERTY_END = "END";
    private static final String VCARD_PROPERTY_NAME = "N";
    private static final String VCARD_PROPERTY_NOTE = "NOTE";
    private static final String VCARD_PROPERTY_ORG = "ORG";
    private static final String VCARD_PROPERTY_SOUND = "SOUND";
    private static final String VCARD_PROPERTY_TEL = "TEL";
    private static final String VCARD_PROPERTY_TITLE = "TITLE";
    private static final String VCARD_PROPERTY_PHOTO = "PHOTO";
    private static final String VCARD_PROPERTY_VERSION = "VERSION";
    private static final String VCARD_PROPERTY_BDAY = "BDAY";
    private static final String VCARD_PROPERTY_URL = "URL";

    // Properties for DoCoMo vCard.
    private static final String VCARD_PROPERTY_X_CLASS = "X-CLASS";
    private static final String VCARD_PROPERTY_X_REDUCTION = "X-REDUCTION";
    private static final String VCARD_PROPERTY_X_NO = "X-NO";
    private static final String VCARD_PROPERTY_X_DCM_HMN_MODE = "X-DCM-HMN-MODE";

    private static final String VCARD_DATA_VCARD = "VCARD";
    private static final String VCARD_DATA_VERSION_V21 = "2.1";
    private static final String VCARD_DATA_PUBLIC = "PUBLIC";

    private static final String VCARD_ATTR_SEPARATOR = ";";
    private static final String VCARD_COL_SEPARATOR = "\r\n";
    private static final String VCARD_DATA_SEPARATOR = ":";
    private static final String VCARD_ITEM_SEPARATOR = ";";
    private static final String VCARD_WS = " ";

    private static final String VCARD_ATTR_VOICE = "VOICE";
    private static final String VCARD_ATTR_CELL = "CELL";
    private static final String VCARD_ATTR_WORK = "WORK";
    private static final String VCARD_ATTR_HOME = "HOME";
    private static final String VCARD_ATTR_FAX = "FAX";
    private static final String VCARD_ATTR_INTERNET = "INTERNET";
    private static final String VCARD_ATTR_ENCODING_QP = "ENCODING=QUOTED-PRINTABLE";
    private static final String VCARD_ATTR_ENCODING_BASE64_V21 = "ENCODING=BASE64";
    // This is just a reminder: in VCard 3.0, do not use the string "BASE64"
    @SuppressWarnings("unused")
    private static final String VCARD_ATTR_ENCODING_BASE64_V30 = "ENCODING=b";

    // DoCoMo specific attribute.Used with "SOUND" property.
    private static final String VCARD_ATTR_X_IRMC_N = "X-IRMC-N";

    private static final String SHIFT_JIS = "SHIFT_JIS";

    private Cursor mCursor;
    private int mIdColumn;
    private int mNameColumn;
    private int mNotesColumn;
    private int mPhoneticNameColumn;
    private ContentResolver mContentResolver;

    private int mVCardType;
    private String mCharsetString;
    private static String mVCardAttributeCharset;
    private OutputStream mOutputStream;  // mWriter will close this.
    private Writer mWriter;
    private boolean mTerminateIsCalled;

    private String mErrorReason = "No error";

    /**
     * @param resolver
     * @param outputStream close() must not be called outside.
     * @param vcardType
     */
    public VCardExporterImpl(ContentResolver resolver, OutputStream outputStream, int vcardType) {
        mContentResolver = resolver;
        mOutputStream = outputStream;

        mVCardType = vcardType;
        if (vcardType == VCARD_TYPE_DOCOMO) {
            mCharsetString = CharsetUtils.charsetForVendor(SHIFT_JIS, "docomo").name();
            mVCardAttributeCharset = "CHARSET=" + SHIFT_JIS;
        } else {
            mCharsetString = "UTF-8";
            mVCardAttributeCharset = "CHARSET=UTF-8";
        }
    }

    public VCardExporterImpl(ContentResolver resolver, OutputStream outputStream, String vcardType) {
        this(resolver, outputStream,
                (vcardType.equalsIgnoreCase(VCARD_TYPE_STRING_DOCOMO) ?
                        VCARD_TYPE_DOCOMO : VCARD_TYPE_GENERIC));
    }

    /**
     * @return Returns true when initialization is successful and all the other methods are
     * available. Returns false otherwise.
     */
    public boolean init() {
        try {
            mWriter = new BufferedWriter(
                    new OutputStreamWriter(mOutputStream, mCharsetString));
        } catch (UnsupportedEncodingException e1) {
            Log.e(LOG_TAG, "Unsupported charset: " + mCharsetString);
            mErrorReason = "Encoding is not supported (usually this does not happen!): " +
                mCharsetString;
            return false;
        }

        final String[] projection = new String[] {
                People._ID,
                People.NAME,
                People.NOTES,
                People.PHONETIC_NAME,
        };

        mCursor = mContentResolver.query(People.CONTENT_URI, projection, null, null, null);
        if (mCursor == null || !mCursor.moveToFirst()) {
            if (mCursor != null) {
                try {
                    mCursor.close();
                } catch (SQLiteException e) {
                }
                mCursor = null;
            }
            mErrorReason = "Getting database information failed.";
            return false;
        }

        mIdColumn = mCursor.getColumnIndex(People._ID);
        mNameColumn = mCursor.getColumnIndex(People.NAME);
        mNotesColumn = mCursor.getColumnIndex(People.NOTES);
        mPhoneticNameColumn = mCursor.getColumnIndex(People.PHONETIC_NAME);

        if (mVCardType == VCARD_TYPE_DOCOMO) {
            try {
                mWriter.write(convertContactToVCard(new ContactData()));
            } catch (IOException e) {
                Log.e(LOG_TAG, "IOException occurred during exportOneContactData: " +
                        e.getMessage());
                mErrorReason = "IOException occurred: " + e.getMessage();
            }
        }

        return true;
    }

    @Override
    public void finalize() {
        if (!mTerminateIsCalled) {
            terminate();
        }
    }

    public void terminate() {
        if (mWriter != null) {
            try {
                // Flush and sync the data so that a user is able to pull the SDCard just after the
                // export.
                mWriter.flush();
                if (mOutputStream != null && mOutputStream instanceof FileOutputStream) {
                    try {
                        ((FileOutputStream)mOutputStream).getFD().sync();
                    } catch (IOException e) {
                    }
                }
                mWriter.close();
            } catch (IOException e) {
            }
        }
        if (mCursor != null) {
            try {
                mCursor.close();
            } catch (SQLiteException e) {
            }
            mCursor = null;
        }
    }

    public int getCount() {
        if (mCursor == null) {
            return 0;
        }
        return mCursor.getCount();
    }

    public boolean isAfterLast() {
        if (mCursor == null) {
            return false;
        }
        return mCursor.isAfterLast();
    }

    public boolean exportOneContactData() {
        if (mCursor == null || mCursor.isAfterLast()) {
            mErrorReason = "Not initialized or database has some problem.";
            return false;
        }
        String name = null;
        try {
            ContactData contactData = new ContactData();
            int personId = mCursor.getInt(mIdColumn);
            name = contactData.mName = mCursor.getString(mNameColumn);
            contactData.mNote = mCursor.getString(mNotesColumn);
            contactData.mPhoneticName = mCursor.getString(mPhoneticNameColumn);

            readAllPhones(contactData, personId);
            readAllAddresses(contactData, personId);
            readAllOrgs(contactData, personId);
            readAllPhotos(contactData, personId);
            readAllExtensions(contactData, personId);

            mCursor.moveToNext();

            String vcardString = convertContactToVCard(contactData);
            try {
                mWriter.write(vcardString);
            } catch (IOException e) {
                Log.e(LOG_TAG, "IOException occurred during exportOneContactData: " +
                        e.getMessage());
                mErrorReason = "IOException occurred: " + e.getMessage();
                return false;
            }
        } catch (OutOfMemoryError error) {
            // Maybe some data (e.g. photo) is too big to have in memory. But it should be rare.
            Log.e(LOG_TAG, "OutOfMemoryError occured. Ignore the entry: " + name);
            System.gc();
        }

        return true;
    }

    /**
     * @return Return the error reason if possible.
     */
    public String getErrorReason() {
        return mErrorReason;
    }

    private void readAllPhones(ContactData contact, int personId) {
        final String[] projection = new String[] {
                Contacts.Phones.TYPE,
                Contacts.Phones.LABEL,
                Contacts.Phones.NUMBER,
                Contacts.Phones.LABEL,
        };
        String selection = String.format("%s=%d", Contacts.Phones.PERSON_ID, personId);
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(Contacts.Phones.CONTENT_URI,
                    projection, selection, null, null);
            if ((cursor != null) && (cursor.moveToFirst())) {
                int typeColumn = cursor.getColumnIndex(Contacts.Phones.TYPE);
                int labelColumn = cursor.getColumnIndex(Contacts.Phones.LABEL);
                int numberColumn = cursor.getColumnIndex(Contacts.Phones.NUMBER);
                do {
                    TelData telData = new TelData(cursor.getInt(typeColumn),
                            cursor.getString(labelColumn), cursor.getString(numberColumn));
                    contact.mTel.add(telData);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void readAllPhotos(ContactData contact, int personId) {
        final String[] projection = new String[] {
                Contacts.Photos.DATA,
        };
        String selection = String.format("%s=%d", Contacts.Photos.PERSON_ID, personId);
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(Contacts.Photos.CONTENT_URI,
                    projection, selection, null, null);
            if ((cursor != null) && (cursor.moveToFirst())) {
                int dataColumn = cursor.getColumnIndex(Contacts.Photos.DATA);

                byte[] data;
                do {
                    data = cursor.getBlob(dataColumn);
                    // Use some heuristics for guessing the format of the image.
                    if (data != null && data.length > 0) {
                        if (data.length >= 3 &&
                                data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
                            contact.mPhotoType = "GIF";
                        } else if (data.length >= 4 &&
                                data[0] == (byte)0x89 && data[1] == 'P' && data[2] == 'N' &&
                                data[3] == 'G') {
                            // Note: vCard 2.1 officially does not support PNG, but we may have it
                            // and using X- word like "X-PNG" may not let importers know it is
                            // PNG. So we use the String "PNG" as is...
                            contact.mPhotoType = "PNG";
                        } else if (data.length >= 2 &&
                                data[0] == (byte)0xff && data[1] == (byte)0xd8) {
                            contact.mPhotoType = "JPEG";
                        } else {
                            // TODO: vCard specification requires the other formats like TIFF...
                            Log.d(LOG_TAG, "Unknown photo type. Ignore.");
                            continue;
                        }
                    }
                    String photoData = encodeBase64(data);
                    if (photoData.length() > 0) {
                        contact.mPhoto = photoData;
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void readAllAddresses(ContactData contact, int personId) {
        final String[] projection = new String[] {
                Contacts.ContactMethods.TYPE,
                Contacts.ContactMethods.LABEL,
                Contacts.ContactMethods.DATA,
                Contacts.ContactMethods.KIND,
        };
        String selection = String.format("%s=%d AND %s IN (1,2)",
                Contacts.ContactMethods.PERSON_ID, personId, Contacts.ContactMethods.KIND);
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(Contacts.ContactMethods.CONTENT_URI,
                    projection, selection, null, null);
            if ((cursor != null) && (cursor.moveToFirst())) {
                int typeColumn = cursor.getColumnIndex(Contacts.ContactMethods.TYPE);
                int labelColumn = cursor.getColumnIndex(Contacts.ContactMethods.LABEL);
                int dataColumn = cursor.getColumnIndex(Contacts.ContactMethods.DATA);
                int kindColumn = cursor.getColumnIndex(Contacts.ContactMethods.KIND);
                do {
                    int kind = cursor.getInt(kindColumn);

                    switch(kind) {
                    case Contacts.KIND_EMAIL:
                        EmailData emailData = new EmailData(cursor.getInt(typeColumn),
                                cursor.getString(labelColumn), cursor.getString(dataColumn));
                        contact.mEmail.add(emailData);
                        break;
                    case Contacts.KIND_POSTAL:
                        AddressData addr = new AddressData(cursor.getInt(typeColumn),
                                cursor.getString(labelColumn), cursor.getString(dataColumn));
                        contact.mAddr.add(addr);
                        break;
                    default:
                        break;
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void readAllOrgs(ContactData contactData, int personId) {
        final String[] projection = new String[] {
                Contacts.Organizations.COMPANY,
                Contacts.Organizations.TITLE,
        };
        String selection = String.format("%s=%d", Contacts.ContactMethods.PERSON_ID, personId);
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(Contacts.Organizations.CONTENT_URI,
                    projection, selection, null, null);
            if ((cursor != null) && (cursor.moveToFirst())) {
                int companyColumn = cursor.getColumnIndex(Contacts.Organizations.COMPANY);
                int titleColumn = cursor.getColumnIndex(Contacts.Organizations.TITLE);
                do {
                    contactData.mOrg =  cursor.getString(companyColumn);
                    contactData.mTitle = cursor.getString(titleColumn);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void readAllExtensions(ContactData contactData, int personId) {
        final String[] projection = new String[] {
                Contacts.Extensions.NAME,
                Contacts.Extensions.VALUE,
        };
        String selection = String.format("%s=%d", Contacts.Extensions.PERSON_ID, personId);
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(Contacts.Extensions.CONTENT_URI,
                    projection, selection, null, null);
            if ((cursor != null) && (cursor.moveToFirst())) {
                int nameColumn = cursor.getColumnIndex(Contacts.Extensions.NAME);
                int valueColumn = cursor.getColumnIndex(Contacts.Extensions.VALUE);
                do {
                    contactData.mExtensions.put(
                            cursor.getString(nameColumn),
                            cursor.getString(valueColumn));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String toHalfWidthString(String orgString) {
        StringBuilder builder = new StringBuilder();
        int length = orgString.length();
        for (int i = 0; i < length; i++) {
            // All Japanese character is able to be expressed by char.
            // Do not need to use String#codepPointAt().
            char ch = orgString.charAt(i);
            CharSequence halfWidthText = JapaneseUtils.tryGetHalfWidthText(ch);
            if (halfWidthText != null) {
                builder.append(halfWidthText);
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String encodeSomeCharacters(String str) {
        char[] strArray = str.toCharArray();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < strArray.length; i++) {
            char ch = strArray[i];
            switch (ch) {
            case ';':
                builder.append('\\');
                builder.append(';');
                break;
            case '\r':
            case '\n':
                // ignore
                break;
            case '\\':
            case '<':
            case '>':
                if (mVCardType == VCARD_TYPE_DOCOMO) {
                    builder.append('\\');
                    builder.append(ch);
                }
                break;
            default:
                builder.append(ch);
                break;
            }
        }
        return builder.toString();
    }

    private String convertContactToVCard(ContactData contactData) {
        // Some DoCoMo mobile devices cannot parse a VCard data which does not have empty field.
        final boolean isDoCoMo = (mVCardType == VCARD_TYPE_DOCOMO);
        StringBuilder builder = new StringBuilder();
        appendVCardLine(builder, VCARD_PROPERTY_BEGIN, VCARD_DATA_VCARD);
        appendVCardLine(builder, VCARD_PROPERTY_VERSION, VCARD_DATA_VERSION_V21);

        if (!TextUtils.isEmpty(contactData.mName)) {
            builder.append(VCARD_PROPERTY_NAME);
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(mVCardAttributeCharset);
            builder.append(VCARD_DATA_SEPARATOR);
            builder.append(encodeSomeCharacters(contactData.mName));
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_COL_SEPARATOR);
        } else if (isDoCoMo) {
            appendVCardLine(builder, VCARD_PROPERTY_NAME, "");
        }

        if (!TextUtils.isEmpty(contactData.mPhoneticName)) {
            // Note: There is no appropriate property for expressing phonetic name in VCard 2.1,
            //       while there is in VCard 3.0 (SORT-STRING).
            //       We choose to use DoCoMo's way since it is supported by Japanese mobile phones.
            builder.append(VCARD_PROPERTY_SOUND);
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(VCARD_ATTR_X_IRMC_N);
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(mVCardAttributeCharset);
            builder.append(VCARD_DATA_SEPARATOR);
            // TODO: Not only DoCoMo but also other Japanese mobile careers requires this.
            String phoneticName =
                (isDoCoMo ? toHalfWidthString(contactData.mPhoneticName) :
                    contactData.mPhoneticName);
            builder.append(encodeSomeCharacters(phoneticName));
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_COL_SEPARATOR);
        } else if (isDoCoMo) {
            // VCARD_ITEM_SEPARATOR should be inserted for DoCoMo devices.
            builder.append(VCARD_PROPERTY_SOUND);
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(VCARD_ATTR_X_IRMC_N);
            builder.append(VCARD_DATA_SEPARATOR);
            // Empty data.
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_COL_SEPARATOR);
        }

        if (contactData.mTel.size() > 0) {
            for (TelData telData : contactData.mTel) {
                appendVCardTelephoneLine(builder, telData.mType,
                        telData.mLabel, telData.mValue);
            }
        } else if (isDoCoMo) {
            appendVCardTelephoneLine(builder, Contacts.Phones.TYPE_HOME, "", "");
        }

        if (contactData.mEmail.size() > 0) {
            for (EmailData emailData : contactData.mEmail) {
                appendVCardEmailLine(builder, emailData.mType,
                        emailData.mLabel, emailData.mValue);
            }
        } else if (isDoCoMo) {
            appendVCardEmailLine(builder, Contacts.ContactMethods.TYPE_HOME, "", "");
        }

        if (isDoCoMo) {
            appendVCardAddressLinesForDoCoMo(builder, contactData);
        } else {
            appendVCardAddressLinesForGeneric(builder, contactData);
        }

        if (!TextUtils.isEmpty(contactData.mOrg)) {
            appendVCardLine(builder, VCARD_PROPERTY_ORG, contactData.mOrg, true, true);
        }

        if (!TextUtils.isEmpty(contactData.mTitle)) {
            appendVCardLine(builder, VCARD_PROPERTY_TITLE, contactData.mTitle, true, true);
        }

        if (!TextUtils.isEmpty(contactData.mNote)) {
            appendVCardLine(builder, VCARD_PROPERTY_NOTE, contactData.mNote, true, true);
        }

        if ((contactData.mPhoto != null) && (contactData.mPhoto.length() > 0)) {
            // Note that contactData.mPhoto is already BASE64-encoded.
            appendVCardPhotoLine(builder, contactData.mPhoto, contactData.mPhotoType);
        }

        appendVCardExtension(builder, contactData, VCARD_PROPERTY_BDAY, isDoCoMo);

        // XXX: URL may have non-ascii chars. Should we add charset?
        appendVCardExtension(builder, contactData, VCARD_PROPERTY_URL, isDoCoMo);

        if (isDoCoMo) {
            if (contactData.mExtensions.containsKey(VCARD_PROPERTY_X_CLASS)) {
                appendVCardExtension(builder, contactData, VCARD_PROPERTY_X_CLASS, true);
            } else {
                appendVCardLine(builder, VCARD_PROPERTY_X_CLASS, VCARD_DATA_PUBLIC);
            }
            appendVCardExtension(builder, contactData, VCARD_PROPERTY_X_REDUCTION, true);
            appendVCardExtension(builder, contactData, VCARD_PROPERTY_X_NO, true);
            appendVCardExtension(builder, contactData, VCARD_PROPERTY_X_DCM_HMN_MODE, true);
        }

        appendVCardLine(builder, VCARD_PROPERTY_END, VCARD_DATA_VCARD);

        return builder.toString();
    }

    private void appendVCardAddressLinesForGeneric(StringBuilder builder, ContactData contactData) {
        for (AddressData addr : contactData.mAddr) {
            appendVCardAddressLine(builder, addr.mType, addr.mLabel, addr.mValue);
        }
    }

    private void appendVCardAddressLinesForDoCoMo(StringBuilder builder, ContactData contactData) {
        boolean isAddrSet = false;
        for (AddressData addr : contactData.mAddr) {
            if ((!isAddrSet) && (addr.mType == Contacts.ContactMethods.TYPE_HOME)) {
                appendVCardAddressLine(builder, addr.mType, addr.mLabel,
                        addr.mValue);
                isAddrSet = true;
                break;
            }
        }
        if (!isAddrSet) {
            for (AddressData addr : contactData.mAddr) {
                if ((!isAddrSet) && (addr.mType == Contacts.ContactMethods.TYPE_WORK)) {
                    appendVCardAddressLine(builder, addr.mType, addr.mLabel,
                            addr.mValue);
                    isAddrSet = true;
                    break;
                }
            }
        }
        if (!isAddrSet) {
            for (AddressData addr : contactData.mAddr) {
                if ((!isAddrSet)  && (addr.mType == Contacts.ContactMethods.TYPE_OTHER)) {
                    appendVCardAddressLine(builder, addr.mType, addr.mLabel,
                            addr.mValue);
                    isAddrSet = true;
                    break;
                }
            }
        }
        if (!isAddrSet) {
            for (AddressData addr : contactData.mAddr) {
                if ((!isAddrSet) && (addr.mType == Contacts.ContactMethods.TYPE_CUSTOM)) {
                    appendVCardAddressLine(builder, addr.mType, addr.mLabel,
                            addr.mValue);
                    isAddrSet = true;
                    break;
                }
            }
        }
        if (!isAddrSet) {
            appendVCardAddressLine(builder, Contacts.ContactMethods.TYPE_HOME, "", "");
        }
    }

    private void appendVCardPhotoLine(StringBuilder builder, String encodedData, String type) {
        StringBuilder tmpBuilder = new StringBuilder();
        tmpBuilder.append(VCARD_PROPERTY_PHOTO);
        tmpBuilder.append(VCARD_ATTR_SEPARATOR);
        tmpBuilder.append(VCARD_ATTR_ENCODING_BASE64_V21);
        tmpBuilder.append(VCARD_ATTR_SEPARATOR);
        tmpBuilder.append("TYPE=");
        tmpBuilder.append(type);
        tmpBuilder.append(VCARD_DATA_SEPARATOR);
        tmpBuilder.append(encodedData);

        String tmpStr = tmpBuilder.toString();
        tmpBuilder = new StringBuilder();
        int lineCount = 0;
        for (int i = 0; i < tmpStr.length(); i++) {
            tmpBuilder.append(tmpStr.charAt(i));
            lineCount++;
            if (lineCount > 72) {
                tmpBuilder.append(VCARD_COL_SEPARATOR);
                tmpBuilder.append(VCARD_WS);
                lineCount = 0;
            }
        }
        builder.append(tmpBuilder.toString());
        builder.append(VCARD_COL_SEPARATOR);
        builder.append(VCARD_COL_SEPARATOR);
    }

    private void appendVCardAddressLine(StringBuilder builder,
            int type, String label, String rawData) {
        builder.append(VCARD_PROPERTY_ADR);
        builder.append(VCARD_ATTR_SEPARATOR);

        boolean dataExists = !TextUtils.isEmpty(rawData);

        switch(type) {
        case Contacts.ContactMethods.TYPE_HOME:
            builder.append(VCARD_ATTR_HOME);
            if (dataExists) {
                builder.append(VCARD_ATTR_SEPARATOR);
            }
            break;
        case Contacts.ContactMethods.TYPE_WORK:
            builder.append(VCARD_ATTR_WORK);
            if (dataExists) {
                builder.append(VCARD_ATTR_SEPARATOR);
            }
            break;
        case Contacts.ContactMethods.TYPE_CUSTOM:
            // Ignore custom value since
            // - it may violate vCard spec
            // - it may contain non-ASCII characters
            // TODO: fix this.
            //
            // builder.append(label);
            // builder.append(VCARD_DATA_SEPARATOR);
            // break;
        case Contacts.ContactMethods.TYPE_OTHER:
        default:
            // Ignore other methods.
            // TODO: fix this.
            break;
        }

        if (dataExists) {
            builder.append(mVCardAttributeCharset);
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(VCARD_ATTR_ENCODING_QP);
        }
        builder.append(VCARD_DATA_SEPARATOR);
        if (dataExists) {
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(encodeQuotedPrintable(rawData));
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
            builder.append(VCARD_ITEM_SEPARATOR);
        }
        builder.append(VCARD_COL_SEPARATOR);
    }

    private void appendVCardEmailLine(StringBuilder builder, int type, String label, String data) {
        builder.append(VCARD_PROPERTY_EMAIL);
        builder.append(VCARD_ATTR_SEPARATOR);

        switch(type) {
        case Contacts.ContactMethods.TYPE_CUSTOM:
            if (label.equals(Contacts.ContactMethodsColumns.MOBILE_EMAIL_TYPE_NAME)) {
                builder.append(VCARD_ATTR_CELL);
            } else {
                // Ignore custom value.
                builder.append(VCARD_ATTR_INTERNET);
            }
            break;
        case Contacts.ContactMethods.TYPE_HOME:
            builder.append(VCARD_ATTR_HOME);
            break;
        case Contacts.ContactMethods.TYPE_WORK:
            builder.append(VCARD_ATTR_WORK);
            break;
        case Contacts.ContactMethods.TYPE_OTHER:
        default:
            builder.append(VCARD_ATTR_INTERNET);
            break;
        }

        builder.append(VCARD_DATA_SEPARATOR);
        builder.append(data);
        builder.append(VCARD_COL_SEPARATOR);
    }

    private void appendVCardTelephoneLine(StringBuilder builder,
            int type, String label, String data) {
        builder.append(VCARD_PROPERTY_TEL);
        builder.append(VCARD_ATTR_SEPARATOR);

        switch(type) {
        case Contacts.Phones.TYPE_CUSTOM:
            // Ignore custom label.
            builder.append(VCARD_ATTR_VOICE);
            break;
        case Contacts.Phones.TYPE_HOME:
            builder.append(VCARD_ATTR_HOME);
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(VCARD_ATTR_VOICE);
            break;
        case Contacts.Phones.TYPE_MOBILE:
            builder.append(VCARD_ATTR_CELL);
            break;
        case Contacts.Phones.TYPE_WORK:
            builder.append(VCARD_ATTR_WORK);
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(VCARD_ATTR_VOICE);
            break;
        case Contacts.Phones.TYPE_FAX_WORK:
            builder.append(VCARD_ATTR_WORK);
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(VCARD_ATTR_FAX);
            break;
        case Contacts.Phones.TYPE_FAX_HOME:
            builder.append(VCARD_ATTR_HOME);
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(VCARD_ATTR_FAX);
            break;
        case Contacts.Phones.TYPE_PAGER:
            builder.append(VCARD_ATTR_VOICE);
            break;
        case Contacts.Phones.TYPE_OTHER:
            builder.append(VCARD_ATTR_VOICE);
            break;
        default:
            builder.append(VCARD_ATTR_VOICE);
            break;
        }

        builder.append(VCARD_DATA_SEPARATOR);
        builder.append(data);
        builder.append(VCARD_COL_SEPARATOR);
    }

    private void appendVCardExtension(StringBuilder builder, ContactData contactData,
            String propertyName, boolean mustEmitSomething) {
        if (contactData.mExtensions.containsKey(propertyName)) {
            PropertyNode propertyNode =
                PropertyNode.decode(contactData.mExtensions.get(propertyName));
            appendVCardLine(builder, propertyName, propertyNode.propValue);
        } else if (mustEmitSomething) {
            appendVCardLine(builder, propertyName, "");
        }
    }

    private void appendVCardLine(StringBuilder builder, String propertyName, String data) {
        appendVCardLine(builder, propertyName, data, false, false);
    }

    private void appendVCardLine(StringBuilder builder, String field, String data,
            boolean needCharset, boolean needQuotedPrintable) {
        builder.append(field);
        if (needCharset) {
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(mVCardAttributeCharset);
        }

        if (needQuotedPrintable) {
            builder.append(VCARD_ATTR_SEPARATOR);
            builder.append(VCARD_ATTR_ENCODING_QP);
            data = encodeQuotedPrintable(data);
        }

        builder.append(VCARD_DATA_SEPARATOR);
        builder.append(data);
        builder.append(VCARD_COL_SEPARATOR);
    }

    // TODO: Replace with the method in Base64 class.
    private static char PAD = '=';
    private static final char[] ENCODE64 = {
        'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P',
        'Q','R','S','T','U','V','W','X','Y','Z','a','b','c','d','e','f',
        'g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v',
        'w','x','y','z','0','1','2','3','4','5','6','7','8','9','+','/'
    };

    private String encodeBase64(byte[] data) {
        if (data == null) {
            return "";
        }

        char[] charBuffer = new char[(data.length + 2) / 3 * 4];
        int position = 0;
        int _3byte = 0;
        for (int i=0; i<data.length-2; i+=3) {
            _3byte = ((data[i] & 0xFF) << 16) + ((data[i+1] & 0xFF) << 8) + (data[i+2] & 0xFF);
            charBuffer[position++] = ENCODE64[_3byte >> 18];
            charBuffer[position++] = ENCODE64[(_3byte >> 12) & 0x3F];
            charBuffer[position++] = ENCODE64[(_3byte >>  6) & 0x3F];
            charBuffer[position++] = ENCODE64[_3byte & 0x3F];
        }
        switch(data.length % 3) {
        case 1: // [111111][11 0000][0000 00][000000]
            _3byte = ((data[data.length-1] & 0xFF) << 16);
            charBuffer[position++] = ENCODE64[_3byte >> 18];
            charBuffer[position++] = ENCODE64[(_3byte >> 12) & 0x3F];
            charBuffer[position++] = PAD;
            charBuffer[position++] = PAD;
            break;
        case 2: // [111111][11 1111][1111 00][000000]
            _3byte = ((data[data.length-2] & 0xFF) << 16) + ((data[data.length-1] & 0xFF) << 8);
            charBuffer[position++] = ENCODE64[_3byte >> 18];
            charBuffer[position++] = ENCODE64[(_3byte >> 12) & 0x3F];
            charBuffer[position++] = ENCODE64[(_3byte >>  6) & 0x3F];
            charBuffer[position++] = PAD;
            break;
        }

        return new String(charBuffer);
    }

    private String encodeQuotedPrintable(String str) {
        {
            // Replace "\n" and "\r" with "\r\n".
            StringBuilder tmpBuilder = new StringBuilder();
            int length = str.length();
            for (int i = 0; i < length; i++) {
                char ch = str.charAt(i);
                if (ch == '\r') {
                    if (i + 1 < length && str.charAt(i + 1) == '\n') {
                        i++;
                    }
                    tmpBuilder.append("\r\n");
                } else if (ch == '\n') {
                    tmpBuilder.append("\r\n");
                } else {
                    tmpBuilder.append(str.charAt(i));
                }
            }
            str = tmpBuilder.toString();
        }

        StringBuilder builder = new StringBuilder();
        int index = 0;
        int lineCount = 0;
        byte[] strArray = null;

        try {
            strArray = str.getBytes(mCharsetString);
        } catch (UnsupportedEncodingException e) {
            Log.e(LOG_TAG, "Charset " + mCharsetString + " cannot be used. " +
                    "Try default charset");
            strArray = str.getBytes();
        }
        while (index < strArray.length) {
            builder.append(String.format("=%02X", strArray[index]));
            index += 1;
            lineCount += 3;

            if (lineCount >= 67) {
                // Specification requires CRLF must be inserted before the length of the line
                // becomes more than 76.
                // Assuming that the next character is a multi-byte character, it will become
                // 6 bytes.
                // 76 - 6 - 3 = 67
                builder.append("=\r\n");
                lineCount = 0;
            }
        }

        return builder.toString();
    }

    // TODO: replace this with ContactStruct
    public class ContactData {
        private String mName = "";
        private String mPhoneticName = "";
        private ArrayList<TelData> mTel = new ArrayList<TelData>();
        private ArrayList<EmailData> mEmail = new ArrayList<EmailData>();
        private ArrayList<AddressData> mAddr = new ArrayList<AddressData>();
        private String mOrg = "";
        private String mTitle = "";
        private String mNote = "";
        private String mPhoto = "";
        private String mPhotoType = "JPG"; // Default
        private Map<String, String> mExtensions = new HashMap<String, String>();

        public boolean isEmptyName() {
            return TextUtils.isEmpty(mName);
        }
    }

    private class AddressData {
        private int mType;
        private String mLabel;
        private String mValue;

        public AddressData(int type, String label, String value) {
            mType = type;
            mLabel = label;
            mValue = value;
        }
    }

    private class EmailData {
        private int mType;
        private String mLabel;
        private String mValue;

        public EmailData(int type, String label, String value) {
            mType = type;
            mLabel = label;
            mValue = value;
        }
    }

    private class TelData {
        private int mType;
        private String mLabel;
        private String mValue;

        public TelData(int type, String label, String value) {
            mType = type;
            mLabel = label;
            mValue = value;
        }
    }
}

/**
 * TextUtils especially for Japanese.
 * TODO: make this in android.text in the future
 */
class JapaneseUtils {
    static private final Map<Character, String> sHalfWidthMap =
        new HashMap<Character, String>();

    static {
        // There's no logical mapping rule in Unicode. Sigh.
        sHalfWidthMap.put('\u3001', "\uFF64");
        sHalfWidthMap.put('\u3002', "\uFF61");
        sHalfWidthMap.put('\u300C', "\uFF62");
        sHalfWidthMap.put('\u300D', "\uFF63");
        sHalfWidthMap.put('\u301C', "~");
        sHalfWidthMap.put('\u3041', "\uFF67");
        sHalfWidthMap.put('\u3042', "\uFF71");
        sHalfWidthMap.put('\u3043', "\uFF68");
        sHalfWidthMap.put('\u3044', "\uFF72");
        sHalfWidthMap.put('\u3045', "\uFF69");
        sHalfWidthMap.put('\u3046', "\uFF73");
        sHalfWidthMap.put('\u3047', "\uFF6A");
        sHalfWidthMap.put('\u3048', "\uFF74");
        sHalfWidthMap.put('\u3049', "\uFF6B");
        sHalfWidthMap.put('\u304A', "\uFF75");
        sHalfWidthMap.put('\u304B', "\uFF76");
        sHalfWidthMap.put('\u304C', "\uFF76\uFF9E");
        sHalfWidthMap.put('\u304D', "\uFF77");
        sHalfWidthMap.put('\u304E', "\uFF77\uFF9E");
        sHalfWidthMap.put('\u304F', "\uFF78");
        sHalfWidthMap.put('\u3050', "\uFF78\uFF9E");
        sHalfWidthMap.put('\u3051', "\uFF79");
        sHalfWidthMap.put('\u3052', "\uFF79\uFF9E");
        sHalfWidthMap.put('\u3053', "\uFF7A");
        sHalfWidthMap.put('\u3054', "\uFF7A\uFF9E");
        sHalfWidthMap.put('\u3055', "\uFF7B");
        sHalfWidthMap.put('\u3056', "\uFF7B\uFF9E");
        sHalfWidthMap.put('\u3057', "\uFF7C");
        sHalfWidthMap.put('\u3058', "\uFF7C\uFF9E");
        sHalfWidthMap.put('\u3059', "\uFF7D");
        sHalfWidthMap.put('\u305A', "\uFF7D\uFF9E");
        sHalfWidthMap.put('\u305B', "\uFF7E");
        sHalfWidthMap.put('\u305C', "\uFF7E\uFF9E");
        sHalfWidthMap.put('\u305D', "\uFF7F");
        sHalfWidthMap.put('\u305E', "\uFF7F\uFF9E");
        sHalfWidthMap.put('\u305F', "\uFF80");
        sHalfWidthMap.put('\u3060', "\uFF80\uFF9E");
        sHalfWidthMap.put('\u3061', "\uFF81");
        sHalfWidthMap.put('\u3062', "\uFF81\uFF9E");
        sHalfWidthMap.put('\u3063', "\uFF6F");
        sHalfWidthMap.put('\u3064', "\uFF82");
        sHalfWidthMap.put('\u3065', "\uFF82\uFF9E");
        sHalfWidthMap.put('\u3066', "\uFF83");
        sHalfWidthMap.put('\u3067', "\uFF83\uFF9E");
        sHalfWidthMap.put('\u3068', "\uFF84");
        sHalfWidthMap.put('\u3069', "\uFF84\uFF9E");
        sHalfWidthMap.put('\u306A', "\uFF85");
        sHalfWidthMap.put('\u306B', "\uFF86");
        sHalfWidthMap.put('\u306C', "\uFF87");
        sHalfWidthMap.put('\u306D', "\uFF88");
        sHalfWidthMap.put('\u306E', "\uFF89");
        sHalfWidthMap.put('\u306F', "\uFF8A");
        sHalfWidthMap.put('\u3070', "\uFF8A\uFF9E");
        sHalfWidthMap.put('\u3071', "\uFF8A\uFF9F");
        sHalfWidthMap.put('\u3072', "\uFF8B");
        sHalfWidthMap.put('\u3073', "\uFF8B\uFF9E");
        sHalfWidthMap.put('\u3074', "\uFF8B\uFF9F");
        sHalfWidthMap.put('\u3075', "\uFF8C");
        sHalfWidthMap.put('\u3076', "\uFF8C\uFF9E");
        sHalfWidthMap.put('\u3077', "\uFF8C\uFF9F");
        sHalfWidthMap.put('\u3078', "\uFF8D");
        sHalfWidthMap.put('\u3079', "\uFF8D\uFF9E");
        sHalfWidthMap.put('\u307A', "\uFF8D\uFF9F");
        sHalfWidthMap.put('\u307B', "\uFF8E");
        sHalfWidthMap.put('\u307C', "\uFF8E\uFF9E");
        sHalfWidthMap.put('\u307D', "\uFF8E\uFF9F");
        sHalfWidthMap.put('\u307E', "\uFF8F");
        sHalfWidthMap.put('\u307F', "\uFF90");
        sHalfWidthMap.put('\u3080', "\uFF91");
        sHalfWidthMap.put('\u3081', "\uFF92");
        sHalfWidthMap.put('\u3082', "\uFF93");
        sHalfWidthMap.put('\u3083', "\uFF6C");
        sHalfWidthMap.put('\u3084', "\uFF94");
        sHalfWidthMap.put('\u3085', "\uFF6D");
        sHalfWidthMap.put('\u3086', "\uFF95");
        sHalfWidthMap.put('\u3087', "\uFF6E");
        sHalfWidthMap.put('\u3088', "\uFF96");
        sHalfWidthMap.put('\u3089', "\uFF97");
        sHalfWidthMap.put('\u308A', "\uFF98");
        sHalfWidthMap.put('\u308B', "\uFF99");
        sHalfWidthMap.put('\u308C', "\uFF9A");
        sHalfWidthMap.put('\u308D', "\uFF9B");
        sHalfWidthMap.put('\u308E', "\uFF9C");
        sHalfWidthMap.put('\u308F', "\uFF9C");
        sHalfWidthMap.put('\u3090', "\uFF72");
        sHalfWidthMap.put('\u3091', "\uFF74");
        sHalfWidthMap.put('\u3092', "\uFF66");
        sHalfWidthMap.put('\u3093', "\uFF9D");
        sHalfWidthMap.put('\u309B', "\uFF9E");
        sHalfWidthMap.put('\u309C', "\uFF9F");
        sHalfWidthMap.put('\u30A1', "\uFF67");
        sHalfWidthMap.put('\u30A2', "\uFF71");
        sHalfWidthMap.put('\u30A3', "\uFF68");
        sHalfWidthMap.put('\u30A4', "\uFF72");
        sHalfWidthMap.put('\u30A5', "\uFF69");
        sHalfWidthMap.put('\u30A6', "\uFF73");
        sHalfWidthMap.put('\u30A7', "\uFF6A");
        sHalfWidthMap.put('\u30A8', "\uFF74");
        sHalfWidthMap.put('\u30A9', "\uFF6B");
        sHalfWidthMap.put('\u30AA', "\uFF75");
        sHalfWidthMap.put('\u30AB', "\uFF76");
        sHalfWidthMap.put('\u30AC', "\uFF76\uFF9E");
        sHalfWidthMap.put('\u30AD', "\uFF77");
        sHalfWidthMap.put('\u30AE', "\uFF77\uFF9E");
        sHalfWidthMap.put('\u30AF', "\uFF78");
        sHalfWidthMap.put('\u30B0', "\uFF78\uFF9E");
        sHalfWidthMap.put('\u30B1', "\uFF79");
        sHalfWidthMap.put('\u30B2', "\uFF79\uFF9E");
        sHalfWidthMap.put('\u30B3', "\uFF7A");
        sHalfWidthMap.put('\u30B4', "\uFF7A\uFF9E");
        sHalfWidthMap.put('\u30B5', "\uFF7B");
        sHalfWidthMap.put('\u30B6', "\uFF7B\uFF9E");
        sHalfWidthMap.put('\u30B7', "\uFF7C");
        sHalfWidthMap.put('\u30B8', "\uFF7C\uFF9E");
        sHalfWidthMap.put('\u30B9', "\uFF7D");
        sHalfWidthMap.put('\u30BA', "\uFF7D\uFF9E");
        sHalfWidthMap.put('\u30BB', "\uFF7E");
        sHalfWidthMap.put('\u30BC', "\uFF7E\uFF9E");
        sHalfWidthMap.put('\u30BD', "\uFF7F");
        sHalfWidthMap.put('\u30BE', "\uFF7F\uFF9E");
        sHalfWidthMap.put('\u30BF', "\uFF80");
        sHalfWidthMap.put('\u30C0', "\uFF80\uFF9E");
        sHalfWidthMap.put('\u30C1', "\uFF81");
        sHalfWidthMap.put('\u30C2', "\uFF81\uFF9E");
        sHalfWidthMap.put('\u30C3', "\uFF6F");
        sHalfWidthMap.put('\u30C4', "\uFF82");
        sHalfWidthMap.put('\u30C5', "\uFF82\uFF9E");
        sHalfWidthMap.put('\u30C6', "\uFF83");
        sHalfWidthMap.put('\u30C7', "\uFF83\uFF9E");
        sHalfWidthMap.put('\u30C8', "\uFF84");
        sHalfWidthMap.put('\u30C9', "\uFF84\uFF9E");
        sHalfWidthMap.put('\u30CA', "\uFF85");
        sHalfWidthMap.put('\u30CB', "\uFF86");
        sHalfWidthMap.put('\u30CC', "\uFF87");
        sHalfWidthMap.put('\u30CD', "\uFF88");
        sHalfWidthMap.put('\u30CE', "\uFF89");
        sHalfWidthMap.put('\u30CF', "\uFF8A");
        sHalfWidthMap.put('\u30D0', "\uFF8A\uFF9E");
        sHalfWidthMap.put('\u30D1', "\uFF8A\uFF9F");
        sHalfWidthMap.put('\u30D2', "\uFF8B");
        sHalfWidthMap.put('\u30D3', "\uFF8B\uFF9E");
        sHalfWidthMap.put('\u30D4', "\uFF8B\uFF9F");
        sHalfWidthMap.put('\u30D5', "\uFF8C");
        sHalfWidthMap.put('\u30D6', "\uFF8C\uFF9E");
        sHalfWidthMap.put('\u30D7', "\uFF8C\uFF9F");
        sHalfWidthMap.put('\u30D8', "\uFF8D");
        sHalfWidthMap.put('\u30D9', "\uFF8D\uFF9E");
        sHalfWidthMap.put('\u30DA', "\uFF8D\uFF9F");
        sHalfWidthMap.put('\u30DB', "\uFF8E");
        sHalfWidthMap.put('\u30DC', "\uFF8E\uFF9E");
        sHalfWidthMap.put('\u30DD', "\uFF8E\uFF9F");
        sHalfWidthMap.put('\u30DE', "\uFF8F");
        sHalfWidthMap.put('\u30DF', "\uFF90");
        sHalfWidthMap.put('\u30E0', "\uFF91");
        sHalfWidthMap.put('\u30E1', "\uFF92");
        sHalfWidthMap.put('\u30E2', "\uFF93");
        sHalfWidthMap.put('\u30E3', "\uFF6C");
        sHalfWidthMap.put('\u30E4', "\uFF94");
        sHalfWidthMap.put('\u30E5', "\uFF6D");
        sHalfWidthMap.put('\u30E6', "\uFF95");
        sHalfWidthMap.put('\u30E7', "\uFF6E");
        sHalfWidthMap.put('\u30E8', "\uFF96");
        sHalfWidthMap.put('\u30E9', "\uFF97");
        sHalfWidthMap.put('\u30EA', "\uFF98");
        sHalfWidthMap.put('\u30EB', "\uFF99");
        sHalfWidthMap.put('\u30EC', "\uFF9A");
        sHalfWidthMap.put('\u30ED', "\uFF9B");
        sHalfWidthMap.put('\u30EE', "\uFF9C");
        sHalfWidthMap.put('\u30EF', "\uFF9C");
        sHalfWidthMap.put('\u30F0', "\uFF72");
        sHalfWidthMap.put('\u30F1', "\uFF74");
        sHalfWidthMap.put('\u30F2', "\uFF66");
        sHalfWidthMap.put('\u30F3', "\uFF9D");
        sHalfWidthMap.put('\u30F4', "\uFF73\uFF9E");
        sHalfWidthMap.put('\u30F5', "\uFF76");
        sHalfWidthMap.put('\u30F6', "\uFF79");
        sHalfWidthMap.put('\u30FB', "\uFF65");
        sHalfWidthMap.put('\u30FC', "\uFF70");
        sHalfWidthMap.put('\uFF01', "!");
        sHalfWidthMap.put('\uFF02', "\"");
        sHalfWidthMap.put('\uFF03', "#");
        sHalfWidthMap.put('\uFF04', "$");
        sHalfWidthMap.put('\uFF05', "%");
        sHalfWidthMap.put('\uFF06', "&");
        sHalfWidthMap.put('\uFF07', "'");
        sHalfWidthMap.put('\uFF08', "(");
        sHalfWidthMap.put('\uFF09', ")");
        sHalfWidthMap.put('\uFF0A', "*");
        sHalfWidthMap.put('\uFF0B', "+");
        sHalfWidthMap.put('\uFF0C', ",");
        sHalfWidthMap.put('\uFF0D', "-");
        sHalfWidthMap.put('\uFF0E', ".");
        sHalfWidthMap.put('\uFF0F', "/");
        sHalfWidthMap.put('\uFF10', "0");
        sHalfWidthMap.put('\uFF11', "1");
        sHalfWidthMap.put('\uFF12', "2");
        sHalfWidthMap.put('\uFF13', "3");
        sHalfWidthMap.put('\uFF14', "4");
        sHalfWidthMap.put('\uFF15', "5");
        sHalfWidthMap.put('\uFF16', "6");
        sHalfWidthMap.put('\uFF17', "7");
        sHalfWidthMap.put('\uFF18', "8");
        sHalfWidthMap.put('\uFF19', "9");
        sHalfWidthMap.put('\uFF1A', ":");
        sHalfWidthMap.put('\uFF1B', ";");
        sHalfWidthMap.put('\uFF1C', "<");
        sHalfWidthMap.put('\uFF1D', "=");
        sHalfWidthMap.put('\uFF1E', ">");
        sHalfWidthMap.put('\uFF1F', "?");
        sHalfWidthMap.put('\uFF20', "@");
        sHalfWidthMap.put('\uFF21', "A");
        sHalfWidthMap.put('\uFF22', "B");
        sHalfWidthMap.put('\uFF23', "C");
        sHalfWidthMap.put('\uFF24', "D");
        sHalfWidthMap.put('\uFF25', "E");
        sHalfWidthMap.put('\uFF26', "F");
        sHalfWidthMap.put('\uFF27', "G");
        sHalfWidthMap.put('\uFF28', "H");
        sHalfWidthMap.put('\uFF29', "I");
        sHalfWidthMap.put('\uFF2A', "J");
        sHalfWidthMap.put('\uFF2B', "K");
        sHalfWidthMap.put('\uFF2C', "L");
        sHalfWidthMap.put('\uFF2D', "M");
        sHalfWidthMap.put('\uFF2E', "N");
        sHalfWidthMap.put('\uFF2F', "O");
        sHalfWidthMap.put('\uFF30', "P");
        sHalfWidthMap.put('\uFF31', "Q");
        sHalfWidthMap.put('\uFF32', "R");
        sHalfWidthMap.put('\uFF33', "S");
        sHalfWidthMap.put('\uFF34', "T");
        sHalfWidthMap.put('\uFF35', "U");
        sHalfWidthMap.put('\uFF36', "V");
        sHalfWidthMap.put('\uFF37', "W");
        sHalfWidthMap.put('\uFF38', "X");
        sHalfWidthMap.put('\uFF39', "Y");
        sHalfWidthMap.put('\uFF3A', "Z");
        sHalfWidthMap.put('\uFF3B', "[");
        sHalfWidthMap.put('\uFF3C', "\\");
        sHalfWidthMap.put('\uFF3D', "]");
        sHalfWidthMap.put('\uFF3E', "^");
        sHalfWidthMap.put('\uFF3F', "_");
        sHalfWidthMap.put('\uFF41', "a");
        sHalfWidthMap.put('\uFF42', "b");
        sHalfWidthMap.put('\uFF43', "c");
        sHalfWidthMap.put('\uFF44', "d");
        sHalfWidthMap.put('\uFF45', "e");
        sHalfWidthMap.put('\uFF46', "f");
        sHalfWidthMap.put('\uFF47', "g");
        sHalfWidthMap.put('\uFF48', "h");
        sHalfWidthMap.put('\uFF49', "i");
        sHalfWidthMap.put('\uFF4A', "j");
        sHalfWidthMap.put('\uFF4B', "k");
        sHalfWidthMap.put('\uFF4C', "l");
        sHalfWidthMap.put('\uFF4D', "m");
        sHalfWidthMap.put('\uFF4E', "n");
        sHalfWidthMap.put('\uFF4F', "o");
        sHalfWidthMap.put('\uFF50', "p");
        sHalfWidthMap.put('\uFF51', "q");
        sHalfWidthMap.put('\uFF52', "r");
        sHalfWidthMap.put('\uFF53', "s");
        sHalfWidthMap.put('\uFF54', "t");
        sHalfWidthMap.put('\uFF55', "u");
        sHalfWidthMap.put('\uFF56', "v");
        sHalfWidthMap.put('\uFF57', "w");
        sHalfWidthMap.put('\uFF58', "x");
        sHalfWidthMap.put('\uFF59', "y");
        sHalfWidthMap.put('\uFF5A', "z");
        sHalfWidthMap.put('\uFF5B', "{");
        sHalfWidthMap.put('\uFF5C', "|");
        sHalfWidthMap.put('\uFF5D', "}");
        sHalfWidthMap.put('\uFF5E', "~");
        sHalfWidthMap.put('\uFF61', "\uFF61");
        sHalfWidthMap.put('\uFF62', "\uFF62");
        sHalfWidthMap.put('\uFF63', "\uFF63");
        sHalfWidthMap.put('\uFF64', "\uFF64");
        sHalfWidthMap.put('\uFF65', "\uFF65");
        sHalfWidthMap.put('\uFF66', "\uFF66");
        sHalfWidthMap.put('\uFF67', "\uFF67");
        sHalfWidthMap.put('\uFF68', "\uFF68");
        sHalfWidthMap.put('\uFF69', "\uFF69");
        sHalfWidthMap.put('\uFF6A', "\uFF6A");
        sHalfWidthMap.put('\uFF6B', "\uFF6B");
        sHalfWidthMap.put('\uFF6C', "\uFF6C");
        sHalfWidthMap.put('\uFF6D', "\uFF6D");
        sHalfWidthMap.put('\uFF6E', "\uFF6E");
        sHalfWidthMap.put('\uFF6F', "\uFF6F");
        sHalfWidthMap.put('\uFF70', "\uFF70");
        sHalfWidthMap.put('\uFF71', "\uFF71");
        sHalfWidthMap.put('\uFF72', "\uFF72");
        sHalfWidthMap.put('\uFF73', "\uFF73");
        sHalfWidthMap.put('\uFF74', "\uFF74");
        sHalfWidthMap.put('\uFF75', "\uFF75");
        sHalfWidthMap.put('\uFF76', "\uFF76");
        sHalfWidthMap.put('\uFF77', "\uFF77");
        sHalfWidthMap.put('\uFF78', "\uFF78");
        sHalfWidthMap.put('\uFF79', "\uFF79");
        sHalfWidthMap.put('\uFF7A', "\uFF7A");
        sHalfWidthMap.put('\uFF7B', "\uFF7B");
        sHalfWidthMap.put('\uFF7C', "\uFF7C");
        sHalfWidthMap.put('\uFF7D', "\uFF7D");
        sHalfWidthMap.put('\uFF7E', "\uFF7E");
        sHalfWidthMap.put('\uFF7F', "\uFF7F");
        sHalfWidthMap.put('\uFF80', "\uFF80");
        sHalfWidthMap.put('\uFF81', "\uFF81");
        sHalfWidthMap.put('\uFF82', "\uFF82");
        sHalfWidthMap.put('\uFF83', "\uFF83");
        sHalfWidthMap.put('\uFF84', "\uFF84");
        sHalfWidthMap.put('\uFF85', "\uFF85");
        sHalfWidthMap.put('\uFF86', "\uFF86");
        sHalfWidthMap.put('\uFF87', "\uFF87");
        sHalfWidthMap.put('\uFF88', "\uFF88");
        sHalfWidthMap.put('\uFF89', "\uFF89");
        sHalfWidthMap.put('\uFF8A', "\uFF8A");
        sHalfWidthMap.put('\uFF8B', "\uFF8B");
        sHalfWidthMap.put('\uFF8C', "\uFF8C");
        sHalfWidthMap.put('\uFF8D', "\uFF8D");
        sHalfWidthMap.put('\uFF8E', "\uFF8E");
        sHalfWidthMap.put('\uFF8F', "\uFF8F");
        sHalfWidthMap.put('\uFF90', "\uFF90");
        sHalfWidthMap.put('\uFF91', "\uFF91");
        sHalfWidthMap.put('\uFF92', "\uFF92");
        sHalfWidthMap.put('\uFF93', "\uFF93");
        sHalfWidthMap.put('\uFF94', "\uFF94");
        sHalfWidthMap.put('\uFF95', "\uFF95");
        sHalfWidthMap.put('\uFF96', "\uFF96");
        sHalfWidthMap.put('\uFF97', "\uFF97");
        sHalfWidthMap.put('\uFF98', "\uFF98");
        sHalfWidthMap.put('\uFF99', "\uFF99");
        sHalfWidthMap.put('\uFF9A', "\uFF9A");
        sHalfWidthMap.put('\uFF9B', "\uFF9B");
        sHalfWidthMap.put('\uFF9C', "\uFF9C");
        sHalfWidthMap.put('\uFF9D', "\uFF9D");
        sHalfWidthMap.put('\uFF9E', "\uFF9E");
        sHalfWidthMap.put('\uFF9F', "\uFF9F");
        sHalfWidthMap.put('\uFFE5', "\u005C\u005C");
    }

    /**
     * Return half-width version of that character if possible. Return null if not possible
     * @param ch input character
     * @return CharSequence object if the mapping for ch exists. Return null otherwise.
     */
    public static CharSequence tryGetHalfWidthText(char ch) {
        if (sHalfWidthMap.containsKey(ch)) {
            return sHalfWidthMap.get(ch);
        } else {
            return null;
        }
    }
}
