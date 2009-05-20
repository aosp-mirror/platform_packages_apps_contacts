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

package com.android.contacts;

import com.android.contacts.EdgeTriggerView.EdgeTriggerListener;
import com.android.contacts.SocialStreamActivity.MappingCache.Mapping;
import com.android.providers.contacts2.ContactsContract;
import com.android.providers.contacts2.ContactsContract.Aggregates;
import com.android.providers.contacts2.ContactsContract.Contacts;
import com.android.providers.contacts2.ContactsContract.Data;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Photo;
import com.android.providers.contacts2.SocialContract.Activities;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class SocialStreamActivity extends ListActivity implements EdgeTriggerListener {
    private static final String TAG = "SocialStreamActivity";

    private static final String[] PROJ_ACTIVITIES = new String[] {
        Activities._ID,
        Activities.PACKAGE,
        Activities.MIMETYPE,
        Activities.AUTHOR_CONTACT_ID,
        Contacts.AGGREGATE_ID,
        Aggregates.DISPLAY_NAME,
        Activities.PUBLISHED,
        Activities.TITLE,
        Activities.SUMMARY,
        Activities.THREAD_PUBLISHED,
    };

    private static final int COL_ID = 0;
    private static final int COL_PACKAGE = 1;
    private static final int COL_MIMETYPE = 2;
    private static final int COL_AUTHOR_CONTACT_ID = 3;
    private static final int COL_AGGREGATE_ID = 4;
    private static final int COL_DISPLAY_NAME = 5;
    private static final int COL_PUBLISHED = 6;
    private static final int COL_TITLE = 7;
    private static final int COL_SUMMARY = 8;
    private static final int COL_THREAD_PUBLISHED = 9;

    public static final int PHOTO_SIZE = 58;

    private ListAdapter mAdapter;

    private FloatyListView mListView;
    private EdgeTriggerView mEdgeTrigger;
    private FastTrackWindow mFastTrack;

    private ContactsCache mContactsCache;
    private MappingCache mMappingCache;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.social_list);

        mContactsCache = new ContactsCache(this);
        mMappingCache = MappingCache.createAndFill(this);

        Cursor cursor = managedQuery(Activities.CONTENT_URI, PROJ_ACTIVITIES, null, null);
        mAdapter = new SocialAdapter(this, cursor, mContactsCache, mMappingCache);

        setListAdapter(mAdapter);

        mListView = (FloatyListView)findViewById(android.R.id.list);

        // Find and listen for edge triggers
        mEdgeTrigger = (EdgeTriggerView)findViewById(R.id.edge_trigger);
        mEdgeTrigger.setOnEdgeTriggerListener(this);
    }

    /** {@inheritDoc} */
    public void onTrigger(float downX, float downY, int edge) {
        // Find list item user triggered over
        int position = mListView.pointToPosition((int)downX, (int)downY);

        Cursor cursor = (Cursor)mAdapter.getItem(position);
        long aggId = cursor.getLong(COL_AGGREGATE_ID);

        Log.d(TAG, "onTrigger found position=" + position + ", contactId=" + aggId);

        Uri aggUri = ContentUris.withAppendedId(ContactsContract.Aggregates.CONTENT_URI, aggId);

        // Dismiss any existing window first
        if (mFastTrack != null) {
            mFastTrack.dismiss();
        }

        mFastTrack = new FastTrackWindow(this, mListView, aggUri, mMappingCache);
        mListView.setFloatyWindow(mFastTrack, position);

    }

    /**
     * List adapter for social stream data queried from
     * {@link Activities#CONTENT_URI}.
     */
    private static class SocialAdapter extends CursorAdapter {
        private final Context mContext;
        private final LayoutInflater mInflater;
        private final ContactsCache mContactsCache;
        private final MappingCache mMappingCache;
        private final StyleSpan mTextStyleName;

        private static class SocialHolder {
            ImageView photo;
            ImageView sourceIcon;
            TextView content;
            SpannableStringBuilder contentBuilder = new SpannableStringBuilder();
            TextView published;
        }

        public SocialAdapter(Context context, Cursor c, ContactsCache contactsCache,
                MappingCache mappingCache) {
            super(context, c, true);
            mContext = context;
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mContactsCache = contactsCache;
            mMappingCache = mappingCache;
            mTextStyleName = new StyleSpan(android.graphics.Typeface.BOLD);
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            Cursor cursor = (Cursor) getItem(position);
            return isReply(cursor) ? 0 : 1;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            SocialHolder holder = (SocialHolder)view.getTag();

            long contactId = cursor.getLong(COL_AUTHOR_CONTACT_ID);
            String name = cursor.getString(COL_DISPLAY_NAME);
            String title = cursor.getString(COL_TITLE);
            long published = cursor.getLong(COL_PUBLISHED);

            // TODO: trigger async query to find actual name and photo instead
            // of using this lazy caching mechanism
            Bitmap photo = mContactsCache.getPhoto(contactId);
            if (photo != null) {
                holder.photo.setImageBitmap(photo);
            } else {
                holder.photo.setImageResource(R.drawable.ic_contact_list_picture);
            }
            holder.contentBuilder.clear();
            holder.contentBuilder.append(name);
            holder.contentBuilder.append(" ");
            holder.contentBuilder.append(title);
            holder.contentBuilder.setSpan(mTextStyleName, 0, name.length(), 0);
            holder.content.setText(holder.contentBuilder);

            CharSequence relativePublished = DateUtils.getRelativeTimeSpanString(published,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            holder.published.setText(relativePublished);

            if (holder.sourceIcon != null) {
                String packageName = cursor.getString(COL_PACKAGE);
                String mimeType = cursor.getString(COL_MIMETYPE);
                Mapping mapping = mMappingCache.getMapping(packageName, mimeType);
                if (mapping != null && mapping.icon != null) {
                    holder.sourceIcon.setImageBitmap(mapping.icon);
                } else {
                    holder.sourceIcon.setImageDrawable(null);
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mInflater.inflate(
                    isReply(cursor) ? R.layout.social_list_item_reply : R.layout.social_list_item,
                    parent, false);

            SocialHolder holder = new SocialHolder();
            holder.photo = (ImageView) view.findViewById(R.id.photo);
            holder.sourceIcon = (ImageView) view.findViewById(R.id.sourceIcon);
            holder.content = (TextView) view.findViewById(R.id.content);
            holder.published = (TextView) view.findViewById(R.id.published);
            view.setTag(holder);

            return view;
        }

        private boolean isReply(Cursor cursor) {

            /*
             * Comparing the message timestamp to the thread timestamp rather than checking the
             * in_reply_to field.  The rationale for this approach is that in the case when the
             * original message to which the reply was posted is missing, we want to display
             * the message as if it was an original; otherwise it would appear to be a reply
             * to whatever message preceded it in the list.  In the case when the original message
             * of the thread is missing, the two timestamps will be the same.
             */
            long published = cursor.getLong(COL_PUBLISHED);
            long threadPublished = cursor.getLong(COL_THREAD_PUBLISHED);
            return published != threadPublished;
        }
    }

    /**
     * Keep a cache that maps from {@link Contacts#_ID} to {@link Photo#PHOTO}
     * values.
     */
    private static class ContactsCache {
        private static final String TAG = "ContactsCache";

        private static final String[] PROJ_DETAILS = new String[] {
            Data.MIMETYPE,
            Data.CONTACT_ID,
            Photo.PHOTO,
        };

        private static final int COL_MIMETYPE = 0;
        private static final int COL_CONTACT_ID = 1;
        private static final int COL_PHOTO = 2;

        private HashMap<Long, Bitmap> mPhoto = new HashMap<Long, Bitmap>();

        public ContactsCache(Context context) {
            Log.d(TAG, "building ContactsCache...");

            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = resolver.query(Data.CONTENT_URI, PROJ_DETAILS,
                    Data.MIMETYPE + "=?", new String[] { Photo.CONTENT_ITEM_TYPE }, null);

            while (cursor.moveToNext()) {
                long contactId = cursor.getLong(COL_CONTACT_ID);
                String mimeType = cursor.getString(COL_MIMETYPE);
                if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    byte[] photoBlob = cursor.getBlob(COL_PHOTO);
                    Bitmap photo = BitmapFactory.decodeByteArray(photoBlob, 0, photoBlob.length);
                    photo = Utilities.createBitmapThumbnail(photo, context, PHOTO_SIZE);

                    mPhoto.put(contactId, photo);
                }
            }

            cursor.close();
            Log.d(TAG, "done building ContactsCache");
        }

        public Bitmap getPhoto(long contactId) {
            return mPhoto.get(contactId);
        }
    }

    /**
     * Store a parsed <code>RemoteViewsMapping</code> object, which maps
     * mime-types to <code>RemoteViews</code> XML resources and possible icons.
     */
    public static class MappingCache {
        private static final String TAG = "MappingCache";

        private static final String TAG_MAPPINGSET = "MappingSet";
        private static final String TAG_MAPPING = "Mapping";

        private static final String MAPPING_METADATA = "com.android.contacts.stylemap";

        private LinkedList<Mapping> mappings = new LinkedList<Mapping>();

        private MappingCache() {
        }

        public static class Mapping {
            String packageName;
            String mimeType;
            int remoteViewsRes;
            Bitmap icon;
        }

        public void addMapping(Mapping mapping) {
            mappings.add(mapping);
        }

        /**
         * Find matching <code>RemoteViews</code> XML resource for requested
         * package and mime-type. Returns -1 if no mapping found.
         */
        public Mapping getMapping(String packageName, String mimeType) {
            for (Mapping mapping : mappings) {
                if (mapping.packageName.equals(packageName) && mapping.mimeType.equals(mimeType)) {
                    return mapping;
                }
            }
            return null;
        }

        /**
         * Create a new {@link MappingCache} object and fill by walking across
         * all packages to find those that provide mappings.
         */
        public static MappingCache createAndFill(Context context) {
            Log.d(TAG, "searching for mimetype mappings...");
            final PackageManager pm = context.getPackageManager();
            MappingCache building = new MappingCache();
            List<ApplicationInfo> installed = pm
                    .getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo info : installed) {
                if (info.metaData != null && info.metaData.containsKey(MAPPING_METADATA)) {
                    try {
                        // Found metadata, so clone into their context to
                        // inflate reference
                        Context theirContext = context.createPackageContext(info.packageName, 0);
                        XmlPullParser mappingParser = info.loadXmlMetaData(pm, MAPPING_METADATA);
                        building.inflateMappings(theirContext, info.uid, info.packageName,
                                mappingParser);
                    } catch (NameNotFoundException e) {
                        Log.w(TAG, "Problem creating context for remote package", e);
                    } catch (InflateException e) {
                        Log.w(TAG, "Problem inflating MappingSet from remote package", e);
                    }
                }
            }
            return building;
        }

        public static class InflateException extends Exception {
            public InflateException(String message) {
                super(message);
            }

            public InflateException(String message, Throwable throwable) {
                super(message, throwable);
            }
        }

        /**
         * Inflate a <code>MappingSet</code> from an XML resource, assuming the
         * given package name as the source.
         */
        public void inflateMappings(Context context, int uid, String packageName,
                XmlPullParser parser) throws InflateException {
            final AttributeSet attrs = Xml.asAttributeSet(parser);

            try {
                int type;
                while ((type = parser.next()) != XmlPullParser.START_TAG
                        && type != XmlPullParser.END_DOCUMENT) {
                    // Drain comments and whitespace
                }

                if (type != XmlPullParser.START_TAG) {
                    throw new InflateException("No start tag found");
                }

                if (!TAG_MAPPINGSET.equals(parser.getName())) {
                    throw new InflateException("Top level element must be MappingSet");
                }

                // Parse all children actions
                final int depth = parser.getDepth();
                while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                        && type != XmlPullParser.END_DOCUMENT) {
                    if (type == XmlPullParser.END_TAG) {
                        continue;
                    }

                    if (!TAG_MAPPING.equals(parser.getName())) {
                        throw new InflateException("Expected Mapping tag");
                    }

                    // Parse kind, mime-type, and RemoteViews reference
                    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Mapping);

                    Mapping mapping = new Mapping();
                    mapping.packageName = packageName;
                    mapping.mimeType = a.getString(R.styleable.Mapping_mimeType);
                    mapping.remoteViewsRes = a.getResourceId(R.styleable.Mapping_remoteViews, -1);

                    // Read and resize icon if provided
                    int iconRes = a.getResourceId(R.styleable.Mapping_icon, -1);
                    if (iconRes != -1) {
                        mapping.icon = BitmapFactory
                                .decodeResource(context.getResources(), iconRes);
                        mapping.icon = Utilities.createBitmapThumbnail(mapping.icon, context,
                                FastTrackWindow.ICON_SIZE);
                    }

                    addMapping(mapping);
                    Log.d(TAG, "Added mapping for packageName=" + mapping.packageName
                            + ", mimetype=" + mapping.mimeType);
                }
            } catch (XmlPullParserException e) {
                throw new InflateException("Problem reading XML", e);
            } catch (IOException e) {
                throw new InflateException("Problem reading XML", e);
            }
        }
    }

    /**
     * Borrowed from Launcher for {@link Bitmap} resizing.
     */
    static final class Utilities {
        private static final Paint sPaint = new Paint();
        private static final Rect sBounds = new Rect();
        private static final Rect sOldBounds = new Rect();
        private static Canvas sCanvas = new Canvas();

        static {
            sCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                    Paint.FILTER_BITMAP_FLAG));
        }

        /**
         * Returns a Bitmap representing the thumbnail of the specified Bitmap.
         * The size of the thumbnail is defined by the dimension
         * android.R.dimen.launcher_application_icon_size. This method is not
         * thread-safe and should be invoked on the UI thread only.
         *
         * @param bitmap The bitmap to get a thumbnail of.
         * @param context The application's context.
         * @return A thumbnail for the specified bitmap or the bitmap itself if
         *         the thumbnail could not be created.
         */
        static Bitmap createBitmapThumbnail(Bitmap bitmap, Context context, int size) {
            int width = size;
            int height = size;

            final int bitmapWidth = bitmap.getWidth();
            final int bitmapHeight = bitmap.getHeight();

            if (width > 0 && height > 0 && (width < bitmapWidth || height < bitmapHeight)) {
                final float ratio = (float)bitmapWidth / bitmapHeight;

                if (bitmapWidth > bitmapHeight) {
                    height = (int)(width / ratio);
                } else if (bitmapHeight > bitmapWidth) {
                    width = (int)(height * ratio);
                }

                final Bitmap.Config c = (width == size && height == size) ? bitmap.getConfig()
                        : Bitmap.Config.ARGB_8888;
                final Bitmap thumb = Bitmap.createBitmap(size, size, c);
                final Canvas canvas = sCanvas;
                final Paint paint = sPaint;
                canvas.setBitmap(thumb);
                paint.setDither(false);
                paint.setFilterBitmap(true);
                sBounds.set((size - width) / 2, (size - height) / 2, width, height);
                sOldBounds.set(0, 0, bitmapWidth, bitmapHeight);
                canvas.drawBitmap(bitmap, sOldBounds, sBounds, paint);
                return thumb;
            }

            return bitmap;
        }
    }
}
