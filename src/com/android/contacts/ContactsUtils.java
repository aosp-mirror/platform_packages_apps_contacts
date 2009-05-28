package com.android.contacts;


import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Postal;
import android.provider.Im.ProviderNames;

import android.content.Context;
import android.text.TextUtils;

public class ContactsUtils {
    
    public static final CharSequence getDisplayLabel(Context context, String mimetype, int type,
            CharSequence label) {
        CharSequence display = "";
        final int customType;
        final int defaultType;
        final int arrayResId;

        if (Phone.CONTENT_ITEM_TYPE.equals(mimetype)) {
            defaultType = Phone.TYPE_HOME;
            customType = Phone.TYPE_CUSTOM;
            arrayResId = com.android.internal.R.array.phoneTypes;
        } else if (Email.CONTENT_ITEM_TYPE.equals(mimetype)) {
            defaultType = Email.TYPE_HOME;
            customType = Email.TYPE_CUSTOM;
            arrayResId = com.android.internal.R.array.emailAddressTypes;
        } else if (Postal.CONTENT_ITEM_TYPE.equals(mimetype)) {
            defaultType = Postal.TYPE_HOME;
            customType = Postal.TYPE_CUSTOM;
            arrayResId = com.android.internal.R.array.postalAddressTypes;
        } else if (Organization.CONTENT_ITEM_TYPE.equals(mimetype)) {
            defaultType = Organization.TYPE_HOME;
            customType = Organization.TYPE_CUSTOM;
            arrayResId = com.android.internal.R.array.organizationTypes;
        } else {
            // Can't return display label for given mimetype.
            return display;
        }
        
        if (type != customType) {
            CharSequence[] labels = context.getResources().getTextArray(arrayResId);
            try {
                display = labels[type - 1];
            } catch (ArrayIndexOutOfBoundsException e) {
                display = labels[defaultType - 1];
            }
        } else {
            if (!TextUtils.isEmpty(label)) {
                display = label;
            }
        }
        return display;
    }
    
    public static Object decodeImProtocol(String encodedString) {
        if (encodedString == null) {
            return null;
        }

        if (encodedString.startsWith("pre:")) {
            return Integer.parseInt(encodedString.substring(4));
        }

        if (encodedString.startsWith("custom:")) {
            return encodedString.substring(7);
        }

        throw new IllegalArgumentException(
                "the value is not a valid encoded protocol, " + encodedString);
    }
    

    
    /**
     * This looks up the provider name defined in
     * {@link android.provider.Im.ProviderNames} from the predefined IM protocol id.
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

}
