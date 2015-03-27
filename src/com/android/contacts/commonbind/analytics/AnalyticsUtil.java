package com.android.contacts.commonbind.analytics;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.text.TextUtils;

public class AnalyticsUtil {

    /**
     * Initialize this class and setup automatic activity tracking.
     */
    public static void initialize(Application application) { }

    /**
     * Log a screen view for {@param fragment}.
     */
    public static void sendScreenView(Fragment fragment) {}

    public static void sendScreenView(Fragment fragment, Activity activity) {}

    public static void sendScreenView(Fragment fragment, Activity activity, String tag) {}

    public static void sendScreenView(String fragmentName, Activity activity, String tag) {}

    /**
     * Logs a event to the analytics server.
     *
     * @param application The application the tracker is stored in.
     * @param category The category for the event.
     * @param action The event action.
     * @param label The event label.
     * @param value The value associated with the event.
     */
    public static void sendEvent(Application application, String category, String action,
            String label, long value) { }
}