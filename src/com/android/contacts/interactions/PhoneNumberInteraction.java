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
package com.android.contacts.interactions;

import com.android.contacts.Collapser;
import com.android.contacts.Collapser.Collapsible;
import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.activities.DialtactsActivity;
import com.android.contacts.activities.TransactionSafeActivity;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountType.StringInflater;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.DataKind;
import com.google.common.annotations.VisibleForTesting;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Initiates phone calls or a text message. If there are multiple candidates, this class shows a
 * dialog to pick one. Creating one of these interactions should be done through the static
 * factory methods.
 */
public class PhoneNumberInteraction implements OnLoadCompleteListener<Cursor> {
    private static final String TAG = PhoneNumberInteraction.class.getSimpleName();

    @VisibleForTesting
    /* package */ enum InteractionType {
        PHONE_CALL,
        SMS
    }

    /**
     * A model object for capturing a phone number for a given contact.
     */
    @VisibleForTesting
    /* package */ static class PhoneItem implements Parcelable, Collapsible<PhoneItem> {
        long id;
        String phoneNumber;
        String accountType;
        String dataSet;
        long type;
        String label;

        public PhoneItem() {
        }

        private PhoneItem(Parcel in) {
            this.id          = in.readLong();
            this.phoneNumber = in.readString();
            this.accountType = in.readString();
            this.dataSet     = in.readString();
            this.type        = in.readLong();
            this.label       = in.readString();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(id);
            dest.writeString(phoneNumber);
            dest.writeString(accountType);
            dest.writeString(dataSet);
            dest.writeLong(type);
            dest.writeString(label);
        }

        public int describeContents() {
            return 0;
        }

        public boolean collapseWith(PhoneItem phoneItem) {
            if (!shouldCollapseWith(phoneItem)) {
                return false;
            }
            // Just keep the number and id we already have.
            return true;
        }

        public boolean shouldCollapseWith(PhoneItem phoneItem) {
            return ContactsUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE, phoneNumber,
                    Phone.CONTENT_ITEM_TYPE, phoneItem.phoneNumber);
        }

        @Override
        public String toString() {
            return phoneNumber;
        }

