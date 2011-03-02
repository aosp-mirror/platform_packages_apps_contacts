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

package com.android.contacts.model;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import java.io.IOException;
import java.util.List;

/**
 * A general contacts account type descriptor.
 */
public class ExternalAccountType extends BaseAccountType {
    private static final String TAG = "ExternalAccountType";

    private static final String ACTION_SYNC_ADAPTER = "android.content.SyncAdapter";
    private static final String METADATA_CONTACTS = "android.provider.CONTACTS_STRUCTURE";

    private static final String TAG_CONTACTS_SOURCE_LEGACY = "ContactsSource";
    private static final String TAG_CONTACTS_ACCOUNT_TYPE = "ContactsAccountType";
    private static final String TAG_CONTACTS_DATA_KIND = "ContactsDataKind";

    private static final String ATTR_EDIT_CONTACT_ACTIVITY = "editContactActivity";
    private static final String ATTR_CREATE_CONTACT_ACTIVITY = "createContactActivity";

    private String mEditContactActivityClassName;
    private String mCreateContactActivityClassName;

    public ExternalAccountType(Context context, String resPackageName) {
        this.resPackageName = resPackageName;
        this.summaryResPackageName = resPackageName;

        // Handle unknown sources by searching their package
        final PackageManager pm = context.getPackageManager();
        final Intent syncAdapter = new Intent(ACTION_SYNC_ADAPTER);
        final List<ResolveInfo> matches = pm.queryIntentServices(syncAdapter,
                PackageManager.GET_META_DATA);
        for (ResolveInfo info : matches) {
            ServiceInfo serviceInfo = info.serviceInfo;
            if (serviceInfo.packageName.equals(resPackageName)) {
                final XmlResourceParser parser = serviceInfo.loadXmlMetaData(pm,
                        METADATA_CONTACTS);
                if (parser == null) continue;
                inflate(context, parser);
            }
        }

        // Bring in name and photo from fallback source, which are non-optional
        addDataKindStructuredName(context);
        addDataKindDisplayName(context);
        addDataKindPhoneticName(context);
        addDataKindPhoto(context);
    }

    @Override
    public boolean isExternal() {
        return true;
    }

    @Override
    public String getEditContactActivityClassName() {
        return mEditContactActivityClassName;
    }

    @Override
    public String getCreateContactActivityClassName() {
        return mCreateContactActivityClassName;
    }

    /**
     * Inflate this {@link AccountType} from the given parser. This may only
     * load details matching the publicly-defined schema.
     */
    protected void inflate(Context context, XmlPullParser parser) {
        final AttributeSet attrs = Xml.asAttributeSet(parser);

        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // Drain comments and whitespace
            }

            if (type != XmlPullParser.START_TAG) {
                throw new IllegalStateException("No start tag found");
            }

            String rootTag = parser.getName();
            if (!TAG_CONTACTS_ACCOUNT_TYPE.equals(rootTag) &&
                    !TAG_CONTACTS_SOURCE_LEGACY.equals(rootTag)) {
                throw new IllegalStateException("Top level element must be "
                        + TAG_CONTACTS_ACCOUNT_TYPE + ", not " + rootTag);
            }

            int attributeCount = parser.getAttributeCount();
            for (int i = 0; i < attributeCount; i++) {
                String attr = parser.getAttributeName(i);
                if (ATTR_EDIT_CONTACT_ACTIVITY.equals(attr)) {
                    mEditContactActivityClassName = parser.getAttributeValue(i);
                } else if (ATTR_CREATE_CONTACT_ACTIVITY.equals(attr)) {
                    mCreateContactActivityClassName = parser.getAttributeValue(i);
                } else {
                    Log.e(TAG, "Unsupported attribute " + attr);
                }
            }

            // Parse all children kinds
            final int depth = parser.getDepth();
            while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                    && type != XmlPullParser.END_DOCUMENT) {
                String tag = parser.getName();
                if (type == XmlPullParser.END_TAG || !TAG_CONTACTS_DATA_KIND.equals(tag)) {
                    continue;
                }

                final TypedArray a = context.obtainStyledAttributes(attrs,
                        android.R.styleable.ContactsDataKind);
                final DataKind kind = new DataKind();

                kind.mimeType = a
                        .getString(com.android.internal.R.styleable.ContactsDataKind_mimeType);
                kind.iconRes = a.getResourceId(
                        com.android.internal.R.styleable.ContactsDataKind_icon, -1);

                final String summaryColumn = a
                        .getString(com.android.internal.R.styleable.ContactsDataKind_summaryColumn);
                if (summaryColumn != null) {
                    // Inflate a specific column as summary when requested
                    kind.actionHeader = new FallbackAccountType.SimpleInflater(summaryColumn);
                }

                final String detailColumn = a
                        .getString(com.android.internal.R.styleable.ContactsDataKind_detailColumn);
                final boolean detailSocialSummary = a.getBoolean(
                        com.android.internal.R.styleable.ContactsDataKind_detailSocialSummary,
                        false);

                if (detailSocialSummary) {
                    // Inflate social summary when requested
                    kind.actionBodySocial = true;
                }

                if (detailColumn != null) {
                    // Inflate specific column as summary
                    kind.actionBody = new FallbackAccountType.SimpleInflater(detailColumn);
                }

                addKind(kind);
            }
        } catch (XmlPullParserException e) {
            throw new IllegalStateException("Problem reading XML", e);
        } catch (IOException e) {
            throw new IllegalStateException("Problem reading XML", e);
        }
    }

    @Override
    public int getHeaderColor(Context context) {
        return 0xff6d86b4;
    }

    @Override
    public int getSideBarColor(Context context) {
        return 0xff6d86b4;
    }
}
