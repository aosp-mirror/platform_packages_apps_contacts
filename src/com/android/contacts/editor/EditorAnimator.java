/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.editor;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import com.android.contacts.util.SchedulingUtils;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Configures animations for typical use-cases
 */
public class EditorAnimator {
    private static EditorAnimator sInstance = new EditorAnimator();

    public static  EditorAnimator getInstance() {
        return sInstance;
    }

    /** Private constructor for singleton */
    private EditorAnimator() { }

    private AnimatorRunner mRunner = new AnimatorRunner();

    public void removeEditorView(final View victim) {
        mRunner.endOldAnimation();
        final int offset = victim.getHeight();

        final List<View> viewsToMove = getViewsBelowOf(victim);
        final List<Animator> animators = Lists.newArrayList();

        // Fade out
        final ObjectAnimator fadeOutAnimator =
                ObjectAnimator.ofFloat(victim, View.ALPHA, 1.0f, 0.0f);
        fadeOutAnimator.setDuration(200);
        animators.add(fadeOutAnimator);

        // Translations
        translateViews(animators, viewsToMove, 0.0f, -offset, 100, 200);

        mRunner.run(animators, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Clean up: Remove all the translations
                for (int i = 0; i < viewsToMove.size(); i++) {
                    final View view = viewsToMove.get(i);
                    view.setTranslationY(0.0f);
                }
                // Remove our target view (if parent is null, we were run several times by quick
                // fingers. Just ignore)
                final ViewGroup victimParent = (ViewGroup) victim.getParent();
                if (victimParent != null) {
                    victimParent.removeView(victim);
                }
            }
        });
    }

    /**
     * Slides the view into its new height, while simultaneously fading it into view.
     *
     * @param target The target view to perform the animation on.
     * @param previousHeight The previous height of the view before its height was changed.
     * Needed because the view does not store any state information about its previous height.
     */
    public void slideAndFadeIn(final ViewGroup target, final int previousHeight) {
        mRunner.endOldAnimation();
        target.setVisibility(View.VISIBLE);
        target.setAlpha(0.0f);
        target.requestFocus();
        SchedulingUtils.doAfterLayout(target, new Runnable() {
            @Override
            public void run() {
                final int offset = target.getHeight() - previousHeight;
                final List<Animator> animators = Lists.newArrayList();

                // Translations
                final List<View> viewsToMove = getViewsBelowOf(target);

                translateViews(animators, viewsToMove, -offset, 0.0f, 0, 200);

                // Fade in
                final ObjectAnimator fadeInAnimator = ObjectAnimator.ofFloat(
                        target, View.ALPHA, 0.0f, 1.0f);
                fadeInAnimator.setDuration(200);
                fadeInAnimator.setStartDelay(200);
                animators.add(fadeInAnimator);

                mRunner.run(animators);
            }
        });
    }

    public void expandOrganization(final View addOrganizationButton,
            final ViewGroup organizationSectionViewContainer) {
        mRunner.endOldAnimation();
        // Make the new controls visible and do one layout pass (so that we can measure)
        organizationSectionViewContainer.setVisibility(View.VISIBLE);
        organizationSectionViewContainer.setAlpha(0.0f);
        organizationSectionViewContainer.requestFocus();
        SchedulingUtils.doAfterLayout(addOrganizationButton, new Runnable() {
            @Override
            public void run() {
                // How many pixels extra do we need?
                final int offset = organizationSectionViewContainer.getHeight() -
                        addOrganizationButton.getHeight();
                final List<Animator> animators = Lists.newArrayList();

                // Fade out
                final ObjectAnimator fadeOutAnimator = ObjectAnimator.ofFloat(
                        addOrganizationButton, View.ALPHA, 1.0f, 0.0f);
                fadeOutAnimator.setDuration(200);
                animators.add(fadeOutAnimator);

                // Translations
                final List<View> viewsToMove = getViewsBelowOf(organizationSectionViewContainer);
                translateViews(animators, viewsToMove, -offset, 0.0f, 0, 200);
                // Fade in
                final ObjectAnimator fadeInAnimator = ObjectAnimator.ofFloat(
                        organizationSectionViewContainer, View.ALPHA, 0.0f, 1.0f);
                fadeInAnimator.setDuration(200);
                fadeInAnimator.setStartDelay(200);
                animators.add(fadeInAnimator);

                mRunner.run(animators);
            }
        });
    }

    public void showAddFieldFooter(final View view) {
        mRunner.endOldAnimation();
        if (view.getVisibility() == View.VISIBLE) return;
        // Make the new controls visible and do one layout pass (so that we can measure)
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0.0f);
        SchedulingUtils.doAfterLayout(view, new Runnable() {
            @Override
            public void run() {
                // How many pixels extra do we need?
                final int offset = view.getHeight();

                final List<Animator> animators = Lists.newArrayList();

                // Translations
                final List<View> viewsToMove = getViewsBelowOf(view);
                translateViews(animators, viewsToMove, -offset, 0.0f, 0, 200);

                // Fade in
                final ObjectAnimator fadeInAnimator = ObjectAnimator.ofFloat(
                        view, View.ALPHA, 0.0f, 1.0f);
                fadeInAnimator.setDuration(200);
                fadeInAnimator.setStartDelay(200);
                animators.add(fadeInAnimator);

                mRunner.run(animators);
            }
        });
    }

    public void hideAddFieldFooter(final View victim) {
        mRunner.endOldAnimation();
        if (victim.getVisibility() == View.GONE) return;
        final int offset = victim.getHeight();

        final List<View> viewsToMove = getViewsBelowOf(victim);
        final List<Animator> animators = Lists.newArrayList();

        // Fade out
        final ObjectAnimator fadeOutAnimator =
                ObjectAnimator.ofFloat(victim, View.ALPHA, 1.0f, 0.0f);
        fadeOutAnimator.setDuration(200);
        animators.add(fadeOutAnimator);

        // Translations
        translateViews(animators, viewsToMove, 0.0f, -offset, 100, 200);

        // Combine
        mRunner.run(animators, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Clean up: Remove all the translations
                for (int i = 0; i < viewsToMove.size(); i++) {
                    final View view = viewsToMove.get(i);
                    view.setTranslationY(0.0f);
                }

                // Restore alpha (for next time), but hide the view for good now
                victim.setAlpha(1.0f);
                victim.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Creates a translation-animation for the given views
     */
    private static void translateViews(List<Animator> animators, List<View> views, float fromY,
            float toY, int startDelay, int duration) {
        for (int i = 0; i < views.size(); i++) {
            final View child = views.get(i);
            final ObjectAnimator translateAnimator =
                    ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, fromY, toY);
            translateAnimator.setStartDelay(startDelay);
            translateAnimator.setDuration(duration);
            animators.add(translateAnimator);
        }
    }

    /**
     * Traverses up the view hierarchy and returns all views physically below this item.
     *
     * @return List of views that are below the given view. Empty list if parent of view is null.
     */
    private static List<View> getViewsBelowOf(View view) {
        final ViewGroup victimParent = (ViewGroup) view.getParent();
        final List<View> result = Lists.newArrayList();
        if (victimParent != null) {
            final int index = victimParent.indexOfChild(view);
            getViewsBelowOfRecursive(result, victimParent, index + 1, view);
        }
        return result;
    }

    private static void getViewsBelowOfRecursive(List<View> result, ViewGroup container,
            int index, View target) {
        for (int i = index; i < container.getChildCount(); i++) {
            View view = container.getChildAt(i);
            // consider the child view below the target view only if it is physically
            // below the view on-screen, using half the height of the target view as the
            // baseline
            if (view.getY() > (target.getY() + target.getHeight() / 2)) {
                result.add(view);
            }
        }

        final ViewParent parent = container.getParent();
        if (parent instanceof LinearLayout) {
            final LinearLayout parentLayout = (LinearLayout) parent;
            int containerIndex = parentLayout.indexOfChild(container);
            getViewsBelowOfRecursive(result, parentLayout, containerIndex + 1, target);
        }
    }

    /**
     * Keeps a reference to the last animator, so that we can end that early if the user
     * quickly pushes buttons. Removes the reference once the animation has finished
     */
    /* package */ static class AnimatorRunner extends AnimatorListenerAdapter {
        private Animator mLastAnimator;

        @Override
        public void onAnimationEnd(Animator animation) {
            mLastAnimator = null;
        }

        public void run(List<Animator> animators) {
            run(animators, null);
        }

        public void run(List<Animator> animators, AnimatorListener listener) {
            final AnimatorSet set = new AnimatorSet();
            set.playTogether(animators);
            if (listener != null) set.addListener(listener);
            set.addListener(this);
            mLastAnimator = set;
            set.start();
        }

        public void endOldAnimation() {
            if (mLastAnimator != null) {
                mLastAnimator.end();
            }
        }
    }
}
