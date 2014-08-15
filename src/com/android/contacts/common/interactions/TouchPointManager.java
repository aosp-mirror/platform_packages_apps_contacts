package com.android.contacts.common.interactions;

import android.graphics.Point;

/**
 * Singleton class to keep track of where the user last touched the screen.
 *
 * Used to pass on to the InCallUI for animation.
 */
public class TouchPointManager {
    public static final String TOUCH_POINT = "touchPoint";

    private static TouchPointManager sInstance = new TouchPointManager();

    private Point mPoint = new Point();

    /**
     * Private constructor.  Instance should only be acquired through getInstance().
     */
    private TouchPointManager() {
    }

    public static TouchPointManager getInstance() {
        return sInstance;
    }

    public Point getPoint() {
        return mPoint;
    }

    public void setPoint(int x, int y) {
        mPoint.set(x, y);
    }
}
