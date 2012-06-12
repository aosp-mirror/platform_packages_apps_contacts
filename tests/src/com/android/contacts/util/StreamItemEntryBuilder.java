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

import android.content.Context;

/**
 * Builder for {@link StreamItemEntry}s to make writing tests easier.
 */
public class StreamItemEntryBuilder {
    private long mId;
    private String mText;
    private String mComment;
    private long mTimestamp;
    private String mAccountType;
    private String mAccountName;
    private String mDataSet;
    private String mResPackage;
    private String mIconRes;
    private String mLabelRes;

    public StreamItemEntryBuilder() {}

    public StreamItemEntryBuilder setText(String value) {
        mText = value;
        return this;
    }

    public StreamItemEntryBuilder setComment(String value) {
        mComment = value;
        return this;
    }

    public StreamItemEntryBuilder setAccountType(String value) {
        mAccountType = value;
        return this;
    }

    public StreamItemEntryBuilder setAccountName(String value) {
        mAccountName = value;
        return this;
    }

    public StreamItemEntryBuilder setDataSet(String value) {
        mDataSet = value;
        return this;
    }

    public StreamItemEntry build(Context context) {
        StreamItemEntry ret = StreamItemEntry.createForTest(mId, mText, mComment, mTimestamp,
                mAccountType, mAccountName, mDataSet, mResPackage, mIconRes, mLabelRes);
        ret.decodeHtml(context);
        return ret;
    }
}
