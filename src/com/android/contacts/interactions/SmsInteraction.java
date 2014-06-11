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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Telephony.Sms;

/**
 * Represents an sms interaction, wrapping the columns in
 * {@link android.provider.Telephony.Sms}.
 */
public class SmsInteraction implements ContactInteraction {

    private static final String URI_TARGET_PREFIX = "smsto:";
    private static final int SMS_ICON_RES = R.drawable.ic_message_24dp;

    private ContentValues mValues;

    public SmsInteraction(ContentValues values) {
        mValues = values;
    }

    @Override
    public Intent getIntent() {
        return new Intent(Intent.ACTION_VIEW).setData(Uri.parse(URI_TARGET_PREFIX + getAddress()));
    }

    @Override
    public String getViewDate(Context context) {
        return ContactInteractionUtil.formatDateStringFromTimestamp(getDate(), context);
    }

    @Override
    public long getInteractionDate() {
        return getDate();
    }

    @Override
    public String getViewHeader(Context context) {
        return getBody();
    }

    @Override
    public String getViewBody(Context context) {
        return getAddress();
    }

    @Override
    public String getViewFooter(Context context) {
        return getViewDate(context);
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getResources().getDrawable(SMS_ICON_RES);
    }

    @Override
    public Drawable getBodyIcon(Context context) {
        return null;
    }

    @Override
    public Drawable getFooterIcon(Context context) {
        return null;
    }

    public String getAddress() {
        return mValues.getAsString(Sms.ADDRESS);
    }

    public String getBody() {
        return mValues.getAsString(Sms.BODY);
    }

    public long getDate() {
        return mValues.getAsLong(Sms.DATE);
    }


    public long getDateSent() {
        return mValues.getAsLong(Sms.DATE_SENT);
    }

    public int getErrorCode() {
        return mValues.getAsInteger(Sms.ERROR_CODE);
    }

    public boolean getLocked() {
        return mValues.getAsBoolean(Sms.LOCKED);
    }

    public int getPerson() {
        return mValues.getAsInteger(Sms.PERSON);
    }

    public int getProtocol() {
        return mValues.getAsInteger(Sms.PROTOCOL);
    }

    public boolean getRead() {
        return mValues.getAsBoolean(Sms.READ);
    }

    public boolean getReplyPathPresent() {
        return mValues.getAsBoolean(Sms.REPLY_PATH_PRESENT);
    }

    public boolean getSeen() {
        return mValues.getAsBoolean(Sms.SEEN);
    }

    public String getServiceCenter() {
        return mValues.getAsString(Sms.SERVICE_CENTER);
    }

    public int getStatus() {
        return mValues.getAsInteger(Sms.STATUS);
    }

    public String getSubject() {
        return mValues.getAsString(Sms.SUBJECT);
    }

    public int getThreadId() {
        return mValues.getAsInteger(Sms.THREAD_ID);
    }

    public int getType() {
        return mValues.getAsInteger(Sms.TYPE);
    }
}
