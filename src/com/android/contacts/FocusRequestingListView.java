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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

/* Subclass of ListView that requests focus after it is layed out for the first time. */
public class FocusRequestingListView extends ListView {

    private boolean mFirstLayoutDone = false;

    public FocusRequestingListView(Context context) {
        super(context);
    }

    public FocusRequestingListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusRequestingListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!mFirstLayoutDone) {
            setFocusable(true);
            requestFocus();
        }
        mFirstLayoutDone = true;
    }
}
