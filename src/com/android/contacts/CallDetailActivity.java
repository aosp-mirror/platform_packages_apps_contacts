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

import com.android.contacts.calllog.CallDetailHistoryAdapter;
import com.android.contacts.calllog.CallTypeHelper;
import com.android.contacts.calllog.PhoneNumberHelper;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the details of a specific call log entry.
 * <p>
 * This activity can be either started with the URI of a single call log entry, or with the
 * {@link #EXTRA_CALL_LOG_IDS} extra to specify a group of call log entries.
 */
public class CallDetailActivity extends ListActivity implements
        AdapterView.OnItemClickListener {
    private static final String TAG = "CallDetail";

    /** A long array extra containing ids of call log entries to display. */
    public static final String EXTRA_CALL_LOG_IDS = "com.android.contacts.CALL_LOG_IDS";

    /** The views representing the details of a phone call. */
    private PhoneCallDetailsViews mPhoneCallDetailsViews;
    private CallTypeHelper mCallTypeHelper;
    private PhoneNumberHelper mPhoneNumberHelper;
    private PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    private View mHomeActionView;
    private ImageView mMainActionView;
    private ImageView mContactBackgroundView;

    private String mNumber = null;
    private String mDefaultCountryIso;

    /* package */ LayoutInflater mInflater;
    /* package */ Resources mResources;
    /** Helper to load contact photos. */
    private ContactPhotoManager mContactPhotoManager;

    static final String[] CALL_LOG_PROJECTION = new String[] {
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.COUNTRY_ISO,
    };

    static final int DATE_COLUMN_INDEX = 0;
    static final int DURATION_COLUMN_INDEX = 1;
    static final int NUMBER_COLUMN_INDEX = 2;
    static final int CALL_TYPE_COLUMN_INDEX = 3;
    static final int COUNTRY_ISO_COLUMN_INDEX = 4;

    static final String[] PHONES_PROJECTION = new String[] {
        PhoneLookup._ID,
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.TYPE,
        PhoneLookup.LABEL,
        PhoneLookup.NUMBER,
        PhoneLookup.NORMALIZED_NUMBER,
        PhoneLookup.PHOTO_URI,
    };
    static final int COLUMN_INDEX_ID = 0;
    static final int COLUMN_INDEX_NAME = 1;
    static final int COLUMN_INDEX_TYPE = 2;
    static final int COLUMN_INDEX_LABEL = 3;
    static final int COLUMN_INDEX_NUMBER = 4;
    static final int COLUMN_INDEX_NORMALIZED_NUMBER = 5;
    static final int COLUMN_INDEX_PHOTO_URI = 6;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.call_detail);

        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();

        mPhoneCallDetailsViews = PhoneCallDetailsViews.fromView(getWindow().getDecorView());
        mCallTypeHelper = new CallTypeHelper(getResources(),
                getResources().getDrawable(R.drawable.ic_call_incoming_holo_dark),
                getResources().getDrawable(R.drawable.ic_call_outgoing_holo_dark),
                getResources().getDrawable(R.drawable.ic_call_missed_holo_dark),
                getResources().getDrawable(R.drawable.ic_call_voicemail_holo_dark));
        mPhoneNumberHelper = new PhoneNumberHelper(mResources, getVoicemailNumber());
        mPhoneCallDetailsHelper = new PhoneCallDetailsHelper(this, mResources, mCallTypeHelper,
                mPhoneNumberHelper);
        mHomeActionView = findViewById(R.id.action_bar_home);
        mMainActionView = (ImageView) findViewById(R.id.main_action);
        mContactBackgroundView = (ImageView) findViewById(R.id.contact_background);
        mDefaultCountryIso = ContactsUtils.getCurrentCountryIso(this);
        mContactPhotoManager = ContactPhotoManager.getInstance(this);
        getListView().setOnItemClickListener(this);
        mHomeActionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // We want this to start the call log if this activity was not started from the
                // call log itself.
                CallDetailActivity.this.finish();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateData(getCallLogEntryUris());
    }

    /**
     * Returns the list of URIs to show.
     * <p>
     * There are two ways the URIs can be provided to the activity: as the data on the intent, or as
     * a list of ids in the call log added as an extra on the URI.
     * <p>
     * If both are available, the data on the intent takes precedence.
     */
    private Uri[] getCallLogEntryUris() {
        Uri uri = getIntent().getData();
        if (uri != null) {
            // If there is a data on the intent, it takes precedence over the extra.
            return new Uri[]{ uri };
        }
        long[] ids = getIntent().getLongArrayExtra(EXTRA_CALL_LOG_IDS);
        Uri[] uris = new Uri[ids.length];
        for (int index = 0; index < ids.length; ++index) {
            uris[index] = ContentUris.withAppendedId(Calls.CONTENT_URI_WITH_VOICEMAIL, ids[index]);
        }
        return uris;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                // Make sure phone isn't already busy before starting direct call
                TelephonyManager tm = (TelephonyManager)
                        getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                    Intent callIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts("tel", mNumber, null));
                    startActivity(callIntent);
                    return true;
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Update user interface with details of given call.
     *
     * @param callUris URIs into {@link CallLog.Calls} of the calls to be displayed
     */
    private void updateData(final Uri... callUris) {
        // TODO: All phone calls correspond to the same person, so we can make a single lookup.
        final int numCalls = callUris.length;
        final PhoneCallDetails[] details = new PhoneCallDetails[numCalls];
        try {
            for (int index = 0; index < numCalls; ++index) {
                details[index] = getPhoneCallDetailsForUri(callUris[index]);
            }
        } catch (IllegalArgumentException e) {
            // Something went wrong reading in our primary data, so we're going to
            // bail out and show error to users.
            Log.w(TAG, "invalid URI starting call details", e);
            Toast.makeText(this, R.string.toast_call_detail_error,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // We know that all calls are from the same number and the same contact, so pick the first.
        mNumber = details[0].number.toString();
        final long personId = details[0].personId;
        final Uri photoUri = details[0].photoUri;

        // Set the details header, based on the first phone call.
        mPhoneCallDetailsHelper.setPhoneCallDetails(mPhoneCallDetailsViews,
                details[0], false, false);

        // Cache the details about the phone number.
        final Uri numberCallUri = mPhoneNumberHelper.getCallUri(mNumber);
        final boolean canPlaceCallsTo = mPhoneNumberHelper.canPlaceCallsTo(mNumber);
        final boolean isVoicemailNumber = mPhoneNumberHelper.isVoicemailNumber(mNumber);
        final boolean isSipNumber = mPhoneNumberHelper.isSipNumber(mNumber);

        // Let user view contact details if they exist, otherwise add option to create new contact
        // from this number.
        final Intent mainActionIntent;
        final int mainActionIcon;

        if (details[0].personId != -1) {
            Uri personUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, personId);
            mainActionIntent = new Intent(Intent.ACTION_VIEW, personUri);
            mainActionIcon = R.drawable.sym_action_view_contact;
        } else if (isVoicemailNumber) {
            mainActionIntent = null;
            mainActionIcon = 0;
        } else if (isSipNumber) {
            // TODO: This item is currently disabled for SIP addresses, because
            // the Insert.PHONE extra only works correctly for PSTN numbers.
            //
            // To fix this for SIP addresses, we need to:
            // - define ContactsContract.Intents.Insert.SIP_ADDRESS, and use it here if
            //   the current number is a SIP address
            // - update the contacts UI code to handle Insert.SIP_ADDRESS by
            //   updating the SipAddress field
            // and then we can remove the "!isSipNumber" check above.
            mainActionIntent = null;
            mainActionIcon = 0;
        } else {
            mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            mainActionIntent.setType(Contacts.CONTENT_ITEM_TYPE);
            mainActionIntent.putExtra(Insert.PHONE, mNumber);
            mainActionIcon = R.drawable.sym_action_add;
        }

        if (mainActionIntent == null) {
            mMainActionView.setVisibility(View.INVISIBLE);
        } else {
            mMainActionView.setVisibility(View.VISIBLE);
            mMainActionView.setImageResource(mainActionIcon);
            mMainActionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(mainActionIntent);
                }
            });
        }

        // Build list of various available actions.
        final List<ViewEntry> actions = new ArrayList<ViewEntry>();

        // This action allows to call the number that places the call.
        if (canPlaceCallsTo) {
            actions.add(new ViewEntry(android.R.drawable.sym_action_call,
                    getString(R.string.menu_callNumber, mNumber),
                    new Intent(Intent.ACTION_CALL_PRIVILEGED, numberCallUri)));
        }

        // This action allows to send an SMS to the number that placed the call.
        if (mPhoneNumberHelper.canSendSmsTo(mNumber)) {
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO,
                    Uri.fromParts("sms", mNumber, null));
            actions.add(new ViewEntry(R.drawable.sym_action_sms,
                    getString(R.string.menu_sendTextMessage), smsIntent));
        }

        // This action deletes all elements in the group from the call log.
        actions.add(new ViewEntry(android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.recentCalls_removeFromRecentList),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        StringBuilder callIds = new StringBuilder();
                        for (Uri callUri : callUris) {
                            if (callIds.length() != 0) {
                                callIds.append(",");
                            }
                            callIds.append(ContentUris.parseId(callUri));
                        }

                        getContentResolver().delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                                Calls._ID + " IN (" + callIds + ")", null);
                        finish();
                    }
                }));

        if (canPlaceCallsTo && !isSipNumber && !isVoicemailNumber) {
            // "Edit the number before calling" is only available for PSTN numbers.
            actions.add(new ViewEntry(android.R.drawable.sym_action_call,
                    getString(R.string.recentCalls_editNumberBeforeCall),
                    new Intent(Intent.ACTION_DIAL, numberCallUri)));
        }

        // Set the actions for this phone number.
        setListAdapter(new ViewAdapter(this, actions));

        ListView historyList = (ListView) findViewById(R.id.history);
        historyList.setAdapter(
                new CallDetailHistoryAdapter(this, mInflater, mCallTypeHelper, details));
        loadContactPhotos(photoUri);
    }

    /** Return the phone call details for a given call log URI. */
    private PhoneCallDetails getPhoneCallDetailsForUri(Uri callUri) {
        ContentResolver resolver = getContentResolver();
        Cursor callCursor = resolver.query(callUri, CALL_LOG_PROJECTION, null, null, null);
        try {
            if (callCursor == null || !callCursor.moveToFirst()) {
                throw new IllegalArgumentException("Cannot find content: " + callUri);
            }

            // Read call log specifics.
            String number = callCursor.getString(NUMBER_COLUMN_INDEX);
            long date = callCursor.getLong(DATE_COLUMN_INDEX);
            long duration = callCursor.getLong(DURATION_COLUMN_INDEX);
            int callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
            String countryIso = callCursor.getString(COUNTRY_ISO_COLUMN_INDEX);
            if (TextUtils.isEmpty(countryIso)) {
                countryIso = mDefaultCountryIso;
            }

            // Formatted phone number.
            final CharSequence numberText;
            // Read contact specifics.
            CharSequence nameText = "";
            int numberType = 0;
            CharSequence numberLabel = "";
            long personId = -1L;
            Uri photoUri = null;
            // If this is not a regular number, there is no point in looking it up in the contacts.
            if (!mPhoneNumberHelper.canPlaceCallsTo(number)) {
                numberText = mPhoneNumberHelper.getDisplayNumber(number, null);
            } else {
                // Perform a reverse-phonebook lookup to find the contact details.
                Uri phoneUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(number));
                Cursor phonesCursor = resolver.query(phoneUri, PHONES_PROJECTION, null, null, null);
                String candidateNumberText = number;
                try {
                    if (phonesCursor != null && phonesCursor.moveToFirst()) {
                        personId = phonesCursor.getLong(COLUMN_INDEX_ID);
                        nameText = phonesCursor.getString(COLUMN_INDEX_NAME);
                        String photoUriString = phonesCursor.getString(COLUMN_INDEX_PHOTO_URI);
                        photoUri = photoUriString == null ? null : Uri.parse(photoUriString);
                        candidateNumberText = PhoneNumberUtils.formatNumber(
                                phonesCursor.getString(COLUMN_INDEX_NUMBER),
                                phonesCursor.getString(COLUMN_INDEX_NORMALIZED_NUMBER),
                                countryIso);
                        numberType = phonesCursor.getInt(COLUMN_INDEX_TYPE);
                        numberLabel = phonesCursor.getString(COLUMN_INDEX_LABEL);
                    } else {
                        // We could not find this contact in the contacts, just format the phone
                        // number as best as we can. All the other fields will have their default
                        // values.
                        candidateNumberText =
                                PhoneNumberUtils.formatNumber(number, countryIso);
                    }
                } finally {
                    if (phonesCursor != null) phonesCursor.close();
                    numberText = candidateNumberText;
                }
            }
            return new PhoneCallDetails(number, numberText, new int[]{ callType }, date, duration,
                    nameText, numberType, numberLabel, personId, photoUri);
        } finally {
            if (callCursor != null) {
                callCursor.close();
            }
        }
    }

    /** Load the contact photos and places them in the corresponding views. */
    private void loadContactPhotos(Uri photoUri) {
        mContactPhotoManager.loadPhoto(mContactBackgroundView, photoUri);
    }

    private String getVoicemailNumber() {
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getVoiceMailNumber();
    }

    static final class ViewEntry {
        public final int icon;
        public final String text;
        public final Intent intent;
        public final View.OnClickListener action;

        public String label = null;
        public String number = null;

        public ViewEntry(int icon, String text, Intent intent) {
            this.icon = icon;
            this.text = text;
            this.intent = intent;
            this.action = null;
        }

        public ViewEntry(int icon, String text, View.OnClickListener listener) {
            this.icon = icon;
            this.text = text;
            this.intent = null;
            this.action = listener;
        }
    }

    static final class ViewAdapter extends BaseAdapter {

        private final List<ViewEntry> mActions;

        private final LayoutInflater mInflater;

        public ViewAdapter(Context context, List<ViewEntry> actions) {
            mActions = actions;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mActions.size();
        }

        @Override
        public Object getItem(int position) {
            return mActions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Make sure we have a valid convertView to start with
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.call_detail_list_item, parent, false);
            }

            // Fill action with icon and text.
            ViewEntry entry = mActions.get(position);
            convertView.setTag(entry);

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            TextView text = (TextView) convertView.findViewById(android.R.id.text1);

            icon.setImageResource(entry.icon);
            text.setText(entry.text);

            View line2 = convertView.findViewById(R.id.line2);
            boolean numberEmpty = TextUtils.isEmpty(entry.number);
            boolean labelEmpty = TextUtils.isEmpty(entry.label) || numberEmpty;
            if (labelEmpty && numberEmpty) {
                line2.setVisibility(View.GONE);
            } else {
                line2.setVisibility(View.VISIBLE);

                TextView label = (TextView) convertView.findViewById(R.id.label);
                if (labelEmpty) {
                    label.setVisibility(View.GONE);
                } else {
                    label.setText(entry.label);
                    label.setVisibility(View.VISIBLE);
                }

                TextView number = (TextView) convertView.findViewById(R.id.number);
                number.setText(entry.number);
            }

            return convertView;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Handle passing action off to correct handler.
        if (view.getTag() instanceof ViewEntry) {
            ViewEntry entry = (ViewEntry) view.getTag();
            if (entry.intent != null) {
                startActivity(entry.intent);
            } else if (entry.action != null) {
                entry.action.onClick(view);
            }
        }
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }
}
