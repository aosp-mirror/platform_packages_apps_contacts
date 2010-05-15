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
package com.android.contacts.list;

import com.android.contacts.ContactsUtils;
import com.android.contacts.PhoneDisambigDialog;
import com.android.contacts.R;

import android.app.LoaderManagingFragment;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts.Data;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * Checks provider status and configures a list adapter accordingly.
 */
public class ProviderStatusLoader {

    private final CursorLoader mLoader;

    public ProviderStatusLoader(CursorLoader loader) {
        this.mLoader = loader;
    }

    public int getProviderStatus() {
        // This query can be performed on the UI thread because
        // the API explicitly allows such use.
        Cursor cursor = mLoader.getContext().getContentResolver().query(
                ProviderStatus.CONTENT_URI,
                new String[] { ProviderStatus.STATUS, ProviderStatus.DATA1 }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }

        return ProviderStatus.STATUS_NORMAL;
    }


//  View importFailureView = findViewById(R.id.import_failure);
//  if (importFailureView == null) {
//      return true;
//  }
//
//  TextView messageView = (TextView) findViewById(R.id.emptyText);
//
//  // This query can be performed on the UI thread because
//  // the API explicitly allows such use.
//  Cursor cursor = getContentResolver().query(ProviderStatus.CONTENT_URI,
//          new String[] { ProviderStatus.STATUS, ProviderStatus.DATA1 }, null, null, null);
//  if (cursor != null) {
//      try {
//          if (cursor.moveToFirst()) {
//              int status = cursor.getInt(0);
//              if (status != mProviderStatus) {
//                  mProviderStatus = status;
//                  switch (status) {
//                      case ProviderStatus.STATUS_NORMAL:
//                          mAdapter.notifyDataSetInvalidated();
//                          if (loadData) {
//                              startQuery();
//                          }
//                          break;
//
//                      case ProviderStatus.STATUS_CHANGING_LOCALE:
//                          messageView.setText(R.string.locale_change_in_progress);
//                          mAdapter.changeCursor(null);
//                          mAdapter.notifyDataSetInvalidated();
//                          break;
//
//                      case ProviderStatus.STATUS_UPGRADING:
//                          messageView.setText(R.string.upgrade_in_progress);
//                          mAdapter.changeCursor(null);
//                          mAdapter.notifyDataSetInvalidated();
//                          break;
//
//                      case ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY:
//                          long size = cursor.getLong(1);
//                          String message = getResources().getString(
//                                  R.string.upgrade_out_of_memory, new Object[] {size});
//                          messageView.setText(message);
//                          configureImportFailureView(importFailureView);
//                          mAdapter.changeCursor(null);
//                          mAdapter.notifyDataSetInvalidated();
//                          break;
//                  }
//              }
//          }
//      } finally {
//          cursor.close();
//      }
//  }
//
//  importFailureView.setVisibility(
//          mProviderStatus == ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY
//                  ? View.VISIBLE
//                  : View.GONE);
//  return mProviderStatus == ProviderStatus.STATUS_NORMAL;
//}

//
//    /**
//     * Obtains the contacts provider status and configures the UI accordingly.
//     *
//     * @param loadData true if the method needs to start a query when the
//     *            provider is in the normal state
//     * @return true if the provider status is normal
//     */
//    private boolean checkProviderState(boolean loadData) {
//        View importFailureView = findViewById(R.id.import_failure);
//        if (importFailureView == null) {
//            return true;
//        }
//
//        TextView messageView = (TextView) findViewById(R.id.emptyText);
//
//        // This query can be performed on the UI thread because
//        // the API explicitly allows such use.
//        Cursor cursor = getContentResolver().query(ProviderStatus.CONTENT_URI,
//                new String[] { ProviderStatus.STATUS, ProviderStatus.DATA1 }, null, null, null);
//        if (cursor != null) {
//            try {
//                if (cursor.moveToFirst()) {
//                    int status = cursor.getInt(0);
//                    if (status != mProviderStatus) {
//                        mProviderStatus = status;
//                        switch (status) {
//                            case ProviderStatus.STATUS_NORMAL:
//                                mAdapter.notifyDataSetInvalidated();
//                                if (loadData) {
//                                    startQuery();
//                                }
//                                break;
//
//                            case ProviderStatus.STATUS_CHANGING_LOCALE:
//                                messageView.setText(R.string.locale_change_in_progress);
//                                mAdapter.changeCursor(null);
//                                mAdapter.notifyDataSetInvalidated();
//                                break;
//
//                            case ProviderStatus.STATUS_UPGRADING:
//                                messageView.setText(R.string.upgrade_in_progress);
//                                mAdapter.changeCursor(null);
//                                mAdapter.notifyDataSetInvalidated();
//                                break;
//
//                            case ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY:
//                                long size = cursor.getLong(1);
//                                String message = getResources().getString(
//                                        R.string.upgrade_out_of_memory, new Object[] {size});
//                                messageView.setText(message);
//                                configureImportFailureView(importFailureView);
//                                mAdapter.changeCursor(null);
//                                mAdapter.notifyDataSetInvalidated();
//                                break;
//                        }
//                    }
//                }
//            } finally {
//                cursor.close();
//            }
//        }
//
//        importFailureView.setVisibility(
//                mProviderStatus == ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY
//                        ? View.VISIBLE
//                        : View.GONE);
//        return mProviderStatus == ProviderStatus.STATUS_NORMAL;
//    }
//
//    private void configureImportFailureView(View importFailureView) {
//
//        OnClickListener listener = new OnClickListener(){
//
//            public void onClick(View v) {
//                switch(v.getId()) {
//                    case R.id.import_failure_uninstall_apps: {
//                        startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
//                        break;
//                    }
//                    case R.id.import_failure_retry_upgrade: {
//                        // Send a provider status update, which will trigger a retry
//                        ContentValues values = new ContentValues();
//                        values.put(ProviderStatus.STATUS, ProviderStatus.STATUS_UPGRADING);
//                        getContentResolver().update(ProviderStatus.CONTENT_URI, values, null, null);
//                        break;
//                    }
//                }
//            }};
//
//        Button uninstallApps = (Button) findViewById(R.id.import_failure_uninstall_apps);
//        uninstallApps.setOnClickListener(listener);
//
//        Button retryUpgrade = (Button) findViewById(R.id.import_failure_retry_upgrade);
//        retryUpgrade.setOnClickListener(listener);
//    }

}
