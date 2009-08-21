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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import java.util.Arrays;

import com.android.contacts.StyleManager;
import com.android.contacts.tests.R;

/**
 * Tests for the StyleManager class.
 */
@LargeTest
public class StyleManagerTests extends AndroidTestCase {

    public static final String LOG_TAG = "StyleManagerTests";

    private StyleManager mStyleManager;
    private static final String PACKAGE_NAME = "com.android.contacts.tests";
    private static final String PHONE_MIMETYPE = "vnd.android.cursor.item/phone";
    private Context mContext;

    public StyleManagerTests() {
        super();
    }

    @Override
    public void setUp() {
        mContext = getContext();
        mStyleManager = StyleManager.getInstance(mContext);
    }

    public void testGetMimetypeIcon() {
        Bitmap phoneIconFromSm = mStyleManager.getMimetypeIcon(mContext, PACKAGE_NAME, PHONE_MIMETYPE);
        int smHeight = phoneIconFromSm.getHeight();
        int smWidth = phoneIconFromSm.getWidth();

        Bitmap phoneIconFromRes = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.phone_icon, null);
        int resHeight = phoneIconFromRes.getHeight();
        int resWidth = phoneIconFromRes.getWidth();

        int[] smPixels = new int[smWidth*smHeight];
        phoneIconFromSm.getPixels(smPixels, 0, smWidth, 0, 0, smWidth, smHeight);

        int[] resPixels = new int[resWidth*resHeight];
        phoneIconFromRes.getPixels(resPixels, 0, resWidth, 0, 0, resWidth, resHeight);

        assertTrue(Arrays.equals(smPixels, resPixels));
    }

    public void testGetMissingMimetypeIcon() {
        Bitmap postalIconFromSm = mStyleManager.getMimetypeIcon(mContext, PACKAGE_NAME,
                "vnd.android.cursor.item/postal-address");

        assertNull(postalIconFromSm);
    }

    public void testGetDefaultIcon() {
        Bitmap defaultIconFromSm = mStyleManager.getDefaultIcon(mContext, PACKAGE_NAME);

        int smHeight = defaultIconFromSm.getHeight();
        int smWidth = defaultIconFromSm.getWidth();

        Bitmap defaultIconFromRes = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.default_icon, null);
        int resHeight = defaultIconFromRes.getHeight();
        int resWidth = defaultIconFromRes.getWidth();

        int[] smPixels = new int[smWidth*smHeight];
        defaultIconFromSm.getPixels(smPixels, 0, smWidth, 0, 0, smWidth, smHeight);

        int[] resPixels = new int[resWidth*resHeight];
        defaultIconFromRes.getPixels(resPixels, 0, resWidth, 0, 0, resWidth, resHeight);

        assertTrue(Arrays.equals(smPixels, resPixels));
    }

    public void testCaching() {
        // Clear cache
        mStyleManager.onPackageChange(PACKAGE_NAME);
        assertTrue(mStyleManager.getIconCacheSize() == 0);
        assertTrue(mStyleManager.getStyleSetCacheSize() == 0);

        // Getting the icon should add it to the cache.
        mStyleManager.getDefaultIcon(mContext, PACKAGE_NAME);
        assertTrue(mStyleManager.getIconCacheSize() == 1);
        assertTrue(mStyleManager.getStyleSetCacheSize() == 1);
        assertTrue(mStyleManager.isIconCacheHit(PACKAGE_NAME, StyleManager.DEFAULT_MIMETYPE));
        assertFalse(mStyleManager.isIconCacheHit(PACKAGE_NAME, PHONE_MIMETYPE));
        assertTrue(mStyleManager.isStyleSetCacheHit(PACKAGE_NAME));

        mStyleManager.getMimetypeIcon(mContext, PACKAGE_NAME, PHONE_MIMETYPE);
        assertTrue(mStyleManager.getIconCacheSize() == 2);
        assertTrue(mStyleManager.getStyleSetCacheSize() == 1);
        assertTrue(mStyleManager.isIconCacheHit(PACKAGE_NAME, StyleManager.DEFAULT_MIMETYPE));
        assertTrue(mStyleManager.isIconCacheHit(PACKAGE_NAME, PHONE_MIMETYPE));
        assertTrue(mStyleManager.isStyleSetCacheHit(PACKAGE_NAME));

        // Clear cache
        mStyleManager.onPackageChange(PACKAGE_NAME);
        assertTrue(mStyleManager.getIconCacheSize() == 0);
        assertTrue(mStyleManager.getStyleSetCacheSize() == 0);
        assertFalse(mStyleManager.isIconCacheHit(PACKAGE_NAME, StyleManager.DEFAULT_MIMETYPE));
        assertFalse(mStyleManager.isIconCacheHit(PACKAGE_NAME, PHONE_MIMETYPE));
        assertFalse(mStyleManager.isStyleSetCacheHit(PACKAGE_NAME));
    }

}
