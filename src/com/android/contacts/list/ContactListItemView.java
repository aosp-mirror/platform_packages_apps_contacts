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
import com.android.contacts.R;
import com.android.contacts.widget.TextWithHighlighting;
import com.android.contacts.widget.TextWithHighlightingFactory;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.Contacts;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
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
 */
public class ContactListItemView extends ViewGroup
        implements SelectionBoundsAdjuster
{

    private static final int QUICK_CONTACT_BADGE_STYLE =
            com.android.internal.R.attr.quickContactBadgeStyleWindowMedium;

    protected final Context mContext;

    private final int mPreferredHeight;
    private final int mVerticalDividerMargin;
    private final int mPaddingTop;
    private final int mPaddingRight;
    private final int mPaddingBottom;
    private final int mPaddingLeft;
    private final int mGapBetweenImageAndText;
    private final int mGapBetweenLabelAndData;
    private final int mCallButtonPadding;
    private final int mPresenceIconMargin;
    private final int mPrefixHightlightColor;
    private final int mHeaderTextColor;
    private final int mHeaderTextIndent;
    private final int mHeaderTextSize;

    private Drawable mActivatedBackgroundDrawable;

    private boolean mHorizontalDividerVisible = true;
    private Drawable mHorizontalDividerDrawable;
    private int mHorizontalDividerHeight;

    private boolean mVerticalDividerVisible;
    private Drawable mVerticalDividerDrawable;
    private int mVerticalDividerWidth;

    private boolean mHeaderVisible;
    private Drawable mHeaderBackgroundDrawable;
    private int mHeaderBackgroundHeight;
    private TextView mHeaderTextView;

    private boolean mQuickContactEnabled = true;
    private QuickContactBadge mQuickContact;
    private ImageView mPhotoView;
    private TextView mNameTextView;
    private TextView mPhoneticNameTextView;
    private DontPressWithParentImageView mCallButton;
    private TextView mLabelView;
    private TextView mDataView;
    private TextView mSnippetView;
    private ImageView mPresenceIcon;

    private char[] mHighlightedPrefix;

    private int mDefaultPhotoViewSize;
    private int mPhotoViewWidth;
    private int mPhotoViewHeight;
    private int mLine1Height;
    private int mLine2Height;
    private int mLine3Height;
    private int mLine4Height;

    private OnClickListener mCallButtonClickListener;
    private TextWithHighlightingFactory mTextWithHighlightingFactory;
    private CharArrayBuffer mNameBuffer = new CharArrayBuffer(128);
    private CharArrayBuffer mDataBuffer = new CharArrayBuffer(128);
    private CharArrayBuffer mHighlightedTextBuffer = new CharArrayBuffer(128);
    private TextWithHighlighting mTextWithHighlighting;
    private CharArrayBuffer mPhoneticNameBuffer = new CharArrayBuffer(128);

    private CharSequence mUnknownNameText;

    private boolean mActivatedStateSupported;

    private ForegroundColorSpan mPrefixColorSpan;

    private Rect mBoundsWithoutHeader = new Rect();

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

        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ContactListItemView);
        mPreferredHeight = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_height, 0);
        mActivatedBackgroundDrawable = a.getDrawable(
                R.styleable.ContactListItemView_activated_background);
        mHeaderBackgroundDrawable = a.getDrawable(
                R.styleable.ContactListItemView_section_header_background);
        mHorizontalDividerDrawable = a.getDrawable(
                R.styleable.ContactListItemView_list_item_divider);
        mVerticalDividerMargin = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_vertical_divider_margin, 0);
        mPaddingTop = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_top, 0);
        mPaddingBottom = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_bottom, 0);
        mPaddingLeft = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_left, 0);
        mPaddingRight = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_right, 0);
        mGapBetweenImageAndText = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_gap_between_image_and_text, 0);
        mGapBetweenLabelAndData = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_gap_between_label_and_data, 0);
        mCallButtonPadding = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_call_button_padding, 0);
        mPresenceIconMargin = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_presence_icon_margin, 0);
        mDefaultPhotoViewSize = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_photo_size, 0);
        mPrefixHightlightColor = a.getColor(
                R.styleable.ContactListItemView_list_item_prefix_highlight_color, Color.GREEN);

        mHeaderTextIndent = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_header_text_indent, 0);
        mHeaderTextColor = a.getColor(
                R.styleable.ContactListItemView_list_item_header_text_color, Color.BLACK);
        mHeaderTextSize = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_header_text_size, 12);

        a.recycle();

        mHeaderBackgroundHeight = mHeaderBackgroundDrawable.getIntrinsicHeight();
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

    public void setTextWithHighlightingFactory(TextWithHighlightingFactory factory) {
        mTextWithHighlightingFactory = factory;
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
        int width = resolveSize(0, widthMeasureSpec);
        int height = 0;
        int preferredHeight = mPreferredHeight;

        mLine1Height = 0;
        mLine2Height = 0;
        mLine3Height = 0;
        mLine4Height = 0;

        // Obtain the natural dimensions of the name text (we only care about height)
        if (isVisible(mNameTextView)) {
            mNameTextView.measure(0, 0);
            mLine1Height = mNameTextView.getMeasuredHeight();
        }

        if (isVisible(mPhoneticNameTextView)) {
            mPhoneticNameTextView.measure(0, 0);
            mLine2Height = mPhoneticNameTextView.getMeasuredHeight();
        }

        if (isVisible(mLabelView)) {
            mLabelView.measure(0, 0);
            mLine3Height = mLabelView.getMeasuredHeight();
        }

        if (isVisible(mDataView)) {
            mDataView.measure(0, 0);
            mLine3Height = Math.max(mLine3Height, mDataView.getMeasuredHeight());
        }

        if (isVisible(mSnippetView)) {
            mSnippetView.measure(0, 0);
            mLine4Height = mSnippetView.getMeasuredHeight();
        }

        height += mLine1Height + mLine2Height + mLine3Height + mLine4Height
                + mPaddingTop + mPaddingBottom;

        if (isVisible(mCallButton)) {
            mCallButton.measure(0, 0);
        }
        if (isVisible(mPresenceIcon)) {
            mPresenceIcon.measure(0, 0);
        }

        ensurePhotoViewSize();

        height = Math.max(height, mPhotoViewHeight + mPaddingBottom + mPaddingTop);

        if (mHorizontalDividerVisible) {
            height += mHorizontalDividerHeight;
            preferredHeight += mHorizontalDividerHeight;
        }

        height = Math.max(height, preferredHeight);

        if (mHeaderVisible) {
            mHeaderTextView.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mHeaderBackgroundHeight, MeasureSpec.EXACTLY));
            height += mHeaderBackgroundHeight;
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;

        // Determine the vertical bounds by laying out the header first.
        int topBound = 0;
        int bottomBound = height;

        if (mHeaderVisible) {
            mHeaderBackgroundDrawable.setBounds(
                    0,
                    0,
                    width,
                    mHeaderBackgroundHeight);
            mHeaderTextView.layout(mHeaderTextIndent, 0, width, mHeaderBackgroundHeight);
            topBound += mHeaderBackgroundHeight;
        }

        if (mHorizontalDividerVisible) {
            mHorizontalDividerDrawable.setBounds(
                    0,
                    height - mHorizontalDividerHeight,
                    width,
                    height);
            bottomBound -= mHorizontalDividerHeight;
        }

        mBoundsWithoutHeader.set(0, topBound, width, bottomBound);

        if (mActivatedStateSupported) {
            mActivatedBackgroundDrawable.setBounds(mBoundsWithoutHeader);
        }

        topBound += mPaddingTop;
        bottomBound -= mPaddingBottom;

        // Positions of views on the left are fixed and so are those on the right side.
        // The stretchable part of the layout is in the middle.  So, we will start off
        // by laying out the left and right sides. Then we will allocate the remainder
        // to the text fields in the middle.

        int leftBound = layoutLeftSide(height, topBound, bottomBound, mPaddingLeft);
        int rightBound = layoutRightSide(height, topBound, width);

        // Text lines, centered vertically
        rightBound -= mPaddingRight;

        // Center text vertically
        int totalTextHeight = mLine1Height + mLine2Height + mLine3Height + mLine4Height;
        int textTopBound = (bottomBound + topBound - totalTextHeight) / 2;

        if (isVisible(mNameTextView)) {
            mNameTextView.layout(leftBound,
                    textTopBound,
                    rightBound,
                    textTopBound + mLine1Height);
        }

        int dataLeftBound = leftBound;
        if (isVisible(mPhoneticNameTextView)) {
            mPhoneticNameTextView.layout(leftBound,
                    textTopBound + mLine1Height,
                    rightBound,
                    textTopBound + mLine1Height + mLine2Height);
        }

        if (isVisible(mLabelView)) {
            dataLeftBound = leftBound + mLabelView.getMeasuredWidth();
            mLabelView.layout(leftBound,
                    textTopBound + mLine1Height + mLine2Height,
                    dataLeftBound,
                    textTopBound + mLine1Height + mLine2Height + mLine3Height);
            dataLeftBound += mGapBetweenLabelAndData;
        }

        if (isVisible(mDataView)) {
            mDataView.layout(dataLeftBound,
                    textTopBound + mLine1Height + mLine2Height,
                    rightBound,
                    textTopBound + mLine1Height + mLine2Height + mLine3Height);
        }

        if (isVisible(mSnippetView)) {
            mSnippetView.layout(leftBound,
                    textTopBound + mLine1Height + mLine2Height + mLine3Height,
                    rightBound,
                    textTopBound + mLine1Height + mLine2Height + mLine3Height + mLine4Height);
        }
    }

    /**
     * Performs layout of the left side of the view
     *
     * @return new left boundary
     */
    protected int layoutLeftSide(int height, int topBound, int bottomBound, int leftBound) {
        View photoView = mQuickContact != null ? mQuickContact : mPhotoView;
        if (photoView != null) {
            // Center the photo vertically
            int photoTop = topBound + (bottomBound - topBound - mPhotoViewHeight) / 2;
            photoView.layout(
                    leftBound,
                    photoTop,
                    leftBound + mPhotoViewWidth,
                    photoTop + mPhotoViewHeight);
            leftBound += mPhotoViewWidth + mGapBetweenImageAndText;
        }
        return leftBound;
    }

    /**
     * Performs layout of the right side of the view
     *
     * @return new right boundary
     */
    protected int layoutRightSide(int height, int topBound, int rightBound) {
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

        if (isVisible(mPresenceIcon)) {
            int iconWidth = mPresenceIcon.getMeasuredWidth();
            rightBound -= mPresenceIconMargin + iconWidth;
            mPresenceIcon.layout(
                    rightBound,
                    topBound,
                    rightBound + iconWidth,
                    height);
        }
        return rightBound;
    }

    @Override
    public void adjustListItemSelectionBounds(Rect bounds) {
        bounds.top += mBoundsWithoutHeader.top;
        bounds.bottom = bounds.top + mBoundsWithoutHeader.height();
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
        if (mPhotoViewWidth == 0 && mPhotoViewHeight == 0) {
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
            } else {
                mPhotoViewWidth = mPhotoViewHeight = mDefaultPhotoViewSize;
            }
        }
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
        if (mActivatedStateSupported) {
            mActivatedBackgroundDrawable.draw(canvas);
        }
        if (mHeaderVisible) {
            mHeaderBackgroundDrawable.draw(canvas);
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
                mHeaderTextView.setTextSize(mHeaderTextSize);
                mHeaderTextView.setTypeface(mHeaderTextView.getTypeface(), Typeface.BOLD);
                mHeaderTextView.setGravity(Gravity.CENTER_VERTICAL);
                addView(mHeaderTextView);
            }
            mHeaderTextView.setText(title);
            mHeaderTextView.setVisibility(View.VISIBLE);
            mHeaderVisible = true;
        } else {
            if (mHeaderTextView != null) {
                mHeaderTextView.setVisibility(View.GONE);
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
            mQuickContact.setExcludeMimes(new String[] { Contacts.CONTENT_ITEM_TYPE });
            addView(mQuickContact);
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
        }
        return mPhotoView;
    }

    /**
     * Removes the photo view.  Should not be needed once we start handling different
     * types of views as different types of views from the List's perspective.
     */
    public void removePhotoView() {
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
            mNameTextView.setTextAppearance(mContext, android.R.style.TextAppearance_Large);
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
            mPhoneticNameTextView.setText(text, 0, size);
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
            mLabelView.setText(text);
            mLabelView.setVisibility(VISIBLE);
        }
    }

    /**
     * Adds or updates a text view for the data label.
     */
    public void setLabel(char[] text, int size) {
        if (text == null || size == 0) {
            if (mLabelView != null) {
                mLabelView.setVisibility(View.GONE);
            }
        } else {
            getLabelView();
            mLabelView.setText(text, 0, size);
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
            mLabelView.setTypeface(mLabelView.getTypeface(), Typeface.BOLD);
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
            return;
        } else {
            getDataView();
            mDataView.setText(text, 0, size);
            mDataView.setVisibility(VISIBLE);
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
            getSnippetView();
            setTextWithPrefixHighlighting(mSnippetView, text);
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
            addView(mSnippetView);
        }
        return mSnippetView;
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
        return mActivatedStateSupported ? TruncateAt.START : TruncateAt.MARQUEE;
    }

    public void showDisplayName(Cursor cursor, int nameColumnIndex, boolean highlightingEnabled,
            int alternativeNameColumnIndex) {
        cursor.copyStringToBuffer(nameColumnIndex, mNameBuffer);
        TextView nameView = getNameTextView();
        int size = mNameBuffer.sizeCopied;
        if (size != 0) {
            if (mHighlightedPrefix != null) {
                setTextWithPrefixHighlighting(nameView, mNameBuffer);
            } else if (highlightingEnabled) {
                if (mTextWithHighlighting == null) {
                    mTextWithHighlighting =
                            mTextWithHighlightingFactory.createTextWithHighlighting();
                }
                cursor.copyStringToBuffer(alternativeNameColumnIndex, mHighlightedTextBuffer);
                mTextWithHighlighting.setText(mNameBuffer, mHighlightedTextBuffer);
                nameView.setText(mTextWithHighlighting);
            } else {
                nameView.setText(mNameBuffer.data, 0, size);
            }
        } else {
            nameView.setText(mUnknownNameText);
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

    /**
     * Sets the proper icon (star or presence or nothing)
     */
    public void showPresence(Cursor cursor, int presenceColumnIndex, int capabilityColumnIndex) {
        Drawable icon = null;
        if (!cursor.isNull(presenceColumnIndex)) {
            int status = cursor.getInt(presenceColumnIndex);
            int chatCapability = 0;
            if (capabilityColumnIndex != 0 && !cursor.isNull(presenceColumnIndex)) {
                chatCapability = cursor.getInt(capabilityColumnIndex);
            }
            icon = ContactPresenceIconUtil.getChatCapabilityIcon(
                    getContext(), status, chatCapability);
        }
        setPresence(icon);
    }

    /**
     * Shows search snippet.
     */
    public void showSnippet(Cursor cursor, int summarySnippetMimetypeColumnIndex,
            int summarySnippetData1ColumnIndex, int summarySnippetData4ColumnIndex) {
        String snippet = null;
        String snippetMimeType = cursor.getString(summarySnippetMimetypeColumnIndex);
        if (Email.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
            String email = cursor.getString(summarySnippetData1ColumnIndex);
            if (!TextUtils.isEmpty(email)) {
                snippet = email;
            }
        } else if (Organization.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
            String company = cursor.getString(summarySnippetData1ColumnIndex);
            String title = cursor.getString(summarySnippetData4ColumnIndex);
            if (!TextUtils.isEmpty(company)) {
                if (!TextUtils.isEmpty(title)) {
                    snippet = company + " / " + title;
                } else {
                    snippet = company;
                }
            } else if (!TextUtils.isEmpty(title)) {
                snippet = title;
            }
        } else if (Nickname.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
            String nickname = cursor.getString(summarySnippetData1ColumnIndex);
            if (!TextUtils.isEmpty(nickname)) {
                snippet = nickname;
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

    /**
     * Sets text on the given text view, highlighting the word that matches
     * the given prefix (see {@link #setHighlightedPrefix}).
     */
    private void setTextWithPrefixHighlighting(TextView textView, String text) {
        mHighlightedTextBuffer.sizeCopied =
                Math.min(text.length(), mHighlightedTextBuffer.data.length);
        text.getChars(0, mHighlightedTextBuffer.sizeCopied, mHighlightedTextBuffer.data, 0);
        setTextWithPrefixHighlighting(textView, mHighlightedTextBuffer);
    }

    /**
     * Sets text on the given text view, highlighting the word that matches
     * the given prefix (see {@link #setHighlightedPrefix}).
     */
    private void setTextWithPrefixHighlighting(TextView textView, CharArrayBuffer text) {
        int index = indexOfWordPrefix(text, mHighlightedPrefix);
        if (index != -1) {
            if (mPrefixColorSpan == null) {
                mPrefixColorSpan = new ForegroundColorSpan(mPrefixHightlightColor);
            }

            String string = new String(text.data, 0, text.sizeCopied);
            SpannableString name = new SpannableString(string);
            name.setSpan(mPrefixColorSpan, index, index + mHighlightedPrefix.length, 0 /* flags */);
            textView.setText(name);
        } else {
            textView.setText(text.data, 0, text.sizeCopied);
        }
    }

    /**
     * Finds the index of the word that starts with the given prefix.  If not found,
     * returns -1.
     */
    private int indexOfWordPrefix(CharArrayBuffer buffer, char[] prefix) {
        if (prefix == null || prefix.length == 0) {
            return -1;
        }

        char[] string1 = buffer.data;
        int count1 = buffer.sizeCopied;
        int count2 = prefix.length;

        int size = count2;
        int i = 0;
        while (i < count1) {

            // Skip non-word characters
            while (i < count1 && !Character.isLetterOrDigit(string1[i])) {
                i++;
            }

            if (i + size > count1) {
                return -1;
            }

            // Compare the prefixes
            int j;
            for (j = 0; j < size; j++) {
                if (Character.toUpperCase(string1[i+j]) != prefix[j]) {
                    break;
                }
            }
            if (j == size) {
                return i;
            }

            // Skip this word
            while (i < count1 && Character.isLetterOrDigit(string1[i])) {
                i++;
            }
        }

        return -1;
    }

    @Override
    public void requestLayout() {
        // We will assume that once measured this will not need to resize
        // itself, so there is no need to pass the layout request to the parent
        // view (ListView).
        forceLayout();
    }
}
