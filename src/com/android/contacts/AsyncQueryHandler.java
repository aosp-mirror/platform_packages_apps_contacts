package com.android.contacts;

import android.content.Context;
import android.database.Cursor;

import java.lang.ref.WeakReference;

/**
 * Slightly more abstract {@link android.content.AsyncQueryHandler} that helps
 * keep a {@link WeakReference} back to a callback interface. Will properly
 * close the completed query if the listener ceases to exist.
 * <p>
 * Using this pattern will help keep you from leaking a {@link Context}.
 */
public class AsyncQueryHandler extends android.content.AsyncQueryHandler {
    private final WeakReference<QueryCompleteListener> mListener;

    /**
     * Interface to listen for completed queries.
     */
    public static interface QueryCompleteListener {
        public void onQueryComplete(int token, Object cookie, Cursor cursor);
    }

    public AsyncQueryHandler(Context context, QueryCompleteListener listener) {
        super(context.getContentResolver());
        mListener = new WeakReference<QueryCompleteListener>(listener);
    }

    /** {@inheritDoc} */
    @Override
    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        final QueryCompleteListener listener = mListener.get();
        if (listener != null) {
            listener.onQueryComplete(token, cookie, cursor);
        } else {
            cursor.close();
        }
    }
}
