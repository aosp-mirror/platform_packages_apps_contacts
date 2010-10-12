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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.SectionIndexer;
import android.widget.TextView;

/**
 * A View that displays the sections given by an Indexer and their relative sizes. For
 * English and similar languages, this is an A to Z list (where only the used letters are
 * displayed). As the sections are shown in their relative sizes, this View can be used as a
 * scrollbar.
 */
public class ContactListAizyView extends View {
    private static final String TAG = "ContactListAizyView";

    private SectionIndexer mIndexer;
    private int mItemCount;
    private int[] mSectionPositions;

    private Listener mListener;
    private float mPosition;

    private int mPreviewPosition;
    private PopupWindow mPreviewPopupWindow;
    private TextView mPreviewPopupTextView;
    private boolean mPreviewPopupVisible;
    private final int[] mWindowOffset = new int[2];

    private ResourceValues mResourceValues;

    public ContactListAizyView(Context context) {
        super(context);
    }

    public ContactListAizyView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ContactListAizyView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mResourceValues = new ResourceValues(getContext().getResources());

        final LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPreviewPopupWindow = new PopupWindow(
                inflater.inflate(R.layout.aizy_popup_window, null, false),
                mResourceValues.getPreviewWidth(), mResourceValues.getPreviewHeight());
        mPreviewPopupWindow.setAnimationStyle(R.style.AizyPreviewPopupAnimation);
        mPreviewPopupTextView =
                (TextView) mPreviewPopupWindow.getContentView().findViewById(R.id.caption);
    }

    public void setIndexer(SectionIndexer indexer, int itemCount) {
        mIndexer = indexer;
        mItemCount = itemCount;
        if (mIndexer == null) {
            mSectionPositions = null;
            return;
        }

        // Read the section positions
        final Object[] sections = mIndexer.getSections();
        final int sectionCount = sections.length;
        if (mSectionPositions == null || mSectionPositions.length != sectionCount) {
            mSectionPositions = new int[sectionCount];
        }
        for (int i = 0; i < sectionCount; i++) {
            mSectionPositions[i] = mIndexer.getPositionForSection(i);
        }

    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(0, widthMeasureSpec), resolveSize(0, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mIndexer == null) return;

        drawLineAndText(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        getLocationInWindow(mWindowOffset);

        final int previewX = mWindowOffset[0] + getWidth();
        final int previewY = (int) event.getY() + mWindowOffset[1]
                - mPreviewPopupWindow.getHeight() / 2;
        final boolean previewPopupVisible =
            event.getActionMasked() == MotionEvent.ACTION_MOVE ||
            event.getActionMasked() == MotionEvent.ACTION_DOWN;
        if (previewPopupVisible != mPreviewPopupVisible) {
            if (previewPopupVisible) {
                mPreviewPopupWindow.showAtLocation(this, Gravity.LEFT | Gravity.TOP,
                        previewX, previewY);
            } else {
                mPreviewPopupWindow.dismiss();
            }
            mPreviewPopupVisible = previewPopupVisible;
        } else {
            mPreviewPopupWindow.update(previewX, previewY, -1, -1);
        }
        final float yFactor = (float) getHeight() / mItemCount;
        final int position = Math.max(0, (int) (event.getY() / yFactor));
        if (mIndexer != null) {
            final int index = mIndexer.getSectionForPosition(position);
            final Object[] sections = mIndexer.getSections();
            final String caption =
                    (index != -1 && index < sections.length) ? sections[index].toString() : "";
            mPreviewPopupTextView.setText(caption);
        }

        if (mListener != null) mListener.onScroll(position);
        mPreviewPosition = position;

        super.onTouchEvent(event);
        return true;
    }

    private void drawLineAndText(Canvas canvas) {
        final float yFactor = (float) getHeight() / mItemCount;

        final Paint paint = new Paint();

        paint.setColor(mResourceValues.getLineColor());
        paint.setAntiAlias(true);

        // Draw sections
        final float centerX = getWidth() * 0.5f;
        for (int i = 1; i < mSectionPositions.length; i++) {
            final float y1 = mSectionPositions[i - 1] * yFactor;
            final float y2 = mSectionPositions[i] * yFactor;
            canvas.drawLine(
                    centerX, y1 + 1.0f,
                    centerX, y2 - 1.0f,
                    paint);
        }

        // Draw knob
        final Drawable knob = mResourceValues.getKnobDrawable();
        final int w = knob.getIntrinsicWidth();
        final int h = knob.getIntrinsicWidth();
        final float y;
//        if (mPreviewPopupWindow.) {
//            y = mPreviewPosition * yFactor;
//        } else {
            y = mPosition * yFactor;
//        }
        knob.setBounds(
                (int) (centerX - w / 2.0f), (int) (y - h / 2.0f),
                (int) (centerX + w / 2.0f), (int) (y + h / 2.0f));
        knob.draw(canvas);
    }

    public void listOnScroll(int firstVisibleItem) {
        mPosition = firstVisibleItem;
        invalidate();
    }

    private static class ResourceValues {
        private int mLineColor;
        private Drawable mKnobDrawable;
        private int mPreviewWidth;
        private int mPreviewHeight;

        public int getLineColor() {
            return mLineColor;
        }

        public Drawable getKnobDrawable() {
            return mKnobDrawable;
        }

        public int getPreviewWidth() {
            return mPreviewWidth;
        }

        public int getPreviewHeight() {
            return mPreviewHeight;
        }

        public ResourceValues(Resources resources) {
            mLineColor = resources.getColor(R.color.aizy_line_color);
            mKnobDrawable = resources.getDrawable(R.drawable.temp_aizy_knob);
            mPreviewWidth = resources.getDimensionPixelSize(R.dimen.aizy_preview_width);
            mPreviewHeight = resources.getDimensionPixelSize(R.dimen.aizy_preview_height);
        }
    }

    public interface Listener {
        void onScroll(int position);
    }
}