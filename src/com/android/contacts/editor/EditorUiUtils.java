/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.editor;

import static android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;
import static com.android.contacts.common.util.MaterialColorMapUtils.getDefaultPrimaryAndSecondaryColors;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.ImageView;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageProvider;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.GoogleAccountType;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.util.ContactPhotoUtils;
import com.android.contacts.widget.QuickContactImageView;

import com.google.common.collect.Maps;

import java.io.FileNotFoundException;
import java.util.HashMap;

/**
 * Utility methods for creating contact editor.
 */
@NeededForTesting
public class EditorUiUtils {

    // Maps DataKind.mimeType to editor view layouts.
    private static final HashMap<String, Integer> mimetypeLayoutMap = Maps.newHashMap();
    static {
        // Generally there should be a layout mapped to each existing DataKind mimetype but lots of
        // them use the default text_fields_editor_view which we return as default so they don't
        // need to be mapped.
        //
        // Other possible mime mappings are:
        // DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME
        // Nickname.CONTENT_ITEM_TYPE
        // Email.CONTENT_ITEM_TYPE
        // StructuredPostal.CONTENT_ITEM_TYPE
        // Im.CONTENT_ITEM_TYPE
        // Note.CONTENT_ITEM_TYPE
        // Organization.CONTENT_ITEM_TYPE
        // Phone.CONTENT_ITEM_TYPE
        // SipAddress.CONTENT_ITEM_TYPE
        // Website.CONTENT_ITEM_TYPE
        // Relation.CONTENT_ITEM_TYPE
        //
        // Un-supported mime types need to mapped with -1.

        mimetypeLayoutMap.put(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME,
                R.layout.phonetic_name_editor_view);
        mimetypeLayoutMap.put(StructuredName.CONTENT_ITEM_TYPE,
                R.layout.structured_name_editor_view);
        mimetypeLayoutMap.put(GroupMembership.CONTENT_ITEM_TYPE, -1);
        mimetypeLayoutMap.put(Photo.CONTENT_ITEM_TYPE, -1);
        mimetypeLayoutMap.put(Event.CONTENT_ITEM_TYPE, R.layout.event_field_editor_view);
    }

    /**
     * Fetches a layout for a given mimetype.
     *
     * @param mimetype The mime type (e.g. StructuredName.CONTENT_ITEM_TYPE)
     * @return The layout resource id.
     */
    public static int getLayoutResourceId(String mimetype) {
        final Integer id = mimetypeLayoutMap.get(mimetype);
        if (id == null) {
            return R.layout.text_fields_editor_view;
        }
        return id;
    }

    /**
     * Returns the account name and account type labels to display for local accounts.
     */
    @NeededForTesting
    public static Pair<String,String> getLocalAccountInfo(Context context,
            String accountName, AccountType accountType) {
        if (TextUtils.isEmpty(accountName)) {
            return new Pair<>(
                    /* accountName =*/ null,
                    context.getString(R.string.local_profile_title));
        }
        return new Pair<>(
                accountName,
                context.getString(R.string.external_profile_title,
                        accountType.getDisplayLabel(context)));
    }

    /**
     * Returns the account name and account type labels to display for the given account type.
     */
    @NeededForTesting
    public static Pair<String,String> getAccountInfo(Context context, String accountName,
            AccountType accountType) {
        CharSequence accountTypeDisplayLabel = accountType.getDisplayLabel(context);
        if (TextUtils.isEmpty(accountTypeDisplayLabel)) {
            accountTypeDisplayLabel = context.getString(R.string.account_phone);
        }

        if (TextUtils.isEmpty(accountName)) {
            return new Pair<>(
                    /* accountName =*/ null,
                    context.getString(R.string.account_type_format, accountTypeDisplayLabel));
        }

        final String accountNameDisplayLabel =
                context.getString(R.string.from_account_format, accountName);

        if (GoogleAccountType.ACCOUNT_TYPE.equals(accountType.accountType)
                && accountType.dataSet == null) {
            return new Pair<>(
                    accountNameDisplayLabel,
                    context.getString(R.string.google_account_type_format, accountTypeDisplayLabel));
        }
        return new Pair<>(
                accountNameDisplayLabel,
                context.getString(R.string.account_type_format, accountTypeDisplayLabel));
    }

