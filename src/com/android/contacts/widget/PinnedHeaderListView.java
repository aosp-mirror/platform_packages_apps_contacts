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

package com.android.contacts.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemSelectedListener;

/**
 * A ListView that maintains a header pinned at the top of the list. The
 * pinned header can be pushed up and dissolved as needed.
 */
public class PinnedHeaderListView extends ListView
        implements OnScrollListener, OnItemSelectedListener {

    /**
     * Adapter interface.  The list adapter must implement this interface.
     */
    public interface PinnedHeaderAdapter {

        /**
         * Returns the overall number of pinned headers, visible or not.
         */
        int getPinnedHeaderCount();

        /**
         * Creates the pinned header view.
         */
        View createPinnedHeaderView(int viewIndex, ViewGroup parent);

        /**
         * Configures the pinned headers to match the visible list items. The
         * adapter should call {@link PinnedHeaderListView#setHeaderPinnedAtTop},
         * {@link PinnedHeaderListView#setHeaderPinnedAtBottom},
         * {@link PinnedHeaderListView#setFadingHeader} or
         * {@link PinnedHeaderListView#setHeaderInvisible}, for each header that
         * needs to change its position or visibility.
         */
        void configurePinnedHeaders(PinnedHeaderListView listView);
    }

    private static final int MAX_ALPHA = 255;
    private static final int TOP = 0;
    private static final int BOTTOM = 1;
    private static final int FADING = 2;

    private static final class PinnedHeader {
        View view;
        boolean visible;
        int y;
        int height;
        int alpha;
        int state;
    }

    private PinnedHeaderAdapter mAdapter;
    private PinnedHeader[] mHeaders;
    private int mPinnedHeaderBackgroundColor;
    private RectF mBounds = new RectF();
    private Paint mPaint = new Paint();
    private OnScrollListener mOnScrollListener;
    private OnItemSelectedListener mOnItemSelectedListener;

    public PinnedHeaderListView(Context context) {
        this(context, null, com.android.internal.R.attr.listViewStyle);
    }

    public PinnedHeaderListView(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.listViewStyle);
    }

    public PinnedHeaderListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        super.setOnScrollListener(this);
        super.setOnItemSelectedListener(this);
    }

    /**
     * An approximation of the background color of the pinned header. This color
     * is used when the pinned header is being pushed up. At that point the
     * header "fades away". Rather than computing a faded bitmap based on the
     * 9-patch normally used for the background, we will use a solid color,
     * which will provide better performance and reduced complexity.
     */
    public void setPinnedHeaderBackgroundColor(int color) {
        mPinnedHeaderBackgroundColor = color;
        mPaint.setColor(mPinnedHeaderBackgroundColor);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
        mAdapter = (PinnedHeaderAdapter)adapter;
        int count = mAdapter.getPinnedHeaderCount();
        mHeaders = new PinnedHeader[count];
        for (int i = 0; i < count; i++) {
            PinnedHeader header = new PinnedHeader();
            header.view = mAdapter.createPinnedHeaderView(i, this);
            mHeaders[i] = header;
        }

        // Disable vertical fading when the pinned header is present
        // TODO change ListView to allow separate measures for top and bottom fading edge;
        // in this particular case we would like to disable the top, but not the bottom edge.
        if (count > 0) {
            setFadingEdgeLength(0);
        }

        requestLayout();
    }

    @Override
    public void setOnScrollListener(OnScrollListener onScrollListener) {
        mOnScrollListener = onScrollListener;
        super.setOnScrollListener(this);
    }

    @Override
    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        mOnItemSelectedListener = listener;
        super.setOnItemSelectedListener(this);
    }

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        if (mAdapter != null) {
            mAdapter.configurePinnedHeaders(this);
        }
        if (mOnScrollListener != null) {
            mOnScrollListener.onScroll(this, firstVisibleItem, visibleItemCount, totalItemCount);
        }
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChanged(this, scrollState);
        }
    }

    /**
     * Ensures that the selected item is positioned below the top-pinned headers
     * and above the bottom-pinned ones.
     */
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int height = getHeight();

        int windowTop = 0;
        int windowBottom = height;

        int prevHeaderBottom = 0;
        for (int i = 0; i < mHeaders.length; i++) {
            PinnedHeader header = mHeaders[i];
            if (header.visible) {
                if (header.state == TOP) {
                    windowTop = header.y + header.height;
                } else if (header.state == BOTTOM) {
                    windowBottom = header.y;
                    break;
                }
            }
        }

        View selectedView = getSelectedView();
        if (selectedView.getTop() < windowTop) {
            setSelectionFromTop(position, windowTop);
        } else if (selectedView.getBottom() > windowBottom) {
            setSelectionFromTop(position, windowBottom - selectedView.getHeight());
        }

        if (mOnItemSelectedListener != null) {
            mOnItemSelectedListener.onItemSelected(parent, view, position, id);
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
        if (mOnItemSelectedListener != null) {
            mOnItemSelectedListener.onNothingSelected(parent);
        }
    }

    public int getPinnedHeaderHeight(int viewIndex) {
        ensurePinnedHeaderLayout(viewIndex);
        return mHeaders[viewIndex].view.getHeight();
    }

    /**
     * Set header to be pinned at the top.
     *
     * @param viewIndex index of the header view
     * @param y is position of the header in pixels.
     */
    public void setHeaderPinnedAtTop(int viewIndex, int y) {
        ensurePinnedHeaderLayout(viewIndex);
        PinnedHeader header = mHeaders[viewIndex];
        header.visible = true;
        header.y = y;
        header.state = TOP;
    }

    /**
     * Set header to be pinned at the bottom.
     *
     * @param viewIndex index of the header view
     * @param y is position of the header in pixels.
     */
    public void setHeaderPinnedAtBottom(int viewIndex, int y) {
        ensurePinnedHeaderLayout(viewIndex);
        PinnedHeader header = mHeaders[viewIndex];
        header.visible = true;
        header.y = y;
        header.state = BOTTOM;
    }

    /**
     * Set header to be pinned at the top of the first visible item.
     *
     * @param viewIndex index of the header view
     * @param position is position of the header in pixels.
     */
    public void setFadingHeader(int viewIndex, int position, boolean fade) {
        ensurePinnedHeaderLayout(viewIndex);

        View child = getChildAt(position - getFirstVisiblePosition());

        PinnedHeader header = mHeaders[viewIndex];
        header.visible = true;
        header.state = FADING;
        header.alpha = MAX_ALPHA;

        int top = getTotalTopPinnedHeaderHeight();
        header.y = top;
        if (fade) {
            int bottom = child.getBottom() - top;
            int headerHeight = header.height;
            if (bottom < headerHeight) {
                int portion = bottom - headerHeight;
                header.alpha = MAX_ALPHA * (headerHeight + portion) / headerHeight;
                header.y = top + portion;
            }
        }
    }

    public void setHeaderInvisible(int viewIndex) {
        mHeaders[viewIndex].visible = false;
    }

    private void ensurePinnedHeaderLayout(int viewIndex) {
        View view = mHeaders[viewIndex].view;
        if (view.isLayoutRequested()) {
            view.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.UNSPECIFIED);
            int height = view.getMeasuredHeight();
            mHeaders[viewIndex].height = height;
            view.layout(0, 0, view.getMeasuredWidth(), height);
        }
    }

    /**
     * Returns the sum of heights of headers pinned to the top.
     */
    public int getTotalTopPinnedHeaderHeight() {
        for (int i = mHeaders.length; --i >= 0;) {
            PinnedHeader header = mHeaders[i];
            if (header.visible && header.state == TOP) {
                return header.y + header.height;
            }
        }
        return 0;
    }

    /**
     * Returns the list item position at the specified y coordinate.
     */
    public int getPositionAt(int y) {
        do {
            int position = pointToPosition(0, y);
            if (position != -1) {
                return position;
            }
            // If position == -1, we must have hit a separator. Let's examine
            // a nearby pixel
            y--;
        } while (y > 0);
        return 0;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        for (int i = mHeaders.length; --i >= 0;) {
            PinnedHeader header = mHeaders[i];
            if (header.visible) {
                View view = header.view;
                if (header.state == FADING) {
                    int saveCount = canvas.save();
                    canvas.translate(0, header.y);
                    mBounds.set(0, 0, view.getWidth(), view.getHeight());
                    canvas.drawRect(mBounds, mPaint);
                    canvas.saveLayerAlpha(mBounds, header.alpha, Canvas.ALL_SAVE_FLAG);
                    view.draw(canvas);
                    canvas.restoreToCount(saveCount);
                } else {
                    canvas.save();
                    canvas.translate(0, header.y);
                    view.draw(canvas);
                    canvas.restore();
                }
            }
        }
    }
}
