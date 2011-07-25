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
import com.android.contacts.activities.DialtactsActivity;
import com.android.contacts.activities.DialtactsActivity.ViewPagerVisibilityListener;
import com.android.contacts.util.ExpirableCache;
import com.android.contacts.voicemail.VoicemailStatusHelper;
import com.android.contacts.voicemail.VoicemailStatusHelper.StatusMessage;
import com.android.contacts.voicemail.VoicemailStatusHelperImpl;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;
import com.google.common.annotations.VisibleForTesting;

import android.app.ListFragment;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.List;

/**
 * Displays a list of call log entries.
 */
public class CallLogFragment extends ListFragment implements ViewPagerVisibilityListener,
        CallLogQueryHandler.Listener {
    private static final String TAG = "CallLogFragment";

    /** The size of the cache of contact info. */
    private static final int CONTACT_INFO_CACHE_SIZE = 100;

    /** The query for the call log table. */
    public static final class CallLogQuery {
        // If you alter this, you must also alter the method that inserts a fake row to the headers
        // in the CallLogQueryHandler class called createHeaderCursorFor().
        public static final String[] _PROJECTION = new String[] {
                Calls._ID,
                Calls.NUMBER,
                Calls.DATE,
                Calls.DURATION,
                Calls.TYPE,
                Calls.COUNTRY_ISO,
                Calls.VOICEMAIL_URI,
        };

        public static final int ID = 0;
        public static final int NUMBER = 1;
        public static final int DATE = 2;
        public static final int DURATION = 3;
        public static final int CALL_TYPE = 4;
        public static final int COUNTRY_ISO = 5;
        public static final int VOICEMAIL_URI = 6;

        /**
         * The name of the synthetic "section" column.
         * <p>
         * This column identifies whether a row is a header or an actual item, and whether it is
         * part of the new or old calls.
         */
        public static final String SECTION_NAME = "section";
        /** The index of the "section" column in the projection. */
        public static final int SECTION = 7;
        /** The value of the "section" column for the header of the new section. */
        public static final int SECTION_NEW_HEADER = 0;
        /** The value of the "section" column for the items of the new section. */
        public static final int SECTION_NEW_ITEM = 1;
        /** The value of the "section" column for the header of the old section. */
        public static final int SECTION_OLD_HEADER = 2;
        /** The value of the "section" column for the items of the old section. */
        public static final int SECTION_OLD_ITEM = 3;

        /** The call log projection including the section name. */
        public static final String[] EXTENDED_PROJECTION;
        static {
            EXTENDED_PROJECTION = new String[_PROJECTION.length + 1];
            System.arraycopy(_PROJECTION, 0, EXTENDED_PROJECTION, 0, _PROJECTION.length);
            EXTENDED_PROJECTION[_PROJECTION.length] = SECTION_NAME;
        }

        public static boolean isSectionHeader(Cursor cursor) {
            int section = cursor.getInt(CallLogQuery.SECTION);
            return section == CallLogQuery.SECTION_NEW_HEADER
                    || section == CallLogQuery.SECTION_OLD_HEADER;
        }

        public static boolean isNewSection(Cursor cursor) {
            int section = cursor.getInt(CallLogQuery.SECTION);
            return section == CallLogQuery.SECTION_NEW_ITEM
                    || section == CallLogQuery.SECTION_NEW_HEADER;
        }
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
                PhoneLookup.PHOTO_THUMBNAIL_URI,
                PhoneLookup.LOOKUP_KEY};

        public static final int PERSON_ID = 0;
        public static final int NAME = 1;
        public static final int PHONE_TYPE = 2;
        public static final int LABEL = 3;
        public static final int MATCHED_NUMBER = 4;
        public static final int NORMALIZED_NUMBER = 5;
        public static final int THUMBNAIL_URI = 6;
        public static final int LOOKUP_KEY = 7;
    }

    private CallLogAdapter mAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private String mVoiceMailNumber;
    private String mCurrentCountryIso;
    private boolean mScrollToTop;

    private boolean mShowOptionsMenu;

    private VoicemailStatusHelper mVoicemailStatusHelper;
    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;

    public static final class ContactInfo {
        public long personId;
        public String name;
        public int type;
        public String label;
        public String number;
        public String formattedNumber;
        public String normalizedNumber;
        public Uri thumbnailUri;
        public String lookupKey;

        public static ContactInfo EMPTY = new ContactInfo();

        @Override
        public int hashCode() {
            // Uses only name and personId to determine hashcode.
            // This should be sufficient to have a reasonable distribution of hash codes.
            // Moreover, there should be no two people with the same personId.
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (personId ^ (personId >>> 32));
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            ContactInfo other = (ContactInfo) obj;
            if (personId != other.personId) return false;
            if (!TextUtils.equals(name, other.name)) return false;
            if (type != other.type) return false;
            if (!TextUtils.equals(label, other.label)) return false;
            if (!TextUtils.equals(number, other.number)) return false;
            // Ignore formatted number.
            if (!TextUtils.equals(normalizedNumber, other.normalizedNumber)) return false;
            if (!uriEquals(thumbnailUri, other.thumbnailUri)) return false;
            if (!TextUtils.equals(lookupKey, other.lookupKey)) return false;
            return true;
        }

        private static boolean uriEquals(Uri thumbnailUri1, Uri thumbnailUri2) {
            if (thumbnailUri1 == thumbnailUri2) return true;
            if (thumbnailUri1 == null) return false;
            return thumbnailUri1.equals(thumbnailUri2);
        }
    }

    public interface GroupCreator {
        public void addGroup(int cursorPosition, int size, boolean expanded);
    }

    /** Adapter class to fill in data for the Call Log */
    public final class CallLogAdapter extends GroupingListAdapter
            implements Runnable, ViewTreeObserver.OnPreDrawListener, View.OnClickListener,
            GroupCreator {
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
        private final LinkedList<String> mRequests;

        private volatile boolean mDone;
        private boolean mLoading = true;
        private ViewTreeObserver.OnPreDrawListener mPreDrawListener;
        private static final int REDRAW = 1;
        private static final int START_THREAD = 2;
        private boolean mFirst;
        private Thread mCallerIdThread;

        /** Instance of helper class for managing views. */
        private final CallLogListItemHelper mCallLogViewsHelper;

        /** Helper to set up contact photos. */
        private final ContactPhotoManager mContactPhotoManager;
        /** Helper to parse and process phone numbers. */
        private PhoneNumberHelper mPhoneNumberHelper;
        /** Helper to group call log entries. */
        private final CallLogGroupBuilder mCallLogGroupBuilder;

        /** Can be set to true by tests to disable processing of requests. */
        private volatile boolean mRequestProcessingDisabled = false;

        @Override
        public void onClick(View view) {
            IntentProvider intentProvider = (IntentProvider) view.getTag();
            if (intentProvider != null) {
                startActivity(intentProvider.getIntent(CallLogFragment.this.getActivity()));
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
            mRequests = new LinkedList<String>();
            mPreDrawListener = null;

            Resources resources = getResources();
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            CallTypeHelper callTypeHelper = new CallTypeHelper(resources, layoutInflater);
            Drawable callDrawable = resources.getDrawable(R.drawable.ic_dial_action_call);
            Drawable playDrawable = resources.getDrawable(
                    R.drawable.ic_call_log_list_action_play);

            mContactPhotoManager = ContactPhotoManager.getInstance(getActivity());
            mPhoneNumberHelper = new PhoneNumberHelper(getResources(), mVoiceMailNumber);
            PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                    resources, callTypeHelper, mPhoneNumberHelper);
            mCallLogViewsHelper =
                    new CallLogListItemHelper(phoneCallDetailsHelper, mPhoneNumberHelper);
            mCallLogGroupBuilder = new CallLogGroupBuilder(this);
        }

        /**
         * Requery on background thread when {@link Cursor} changes.
         */
        @Override
        protected void onContentChanged() {
            // Start async requery
            startCallsQuery();
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

        private void enqueueRequest(String number, boolean immediate) {
            synchronized (mRequests) {
                if (!mRequests.contains(number)) {
                    mRequests.add(number);
                    mRequests.notifyAll();
                }
            }
            if (mFirst && immediate) {
                startRequestProcessing();
                mFirst = false;
            }
        }

        /**
         * Determines the contact information for the given SIP address.
         * <p>
         * It returns the contact info if found.
         * <p>
         * If no contact corresponds to the given SIP address, returns {@link ContactInfo#EMPTY}.
         * <p>
         * If the lookup fails for some other reason, it returns null.
         */
        private ContactInfo queryContactInfoForSipAddress(String sipAddress) {
            final ContactInfo info;

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
            String[] selectionArgs = new String[] { sipAddress.toUpperCase() };

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
                    final String thumbnailUriString = dataTableCursor.getString(
                            dataTableCursor.getColumnIndex(Data.PHOTO_THUMBNAIL_URI));
                    info.thumbnailUri = thumbnailUriString == null
                            ? null
                            : Uri.parse(thumbnailUriString);
                    info.lookupKey = dataTableCursor.getString(
                            dataTableCursor.getColumnIndex(Data.LOOKUP_KEY));
                } else {
                    info = ContactInfo.EMPTY;
                }
                dataTableCursor.close();
            } else {
                // Failed to fetch the data, ignore this request.
                info = null;
            }
            return info;
        }

        /**
         * Determines the contact information for the given phone number.
         * <p>
         * It returns the contact info if found.
         * <p>
         * If no contact corresponds to the given phone number, returns {@link ContactInfo#EMPTY}.
         * <p>
         * If the lookup fails for some other reason, it returns null.
         */
        private ContactInfo queryContactInfoForPhoneNumber(String number) {
            final ContactInfo info;

            // "number" is a regular phone number, so use the
            // PhoneLookup table:
            Cursor phonesCursor =
                    getActivity().getContentResolver().query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                                Uri.encode(number)),
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
                    final String thumbnailUriString = phonesCursor.getString(
                            PhoneQuery.THUMBNAIL_URI);
                    info.thumbnailUri = thumbnailUriString == null
                            ? null
                            : Uri.parse(thumbnailUriString);
                    info.lookupKey = phonesCursor.getString(PhoneQuery.LOOKUP_KEY);
                } else {
                    info = ContactInfo.EMPTY;
                }
                phonesCursor.close();
            } else {
                // Failed to fetch the data, ignore this request.
                info = null;
            }
            return info;
        }

        /**
         * Queries the appropriate content provider for the contact associated with the number.
         * <p>
         * The number might be either a SIP address or a phone number.
         * <p>
         * It returns true if it updated the content of the cache and we should therefore tell the
         * view to update its content.
         */
        private boolean queryContactInfo(String number) {
            final ContactInfo info;

            // Determine the contact info.
            if (PhoneNumberUtils.isUriNumber(number)) {
                // This "number" is really a SIP address.
                info = queryContactInfoForSipAddress(number);
            } else {
                info = queryContactInfoForPhoneNumber(number);
            }

            if (info == null) {
                // The lookup failed, just return without requesting to update the view.
                return false;
            }

            // Check the existing entry in the cache: only if it has changed we should update the
            // view.
            ContactInfo existingInfo = mContactInfoCache.getPossiblyExpired(number);
            boolean updated = !info.equals(existingInfo);
            if (updated) {
                // The formattedNumber is computed by the UI thread when needed. Since we updated
                // the details of the contact, set this value to null for now.
                info.formattedNumber = null;
            }
            // Store the data in the cache so that the UI thread can use to display it. Store it
            // even if it has not changed so that it is marked as not expired.
            mContactInfoCache.put(number, info);
            return updated;
        }

        /*
         * Handles requests for contact name and number type
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            boolean needNotify = false;
            while (!mDone) {
                String number = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        number = mRequests.removeFirst();
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
                if (!mDone && number != null && queryContactInfo(number)) {
                    needNotify = true;
                }
            }
        }

        @Override
        protected void addGroups(Cursor cursor) {
            mCallLogGroupBuilder.addGroups(cursor);
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
            bindView(view, cursor, 1);
        }

        @VisibleForTesting
        @Override
        public View newChildView(Context context, ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.call_log_list_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @VisibleForTesting
        @Override
        public void bindChildView(View view, Context context, Cursor cursor) {
            bindView(view, cursor, 1);
        }

        @VisibleForTesting
        @Override
        public View newGroupView(Context context, ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.call_log_list_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @VisibleForTesting
        @Override
        public void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
                boolean expanded) {
            bindView(view, cursor, groupSize);
        }

        private void findAndCacheViews(View view) {
            // Get the views to bind to.
            CallLogListItemViews views = CallLogListItemViews.fromView(view);
            views.callView.setOnClickListener(this);
            view.setTag(views);
        }

        /**
         * Binds the views in the entry to the data in the call log.
         *
         * @param view the view corresponding to this entry
         * @param c the cursor pointing to the entry in the call log
         * @param count the number of entries in the current item, greater than 1 if it is a group
         */
        private void bindView(View view, Cursor c, int count) {
            final CallLogListItemViews views = (CallLogListItemViews) view.getTag();
            final int section = c.getInt(CallLogQuery.SECTION);

            // This might be a header: check the value of the section column in the cursor.
            if (section == CallLogQuery.SECTION_NEW_HEADER
                    || section == CallLogQuery.SECTION_OLD_HEADER) {
                views.listItemView.setVisibility(View.GONE);
                views.listHeaderView.setVisibility(View.VISIBLE);
                views.listHeaderTextView.setText(
                        section == CallLogQuery.SECTION_NEW_HEADER
                                ? R.string.call_log_new_header
                                : R.string.call_log_old_header);
                // Nothing else to set up for a header.
                return;
            }
            // Default case: an item in the call log.
            views.listItemView.setVisibility(View.VISIBLE);
            views.listHeaderView.setVisibility(View.GONE);

            final String number = c.getString(CallLogQuery.NUMBER);
            final long date = c.getLong(CallLogQuery.DATE);
            final long duration = c.getLong(CallLogQuery.DURATION);
            final int callType = c.getInt(CallLogQuery.CALL_TYPE);
            final String formattedNumber;
            final String countryIso = c.getString(CallLogQuery.COUNTRY_ISO);

            // Store away the voicemail information so we can play it directly.
            if (callType == Calls.VOICEMAIL_TYPE) {
                String voicemailUri = c.getString(CallLogQuery.VOICEMAIL_URI);
                final long rowId = c.getLong(CallLogQuery.ID);
                views.callView.setTag(
                        IntentProvider.getPlayVoicemailIntentProvider(rowId, voicemailUri));
            } else if (!TextUtils.isEmpty(number)) {
                // Store away the number so we can call it directly if you click on the call icon.
                views.callView.setTag(IntentProvider.getReturnCallIntentProvider(number));
            } else {
                // No action enabled.
                views.callView.setTag(null);
            }

            // Lookup contacts with this number
            ExpirableCache.CachedValue<ContactInfo> cachedInfo =
                    mContactInfoCache.getCachedValue(number);
            ContactInfo info = cachedInfo == null ? null : cachedInfo.getValue();
            if (cachedInfo == null) {
                // Mark it as empty and queue up a request to find the name.
                // The db request should happen on a non-UI thread.
                info = ContactInfo.EMPTY;
                mContactInfoCache.put(number, info);
                // Request the contact details immediately since they are currently missing.
                enqueueRequest(number, true);
                // Format the phone number in the call log as best as we can.
                formattedNumber = formatPhoneNumber(number, null, countryIso);
            } else {
                if (cachedInfo.isExpired()) {
                    // The contact info is no longer up to date, we should request it. However, we
                    // do not need to request them immediately.
                    enqueueRequest(number, false);
                }

                if (info != ContactInfo.EMPTY) {
                    // Format and cache phone number for found contact.
                    if (info.formattedNumber == null) {
                        info.formattedNumber =
                                formatPhoneNumber(info.number, info.normalizedNumber, countryIso);
                    }
                    formattedNumber = info.formattedNumber;
                } else {
                    // Format the phone number in the call log as best as we can.
                    formattedNumber = formatPhoneNumber(number, null, countryIso);
                }
            }

            final long personId = info.personId;
            final String name = info.name;
            final int ntype = info.type;
            final String label = info.label;
            final Uri thumbnailUri = info.thumbnailUri;
            final String lookupKey = info.lookupKey;
            final int[] callTypes = getCallTypes(c, count);
            final PhoneCallDetails details;
            if (TextUtils.isEmpty(name)) {
                details = new PhoneCallDetails(number, formattedNumber, countryIso,
                        callTypes, date, duration);
            } else {
                details = new PhoneCallDetails(number, formattedNumber, countryIso,
                        callTypes, date, duration, name, ntype, label, personId, thumbnailUri);
            }

            final boolean isNew = CallLogQuery.isNewSection(c);
            // Use icons for old items, but text for new ones.
            final boolean useIcons = !isNew;
            // New items also use the highlighted version of the text.
            final boolean isHighlighted = isNew;
            mCallLogViewsHelper.setPhoneCallDetails(views, details, useIcons, isHighlighted);
            if (views.photoView != null) {
                bindQuickContact(views.photoView, thumbnailUri, personId, lookupKey);
            }


            // Listen for the first draw
            if (mPreDrawListener == null) {
                mFirst = true;
                mPreDrawListener = this;
                view.getViewTreeObserver().addOnPreDrawListener(this);
            }
        }

        /**
         * Returns the call types for the given number of items in the cursor.
         * <p>
         * It uses the next {@code count} rows in the cursor to extract the types.
         * <p>
         * It position in the cursor is unchanged by this function.
         */
        private int[] getCallTypes(Cursor cursor, int count) {
            int position = cursor.getPosition();
            int[] callTypes = new int[count];
            for (int index = 0; index < count; ++index) {
                callTypes[index] = cursor.getInt(CallLogQuery.CALL_TYPE);
                cursor.moveToNext();
            }
            cursor.moveToPosition(position);
            return callTypes;
        }

        private void bindQuickContact(QuickContactBadge view, Uri thumbnailUri, long contactId,
                String lookupKey) {
            view.assignContactUri(getContactUri(contactId, lookupKey));
            mContactPhotoManager.loadPhoto(view, thumbnailUri);
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

        public void injectContactInfoForTest(String number, ContactInfo contactInfo) {
            mContactInfoCache.put(number, contactInfo);
        }

        @Override
        public void addGroup(int cursorPosition, int size, boolean expanded) {
            super.addGroup(cursorPosition, size, expanded);
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        mVoiceMailNumber = ((TelephonyManager) getActivity().getSystemService(
                Context.TELEPHONY_SERVICE)).getVoiceMailNumber();
        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(), this);

        mCurrentCountryIso = ContactsUtils.getCurrentCountryIso(getActivity());

        setHasOptionsMenu(true);
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public void onCallsFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);
        if (mScrollToTop) {
            final ListView listView = getListView();
            if (listView.getFirstVisiblePosition() > 5) {
                listView.setSelection(5);
            }
            listView.smoothScrollToPosition(0);
            mScrollToTop = false;
        }
    }

    /**
     * Called by {@link CallLogQueryHandler} after a successful query to voicemail status provider.
     */
    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        updateVoicemailStatusMessage(statusCursor);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_log_fragment, container, false);
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mStatusMessageView = view.findViewById(R.id.voicemail_status);
        mStatusMessageText = (TextView) view.findViewById(R.id.voicemail_status_message);
        mStatusMessageAction = (TextView) view.findViewById(R.id.voicemail_status_action);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
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
        super.onResume();
        refreshData();
    }

    private void updateVoicemailStatusMessage(Cursor statusCursor) {
        List<StatusMessage> messages = mVoicemailStatusHelper.getStatusMessages(statusCursor);
        if (messages.size() == 0) {
            mStatusMessageView.setVisibility(View.GONE);
        } else {
            mStatusMessageView.setVisibility(View.VISIBLE);
            // TODO: Change the code to show all messages. For now just pick the first message.
            final StatusMessage message = messages.get(0);
            if (message.showInCallLog()) {
                mStatusMessageText.setText(message.callLogMessageId);
            }
            if (message.actionMessageId != -1) {
                mStatusMessageAction.setText(message.actionMessageId);
            }
            if (message.actionUri != null) {
                mStatusMessageAction.setClickable(true);
                mStatusMessageAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getActivity().startActivity(
                                new Intent(Intent.ACTION_VIEW, message.actionUri));
                    }
                });
            } else {
                mStatusMessageAction.setClickable(false);
            }
        }
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
        mCallLogQueryHandler.updateMissedCalls();
    }

    private void startCallsQuery() {
        mAdapter.setLoading(true);
        mCallLogQueryHandler.fetchAllCalls();
    }

    private void startVoicemailStatusQuery() {
        mCallLogQueryHandler.fetchVoicemailStatus();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.call_log_options, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.delete_all).setVisible(mShowOptionsMenu);
        menu.findItem(R.id.show_voicemails_only).setVisible(mShowOptionsMenu);
        final MenuItem callSettingsMenuItem = menu.findItem(R.id.menu_call_settings_call_log);
        if (mShowOptionsMenu) {
            callSettingsMenuItem.setVisible(true);
            callSettingsMenuItem.setIntent(DialtactsActivity.getCallSettingsIntent());
        } else {
            callSettingsMenuItem.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete_all: {
                ClearCallLogDialog.show(getFragmentManager());
                return true;
            }

            case R.id.show_voicemails_only: {
                mCallLogQueryHandler.fetchVoicemailOnly();
                return true;
            }
            default:
                return false;
        }
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
        Cursor cursor = (Cursor) mAdapter.getItem(position);
        if (CallLogQuery.isSectionHeader(cursor)) {
            // Do nothing when a header is clicked.
            return;
        }
        Intent intent = new Intent(getActivity(), CallDetailActivity.class);
        if (mAdapter.isGroupHeader(position)) {
            // We want to restore the position in the cursor at the end.
            int currentPosition = cursor.getPosition();
            int groupSize = mAdapter.getGroupSize(position);
            long[] ids = new long[groupSize];
            // Copy the ids of the rows in the group.
            for (int index = 0; index < groupSize; ++index) {
                ids[index] = cursor.getLong(CallLogQuery.ID);
                cursor.moveToNext();
            }
            intent.putExtra(CallDetailActivity.EXTRA_CALL_LOG_IDS, ids);
            cursor.moveToPosition(currentPosition);
        } else {
            // If there is a single item, use the direct URI for it.
            intent.setData(ContentUris.withAppendedId(Calls.CONTENT_URI_WITH_VOICEMAIL, id));
            String voicemailUri = cursor.getString(CallLogQuery.VOICEMAIL_URI);
            if (voicemailUri != null) {
                intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI, Uri.parse(voicemailUri));
            }
            intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, false);
        }
        startActivity(intent);
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
        mShowOptionsMenu = visible;
        if (visible && isResumed()) {
            refreshData();
        }
    }

    /** Requests updates to the data to be shown. */
    private void refreshData() {
        // Mark all entries in the contact info cache as out of date, so they will be looked up
        // again once being shown.
        mAdapter.invalidateCache();
        startCallsQuery();
        resetNewCallsFlag();
        startVoicemailStatusQuery();
        mAdapter.mPreDrawListener = null; // Let it restart the thread after next draw
        // Clear notifications only when window gains focus.  This activity won't
        // immediately receive focus if the keyguard screen is above it.
        if (getActivity().hasWindowFocus()) {
            removeMissedCallNotifications();
        }
    }

    /** Removes the missed call notifications. */
    private void removeMissedCallNotifications() {
        try {
            ITelephony telephony =
                    ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
            if (telephony != null) {
                telephony.cancelMissedCallsNotification();
            } else {
                Log.w(TAG, "Telephony service is null, can't call " +
                        "cancelMissedCallsNotification");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to clear missed calls notification due to remote exception");
        }
    }
}
