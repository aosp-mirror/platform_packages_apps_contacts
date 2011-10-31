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

package com.android.contacts.list;

import com.android.contacts.ContactPresenceIconUtil;
import com.android.contacts.ContactStatusUtil;
import com.android.contacts.R;
import com.android.contacts.format.PrefixHighlighter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.SelectionBoundsAdjuster;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.QuickContactBadge;
import android.widget.TextView;

/**
 * A custom view for an item in the contact list.
 * The view contains the contact's photo, a set of text views (for name, status, etc...) and
 * icons for presence and call.
 * The view uses no XML file for layout and all the measurements and layouts are done
 * in the onMeasure and onLayout methods.
 *
 * The layout puts the contact's photo on the right side of the view, the call icon (if present)
 * to the left of the photo, the text lines are aligned to the left and the presence icon (if
 * present) is set to the left of the status line.
 *
 * The layout also supports a header (used as a header of a group of contacts) that is above the
 * contact's data and a divider between contact view.
 */

public class ContactListItemView extends ViewGroup
        implements SelectionBoundsAdjuster {

    private static final int QUICK_CONTACT_BADGE_STYLE =
            com.android.internal.R.attr.quickContactBadgeStyleWindowMedium;

    protected final Context mContext;

    // Style values for layout and appearance
    private final int mPreferredHeight;
    private final int mVerticalDividerMargin;
    private final int mGapBetweenImageAndText;
    private final int mGapBetweenLabelAndData;
    private final int mCallButtonPadding;
    private final int mPresenceIconMargin;
    private final int mPresenceIconSize;
    private final int mHeaderTextColor;
    private final int mHeaderTextIndent;
    private final int mHeaderTextSize;
    private final int mHeaderUnderlineHeight;
    private final int mHeaderUnderlineColor;
    private final int mCountViewTextSize;
    private final int mContactsCountTextColor;
    private final int mTextIndent;
    private Drawable mActivatedBackgroundDrawable;

    /**
     * Used with {@link #mLabelView}, specifying the width ratio between label and data.
     */
    private final int mLabelViewWidthWeight;
    /**
     * Used with {@link #mDataView}, specifying the width ratio between label and data.
     */
    private final int mDataViewWidthWeight;

    // Will be used with adjustListItemSelectionBounds().
    private int mSelectionBoundsMarginLeft;
    private int mSelectionBoundsMarginRight;

    // Horizontal divider between contact views.
    private boolean mHorizontalDividerVisible = true;
    private Drawable mHorizontalDividerDrawable;
    private int mHorizontalDividerHeight;

    /**
     * Where to put contact photo. This affects the other Views' layout or look-and-feel.
     */
    public enum PhotoPosition {
        LEFT,
        RIGHT
    }
    public static final PhotoPosition DEFAULT_PHOTO_POSITION = PhotoPosition.RIGHT;
    private PhotoPosition mPhotoPosition = DEFAULT_PHOTO_POSITION;

    // Vertical divider between the call icon and the text.
    private boolean mVerticalDividerVisible;
    private Drawable mVerticalDividerDrawable;
    private int mVerticalDividerWidth;

    // Header layout data
    private boolean mHeaderVisible;
    private View mHeaderDivider;
    private int mHeaderBackgroundHeight;
    private TextView mHeaderTextView;

    // The views inside the contact view
    private boolean mQuickContactEnabled = true;
    private QuickContactBadge mQuickContact;
    private ImageView mPhotoView;
    private TextView mNameTextView;
    private TextView mPhoneticNameTextView;
    private DontPressWithParentImageView mCallButton;
    private TextView mLabelView;
    private TextView mDataView;
    private TextView mSnippetView;
    private TextView mStatusView;
    private TextView mCountView;
    private ImageView mPresenceIcon;

    private ColorStateList mSecondaryTextColor;

    private char[] mHighlightedPrefix;

    private int mDefaultPhotoViewSize;
    /**
     * Can be effective even when {@link #mPhotoView} is null, as we want to have horizontal padding
     * to align other data in this View.
     */
    private int mPhotoViewWidth;
    /**
     * Can be effective even when {@link #mPhotoView} is null, as we want to have vertical padding.
     */
    private int mPhotoViewHeight;

    /**
     * Only effective when {@link #mPhotoView} is null.
     * When true all the Views on the right side of the photo should have horizontal padding on
     * those left assuming there is a photo.
     */
    private boolean mKeepHorizontalPaddingForPhotoView;
    /**
     * Only effective when {@link #mPhotoView} is null.
     */
    private boolean mKeepVerticalPaddingForPhotoView;

    /**
     * True when {@link #mPhotoViewWidth} and {@link #mPhotoViewHeight} are ready for being used.
     * False indicates those values should be updated before being used in position calculation.
     */
    private boolean mPhotoViewWidthAndHeightAreReady = false;

    private int mNameTextViewHeight;
    private int mPhoneticNameTextViewHeight;
    private int mLabelViewHeight;
    private int mDataViewHeight;
    private int mSnippetTextViewHeight;
    private int mStatusTextViewHeight;

    // Holds Math.max(mLabelTextViewHeight, mDataViewHeight), assuming Label and Data share the
    // same row.
    private int mLabelAndDataViewMaxHeight;

    private OnClickListener mCallButtonClickListener;
    // TODO: some TextView fields are using CharArrayBuffer while some are not. Determine which is
    // more efficient for each case or in general, and simplify the whole implementation.
    // Note: if we're sure MARQUEE will be used every time, there's no reason to use
    // CharArrayBuffer, since MARQUEE requires Span and thus we need to copy characters inside the
    // buffer to Spannable once, while CharArrayBuffer is for directly applying char array to
    // TextView without any modification.
    private final CharArrayBuffer mDataBuffer = new CharArrayBuffer(128);
    private final CharArrayBuffer mPhoneticNameBuffer = new CharArrayBuffer(128);

    private boolean mActivatedStateSupported;

    private Rect mBoundsWithoutHeader = new Rect();

    /** A helper used to highlight a prefix in a text field. */
    private PrefixHighlighter mPrefixHighligher;
    private CharSequence mUnknownNameText;

    /**
     * Special class to allow the parent to be pressed without being pressed itself.
     * This way the line of a tab can be pressed, but the image itself is not.
     */
    // TODO: understand this
    private static class DontPressWithParentImageView extends ImageView {

        public DontPressWithParentImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void setPressed(boolean pressed) {
            // If the parent is pressed, do not set to pressed.
            if (pressed && ((View) getParent()).isPressed()) {
                return;
            }
            super.setPressed(pressed);
        }
    }

    public ContactListItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        // Read all style values
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ContactListItemView);
        mPreferredHeight = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_height, 0);
        mActivatedBackgroundDrawable = a.getDrawable(
                R.styleable.ContactListItemView_activated_background);
        mHorizontalDividerDrawable = a.getDrawable(
                R.styleable.ContactListItemView_list_item_divider);
        mVerticalDividerMargin = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_vertical_divider_margin, 0);

        mGapBetweenImageAndText = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_gap_between_image_and_text, 0);
        mGapBetweenLabelAndData = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_gap_between_label_and_data, 0);
        mCallButtonPadding = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_call_button_padding, 0);
        mPresenceIconMargin = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_presence_icon_margin, 4);
        mPresenceIconSize = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_presence_icon_size, 16);
        mDefaultPhotoViewSize = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_photo_size, 0);
        mHeaderTextIndent = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_header_text_indent, 0);
        mHeaderTextColor = a.getColor(
                R.styleable.ContactListItemView_list_item_header_text_color, Color.BLACK);
        mHeaderTextSize = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_header_text_size, 12);
        mHeaderBackgroundHeight = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_header_height, 30);
        mHeaderUnderlineHeight = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_header_underline_height, 1);
        mHeaderUnderlineColor = a.getColor(
                R.styleable.ContactListItemView_list_item_header_underline_color, 0);
        mTextIndent = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_text_indent, 0);
        mCountViewTextSize = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_contacts_count_text_size, 12);
        mContactsCountTextColor = a.getColor(
                R.styleable.ContactListItemView_list_item_contacts_count_text_color, Color.BLACK);
        mDataViewWidthWeight = a.getInteger(
                R.styleable.ContactListItemView_list_item_data_width_weight, 5);
        mLabelViewWidthWeight = a.getInteger(
                R.styleable.ContactListItemView_list_item_label_width_weight, 3);

        setPadding(
                a.getDimensionPixelOffset(
                        R.styleable.ContactListItemView_list_item_padding_left, 0),
                a.getDimensionPixelOffset(
                        R.styleable.ContactListItemView_list_item_padding_top, 0),
                a.getDimensionPixelOffset(
                        R.styleable.ContactListItemView_list_item_padding_right, 0),
                a.getDimensionPixelOffset(
                        R.styleable.ContactListItemView_list_item_padding_bottom, 0));

        mPrefixHighligher = new PrefixHighlighter(
                a.getColor(R.styleable.ContactListItemView_list_item_prefix_highlight_color,
                        Color.GREEN));
        a.recycle();

        a = getContext().obtainStyledAttributes(android.R.styleable.Theme);
        mSecondaryTextColor = a.getColorStateList(android.R.styleable.Theme_textColorSecondary);
        a.recycle();

        mHorizontalDividerHeight = mHorizontalDividerDrawable.getIntrinsicHeight();

        if (mActivatedBackgroundDrawable != null) {
            mActivatedBackgroundDrawable.setCallback(this);
        }
    }

    /**
     * Installs a call button listener.
     */
    public void setOnCallButtonClickListener(OnClickListener callButtonClickListener) {
        mCallButtonClickListener = callButtonClickListener;
    }

    public void setUnknownNameText(CharSequence unknownNameText) {
        mUnknownNameText = unknownNameText;
    }

    public void setQuickContactEnabled(boolean flag) {
        mQuickContactEnabled = flag;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We will match parent's width and wrap content vertically, but make sure
        // height is no less than listPreferredItemHeight.
        final int specWidth = resolveSize(0, widthMeasureSpec);
        final int preferredHeight;
        if (mHorizontalDividerVisible) {
            preferredHeight = mPreferredHeight + mHorizontalDividerHeight;
        } else {
            preferredHeight = mPreferredHeight;
        }

        mNameTextViewHeight = 0;
        mPhoneticNameTextViewHeight = 0;
        mLabelViewHeight = 0;
        mDataViewHeight = 0;
        mLabelAndDataViewMaxHeight = 0;
        mSnippetTextViewHeight = 0;
        mStatusTextViewHeight = 0;

        ensurePhotoViewSize();

        // Width each TextView is able to use.
        final int effectiveWidth;
        // All the other Views will honor the photo, so available width for them may be shrunk.
        if (mPhotoViewWidth > 0 || mKeepHorizontalPaddingForPhotoView) {
            effectiveWidth = specWidth - getPaddingLeft() - getPaddingRight()
                    - (mPhotoViewWidth + mGapBetweenImageAndText);
        } else {
            effectiveWidth = specWidth - getPaddingLeft() - getPaddingRight();
        }

        // Go over all visible text views and measure actual width of each of them.
        // Also calculate their heights to get the total height for this entire view.

        if (isVisible(mNameTextView)) {
            mNameTextView.measure(
                    MeasureSpec.makeMeasureSpec(effectiveWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mNameTextViewHeight = mNameTextView.getMeasuredHeight();
        }

        if (isVisible(mPhoneticNameTextView)) {
            mPhoneticNameTextView.measure(
                    MeasureSpec.makeMeasureSpec(effectiveWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mPhoneticNameTextViewHeight = mPhoneticNameTextView.getMeasuredHeight();
        }

        // If both data (phone number/email address) and label (type like "MOBILE") are quite long,
        // we should ellipsize both using appropriate ratio.
        final int dataWidth;
        final int labelWidth;
        if (isVisible(mDataView)) {
            if (isVisible(mLabelView)) {
                final int totalWidth = effectiveWidth - mGapBetweenLabelAndData;
                dataWidth = ((totalWidth * mDataViewWidthWeight)
                        / (mDataViewWidthWeight + mLabelViewWidthWeight));
                labelWidth = ((totalWidth * mLabelViewWidthWeight) /
                        (mDataViewWidthWeight + mLabelViewWidthWeight));
            } else {
                dataWidth = effectiveWidth;
                labelWidth = 0;
            }
        } else {
            dataWidth = 0;
            if (isVisible(mLabelView)) {
                labelWidth = effectiveWidth;
            } else {
                labelWidth = 0;
            }
        }

        if (isVisible(mDataView)) {
            mDataView.measure(MeasureSpec.makeMeasureSpec(dataWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mDataViewHeight = mDataView.getMeasuredHeight();
        }

        if (isVisible(mLabelView)) {
            // For performance reason we don't want AT_MOST usually, but when the picture is
            // on right, we need to use it anyway because mDataView is next to mLabelView.
            final int mode = (mPhotoPosition == PhotoPosition.LEFT
                    ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
            mLabelView.measure(MeasureSpec.makeMeasureSpec(labelWidth, mode),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mLabelViewHeight = mLabelView.getMeasuredHeight();
        }
        mLabelAndDataViewMaxHeight = Math.max(mLabelViewHeight, mDataViewHeight);

        if (isVisible(mSnippetView)) {
            mSnippetView.measure(
                    MeasureSpec.makeMeasureSpec(effectiveWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mSnippetTextViewHeight = mSnippetView.getMeasuredHeight();
        }

        // Status view height is the biggest of the text view and the presence icon
        if (isVisible(mPresenceIcon)) {
            mPresenceIcon.measure(mPresenceIconSize, mPresenceIconSize);
            mStatusTextViewHeight = mPresenceIcon.getMeasuredHeight();
        }

        if (isVisible(mStatusView)) {
            // Presence and status are in a same row, so status will be affected by icon size.
            final int statusWidth;
            if (isVisible(mPresenceIcon)) {
                statusWidth = (effectiveWidth - mPresenceIcon.getMeasuredWidth()
                        - mPresenceIconMargin);
            } else {
                statusWidth = effectiveWidth;
            }
            mStatusView.measure(MeasureSpec.makeMeasureSpec(statusWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mStatusTextViewHeight =
                    Math.max(mStatusTextViewHeight, mStatusView.getMeasuredHeight());
        }

        // Calculate height including padding.
        int height = (mNameTextViewHeight + mPhoneticNameTextViewHeight +
                mLabelAndDataViewMaxHeight +
                mSnippetTextViewHeight + mStatusTextViewHeight);

        if (isVisible(mCallButton)) {
            mCallButton.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }

        // Make sure the height is at least as high as the photo
        height = Math.max(height, mPhotoViewHeight + getPaddingBottom() + getPaddingTop());

        // Add horizontal divider height
        if (mHorizontalDividerVisible) {
            height += mHorizontalDividerHeight;
        }

        // Make sure height is at least the preferred height
        height = Math.max(height, preferredHeight);

        // Add the height of the header if visible
        if (mHeaderVisible) {
            mHeaderTextView.measure(
                    MeasureSpec.makeMeasureSpec(specWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mHeaderBackgroundHeight, MeasureSpec.EXACTLY));
            if (mCountView != null) {
                mCountView.measure(
                        MeasureSpec.makeMeasureSpec(specWidth, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(mHeaderBackgroundHeight, MeasureSpec.EXACTLY));
            }
            mHeaderBackgroundHeight = Math.max(mHeaderBackgroundHeight,
                    mHeaderTextView.getMeasuredHeight());
            height += (mHeaderBackgroundHeight + mHeaderUnderlineHeight);
        }

        setMeasuredDimension(specWidth, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int height = bottom - top;
        final int width = right - left;

        // Determine the vertical bounds by laying out the header first.
        int topBound = 0;
        int bottomBound = height;
        int leftBound = getPaddingLeft();
        int rightBound = width - getPaddingRight();

        // Put the header in the top of the contact view (Text + underline view)
        if (mHeaderVisible) {
            mHeaderTextView.layout(leftBound + mHeaderTextIndent,
                    0,
                    rightBound,
                    mHeaderBackgroundHeight);
            if (mCountView != null) {
                mCountView.layout(rightBound - mCountView.getMeasuredWidth(),
                        0,
                        rightBound,
                        mHeaderBackgroundHeight);
            }
            mHeaderDivider.layout(leftBound,
                    mHeaderBackgroundHeight,
                    rightBound,
                    mHeaderBackgroundHeight + mHeaderUnderlineHeight);
            topBound += (mHeaderBackgroundHeight + mHeaderUnderlineHeight);
        }

        // Put horizontal divider at the bottom
        if (mHorizontalDividerVisible) {
            mHorizontalDividerDrawable.setBounds(
                    leftBound,
                    height - mHorizontalDividerHeight,
                    rightBound,
                    height);
            bottomBound -= mHorizontalDividerHeight;
        }

        mBoundsWithoutHeader.set(0, topBound, width, bottomBound);

        if (mActivatedStateSupported && isActivated()) {
            mActivatedBackgroundDrawable.setBounds(mBoundsWithoutHeader);
        }

        final View photoView = mQuickContact != null ? mQuickContact : mPhotoView;
        if (mPhotoPosition == PhotoPosition.LEFT) {
            // Photo is the left most view. All the other Views should on the right of the photo.
            if (photoView != null) {
                // Center the photo vertically
                final int photoTop = topBound + (bottomBound - topBound - mPhotoViewHeight) / 2;
                photoView.layout(
                        leftBound,
                        photoTop,
                        leftBound + mPhotoViewWidth,
                        photoTop + mPhotoViewHeight);
                leftBound += mPhotoViewWidth + mGapBetweenImageAndText;
            } else if (mKeepHorizontalPaddingForPhotoView) {
                // Draw nothing but keep the padding.
                leftBound += mPhotoViewWidth + mGapBetweenImageAndText;
            }
        } else {
            // Photo is the right most view. Right bound should be adjusted that way.
            if (photoView != null) {
                // Center the photo vertically
                final int photoTop = topBound + (bottomBound - topBound - mPhotoViewHeight) / 2;
                photoView.layout(
                        rightBound - mPhotoViewWidth,
                        photoTop,
                        rightBound,
                        photoTop + mPhotoViewHeight);
                rightBound -= (mPhotoViewWidth + mGapBetweenImageAndText);
            }

            // Add indent between left-most padding and texts.
            leftBound += mTextIndent;
        }

        // Layout the call button.
        rightBound = layoutRightSide(height, topBound, bottomBound, rightBound);

        // Center text vertically
        final int totalTextHeight = mNameTextViewHeight + mPhoneticNameTextViewHeight +
                mLabelAndDataViewMaxHeight + mSnippetTextViewHeight + mStatusTextViewHeight;
        int textTopBound = (bottomBound + topBound - totalTextHeight) / 2;

        // Layout all text view and presence icon
        // Put name TextView first
        if (isVisible(mNameTextView)) {
            mNameTextView.layout(leftBound,
                    textTopBound,
                    rightBound,
                    textTopBound + mNameTextViewHeight);
            textTopBound += mNameTextViewHeight;
        }

        // Presence and status
        int statusLeftBound = leftBound;
        if (isVisible(mPresenceIcon)) {
            int iconWidth = mPresenceIcon.getMeasuredWidth();
            mPresenceIcon.layout(
                    leftBound,
                    textTopBound,
                    leftBound + iconWidth,
                    textTopBound + mStatusTextViewHeight);
            statusLeftBound += (iconWidth + mPresenceIconMargin);
        }

        if (isVisible(mStatusView)) {
            mStatusView.layout(statusLeftBound,
                    textTopBound,
                    rightBound,
                    textTopBound + mStatusTextViewHeight);
        }

        if (isVisible(mStatusView) || isVisible(mPresenceIcon)) {
            textTopBound += mStatusTextViewHeight;
        }

        // Rest of text views
        int dataLeftBound = leftBound;
        if (isVisible(mPhoneticNameTextView)) {
            mPhoneticNameTextView.layout(leftBound,
                    textTopBound,
                    rightBound,
                    textTopBound + mPhoneticNameTextViewHeight);
            textTopBound += mPhoneticNameTextViewHeight;
        }

        // Label and Data align bottom.
        if (isVisible(mLabelView)) {
            if (mPhotoPosition == PhotoPosition.LEFT) {
                // When photo is on left, label is placed on the right edge of the list item.
                mLabelView.layout(rightBound - mLabelView.getMeasuredWidth(),
                        textTopBound + mLabelAndDataViewMaxHeight - mLabelViewHeight,
                        rightBound,
                        textTopBound + mLabelAndDataViewMaxHeight);
                rightBound -= mLabelView.getMeasuredWidth();
            } else {
                // When photo is on right, label is placed on the left of data view.
                dataLeftBound = leftBound + mLabelView.getMeasuredWidth();
                mLabelView.layout(leftBound,
                        textTopBound + mLabelAndDataViewMaxHeight - mLabelViewHeight,
                        dataLeftBound,
                        textTopBound + mLabelAndDataViewMaxHeight);
                dataLeftBound += mGapBetweenLabelAndData;
            }
        }

        if (isVisible(mDataView)) {
            mDataView.layout(dataLeftBound,
                    textTopBound + mLabelAndDataViewMaxHeight - mDataViewHeight,
                    rightBound,
                    textTopBound + mLabelAndDataViewMaxHeight);
        }
        if (isVisible(mLabelView) || isVisible(mDataView)) {
            textTopBound += mLabelAndDataViewMaxHeight;
        }

        if (isVisible(mSnippetView)) {
            mSnippetView.layout(leftBound,
                    textTopBound,
                    rightBound,
                    textTopBound + mSnippetTextViewHeight);
        }
    }

    /**
     * Performs layout of the right side of the view
     *
     * @return new right boundary
     */
    protected int layoutRightSide(int height, int topBound, int bottomBound, int rightBound) {
        // Put call button and vertical divider
        if (isVisible(mCallButton)) {
            int buttonWidth = mCallButton.getMeasuredWidth();
            rightBound -= buttonWidth;
            mCallButton.layout(
                    rightBound,
                    topBound,
                    rightBound + buttonWidth,
                    height - mHorizontalDividerHeight);
            mVerticalDividerVisible = true;
            ensureVerticalDivider();
            rightBound -= mVerticalDividerWidth;
            mVerticalDividerDrawable.setBounds(
                    rightBound,
                    topBound + mVerticalDividerMargin,
                    rightBound + mVerticalDividerWidth,
                    height - mVerticalDividerMargin);
        } else {
            mVerticalDividerVisible = false;
        }

        return rightBound;
    }

    @Override
    public void adjustListItemSelectionBounds(Rect bounds) {
        bounds.top += mBoundsWithoutHeader.top;
        bounds.bottom = bounds.top + mBoundsWithoutHeader.height();
        bounds.left += mSelectionBoundsMarginLeft;
        bounds.right -= mSelectionBoundsMarginRight;
    }

    protected boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    /**
     * Loads the drawable for the vertical divider if it has not yet been loaded.
     */
    private void ensureVerticalDivider() {
        if (mVerticalDividerDrawable == null) {
            mVerticalDividerDrawable = mContext.getResources().getDrawable(
                    R.drawable.divider_vertical_dark);
            mVerticalDividerWidth = mVerticalDividerDrawable.getIntrinsicWidth();
        }
    }

    /**
     * Extracts width and height from the style
     */
    private void ensurePhotoViewSize() {
        if (!mPhotoViewWidthAndHeightAreReady) {
            if (mQuickContactEnabled) {
                TypedArray a = mContext.obtainStyledAttributes(null,
                        com.android.internal.R.styleable.ViewGroup_Layout,
                        QUICK_CONTACT_BADGE_STYLE, 0);
                mPhotoViewWidth = a.getLayoutDimension(
                        android.R.styleable.ViewGroup_Layout_layout_width,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                mPhotoViewHeight = a.getLayoutDimension(
                        android.R.styleable.ViewGroup_Layout_layout_height,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                a.recycle();
            } else if (mPhotoView != null) {
                mPhotoViewWidth = mPhotoViewHeight = getDefaultPhotoViewSize();
            } else {
                final int defaultPhotoViewSize = getDefaultPhotoViewSize();
                mPhotoViewWidth = mKeepHorizontalPaddingForPhotoView ? defaultPhotoViewSize : 0;
                mPhotoViewHeight = mKeepVerticalPaddingForPhotoView ? defaultPhotoViewSize : 0;
            }

            mPhotoViewWidthAndHeightAreReady = true;
        }
    }

    protected void setDefaultPhotoViewSize(int pixels) {
        mDefaultPhotoViewSize = pixels;
    }

    protected int getDefaultPhotoViewSize() {
        return mDefaultPhotoViewSize;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mActivatedStateSupported) {
            mActivatedBackgroundDrawable.setState(getDrawableState());
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mActivatedBackgroundDrawable || super.verifyDrawable(who);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mActivatedStateSupported) {
            mActivatedBackgroundDrawable.jumpToCurrentState();
        }
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        if (mActivatedStateSupported && isActivated()) {
            mActivatedBackgroundDrawable.draw(canvas);
        }
        if (mHorizontalDividerVisible) {
            mHorizontalDividerDrawable.draw(canvas);
        }
        if (mVerticalDividerVisible) {
            mVerticalDividerDrawable.draw(canvas);
        }

        super.dispatchDraw(canvas);
    }

    /**
     * Sets the flag that determines whether a divider should drawn at the bottom
     * of the view.
     */
    public void setDividerVisible(boolean visible) {
        mHorizontalDividerVisible = visible;
    }

    /**
     * Sets section header or makes it invisible if the title is null.
     */
    public void setSectionHeader(String title) {
        if (!TextUtils.isEmpty(title)) {
            if (mHeaderTextView == null) {
                mHeaderTextView = new TextView(mContext);
                mHeaderTextView.setTextColor(mHeaderTextColor);
                mHeaderTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mHeaderTextSize);
                mHeaderTextView.setTypeface(mHeaderTextView.getTypeface(), Typeface.BOLD);
                mHeaderTextView.setGravity(Gravity.CENTER_VERTICAL);
                addView(mHeaderTextView);
            }
            if (mHeaderDivider == null) {
                mHeaderDivider = new View(mContext);
                mHeaderDivider.setBackgroundColor(mHeaderUnderlineColor);
                addView(mHeaderDivider);
            }
            setMarqueeText(mHeaderTextView, title);
            mHeaderTextView.setVisibility(View.VISIBLE);
            mHeaderDivider.setVisibility(View.VISIBLE);
            mHeaderTextView.setAllCaps(true);
            mHeaderVisible = true;
        } else {
            if (mHeaderTextView != null) {
                mHeaderTextView.setVisibility(View.GONE);
            }
            if (mHeaderDivider != null) {
                mHeaderDivider.setVisibility(View.GONE);
            }
            mHeaderVisible = false;
        }
    }

    /**
     * Returns the quick contact badge, creating it if necessary.
     */
    public QuickContactBadge getQuickContact() {
        if (!mQuickContactEnabled) {
            throw new IllegalStateException("QuickContact is disabled for this view");
        }
        if (mQuickContact == null) {
            mQuickContact = new QuickContactBadge(mContext, null, QUICK_CONTACT_BADGE_STYLE);
            if (mNameTextView != null) {
                mQuickContact.setContentDescription(mContext.getString(
                        R.string.description_quick_contact_for, mNameTextView.getText()));
            }

            addView(mQuickContact);
            mPhotoViewWidthAndHeightAreReady = false;
        }
        return mQuickContact;
    }

    /**
     * Returns the photo view, creating it if necessary.
     */
    public ImageView getPhotoView() {
        if (mPhotoView == null) {
            if (mQuickContactEnabled) {
                mPhotoView = new ImageView(mContext, null, QUICK_CONTACT_BADGE_STYLE);
            } else {
                mPhotoView = new ImageView(mContext);
            }
            // Quick contact style used above will set a background - remove it
            mPhotoView.setBackgroundDrawable(null);
            addView(mPhotoView);
            mPhotoViewWidthAndHeightAreReady = false;
        }
        return mPhotoView;
    }

    /**
     * Removes the photo view.
     */
    public void removePhotoView() {
        removePhotoView(false, true);
    }

    /**
     * Removes the photo view.
     *
     * @param keepHorizontalPadding True means data on the right side will have
     *            padding on left, pretending there is still a photo view.
     * @param keepVerticalPadding True means the View will have some height
     *            enough for accommodating a photo view.
     */
    public void removePhotoView(boolean keepHorizontalPadding, boolean keepVerticalPadding) {
        mPhotoViewWidthAndHeightAreReady = false;
        mKeepHorizontalPaddingForPhotoView = keepHorizontalPadding;
        mKeepVerticalPaddingForPhotoView = keepVerticalPadding;
        if (mPhotoView != null) {
            removeView(mPhotoView);
            mPhotoView = null;
        }
        if (mQuickContact != null) {
            removeView(mQuickContact);
            mQuickContact = null;
        }
    }

    /**
     * Sets a word prefix that will be highlighted if encountered in fields like
     * name and search snippet.
     * <p>
     * NOTE: must be all upper-case
     */
    public void setHighlightedPrefix(char[] upperCasePrefix) {
        mHighlightedPrefix = upperCasePrefix;
    }

    /**
     * Returns the text view for the contact name, creating it if necessary.
     */
    public TextView getNameTextView() {
        if (mNameTextView == null) {
            mNameTextView = new TextView(mContext);
            mNameTextView.setSingleLine(true);
            mNameTextView.setEllipsize(getTextEllipsis());
            mNameTextView.setTextAppearance(mContext, android.R.style.TextAppearance_Medium);
            // Manually call setActivated() since this view may be added after the first
            // setActivated() call toward this whole item view.
            mNameTextView.setActivated(isActivated());
            mNameTextView.setGravity(Gravity.CENTER_VERTICAL);
            addView(mNameTextView);
        }
        return mNameTextView;
    }

    /**
     * Adds a call button using the supplied arguments as an id and tag.
     */
    public void showCallButton(int id, int tag) {
        if (mCallButton == null) {
            mCallButton = new DontPressWithParentImageView(mContext, null);
            mCallButton.setId(id);
            mCallButton.setOnClickListener(mCallButtonClickListener);
            mCallButton.setBackgroundResource(R.drawable.call_background);
            mCallButton.setImageResource(android.R.drawable.sym_action_call);
            mCallButton.setPadding(mCallButtonPadding, 0, mCallButtonPadding, 0);
            mCallButton.setScaleType(ScaleType.CENTER);
            addView(mCallButton);
        }

        mCallButton.setTag(tag);
        mCallButton.setVisibility(View.VISIBLE);
    }

    public void hideCallButton() {
        if (mCallButton != null) {
            mCallButton.setVisibility(View.GONE);
        }
    }

    /**
     * Adds or updates a text view for the phonetic name.
     */
    public void setPhoneticName(char[] text, int size) {
        if (text == null || size == 0) {
            if (mPhoneticNameTextView != null) {
                mPhoneticNameTextView.setVisibility(View.GONE);
            }
        } else {
            getPhoneticNameTextView();
            setMarqueeText(mPhoneticNameTextView, text, size);
            mPhoneticNameTextView.setVisibility(VISIBLE);
        }
    }

    /**
     * Returns the text view for the phonetic name, creating it if necessary.
     */
    public TextView getPhoneticNameTextView() {
        if (mPhoneticNameTextView == null) {
            mPhoneticNameTextView = new TextView(mContext);
            mPhoneticNameTextView.setSingleLine(true);
            mPhoneticNameTextView.setEllipsize(getTextEllipsis());
            mPhoneticNameTextView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
            mPhoneticNameTextView.setTypeface(mPhoneticNameTextView.getTypeface(), Typeface.BOLD);
            mPhoneticNameTextView.setActivated(isActivated());
            addView(mPhoneticNameTextView);
        }
        return mPhoneticNameTextView;
    }

    /**
     * Adds or updates a text view for the data label.
     */
    public void setLabel(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            if (mLabelView != null) {
                mLabelView.setVisibility(View.GONE);
            }
        } else {
            getLabelView();
            setMarqueeText(mLabelView, text);
            mLabelView.setVisibility(VISIBLE);
        }
    }

    /**
     * Returns the text view for the data label, creating it if necessary.
     */
    public TextView getLabelView() {
        if (mLabelView == null) {
            mLabelView = new TextView(mContext);
            mLabelView.setSingleLine(true);
            mLabelView.setEllipsize(getTextEllipsis());
            mLabelView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
            if (mPhotoPosition == PhotoPosition.LEFT) {
                mLabelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mCountViewTextSize);
                mLabelView.setAllCaps(true);
                mLabelView.setGravity(Gravity.RIGHT);
            } else {
                mLabelView.setTypeface(mLabelView.getTypeface(), Typeface.BOLD);
            }
            mLabelView.setActivated(isActivated());
            addView(mLabelView);
        }
        return mLabelView;
    }

    /**
     * Adds or updates a text view for the data element.
     */
    public void setData(char[] text, int size) {
        if (text == null || size == 0) {
            if (mDataView != null) {
                mDataView.setVisibility(View.GONE);
            }
        } else {
            getDataView();
            setMarqueeText(mDataView, text, size);
            mDataView.setVisibility(VISIBLE);
        }
    }

    private void setMarqueeText(TextView textView, char[] text, int size) {
        if (getTextEllipsis() == TruncateAt.MARQUEE) {
            setMarqueeText(textView, new String(text, 0, size));
        } else {
            textView.setText(text, 0, size);
        }
    }

    private void setMarqueeText(TextView textView, CharSequence text) {
        if (getTextEllipsis() == TruncateAt.MARQUEE) {
            // To show MARQUEE correctly (with END effect during non-active state), we need
            // to build Spanned with MARQUEE in addition to TextView's ellipsize setting.
            final SpannableString spannable = new SpannableString(text);
            spannable.setSpan(TruncateAt.MARQUEE, 0, spannable.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(spannable);
        } else {
            textView.setText(text);
        }
    }

    /**
     * Returns the text view for the data text, creating it if necessary.
     */
    public TextView getDataView() {
        if (mDataView == null) {
            mDataView = new TextView(mContext);
            mDataView.setSingleLine(true);
            mDataView.setEllipsize(getTextEllipsis());
            mDataView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
            mDataView.setActivated(isActivated());
            addView(mDataView);
        }
        return mDataView;
    }

    /**
     * Adds or updates a text view for the search snippet.
     */
    public void setSnippet(String text) {
        if (TextUtils.isEmpty(text)) {
            if (mSnippetView != null) {
                mSnippetView.setVisibility(View.GONE);
            }
        } else {
            mPrefixHighligher.setText(getSnippetView(), text, mHighlightedPrefix);
            mSnippetView.setVisibility(VISIBLE);
        }
    }

    /**
     * Returns the text view for the search snippet, creating it if necessary.
     */
    public TextView getSnippetView() {
        if (mSnippetView == null) {
            mSnippetView = new TextView(mContext);
            mSnippetView.setSingleLine(true);
            mSnippetView.setEllipsize(getTextEllipsis());
            mSnippetView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
            mSnippetView.setTypeface(mSnippetView.getTypeface(), Typeface.BOLD);
            mSnippetView.setActivated(isActivated());
            addView(mSnippetView);
        }
        return mSnippetView;
    }

    /**
     * Returns the text view for the status, creating it if necessary.
     */
    public TextView getStatusView() {
        if (mStatusView == null) {
            mStatusView = new TextView(mContext);
            mStatusView.setSingleLine(true);
            mStatusView.setEllipsize(getTextEllipsis());
            mStatusView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
            mStatusView.setTextColor(mSecondaryTextColor);
            mStatusView.setActivated(isActivated());
            addView(mStatusView);
        }
        return mStatusView;
    }

    /**
     * Returns the text view for the contacts count, creating it if necessary.
     */
    public TextView getCountView() {
        if (mCountView == null) {
            mCountView = new TextView(mContext);
            mCountView.setSingleLine(true);
            mCountView.setEllipsize(getTextEllipsis());
            mCountView.setTextAppearance(mContext, android.R.style.TextAppearance_Medium);
            mCountView.setTextColor(R.color.contact_count_text_color);
            addView(mCountView);
        }
        return mCountView;
    }

    /**
     * Adds or updates a text view for the contacts count.
     */
    public void setCountView(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            if (mCountView != null) {
                mCountView.setVisibility(View.GONE);
            }
        } else {
            getCountView();
            setMarqueeText(mCountView, text);
            mCountView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mCountViewTextSize);
            mCountView.setGravity(Gravity.CENTER_VERTICAL);
            mCountView.setTextColor(mContactsCountTextColor);
            mCountView.setVisibility(VISIBLE);
        }
    }

    /**
     * Adds or updates a text view for the status.
     */
    public void setStatus(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            if (mStatusView != null) {
                mStatusView.setVisibility(View.GONE);
            }
        } else {
            getStatusView();
            setMarqueeText(mStatusView, text);
            mStatusView.setVisibility(VISIBLE);
        }
    }

    /**
     * Adds or updates the presence icon view.
     */
    public void setPresence(Drawable icon) {
        if (icon != null) {
            if (mPresenceIcon == null) {
                mPresenceIcon = new ImageView(mContext);
                addView(mPresenceIcon);
            }
            mPresenceIcon.setImageDrawable(icon);
            mPresenceIcon.setScaleType(ScaleType.CENTER);
            mPresenceIcon.setVisibility(View.VISIBLE);
        } else {
            if (mPresenceIcon != null) {
                mPresenceIcon.setVisibility(View.GONE);
            }
        }
    }

    private TruncateAt getTextEllipsis() {
        return TruncateAt.MARQUEE;
    }

    public void showDisplayName(Cursor cursor, int nameColumnIndex, int displayOrder) {
        CharSequence name = cursor.getString(nameColumnIndex);
        if (!TextUtils.isEmpty(name)) {
            name = mPrefixHighligher.apply(name, mHighlightedPrefix);
        } else {
            name = mUnknownNameText;
        }
        setMarqueeText(getNameTextView(), name);

        // Since the quick contact content description is derived from the display name and there is
        // no guarantee that when the quick contact is initialized the display name is already set,
        // do it here too.
        if (mQuickContact != null) {
            mQuickContact.setContentDescription(mContext.getString(
                    R.string.description_quick_contact_for, mNameTextView.getText()));
        }
    }

    public void hideDisplayName() {
        if (mNameTextView != null) {
            removeView(mNameTextView);
            mNameTextView = null;
        }
    }

    public void showPhoneticName(Cursor cursor, int phoneticNameColumnIndex) {
        cursor.copyStringToBuffer(phoneticNameColumnIndex, mPhoneticNameBuffer);
        int phoneticNameSize = mPhoneticNameBuffer.sizeCopied;
        if (phoneticNameSize != 0) {
            setPhoneticName(mPhoneticNameBuffer.data, phoneticNameSize);
        } else {
            setPhoneticName(null, 0);
        }
    }

    public void hidePhoneticName() {
        if (mPhoneticNameTextView != null) {
            removeView(mPhoneticNameTextView);
            mPhoneticNameTextView = null;
        }
    }

    /**
     * Sets the proper icon (star or presence or nothing) and/or status message.
     */
    public void showPresenceAndStatusMessage(Cursor cursor, int presenceColumnIndex,
            int contactStatusColumnIndex) {
        Drawable icon = null;
        int presence = 0;
        if (!cursor.isNull(presenceColumnIndex)) {
            presence = cursor.getInt(presenceColumnIndex);
            icon = ContactPresenceIconUtil.getPresenceIcon(getContext(), presence);
        }
        setPresence(icon);

        String statusMessage = null;
        if (contactStatusColumnIndex != 0 && !cursor.isNull(contactStatusColumnIndex)) {
            statusMessage = cursor.getString(contactStatusColumnIndex);
        }
        // If there is no status message from the contact, but there was a presence value, then use
        // the default status message string
        if (statusMessage == null && presence != 0) {
            statusMessage = ContactStatusUtil.getStatusString(getContext(), presence);
        }
        setStatus(statusMessage);
    }

    /**
     * Shows search snippet.
     */
    public void showSnippet(Cursor cursor, int summarySnippetColumnIndex) {
        if (cursor.getColumnCount() <= summarySnippetColumnIndex) {
            setSnippet(null);
            return;
        }
        String snippet;
        String columnContent = cursor.getString(summarySnippetColumnIndex);

        // Do client side snippeting if provider didn't do it
        Bundle extras = cursor.getExtras();
        if (extras.getBoolean(ContactsContract.DEFERRED_SNIPPETING)) {
            int displayNameIndex = cursor.getColumnIndex(Contacts.DISPLAY_NAME);

            snippet = ContactsContract.snippetize(columnContent,
                    displayNameIndex < 0 ? null : cursor.getString(displayNameIndex),
                            extras.getString(ContactsContract.DEFERRED_SNIPPETING_QUERY),
                            DefaultContactListAdapter.SNIPPET_START_MATCH,
                            DefaultContactListAdapter.SNIPPET_END_MATCH,
                            DefaultContactListAdapter.SNIPPET_ELLIPSIS,
                            DefaultContactListAdapter.SNIPPET_MAX_TOKENS);
        } else {
            snippet = columnContent;
        }

        if (snippet != null) {
            int from = 0;
            int to = snippet.length();
            int start = snippet.indexOf(DefaultContactListAdapter.SNIPPET_START_MATCH);
            if (start == -1) {
                snippet = null;
            } else {
                int firstNl = snippet.lastIndexOf('\n', start);
                if (firstNl != -1) {
                    from = firstNl + 1;
                }
                int end = snippet.lastIndexOf(DefaultContactListAdapter.SNIPPET_END_MATCH);
                if (end != -1) {
                    int lastNl = snippet.indexOf('\n', end);
                    if (lastNl != -1) {
                        to = lastNl;
                    }
                }

                StringBuilder sb = new StringBuilder();
                for (int i = from; i < to; i++) {
                    char c = snippet.charAt(i);
                    if (c != DefaultContactListAdapter.SNIPPET_START_MATCH &&
                            c != DefaultContactListAdapter.SNIPPET_END_MATCH) {
                        sb.append(c);
                    }
                }
                snippet = sb.toString();
            }
        }
        setSnippet(snippet);
    }

    /**
     * Shows data element (e.g. phone number).
     */
    public void showData(Cursor cursor, int dataColumnIndex) {
        cursor.copyStringToBuffer(dataColumnIndex, mDataBuffer);
        setData(mDataBuffer.data, mDataBuffer.sizeCopied);
    }

    public void setActivatedStateSupported(boolean flag) {
        this.mActivatedStateSupported = flag;
    }

    @Override
    public void requestLayout() {
        // We will assume that once measured this will not need to resize
        // itself, so there is no need to pass the layout request to the parent
        // view (ListView).
        forceLayout();
    }

    public void setPhotoPosition(PhotoPosition photoPosition) {
        mPhotoPosition = photoPosition;
    }

    public PhotoPosition getPhotoPosition() {
        return mPhotoPosition;
    }

    /**
     * Specifies left and right margin for selection bounds. See also
     * {@link #adjustListItemSelectionBounds(Rect)}.
     */
    public void setSelectionBoundsHorizontalMargin(int left, int right) {
        mSelectionBoundsMarginLeft = left;
        mSelectionBoundsMarginRight = right;
    }
}
