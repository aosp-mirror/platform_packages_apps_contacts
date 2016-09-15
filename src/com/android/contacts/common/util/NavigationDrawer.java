/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.common.util;

import android.support.design.widget.NavigationView;
import android.support.v7.app.AppCompatActivity;

import com.android.contacts.R;

public interface NavigationDrawer {
    void onStart();
    void onResume();
    void onPause();
    void onStop();
    NavigationView getNavigationView();

    class Default implements NavigationDrawer {

        AppCompatActivity mActivity;

        public Default(AppCompatActivity activity) {
            mActivity = activity;
        }

        @Override
        public void onStart() {}

        @Override
        public void onResume() {}

        @Override
        public void onPause() {}

        @Override
        public void onStop() {}

        @Override
        public NavigationView getNavigationView() {
            return (NavigationView) mActivity.findViewById(R.id.nav_view);
        }
    }
}
