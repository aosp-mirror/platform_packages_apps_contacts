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
package com.android.contacts.list;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Custom container for ImageView or ContactBadge inside {@link ContactTileView}.
 *
 * This improves the performance of favorite tabs by not passing the layout request to the parent
 * views, assuming that once measured this will not need to resize itself.
 */
public class ContactTileImageContainer extends FrameLayout {

    public ContactTileImageContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void requestLayout() {
        forceLayout();
    }
}