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

import com.android.contacts.R;
import com.android.vcard.VCardEntryCommitter;
import com.android.vcard.VCardEntryConstructor;
import com.android.vcard.VCardInterpreter;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.exception.VCardException;
import com.android.vcard.exception.VCardNestedException;
import com.android.vcard.exception.VCardNotSupportedException;
import com.android.vcard.exception.VCardVersionException;

import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for processing one import request from a user. Dropped after importing requested Uri(s).
 * {@link VCardService} will create another object when there is another import request.
 */
public class ImportProcessor extends ProcessorBase {
    private static final String LOG_TAG = "VCardImport";

    private final VCardService mService;
    private final ContentResolver mResolver;
    private final NotificationManager mNotificationManager;
    private final ImportRequest mImportRequest;
    private final int mJobId;

    private final ImportProgressNotifier mNotifier = new ImportProgressNotifier();

    // TODO: remove and show appropriate message instead.
    private final List<Uri> mFailedUris = new ArrayList<Uri>();

    private VCardParser mVCardParser;

    private volatile boolean mCancelled;
    private volatile boolean mDone;

    public ImportProcessor(final VCardService service, final ImportRequest request,
            int jobId) {
        mService = service;
        mResolver = mService.getContentResolver();
        mNotificationManager = (NotificationManager)
                mService.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifier.init(mService, mNotificationManager);
        mImportRequest = request;
        mJobId = jobId;
    }

    @Override
    public final int getType() {
        return PROCESSOR_TYPE_IMPORT;
    }

