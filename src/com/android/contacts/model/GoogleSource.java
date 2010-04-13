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
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.google.android.collect.Lists;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts.Data;

import java.util.ArrayList;

public class GoogleSource extends FallbackSource {
    public static final String ACCOUNT_TYPE = "com.google";

    private static final String SELECTION_GROUPS_BY_TITLE_AND_ACCOUNT =
            Groups.TITLE + "=? AND " + Groups.ACCOUNT_NAME + "=? AND " + Groups.ACCOUNT_TYPE + "=?";

    public GoogleSource(String resPackageName) {
        this.accountType = ACCOUNT_TYPE;
        this.resPackageName = null;
        this.summaryResPackageName = resPackageName;
    }

    @Override
    protected void inflate(Context context, int inflateLevel) {

        inflateStructuredName(context, inflateLevel);
        inflateNickname(context, inflateLevel);
        inflatePhone(context, inflateLevel);
        inflateEmail(context, inflateLevel);
        inflateStructuredPostal(context, inflateLevel);
        inflateIm(context, inflateLevel);
        inflateOrganization(context, inflateLevel);
        inflatePhoto(context, inflateLevel);
        inflateNote(context, inflateLevel);
        inflateWebsite(context, inflateLevel);
        inflateEvent(context, inflateLevel);

        // TODO: GOOGLE: GROUPMEMBERSHIP

        setInflatedLevel(inflateLevel);

    }

    @Override
    protected DataKind inflateStructuredName(Context context, int inflateLevel) {
        return super.inflateStructuredName(context, inflateLevel);
    }

    @Override
    protected DataKind inflateNickname(Context context, int inflateLevel) {
        return super.inflateNickname(context, inflateLevel);
    }

    @Override
    protected DataKind inflatePhone(Context context, int inflateLevel) {
        final DataKind kind = super.inflatePhone(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.typeColumn = Phone.TYPE;
            kind.typeList = Lists.newArrayList();
            kind.typeList.add(buildPhoneType(Phone.TYPE_HOME));
            kind.typeList.add(buildPhoneType(Phone.TYPE_MOBILE));
            kind.typeList.add(buildPhoneType(Phone.TYPE_WORK));
            kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_WORK).setSecondary(true));
            kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_HOME).setSecondary(true));
            kind.typeList.add(buildPhoneType(Phone.TYPE_PAGER).setSecondary(true));
            kind.typeList.add(buildPhoneType(Phone.TYPE_OTHER));
            kind.typeList.add(buildPhoneType(Phone.TYPE_CUSTOM).setSecondary(true).setCustomColumn(
                    Phone.LABEL));

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));
        }

        return kind;
    }

    @Override
    protected DataKind inflateEmail(Context context, int inflateLevel) {
        final DataKind kind = super.inflateEmail(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.typeColumn = Email.TYPE;
            kind.typeList = Lists.newArrayList();
            kind.typeList.add(buildEmailType(Email.TYPE_HOME));
            kind.typeList.add(buildEmailType(Email.TYPE_WORK));
            kind.typeList.add(buildEmailType(Email.TYPE_OTHER));
            kind.typeList.add(buildEmailType(Email.TYPE_CUSTOM).setSecondary(true).setCustomColumn(
                    Email.LABEL));

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup, FLAGS_EMAIL));
        }

        return kind;
    }

    @Override
    protected DataKind inflateStructuredPostal(Context context, int inflateLevel) {
        return super.inflateStructuredPostal(context, inflateLevel);
    }

    @Override
    protected DataKind inflateIm(Context context, int inflateLevel) {
        return super.inflateIm(context, inflateLevel);
    }

    @Override
    protected DataKind inflateOrganization(Context context, int inflateLevel) {
        return super.inflateOrganization(context, inflateLevel);
    }

    @Override
    protected DataKind inflatePhoto(Context context, int inflateLevel) {
        return super.inflatePhoto(context, inflateLevel);
    }

    @Override
    protected DataKind inflateNote(Context context, int inflateLevel) {
        return super.inflateNote(context, inflateLevel);
    }

    @Override
    protected DataKind inflateWebsite(Context context, int inflateLevel) {
        return super.inflateWebsite(context, inflateLevel);
    }

    @Override
    public int getHeaderColor(Context context) {
        return 0xff89c2c2;
    }

    @Override
    public int getSideBarColor(Context context) {
        return 0xff5bb4b4;
    }
}
