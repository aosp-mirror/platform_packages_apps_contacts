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
package com.android.contacts.list;

import com.android.contacts.R;
import com.android.contacts.widget.PinnedHeaderListView;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.SectionIndexer;
import android.widget.TextView;

/**
 * A list adapter that supports section indexer and a pinned header.
 */
public abstract class PinnedHeaderListAdapter extends CursorAdapter
        implements SectionIndexer, PinnedHeaderListView.PinnedHeaderAdapter {

    /**
     * An approximation of the background color of the pinned header. This color
     * is used when the pinned header is being pushed up.  At that point the header
     * "fades away".  Rather than computing a faded bitmap based on the 9-patch
     * normally used for the background, we will use a solid color, which will
     * provide better performance and reduced complexity.
     */
    private int mPinnedHeaderBackgroundColor;

    private SectionIndexer mIndexer;

    public PinnedHeaderListAdapter(Context context) {
        super(context, null, false);
        this.mContext = context;
        mPinnedHeaderBackgroundColor =
                context.getResources().getColor(R.color.pinned_header_background);
    }

    public void setIndexer(SectionIndexer indexer) {
        mIndexer = indexer;
    }

    /**
     * Maps an adapter position to the corresponding cursor position.  Should not
     * be needed once we have switched to using header views and composite
     * list adapters.
     */
    @Deprecated
    protected int getCursorPosition(int position) {
        return position;
    }

    public Object [] getSections() {
        if (mIndexer == null) {
            return new String[] { " " };
        } else {
            return mIndexer.getSections();
        }
    }

    public int getPositionForSection(int sectionIndex) {
        if (mIndexer == null) {
            return -1;
        }

        return mIndexer.getPositionForSection(sectionIndex);
    }

    public int getSectionForPosition(int position) {
        if (mIndexer == null) {
            return -1;
        }

        return mIndexer.getSectionForPosition(position);
    }

    /**
     * Computes the state of the pinned header.  It can be invisible, fully
     * visible or partially pushed up out of the view.
     */
    public int getPinnedHeaderState(int position) {
        if (mIndexer == null || mCursor == null || mCursor.getCount() == 0) {
            return PINNED_HEADER_GONE;
        }

        int realPosition = getCursorPosition(position);
        if (realPosition < 0) {
            return PINNED_HEADER_GONE;
        }

        // The header should get pushed up if the top item shown
        // is the last item in a section for a particular letter.
        int section = getSectionForPosition(realPosition);
        int nextSectionPosition = getPositionForSection(section + 1);
        if (nextSectionPosition != -1 && realPosition == nextSectionPosition - 1) {
            return PINNED_HEADER_PUSHED_UP;
        }

        return PINNED_HEADER_VISIBLE;
    }

    final static class PinnedHeaderCache {
        public TextView titleView;
        public ColorStateList textColor;
        public Drawable background;
    }

    /**
     * Configures the pinned header by setting the appropriate text label
     * and also adjusting color if necessary.  The color needs to be
     * adjusted when the pinned header is being pushed up from the view.
     */
    public void configurePinnedHeader(View header, int position, int alpha) {
        PinnedHeaderCache cache = (PinnedHeaderCache)header.getTag();
        if (cache == null) {
            cache = new PinnedHeaderCache();
            cache.titleView = (TextView)header.findViewById(R.id.header_text);
            cache.textColor = cache.titleView.getTextColors();
            cache.background = header.getBackground();
            header.setTag(cache);
        }

        int realPosition = getCursorPosition(position);
        int section = getSectionForPosition(realPosition);

        String title = (String)mIndexer.getSections()[section];
        cache.titleView.setText(title);

        if (alpha == 255) {
            // Opaque: use the default background, and the original text color
            header.setBackgroundDrawable(cache.background);
            cache.titleView.setTextColor(cache.textColor);
        } else {
            // Faded: use a solid color approximation of the background, and
            // a translucent text color
            header.setBackgroundColor(Color.rgb(
                    Color.red(mPinnedHeaderBackgroundColor) * alpha / 255,
                    Color.green(mPinnedHeaderBackgroundColor) * alpha / 255,
                    Color.blue(mPinnedHeaderBackgroundColor) * alpha / 255));

            int textColor = cache.textColor.getDefaultColor();
            cache.titleView.setTextColor(Color.argb(alpha,
                    Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
        }
    }

    public View createPinnedHeaderView(ViewGroup parent) {
        return LayoutInflater.from(mContext).inflate(R.layout.list_section, parent, false);
    }
}
