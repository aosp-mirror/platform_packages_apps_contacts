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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
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

    private Drawable mHeaderBackgroundDrawable;
    private int mHeaderBackgroundHeight;
    private TextView mHeaderTextView;

    public ContactListPinnedHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ContactListItemView);

        mHeaderBackgroundDrawable = a.getDrawable(
                R.styleable.ContactListItemView_section_header_background);
        mHeaderTextIndent = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_header_text_indent, 0);
        mHeaderTextColor = a.getColor(
                R.styleable.ContactListItemView_list_item_header_text_color, Color.BLACK);
        mHeaderTextSize = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_header_text_size, 12);

        a.recycle();

        mHeaderBackgroundHeight = mHeaderBackgroundDrawable.getIntrinsicHeight();

        mHeaderTextView = new TextView(mContext);
        mHeaderTextView.setTextColor(mHeaderTextColor);
        mHeaderTextView.setTextSize(mHeaderTextSize);
        mHeaderTextView.setTypeface(mHeaderTextView.getTypeface(), Typeface.BOLD);
        mHeaderTextView.setGravity(Gravity.CENTER_VERTICAL);
        addView(mHeaderTextView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        // We will match parent's width and wrap content vertically.
        int width = resolveSize(0, widthMeasureSpec);

        mHeaderTextView.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mHeaderBackgroundHeight, MeasureSpec.EXACTLY));

        setMeasuredDimension(width, mHeaderBackgroundHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        mHeaderBackgroundDrawable.setBounds(0, 0, width, mHeaderBackgroundHeight);
        mHeaderTextView.layout(mHeaderTextIndent, 0, width, mHeaderBackgroundHeight);
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        mHeaderBackgroundDrawable.draw(canvas);
        super.dispatchDraw(canvas);
    }

    /**
     * Sets section header or makes it invisible if the title is null.
     */
    public void setSectionHeader(String title) {
        if (!TextUtils.isEmpty(title)) {
            mHeaderTextView.setText(title);
            mHeaderTextView.setVisibility(View.VISIBLE);
        } else {
            mHeaderTextView.setVisibility(View.GONE);
        }
    }

    @Override
    public void requestLayout() {
        // We will assume that once measured this will not need to resize
        // itself, so there is no need to pass the layout request to the parent
        // view (ListView).
        forceLayout();
    }
}
