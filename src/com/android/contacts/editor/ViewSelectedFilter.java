/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.editor;

import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

/**
 * This is an AccessibilityDelegate that filters out the TYPE_VIEW_SELECTED event.
 */
public class ViewSelectedFilter extends AccessibilityDelegate {
    private View mView; //the view we don't want TYPE_VIEW_SELECTED event to fire.

    private ViewSelectedFilter(View view) {
        super();
        mView = view;
    }

    /**
     * AccessibilityEvent can only be suppressed at a view's parent, so this function adds the
     * delegate to the view's parent.
     * @param view the view whose TYPE_VIEW_SELECTED event should be suppressed.
     */
    public static void suppressViewSelectedEvent(View view) {
        final View parent = (View) view.getParent();
        parent.setAccessibilityDelegate(new ViewSelectedFilter(view));
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(
            ViewGroup host, View child,AccessibilityEvent event) {
        if (child == mView && event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            return false;
        }
        return super.onRequestSendAccessibilityEvent(host, child, event);
    }
}
