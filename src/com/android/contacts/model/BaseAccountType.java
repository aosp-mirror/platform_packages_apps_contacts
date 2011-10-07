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
import com.google.android.collect.Lists;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.BaseTypes;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.view.inputmethod.EditorInfo;

public abstract class BaseAccountType extends AccountType {
    protected static final int FLAGS_PHONE = EditorInfo.TYPE_CLASS_PHONE;
    protected static final int FLAGS_EMAIL = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    protected static final int FLAGS_PERSON_NAME = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS | EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME;
    protected static final int FLAGS_PHONETIC = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_PHONETIC;
    protected static final int FLAGS_GENERIC_NAME = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;
    protected static final int FLAGS_NOTE = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
    protected static final int FLAGS_EVENT = EditorInfo.TYPE_CLASS_TEXT;
    protected static final int FLAGS_WEBSITE = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_URI;
    protected static final int FLAGS_POSTAL = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
            | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
    protected static final int FLAGS_SIP_ADDRESS = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;  // since SIP addresses have the same
                                                             // basic format as email addresses

    public BaseAccountType() {
        this.accountType = null;
        this.dataSet = null;
        this.titleRes = R.string.account_phone;
        this.iconRes = R.mipmap.ic_launcher_contacts;
    }

    protected EditType buildPhoneType(int type) {
        return new EditType(type, Phone.getTypeLabelResource(type));
    }

    protected EditType buildEmailType(int type) {
        return new EditType(type, Email.getTypeLabelResource(type));
    }

    protected EditType buildPostalType(int type) {
        return new EditType(type, StructuredPostal.getTypeLabelResource(type));
    }

    protected EditType buildImType(int type) {
        return new EditType(type, Im.getProtocolLabelResource(type));
    }

    protected EditType buildEventType(int type, boolean yearOptional) {
        return new EventEditType(type, Event.getTypeResource(type)).setYearOptional(yearOptional);
    }

    protected EditType buildRelationType(int type) {
        return new EditType(type, Relation.getTypeLabelResource(type));
    }

