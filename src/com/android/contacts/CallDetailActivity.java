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
import com.android.contacts.voicemail.VoicemailPlaybackFragment;
import com.android.contacts.voicemail.VoicemailStatusHelper;
import com.android.contacts.voicemail.VoicemailStatusHelper.StatusMessage;
import com.android.contacts.voicemail.VoicemailStatusHelperImpl;

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.VoicemailContract.Voicemails;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
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
    public static final String EXTRA_CALL_LOG_IDS = "EXTRA_CALL_LOG_IDS";
    /** If we are started with a voicemail, we'll find the uri to play with this extra. */
    public static final String EXTRA_VOICEMAIL_URI = "EXTRA_VOICEMAIL_URI";
    /** If we should immediately start playback of the voicemail, this extra will be set to true. */
    public static final String EXTRA_VOICEMAIL_START_PLAYBACK = "EXTRA_VOICEMAIL_START_PLAYBACK";

    /** The views representing the details of a phone call. */
    private PhoneCallDetailsViews mPhoneCallDetailsViews;
    private CallTypeHelper mCallTypeHelper;
    private PhoneNumberHelper mPhoneNumberHelper;
    private PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    private ImageView mMainActionView;
    private ImageButton mMainActionPushLayerView;
    private ImageView mContactBackgroundView;

    private String mNumber = null;
    private String mDefaultCountryIso;

    /* package */ LayoutInflater mInflater;
    /* package */ Resources mResources;
    /** Helper to load contact photos. */
    private ContactPhotoManager mContactPhotoManager;
    /** Helper to make async queries to content resolver. */
    private CallDetailActivityQueryHandler mAsyncQueryHandler;
    /** Helper to get voicemail status messages. */
    private VoicemailStatusHelper mVoicemailStatusHelper;
    // Views related to voicemail status message.
    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;

    /** Whether we should show "edit number before call" in the options menu. */
    private boolean mHasEditNumberBeforeCall;

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
        mCallTypeHelper = new CallTypeHelper(getResources());
        mPhoneNumberHelper = new PhoneNumberHelper(mResources, getVoicemailNumber());
        mPhoneCallDetailsHelper = new PhoneCallDetailsHelper(mResources, mCallTypeHelper,
                mPhoneNumberHelper);
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mAsyncQueryHandler = new CallDetailActivityQueryHandler(this);
        mStatusMessageView = findViewById(R.id.voicemail_status);
        mStatusMessageText = (TextView) findViewById(R.id.voicemail_status_message);
        mStatusMessageAction = (TextView) findViewById(R.id.voicemail_status_action);
        mMainActionView = (ImageView) findViewById(R.id.main_action);
        mMainActionPushLayerView = (ImageButton) findViewById(R.id.main_action_push_layer);
        mContactBackgroundView = (ImageView) findViewById(R.id.contact_background);
        mDefaultCountryIso = ContactsUtils.getCurrentCountryIso(this);
        mContactPhotoManager = ContactPhotoManager.getInstance(this);
        getListView().setOnItemClickListener(this);
        configureActionBar();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateData(getCallLogEntryUris());
        optionallyHandleVoicemail();
    }

    /**
     * Handle voicemail playback or hide voicemail ui.
     * <p>
     * If the Intent used to start this Activity contains the suitable extras, then start voicemail
     * playback.  If it doesn't, then hide the voicemail ui.
     */
    private void optionallyHandleVoicemail() {
        if (hasVoicemail()) {
            // Has voicemail: add the voicemail fragment.  Add suitable arguments to set the uri
            // to play and optionally start the playback.
            // Do a query to fetch the voicemail status messages.
            VoicemailPlaybackFragment playbackFragment = new VoicemailPlaybackFragment();
            Bundle fragmentArguments = new Bundle();
            fragmentArguments.putParcelable(EXTRA_VOICEMAIL_URI, getVoicemailUri());
            if (getIntent().getBooleanExtra(EXTRA_VOICEMAIL_START_PLAYBACK, false)) {
                fragmentArguments.putBoolean(EXTRA_VOICEMAIL_START_PLAYBACK, true);
            }
            playbackFragment.setArguments(fragmentArguments);
            getFragmentManager().beginTransaction()
                    .add(R.id.voicemail_container, playbackFragment).commit();
            mAsyncQueryHandler.startVoicemailStatusQuery(getVoicemailUri());
            markVoicemailAsRead(getVoicemailUri());
        } else {
            // No voicemail uri: hide the status view.
            mStatusMessageView.setVisibility(View.GONE);
        }
    }

    private boolean hasVoicemail() {
        return getVoicemailUri() != null;
    }

    private Uri getVoicemailUri() {
        return getIntent().getParcelableExtra(EXTRA_VOICEMAIL_URI);
    }

    private void markVoicemailAsRead(final Uri voicemailUri) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ContentValues values = new ContentValues();
                values.put(Voicemails.IS_READ, true);
                getContentResolver().update(voicemailUri, values, null, null);
                return null;
            }
        }.execute();
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
                details[0], false, false, true);

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
            mainActionIcon = R.drawable.ic_contacts_holo_dark;
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
        } else if (canPlaceCallsTo) {
            mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            mainActionIntent.setType(Contacts.CONTENT_ITEM_TYPE);
            mainActionIntent.putExtra(Insert.PHONE, mNumber);
            mainActionIcon = R.drawable.ic_add_contact_holo_dark;
        } else {
            // If we cannot call the number, when we probably cannot add it as a contact either.
            // This is usually the case of private, unknown, or payphone numbers.
            mainActionIntent = null;
            mainActionIcon = 0;
        }

        if (mainActionIntent == null) {
            mMainActionView.setVisibility(View.INVISIBLE);
            mMainActionPushLayerView.setVisibility(View.GONE);
        } else {
            mMainActionView.setVisibility(View.VISIBLE);
            mMainActionView.setImageResource(mainActionIcon);
            mMainActionPushLayerView.setVisibility(View.VISIBLE);
            mMainActionPushLayerView.setOnClickListener(new View.OnClickListener() {
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

        mHasEditNumberBeforeCall = canPlaceCallsTo && !isSipNumber && !isVoicemailNumber;

        if (actions.size() != 0) {
            // Set the actions for this phone number.
            setListAdapter(new ViewAdapter(this, actions));
            getListView().setVisibility(View.VISIBLE);
        } else {
            getListView().setVisibility(View.GONE);
        }

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
            return new PhoneCallDetails(number, numberText, countryIso,
                    new int[]{ callType }, date, duration,
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

    protected void updateVoicemailStatusMessage(Cursor statusCursor) {
        if (statusCursor == null) {
            mStatusMessageView.setVisibility(View.GONE);
            return;
        }
        final StatusMessage message = getStatusMessage(statusCursor);
        if (message == null || !message.showInCallDetails()) {
            mStatusMessageView.setVisibility(View.GONE);
            return;
        }

        mStatusMessageView.setVisibility(View.VISIBLE);
        mStatusMessageText.setText(message.callDetailsMessageId);
        if (message.actionMessageId != -1) {
            mStatusMessageAction.setText(message.actionMessageId);
        }
        if (message.actionUri != null) {
            mStatusMessageAction.setClickable(true);
            mStatusMessageAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW, message.actionUri));
                }
            });
        } else {
            mStatusMessageAction.setClickable(false);
        }
    }

    private StatusMessage getStatusMessage(Cursor statusCursor) {
        List<StatusMessage> messages = mVoicemailStatusHelper.getStatusMessages(statusCursor);
        Log.d(TAG, "Num status messages: " + messages.size());
        if (messages.size() == 0) {
            return null;
        }
        // There can only be a single status message per source package, so num of messages can
        // at most be 1.
        if (messages.size() > 1) {
            Log.w(TAG, String.format("Expected 1, found (%d) num of status messages." +
                    " Will use the first one.", messages.size()));
        }
        return messages.get(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_details_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // This action deletes all elements in the group from the call log.
        // We don't have this action for voicemails, because you can just use the trash button.
        menu.findItem(R.id.menu_remove_from_call_log).setVisible(!hasVoicemail());
        menu.findItem(R.id.menu_edit_number_before_call).setVisible(mHasEditNumberBeforeCall);
        menu.findItem(R.id.menu_trash).setVisible(hasVoicemail());
        menu.findItem(R.id.menu_share_voicemail).setVisible(hasVoicemail());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onHomeSelected();
                return true;
            }

            // All the options menu items are handled by onMenu... methods.
            default:
                throw new IllegalArgumentException();
        }
    }

    public void onMenuRemoveFromCallLog(MenuItem menuItem) {
        StringBuilder callIds = new StringBuilder();
        for (Uri callUri : getCallLogEntryUris()) {
            if (callIds.length() != 0) {
                callIds.append(",");
            }
            callIds.append(ContentUris.parseId(callUri));
        }

        getContentResolver().delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                Calls._ID + " IN (" + callIds + ")", null);
        // Also close the activity.
        finish();
    }

    public void onMenuEditNumberBeforeCall(MenuItem menuItem) {
        startActivity(new Intent(Intent.ACTION_DIAL, mPhoneNumberHelper.getCallUri(mNumber)));
    }

    public void onMenuShareVoicemail(MenuItem menuItem) {
        Log.w(TAG, "onMenuShareVoicemail not yet implemented");
    }

    public void onMenuTrashVoicemail(MenuItem menuItem) {
        getContentResolver().delete(getVoicemailUri(), null, null);
        finish();
    }

    private void configureActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
        }
    }

    /** Invoked when the user presses the home button in the action bar. */
    private void onHomeSelected() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Calls.CONTENT_URI);
        // This will open the call log even if the detail view has been opened directly.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
