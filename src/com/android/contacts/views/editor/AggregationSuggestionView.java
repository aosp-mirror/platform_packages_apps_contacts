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

package com.android.contacts.views.editor;

import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.views.editor.AggregationSuggestionEngine.RawContact;
import com.android.contacts.views.editor.AggregationSuggestionEngine.Suggestion;
import com.google.android.collect.Lists;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A view that contains a name, picture and other data for a contact aggregation suggestion.
 */
public class AggregationSuggestionView extends RelativeLayout implements OnClickListener {

    public interface Listener {

        /**
         * Callback that passes the contact ID to join with and, for convenience,
         * also the list of constituent raw contact IDs to avoid a separate query
         * for those.
         */
        public void onJoinAction(long contactId, List<Long> rawContacIds);

        /**
         * Callback that passes the contact ID to edit instead of the current contact.
         */
        public void onEditAction(Uri contactLookupUri);
    }

    private Listener mListener;
    private long mContactId;
    private String mLookupKey;
    private List<RawContact> mRawContacts = Lists.newArrayList();
    private boolean mNewContact;

    public AggregationSuggestionView(Context context) {
        super(context);
    }

    public AggregationSuggestionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AggregationSuggestionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setNewContact(boolean flag) {
        mNewContact = flag;
    }

    public void bindSuggestion(Suggestion suggestion) {
        mContactId = suggestion.contactId;
        mLookupKey = suggestion.lookupKey;
        mRawContacts = suggestion.rawContacts;
        ImageView photo = (ImageView) findViewById(R.id.aggregation_suggestion_photo);
        if (suggestion.photo != null) {
            photo.setImageBitmap(BitmapFactory.decodeByteArray(
                    suggestion.photo, 0, suggestion.photo.length));
        } else {
            photo.setImageResource(R.drawable.ic_contact_picture_2);
        }

        TextView name = (TextView) findViewById(R.id.aggregation_suggestion_name);
        name.setText(suggestion.name);

        TextView data = (TextView) findViewById(R.id.aggregation_suggestion_data);
        String dataText = null;
        if (suggestion.nickname != null) {
            dataText = suggestion.nickname;
        } else if (suggestion.emailAddress != null) {
            dataText = suggestion.emailAddress;
        } else if (suggestion.phoneNumber != null) {
            dataText = suggestion.phoneNumber;
        }
        data.setText(dataText);

        boolean canEdit = canEditSuggestedContact();
        Button join = (Button) findViewById(R.id.aggregation_suggestion_join_button);
        join.setOnClickListener(this);
        join.setVisibility(canEdit ? View.GONE : View.VISIBLE);

        Button edit = (Button) findViewById(R.id.aggregation_suggestion_edit_button);
        edit.setOnClickListener(this);
        edit.setVisibility(canEdit ? View.VISIBLE : View.GONE);
    }

    /**
     * Returns true if the suggested contact can be edited.
     */
    private boolean canEditSuggestedContact() {
        if (!mNewContact) {
            return false;
        }

        Sources sources = Sources.getInstance(getContext());
        for (RawContact rawContact : mRawContacts) {
            String accountType = rawContact.accountType;
            if (accountType == null) {
                return true;
            }
            ContactsSource source = sources.getInflatedSource(
                    accountType, ContactsSource.LEVEL_SUMMARY);
            if (!source.readOnly) {
                return true;
            }
        }

        return false;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            if (v.getId() == R.id.aggregation_suggestion_join_button) {
                ArrayList<Long> rawContactIds = Lists.newArrayList();
                for (RawContact rawContact : mRawContacts) {
                    rawContactIds.add(rawContact.rawContactId);
                }
                mListener.onJoinAction(mContactId, rawContactIds);
            } else {
                mListener.onEditAction(Contacts.getLookupUri(mContactId, mLookupKey));
            }
        }
    }
}
