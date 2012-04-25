/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.list;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;

/**
 * A specialized loader for the Join Contacts UI.  It executes two queries:
 * join suggestions and (optionally) the full contact list.
 *
 * This loader also loads the "suggestion" cursor, which can be accessed with:
 * {@code ((JoinContactLoaderResult) result).suggestionCursor }
 */
public class JoinContactLoader extends CursorLoader {

    private String[] mProjection;
    private Uri mSuggestionUri;

    /**
     * Actual returned class.  It's guaranteed that this loader always returns an instance of this
     * class.  This class is needed to tie the lifecycle of the second cursor to that of the
     * primary one.
     *
     * Note we can't change the result type of this loader itself, because CursorLoader
     * extends AsyncTaskLoader<Cursor>, not AsyncTaskLoader<? extends Cursor>
     */
    public static class JoinContactLoaderResult extends CursorWrapper {
        public final Cursor suggestionCursor;

        public JoinContactLoaderResult(Cursor baseCursor, Cursor suggestionCursor) {
            super(baseCursor);
            this.suggestionCursor = suggestionCursor;
        }

        @Override
        public void close() {
            try {
                suggestionCursor.close();
            } finally {
                super.close();
            }
        }
    }

    public JoinContactLoader(Context context) {
        super(context, null, null, null, null, null);
    }

    public void setSuggestionUri(Uri uri) {
        this.mSuggestionUri = uri;
    }

    @Override
    public void setProjection(String[] projection) {
        super.setProjection(projection);
        this.mProjection = projection;
    }

    @Override
    public Cursor loadInBackground() {
        // First execute the suggestions query, then call super.loadInBackground
        // to load the entire list
        final Cursor suggestionsCursor = getContext().getContentResolver()
                .query(mSuggestionUri, mProjection, null, null, null);
        return new JoinContactLoaderResult(super.loadInBackground(), suggestionsCursor);
    }
}