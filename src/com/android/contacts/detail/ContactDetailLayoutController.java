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

import android.app.Activity;
import android.app.FragmentManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewPropertyAnimator;

import com.android.contacts.R.id;
import com.android.contacts.activities.ContactDetailActivity.FragmentKeyListener;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.util.UriUtils;

/**
 * Sets ContactDetailFragment data and performs animations when data changes.
 *
 * TODO: rename, move some of this logic into ContactDetailFragment and/or delete this class. This
 * class used to have more responsibility: the ContactDetailFragment was used beside social update
 * Fragments.
 */
public class ContactDetailLayoutController {

    private static final String KEY_CONTACT_URI = "contactUri";

    private final int SINGLE_PANE_FADE_IN_DURATION = 275;

    private final Activity mActivity;
    private final FragmentManager mFragmentManager;
    private final ContactDetailFragment.Listener mContactDetailFragmentListener;
    private final View mViewContainer;

    private ContactDetailFragment mDetailFragment;
    private Contact mContactData;
    private Uri mContactUri;

    public ContactDetailLayoutController(Activity activity, Bundle savedState,
            FragmentManager fragmentManager, View viewContainer,
            ContactDetailFragment.Listener contactDetailFragmentListener) {

        if (fragmentManager == null) {
            throw new IllegalStateException("Cannot initialize a ContactDetailLayoutController "
                    + "without a non-null FragmentManager");
        }

        mActivity = activity;
        mFragmentManager = fragmentManager;
        mContactDetailFragmentListener = contactDetailFragmentListener;
        mViewContainer = viewContainer;

        initialize(savedState);
    }

    private void initialize(Bundle savedState) {
        mDetailFragment = (ContactDetailFragment) mFragmentManager
                .findFragmentById(id.contact_detail_about_fragment);

        mDetailFragment.setListener(mContactDetailFragmentListener);

        if (savedState != null) {
            mContactUri = savedState.getParcelable(KEY_CONTACT_URI);

            // Immediately setup layout since we have saved state
            showContact();
        }
    }

    public void setContactData(Contact data) {
        final boolean contactWasLoaded;
        final boolean isDifferentContact;
        if (mContactData == null) {
            contactWasLoaded = false;
            isDifferentContact = true;
        } else {
            contactWasLoaded = true;
            isDifferentContact =
                    !UriUtils.areEqual(mContactData.getLookupUri(), data.getLookupUri());
        }
        mContactData = data;

        // Small screen: We are on our own screen. Fade the data in, but only the first time
        if (!contactWasLoaded) {
            mViewContainer.setAlpha(0.0f);
            final ViewPropertyAnimator animator = mViewContainer.animate();
            animator.alpha(1.0f);
            animator.setDuration(SINGLE_PANE_FADE_IN_DURATION);
        }

        showContact();
    }

    public void showEmptyState() {
        mDetailFragment.setShowStaticPhoto(false);
        mDetailFragment.showEmptyState();
    }

    private void showContact() {
        if (mContactData == null) {
            return;
        }

        Uri previousContactUri = mContactUri;
        mContactUri = mContactData.getLookupUri();
        boolean isDifferentContact = !UriUtils.areEqual(previousContactUri, mContactUri);

        mDetailFragment.setShowStaticPhoto(true);

        if (isDifferentContact) {
            mDetailFragment.resetAdapter();
        }

        mDetailFragment.setData(mContactUri, mContactData);
    }

    public FragmentKeyListener getCurrentPage() {
        return mDetailFragment;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_CONTACT_URI, mContactUri);
    }
}
