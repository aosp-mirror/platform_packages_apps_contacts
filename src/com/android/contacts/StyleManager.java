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

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.WeakHashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;


public final class StyleManager extends BroadcastReceiver {

    public static final String TAG = "StyleManager";

    private static StyleManager sInstance = null;

    private WeakHashMap<String, Bitmap> mIconCache;
    private HashMap<String, StyleSet> mStyleSetCache;

    /*package*/ static final String DEFAULT_MIMETYPE = "default-icon";
    private static final String ICON_SET_META_DATA = "com.android.contacts.iconset";
    private static final String TAG_ICON_SET = "icon-set";
    private static final String TAG_ICON = "icon";
    private static final String TAG_ICON_DEFAULT = "icon-default";
    private static final String KEY_JOIN_CHAR = "|";

    private StyleManager(Context context) {
        mIconCache = new WeakHashMap<String, Bitmap>();
        mStyleSetCache = new HashMap<String, StyleSet>();
        registerIntentReceivers(context);
    }

    /**
     * Returns an instance of StyleManager. This method enforces that only a single instance of this
     * class exists at any one time in a process.
     *
     * @param context A context object
     * @return StyleManager object
     */
    public static StyleManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new StyleManager(context);
        }
        return sInstance;
    }

    private void registerIntentReceivers(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");

        // We use getApplicationContext() so that the broadcast reciever can stay registered for
        // the length of the application lifetime (instead of the calling activity's lifetime).
        // This is so that we can notified of package changes, and purge the cache accordingly,
        // but not be woken up if the application process isn't already running, since we will
        // have no cache to clear at that point.
        context.getApplicationContext().registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final String packageName = intent.getData().getSchemeSpecificPart();

        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_ADDED.equals(action)
                || Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
            onPackageChange(packageName);
        }
    }

    public void onPackageChange(String packageName) {
        Iterator<String> itr;

        // Remove cached icons for this package
        for (itr = mIconCache.keySet().iterator(); itr.hasNext(); ) {
            if (itr.next().startsWith(packageName + KEY_JOIN_CHAR)) {
                itr.remove();
            }
        }

        // Remove the cached style set for this package
        mStyleSetCache.remove(packageName);
    }

    /**
     * Get the default icon for a given package. If no icon is specified for that package
     * null is returned.
     *
     * @param packageName
     * @return Bitmap holding the default icon.
     */
    public Bitmap getDefaultIcon(Context context, String packageName) {
        return getMimetypeIcon(context, packageName, DEFAULT_MIMETYPE);
    }

    /**
     * Get the icon associated with a mimetype for a given package. If no icon is specified for that
     * package null is returned.
     *
     * @param packageName
     * @return Bitmap holding the default icon.
     */
    public Bitmap getMimetypeIcon(Context context, String packageName, String mimetype) {
        String key = getKey(packageName, mimetype);

        synchronized(mIconCache) {
            if (!mIconCache.containsKey(key)) {
                // Cache miss

                // loadIcon() may return null, which is fine since, if no icon was found we want to
                // store a null value so we know not to look next time.
                mIconCache.put(key, loadIcon(context, packageName, mimetype));
            }
            return mIconCache.get(key);
        }
    }

    private Bitmap loadIcon(Context context, String packageName, String mimetype) {
        StyleSet ss = null;

        synchronized(mStyleSetCache) {
            if (!mStyleSetCache.containsKey(packageName)) {
                // Cache miss
                try {
                    StyleSet inflated = inflateStyleSet(context, packageName);
                    mStyleSetCache.put(packageName, inflated);
                } catch (InflateException e) {
                    // If inflation failed keep a null entry so we know not to try again.
                    Log.w(TAG, "Inflation failed: " + e);
                    mStyleSetCache.put(packageName, null);
                }
            }
        }

        ss = mStyleSetCache.get(packageName);
        if (ss == null) {
            return null;
        }

        int iconRes;
        if ((iconRes = ss.getIconRes(mimetype)) == -1) {
            return null;
        }

        return BitmapFactory.decodeResource(context.getResources(),
                iconRes, null);
    }

    private StyleSet inflateStyleSet(Context context, String packageName) throws InflateException {
        final PackageManager pm = context.getPackageManager();
        final ApplicationInfo ai;

        try {
            ai = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            return null;
        }

        XmlPullParser parser = ai.loadXmlMetaData(pm, ICON_SET_META_DATA);
        final AttributeSet attrs = Xml.asAttributeSet(parser);

        if (parser == null) {
            return null;
        }

        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // Drain comments and whitespace
            }

            if (type != XmlPullParser.START_TAG) {
                throw new InflateException("No start tag found");
            }

            if (!TAG_ICON_SET.equals(parser.getName())) {
                throw new InflateException("Top level element must be StyleSet");
            }

            // Parse all children actions
            StyleSet styleSet = new StyleSet();
            final int depth = parser.getDepth();
            while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                    && type != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.END_TAG) {
                    continue;
                }

                TypedArray a;

                String mimetype;
                if (TAG_ICON.equals(parser.getName())) {
                    a = context.obtainStyledAttributes(attrs, android.R.styleable.Icon);
                    mimetype = a.getString(com.android.internal.R.styleable.Icon_mimeType);
                    if (mimetype != null) {
                        styleSet.addIcon(mimetype,
                                a.getResourceId(com.android.internal.R.styleable.Icon_icon, -1));
                    }
                } else if (TAG_ICON_DEFAULT.equals(parser.getName())) {
                    a = context.obtainStyledAttributes(attrs, android.R.styleable.IconDefault);
                    styleSet.addIcon(DEFAULT_MIMETYPE,
                            a.getResourceId(
                                    com.android.internal.R.styleable.IconDefault_icon, -1));
                } else {
                    throw new InflateException("Expected " + TAG_ICON + " or "
                            + TAG_ICON_DEFAULT + " tag");
                }
            }
            return styleSet;

        } catch (XmlPullParserException e) {
            throw new InflateException("Problem reading XML", e);
        } catch (IOException e) {
            throw new InflateException("Problem reading XML", e);
        }
    }

    private String getKey(String packageName, String mimetype) {
        return packageName + KEY_JOIN_CHAR + mimetype;
    }

    public static class InflateException extends Exception {
        public InflateException(String message) {
            super(message);
        }

        public InflateException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

    private static class StyleSet {
        private HashMap<String, Integer> mMimetypeIconResMap;

        public StyleSet() {
            mMimetypeIconResMap = new HashMap<String, Integer>();
        }

        public int getIconRes(String mimetype) {
            if (!mMimetypeIconResMap.containsKey(mimetype)) {
                return -1;
            }
            return mMimetypeIconResMap.get(mimetype);
        }

        public void addIcon(String mimetype, int res) {
            if (mimetype == null) {
                return;
            }
            mMimetypeIconResMap.put(mimetype, res);
        }
    }

    //-------------------------------------------//
    //-- Methods strictly for testing purposes --//
    //-------------------------------------------//

    /*package*/ int getIconCacheSize() {
        return mIconCache.size();
    }

    /*package*/ int getStyleSetCacheSize() {
        return mStyleSetCache.size();
    }

    /*package*/ boolean isStyleSetCacheHit(String packageName) {
        return mStyleSetCache.containsKey(packageName);
    }

    /*package*/ boolean isIconCacheHit(String packageName, String mimetype) {
        return mIconCache.containsKey(getKey(packageName, mimetype));
    }

    //-------------------------------------------//
}
