package com.android.contacts.widget;

import com.android.contacts.R;
import com.android.contacts.quickcontact.ExpandingEntryCardView;
import com.android.contacts.test.NeededForReflection;
import com.android.contacts.util.SchedulingUtils;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManagerGlobal;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.ScrollView;
import android.widget.TextView;

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
 *
 * Before copying this approach to nested scrolling, consider whether something simpler & less
 * customized will work for you. For example, see the re-usable StickyHeaderListView used by
 * WifiSetupActivity (very nice). Alternatively, check out Google+'s cover photo scrolling or
 * Android L's built in nested scrolling support. I thought I needed a more custom ViewGroup in
 * order to track velocity, modify EdgeEffect color & perform specific animations such as the ones
 * inside snapToBottom(). As a result this ViewGroup has non-standard talkback and keyboard support.
 */
public class MultiShrinkScroller extends FrameLayout {

    /**
     * 1000 pixels per millisecond. Ie, 1 pixel per second.
     */
    private static final int PIXELS_PER_SECOND = 1000;

    /**
     * Length of the acceleration animations. This value was taken from ValueAnimator.java.
     */
    private static final int EXIT_FLING_ANIMATION_DURATION_MS = 300;

    /**
     * Length of the entrance animation.
     */
    private static final int ENTRANCE_ANIMATION_SLIDE_OPEN_DURATION_MS = 250;

    /**
     * In portrait mode, the height:width ratio of the photo's starting height.
     */
    private static final float INTERMEDIATE_HEADER_HEIGHT_RATIO = 0.5f;

    /**
     * Maximum velocity for flings in dips per second. Picked via non-rigorous experimentation.
     */
    private static final float MAXIMUM_FLING_VELOCITY = 2000;

    private float[] mLastEventPosition = { 0, 0 };
    private VelocityTracker mVelocityTracker;
    private boolean mIsBeingDragged = false;
    private boolean mReceivedDown = false;

    private ScrollView mScrollView;
    private View mScrollViewChild;
    private View mToolbar;
    private QuickContactImageView mPhotoView;
    private View mPhotoViewContainer;
    private View mTransparentView;
    private MultiShrinkScrollerListener mListener;
    private TextView mLargeTextView;
    private View mPhotoTouchInterceptOverlay;
    /** Contains desired location/size of the title, once the header is fully compressed */
    private TextView mInvisiblePlaceholderTextView;
    private View mTitleGradientView;
    private View mActionBarGradientView;
    private View mStartColumn;
    private int mHeaderTintColor;
    private int mMaximumHeaderHeight;
    private int mMinimumHeaderHeight;
    /**
     * When the contact photo is tapped, it is resized to max size or this size. This value also
     * sometimes represents the maximum achievable header size achieved by scrolling. To enforce
     * this maximum in scrolling logic, always access this value via
     * {@link #getMaximumScrollableHeaderHeight}.
     */
    private int mIntermediateHeaderHeight;
    /**
     * If true, regular scrolling can expand the header beyond mIntermediateHeaderHeight. The
     * header, that contains the contact photo, can expand to a height equal its width.
     */
    private boolean mIsOpenContactSquare;
    private int mMaximumHeaderTextSize;
    private int mCollapsedTitleBottomMargin;
    private int mCollapsedTitleStartMargin;
    private int mMinimumPortraitHeaderHeight;
    private int mMaximumPortraitHeaderHeight;
    /**
     * True once the header has touched the top of the screen at least once.
     */
    private boolean mHasEverTouchedTheTop;

    private final Scroller mScroller;
    private final EdgeEffect mEdgeGlowBottom;
    private final int mTouchSlop;
    private final int mMaximumVelocity;
    private final int mMinimumVelocity;
    private final int mTransparentStartHeight;
    private final int mMaximumTitleMargin;
    private final float mToolbarElevation;
    private final boolean mIsTwoPanel;
    private final int mActionBarSize;

    // Objects used to perform color filtering on the header. These are stored as fields for
    // the sole purpose of avoiding "new" operations inside animation loops.
    private final ColorMatrix mWhitenessColorMatrix = new ColorMatrix();
    private final ColorMatrix mColorMatrix = new ColorMatrix();
    private final float[] mAlphaMatrixValues = {
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 1, 0
    };
    private final ColorMatrix mMultiplyBlendMatrix = new ColorMatrix();
    private final float[] mMultiplyBlendMatrixValues = {
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 1, 0
    };

    private final PathInterpolator mTextSizePathInterpolator
            = new PathInterpolator(0.16f, 0.4f, 0.2f, 1);
    /**
     * Interpolator that starts and ends with nearly straight segments. At x=0 it has a y of
     * approximately 0.25. We only want the contact photo 25% faded when half collapsed.
     */
    private final PathInterpolator mWhiteBlendingPathInterpolator
            = new PathInterpolator(1.0f, 0.4f, 0.9f, 0.8f);

