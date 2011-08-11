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

package com.android.contacts;

import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

/**
 * Handles scrolling back of a list tied to a header.
 * <p>
 * This is used to implement a header that scrolls up with the content of a list to be partially
 * obscured.
 */
public class BackScrollManager {
    /** Defines the header to be scrolled. */
    public interface ScrollableHeader {
        /** Sets the offset by which to scroll. */
        public void setOffset(int offset);
        /** Gets the maximum offset that should be applied to the header. */
        public int getMaximumScrollableHeaderOffset();
    }

    private final ScrollableHeader mHeader;
    private final ListView mListView;

    private final AbsListView.OnScrollListener mScrollListener =
            new AbsListView.OnScrollListener() {
                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                        int totalItemCount) {
                    if (firstVisibleItem != 0) {
                        // The first item is not shown, the header should be pinned at the top.
                        mHeader.setOffset(mHeader.getMaximumScrollableHeaderOffset());
                        return;
                    }

                    View firstVisibleItemView = view.getChildAt(firstVisibleItem);
                    if (firstVisibleItemView == null) {
                        return;
                    }
                    // We scroll the header up, but at most pin it to the top of the screen.
                    int offset = Math.min(
                            (int) -view.getChildAt(firstVisibleItem).getY(),
                            mHeader.getMaximumScrollableHeaderOffset());
                    mHeader.setOffset(offset);
                }

                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    // Nothing to do here.
                }
            };

    /**
     * Creates a new instance of a {@link BackScrollManager} that connected the header and the list
     * view.
     */
    public static void bind(ScrollableHeader header, ListView listView) {
        BackScrollManager backScrollManager = new BackScrollManager(header, listView);
        backScrollManager.bind();
    }

    private BackScrollManager(ScrollableHeader header, ListView listView) {
        mHeader = header;
        mListView = listView;
    }

    private void bind() {
        mListView.setOnScrollListener(mScrollListener);
        // We disable the scroll bar because it would otherwise be incorrect because of the hidden
        // header.
        mListView.setVerticalScrollBarEnabled(false);
    }
}
