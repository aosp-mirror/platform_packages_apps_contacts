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

import android.content.ContentValues;
import android.database.Cursor;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/*

<!-- example of what SourceConstraints would look like in XML -->
<!-- NOTE: may not directly match the current structure version -->

<DataKind
    mimeType="vnd.android.cursor.item/email"
    title="@string/title_postal"
    icon="@drawable/icon_postal"
    weight="12"
    editable="true">

    <!-- these are defined using string-builder-ish -->
    <ActionHeader></ActionHeader>
    <ActionBody socialSummary="true" />  <!-- can pull together various columns -->

    <!-- ordering handles precedence the "insert/add" case -->
    <!-- assume uniform type when missing "column", use title in place -->
    <EditTypes column="data5" overallMax="-1">
        <EditType rawValue="0" label="@string/type_home" specificMax="-1" />
        <EditType rawValue="1" label="@string/type_work" specificMax="-1" secondary="true" />
        <EditType rawValue="4" label="@string/type_custom" customColumn="data6" specificMax="-1" secondary="true" />
    </EditTypes>

    <!-- when single edit field, simplifies edit case -->
    <EditField column="data1" title="@string/field_family_name" android:inputType="textCapWords|textPhonetic" />
    <EditField column="data2" title="@string/field_given_name" android:minLines="2" />
    <EditField column="data3" title="@string/field_suffix" />

</DataKind>

*/

/**
 * Internal structure that represents constraints for a specific data source,
 * such as the various data types they support, including details on how those
 * types should be rendered and edited.
 * <p>
 * In the future this may be inflated from XML defined by a data source.
 */
public class ContactsSource {
    /**
     * The {@link RawContacts#ACCOUNT_TYPE} these constraints apply to.
     */
    public String accountType;

    /**
     * Set of {@link DataKind} supported by this source.
     */
    private ArrayList<DataKind> mKinds = new ArrayList<DataKind>();

    /**
     * {@link Comparator} to sort by {@link DataKind#weight}.
     */
    private static Comparator<DataKind> sWeightComparator = new Comparator<DataKind>() {
        public int compare(DataKind object1, DataKind object2) {
            return object1.weight - object2.weight;
        }
    };

    /**
     * Return list of {@link DataKind} supported, sorted by
     * {@link DataKind#weight}.
     */
    public ArrayList<DataKind> getSortedDataKinds() {
        // TODO: optimize by marking if already sorted
        Collections.sort(mKinds, sWeightComparator);
        return mKinds;
    }

    /**
     * Find the {@link DataKind} for a specifc MIME-type, if it's handled by
     * this data source.
     */
    public DataKind getKindForMimetype(String mimeType) {
        for (DataKind kind : mKinds) {
            if (mimeType.equals(kind.mimeType)) {
                return kind;
            }
        }
        return null;
    }

    public void add(DataKind kind) {
        this.mKinds.add(kind);
    }

    /**
     * Description of a specific data type, usually marked by a unique
     * {@link Data#MIMETYPE}. Includes details about how to view and edit
     * {@link Data} rows of this kind, including the possible {@link EditType}
     * labels and editable {@link EditField}.
     */
    public static class DataKind {
        public String mimeType;
        public int titleRes;
        public int iconRes;
        public int weight;
        public boolean secondary;
        public boolean editable;

        public StringInflater actionHeader;
        public StringInflater actionBody;
        public boolean actionBodySocial;
        public boolean actionBodyCombine;

        public String typeColumn;
        public int typeOverallMax;

        public List<EditType> typeList;
        public List<EditField> fieldList;

        public ContentValues defaultValues;

        public DataKind(String mimeType, int titleRes, int iconRes, int weight, boolean editable) {
            this.mimeType = mimeType;
            this.titleRes = titleRes;
            this.iconRes = iconRes;
            this.weight = weight;
            this.editable = editable;
            this.typeOverallMax = -1;
        }
    }

    /**
     * Description of a specific "type" or "label" of a {@link DataKind} row,
     * such as {@link Phone#TYPE_WORK}. Includes constraints on total number of
     * rows a {@link Contacts} may have of this type, and details on how
     * user-defined labels are stored.
     */
    public static class EditType {
        public int rawValue;
        public int labelRes;
        public boolean secondary;
        public int specificMax;
        public String customColumn;

        public EditType(int rawValue, int labelRes) {
            this.rawValue = rawValue;
            this.labelRes = labelRes;
            this.specificMax = -1;
        }

        public EditType(int rawValue, int labelRes, boolean secondary) {
            this(rawValue, labelRes);
            this.secondary = secondary;
        }

        public EditType(int rawValue, int labelRes, boolean secondary, int specificMax) {
            this(rawValue, labelRes, secondary);
            this.specificMax = specificMax;
        }

        public EditType(int rawValue, int labelRes, boolean secondary, int specificMax, String customColumn) {
            this(rawValue, labelRes, secondary, specificMax);
            this.customColumn = customColumn;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof EditType) {
                final EditType other = (EditType)object;
                return other.rawValue == rawValue;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return rawValue;
        }
    }

    /**
     * Description of a user-editable field on a {@link DataKind} row, such as
     * {@link Phone#NUMBER}. Includes flags to apply to an {@link EditText}, and
     * the column where this field is stored.
     */
    public static class EditField {
        public String column;
        public int titleRes;
        public int inputType;
        public int minLines;
        public boolean optional;

        public EditField(String column, int titleRes) {
            this.column = column;
            this.titleRes = titleRes;
        }

        public EditField(String column, int titleRes, int inputType) {
            this(column, titleRes);
            this.inputType = inputType;
        }

        public EditField(String column, int titleRes, int inputType, boolean optional) {
            this(column, titleRes, inputType);
            this.optional = optional;
        }
    }

    /**
     * Generic method of inflating a given {@link Cursor} into a user-readable
     * {@link CharSequence}. For example, an inflater could combine the multiple
     * columns of {@link StructuredPostal} together using a string resource
     * before presenting to the user.
     */
    public interface StringInflater {
        public CharSequence inflateUsing(Cursor cursor);
    }

}
