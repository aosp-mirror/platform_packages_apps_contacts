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
import android.widget.TextView;

/**
 * Encapsulates the views that are used to display the details of a phone call in the call log.
 */
public final class PhoneCallDetailsViews {
    public final TextView mNameView;
    public final TextView mCallTypeAndDateView;
    public final TextView mNumberView;

    private PhoneCallDetailsViews(TextView nameView, TextView callTypeAndDateView,
            TextView numberView) {
        mNameView = nameView;
        mCallTypeAndDateView = callTypeAndDateView;
        mNumberView = numberView;
    }

    /**
     * Create a new instance by extracting the elements from the given view.
     * <p>
     * The view should contain three text views with identifiers {@code R.id.name},
     * {@code R.id.call_type}, and {@code R.id.number}.
     */
    public static PhoneCallDetailsViews fromView(View view) {
        return new PhoneCallDetailsViews((TextView) view.findViewById(R.id.name),
                (TextView) view.findViewById(R.id.call_type),
                (TextView) view.findViewById(R.id.number));
    }

    public static PhoneCallDetailsViews createForTest(TextView nameView,
            TextView callTypeAndDateView, TextView numberView) {
        return new PhoneCallDetailsViews(nameView, callTypeAndDateView, numberView);
    }
}
