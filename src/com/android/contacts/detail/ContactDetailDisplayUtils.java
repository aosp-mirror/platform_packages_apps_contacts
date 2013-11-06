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

import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.Preferences;
import android.provider.ContactsContract.StreamItems;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.OrganizationDataItem;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.util.StreamItemEntry;
import com.android.contacts.util.ContactBadgeUtil;
import com.android.contacts.util.HtmlUtils;
import com.android.contacts.util.MoreMath;
import com.android.contacts.util.StreamItemPhotoEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

import java.util.List;

/**
 * This class contains utility methods to bind high-level contact details
 * (meaning name, phonetic name, job, and attribution) from a
 * {@link Contact} data object to appropriate {@link View}s.
 */
public class ContactDetailDisplayUtils {
    private static final String TAG = "ContactDetailDisplayUtils";

    /**
     * Tag object used for stream item photos.
     */
    public static class StreamPhotoTag {
        public final StreamItemEntry streamItem;
        public final StreamItemPhotoEntry streamItemPhoto;

        public StreamPhotoTag(StreamItemEntry streamItem, StreamItemPhotoEntry streamItemPhoto) {
            this.streamItem = streamItem;
            this.streamItemPhoto = streamItemPhoto;
        }

        public Uri getStreamItemPhotoUri() {
            final Uri.Builder builder = StreamItems.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, streamItem.getId());
            builder.appendPath(StreamItems.StreamItemPhotos.CONTENT_DIRECTORY);
            ContentUris.appendId(builder, streamItemPhoto.getId());
            return builder.build();
        }
    }

    private ContactDetailDisplayUtils() {
        // Disallow explicit creation of this class.
    }

    /**
     * Returns the display name of the contact, using the current display order setting.
     * Returns res/string/missing_name if there is no display name.
     */
    public static CharSequence getDisplayName(Context context, Contact contactData) {
        ContactsPreferences prefs = new ContactsPreferences(context);
        final CharSequence displayName = contactData.getDisplayName();
        if (prefs.getDisplayOrder() == Preferences.DISPLAY_ORDER_PRIMARY) {
            if (!TextUtils.isEmpty(displayName)) {
                return displayName;
            }
        } else {
            final CharSequence altDisplayName = contactData.getAltDisplayName();
            if (!TextUtils.isEmpty(altDisplayName)) {
                return altDisplayName;
            }
        }
        return context.getResources().getString(R.string.missing_name);
    }

    /**
     * Returns the phonetic name of the contact or null if there isn't one.
     */
    public static String getPhoneticName(Context context, Contact contactData) {
        String phoneticName = contactData.getPhoneticName();
        if (!TextUtils.isEmpty(phoneticName)) {
            return phoneticName;
        }
        return null;
    }

    /**
     * Returns the attribution string for the contact, which may specify the contact directory that
     * the contact came from. Returns null if there is none applicable.
     */
    public static String getAttribution(Context context, Contact contactData) {
        if (contactData.isDirectoryEntry()) {
            String directoryDisplayName = contactData.getDirectoryDisplayName();
            String directoryType = contactData.getDirectoryType();
            final String displayName;
            if (!TextUtils.isEmpty(directoryDisplayName)) {
                displayName = directoryDisplayName;
            } else if (!TextUtils.isEmpty(directoryType)) {
                displayName = directoryType;
            } else {
                return null;
            }
            return context.getString(R.string.contact_directory_description, displayName);
        }
        return null;
    }

    /**
     * Returns the organization of the contact. If several organizations are given,
     * the first one is used. Returns null if not applicable.
     */
    public static String getCompany(Context context, Contact contactData) {
        final boolean displayNameIsOrganization = contactData.getDisplayNameSource()
                == DisplayNameSources.ORGANIZATION;
        for (RawContact rawContact : contactData.getRawContacts()) {
            for (DataItem dataItem : Iterables.filter(
                    rawContact.getDataItems(), OrganizationDataItem.class)) {
                OrganizationDataItem organization = (OrganizationDataItem) dataItem;
                final String company = organization.getCompany();
                final String title = organization.getTitle();
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
        return null;
    }

    /**
     * Sets the starred state of this contact.
     */
    public static void configureStarredImageView(ImageView starredView, boolean isDirectoryEntry,
            boolean isUserProfile, boolean isStarred) {
        // Check if the starred state should be visible
        if (!isDirectoryEntry && !isUserProfile) {
            starredView.setVisibility(View.VISIBLE);
            final int resId = isStarred
                    ? R.drawable.btn_star_on_normal_holo_light
                    : R.drawable.btn_star_off_normal_holo_light;
            starredView.setImageResource(resId);
            starredView.setTag(isStarred);
            starredView.setContentDescription(starredView.getResources().getString(
                    isStarred ? R.string.menu_removeStar : R.string.menu_addStar));
        } else {
            starredView.setVisibility(View.GONE);
        }
    }

    /**
     * Sets the starred state of this contact.
     */
    public static void configureStarredMenuItem(MenuItem starredMenuItem, boolean isDirectoryEntry,
            boolean isUserProfile, boolean isStarred) {
        // Check if the starred state should be visible
        if (!isDirectoryEntry && !isUserProfile) {
            starredMenuItem.setVisible(true);
            final int resId = isStarred
                    ? R.drawable.btn_star_on_normal_holo_light
                    : R.drawable.btn_star_off_normal_holo_light;
            starredMenuItem.setIcon(resId);
            starredMenuItem.setChecked(isStarred);
            starredMenuItem.setTitle(isStarred ? R.string.menu_removeStar : R.string.menu_addStar);
        } else {
            starredMenuItem.setVisible(false);
        }
    }

    /**
     * Set the social snippet text. If there isn't one, then set the view to gone.
     */
    public static void setSocialSnippet(Context context, Contact contactData, TextView statusView,
            ImageView statusPhotoView) {
        if (statusView == null) {
            return;
        }

        CharSequence snippet = null;
        String photoUri = null;
        setDataOrHideIfNone(snippet, statusView);
        if (photoUri != null) {
            ContactPhotoManager.getInstance(context).loadPhoto(
                    statusPhotoView, Uri.parse(photoUri), -1, false,
                    ContactPhotoManager.DEFAULT_BLANK);
            statusPhotoView.setVisibility(View.VISIBLE);
        } else {
            statusPhotoView.setVisibility(View.GONE);
        }
    }

    /** Creates the view that represents a stream item. */
    public static View createStreamItemView(LayoutInflater inflater, Context context,
            View convertView, StreamItemEntry streamItem, View.OnClickListener photoClickListener) {

        // Try to recycle existing views.
        final View container;
        if (convertView != null) {
            container = convertView;
        } else {
            container = inflater.inflate(R.layout.stream_item_container, null, false);
        }

        final ContactPhotoManager contactPhotoManager = ContactPhotoManager.getInstance(context);
        final List<StreamItemPhotoEntry> photos = streamItem.getPhotos();
        final int photoCount = photos.size();

        // Add the text part.
        addStreamItemText(context, streamItem, container);

        // Add images.
        final ViewGroup imageRows = (ViewGroup) container.findViewById(R.id.stream_item_image_rows);

        if (photoCount == 0) {
            // This stream item only has text.
            imageRows.setVisibility(View.GONE);
        } else {
            // This stream item has text and photos.
            imageRows.setVisibility(View.VISIBLE);

            // Number of image rows needed, which is cailing(photoCount / 2)
            final int numImageRows = (photoCount + 1) / 2;

            // Actual image rows.
            final int numOldImageRows = imageRows.getChildCount();

            // Make sure we have enough stream_item_row_images.
            if (numOldImageRows == numImageRows) {
                // Great, we have the just enough number of rows...

            } else if (numOldImageRows < numImageRows) {
                // Need to add more image rows.
                for (int i = numOldImageRows; i < numImageRows; i++) {
                    View imageRow = inflater.inflate(R.layout.stream_item_row_images, imageRows,
                            true);
                }
            } else {
                // We have exceeding image rows.  Hide them.
                for (int i = numImageRows; i < numOldImageRows; i++) {
                    imageRows.getChildAt(i).setVisibility(View.GONE);
                }
            }

            // Put images, two by two.
            for (int i = 0; i < photoCount; i += 2) {
                final View imageRow = imageRows.getChildAt(i / 2);
                // Reused image rows may not visible, so make sure they're shown.
                imageRow.setVisibility(View.VISIBLE);

                // Show first image.
                loadPhoto(contactPhotoManager, streamItem, photos.get(i), imageRow,
                        R.id.stream_item_first_image, photoClickListener);
                final View secondContainer = imageRow.findViewById(R.id.second_image_container);
                if (i + 1 < photoCount) {
                    // Show the second image too.
                    loadPhoto(contactPhotoManager, streamItem, photos.get(i + 1), imageRow,
                            R.id.stream_item_second_image, photoClickListener);
                    secondContainer.setVisibility(View.VISIBLE);
                } else {
                    // Hide the second image, but it still has to occupy the space.
                    secondContainer.setVisibility(View.INVISIBLE);
                }
            }
        }

        return container;
    }

    /** Loads a photo into an image view. The image view is identified by the given id. */
    private static void loadPhoto(ContactPhotoManager contactPhotoManager,
            final StreamItemEntry streamItem, final StreamItemPhotoEntry streamItemPhoto,
            View photoContainer, int imageViewId, View.OnClickListener photoClickListener) {
        final View frame = photoContainer.findViewById(imageViewId);
        final View pushLayerView = frame.findViewById(R.id.push_layer);
        final ImageView imageView = (ImageView) frame.findViewById(R.id.image);
        if (photoClickListener != null) {
            pushLayerView.setOnClickListener(photoClickListener);
            pushLayerView.setTag(new StreamPhotoTag(streamItem, streamItemPhoto));
            pushLayerView.setFocusable(true);
            pushLayerView.setEnabled(true);
        } else {
            pushLayerView.setOnClickListener(null);
            pushLayerView.setTag(null);
            pushLayerView.setFocusable(false);
            // setOnClickListener makes it clickable, so we need to overwrite it
            pushLayerView.setClickable(false);
            pushLayerView.setEnabled(false);
        }
        contactPhotoManager.loadPhoto(imageView, Uri.parse(streamItemPhoto.getPhotoUri()), -1,
                false, ContactPhotoManager.DEFAULT_BLANK);
    }

    @VisibleForTesting
    static View addStreamItemText(Context context, StreamItemEntry streamItem, View rootView) {
        TextView htmlView = (TextView) rootView.findViewById(R.id.stream_item_html);
        TextView attributionView = (TextView) rootView.findViewById(
                R.id.stream_item_attribution);
        TextView commentsView = (TextView) rootView.findViewById(R.id.stream_item_comments);
        ImageGetter imageGetter = new DefaultImageGetter(context.getPackageManager());

        // Stream item text
        setDataOrHideIfNone(streamItem.getDecodedText(), htmlView);
        // Attribution
        setDataOrHideIfNone(ContactBadgeUtil.getSocialDate(streamItem, context),
                attributionView);
        // Comments
        setDataOrHideIfNone(streamItem.getDecodedComments(), commentsView);
        return rootView;
    }

    /**
     * Sets the display name of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setDisplayName(Context context, Contact contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getDisplayName(context, contactData), textView);
    }

    /**
     * Sets the company and job title of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setCompanyName(Context context, Contact contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getCompany(context, contactData), textView);
    }

    /**
     * Sets the phonetic name of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setPhoneticName(Context context, Contact contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getPhoneticName(context, contactData), textView);
    }

    /**
     * Sets the attribution contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setAttribution(Context context, Contact contactData, TextView textView) {
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

    private static Html.ImageGetter sImageGetter;

    public static Html.ImageGetter getImageGetter(Context context) {
        if (sImageGetter == null) {
            sImageGetter = new DefaultImageGetter(context.getPackageManager());
        }
        return sImageGetter;
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
            view.setBackgroundColor((int) (MoreMath.clamp(alpha, 0.0f, 1.0f) * 255) << 24);
        }
    }

    /**
     * Returns the top coordinate of the first item in the {@link ListView}. If the first item
     * in the {@link ListView} is not visible or there are no children in the list, then return
     * Integer.MIN_VALUE. Note that the returned value will be <= 0 because the first item in the
     * list cannot have a positive offset.
     */
    public static int getFirstListItemOffset(ListView listView) {
        if (listView == null || listView.getChildCount() == 0 ||
                listView.getFirstVisiblePosition() != 0) {
            return Integer.MIN_VALUE;
        }
        return listView.getChildAt(0).getTop();
    }

    /**
     * Tries to scroll the first item in the list to the given offset (this can be a no-op if the
     * list is already in the correct position).
     * @param listView that should be scrolled
     * @param offset which should be <= 0
     */
    public static void requestToMoveToOffset(ListView listView, int offset) {
        // We try to offset the list if the first item in the list is showing (which is presumed
        // to have a larger height than the desired offset). If the first item in the list is not
        // visible, then we simply do not scroll the list at all (since it can get complicated to
        // compute how many items in the list will equal the given offset). Potentially
        // some animation elsewhere will make the transition smoother for the user to compensate
        // for this simplification.
        if (listView == null || listView.getChildCount() == 0 ||
                listView.getFirstVisiblePosition() != 0 || offset > 0) {
            return;
        }

        // As an optimization, check if the first item is already at the given offset.
        if (listView.getChildAt(0).getTop() == offset) {
            return;
        }

        listView.setSelectionFromTop(0, offset);
    }
}
