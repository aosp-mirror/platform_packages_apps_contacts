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

import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.pim.vcard.VCardConfig;
import android.pim.vcard.VCardEntry;
import android.pim.vcard.VCardEntryCommitter;
import android.pim.vcard.VCardEntryConstructor;
import android.pim.vcard.VCardEntryCounter;
import android.pim.vcard.VCardEntryHandler;
import android.pim.vcard.VCardInterpreter;
import android.pim.vcard.VCardInterpreterCollection;
import android.pim.vcard.VCardParser;
import android.pim.vcard.VCardParser_V21;
import android.pim.vcard.VCardParser_V30;
import android.pim.vcard.VCardSourceDetector;
import android.pim.vcard.exception.VCardException;
import android.pim.vcard.exception.VCardNestedException;
import android.pim.vcard.exception.VCardNotSupportedException;
import android.pim.vcard.exception.VCardVersionException;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * The class responsible for importing vCard from one ore multiple Uris.
 */
public class ImportVCardService extends Service {
    private final static String LOG_TAG = "ImportVCardService";

    private class ProgressNotifier implements VCardEntryHandler {
        private final int mId;

        public ProgressNotifier(int id) {
            mId = id;
        }

        public void onStart() {
        }

        public void onEntryCreated(VCardEntry contactStruct) {
            mCurrentCount++;  // 1 origin.
            if (contactStruct.isIgnorable()) {
                return;
            }

            final Context context = ImportVCardService.this;
            // We don't use startEntry() since:
            // - We cannot know name there but here.
            // - There's high probability where name comes soon after the beginning of entry, so
            //   we don't need to hurry to show something.
            final String packageName = "com.android.contacts";
            final RemoteViews remoteViews = new RemoteViews(packageName,
                    R.layout.status_bar_ongoing_event_progress_bar);
            final String title = getString(R.string.reading_vcard_title);
            final String text = getString(R.string.progress_notifier_message,
                    String.valueOf(mCurrentCount),
                    String.valueOf(mTotalCount),
                    contactStruct.getDisplayName());

            // TODO: uploading image does not work correctly. (looks like a static image).
            remoteViews.setTextViewText(R.id.description, text);
            remoteViews.setProgressBar(R.id.progress_bar, mTotalCount, mCurrentCount,
                    mTotalCount == -1);
            final String percentage =
                    getString(R.string.percentage,
                            String.valueOf(mCurrentCount * 100/mTotalCount));
            remoteViews.setTextViewText(R.id.progress_text, percentage);
            remoteViews.setImageViewResource(R.id.appIcon, android.R.drawable.stat_sys_download);

            final Notification notification = new Notification();
            notification.icon = android.R.drawable.stat_sys_download;
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            notification.contentView = remoteViews;

            notification.contentIntent =
                    PendingIntent.getActivity(context, 0,
                            new Intent(context, ContactsListActivity.class), 0);
            mNotificationManager.notify(mId, notification);
        }

        public void onEnd() {
        }
    }

    private class VCardReadThread extends Thread {
        private final Context mContext;
        private final ContentResolver mResolver;
        private VCardParser mVCardParser;
        private boolean mCanceled;
        private final List<Uri> mErrorUris;
        private final List<Uri> mCreatedUris;

        public VCardReadThread() {
            mContext = ImportVCardService.this;
            mResolver = mContext.getContentResolver();
            mErrorUris = new ArrayList<Uri>();
            mCreatedUris = new ArrayList<Uri>();
        }

        @Override
        public void run() {
            while (!mCanceled) {
                mErrorUris.clear();
                mCreatedUris.clear();

                final Account account;
                final Uri[] uris;
                final int id;
                final boolean needReview;
                synchronized (mContext) {
                    if (mPendingInputs.size() == 0) {
                        mNowRunning = false;
                        break;
                    } else {
                        final PendingInput pendingInput = mPendingInputs.poll();
                        account = pendingInput.account;
                        uris = pendingInput.uris;
                        id = pendingInput.id;
                    }
                }
                runInternal(account, uris, id);
                doFinishNotification(id, uris);
            }
            Log.i(LOG_TAG, "Successfully imported. Total: " + mTotalCount);
            stopSelf();
        }

