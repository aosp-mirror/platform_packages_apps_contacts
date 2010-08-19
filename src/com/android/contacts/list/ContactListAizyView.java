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
import com.android.contacts.util.PhonebookCollatorFactory;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;

/**
 * A View that displays the sections given by an Indexer and their relative sizes. For
 * English and similar languages, this is an A to Z list (where only the used letters are
 * displayed). As the sections are shown in their relative sizes, this View can be used as a
 * scrollbar.
 */
public class ContactListAizyView extends View {
    private static final String TAG = "ContactListAizyView";

    private static final int PREVIEW_TIME_DELAY_MS = 400;

    private Listener mListener;
    private PopupWindow mPreviewPopupWindow;
    private TextView mPreviewPopupTextView;

    private ResourceValues mResourceValues;

    /**
     * True if the popup window is currently visible.
     */
    private boolean mPreviewPopupVisible;

    /**
     * Time when the user started tapping. This is used to calculate the time delay before fading
     * in the PopupWindow
     */
    private long mPreviewPopupStartTime;

    /**
     * Needed only inside {@link #onTouchEvent(MotionEvent)} to get the location of touch events.
     */
    private int[] mWindowOffset;

    /**
     * Needed to measure text. Used inside {@link #onDraw(Canvas)}
     */
    private final Rect bounds = new Rect();

    /**
     * Used and cached inside {@link #onDraw(Canvas)}
     */
    private FontMetrics mFontMetrics;

    /**
     * Used and cached inside {@link #onDraw(Canvas)}
     */
    private Paint mPaint;

    /**
     * The list of displayed sections. "Virtual" sections can be empty and therefore don't show
     * up as regular sections
     */
    private final ArrayList<VirtualSection> mVirtualSections = new ArrayList<VirtualSection>();

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

        mResourceValues = new ResourceValues(getResources());

