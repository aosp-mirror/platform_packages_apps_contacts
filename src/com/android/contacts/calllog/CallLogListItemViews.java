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

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Simple value object containing the various views within a call log entry.
 */
public final class CallLogListItemViews {
    /** The first line in the call log entry, containing either the name or the number. */
    public TextView line1View;
    /** The label associated with the phone number. */
    public TextView labelView;
    /**
     * The number the call was from or to.
     * <p>
     * Only filled in if the number is not already in the first line, i.e., {@link #line1View}.
     */
    public TextView numberView;
    /** The date of the call. */
    public TextView dateView;
    /** The icon indicating the type of call. */
    public ImageView iconView;
    /** The icon used to place a call to the contact. Only present for non-group entries. */
    public View callView;
    /** The icon used to expand and collapse an entry. Only present for group entries. */
    public ImageView groupIndicator;
    /**
     * The text view containing the number of items in the group. Only present for group
     * entries.
     */
    public TextView groupSize;
    /** The contact photo for the contact. Only present for group and stand alone entries. */
    public ImageView photoView;
}