        private void runInternal(Account account, Uri[] uris, int id) {
            int totalCount = 0;
            final ArrayList<VCardSourceDetector> detectorList =
                new ArrayList<VCardSourceDetector>();
            // First scan all Uris with a default charset and try to understand an exact
            // charset to be used to each Uri. Note that detector would return null when
            // it does not know an appropriate charset, so stick to use the default
            // at that time.
            // TODO: notification for first scanning?
            for (Uri uri : uris) {
                if (mCanceled) {
                    return;
                }
                final VCardEntryCounter counter = new VCardEntryCounter();
                final VCardSourceDetector detector = new VCardSourceDetector();
                final VCardInterpreterCollection interpreterCollection =
                        new VCardInterpreterCollection(Arrays.asList(counter, detector));
                if (!readOneVCard(uri, VCardConfig.VCARD_TYPE_UNKNOWN, null,
                        interpreterCollection)) {
                    mErrorUris.add(uri);
                }

                totalCount += counter.getCount();
                detectorList.add(detector);
            }

            if (mErrorUris.size() > 0) {
                final StringBuilder builder = new StringBuilder();
                builder.append("Error happened on ");
                for (Uri errorUri : mErrorUris) {
                    builder.append("\"");
                    builder.append(errorUri.toString());
                    builder.append("\"");
                }
                Log.e(LOG_TAG, builder.toString());
                doErrorNotification(id);
                return;
            }

            if (uris.length != detectorList.size()) {
                Log.e(LOG_TAG,
                        "The number of Uris to be imported is different from that of " +
                        "charset to be used.");
                doErrorNotification(id);
                return;
            }

            // First scanning is over. Try to import each vCard, which causes side effects.
            mTotalCount = totalCount;
            mCurrentCount = 0;

            for (int i = 0; i < uris.length; i++) {
                if (mCanceled) {
                    Log.w(LOG_TAG, "Canceled during importing (with storing data in database)");
                    // TODO: implement cancel correctly.
                    return;
                }
                final Uri uri = uris[i];

                final VCardSourceDetector detector = detectorList.get(i);
                final int vcardType =  detector.getEstimatedType();  
                final String charset = detector.getEstimatedCharset();

                final VCardEntryConstructor constructor =
                        new VCardEntryConstructor(charset, charset, false, vcardType, account);
                final VCardEntryCommitter committer = new VCardEntryCommitter(mResolver);
                constructor.addEntryHandler(committer);
                constructor.addEntryHandler(new ProgressNotifier(id));

                if (!readOneVCard(uri, vcardType, charset, constructor)) {
                        Log.e(LOG_TAG, "Failed to read \"" + uri.toString() + "\" " +
                                "while first scan was successful.");
                }
                final List<Uri> createdUris = committer.getCreatedUris();
                if (createdUris != null && createdUris.size() > 0) {
                    mCreatedUris.addAll(createdUris);
                } else {
                    Log.w(LOG_TAG, "Created Uris is null (src = " + uri.toString() + "\"");
                }
            }
        }

