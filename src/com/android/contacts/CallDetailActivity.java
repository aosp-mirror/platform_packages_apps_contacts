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

import com.android.internal.telephony.CallerInfo;

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
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the details of a specific call log entry.
 */
public class CallDetailActivity extends ListActivity implements
        AdapterView.OnItemClickListener {
    private static final String TAG = "CallDetail";

    /** The views representing the details of a phone call. */
    private PhoneCallDetailsViews mPhoneCallDetailsViews;
    private PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    private TextView mCallTimeView;
    private TextView mCallDurationView;
    private View mHomeActionView;
    private ImageView mMainActionView;
    private ImageView mContactBackgroundView;

    private String mNumber = null;
    private String mDefaultCountryIso;

    /* package */ LayoutInflater mInflater;
    /* package */ Resources mResources;
    /** Helper to load contact photos. */
    private ContactPhotoManager mContactPhotoManager;
    /** Attached to the call action button in the UI. */

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
        PhoneLookup.PHOTO_ID,
    };
    static final int COLUMN_INDEX_ID = 0;
    static final int COLUMN_INDEX_NAME = 1;
    static final int COLUMN_INDEX_TYPE = 2;
    static final int COLUMN_INDEX_LABEL = 3;
    static final int COLUMN_INDEX_NUMBER = 4;
    static final int COLUMN_INDEX_NORMALIZED_NUMBER = 5;
    static final int COLUMN_INDEX_PHOTO_ID = 6;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.call_detail);

        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();

        mPhoneCallDetailsViews = PhoneCallDetailsViews.fromView(getWindow().getDecorView());
        mPhoneCallDetailsHelper = new PhoneCallDetailsHelper(this, getResources(),
                getVoicemailNumber(),
                getResources().getDrawable(R.drawable.ic_call_log_list_incoming_call),
                getResources().getDrawable(R.drawable.ic_call_log_list_outgoing_call),
                getResources().getDrawable(R.drawable.ic_call_log_list_missed_call),
                getResources().getDrawable(R.drawable.ic_call_log_list_voicemail));
        mHomeActionView = findViewById(R.id.action_bar_home);
        mMainActionView = (ImageView) findViewById(R.id.main_action);
        mContactBackgroundView = (ImageView) findViewById(R.id.contact_background);
        mCallTimeView = (TextView) findViewById(R.id.time);
        mCallDurationView = (TextView) findViewById(R.id.duration);
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
        updateData(getIntent().getData());
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
     * @param callUri Uri into {@link CallLog.Calls}
     */
    private void updateData(final Uri callUri) {
        ContentResolver resolver = getContentResolver();
        Cursor callCursor = resolver.query(callUri, CALL_LOG_PROJECTION, null, null, null);
        try {
            if (callCursor != null && callCursor.moveToFirst()) {
                // Read call log specifics
                mNumber = callCursor.getString(NUMBER_COLUMN_INDEX);
                long date = callCursor.getLong(DATE_COLUMN_INDEX);
                long duration = callCursor.getLong(DURATION_COLUMN_INDEX);
                int callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
                String countryIso = callCursor.getString(COUNTRY_ISO_COLUMN_INDEX);
                if (TextUtils.isEmpty(countryIso)) {
                    countryIso = mDefaultCountryIso;
                }
                // Pull out string in format [relative], [date]
                CharSequence dateClause = DateUtils.formatDateRange(this, date, date,
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                        DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR);
                mCallTimeView.setText(dateClause);

                // Set the duration
                if (callType == Calls.MISSED_TYPE) {
                    mCallDurationView.setVisibility(View.GONE);
                } else {
                    mCallDurationView.setVisibility(View.VISIBLE);
                    mCallDurationView.setText(formatDuration(duration));
                }

                long photoId = 0L;
                CharSequence nameText = "";
                final CharSequence numberText;
                int numberType = 0;
                CharSequence numberLabel = "";
                if (mNumber.equals(CallerInfo.UNKNOWN_NUMBER) ||
                        mNumber.equals(CallerInfo.PRIVATE_NUMBER)) {
                    numberText = getString(mNumber.equals(CallerInfo.PRIVATE_NUMBER)
                            ? R.string.private_num : R.string.unknown);
                    mMainActionView.setVisibility(View.GONE);
                } else {
                    // Perform a reverse-phonebook lookup to find the PERSON_ID
                    Uri personUri = null;
                    Uri phoneUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                            Uri.encode(mNumber));
                    Cursor phonesCursor = resolver.query(
                            phoneUri, PHONES_PROJECTION, null, null, null);
                    String candidateNumberText = mNumber;
                    try {
                        if (phonesCursor != null && phonesCursor.moveToFirst()) {
                            long personId = phonesCursor.getLong(COLUMN_INDEX_ID);
                            personUri = ContentUris.withAppendedId(
                                    Contacts.CONTENT_URI, personId);
                            nameText = phonesCursor.getString(COLUMN_INDEX_NAME);
                            photoId = phonesCursor.getLong(COLUMN_INDEX_PHOTO_ID);
                            candidateNumberText = PhoneNumberUtils.formatNumber(
                                    phonesCursor.getString(COLUMN_INDEX_NUMBER),
                                    phonesCursor.getString(COLUMN_INDEX_NORMALIZED_NUMBER),
                                    countryIso);
                            numberType = phonesCursor.getInt(COLUMN_INDEX_TYPE);
                            numberLabel = phonesCursor.getString(COLUMN_INDEX_LABEL);
                        } else {
                            candidateNumberText =
                                    PhoneNumberUtils.formatNumber(mNumber, countryIso);
                        }
                    } finally {
                        if (phonesCursor != null) phonesCursor.close();
                        numberText = candidateNumberText;
                    }

                    // Let user view contact details if they exist, otherwise add option
                    // to create new contact from this number.
                    final Intent mainActionIntent;
                    final int mainActionIcon;
                    if (personUri != null) {
                        mainActionIntent = new Intent(Intent.ACTION_VIEW, personUri);
                        mainActionIcon = R.drawable.sym_action_view_contact;
                    } else {
                        mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                        mainActionIntent.setType(Contacts.CONTENT_ITEM_TYPE);
                        mainActionIntent.putExtra(Insert.PHONE, mNumber);
                        mainActionIcon = R.drawable.sym_action_add;
                    }

                    mMainActionView.setVisibility(View.VISIBLE);
                    mMainActionView.setImageResource(mainActionIcon);
                    mMainActionView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(mainActionIntent);
                        }
                    });

                    // Build list of various available actions
                    List<ViewEntry> actions = new ArrayList<ViewEntry>();

                    final boolean isSipNumber = PhoneNumberUtils.isUriNumber(mNumber);
                    final Uri numberCallUri;
                    if (isSipNumber) {
                        numberCallUri = Uri.fromParts("sip", mNumber, null);
                    } else {
                        numberCallUri = Uri.fromParts("tel", mNumber, null);
                    }

                    actions.add(new ViewEntry(android.R.drawable.sym_action_call,
                            getString(R.string.menu_callNumber, mNumber),
                            new Intent(Intent.ACTION_CALL_PRIVILEGED, numberCallUri)));

                    if (!isSipNumber) {
                        Intent smsIntent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts("sms", mNumber, null));
                        actions.add(new ViewEntry(R.drawable.sym_action_sms,
                                getString(R.string.menu_sendTextMessage), smsIntent));
                    }

                    actions.add(new ViewEntry(android.R.drawable.ic_menu_close_clear_cancel,
                            getString(R.string.recentCalls_removeFromRecentList),
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    long id = ContentUris.parseId(callUri);
                                    getContentResolver().delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                                            Calls._ID + " = ?", new String[]{Long.toString(id)});
                                    finish();
                                }
                            }));

                    if (!isSipNumber) {
                        actions.add(new ViewEntry(android.R.drawable.sym_action_call,
                                getString(R.string.recentCalls_editNumberBeforeCall),
                                new Intent(Intent.ACTION_DIAL, numberCallUri)));
                    }

                    ViewAdapter adapter = new ViewAdapter(this, actions);
                    setListAdapter(adapter);
                }
                mPhoneCallDetailsHelper.setPhoneCallDetails(mPhoneCallDetailsViews,
                        new PhoneCallDetails(mNumber, numberText, new int[]{ callType }, date,
                                nameText, numberType, numberLabel), false);

                loadContactPhotos(photoId);
            } else {
                // Something went wrong reading in our primary data, so we're going to
                // bail out and show error to users.
                Toast.makeText(this, R.string.toast_call_detail_error,
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        } finally {
            if (callCursor != null) {
                callCursor.close();
            }
        }
    }

    /** Load the contact photos and places them in the corresponding views. */
    private void loadContactPhotos(final long photoId) {
        mContactPhotoManager.loadPhoto(mContactBackgroundView, photoId);
    }

    private String formatDuration(long elapsedSeconds) {
        long minutes = 0;
        long seconds = 0;

        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
        }
        seconds = elapsedSeconds;

        return getString(R.string.callDetailsDurationFormat, minutes, seconds);
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
