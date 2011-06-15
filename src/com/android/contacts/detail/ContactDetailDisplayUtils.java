/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.detail;

import com.android.contacts.ContactLoader;
import com.android.contacts.ContactLoader.Result;
import com.android.contacts.R;
import com.android.contacts.format.FormatUtils;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.ContactBadgeUtil;

import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.Entity.NamedContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * This class contains utility methods to bind high-level contact details
 * (meaning name, phonetic name, job, and attribution) from a
 * {@link ContactLoader.Result} data object to appropriate {@link View}s.
 */
public class ContactDetailDisplayUtils {

    private static final int PHOTO_FADE_IN_ANIMATION_DURATION_MILLIS = 100;

    private ContactDetailDisplayUtils() {
        // Disallow explicit creation of this class.
    }

    /**
     * Returns the display name of the contact. Depending on the preference for
     * display name ordering, the contact's first name may be bolded if
     * possible. Returns empty string if there is no display name.
     */
    public static CharSequence getDisplayName(Context context, Result contactData) {
        CharSequence displayName = contactData.getDisplayName();
        CharSequence altDisplayName = contactData.getAltDisplayName();
        ContactsPreferences prefs = new ContactsPreferences(context);
        CharSequence styledName = "";
        if (!TextUtils.isEmpty(displayName) && !TextUtils.isEmpty(altDisplayName)) {
            if (prefs.getDisplayOrder() == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                int overlapPoint = FormatUtils.overlapPoint(
                        displayName.toString(), altDisplayName.toString());
                if (overlapPoint > 0) {
                    styledName = FormatUtils.applyStyleToSpan(Typeface.BOLD,
                            displayName, 0, overlapPoint, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    styledName = displayName;
                }
            } else {
                // Displaying alternate display name.
                int overlapPoint = FormatUtils.overlapPoint(
                        altDisplayName.toString(), displayName.toString());
                if (overlapPoint > 0) {
                    styledName = FormatUtils.applyStyleToSpan(Typeface.BOLD,
                            altDisplayName, overlapPoint, altDisplayName.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    styledName = altDisplayName;
                }
            }
        }
        return styledName;
    }

    /**
     * Returns the phonetic name of the contact or null if there isn't one.
     */
    public static String getPhoneticName(Context context, Result contactData) {
        String phoneticName = contactData.getPhoneticName();
        if (!TextUtils.isEmpty(phoneticName)) {
            return phoneticName;
        }
        return null;
    }

    /**
     * Returns the attribution string for the contact. This could either specify
     * that this is a joined contact or specify the contact directory that the
     * contact came from. Returns null if there is none applicable.
     */
    public static String getAttribution(Context context, Result contactData) {
        // Check if this is a joined contact
        if (contactData.getEntities().size() > 1) {
            return context.getString(R.string.indicator_joined_contact);
        } else if (contactData.isDirectoryEntry()) {
            // This contact is from a directory
            String directoryDisplayName = contactData.getDirectoryDisplayName();
            String directoryType = contactData.getDirectoryType();
            String displayName = !TextUtils.isEmpty(directoryDisplayName)
                    ? directoryDisplayName
                    : directoryType;
            return context.getString(R.string.contact_directory_description, displayName);
        }
        return null;
    }

    /**
     * Returns the organization of the contact. If several organizations are given,
     * the first one is used. Returns null if not applicable.
     */
    public static String getCompany(Context context, Result contactData) {
        final boolean displayNameIsOrganization = contactData.getDisplayNameSource()
                == DisplayNameSources.ORGANIZATION;
        for (Entity entity : contactData.getEntities()) {
            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);

                if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final String company = entryValues.getAsString(Organization.COMPANY);
                    final String title = entryValues.getAsString(Organization.TITLE);
                    final String combined;
                    // We need to show company and title in a combined string. However, if the
                    // DisplayName is already the organization, it mirrors company or (if company
                    // is empty title). Make sure we don't show what's already shown as DisplayName
                    if (TextUtils.isEmpty(company)) {
                        combined = displayNameIsOrganization ? null : title;
                    } else {
                        if (TextUtils.isEmpty(title)) {
                            combined = displayNameIsOrganization ? null : company;
                        } else {
                            if (displayNameIsOrganization) {
                                combined = title;
                            } else {
                                combined = context.getString(
                                        R.string.organization_company_and_title,
                                        company, title);
                            }
                        }
                    }

                    if (!TextUtils.isEmpty(combined)) {
                        return combined;
                    }
                }
            }
        }
        return null;
    }


    /**
     * Sets the contact photo to display in the given {@link ImageView}. If bitmap is null, the
     * default placeholder image is shown.
     */
    public static void setPhoto(Context context, Result contactData, ImageView photoView) {
        if (contactData.isLoadingPhoto()) {
            photoView.setImageBitmap(null);
            return;
        }
        byte[] photo = contactData.getPhotoBinaryData();
        Bitmap bitmap = photo != null ? BitmapFactory.decodeByteArray(photo, 0, photo.length)
                : ContactBadgeUtil.loadPlaceholderPhoto(context);
        boolean fadeIn = contactData.isDirectoryEntry();
        if (photoView.getDrawable() == null && fadeIn) {
            AlphaAnimation animation = new AlphaAnimation(0, 1);
            animation.setDuration(PHOTO_FADE_IN_ANIMATION_DURATION_MILLIS);
            animation.setInterpolator(new AccelerateInterpolator());
            photoView.startAnimation(animation);
        }
        photoView.setImageBitmap(bitmap);
    }

    /**
     * Sets the starred state of this contact.
     */
    public static void setStarred(Result contactData, CheckBox starredView) {
        // Check if the starred state should be visible
        if (!contactData.isDirectoryEntry()) {
            starredView.setVisibility(View.VISIBLE);
            starredView.setChecked(contactData.getStarred());
        } else {
            starredView.setVisibility(View.GONE);
        }
    }

    /**
     * Set the social snippet text and date. If there isn't one, then set the view to gone.
     */
    public static void setSocialSnippetAndDate(Context context, Result contactData,
            TextView statusView, TextView dateView) {
        setDataOrHideIfNone(contactData.getSocialSnippet(), statusView);
        setDataOrHideIfNone(ContactBadgeUtil.getSocialDate(contactData, context), dateView);
    }

    /**
     * Sets the phonetic name of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setPhoneticName(Context context, Result contactData, TextView textView) {
        setDataOrHideIfNone(getPhoneticName(context, contactData), textView);
    }

    /**
     * Sets the attribution contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setAttribution(Context context, Result contactData, TextView textView) {
        setDataOrHideIfNone(getAttribution(context, contactData), textView);
    }

    /**
     * Helper function to display the given text in the {@link TextView} or
     * hides the {@link TextView} if the text is empty or null.
     */
    private static void setDataOrHideIfNone(CharSequence textToDisplay, TextView textView) {
        if (!TextUtils.isEmpty(textToDisplay)) {
            textView.setText(textToDisplay);
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setText(null);
            textView.setVisibility(View.GONE);
        }
    }

}