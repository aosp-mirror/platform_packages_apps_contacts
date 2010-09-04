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
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
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
    private Drawable mDefaultSelector;
    private boolean mSelectionVisible;
    private ContactEntryListAdapter mAdapter;

    public ContactEntryListView(Context context) {
        this(context, null);
    }

    public ContactEntryListView(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.listViewStyle);
    }

    public ContactEntryListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPinnedHeaderBackgroundColor(
                context.getResources().getColor(R.color.pinned_header_background));
        mDefaultSelector = getSelector();
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

    public void setSelectionVisible(boolean selectionVisible) {
        if (selectionVisible != mSelectionVisible) {
            mSelectionVisible = selectionVisible;
            if (selectionVisible) {
                // When a persistent selection is handled by the adapter,
                // we want to disable the standard selection drawing.
                setSelector(new EmptyDrawable());
            } else {
                setSelector(mDefaultSelector);
            }
        }
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

    /**
     * A drawable that is ignored.  We have to use an empty drawable instead
     * of null, because ListView does not allow selection to be null.
     */
    private class EmptyDrawable extends Drawable {

        @Override
        public void draw(Canvas canvas) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }
    }
}