        final LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPreviewPopupWindow = new PopupWindow(
                inflater.inflate(R.layout.aizy_popup_window, null, false),
                (int) mResourceValues.previewWidth, (int) mResourceValues.previewHeight);
        mPreviewPopupWindow.setAnimationStyle(android.R.style.Animation_Toast);
        mPreviewPopupTextView =
                (TextView) mPreviewPopupWindow.getContentView().findViewById(R.id.caption);
    }

    /**
     * Sets up the Aizy based on the indexer and completely reads its contents.
     * This function has to be called everytime the data is changed.
     */
    public void readFromIndexer(SectionIndexer indexer) {
        mVirtualSections.clear();
        final String alphabetString = getResources().getString(R.string.visualScrollerAlphabet);
        final String[] alphabet = alphabetString.split(";");

        // We expect to get 10 additional items that the base alphabet
        mVirtualSections.ensureCapacity(alphabet.length + 10);

        if (indexer != null) {
            // Add the real sections
            final Object[] sections = indexer.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                final Object section = sections[sectionIndex];
                final String caption = section == null ? "" : section.toString();
                final int position = indexer.getPositionForSection(sectionIndex);
                mVirtualSections.add(new VirtualSection(caption, sectionIndex, position));
            }
        }

        final Collator collator = PhonebookCollatorFactory.getCollator();

        // Add the base alphabet if missing
        for (String caption : alphabet) {
            boolean insertAtEnd = true;
            VirtualSection previousVirtualSection = null;
            for (int i = 0; i < mVirtualSections.size(); i++) {
                final VirtualSection virtualSection = mVirtualSections.get(i);
                final String virtualSectionCaption = virtualSection.getCaption();
                final int comparison = collator.compare(virtualSectionCaption, caption);
                if (comparison == 0) {
                    // element is already in the list.
                    insertAtEnd = false;
                    break;
                }
                if (comparison > 0) {
                    // we stepped too far. the element belongs before the element at i
                    insertAtEnd = false;
                    final int realSectionPosition = previousVirtualSection == null ? 0
                            : previousVirtualSection.getRealSectionPosition();
                    mVirtualSections.add(i, new VirtualSection(caption, -1, realSectionPosition));
                    break;
                }
                previousVirtualSection = virtualSection;
            }
            if (insertAtEnd) {
                final int realSectionPosition = previousVirtualSection == null ? 0
                        : previousVirtualSection.getRealSectionPosition();
                mVirtualSections.add(new VirtualSection(caption, -1, realSectionPosition));
            }
        }
        invalidate();
    }

    /**
     * Sets the Listener that is called everytime the user taps on this control.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(0, widthMeasureSpec), resolveSize(0, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mPaint == null) {
            mPaint = new Paint();
            mPaint.setTextSize(mResourceValues.textSize);
            mPaint.setAntiAlias(true);
            mPaint.setTextAlign(Align.CENTER);
        }
        if (mFontMetrics == null) {
            mFontMetrics = mPaint.getFontMetrics();
        }
        final float fontHeight = mFontMetrics.descent - mFontMetrics.ascent;
        final int halfWidth = getWidth() / 2;
        // Draw
        float lastVisibleY = Float.NEGATIVE_INFINITY;
        final float sectionHeight = (float) getHeight() / mVirtualSections.size();
        for (int i = 0; i < mVirtualSections.size(); i++) {
            final VirtualSection virtualSection = mVirtualSections.get(i);
            final String caption = virtualSection.getCaption();
            if (!virtualSection.isMeasured()) {
                mPaint.getTextBounds(caption, 0, caption.length(), bounds);
                virtualSection.setMeasuredSize(-bounds.top);
            }
            final float y = i * sectionHeight;
            if (lastVisibleY + fontHeight < y) {
                mPaint.setColor(virtualSection.getRealSectionIndex() != -1
                        ? mResourceValues.nonEmptySectionColor : mResourceValues.emptySectionColor);

                canvas.drawText(caption, halfWidth,
                        y + sectionHeight / 2 + virtualSection.getMeasuredSize() / 2, mPaint);
                lastVisibleY = y;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mWindowOffset == null) {
            mWindowOffset = new int[2];
            getLocationInWindow(mWindowOffset);
        }

        // Scroll the list itself
        final int boundedY = Math.min(Math.max(0, (int) (event.getY())), getHeight() - 1);
        final int index = boundedY * mVirtualSections.size() / getHeight();
        final VirtualSection virtualSection = mVirtualSections.get(index);
        final int sectionY = index * getHeight() / mVirtualSections.size();
        mPreviewPopupTextView.setText(virtualSection.getCaption());
        mPreviewPopupTextView.setTextColor(virtualSection.getRealSectionIndex() != -1
                ? mResourceValues.nonEmptySectionColor : mResourceValues.emptySectionColor);

        // Draw popup window
        final int previewX = mWindowOffset[0] + getWidth();
        final float sectionHeight = (float) getHeight() / mVirtualSections.size();
        final int previewY = (int) (sectionY + mWindowOffset[1] + (sectionHeight -
                mPreviewPopupWindow.getHeight()) / 2);
        final int actionMasked = event.getActionMasked();
        final boolean fingerIsDown = actionMasked == MotionEvent.ACTION_DOWN;
        if (fingerIsDown) {
            mPreviewPopupStartTime = System.currentTimeMillis();
        }
        final boolean fingerIsDownOrScrubbing =
            actionMasked == MotionEvent.ACTION_MOVE || actionMasked == MotionEvent.ACTION_DOWN;

        final boolean previewPopupVisible = fingerIsDownOrScrubbing &&
                (System.currentTimeMillis() > mPreviewPopupStartTime + PREVIEW_TIME_DELAY_MS);

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

        // Perform the actual scrolling
        if (mListener != null) mListener.onScroll(virtualSection.getRealSectionPosition());

        super.onTouchEvent(event);
        return true;
    }

    /**
     * Reads an provides all values from the resource files
     */
    private static class ResourceValues {
        private final int emptySectionColor;
        private final int nonEmptySectionColor;
        private final float textSize;
        private final float previewWidth;
        private final float previewHeight;

        private ResourceValues(Resources resources) {
            emptySectionColor = resources.getColor(R.color.aizy_empty_section);
            nonEmptySectionColor = resources.getColor(R.color.aizy_non_empty_section);
            textSize = resources.getDimension(R.dimen.aizy_text_size);
            previewWidth = resources.getDimension(R.dimen.aizy_preview_width);
            previewHeight = resources.getDimension(R.dimen.aizy_preview_height);
        }
    }

    private static class VirtualSection {
        private final String mCaption;
        private final int mRealSectionIndex;
        private final int mRealSectionPosition;
        private float mMeasuredSize = Float.NaN;

        public String getCaption() {
            return mCaption;
        }

        public int getRealSectionIndex() {
            return mRealSectionIndex;
        }

        public int getRealSectionPosition() {
            return mRealSectionPosition;
        }

        public boolean isMeasured() {
            return mMeasuredSize == Float.NaN;
        }

        public void setMeasuredSize(float value) {
            mMeasuredSize = value;
        }

        public float getMeasuredSize() {
            return mMeasuredSize;
        }

        public VirtualSection(String caption, int realSectionIndex, int realSectionPosition) {
            mCaption = caption;
            mRealSectionIndex = realSectionIndex;
            mRealSectionPosition = realSectionPosition;
        }
    }

    public interface Listener {
        void onScroll(int position);
    }
}
