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

import com.android.contacts.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A custom view for the pinned section header shown at the top of the contact list.
 */
public class ContactListPinnedHeaderView extends ViewGroup {

    protected final Context mContext;

    private final int mHeaderTextColor;
    private final int mHeaderTextIndent;
    private final int mHeaderTextSize;
    private final int mHeaderUnderlineHeight;
    private final int mHeaderUnderlineColor;
    private final int mPaddingRight;
    private final int mPaddingLeft;
    private final int mContactsCountTextColor;
    private final int mCountViewTextSize;

    private int mHeaderBackgroundHeight;
    private TextView mHeaderTextView;
    private TextView mCountTextView = null;
    private View mHeaderDivider;

    public ContactListPinnedHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ContactListItemView);

        mHeaderTextIndent = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_header_text_indent, 0);
        mHeaderTextColor = a.getColor(
                R.styleable.ContactListItemView_list_item_header_text_color, Color.BLACK);
        mHeaderTextSize = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_header_text_size, 12);
        mHeaderUnderlineHeight = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_header_underline_height, 1);
        mHeaderUnderlineColor = a.getColor(
                R.styleable.ContactListItemView_list_item_header_underline_color, 0);
        mHeaderBackgroundHeight = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_header_height, 30);
        mPaddingLeft = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_left, 0);
        mPaddingRight = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_right, 0);
        mContactsCountTextColor = a.getColor(
                R.styleable.ContactListItemView_list_item_contacts_count_text_color, Color.BLACK);
        mCountViewTextSize = (int)a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_contacts_count_text_size, 12);

        a.recycle();

        mHeaderTextView = new TextView(mContext);
        mHeaderTextView.setTextColor(mHeaderTextColor);
        mHeaderTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mHeaderTextSize);
        mHeaderTextView.setTypeface(mHeaderTextView.getTypeface(), Typeface.BOLD);
        mHeaderTextView.setGravity(Gravity.CENTER_VERTICAL);
        mHeaderTextView.setAllCaps(true);
        addView(mHeaderTextView);
        mHeaderDivider = new View(mContext);
        mHeaderDivider.setBackgroundColor(mHeaderUnderlineColor);
        addView(mHeaderDivider);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        // We will match parent's width and wrap content vertically.
        int width = resolveSize(0, widthMeasureSpec);

        mHeaderTextView.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(mHeaderBackgroundHeight, MeasureSpec.EXACTLY));
        if (isViewMeasurable(mCountTextView)) {
            mCountTextView.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(mHeaderBackgroundHeight, MeasureSpec.EXACTLY));
        }

        setMeasuredDimension(width, mHeaderBackgroundHeight + mHeaderUnderlineHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;

        // Take into account left and right padding when laying out the below views.
        mHeaderTextView.layout(mHeaderTextIndent + mPaddingLeft,
                0,
                mHeaderTextView.getMeasuredWidth() + mHeaderTextIndent + mPaddingLeft,
                mHeaderBackgroundHeight);

        if (isViewMeasurable(mCountTextView)) {
            mCountTextView.layout(width - mPaddingRight - mCountTextView.getMeasuredWidth(),
                    0,
                    width - mPaddingRight,
                    mHeaderBackgroundHeight);
        }

        mHeaderDivider.layout(mPaddingLeft,
                mHeaderBackgroundHeight,
                width - mPaddingRight,
                mHeaderBackgroundHeight + mHeaderUnderlineHeight);
    }

    /**
     * Sets section header or makes it invisible if the title is null.
     */
    public void setSectionHeader(String title) {
        if (!TextUtils.isEmpty(title)) {
            mHeaderTextView.setText(title);
            mHeaderTextView.setVisibility(View.VISIBLE);
            mHeaderDivider.setVisibility(View.VISIBLE);
        } else {
            mHeaderTextView.setVisibility(View.GONE);
            mHeaderDivider.setVisibility(View.GONE);
        }
    }

    @Override
    public void requestLayout() {
        // We will assume that once measured this will not need to resize
        // itself, so there is no need to pass the layout request to the parent
        // view (ListView).
        forceLayout();
    }

    public void setCountView(String count) {
        if (mCountTextView == null) {
            mCountTextView = new TextView(mContext);
            mCountTextView.setTextColor(mContactsCountTextColor);
            mCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mCountViewTextSize);
            mCountTextView.setGravity(Gravity.CENTER_VERTICAL);
            addView(mCountTextView);
        }
        mCountTextView.setText(count);
        if (count == null || count.isEmpty()) {
            mCountTextView.setVisibility(View.GONE);
        } else {
            mCountTextView.setVisibility(View.VISIBLE);
        }
    }

    private boolean isViewMeasurable(View view) {
        return (view != null && view.getVisibility() == View.VISIBLE);
    }
}
