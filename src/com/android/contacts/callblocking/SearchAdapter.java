/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.callblocking;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.callblocking.FilteredNumberAsyncQueryHandler;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.util.PhoneNumberHelper;

import com.android.contacts.R;

public class SearchAdapter extends PhoneNumberListAdapter {

    private String mFormattedQueryString;
    private String mCountryIso;

    public final static int SHORTCUT_INVALID = -1;
    public final static int SHORTCUT_DIRECT_CALL = 0;
    public final static int SHORTCUT_CREATE_NEW_CONTACT = 1;
    public final static int SHORTCUT_ADD_TO_EXISTING_CONTACT = 2;
    public final static int SHORTCUT_SEND_SMS_MESSAGE = 3;
    public final static int SHORTCUT_MAKE_VIDEO_CALL = 4;
    public final static int SHORTCUT_BLOCK_NUMBER = 5;

    public final static int SHORTCUT_COUNT = 6;

    private final boolean[] mShortcutEnabled = new boolean[SHORTCUT_COUNT];

    private final BidiFormatter mBidiFormatter = BidiFormatter.getInstance();
    private boolean mVideoCallingEnabled = false;

    protected boolean mIsQuerySipAddress;

    private Resources mResources;
    private FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;

    public SearchAdapter(Context context) {
        super(context);
        // below is from ContactsPhoneNumberListAdapter
        mCountryIso = GeoUtil.getCurrentCountryIso(context);
        mVideoCallingEnabled = CallUtil.isVideoEnabled(context);
        // below is from RegularSearchListAdapter
        setShortcutEnabled(SHORTCUT_CREATE_NEW_CONTACT, false);
        setShortcutEnabled(SHORTCUT_ADD_TO_EXISTING_CONTACT, false);
        // below is from BlockedListSearchAdapter
        mResources = context.getResources();
        disableAllShortcuts();
        setShortcutEnabled(SHORTCUT_BLOCK_NUMBER, true);
        mFilteredNumberAsyncQueryHandler =
                new FilteredNumberAsyncQueryHandler(context.getContentResolver());
    }

    @Override
    public int getCount() {
        return super.getCount() + getShortcutCount();
    }

