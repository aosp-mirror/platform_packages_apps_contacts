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

package com.android.contacts.tests.widget;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.contacts.tests.R;
import com.android.contacts.common.list.PinnedHeaderListView;

/**
 * An activity that demonstrates various use cases for the {@link PinnedHeaderListView}.
 */
public class PinnedHeaderUseCaseActivity extends ListActivity {

    private static final int SINGLE_SHORT_SECTION_NO_HEADERS = 0;
    private static final int TWO_SHORT_SECTIONS_WITH_HEADERS = 1;
    private static final int FIVE_SHORT_SECTIONS_WITH_HEADERS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setListAdapter(new ArrayAdapter<String>(this, R.layout.intent_list_item,
                getResources().getStringArray(R.array.pinnedHeaderUseCases)));
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        switch (position) {
            case SINGLE_SHORT_SECTION_NO_HEADERS:
                startActivity(
                        new int[]{5},
                        new String[]{"Line"},
                        new boolean[]{false},
                        new boolean[]{false},
                        new int[]{0});
                break;
            case TWO_SHORT_SECTIONS_WITH_HEADERS:
                startActivity(
                        new int[]{2, 30},
                        new String[]{"First", "Second"},
                        new boolean[]{true, true},
                        new boolean[]{false, false},
                        new int[]{0, 2000});
                break;
            case FIVE_SHORT_SECTIONS_WITH_HEADERS:
                startActivity(
                        new int[]{1, 5, 5, 5, 5},
                        new String[]{"First", "Second", "Third", "Fourth", "Fifth"},
                        new boolean[]{true, true, true, true, true},
                        new boolean[]{false, false, false, false, false},
                        new int[]{0, 2000, 3000, 4000, 5000});
                break;
        }
    }

    private void startActivity(int[] counts, String[] names, boolean[] headers,
            boolean[] showIfEmpty, int[] delays) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.android.contacts",
                "com.android.contacts.widget.PinnedHeaderListDemoActivity"));
        intent.putExtra("counts", counts);
        intent.putExtra("names", names);
        intent.putExtra("headers", headers);
        intent.putExtra("showIfEmpty", showIfEmpty);
        intent.putExtra("delays", delays);

        startActivity(intent);
    }
}
