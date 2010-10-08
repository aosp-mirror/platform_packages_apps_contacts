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

package com.android.contacts.widget;

import android.widget.ListView;

/**
 * Utility methods for working with ListView.
 */
public final class ListViewUtils {

    /**
     * Position the element at about 1/3 of the list height
     */
    private static final float PREFERRED_SELECTION_OFFSET_FROM_TOP = 0.33f;

    /**
     * Brings the specified position to view by optionally performing a jump-scroll maneuver:
     * first it jumps to some position near the one requested and then does a smooth
     * scroll to the requested position.  This creates an impression of full smooth
     * scrolling without actually traversing the entire list.  If smooth scrolling is
     * not requested, instantly positions the requested item at a preferred offset.
     */
    public static void requestPositionToScreen(
            final ListView listView, final int position, boolean smoothScroll) {
        if (!smoothScroll) {
            final int offset = (int) (listView.getHeight() * PREFERRED_SELECTION_OFFSET_FROM_TOP);
            listView.setSelectionFromTop(position, offset);
            return;
        }

        listView.post(new Runnable() {

            @Override
            public void run() {
                int firstPosition = listView.getFirstVisiblePosition() + 1;
                int lastPosition = listView.getLastVisiblePosition();
                if (position >= firstPosition && position <= lastPosition) {
                    return; // Already on screen
                }

                // We will first position the list a couple of screens before or after
                // the new selection and then scroll smoothly to it.
                int twoScreens = (lastPosition - firstPosition) * 2;
                int preliminaryPosition;
                if (position < firstPosition) {
                    preliminaryPosition = position + twoScreens;
                    if (preliminaryPosition >= listView.getCount()) {
                        preliminaryPosition = listView.getCount() - 1;
                    }
                    if (preliminaryPosition < firstPosition) {
                        listView.setSelection(preliminaryPosition);
                    }
                } else {
                    preliminaryPosition = position - twoScreens;
                    if (preliminaryPosition < 0) {
                        preliminaryPosition = 0;
                    }
                    if (preliminaryPosition > lastPosition) {
                        listView.setSelection(preliminaryPosition);
                    }
                }

                scrollToFinalPosition(listView, position);
            }
        });
    }

    private static void scrollToFinalPosition(final ListView listView, final int position) {
        final int offset = (int) (listView.getHeight() * PREFERRED_SELECTION_OFFSET_FROM_TOP);
        listView.post(new Runnable() {

            @Override
            public void run() {
                listView.smoothScrollToPositionFromTop(position, offset);
            }
        });
    }
}
