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

import com.android.contacts.test.NeededForTesting;

import android.util.LruCache;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An LRU cache in which all items can be marked as expired at a given time and it is possible to
 * query whether a particular cached value is expired or not.
 * <p>
 * A typical use case for this is caching of values which are expensive to compute but which are
 * still useful when out of date.
 * <p>
 * Consider a cache for contact information:
 * <pre>{@code
 *     private ExpirableCache<String, Contact> mContactCache;}</pre>
 * which stores the contact information for a given phone number.
 * <p>
 * When we need to store contact information for a given phone number, we can look up the info in
 * the cache:
 * <pre>{@code
 *     CachedValue<Contact> cachedContact = mContactCache.getCachedValue(phoneNumber);
 * }</pre>
 * We might also want to fetch the contact information again if the item is expired.
 * <pre>
 *     if (cachedContact.isExpired()) {
 *         fetchContactForNumber(phoneNumber,
 *                 new FetchListener() {
 *                     &#64;Override
 *                     public void onFetched(Contact contact) {
 *                         mContactCache.put(phoneNumber, contact);
 *                     }
 *                 });
 *     }</pre>
 * and insert it back into the cache when the fetch completes.
 * <p>
 * At a certain point we want to expire the content of the cache because we know the content may
 * no longer be up-to-date, for instance, when resuming the activity this is shown into:
 * <pre>
 *     &#64;Override
 *     protected onResume() {
 *         // We were paused for some time, the cached value might no longer be up to date.
 *         mContactCache.expireAll();
 *         super.onResume();
 *     }
 * </pre>
 * The values will be still available from the cache, but they will be expired.
 * <p>
 * If interested only in the value itself, not whether it is expired or not, one should use the
 * {@link #getPossiblyExpired(Object)} method. If interested only in non-expired values, one should
 * use the {@link #get(Object)} method instead.
 * <p>
 * This class wraps around an {@link LruCache} instance: it follows the {@link LruCache} behavior
 * for evicting items when the cache is full. It is possible to supply your own subclass of LruCache
 * by using the {@link #create(LruCache)} method, which can define a custom expiration policy.
 * Since the underlying cache maps keys to cached values it can determine which items are expired
 * and which are not, allowing for an implementation that evicts expired items before non expired
 * ones.
 * <p>
 * This class is thread-safe.
 *
 * @param <K> the type of the keys
 * @param <V> the type of the values
 */
@ThreadSafe
public class ExpirableCache<K, V> {
    /**
     * A cached value stored inside the cache.
     * <p>
     * It provides access to the value stored in the cache but also allows to check whether the
     * value is expired.
     *
     * @param <V> the type of value stored in the cache
     */
    public interface CachedValue<V> {
        /** Returns the value stored in the cache for a given key. */
        public V getValue();

        /**
         * Checks whether the value, while still being present in the cache, is expired.
         *
         * @return true if the value is expired
         */
        public boolean isExpired();
    }

    /**
     * Cached values storing the generation at which they were added.
     */
    @Immutable
    private static class GenerationalCachedValue<V> implements ExpirableCache.CachedValue<V> {
        /** The value stored in the cache. */
        public final V mValue;
        /** The generation at which the value was added to the cache. */
        private final int mGeneration;
        /** The atomic integer storing the current generation of the cache it belongs to. */
        private final AtomicInteger mCacheGeneration;

        /**
         * @param cacheGeneration the atomic integer storing the generation of the cache in which
         *        this value will be stored
         */
        public GenerationalCachedValue(V value, AtomicInteger cacheGeneration) {
            mValue = value;
            mCacheGeneration = cacheGeneration;
            // Snapshot the current generation.
            mGeneration = mCacheGeneration.get();
        }

        @Override
        public V getValue() {
            return mValue;
        }

        @Override
        public boolean isExpired() {
            return mGeneration != mCacheGeneration.get();
        }
    }

    /** The underlying cache used to stored the cached values. */
    private LruCache<K, CachedValue<V>> mCache;

    /**
     * The current generation of items added to the cache.
     * <p>
     * Items in the cache can belong to a previous generation, but in that case they would be
     * expired.
     *
     * @see ExpirableCache.CachedValue#isExpired()
     */
    private final AtomicInteger mGeneration;

    private ExpirableCache(LruCache<K, CachedValue<V>> cache) {
        mCache = cache;
        mGeneration = new AtomicInteger(0);
    }

    /**
     * Returns the cached value for the given key, or null if no value exists.
     * <p>
     * The cached value gives access both to the value associated with the key and whether it is
     * expired or not.
     * <p>
     * If not interested in whether the value is expired, use {@link #getPossiblyExpired(Object)}
     * instead.
     * <p>
     * If only wants values that are not expired, use {@link #get(Object)} instead.
     *
     * @param key the key to look up
     */
    public CachedValue<V> getCachedValue(K key) {
        return mCache.get(key);
    }

    /**
     * Returns the value for the given key, or null if no value exists.
     * <p>
     * When using this method, it is not possible to determine whether the value is expired or not.
     * Use {@link #getCachedValue(Object)} to achieve that instead. However, if using
     * {@link #getCachedValue(Object)} to determine if an item is expired, one should use the item
     * within the {@link CachedValue} and not call {@link #getPossiblyExpired(Object)} to get the
     * value afterwards, since that is not guaranteed to return the same value or that the newly
     * returned value is in the same state.
     *
     * @param key the key to look up
     */
    public V getPossiblyExpired(K key) {
        CachedValue<V> cachedValue = getCachedValue(key);
        return cachedValue == null ? null : cachedValue.getValue();
    }

    /**
     * Returns the value for the given key only if it is not expired, or null if no value exists or
     * is expired.
     * <p>
     * This method will return null if either there is no value associated with this key or if the
     * associated value is expired.
     *
     * @param key the key to look up
     */
    @NeededForTesting
    public V get(K key) {
        CachedValue<V> cachedValue = getCachedValue(key);
        return cachedValue == null || cachedValue.isExpired() ? null : cachedValue.getValue();
    }

    /**
     * Puts an item in the cache.
     * <p>
     * Newly added item will not be expired until {@link #expireAll()} is next called.
     *
     * @param key the key to look up
     * @param value the value to associate with the key
     */
    public void put(K key, V value) {
        mCache.put(key, newCachedValue(value));
    }

    /**
     * Mark all items currently in the cache as expired.
     * <p>
     * Newly added items after this call will be marked as not expired.
     * <p>
     * Expiring the items in the cache does not imply they will be evicted.
     */
    public void expireAll() {
        mGeneration.incrementAndGet();
    }

    /**
     * Creates a new {@link CachedValue} instance to be stored in this cache.
     * <p>
     * Implementation of {@link LruCache#create(K)} can use this method to create a new entry.
     */
    public CachedValue<V> newCachedValue(V value) {
        return new GenerationalCachedValue<V>(value, mGeneration);
    }

    /**
     * Creates a new {@link ExpirableCache} that wraps the given {@link LruCache}.
     * <p>
     * The created cache takes ownership of the cache passed in as an argument.
     *
     * @param <K> the type of the keys
     * @param <V> the type of the values
     * @param cache the cache to store the value in
     * @return the newly created expirable cache
     * @throws IllegalArgumentException if the cache is not empty
     */
    public static <K, V> ExpirableCache<K, V> create(LruCache<K, CachedValue<V>> cache) {
        return new ExpirableCache<K, V>(cache);
    }

    /**
     * Creates a new {@link ExpirableCache} with the given maximum size.
     *
     * @param <K> the type of the keys
     * @param <V> the type of the values
     * @return the newly created expirable cache
     */
    public static <K, V> ExpirableCache<K, V> create(int maxSize) {
        return create(new LruCache<K, CachedValue<V>>(maxSize));
    }
}
