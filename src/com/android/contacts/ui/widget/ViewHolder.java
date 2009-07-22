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

package com.android.contacts.ui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

/**
 * Helper to inflate a given layout and produce the {@link View} when requested.
 */
public class ViewHolder {
    protected Context mContext;
    protected LayoutInflater mInflater;
    protected View mContent;

    public ViewHolder(Context context, int layoutRes) {
        mContext = context;
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContent = mInflater.inflate(layoutRes, null);
    }

    public View getView() {
        return mContent;
    }
}
