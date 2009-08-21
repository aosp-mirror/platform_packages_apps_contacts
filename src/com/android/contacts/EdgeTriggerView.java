/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Lightweight view that wraps around an existing view, watching and capturing
 * sliding gestures from the left or right edges within a given tolerance.
 */
public class EdgeTriggerView extends FrameLayout {
    public static final int FLAG_NONE = 0x00;
    public static final int FLAG_LEFT = 0x01;
    public static final int FLAG_RIGHT = 0x02;

    private int mTouchSlop;

    private int mEdgeWidth;
    private int mListenEdges;

    private boolean mListenLeft = false;
    private boolean mListenRight = false;

    private MotionEvent mDownStart;
    private int mEdge = FLAG_NONE;

    public static interface EdgeTriggerListener {
        public void onTrigger(float downX, float downY, int edge);
    }

    private EdgeTriggerListener mListener;

    /**
     * Add a {@link EdgeTriggerListener} to watch for edge events.
     */
    public void setOnEdgeTriggerListener(EdgeTriggerListener listener) {
        mListener = listener;
    }

    public EdgeTriggerView(Context context) {
        this(context, null);
    }

    public EdgeTriggerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EdgeTriggerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setClickable(true);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // TODO: enable reading these values again once we move away from symlinks
//        TypedArray a = context.obtainStyledAttributes(attrs,
//                R.styleable.EdgeTriggerView, defStyle, -1);
//        mEdgeWidth = a.getDimensionPixelSize(R.styleable.EdgeTriggerView_edgeWidth, mTouchSlop);
//        mListenEdges = a.getInt(R.styleable.EdgeTriggerView_listenEdges, FLAG_LEFT);

        mEdgeWidth = 80;
        mListenEdges = FLAG_LEFT;

        mListenLeft = (mListenEdges & FLAG_LEFT) == FLAG_LEFT;
        mListenRight = (mListenEdges & FLAG_RIGHT) == FLAG_RIGHT;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                // Consider watching this touch event based on listening flags
                final float x = event.getX();
                if (mListenLeft && x < mEdgeWidth) {
                    mEdge = FLAG_LEFT;
                } else if (mListenRight && x > getWidth() - mEdgeWidth) {
                    mEdge = FLAG_RIGHT;
                } else {
                    mEdge = FLAG_NONE;
                }

                if (mEdge != FLAG_NONE) {
                    mDownStart = MotionEvent.obtain(event);
                } else {
                    mDownStart = null;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mEdge != FLAG_NONE) {
                    // If moved far enough, capture touch event for ourselves
                    float delta = event.getX() - mDownStart.getX();
                    if (mEdge == FLAG_LEFT && delta > mTouchSlop) {
                        return true;
                    } else if (mEdge == FLAG_RIGHT && delta < -mTouchSlop) {
                        return true;
                    }
                }
                break;
            }
        }

        // Otherwise let the event slip through to children
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Pass trigger event to listener and return true to consume
        if (mEdge != FLAG_NONE && mListener != null) {
            mListener.onTrigger(mDownStart.getX(), mDownStart.getY(), mEdge);

            // Reset values so we don't sent twice
            mEdge = FLAG_NONE;
            mDownStart = null;
        }
        return true;
    }
}
