/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.FastTrack;

/**
 * Stub translucent activity that just shows {@link FastTrackWindow} floating
 * above the caller. This temporary hack should eventually be replaced with
 * direct framework support.
 */
public final class FastTrackActivity extends Activity implements FastTrackWindow.OnDismissListener {
    private FastTrackWindow mFastTrack;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Use our local window token for now
        final Intent intent = getIntent();
        final Uri lookupUri = intent.getData();
        final Bundle extras = intent.getExtras();

        // Read requested parameters for displaying
        final Rect target = (Rect)extras.getParcelable(FastTrack.EXTRA_TARGET_RECT);
        final int mode = extras.getInt(FastTrack.EXTRA_MODE, FastTrack.MODE_MEDIUM);
        final String[] excludeMimes = extras.getStringArray(FastTrack.EXTRA_EXCLUDE_MIMES);

        mFastTrack = new FastTrackWindow(this, this);
        mFastTrack.show(lookupUri, target, mode, excludeMimes);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mFastTrack.dismiss();
    }

    /** {@inheritDoc} */
    public void onDismiss(FastTrackWindow dialog) {
        // When dismissed, finish this activity
        finish();
    }
}
