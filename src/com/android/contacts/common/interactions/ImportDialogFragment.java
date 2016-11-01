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
import android.support.v4.text.TextUtilsCompat;
import android.text.SpannableString;
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
import com.android.contacts.common.model.SimContact;
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

    public static final String EXTRA_SIM_CONTACT_COUNT_PREFIX = "simContactCount_";

    private boolean mSimOnly = false;
    private SimContactDao mSimDao;

    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager) {
        final ImportDialogFragment fragment = new ImportDialogFragment();
        fragment.show(fragmentManager, TAG);
    }

    public static void show(FragmentManager fragmentManager, List<SimCard> sims,
            boolean includeVcf) {
        final ImportDialogFragment fragment = new ImportDialogFragment();
        final Bundle args = new Bundle();
        args.putBoolean(EXTRA_SIM_ONLY, !includeVcf);
        for (SimCard sim : sims) {
            final List<SimContact> contacts = sim.getContacts();
            if (contacts == null) {
                continue;
            }
            args.putInt(EXTRA_SIM_CONTACT_COUNT_PREFIX + sim.getSimId(), contacts.size());
        }

        fragment.setArguments(args);
        fragment.show(fragmentManager, TAG);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(STYLE_NORMAL, R.style.ContactsAlertDialogThemeAppCompat);

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
        final LayoutInflater dialogInflater = (LayoutInflater)
                getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<AdapterEntry> adapter = new ArrayAdapter<AdapterEntry>(getActivity(),
                R.layout.select_dialog_item) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final View result = convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false);
                final TextView primaryText = (TextView) result.findViewById(R.id.primary_text);
                final TextView secondaryText = (TextView) result.findViewById(R.id.secondary_text);
                final AdapterEntry entry = getItem(position);
                secondaryText.setVisibility(View.GONE);
                if (entry.mChoiceResourceId == R.string.import_from_sim) {
                    final CharSequence secondary = getSimSecondaryText(entry.mSim);
                    if (TextUtils.isEmpty(secondary)) {
                        secondaryText.setVisibility(View.GONE);
                    } else {
                        secondaryText.setText(secondary);
                        secondaryText.setVisibility(View.VISIBLE);
                    }
                }
                primaryText.setText(entry.mLabel);
                return result;
            }

            CharSequence getSimSecondaryText(SimCard sim) {
                int count = getSimContactCount(sim);

                CharSequence phone = sim.getFormattedPhone();
                if (phone == null) {
                    phone = sim.getPhone();
                }
                if (phone != null) {
                    phone = PhoneNumberUtilsCompat.createTtsSpannable(phone);
                }

                if (count != -1 && phone != null) {
                    // We use a template instead of format string so that the TTS span is preserved
                    final CharSequence template = getResources()
                            .getQuantityString(R.plurals.import_from_sim_secondary_template, count);
                    return TextUtils.expandTemplate(template, String.valueOf(count), phone);
                } else if (phone != null) {
                    return phone;
                } else if (count != -1) {
                    // count != -1
                    return getResources()
                            .getQuantityString(
                                    R.plurals.import_from_sim_secondary_contact_count_fmt, count,
                                    count);
                } else {
                    return null;
                }
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
                    dismissDialog = handleSimImportRequest(adapter.getItem(which).mSim);
                } else if (resId == R.string.import_from_vcf_file) {
                        dismissDialog = handleImportRequest(resId, SimCard.NO_SUBSCRIPTION_ID);
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

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), getTheme())
                .setTitle(R.string.dialog_import)
                .setNegativeButton(android.R.string.cancel, null);
        if (adapter.isEmpty()) {
            // Handle edge case; e.g. SIM card was removed.
            builder.setMessage(R.string.nothing_to_import_message);
        } else {
            builder.setSingleChoiceItems(adapter, -1, clickListener);
        }

        return builder.create();
    }

    private int getSimContactCount(SimCard sim) {
        if (sim.getContacts() != null) {
            return sim.getContacts().size();
        }
        final Bundle args = getArguments();
        if (args == null) {
            return -1;
        }
        return args.getInt(EXTRA_SIM_CONTACT_COUNT_PREFIX + sim.getSimId(), -1);
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
                    R.string.import_from_sim, sims.get(0)));
            return;
        }
        for (int i = 0; i < sims.size(); i++) {
            final SimCard sim = sims.get(i);
            adapter.add(new AdapterEntry(getSimDescription(sim, i), R.string.import_from_sim, sim));
        }
    }

    private boolean handleSimImportRequest(SimCard sim) {
        SimImportFragment.newInstance(sim.getSubscriptionId()).show(getFragmentManager(),
                "SimImport");
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

    private CharSequence getSimDescription(SimCard sim, int index) {
        final CharSequence name = sim.getDisplayName();
        if (name != null) {
            return getString(R.string.import_from_sim_summary_fmt, name);
        } else {
            return getString(R.string.import_from_sim_summary_fmt, String.valueOf(index));
        }
    }

    private static class AdapterEntry {
        public final CharSequence mLabel;
        public final int mChoiceResourceId;
        public final SimCard mSim;

        public AdapterEntry(CharSequence label, int resId, SimCard sim) {
            mLabel = label;
            mChoiceResourceId = resId;
            mSim = sim;
        }

        public AdapterEntry(String label, int resId) {
            // Store a nonsense value for mSubscriptionId. If this constructor is used,
            // the mSubscriptionId value should not be read later.
            this(label, resId, /* sim= */ null);
        }
    }
}
