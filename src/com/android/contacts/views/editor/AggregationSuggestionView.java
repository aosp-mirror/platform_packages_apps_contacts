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
import com.android.contacts.views.editor.AggregationSuggestionEngine.Suggestion;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

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
    }

    private Listener mListener;
    private long mContactId;
    private List<Long> mRawContactIds;

    public AggregationSuggestionView(Context context) {
        super(context);
    }

    public AggregationSuggestionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AggregationSuggestionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bindSuggestion(Suggestion suggestion) {
        mContactId = suggestion.contactId;
        mRawContactIds = suggestion.rawContactIds;
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

        Button join = (Button) findViewById(R.id.aggregation_suggestion_join_button);
        join.setOnClickListener(this);
        join.setVisibility(View.VISIBLE);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            mListener.onJoinAction(mContactId, mRawContactIds);
        }
    }
}
