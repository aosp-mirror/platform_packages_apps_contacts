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
 * limitations under the License
 */

package com.android.contacts.util;

/**
 * Builder for {@link StreamItemEntry}s to make writing tests easier.
 */
public class StreamItemEntryBuilder {
    private long mId;
    private String mText;
    private String mComment;
    private long mTimestamp;
    private String mAction;
    private String mActionUri;
    private String mResPackage;
    private int mIconRes;
    private int mLabelRes;

    public StreamItemEntryBuilder() {}

    public StreamItemEntryBuilder setText(String text) {
        mText = text;
        return this;
    }

    public StreamItemEntryBuilder setComment(String comment) {
        mComment = comment;
        return this;
    }

    public StreamItemEntryBuilder setAction(String action) {
        mAction = action;
        return this;
    }

    public StreamItemEntryBuilder setActionUri(String actionUri) {
        mActionUri = actionUri;
        return this;
    }

    public StreamItemEntry build() {
        return new StreamItemEntry(mId, mText, mComment, mTimestamp, mAction, mActionUri,
                mResPackage, mIconRes, mLabelRes);
    }
}