    @Override
    public void run() {
        // ExecutorService ignores RuntimeException, so we need to show it here.
        try {
            runInternal();
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "RuntimeException thrown during import", e);
            throw e;
        } finally {
            synchronized (this) {
                mDone = true;
            }
        }
    }

    private void runInternal() {
        Log.i(LOG_TAG, String.format("vCard import (id: %d) has started.", mJobId));
        final ImportRequest request = mImportRequest;
        if (mCancelled) {
            Log.i(LOG_TAG, "Canceled before actually handling parameter (" + request.uri + ")");
            return;
        }
        final int[] possibleVCardVersions;
        if (request.vcardVersion == ImportVCardActivity.VCARD_VERSION_AUTO_DETECT) {
            /**
             * Note: this code assumes that a given Uri is able to be opened more than once,
             * which may not be true in certain conditions.
             */
            possibleVCardVersions = new int[] {
                    ImportVCardActivity.VCARD_VERSION_V21,
                    ImportVCardActivity.VCARD_VERSION_V30
            };
        } else {
            possibleVCardVersions = new int[] {
                    request.vcardVersion
            };
        }

        final Uri uri = request.uri;
        final Account account = request.account;
        final int estimatedVCardType = request.estimatedVCardType;
        final String estimatedCharset = request.estimatedCharset;
        final int entryCount = request.entryCount;
        mNotifier.addTotalCount(entryCount);

        final VCardEntryConstructor constructor =
                new VCardEntryConstructor(estimatedVCardType, account, estimatedCharset);
        final VCardEntryCommitter committer = new VCardEntryCommitter(mResolver);
        constructor.addEntryHandler(committer);
        constructor.addEntryHandler(mNotifier);

        final boolean successful =
            readOneVCard(uri, estimatedVCardType, estimatedCharset,
                    constructor, possibleVCardVersions);

        mService.handleFinishImportNotification(mJobId, successful);

        if (successful) {
            // TODO: successful becomes true even when cancelled. Should return more appropriate
            // value
            if (isCancelled()) {
                Log.i(LOG_TAG, "vCard import has been canceled (uri: " + uri + ")");
            } else {
                Log.i(LOG_TAG, "Successfully finished importing one vCard file: " + uri);
            }
            List<Uri> uris = committer.getCreatedUris();
            if (uris != null && uris.size() > 0) {
                doFinishNotification(uris.get(0));
            } else {
                // Not critical, but suspicious.
                Log.w(LOG_TAG,
                        "Created Uris is null or 0 length " +
                        "though the creation itself is successful.");
                doFinishNotification(null);
            }
        } else {
            Log.w(LOG_TAG, "Failed to read one vCard file: " + uri);
            mFailedUris.add(uri);
        }
    }

    /*
    private void doErrorNotification(int id) {
        final Notification notification = new Notification();
        notification.icon = android.R.drawable.stat_sys_download_done;
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        final String title = mService.getString(R.string.reading_vcard_failed_title);
        final PendingIntent intent =
                PendingIntent.getActivity(mService, 0, new Intent(), 0);
        notification.setLatestEventInfo(mService, title, "", intent);
        mNotificationManager.notify(MESSAGE_ID, notification);
    }
    */

    private void doFinishNotification(final Uri createdUri) {
        final Notification notification = new Notification();
        final String title;
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        if (isCancelled()) {
            notification.icon = android.R.drawable.stat_notify_error;
            title = mService.getString(R.string.importing_vcard_canceled_title);
        } else {
            notification.icon = android.R.drawable.stat_sys_download_done;
            title = mService.getString(R.string.importing_vcard_finished_title);
        }

        final Intent intent;
        if (createdUri != null) {
            final long rawContactId = ContentUris.parseId(createdUri);
            final Uri contactUri = RawContacts.getContactLookupUri(
                    mResolver, ContentUris.withAppendedId(
                            RawContacts.CONTENT_URI, rawContactId));
            intent = new Intent(Intent.ACTION_VIEW, contactUri);
        } else {
            intent = null;
        }

        notification.setLatestEventInfo(mService, title, "",
                PendingIntent.getActivity(mService, 0, intent, 0));
        mNotificationManager.notify(VCardService.IMPORT_NOTIFICATION_ID, notification);
    }

    private boolean readOneVCard(Uri uri, int vcardType, String charset,
            final VCardInterpreter interpreter,
            final int[] possibleVCardVersions) {
        Log.i(LOG_TAG, "start importing one vCard (Uri: " + uri + ")");
        boolean successful = false;
        final int length = possibleVCardVersions.length;
        for (int i = 0; i < length; i++) {
            InputStream is = null;
            final int vcardVersion = possibleVCardVersions[i];
            try {
                if (i > 0 && (interpreter instanceof VCardEntryConstructor)) {
                    // Let the object clean up internal temporary objects,
                    ((VCardEntryConstructor) interpreter).clear();
                }

                is = mResolver.openInputStream(uri);

                // We need synchronized block here,
                // since we need to handle mCanceled and mVCardParser at once.
                // In the worst case, a user may call cancel() just before creating
                // mVCardParser.
                synchronized (this) {
                    mVCardParser = (vcardVersion == ImportVCardActivity.VCARD_VERSION_V30 ?
                            new VCardParser_V30(vcardType) :
                                new VCardParser_V21(vcardType));
                    if (mCancelled) {
                        Log.i(LOG_TAG, "ImportProcessor already recieves cancel request, so " +
                                "send cancel request to vCard parser too.");
                        mVCardParser.cancel();
                    }
                }
                mVCardParser.parse(is, interpreter);

                successful = true;
                break;
            } catch (IOException e) {
                Log.e(LOG_TAG, "IOException was emitted: " + e.getMessage());
            } catch (VCardNestedException e) {
                // This exception should not be thrown here. We should instead handle it
                // in the preprocessing session in ImportVCardActivity, as we don't try
                // to detect the type of given vCard here.
                //
                // TODO: Handle this case appropriately, which should mean we have to have
                // code trying to auto-detect the type of given vCard twice (both in
                // ImportVCardActivity and ImportVCardService).
                Log.e(LOG_TAG, "Nested Exception is found.");
            } catch (VCardNotSupportedException e) {
                Log.e(LOG_TAG, e.getMessage());
            } catch (VCardVersionException e) {
                if (i == length - 1) {
                    Log.e(LOG_TAG, "Appropriate version for this vCard is not found.");
                } else {
                    // We'll try the other (v30) version.
                }
            } catch (VCardException e) {
                Log.e(LOG_TAG, e.getMessage());
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        return successful;
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        Log.i(LOG_TAG, "ImportProcessor received cancel request");
        if (mDone || mCancelled) {
            return false;
        }
        mCancelled = true;
        synchronized (this) {
            if (mVCardParser != null) {
                mVCardParser.cancel();
            }
        }
        return true;
    }

    @Override
    public synchronized boolean isCancelled() {
        return mCancelled;
    }


    @Override
    public synchronized boolean isDone() {
        return mDone;
    }
}
