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

package com.android.contacts.detail;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * Custom {@link LinearLayout} which remembers its position in the {@link ListView}. Should be
 * used for action touch targets in {@link ContactDetailFragment}.
 */
/* package */ class ActionsViewContainer extends LinearLayout {

    private ContextMenuInfo mContextMenuInfo;

    public ActionsViewContainer(Context context) {
        super(context);
    }

    public ActionsViewContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionsViewContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setPosition(int position) {
        mContextMenuInfo = new AdapterView.AdapterContextMenuInfo(this, position, -1);
    }

    @Override
    public ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
    }
}