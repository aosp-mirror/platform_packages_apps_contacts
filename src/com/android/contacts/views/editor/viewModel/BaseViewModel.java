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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class BaseViewModel {
    private final DisplayRawContact mRawContact;
    private final Context mContext;

    public BaseViewModel(Context context, DisplayRawContact rawContact) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (rawContact == null) throw new IllegalArgumentException("rawContact must not be null");
        mContext = context;
        mRawContact = rawContact;
    }

    public DisplayRawContact getRawContact() {
        return mRawContact;
    }

    public Context getContext() {
        return mContext;
    }

    public abstract int getEntryType();
    public abstract View getView(LayoutInflater inflater, View convertView, ViewGroup parent);
}
