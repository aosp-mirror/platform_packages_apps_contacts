/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.common.model.account;

import android.accounts.AuthenticatorDescription;
import android.content.Context;

import com.android.contacts.R;
import com.android.contacts.common.model.dataitem.DataKind;

import java.util.Collections;

/**
 * Account type for SIM card contacts
 */
public class SimAccountType extends BaseAccountType {

    public SimAccountType(Context context) {
        this.titleRes = R.string.account_sim;
        this.iconRes = R.drawable.ic_sim_card_tinted_24dp;

        try {
            addDataKindDisplayName(context);
            // SIM cards probably don't natively support full structured name data (separate
            // first and last names) but restricting to just a single field in
            // StructuredNameEditorView will require more significant changes.
            addDataKindStructuredName(context);

            final DataKind phoneKind = addDataKindPhone(context);
            phoneKind.typeOverallMax = 1;
            // SIM card contacts don't necessarily support separate types (based on data exposed
            // in Samsung and LG Contacts Apps.
            phoneKind.typeList = Collections.emptyList();

            mIsInitialized = true;
        } catch (DefinitionException e) {
            // Just fail fast. Because we're explicitly adding the fields in this class this
            // exception should only happen in case of a bug.
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean areContactsWritable() {
        return true;
    }

    @Override
    public boolean isGroupMembershipEditable() {
        return false;
    }

    @Override
    public void initializeFieldsFromAuthenticator(AuthenticatorDescription authenticator) {
        // Do nothing. We want to use our local icon and title
    }
}
