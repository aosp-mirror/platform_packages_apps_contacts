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
package com.android.contacts.common.util;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.contacts.common.list.ContactListFilter;

import java.util.Collections;
import java.util.List;

/**
 * Supplies the label and icon that should be used for device accounts in the Nav Drawer.
 *
 * This operates on the list of filters to allow the implementation to choose better resources
 * in the case that there are multiple device accounts in the filter list.
 */
public interface DeviceAccountPresentationValues {
    void setFilters(List<ContactListFilter> filters);

    CharSequence getLabel(int index);

    Drawable getIcon(int index);

    /**
     * The default implementation only returns a label and icon for a device filter that as null
     * values for the accountType and accountName
     */
    class Default implements DeviceAccountPresentationValues {
        private final Context mContext;

        private List<ContactListFilter> mFilters = null;

        public Default(Context context) {
            mContext = context;
        }

        @Override
        public CharSequence getLabel(int index) {
            assertFiltersInitialized();

            final ContactListFilter filter = mFilters.get(index);
            if (filter.filterType != ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS) {
                return filter.accountName;
            }
            return filter.accountName != null ? filter.accountName :
                    mContext.getString(com.android.contacts.common.R.string.account_phone);
        }

        @Override
        public Drawable getIcon(int index) {
            assertFiltersInitialized();

            final ContactListFilter filter = mFilters.get(index);
            if (filter.filterType != ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS) {
                return filter.icon;
            }
            return mContext.getDrawable(com.android.contacts.common.R.drawable.ic_device);
        }

        @Override
        public void setFilters(List<ContactListFilter> filters) {
            if (filters == null) {
                mFilters = Collections.emptyList();
            } else {
                mFilters = filters;
            }
        }

        private void assertFiltersInitialized() {
            if (mFilters == null) {
                throw new IllegalStateException("setFilters must be called first.");
            }
        }
    }

}
