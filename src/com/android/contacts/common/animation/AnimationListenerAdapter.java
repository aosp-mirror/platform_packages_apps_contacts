/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.contacts.common.animation;

import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;

/**
 * Provides empty implementations of the methods in {@link AnimationListener}
 * for convenience reasons.
 */
public class AnimationListenerAdapter implements AnimationListener {

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAnimationStart(Animation animation) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAnimationEnd(Animation animation) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAnimationRepeat(Animation animation) {
    }
}
