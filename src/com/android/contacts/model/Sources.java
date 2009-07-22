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

import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.view.inputmethod.EditorInfo;

import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.ContactsSource.EditField;
import com.android.contacts.model.ContactsSource.StringInflater;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Singleton holder for all parsed {@link ContactsSource} available on the
 * system, typically filled through {@link PackageManager} queries.
 * <p>
 * Some {@link ContactsSource} may be hard-coded here, as the constraint
 * language hasn't been finalized.
 */
public class Sources {
    // TODO: finish hard-coding all constraints

    private static Sources sInstance;

    public static synchronized Sources getInstance() {
        if (sInstance == null) {
            sInstance = new Sources();
        }
        return sInstance;
    }

    public static final String ACCOUNT_TYPE_GOOGLE = "com.google.GAIA";
    public static final String ACCOUNT_TYPE_EXCHANGE = "vnd.exchange";

    private HashMap<String, ContactsSource> mSources = new HashMap<String, ContactsSource>();

    private Sources() {
        mSources.put(ACCOUNT_TYPE_GOOGLE, buildGoogle());
        mSources.put(ACCOUNT_TYPE_EXCHANGE, buildExchange());
    }

    /**
     * Find the {@link ContactsSource} for the given
     * {@link Contacts#ACCOUNT_TYPE}.
     */
    public ContactsSource getKindsForAccountType(String accountType) {
        return mSources.get(accountType);
    }

    /**
     * Hard-coded instance of {@link ContactsSource} for Google Contacts.
     */
    private ContactsSource buildGoogle() {
        final ContactsSource list = new ContactsSource();

        {
            // GOOGLE: STRUCTUREDNAME
            DataKind kind = new DataKind(StructuredName.CONTENT_ITEM_TYPE,
                    R.string.nameLabelsGroup, -1, -1, true);
            list.add(kind);
        }

        {
            // GOOGLE: PHOTO
            DataKind kind = new DataKind(Photo.CONTENT_ITEM_TYPE, -1, -1, -1, true);
            list.add(kind);
        }

        {
            // GOOGLE: PHONE
            DataKind kind = new DataKind(Phone.CONTENT_ITEM_TYPE,
                    R.string.phoneLabelsGroup, android.R.drawable.sym_action_call, 10, true);

            kind.actionHeader = new ActionLabelInflater(R.string.actionCall, kind);
            kind.actionBody = new ColumnInflater(Phone.NUMBER);

            kind.typeColumn = Phone.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(Phone.TYPE_HOME, R.string.type_home));
            kind.typeList.add(new EditType(Phone.TYPE_MOBILE, R.string.type_mobile));
            kind.typeList.add(new EditType(Phone.TYPE_WORK, R.string.type_work));
            kind.typeList.add(new EditType(Phone.TYPE_FAX_WORK, R.string.type_fax_work, true));
            kind.typeList.add(new EditType(Phone.TYPE_FAX_HOME, R.string.type_fax_home, true));
            kind.typeList.add(new EditType(Phone.TYPE_PAGER, R.string.type_pager, true));
            kind.typeList.add(new EditType(Phone.TYPE_OTHER, R.string.type_other));
            kind.typeList.add(new EditType(Phone.TYPE_CUSTOM, R.string.type_custom, true, -1,
                    Phone.LABEL));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup,
                    EditorInfo.TYPE_CLASS_PHONE));

            list.add(kind);
        }

        {
            // GOOGLE: EMAIL
            DataKind kind = new DataKind(Email.CONTENT_ITEM_TYPE,
                    R.string.emailLabelsGroup, android.R.drawable.sym_action_email, 15, true);

            kind.actionHeader = new ActionLabelInflater(R.string.actionEmail, kind);
            kind.actionBody = new ColumnInflater(Email.DATA);

            kind.typeColumn = Email.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(Email.TYPE_HOME, R.string.type_home));
            kind.typeList.add(new EditType(Email.TYPE_WORK, R.string.type_work));
            kind.typeList.add(new EditType(Email.TYPE_OTHER, R.string.type_other));
            kind.typeList.add(new EditType(Email.TYPE_CUSTOM, R.string.type_custom, true, -1,
                    Email.LABEL));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup,
                    EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));

            list.add(kind);
        }

        {
            // GOOGLE: POSTAL
            DataKind kind = new DataKind(StructuredPostal.CONTENT_ITEM_TYPE,
                    R.string.postalLabelsGroup, R.drawable.sym_action_map, 20, true);

            kind.actionHeader = new ActionLabelInflater(R.string.actionMap, kind);

            kind.typeColumn = StructuredPostal.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(StructuredPostal.TYPE_HOME, R.string.type_home));
            kind.typeList.add(new EditType(StructuredPostal.TYPE_WORK, R.string.type_work));
            kind.typeList.add(new EditType(StructuredPostal.TYPE_OTHER, R.string.type_other));
            kind.typeList.add(new EditType(StructuredPostal.TYPE_CUSTOM, R.string.type_custom,
                    true, -1, StructuredPostal.LABEL));

            // TODO: define editors for each field

