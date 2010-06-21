/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.contacts.views.editor;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;

/**
 * Compatibility pseudo-ListView. This just renders everything into a LinearLayout using a ListView
 * adapter. If this turns out to be fast enough, we can keep using this. This view will be removed
 * once the decision has been made
 */
public class MyListView extends LinearLayout {
    public MyListView(Context context) {
        super(context);
    }

    public MyListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOrientation(VERTICAL);
    }

    public void setOnItemClickListener(ContactEditorFragment contactEditorFragment) {
        // Keep compatibility with ListView
    }

    public void setItemsCanFocus(boolean value) {
        // Keep compatibility with ListView
    }

    public void setAdapter(BaseAdapter adapter) {
        removeAllViews();
        for (int i = 0; i < adapter.getCount(); i++) {
            final View childView = adapter.getView(i, null, this);
            addView(childView);
        }
    }
}
