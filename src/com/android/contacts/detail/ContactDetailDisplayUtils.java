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
import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.format.FormatUtils;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.ContactBadgeUtil;
import com.android.contacts.util.StreamItemEntry;
import com.android.contacts.util.StreamItemPhotoEntry;
import com.google.common.annotations.VisibleForTesting;

import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.Entity.NamedContentValues;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * This class contains utility methods to bind high-level contact details
 * (meaning name, phonetic name, job, and attribution) from a
 * {@link ContactLoader.Result} data object to appropriate {@link View}s.
 */
public class ContactDetailDisplayUtils {
    private static final String TAG = "ContactDetailDisplayUtils";

    private static final int PHOTO_FADE_IN_ANIMATION_DURATION_MILLIS = 100;

    private ContactDetailDisplayUtils() {
        // Disallow explicit creation of this class.
    }

    /**
     * Returns the display name of the contact, using the current display order setting.
     * Returns res/string/missing_name if there is no display name.
     */
    public static CharSequence getDisplayName(Context context, Result contactData) {
        CharSequence displayName = contactData.getDisplayName();
        CharSequence altDisplayName = contactData.getAltDisplayName();
        ContactsPreferences prefs = new ContactsPreferences(context);
        CharSequence styledName = "";
        if (!TextUtils.isEmpty(displayName) && !TextUtils.isEmpty(altDisplayName)) {
            if (prefs.getDisplayOrder() == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                styledName = displayName;
            } else {
                styledName = altDisplayName;
            }
        } else {
            styledName = context.getResources().getString(R.string.missing_name);
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
        if (!contactData.isDirectoryEntry() && !contactData.isUserProfile()) {
            starredView.setVisibility(View.VISIBLE);
            starredView.setChecked(contactData.getStarred());
        } else {
            starredView.setVisibility(View.GONE);
        }
    }

    /**
     * Set the social snippet text. If there isn't one, then set the view to gone.
     */
    public static void setSocialSnippet(Context context, Result contactData, TextView statusView,
            ImageView statusPhotoView) {
        if (statusView == null) {
            return;
        }

        CharSequence snippet = null;
        String photoUri = null;
        if (!contactData.getStreamItems().isEmpty()) {
            StreamItemEntry firstEntry = contactData.getStreamItems().get(0);
            snippet = Html.fromHtml(firstEntry.getText());
            // Add quotes around the text
            snippet = context.getString(R.string.recent_updates_tab_text, snippet);
            if (!firstEntry.getPhotos().isEmpty()) {
                StreamItemPhotoEntry firstPhoto = firstEntry.getPhotos().get(0);
                photoUri = firstPhoto.getPhotoUri();

                // If displaying an image, hide the snippet text.
                snippet = null;
            }
        }
        setDataOrHideIfNone(snippet, statusView);
        if (photoUri != null) {
            ContactPhotoManager.getInstance(context).loadPhoto(
                    statusPhotoView, Uri.parse(photoUri));
            statusPhotoView.setVisibility(View.VISIBLE);
        } else {
            statusPhotoView.setVisibility(View.GONE);
        }
    }

    /** Creates the view that represents a stream item. */
    public static View createStreamItemView(LayoutInflater inflater, Context context,
            StreamItemEntry streamItem, LinearLayout parent) {
        View container = inflater.inflate(R.layout.stream_item_container, parent, false);
        ViewGroup contentTable = (ViewGroup) container.findViewById(R.id.stream_item_content);

        ContactPhotoManager contactPhotoManager = ContactPhotoManager.getInstance(context);
        List<StreamItemPhotoEntry> photos = streamItem.getPhotos();
        final int photoCount = photos.size();

        // Process the photos, two at a time.
        for (int index = 0; index < photoCount; index += 2) {
            final StreamItemPhotoEntry firstPhoto = photos.get(index);
            if (index + 1 < photoCount) {
                // Put in two photos, side by side.
                final StreamItemPhotoEntry secondPhoto = photos.get(index + 1);

                View photoContainer = inflater.inflate(R.layout.stream_item_row_two_images,
                        contentTable, false);
                loadPhoto(contactPhotoManager, firstPhoto, photoContainer,
                        R.id.stream_item_first_image);
                loadPhoto(contactPhotoManager, secondPhoto, photoContainer,
                        R.id.stream_item_second_image);
                contentTable.addView(photoContainer);
            } else {
                // Put in a single photo with text on the side.
                View photoContainer = inflater.inflate(
                        R.layout.stream_item_row_image_and_text, contentTable, false);
                loadPhoto(contactPhotoManager, firstPhoto, photoContainer,
                        R.id.stream_item_first_image);
                addStreamItemText(context, streamItem,
                        photoContainer.findViewById(R.id.stream_item_second_text));
                contentTable.addView(photoContainer);
            }
        }

        if (photoCount % 2 == 0) {
            // Even number of photos, add the text below them. Otherwise, it should have been
            // already added next to the last photo.
            View textContainer = inflater.inflate(R.layout.stream_item_row_text_only, contentTable,
                    false);
            addStreamItemText(context, streamItem, textContainer);
            contentTable.addView(textContainer);
        }

        if (parent != null) {
            parent.addView(container);
        }

        return container;
    }

    /** Loads a photo into an image view. The image view is identifiedc by the given id. */
    private static void loadPhoto(ContactPhotoManager contactPhotoManager,
            final StreamItemPhotoEntry firstPhoto, View photoContainer, int imageViewId) {
        ImageView firstImageView = (ImageView) photoContainer.findViewById(imageViewId);
        contactPhotoManager.loadPhoto(firstImageView, Uri.parse(firstPhoto.getPhotoUri()));
    }

    @VisibleForTesting
    static View addStreamItemText(Context context, StreamItemEntry streamItem, View rootView) {
        TextView htmlView = (TextView) rootView.findViewById(R.id.stream_item_html);
        TextView attributionView = (TextView) rootView.findViewById(
                R.id.stream_item_attribution);
        TextView commentsView = (TextView) rootView.findViewById(R.id.stream_item_comments);
        ImageGetter imageGetter = new DefaultImageGetter(context.getPackageManager());
        htmlView.setText(Html.fromHtml(streamItem.getText(), imageGetter, null));
        attributionView.setText(ContactBadgeUtil.getSocialDate(streamItem, context));
        if (streamItem.getComments() != null) {
            commentsView.setText(Html.fromHtml(streamItem.getComments(), imageGetter, null));
            commentsView.setVisibility(View.VISIBLE);
        } else {
            commentsView.setVisibility(View.GONE);
        }
        return rootView;
    }

    /**
     * Sets the display name of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setDisplayName(Context context, Result contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getDisplayName(context, contactData), textView);
    }

    /**
     * Sets the company and job title of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setCompanyName(Context context, Result contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getCompany(context, contactData), textView);
    }

    /**
     * Sets the phonetic name of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setPhoneticName(Context context, Result contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getPhoneticName(context, contactData), textView);
    }

    /**
     * Sets the attribution contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setAttribution(Context context, Result contactData, TextView textView) {
        if (textView == null) {
            return;
        }
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

    /** Fetcher for images from resources to be included in HTML text. */
    private static class DefaultImageGetter implements Html.ImageGetter {
        /** The scheme used to load resources. */
        private static final String RES_SCHEME = "res";

        private final PackageManager mPackageManager;

        public DefaultImageGetter(PackageManager packageManager) {
            mPackageManager = packageManager;
        }

        @Override
        public Drawable getDrawable(String source) {
            // Returning null means that a default image will be used.
            Uri uri;
            try {
                uri = Uri.parse(source);
            } catch (Throwable e) {
                Log.d(TAG, "Could not parse image source: " + source);
                return null;
            }
            if (!RES_SCHEME.equals(uri.getScheme())) {
                Log.d(TAG, "Image source does not correspond to a resource: " + source);
                return null;
            }
            // The URI authority represents the package name.
            String packageName = uri.getAuthority();

            Resources resources = getResourcesForResourceName(packageName);
            if (resources == null) {
                Log.d(TAG, "Could not parse image source: " + source);
                return null;
            }

            List<String> pathSegments = uri.getPathSegments();
            if (pathSegments.size() != 1) {
                Log.d(TAG, "Could not parse image source: " + source);
                return null;
            }

            final String name = pathSegments.get(0);
            final int resId = resources.getIdentifier(name, "drawable", packageName);

            if (resId == 0) {
                // Use the default image icon in this case.
                Log.d(TAG, "Cannot resolve resource identifier: " + source);
                return null;
            }

            try {
                return getResourceDrawable(resources, resId);
            } catch (NotFoundException e) {
                Log.d(TAG, "Resource not found: " + source, e);
                return null;
            }
        }

        /** Returns the drawable associated with the given id. */
        private Drawable getResourceDrawable(Resources resources, int resId)
                throws NotFoundException {
            Drawable drawable = resources.getDrawable(resId);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            return drawable;
        }

        /** Returns the {@link Resources} of the package of the given resource name. */
        private Resources getResourcesForResourceName(String packageName) {
            try {
                return mPackageManager.getResourcesForApplication(packageName);
            } catch (NameNotFoundException e) {
                Log.d(TAG, "Could not find package: " + packageName);
                return null;
            }
        }
    }

    /**
     * Sets an alpha value on the view.
     */
    public static void setAlphaOnViewBackground(View view, float alpha) {
        if (view != null) {
            // Convert alpha layer to a black background HEX color with an alpha value for better
            // performance (i.e. use setBackgroundColor() instead of setAlpha())
            view.setBackgroundColor((int) (alpha * 255) << 24);
        }
    }
}
