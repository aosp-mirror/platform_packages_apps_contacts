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
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;

/**
 * A custom view for an item in the phone multi-picker list.
 */
public class MultiplePhonePickerItemView extends ContactListItemView {

    // Used to indicate the sequence of phones belong to the same contact in multi-picker
    private View mChipView;
    // Used to select the phone in multi-picker
    private CheckBox mCheckBox;

    private int mChipWidth;
    private int mChipRightMargin;
    private int mCheckBoxMargin;

    public long phoneId;
    // phoneNumber only validates when phoneId = INVALID_PHONE_ID
    public String phoneNumber;

    public MultiplePhonePickerItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources resources = context.getResources();
        mChipWidth =
                resources.getDimensionPixelOffset(R.dimen.list_item_header_chip_width);
        mChipRightMargin =
                resources.getDimensionPixelOffset(R.dimen.list_item_header_chip_right_margin);
        mCheckBoxMargin =
                resources.getDimensionPixelOffset(R.dimen.list_item_header_checkbox_margin);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isVisible(mChipView)) {
            mChipView.measure(0, 0);
        }

        if (isVisible(mCheckBox)) {
            mCheckBox.measure(0, 0);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected int layoutLeftSide(int height, int topBound, int leftBound) {
        if (mChipView != null) {
            mChipView.layout(leftBound, topBound, leftBound + mChipWidth, height);
            leftBound += mChipWidth + mChipRightMargin;
        }

        return super.layoutLeftSide(height, topBound, leftBound);
    }

    @Override
    protected int layoutRightSide(int height, int topBound, int rightBound) {
        rightBound = super.layoutRightSide(height, topBound, rightBound);

        if (isVisible(mCheckBox)) {
            int checkBoxWidth = mCheckBox.getMeasuredWidth();
            int checkBoxHight = mCheckBox.getMeasuredHeight();
            rightBound -= mCheckBoxMargin + checkBoxWidth;
            int checkBoxTop = topBound + (height - topBound - checkBoxHight) / 2;
            mCheckBox.layout(
                    rightBound,
                    checkBoxTop,
                    rightBound + checkBoxWidth,
                    checkBoxTop + checkBoxHight);
        }

        return rightBound;
    }

    /**
     * Returns the chip view for the multipicker, creating it if necessary.
     */
    public View getChipView() {
        if (mChipView == null) {
            mChipView = new View(mContext);
            addView(mChipView);
        }
        return mChipView;
    }

    /**
     * Returns the CheckBox view for the multipicker, creating it if necessary.
     */
    public CheckBox getCheckBoxView() {
        if (mCheckBox == null) {
            mCheckBox = new CheckBox(mContext);
            mCheckBox.setClickable(false);
            mCheckBox.setFocusable(false);
            addView(mCheckBox);
        }
        return mCheckBox;
    }
}
