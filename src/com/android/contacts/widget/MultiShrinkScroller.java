package com.android.contacts.widget;

import com.android.contacts.R;
import com.android.contacts.test.NeededForReflection;
import com.android.contacts.util.SchedulingUtils;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.EdgeEffect;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.ScrollView;

/**
 * A custom {@link ViewGroup} that operates similarly to a {@link ScrollView}, except with multiple
 * subviews. These subviews are scrolled or shrinked one at a time, until each reaches their
 * minimum or maximum value.
 *
 * MultiShrinkScroller is designed for a specific problem. As such, this class is designed to be
 * used with a specific layout file: quickcontact_activity.xml. MultiShrinkScroller expects subviews
 * with specific ID values.
 *
 * MultiShrinkScroller's code is heavily influenced by ScrollView. Nonetheless, several ScrollView
 * features are missing. For example: handling of KEYCODES, OverScroll bounce and saving
 * scroll state in savedInstanceState bundles.
 */
public class MultiShrinkScroller extends LinearLayout {

    /**
     * 1000 pixels per millisecond. Ie, 1 pixel per second.
     */
    private static final int PIXELS_PER_SECOND = 1000;

    private float[] mLastEventPosition = { 0, 0 };
    private VelocityTracker mVelocityTracker;
    private boolean mIsBeingDragged = false;
    private boolean mReceivedDown = false;

    private ScrollView mScrollView;
    private View mScrollViewChild;
    private View mToolbar;
    private ImageView mPhotoView;
    private View mPhotoViewContainer;
    private MultiShrinkScrollerListener mListener;
    private int mHeaderTintColor;
    private int mMaximumHeaderHeight;

    private final Scroller mScroller;
    private final EdgeEffect mEdgeGlowBottom;
    private final int mTouchSlop;
    private final int mMaximumVelocity;
    private final int mMinimumVelocity;
    private final int mIntermediateHeaderHeight;
    private final int mMinimumHeaderHeight;
    private final int mTransparentStartHeight;
    private final float mToolbarElevation;
    private final PorterDuffColorFilter mColorFilter
            = new PorterDuffColorFilter(0, PorterDuff.Mode.SRC_ATOP);

    public interface MultiShrinkScrollerListener {
        void onScrolledOffBottom();

        void onEnterFullscreen();

        void onExitFullscreen();
    }

    private final AnimatorListener mHeaderExpandAnimationListener = new AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {}

        @Override
        public void onAnimationEnd(Animator animation) {
            mPhotoView.setClickable(true);
        }

        @Override
        public void onAnimationCancel(Animator animation) {}