    /**
     * @return The number of enabled shortcuts. Ranges from 0 to a maximum of SHORTCUT_COUNT
     */
    public int getShortcutCount() {
        int count = 0;
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            if (mShortcutEnabled[i]) count++;
        }
        return count;
    }

    public void disableAllShortcuts() {
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            mShortcutEnabled[i] = false;
        }
    }

    @Override
    public int getItemViewType(int position) {
        final int shortcut = getShortcutTypeFromPosition(position);
        if (shortcut >= 0) {
            // shortcutPos should always range from 1 to SHORTCUT_COUNT
            return super.getViewTypeCount() + shortcut;
        } else {
            return super.getItemViewType(position);
        }
    }

    @Override
    public int getViewTypeCount() {
        // Number of item view types in the super implementation + 2 for the 2 new shortcuts
        return super.getViewTypeCount() + SHORTCUT_COUNT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (getShortcutTypeFromPosition(position) >= 0) {
            if (convertView != null) {
                assignShortcutToView((ContactListItemView) convertView);
                return convertView;
            } else {
                final ContactListItemView v = new ContactListItemView(getContext(), null,
                        mVideoCallingEnabled);
                assignShortcutToView(v);
                return v;
            }
        } else {
            return super.getView(position, convertView, parent);
        }
    }

    @Override
    protected ContactListItemView newView(
            Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        final ContactListItemView view = super.newView(context, partition, cursor, position,
                parent);

        view.setSupportVideoCallIcon(mVideoCallingEnabled);
        return view;
    }

    /**
     * @param position The position of the item
     * @return The enabled shortcut type matching the given position if the item is a
     * shortcut, -1 otherwise
     */
    public int getShortcutTypeFromPosition(int position) {
        int shortcutCount = position - super.getCount();
        if (shortcutCount >= 0) {
            // Iterate through the array of shortcuts, looking only for shortcuts where
            // mShortcutEnabled[i] is true
            for (int i = 0; shortcutCount >= 0 && i < mShortcutEnabled.length; i++) {
                if (mShortcutEnabled[i]) {
                    shortcutCount--;
                    if (shortcutCount < 0) return i;
                }
            }
            throw new IllegalArgumentException("Invalid position - greater than cursor count "
                    + " but not a shortcut.");
        }
        return SHORTCUT_INVALID;
    }

    @Override
    public boolean isEmpty() {
        return getShortcutCount() == 0 && super.isEmpty();
    }

    @Override
    public boolean isEnabled(int position) {
        final int shortcutType = getShortcutTypeFromPosition(position);
        if (shortcutType >= 0) {
            return true;
        } else {
            return super.isEnabled(position);
        }
    }

    private void assignShortcutToView(ContactListItemView v) {
        v.setDrawableResource(R.drawable.ic_not_interested_googblue_24dp);
        v.setDisplayName(
                getContext().getResources().getString(R.string.search_shortcut_block_number));
        v.setPhotoPosition(super.getPhotoPosition());
        v.setAdjustSelectionBoundsEnabled(false);
    }

    /**
     * @return True if the shortcut state (disabled vs enabled) was changed by this operation
     */
    public boolean setShortcutEnabled(int shortcutType, boolean visible) {
        final boolean changed = mShortcutEnabled[shortcutType] != visible;
        mShortcutEnabled[shortcutType] = visible;
        return changed;
    }

    public String getFormattedQueryString() {
        if (mIsQuerySipAddress) {
            // Return unnormalized SIP address
            return getQueryString();
        }
        return mFormattedQueryString;
    }

    @Override
    public void setQueryString(String queryString) {
        // Don't show actions if the query string contains a letter.
        final boolean showNumberShortcuts = !TextUtils.isEmpty(getFormattedQueryString())
                && hasDigitsInQueryString();
        mIsQuerySipAddress = PhoneNumberHelper.isUriNumber(queryString);

        if (isChanged(showNumberShortcuts)) {
            notifyDataSetChanged();
        }
        mFormattedQueryString = PhoneNumberUtils.formatNumber(
                PhoneNumberUtils.normalizeNumber(queryString), mCountryIso);
        super.setQueryString(queryString);
    }

    protected boolean isChanged(boolean showNumberShortcuts) {
        return setShortcutEnabled(SHORTCUT_BLOCK_NUMBER, showNumberShortcuts || mIsQuerySipAddress);
    }

    /**
     * Whether there is at least one digit in the query string.
     */
    private boolean hasDigitsInQueryString() {
        String queryString = getQueryString();
        int length = queryString.length();
        for (int i = 0; i < length; i++) {
            if (Character.isDigit(queryString.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public void setViewBlocked(ContactListItemView view, Integer id) {
        view.setTag(R.id.block_id, id);
        final int textColor = mResources.getColor(R.color.blocked_number_block_color);
        view.getDataView().setTextColor(textColor);
        view.getLabelView().setTextColor(textColor);
        //TODO: Add icon
    }

    public void setViewUnblocked(ContactListItemView view) {
        view.setTag(R.id.block_id, null);
        final int textColor = mResources.getColor(R.color.blocked_number_secondary_text_color);
        view.getDataView().setTextColor(textColor);
        view.getLabelView().setTextColor(textColor);
        //TODO: Remove icon
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);

        final ContactListItemView view = (ContactListItemView) itemView;
        // Reset view state to unblocked.
        setViewUnblocked(view);

        final String number = getPhoneNumber(position);
        final String countryIso = GeoUtil.getCurrentCountryIso(mContext);
        final FilteredNumberAsyncQueryHandler.OnCheckBlockedListener onCheckListener =
                new FilteredNumberAsyncQueryHandler.OnCheckBlockedListener() {
                    @Override
                    public void onCheckComplete(Integer id) {
                        if (id != null) {
                            setViewBlocked(view, id);
                        }
                    }
                };
        mFilteredNumberAsyncQueryHandler.isBlockedNumber(onCheckListener, number, countryIso);
    }
}
