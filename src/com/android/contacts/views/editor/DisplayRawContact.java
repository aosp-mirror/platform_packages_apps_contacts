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

package com.android.contacts.views.editor;

import com.android.contacts.model.ContactsSource;
import com.android.contacts.views.editor.viewModel.BaseViewModel;
import com.android.contacts.views.editor.viewModel.FooterViewModel;
import com.android.contacts.views.editor.viewModel.HeaderViewModel;

import android.content.Context;

import java.util.ArrayList;

public class DisplayRawContact {
    private final ContactsSource mSource;
    private String mAccountName;
    private final long mId;
    private boolean mWritable;
    private final HeaderViewModel mHeader;
    private final FooterViewModel mFooter;
    private final ArrayList<BaseViewModel> mFields = new ArrayList<BaseViewModel>();

    public DisplayRawContact(Context context, ContactsSource source, String accountName, long id,
            boolean writable, FooterViewModel.Listener footerListener) {
        mSource = source;
        mAccountName = accountName;
        mId = id;
        mWritable = writable;
        mHeader = new HeaderViewModel(context, this);
        mFooter = new FooterViewModel(context, this, footerListener);
    }

    public ContactsSource getSource() {
        return mSource;
    }

    public String getAccountName() {
        return mAccountName;
    }

    public long getId() {
        return mId;
    }

    public boolean isWritable() {
        return mWritable;
    }

    public ArrayList<BaseViewModel> getFields() {
        return mFields;
    }

    public HeaderViewModel getHeader() {
        return mHeader;
    }

    public FooterViewModel getFooter() {
        return mFooter;
    }
}
