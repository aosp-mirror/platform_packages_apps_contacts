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

package com.android.contacts.model;

import com.android.contacts.R;
import com.android.contacts.model.AccountType.EditField;
import com.android.contacts.model.AccountType.EditType;
import com.android.contacts.model.AccountType.StringInflater;
import com.google.common.collect.Iterators;

import android.content.ContentValues;
import android.provider.ContactsContract.Data;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Description of a specific data type, usually marked by a unique
 * {@link Data#MIMETYPE}. Includes details about how to view and edit
 * {@link Data} rows of this kind, including the possible {@link EditType}
 * labels and editable {@link EditField}.
 */
public final class DataKind {

    public static final String PSEUDO_MIME_TYPE_DISPLAY_NAME = "#displayName";
    public static final String PSEUDO_MIME_TYPE_PHONETIC_NAME = "#phoneticName";
    public static final String PSEUDO_COLUMN_PHONETIC_NAME = "#phoneticName";

    public String resPackageName;
    public String mimeType;
    public int titleRes;
    public int iconAltRes;
    public int iconAltDescriptionRes;
    public int weight;
    public boolean editable;

    public StringInflater actionHeader;
    public StringInflater actionAltHeader;
    public StringInflater actionBody;

    public boolean actionBodySocial = false;

    public String typeColumn;

    /**
     * Maximum number of values allowed in the list. -1 represents infinity.
     */
    public int typeOverallMax;

    public List<EditType> typeList;
    public List<EditField> fieldList;

    public ContentValues defaultValues;

    /** Layout resource id for an editor view to edit this {@link DataKind}. */
    public final int editorLayoutResourceId;

    /**
     * If this is a date field, this specifies the format of the date when saving. The
     * date includes year, month and day. If this is not a date field or the date field is not
     * editable, this value should be ignored.
     */
    public SimpleDateFormat dateFormatWithoutYear;

    /**
     * If this is a date field, this specifies the format of the date when saving. The
     * date includes month and day. If this is not a date field, the field is not editable or
     * dates without year are not supported, this value should be ignored.
     */
    public SimpleDateFormat dateFormatWithYear;

    public DataKind() {
        editorLayoutResourceId = R.layout.text_fields_editor_view;
    }

    public DataKind(String mimeType, int titleRes, int weight, boolean editable,
            int editorLayoutResourceId) {
        this.mimeType = mimeType;
        this.titleRes = titleRes;
        this.weight = weight;
        this.editable = editable;
        this.typeOverallMax = -1;
        this.editorLayoutResourceId = editorLayoutResourceId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DataKind:");
        sb.append(" resPackageName=").append(resPackageName);
        sb.append(" mimeType=").append(mimeType);
        sb.append(" titleRes=").append(titleRes);
        sb.append(" iconAltRes=").append(iconAltRes);
        sb.append(" iconAltDescriptionRes=").append(iconAltDescriptionRes);
        sb.append(" weight=").append(weight);
        sb.append(" editable=").append(editable);
        sb.append(" actionHeader=").append(actionHeader);
        sb.append(" actionAltHeader=").append(actionAltHeader);
        sb.append(" actionBody=").append(actionBody);
        sb.append(" actionBodySocial=").append(actionBodySocial);
        sb.append(" typeColumn=").append(typeColumn);
        sb.append(" typeOverallMax=").append(typeOverallMax);
        sb.append(" typeList=").append(toString(typeList));
        sb.append(" fieldList=").append(toString(fieldList));
        sb.append(" defaultValues=").append(defaultValues);
        sb.append(" editorLayoutResourceId=").append(editorLayoutResourceId);
        sb.append(" dateFormatWithoutYear=").append(toString(dateFormatWithoutYear));
        sb.append(" dateFormatWithYear=").append(toString(dateFormatWithYear));

        return sb.toString();
    }

    public static String toString(SimpleDateFormat format) {
        return format == null ? "(null)" : format.toPattern();
    }

    public static String toString(Iterable<?> list) {
        if (list == null) {
            return "(null)";
        } else {
            return Iterators.toString(list.iterator());
        }
    }
}