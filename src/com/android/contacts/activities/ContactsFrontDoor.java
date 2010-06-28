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
 * limitations under the License
 */

package com.android.contacts.activities;

import com.android.contacts.DialtactsActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

public class ContactsFrontDoor extends Activity {
    public static final String EXTRA_FRONT_DOOR = "front_door";

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        Intent intent = new Intent();
        intent.setFlags(
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        intent.putExtra(EXTRA_FRONT_DOOR, true);

        // The user launched the config based front door, pick the right activity to go to
        Configuration config = getResources().getConfiguration();
        int screenLayoutSize = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        if (screenLayoutSize == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            // XL screen, use two pane UI
            intent.setClass(this, TwoPaneActivity.class);
        } else {
            // Default to the normal dialtacts layout
            intent.setClass(this, DialtactsActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
