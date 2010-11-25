package com.android.contacts.quickcontact;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.model.AccountType.DataKind;
import com.android.contacts.util.Constants;
import com.android.contacts.util.PhoneCapabilityTester;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.text.TextUtils;
import android.util.Log;

/**
 * Description of a specific {@link Data#_ID} item, with style information
 * defined by a {@link DataKind}.
 */
public class DataAction implements Action {
    private static final String TAG = "DataAction";

    private final Context mContext;
    private final DataKind mKind;
    private final String mMimeType;

    private CharSequence mHeader;
    private CharSequence mBody;
    private Intent mIntent;

    private boolean mAlternate;
    private Uri mDataUri;
    private long mDataId;
    private boolean mIsPrimary;

    /**
     * Create an action from common {@link Data} elements.
     */
    public DataAction(Context context, String mimeType, DataKind kind,
            long dataId, Cursor cursor) {
        mContext = context;
        mKind = kind;
        mMimeType = mimeType;

        // Inflate strings from cursor
        mAlternate = Constants.MIME_SMS_ADDRESS.equals(mimeType);
        if (mAlternate && mKind.actionAltHeader != null) {
            mHeader = mKind.actionAltHeader.inflateUsing(context, cursor);
        } else if (mKind.actionHeader != null) {
            mHeader = mKind.actionHeader.inflateUsing(context, cursor);
        }

        if (getAsInt(cursor, Data.IS_SUPER_PRIMARY) != 0) {
            mIsPrimary = true;
        }

        if (mKind.actionBody != null) {
            mBody = mKind.actionBody.inflateUsing(context, cursor);
        }

        mDataId = dataId;
        mDataUri = ContentUris.withAppendedId(Data.CONTENT_URI, dataId);

        // Handle well-known MIME-types with special care
        if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
            if (PhoneCapabilityTester.isPhone(mContext)) {
                final String number = getAsString(cursor, Phone.NUMBER);
                if (!TextUtils.isEmpty(number)) {
                    final Uri callUri = Uri.fromParts(Constants.SCHEME_TEL, number, null);
                    mIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, callUri);
                }
            }
        } else if (SipAddress.CONTENT_ITEM_TYPE.equals(mimeType)) {
            if (PhoneCapabilityTester.isSipPhone(mContext)) {
                final String address = getAsString(cursor, SipAddress.SIP_ADDRESS);
                if (!TextUtils.isEmpty(address)) {
                    final Uri callUri = Uri.fromParts(Constants.SCHEME_SIP, address, null);
                    mIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, callUri);
                    // Note that this item will get a SIP-specific variant
                    // of the "call phone" icon, rather than the standard
                    // app icon for the Phone app (which we show for
                    // regular phone numbers.)  That's because the phone
                    // app explicitly specifies an android:icon attribute
                    // for the SIP-related intent-filters in its manifest.
                }
            }
        } else if (Constants.MIME_SMS_ADDRESS.equals(mimeType)) {
            if (PhoneCapabilityTester.isSmsIntentRegistered(mContext)) {
                final String number = getAsString(cursor, Phone.NUMBER);
                if (!TextUtils.isEmpty(number)) {
                    final Uri smsUri = Uri.fromParts(Constants.SCHEME_SMSTO, number, null);
                    mIntent = new Intent(Intent.ACTION_SENDTO, smsUri);
                }
            }
        } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final String address = getAsString(cursor, Email.DATA);
            if (!TextUtils.isEmpty(address)) {
                final Uri mailUri = Uri.fromParts(Constants.SCHEME_MAILTO, address, null);
                mIntent = new Intent(Intent.ACTION_SENDTO, mailUri);
            }

        } else if (Website.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final String url = getAsString(cursor, Website.URL);
            if (!TextUtils.isEmpty(url)) {
                mIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }

        } else if (Im.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final boolean isEmail = Email.CONTENT_ITEM_TYPE.equals(
                    getAsString(cursor, Data.MIMETYPE));
            if (isEmail || isProtocolValid(cursor)) {
                final int protocol = isEmail ? Im.PROTOCOL_GOOGLE_TALK :
                    getAsInt(cursor, Im.PROTOCOL);

                if (isEmail) {
                    // Use Google Talk string when using Email, and clear data
                    // Uri so we don't try saving Email as primary.
                    mHeader = context.getText(R.string.chat_gtalk);
                    mDataUri = null;
                }

                String host = getAsString(cursor, Im.CUSTOM_PROTOCOL);
                String data = getAsString(cursor,
                        isEmail ? Email.DATA : Im.DATA);
                if (protocol != Im.PROTOCOL_CUSTOM) {
                    // Try bringing in a well-known host for specific protocols
                    host = ContactsUtils.lookupProviderNameFromId(protocol);
                }

                if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(data)) {
                    final String authority = host.toLowerCase();
                    final Uri imUri = new Uri.Builder().scheme(Constants.SCHEME_IMTO).authority(
                            authority).appendPath(data).build();
                    mIntent = new Intent(Intent.ACTION_SENDTO, imUri);
                }
            }
        }

        if (mIntent == null) {
            // Otherwise fall back to default VIEW action
            mIntent = new Intent(Intent.ACTION_VIEW, mDataUri);
        }

        // Always launch as new task, since we're like a launcher
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    /** Read {@link String} from the given {@link Cursor}. */
    private static String getAsString(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        return cursor.getString(index);
    }

    /** Read {@link Integer} from the given {@link Cursor}. */
    private static int getAsInt(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        return cursor.getInt(index);
    }

    private boolean isProtocolValid(Cursor cursor) {
        final int columnIndex = cursor.getColumnIndex(Im.PROTOCOL);
        if (cursor.isNull(columnIndex)) {
            return false;
        }
        try {
            Integer.valueOf(cursor.getString(columnIndex));
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public CharSequence getHeader() {
        return mHeader;
    }

    /** {@inheritDoc} */
    @Override
    public CharSequence getBody() {
        return mBody;
    }

    /** {@inheritDoc} */
    @Override
    public String getMimeType() {
        return mMimeType;
    }

    /** {@inheritDoc} */
    @Override
    public Uri getDataUri() {
        return mDataUri;
    }

    /** {@inheritDoc} */
    @Override
    public long getDataId() {
        return mDataId;
    }

    /** {@inheritDoc} */
    @Override
    public Boolean isPrimary() {
        return mIsPrimary;
    }

    /** {@inheritDoc} */
    @Override
    public Drawable getFallbackIcon() {
        // Bail early if no valid resources
        final String resPackageName = mKind.resPackageName;
        if (resPackageName == null) return null;

        final PackageManager pm = mContext.getPackageManager();
        if (mAlternate && mKind.iconAltRes != -1) {
            return pm.getDrawable(resPackageName, mKind.iconAltRes, null);
        } else if (mKind.iconRes != -1) {
            return pm.getDrawable(resPackageName, mKind.iconRes, null);
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Intent getIntent() {
        return mIntent;
    }

    /** {@inheritDoc} */
    @Override
    public boolean collapseWith(Action other) {
        if (!shouldCollapseWith(other)) {
            return false;
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldCollapseWith(Action t) {
        if (t == null) {
            return false;
        }
        if (!(t instanceof DataAction)) {
            Log.e(TAG, "t must be DataAction");
            return false;
        }
        DataAction other = (DataAction)t;
        if (!ContactsUtils.areObjectsEqual(mKind, other.mKind)) {
            return false;
        }
        if (!ContactsUtils.shouldCollapse(mContext, mMimeType, mBody, other.mMimeType,
                other.mBody)) {
            return false;
        }
        if (!TextUtils.equals(mMimeType, other.mMimeType)
                || !ContactsUtils.areIntentActionEqual(mIntent, other.mIntent)
                ) {
            return false;
        }
        return true;
    }
}
