package com.android.contacts.editor;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountDisplayInfo;
import com.android.contacts.common.model.account.AccountDisplayInfoFactory;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.GoogleAccountType;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;

/**
 * Dialog containing the raw contacts that make up a contact. On selection the editor is loaded
 * for the chosen raw contact.
 */
public class PickRawContactDialogFragment extends DialogFragment {
    /**
     * Used to list the account info for the given raw contacts list.
     */
    private final class RawContactAccountListAdapter extends CursorAdapter {
        private final LayoutInflater mInflater;
        private final Context mContext;
        private final AccountDisplayInfoFactory mAccountDisplayInfoFactory;
        private final AccountTypeManager mAccountTypeManager;
        private final ContactsPreferences mPreferences;

        public RawContactAccountListAdapter(Context context, Cursor cursor) {
            super(context, cursor, 0);
            mContext = context;
            mInflater = LayoutInflater.from(context);
            mAccountDisplayInfoFactory = AccountDisplayInfoFactory.forWritableAccounts(context);
            mAccountTypeManager = AccountTypeManager.getInstance(context);
            mPreferences = new ContactsPreferences(context);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final long rawContactId = cursor.getLong(PickRawContactLoader.RAW_CONTACT_ID);
            final String accountName = cursor.getString(PickRawContactLoader.ACCOUNT_NAME);
            final String accountType = cursor.getString(PickRawContactLoader.ACCOUNT_TYPE);
            final String dataSet = cursor.getString(PickRawContactLoader.DATA_SET);
            final AccountType account = mAccountTypeManager.getAccountType(accountType, dataSet);

            final int displayNameColumn =
                    mPreferences.getDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY
                            ? PickRawContactLoader.DISPLAY_NAME_PRIMARY
                            : PickRawContactLoader.DISPLAY_NAME_ALTERNATIVE;

            String displayName = cursor.getString(displayNameColumn);

            if (TextUtils.isEmpty(displayName)) {
                displayName = mContext.getString(R.string.missing_name);
            }

            if (!account.areContactsWritable()) {
                displayName = mContext
                        .getString(R.string.contact_editor_pick_raw_contact_read_only, displayName);
                view.setAlpha(.38f);
            } else {
                view.setAlpha(1f);
            }
            final TextView nameView = (TextView) view.findViewById(
                    R.id.display_name);
            nameView.setText(displayName);

            final String accountDisplayLabel;

            // Use the same string as editor if it's an editable user profile raw contact.
            if (mIsUserProfile && account.areContactsWritable()) {
                final AccountDisplayInfo displayInfo =
                        mAccountDisplayInfoFactory.getAccountDisplayInfo(
                                new AccountWithDataSet(accountName, accountType, dataSet));
                accountDisplayLabel = EditorUiUtils.getAccountHeaderLabelForMyProfile(mContext,
                        displayInfo);
            }
            else if (GoogleAccountType.ACCOUNT_TYPE.equals(accountType)
                    && account.dataSet == null) {
                // Focus Google accounts have the account name shown
                accountDisplayLabel = accountName;
            } else {
                accountDisplayLabel = account.getDisplayLabel(mContext).toString();
            }
            final TextView accountTextView = (TextView) view.findViewById(
                    R.id.account_name);
            final ImageView accountIconView = (ImageView) view.findViewById(
                    R.id.account_icon);
            accountTextView.setText(accountDisplayLabel);
            accountIconView.setImageDrawable(account.getDisplayIcon(mContext));

            final ContactPhotoManager.DefaultImageRequest
                    request = new ContactPhotoManager.DefaultImageRequest(
                    displayName, String.valueOf(rawContactId), /* isCircular = */ true);
            final Uri photoUri = Uri.withAppendedPath(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                    RawContacts.DisplayPhoto.CONTENT_DIRECTORY);
            final ImageView photoView = (ImageView) view.findViewById(
                    R.id.photo);
            ContactPhotoManager.getInstance(mContext).loadDirectoryPhoto(photoView,
                    photoUri,
                    /* darkTheme = */ false,
                    /* isCircular = */ true,
                    request);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return mInflater.inflate(R.layout.raw_contact_list_item, parent, false);
        }

        @Override
        public long getItemId(int position) {
            getCursor().moveToPosition(position);
            return getCursor().getLong(PickRawContactLoader.RAW_CONTACT_ID);
        }
    }

    // Cursor holding all raw contact rows for the given Contact.
    private Cursor mCursor;
    // Uri for the whole Contact.
    private Uri mUri;
    private CursorAdapter mAdapter;
    private MaterialPalette mMaterialPalette;
    private boolean mIsUserProfile;

    public static PickRawContactDialogFragment getInstance(Uri uri, Cursor cursor,
            MaterialPalette materialPalette, boolean isUserProfile) {
        final PickRawContactDialogFragment fragment = new PickRawContactDialogFragment();
        fragment.setUri(uri);
        fragment.setCursor(cursor);
        fragment.setMaterialPalette(materialPalette);
        fragment.setIsUserProfile(isUserProfile);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        mAdapter = new RawContactAccountListAdapter(getContext(), mCursor);
        builder.setTitle(R.string.contact_editor_pick_raw_contact_dialog_title);
        builder.setAdapter(mAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final long rawContactId = mAdapter.getItemId(which);
                final Intent intent = EditorIntents.createEditContactIntentForRawContact(
                        getActivity(), mUri, rawContactId, mMaterialPalette);
                intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                ImplicitIntentsUtil.startActivityInApp(getActivity(), intent);
            }
        });
        builder.setCancelable(true);
        return builder.create();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        mCursor = null;
        finishActivity();
    }

    private void setUri(Uri uri) {
        mUri = uri;
    }

    public void setCursor(Cursor cursor) {
        if (mAdapter != null) {
            mAdapter.swapCursor(cursor);
        }
        mCursor = cursor;
    }

    private void setMaterialPalette(MaterialPalette materialPalette) {
        mMaterialPalette = materialPalette;
    }

    private void setIsUserProfile(boolean isUserProfile) {
        mIsUserProfile = isUserProfile;
    }

    private void finishActivity() {
        if (getActivity() != null && !getActivity().isFinishing()) {
            getActivity().finish();
        }
    }
}
