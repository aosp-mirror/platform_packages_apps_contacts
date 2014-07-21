/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.contacts.quickcontact;

import com.android.contacts.R;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Display entries in a LinearLayout that can be expanded to show all entries.
 */
public class ExpandingEntryCardView extends LinearLayout {

    private static final String TAG = "ExpandingEntryCardView";

    /**
     * Entry data.
     */
    public static final class Entry {

        private final int mViewId;
        private final Drawable mIcon;
        private final String mHeader;
        private final String mSubHeader;
        private final Drawable mSubHeaderIcon;
        private final String mText;
        private final Drawable mTextIcon;
        private final Intent mIntent;
        private final Drawable mAlternateIcon;
        private final Intent mAlternateIntent;
        private final String mAlternateContentDescription;
        private final boolean mShouldApplyColor;
        private final boolean mIsEditable;

        public Entry(int viewId, Drawable icon, String header, String subHeader, String text,
                Intent intent, Drawable alternateIcon, Intent alternateIntent,
                String alternateContentDescription, boolean shouldApplyColor,
                boolean isEditable) {
            this(viewId, icon, header, subHeader, null, text, null, intent, alternateIcon,
                    alternateIntent, alternateContentDescription, shouldApplyColor, isEditable);
        }

        public Entry(int viewId, Drawable mainIcon, String header, String subHeader,
                Drawable subHeaderIcon, String text, Drawable textIcon, Intent intent,
                Drawable alternateIcon, Intent alternateIntent, String alternateContentDescription,
                boolean shouldApplyColor, boolean isEditable) {
            mViewId = viewId;
            mIcon = mainIcon;
            mHeader = header;
            mSubHeader = subHeader;
            mSubHeaderIcon = subHeaderIcon;
            mText = text;
            mTextIcon = textIcon;
            mIntent = intent;
            mAlternateIcon = alternateIcon;
            mAlternateIntent = alternateIntent;
            mAlternateContentDescription = alternateContentDescription;
            mShouldApplyColor = shouldApplyColor;
            mIsEditable = isEditable;
        }

        Drawable getIcon() {
            return mIcon;
        }

        String getHeader() {
            return mHeader;
        }

        String getSubHeader() {
            return mSubHeader;
        }

        Drawable getSubHeaderIcon() {
            return mSubHeaderIcon;
        }

        public String getText() {
            return mText;
        }

        Drawable getTextIcon() {
            return mTextIcon;
        }

        Intent getIntent() {
            return mIntent;
        }

        Drawable getAlternateIcon() {
            return mAlternateIcon;
        }

        Intent getAlternateIntent() {
            return mAlternateIntent;
        }

        String getAlternateContentDescription() {
            return mAlternateContentDescription;
        }

        boolean shouldApplyColor() {
            return mShouldApplyColor;
        }

        boolean isEditable() {
            return mIsEditable;
        }

        int getViewId() {
            return mViewId;
        }
    }

    public interface ExpandingEntryCardViewListener {
        void onCollapse(int heightDelta);
    }

    private View mExpandCollapseButton;
    private TextView mExpandCollapseTextView;
    private TextView mTitleTextView;
    private CharSequence mExpandButtonText;
    private CharSequence mCollapseButtonText;
    private OnClickListener mOnClickListener;
    private boolean mIsExpanded = false;
    private int mCollapsedEntriesCount;
    private ExpandingEntryCardViewListener mListener;
    private List<List<Entry>> mEntries;
    private int mNumEntries = 0;
    private boolean mAllEntriesInflated = false;
    private List<List<View>> mEntryViews;
    private LinearLayout mEntriesViewGroup;
    private final Drawable mCollapseArrowDrawable;
    private final Drawable mExpandArrowDrawable;
    private int mThemeColor;
    private ColorFilter mThemeColorFilter;
    private boolean mIsAlwaysExpanded;

