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

package com.android.contacts.quickcontact;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.model.AccountType.EditType;
import com.android.contacts.model.DataKind;
import com.android.contacts.util.Constants;
import com.android.contacts.util.PhoneCapabilityTester;
import com.android.contacts.util.StructuredPostalUtils;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.WebAddress;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
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

    private CharSequence mBody;
    private CharSequence mSubtitle;
    private Intent mIntent;
    private Intent mAlternateIntent;
    private int mAlternateIconDescriptionRes;
    private int mAlternateIconRes;
    private int mPresence = -1;

    private Uri mDataUri;
    private long mDataId;
    private boolean mIsPrimary;

    /**
     * Create an action from common {@link Data} elements.
     */
    public DataAction(Context context, String mimeType, DataKind kind, long dataId,
            ContentValues entryValues) {
        mContext = context;
        mKind = kind;
        mMimeType = mimeType;

        // Determine type for subtitle
        mSubtitle = "";
        if (kind.typeColumn != null) {
            if (entryValues.containsKey(kind.typeColumn)) {
                final int typeValue = entryValues.getAsInteger(kind.typeColumn);

                // get type string
                for (EditType type : kind.typeList) {
                    if (type.rawValue == typeValue) {
                        if (type.customColumn == null) {
                            // Non-custom type. Get its description from the resource
                            mSubtitle = context.getString(type.labelRes);
                        } else {
                            // Custom type. Read it from the database
                            mSubtitle = entryValues.getAsString(type.customColumn);
                        }
                        break;
                    }
                }
            }
        }

        final Integer superPrimary = entryValues.getAsInteger(Data.IS_SUPER_PRIMARY);
        mIsPrimary = superPrimary != null && superPrimary != 0;

        if (mKind.actionBody != null) {
            mBody = mKind.actionBody.inflateUsing(context, entryValues);
        }

        mDataId = dataId;
        mDataUri = ContentUris.withAppendedId(Data.CONTENT_URI, dataId);

        final boolean hasPhone = PhoneCapabilityTester.isPhone(mContext);
        final boolean hasSms = PhoneCapabilityTester.isSmsIntentRegistered(mContext);

        // Handle well-known MIME-types with special care
        if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
            if (PhoneCapabilityTester.isPhone(mContext)) {
                final String number = entryValues.getAsString(Phone.NUMBER);
                if (!TextUtils.isEmpty(number)) {

                    final Intent phoneIntent = hasPhone ? ContactsUtils.getCallIntent(number)
                            : null;
                    final Intent smsIntent = hasSms ? new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts(Constants.SCHEME_SMSTO, number, null)) : null;

                    // Configure Icons and Intents. Notice actionIcon is already set to the phone
                    if (hasPhone && hasSms) {
                        mIntent = phoneIntent;
                        mAlternateIntent = smsIntent;
                        mAlternateIconRes = kind.iconAltRes;
                        mAlternateIconDescriptionRes = kind.iconAltDescriptionRes;
                    } else if (hasPhone) {
                        mIntent = phoneIntent;
                    } else if (hasSms) {
                        mIntent = smsIntent;
                    }
                }
            }
        } else if (SipAddress.CONTENT_ITEM_TYPE.equals(mimeType)) {
            if (PhoneCapabilityTester.isSipPhone(mContext)) {
                final String address = entryValues.getAsString(SipAddress.SIP_ADDRESS);
                if (!TextUtils.isEmpty(address)) {
                    final Uri callUri = Uri.fromParts(Constants.SCHEME_SIP, address, null);
                    mIntent = ContactsUtils.getCallIntent(callUri);
                    // Note that this item will get a SIP-specific variant
                    // of the "call phone" icon, rather than the standard
                    // app icon for the Phone app (which we show for
                    // regular phone numbers.)  That's because the phone
                    // app explicitly specifies an android:icon attribute
                    // for the SIP-related intent-filters in its manifest.
                }
            }
        } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final String address = entryValues.getAsString(Email.DATA);
            if (!TextUtils.isEmpty(address)) {
                final Uri mailUri = Uri.fromParts(Constants.SCHEME_MAILTO, address, null);
                mIntent = new Intent(Intent.ACTION_SENDTO, mailUri);
            }

        } else if (Website.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final String url = entryValues.getAsString(Website.URL);
            if (!TextUtils.isEmpty(url)) {
                WebAddress webAddress = new WebAddress(url);
                mIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webAddress.toString()));
            }

        } else if (Im.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final boolean isEmail = Email.CONTENT_ITEM_TYPE.equals(
                    entryValues.getAsString(Data.MIMETYPE));
            if (isEmail || isProtocolValid(entryValues)) {
                final int protocol = isEmail ? Im.PROTOCOL_GOOGLE_TALK :
                        entryValues.getAsInteger(Im.PROTOCOL);

                if (isEmail) {
                    // Use Google Talk string when using Email, and clear data
                    // Uri so we don't try saving Email as primary.
                    mSubtitle = Im.getProtocolLabel(context.getResources(), Im.PROTOCOL_GOOGLE_TALK,
                            null);
                    mDataUri = null;
                }

                String host = entryValues.getAsString(Im.CUSTOM_PROTOCOL);
                String data = entryValues.getAsString(isEmail ? Email.DATA : Im.DATA);
                if (protocol != Im.PROTOCOL_CUSTOM) {
                    // Try bringing in a well-known host for specific protocols
                    host = ContactsUtils.lookupProviderNameFromId(protocol);
                }

                if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(data)) {
                    final String authority = host.toLowerCase();
                    final Uri imUri = new Uri.Builder().scheme(Constants.SCHEME_IMTO).authority(
                            authority).appendPath(data).build();
                    mIntent = new Intent(Intent.ACTION_SENDTO, imUri);

                    // If the address is also available for a video chat, we'll show the capability
                    // as a secondary action.
                    final Integer chatCapabilityObj = entryValues.getAsInteger(Im.CHAT_CAPABILITY);
                    final int chatCapability = chatCapabilityObj == null ? 0 : chatCapabilityObj;
                    final boolean isVideoChatCapable =
                            (chatCapability & Im.CAPABILITY_HAS_CAMERA) != 0;
                    final boolean isAudioChatCapable =
                            (chatCapability & Im.CAPABILITY_HAS_VOICE) != 0;
                    if (isVideoChatCapable || isAudioChatCapable) {
                        mAlternateIntent = new Intent(
                                Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?call"));
                        if (isVideoChatCapable) {
                            mAlternateIconRes = R.drawable.sym_action_videochat_holo_light;
                            mAlternateIconDescriptionRes = R.string.video_chat;
                        } else {
                            mAlternateIconRes = R.drawable.sym_action_audiochat_holo_light;
                            mAlternateIconDescriptionRes = R.string.audio_chat;
                        }
                    }
                }
            }
        } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final String postalAddress =
                    entryValues.getAsString(StructuredPostal.FORMATTED_ADDRESS);
            if (!TextUtils.isEmpty(postalAddress)) {
                mIntent = StructuredPostalUtils.getViewPostalAddressIntent(postalAddress);
            }
        }

        if (mIntent == null) {
            // Otherwise fall back to default VIEW action
            mIntent = new Intent(Intent.ACTION_VIEW);
            mIntent.setDataAndType(mDataUri, mimeType);
        }

        mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
    }

    @Override
    public int getPresence() {
        return mPresence;
    }

    public void setPresence(int presence) {
        mPresence = presence;
    }

    private boolean isProtocolValid(ContentValues entryValues) {
        final String protocol = entryValues.getAsString(Im.PROTOCOL);
        if (protocol == null) {
            return false;
        }
        try {
            Integer.valueOf(protocol);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    @Override
    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    @Override
    public CharSequence getBody() {
        return mBody;
    }

    @Override
    public String getMimeType() {
        return mMimeType;
    }

    @Override
    public Uri getDataUri() {
        return mDataUri;
    }

    @Override
    public long getDataId() {
        return mDataId;
    }

    @Override
    public Boolean isPrimary() {
        return mIsPrimary;
    }

    @Override
    public Drawable getAlternateIcon() {
        if (mAlternateIconRes == 0) return null;

        final String resourcePackageName = mKind.resourcePackageName;
        if (resourcePackageName == null) {
            return mContext.getResources().getDrawable(mAlternateIconRes);
        }

        final PackageManager pm = mContext.getPackageManager();
        return pm.getDrawable(resourcePackageName, mAlternateIconRes, null);
    }

    @Override
    public String getAlternateIconDescription() {
        if (mAlternateIconDescriptionRes == 0) return null;
        return mContext.getResources().getString(mAlternateIconDescriptionRes);
    }

    @Override
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public Intent getAlternateIntent() {
        return mAlternateIntent;
    }

    @Override
    public boolean collapseWith(Action other) {
        if (!shouldCollapseWith(other)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean shouldCollapseWith(Action t) {
        if (t == null) {
            return false;
        }
        if (!(t instanceof DataAction)) {
            Log.e(TAG, "t must be DataAction");
            return false;
        }
        DataAction that = (DataAction)t;
        if (!ContactsUtils.shouldCollapse(mMimeType, mBody, that.mMimeType, that.mBody)) {
            return false;
        }
        if (!TextUtils.equals(mMimeType, that.mMimeType)
                || !ContactsUtils.areIntentActionEqual(mIntent, that.mIntent)) {
            return false;
        }
        return true;
    }
}
