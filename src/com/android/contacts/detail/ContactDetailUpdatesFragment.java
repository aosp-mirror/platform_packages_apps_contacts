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
 * limitations under the License
 */

package com.android.contacts.detail;

import com.android.contacts.ContactLoader;
import com.android.contacts.R;
import com.android.contacts.activities.ContactDetailActivity.FragmentKeyListener;

import android.app.Fragment;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

public class ContactDetailUpdatesFragment extends Fragment
        implements FragmentKeyListener, FragmentOverlay {

    private static final String TAG = "ContactDetailUpdatesFragment";

    private ContactLoader.Result mContactData;
    private Uri mLookupUri;

    /**
     * This optional view adds an alpha layer over the entire fragment.
     */
    private View mAlphaLayer;

    /**
     * This optional view adds a layer over the entire fragment so that when visible, it intercepts
     * all touch events on the fragment.
     */
    private View mTouchInterceptLayer;

    public ContactDetailUpdatesFragment() {
        // Explicit constructor for inflation
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View rootView = inflater.inflate(R.layout.contact_detail_updates_fragment, container,
                false);

        mAlphaLayer = rootView.findViewById(R.id.alpha_overlay);
        mTouchInterceptLayer = rootView.findViewById(R.id.touch_intercept_overlay);

        return rootView;
    }

    public void setData(Uri lookupUri, ContactLoader.Result result) {
        mLookupUri = lookupUri;
        mContactData = result;
        // TODO: Load up the "recent updates" section based on the result
    }

    @Override
    public void setAlphaLayerValue(float alpha) {
        if (mAlphaLayer != null) {
            mAlphaLayer.setAlpha(alpha);
        }
    }

    @Override
    public void enableAlphaLayer() {
        if (mAlphaLayer != null) {
            mAlphaLayer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void enableTouchInterceptor(OnClickListener clickListener) {
        if (mTouchInterceptLayer != null) {
            mTouchInterceptLayer.setVisibility(View.VISIBLE);
            mTouchInterceptLayer.setOnClickListener(clickListener);
        }
    }

    @Override
    public void disableTouchInterceptor() {
        if (mTouchInterceptLayer != null) {
            mTouchInterceptLayer.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean handleKeyDown(int keyCode) {
        return false;
    }
}
