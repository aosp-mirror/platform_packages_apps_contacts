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

package com.android.contacts;

import com.android.contacts.list.ContactEntryListAdapter;
import com.android.contacts.widget.PinnedHeaderListView;
import com.android.contacts.widget.TextHighlightingAnimation;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.AbsListView;
import android.widget.ListAdapter;

/**
 * A custom list view for a list of contacts or contact-related entries.  It handles
 * animation of names on scroll.
 */
public class ContactEntryListView extends PinnedHeaderListView {

    private static final int TEXT_HIGHLIGHTING_ANIMATION_DURATION = 350;

    private final TextHighlightingAnimation mHighlightingAnimation =
            new ContactNameHighlightingAnimation(this, TEXT_HIGHLIGHTING_ANIMATION_DURATION);

    private boolean mHighlightNamesWhenScrolling;

    public ContactEntryListView(Context context) {
        super(context);
    }

    public ContactEntryListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ContactEntryListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public TextHighlightingAnimation getTextHighlightingAnimation() {
        return mHighlightingAnimation;
    }

    public boolean getHighlightNamesWhenScrolling() {
        return mHighlightNamesWhenScrolling;
    }

    public void setHighlightNamesWhenScrolling(boolean flag) {
        mHighlightNamesWhenScrolling = flag;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
        if (adapter instanceof ContactEntryListAdapter) {
            ((ContactEntryListAdapter)adapter)
                    .setTextWithHighlightingFactory(mHighlightingAnimation);
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        super.onScrollStateChanged(view, scrollState);
        if (mHighlightNamesWhenScrolling) {
            if (scrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                mHighlightingAnimation.startHighlighting();
            } else {
                mHighlightingAnimation.stopHighlighting();
            }
        }
    }
}