// EditorInfo.TYPE_CLASS_TEXT
//          | EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
//          | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
//          | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;

//          entry.maxLines = 4;
//          entry.lines = 2;

            list.add(kind);
        }

        // TODO: GOOGLE: IM
//      entry.contentType = EditorInfo.TYPE_CLASS_TEXT
//      | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;

        {
            // GOOGLE: ORGANIZATION
            DataKind kind = new DataKind(Organization.CONTENT_ITEM_TYPE,
                    R.string.organizationLabelsGroup, R.drawable.sym_action_organization, 30, true);

            kind.typeColumn = Organization.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(Organization.TYPE_WORK, R.string.type_work));
            kind.typeList.add(new EditType(Organization.TYPE_OTHER, R.string.type_other));
            kind.typeList.add(new EditType(Organization.TYPE_CUSTOM, R.string.type_custom, true,
                    -1, Organization.LABEL));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Organization.COMPANY, R.string.ghostData_company,
                    EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS));
            kind.fieldList.add(new EditField(Organization.TITLE, R.string.ghostData_title,
                    EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS));

            list.add(kind);
        }

        {
            // GOOGLE: NOTE
            DataKind kind = new DataKind(Note.CONTENT_ITEM_TYPE,
                    R.string.label_notes, R.drawable.sym_note, 110, true);
            kind.secondary = true;

//            kind.actionHeader = new ActionLabelInflater(R.string.ac, kind);
            kind.actionBody = new ColumnInflater(Email.DATA);

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Email.DATA, R.string.label_notes,
                    EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
                            | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE));

            list.add(kind);
        }

        {
            // GOOGLE: NICKNAME
            DataKind kind = new DataKind(Nickname.CONTENT_ITEM_TYPE,
                    R.string.nicknameLabelsGroup, -1, 115, true);
            kind.secondary = true;

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Nickname.NAME, R.string.nicknameLabelsGroup));

            list.add(kind);
        }

        return list;
    }

    /**
     * The constants below are shared with the Exchange sync adapter, and are
     * currently static. These values should be maintained in parallel.
     */
    private static final int TYPE_EMAIL1 = 20;
    private static final int TYPE_EMAIL2 = 21;
    private static final int TYPE_EMAIL3 = 22;

    private static final int TYPE_IM1 = 23;
    private static final int TYPE_IM2 = 24;
    private static final int TYPE_IM3 = 25;

    private static final int TYPE_WORK2 = 26;
    private static final int TYPE_HOME2 = 27;
    private static final int TYPE_CAR = 28;
    private static final int TYPE_COMPANY_MAIN = 29;
    private static final int TYPE_MMS = 30;
    private static final int TYPE_RADIO = 31;

    /**
     * Hard-coded instance of {@link ContactsSource} for Exchange.
     */
    private ContactsSource buildExchange() {
        final ContactsSource list = new ContactsSource();

        {
            // EXCHANGE: STRUCTUREDNAME
            DataKind kind = new DataKind(StructuredName.CONTENT_ITEM_TYPE,
                    R.string.nameLabelsGroup, -1, -1, true);
            kind.typeOverallMax = 1;
            list.add(kind);
        }

        {
            // EXCHANGE: PHOTO
            DataKind kind = new DataKind(Photo.CONTENT_ITEM_TYPE, -1, -1, -1, true);
            kind.typeOverallMax = 1;
            list.add(kind);
        }

        {
            // EXCHANGE: PHONE
            DataKind kind = new DataKind(Phone.CONTENT_ITEM_TYPE,
                    R.string.phoneLabelsGroup, android.R.drawable.sym_action_call, 10, true);

            kind.actionHeader = new ActionLabelInflater(R.string.actionCall, kind);
            kind.actionBody = new ColumnInflater(Phone.NUMBER);

            kind.typeColumn = Phone.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(Phone.TYPE_HOME, R.string.type_home, false, 1));
            kind.typeList.add(new EditType(TYPE_HOME2, R.string.type_home_2, true, 1));
            kind.typeList.add(new EditType(Phone.TYPE_MOBILE, R.string.type_mobile, false, 1));
            kind.typeList.add(new EditType(Phone.TYPE_WORK, R.string.type_work, false, 1));
            kind.typeList.add(new EditType(TYPE_WORK2, R.string.type_work_2, true, 1));
            kind.typeList.add(new EditType(Phone.TYPE_FAX_WORK, R.string.type_fax_work, true, 1));
            kind.typeList.add(new EditType(Phone.TYPE_FAX_HOME, R.string.type_fax_home, true, 1));
            kind.typeList.add(new EditType(Phone.TYPE_PAGER, R.string.type_pager, true, 1));
            kind.typeList.add(new EditType(TYPE_CAR, R.string.type_car, true, 1));
            kind.typeList.add(new EditType(TYPE_COMPANY_MAIN, R.string.type_company_main, true, 1));
            kind.typeList.add(new EditType(TYPE_MMS, R.string.type_mms, true, 1));
            kind.typeList.add(new EditType(TYPE_RADIO, R.string.type_radio, true, 1));
            kind.typeList.add(new EditType(Phone.TYPE_CUSTOM, R.string.type_assistant, true, 1,
                    Phone.LABEL));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup));

            list.add(kind);
        }

        {
            // EXCHANGE: EMAIL
            DataKind kind = new DataKind(Email.CONTENT_ITEM_TYPE,
                    R.string.emailLabelsGroup, android.R.drawable.sym_action_email, 15, true);

            kind.actionHeader = new ActionLabelInflater(R.string.actionEmail, kind);
            kind.actionBody = new ColumnInflater(Email.DATA);

            kind.typeColumn = Email.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(TYPE_EMAIL1, R.string.type_email_1, false, 1));
            kind.typeList.add(new EditType(TYPE_EMAIL2, R.string.type_email_2, false, 1));
            kind.typeList.add(new EditType(TYPE_EMAIL3, R.string.type_email_3, false, 1));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup));

            list.add(kind);
        }

        {
            // EXCHANGE: NICKNAME
            DataKind kind = new DataKind(Nickname.CONTENT_ITEM_TYPE,
                    R.string.nicknameLabelsGroup, -1, 115, true);
            kind.secondary = true;

            kind.typeOverallMax = 1;

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Nickname.NAME, R.string.nicknameLabelsGroup));

            list.add(kind);
        }

        {
            // EXCHANGE: WEBSITE
            DataKind kind = new DataKind(Website.CONTENT_ITEM_TYPE,
                    R.string.websiteLabelsGroup, -1, 120, true);
            kind.secondary = true;

            kind.typeOverallMax = 1;

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Website.URL, R.string.websiteLabelsGroup));

            list.add(kind);
        }

        return list;
    }

    /**
     * Simple inflater that assumes a string resource has a "%s" that will be
     * filled from the given column.
     */
    public static class SimpleInflater implements StringInflater {
        // TODO: implement this

        public SimpleInflater(int stringRes, String columnName) {
        }

        public CharSequence inflateUsing(Cursor cursor) {
            return null;
        }
    }

    /**
     * Simple inflater that will combine two string resources, usually to
     * provide an action string like "Call home", where "home" is provided from
     * {@link EditType#labelRes}.
     */
    public static class ActionLabelInflater implements StringInflater {
        // TODO: implement this

        public ActionLabelInflater(int actionRes, DataKind labelProvider) {
        }

        public CharSequence inflateUsing(Cursor cursor) {
            // use the given action string along with localized label name
            return null;
        }
    }

    /**
     * Simple inflater that uses the raw value from the given column.
     */
    public static class ColumnInflater implements StringInflater {
        // TODO: implement this

        public ColumnInflater(String columnName) {
        }

        public CharSequence inflateUsing(Cursor cursor) {
            // return the cursor value for column name
            return null;
        }
    }

}
