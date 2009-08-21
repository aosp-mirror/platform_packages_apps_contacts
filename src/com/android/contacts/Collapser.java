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

import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;

/**
 * Class used for collapsing data items into groups of similar items. The data items that should be
 * collapsible should implement the Collapsible interface. The class also contains a utility
 * function that takes an ArrayList of items and returns a list of the same items collapsed into
 * groups.
 */
public final class Collapser {

    /*
     * This utility class cannot be instantiated.
     */
    private Collapser() {}

    /*
     * Interface implemented by data types that can be collapsed into groups of similar data. This
     * can be used for example to collapse similar contact data items into a single item.
     */
    public interface Collapsible<T> {
        public boolean collapseWith(T t);
        public String getCollapseKey();
    }

    /**
     * Collapses a list of Collapsible items into a list of collapsed items. Items are collapsed
     * if they produce equal collapseKeys {@Link Collapsible#getCollapseKey()}, and are collapsed
     * through the {@Link Collapsible#doCollapseWith(Object)} function implemented by the data item.
     *
     * @param list ArrayList of Objects of type <T extends Collapsible<T>> to be collapsed.
     */
    public static <T extends Collapsible<T>> void collapseList(ArrayList<T> list) {
        HashMap<String, T> collapseMap = new HashMap<String, T>();
        ArrayList<String> collapseKeys = new ArrayList<String>();

        int listSize = list.size();
        for (int j = 0; j < listSize; j++) {
            T entry = list.get(j);
            String collapseKey = entry.getCollapseKey();
            if (!collapseMap.containsKey(collapseKey)) {
                collapseMap.put(collapseKey, entry);
                collapseKeys.add(collapseKey);
            } else {
                collapseMap.get(collapseKey).collapseWith(entry);
            }
        }

        if (collapseKeys.size() < listSize) {
            list.clear();
            Iterator<String> itr = collapseKeys.iterator();
            while (itr.hasNext()) {
                list.add(collapseMap.get(itr.next()));
            }
        }
    }
}
