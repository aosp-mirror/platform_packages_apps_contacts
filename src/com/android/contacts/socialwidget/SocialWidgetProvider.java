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

package com.android.contacts.socialwidget;

import com.android.contacts.ContactLoader;
import com.android.contacts.R;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.util.ContactBadgeUtil;
import com.android.contacts.util.HtmlUtils;
import com.android.contacts.util.StreamItemEntry;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.StreamItems;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

public class SocialWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "SocialWidgetProvider";

    /**
     * Max length of a snippet that is considered "short" and displayed in
     * a separate line.
     */
    private static final int SHORT_SNIPPET_LENGTH = 48;

    private static SparseArray<ContactLoader> sLoaders = new SparseArray<ContactLoader>();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Log.d(TAG, "onUpdate called for " + appWidgetId);
        }

        for (int appWidgetId : appWidgetIds) {
            loadWidgetData(context, appWidgetManager, appWidgetId, false);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            ContactLoader loader = sLoaders.get(appWidgetId);
            if (loader != null) {
                Log.d(TAG, "Stopping loader for widget with id=" + appWidgetId);
                loader.stopLoading();
                sLoaders.delete(appWidgetId);
            }
        }
        SocialWidgetSettings.getInstance().remove(context, appWidgetIds);
    }

    public static void loadWidgetData(final Context context,
            final AppWidgetManager appWidgetManager, final int widgetId, boolean forceLoad) {
        ContactLoader previousLoader = sLoaders.get(widgetId);

        if (previousLoader != null && !forceLoad) {
            previousLoader.startLoading();
            return;
        }

        if (previousLoader != null) {
            previousLoader.reset();
        }

        // Show that we are loading
        final RemoteViews loadingViews =
                new RemoteViews(context.getPackageName(), R.layout.social_widget);
        loadingViews.setTextViewText(R.id.name,
                context.getString(R.string.social_widget_loading));
        loadingViews.setViewVisibility(R.id.name, View.VISIBLE);
        loadingViews.setViewVisibility(R.id.name_and_snippet, View.GONE);
        appWidgetManager.updateAppWidget(widgetId, loadingViews);

        // Load
        final Uri contactUri =
                SocialWidgetSettings.getInstance().getContactUri(context, widgetId);
        if (contactUri == null) {
            // Not yet set-up (this can happen while the Configuration activity is visible)
            return;
        }
        final ContactLoader contactLoader = new ContactLoader(context, contactUri, false, true,
                false);
        contactLoader.registerListener(0,
                new ContactLoader.OnLoadCompleteListener<ContactLoader.Result>() {
                    @Override
                    public void onLoadComplete(Loader<ContactLoader.Result> loader,
                            ContactLoader.Result contactData) {
                        bindRemoteViews(context, widgetId, appWidgetManager, contactData);
                    }
                });
        contactLoader.startLoading();
        sLoaders.append(widgetId, contactLoader);
    }

    private static void bindRemoteViews(final Context context, final int widgetId,
            final AppWidgetManager widgetManager, ContactLoader.Result contactData) {
        Log.d(TAG, "Loaded " + contactData.getLookupKey()
                + " for widget with id=" + widgetId);
        final RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.social_widget);

        if (!contactData.isLoaded()) {
            setDisplayNameAndSnippet(context, views,
                    context.getString(R.string.invalidContactMessage), null, null, null);
            setPhoto(views, ContactBadgeUtil.loadDefaultAvatarPhoto(context, false, false));
        } else {
            byte[] photo = contactData.getPhotoBinaryData();
            setPhoto(views, photo != null
                    ? BitmapFactory.decodeByteArray(photo, 0, photo.length)
                            : ContactBadgeUtil.loadDefaultAvatarPhoto(context, false, false));

            // TODO: Rotate between all the stream items?

            // OnClick launch QuickContact
            final Intent intent = new Intent(QuickContact.ACTION_QUICK_CONTACT);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            intent.setData(contactData.getLookupUri());
            intent.putExtra(QuickContact.EXTRA_MODE, QuickContact.MODE_SMALL);

            final PendingIntent pendingIntent = PendingIntent.getActivity(context,
                    0, intent, 0);
            views.setOnClickPendingIntent(R.id.border, pendingIntent);

            setDisplayNameAndSnippet(context, views, contactData.getDisplayName(),
                    contactData.getPhoneticName(), contactData.getStreamItems(), pendingIntent);
        }

        // Configure UI
        widgetManager.updateAppWidget(widgetId, views);
    }


    private static void setPhoto(RemoteViews views, Bitmap photo) {
        views.setImageViewBitmap(R.id.image, photo);
    }

    /**
     * Set the display name, phonetic name and the social snippet.
     */
    private static void setDisplayNameAndSnippet(Context context, RemoteViews views,
            CharSequence displayName, CharSequence phoneticName,
            List<StreamItemEntry> streamItems, PendingIntent defaultIntent) {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        CharSequence name = displayName;
        // If there is no display name, use the default missing name string
        if (TextUtils.isEmpty(name)) {
            name = context.getString(R.string.missing_name);
        }
        if (!TextUtils.isEmpty(phoneticName)) {
            name = context.getString(R.string.widget_name_and_phonetic,
                    name, phoneticName);
        }
        sb.append(name);

        AbsoluteSizeSpan sizeSpan = new AbsoluteSizeSpan(
                context.getResources().getDimensionPixelSize(R.dimen.widget_text_size_name));
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
        sb.setSpan(sizeSpan, 0, name.length(), 0);
        sb.setSpan(styleSpan, 0, name.length(), 0);

        if (streamItems == null || streamItems.isEmpty()) {
            views.setTextViewText(R.id.name, sb);
            views.setViewVisibility(R.id.name, View.VISIBLE);
            views.setViewVisibility(R.id.name_and_snippet, View.GONE);
            // Don't set a pending intent if the intent is null, otherwise the system will try
            // to write the null intent to a Parcel.
            if (defaultIntent != null) {
                views.setOnClickPendingIntent(R.id.widget_container, defaultIntent);
            }
        } else {
            // TODO: Rotate between all the stream items?
            StreamItemEntry streamItem = streamItems.get(0);
            CharSequence status = HtmlUtils.fromHtml(context, streamItem.getText());
            if (status == null) {
              status = "";
            }
            if (status.length() <= SHORT_SNIPPET_LENGTH) {
                sb.append("\n");
            } else {
                sb.append("  ");
            }
            sb.append(status);
            views.setTextViewText(R.id.name_and_snippet, sb);
            views.setViewVisibility(R.id.name, View.GONE);
            views.setViewVisibility(R.id.name_and_snippet, View.VISIBLE);
            final AccountTypeManager manager = AccountTypeManager.getInstance(context);
            final AccountType accountType =
                    manager.getAccountType(streamItem.getAccountType(), streamItem.getDataSet());
            if (accountType.getViewStreamItemActivity() != null) {
                final Uri uri = ContentUris.withAppendedId(StreamItems.CONTENT_URI,
                        streamItem.getId());
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setClassName(accountType.resPackageName,
                        accountType.getViewStreamItemActivity());
                views.setOnClickPendingIntent(R.id.name_and_snippet_container,
                        PendingIntent.getActivity(context, 0, intent, 0));
            }
        }
    }
}
