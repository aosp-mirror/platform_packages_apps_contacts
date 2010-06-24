/*
 * Copyright (C) 2010 Google Inc.
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
 * limitations under the License
 */

package com.android.contacts.views.editor.viewModel;

import com.android.contacts.views.editor.DisplayRawContact;
import com.android.contacts.views.editor.view.OrganizationView;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContentProviderOperation.Builder;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

public class OrganizationViewModel extends DataViewModel {
    private static final String TAG = "OrganizationViewModel";

    private final int mLabelResId;
    private final String mCompanyFieldColumn;
    private final String mTitleFieldColumn;
    private final String mLabelColumn;
    private final String mTypeColumn;

    private OrganizationViewModel(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, int labelResId) {
        super(context, rawContact, dataId, contentValues, Organization.CONTENT_ITEM_TYPE);
        mLabelResId = labelResId;

        mCompanyFieldColumn = Organization.COMPANY;
        mTitleFieldColumn = Organization.TITLE;
        mTypeColumn = Organization.TYPE;
        mLabelColumn = Organization.LABEL;
    }

    public static OrganizationViewModel createForExisting(Context context,
            DisplayRawContact rawContact, long dataId, ContentValues contentValues,
            int titleResId) {
        return new OrganizationViewModel(context, rawContact, dataId, contentValues, titleResId);
    }

    @Override
    public OrganizationView createAndAddView(LayoutInflater inflater, ViewGroup parent) {
        final OrganizationView result = OrganizationView.inflate(inflater, parent, false);

        result.setListener(mViewListener);
        result.setLabelText(mLabelResId);
        result.setFieldValues(getCompanyFieldValue(), getTitleFieldValue());
        result.setTypeDisplayLabel(getTypeDisplayLabel());

        parent.addView(result);
        return result;
    }

    @Override
    protected void writeToBuilder(Builder builder, boolean isInsert) {
        builder.withValue(mCompanyFieldColumn, getCompanyFieldValue());
        builder.withValue(mTitleFieldColumn, getTitleFieldValue());
        builder.withValue(mTypeColumn, getType());
        builder.withValue(mLabelColumn, getLabel());
    }

    protected String getCompanyFieldValue() {
        return getContentValues().getAsString(mCompanyFieldColumn);
    }

    protected String getTitleFieldValue() {
        return getContentValues().getAsString(mTitleFieldColumn);
    }

    private void putCompanyFieldValue(String value) {
        getContentValues().put(mCompanyFieldColumn, value);
    }

    private void putTitleFieldValue(String value) {
        getContentValues().put(mTitleFieldColumn, value);
    }

    private int getType() {
        return getContentValues().getAsInteger(mTypeColumn).intValue();
    }

    private void putType(int value) {
        getContentValues().put(mTypeColumn, value);
    }

    private String getLabel() {
        return getContentValues().getAsString(mLabelColumn);
    }

    private void putLabel(String value) {
        getContentValues().put(mLabelColumn, value);
    }

    private CharSequence getTypeDisplayLabel() {
        return Organization.getTypeLabel(getContext().getResources(), getType(), getLabel());
    }

    private OrganizationView.Listener mViewListener = new OrganizationView.Listener() {
        public void onFocusLost(OrganizationView view) {
            Log.v(TAG, "Received FocusLost. Checking for changes");
            boolean hasChanged = false;

            final String oldCompanyValue = getCompanyFieldValue();
            final String newCompanyValue = view.getCompanyFieldValue().toString();
            if (!TextUtils.equals(oldCompanyValue, newCompanyValue)) {
                putCompanyFieldValue(newCompanyValue);
                hasChanged = true;
            }

            final String oldTitleValue = getTitleFieldValue();
            final String newTitleValue = view.getTitleFieldValue().toString();
            if (!TextUtils.equals(oldTitleValue, newTitleValue)) {
                putTitleFieldValue(newTitleValue);
                hasChanged = true;
            }
            if (hasChanged) {
                Log.v(TAG, "Found changes. Updating DB");
                saveData();
            }
        }
    };
}
