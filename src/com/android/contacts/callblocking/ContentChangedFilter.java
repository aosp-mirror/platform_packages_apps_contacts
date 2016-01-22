/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.callblocking;

import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

/**
 * AccessibilityDelegate that will filter out TYPE_WINDOW_CONTENT_CHANGED
 * Used to suppress "Showing items x of y" from firing of ListView whenever it's content changes.
 * AccessibilityEvent can only be rejected at a view's parent once it is generated,
 * use addToParent() to add this delegate to the parent.
 */
public class ContentChangedFilter extends AccessibilityDelegate {
    //the view we don't want TYPE_WINDOW_CONTENT_CHANGED to fire.
    private View mView;

    /**
     * Add this delegate to the parent of @param view to filter out TYPE_WINDOW_CONTENT_CHANGED
     */
    public static void addToParent(View view){
        View parent = (View) view.getParent();
        parent.setAccessibilityDelegate(new ContentChangedFilter(view));
    }

    private ContentChangedFilter(View view){
        super();
        mView = view;
    }

    @Override
    public boolean onRequestSendAccessibilityEvent (ViewGroup host, View child, AccessibilityEvent event){
        if(child == mView){
            if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED){
                return false;
            }
        }
        return super.onRequestSendAccessibilityEvent(host,child,event);
    }

}
