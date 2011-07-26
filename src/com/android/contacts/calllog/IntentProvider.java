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

package com.android.contacts.calllog;

import com.android.contacts.CallDetailActivity;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;

/**
 * Used to create an intent to attach to an action in the call log.
 * <p>
 * The intent is constructed lazily with the given information.
 */
public abstract class IntentProvider {
    public abstract Intent getIntent(Context context);

    public static IntentProvider getReturnCallIntentProvider(final String number) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                // Here, "number" can either be a PSTN phone number or a
                // SIP address.  So turn it into either a tel: URI or a
                // sip: URI, as appropriate.
                Uri uri;
                if (PhoneNumberUtils.isUriNumber(number)) {
                    uri = Uri.fromParts("sip", number, null);
                } else {
                    uri = Uri.fromParts("tel", number, null);
                }
                return new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
            }
        };
    }

    public static IntentProvider getPlayVoicemailIntentProvider(final long rowId,
            final String voicemailUri) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = new Intent(context, CallDetailActivity.class);
                intent.setData(ContentUris.withAppendedId(
                        Calls.CONTENT_URI_WITH_VOICEMAIL, rowId));
                if (voicemailUri != null) {
                    intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI,
                            Uri.parse(voicemailUri));
                }
                intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, true);
                return intent;
            }
        };
    }
}
