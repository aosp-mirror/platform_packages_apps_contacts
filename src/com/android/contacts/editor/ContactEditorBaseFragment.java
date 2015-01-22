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
 * limitations under the License
 */

package com.android.contacts.editor;

import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.quickcontact.QuickContactActivity;

import android.app.Fragment;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.QuickContact;

import java.util.ArrayList;

/**
 * Base Fragment for contact editors.
 */
abstract public class ContactEditorBaseFragment extends Fragment {

    protected static final String TAG = "ContactCompactEditor";

    /**
     * Callbacks for Activities that host contact editors Fragments.
     */
    public interface Listener {

        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete
         */
        void onContactNotFound();

        /**
         * Contact was split, so we can close now.
         *
         * @param newLookupUri The lookup uri of the new contact that should be shown to the user.
         *                     The editor tries best to chose the most natural contact here.
         */
        void onContactSplit(Uri newLookupUri);

        /**
         * User has tapped Revert, close the fragment now.
         */
        void onReverted();

        /**
         * Contact was saved and the Fragment can now be closed safely.
         */
        void onSaveFinished(Intent resultIntent);

        /**
         * User switched to editing a different contact (a suggestion from the
         * aggregation engine).
         */
        void onEditOtherContactRequested(Uri contactLookupUri,
                ArrayList<ContentValues> contentValues);

        /**
         * Contact is being created for an external account that provides its own
         * new contact activity.
         */
        void onCustomCreateContactActivityRequested(AccountWithDataSet account,
                Bundle intentExtras);

        /**
         * The edited raw contact belongs to an external account that provides
         * its own edit activity.
         *
         * @param redirect indicates that the current editor should be closed
         *                 before the custom editor is shown.
         */
        void onCustomEditContactActivityRequested(AccountWithDataSet account, Uri rawContactUri,
                Bundle intentExtras, boolean redirect);

        /**
         *
         */
        void onDeleteRequested(Uri contactUri);
    }

    /**
     * Returns a legacy version of the given contactLookupUri if a legacy Uri was originally
     * passed to the contact editor.
     *
     * @param contactLookupUri The Uri to possibly convert to legacy format.
     * @param requestLookupUri The lookup Uri originally passed to the contact editor
     *                         (via Intent data), may be null.
     */
    protected static Uri maybeConvertToLegacyLookupUri(Context context, Uri contactLookupUri,
            Uri requestLookupUri) {
        final String legacyAuthority = "contacts";
        final String requestAuthority = requestLookupUri == null
                ? null : requestLookupUri.getAuthority();
        if (legacyAuthority.equals(requestAuthority)) {
            // Build a legacy Uri if that is what was requested by caller
            final long contactId = ContentUris.parseId(Contacts.lookupContact(
                    context.getContentResolver(), contactLookupUri));
            final Uri legacyContentUri = Uri.parse("content://contacts/people");
            return ContentUris.withAppendedId(legacyContentUri, contactId);
        }
        // Otherwise pass back a lookup-style Uri
        return contactLookupUri;
    }

    /**
     * Creates the result Intent for the given contactLookupUri that should started after a
     * successful saving a contact.
     */
    protected static Intent composeQuickContactsIntent(Context context, Uri contactLookupUri) {
        final Intent intent = QuickContact.composeQuickContactsIntent(
                context, (Rect) null, contactLookupUri, QuickContactActivity.MODE_FULLY_EXPANDED,
                /* excludedMimes =*/ null);
        // Make sure not to show QuickContacts on top of another QuickContacts.
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }
}
