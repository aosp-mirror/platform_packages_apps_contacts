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
 * limitations under the License
 */

package com.android.contacts.views.editor;

import android.app.DialogFragment;
import android.os.Bundle;

/**
 * A DialogFragment that can send its result to a target (which is either an Activity or a Fragment)
 * TODO: This should be removed once there is Framework support for handling of Targets.
 */
public class TargetedDialogFragment extends DialogFragment {
    private static final String TARGET_FRAGMENT_ID = "TARGET_FRAGMENT_ID";

    private int mTargetFragmentId;

    public TargetedDialogFragment() {
        mTargetFragmentId = -1;
    }

    public TargetedDialogFragment(int targetFragmentId) {
        mTargetFragmentId = targetFragmentId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mTargetFragmentId = savedInstanceState.getInt(TARGET_FRAGMENT_ID);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(TARGET_FRAGMENT_ID, mTargetFragmentId);
    }

    protected Object getTarget() {
        return mTargetFragmentId == -1 ? getActivity()
                : getActivity().getFragmentManager().findFragmentById(mTargetFragmentId);
    }
}
