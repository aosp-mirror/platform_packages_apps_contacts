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

package com.android.contacts.model;

import com.android.contacts.R;

import android.content.Context;

public class FallbackAccountType extends BaseAccountType {

    public FallbackAccountType(Context context) {
        this.accountType = null;
        this.dataSet = null;
        this.titleRes = R.string.account_phone;
        this.iconRes = R.mipmap.ic_launcher_contacts;

        this.resPackageName = null;
        this.summaryResPackageName = resPackageName;

        addDataKindStructuredName(context);
        addDataKindDisplayName(context);
        addDataKindPhoneticName(context);
        addDataKindNickname(context);
        addDataKindPhone(context);
        addDataKindEmail(context);
        addDataKindStructuredPostal(context);
        addDataKindIm(context);
        addDataKindOrganization(context);
        addDataKindPhoto(context);
        addDataKindNote(context);
        addDataKindWebsite(context);
        addDataKindSipAddress(context);
    }

    @Override
    public int getHeaderColor(Context context) {
        return 0xff7f93bc;
    }

    @Override
    public int getSideBarColor(Context context) {
        return 0xffbdc7b8;
    }

    @Override
    public boolean areContactsWritable() {
        return true;
    }
}