        public static final Parcelable.Creator<PhoneItem> CREATOR
                = new Parcelable.Creator<PhoneItem>() {
            public PhoneItem createFromParcel(Parcel in) {
                return new PhoneItem(in);
            }

            public PhoneItem[] newArray(int size) {
                return new PhoneItem[size];
            }
        };
    }

    /**
     * A list adapter that populates the list of contact's phone numbers.
     */
    private static class PhoneItemAdapter extends ArrayAdapter<PhoneItem> {
        private final InteractionType mInteractionType;
        private final AccountTypeManager mAccountTypeManager;

        public PhoneItemAdapter(Context context, List<PhoneItem> list,
                InteractionType interactionType) {
            super(context, R.layout.phone_disambig_item, android.R.id.text2, list);
            mInteractionType = interactionType;
            mAccountTypeManager = AccountTypeManager.getInstance(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);

            final PhoneItem item = getItem(position);
            final AccountType accountType = mAccountTypeManager.getAccountType(
                    item.accountType, item.dataSet);
            final TextView typeView = (TextView) view.findViewById(android.R.id.text1);
            final DataKind kind = accountType.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
            if (kind != null) {
                ContentValues values = new ContentValues();
                values.put(Phone.TYPE, item.type);
                values.put(Phone.LABEL, item.label);
                StringInflater header = (mInteractionType == InteractionType.SMS)
                        ? kind.actionAltHeader : kind.actionHeader;
                typeView.setText(header.inflateUsing(getContext(), values));
            } else {
                typeView.setText(R.string.call_other);
            }
            return view;
        }
    }

    /**
     * {@link DialogFragment} used for displaying a dialog with a list of phone numbers of which
     * one will be chosen to make a call or initiate an sms message.
     *
     * It is recommended to use
     * {@link PhoneNumberInteraction#startInteractionForPhoneCall(TransactionSafeActivity, Uri)} or
     * {@link PhoneNumberInteraction#startInteractionForTextMessage(TransactionSafeActivity, Uri)}
     * instead of directly using this class, as those methods handle one or multiple data cases
     * appropriately.
     */
    /* Made public to let the system reach this class */
    public static class PhoneDisambiguationDialogFragment extends DialogFragment
            implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

        private static final String ARG_PHONE_LIST = "phoneList";
        private static final String ARG_INTERACTION_TYPE = "interactionType";
        private static final String ARG_CALL_ORIGIN = "callOrigin";

        private InteractionType mInteractionType;
        private ListAdapter mPhonesAdapter;
        private List<PhoneItem> mPhoneList;
        private String mCallOrigin;

        public static void show(FragmentManager fragmentManager,
                ArrayList<PhoneItem> phoneList, InteractionType interactionType,
                String callOrigin) {
            PhoneDisambiguationDialogFragment fragment = new PhoneDisambiguationDialogFragment();
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(ARG_PHONE_LIST, phoneList);
            bundle.putSerializable(ARG_INTERACTION_TYPE, interactionType);
            bundle.putString(ARG_CALL_ORIGIN, callOrigin);
            fragment.setArguments(bundle);
            fragment.show(fragmentManager, TAG);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            mPhoneList = getArguments().getParcelableArrayList(ARG_PHONE_LIST);
            mInteractionType =
                    (InteractionType) getArguments().getSerializable(ARG_INTERACTION_TYPE);
            mCallOrigin = getArguments().getString(ARG_CALL_ORIGIN);

            mPhonesAdapter = new PhoneItemAdapter(activity, mPhoneList, mInteractionType);
            final LayoutInflater inflater = activity.getLayoutInflater();
            final View setPrimaryView = inflater.inflate(R.layout.set_primary_checkbox, null);
            return new AlertDialog.Builder(activity)
                    .setAdapter(mPhonesAdapter, this)
                    .setTitle(mInteractionType == InteractionType.SMS
                            ? R.string.sms_disambig_title : R.string.call_disambig_title)
                    .setView(setPrimaryView)
                    .create();
        }

        public void onClick(DialogInterface dialog, int which) {
            final AlertDialog alertDialog = (AlertDialog)dialog;
            if (mPhoneList.size() > which && which >= 0) {
                final PhoneItem phoneItem = mPhoneList.get(which);
                final CheckBox checkBox = (CheckBox)alertDialog.findViewById(R.id.setPrimary);
                if (checkBox.isChecked()) {
                    // Request to mark the data as primary in the background.
                    final Intent serviceIntent = ContactSaveService.createSetSuperPrimaryIntent(
                            getActivity(), phoneItem.id);
                    getActivity().startService(serviceIntent);
                }

                PhoneNumberInteraction.performAction(getActivity(), phoneItem.phoneNumber,
                        mInteractionType, mCallOrigin);
            } else {
                dialog.dismiss();
            }
        }
    }

    private static final String[] PHONE_NUMBER_PROJECTION = new String[] {
            Phone._ID,
            Phone.NUMBER,
            Phone.IS_SUPER_PRIMARY,
            RawContacts.ACCOUNT_TYPE,
            RawContacts.DATA_SET,
            Phone.TYPE,
            Phone.LABEL
    };

    private static final String PHONE_NUMBER_SELECTION = Data.MIMETYPE + "='"
            + Phone.CONTENT_ITEM_TYPE + "' AND " + Phone.NUMBER + " NOT NULL";

    private final Context mContext;
    private final OnDismissListener mDismissListener;
    private final InteractionType mInteractionType;

    private final String mCallOrigin;

    private CursorLoader mLoader;

    /**
     * Constructs a new {@link PhoneNumberInteraction}. The constructor takes in a {@link Context}
     * instead of a {@link TransactionSafeActivity} for testing purposes to verify the functionality
     * of this class. However, all factory methods for creating {@link PhoneNumberInteraction}s
     * require a {@link TransactionSafeActivity} (i.e. see {@link #startInteractionForPhoneCall}).
     */
    @VisibleForTesting
    /* package */ PhoneNumberInteraction(Context context, InteractionType interactionType,
            DialogInterface.OnDismissListener dismissListener) {
        this(context, interactionType, dismissListener, null);
    }

    private PhoneNumberInteraction(Context context, InteractionType interactionType,
            DialogInterface.OnDismissListener dismissListener, String callOrigin) {
        mContext = context;
        mInteractionType = interactionType;
        mDismissListener = dismissListener;
        mCallOrigin = callOrigin;
    }

    private void performAction(String phoneNumber) {
        PhoneNumberInteraction.performAction(mContext, phoneNumber, mInteractionType, mCallOrigin);
    }

    private static void performAction(
            Context context, String phoneNumber, InteractionType interactionType,
            String callOrigin) {
        Intent intent;
        switch (interactionType) {
            case SMS:
                intent = new Intent(
                        Intent.ACTION_SENDTO, Uri.fromParts("sms", phoneNumber, null));
                break;
            default:
                intent = new Intent(
                        Intent.ACTION_CALL_PRIVILEGED, Uri.fromParts("tel", phoneNumber, null));
                if (callOrigin != null) {
                    intent.putExtra(DialtactsActivity.EXTRA_CALL_ORIGIN, callOrigin);
                }
                break;
        }
        context.startActivity(intent);
    }

    /**
     * Initiates the interaction. This may result in a phone call or sms message started
     * or a disambiguation dialog to determine which phone number should be used.
     */
    @VisibleForTesting
    /* package */ void startInteraction(Uri uri) {
        if (mLoader != null) {
            mLoader.reset();
        }

        final Uri queryUri;
        final String inputUriAsString = uri.toString();
        if (inputUriAsString.startsWith(Contacts.CONTENT_URI.toString())) {
            if (!inputUriAsString.endsWith(Contacts.Data.CONTENT_DIRECTORY)) {
                queryUri = Uri.withAppendedPath(uri, Contacts.Data.CONTENT_DIRECTORY);
            } else {
                queryUri = uri;
            }
        } else if (inputUriAsString.startsWith(Data.CONTENT_URI.toString())) {
            queryUri = uri;
        } else {
            throw new UnsupportedOperationException(
                    "Input Uri must be contact Uri or data Uri (input: \"" + uri + "\")");
        }

        mLoader = new CursorLoader(mContext,
                queryUri,
                PHONE_NUMBER_PROJECTION,
                PHONE_NUMBER_SELECTION,
                null,
                null);
        mLoader.registerListener(0, this);
        mLoader.startLoading();
    }

    @Override
    public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
        if (cursor == null || !isSafeToCommitTransactions()) {
            onDismiss();
            return;
        }

        ArrayList<PhoneItem> phoneList = new ArrayList<PhoneItem>();
        String primaryPhone = null;
        try {
            while (cursor.moveToNext()) {
                if (cursor.getInt(cursor.getColumnIndex(Phone.IS_SUPER_PRIMARY)) != 0) {
                    // Found super primary, call it.
                    primaryPhone = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                    break;
                }

                PhoneItem item = new PhoneItem();
                item.id = cursor.getLong(cursor.getColumnIndex(Data._ID));
                item.phoneNumber = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                item.accountType =
                        cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_TYPE));
                item.dataSet = cursor.getString(cursor.getColumnIndex(RawContacts.DATA_SET));
                item.type = cursor.getInt(cursor.getColumnIndex(Phone.TYPE));
                item.label = cursor.getString(cursor.getColumnIndex(Phone.LABEL));

                phoneList.add(item);
            }
        } finally {
            cursor.close();
        }

        if (primaryPhone != null) {
            performAction(primaryPhone);
            onDismiss();
            return;
        }

        Collapser.collapseList(phoneList);

        if (phoneList.size() == 0) {
            onDismiss();
        } else if (phoneList.size() == 1) {
            onDismiss();
            performAction(phoneList.get(0).phoneNumber);
        } else {
            // There are multiple candidates. Let the user choose one.
            showDisambiguationDialog(phoneList);
        }
    }

    private boolean isSafeToCommitTransactions() {
        return mContext instanceof TransactionSafeActivity ?
                ((TransactionSafeActivity) mContext).isSafeToCommitTransactions() : true;
    }

    private void onDismiss() {
        if (mDismissListener != null) {
            mDismissListener.onDismiss(null);
        }
    }

    /**
     * Start call action using given contact Uri. If there are multiple candidates for the phone
     * call, dialog is automatically shown and the user is asked to choose one.
     *
     * @param activity that is calling this interaction. This must be of type
     * {@link TransactionSafeActivity} because we need to check on the activity state after the
     * phone numbers have been queried for.
     * @param uri contact Uri (built from {@link Contacts#CONTENT_URI}) or data Uri
     * (built from {@link Data#CONTENT_URI}). Contact Uri may show the disambiguation dialog while
     * data Uri won't.
     */
    public static void startInteractionForPhoneCall(TransactionSafeActivity activity, Uri uri) {
        (new PhoneNumberInteraction(activity, InteractionType.PHONE_CALL, null))
                .startInteraction(uri);
    }

    /**
     * @param activity that is calling this interaction. This must be of type
     * {@link TransactionSafeActivity} because we need to check on the activity state after the
     * phone numbers have been queried for.
     * @param callOrigin If non null, {@link DialtactsActivity#EXTRA_CALL_ORIGIN} will be
     * appended to the Intent initiating phone call. See comments in Phone package (PhoneApp)
     * for more detail.
     */
    public static void startInteractionForPhoneCall(TransactionSafeActivity activity, Uri uri,
            String callOrigin) {
        (new PhoneNumberInteraction(activity, InteractionType.PHONE_CALL, null, callOrigin))
                .startInteraction(uri);
    }

    /**
     * Start text messaging (a.k.a SMS) action using given contact Uri. If there are multiple
     * candidates for the phone call, dialog is automatically shown and the user is asked to choose
     * one.
     *
     * @param activity that is calling this interaction. This must be of type
     * {@link TransactionSafeActivity} because we need to check on the activity state after the
     * phone numbers have been queried for.
     * @param uri contact Uri (built from {@link Contacts#CONTENT_URI}) or data Uri
     * (built from {@link Data#CONTENT_URI}). Contact Uri may show the disambiguation dialog while
     * data Uri won't.
     */
    public static void startInteractionForTextMessage(TransactionSafeActivity activity, Uri uri) {
        (new PhoneNumberInteraction(activity, InteractionType.SMS, null)).startInteraction(uri);
    }

    @VisibleForTesting
    /* package */ CursorLoader getLoader() {
        return mLoader;
    }

    @VisibleForTesting
    /* package */ void showDisambiguationDialog(ArrayList<PhoneItem> phoneList) {
        PhoneDisambiguationDialogFragment.show(((Activity)mContext).getFragmentManager(),
                phoneList, mInteractionType, mCallOrigin);
    }
}
