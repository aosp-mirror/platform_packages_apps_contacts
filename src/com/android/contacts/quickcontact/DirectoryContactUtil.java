package com.android.contacts.quickcontact;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.quickcontact.QuickContactActivity.SelectAccountDialogFragmentListener;

import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.provider.ContactsContract.Directory;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to support adding directory contacts.
 *
 * This class is coupled with {@link QuickContactActivity}, but is left out of
 * QuickContactActivity.java to avoid ballooning the size of the file.
 */
public class DirectoryContactUtil {

    public static boolean isDirectoryContact(Contact contactData) {
        // Not a directory contact? Nothing to fix here
        if (contactData == null || !contactData.isDirectoryEntry()) return false;

        // No export support? Too bad
        return contactData.getDirectoryExportSupport() != Directory.EXPORT_SUPPORT_NONE;
    }

    public static void addToMyContacts(Contact contactData, Context context,
            FragmentManager fragmentManager,
            SelectAccountDialogFragmentListener selectAccountCallbacks) {
        int exportSupport = contactData.getDirectoryExportSupport();
        switch (exportSupport) {
            case Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY: {
                createCopy(contactData.getContentValues(),
                        new AccountWithDataSet(contactData.getDirectoryAccountName(),
                        contactData.getDirectoryAccountType(), null),
                        context);
                break;
            }
            case Directory.EXPORT_SUPPORT_ANY_ACCOUNT: {
                final List<AccountWithDataSet> accounts =
                        AccountTypeManager.getInstance(context).getAccounts(true);
                if (accounts.isEmpty()) {
                    createCopy(contactData.getContentValues(), null, context);
                    return;  // Don't show a dialog.
                }

                // In the common case of a single writable account, auto-select
                // it without showing a dialog.
                if (accounts.size() == 1) {
                    createCopy(contactData.getContentValues(), accounts.get(0), context);
                    return;  // Don't show a dialog.
                }

                SelectAccountDialogFragment.show(fragmentManager,
                        selectAccountCallbacks, R.string.dialog_new_contact_account,
                        AccountListFilter.ACCOUNTS_CONTACT_WRITABLE, null);
                break;
            }
        }
    }

    public static void createCopy(
            ArrayList<ContentValues> values, AccountWithDataSet account,
            Context context) {
        Toast.makeText(context, R.string.toast_making_personal_copy,
                Toast.LENGTH_LONG).show();
        Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                context, values, account,
                QuickContactActivity.class, Intent.ACTION_VIEW);
        context.startService(serviceIntent);
    }
}
