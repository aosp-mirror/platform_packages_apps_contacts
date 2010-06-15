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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
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

    // TODO: Put these into resource files or create from image resources
    private static final int TEXT_WIDTH = 20;
    private static final int CIRCLE_DIAMETER = 30;
    private static final int PREVIEW_WIDTH = 130;
    private static final int PREVIEW_HEIGHT = 115;

    private SectionIndexer mIndexer;

    private boolean mCalculateYCoordinates;
    private ListView mListView;
    private float mPosition;
    private float mFactor;
    private PopupWindow mPreviewPopupWindow;
    private TextView mPreviewPopupTextView;
    private boolean mPreviewPopupVisible;
    private int[] mWindowOffset;
    private float[] yPositions = null;

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

        final LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPreviewPopupWindow = new PopupWindow(
                inflater.inflate(R.layout.aizy_popup_window, null, false),
                PREVIEW_WIDTH, PREVIEW_HEIGHT);
        mPreviewPopupTextView =
                (TextView) mPreviewPopupWindow.getContentView().findViewById(R.id.caption);
    }

    public void setIndexer(SectionIndexer indexer) {
        mIndexer = indexer;
        mCalculateYCoordinates = true;
    }

    public void setListView(ListView listView) {
        mListView = listView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(TEXT_WIDTH + CIRCLE_DIAMETER, resolveSize(0, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mCalculateYCoordinates = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mIndexer == null) return;

        calcYCoordinates();

        drawLineAndText(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mWindowOffset == null) {
            mWindowOffset = new int[2];
            getLocationInWindow(mWindowOffset);
        }

        final int previewX = mWindowOffset[0] + getWidth();
        final int previewY = (int) event.getY() + mWindowOffset[1]
                - mPreviewPopupWindow.getHeight() / 2;
        final boolean previewPopupVisible = event.getActionMasked() == MotionEvent.ACTION_MOVE;
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
        final int position = Math.max(0, (int) (event.getY() / mFactor));
        if (mIndexer != null) {
            final int index = mIndexer.getSectionForPosition(position);
            final Object[] sections = mIndexer.getSections();
            final String caption =
                    (index != -1 && index < sections.length) ? sections[index].toString() : "";
            mPreviewPopupTextView.setText(caption);
        }
        if (mListView != null) {
            mListView.setSelectionFromTop(position, 0);
        }

        super.onTouchEvent(event);
        return true;
    }

    private void calcYCoordinates() {
        if (!mCalculateYCoordinates) return;
        mCalculateYCoordinates = false;

        // Get a String[] of the sections.
        final Object[] sectionObjects = mIndexer.getSections();
        final int sectionCount = sectionObjects.length;
        final String[] sections;
        if (sectionObjects instanceof String[]) {
            sections = (String[]) sectionObjects;
        } else {
            sections = new String[sectionCount];
            for (int i = 0; i < sectionCount; i++) {
                sections[i] = sectionObjects[i] == null ? null : sectionObjects[i].toString();
            }
        }

        mFactor = (float) getHeight() / mListView.getCount();
    }

    private void drawLineAndText(Canvas canvas) {
        // TODO: Figure out how to set the text size and fetch the height in pixels. This
        // behaviour is OK for prototypes, but has to be refined later
        final float textSize = 20.0f;

        // Move A down, Z up
        final Paint paint = new Paint();
        paint.setColor(Color.LTGRAY);
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        final Object[] sections = mIndexer.getSections();
        canvas.drawLine(
                TEXT_WIDTH + CIRCLE_DIAMETER * 0.5f, 0.0f,
                TEXT_WIDTH + CIRCLE_DIAMETER * 0.5f, getHeight(),
                paint);
        final int sectionCount = sections.length;
        if (yPositions == null || yPositions.length != sectionCount) {
            yPositions = new float[sectionCount];
        }

        // Calculate Positions
        for (int i = 0; i < sectionCount; i++) {
            yPositions[i] = mIndexer.getPositionForSection(i) * mFactor;
        }

        // Draw
        float lastVisibleY = Float.MAX_VALUE;
        for (int i = sectionCount - 1; i >= 0; i--) {
            final float y = yPositions[i];
            if (lastVisibleY - textSize > y) {
                canvas.drawText(sections[i].toString(), 0.0f, y + 0.5f * textSize, paint);
                lastVisibleY = y;
            }
            canvas.drawLine(
                    TEXT_WIDTH + CIRCLE_DIAMETER * 0.5f - 2, y,
                    TEXT_WIDTH + CIRCLE_DIAMETER * 0.5f + 2, y,
                    paint);
        }

        paint.setColor(Color.YELLOW);
        canvas.drawLine(
                TEXT_WIDTH + CIRCLE_DIAMETER * 0.0f, mPosition * mFactor,
                TEXT_WIDTH + CIRCLE_DIAMETER * 1.0f, mPosition * mFactor,
                paint);
    }

    public void listOnScroll(int firstVisibleItem) {
        mPosition = firstVisibleItem;
        invalidate();
    }
}
