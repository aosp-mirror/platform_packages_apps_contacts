package com.android.contacts.common.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.common.R;

/**
 * Dialog that allows the user to switch between default SIM cards
 */
public class SelectSIMDialogFragment extends DialogFragment {
    private int mInitialSelectedItem;
    private int mCurrentSelectedItem;
    private boolean mHasDefaultSim;
    private boolean mIsDefaultSet;
    private OnClickOkListener mOkListener;

    /* Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager,
            int currentSimCard) {
        SelectSIMDialogFragment fragment = new SelectSIMDialogFragment();
        fragment.mInitialSelectedItem = currentSimCard;
        fragment.mCurrentSelectedItem = currentSimCard;
        fragment.show(fragmentManager, "selectSIM");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // TODO: Get the sim names programmatically
        CharSequence[] sims = {"SIM1-ATT", "SIM2-TMobile"};

        // TODO: pull default sim from database
        mHasDefaultSim = false;

        final DialogInterface.OnClickListener selectionListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mCurrentSelectedItem = which;
            }
        };
        final DialogInterface.OnClickListener okListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                boolean isChanged = mInitialSelectedItem != mCurrentSelectedItem;

                if (isChanged) {
                    mOkListener.passSimUpdate(mCurrentSelectedItem);
                }
                if (mIsDefaultSet && (!mHasDefaultSim || isChanged)) {
                    // if setting a new default, save to database
                    // TODO: save to database
                }
                else if (!mIsDefaultSet && mHasDefaultSim) {
                    // if unchecking the checkbox, remove the default
                    // TODO: remove from database
                }
            }
        };

        final CompoundButton.OnCheckedChangeListener checkListener =
                new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton check, boolean isChecked) {
                mIsDefaultSet = isChecked;
            }
        };

        // Generate custom checkbox view
        LinearLayout checkboxLayout = (LinearLayout) getActivity()
                .getLayoutInflater()
                .inflate(R.layout.default_sim_checkbox, null);

        CheckBox cb = (CheckBox) checkboxLayout.findViewById(R.id.default_sim_checkbox_view);
        cb.setOnCheckedChangeListener(checkListener);
        cb.setChecked(mHasDefaultSim);

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.menu_select_sim)
                .setSingleChoiceItems(sims, mInitialSelectedItem, selectionListener)
                .setPositiveButton(android.R.string.ok, okListener)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.getListView().addFooterView(checkboxLayout);

        return dialog;
    }

    /*
     * Interface to help pass updated SIM information to the main dialer
     */
    public interface OnClickOkListener {
        public void passSimUpdate(int simId);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mOkListener = (OnClickOkListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnClickOkListener");
        }
    }
}