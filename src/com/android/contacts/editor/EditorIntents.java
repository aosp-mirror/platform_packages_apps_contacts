/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.contacts.editor;

import com.android.contacts.activities.CompactContactEditorActivity;
import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.activities.ContactEditorBaseActivity;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;

import java.util.ArrayList;

/**
 * Creates Intents to edit contacts.
 */
public class EditorIntents {

    private EditorIntents() {
    }

    /**
     * Returns an Intent to start the {@link CompactContactEditorActivity} for an
     * existing contact.
     */
    public static Intent createCompactEditContactIntent(Uri contactLookupUri,
            MaterialPalette materialPalette, Bundle updatedPhotos, long photoId, long nameId) {
        final Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
        putMaterialPalette(intent, materialPalette);
        putUpdatedPhotos(intent, updatedPhotos);
        putPhotoId(intent, photoId);
        putNameId(intent, nameId);
        return intent;
    }

    /**
     * Returns an Intent to start the {@link CompactContactEditorActivity} for a new contact.
     */
    public static Intent createCompactInsertContactIntent() {
        return createCompactInsertContactIntent(/* rawContactDeltaList =*/ null,
                /* displayName =*/ null, /* phoneticName =*/ null, /* updatedPhotos =*/ null);
    }

    /**
     * Returns an Intent to start the {@link CompactContactEditorActivity} for a new contact with
     * the field values specified by rawContactDeltaList pre-populate in the form.
     */
    public static Intent createCompactInsertContactIntent(RawContactDeltaList rawContactDeltaList,
            String displayName, String phoneticName, Bundle updatedPhotos) {
        final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
        if (rawContactDeltaList != null || displayName != null || phoneticName != null) {
            putRawContactDeltaValues(intent, rawContactDeltaList, displayName, phoneticName);
        }
        putUpdatedPhotos(intent, updatedPhotos);
        return intent;
    }

    /**
     * Returns an Intent to edit a different contact (in the fully expaned editor) with whatever
     * values were already entered on the currently displayed contact editor.
     */
    public static Intent createEditOtherContactIntent(Uri contactLookupUri,
            ArrayList<ContentValues> contentValues) {
        final Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        intent.putExtra(ContactEditorFragment.INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY, "");

        // Pass on all the data that has been entered so far
        if (contentValues != null && contentValues.size() != 0) {
            intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, contentValues);
        }
        return intent;
    }

    /**
     * Returns an Intent to start the fully expanded {@link ContactEditorActivity} for a
     * new contact.
     */
    public static Intent createEditContactIntent(Uri contactLookupUri,
            MaterialPalette materialPalette, long photoId, long nameId) {
        final Intent intent = new Intent(ContactEditorBaseActivity.ACTION_EDIT, contactLookupUri);
        addContactIntentFlags(intent);
        putMaterialPalette(intent, materialPalette);
        putPhotoId(intent, photoId);
        putNameId(intent, nameId);
        return intent;
    }

    /**
     * Returns an Intent to start the fully expanded {@link ContactEditorActivity} for an
     * existing contact.
     */
    public static Intent createInsertContactIntent(RawContactDeltaList rawContactDeltaList,
            String displayName, String phoneticName, Bundle updatedPhotos) {
        final Intent intent = new Intent(ContactEditorBaseActivity.ACTION_INSERT,
                Contacts.CONTENT_URI);
        addContactIntentFlags(intent);
        putRawContactDeltaValues(intent, rawContactDeltaList, displayName, phoneticName);
        putUpdatedPhotos(intent, updatedPhotos);
        return intent;
    }


    private static void addContactIntentFlags(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
    }

    private static void putMaterialPalette(Intent intent, MaterialPalette materialPalette) {
        if (materialPalette != null) {
            intent.putExtra(ContactEditorBaseFragment.INTENT_EXTRA_MATERIAL_PALETTE_PRIMARY_COLOR,
                    materialPalette.mPrimaryColor);
            intent.putExtra(ContactEditorBaseFragment.INTENT_EXTRA_MATERIAL_PALETTE_SECONDARY_COLOR,
                    materialPalette.mSecondaryColor);
        }
    }

    private static void putUpdatedPhotos(Intent intent, Bundle updatedPhotos) {
        if (updatedPhotos != null && !updatedPhotos.isEmpty()) {
            intent.putExtra(ContactEditorBaseFragment.INTENT_EXTRA_UPDATED_PHOTOS, updatedPhotos);
        }
    }

    private static void putPhotoId(Intent intent, long photoId) {
        if (photoId >= 0) {
            intent.putExtra(ContactEditorBaseFragment.INTENT_EXTRA_PHOTO_ID, photoId);
        }
    }

    private static void putNameId(Intent intent, long nameId) {
        if (nameId >= 0) {
            intent.putExtra(ContactEditorBaseFragment.INTENT_EXTRA_NAME_ID, nameId);
        }
    }

    private static void putRawContactDeltaValues(Intent intent,
            RawContactDeltaList rawContactDeltaList, String displayName, String phoneticName) {
        // Pass on all the data that has been entered so far
        if (rawContactDeltaList != null && !rawContactDeltaList.isEmpty()) {
            ArrayList<ContentValues> contentValues = rawContactDeltaList.get(0).getContentValues();
            if (contentValues != null && contentValues.size() != 0) {
                intent.putParcelableArrayListExtra(
                        ContactsContract.Intents.Insert.DATA, contentValues);
            }
        }
        // Names must be passed separately since they are skipped in RawContactModifier.parseValues
        if (!TextUtils.isEmpty(displayName)) {
            intent.putExtra(ContactsContract.Intents.Insert.NAME, displayName);
        }
        if (!TextUtils.isEmpty(phoneticName)) {
            intent.putExtra(ContactsContract.Intents.Insert.PHONETIC_NAME, phoneticName);
        }
    }
}
