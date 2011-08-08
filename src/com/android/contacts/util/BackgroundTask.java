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
 * limitations under the License.
 */

package com.android.contacts.util;

/**
 * Simple interface to improve the testability of code using AsyncTasks.
 * <p>
 * Provides a trivial replacement for no-arg versions of AsyncTask clients.  We may extend this
 * to add more functionality as we require.
 * <p>
 * The same memory-visibility guarantees are made here as are made for AsyncTask objects, namely
 * that fields set in {@link #doInBackground()} are visible to {@link #onPostExecute()}.
 */
public interface BackgroundTask {
    public void doInBackground();
    public void onPostExecute();
}
