/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.contacts.interactions;

import com.android.contacts.R;
import com.android.contacts.common.util.BitmapUtil;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;

/**
 * Represents a call log event interaction, wrapping the columns in
 * {@link android.provider.CallLog.Calls}.
 *
 * This class does not return log entries related to voicemail or SIP calls. Additionally,
 * this class ignores number presentation. Number presentation affects how to identify phone
 * numbers. Since, we already know the identity of the phone number owner we can ignore number
 * presentation.
 *
 * As a result of ignoring voicemail and number presentation, we don't need to worry about API
 * version.
 */
public class CallLogInteraction implements ContactInteraction {

    private static final String URI_TARGET_PREFIX = "tel:";
    private static final int CALL_LOG_ICON_RES = R.drawable.ic_phone_24dp;
    private static final int CALL_ARROW_ICON_RES = R.drawable.ic_call_arrow;

    private ContentValues mValues;

    public CallLogInteraction(ContentValues values) {
        mValues = values;
    }

    @Override
    public Intent getIntent() {
        return new Intent(Intent.ACTION_CALL).setData(Uri.parse(URI_TARGET_PREFIX + getNumber()));
    }

    @Override
    public String getViewHeader(Context context) {
        return getNumber();
    }

    @Override
    public long getInteractionDate() {
        return getDate();
    }

    @Override
    public String getViewBody(Context context) {
        int numberType = getCachedNumberType();
        if (numberType == -1) {
            return null;
        }
        return Phone.getTypeLabel(context.getResources(), getCachedNumberType(),
                getCachedNumberLabel()).toString();
    }

    @Override
    public String getViewFooter(Context context) {
        return ContactInteractionUtil.formatDateStringFromTimestamp(getDate(), context);
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getResources().getDrawable(CALL_LOG_ICON_RES);
    }

    @Override
    public Drawable getBodyIcon(Context context) {
        return null;
    }

    @Override
    public Drawable getFooterIcon(Context context) {
        Drawable callArrow = null;
        Resources res = context.getResources();
        switch (getType()) {
            case Calls.INCOMING_TYPE:
                callArrow = res.getDrawable(CALL_ARROW_ICON_RES);
                callArrow.setColorFilter(res.getColor(R.color.call_arrow_green),
                        PorterDuff.Mode.MULTIPLY);
                break;
            case Calls.MISSED_TYPE:
                callArrow = res.getDrawable(CALL_ARROW_ICON_RES);
                callArrow.setColorFilter(res.getColor(R.color.call_arrow_red),
                        PorterDuff.Mode.MULTIPLY);
                break;
            case Calls.OUTGOING_TYPE:
                callArrow = BitmapUtil.getRotatedDrawable(res, CALL_ARROW_ICON_RES, 180f);
                callArrow.setColorFilter(res.getColor(R.color.call_arrow_green),
                        PorterDuff.Mode.MULTIPLY);
                break;
        }
        return callArrow;
    }

    public String getCachedName() {
        return mValues.getAsString(Calls.CACHED_NAME);
    }

    public String getCachedNumberLabel() {
        return mValues.getAsString(Calls.CACHED_NUMBER_LABEL);
    }

    public int getCachedNumberType() {
        Integer type = mValues.getAsInteger(Calls.CACHED_NUMBER_TYPE);
        return type != null ? type : -1;
    }

    public long getDate() {
        return mValues.getAsLong(Calls.DATE);
    }

    public long getDuration() {
        return mValues.getAsLong(Calls.DURATION);
    }

    public boolean getIsRead() {
        return mValues.getAsBoolean(Calls.IS_READ);
    }

    public int getLimitParamKey() {
        return mValues.getAsInteger(Calls.LIMIT_PARAM_KEY);
    }

    public boolean getNew() {
        return mValues.getAsBoolean(Calls.NEW);
    }

    public String getNumber() {
        return mValues.getAsString(Calls.NUMBER);
    }

    public int getNumberPresentation() {
        return mValues.getAsInteger(Calls.NUMBER_PRESENTATION);
    }

    public int getOffsetParamKey() {
        return mValues.getAsInteger(Calls.OFFSET_PARAM_KEY);
    }

    public int getType() {
        return mValues.getAsInteger(Calls.TYPE);
    }
}