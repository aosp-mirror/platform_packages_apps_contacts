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
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryCommitter;
import com.android.vcard.VCardEntryConstructor;
import com.android.vcard.VCardEntryHandler;
import com.android.vcard.VCardInterpreter;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.VCardSourceDetector;
import com.android.vcard.exception.VCardException;
import com.android.vcard.exception.VCardNestedException;
import com.android.vcard.exception.VCardNotSupportedException;
import com.android.vcard.exception.VCardVersionException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * The class responsible for importing vCard from one ore multiple Uris.
 */
public class ImportVCardService extends Service {
    private final static String LOG_TAG = "ImportVCardService";

    /* package */ static final int IMPORT_REQUEST = 1;

    private static final int MESSAGE_ID = 1000;

    // TODO: Too many static classes. Create separate files for them.

    /**
     * Class representing one request for reading vCard (as a Uri representation).
     */
    /* package */ static class RequestParameter {
        public final Account account;
        /**
         * Note: One Uri does not mean there's only one vCard entry since one Uri
         * often has multiple vCard entries. 
         */
        public final Uri uri;
        /**
         * Can be {@link VCardSourceDetector#PARSE_TYPE_UNKNOWN}.
         */
        public final int estimatedVCardType;
        /**
         * Can be null, meaning no preferable charset is available.
         */
        public final String estimatedCharset;
        /**
         * Assumes that one Uri contains only one version, while there's a (tiny) possibility
         * we may have two types in one vCard.
         * 
         * e.g.
         * BEGIN:VCARD
         * VERSION:2.1
         * ...
         * END:VCARD
         * BEGIN:VCARD
         * VERSION:3.0
         * ...
         * END:VCARD
         *
         * We've never seen this kind of a file, but we may have to cope with it in the future.
         */
        public final int vcardVersion;
        public final int entryCount;

        public RequestParameter(Account account,
                Uri uri, int estimatedType, String estimatedCharset,
                int vcardVersion, int entryCount) {
            this.account = account;
            this.uri = uri;
            this.estimatedVCardType = estimatedType;
            this.estimatedCharset = estimatedCharset;
            this.vcardVersion = vcardVersion;
            this.entryCount = entryCount;
        }
    }

