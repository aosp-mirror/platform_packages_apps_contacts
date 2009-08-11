package com.android.contacts;

import android.content.AsyncQueryHandler;
import android.content.Context;
import android.content.EntityIterator;
import android.database.Cursor;

import java.lang.ref.WeakReference;

/**
 * Slightly more abstract {@link android.content.AsyncQueryHandler} that helps
 * keep a {@link WeakReference} back to a callback interface. Will properly
 * close the completed query if the listener ceases to exist.
 * <p>
 * Using this pattern will help keep you from leaking a {@link Context}.
 */
public class NotifyingAsyncQueryHandler extends AsyncQueryHandler {
    private WeakReference<AsyncQueryListener> mListener;

    /**
     * Interface to listen for completed query operations.
     */
    public interface AsyncQueryListener {
        void onQueryComplete(int token, Object cookie, Cursor cursor);
        void onQueryEntitiesComplete(int token, Object cookie, EntityIterator iterator);
    }

    public NotifyingAsyncQueryHandler(Context context, AsyncQueryListener listener) {
        super(context.getContentResolver());
        setQueryListener(listener);
    }

    /**
     * Assign the given {@link AsyncQueryListener} to receive query events from
     * asynchronous calls. Will replace any existing listener.
     */
    public void setQueryListener(AsyncQueryListener listener) {
        mListener = new WeakReference<AsyncQueryListener>(listener);
    }

    /** {@inheritDoc} */
    @Override
    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        final AsyncQueryListener listener = mListener.get();
        if (listener != null) {
            listener.onQueryComplete(token, cookie, cursor);
        } else if (cursor != null) {
            cursor.close();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onQueryEntitiesComplete(int token, Object cookie, EntityIterator iterator) {
        final AsyncQueryListener listener = mListener.get();
        if (listener != null) {
            listener.onQueryEntitiesComplete(token, cookie, iterator);
        } else if (iterator != null) {
            iterator.close();
        }
    }
}
