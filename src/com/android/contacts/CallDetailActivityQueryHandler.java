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

package com.android.contacts;

import android.content.AsyncQueryHandler;
import android.database.Cursor;
import android.net.Uri;
import android.provider.VoicemailContract.Status;
import android.provider.VoicemailContract.Voicemails;
import android.util.Log;

import com.android.common.io.MoreCloseables;
import com.android.contacts.voicemail.VoicemailStatusHelperImpl;

/**
 * Class used by {@link CallDetailActivity} to fire async content resolver queries.
 */
public class CallDetailActivityQueryHandler extends AsyncQueryHandler {
    private static final String TAG = "CallDetail";
    private static final int QUERY_VOICEMAIL_CONTENT_TOKEN = 101;
    private static final int QUERY_VOICEMAIL_STATUS_TOKEN = 102;

    private final String[] VOICEMAIL_CONTENT_PROJECTION = new String[] {
        Voicemails.SOURCE_PACKAGE,
        Voicemails.HAS_CONTENT
    };
    private static final int SOURCE_PACKAGE_COLUMN_INDEX = 0;
    private static final int HAS_CONTENT_COLUMN_INDEX = 1;

    private final CallDetailActivity mCallDetailActivity;

    public CallDetailActivityQueryHandler(CallDetailActivity callDetailActivity) {
        super(callDetailActivity.getContentResolver());
        mCallDetailActivity = callDetailActivity;
    }

    /**
     * Fires a query to update voicemail status for the given voicemail record. On completion of the
     * query a call to {@link CallDetailActivity#updateVoicemailStatusMessage(Cursor)} is made.
     * <p>
     * if this is a voicemail record then it makes up to two asynchronous content resolver queries.
     * The first one to fetch voicemail content details and check if the voicemail record has audio.
     * If the voicemail record does not have an audio yet then it fires the second query to get the
     * voicemail status of the associated source.
     */
    public void startVoicemailStatusQuery(Uri voicemailUri) {
        startQuery(QUERY_VOICEMAIL_CONTENT_TOKEN, null, voicemailUri, VOICEMAIL_CONTENT_PROJECTION,
                null, null, null);
    }

    @Override
    protected synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
        try {
            if (token == QUERY_VOICEMAIL_CONTENT_TOKEN) {
                // Query voicemail status only if this voicemail record does not have audio.
                if (moveToFirst(cursor) && hasNoAudio(cursor)) {
                    startQuery(QUERY_VOICEMAIL_STATUS_TOKEN, null,
                            Status.buildSourceUri(getSourcePackage(cursor)),
                            VoicemailStatusHelperImpl.PROJECTION, null, null, null);
                } else {
                    // nothing to show in status
                    mCallDetailActivity.updateVoicemailStatusMessage(null);
                }
            } else if (token == QUERY_VOICEMAIL_STATUS_TOKEN) {
                mCallDetailActivity.updateVoicemailStatusMessage(cursor);
            } else {
                Log.w(TAG, "Unknown query completed: ignoring: " + token);
            }
        } finally {
            MoreCloseables.closeQuietly(cursor);
        }
    }

    /** Check that the cursor is non-null and can be moved to first. */
    private boolean moveToFirst(Cursor cursor) {
        if (cursor == null || !cursor.moveToFirst()) {
            Log.e(TAG, "Cursor not valid, could not move to first");
            return false;
        }
        return true;
    }

    private boolean hasNoAudio(Cursor voicemailCursor) {
        return voicemailCursor.getInt(HAS_CONTENT_COLUMN_INDEX) == 0;
    }

    private String getSourcePackage(Cursor voicemailCursor) {
        return voicemailCursor.getString(SOURCE_PACKAGE_COLUMN_INDEX);
    }
}
