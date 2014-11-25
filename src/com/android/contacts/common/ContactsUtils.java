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

package com.android.contacts.common;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.DisplayPhoto;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Pair;

import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.dataitem.ImDataItem;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.model.AccountTypeManager;

import java.util.List;

public class ContactsUtils {
    private static final String TAG = "ContactsUtils";

    // Telecomm related schemes are in CallUtil
    public static final String SCHEME_IMTO = "imto";
    public static final String SCHEME_MAILTO = "mailto";
    public static final String SCHEME_SMSTO = "smsto";

    private static final int DEFAULT_THUMBNAIL_SIZE = 96;

    private static int sThumbnailSize = -1;

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
     * Returns the size (width and height) of thumbnail pictures as configured in the provider. This
     * can safely be called from the UI thread, as the provider can serve this without performing
     * a database access
     */
    public static int getThumbnailSize(Context context) {
        if (sThumbnailSize == -1) {
            final Cursor c = context.getContentResolver().query(
                    DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                    new String[] { DisplayPhoto.THUMBNAIL_MAX_DIM }, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        sThumbnailSize = c.getInt(0);
                    }
                } finally {
                    c.close();
                }
            }
        }
        return sThumbnailSize != -1 ? sThumbnailSize : DEFAULT_THUMBNAIL_SIZE;
    }

    private static Intent getCustomImIntent(ImDataItem im, int protocol) {
        String host = im.getCustomProtocol();
        final String data = im.getData();
        if (TextUtils.isEmpty(data)) {
            return null;
        }
        if (protocol != Im.PROTOCOL_CUSTOM) {
            // Try bringing in a well-known host for specific protocols
            host = ContactsUtils.lookupProviderNameFromId(protocol);
        }
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        final String authority = host.toLowerCase();
        final Uri imUri = new Uri.Builder().scheme(SCHEME_IMTO).authority(
                authority).appendPath(data).build();
        final Intent intent = new Intent(Intent.ACTION_SENDTO, imUri);
        return intent;
    }

    /**
     * Returns the proper Intent for an ImDatItem. If available, a secondary intent is stored
     * in the second Pair slot
     */
    public static Pair<Intent, Intent> buildImIntent(Context context, ImDataItem im) {
        Intent intent = null;
        Intent secondaryIntent = null;
        final boolean isEmail = im.isCreatedFromEmail();

        if (!isEmail && !im.isProtocolValid()) {
            return new Pair<>(null, null);
        }

        final String data = im.getData();
        if (TextUtils.isEmpty(data)) {
            return new Pair<>(null, null);
        }

        final int protocol = isEmail ? Im.PROTOCOL_GOOGLE_TALK : im.getProtocol();

        if (protocol == Im.PROTOCOL_GOOGLE_TALK) {
            final int chatCapability = im.getChatCapability();
            if ((chatCapability & Im.CAPABILITY_HAS_CAMERA) != 0) {
                intent = new Intent(Intent.ACTION_SENDTO,
                                Uri.parse("xmpp:" + data + "?message"));
                secondaryIntent = new Intent(Intent.ACTION_SENDTO,
                        Uri.parse("xmpp:" + data + "?call"));
            } else if ((chatCapability & Im.CAPABILITY_HAS_VOICE) != 0) {
                // Allow Talking and Texting
                intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
                secondaryIntent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?call"));
            } else {
                intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
            }
        } else {
            // Build an IM Intent
            intent = getCustomImIntent(im, protocol);
        }
        return new Pair<>(intent, secondaryIntent);
    }
}
