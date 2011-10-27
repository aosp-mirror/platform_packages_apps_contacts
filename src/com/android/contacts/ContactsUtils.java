/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.AccountWithDataSet;
import com.android.contacts.test.NeededForTesting;
import com.android.i18n.phonenumbers.PhoneNumberUtil;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.location.CountryDetector;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.util.List;

public class ContactsUtils {
    private static final String TAG = "ContactsUtils";
    private static final String WAIT_SYMBOL_AS_STRING = String.valueOf(PhoneNumberUtils.WAIT);


    // TODO find a proper place for the canonical version of these
    public interface ProviderNames {
        String YAHOO = "Yahoo";
        String GTALK = "GTalk";
        String MSN = "MSN";
        String ICQ = "ICQ";
        String AIM = "AIM";
        String XMPP = "XMPP";
        String JABBER = "JABBER";
        String SKYPE = "SKYPE";
        String QQ = "QQ";
    }

    /**
     * This looks up the provider name defined in
     * ProviderNames from the predefined IM protocol id.
     * This is used for interacting with the IM application.
     *
     * @param protocol the protocol ID
     * @return the provider name the IM app uses for the given protocol, or null if no
     * provider is defined for the given protocol
     * @hide
     */
    public static String lookupProviderNameFromId(int protocol) {
        switch (protocol) {
            case Im.PROTOCOL_GOOGLE_TALK:
                return ProviderNames.GTALK;
            case Im.PROTOCOL_AIM:
                return ProviderNames.AIM;
            case Im.PROTOCOL_MSN:
                return ProviderNames.MSN;
            case Im.PROTOCOL_YAHOO:
                return ProviderNames.YAHOO;
            case Im.PROTOCOL_ICQ:
                return ProviderNames.ICQ;
            case Im.PROTOCOL_JABBER:
                return ProviderNames.JABBER;
            case Im.PROTOCOL_SKYPE:
                return ProviderNames.SKYPE;
            case Im.PROTOCOL_QQ:
                return ProviderNames.QQ;
        }
        return null;
    }

    /**
     * Test if the given {@link CharSequence} contains any graphic characters,
     * first checking {@link TextUtils#isEmpty(CharSequence)} to handle null.
     */
    public static boolean isGraphic(CharSequence str) {
        return !TextUtils.isEmpty(str) && TextUtils.isGraphic(str);
    }

    /**
     * Returns true if two objects are considered equal.  Two null references are equal here.
     */
    @NeededForTesting
    public static boolean areObjectsEqual(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    /**
     * Returns true if two data with mimetypes which represent values in contact entries are
     * considered equal for collapsing in the GUI. For caller-id, use
     * {@link PhoneNumberUtils#compare(Context, String, String)} instead
     */
    public static final boolean shouldCollapse(CharSequence mimetype1, CharSequence data1,
            CharSequence mimetype2, CharSequence data2) {
        // different mimetypes? don't collapse
        if (!TextUtils.equals(mimetype1, mimetype2)) return false;

        // exact same string? good, bail out early
        if (TextUtils.equals(data1, data2)) return true;

        // so if either is null, these two must be different
        if (data1 == null || data2 == null) return false;

        // if this is not about phone numbers, we know this is not a match (of course, some
        // mimetypes could have more sophisticated matching is the future, e.g. addresses)
        if (!TextUtils.equals(Phone.CONTENT_ITEM_TYPE, mimetype1)) return false;

        // Now do the full phone number thing. split into parts, seperated by waiting symbol
        // and compare them individually
        final String[] dataParts1 = data1.toString().split(WAIT_SYMBOL_AS_STRING);
        final String[] dataParts2 = data2.toString().split(WAIT_SYMBOL_AS_STRING);
        if (dataParts1.length != dataParts2.length) return false;
        final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        for (int i = 0; i < dataParts1.length; i++) {
            final String dataPart1 = dataParts1[i];
            final String dataPart2 = dataParts2[i];

            // substrings equal? shortcut, don't parse
            if (TextUtils.equals(dataPart1, dataPart2)) continue;

            // do a full parse of the numbers
            switch (util.isNumberMatch(dataPart1, dataPart2)) {
                case NOT_A_NUMBER:
                    // don't understand the numbers? let's play it safe
                    return false;
                case NO_MATCH:
                    return false;
                case EXACT_MATCH:
                case SHORT_NSN_MATCH:
                case NSN_MATCH:
                    break;
                default:
                    throw new IllegalStateException("Unknown result value from phone number " +
                            "library");
            }
        }
        return true;
    }

    /**
     * Returns true if two {@link Intent}s are both null, or have the same action.
     */
    public static final boolean areIntentActionEqual(Intent a, Intent b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return TextUtils.equals(a.getAction(), b.getAction());
    }

    /**
     * @return The ISO 3166-1 two letters country code of the country the user
     *         is in.
     */
    public static final String getCurrentCountryIso(Context context) {
        CountryDetector detector =
                (CountryDetector) context.getSystemService(Context.COUNTRY_DETECTOR);
        return detector.detectCountry().getCountryIso();
    }

    public static boolean areContactWritableAccountsAvailable(Context context) {
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(context).getAccounts(true /* writeable */);
        return !accounts.isEmpty();
    }

    public static boolean areGroupWritableAccountsAvailable(Context context) {
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(context).getGroupWritableAccounts();
        return !accounts.isEmpty();
    }

    /**
     * Returns the intent to launch for the given invitable account type and contact lookup URI.
     * This will return null if the account type is not invitable (i.e. there is no
     * {@link AccountType#getInviteContactActivityClassName()} or
     * {@link AccountType#resPackageName}).
     */
    public static Intent getInvitableIntent(AccountType accountType, Uri lookupUri) {
        String resPackageName = accountType.resPackageName;
        String className = accountType.getInviteContactActivityClassName();
        if (TextUtils.isEmpty(resPackageName) || TextUtils.isEmpty(className)) {
            return null;
        }
        Intent intent = new Intent();
        intent.setClassName(resPackageName, className);

        intent.setAction(ContactsContract.Intents.INVITE_CONTACT);

        // Data is the lookup URI.
        intent.setData(lookupUri);
        return intent;
    }

    /**
     * Returns a header view based on the R.layout.list_separator, where the
     * containing {@link TextView} is set using the given textResourceId.
     */
    public static View createHeaderView(Context context, int textResourceId) {
        View view = View.inflate(context, R.layout.list_separator, null);
        TextView textView = (TextView) view.findViewById(R.id.title);
        textView.setText(context.getString(textResourceId));
        return view;
    }

    /**
     * Returns the {@link Rect} with left, top, right, and bottom coordinates
     * that are equivalent to the given {@link View}'s bounds. This is equivalent to how the
     * target {@link Rect} is calculated in {@link QuickContact#showQuickContact}.
     */
    public static Rect getTargetRectFromView(Context context, View view) {
        final float appScale = context.getResources().getCompatibilityInfo().applicationScale;
        final int[] pos = new int[2];
        view.getLocationOnScreen(pos);

        final Rect rect = new Rect();
        rect.left = (int) (pos[0] * appScale + 0.5f);
        rect.top = (int) (pos[1] * appScale + 0.5f);
        rect.right = (int) ((pos[0] + view.getWidth()) * appScale + 0.5f);
        rect.bottom = (int) ((pos[1] + view.getHeight()) * appScale + 0.5f);
        return rect;
    }
}
