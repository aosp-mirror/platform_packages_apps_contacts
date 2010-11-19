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

import com.android.contacts.R;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;

/**
 * Specific action that launches the profile card.
 */
public class ProfileAction implements Action {
    private final Context mContext;
    private final Uri mLookupUri;

    public ProfileAction(Context context, Uri lookupUri) {
        mContext = context;
        mLookupUri = lookupUri;
    }

    /** {@inheritDoc} */
    @Override
    public CharSequence getHeader() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public CharSequence getBody() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String getMimeType() {
        return Contacts.CONTENT_ITEM_TYPE;
    }

    /** {@inheritDoc} */
    @Override
    public Drawable getFallbackIcon() {
        return mContext.getResources().getDrawable(R.drawable.ic_contacts_details);
    }

    /** {@inheritDoc} */
    @Override
    public Intent getIntent() {
        final Intent intent = new Intent(Intent.ACTION_VIEW, mLookupUri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /** {@inheritDoc} */
    @Override
    public Boolean isPrimary() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Uri getDataUri() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean collapseWith(Action t) {
        return false; // Never dup.
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldCollapseWith(Action t) {
        return false; // Never dup.
    }
}
