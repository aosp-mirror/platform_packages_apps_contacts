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

import com.android.contacts.ContactLoader;
import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.activities.ContactDetailActivity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * This is a horizontally scrolling carousel with 2 tabs: one to see info about the contact and
 * one to see updates from the contact.
 * TODO: Create custom views for the tabs so their width can be programatically set as 2/3 of the
 * screen width.
 */
public class ContactDetailTabCarousel extends HorizontalScrollView
        implements View.OnClickListener, OnTouchListener {
    private static final String TAG = "ContactDetailTabCarousel";

    private CheckBox mStarredView;
    private ImageView mPhotoView;
    private TextView mStatusView;
    private TextView mStatusDateView;

    private Uri mContactUri;
    private Listener mListener;

    private View[] mTabs = new View[2];

    private int mAllowedScrollLength;

    /**
     * Interface for callbacks invoked when the user interacts with the carousel.
     */
    public interface Listener {
        public void onTouchDown();
        public void onTouchUp();
        public void onScrollChanged(int l, int t, int oldl, int oldt);
        public void onTabSelected(int position);
    }

    public ContactDetailTabCarousel(Context context) {
        this(context, null);
    }

    public ContactDetailTabCarousel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ContactDetailTabCarousel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater =
            (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.contact_detail_tab_carousel, this);

        setOnTouchListener(this);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mListener.onScrollChanged(l, t, oldl, oldt);
    }

    /**
     * Returns the number of pixels that this view can be scrolled.
     */
    public int getAllowedScrollLength() {
        if (mAllowedScrollLength == 0) {
            // Find the total length of two tabs side-by-side
            int totalLength = 0;
            for (int i=0; i < mTabs.length; i++) {
                totalLength += mTabs[i].getWidth();
            }
            // Find the allowed scrolling length by subtracting the current visible screen width
            // from the total length of the tabs.
            mAllowedScrollLength = totalLength - getWidth();
        }
        return mAllowedScrollLength;
    }

    /**
     * Updates the tab selection.
     */
    public void setCurrentTab(int position) {
        if (position < 0 || position > mTabs.length) {
            throw new IllegalStateException("Invalid position in array of tabs: " + position);
        }
        // TODO: Handle device rotation (saving and restoring state of the selected tab)
        // This will take more work because there is no tab carousel in phone landscape
        if (mTabs[position] == null) {
            return;
        }
        mTabs[position].setSelected(true);
        unselectAllOtherTabs(position);
    }

    private void unselectAllOtherTabs(int position) {
        for (int i = 0; i < mTabs.length; i++) {
            if (position != i) {
                mTabs[i].setSelected(false);
            }
        }
    }

    /**
     * Loads the data from the Loader-Result. This is the only function that has to be called
     * from the outside to fully setup the View
     */
    public void loadData(ContactLoader.Result contactData) {
        mContactUri = contactData.getLookupUri();

        View aboutView = findViewById(R.id.tab_about);
        View updateView = findViewById(R.id.tab_update);

        TextView aboutTab = (TextView) aboutView.findViewById(R.id.label);
        aboutTab.setText(mContext.getString(R.string.contactDetailAbout));
        aboutTab.setClickable(true);
        aboutTab.setSelected(true);

        TextView updatesTab = (TextView) updateView.findViewById(R.id.label);
        updatesTab.setText(mContext.getString(R.string.contactDetailUpdates));
        updatesTab.setClickable(true);

        aboutTab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onTabSelected(0);
            }
        });
        updatesTab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onTabSelected(1);
            }
        });

        mTabs[0] = aboutTab;
        mTabs[1] = updatesTab;

        // Retrieve the photo and star views for the "about" tab
        mPhotoView = (ImageView) aboutView.findViewById(R.id.photo);
        mStarredView = (CheckBox) aboutView.findViewById(R.id.star);
        mStarredView.setOnClickListener(this);

        // Retrieve the social update views for the "updates" tab
        mStatusView = (TextView) updateView.findViewById(R.id.status);
        mStatusDateView = (TextView) updateView.findViewById(R.id.status_date);

        ContactDetailDisplayUtils.setPhoto(mContext, contactData, mPhotoView);
        ContactDetailDisplayUtils.setStarred(contactData, mStarredView);
        ContactDetailDisplayUtils.setSocialSnippetAndDate(mContext, contactData, mStatusView,
                mStatusDateView);
    }

    /**
     * Set the given {@link Listener} to handle carousel events.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    // TODO: The starred icon needs to move to the action bar.
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.star: {
                // Toggle "starred" state
                // Make sure there is a contact
                if (mContactUri != null) {
                    Intent intent = ContactSaveService.createSetStarredIntent(
                            getContext(), mContactUri, mStarredView.isChecked());
                    getContext().startService(intent);
                }
                break;
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mListener.onTouchDown();
                return true;
            case MotionEvent.ACTION_UP:
                mListener.onTouchUp();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean interceptTouch = super.onInterceptTouchEvent(ev);
        if (interceptTouch) {
            mListener.onTouchDown();
        }
        return interceptTouch;
    }
}
