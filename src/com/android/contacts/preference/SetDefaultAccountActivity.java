package com.android.contacts.preference;

import android.app.Activity;
import android.os.Bundle;

import com.android.contacts.R;
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.model.AccountTypeManager.AccountFilter;
import com.android.contacts.model.account.AccountWithDataSet;

/** Activity to open a dialog for default account selection. */
public final class SetDefaultAccountActivity extends Activity
        implements SelectAccountDialogFragment.Listener {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      SelectAccountDialogFragment.show(getFragmentManager(),
              R.string.default_editor_account, AccountFilter.CONTACTS_WRITABLE, null);
  }

  @Override
  public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
      ContactsPreferences preferences = new ContactsPreferences(this);
      preferences.setDefaultAccount(account);
      setResult(Activity.RESULT_OK);
      finish();
  }

  @Override
  public void onAccountSelectorCancelled() {
      setResult(Activity.RESULT_CANCELED);
      finish();
  }
}
