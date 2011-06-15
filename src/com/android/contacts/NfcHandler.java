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

import com.android.contacts.detail.ContactDetailFragment;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.net.Uri.Builder;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class NfcHandler implements NfcAdapter.NdefPushCallback {
    private NfcAdapter mNfcAdapter;
    private ContactDetailFragment mContactFragment;
    private static final String TAG = "ContactsNfcHandler";

    public NfcHandler(ContactDetailFragment contactFragment) {
        mContactFragment = contactFragment;
        mNfcAdapter = NfcAdapter.getDefaultAdapter(
                mContactFragment.getActivity());
    }

    public void onPause() {
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundNdefPush(
                    mContactFragment.getActivity());
        }
    }

    public void onResume() {
        if (mNfcAdapter != null) {
            mNfcAdapter.enableForegroundNdefPush(
                    mContactFragment.getActivity(), this);
        }
    }

    @Override
    public NdefMessage createMessage() {
        // Get the current contact URI
        Uri contactUri = mContactFragment.getUri();
        ContentResolver resolver = mContactFragment.getActivity().getContentResolver();
        if (contactUri != null) {
            final String lookupKey = Uri.encode(contactUri.getPathSegments().get(2));
            final Uri shareUri = Contacts.CONTENT_VCARD_URI.buildUpon().
                    appendPath(lookupKey).
                    appendQueryParameter(Contacts.QUERY_PARAMETER_VCARD_NO_PHOTO, "true").
                    build();
            ByteArrayOutputStream ndefBytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int r;
            try {
                InputStream vcardInputStream = resolver.openInputStream(shareUri);
                vcardInputStream = resolver.openInputStream(shareUri);
                while ((r = vcardInputStream.read(buffer)) > 0) {
                    ndefBytes.write(buffer, 0, r);
                }

                NdefRecord vcardRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
                        "text/x-vCard".getBytes(), new byte[]{}, ndefBytes.toByteArray());
                return new NdefMessage(new NdefRecord[] {vcardRecord});
            } catch (IOException e) {
                Log.e(TAG, "IOException creating vcard.");
                return null;
            }
        } else {
            Log.w(TAG, "No contact URI to share.");
            return null;
        }
    }

    @Override
    public void onMessagePushed() {
        // We may add a sound/notification here at some point.
    }
}
