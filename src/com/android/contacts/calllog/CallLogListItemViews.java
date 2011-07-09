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

import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;

/**
 * Simple value object containing the various views within a call log entry.
 */
public final class CallLogListItemViews {
    /** The quick contact badge for the contact. Only present for group and stand alone entries. */
    public final QuickContactBadge photoView;
    /** The main action button on the entry. */
    public final ImageView callView;
    /** The details of the phone call. */
    public final PhoneCallDetailsViews phoneCallDetailsViews;

    private CallLogListItemViews(QuickContactBadge photoView, ImageView callView,
            PhoneCallDetailsViews phoneCallDetailsViews) {
        this.photoView = photoView;
        this.callView = callView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
    }

    public static CallLogListItemViews fromView(View view) {
        return new CallLogListItemViews((QuickContactBadge) view.findViewById(R.id.contact_photo),
                (ImageView) view.findViewById(R.id.call_icon),
                PhoneCallDetailsViews.fromView(view));
    }

    public static CallLogListItemViews createForTest(QuickContactBadge photoView,
            ImageView callView, PhoneCallDetailsViews phoneCallDetailsViews) {
        return new CallLogListItemViews(photoView, callView, phoneCallDetailsViews);
    }
}
