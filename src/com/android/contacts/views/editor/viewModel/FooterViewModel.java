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
import com.android.contacts.views.editor.view.FooterView;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class FooterViewModel extends BaseViewModel {
    private final Listener mListener;

    public FooterViewModel(Context context, DisplayRawContact rawContact, Listener listener) {
        super(context, rawContact);
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        mListener = listener;
    }

    @Override
    public View createAndAddView(LayoutInflater inflater, ViewGroup parent) {
        final FooterView result = FooterView.inflate(inflater, parent, false);

        result.setListener(mViewListener);
        parent.addView(result);
        return result;
    }

    private FooterView.Listener mViewListener = new FooterView.Listener() {
        public void onAddClicked() {
            if (mListener != null) mListener.onAddClicked(getRawContact());
        }

        public void onSeparateClicked() {
            if (mListener != null) mListener.onAddClicked(getRawContact());
        }

        public void onDeleteClicked() {
            if (mListener != null) mListener.onAddClicked(getRawContact());
        }
    };

    public interface Listener {
        public void onAddClicked(DisplayRawContact rawContact);
        public void onSeparateClicked(DisplayRawContact rawContact);
        public void onDeleteClicked(DisplayRawContact rawContact);
    }
}
