/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.interactions;


import com.android.contacts.ContactSaveService;
import com.android.contacts.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import java.util.TreeSet;

/**
 * An interaction invoked to join multiple contacts together.
 */
public class JoinContactsDialogFragment extends DialogFragment {

    private static final String FRAGMENT_TAG = "joinDialog";
    private static final String KEY_CONTACT_IDS = "contactIds";

    public interface JoinContactsListener {
        void onContactsJoined();
    }

    public static void start(Activity activity, TreeSet<Long> contactIds) {
        final FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
        final JoinContactsDialogFragment newFragment
                = JoinContactsDialogFragment.newInstance(contactIds);
        newFragment.show(ft, FRAGMENT_TAG);
    }

    private static JoinContactsDialogFragment newInstance(TreeSet<Long> contactIds) {
        final JoinContactsDialogFragment fragment = new JoinContactsDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putSerializable(KEY_CONTACT_IDS, contactIds);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final TreeSet<Long> contactIds =
                (TreeSet<Long>) getArguments().getSerializable(KEY_CONTACT_IDS);
        if (contactIds.size() <= 1) {
            return new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.batch_merge_single_contact_warning)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }
        return new AlertDialog.Builder(getActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(R.string.batch_merge_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                joinContacts(contactIds);
                            }
                        }
                )
                .create();
    }

    private void joinContacts(TreeSet<Long> contactIds) {
        final Long[] contactIdsArray = contactIds.toArray(new Long[contactIds.size()]);
        final long[] contactIdsArray2 = new long[contactIdsArray.length];
        for (int i = 0; i < contactIds.size(); i++) {
            contactIdsArray2[i] = contactIdsArray[i];
        }

        final Intent intent = ContactSaveService.createJoinSeveralContactsIntent(getActivity(),
                contactIdsArray2);
        getActivity().startService(intent);

        notifyListener();
    }

    private void notifyListener() {
        if (getActivity() instanceof JoinContactsListener) {
            ((JoinContactsListener) getActivity()).onContactsJoined();
        }
    }

}
