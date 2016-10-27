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

package com.android.contacts.common.interactions;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.telephony.SubscriptionManager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.android.contacts.SimImportFragment;
import com.android.contacts.common.R;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.contacts.common.database.SimContactDao;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.SimCard;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountSelectionUtil;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.editor.SelectAccountDialogFragment;

import java.util.List;

/**
 * An dialog invoked to import/export contacts.
 */
public class ImportDialogFragment extends DialogFragment
        implements SelectAccountDialogFragment.Listener {
    public static final String TAG = "ImportDialogFragment";

    private static final String KEY_RES_ID = "resourceId";
    private static final String KEY_SUBSCRIPTION_ID = "subscriptionId";

    public static final String EXTRA_SIM_ONLY = "extraSimOnly";

    private boolean mSimOnly = false;
    private SimContactDao mSimDao;

    private final String[] LOOKUP_PROJECTION = new String[] {
            Contacts.LOOKUP_KEY
    };

    private SubscriptionManager mSubscriptionManager;

    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager) {
        final ImportDialogFragment fragment = new ImportDialogFragment();
        fragment.show(fragmentManager, TAG);
    }

    /**
     * Create an instance that will only have items for the SIM cards (VCF will not be included).
     */
    public static void showForSimOnly(FragmentManager fragmentManager) {
        final ImportDialogFragment fragment = new ImportDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(EXTRA_SIM_ONLY, true);
        fragment.setArguments(args);
        fragment.show(fragmentManager, TAG);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        mSimOnly = args != null && args.getBoolean(EXTRA_SIM_ONLY, false);
        mSimDao = SimContactDao.create(getContext());
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using the correct theme
        final LayoutInflater dialogInflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<AdapterEntry> adapter = new ArrayAdapter<AdapterEntry>(getActivity(),
                R.layout.select_dialog_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TextView result = (TextView)(convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false));

                result.setText(getItem(position).mLabel);
                return result;
            }
        };

        addItems(adapter);

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean dismissDialog;
                final int resId = adapter.getItem(which).mChoiceResourceId;
                if (resId == R.string.import_from_sim) {
                    dismissDialog = handleSimImportRequest(adapter.getItem(which).mSubscriptionId);
                } else if (resId == R.string.import_from_vcf_file) {
                        dismissDialog = handleImportRequest(resId,
                                adapter.getItem(which).mSubscriptionId);
                } else {
                    dismissDialog = true;
                    Log.e(TAG, "Unexpected resource: "
                            + getActivity().getResources().getResourceEntryName(resId));
                }
                if (dismissDialog) {
                    dialog.dismiss();
                }
            }
        };
        final TextView title = (TextView) View.inflate(getActivity(), R.layout.dialog_title, null);
        title.setText(R.string.dialog_import);
        return new AlertDialog.Builder(getActivity())
                .setCustomTitle(title)
                .setSingleChoiceItems(adapter, -1, clickListener)
                .create();
    }

    private void addItems(ArrayAdapter<AdapterEntry> adapter) {
        final Resources res = getActivity().getResources();
        if (res.getBoolean(R.bool.config_allow_import_from_vcf_file) && !mSimOnly) {
            adapter.add(new AdapterEntry(getString(R.string.import_from_vcf_file),
                    R.string.import_from_vcf_file));
        }
        final List<SimCard> sims = mSimDao.getSimCards();

        if (sims.size() == 1) {
            adapter.add(new AdapterEntry(getString(R.string.import_from_sim),
                    R.string.import_from_sim, SimCard.NO_SUBSCRIPTION_ID));
            return;
        }
        for (SimCard sim : sims) {
            adapter.add(new AdapterEntry(getSimDescription(sim), R.string.import_from_sim,
                    sim.getSubscriptionId()));
        }
    }

    private boolean handleSimImportRequest(int subscriptionId) {
        SimImportFragment.newInstance(subscriptionId).show(getFragmentManager(), "SimImport");
        return true;
    }

    /**
     * Handle "import from SD".
     *
     * @return {@code true} if the dialog show be closed.  {@code false} otherwise.
     */
    private boolean handleImportRequest(int resId, int subscriptionId) {
        // There are three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        final List<AccountWithDataSet> accountList = accountTypes.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            // Send over to the account selector
            final Bundle args = new Bundle();
            args.putInt(KEY_RES_ID, resId);
            args.putInt(KEY_SUBSCRIPTION_ID, subscriptionId);
            SelectAccountDialogFragment.show(
                    getFragmentManager(), this,
                    R.string.dialog_new_contact_account,
                    AccountListFilter.ACCOUNTS_CONTACT_WRITABLE, args);

            // In this case, because this DialogFragment is used as a target fragment to
            // SelectAccountDialogFragment, we can't close it yet.  We close the dialog when
            // we get a callback from it.
            return false;
        }

        AccountSelectionUtil.doImport(getActivity(), resId,
                (size == 1 ? accountList.get(0) : null),
                (CompatUtils.isMSIMCompatible() ? subscriptionId : -1));
        return true; // Close the dialog.
    }

    /**
     * Called when an account is selected on {@link SelectAccountDialogFragment}.
     */
    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        AccountSelectionUtil.doImport(getActivity(), extraArgs.getInt(KEY_RES_ID),
                account, extraArgs.getInt(KEY_SUBSCRIPTION_ID));

        // At this point the dialog is still showing (which is why we can use getActivity() above)
        // So close it.
        dismiss();
    }

    @Override
    public void onAccountSelectorCancelled() {
        // See onAccountChosen() -- at this point the dialog is still showing.  Close it.
        dismiss();
    }

    private CharSequence getSimDescription(SimCard sim) {
        final CharSequence name = sim.getDisplayName();
        CharSequence number = sim.getFormattedPhone();
        // If formatting fails use the raw phone number
        if (number == null) {
            number = sim.getPhone();
        }
        if (number != null) {
            number = PhoneNumberUtilsCompat.createTtsSpannable(number);
        }
        if (TextUtils.isEmpty(number)) {
            return getString(R.string.import_from_sim_summary_fmt, name);
        }
        return new SpannableStringBuilder(getString(R.string.import_from_sim_summary_fmt, name))
                .append('\n').append(number);
    }

    private static class AdapterEntry {
        public final CharSequence mLabel;
        public final int mChoiceResourceId;
        public final int mSubscriptionId;

        public AdapterEntry(CharSequence label, int resId, int subId) {
            mLabel = label;
            mChoiceResourceId = resId;
            mSubscriptionId = subId;
        }

        public AdapterEntry(String label, int resId) {
            // Store a nonsense value for mSubscriptionId. If this constructor is used,
            // the mSubscriptionId value should not be read later.
            this(label, resId, /* subId = */ -1);
        }
    }
}