    private final OnClickListener mExpandCollapseButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mIsExpanded) {
                collapse();
            } else {
                expand();
            }
        }
    };

    public ExpandingEntryCardView(Context context) {
        this(context, null);
    }

    public ExpandingEntryCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = LayoutInflater.from(context);
        View expandingEntryCardView = inflater.inflate(R.layout.expanding_entry_card_view, this);
        mEntriesViewGroup = (LinearLayout)
                expandingEntryCardView.findViewById(R.id.content_area_linear_layout);
        mTitleTextView = (TextView) expandingEntryCardView.findViewById(R.id.title);
        mCollapseArrowDrawable =
                getResources().getDrawable(R.drawable.expanding_entry_card_collapse_white_24);
        mExpandArrowDrawable =
                getResources().getDrawable(R.drawable.expanding_entry_card_expand_white_24);

        mExpandCollapseButton = inflater.inflate(
                R.layout.quickcontact_expanding_entry_card_button, this, false);
        mExpandCollapseTextView = (TextView) mExpandCollapseButton.findViewById(R.id.text);
        mExpandCollapseButton.setOnClickListener(mExpandCollapseButtonListener);


    }

    /**
     * Sets the Entry list to display.
     *
     * @param entries The Entry list to display.
     */
    public void initialize(List<List<Entry>> entries, int numInitialVisibleEntries,
            boolean isExpanded, boolean isAlwaysExpanded,
            ExpandingEntryCardViewListener listener) {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        mIsExpanded = isExpanded;
        mIsAlwaysExpanded = isAlwaysExpanded;
        // If isAlwaysExpanded is true, mIsExpanded should be true
        mIsExpanded |= mIsAlwaysExpanded;
        mEntryViews = new ArrayList<List<View>>(entries.size());
        mEntries = entries;
        mNumEntries = 0;
        mAllEntriesInflated = false;
        for (List<Entry> entryList : mEntries) {
            mNumEntries += entryList.size();
            mEntryViews.add(new ArrayList<View>());
        }
        mCollapsedEntriesCount = Math.min(numInitialVisibleEntries, mNumEntries);
        // Only show the head of each entry list if the initial visible number falls between the
        // number of lists and the total number of entries
        if (mCollapsedEntriesCount > mEntries.size()) {
            mCollapsedEntriesCount = mEntries.size();
        }
        mListener = listener;

        if (mIsExpanded) {
            updateExpandCollapseButton(getCollapseButtonText());
            inflateAllEntries(layoutInflater);
        } else {
            updateExpandCollapseButton(getExpandButtonText());
            inflateInitialEntries(layoutInflater);
        }
        insertEntriesIntoViewGroup();
        applyColor();
    }

    /**
     * Sets the text for the expand button.
     *
     * @param expandButtonText The expand button text.
     */
    public void setExpandButtonText(CharSequence expandButtonText) {
        mExpandButtonText = expandButtonText;
        if (mExpandCollapseTextView != null && !mIsExpanded) {
            mExpandCollapseTextView.setText(expandButtonText);
        }
    }

    /**
     * Sets the text for the expand button.
     *
     * @param expandButtonText The expand button text.
     */
    public void setCollapseButtonText(CharSequence expandButtonText) {
        mCollapseButtonText = expandButtonText;
        if (mExpandCollapseTextView != null && mIsExpanded) {
            mExpandCollapseTextView.setText(mCollapseButtonText);
        }
    }

    @Override
    public void setOnClickListener(OnClickListener listener) {
        mOnClickListener = listener;
    }

    private void insertEntriesIntoViewGroup() {
        mEntriesViewGroup.removeAllViews();

        if (mIsExpanded) {
            for (List<View> viewList : mEntryViews) {
                for (View view : viewList) {
                    addEntry(view);
                }
            }
        } else {
            for (int i = 0; i < mCollapsedEntriesCount; i++) {
                addEntry(mEntryViews.get(i).get(0));
            }
        }

        removeView(mExpandCollapseButton);
        if (mCollapsedEntriesCount < mNumEntries
                && mExpandCollapseButton.getParent() == null && !mIsAlwaysExpanded) {
            addView(mExpandCollapseButton, -1);
        }
    }

    private void addEntry(View entry) {
        if (mEntriesViewGroup.getChildCount() > 0) {
            View separator = new View(getContext());
            separator.setBackgroundColor(getResources().getColor(
                    R.color.expanding_entry_card_item_separator_color));
            LayoutParams layoutParams = generateDefaultLayoutParams();
            Resources resources = getResources();
            layoutParams.height = resources.getDimensionPixelSize(
                    R.dimen.expanding_entry_card_item_separator_height);
            // The separator is aligned with the text in the entry. This is offset by a default
            // margin. If there is an icon present, the icon's width and margin are added
            int marginStart = resources.getDimensionPixelSize(
                    R.dimen.expanding_entry_card_item_padding_start);
            ImageView entryIcon = (ImageView) entry.findViewById(R.id.icon);
            if (entryIcon.getDrawable() != null) {
                int imageWidthAndMargin =
                        resources.getDimensionPixelSize(
                                R.dimen.expanding_entry_card_item_icon_width) +
                        resources.getDimensionPixelSize(
                                R.dimen.expanding_entry_card_item_image_spacing);
                marginStart += imageWidthAndMargin;
            }
            if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                layoutParams.rightMargin = marginStart;
            } else {
                layoutParams.leftMargin = marginStart;
            }
            separator.setLayoutParams(layoutParams);
            mEntriesViewGroup.addView(separator);
        }
        mEntriesViewGroup.addView(entry);
    }

    private CharSequence getExpandButtonText() {
        if (!TextUtils.isEmpty(mExpandButtonText)) {
            return mExpandButtonText;
        } else {
            // Default to "See more".
            return getResources().getText(R.string.expanding_entry_card_view_see_more);
        }
    }

    private CharSequence getCollapseButtonText() {
        if (!TextUtils.isEmpty(mCollapseButtonText)) {
            return mCollapseButtonText;
        } else {
            // Default to "See less".
            return getResources().getText(R.string.expanding_entry_card_view_see_less);
        }
    }

    /**
     * Inflates the initial entries to be shown.
     */
    private void inflateInitialEntries(LayoutInflater layoutInflater) {
        // If the number of collapsed entries equals total entries, inflate all
        if (mCollapsedEntriesCount == mNumEntries) {
            inflateAllEntries(layoutInflater);
        } else {
            // Otherwise inflate the top entry from each list
            for (int i = 0; i < mCollapsedEntriesCount; i++) {
                mEntryViews.get(i).add(createEntryView(layoutInflater, mEntries.get(i).get(0)));
            }
        }
    }

    /**
     * Inflates all entries.
     */
    private void inflateAllEntries(LayoutInflater layoutInflater) {
        if (mAllEntriesInflated) {
            return;
        }
        for (int i = 0; i < mEntries.size(); i++) {
            List<Entry> entryList = mEntries.get(i);
            List<View> viewList = mEntryViews.get(i);
            for (int j = viewList.size(); j < entryList.size(); j++) {
                viewList.add(createEntryView(layoutInflater, entryList.get(j)));
            }
        }
        mAllEntriesInflated = true;
    }

    public void setColorAndFilter(int color, ColorFilter colorFilter) {
        mThemeColor = color;
        mThemeColorFilter = colorFilter;
        applyColor();
    }

    public void setEntryHeaderColor(int color) {
        if (mEntries != null) {
            for (List<View> entryList : mEntryViews) {
                for (View entryView : entryList) {
                    TextView header = (TextView) entryView.findViewById(R.id.header);
                    if (header != null) {
                        header.setTextColor(color);
                    }
                }
            }
        }
    }

    /**
     * The ColorFilter is passed in along with the color so that a new one only needs to be created
     * once for the entire activity.
     * 1. Title
     * 2. Entry icons
     * 3. Expand/Collapse Text
     * 4. Expand/Collapse Button
     */
    public void applyColor() {
        if (mThemeColor != 0 && mThemeColorFilter != null) {
            // Title
            if (mTitleTextView != null) {
                mTitleTextView.setTextColor(mThemeColor);
            }

            // Entry icons
            if (mEntries != null) {
                for (List<Entry> entryList : mEntries) {
                    for (Entry entry : entryList) {
                        if (entry.shouldApplyColor()) {
                            Drawable icon = entry.getIcon();
                            if (icon != null) {
                                icon.setColorFilter(mThemeColorFilter);
                            }
                        }
                        Drawable alternateIcon = entry.getAlternateIcon();
                        if (alternateIcon != null) {
                            alternateIcon.setColorFilter(mThemeColorFilter);
                        }
                    }
                }
            }

            // Expand/Collapse
            mExpandCollapseTextView.setTextColor(mThemeColor);
            mCollapseArrowDrawable.setColorFilter(mThemeColorFilter);
            mExpandArrowDrawable.setColorFilter(mThemeColorFilter);
        }
    }

    private View createEntryView(LayoutInflater layoutInflater, Entry entry) {
        final View view = layoutInflater.inflate(
                R.layout.expanding_entry_card_item, this, false);

        view.setId(entry.getViewId());

        final ImageView icon = (ImageView) view.findViewById(R.id.icon);
        if (entry.getIcon() != null) {
            icon.setImageDrawable(entry.getIcon());
        } else {
            icon.setVisibility(View.GONE);
        }

        final TextView header = (TextView) view.findViewById(R.id.header);
        if (entry.getHeader() != null) {
            header.setText(entry.getHeader());
        } else {
            header.setVisibility(View.GONE);
        }

        final TextView subHeader = (TextView) view.findViewById(R.id.sub_header);
        if (entry.getSubHeader() != null) {
            subHeader.setText(entry.getSubHeader());
        } else {
            subHeader.setVisibility(View.GONE);
        }

        final ImageView subHeaderIcon = (ImageView) view.findViewById(R.id.icon_sub_header);
        if (entry.getSubHeaderIcon() != null) {
            subHeaderIcon.setImageDrawable(entry.getSubHeaderIcon());
        } else {
            subHeaderIcon.setVisibility(View.GONE);
        }

        final TextView text = (TextView) view.findViewById(R.id.text);
        if (entry.getText() != null) {
            text.setText(entry.getText());
        } else {
            text.setVisibility(View.GONE);
        }

        final ImageView textIcon = (ImageView) view.findViewById(R.id.icon_text);
        if (entry.getTextIcon() != null) {
            textIcon.setImageDrawable(entry.getTextIcon());
        } else {
            textIcon.setVisibility(View.GONE);
        }

        if (entry.getIntent() != null) {
            view.setOnClickListener(mOnClickListener);
            view.setTag(entry.getIntent());
        }

        final ImageView alternateIcon = (ImageView) view.findViewById(R.id.icon_alternate);
        if (entry.getAlternateIcon() != null && entry.getAlternateIntent() != null) {
            alternateIcon.setImageDrawable(entry.getAlternateIcon());
            alternateIcon.setOnClickListener(mOnClickListener);
            alternateIcon.setTag(entry.getAlternateIntent());
            alternateIcon.setId(entry.getViewId());
            alternateIcon.setVisibility(View.VISIBLE);
            alternateIcon.setContentDescription(entry.getAlternateContentDescription());

            // Expand the clickable area for alternate icon to be top to bottom and to end edge
            // of the entry view
            view.post(new Runnable() {
                @Override
                public void run() {
                    final Rect alternateIconRect = new Rect();
                    alternateIcon.getHitRect(alternateIconRect);

                    alternateIconRect.bottom = view.getHeight();
                    alternateIconRect.top = 0;
                    if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                        alternateIconRect.left = 0;
                    } else {
                        alternateIconRect.right = view.getWidth();
                    }
                    final TouchDelegate touchDelegate =
                            new TouchDelegate(alternateIconRect, alternateIcon);
                    view.setTouchDelegate(touchDelegate);
                }
            });
        }

        return view;
    }

    private void updateExpandCollapseButton(CharSequence buttonText) {
        final Drawable arrow = mIsExpanded ? mCollapseArrowDrawable : mExpandArrowDrawable;
        if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            mExpandCollapseTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, arrow,
                    null);
        } else {
            mExpandCollapseTextView.setCompoundDrawablesWithIntrinsicBounds(arrow, null, null,
                    null);
        }
        mExpandCollapseTextView.setText(buttonText);
    }

    private void expand() {
        final int startingHeight = mEntriesViewGroup.getHeight();

        mIsExpanded = true;
        // In order to insert new entries, we may need to inflate them for the first time
        inflateAllEntries(LayoutInflater.from(getContext()));
        insertEntriesIntoViewGroup();
        updateExpandCollapseButton(getCollapseButtonText());

        // When expanding, all the TextViews haven't been laid out yet. Therefore,
        // calling measure() would return an incorrect result. Therefore, we need a pre draw
        // listener.
        final ViewTreeObserver observer = mEntriesViewGroup.getViewTreeObserver();
        observer.addOnPreDrawListener(new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (observer.isAlive()) {
                    mEntriesViewGroup.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                createExpandAnimator(startingHeight, mEntriesViewGroup.getHeight()).start();
                // Do not draw the final frame of the animation immediately.
                return false;
            }
        });
    }

    private void collapse() {
        int startingHeight = mEntriesViewGroup.getHeight();
        int finishHeight = measureCollapsedViewGroupHeight();
        mListener.onCollapse(startingHeight - finishHeight);

        mIsExpanded = false;
        updateExpandCollapseButton(getExpandButtonText());
        createExpandAnimator(startingHeight, finishHeight).start();
    }

    private int measureCollapsedViewGroupHeight() {
        if (mCollapsedEntriesCount == 0) {
            return 0;
        }
        final View bottomCollapsedView = mEntryViews.get(mCollapsedEntriesCount - 1).get(0);
        return bottomCollapsedView.getTop() + bottomCollapsedView.getHeight();
    }

    /**
     * Create ValueAnimator that performs an expand animation on the content LinearLayout.
     *
     * The animation needs to be performed manually using a ValueAnimator, since LinearLayout
     * doesn't have a single set-able height property (ie, no setHeight()).
     */
    private ValueAnimator createExpandAnimator(int start, int end) {
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int value = (Integer) valueAnimator.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams = mEntriesViewGroup.getLayoutParams();
                layoutParams.height = value;
                mEntriesViewGroup.setLayoutParams(layoutParams);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                insertEntriesIntoViewGroup();
                // Now that the animation is done, stop using a fixed height.
                ViewGroup.LayoutParams layoutParams = mEntriesViewGroup.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                mEntriesViewGroup.setLayoutParams(layoutParams);
            }
        });
        return animator;
    }

    /**
     * Returns whether the view is currently in its expanded state.
     */
    public boolean isExpanded() {
        return mIsExpanded;
    }

    /**
     * Sets the title text of this ExpandingEntryCardView.
     * @param title The title to set. A null title will result in the title being removed.
     */
    public void setTitle(String title) {
        if (mTitleTextView == null) {
            Log.e(TAG, "mTitleTextView is null");
        }
        if (title == null) {
            mTitleTextView.setVisibility(View.GONE);
            findViewById(R.id.title_separator).setVisibility(View.GONE);
        }
        mTitleTextView.setText(title);
        mTitleTextView.setVisibility(View.VISIBLE);
        findViewById(R.id.title_separator).setVisibility(View.VISIBLE);
    }

    public boolean shouldShow() {
        return mEntries != null && mEntries.size() > 0;
    }
}
