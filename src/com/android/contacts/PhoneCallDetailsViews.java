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

package com.android.contacts;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Encapsulates the views that are used to display the details of a phone call in the call log.
 */
public final class PhoneCallDetailsViews {
    public final TextView nameView;
    public final LinearLayout callTypeIcons;
    public final TextView callTypeText;
    public final View callTypeSeparator;
    public final TextView dateView;
    public final TextView numberView;

    private PhoneCallDetailsViews(TextView nameView, LinearLayout callTypeIcons,
            TextView callTypeText, View callTypeSeparator, TextView dateView, TextView numberView) {
        this.nameView = nameView;
        this.callTypeIcons = callTypeIcons;
        this.callTypeText = callTypeText;
        this.callTypeSeparator = callTypeSeparator;
        this.dateView = dateView;
        this.numberView = numberView;
    }

    /**
     * Create a new instance by extracting the elements from the given view.
     * <p>
     * The view should contain three text views with identifiers {@code R.id.name},
     * {@code R.id.date}, and {@code R.id.number}, and a linear layout with identifier
     * {@code R.id.call_types}.
     */
    public static PhoneCallDetailsViews fromView(View view) {
        return new PhoneCallDetailsViews((TextView) view.findViewById(R.id.name),
                (LinearLayout) view.findViewById(R.id.call_type_icons),
                (TextView) view.findViewById(R.id.call_type_name),
                view.findViewById(R.id.call_type_separator),
                (TextView) view.findViewById(R.id.date),
                (TextView) view.findViewById(R.id.number));
    }

    public static PhoneCallDetailsViews createForTest(TextView nameView,
            LinearLayout callTypeIcons, TextView callTypeText, View callTypeSeparator,
            TextView dateView, TextView numberView) {
        return new PhoneCallDetailsViews(nameView, callTypeIcons, callTypeText, callTypeSeparator,
                dateView, numberView);
    }
}
