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
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

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

    public void swapInto(ViewGroup target) {
        target.removeAllViews();
        target.addView(mContent);
    }

    public void swapWith(View target) {
        // Borrow layout params and id for ourselves
        this.mContent.setLayoutParams(target.getLayoutParams());
        this.mContent.setId(target.getId());

        // Find the direct parent of this view
        final ViewParent parent = target.getParent();
        if (parent == null || !(parent instanceof ViewGroup)) return;

        // Swap out existing view with ourselves
        final ViewGroup parentGroup = (ViewGroup)parent;
        final int index = parentGroup.indexOfChild(target);
        parentGroup.removeViewAt(index);
        parentGroup.addView(this.mContent, index);
    }

    public void swapWith(View ancestor, int id) {
        // Try finding the target view to replace
        final View target = ancestor.findViewById(id);
        if (target != null) {
            swapWith(target);
        }
    }

    public View getView() {
        return mContent;
    }
}
