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

package com.android.contacts.util;

import com.android.contacts.util.ExpirableCache.CachedValue;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.LruCache;

/**
 * Unit tests for {@link ExpirableCache}.
 */
@SmallTest
public class ExpirableCacheTest extends AndroidTestCase {
    /** The object under test. */
    private ExpirableCache<String, Integer> mCache;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LruCache<String, CachedValue<Integer>> lruCache =
            new LruCache<String, ExpirableCache.CachedValue<Integer>>(20);
        mCache = ExpirableCache.create(lruCache);
    }

    @Override
    protected void tearDown() throws Exception {
        mCache = null;
        super.tearDown();
    }

    public void testPut() {
        mCache.put("a", 1);
        mCache.put("b", 2);
        assertEquals(1, mCache.getPossiblyExpired("a").intValue());
        assertEquals(2, mCache.getPossiblyExpired("b").intValue());
        mCache.put("a", 3);
        assertEquals(3, mCache.getPossiblyExpired("a").intValue());
    }

    public void testGet_NotExisting() {
        assertNull(mCache.getPossiblyExpired("a"));
        mCache.put("b", 1);
        assertNull(mCache.getPossiblyExpired("a"));
    }

    public void testGet_Expired() {
        mCache.put("a", 1);
        assertEquals(1, mCache.getPossiblyExpired("a").intValue());
        mCache.expireAll();
        assertEquals(1, mCache.getPossiblyExpired("a").intValue());
    }

    public void testGetNotExpired_NotExisting() {
        assertNull(mCache.get("a"));
        mCache.put("b", 1);
        assertNull(mCache.get("a"));
    }

    public void testGetNotExpired_Expired() {
        mCache.put("a", 1);
        assertEquals(1, mCache.get("a").intValue());
        mCache.expireAll();
        assertNull(mCache.get("a"));
    }

    public void testGetCachedValue_NotExisting() {
        assertNull(mCache.getCachedValue("a"));
        mCache.put("b", 1);
        assertNull(mCache.getCachedValue("a"));
    }

    public void testGetCachedValue_Expired() {
        mCache.put("a", 1);
        assertFalse("Should not be expired", mCache.getCachedValue("a").isExpired());
        mCache.expireAll();
        assertTrue("Should be expired", mCache.getCachedValue("a").isExpired());
    }

    public void testGetChangedValue_PutAfterExpired() {
        mCache.put("a", 1);
        mCache.expireAll();
        mCache.put("a", 1);
        assertFalse("Should not be expired", mCache.getCachedValue("a").isExpired());
    }

    public void testComputingCache() {
        // Creates a cache in which all unknown values default to zero.
        mCache = ExpirableCache.create(
                new LruCache<String, ExpirableCache.CachedValue<Integer>>(10) {
                    @Override
                    protected CachedValue<Integer> create(String key) {
                        return mCache.newCachedValue(0);
                    }
                });

        // The first time we request a new value, we add it to the cache.
        CachedValue<Integer> cachedValue = mCache.getCachedValue("a");
        assertNotNull("Should have been created implicitly", cachedValue);
        assertEquals(0, cachedValue.getValue().intValue());
        assertFalse("Should not be expired", cachedValue.isExpired());

        // If we expire all the values, the implicitly created value will also be marked as expired.
        mCache.expireAll();
        CachedValue<Integer> expiredCachedValue = mCache.getCachedValue("a");
        assertNotNull("Should have been created implicitly", expiredCachedValue);
        assertEquals(0, expiredCachedValue.getValue().intValue());
        assertTrue("Should be expired", expiredCachedValue.isExpired());
    }
}