    /**
     * Returns a content description String for the container of the account information
     * returned by {@link #getAccountInfo}.
     */
    public static String getAccountInfoContentDescription(CharSequence accountName,
            CharSequence accountType) {
        final StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(accountType)) {
            builder.append(accountType).append('\n');
        }
        if (!TextUtils.isEmpty(accountName)) {
            builder.append(accountName);
        }
        return builder.toString();
    }

    /**
     * Return an icon that represents {@param mimeType}.
     */
    public static Drawable getMimeTypeDrawable(Context context, String mimeType) {
        switch (mimeType) {
            case StructuredName.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_person_black_24dp);
            case StructuredPostal.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_place_24dp);
            case SipAddress.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_dialer_sip_black_24dp);
            case Phone.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_phone_24dp);
            case Im.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_message_24dp);
            case Event.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_event_24dp);
            case Email.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_email_24dp);
            case Website.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_public_black_24dp);
            case Photo.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_camera_alt_black_24dp);
            case GroupMembership.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_people_black_24dp);
            case Organization.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_business_black_24dp);
            case Note.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(R.drawable.ic_insert_comment_black_24dp);
            case Relation.CONTENT_ITEM_TYPE:
                return context.getResources().getDrawable(
                        R.drawable.ic_circles_extended_black_24dp);
            default:
                return null;
        }
    }

    /**
     * Returns a ringtone string based on the ringtone URI and version #.
     */
    @NeededForTesting
    public static String getRingtoneStringFromUri(Uri pickedUri, int currentVersion) {
        if (isNewerThanM(currentVersion)) {
            if (pickedUri == null) return ""; // silent ringtone
            if (RingtoneManager.isDefault(pickedUri)) return null; // default ringtone
        }
        if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) return null;
        return pickedUri.toString();
    }

    /**
     * Returns a ringtone URI, based on the string and version #.
     */
    @NeededForTesting
    public static Uri getRingtoneUriFromString(String str, int currentVersion) {
        if (str != null) {
            if (isNewerThanM(currentVersion) && TextUtils.isEmpty(str)) return null;
            return Uri.parse(str);
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }

    private static boolean isNewerThanM(int currentVersion) {
        return currentVersion > Build.VERSION_CODES.M;
    }

    /** Returns the {@link Photo#PHOTO_FILE_ID} from the given ValuesDelta. */
    public static Long getPhotoFileId(ValuesDelta valuesDelta) {
        if (valuesDelta == null) return null;
        if (valuesDelta.getAfter() == null || valuesDelta.getAfter().get(Photo.PHOTO) == null) {
            return valuesDelta.getAsLong(Photo.PHOTO_FILE_ID);
        }
        return null;
    }

    /** Binds the full resolution image at the given Uri to the provided ImageView. */
    static void loadPhoto(ContactPhotoManager contactPhotoManager, ImageView imageView,
            Uri photoUri) {
        final DefaultImageProvider fallbackToPreviousImage = new DefaultImageProvider() {
            @Override
            public void applyDefaultImage(ImageView view, int extent, boolean darkTheme,
                    DefaultImageRequest defaultImageRequest) {
                // Before we finish setting the full sized image, don't change the current
                // image that is set in any way.
            }
        };
        contactPhotoManager.loadPhoto(imageView, photoUri, imageView.getWidth(),
                /* darkTheme =*/ false, /* isCircular =*/ false,
                /* defaultImageRequest =*/ null, fallbackToPreviousImage);
    }

    /** Decodes the Bitmap from the photo bytes from the given ValuesDelta. */
    public static Bitmap getPhotoBitmap(ValuesDelta valuesDelta) {
        if (valuesDelta == null) return null;
        final byte[] bytes = valuesDelta.getAsByteArray(Photo.PHOTO);
        if (bytes == null) return null;
        return BitmapFactory.decodeByteArray(bytes, /* offset =*/ 0, bytes.length);
    }

    /** Binds the default avatar to the given ImageView and tints it to match QuickContacts. */
    public static void setDefaultPhoto(ImageView imageView , Resources resources,
            MaterialPalette materialPalette) {
        // Use the default avatar drawable
        imageView.setImageDrawable(ContactPhotoManager.getDefaultAvatarDrawableForContact(
                resources, /* hires =*/ false, /* defaultImageRequest =*/ null));

        // Tint it to match the quick contacts
        if (imageView instanceof QuickContactImageView) {
            ((QuickContactImageView) imageView).setTint(materialPalette == null
                    ? getDefaultPrimaryAndSecondaryColors(resources).mPrimaryColor
                    : materialPalette.mPrimaryColor);
        }
    }

    /**  Returns compressed bitmap bytes from the given Uri, scaled to the thumbnail dimensions. */
    public static byte[] getCompressedThumbnailBitmapBytes(Context context, Uri uri)
            throws FileNotFoundException {
        final Bitmap bitmap = ContactPhotoUtils.getBitmapFromUri(context, uri);
        final int size = ContactsUtils.getThumbnailSize(context);
        final Bitmap bitmapScaled = Bitmap.createScaledBitmap(
                bitmap, size, size, /* filter =*/ false);
        return ContactPhotoUtils.compressBitmap(bitmapScaled);
    }
}
