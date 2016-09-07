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

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.contacts.R;

/**
 * Account type for SIM card contacts
 *
 * TODO: Right now this is the same as FallbackAccountType with a different icon and label.
 * Instead it should setup it's own DataKinds that are known to work on SIM card.
 */
public class SimAccountType extends FallbackAccountType {

    public SimAccountType(Context context) {
        super(context);
        this.titleRes = R.string.account_sim;
        this.iconRes = R.drawable.ic_sim_card_tinted_24dp;

    }

    @Override
    public boolean isGroupMembershipEditable() {
        return false;
    }
}
