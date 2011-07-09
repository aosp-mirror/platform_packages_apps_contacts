/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.calllog;

import com.android.common.widget.GroupingListAdapter;
import com.android.contacts.CallDetailActivity;
import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactsUtils;
import com.android.contacts.PhoneCallDetails;
import com.android.contacts.PhoneCallDetailsHelper;
import com.android.contacts.R;
import com.android.contacts.activities.DialtactsActivity.ViewPagerVisibilityListener;
import com.android.contacts.util.ExpirableCache;
import com.android.internal.telephony.CallerInfo;
import com.google.common.annotations.VisibleForTesting;

import android.app.ListFragment;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.QuickContactBadge;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

/**
 * Displays a list of call log entries.
 */
public class CallLogFragment extends ListFragment
        implements View.OnCreateContextMenuListener, ViewPagerVisibilityListener {
    private static final String TAG = "CallLogFragment";

    /**
     * The size of the cache of contact info.
     */
    private static final int CONTACT_INFO_CACHE_SIZE = 100;

    /** The query for the call log table */
    private static final class CallLogQuery {
        public static final String[] _PROJECTION = new String[] {
                Calls._ID,
                Calls.NUMBER,
                Calls.DATE,
                Calls.DURATION,
                Calls.TYPE,
                Calls.CACHED_NAME,
                Calls.CACHED_NUMBER_TYPE,
                Calls.CACHED_NUMBER_LABEL,
                Calls.COUNTRY_ISO};

        public static final int ID = 0;
        public static final int NUMBER = 1;
        public static final int DATE = 2;
        public static final int DURATION = 3;
        public static final int CALL_TYPE = 4;
        public static final int CALLER_NAME = 5;
        public static final int CALLER_NUMBERTYPE = 6;
        public static final int CALLER_NUMBERLABEL = 7;
        public static final int COUNTRY_ISO = 8;
    }

    /** The query to use for the phones table */
    private static final class PhoneQuery {
        public static final String[] _PROJECTION = new String[] {
                PhoneLookup._ID,
                PhoneLookup.DISPLAY_NAME,
                PhoneLookup.TYPE,
                PhoneLookup.LABEL,
                PhoneLookup.NUMBER,
                PhoneLookup.NORMALIZED_NUMBER,
                PhoneLookup.PHOTO_ID,
                PhoneLookup.LOOKUP_KEY};

        public static final int PERSON_ID = 0;
        public static final int NAME = 1;
        public static final int PHONE_TYPE = 2;
        public static final int LABEL = 3;
        public static final int MATCHED_NUMBER = 4;
        public static final int NORMALIZED_NUMBER = 5;
        public static final int PHOTO_ID = 6;
        public static final int LOOKUP_KEY = 7;
    }

    private static final class MenuItems {
        public static final int DELETE = 1;
    }

    private static final class OptionsMenuItems {
        public static final int DELETE_ALL = 1;
    }

    private static final int QUERY_TOKEN = 53;
    private static final int UPDATE_TOKEN = 54;

    private CallLogAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private String mVoiceMailNumber;
    private String mCurrentCountryIso;
    private boolean mScrollToTop;

    private boolean mShowMenu;

    public static final class ContactInfo {
        public long personId;
        public String name;
        public int type;
        public String label;
        public String number;
        public String formattedNumber;
        public String normalizedNumber;
        public long photoId;
        public String lookupKey;

        public static ContactInfo EMPTY = new ContactInfo();
    }

    public static final class CallerInfoQuery {
        public String number;
        public int position;
        public String name;
        public int numberType;
        public String numberLabel;
        public long photoId;
        public String lookupKey;
    }

    /** Adapter class to fill in data for the Call Log */
    public final class CallLogAdapter extends GroupingListAdapter
            implements Runnable, ViewTreeObserver.OnPreDrawListener, View.OnClickListener {
        /** The time in millis to delay starting the thread processing requests. */
        private static final int START_PROCESSING_REQUESTS_DELAY_MILLIS = 1000;

        /**
         * A cache of the contact details for the phone numbers in the call log.
         * <p>
         * The content of the cache is expired (but not purged) whenever the application comes to
         * the foreground.
         */
        private ExpirableCache<String, ContactInfo> mContactInfoCache;

        /**
         * List of requests to update contact details.
         * <p>
         * The requests are added when displaying the contacts and are processed by a background
         * thread.
         */
        private final LinkedList<CallerInfoQuery> mRequests;

        private volatile boolean mDone;
        private boolean mLoading = true;
        private ViewTreeObserver.OnPreDrawListener mPreDrawListener;
        private static final int REDRAW = 1;
        private static final int START_THREAD = 2;
        private boolean mFirst;
        private Thread mCallerIdThread;

        /** Instance of helper class for managing views. */
        private final CallLogListItemHelper mCallLogViewsHelper;

        /**
         * Reusable char array buffers.
         */
        private CharArrayBuffer mBuffer1 = new CharArrayBuffer(128);
        private CharArrayBuffer mBuffer2 = new CharArrayBuffer(128);
        /** Helper to set up contact photos. */
        private final ContactPhotoManager mContactPhotoManager;

        /** Can be set to true by tests to disable processing of requests. */
        private volatile boolean mRequestProcessingDisabled = false;

        @Override
        public void onClick(View view) {
            String number = (String) view.getTag();
            if (!TextUtils.isEmpty(number)) {
                // Here, "number" can either be a PSTN phone number or a
                // SIP address.  So turn it into either a tel: URI or a
                // sip: URI, as appropriate.
                Uri callUri;
                if (PhoneNumberUtils.isUriNumber(number)) {
                    callUri = Uri.fromParts("sip", number, null);
                } else {
                    callUri = Uri.fromParts("tel", number, null);
                }
                startActivity(new Intent(Intent.ACTION_CALL_PRIVILEGED, callUri));
            }
        }

        @Override
        public boolean onPreDraw() {
            if (mFirst) {
                mHandler.sendEmptyMessageDelayed(START_THREAD,
                        START_PROCESSING_REQUESTS_DELAY_MILLIS);
                mFirst = false;
            }
            return true;
        }

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REDRAW:
                        notifyDataSetChanged();
                        break;
                    case START_THREAD:
                        startRequestProcessing();
                        break;
                }
            }
        };

        public CallLogAdapter() {
            super(getActivity());

            mContactInfoCache = ExpirableCache.create(CONTACT_INFO_CACHE_SIZE);
            mRequests = new LinkedList<CallerInfoQuery>();
            mPreDrawListener = null;

            Drawable incomingDrawable = getResources().getDrawable(
                    R.drawable.ic_call_log_list_incoming_call);
            Drawable outgoingDrawable = getResources().getDrawable(
                    R.drawable.ic_call_log_list_outgoing_call);
            Drawable missedDrawable = getResources().getDrawable(
                    R.drawable.ic_call_log_list_missed_call);
            Drawable voicemailDrawable = getResources().getDrawable(
                    R.drawable.ic_call_log_list_voicemail);
            Drawable callDrawable = getResources().getDrawable(
                    R.drawable.ic_call_log_list_action_call);
            Drawable playDrawable = getResources().getDrawable(
                    R.drawable.ic_call_log_list_action_play);

            mContactPhotoManager = ContactPhotoManager.getInstance(getActivity());
            PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                    getActivity(), getResources(), mVoiceMailNumber, incomingDrawable,
                    outgoingDrawable, missedDrawable, voicemailDrawable);
            mCallLogViewsHelper = new CallLogListItemHelper(phoneCallDetailsHelper, callDrawable,
                    playDrawable);
        }

        /**
         * Requery on background thread when {@link Cursor} changes.
         */
        @Override
        protected void onContentChanged() {
            // Start async requery
            startQuery();
        }

        void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        public ContactInfo getContactInfo(String number) {
            return mContactInfoCache.getPossiblyExpired(number);
        }

        public void startRequestProcessing() {
            if (mRequestProcessingDisabled) {
                return;
            }

            mDone = false;
            mCallerIdThread = new Thread(this);
            mCallerIdThread.setPriority(Thread.MIN_PRIORITY);
            mCallerIdThread.start();
        }

        /**
         * Stops the background thread that processes updates and cancels any pending requests to
         * start it.
         * <p>
         * Should be called from the main thread to prevent a race condition between the request to
         * start the thread being processed and stopping the thread.
         */
        public void stopRequestProcessing() {
            // Remove any pending requests to start the processing thread.
            mHandler.removeMessages(START_THREAD);
            mDone = true;
            if (mCallerIdThread != null) mCallerIdThread.interrupt();
        }

        public void invalidateCache() {
            mContactInfoCache.expireAll();
        }

        private void updateCallLog(CallerInfoQuery ciq, ContactInfo ci) {
            // Check if they are different. If not, don't update.
            if (TextUtils.equals(ciq.name, ci.name)
                    && TextUtils.equals(ciq.numberLabel, ci.label)
                    && ciq.numberType == ci.type
                    && ciq.photoId == ci.photoId
                    && ciq.lookupKey == ci.lookupKey) {
                return;
            }
            ContentValues values = new ContentValues(3);
            values.put(Calls.CACHED_NAME, ci.name);
            values.put(Calls.CACHED_NUMBER_TYPE, ci.type);
            values.put(Calls.CACHED_NUMBER_LABEL, ci.label);

            try {
                getActivity().getContentResolver().update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                        Calls.NUMBER + "='" + ciq.number + "'", null);
            } catch (SQLiteDiskIOException e) {
                Log.w(TAG, "Exception while updating call info", e);
            } catch (SQLiteFullException e) {
                Log.w(TAG, "Exception while updating call info", e);
            } catch (SQLiteDatabaseCorruptException e) {
                Log.w(TAG, "Exception while updating call info", e);
            }
        }

        private void enqueueRequest(String number, boolean immediate, int position,
                String name, int numberType, String numberLabel, long photoId, String lookupKey) {
            CallerInfoQuery ciq = new CallerInfoQuery();
            ciq.number = number;
            ciq.position = position;
            ciq.name = name;
            ciq.numberType = numberType;
            ciq.numberLabel = numberLabel;
            ciq.photoId = photoId;
            ciq.lookupKey = lookupKey;
            synchronized (mRequests) {
                mRequests.add(ciq);
                mRequests.notifyAll();
            }
            if (mFirst && immediate) {
                startRequestProcessing();
                mFirst = false;
            }
        }

        private boolean queryContactInfo(CallerInfoQuery ciq) {
            // First check if there was a prior request for the same number
            // that was already satisfied
            ContactInfo info = mContactInfoCache.get(ciq.number);
            boolean needNotify = false;
            if (info != null && info != ContactInfo.EMPTY) {
                return true;
            } else {
                // Ok, do a fresh Contacts lookup for ciq.number.
                boolean infoUpdated = false;

                if (PhoneNumberUtils.isUriNumber(ciq.number)) {
                    // This "number" is really a SIP address.

                    // TODO: This code is duplicated from the
                    // CallerInfoAsyncQuery class.  To avoid that, could the
                    // code here just use CallerInfoAsyncQuery, rather than
                    // manually running ContentResolver.query() itself?

                    // We look up SIP addresses directly in the Data table:
                    Uri contactRef = Data.CONTENT_URI;

                    // Note Data.DATA1 and SipAddress.SIP_ADDRESS are equivalent.
                    //
                    // Also note we use "upper(data1)" in the WHERE clause, and
                    // uppercase the incoming SIP address, in order to do a
                    // case-insensitive match.
                    //
                    // TODO: May also need to normalize by adding "sip:" as a
                    // prefix, if we start storing SIP addresses that way in the
                    // database.
                    String selection = "upper(" + Data.DATA1 + ")=?"
                            + " AND "
                            + Data.MIMETYPE + "='" + SipAddress.CONTENT_ITEM_TYPE + "'";
                    String[] selectionArgs = new String[] { ciq.number.toUpperCase() };

                    Cursor dataTableCursor =
                            getActivity().getContentResolver().query(
                                    contactRef,
                                    null,  // projection
                                    selection,  // selection
                                    selectionArgs,  // selectionArgs
                                    null);  // sortOrder

                    if (dataTableCursor != null) {
                        if (dataTableCursor.moveToFirst()) {
                            info = new ContactInfo();

                            // TODO: we could slightly speed this up using an
                            // explicit projection (and thus not have to do
                            // those getColumnIndex() calls) but the benefit is
                            // very minimal.

                            // Note the Data.CONTACT_ID column here is
                            // equivalent to the PERSON_ID_COLUMN_INDEX column
                            // we use with "phonesCursor" below.
                            info.personId = dataTableCursor.getLong(
                                    dataTableCursor.getColumnIndex(Data.CONTACT_ID));
                            info.name = dataTableCursor.getString(
                                    dataTableCursor.getColumnIndex(Data.DISPLAY_NAME));
                            // "type" and "label" are currently unused for SIP addresses
                            info.type = SipAddress.TYPE_OTHER;
                            info.label = null;

                            // And "number" is the SIP address.
                            // Note Data.DATA1 and SipAddress.SIP_ADDRESS are equivalent.
                            info.number = dataTableCursor.getString(
                                    dataTableCursor.getColumnIndex(Data.DATA1));
                            info.normalizedNumber = null;  // meaningless for SIP addresses
                            info.photoId = dataTableCursor.getLong(
                                    dataTableCursor.getColumnIndex(Data.PHOTO_ID));
                            info.lookupKey = dataTableCursor.getString(
                                    dataTableCursor.getColumnIndex(Data.LOOKUP_KEY));

                            infoUpdated = true;
                        }
                        dataTableCursor.close();
                    }
                } else {
                    // "number" is a regular phone number, so use the
                    // PhoneLookup table:
                    Cursor phonesCursor =
                            getActivity().getContentResolver().query(
                                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                                        Uri.encode(ciq.number)),
                                        PhoneQuery._PROJECTION, null, null, null);
                    if (phonesCursor != null) {
                        if (phonesCursor.moveToFirst()) {
                            info = new ContactInfo();
                            info.personId = phonesCursor.getLong(PhoneQuery.PERSON_ID);
                            info.name = phonesCursor.getString(PhoneQuery.NAME);
                            info.type = phonesCursor.getInt(PhoneQuery.PHONE_TYPE);
                            info.label = phonesCursor.getString(PhoneQuery.LABEL);
                            info.number = phonesCursor
                                    .getString(PhoneQuery.MATCHED_NUMBER);
                            info.normalizedNumber = phonesCursor
                                    .getString(PhoneQuery.NORMALIZED_NUMBER);
                            info.photoId = phonesCursor.getLong(PhoneQuery.PHOTO_ID);
                            info.lookupKey = phonesCursor.getString(PhoneQuery.LOOKUP_KEY);

                            infoUpdated = true;
                        }
                        phonesCursor.close();
                    }
                }

                if (infoUpdated) {
                    // New incoming phone number invalidates our formatted
                    // cache. Any cache fills happen only on the GUI thread.
                    info.formattedNumber = null;

                    mContactInfoCache.put(ciq.number, info);

                    // Inform list to update this item, if in view
                    needNotify = true;
                }
            }
            if (info != null) {
                updateCallLog(ciq, info);
            }
            return needNotify;
        }

        /*
         * Handles requests for contact name and number type
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            boolean needNotify = false;
            while (!mDone) {
                CallerInfoQuery ciq = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        ciq = mRequests.removeFirst();
                    } else {
                        if (needNotify) {
                            needNotify = false;
                            mHandler.sendEmptyMessage(REDRAW);
                        }
                        try {
                            mRequests.wait(1000);
                        } catch (InterruptedException ie) {
                            // Ignore and continue processing requests
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                if (!mDone && ciq != null && queryContactInfo(ciq)) {
                    needNotify = true;
                }
            }
        }

        @Override
        protected void addGroups(Cursor cursor) {

            int count = cursor.getCount();
            if (count == 0) {
                return;
            }

            int groupItemCount = 1;

            CharArrayBuffer currentValue = mBuffer1;
            CharArrayBuffer value = mBuffer2;
            cursor.moveToFirst();
            cursor.copyStringToBuffer(CallLogQuery.NUMBER, currentValue);
            int currentCallType = cursor.getInt(CallLogQuery.CALL_TYPE);
            for (int i = 1; i < count; i++) {
                cursor.moveToNext();
                cursor.copyStringToBuffer(CallLogQuery.NUMBER, value);
                boolean sameNumber = equalPhoneNumbers(value, currentValue);

                // Group adjacent calls with the same number. Make an exception
                // for the latest item if it was a missed call.  We don't want
                // a missed call to be hidden inside a group.
                if (sameNumber && currentCallType != Calls.MISSED_TYPE) {
                    groupItemCount++;
                } else {
                    if (groupItemCount > 1) {
                        addGroup(i - groupItemCount, groupItemCount, false);
                    }

                    groupItemCount = 1;

                    // Swap buffers
                    CharArrayBuffer temp = currentValue;
                    currentValue = value;
                    value = temp;

                    // If we have just examined a row following a missed call, make
                    // sure that it is grouped with subsequent calls from the same number
                    // even if it was also missed.
                    if (sameNumber && currentCallType == Calls.MISSED_TYPE) {
                        currentCallType = 0;       // "not a missed call"
                    } else {
                        currentCallType = cursor.getInt(CallLogQuery.CALL_TYPE);
                    }
                }
            }
            if (groupItemCount > 1) {
                addGroup(count - groupItemCount, groupItemCount, false);
            }
        }

        protected boolean equalPhoneNumbers(CharArrayBuffer buffer1, CharArrayBuffer buffer2) {

            // TODO add PhoneNumberUtils.compare(CharSequence, CharSequence) to avoid
            // string allocation
            return PhoneNumberUtils.compare(new String(buffer1.data, 0, buffer1.sizeCopied),
                    new String(buffer2.data, 0, buffer2.sizeCopied));
        }


        @VisibleForTesting
        @Override
        public View newStandAloneView(Context context, ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.call_log_list_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @VisibleForTesting
        @Override
        public void bindStandAloneView(View view, Context context, Cursor cursor) {
            bindView(context, view, cursor);
        }

        @VisibleForTesting
        @Override
        public View newChildView(Context context, ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.call_log_list_child_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @VisibleForTesting
        @Override
        public void bindChildView(View view, Context context, Cursor cursor) {
            bindView(context, view, cursor);
        }

        @VisibleForTesting
        @Override
        public View newGroupView(Context context, ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.call_log_list_group_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @VisibleForTesting
        @Override
        public void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
                boolean expanded) {
            final CallLogListItemViews views = (CallLogListItemViews) view.getTag();
            int groupIndicator = expanded
                    ? com.android.internal.R.drawable.expander_ic_maximized
                    : com.android.internal.R.drawable.expander_ic_minimized;
            bindView(context, view, cursor);
        }

        private void findAndCacheViews(View view) {
            // Get the views to bind to.
            CallLogListItemViews views = CallLogListItemViews.fromView(view);
            if (views.callView != null) {
                views.callView.setOnClickListener(this);
            }
            view.setTag(views);
        }

        public void bindView(Context context, View view, Cursor c) {
            final CallLogListItemViews views = (CallLogListItemViews) view.getTag();

            String number = c.getString(CallLogQuery.NUMBER);
            long date = c.getLong(CallLogQuery.DATE);
            int callType = c.getInt(CallLogQuery.CALL_TYPE);
            final String formattedNumber;
            String callerName = c.getString(CallLogQuery.CALLER_NAME);
            int callerNumberType = c.getInt(CallLogQuery.CALLER_NUMBERTYPE);
            String callerNumberLabel = c.getString(CallLogQuery.CALLER_NUMBERLABEL);
            String countryIso = c.getString(CallLogQuery.COUNTRY_ISO);
            // Store away the number so we can call it directly if you click on the call icon
            if (views.callView != null) {
                views.callView.setTag(number);
            }

            // Lookup contacts with this number
            ExpirableCache.CachedValue<ContactInfo> cachedInfo =
                    mContactInfoCache.getCachedValue(number);
            ContactInfo info = cachedInfo == null ? null : cachedInfo.getValue();
            if (cachedInfo == null) {
                // Mark it as empty and queue up a request to find the name
                // The db request should happen on a non-UI thread
                info = ContactInfo.EMPTY;
                // Format the cached call_log phone number
                formattedNumber = formatPhoneNumber(number, null, countryIso);
                mContactInfoCache.put(number, info);
                Log.d(TAG, "Contact info missing: " + number);
                // Request the contact details immediately since they are currently missing.
                enqueueRequest(number, true, c.getPosition(),
                        callerName, callerNumberType, callerNumberLabel, 0L, "");
            } else if (info != ContactInfo.EMPTY) { // Has been queried
                // Check if any data is different from the data cached in the
                // calls db. If so, queue the request so that we can update
                // the calls db.
                if (!TextUtils.equals(info.name, callerName)
                        || info.type != callerNumberType
                        || !TextUtils.equals(info.label, callerNumberLabel)) {
                    // Something is amiss, so sync up.
                    Log.w(TAG, "Contact info inconsistent: " + number);
                    // Request the contact details immediately since they are probably wrong.
                    enqueueRequest(number, true, c.getPosition(),
                            callerName, callerNumberType, callerNumberLabel, info.photoId,
                            info.lookupKey);
                } else if (cachedInfo.isExpired()) {
                    Log.d(TAG, "Contact info expired: " + number);
                    // Put it back in the cache, therefore marking it as not expired, so that other
                    // entries with the same number will not re-request it.
                    mContactInfoCache.put(number, info);
                    // The contact info is no longer up to date, we should request it. However, we
                    // do not need to request them immediately.
                    enqueueRequest(number, false, c.getPosition(), info.name, info.type, info.label,
                            info.photoId, info.lookupKey);
                }

                // Format and cache phone number for found contact
                if (info.formattedNumber == null) {
                    info.formattedNumber =
                            formatPhoneNumber(info.number, info.normalizedNumber, countryIso);
                }
                formattedNumber = info.formattedNumber;
            } else {
                // Format the cached call_log phone number
                formattedNumber = formatPhoneNumber(number, null, countryIso);
            }

            long contactId = info.personId;
            String name = info.name;
            int ntype = info.type;
            String label = info.label;
            long photoId = info.photoId;
            String lookupKey = info.lookupKey;
            // If there's no name cached in our hashmap, but there's one in the
            // calls db, use the one in the calls db. Otherwise the name in our
            // hashmap is more recent, so it has precedence.
            if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(callerName)) {
                name = callerName;
                ntype = callerNumberType;
                label = callerNumberLabel;
            }

            // Assumes the call back feature is on most of the
            // time. For private and unknown numbers: hide it.
            if (views.callView != null) {
                views.callView.setVisibility(View.VISIBLE);
            }

            final PhoneCallDetails details;
            if (TextUtils.isEmpty(name)) {
                details = new PhoneCallDetails(number, formattedNumber, callType, date);
            } else {
                details = new PhoneCallDetails(number, formattedNumber, callType, date, name,
                        ntype, label);
            }
            mCallLogViewsHelper.setPhoneCallDetails(views, details , true);
            if (views.photoView != null) {
                bindQuickContact(views.photoView, photoId, contactId, lookupKey);
            }


            // Listen for the first draw
            if (mPreDrawListener == null) {
                mFirst = true;
                mPreDrawListener = this;
                view.getViewTreeObserver().addOnPreDrawListener(this);
            }
        }

        private void bindQuickContact(QuickContactBadge view, long photoId, long contactId,
                String lookupKey) {
            view.assignContactUri(getContactUri(contactId, lookupKey));
            mContactPhotoManager.loadPhoto(view, photoId);
        }

        private Uri getContactUri(long contactId, String lookupKey) {
            return Contacts.getLookupUri(contactId, lookupKey);
        }

        /**
         * Sets whether processing of requests for contact details should be enabled.
         * <p>
         * This method should be called in tests to disable such processing of requests when not
         * needed.
         */
        public void disableRequestProcessingForTest() {
            mRequestProcessingDisabled = true;
        }
    }

    private static final class QueryHandler extends AsyncQueryHandler {
        private final WeakReference<CallLogFragment> mFragment;

        /**
         * Simple handler that wraps background calls to catch
         * {@link SQLiteException}, such as when the disk is full.
         */
        protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
            public CatchingWorkerHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                try {
                    // Perform same query while catching any exceptions
                    super.handleMessage(msg);
                } catch (SQLiteDiskIOException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                } catch (SQLiteFullException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                } catch (SQLiteDatabaseCorruptException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                }
            }
        }

        @Override
        protected Handler createHandler(Looper looper) {
            // Provide our special handler that catches exceptions
            return new CatchingWorkerHandler(looper);
        }

        public QueryHandler(CallLogFragment fragment) {
            super(fragment.getActivity().getContentResolver());
            mFragment = new WeakReference<CallLogFragment>(fragment);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final CallLogFragment fragment = mFragment.get();
            if (fragment != null && fragment.getActivity() != null &&
                    !fragment.getActivity().isFinishing()) {
                final CallLogFragment.CallLogAdapter callsAdapter = fragment.mAdapter;
                callsAdapter.setLoading(false);
                callsAdapter.changeCursor(cursor);
                if (fragment.mScrollToTop) {
                    final ListView listView = fragment.getListView();
                    if (listView.getFirstVisiblePosition() > 5) {
                        listView.setSelection(5);
                    }
                    listView.smoothScrollToPosition(0);
                    fragment.mScrollToTop = false;
                }
            } else {
                cursor.close();
            }
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        mVoiceMailNumber = ((TelephonyManager) getActivity().getSystemService(
                Context.TELEPHONY_SERVICE)).getVoiceMailNumber();
        mQueryHandler = new QueryHandler(this);

        mCurrentCountryIso = ContactsUtils.getCurrentCountryIso(getActivity());

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.call_log_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setOnCreateContextMenuListener(this);
        mAdapter = new CallLogAdapter();
        setListAdapter(mAdapter);
    }

    @Override
    public void onStart() {
        mScrollToTop = true;
        super.onStart();
    }

    @Override
    public void onResume() {
        // Mark all entries in the contact info cache as out of date, so they will be looked up
        // again once being shown.
        if (mAdapter != null) {
            mAdapter.invalidateCache();
        }

        startQuery();
        resetNewCallsFlag();

        super.onResume();

        mAdapter.mPreDrawListener = null; // Let it restart the thread after next draw
    }

    @Override
    public void onPause() {
        super.onPause();

        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        mAdapter.changeCursor(null);
    }

    /**
     * Format the given phone number
     *
     * @param number the number to be formatted.
     * @param normalizedNumber the normalized number of the given number.
     * @param countryIso the ISO 3166-1 two letters country code, the country's
     *        convention will be used to format the number if the normalized
     *        phone is null.
     *
     * @return the formatted number, or the given number if it was formatted.
     */
    private String formatPhoneNumber(String number, String normalizedNumber, String countryIso) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        // If "number" is really a SIP address, don't try to do any formatting at all.
        if (PhoneNumberUtils.isUriNumber(number)) {
            return number;
        }
        if (TextUtils.isEmpty(countryIso)) {
            countryIso = mCurrentCountryIso;
        }
        return PhoneNumberUtils.formatNumber(number, normalizedNumber, countryIso);
    }

    private void resetNewCallsFlag() {
        // Mark all "new" missed calls as not new anymore
        StringBuilder where = new StringBuilder("type=");
        where.append(Calls.MISSED_TYPE);
        where.append(" AND new=1");

        ContentValues values = new ContentValues(1);
        values.put(Calls.NEW, "0");
        mQueryHandler.startUpdate(UPDATE_TOKEN, null, Calls.CONTENT_URI_WITH_VOICEMAIL,
                values, where.toString(), null);
    }

    private void startQuery() {
        mAdapter.setLoading(true);

        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);
        mQueryHandler.startQuery(QUERY_TOKEN, null, Calls.CONTENT_URI_WITH_VOICEMAIL,
                CallLogQuery._PROJECTION, null, null, Calls.DEFAULT_SORT_ORDER);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.call_log_options, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.delete_all).setVisible(mShowMenu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        AdapterView.AdapterContextMenuInfo menuInfo;
        try {
             menuInfo = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfoIn", e);
            return;
        }

        Cursor cursor = (Cursor) mAdapter.getItem(menuInfo.position);

        String number = cursor.getString(CallLogQuery.NUMBER);
        Uri numberUri = null;
        boolean isVoicemail = false;
        boolean isSipNumber = false;
        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            number = getString(R.string.unknown);
        } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            number = getString(R.string.private_num);
        } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            number = getString(R.string.payphone);
        } else if (PhoneNumberUtils.extractNetworkPortion(number).equals(mVoiceMailNumber)) {
            number = getString(R.string.voicemail);
            numberUri = Uri.parse("voicemail:x");
            isVoicemail = true;
        } else if (PhoneNumberUtils.isUriNumber(number)) {
            numberUri = Uri.fromParts("sip", number, null);
            isSipNumber = true;
        } else {
            numberUri = Uri.fromParts("tel", number, null);
        }

        ContactInfo info = mAdapter.getContactInfo(number);
        boolean contactInfoPresent = (info != null && info != ContactInfo.EMPTY);
        if (contactInfoPresent) {
            menu.setHeaderTitle(info.name);
        } else {
            menu.setHeaderTitle(number);
        }

        if (numberUri != null) {
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, numberUri);
            menu.add(0, 0, 0, getResources().getString(R.string.recentCalls_callNumber, number))
                    .setIntent(intent);
        }

        if (contactInfoPresent) {
            menu.add(0, 0, 0, R.string.menu_viewContact)
                    .setIntent(new Intent(Intent.ACTION_VIEW,
                            ContentUris.withAppendedId(Contacts.CONTENT_URI, info.personId)));
        }

        if (numberUri != null && !isVoicemail && !isSipNumber) {
            menu.add(0, 0, 0, R.string.recentCalls_editNumberBeforeCall)
                    .setIntent(new Intent(Intent.ACTION_DIAL, numberUri));
            menu.add(0, 0, 0, R.string.menu_sendTextMessage)
                    .setIntent(new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts("sms", number, null)));
        }

        // "Add to contacts" item, if this entry isn't already associated with a contact
        if (!contactInfoPresent && numberUri != null && !isVoicemail && !isSipNumber) {
            // TODO: This item is currently disabled for SIP addresses, because
            // the Insert.PHONE extra only works correctly for PSTN numbers.
            //
            // To fix this for SIP addresses, we need to:
            // - define ContactsContract.Intents.Insert.SIP_ADDRESS, and use it here if
            //   the current number is a SIP address
            // - update the contacts UI code to handle Insert.SIP_ADDRESS by
            //   updating the SipAddress field
            // and then we can remove the "!isSipNumber" check above.

            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(Insert.PHONE, number);
            menu.add(0, 0, 0, R.string.recentCalls_addToContact)
                    .setIntent(intent);
        }
        menu.add(0, MenuItems.DELETE, 0, R.string.recentCalls_removeFromRecentList);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OptionsMenuItems.DELETE_ALL: {
                ClearCallLogDialog.show(getFragmentManager());
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // Convert the menu info to the proper type
        AdapterView.AdapterContextMenuInfo menuInfo;
        try {
             menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfoIn", e);
            return false;
        }

        switch (item.getItemId()) {
            case MenuItems.DELETE: {
                Cursor cursor = (Cursor)mAdapter.getItem(menuInfo.position);
                int groupSize = 1;
                if (mAdapter.isGroupHeader(menuInfo.position)) {
                    groupSize = mAdapter.getGroupSize(menuInfo.position);
                }

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < groupSize; i++) {
                    if (i != 0) {
                        sb.append(",");
                        cursor.moveToNext();
                    }
                    long id = cursor.getLong(CallLogQuery.ID);
                    sb.append(id);
                }

                getActivity().getContentResolver().delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                        Calls._ID + " IN (" + sb + ")", null);
            }
        }
        return super.onContextItemSelected(item);
    }

    /*
     * Get the number from the Contacts, if available, since sometimes
     * the number provided by caller id may not be formatted properly
     * depending on the carrier (roaming) in use at the time of the
     * incoming call.
     * Logic : If the caller-id number starts with a "+", use it
     *         Else if the number in the contacts starts with a "+", use that one
     *         Else if the number in the contacts is longer, use that one
     */
    private String getBetterNumberFromContacts(String number) {
        String matchingNumber = null;
        // Look in the cache first. If it's not found then query the Phones db
        ContactInfo ci = mAdapter.mContactInfoCache.getPossiblyExpired(number);
        if (ci != null && ci != ContactInfo.EMPTY) {
            matchingNumber = ci.number;
        } else {
            try {
                Cursor phonesCursor = getActivity().getContentResolver().query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, number),
                        PhoneQuery._PROJECTION, null, null, null);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        matchingNumber = phonesCursor.getString(PhoneQuery.MATCHED_NUMBER);
                    }
                    phonesCursor.close();
                }
            } catch (Exception e) {
                // Use the number from the call log
            }
        }
        if (!TextUtils.isEmpty(matchingNumber) &&
                (matchingNumber.startsWith("+")
                        || matchingNumber.length() > number.length())) {
            number = matchingNumber;
        }
        return number;
    }

    public void callSelectedEntry() {
        int position = getListView().getSelectedItemPosition();
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] [send] calls the
            // most recent entry.
            position = 0;
        }
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        if (cursor != null) {
            String number = cursor.getString(CallLogQuery.NUMBER);
            if (TextUtils.isEmpty(number)
                    || number.equals(CallerInfo.UNKNOWN_NUMBER)
                    || number.equals(CallerInfo.PRIVATE_NUMBER)
                    || number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                // This number can't be called, do nothing
                return;
            }
            Intent intent;
            // If "number" is really a SIP address, construct a sip: URI.
            if (PhoneNumberUtils.isUriNumber(number)) {
                intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                    Uri.fromParts("sip", number, null));
            } else {
                // We're calling a regular PSTN phone number.
                // Construct a tel: URI, but do some other possible cleanup first.
                int callType = cursor.getInt(CallLogQuery.CALL_TYPE);
                if (!number.startsWith("+") &&
                       (callType == Calls.INCOMING_TYPE
                                || callType == Calls.MISSED_TYPE)) {
                    // If the caller-id matches a contact with a better qualified number, use it
                    number = getBetterNumberFromContacts(number);
                }
                intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                    Uri.fromParts("tel", number, null));
            }
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (mAdapter.isGroupHeader(position)) {
            mAdapter.toggleGroup(position);
        } else {
            Intent intent = new Intent(getActivity(), CallDetailActivity.class);
            intent.setData(ContentUris.withAppendedId(Calls.CONTENT_URI_WITH_VOICEMAIL, id));
            startActivity(intent);
        }
    }

    @VisibleForTesting
    public CallLogAdapter getAdapter() {
        return mAdapter;
    }

    @VisibleForTesting
    public String getVoiceMailNumber() {
        return mVoiceMailNumber;
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
        mShowMenu = visible;
    }
}
