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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.ViewPropertyAnimator;

public class AnimUtils {
    public static final int DEFAULT_DURATION = -1;

    public static void crossFadeViews(final View fadeIn, final View fadeOut, int duration) {
        fadeIn(fadeIn, duration);
        fadeOut(fadeOut, duration);
    }

    public static void fadeOut(final View fadeOut, int duration) {
        fadeOut.setAlpha(1);
        final ViewPropertyAnimator animator = fadeOut.animate();
        animator.cancel();
        animator.alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                fadeOut.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                fadeOut.setVisibility(View.GONE);
                fadeOut.setAlpha(0);
            }
        });
        if (duration != DEFAULT_DURATION) {
            animator.setDuration(duration);
        }
        animator.start();
    }

    public static void fadeIn(final View fadeIn, int duration) {
        fadeIn.setAlpha(0);
        final ViewPropertyAnimator animator = fadeIn.animate();
        animator.cancel();
        animator.alpha(1).withLayer().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                fadeIn.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                fadeIn.setAlpha(1);
            }
        });
        if (duration != DEFAULT_DURATION) {
            animator.setDuration(duration);
        }
        animator.start();
    }
}