        @Override
        public void onAnimationRepeat(Animator animation) {}
    };

    /**
     * Interpolator from android.support.v4.view.ViewPager. Snappier and more elastic feeling
     * than the default interpolator.
     */
    private static final Interpolator sInterpolator = new Interpolator() {

        /**
         * {@inheritDoc}
         */
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    public MultiShrinkScroller(Context context) {
        this(context, null);
    }

    public MultiShrinkScroller(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MultiShrinkScroller(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        setFocusable(false);
        // Drawing must be enabled in order to support EdgeEffect
        setWillNotDraw(/* willNotDraw = */ false);

        mEdgeGlowBottom = new EdgeEffect(context);
        mScroller = new Scroller(context, sInterpolator);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mIntermediateHeaderHeight = (int) getResources().getDimension(
                R.dimen.quickcontact_starting_header_height);
        mTransparentStartHeight = (int) getResources().getDimension(
                R.dimen.quickcontact_starting_empty_height);
        mHeaderTintColor = mContext.getResources().getColor(
                R.color.actionbar_background_color);
        mToolbarElevation = mContext.getResources().getDimension(
                R.dimen.quick_contact_toolbar_elevation);

        final TypedArray attributeArray = context.obtainStyledAttributes(
                new int[]{android.R.attr.actionBarSize});
        mMinimumHeaderHeight = attributeArray.getDimensionPixelSize(0, 0);
        attributeArray.recycle();
    }

    /**
     * This method must be called inside the Activity's OnCreate.
     */
    public void initialize(MultiShrinkScrollerListener listener) {
        mScrollView = (ScrollView) findViewById(R.id.content_scroller);
        mScrollViewChild = findViewById(R.id.card_container);
        mToolbar = findViewById(R.id.toolbar_parent);
        mPhotoViewContainer = findViewById(R.id.toolbar_parent);
        mListener = listener;

        mPhotoView = (ImageView) findViewById(R.id.photo);
        setHeaderHeight(mIntermediateHeaderHeight);
        mPhotoView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                expandCollapseHeader();
            }
        });

        SchedulingUtils.doOnPreDraw(this, /* drawNextFrame = */ true, new Runnable() {
            @Override
            public void run() {
                // We never want the height of the photo view to exceed its width.
                mMaximumHeaderHeight = mToolbar.getWidth();
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // The only time we want to intercept touch events is when we are being dragged.
        return shouldStartDrag(event);
    }

    private boolean shouldStartDrag(MotionEvent event) {
        if (mIsBeingDragged) {
            mIsBeingDragged = false;
            return false;
        }

        switch (event.getAction()) {
            // If we are in the middle of a fling and there is a down event, we'll steal it and
            // start a drag.
            case MotionEvent.ACTION_DOWN:
                updateLastEventPosition(event);
                if (!mScroller.isFinished()) {
                    startDrag();
                    return true;
                } else {
                    mReceivedDown = true;
                }
                break;

            // Otherwise, we will start a drag if there is enough motion in the direction we are
            // capable of scrolling.
            case MotionEvent.ACTION_MOVE:
                if (motionShouldStartDrag(event)) {
                    updateLastEventPosition(event);
                    startDrag();
                    return true;
                }
                break;
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        if (!mIsBeingDragged) {
            if (shouldStartDrag(event)) {
                return true;
            }

            if (action == MotionEvent.ACTION_UP && mReceivedDown) {
                mReceivedDown = false;
                return performClick();
            }
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                final float delta = updatePositionAndComputeDelta(event);
                scrollTo(0, getScroll() + (int) delta);
                mReceivedDown = false;

                if (mIsBeingDragged) {
                    final int heightScrollViewChild = mScrollViewChild.getHeight();
                    final int pulledToY = mScrollView.getScrollY() + (int) delta;
                    if (pulledToY > heightScrollViewChild - mScrollView.getHeight()
                            && mToolbar.getHeight() == mMinimumHeaderHeight) {
                        // The ScrollView is being pulled upwards while there is no more
                        // content offscreen, and the view port is already fully expanded.
                        mEdgeGlowBottom.onPull(delta / getHeight(), 1 - event.getX() / getWidth());
                    }
                    if (!mEdgeGlowBottom.isFinished()) {
                        postInvalidateOnAnimation();
                    }

                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stopDrag(action == MotionEvent.ACTION_CANCEL);
                mReceivedDown = false;
                break;
        }

        return true;
    }

    public void setHeaderTintColor(int color) {
        mHeaderTintColor = color;
        updatePhotoTintAndDropShadow();
    }

    /**
     * Expand to maximum size or starting size. Disable clicks on the photo until the animation is
     * complete.
     */
    private void expandCollapseHeader() {
        mPhotoView.setClickable(false);
        if (getHeaderHeight() != mMaximumHeaderHeight) {
            final ObjectAnimator animator = ObjectAnimator.ofInt(this, "headerHeight",
                    mMaximumHeaderHeight);
            animator.addListener(mHeaderExpandAnimationListener);
            animator.start();
        } else if (getHeaderHeight() != mMinimumHeaderHeight) {
            final ObjectAnimator animator = ObjectAnimator.ofInt(this, "headerHeight",
                    mIntermediateHeaderHeight);
            animator.addListener(mHeaderExpandAnimationListener);
            animator.start();
        }
    }

    private void startDrag() {
        mIsBeingDragged = true;
        mScroller.abortAnimation();
    }

    private void stopDrag(boolean cancelled) {
        mIsBeingDragged = false;
        if (!cancelled && getChildCount() > 0) {
            final float velocity = getCurrentVelocity();
            if (velocity > mMinimumVelocity || velocity < -mMinimumVelocity) {
                fling(-velocity);
                onDragFinished(mScroller.getFinalY() - mScroller.getStartY());
            } else {
                onDragFinished(/* flingDelta = */ 0);
            }
        } else {
            onDragFinished(/* flingDelta = */ 0);
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }

        mEdgeGlowBottom.onRelease();
    }

    private void onDragFinished(int flingDelta) {
        if (!snapToTop(flingDelta)) {
            // The drag/fling won't result in the content at the top of the Window. Consider
            // snapping the content to the bottom of the window.
            snapToBottom(flingDelta);
        }
    }

    /**
     * If needed, snap the subviews to the top of the Window.
     */
    private boolean snapToTop(int flingDelta) {
        if (-getScroll_ignoreOversizedHeader() - flingDelta < 0
                && -getScroll_ignoreOversizedHeader() - flingDelta > -mTransparentStartHeight) {
            // We finish scrolling above the empty starting height, and aren't projected
            // to fling past the top of the Window, so elastically snap the empty space shut.
            mScroller.forceFinished(true);
            smoothScrollBy(-getScroll_ignoreOversizedHeader() + mTransparentStartHeight);
            return true;
        }
        return false;
    }

    /**
     * If needed, scroll all the subviews off the bottom of the Window.
     */
    private void snapToBottom(int flingDelta) {
        if (-getScroll_ignoreOversizedHeader() - flingDelta > 0) {
            mScroller.forceFinished(true);
            ObjectAnimator translateAnimation = ObjectAnimator.ofInt(this, "scroll",
                    getScroll() - getScrollUntilOffBottom());
            translateAnimation.setRepeatCount(0);
            translateAnimation.setInterpolator(new AccelerateInterpolator());
            translateAnimation.start();
        }
    }

    @Override
    public void scrollTo(int x, int y) {
        final int delta = y - getScroll();
        boolean wasFullscreen = getScrollNeededToBeFullScreen() <= 0;
        if (delta > 0) {
            scrollUp(delta);
        } else {
            scrollDown(delta);
        }
        updatePhotoTintAndDropShadow();
        final boolean isFullscreen = getScrollNeededToBeFullScreen() <= 0;
        if (mListener != null) {
            if (wasFullscreen && !isFullscreen) {
                 mListener.onExitFullscreen();
            } else if (!wasFullscreen && isFullscreen) {
                mListener.onEnterFullscreen();
            }
        }
    }

    /**
     * Set the height of the toolbar and update its tint accordingly.
     */
    @NeededForReflection
    public void setHeaderHeight(int height) {
        final LinearLayout.LayoutParams toolbarLayoutParams
                = (LayoutParams) mToolbar.getLayoutParams();
        toolbarLayoutParams.height = height;
        mToolbar.setLayoutParams(toolbarLayoutParams);
        updatePhotoTintAndDropShadow();
    }

    @NeededForReflection
    public int getHeaderHeight() {
        return mToolbar.getLayoutParams().height;
    }

    @NeededForReflection
    public void setScroll(int scroll) {
        scrollTo(0, scroll);
    }

    /**
     * Returns the total amount scrolled inside the nested ScrollView + the amount of shrinking
     * performed on the ToolBar. This is the value inspected by animators.
     */
    @NeededForReflection
    public int getScroll() {
        final LinearLayout.LayoutParams toolbarLayoutParams
                = (LayoutParams) mToolbar.getLayoutParams();
        return mTransparentStartHeight - toolbarLayoutParams.topMargin
                + mIntermediateHeaderHeight - toolbarLayoutParams.height + mScrollView.getScrollY();
    }

    /**
     * A variant of {@link #getScroll} that pretends the header is never larger than
     * than mIntermediateHeaderHeight. This function is sometimes needed when making scrolling
     * decisions that will not change the header size (ie, snapping to the bottom or top).
     */
    public int getScroll_ignoreOversizedHeader() {
        final LinearLayout.LayoutParams toolbarLayoutParams
                = (LayoutParams) mToolbar.getLayoutParams();
        return mTransparentStartHeight - toolbarLayoutParams.topMargin
                + Math.max(mIntermediateHeaderHeight - toolbarLayoutParams.height, 0)
                + mScrollView.getScrollY();
    }

    /**
     * Amount of transparent space above the header/toolbar.
     */
    public int getScrollNeededToBeFullScreen() {
        final LinearLayout.LayoutParams toolbarLayoutParams
                = (LayoutParams) mToolbar.getLayoutParams();
        return toolbarLayoutParams.topMargin;
    }

    /**
     * Return amount of scrolling needed in order for all the visible subviews to scroll off the
     * bottom.
     */
    public int getScrollUntilOffBottom() {
        return getHeight() + getScroll_ignoreOversizedHeader() - mTransparentStartHeight;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            // Examine the fling results in order to activate EdgeEffect when we fling to the end.
            final int oldScroll = getScroll();
            scrollTo(0, mScroller.getCurrY());
            final int delta = mScroller.getCurrY() - oldScroll;
            final int distanceFromMaxScrolling = getMaximumScrollUpwards() - getScroll();
            if (delta > distanceFromMaxScrolling && distanceFromMaxScrolling > 0) {
                mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
            }

            if (!awakenScrollBars()) {
                // Keep on drawing until the animation has finished.
                postInvalidateOnAnimation();
            }
            if (mScroller.getCurrY() >= getMaximumScrollUpwards()) {
                mScroller.abortAnimation();
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (!mEdgeGlowBottom.isFinished()) {
            final int restoreCount = canvas.save();
            final int width = getWidth() - getPaddingLeft() - getPaddingRight();
            final int height = getHeight();

            // Draw the EdgeEffect on the bottom of the Window (Or a little bit below the bottom
            // of the Window if we start to scroll upwards while EdgeEffect is visible). This
            // does not need to consider the case where this MultiShrinkScroller doesn't fill
            // the Window, since the nested ScrollView should be set to fillViewport.
            canvas.translate(-width + getPaddingLeft(),
                    height + getMaximumScrollUpwards() - getScroll());

            canvas.rotate(180, width, 0);
            mEdgeGlowBottom.setSize(width, height);
            if (mEdgeGlowBottom.draw(canvas)) {
                postInvalidateOnAnimation();
            }
            canvas.restoreToCount(restoreCount);
        }
    }

    private float getCurrentVelocity() {
        mVelocityTracker.computeCurrentVelocity(PIXELS_PER_SECOND, mMaximumVelocity);
        return mVelocityTracker.getYVelocity();
    }

    private void fling(float velocity) {
        // For reasons I do not understand, scrolling is less janky when maxY=Integer.MAX_VALUE
        // then when maxY is set to an actual value.
        mScroller.fling(0, getScroll(), 0, (int) velocity, 0, 0, -Integer.MAX_VALUE,
                Integer.MAX_VALUE);
        invalidate();
    }

    private int getMaximumScrollUpwards() {
        return mTransparentStartHeight
                // How much the Header view can compress
                + mIntermediateHeaderHeight - mMinimumHeaderHeight
                // How much the ScrollView can scroll. 0, if child is smaller than ScrollView.
                + Math.max(0, mScrollViewChild.getHeight() - getHeight() + mMinimumHeaderHeight);
    }

    private void scrollUp(int delta) {
        LinearLayout.LayoutParams toolbarLayoutParams = (LayoutParams) mToolbar.getLayoutParams();
        if (toolbarLayoutParams.topMargin != 0) {
            final int originalValue = toolbarLayoutParams.topMargin;
            toolbarLayoutParams.topMargin -= delta;
            toolbarLayoutParams.topMargin = Math.max(toolbarLayoutParams.topMargin, 0);
            mToolbar.setLayoutParams(toolbarLayoutParams);
            delta -= originalValue - toolbarLayoutParams.topMargin;
        }
        if (toolbarLayoutParams.height != mMinimumHeaderHeight) {
            final int originalValue = toolbarLayoutParams.height;
            toolbarLayoutParams.height -= delta;
            toolbarLayoutParams.height = Math.max(toolbarLayoutParams.height, mMinimumHeaderHeight);
            mToolbar.setLayoutParams(toolbarLayoutParams);
            delta -= originalValue - toolbarLayoutParams.height;
        }
        mScrollView.scrollBy(0, delta);
    }

    private void scrollDown(int delta) {
        LinearLayout.LayoutParams toolbarLayoutParams = (LayoutParams) mToolbar.getLayoutParams();
        if (mScrollView.getScrollY() > 0) {
            final int originalValue = mScrollView.getScrollY();
            mScrollView.scrollBy(0, delta);
            delta -= mScrollView.getScrollY() - originalValue;
        }
        if (toolbarLayoutParams.height < mIntermediateHeaderHeight) {
            final int originalValue = toolbarLayoutParams.height;
            toolbarLayoutParams.height -= delta;
            toolbarLayoutParams.height = Math.min(toolbarLayoutParams.height,
                    mIntermediateHeaderHeight);
            mToolbar.setLayoutParams(toolbarLayoutParams);
            delta -= originalValue - toolbarLayoutParams.height;
        }
        toolbarLayoutParams.topMargin -= delta;
        mToolbar.setLayoutParams(toolbarLayoutParams);

        if (mListener != null && getScrollUntilOffBottom() <= 0) {
            post(new Runnable() {
                @Override
                public void run() {
                    mListener.onScrolledOffBottom();
                }
            });
        }
    }

    private void updatePhotoTintAndDropShadow() {
        // We need to use toolbarLayoutParams to determine the height, since the layout
        // params can be updated before the height change is reflected inside the View#getHeight().
        final int toolbarHeight = mToolbar.getLayoutParams().height;
        // Reuse an existing mColorFilter (to avoid GC pauses) to change the photo's tint.
        mPhotoView.clearColorFilter();
        if (toolbarHeight >= mIntermediateHeaderHeight) {
            mPhotoViewContainer.setElevation(0);
            return;
        }
        if (toolbarHeight <= mMinimumHeaderHeight) {
            mColorFilter.setColor(mHeaderTintColor);
            mPhotoView.setColorFilter(mColorFilter);
            mPhotoViewContainer.setElevation(mToolbarElevation);
        } else if (toolbarHeight <= mIntermediateHeaderHeight) {
            mPhotoViewContainer.setElevation(0);
            final int alphaBits = 0xff - 0xff * (toolbarHeight  - mMinimumHeaderHeight)
                    / (mIntermediateHeaderHeight - mMinimumHeaderHeight);
            final int color = alphaBits << 24 | (mHeaderTintColor & 0xffffff);
            mColorFilter.setColor(color);
            mPhotoView.setColorFilter(mColorFilter);
        }
    }

    private void updateLastEventPosition(MotionEvent event) {
        mLastEventPosition[0] = event.getX();
        mLastEventPosition[1] = event.getY();
    }

    private boolean motionShouldStartDrag(MotionEvent event) {
        final float deltaX = event.getX() - mLastEventPosition[0];
        final float deltaY = event.getY() - mLastEventPosition[1];
        final boolean draggedX = (deltaX > mTouchSlop || deltaX < -mTouchSlop);
        final boolean draggedY = (deltaY > mTouchSlop || deltaY < -mTouchSlop);
        return draggedY && !draggedX;
    }

    private float updatePositionAndComputeDelta(MotionEvent event) {
        final int VERTICAL = 1;
        final float position = mLastEventPosition[VERTICAL];
        updateLastEventPosition(event);
        return position - mLastEventPosition[VERTICAL];
    }

    private void smoothScrollBy(int delta) {
        if (delta == 0) {
            // Delta=0 implies the code calling smoothScrollBy is sloppy. We should avoid doing
            // this, since it prevents Views from being able to register any clicks for 250ms.
            throw new IllegalArgumentException("Smooth scrolling by delta=0 is "
                    + "pointless and harmful");
        }
        mScroller.startScroll(0, getScroll(), 0, delta);
        invalidate();
    }
}
