/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.quickcontact;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.LinearLayout;

/**
 * Custom layout for Quick Contact. It intercepts the BACK key and
 * close QC even when the soft keyboard is open.
 */
public class QuickContactRootLayout extends LinearLayout {
    private Listener mListener;

    public QuickContactRootLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    /**
     * Intercepts the BACK key event and dismisses QuickContact window.
     */
    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (mListener != null) mListener.onBackPressed();
            return true;
        }
        return super.dispatchKeyEventPreIme(event);
    }

    public interface Listener {
        void onBackPressed();
    }
}
