//package com.motorola.vcardshare;

package com.android.contacts;

import java.util.List;
import com.android.contacts.model.Sources;
import com.android.contacts.util.AccountSelectionUtil;
import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class VCardActivity extends Activity {
    private static final String TAG = "VCardActivity";
    public static final int IMPORT_TYPE_GOOGLE = 0;
    public static final int IMPORT_TYPE_EXCHANGE = 1;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vcardshare_main);

        Intent intent = getIntent();
        String action = intent.getAction();
        Log.v(TAG, "action is " + action);
        Uri path = intent.getData();
        Log.v(TAG, "path is " + path);
        String mimeType = intent.getType();
        Log.v(TAG, "mimeType is " + mimeType);

        if (action.equals(Intent.ACTION_VIEW)) {

            AccountSelectionUtil.mVCardShare = true;
            AccountSelectionUtil.mPath = path;
            handleImportRequest(R.string.import_from_sdcard);
        } else {
            Log.w(TAG, "VcardActivity not handle such action: " + action);
        }
        finish();
    }
    
    private void handleImportRequest(int resId) {
        // There's three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final Sources sources = Sources.getInstance(this);
        final List<Account> accountList = sources.getAccounts(true);
        final int size = accountList.size();
        Log.v(TAG, "account num =  " + size);

        if (size > 1) {
            // showDialog(resId);
            AccountSelectionUtil.getSelectAccountDialog(this, resId);
        }

        AccountSelectionUtil.doImport(this, resId, (size == 1 ? accountList.get(0) : null));
    }
}
