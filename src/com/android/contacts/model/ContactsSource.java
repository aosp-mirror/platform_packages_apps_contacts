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

import com.google.android.collect.Lists;
import com.google.android.collect.Maps;

import android.accounts.Account;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Internal structure that represents constraints and styles for a specific data
 * source, such as the various data types they support, including details on how
 * those types should be rendered and edited.
 * <p>
 * In the future this may be inflated from XML defined by a data source.
 */
public abstract class ContactsSource {
    /**
     * The {@link RawContacts#ACCOUNT_TYPE} these constraints apply to.
     */
    public String accountType = null;

    /**
     * Package that resources should be loaded from, either defined through an
     * {@link Account} or for matching against {@link Data#RES_PACKAGE}.
     */
    public String resPackageName;
    public String summaryResPackageName;

    public int titleRes;
    public int iconRes;

    public boolean readOnly;

    /**
     * Set of {@link DataKind} supported by this source.
     */
    private ArrayList<DataKind> mKinds = Lists.newArrayList();

    /**
     * Lookup map of {@link #mKinds} on {@link DataKind#mimeType}.
     */
    private HashMap<String, DataKind> mMimeKinds = Maps.newHashMap();

    public static final int LEVEL_NONE = 0;
    public static final int LEVEL_SUMMARY = 1;
    public static final int LEVEL_MIMETYPES = 2;
    public static final int LEVEL_CONSTRAINTS = 3;

    private int mInflatedLevel = LEVEL_NONE;

    public synchronized boolean isInflated(int inflateLevel) {
        return mInflatedLevel >= inflateLevel;
    }

    /** @hide exposed for unit tests */
    public void setInflatedLevel(int inflateLevel) {
        mInflatedLevel = inflateLevel;
    }

    /**
     * Ensure that this {@link ContactsSource} has been inflated to the
     * requested level.
     */
    public synchronized void ensureInflated(Context context, int inflateLevel) {
        if (!isInflated(inflateLevel)) {
            inflate(context, inflateLevel);
        }
    }

    /**
     * Perform the actual inflation to the requested level. Called by
     * {@link #ensureInflated(Context, int)} when inflation is needed.
     */
    protected abstract void inflate(Context context, int inflateLevel);

    /**
     * Invalidate any cache for this {@link ContactsSource}, removing all
     * inflated data. Calling {@link #ensureInflated(Context, int)} will
     * populate again from scratch.
     */
    public synchronized void invalidateCache() {
        this.mKinds.clear();
        this.mMimeKinds.clear();
        setInflatedLevel(LEVEL_NONE);
    }

    public CharSequence getDisplayLabel(Context context) {
        if (this.titleRes != -1 && this.summaryResPackageName != null) {
            final PackageManager pm = context.getPackageManager();
            return pm.getText(this.summaryResPackageName, this.titleRes, null);
        } else if (this.titleRes != -1) {
            return context.getText(this.titleRes);
        } else {
            return this.accountType;
        }
    }

    public Drawable getDisplayIcon(Context context) {
        if (this.titleRes != -1 && this.summaryResPackageName != null) {
            final PackageManager pm = context.getPackageManager();
            return pm.getDrawable(this.summaryResPackageName, this.iconRes, null);
        } else if (this.titleRes != -1) {
            return context.getResources().getDrawable(this.iconRes);
        } else {
            return null;
        }
    }

    abstract public int getHeaderColor(Context context);

    abstract public int getSideBarColor(Context context);

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
     * Find the {@link DataKind} for a specific MIME-type, if it's handled by
     * this data source. If you may need a fallback {@link DataKind}, use
     * {@link Sources#getKindOrFallback(String, String, Context, int)}.
     */
    public DataKind getKindForMimetype(String mimeType) {
        return this.mMimeKinds.get(mimeType);
    }

    /**
     * Add given {@link DataKind} to list of those provided by this source.
     */
    public DataKind addKind(DataKind kind) {
        kind.resPackageName = this.resPackageName;
        this.mKinds.add(kind);
        this.mMimeKinds.put(kind.mimeType, kind);
        return kind;
    }

    /**
     * Description of a specific data type, usually marked by a unique
     * {@link Data#MIMETYPE}. Includes details about how to view and edit
     * {@link Data} rows of this kind, including the possible {@link EditType}
     * labels and editable {@link EditField}.
     */
    public static class DataKind {
        public String resPackageName;
        public String mimeType;
        public int titleRes;
        public int iconRes;
        public int iconAltRes;
        public int weight;
        public boolean secondary;
        public boolean editable;

        /**
         * If this is true (default), the user can add and remove values.
         * If false, the editor will always show a single field (which might be empty).
         */
        public boolean isList;

        public StringInflater actionHeader;
        public StringInflater actionAltHeader;
        public StringInflater actionBody;

        public boolean actionBodySocial = false;

        public String typeColumn;

        /**
         * Maximum number of values allowed in the list. -1 represents infinity.
         * If {@link DataKind#isList} is false, this value is ignored.
         */
        public int typeOverallMax;

        public List<EditType> typeList;
        public List<EditField> fieldList;

        public ContentValues defaultValues;

        public DataKind() {
        }

        public DataKind(String mimeType, int titleRes, int iconRes, int weight, boolean editable) {
            this.mimeType = mimeType;
            this.titleRes = titleRes;
            this.iconRes = iconRes;
            this.weight = weight;
            this.editable = editable;
            this.isList = true;
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
//        public int actionRes;
//        public int actionAltRes;
        public boolean secondary;
        public int specificMax;
        public String customColumn;

        public EditType(int rawValue, int labelRes) {
            this.rawValue = rawValue;
            this.labelRes = labelRes;
            this.specificMax = -1;
        }

        public EditType setSecondary(boolean secondary) {
            this.secondary = secondary;
            return this;
        }

        public EditType setSpecificMax(int specificMax) {
            this.specificMax = specificMax;
            return this;
        }

        public EditType setCustomColumn(String customColumn) {
            this.customColumn = customColumn;
            return this;
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

        public EditField setOptional(boolean optional) {
            this.optional = optional;
            return this;
        }
    }

    /**
     * Generic method of inflating a given {@link Cursor} into a user-readable
     * {@link CharSequence}. For example, an inflater could combine the multiple
     * columns of {@link StructuredPostal} together using a string resource
     * before presenting to the user.
     */
    public interface StringInflater {
        public CharSequence inflateUsing(Context context, Cursor cursor);
        public CharSequence inflateUsing(Context context, ContentValues values);
    }

}
