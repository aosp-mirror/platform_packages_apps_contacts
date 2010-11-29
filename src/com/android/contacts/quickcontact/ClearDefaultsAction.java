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

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;

/**
 * Action that expands to show and allow clearing the currently selected defaults.
 */
public class ClearDefaultsAction implements Action {
    /**
     * This is a pseudo-mimetype that is only needed for the action list. It has to be
     * different from the real mime-types used
     */
    public static final String PSEUDO_MIME_TYPE = "__clear_defaults_mime_type";

    @Override
    public boolean collapseWith(Action t) {
        return false;
    }

    @Override
    public boolean shouldCollapseWith(Action t) {
        return false;
    }

    @Override
    public CharSequence getHeader() {
        return null;
    }

    @Override
    public CharSequence getBody() {
        return null;
    }

    @Override
    public String getMimeType() {
        return PSEUDO_MIME_TYPE;
    }

    @Override
    public Drawable getFallbackIcon() {
        return null;
    }

    @Override
    public Intent getIntent() {
        return null;
    }

    @Override
    public Boolean isPrimary() {
        return null;
    }

    @Override
    public Uri getDataUri() {
        return null;
    }

    @Override
    public long getDataId() {
        return -1;
    }
}
