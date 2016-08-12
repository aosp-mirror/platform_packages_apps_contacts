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
package com.android.contacts.common.list;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;

import android.content.Context;
import android.util.AttributeSet;

/**
 * A {@link ContactTileStarredView} displays the contact's picture overlayed with their name
 * in a square. The actual dimensions are set by
 * {@link com.android.contacts.common.list.ContactTileAdapter.ContactTileRow}.
 */
public class ContactTileStarredView extends ContactTileView {

    /**
     * The photo manager should display the default image/letter at 80% of its normal size.
     */
    private static final float DEFAULT_IMAGE_LETTER_SCALE = 0.8f;

    public ContactTileStarredView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean isDarkTheme() {
        return false;
    }

    @Override
    protected int getApproximateImageSize() {
        // The picture is the full size of the tile (minus some padding, but we can be generous)
        return mListener.getApproximateTileWidth();
    }

    @Override
    protected DefaultImageRequest getDefaultImageRequest(String displayName, String lookupKey) {
        return new DefaultImageRequest(displayName, lookupKey, ContactPhotoManager.TYPE_DEFAULT,
                DEFAULT_IMAGE_LETTER_SCALE, /* offset = */ 0, /* isCircular = */ true);
    }
}
