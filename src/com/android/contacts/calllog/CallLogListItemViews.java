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

import com.android.contacts.PhoneCallDetailsViews;
import com.android.contacts.R;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

/**
 * Simple value object containing the various views within a call log entry.
 */
public final class CallLogListItemViews {
    /** The quick contact badge for the contact. */
    public final QuickContactBadge quickContactView;
    /** The photo view without quick contact badge. */
    public final ImageView plainPhotoView;
    /** The main action button on the entry. */
    public final View callView;
    /** The play action button used for voicemail. */
    public final View playView;
    /** The icon used for unheard voicemail. */
    public final View unheardView;
    /** The divider between callView and playView. */
    public final View dividerView;
    /** The details of the phone call. */
    public final PhoneCallDetailsViews phoneCallDetailsViews;
    /** The item view for a stand-alone row, or null for other types of rows. */
    public final View listItemView;
    /** The text of the header in a stand-alone row, or null for other types of rows. */
    public final TextView listHeaderTextView;

    private CallLogListItemViews(QuickContactBadge quickContactView, ImageView photoView,
            View callView, View playView, View unheardView, View dividerView,
            PhoneCallDetailsViews phoneCallDetailsViews, View listItemView,
            TextView listHeaderTextView) {
        this.quickContactView = quickContactView;
        this.plainPhotoView = photoView;
        this.callView = callView;
        this.playView = playView;
        this.unheardView = unheardView;
        this.dividerView = dividerView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
        this.listItemView = listItemView;
        this.listHeaderTextView = listHeaderTextView;
    }

    public static CallLogListItemViews fromView(View view) {
        return new CallLogListItemViews(
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                (ImageView) view.findViewById(R.id.plain_contact_photo),
                view.findViewById(R.id.call_icon),
                view.findViewById(R.id.play_icon),
                view.findViewById(R.id.unheard_icon),
                view.findViewById(R.id.divider),
                PhoneCallDetailsViews.fromView(view),
                view.findViewById(R.id.call_log_item),
                (TextView) view.findViewById(R.id.call_log_header_text));
    }

    public static CallLogListItemViews createForTest(Context context) {
        return new CallLogListItemViews(
                new QuickContactBadge(context),
                new ImageView(context),
                new View(context),
                new View(context),
                new View(context),
                new View(context),
                PhoneCallDetailsViews.createForTest(context),
                new View(context),
                new TextView(context));
    }
}
