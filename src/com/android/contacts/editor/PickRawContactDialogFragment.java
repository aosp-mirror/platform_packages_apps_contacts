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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
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
    private static final class RawContactAccountListAdapter extends CursorAdapter {
        private final LayoutInflater mInflater;
        private final Context mContext;

        public RawContactAccountListAdapter(Context context, Cursor cursor) {
            super(context, cursor, 0);
            mContext = context;
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final long rawContactId = cursor.getLong(PickRawContactLoader.RAW_CONTACT_ID);
            final String accountName = cursor.getString(PickRawContactLoader.ACCOUNT_NAME);
            final String accountType = cursor.getString(PickRawContactLoader.ACCOUNT_TYPE);
            final String dataSet = cursor.getString(PickRawContactLoader.DATA_SET);
            final AccountType account = AccountTypeManager.getInstance(mContext)
                    .getAccountType(accountType, dataSet);

            final ContactsPreferences prefs = new ContactsPreferences(mContext);
            final int displayNameColumn =
                    prefs.getDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY
                            ? PickRawContactLoader.DISPLAY_NAME_PRIMARY
                            : PickRawContactLoader.DISPLAY_NAME_ALTERNATIVE;
            String displayName = cursor.getString(displayNameColumn);

            final TextView nameView = (TextView) view.findViewById(
                    R.id.display_name);
            final TextView accountTextView = (TextView) view.findViewById(
                    R.id.account_name);
            final ImageView accountIconView = (ImageView) view.findViewById(
                    R.id.account_icon);

            if (!account.areContactsWritable()) {
                displayName = mContext
                        .getString(R.string.contact_editor_pick_raw_contact_read_only, displayName);
                view.setAlpha(.38f);
            } else {
                view.setAlpha(1f);
            }

            nameView.setText(displayName);
            accountTextView.setText(accountName);
            accountIconView.setImageDrawable(account.getDisplayIcon(mContext));

            final ContactPhotoManager.DefaultImageRequest
                    request = new ContactPhotoManager.DefaultImageRequest(
                    displayName, String.valueOf(rawContactId), /* isCircular = */ true);
            final ImageView photoView = (ImageView) view.findViewById(
                    R.id.photo);
            final Uri photoUri = Uri.withAppendedPath(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                    RawContacts.DisplayPhoto.CONTENT_DIRECTORY);
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
    private MaterialPalette mMaterialPalette;

    public static PickRawContactDialogFragment getInstance(Uri uri, Cursor cursor,
            MaterialPalette materialPalette) {
        final PickRawContactDialogFragment fragment = new PickRawContactDialogFragment();
        fragment.setUri(uri);
        fragment.setCursor(cursor);
        fragment.setMaterialPalette(materialPalette);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final CursorAdapter adapter = new RawContactAccountListAdapter(getContext(), mCursor);
        builder.setTitle(R.string.contact_editor_pick_raw_contact_dialog_title);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final long rawContactId = adapter.getItemId(which);
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

    private void setCursor(Cursor cursor) {
        mCursor = cursor;
    }

    private void setMaterialPalette(MaterialPalette materialPalette) {
        mMaterialPalette = materialPalette;
    }

    private void finishActivity() {
        if (getActivity() != null && !getActivity().isFinishing()) {
            getActivity().finish();
        }
    }
}
