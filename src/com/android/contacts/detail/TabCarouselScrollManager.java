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

package com.android.contacts.detail;

import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

/**
 * Takes care of managing scrolling for the side-by-side views using the tab carousel.
 */
public class TabCarouselScrollManager {
    private final ContactDetailTabCarousel mTabCarousel;
    private final ContactDetailFragment mAboutFragment;
    private final ContactDetailUpdatesFragment mUpdatesFragment;
    private final OnScrollListener mVerticalScrollListener = new OnScrollListener() {
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                int totalItemCount) {
            if (mTabCarousel == null) {
                return;
            }
            // If the FIRST item is not visible on the screen, then the carousel must be pinned
            // at the top of the screen.
            if (firstVisibleItem != 0) {
                mTabCarousel.setY(-mTabCarousel.getAllowedVerticalScrollLength());
                return;
            }
            View topView = view.getChildAt(firstVisibleItem);
            if (topView == null) {
                return;
            }
            int amtToScroll = Math.max((int) view.getChildAt(firstVisibleItem).getY(),
                    -mTabCarousel.getAllowedVerticalScrollLength());
            mTabCarousel.setY(amtToScroll);
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {}
    };

    private TabCarouselScrollManager(ContactDetailTabCarousel tabCarousel,
            ContactDetailFragment aboutFragment, ContactDetailUpdatesFragment updatesFragment) {
        mTabCarousel = tabCarousel;
        mAboutFragment = aboutFragment;
        mUpdatesFragment = updatesFragment;
    }

    public void bind() {
        mAboutFragment.setVerticalScrollListener(mVerticalScrollListener);
        mUpdatesFragment.setVerticalScrollListener(mVerticalScrollListener);
    }

    public static void bind(ContactDetailTabCarousel tabCarousel,
            ContactDetailFragment aboutFragment, ContactDetailUpdatesFragment updatesFragment) {
        TabCarouselScrollManager scrollManager =
                new TabCarouselScrollManager(tabCarousel, aboutFragment, updatesFragment);
        scrollManager.bind();
    }
}
