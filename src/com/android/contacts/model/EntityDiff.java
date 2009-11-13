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

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Entity;
import android.content.ContentProviderOperation.Builder;
import android.content.Entity.NamedContentValues;
import android.net.Uri;
import android.provider.BaseColumns;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * Describes a set of {@link ContentProviderOperation} that need to be
 * executed to transform a database from one {@link Entity} to another.
 */
@Deprecated
public class EntityDiff extends ArrayList<ContentProviderOperation> {
    private EntityDiff() {
    }

    /**
     * Build the set of {@link ContentProviderOperation} needed to translate
     * from "before" to "after". Tries its best to keep operations to
     * minimal number required. Assumes that all {@link ContentValues} are
     * keyed using {@link BaseColumns#_ID} values.
     */
    public static EntityDiff buildDiff(Entity before, Entity after, Uri targetUri,
            String childForeignKey) {
        final EntityDiff diff = new EntityDiff();

        Builder builder;
        ContentValues values;

        if (before == null) {
            // Before doesn't exist, so insert "after" values
            builder = ContentProviderOperation.newInsert(targetUri);
            builder.withValues(after.getEntityValues());
            diff.add(builder.build());

            for (NamedContentValues child : after.getSubValues()) {
                // Add builder with reference to original _id when needed
                builder = ContentProviderOperation.newInsert(child.uri);
                builder.withValues(child.values);
                if (childForeignKey != null) {
                    builder.withValueBackReference(childForeignKey, 0);
                }
                diff.add(builder.build());
            }

        } else if (after == null) {
            // After doesn't exist, so delete "before" values
            for (NamedContentValues child : before.getSubValues()) {
                builder = ContentProviderOperation.newDelete(child.uri);
                builder.withSelection(getSelectIdClause(child.values), null);
                diff.add(builder.build());
            }

            builder = ContentProviderOperation.newDelete(targetUri);
            builder.withSelection(getSelectIdClause(before.getEntityValues()), null);
            diff.add(builder.build());

        } else {
            // Somewhere between, so update any changed values
            values = after.getEntityValues();
            if (!before.getEntityValues().equals(values)) {
                // Top-level values changed, so update
                builder = ContentProviderOperation.newUpdate(targetUri);
                builder.withSelection(getSelectIdClause(values), null);
                builder.withValues(values);
                diff.add(builder.build());
            }

            // Build lookup maps for children on both sides
            final HashMap<String, NamedContentValues> beforeChildren = buildChildrenMap(before);
            final HashMap<String, NamedContentValues> afterChildren = buildChildrenMap(after);

            // Walk through "before" children looking for deletes and updates
            for (NamedContentValues beforeChild : beforeChildren.values()) {
                final String key = buildChildKey(beforeChild);
                final NamedContentValues afterChild = afterChildren.get(key);

                if (afterChild == null) {
                    // After child doesn't exist, so delete "before" child
                    builder = ContentProviderOperation.newDelete(beforeChild.uri);
                    builder.withSelection(getSelectIdClause(beforeChild.values), null);
                    diff.add(builder.build());
                } else if (!beforeChild.values.equals(afterChild.values)) {
                    // After child still exists, and is different, so update
                    values = afterChild.values;
                    builder = ContentProviderOperation.newUpdate(afterChild.uri);
                    builder.withSelection(getSelectIdClause(values), null);
                    builder.withValues(values);
                    diff.add(builder.build());
                }

                // Remove the now-handled "after" child
                afterChildren.remove(key);
            }

            // Walk through remaining "after" children, which are inserts
            for (NamedContentValues afterChild : afterChildren.values()) {
                builder = ContentProviderOperation.newInsert(afterChild.uri);
                builder.withValues(afterChild.values);
                diff.add(builder.build());
            }
        }

        return diff;
    }

    private static String buildChildKey(NamedContentValues child) {
        return child.uri.toString() + child.values.getAsString(BaseColumns._ID);
    }

    private static String getSelectIdClause(ContentValues values) {
        return BaseColumns._ID + "=" + values.getAsLong(BaseColumns._ID);
    }

    private static HashMap<String, NamedContentValues> buildChildrenMap(Entity entity) {
        final ArrayList<NamedContentValues> children = entity.getSubValues();
        final HashMap<String, NamedContentValues> childrenMap = new HashMap<String, NamedContentValues>(
                children.size());
        for (NamedContentValues child : children) {
            final String key = buildChildKey(child);
            childrenMap.put(key, child);
        }
        return childrenMap;
    }
}