    protected DataKind addDataKindStructuredName(Context context) {
        DataKind kind = addKind(new DataKind(StructuredName.CONTENT_ITEM_TYPE,
                R.string.nameLabelsGroup, -1, true, R.layout.structured_name_editor_view));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(StructuredName.DISPLAY_NAME,
                R.string.full_name, FLAGS_PERSON_NAME));
        kind.fieldList.add(new EditField(StructuredName.PREFIX, R.string.name_prefix,
                FLAGS_PERSON_NAME).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                FLAGS_PERSON_NAME).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.MIDDLE_NAME, R.string.name_middle,
                FLAGS_PERSON_NAME).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                FLAGS_PERSON_NAME).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.SUFFIX, R.string.name_suffix,
                FLAGS_PERSON_NAME).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                R.string.name_phonetic_family, FLAGS_PHONETIC));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_MIDDLE_NAME,
                R.string.name_phonetic_middle, FLAGS_PHONETIC));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                R.string.name_phonetic_given, FLAGS_PHONETIC));

        return kind;
    }

    protected DataKind addDataKindDisplayName(Context context) {
        DataKind kind = addKind(new DataKind(DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME,
                R.string.nameLabelsGroup, -1, true, R.layout.text_fields_editor_view));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(StructuredName.DISPLAY_NAME,
                R.string.full_name, FLAGS_PERSON_NAME).setShortForm(true));

        boolean displayOrderPrimary =
                context.getResources().getBoolean(R.bool.config_editor_field_order_primary);

        if (!displayOrderPrimary) {
            kind.fieldList.add(new EditField(StructuredName.PREFIX, R.string.name_prefix,
                    FLAGS_PERSON_NAME).setLongForm(true));
            kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                    FLAGS_PERSON_NAME).setLongForm(true));
            kind.fieldList.add(new EditField(StructuredName.MIDDLE_NAME, R.string.name_middle,
                    FLAGS_PERSON_NAME).setLongForm(true));
            kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                    FLAGS_PERSON_NAME).setLongForm(true));
            kind.fieldList.add(new EditField(StructuredName.SUFFIX, R.string.name_suffix,
                    FLAGS_PERSON_NAME).setLongForm(true));
        } else {
            kind.fieldList.add(new EditField(StructuredName.PREFIX, R.string.name_prefix,
                    FLAGS_PERSON_NAME).setLongForm(true));
            kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                    FLAGS_PERSON_NAME).setLongForm(true));
            kind.fieldList.add(new EditField(StructuredName.MIDDLE_NAME, R.string.name_middle,
                    FLAGS_PERSON_NAME).setLongForm(true));
            kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                    FLAGS_PERSON_NAME).setLongForm(true));
            kind.fieldList.add(new EditField(StructuredName.SUFFIX, R.string.name_suffix,
                    FLAGS_PERSON_NAME).setLongForm(true));
        }

        return kind;
    }

    protected DataKind addDataKindPhoneticName(Context context) {
        DataKind kind = addKind(new DataKind(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME,
                R.string.name_phonetic, -1, true, R.layout.phonetic_name_editor_view));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(DataKind.PSEUDO_COLUMN_PHONETIC_NAME,
                R.string.name_phonetic, FLAGS_PHONETIC).setShortForm(true));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                R.string.name_phonetic_family, FLAGS_PHONETIC).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_MIDDLE_NAME,
                R.string.name_phonetic_middle, FLAGS_PHONETIC).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                R.string.name_phonetic_given, FLAGS_PHONETIC).setLongForm(true));

        return kind;
    }

    protected DataKind addDataKindNickname(Context context) {
        DataKind kind = addKind(new DataKind(Nickname.CONTENT_ITEM_TYPE,
                    R.string.nicknameLabelsGroup, 115, true, R.layout.text_fields_editor_view));
        kind.typeOverallMax = 1;
        kind.actionHeader = new SimpleInflater(R.string.nicknameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);
        kind.defaultValues = new ContentValues();
        kind.defaultValues.put(Nickname.TYPE, Nickname.TYPE_DEFAULT);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Nickname.NAME, R.string.nicknameLabelsGroup,
                FLAGS_PERSON_NAME));

        return kind;
    }

    protected DataKind addDataKindPhone(Context context) {
        DataKind kind = addKind(new DataKind(Phone.CONTENT_ITEM_TYPE, R.string.phoneLabelsGroup,
                10, true, R.layout.text_fields_editor_view));
        kind.iconAltRes = R.drawable.ic_text_holo_light;
        kind.iconAltDescriptionRes = R.string.sms;
        kind.actionHeader = new PhoneActionInflater();
        kind.actionAltHeader = new PhoneActionAltInflater();
        kind.actionBody = new SimpleInflater(Phone.NUMBER);
        kind.typeColumn = Phone.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildPhoneType(Phone.TYPE_HOME));
        kind.typeList.add(buildPhoneType(Phone.TYPE_MOBILE));
        kind.typeList.add(buildPhoneType(Phone.TYPE_WORK));
        kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_WORK).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_HOME).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_PAGER).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_OTHER));
        kind.typeList.add(
                buildPhoneType(Phone.TYPE_CUSTOM).setSecondary(true).setCustomColumn(Phone.LABEL));
        kind.typeList.add(buildPhoneType(Phone.TYPE_CALLBACK).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_CAR).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_COMPANY_MAIN).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_ISDN).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_MAIN).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_OTHER_FAX).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_RADIO).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_TELEX).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_TTY_TDD).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_WORK_MOBILE).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_WORK_PAGER).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_ASSISTANT).setSecondary(true).setCustomColumn(
                Phone.LABEL));
        kind.typeList.add(buildPhoneType(Phone.TYPE_MMS).setSecondary(true));

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));

        return kind;
    }

    protected DataKind addDataKindEmail(Context context) {
        DataKind kind = addKind(new DataKind(Email.CONTENT_ITEM_TYPE, R.string.emailLabelsGroup,
                15, true, R.layout.text_fields_editor_view));
        kind.actionHeader = new EmailActionInflater();
        kind.actionBody = new SimpleInflater(Email.DATA);
        kind.typeColumn = Email.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildEmailType(Email.TYPE_HOME));
        kind.typeList.add(buildEmailType(Email.TYPE_WORK));
        kind.typeList.add(buildEmailType(Email.TYPE_OTHER));
        kind.typeList.add(buildEmailType(Email.TYPE_MOBILE));
        kind.typeList.add(
                buildEmailType(Email.TYPE_CUSTOM).setSecondary(true).setCustomColumn(Email.LABEL));

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup, FLAGS_EMAIL));

        return kind;
    }

    protected DataKind addDataKindStructuredPostal(Context context) {
        DataKind kind = addKind(new DataKind(StructuredPostal.CONTENT_ITEM_TYPE,
                R.string.postalLabelsGroup, 25, true, R.layout.text_fields_editor_view));
        kind.actionHeader = new PostalActionInflater();
        kind.actionBody = new SimpleInflater(StructuredPostal.FORMATTED_ADDRESS);
        kind.typeColumn = StructuredPostal.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_HOME));
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_WORK));
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_OTHER));
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_CUSTOM).setSecondary(true)
                .setCustomColumn(StructuredPostal.LABEL));

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(
                new EditField(StructuredPostal.FORMATTED_ADDRESS, R.string.postal_address,
                        FLAGS_POSTAL));

        return kind;
    }

    protected DataKind addDataKindIm(Context context) {
        DataKind kind = addKind(new DataKind(Im.CONTENT_ITEM_TYPE, R.string.imLabelsGroup, 20, true,
                    R.layout.text_fields_editor_view));
        kind.actionHeader = new ImActionInflater();
        kind.actionBody = new SimpleInflater(Im.DATA);

        // NOTE: even though a traditional "type" exists, for editing
        // purposes we're using the protocol to pick labels

        kind.defaultValues = new ContentValues();
        kind.defaultValues.put(Im.TYPE, Im.TYPE_OTHER);

        kind.typeColumn = Im.PROTOCOL;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildImType(Im.PROTOCOL_AIM));
        kind.typeList.add(buildImType(Im.PROTOCOL_MSN));
        kind.typeList.add(buildImType(Im.PROTOCOL_YAHOO));
        kind.typeList.add(buildImType(Im.PROTOCOL_SKYPE));
        kind.typeList.add(buildImType(Im.PROTOCOL_QQ));
        kind.typeList.add(buildImType(Im.PROTOCOL_GOOGLE_TALK));
        kind.typeList.add(buildImType(Im.PROTOCOL_ICQ));
        kind.typeList.add(buildImType(Im.PROTOCOL_JABBER));
        kind.typeList.add(buildImType(Im.PROTOCOL_CUSTOM).setSecondary(true).setCustomColumn(
                Im.CUSTOM_PROTOCOL));

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Im.DATA, R.string.imLabelsGroup, FLAGS_EMAIL));

        return kind;
    }

    protected DataKind addDataKindOrganization(Context context) {
        DataKind kind = addKind(new DataKind(Organization.CONTENT_ITEM_TYPE,
                    R.string.organizationLabelsGroup, 5, true,
                    R.layout.text_fields_editor_view));
        kind.actionHeader = new SimpleInflater(Organization.COMPANY);
        kind.actionBody = new SimpleInflater(Organization.TITLE);
        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Organization.COMPANY, R.string.ghostData_company,
                FLAGS_GENERIC_NAME));
        kind.fieldList.add(new EditField(Organization.TITLE, R.string.ghostData_title,
                FLAGS_GENERIC_NAME));

        return kind;
    }

    protected DataKind addDataKindPhoto(Context context) {
        DataKind kind = addKind(new DataKind(Photo.CONTENT_ITEM_TYPE, -1, -1, true, -1));
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Photo.PHOTO, -1, -1));
        return kind;
    }

    protected DataKind addDataKindNote(Context context) {
        DataKind kind = addKind(new DataKind(Note.CONTENT_ITEM_TYPE,
                    R.string.label_notes, 110, true, R.layout.text_fields_editor_view));
        kind.typeOverallMax = 1;
        kind.actionHeader = new SimpleInflater(R.string.label_notes);
        kind.actionBody = new SimpleInflater(Note.NOTE);
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Note.NOTE, R.string.label_notes, FLAGS_NOTE));

        return kind;
    }

    protected DataKind addDataKindWebsite(Context context) {
        DataKind kind = addKind(new DataKind(Website.CONTENT_ITEM_TYPE,
                R.string.websiteLabelsGroup, 120, true, R.layout.text_fields_editor_view));
        kind.actionHeader = new SimpleInflater(R.string.websiteLabelsGroup);
        kind.actionBody = new SimpleInflater(Website.URL);
        kind.defaultValues = new ContentValues();
        kind.defaultValues.put(Website.TYPE, Website.TYPE_OTHER);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Website.URL, R.string.websiteLabelsGroup, FLAGS_WEBSITE));

        return kind;
    }

    protected DataKind addDataKindSipAddress(Context context) {
        // The icon specified here is the one that gets displayed for
        // "Internet call" items, in the "view contact" UI within the
        // Contacts app.
        //
        // This is independent of the "SIP call" icon that gets
        // displayed in the Quick Contacts widget, which comes from
        // the android:icon attribute of the SIP-related
        // intent-filters in the Phone app's manifest.
        DataKind kind = addKind(new DataKind(SipAddress.CONTENT_ITEM_TYPE,
                    R.string.label_sip_address, 130, true, R.layout.text_fields_editor_view));

        kind.typeOverallMax = 1;
        kind.actionHeader = new SimpleInflater(R.string.label_sip_address);
        kind.actionBody = new SimpleInflater(SipAddress.SIP_ADDRESS);
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(SipAddress.SIP_ADDRESS,
                                         R.string.label_sip_address, FLAGS_SIP_ADDRESS));

        return kind;
    }

    protected DataKind addDataKindGroupMembership(Context context) {
        DataKind kind = getKindForMimetype(GroupMembership.CONTENT_ITEM_TYPE);
        kind = addKind(new DataKind(GroupMembership.CONTENT_ITEM_TYPE,
                R.string.groupsLabel, 999, true, -1));

        kind.typeOverallMax = 1;
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(GroupMembership.GROUP_ROW_ID, -1, -1));

        return kind;
    }

    /**
     * Simple inflater that assumes a string resource has a "%s" that will be
     * filled from the given column.
     */
    public static class SimpleInflater implements StringInflater {
        private final int mStringRes;
        private final String mColumnName;

        public SimpleInflater(int stringRes) {
            this(stringRes, null);
        }

        public SimpleInflater(String columnName) {
            this(-1, columnName);
        }

        public SimpleInflater(int stringRes, String columnName) {
            mStringRes = stringRes;
            mColumnName = columnName;
        }

        public CharSequence inflateUsing(Context context, Cursor cursor) {
            final int index = mColumnName != null ? cursor.getColumnIndex(mColumnName) : -1;
            final boolean validString = mStringRes > 0;
            final boolean validColumn = index != -1;

            final CharSequence stringValue = validString ? context.getText(mStringRes) : null;
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

        public CharSequence inflateUsing(Context context, ContentValues values) {
            final boolean validColumn = values.containsKey(mColumnName);
            final boolean validString = mStringRes > 0;

            final CharSequence stringValue = validString ? context.getText(mStringRes) : null;
            final CharSequence columnValue = validColumn ? values.getAsString(mColumnName) : null;

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

    public static abstract class CommonInflater implements StringInflater {
        protected abstract int getTypeLabelResource(Integer type);

        protected boolean isCustom(Integer type) {
            return type == BaseTypes.TYPE_CUSTOM;
        }

        protected String getTypeColumn() {
            return Phone.TYPE;
        }

        protected String getLabelColumn() {
            return Phone.LABEL;
        }

        protected CharSequence getTypeLabel(Resources res, Integer type, CharSequence label) {
            final int labelRes = getTypeLabelResource(type);
            if (type == null) {
                return res.getText(labelRes);
            } else if (isCustom(type)) {
                return res.getString(labelRes, label == null ? "" : label);
            } else {
                return res.getText(labelRes);
            }
        }

        public CharSequence inflateUsing(Context context, Cursor cursor) {
            final Integer type = cursor.getInt(cursor.getColumnIndex(getTypeColumn()));
            final String label = cursor.getString(cursor.getColumnIndex(getLabelColumn()));
            return getTypeLabel(context.getResources(), type, label);
        }

        public CharSequence inflateUsing(Context context, ContentValues values) {
            final Integer type = values.getAsInteger(getTypeColumn());
            final String label = values.getAsString(getLabelColumn());
            return getTypeLabel(context.getResources(), type, label);
        }
    }

    public static class PhoneActionInflater extends CommonInflater {
        @Override
        protected boolean isCustom(Integer type) {
            return type == Phone.TYPE_CUSTOM || type == Phone.TYPE_ASSISTANT;
        }

        @Override
        protected int getTypeLabelResource(Integer type) {
            if (type == null) return R.string.call_other;
            switch (type) {
                case Phone.TYPE_HOME: return R.string.call_home;
                case Phone.TYPE_MOBILE: return R.string.call_mobile;
                case Phone.TYPE_WORK: return R.string.call_work;
                case Phone.TYPE_FAX_WORK: return R.string.call_fax_work;
                case Phone.TYPE_FAX_HOME: return R.string.call_fax_home;
                case Phone.TYPE_PAGER: return R.string.call_pager;
                case Phone.TYPE_OTHER: return R.string.call_other;
                case Phone.TYPE_CALLBACK: return R.string.call_callback;
                case Phone.TYPE_CAR: return R.string.call_car;
                case Phone.TYPE_COMPANY_MAIN: return R.string.call_company_main;
                case Phone.TYPE_ISDN: return R.string.call_isdn;
                case Phone.TYPE_MAIN: return R.string.call_main;
                case Phone.TYPE_OTHER_FAX: return R.string.call_other_fax;
                case Phone.TYPE_RADIO: return R.string.call_radio;
                case Phone.TYPE_TELEX: return R.string.call_telex;
                case Phone.TYPE_TTY_TDD: return R.string.call_tty_tdd;
                case Phone.TYPE_WORK_MOBILE: return R.string.call_work_mobile;
                case Phone.TYPE_WORK_PAGER: return R.string.call_work_pager;
                case Phone.TYPE_ASSISTANT: return R.string.call_assistant;
                case Phone.TYPE_MMS: return R.string.call_mms;
                default: return R.string.call_custom;
            }
        }
    }

    public static class PhoneActionAltInflater extends CommonInflater {
        @Override
        protected boolean isCustom(Integer type) {
            return (type == Phone.TYPE_CUSTOM || type == Phone.TYPE_ASSISTANT);
        }

        @Override
        protected int getTypeLabelResource(Integer type) {
            if (type == null) return R.string.sms_other;
            switch (type) {
                case Phone.TYPE_HOME: return R.string.sms_home;
                case Phone.TYPE_MOBILE: return R.string.sms_mobile;
                case Phone.TYPE_WORK: return R.string.sms_work;
                case Phone.TYPE_FAX_WORK: return R.string.sms_fax_work;
                case Phone.TYPE_FAX_HOME: return R.string.sms_fax_home;
                case Phone.TYPE_PAGER: return R.string.sms_pager;
                case Phone.TYPE_OTHER: return R.string.sms_other;
                case Phone.TYPE_CALLBACK: return R.string.sms_callback;
                case Phone.TYPE_CAR: return R.string.sms_car;
                case Phone.TYPE_COMPANY_MAIN: return R.string.sms_company_main;
                case Phone.TYPE_ISDN: return R.string.sms_isdn;
                case Phone.TYPE_MAIN: return R.string.sms_main;
                case Phone.TYPE_OTHER_FAX: return R.string.sms_other_fax;
                case Phone.TYPE_RADIO: return R.string.sms_radio;
                case Phone.TYPE_TELEX: return R.string.sms_telex;
                case Phone.TYPE_TTY_TDD: return R.string.sms_tty_tdd;
                case Phone.TYPE_WORK_MOBILE: return R.string.sms_work_mobile;
                case Phone.TYPE_WORK_PAGER: return R.string.sms_work_pager;
                case Phone.TYPE_ASSISTANT: return R.string.sms_assistant;
                case Phone.TYPE_MMS: return R.string.sms_mms;
                default: return R.string.sms_custom;
            }
        }
    }

    public static class EmailActionInflater extends CommonInflater {
        @Override
        protected int getTypeLabelResource(Integer type) {
            if (type == null) return R.string.email;
            switch (type) {
                case Email.TYPE_HOME: return R.string.email_home;
                case Email.TYPE_WORK: return R.string.email_work;
                case Email.TYPE_OTHER: return R.string.email_other;
                case Email.TYPE_MOBILE: return R.string.email_mobile;
                default: return R.string.email_custom;
            }
        }
    }

    public static class EventActionInflater extends CommonInflater {
        @Override
        protected int getTypeLabelResource(Integer type) {
            return Event.getTypeResource(type);
        }
    }

    public static class RelationActionInflater extends CommonInflater {
        @Override
        protected int getTypeLabelResource(Integer type) {
            return Relation.getTypeLabelResource(type == null ? Relation.TYPE_CUSTOM : type);
        }
    }

    public static class PostalActionInflater extends CommonInflater {
        @Override
        protected int getTypeLabelResource(Integer type) {
            if (type == null) return R.string.map_other;
            switch (type) {
                case StructuredPostal.TYPE_HOME: return R.string.map_home;
                case StructuredPostal.TYPE_WORK: return R.string.map_work;
                case StructuredPostal.TYPE_OTHER: return R.string.map_other;
                default: return R.string.map_custom;
            }
        }
    }

    public static class ImActionInflater extends CommonInflater {
        @Override
        protected String getTypeColumn() {
            return Im.PROTOCOL;
        }

        @Override
        protected String getLabelColumn() {
            return Im.CUSTOM_PROTOCOL;
        }

        @Override
        protected int getTypeLabelResource(Integer type) {
            if (type == null) return R.string.chat;
            switch (type) {
                case Im.PROTOCOL_AIM: return R.string.chat_aim;
                case Im.PROTOCOL_MSN: return R.string.chat_msn;
                case Im.PROTOCOL_YAHOO: return R.string.chat_yahoo;
                case Im.PROTOCOL_SKYPE: return R.string.chat_skype;
                case Im.PROTOCOL_QQ: return R.string.chat_qq;
                case Im.PROTOCOL_GOOGLE_TALK: return R.string.chat_gtalk;
                case Im.PROTOCOL_ICQ: return R.string.chat_icq;
                case Im.PROTOCOL_JABBER: return R.string.chat_jabber;
                case Im.PROTOCOL_NETMEETING: return R.string.chat;
                default: return R.string.chat;
            }
        }
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
    public boolean isGroupMembershipEditable() {
        return false;
    }
}