        private boolean readOneVCard(Uri uri, int vcardType, String charset,
                VCardInterpreter interpreter) {
            InputStream is;
            try {
                // TODO: use vcardType given from detector and stop trying to read the file twice.
                is = mResolver.openInputStream(uri);

                // We need synchronized since we need to handle mCanceled and mVCardParser
                // at once. In the worst case, a user may call cancel() just before recreating
                // mVCardParser.
                synchronized (this) {
                    mVCardParser = new VCardParser_V21(vcardType);
                    if (mCanceled) {
                        mVCardParser.cancel();
                    }
                }

                try {
                    mVCardParser.parse(is, charset, interpreter);
                } catch (VCardVersionException e1) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                    if (interpreter instanceof VCardEntryConstructor) {
                        // Let the object clean up internal temporal objects,
                        ((VCardEntryConstructor) interpreter).clear();
                    }
                    is = mResolver.openInputStream(uri);

                    synchronized (this) {
                        mVCardParser = new VCardParser_V30();
                        if (mCanceled) {
                            mVCardParser.cancel();
                        }
                    }

                    try {
                        mVCardParser.parse(is, charset, interpreter);
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
            } catch (IOException e) {
                Log.e(LOG_TAG, "IOException was emitted: " + e.getMessage());
                return false;
            } catch (VCardNestedException e) {
                // In the first scan, we may (correctly) encounter this exception.
                // We assume that we were able to detect the type of vCard before
                // the exception being thrown.
                //
                // In the second scan, we may (inappropriately) encounter it.
                // We silently ignore it, since
                // - It is really unusual situation.
                // - We cannot handle it by definition.
                // - Users cannot either.
                // - We should not accept unnecessarily complicated vCard, possibly by wrong manner.
                Log.w(LOG_TAG, "Nested Exception is found (it may be false-positive).");
            } catch (VCardNotSupportedException e) {
                return false;
            } catch (VCardException e) {
                return false;
            }
            return true;
        }

        private void doErrorNotification(int id) {
            final Notification notification = new Notification();
            notification.icon = android.R.drawable.stat_sys_download_done;
            final String title = mContext.getString(R.string.reading_vcard_failed_title);
            final PendingIntent intent =
                    PendingIntent.getActivity(mContext, 0, new Intent(), 0);
            notification.setLatestEventInfo(mContext, title, "", intent);
            mNotificationManager.notify(id, notification);
        }

        private void doFinishNotification(int id, Uri[] uris) {
            final Notification notification = new Notification();
            notification.icon = android.R.drawable.stat_sys_download_done;
            final String title = mContext.getString(R.string.reading_vcard_finished_title);

            final Intent intent;
            final long rawContactId = ContentUris.parseId(mCreatedUris.get(0));
            final Uri contactUri = RawContacts.getContactLookupUri(
                    getContentResolver(), ContentUris.withAppendedId(
                            RawContacts.CONTENT_URI, rawContactId));
            intent = new Intent(Intent.ACTION_VIEW, contactUri);

            final String text = ((uris.length == 1) ? uris[0].getPath() : "");
            final PendingIntent pendingIntent =
                    PendingIntent.getActivity(mContext, 0, intent, 0);
            notification.setLatestEventInfo(mContext, title, text, pendingIntent);
            mNotificationManager.notify(id, notification);
        }

        // We need synchronized since we need to handle mCanceled and mVCardParser at once.
        public synchronized void cancel() {
            mCanceled = true;
            if (mVCardParser != null) {
                mVCardParser.cancel();
            }
        }

        public void onCancel(DialogInterface dialog) {
            cancel();
        }
    }

    private static class PendingInput {
        public final Account account;
        public final Uri[] uris;
        public final int id;

        public PendingInput(Account account, Uri[] uris, int id) {
            this.account = account;
            this.uris = uris;
            this.id = id;
        }
    }

    // The two classes bellow must be called inside the synchronized block, using this context.
    private boolean mNowRunning;
    private final Queue<PendingInput> mPendingInputs = new LinkedList<PendingInput>();

    private NotificationManager mNotificationManager;
    private Thread mThread;
    private int mTotalCount;
    private int mCurrentCount;

    private Uri[] tryGetUris(Intent intent) {
        final String[] uriStrings =
                intent.getStringArrayExtra(ImportVCardActivity.VCARD_URI_ARRAY);
        if (uriStrings == null || uriStrings.length == 0) {
            Log.e(LOG_TAG, "Given uri array is empty");
            return null;
        }

        final int length = uriStrings.length;
        final Uri[] uris = new Uri[length];
        for (int i = 0; i < length; i++) {
            uris[i] = Uri.parse(uriStrings[i]);
        }

        return uris;
    }

    private Account tryGetAccount(Intent intent) {
        if (intent == null) {
            Log.w(LOG_TAG, "Intent is null");
            return null;
        }

        final String accountName = intent.getStringExtra("account_name");
        final String accountType = intent.getStringExtra("account_type");
        if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
            return new Account(accountName, accountType);
        } else {
            Log.w(LOG_TAG, "Account is not set.");
            return null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mNotificationManager == null) {
            mNotificationManager =
                    (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        }

        final Account account = tryGetAccount(intent);
        final Uri[] uris = tryGetUris(intent);
        if (uris == null) {
            Log.e(LOG_TAG, "Uris are null.");
            Toast.makeText(this, getString(R.string.reading_vcard_failed_title),
                    Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        synchronized (this) {
            mPendingInputs.add(new PendingInput(account, uris, startId));
            if (!mNowRunning) {
                Toast.makeText(this, getString(R.string.vcard_importer_start_message),
                        Toast.LENGTH_LONG).show();
                // Assume thread is alredy broken.
                // Even when it still exists, it never scan the PendingInput newly added above.
                mNowRunning = true;
                mThread = new VCardReadThread();
                mThread.start();
            } else {
                Toast.makeText(this, getString(R.string.vcard_importer_will_start_message),
                        Toast.LENGTH_LONG).show();
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
