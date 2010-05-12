/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.list;

import com.android.contacts.R;
import com.android.contacts.list.MultiplePhonePickerAdapter.OnSelectionChangeListener;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;

/**
 * Fragment for the multiple phone picker.
 */
public class MultiplePhonePickerFragment
        extends ContactEntryListFragment<MultiplePhonePickerAdapter>
        implements OnClickListener, OnSelectionChangeListener {

    private static final String SELECTION_EXTRA_KEY = "selection";
    private static final String SELECTION_CHANGED_EXTRA_KEY = "selectionChanged";

    private OnMultiplePhoneNumberPickerActionListener mListener;

    /**
     * UI control of action panel in MODE_PICK_MULTIPLE_PHONES mode.
     */
    private View mFooterView;

    private Uri[] mSelectedUris;
    private boolean mSelectionChanged;

    public MultiplePhonePickerFragment() {
        setSectionHeaderDisplayEnabled(false);
        setPhotoLoaderEnabled(true);
    }

    public void setOnMultiplePhoneNumberPickerActionListener(
            OnMultiplePhoneNumberPickerActionListener listener) {
        mListener = listener;
    }

    public Uri[] getSelectedUris() {
        return getAdapter().getSelectedUris();
    }

    public void setSelectedUris(Parcelable[] extras) {
        Uri[] uris = new Uri[extras == null ? 0 : extras.length];
        if (extras != null) {
            for (int i = 0; i < extras.length; i++) {
                uris[i] = (Uri)extras[i];
            }
        }
        setSelectedUris(uris);
    }

    public void setSelectedUris(Uri[] uris) {
        mSelectedUris = uris;
        MultiplePhonePickerAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.setSelectedUris(uris);
        }
    }

    @Override
    protected MultiplePhonePickerAdapter createListAdapter() {
        return new MultiplePhonePickerAdapter(getActivity());
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        MultiplePhonePickerAdapter adapter = getAdapter();
        adapter.setSelectedUris(mSelectedUris);
        adapter.setSelectionChanged(mSelectionChanged);
        adapter.setOnSelectionChangeListener(this);
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.contacts_list_content, null);
        ViewStub stub = (ViewStub)view.findViewById(R.id.footer_stub);
        if (stub != null) {
            View stubView = stub.inflate();
            mFooterView = stubView.findViewById(R.id.footer);
            mFooterView.setVisibility(View.GONE);
            Button doneButton = (Button) stubView.findViewById(R.id.done);
            doneButton.setOnClickListener(this);
            Button revertButton = (Button) stubView.findViewById(R.id.revert);
            revertButton.setOnClickListener(this);
        }
        return view;
    }

    @Override
    protected void onItemClick(int position, long id) {
        getAdapter().toggleSelection(position);
    }

    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.done:
                mListener.onPhoneNumbersSelectedAction(getAdapter().getSelectedUris());
                break;
            case R.id.revert:
                mListener.onFinishAction();
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWidgets();
    }

    public void onSelectionChange() {
        updateWidgets();
    }

    private void updateWidgets() {
        int selected = getAdapter().getSelectedCount();

        Activity context = getActivity();
        if (selected >= 1) {
            final String format = context.getResources().getQuantityString(
                    R.plurals.multiple_picker_title, selected);

            // TODO: turn this into a callback
            context.setTitle(String.format(format, selected));
        } else {
            // TODO: turn this into a callback
            context.setTitle(context.getString(R.string.contactsList));
        }

        if (getAdapter().isSelectionChanged() && mFooterView.getVisibility() == View.GONE) {
            mFooterView.setVisibility(View.VISIBLE);
            mFooterView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.footer_appear));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        icicle.putParcelableArray(SELECTION_EXTRA_KEY, getAdapter().getSelectedUris());
        icicle.putBoolean(SELECTION_CHANGED_EXTRA_KEY, getAdapter().isSelectionChanged());
    }

    @Override
    public void onRestoreInstanceState(Bundle icicle) {
        super.onRestoreInstanceState(icicle);
        setSelectedUris(icicle.getParcelableArray(SELECTION_EXTRA_KEY));
        mSelectionChanged = icicle.getBoolean(SELECTION_CHANGED_EXTRA_KEY, false);
        if (getAdapter() != null) {
            getAdapter().setSelectionChanged(mSelectionChanged);
        }
    }
}