    public class ImportRequestHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case IMPORT_REQUEST:
                    RequestParameter parameter = (RequestParameter)msg.obj;
                    Toast.makeText(ImportVCardService.this,
                            getString(R.string.vcard_importer_start_message),
                            Toast.LENGTH_LONG).show();
                    final boolean needThreadStart;
                    if (mRequestHandler == null || !mRequestHandler.isRunning()) {
                        mRequestHandler = new RequestHandler();
                        mRequestHandler.init(ImportVCardService.this);
                        needThreadStart = true;
                    } else {
                        needThreadStart = false;
                    }
                    mRequestHandler.addRequest(parameter);
                    if (needThreadStart) {
                        mThread = new Thread(new Runnable() {
                            public void run() {
                                mRequestHandler.handleRequests();
                            }
                        });
                        mThread.start();
                    }
                    break;
                default:
                    Log.e(LOG_TAG, "Unknown request type: " + msg.what);
                    super.hasMessages(msg.what);
            }
        }
    }

    // TODO(dmiyakawa): better design for testing?
    /* package */ interface CommitterGenerator {
        public VCardEntryCommitter generate(ContentResolver resolver);
    }
    /* package */ static class DefaultCommitterGenerator implements CommitterGenerator {
        public VCardEntryCommitter generate(ContentResolver resolver) {
            return new VCardEntryCommitter(resolver);
        }
    }

    /**
     * For testability, we don't inherit Thread here.
     */
    private static class RequestHandler {
        private ImportVCardService mService;
        private ContentResolver mResolver;
        private NotificationManager mNotificationManager;
        private ProgressNotifier mProgressNotifier;

        private final List<Uri> mFailedUris;
        private final List<Uri> mCreatedUris;

        private VCardParser mVCardParser;

        /* package */ CommitterGenerator mCommitterGenerator = new DefaultCommitterGenerator();

        /**
         * Meaning a controller of this object requests the operation should be canceled
         * or not, which implies {@link #mRunning} should be set to false soon, but
         * it does not meaning cancel request is able to immediately stop this object,
         * so we have two variables.
         */
        private boolean mCanceled;
        /**
         * Meaning this object is actually running.
         */
        private boolean mRunning = true;
        private final Queue<RequestParameter> mPendingRequests =
                new LinkedList<RequestParameter>();

        public RequestHandler() {
            // We cannot set Service here since Service is not fully ready at this point.
            // TODO: refactor this class.

            mFailedUris = new ArrayList<Uri>();
            mCreatedUris = new ArrayList<Uri>();
            mProgressNotifier = new ProgressNotifier();            
        }

        public void init(ImportVCardService service) {
            // TODO: Based on fragile fact. fix this.
            mService = service;
            mResolver = service.getContentResolver();
            mNotificationManager =
                (NotificationManager)service.getSystemService(Context.NOTIFICATION_SERVICE);
            mProgressNotifier.init(mService, mNotificationManager);
        }

        public void handleRequests() {
            try {
                while (!mCanceled) {
                    final RequestParameter parameter;
                    synchronized (this) {
                        if (mPendingRequests.size() == 0) {
                            mRunning = false;
                            break;
                        } else {
                            parameter = mPendingRequests.poll();
                        }
                    }  // synchronized (this)
                    handleOneRequest(parameter);
                }

                // Currenty we don't have an appropriate way to let users see all entries
                // imported in this procedure. Instead, we show them entries only when
                // there's just one created uri.
                doFinishNotification(mCreatedUris.size() == 1 ? mCreatedUris.get(0) : null);
            } finally {
                // TODO: verify this works fine.
                mRunning = false;  // Just in case.
                // mService.stopSelf();
            }
        }

        /**
         * Would be run inside syncronized block.
         */
        public boolean handleOneRequest(final RequestParameter parameter) {
            if (mCanceled) {
                Log.i(LOG_TAG, "Canceled before actually handling parameter ("
                        + parameter.uri + ")");
                return false;
            }
            final int[] possibleVCardVersions;
            if (parameter.vcardVersion == ImportVCardActivity.VCARD_VERSION_AUTO_DETECT) {
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
                        parameter.vcardVersion
                };
            }

            final Uri uri = parameter.uri;
            final Account account = parameter.account;
            final int estimatedVCardType = parameter.estimatedVCardType;
            final String estimatedCharset = parameter.estimatedCharset;

            final VCardEntryConstructor constructor =
                new VCardEntryConstructor(estimatedVCardType, account, estimatedCharset);
            final VCardEntryCommitter committer = mCommitterGenerator.generate(mResolver);
            constructor.addEntryHandler(committer);
            constructor.addEntryHandler(mProgressNotifier);

            final boolean successful =
                readOneVCard(uri, estimatedVCardType, estimatedCharset,
                        constructor, possibleVCardVersions);
            if (successful) {
                List<Uri> uris = committer.getCreatedUris();
                if (uris != null) {
                    mCreatedUris.addAll(uris);
                } else {
                    // Not critical, but suspicious.
                    Log.w(LOG_TAG,
                            "Created Uris is null while the creation itself is successful.");
                }
            } else {
                mFailedUris.add(uri);
            }

            return successful;
        }

        /*
        private void doErrorNotification(int id) {
            final Notification notification = new Notification();
            notification.icon = android.R.drawable.stat_sys_download_done;
            final String title = mService.getString(R.string.reading_vcard_failed_title);
            final PendingIntent intent =
                    PendingIntent.getActivity(mService, 0, new Intent(), 0);
            notification.setLatestEventInfo(mService, title, "", intent);
            mNotificationManager.notify(MESSAGE_ID, notification);
        }
        */

        private void doFinishNotification(Uri createdUri) {
            final Notification notification = new Notification();
            notification.icon = android.R.drawable.stat_sys_download_done;
            final String title = mService.getString(R.string.reading_vcard_finished_title);

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

            final PendingIntent pendingIntent =
                    PendingIntent.getActivity(mService, 0, intent, 0);
            notification.setLatestEventInfo(mService, title, "", pendingIntent);
            mNotificationManager.notify(MESSAGE_ID, notification);
        }
        
        private boolean readOneVCard(Uri uri, int vcardType, String charset,
                VCardInterpreter interpreter,
                final int[] possibleVCardVersions) {
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
                        if (mCanceled) {
                            mVCardParser.cancel();
                        }
                    }
                    mVCardParser.parse(is, interpreter);

                    successful = true;
                    break;
                } catch (IOException e) {
                    Log.e(LOG_TAG, "IOException was emitted: " + e.getMessage());
                } catch (VCardNestedException e) {
                    // This exception should not be thrown here. We should intsead handle it
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

        public boolean isRunning() {
            return mRunning;
        }

        public boolean isCanceled() {
            return mCanceled;
        }

        public void cancel() {
            mCanceled = true;
            synchronized (this) {
                if (mVCardParser != null) {
                    mVCardParser.cancel();
                }
            }
        }

        public synchronized void addRequest(RequestParameter parameter) {
            if (mRunning) {
                mProgressNotifier.addTotalCount(parameter.entryCount);
                mPendingRequests.add(parameter);
            } else {
                Log.w(LOG_TAG, "Request came while the service is not running any more.");
            }
        }

        public List<Uri> getCreatedUris() {
            return mCreatedUris;
        }

        public List<Uri> getFailedUris() {
            return mFailedUris;
        }
    }

    private static class ProgressNotifier implements VCardEntryHandler {
        private Context mContext;
        private NotificationManager mNotificationManager;

        private int mCurrentCount;
        private int mTotalCount;

        public void init(Context context, NotificationManager notificationManager) {
            mContext = context;
            mNotificationManager = notificationManager;
        }

        public void onStart() {
        }

        public void onEntryCreated(VCardEntry contactStruct) {
            mCurrentCount++;  // 1 origin.
            if (contactStruct.isIgnorable()) {
                return;
            }

            // We don't use startEntry() since:
            // - We cannot know name there but here.
            // - There's high probability where name comes soon after the beginning of entry, so
            //   we don't need to hurry to show something.
            final String packageName = "com.android.contacts";
            final RemoteViews remoteViews = new RemoteViews(packageName,
                    R.layout.status_bar_ongoing_event_progress_bar);
            final String title = mContext.getString(R.string.reading_vcard_title);
            String totalCountString;
            synchronized (this) {
                totalCountString = String.valueOf(mTotalCount); 
            }
            final String text = mContext.getString(R.string.progress_notifier_message,
                    String.valueOf(mCurrentCount),
                    totalCountString,
                    contactStruct.getDisplayName());

            // TODO: uploading image does not work correctly. (looks like a static image).
            remoteViews.setTextViewText(R.id.description, text);
            remoteViews.setProgressBar(R.id.progress_bar, mTotalCount, mCurrentCount,
                    mTotalCount == -1);
            final String percentage =
                    mContext.getString(R.string.percentage,
                            String.valueOf(mCurrentCount * 100/mTotalCount));
            remoteViews.setTextViewText(R.id.progress_text, percentage);
            remoteViews.setImageViewResource(R.id.appIcon, android.R.drawable.stat_sys_download);

            final Notification notification = new Notification();
            notification.icon = android.R.drawable.stat_sys_download;
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            notification.contentView = remoteViews;

            notification.contentIntent =
                    PendingIntent.getActivity(mContext, 0,
                            new Intent(mContext, ContactsListActivity.class), 0);
            mNotificationManager.notify(MESSAGE_ID, notification);
        }

        public synchronized void addTotalCount(int additionalCount) {
            mTotalCount += additionalCount;
        }

        public void onEnd() {
        }
    }

    private RequestHandler mRequestHandler;
    private Messenger mMessenger = new Messenger(new ImportRequestHandler());
    private Thread mThread = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }
}
