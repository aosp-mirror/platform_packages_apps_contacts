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

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
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

    public static synchronized Sources getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new Sources(context);
        }
        return sInstance;
    }

    public static final String ACCOUNT_TYPE_GOOGLE = "com.google.GAIA";
    public static final String ACCOUNT_TYPE_EXCHANGE = "com.android.exchange";

    private HashMap<String, ContactsSource> mSources = new HashMap<String, ContactsSource>();

    private Sources(Context context) {
        mSources.put(ACCOUNT_TYPE_GOOGLE, buildGoogle(context));
        mSources.put(ACCOUNT_TYPE_EXCHANGE, buildExchange(context));
    }

    /**
     * Find the {@link ContactsSource} for the given
     * {@link Contacts#ACCOUNT_TYPE}.
     */
    public ContactsSource getSourceForType(String accountType) {
        return mSources.get(accountType);
    }

    private static final int FLAGS_PHONE = EditorInfo.TYPE_CLASS_PHONE;
    private static final int FLAGS_EMAIL = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    private static final int FLAGS_PERSON_NAME = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS | EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME;
    private static final int FLAGS_PHONETIC = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_PHONETIC;
    private static final int FLAGS_GENERIC_NAME = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int FLAGS_NOTE = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
    private static final int FLAGS_WEBSITE = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_URI;
    private static final int FLAGS_POSTAL = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
            | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;

    /**
     * Hard-coded instance of {@link ContactsSource} for Google Contacts.
     */
    private ContactsSource buildGoogle(Context context) {
        final ContactsSource list = new ContactsSource();
        list.accountType = ACCOUNT_TYPE_GOOGLE;
        list.resPackageName = context.getPackageName();

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

            kind.actionHeader = new ActionLabelInflater(list.resPackageName, kind);
            kind.actionBody = new SimpleInflater(Phone.NUMBER);

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
            kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));

            list.add(kind);
        }

        {
            // GOOGLE: EMAIL
            DataKind kind = new DataKind(Email.CONTENT_ITEM_TYPE,
                    R.string.emailLabelsGroup, android.R.drawable.sym_action_email, 15, true);

            kind.actionHeader = new ActionLabelInflater(list.resPackageName, kind);
            kind.actionBody = new SimpleInflater(Email.DATA);

            kind.typeColumn = Email.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(Email.TYPE_HOME, R.string.type_home));
            kind.typeList.add(new EditType(Email.TYPE_WORK, R.string.type_work));
            kind.typeList.add(new EditType(Email.TYPE_OTHER, R.string.type_other));
            kind.typeList.add(new EditType(Email.TYPE_CUSTOM, R.string.type_custom, true, -1,
                    Email.LABEL));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup, FLAGS_EMAIL));

            list.add(kind);
        }

        {
            // GOOGLE: IM
            DataKind kind = new DataKind(Im.CONTENT_ITEM_TYPE, R.string.imLabelsGroup,
                    android.R.drawable.sym_action_chat, 20, true);

            kind.actionHeader = new ActionLabelInflater(list.resPackageName, kind);
            kind.actionBody = new SimpleInflater(Im.DATA);

            // NOTE: even though a traditional "type" exists, for editing
            // purposes we're using the network to pick labels

            kind.defaultValues = new ContentValues();
            kind.defaultValues.put(Im.TYPE, Im.TYPE_OTHER);

            kind.typeColumn = Im.PROTOCOL;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(Im.PROTOCOL_AIM, R.string.type_im_aim));
            kind.typeList.add(new EditType(Im.PROTOCOL_MSN, R.string.type_im_msn));
            kind.typeList.add(new EditType(Im.PROTOCOL_YAHOO, R.string.type_im_yahoo));
            kind.typeList.add(new EditType(Im.PROTOCOL_SKYPE, R.string.type_im_skype));
            kind.typeList.add(new EditType(Im.PROTOCOL_QQ, R.string.type_im_qq));
            kind.typeList.add(new EditType(Im.PROTOCOL_GOOGLE_TALK, R.string.type_im_google_talk));
            kind.typeList.add(new EditType(Im.PROTOCOL_ICQ, R.string.type_im_icq));
            kind.typeList.add(new EditType(Im.PROTOCOL_JABBER, R.string.type_im_jabber));
            kind.typeList.add(new EditType(Im.PROTOCOL_CUSTOM, R.string.type_custom, true, -1,
                    Im.CUSTOM_PROTOCOL));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Im.DATA, R.string.imLabelsGroup, FLAGS_EMAIL));

            list.add(kind);
        }

        {
            // GOOGLE: POSTAL
            DataKind kind = new DataKind(StructuredPostal.CONTENT_ITEM_TYPE,
                    R.string.postalLabelsGroup, R.drawable.sym_action_map, 25, true);

            kind.actionHeader = new ActionLabelInflater(list.resPackageName, kind);
            // TODO: build body from various structured fields
            kind.actionBody = new SimpleInflater(StructuredPostal.FORMATTED_ADDRESS);

            kind.typeColumn = StructuredPostal.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(StructuredPostal.TYPE_HOME, R.string.type_home));
            kind.typeList.add(new EditType(StructuredPostal.TYPE_WORK, R.string.type_work));
            kind.typeList.add(new EditType(StructuredPostal.TYPE_OTHER, R.string.type_other));
            kind.typeList.add(new EditType(StructuredPostal.TYPE_CUSTOM, R.string.type_custom,
                    true, -1, StructuredPostal.LABEL));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(StructuredPostal.AGENT, -1, FLAGS_POSTAL, true));
            kind.fieldList.add(new EditField(StructuredPostal.HOUSENAME, -1, FLAGS_POSTAL, true));
            kind.fieldList.add(new EditField(StructuredPostal.STREET, -1, FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.POBOX, -1, FLAGS_POSTAL, true));
            kind.fieldList.add(new EditField(StructuredPostal.NEIGHBORHOOD, -1, FLAGS_POSTAL, true));
            kind.fieldList.add(new EditField(StructuredPostal.CITY, -1, FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.SUBREGION, -1, FLAGS_POSTAL, true));
            kind.fieldList.add(new EditField(StructuredPostal.REGION, -1, FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.POSTCODE, -1, FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.COUNTRY, -1, FLAGS_POSTAL, true));

            list.add(kind);
        }

        {
            // GOOGLE: ORGANIZATION
            DataKind kind = new DataKind(Organization.CONTENT_ITEM_TYPE,
                    R.string.organizationLabelsGroup, R.drawable.sym_action_organization, 30, true);

            kind.actionHeader = new SimpleInflater(list.resPackageName, R.string.organizationLabelsGroup);
            // TODO: build body from multiple fields
            kind.actionBody = new SimpleInflater(Organization.TITLE);

            kind.typeColumn = Organization.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(Organization.TYPE_WORK, R.string.type_work));
            kind.typeList.add(new EditType(Organization.TYPE_OTHER, R.string.type_other));
            kind.typeList.add(new EditType(Organization.TYPE_CUSTOM, R.string.type_custom, true,
                    -1, Organization.LABEL));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Organization.COMPANY, R.string.ghostData_company,
                    FLAGS_GENERIC_NAME));
            kind.fieldList.add(new EditField(Organization.TITLE, R.string.ghostData_title,
                    FLAGS_GENERIC_NAME));

            list.add(kind);
        }

        {
            // GOOGLE: NOTE
            DataKind kind = new DataKind(Note.CONTENT_ITEM_TYPE,
                    R.string.label_notes, R.drawable.sym_note, 110, true);
            kind.secondary = true;

            kind.actionHeader = new SimpleInflater(list.resPackageName, R.string.label_notes);
            kind.actionBody = new SimpleInflater(Note.NOTE);

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Note.NOTE, R.string.label_notes, FLAGS_NOTE));

            list.add(kind);
        }

        {
            // GOOGLE: NICKNAME
            DataKind kind = new DataKind(Nickname.CONTENT_ITEM_TYPE,
                    R.string.nicknameLabelsGroup, -1, 115, true);
            kind.secondary = true;

            kind.actionHeader = new SimpleInflater(list.resPackageName, R.string.nicknameLabelsGroup);
            kind.actionBody = new SimpleInflater(Nickname.NAME);

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Nickname.NAME, R.string.nicknameLabelsGroup,
                    FLAGS_PERSON_NAME));

            list.add(kind);
        }

        // TODO: GOOGLE: GROUPMEMBERSHIP
        // TODO: GOOGLE: WEBSITE

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
    private ContactsSource buildExchange(Context context) {
        final ContactsSource list = new ContactsSource();
        list.accountType = ACCOUNT_TYPE_EXCHANGE;
        list.resPackageName = context.getPackageName();

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

            kind.actionHeader = new ActionLabelInflater(list.resPackageName, kind);
            kind.actionBody = new SimpleInflater(Phone.NUMBER);

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
            kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));

            list.add(kind);
        }

        {
            // EXCHANGE: EMAIL
            DataKind kind = new DataKind(Email.CONTENT_ITEM_TYPE,
                    R.string.emailLabelsGroup, android.R.drawable.sym_action_email, 15, true);

            kind.actionHeader = new ActionLabelInflater(list.resPackageName, kind);
            kind.actionBody = new SimpleInflater(Email.DATA);

            kind.typeColumn = Email.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(TYPE_EMAIL1, R.string.type_email_1, false, 1));
            kind.typeList.add(new EditType(TYPE_EMAIL2, R.string.type_email_2, false, 1));
            kind.typeList.add(new EditType(TYPE_EMAIL3, R.string.type_email_3, false, 1));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup, FLAGS_EMAIL));

            list.add(kind);
        }

        {
            // EXCHANGE: IM
            DataKind kind = new DataKind(Im.CONTENT_ITEM_TYPE, R.string.imLabelsGroup,
                    android.R.drawable.sym_action_chat, 20, true);

            kind.actionHeader = new ActionLabelInflater(list.resPackageName, kind);
            kind.actionBody = new SimpleInflater(Im.DATA);

            kind.typeColumn = Im.TYPE;
            kind.typeList = new ArrayList<EditType>();
            kind.typeList.add(new EditType(TYPE_IM1, R.string.type_im_1, false, 1));
            kind.typeList.add(new EditType(TYPE_IM2, R.string.type_im_2, false, 1));
            kind.typeList.add(new EditType(TYPE_IM3, R.string.type_im_3, false, 1));

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Im.DATA, R.string.imLabelsGroup, FLAGS_EMAIL));

            list.add(kind);
        }

        {
            // EXCHANGE: NICKNAME
            DataKind kind = new DataKind(Nickname.CONTENT_ITEM_TYPE,
                    R.string.nicknameLabelsGroup, -1, 115, true);
            kind.secondary = true;
            kind.typeOverallMax = 1;

            kind.actionHeader = new SimpleInflater(list.resPackageName, R.string.nicknameLabelsGroup);
            kind.actionBody = new SimpleInflater(Nickname.NAME);

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Nickname.NAME, R.string.nicknameLabelsGroup,
                    FLAGS_PERSON_NAME));

            list.add(kind);
        }

        {
            // EXCHANGE: WEBSITE
            DataKind kind = new DataKind(Website.CONTENT_ITEM_TYPE,
                    R.string.websiteLabelsGroup, -1, 120, true);
            kind.secondary = true;
            kind.typeOverallMax = 1;

            kind.actionHeader = new SimpleInflater(list.resPackageName, R.string.websiteLabelsGroup);
            kind.actionBody = new SimpleInflater(Website.URL);

            kind.fieldList = new ArrayList<EditField>();
            kind.fieldList.add(new EditField(Website.URL, R.string.websiteLabelsGroup, FLAGS_WEBSITE));

            list.add(kind);
        }

        return list;
    }

    /**
     * Simple inflater that assumes a string resource has a "%s" that will be
     * filled from the given column.
     */
    public static class SimpleInflater implements StringInflater {
        private final String mPackageName;
        private final int mStringRes;
        private final String mColumnName;

        public SimpleInflater(String packageName, int stringRes) {
            this(packageName, stringRes, null);
        }

        public SimpleInflater(String columnName) {
            this(null, -1, columnName);
        }

        public SimpleInflater(String packageName, int stringRes, String columnName) {
            mPackageName = packageName;
            mStringRes = stringRes;
            mColumnName = columnName;
        }

        public CharSequence inflateUsing(Context context, Cursor cursor) {
            final int index = mColumnName != null ? cursor.getColumnIndex(mColumnName) : -1;
            final boolean validString = mStringRes > 0;
            final boolean validColumn = index != -1;

            final CharSequence stringValue = validString ? context.getPackageManager().getText(
                    mPackageName, mStringRes, null) : null;
            final CharSequence columnValue = validColumn ? cursor.getString(index) : null;

            if (validString && validColumn) {
                return String.format(stringValue.toString(), columnValue);
            } else if (validString) {
                return stringValue;
            } else if (validColumn) {
                return columnValue;
            } else {
                return null;
            }
        }
    }

    /**
     * Simple inflater that will combine two string resources, usually to
     * provide an action string like "Call home", where "home" is provided from
     * {@link EditType#labelRes}.
     */
    public static class ActionLabelInflater implements StringInflater {
        private String mPackageName;
        private DataKind mKind;

        public ActionLabelInflater(String packageName, DataKind labelProvider) {
            mPackageName = packageName;
            mKind = labelProvider;
        }

        public CharSequence inflateUsing(Context context, Cursor cursor) {
            final EditType type = EntityModifier.getCurrentType(cursor, mKind);
            final boolean validString = type.actionRes > 0;
            return validString ? context.getPackageManager().getText(mPackageName, type.actionRes,
                    null) : null;
        }
    }
}
