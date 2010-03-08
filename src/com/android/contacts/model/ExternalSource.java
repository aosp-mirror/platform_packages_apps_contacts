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
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;

import java.io.IOException;
import java.util.List;

/*

<!-- example of what SourceConstraints would look like in XML -->
<!-- NOTE: may not directly match the current structure version -->

<DataKind
    mimeType="vnd.android.cursor.item/email"
    title="@string/title_postal"
    icon="@drawable/icon_postal"
    weight="12"
    editable="true">

    <!-- these are defined using string-builder-ish -->
    <ActionHeader></ActionHeader>
    <ActionBody socialSummary="true" />  <!-- can pull together various columns -->

    <!-- ordering handles precedence the "insert/add" case -->
    <!-- assume uniform type when missing "column", use title in place -->
    <EditTypes column="data5" overallMax="-1">
        <EditType rawValue="0" label="@string/type_home" specificMax="-1" />
        <EditType rawValue="1" label="@string/type_work" specificMax="-1" secondary="true" />
        <EditType rawValue="4" label="@string/type_custom" customColumn="data6" specificMax="-1" secondary="true" />
    </EditTypes>

    <!-- when single edit field, simplifies edit case -->
    <EditField column="data1" title="@string/field_family_name" android:inputType="textCapWords|textPhonetic" />
    <EditField column="data2" title="@string/field_given_name" android:minLines="2" />
    <EditField column="data3" title="@string/field_suffix" />

</DataKind>

*/

/**
 * Internal structure that represents constraints and styles for a specific data
 * source, such as the various data types they support, including details on how
 * those types should be rendered and edited.
 * <p>
 * In the future this may be inflated from XML defined by a data source.
 */
public class ExternalSource extends FallbackSource {
    private static final String ACTION_SYNC_ADAPTER = "android.content.SyncAdapter";
    private static final String METADATA_CONTACTS = "android.provider.CONTACTS_STRUCTURE";

    private interface InflateTags {
        final String CONTACTS_SOURCE = "ContactsSource";
        final String CONTACTS_DATA_KIND = "ContactsDataKind";
    }

    public ExternalSource(String resPackageName) {
        this.resPackageName = resPackageName;
        this.summaryResPackageName = resPackageName;
    }

    /**
     * Ensure that the constraint rules behind this {@link ContactsSource} have
     * been inflated. Because this may involve parsing meta-data from
     * {@link PackageManager}, it shouldn't be called from a UI thread.
     */
    @Override
    public void inflate(Context context, int inflateLevel) {
        // Handle unknown sources by searching their package
        final PackageManager pm = context.getPackageManager();
        final Intent syncAdapter = new Intent(ACTION_SYNC_ADAPTER);
        final List<ResolveInfo> matches = pm.queryIntentServices(syncAdapter,
                PackageManager.GET_META_DATA);
        for (ResolveInfo info : matches) {
            final XmlResourceParser parser = info.serviceInfo.loadXmlMetaData(pm,
                    METADATA_CONTACTS);
            if (parser == null) continue;
            inflate(context, parser);
        }

        // Bring in name and photo from fallback source, which are non-optional
        inflateStructuredName(context, inflateLevel);
        inflatePhoto(context, inflateLevel);

        setInflatedLevel(inflateLevel);
    }

    /**
     * Inflate this {@link ContactsSource} from the given parser. This may only
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

            if (!InflateTags.CONTACTS_SOURCE.equals(parser.getName())) {
                throw new IllegalStateException("Top level element must be "
                        + InflateTags.CONTACTS_SOURCE);
            }

            // Parse all children kinds
            final int depth = parser.getDepth();
            while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                    && type != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.END_TAG
                        || !InflateTags.CONTACTS_DATA_KIND.equals(parser.getName())) {
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
                    kind.actionHeader = new FallbackSource.SimpleInflater(summaryColumn);
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
                    kind.actionBody = new FallbackSource.SimpleInflater(detailColumn);
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
