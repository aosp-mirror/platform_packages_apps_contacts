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
import android.provider.Contacts;
import android.provider.Contacts.Intents.Insert;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;

/**
 * Displays the details of a specific call log entry.
 */
public class CallDetailActivity extends ListActivity implements
        AdapterView.OnItemClickListener {
    private static final String TAG = "CallDetail";

    private Uri mUri;
    
    private View mCallDetailItem;
    
    private TextView mCallType;
    private ImageView mCallTypeIcon;
    private TextView mCallTime;
    private View mCallDurationRow;
    private TextView mCallDuration;

    private String mNumber = null;
    
    /* package */ LayoutInflater mInflater;
    /* package */ Resources mResources;
    
    static final String[] CALL_LOG_PROJECTION = new String[] {
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
    };
    
    static final int DATE_COLUMN_INDEX = 0;
    static final int DURATION_COLUMN_INDEX = 1;
    static final int NUMBER_COLUMN_INDEX = 2;
    static final int CALL_TYPE_COLUMN_INDEX = 3;
    
    static final String[] PHONES_PROJECTION = new String[] {
        Phones.PERSON_ID,
    };

    static final int PERSON_ID_COLUMN_INDEX = 0;
    
    private static final int INVALID_TYPE = -1;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.call_detail);

        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();
        
        mCallDetailItem = mInflater.inflate(R.layout.call_detail_item, getListView(), false);
        
        mCallType = (TextView) mCallDetailItem.findViewById(R.id.call_type);
        mCallTypeIcon = (ImageView) mCallDetailItem.findViewById(R.id.call_type_icon);
        mCallTime = (TextView) mCallDetailItem.findViewById(R.id.call_time);
        mCallDurationRow = mCallDetailItem.findViewById(R.id.call_duration_row);
        mCallDuration = (TextView) mCallDetailItem.findViewById(R.id.call_duration);
        
        getListView().setOnItemClickListener(this);
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
     * Try a reverse-phonebook lookup to find the contact, if any, behind the given number.
     * 
     * @param number Phone number to perform reverse-lookup against
     * @return Uri into {@link Contacts.People} if found, otherwise null
     */ 
    private Uri getPersonUri(String number) {
        Uri personUri = null;
        
        // Perform a reverse-phonebook lookup to find the PERSON_ID
        ContentResolver resolver = getContentResolver();
        Uri phoneUri = Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, Uri.encode(number));
        Cursor phonesCursor = resolver.query(phoneUri, PHONES_PROJECTION, null, null, null);
        try {
            if (phonesCursor != null && phonesCursor.moveToFirst()) {
                long personId = phonesCursor.getLong(PERSON_ID_COLUMN_INDEX);
                personUri = ContentUris.withAppendedId(Contacts.People.CONTENT_URI, personId);
            }
        } finally {
            if (phonesCursor != null) {
                phonesCursor.close();
            }
        }
        
        return personUri;
    }
    
    /**
     * Update user interface with details of given call.
     * 
     * @param callUri Uri into {@link CallLog.Calls}
     */
    private void updateData(Uri callUri) {
        ContentResolver resolver = getContentResolver();
        Cursor callCursor = resolver.query(callUri, CALL_LOG_PROJECTION, null, null, null);
        try {
            if (callCursor != null && callCursor.moveToFirst()) {
                // Read call log specifics
                mNumber = callCursor.getString(NUMBER_COLUMN_INDEX);
                long date = callCursor.getLong(DATE_COLUMN_INDEX);
                long duration = callCursor.getLong(DURATION_COLUMN_INDEX);
                int callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
                
                // Pull out string in format [relative], [date]
                CharSequence dateClause = DateUtils.formatDateRange(this, date, date,
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                        DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR |
                        DateUtils.FORMAT_ABBREV_ALL);
                long now = System.currentTimeMillis();
                CharSequence relativeClause = DateUtils.getRelativeTimeSpanString(date, now,
                        DateUtils.SECOND_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
                String dateString = getString(R.string.datetime_relative,
                        dateClause, relativeClause);
                mCallTime.setText(dateString);
                
                // Set the duration
                if (callType == Calls.MISSED_TYPE) {
                    mCallDurationRow.setVisibility(View.GONE);
                } else {
                    mCallDurationRow.setVisibility(View.VISIBLE);
                    mCallDuration.setText(DateUtils.formatElapsedTime(duration));
                }
    
                // Set the call type icon and caption
                switch (callType) {
                    case Calls.INCOMING_TYPE:
                        mCallTypeIcon.setImageResource(android.R.drawable.sym_call_incoming);
                        mCallType.setText(R.string.type_incoming);
                        break;
    
                    case Calls.OUTGOING_TYPE:
                        mCallTypeIcon.setImageResource(android.R.drawable.sym_call_outgoing);
                        mCallType.setText(R.string.type_outgoing);
                        break;
    
                    case Calls.MISSED_TYPE:
                        mCallTypeIcon.setImageResource(android.R.drawable.sym_call_missed);
                        mCallType.setText(R.string.type_missed);
                        break;
                }
    
                // Build list of various available actions
                List<ViewEntry> actions = new LinkedList<ViewEntry>();
                
                Intent callIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                        Uri.fromParts("tel", mNumber, null));
                actions.add(new ViewEntry(R.drawable.ic_dialer_fork_current_call,
                        getString(R.string.recentCalls_callNumber, mNumber), callIntent));
    
                Intent smsIntent = new Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts("sms", mNumber, null));
                actions.add(new ViewEntry(R.drawable.sym_action_sms,
                        getString(R.string.menu_sendTextMessage), smsIntent));
    
                // Let user view contact details if they exist, otherwise add option
                // to create new contact from this number.
                Uri personUri = getPersonUri(mNumber);
                
                if (personUri != null) {
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW, personUri);
                    actions.add(new ViewEntry(R.drawable.ic_tab_unselected_contacts,
                            getString(R.string.menu_viewContact), viewIntent));
                } else {
                    Intent createIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                    createIntent.setType(People.CONTENT_ITEM_TYPE);
                    createIntent.putExtra(Insert.PHONE, mNumber);
                    actions.add(new ViewEntry(R.drawable.ic_dialer_fork_add_call,
                            getString(R.string.recentCalls_addToContact), createIntent));
                }
                
                ViewAdapter adapter = new ViewAdapter(this, mCallDetailItem, actions);
                setListAdapter(adapter);
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

    static final class ViewEntry {
        public int icon = -1;
        public String text = null;
        public Intent intent = null;
        
        public ViewEntry(int icon, String text, Intent intent) {
            this.icon = icon;
            this.text = text;
            this.intent = intent;
        }
    }

    static final class ViewAdapter extends BaseAdapter {
        
        private final View mCallDetailItem;
        private final List<ViewEntry> mActions;
        
        private final Context mContext;
        private final LayoutInflater mInflater;
        
        public ViewAdapter(Context context, View callDetailItem, List<ViewEntry> actions) {
            mCallDetailItem = callDetailItem;
            mActions = actions;
            
            mContext = context;
            mInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        }
        
        public int getCount() {
            // Count is actions plus two headers and call details panel.
            return mActions.size() + 2;
        }

        public Object getItem(int position) {
            if (position >= POS_FIRST_ITEM) {
                return mActions.get(position - POS_FIRST_ITEM);
            }
            return null;
        }

        public long getItemId(int position) {
            return position;
        }
        
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_CALL_DETAILS = 1;
        private static final int TYPE_ACTION = 2;
        
        private static final int POS_CALL_DETAILS = 0;
        private static final int POS_ACTIONS_HEADER = 1;
        private static final int POS_FIRST_ITEM = 2;

        public int getViewTypeCount() {
            // Types are headers, call details panel, and actions.
            return 3;
        }
        
        public int getItemViewType(int position) {
            switch(position) {
                case POS_CALL_DETAILS:
                    return TYPE_CALL_DETAILS;
                case POS_ACTIONS_HEADER:
                    return TYPE_HEADER;
                default:
                    return TYPE_ACTION;
            }
        }
        
        public boolean areAllItemsEnabled() {
            return false;
        }
        
        public boolean isEnabled(int position) {
            return (position > POS_ACTIONS_HEADER);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            // Make sure we have a valid convertView to start with
            if (convertView == null) {
                switch(getItemViewType(position)) {
                    case TYPE_HEADER: {
                        convertView = mInflater.inflate(R.layout.list_separator, parent, false);
                        break;
                    }
                    case TYPE_CALL_DETAILS: {
                        convertView = mCallDetailItem;
                        break;
                    }
                    case TYPE_ACTION: {
                        convertView = mInflater.inflate(R.layout.dialpad_chooser_list_item,
                                parent, false);
                        break;
                    }
                }
            }

            // Now fill our known-good convertView with data
            switch(position) {
                case POS_CALL_DETAILS: {
                    // Assume mCallDetailItem is already filled with correct data.
                    break;
                }
                case POS_ACTIONS_HEADER: {
                    TextView textView = (TextView) convertView;
                    textView.setText(mContext.getResources().getString(
                            R.string.header_actions));
                    break;
                }
                default: {
                    // Fill action with icon and text.
                    ViewEntry entry = (ViewEntry) getItem(position);
                    convertView.setTag(entry);
                    
                    ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
                    TextView text = (TextView) convertView.findViewById(R.id.text);
                    
                    icon.setImageResource(entry.icon);
                    text.setText(entry.text);
                    
                    break;
                }
            }
            
            return convertView;
        }

    }
    
    public void onItemClick(AdapterView parent, View view, int position, long id) {
        // Handle passing action off to correct handler.
        if (view.getTag() instanceof ViewEntry) {
            ViewEntry entry = (ViewEntry) view.getTag();
            if (entry.intent != null) {
                startActivity(entry.intent);
            }
        }
    }    
}
