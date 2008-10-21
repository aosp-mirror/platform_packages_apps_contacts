/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.database.Cursor;
import android.database.DataSetObserver;
import android.util.SparseIntArray;

/**
 * This class essentially helps in building an index of section boundaries of a
 * sorted column of a cursor. For instance, if a cursor contains a data set 
 * sorted by first name of a person or the title of a song, this class will 
 * perform a binary search to identify the first row that begins with a 
 * particular letter. The search is case-insensitive. The class caches the index 
 * such that subsequent queries for the same letter will return right away.
 */
public class AlphabetIndexer extends DataSetObserver {

    protected Cursor mDataCursor;
    protected int mColumnIndex;
    protected Object[] mAlphabetArray;
    private SparseIntArray mAlphaMap;
    private java.text.Collator mCollator;

    /**
     * Constructs the indexer.
     * @param cursor the cursor containing the data set
     * @param columnIndex the column number in the cursor that is sorted 
     *        alphabetically
     * @param sections the array of objects that represent the sections. The
     * toString() method of each item is called and the first letter of the 
     * String is used as the letter to search for.
     */
    public AlphabetIndexer(Cursor cursor, int columnIndex, Object[] sections) {
        mDataCursor = cursor;
        mColumnIndex = columnIndex;
        mAlphabetArray = sections;
        mAlphaMap = new SparseIntArray(26 /* Optimize for English */);
        if (cursor != null) {
            cursor.registerDataSetObserver(this);
        }
        // Get a Collator for the current locale for string comparisons.
        mCollator = java.text.Collator.getInstance();
        mCollator.setStrength(java.text.Collator.PRIMARY);
    }

    /**
     * Sets a new cursor as the data set and resets the cache of indices.
     * @param cursor the new cursor to use as the data set
     */
    public void setCursor(Cursor cursor) {
        if (mDataCursor != null) {
            mDataCursor.unregisterDataSetObserver(this);
        }
        mDataCursor = cursor;
        if (cursor != null) {
            mDataCursor.registerDataSetObserver(this);
        }
        mAlphaMap.clear();
    }

    /**
     * Performs a binary search or cache lookup to find the first row that
     * matches a given section's starting letter.
     * @param sectionIndex the section to search for
     * @return the row index of the first occurrence, or the nearest next letter.
     * For instance, if searching for "T" and no "T" is found, then the first
     * row starting with "U" or any higher letter is returned. If there is no
     * data following "T" at all, then the list size is returned.
     */
    public int indexOf(int sectionIndex) {
        final SparseIntArray alphaMap = mAlphaMap;
        final Cursor cursor = mDataCursor;

        if (cursor == null || mAlphabetArray == null) {
            return 0;
        }
        
        // Check bounds
        if (sectionIndex <= 0) {
            return 0;
        }
        if (sectionIndex >= mAlphabetArray.length) {
            sectionIndex = mAlphabetArray.length - 1;
        }

        int savedCursorPos = cursor.getPosition();

        int count = cursor.getCount();
        int start = 0;
        int end = count;
        int pos;

        String letter = mAlphabetArray[sectionIndex].toString();
        letter = letter.toUpperCase();
        int key = letter.charAt(0);
        // Check map
        if (Integer.MIN_VALUE != (pos = alphaMap.get(key, Integer.MIN_VALUE))) {
            // Is it approximate? Using negative value to indicate that it's 
            // an approximation and positive value when it is the accurate
            // position.
            if (pos < 0) {
                pos = -pos;
                end = pos;
            } else {
                // Not approximate, this is the confirmed start of section, return it
                return pos;
            }
        }

        // Do we have the position of the previous section?
        if (sectionIndex > 0) {
            int prevLetter =
                    mAlphabetArray[sectionIndex - 1].toString().charAt(0);
            int prevLetterPos = alphaMap.get(prevLetter, Integer.MIN_VALUE);
            if (prevLetterPos != Integer.MIN_VALUE) {
                start = Math.abs(prevLetterPos);
            }
        }

        // Now that we have a possibly optimized start and end, let's binary search

        pos = (end + start) / 2;

        while (pos < end) {
            // Get letter at pos
            cursor.moveToPosition(pos);
            String curName = cursor.getString(mColumnIndex);
            if (curName == null) {
                if (pos == 0) {
                    break;
                } else {
                    pos--;
                    continue;
                }
            }
            int curLetter = Character.toUpperCase(curName.charAt(0));

            if (curLetter != key) {
                // Enter approximation in hash if a better solution doesn't exist
                int curPos = alphaMap.get(curLetter, Integer.MIN_VALUE);
                if (curPos == Integer.MIN_VALUE || Math.abs(curPos) > pos) {
                    // Negative pos indicates that it is an approximation
                    alphaMap.put(curLetter, -pos);
                }
                if (mCollator.compare(curName, letter) < 0) {
                    start = pos + 1;
                    if (start >= count) {
                        pos = count;
                        break;
                    }
                } else {
                    end = pos;
                }
            } else {
                // They're the same, but that doesn't mean it's the start
                if (start == pos) {
                    // This is it
                    break;
                } else {
                    // Need to go further lower to find the starting row
                    end = pos;
                }
            }
            pos = (start + end) / 2;
        }
        alphaMap.put(key, pos);
        cursor.moveToPosition(savedCursorPos);
        return pos;
    }

    @Override
    public void onChanged() {
        super.onChanged();
        mAlphaMap.clear();
    }

    @Override
    public void onInvalidated() {
        super.onInvalidated();
        mAlphaMap.clear();
    }
}
