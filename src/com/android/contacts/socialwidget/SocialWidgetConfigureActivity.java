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
 * limitations under the License.
 */

package com.android.contacts.socialwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;

public class SocialWidgetConfigureActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If the user presses back, we want to cancel
        setResult(RESULT_CANCELED);

        // Forward the Intent to the picker
        final Intent pickerIntent = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
        pickerIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(pickerIntent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // We came back from the Picker. If the user actually selected a contact,
        // return it now
        if (resultCode == Activity.RESULT_OK) {
            final Bundle extras = getIntent().getExtras();
            if (extras == null) throw new IllegalStateException("Intent extras are null");
            final int widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

            // Save the setting
            final SocialWidgetConfigureActivity context = SocialWidgetConfigureActivity.this;
            SocialWidgetSettings.getInstance().setContactUri(context, widgetId, data.getData());

            // Update the widget
            SocialWidgetProvider.loadWidgetData(
                    context, AppWidgetManager.getInstance(this), widgetId, true);

            // Return OK so that the system won't remove the widget
            final Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            setResult(RESULT_OK, resultValue);
        }
        finish();
    }
}
