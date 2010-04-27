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

package com.android.contacts.util;

import android.graphics.Rect;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRoot;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation.AnimationListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Automatically configures natural-feeling animations for views. To use this class,
 * two calls are required:
 * <ul>
 * <li>{@link #captureView(View)} takes a snapshot of all the views and their
 * positions</li>
 * <li>{@link #animate()} takes another snapshot, calculates the differences
 * and creates Translate- and FadeAnimations for the changes</li>
 * </ul>
 * To match views, the chain of {@link View#getId()} of each View and all of its parents is
 * compared. It is therefore not necessary to retain object identity between
 * {@link #captureView(View)} and {@link #animate()}.
 * This mechanism works fine for Views that are new or moved. To get a fade out effect for deleted
 * views, one of two approaches have to be done by the consumer:
 * <ul>
 * <li>Instead of actually removing the view, it is only hidden (by setting
 * its visibility to either {@link View#INVISIBLE} or {@link View#GONE})</li>
 * <li>{@link #removeView(View)} is used to remove the View. This will hide the view during
 * the animation and actually remove it from its parent, once the animation is finished.</li>
 * </ul>
 * The typical usage pattern looks like this:
 * <pre>
 * {@code
 *   final ViewGroupAnimator a = ViewGroupAnimator.captureView(view);
 *   // change view here (except for deletions)
 *   a.removeView(someChildViewThatHasToGo);
 *   a.animate();}
 * </pre>
 */
// TODO: If we don't have any FadeOuts, we could save 150ms by starting the other animations sooner
// TODO: Create an interface containing the normal functions so that we can mock this for tests
public class ViewGroupAnimator {
    /* package */ static final String TAG = "ViewAnimator";

    private static final OnPreDrawListener CANCEL_DRAW_LISTENER = new OnPreDrawListener() {
        public boolean onPreDraw() {
            return false;
        }
    };

    private static int MOVE_DURATION_MILLIS_DEFAULT = 250;
    private static int FADE_DURATION_MILLIS_DEFAULT = 300;

    private static int FADE_OUT_OFFSET_MILLIS_DEFAULT = 0;
    private static int MOVE_OFFSET_MILLIS_DEFAULT = 150;
    private static int FADE_IN_OFFSET_MILLIS_DEFAULT = 400;

    private static final Interpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();

    private final View mRootView;
    private final Snapshot mBeforeSnapshot;
    private final HashSet<View> mViewsToRemove = new HashSet<View>();

    private Runnable mOnAnimationsFinished;

    /**
     * Cancels all pending animations by calling {@link Animation#cancel()} for
     * all animations that are currently attached to the provided view or any of its children.
     */
    public static void cancelRunningAnimations(View view) {
        final Animation animation = view.getAnimation();
        if (animation != null) {
            animation.cancel();
            view.setAnimation(null);
        }
        if (view instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup)view;
            for (int index = 0; index < viewGroup.getChildCount(); index++) {
                cancelRunningAnimations(viewGroup.getChildAt(index));
            }
        }
    }

    private ViewGroupAnimator(View rootView) {
        mRootView = rootView;
        mBeforeSnapshot = buildSnapshot(rootView);
        cancelRunningAnimations(rootView);
    }

    /**
     * Analyses the given view and its children, builds a snapshot and returns an animator
     * that can later animate changes. This is the only function to get an instance of this class.
     */
    public static final ViewGroupAnimator captureView(View rootView) {
       return new ViewGroupAnimator(rootView);
    }

    private void setVisibility(Iterable<View> views, int visibility) {
        for (View view : views) view.setVisibility(visibility);
    }

    private void forceInstantRelayout() {
        // This calls a framework internal function to instantly do the layout
        // TODO: Find an officially supported way once the framework supports it

        final ViewRoot vr = (ViewRoot) mRootView.getParent();
        // vr can be null when rapidly chaining animations
        if (vr != null) vr.handleMessage(Message.obtain(null, ViewRoot.DO_TRAVERSAL));
    }

    private void enableRedraw() {
        mRootView.getViewTreeObserver().removeOnPreDrawListener(CANCEL_DRAW_LISTENER);
    }

    private void disableRedraw() {
        mRootView.getViewTreeObserver().addOnPreDrawListener(CANCEL_DRAW_LISTENER);
    }

    /**
     * Sets a function that should be called once all Animations are finished.
     */
    public void setOnAnimationsFinished(Runnable runnable) {
        mOnAnimationsFinished = runnable;
    }

    /**
     * Marks a view for deletion. This view will be set to both {@link View#INVISIBLE} and
     * {@link View#GONE} during measurement and animation and will be removed from its Parent
     * (using {@link ViewGroup#removeView(View)}) once all Animations are finished.
     */
    public void removeView(View view) {
        mViewsToRemove.add(view);
    }

    /**
     * Performs a difference analysis of positions and visibility, configures animations
     * and starts them.
     */
    public void animate() {
        disableRedraw();
        try {
            setVisibility(mViewsToRemove, View.GONE);
            forceInstantRelayout();
            final Snapshot currentSnapshot = buildSnapshot(mRootView);
            final ArrayList<CachedTranslation> translations = new ArrayList<CachedTranslation>();
            final HashSet<View> goneViews = new HashSet<View>();

            AnimationManager animationManager = new AnimationManager();

            for (String idChain : currentSnapshot.keySet()) {
                final ViewInfo afterViewInfo = currentSnapshot.get(idChain);

                if (mViewsToRemove.contains(afterViewInfo.getView())) {
                    // There is special handling for these views below
                    continue;
                }

                final ViewInfo beforeViewInfo = mBeforeSnapshot.get(idChain);

                final boolean isVisible = afterViewInfo.getVisibility() == View.VISIBLE;

                final boolean existedBefore = beforeViewInfo != null;
                final boolean wasVisible = existedBefore &&
                        beforeViewInfo.getVisibility() == View.VISIBLE;

                if (isVisible && !wasVisible) {
                    // this is a new View ==> fade it in
                    animationManager.doFade(afterViewInfo.getView(),
                            AnimationManager.FADE_TYPE_NEW);
                    continue;
                } else if (wasVisible && !isVisible) {
                    if (afterViewInfo.getVisibility() == View.GONE) {
                        goneViews.add(afterViewInfo.getView());
                    } else {
                        animationManager.doFade(afterViewInfo.getView(),
                                AnimationManager.FADE_TYPE_VISIBLE_TO_INVISIBLE);
                    }
                    continue;
                }

                if (isVisible && wasVisible) {
                    // Check if we have to Transform
                    final Rect afterRectangle = afterViewInfo.getRectangle();
                    final Rect beforeRectangle = beforeViewInfo.getRectangle();

                    final int diffX = afterRectangle.left - beforeRectangle.left;
                    final int diffY = afterRectangle.top - beforeRectangle.top;

                    final boolean doTranslate = diffX != 0 || diffY != 0;

                    if (doTranslate) {
                        translations.add(new CachedTranslation(afterViewInfo.getView(),
                                afterRectangle, diffX, diffY));
                        continue;
                    }
                }
            }

            // Set views to Invisible, because we need their space for the layout
            setVisibility(mViewsToRemove, View.INVISIBLE);
            setVisibility(goneViews, View.INVISIBLE);
            forceInstantRelayout();

            for (CachedTranslation translation : translations) {
                final Rect intermediatePosition = translation.getIntermediatePosition();
                final int addX = intermediatePosition.left - translation.getView().getLeft();
                final int addY = intermediatePosition.top - translation.getView().getTop();
                animationManager.doTranslation(translation.getView(),
                        addX - translation.getDiffX(), addX,
                        addY - translation.getDiffY(), addY);
            }

            for (final View view : mViewsToRemove) {
                animationManager.doFade(view, AnimationManager.FADE_TYPE_VISIBLE_TO_REMOVED);
            }

            for (final View view : goneViews) {
                animationManager.doFade(view, AnimationManager.FADE_TYPE_VISIBLE_TO_GONE);
            }
        } finally {
            enableRedraw();
        }
    }

    private static Snapshot buildSnapshot(View rootView) {
        final Snapshot result = new Snapshot();
        buildSnapshotRecursive(rootView, result, "");
        return result;
    }

    private static void buildSnapshotRecursive(View parentView,
            Snapshot targetSnapshot, final String parentIdChain) {
        if (!(parentView instanceof ViewGroup)) return;

        final ViewGroup parentViewGroup = (ViewGroup) parentView;
        for (int index = 0; index < parentViewGroup.getChildCount(); index++) {
            final View view = parentViewGroup.getChildAt(index);
            final int id = view.getId();
            final String idChain;
            if (id != View.NO_ID) {
                idChain = parentIdChain + "/" + id;
            } else {
                idChain = parentIdChain + "/i" + index;
            }

            targetSnapshot.put(idChain, new ViewInfo(view));

            buildSnapshotRecursive(view, targetSnapshot, idChain);
        }
    }

    private final class AnimationManager implements AnimationListener {
        private int mCountCalled = 0;

        private static final int FADE_TYPE_VISIBLE_TO_GONE = 1;
        private static final int FADE_TYPE_VISIBLE_TO_INVISIBLE = 2;
        private static final int FADE_TYPE_VISIBLE_TO_REMOVED = 3;
        private static final int FADE_TYPE_NEW = 4;

        private static final int CLEANUP_NO_ACTION = 0;
        private static final int CLEANUP_CLEAR_ANIMATION = 1;
        private static final int CLEANUP_REMOVE = 2;
        private static final int CLEANUP_SET_TO_GONE = 3;

        private final HashMap<View, AnimationInfo> mAnimations = new HashMap<View, AnimationInfo>();

        public void doTranslation(View view, int fromX, int toX, int fromY, int toY) {
            final TranslateAnimation animation = new TranslateAnimation(
                    fromX,
                    toX,
                    fromY,
                    toY);
            animation.setFillBefore(true);
            animation.setFillAfter(true);
            animation.setFillEnabled(true);
            animation.setDuration(MOVE_DURATION_MILLIS_DEFAULT);
            animation.setStartOffset(MOVE_OFFSET_MILLIS_DEFAULT);
            animation.setInterpolator(INTERPOLATOR);
            animation.setAnimationListener(this);

            view.startAnimation(animation);

            mAnimations.put(view, new AnimationInfo(animation, CLEANUP_CLEAR_ANIMATION));
        }

        public void doFade(View view, int fadeType) {
            final float fromAlpha = fadeType == FADE_TYPE_NEW ? 0.0f : 1.0f;
            final float toAlpha = fadeType == FADE_TYPE_NEW ? 1.0f : 0.0f;
            final AlphaAnimation animation = new AlphaAnimation(fromAlpha, toAlpha);
            animation.setDuration(FADE_DURATION_MILLIS_DEFAULT);
            animation.setInterpolator(INTERPOLATOR);
            animation.setAnimationListener(this);

            final int cleanUpAction;
            final boolean fill;
            switch (fadeType) {
                case FADE_TYPE_NEW:
                    // No clean up necessary
                    cleanUpAction = CLEANUP_NO_ACTION;
                    fill = false;
                    animation.setStartOffset(FADE_IN_OFFSET_MILLIS_DEFAULT);
                    break;
                case FADE_TYPE_VISIBLE_TO_GONE:
                    cleanUpAction = CLEANUP_SET_TO_GONE;
                    fill = true;
                    animation.setStartOffset(FADE_OUT_OFFSET_MILLIS_DEFAULT);
                    break;
                case FADE_TYPE_VISIBLE_TO_INVISIBLE:
                    // No clean up necessary
                    cleanUpAction = CLEANUP_NO_ACTION;
                    fill = false;
                    animation.setStartOffset(FADE_OUT_OFFSET_MILLIS_DEFAULT);
                    break;
                case FADE_TYPE_VISIBLE_TO_REMOVED:
                    cleanUpAction = CLEANUP_REMOVE;
                    fill = true;
                    animation.setStartOffset(FADE_OUT_OFFSET_MILLIS_DEFAULT);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fadeType");
            }
            if (fill) {
                animation.setFillBefore(true);
                animation.setFillAfter(true);
                animation.setFillEnabled(true);
            }
            mAnimations.put(view, new AnimationInfo(animation, cleanUpAction));

            view.startAnimation(animation);
        }

        public void onAnimationEnd(Animation animation) {
            mCountCalled++;
            if (mCountCalled == mAnimations.size()) {
                Log.d(TAG, "Cleaning up animations");

                cleanUp();

                if (mOnAnimationsFinished != null) mOnAnimationsFinished.run();
            }
        }

        private void cleanUp() {
            for (final View view : mAnimations.keySet()) {
                final AnimationInfo animationInfo = mAnimations.get(view);
                switch (animationInfo.getCleanUpAction()) {
                    case CLEANUP_NO_ACTION:
                    case CLEANUP_CLEAR_ANIMATION:
                        if (view.getAnimation() != animationInfo.getAnimation()) continue;
                        view.clearAnimation();
                        break;
                    case CLEANUP_REMOVE:
                        final ViewGroup parentGroup = (ViewGroup) view.getParent();
                        // has this view already been removed before?
                        if (parentGroup != null) parentGroup.removeView(view);
                        break;
                    case CLEANUP_SET_TO_GONE:
                        if (view.getAnimation() != animationInfo.getAnimation()) continue;
                        view.clearAnimation();
                        view.setVisibility(View.GONE);
                        break;
                    default:
                        throw new IllegalStateException("Unknown cleanup type");
                }
            }
        }

        public void onAnimationRepeat(Animation animation) {
        }

        public void onAnimationStart(Animation animation) {
        }
    }

    private final static class AnimationInfo {
        private final Animation mAnimation;
        private final int mCleanUpAction;

        public Animation getAnimation() {
            return mAnimation;
        }
        public int getCleanUpAction() {
            return mCleanUpAction;
        }

        public AnimationInfo(Animation animation, int cleanUpAction) {
            mAnimation = animation;
            mCleanUpAction = cleanUpAction;
        }
    }

    private final static class ViewInfo {
        private final View mView;
        private final Rect mRectangle;
        private final int mVisibility;

        public Rect getRectangle() {
            return mRectangle;
        }
        public View getView() {
            return mView;
        }
        public int getVisibility() {
            return mVisibility;
        }

        public ViewInfo(View view) {
            mView = view;
            mRectangle = new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
            mVisibility = view.getVisibility();
        }
    }

    /**
     * Shortcut to HashMap<String, ViewInfo>
     */
    private final static class Snapshot extends HashMap<String, ViewInfo> {

    }

    private final static class CachedTranslation {
        private final View mView;
        private final Rect mIntermediatePosition;
        private final int mDiffX;
        private final int mDiffY;

        public View getView() {
            return mView;
        }

        public Rect getIntermediatePosition() {
            return mIntermediatePosition;
        }

        public int getDiffX() {
            return mDiffX;
        }

        public int getDiffY() {
            return mDiffY;
        }

        public CachedTranslation(View view, Rect intermediatePosition, int diffX, int diffY) {
            mView = view;
            mIntermediatePosition = intermediatePosition;
            mDiffX = diffX;
            mDiffY = diffY;
        }
    }
}