    private final int[] mGradientColors = new int[] {0,0xAA000000};
    private GradientDrawable mTitleGradientDrawable = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, mGradientColors);
    private GradientDrawable mActionBarGradientDrawable = new GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP, mGradientColors);

    public interface MultiShrinkScrollerListener {
        void onScrolledOffBottom();

        void onStartScrollOffBottom();

        void onTransparentViewHeightChange(float ratio);

        void onEntranceAnimationDone();

        void onEnterFullscreen();

        void onExitFullscreen();
    }

    private final AnimatorListener mSnapToBottomListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            if (getScrollUntilOffBottom() > 0 && mListener != null) {
                // Due to a rounding error, after the animation finished we haven't fully scrolled
                // off the screen. Lie to the listener: tell it that we did scroll off the screen.
                mListener.onScrolledOffBottom();
                // No other messages need to be sent to the listener.
                mListener = null;
            }
        }
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
        mMaximumVelocity = (int)TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, MAXIMUM_FLING_VELOCITY,
                getResources().getDisplayMetrics());
        mTransparentStartHeight = (int) getResources().getDimension(
                R.dimen.quickcontact_starting_empty_height);
        mToolbarElevation = getResources().getDimension(
                R.dimen.quick_contact_toolbar_elevation);
        mIsTwoPanel = getResources().getBoolean(R.bool.quickcontact_two_panel);
        mMaximumTitleMargin = (int) getResources().getDimension(
                R.dimen.quickcontact_title_initial_margin);

        final TypedArray attributeArray = context.obtainStyledAttributes(
                new int[]{android.R.attr.actionBarSize});
        mActionBarSize = attributeArray.getDimensionPixelSize(0, 0);
        mMinimumHeaderHeight = mActionBarSize;
        // This value is approximately equal to the portrait ActionBar size. It isn't exactly the
        // same, since the landscape and portrait ActionBar sizes can be different.
        mMinimumPortraitHeaderHeight = mMinimumHeaderHeight;
        attributeArray.recycle();
    }

    /**
     * This method must be called inside the Activity's OnCreate.
     */
    public void initialize(MultiShrinkScrollerListener listener, boolean isOpenContactSquare) {
        mScrollView = (ScrollView) findViewById(R.id.content_scroller);
        mScrollViewChild = findViewById(R.id.card_container);
        mToolbar = findViewById(R.id.toolbar_parent);
        mPhotoViewContainer = findViewById(R.id.toolbar_parent);
        mTransparentView = findViewById(R.id.transparent_view);
        mLargeTextView = (TextView) findViewById(R.id.large_title);
        mInvisiblePlaceholderTextView = (TextView) findViewById(R.id.placeholder_textview);
        mStartColumn = findViewById(R.id.empty_start_column);
        // Touching the empty space should close the card
        if (mStartColumn != null) {
            mStartColumn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    scrollOffBottom();
                }
            });
            findViewById(R.id.empty_end_column).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    scrollOffBottom();
                }
            });
        }
        mListener = listener;
        mIsOpenContactSquare = isOpenContactSquare;

        mPhotoView = (QuickContactImageView) findViewById(R.id.photo);

        mTitleGradientView = findViewById(R.id.title_gradient);
        mTitleGradientView.setBackground(mTitleGradientDrawable);
        mActionBarGradientView = findViewById(R.id.action_bar_gradient);
        mActionBarGradientView.setBackground(mActionBarGradientDrawable);

        mPhotoTouchInterceptOverlay = findViewById(R.id.photo_touch_intercept_overlay);
        if (!mIsTwoPanel) {
            mPhotoTouchInterceptOverlay.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    expandHeader();
                }
            });
        }

        SchedulingUtils.doOnPreDraw(this, /* drawNextFrame = */ false, new Runnable() {
            @Override
            public void run() {
                if (!mIsTwoPanel) {
                    // We never want the height of the photo view to exceed its width.
                    mMaximumHeaderHeight = mPhotoViewContainer.getWidth();
                    mIntermediateHeaderHeight = (int) (mMaximumHeaderHeight
                            * INTERMEDIATE_HEADER_HEIGHT_RATIO);
                }
                final boolean isLandscape = getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
                mMaximumPortraitHeaderHeight = isLandscape ? getHeight()
                        : mPhotoViewContainer.getWidth();
                setHeaderHeight(getMaximumScrollableHeaderHeight());
                mMaximumHeaderTextSize = mLargeTextView.getHeight();
                if (mIsTwoPanel) {
                    mMaximumHeaderHeight = getHeight();
                    mMinimumHeaderHeight = mMaximumHeaderHeight;
                    mIntermediateHeaderHeight = mMaximumHeaderHeight;

                    // Permanently set photo width and height.
                    final TypedValue photoRatio = new TypedValue();
                    getResources().getValue(R.vals.quickcontact_photo_ratio, photoRatio,
                            /* resolveRefs = */ true);
                    final ViewGroup.LayoutParams photoLayoutParams
                            = mPhotoViewContainer.getLayoutParams();
                    photoLayoutParams.height = mMaximumHeaderHeight;
                    photoLayoutParams.width = (int) (mMaximumHeaderHeight * photoRatio.getFloat());
                    mPhotoViewContainer.setLayoutParams(photoLayoutParams);

                    // Permanently set title width and margin.
                    final FrameLayout.LayoutParams largeTextLayoutParams
                            = (FrameLayout.LayoutParams) mLargeTextView.getLayoutParams();
                    largeTextLayoutParams.width = photoLayoutParams.width -
                            largeTextLayoutParams.leftMargin - largeTextLayoutParams.rightMargin;
                    largeTextLayoutParams.gravity = Gravity.BOTTOM | Gravity.START;
                    mLargeTextView.setLayoutParams(largeTextLayoutParams);
                } else {
                    // Set the width of mLargeTextView as if it was nested inside
                    // mPhotoViewContainer.
                    mLargeTextView.setWidth(mPhotoViewContainer.getWidth()
                            - 2 * mMaximumTitleMargin);
                }

                calculateCollapsedLargeTitlePadding();
                updateHeaderTextSizeAndMargin();
                configureGradientViewHeights();
            }
        });
    }

    private void configureGradientViewHeights() {
        final float GRADIENT_SIZE_COEFFICIENT = 1.25f;
        final FrameLayout.LayoutParams actionBarGradientLayoutParams
                = (FrameLayout.LayoutParams) mActionBarGradientView.getLayoutParams();
        actionBarGradientLayoutParams.height
                = (int) (mActionBarSize * GRADIENT_SIZE_COEFFICIENT);
        mActionBarGradientView.setLayoutParams(actionBarGradientLayoutParams);
        final FrameLayout.LayoutParams titleGradientLayoutParams
                = (FrameLayout.LayoutParams) mTitleGradientView.getLayoutParams();
        final FrameLayout.LayoutParams largeTextLayoutParms
                = (FrameLayout.LayoutParams) mLargeTextView.getLayoutParams();
        titleGradientLayoutParams.height = (int) ((mLargeTextView.getHeight()
                + largeTextLayoutParms.bottomMargin) * GRADIENT_SIZE_COEFFICIENT);
        mTitleGradientView.setLayoutParams(titleGradientLayoutParams);
    }

    public void setTitle(String title) {
        mLargeTextView.setText(title);
        mPhotoTouchInterceptOverlay.setContentDescription(title);
    }

    public void setUseGradient(boolean useGradient) {
        if (mTitleGradientView != null) {
            mTitleGradientView.setVisibility(useGradient ? View.VISIBLE : View.GONE);
            mActionBarGradientView.setVisibility(useGradient ? View.VISIBLE : View.GONE);
        }
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
                    final int distanceFromMaxScrolling = getMaximumScrollUpwards() - getScroll();
                    if (delta > distanceFromMaxScrolling) {
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
        // We want to use the same amount of alpha on the new tint color as the previous tint color.
        final int edgeEffectAlpha = Color.alpha(mEdgeGlowBottom.getColor());
        mEdgeGlowBottom.setColor((color & 0xffffff) | Color.argb(edgeEffectAlpha, 0, 0, 0));
    }

    /**
     * Expand to maximum size.
     */
    private void expandHeader() {
        if (getHeaderHeight() != mMaximumHeaderHeight) {
            final ObjectAnimator animator = ObjectAnimator.ofInt(this, "headerHeight",
                    mMaximumHeaderHeight);
            animator.setDuration(ExpandingEntryCardView.DURATION_EXPAND_ANIMATION_CHANGE_BOUNDS);
            animator.start();
            // Scroll nested scroll view to its top
            if (mScrollView.getScrollY() != 0) {
                ObjectAnimator.ofInt(mScrollView, "scrollY", -mScrollView.getScrollY()).start();
            }
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
        if (mHasEverTouchedTheTop) {
            // Only when first interacting with QuickContacts should QuickContacts snap to the top
            // of the screen. After this, QuickContacts can be placed most anywhere on the screen.
            return false;
        }
        final int requiredScroll = -getScroll_ignoreOversizedHeaderForSnapping()
                + mTransparentStartHeight;
        if (-getScroll_ignoreOversizedHeaderForSnapping() - flingDelta < 0
                && -getScroll_ignoreOversizedHeaderForSnapping() - flingDelta >
                -mTransparentStartHeight && requiredScroll != 0) {
            // We finish scrolling above the empty starting height, and aren't projected
            // to fling past the top of the Window, so elastically snap the empty space shut.
            mScroller.forceFinished(true);
            smoothScrollBy(requiredScroll);
            return true;
        }
        return false;
    }

    /**
     * If needed, scroll all the subviews off the bottom of the Window.
     */
    private void snapToBottom(int flingDelta) {
        if (mHasEverTouchedTheTop) {
            // If QuickContacts has touched the top of the screen previously, then we
            // will less aggressively snap to the bottom of the screen.
            final float predictedScrollPastTop = -getScroll() + mTransparentStartHeight
                    - flingDelta;
            final boolean isLandscape = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            if (isLandscape) {
                // In landscape orientation, we dismiss the QC once it goes below the starting
                // starting offset that is used when QC starts in collapsed mode.
                if (predictedScrollPastTop > mTransparentStartHeight) {
                    scrollOffBottom();
                }
            } else {
                // In portrait orientation, we dismiss the QC once it goes below
                // mIntermediateHeaderHeight within the bottom of the screen.
                final float heightMinusHeader = getHeight() - mIntermediateHeaderHeight;
                if (predictedScrollPastTop > heightMinusHeader) {
                    scrollOffBottom();
                }
            }
            return;
        }
        if (-getScroll() - flingDelta > 0) {
            scrollOffBottom();
        }
    }

    /**
     * Return ratio of non-transparent:viewgroup-height for this viewgroup at the starting position.
     */
    public float getStartingTransparentHeightRatio() {
        return getTransparentHeightRatio(mTransparentStartHeight);
    }

    private float getTransparentHeightRatio(int transparentHeight) {
        final float heightRatio = (float) transparentHeight / getHeight();
        // Clamp between [0, 1] in case this is called before height is initialized.
        return 1.0f - Math.max(Math.min(1.0f, heightRatio), 0f);
    }

    public void scrollOffBottom() {
        final Interpolator interpolator = new AcceleratingFlingInterpolator(
                EXIT_FLING_ANIMATION_DURATION_MS, getCurrentVelocity(),
                getScrollUntilOffBottom());
        mScroller.forceFinished(true);
        ObjectAnimator translateAnimation = ObjectAnimator.ofInt(this, "scroll",
                getScroll() - getScrollUntilOffBottom());
        translateAnimation.setRepeatCount(0);
        translateAnimation.setInterpolator(interpolator);
        translateAnimation.setDuration(EXIT_FLING_ANIMATION_DURATION_MS);
        translateAnimation.addListener(mSnapToBottomListener);
        translateAnimation.start();
        if (mListener != null) {
            mListener.onStartScrollOffBottom();
        }
    }

    /**
     * @param scrollToCurrentPosition if true, will scroll from the bottom of the screen to the
     * current position. Otherwise, will scroll from the bottom of the screen to the top of the
     * screen.
     */
    public void scrollUpForEntranceAnimation(boolean scrollToCurrentPosition) {
        final int currentPosition = getScroll();
        final int bottomScrollPosition = currentPosition
                - (getHeight() - getTransparentViewHeight()) + 1;
        final Interpolator interpolator = AnimationUtils.loadInterpolator(getContext(),
                android.R.interpolator.linear_out_slow_in);
        final int desiredValue = currentPosition + (scrollToCurrentPosition ? currentPosition
                : getTransparentViewHeight());
        final ObjectAnimator animator = ObjectAnimator.ofInt(this, "scroll", bottomScrollPosition,
                desiredValue);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (animation.getAnimatedValue().equals(desiredValue) && mListener != null) {
                    mListener.onEntranceAnimationDone();
                }
            }
        });
        animator.start();
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
        updateHeaderTextSizeAndMargin();
        final boolean isFullscreen = getScrollNeededToBeFullScreen() <= 0;
        mHasEverTouchedTheTop |= isFullscreen;
        if (mListener != null) {
            if (wasFullscreen && !isFullscreen) {
                 mListener.onExitFullscreen();
            } else if (!wasFullscreen && isFullscreen) {
                mListener.onEnterFullscreen();
            }
            if (!isFullscreen || !wasFullscreen) {
                mListener.onTransparentViewHeightChange(
                        getTransparentHeightRatio(getTransparentViewHeight()));
            }
        }
    }

    /**
     * Change the height of the header/toolbar. Do *not* use this outside animations. This was
     * designed for use by {@link #prepareForShrinkingScrollChild}.
     */
    @NeededForReflection
    public void setToolbarHeight(int delta) {
        final ViewGroup.LayoutParams toolbarLayoutParams
                = mToolbar.getLayoutParams();
        toolbarLayoutParams.height = delta;
        mToolbar.setLayoutParams(toolbarLayoutParams);

        updatePhotoTintAndDropShadow();
        updateHeaderTextSizeAndMargin();
    }

    @NeededForReflection
    public int getToolbarHeight() {
        return mToolbar.getLayoutParams().height;
    }

    /**
     * Set the height of the toolbar and update its tint accordingly.
     */
    @NeededForReflection
    public void setHeaderHeight(int height) {
        final ViewGroup.LayoutParams toolbarLayoutParams
                = mToolbar.getLayoutParams();
        toolbarLayoutParams.height = height;
        mToolbar.setLayoutParams(toolbarLayoutParams);
        updatePhotoTintAndDropShadow();
        updateHeaderTextSizeAndMargin();
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
        return mTransparentStartHeight - getTransparentViewHeight()
                + getMaximumScrollableHeaderHeight() - getToolbarHeight()
                + mScrollView.getScrollY();
    }

    private int getMaximumScrollableHeaderHeight() {
        return mIsOpenContactSquare ? mMaximumHeaderHeight : mIntermediateHeaderHeight;
    }

    /**
     * A variant of {@link #getScroll} that pretends the header is never larger than
     * than mIntermediateHeaderHeight. This function is sometimes needed when making scrolling
     * decisions that will not change the header size (ie, snapping to the bottom or top).
     *
     * When mIsOpenContactSquare is true, this function considers mIntermediateHeaderHeight ==
     * mMaximumHeaderHeight, since snapping decisions will be made relative the full header
     * size when mIsOpenContactSquare = true.
     *
     * This value should never be used in conjunction with {@link #getScroll} values.
     */
    private int getScroll_ignoreOversizedHeaderForSnapping() {
        return mTransparentStartHeight - getTransparentViewHeight()
                + Math.max(getMaximumScrollableHeaderHeight() - getToolbarHeight(), 0)
                + mScrollView.getScrollY();
    }

    /**
     * Amount of transparent space above the header/toolbar.
     */
    public int getScrollNeededToBeFullScreen() {
        return getTransparentViewHeight();
    }

    /**
     * Return amount of scrolling needed in order for all the visible subviews to scroll off the
     * bottom.
     */
    private int getScrollUntilOffBottom() {
        return getHeight() + getScroll_ignoreOversizedHeaderForSnapping()
                - mTransparentStartHeight;
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
            if (mIsTwoPanel) {
                // Only show the EdgeEffect on the bottom of the ScrollView.
                mEdgeGlowBottom.setSize(mScrollView.getWidth(), height);
                if (isLayoutRtl()) {
                    canvas.translate(mPhotoViewContainer.getWidth(), 0);
                }
            } else {
                mEdgeGlowBottom.setSize(width, height);
            }
            if (mEdgeGlowBottom.draw(canvas)) {
                postInvalidateOnAnimation();
            }
            canvas.restoreToCount(restoreCount);
        }
    }

    private float getCurrentVelocity() {
        if (mVelocityTracker == null) {
            return 0;
        }
        mVelocityTracker.computeCurrentVelocity(PIXELS_PER_SECOND, mMaximumVelocity);
        return mVelocityTracker.getYVelocity();
    }

    private void fling(float velocity) {
        if (Math.abs(mMaximumVelocity) < Math.abs(velocity)) {
            velocity = -mMaximumVelocity * Math.signum(velocity);
        }
        // For reasons I do not understand, scrolling is less janky when maxY=Integer.MAX_VALUE
        // then when maxY is set to an actual value.
        mScroller.fling(0, getScroll(), 0, (int) velocity, 0, 0, -Integer.MAX_VALUE,
                Integer.MAX_VALUE);
        invalidate();
    }

    private int getMaximumScrollUpwards() {
        if (!mIsTwoPanel) {
            return mTransparentStartHeight
                    // How much the Header view can compress
                    + getMaximumScrollableHeaderHeight() - getFullyCompressedHeaderHeight()
                    // How much the ScrollView can scroll. 0, if child is smaller than ScrollView.
                    + Math.max(0, mScrollViewChild.getHeight() - getHeight()
                    + getFullyCompressedHeaderHeight());
        } else {
            return mTransparentStartHeight
                    // How much the ScrollView can scroll. 0, if child is smaller than ScrollView.
                    + Math.max(0, mScrollViewChild.getHeight() - getHeight());
        }
    }

    private int getTransparentViewHeight() {
        return mTransparentView.getLayoutParams().height;
    }

    private void setTransparentViewHeight(int height) {
        mTransparentView.getLayoutParams().height = height;
        mTransparentView.setLayoutParams(mTransparentView.getLayoutParams());
    }

    private void scrollUp(int delta) {
        if (getTransparentViewHeight() != 0) {
            final int originalValue = getTransparentViewHeight();
            setTransparentViewHeight(getTransparentViewHeight() - delta);
            setTransparentViewHeight(Math.max(0, getTransparentViewHeight()));
            delta -= originalValue - getTransparentViewHeight();
        }
        final ViewGroup.LayoutParams toolbarLayoutParams
                = mToolbar.getLayoutParams();
        if (toolbarLayoutParams.height > getFullyCompressedHeaderHeight()) {
            final int originalValue = toolbarLayoutParams.height;
            toolbarLayoutParams.height -= delta;
            toolbarLayoutParams.height = Math.max(toolbarLayoutParams.height,
                    getFullyCompressedHeaderHeight());
            mToolbar.setLayoutParams(toolbarLayoutParams);
            delta -= originalValue - toolbarLayoutParams.height;
        }
        mScrollView.scrollBy(0, delta);
    }

    /**
     * Returns the minimum size that we want to compress the header to, given that we don't want to
     * allow the the ScrollView to scroll unless there is new content off of the edge of ScrollView.
     */
    private int getFullyCompressedHeaderHeight() {
        return Math.min(Math.max(mToolbar.getLayoutParams().height - getOverflowingChildViewSize(),
                mMinimumHeaderHeight), getMaximumScrollableHeaderHeight());
    }

    /**
     * Returns the amount of mScrollViewChild that doesn't fit inside its parent.
     */
    private int getOverflowingChildViewSize() {
        final int usedScrollViewSpace = mScrollViewChild.getHeight();
        return -getHeight() + usedScrollViewSpace + mToolbar.getLayoutParams().height;
    }

    private void scrollDown(int delta) {
        if (mScrollView.getScrollY() > 0) {
            final int originalValue = mScrollView.getScrollY();
            mScrollView.scrollBy(0, delta);
            delta -= mScrollView.getScrollY() - originalValue;
        }
        final ViewGroup.LayoutParams toolbarLayoutParams = mToolbar.getLayoutParams();
        if (toolbarLayoutParams.height < getMaximumScrollableHeaderHeight()) {
            final int originalValue = toolbarLayoutParams.height;
            toolbarLayoutParams.height -= delta;
            toolbarLayoutParams.height = Math.min(toolbarLayoutParams.height,
                    getMaximumScrollableHeaderHeight());
            mToolbar.setLayoutParams(toolbarLayoutParams);
            delta -= originalValue - toolbarLayoutParams.height;
        }
        setTransparentViewHeight(getTransparentViewHeight() - delta);

        if (getScrollUntilOffBottom() <= 0) {
            post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onScrolledOffBottom();
                        // No other messages need to be sent to the listener.
                        mListener = null;
                    }
                }
            });
        }
    }

    /**
     * Set the header size and padding, based on the current scroll position.
     */
    private void updateHeaderTextSizeAndMargin() {
        if (mIsTwoPanel) {
            // The text size stays at a constant size & location in two panel layouts.
            return;
        }

        // The pivot point for scaling should be middle of the starting side.
        if (isLayoutRtl()) {
            mLargeTextView.setPivotX(mLargeTextView.getWidth());
        } else {
            mLargeTextView.setPivotX(0);
        }
        mLargeTextView.setPivotY(mLargeTextView.getHeight() / 2);

        final int toolbarHeight = mToolbar.getLayoutParams().height;
        mPhotoTouchInterceptOverlay.setClickable(toolbarHeight != mMaximumHeaderHeight);

        if (toolbarHeight >= mMaximumHeaderHeight) {
            // Everything is full size when the header is fully expanded.
            mLargeTextView.setScaleX(1);
            mLargeTextView.setScaleY(1);
            setInterpolatedTitleMargins(1);
            return;
        }

        final float ratio = (toolbarHeight  - mMinimumHeaderHeight)
                / (float)(mMaximumHeaderHeight - mMinimumHeaderHeight);
        final float minimumSize = mInvisiblePlaceholderTextView.getHeight();
        float bezierOutput = mTextSizePathInterpolator.getInterpolation(ratio);
        float scale = (minimumSize + (mMaximumHeaderTextSize - minimumSize) * bezierOutput)
                / mMaximumHeaderTextSize;

        // Clamp to reasonable/finite values before passing into framework. The values
        // can be wacky before the first pre-render.
        bezierOutput = (float) Math.min(bezierOutput, 1.0f);
        scale = (float) Math.min(scale, 1.0f);

        mLargeTextView.setScaleX(scale);
        mLargeTextView.setScaleY(scale);
        setInterpolatedTitleMargins(bezierOutput);
    }

    /**
     * Calculate the padding around mLargeTextView so that it will look appropriate once it
     * finishes moving into its target location/size.
     */
    private void calculateCollapsedLargeTitlePadding() {
        final Rect largeTextViewRect = new Rect();
        final Rect invisiblePlaceholderTextViewRect = new Rect();
        mToolbar.getBoundsOnScreen(largeTextViewRect);
        mInvisiblePlaceholderTextView.getBoundsOnScreen(invisiblePlaceholderTextViewRect);
        if (isLayoutRtl()) {
            mCollapsedTitleStartMargin = largeTextViewRect.right
                    - invisiblePlaceholderTextViewRect.right;
        } else {
            mCollapsedTitleStartMargin = invisiblePlaceholderTextViewRect.left
                    - largeTextViewRect.left;
        }

        // Distance between top of toolbar to the center of the target rectangle.
        final int desiredTopToCenter = (
                invisiblePlaceholderTextViewRect.top + invisiblePlaceholderTextViewRect.bottom)
                / 2 - largeTextViewRect.top;
        // Padding needed on the mLargeTextView so that it has the same amount of
        // padding as the target rectangle.
        mCollapsedTitleBottomMargin = desiredTopToCenter - mLargeTextView.getHeight() / 2;
    }

    /**
     * Interpolate the title's margin size. When {@param x}=1, use the maximum title margins.
     * When {@param x}=0, use the margin values taken from {@link #mInvisiblePlaceholderTextView}.
     */
    private void setInterpolatedTitleMargins(float x) {
        final FrameLayout.LayoutParams titleLayoutParams
                = (FrameLayout.LayoutParams) mLargeTextView.getLayoutParams();
        final LinearLayout.LayoutParams toolbarLayoutParams
                = (LinearLayout.LayoutParams) mToolbar.getLayoutParams();

        // Need to add more to margin start if there is a start column
        int startColumnWidth = mStartColumn == null ? 0 : mStartColumn.getWidth();

        titleLayoutParams.setMarginStart((int) (mCollapsedTitleStartMargin * (1 - x)
                + mMaximumTitleMargin * x) + startColumnWidth);
        // How offset the title should be from the bottom of the toolbar
        final int pretendBottomMargin =  (int) (mCollapsedTitleBottomMargin * (1 - x)
                + mMaximumTitleMargin * x) ;
        // Calculate how offset the title should be from the top of the screen. Instead of
        // calling mLargeTextView.getHeight() use the mMaximumHeaderTextSize for this calculation.
        // The getHeight() value acts unexpectedly when mLargeTextView is partially clipped by
        // its parent.
        titleLayoutParams.topMargin = getTransparentViewHeight()
                + toolbarLayoutParams.height - pretendBottomMargin
                - mMaximumHeaderTextSize;
        titleLayoutParams.bottomMargin = 0;
        mLargeTextView.setLayoutParams(titleLayoutParams);
    }

    private void updatePhotoTintAndDropShadow() {
        // Let's keep an eye on how long this method takes to complete. Right now, it takes ~0.2ms
        // on a Nexus 5. If it starts to get much slower, there are a number of easy optimizations
        // available.
        Trace.beginSection("updatePhotoTintAndDropShadow");

        if (mIsTwoPanel && !mPhotoView.isBasedOffLetterTile()) {
            // When in two panel mode, UX considers photo tinting unnecessary for non letter
            // tile photos.
            mTitleGradientDrawable.setAlpha(0xFF);
            mActionBarGradientDrawable.setAlpha(0xFF);
            return;
        }

        // We need to use toolbarLayoutParams to determine the height, since the layout
        // params can be updated before the height change is reflected inside the View#getHeight().
        final int toolbarHeight = getToolbarHeight();

        if (toolbarHeight <= mMinimumHeaderHeight && !mIsTwoPanel) {
            mPhotoViewContainer.setElevation(mToolbarElevation);
        } else {
            mPhotoViewContainer.setElevation(0);
        }

        // Reuse an existing mColorFilter (to avoid GC pauses) to change the photo's tint.
        mPhotoView.clearColorFilter();

        // Ratio of current size to maximum size of the header.
        final float ratio;
        // The value that "ratio" will have when the header is at its starting/intermediate size.
        final float intermediateRatio = calculateHeightRatio((int)
                (mMaximumPortraitHeaderHeight * INTERMEDIATE_HEADER_HEIGHT_RATIO));
        if (!mIsTwoPanel) {
            ratio = calculateHeightRatio(toolbarHeight);
        } else {
            // We want the ratio and intermediateRatio to have the *approximate* values
            // they would have in portrait mode when at the intermediate position.
            ratio = intermediateRatio;
        }

        final float linearBeforeMiddle = Math.max(1 - (1 - ratio) / intermediateRatio, 0);

        // Want a function with a derivative of 0 at x=0. I don't want it to grow too
        // slowly before x=0.5. x^1.1 satisfies both requirements.
        final float EXPONENT_ALMOST_ONE = 1.1f;
        final float semiLinearBeforeMiddle = (float) Math.pow(linearBeforeMiddle,
                EXPONENT_ALMOST_ONE);
        mColorMatrix.reset();
        mColorMatrix.setSaturation(semiLinearBeforeMiddle);
        mColorMatrix.postConcat(alphaMatrix(
                1 - mWhiteBlendingPathInterpolator.getInterpolation(1 - ratio), Color.WHITE));

        final float colorAlpha;
        if (mPhotoView.isBasedOffLetterTile()) {
            // Since the letter tile only has white and grey, tint it more slowly. Otherwise
            // it will be completely invisible before we reach the intermediate point. The values
            // for TILE_EXPONENT and slowingFactor are chosen to achieve DESIRED_INTERMEDIATE_ALPHA
            // at the intermediate/starting position.
            final float DESIRED_INTERMEDIATE_ALPHA = 0.9f;
            final float TILE_EXPONENT = 1.5f;
            final float slowingFactor = (float) ((1 - intermediateRatio) / intermediateRatio
                    / (1 - Math.pow(1 - DESIRED_INTERMEDIATE_ALPHA, 1/TILE_EXPONENT)));
            float linearBeforeMiddleish = Math.max(1 - (1 - ratio) / intermediateRatio
                    / slowingFactor, 0);
            colorAlpha = 1 - (float) Math.pow(linearBeforeMiddleish, TILE_EXPONENT);
            mColorMatrix.postConcat(alphaMatrix(colorAlpha, mHeaderTintColor));
        } else {
            colorAlpha = 1 - semiLinearBeforeMiddle;
            mColorMatrix.postConcat(multiplyBlendMatrix(mHeaderTintColor, colorAlpha));
        }

        mPhotoView.setColorFilter(new ColorMatrixColorFilter(mColorMatrix));
        // Tell the photo view what tint we are trying to achieve. Depending on the type of
        // drawable used, the photo view may or may not use this tint.
        mPhotoView.setTint(mHeaderTintColor);

        final int gradientAlpha = (int) (255 * linearBeforeMiddle);
        mTitleGradientDrawable.setAlpha(gradientAlpha);
        mActionBarGradientDrawable.setAlpha(gradientAlpha);

        Trace.endSection();
    }

    private float calculateHeightRatio(int height) {
        return (height - mMinimumPortraitHeaderHeight)
                / (float) (mMaximumPortraitHeaderHeight - mMinimumPortraitHeaderHeight);
    }

    /**
     * Simulates alpha blending an image with {@param color}.
     */
    private ColorMatrix alphaMatrix(float alpha, int color) {
        mAlphaMatrixValues[0] = Color.red(color) * alpha / 255;
        mAlphaMatrixValues[6] = Color.green(color) * alpha / 255;
        mAlphaMatrixValues[12] = Color.blue(color) * alpha / 255;
        mAlphaMatrixValues[4] = 255 * (1 - alpha);
        mAlphaMatrixValues[9] = 255 * (1 - alpha);
        mAlphaMatrixValues[14] = 255 * (1 - alpha);
        mWhitenessColorMatrix.set(mAlphaMatrixValues);
        return mWhitenessColorMatrix;
    }

    /**
     * Simulates multiply blending an image with a single {@param color}.
     *
     * Multiply blending is [Sa * Da, Sc * Dc]. See {@link android.graphics.PorterDuff}.
     */
    private ColorMatrix multiplyBlendMatrix(int color, float alpha) {
        mMultiplyBlendMatrixValues[0] = multiplyBlend(Color.red(color), alpha);
        mMultiplyBlendMatrixValues[6] = multiplyBlend(Color.green(color), alpha);
        mMultiplyBlendMatrixValues[12] = multiplyBlend(Color.blue(color), alpha);
        mMultiplyBlendMatrix.set(mMultiplyBlendMatrixValues);
        return mMultiplyBlendMatrix;
    }

    private float multiplyBlend(int color, float alpha) {
        return color * alpha / 255.0f + (1 - alpha);
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

    /**
     * Interpolator that enforces a specific starting velocity. This is useful to avoid a
     * discontinuity between dragging speed and flinging speed.
     *
     * Similar to a {@link android.view.animation.AccelerateInterpolator} in the sense that
     * getInterpolation() is a quadratic function.
     */
    private static class AcceleratingFlingInterpolator implements Interpolator {

        private final float mStartingSpeedPixelsPerFrame;
        private final float mDurationMs;
        private final int mPixelsDelta;
        private final float mNumberFrames;

        public AcceleratingFlingInterpolator(int durationMs, float startingSpeedPixelsPerSecond,
                int pixelsDelta) {
            mStartingSpeedPixelsPerFrame = startingSpeedPixelsPerSecond / getRefreshRate();
            mDurationMs = durationMs;
            mPixelsDelta = pixelsDelta;
            mNumberFrames = mDurationMs / getFrameIntervalMs();
        }

        @Override
        public float getInterpolation(float input) {
            final float animationIntervalNumber = mNumberFrames * input;
            final float linearDelta = (animationIntervalNumber * mStartingSpeedPixelsPerFrame)
                    / mPixelsDelta;
            // Add the results of a linear interpolator (with the initial speed) with the
            // results of a AccelerateInterpolator.
            if (mStartingSpeedPixelsPerFrame > 0) {
                return Math.min(input * input + linearDelta, 1);
            } else {
                // Initial fling was in the wrong direction, make sure that the quadratic component
                // grows faster in order to make up for this.
                return Math.min(input * (input - linearDelta) + linearDelta, 1);
            }
        }

        private float getRefreshRate() {
            DisplayInfo di = DisplayManagerGlobal.getInstance().getDisplayInfo(
                    Display.DEFAULT_DISPLAY);
            return di.refreshRate;
        }

        public long getFrameIntervalMs() {
            return (long)(1000 / getRefreshRate());
        }
    }

    /**
     * Expand the header if the mScrollViewChild is about to shrink by enough to create new empty
     * space at the bottom of this ViewGroup.
     */
    public void prepareForShrinkingScrollChild(int heightDelta) {
        // The Transition framework may suppress layout on the scene root and its children. If
        // mScrollView has its layout suppressed, user scrolling interactions will not display
        // correctly. By turning suppress off for mScrollView, mScrollView properly adjusts its
        // graphics as the user scrolls during the transition.
        mScrollView.suppressLayout(false);

        final int newEmptyScrollViewSpace = -getOverflowingChildViewSize() + heightDelta;
        if (newEmptyScrollViewSpace > 0 && !mIsTwoPanel) {
            final int newDesiredToolbarHeight = Math.min(getToolbarHeight()
                    + newEmptyScrollViewSpace, getMaximumScrollableHeaderHeight());
            ObjectAnimator.ofInt(this, "toolbarHeight", newDesiredToolbarHeight).setDuration(
                    ExpandingEntryCardView.DURATION_COLLAPSE_ANIMATION_CHANGE_BOUNDS).start();
        }
    }

    public void prepareForExpandingScrollChild() {
        // The Transition framework may suppress layout on the scene root and its children. If
        // mScrollView has its layout suppressed, user scrolling interactions will not display
        // correctly. By turning suppress off for mScrollView, mScrollView properly adjusts its
        // graphics as the user scrolls during the transition.
        mScrollView.suppressLayout(false);
    }
}
