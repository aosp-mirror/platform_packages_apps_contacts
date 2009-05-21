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

import com.android.contacts.NotifyingAsyncQueryHandler.QueryCompleteListener;
import com.android.contacts.SocialStreamActivity.MappingCache;
import com.android.contacts.SocialStreamActivity.Mapping;
import com.android.internal.policy.PolicyManager;
import com.android.providers.contacts2.ContactsContract;
import com.android.providers.contacts2.ContactsContract.Aggregates;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds;
import com.android.providers.contacts2.ContactsContract.Data;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Email;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Im;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Phone;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Photo;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Postal;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Contacts.Phones;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Gallery.LayoutParams;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Window that shows fast-track contact details for a specific
 * {@link Aggregate#_ID}.
 */
public class FastTrackWindow implements Window.Callback, QueryCompleteListener, OnClickListener {
    private static final String TAG = "FastTrackWindow";

    /**
     * Interface used to allow the person showing a {@link FastTrackWindow} to
     * know when the window has been dismissed.
     */
    interface OnDismissListener {
        public void onDismiss(FastTrackWindow dialog);
    }

    final Context mContext;
    final LayoutInflater mInflater;
    final WindowManager mWindowManager;
    Window mWindow;
    View mDecor;

    private boolean mQuerying = false;
    private boolean mShowing = false;

    /** Mapping cache from mime-type to icons and actions */
    private MappingCache mMappingCache;

    private NotifyingAsyncQueryHandler mHandler;
    private OnDismissListener mDismissListener;

    private long mAggId;
    private int mAnchorX;
    private int mAnchorY;
    private int mAnchorHeight;

    private boolean mHasProfile = false;
    private boolean mHasActions = false;

    private View mArrowUp;
    private View mArrowDown;

    private ImageView mPhoto;
    private ImageView mPresence;
    private TextView mContent;
    private TextView mPublished;
    private ViewGroup mTrack;

    // TODO: read from a resource somewhere
    private static final int mHeight = 138;
    private static final int mArrowHeight = 10;
    private static final int mArrowWidth = 24;

    /**
     * Set of {@link ActionInfo} that are associated with the aggregate
     * currently displayed by this fast-track window.
     */
    private ActionSet mActions = new ActionSet();

    /**
     * Specific mime-type for {@link Phone#CONTENT_ITEM_TYPE} entries that
     * distinguishes actions that should initiate a text message.
     */
    public static final String MIME_SMS_ADDRESS = "vnd.android.cursor.item/sms-address";

    /**
     * Specific mime-types that should be bumped to the front of the fast-track.
     * Other mime-types not appearing in this list follow in alphabetic order.
     */
    private static final String[] ORDERED_MIMETYPES = new String[] {
        Aggregates.CONTENT_ITEM_TYPE,
        Phones.CONTENT_ITEM_TYPE,
        MIME_SMS_ADDRESS,
        Email.CONTENT_ITEM_TYPE,
    };

//    public static final int ICON_SIZE = 42;
//    public static final int ICON_PADDING = 3;

    // TODO: read this status from actual query
    private static final String STUB_STATUS = "has a really long random status message that would be far too long to read on a single device without the need for tiny reading glasses";

    private static final boolean INCLUDE_PROFILE_ACTION = true;

    private static final int TOKEN_DISPLAY_NAME = 1;
    private static final int TOKEN_DATA = 2;

    /** Message to show when no activity is found to perform an action */
    // TODO: move this value into a resources string
    private static final String NOT_FOUND = "Couldn't find an app to handle this action";

    /**
     * Prepare a fast-track window to show in the given {@link Context}.
     */
    public FastTrackWindow(Context context) {
        mContext = new ContextThemeWrapper(context, R.style.FastTrack);
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

        mWindow = PolicyManager.makeNewWindow(mContext);
        mWindow.setCallback(this);
        mWindow.setWindowManager(mWindowManager, null, null);

        mWindow.setContentView(R.layout.fasttrack);

        mArrowUp = (View)mWindow.findViewById(R.id.arrow_up);
        mArrowDown = (View)mWindow.findViewById(R.id.arrow_down);
        
        mPhoto = (ImageView)mWindow.findViewById(R.id.photo);
        mPresence = (ImageView)mWindow.findViewById(R.id.presence);
        mContent = (TextView)mWindow.findViewById(R.id.content);
        mPublished = (TextView)mWindow.findViewById(R.id.published);
        mTrack = (ViewGroup)mWindow.findViewById(R.id.fasttrack);

        // TODO: move generation of mime-type cache to more-efficient place
        generateMappingCache();

    }

    /**
     * Prepare a fast-track window to show in the given {@link Context}, and
     * notify the given {@link OnDismissListener} each time this dialog is
     * dismissed.
     */
    public FastTrackWindow(Context context, OnDismissListener dismissListener) {
        this(context);
        mDismissListener = dismissListener;
    }

    /**
     * Generate {@link MappingCache} specifically for fast-track windows. This
     * cache knows how to display {@link CommonDataKinds#PACKAGE_COMMON} data
     * types using generic icons.
     */
    private void generateMappingCache() {
        mMappingCache = MappingCache.createAndFill(mContext);

        Resources res = mContext.getResources();
        Mapping mapping;

        mapping = new Mapping(CommonDataKinds.PACKAGE_COMMON, Aggregates.CONTENT_ITEM_TYPE);
        mapping.icon = BitmapFactory.decodeResource(res, R.drawable.ic_contacts_details);
        mMappingCache.addMapping(mapping);

        mapping = new Mapping(CommonDataKinds.PACKAGE_COMMON, Phone.CONTENT_ITEM_TYPE);
        mapping.summaryColumn = Phone.NUMBER;
        mapping.icon = BitmapFactory.decodeResource(res, android.R.drawable.sym_action_call);
        mMappingCache.addMapping(mapping);

        mapping = new Mapping(CommonDataKinds.PACKAGE_COMMON, MIME_SMS_ADDRESS);
        mapping.summaryColumn = Phone.NUMBER;
        mapping.icon = BitmapFactory.decodeResource(res, R.drawable.sym_action_sms);
        mMappingCache.addMapping(mapping);

        mapping = new Mapping(CommonDataKinds.PACKAGE_COMMON, Email.CONTENT_ITEM_TYPE);
        mapping.summaryColumn = Email.DATA;
        mapping.icon = BitmapFactory.decodeResource(res, android.R.drawable.sym_action_email);
        mMappingCache.addMapping(mapping);

    }

    /**
     * Start showing a fast-track window for the given {@link Aggregate#_ID}
     * pointing towards the given location.
     */
    public void show(Uri aggUri, int x, int y, int height) {
        if (mShowing || mQuerying) {
            Log.w(TAG, "already in process of showing");
            return;
        }

        mAggId = ContentUris.parseId(aggUri);
        mAnchorX = x;
        mAnchorY = y;
        mAnchorHeight = height;
        mQuerying = true;

        // Start data query in background
        Uri dataUri = Uri.withAppendedPath(aggUri,
                ContactsContract.Aggregates.Data.CONTENT_DIRECTORY);

        // TODO: also query for latest status message
        mHandler = new NotifyingAsyncQueryHandler(mContext, this);
        mHandler.startQuery(TOKEN_DISPLAY_NAME, null, aggUri, null, null, null, null);
        mHandler.startQuery(TOKEN_DATA, null, dataUri, null, null, null, null);

    }

    /**
     * Show the correct callout arrow based on a {@link R.id} reference.
     */
    private void showArrow(int whichArrow, int requestedX) {
        final View showArrow = (whichArrow == R.id.arrow_up) ? mArrowUp : mArrowDown;
        final View hideArrow = (whichArrow == R.id.arrow_up) ? mArrowDown : mArrowUp;
        
        showArrow.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams param = (LinearLayout.LayoutParams)showArrow.getLayoutParams();
        param.leftMargin = requestedX - mArrowWidth / 2;
        
        hideArrow.setVisibility(View.INVISIBLE);
    }

    /**
     * Actual internal method to show this fast-track window. Called only by
     * {@link #considerShowing()} when all data requirements have been met.
     */
    private void showInternal() {
        mDecor = mWindow.getDecorView();
        WindowManager.LayoutParams l = mWindow.getAttributes();
        
        l.gravity = Gravity.TOP | Gravity.LEFT;
        l.x = 0;
        
        if (mAnchorY > mHeight) {
            // Show downwards callout when enough room
            showArrow(R.id.arrow_down, mAnchorX);
            l.y = mAnchorY - (mHeight - (mArrowHeight * 2) - 5);
            
        } else {
            // Otherwise show upwards callout
            showArrow(R.id.arrow_up, mAnchorX);
            l.y = mAnchorY + mAnchorHeight - 10;
            
        }
        
        l.width = WindowManager.LayoutParams.FILL_PARENT;
        l.height = mHeight;

        l.dimAmount = 0.6f;
        l.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;

        mWindowManager.addView(mDecor, l);
        mShowing = true;
        mQuerying = false;
    }

    /**
     * Dismiss this fast-track window if showing.
     */
    public void dismiss() {
        if (!mQuerying && !mShowing) {
            Log.d(TAG, "not visible, ignore");
            return;
        }

        // Cancel any pending queries
        mHandler.cancelOperation(TOKEN_DISPLAY_NAME);
        mHandler.cancelOperation(TOKEN_DATA);

        // Reset all views to prepare for possible recycling
        mPhoto.setImageResource(R.drawable.ic_contact_list_picture);
        mPresence.setImageDrawable(null);
        mContent.setText(null);
        mPublished.setText(null);

        mActions.clear();
        mTrack.removeAllViews();

        mHasProfile = false;
        mHasActions = false;

        if (mDecor == null || !mShowing) {
            Log.d(TAG, "not showing, ignore");
            return;
        }

        mWindowManager.removeView(mDecor);
        mDecor = null;
        mWindow.closeAllPanels();
        mShowing = false;

        // Notify any listeners that we've been dismissed
        if (mDismissListener != null) {
            mDismissListener.onDismiss(this);
        }
    }

    /**
     * Returns true if this fast-track window is showing or querying.
     */
    public boolean isShowing() {
        return mShowing || mQuerying;
    }

    /**
     * Consider showing this window, which will only call through to
     * {@link #showInternal()} when all data items are present.
     */
    private synchronized void considerShowing() {
        if (mHasActions && mHasProfile && !mShowing) {
            showInternal();
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cursor == null) {
            return;
        } else if (token == TOKEN_DISPLAY_NAME) {
            handleDisplayName(cursor);
        } else if (token == TOKEN_DATA) {
            handleData(cursor);
        }
    }
    
    private SpannableStringBuilder mBuilder = new SpannableStringBuilder();
    private CharacterStyle mStyleBold = new StyleSpan(android.graphics.Typeface.BOLD);
    private CharacterStyle mStyleBlack = new ForegroundColorSpan(Color.BLACK);

    /**
     * Handle the result from the {@link TOKEN_DISPLAY_NAME} query.
     */
    private void handleDisplayName(Cursor cursor) {
        final int COL_DISPLAY_NAME = cursor.getColumnIndex(Aggregates.DISPLAY_NAME);

        if (cursor.moveToNext()) {
            String foundName = cursor.getString(COL_DISPLAY_NAME);
            
            mBuilder.clear();
            mBuilder.append(foundName);
            mBuilder.append(" ");
            mBuilder.append(STUB_STATUS);
            mBuilder.setSpan(mStyleBold, 0, foundName.length(), 0);
            mBuilder.setSpan(mStyleBlack, 0, foundName.length(), 0);
            mContent.setText(mBuilder);
            
            mPublished.setText("4 hours ago");

        }

        mHasProfile = true;
        considerShowing();
    }

    /**
     * Description of a specific, actionable {@link Data#_ID} item. May have a
     * {@link Mapping} associated with it to find {@link RemoteViews} or icon,
     * and may have built a summary of itself for UI display.
     */
    private class ActionInfo {
        long dataId;
        String packageName;
        String mimeType;

        Mapping mapping;
        String summaryValue;

        /**
         * Create an action from common {@link Data} elements.
         */
        public ActionInfo(long dataId, String packageName, String mimeType) {
            this.dataId = dataId;
            this.packageName = packageName;
            this.mimeType = mimeType;
        }

        /**
         * Attempt to find a {@link Mapping} for the package and mime-type
         * defined by this action. Returns true if one was found.
         */
        public boolean findMapping(MappingCache cache) {
            mapping = cache.findMapping(packageName, mimeType);
            return (mapping != null);
        }

        /**
         * Given a {@link Cursor} pointed at the {@link Data} row associated
         * with this action, use the {@link Mapping} to build a text summary.
         */
        public void buildSummary(Cursor cursor) {
            if (mapping == null || mapping.summaryColumn == null) return;
            int index = cursor.getColumnIndex(mapping.summaryColumn);
            if (index != -1) {
                summaryValue = cursor.getString(index);
            }
        }

        /**
         * Build an {@link Intent} that will perform this action.
         */
        public Intent buildIntent() {
            // Handle well-known mime-types with special care
            if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                Uri callUri = Uri.parse("tel:" + Uri.encode(summaryValue));
                return new Intent(Intent.ACTION_DIAL, callUri);

            } else if (MIME_SMS_ADDRESS.equals(mimeType)) {
                Uri smsUri = Uri.fromParts("smsto", summaryValue, null);
                return new Intent(Intent.ACTION_SENDTO, smsUri);

            } else if (CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                Uri mailUri = Uri.fromParts("mailto", summaryValue, null);
                return new Intent(Intent.ACTION_SENDTO, mailUri);

            }

            // Otherwise fall back to default VIEW action
            Uri dataUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(dataUri);

            return intent;
        }
    }

    /**
     * Provide a simple way of collecting one or more {@link ActionInfo} objects
     * under a mime-type key.
     */
    private class ActionSet extends HashMap<String, LinkedList<ActionInfo>> {
        private void collect(String mimeType, ActionInfo info) {
            // Create mime-type set if needed
            if (!containsKey(mimeType)) {
                put(mimeType, new LinkedList<ActionInfo>());
            }
            LinkedList<ActionInfo> collectList = get(mimeType);
            collectList.add(info);
        }
    }

    /**
     * Handle the result from the {@link TOKEN_DATA} query.
     */
    private void handleData(Cursor cursor) {
        final int COL_ID = cursor.getColumnIndex(Data._ID);
        final int COL_PACKAGE = cursor.getColumnIndex(Data.PACKAGE);
        final int COL_MIMETYPE = cursor.getColumnIndex(Data.MIMETYPE);
        final int COL_PHOTO = cursor.getColumnIndex(Photo.PHOTO);

        ActionInfo info;

        // Add the profile shortcut action if requested
        if (INCLUDE_PROFILE_ACTION) {
            final String mimeType = Aggregates.CONTENT_ITEM_TYPE;
            info = new ActionInfo(mAggId, CommonDataKinds.PACKAGE_COMMON, mimeType);
            if (info.findMapping(mMappingCache)) {
                mActions.collect(mimeType, info);
            }
        }

        while (cursor.moveToNext()) {
            final long dataId = cursor.getLong(COL_ID);
            final String packageName = cursor.getString(COL_PACKAGE);
            final String mimeType = cursor.getString(COL_MIMETYPE);

            // Handle when a photo appears in the various data items
            // TODO: accept a photo only if its marked as primary
            if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                byte[] photoBlob = cursor.getBlob(COL_PHOTO);
                Bitmap photoBitmap = BitmapFactory.decodeByteArray(photoBlob, 0, photoBlob.length);
                mPhoto.setImageBitmap(photoBitmap);
                continue;
            }

            // Build an action for this data entry, find a mapping to a UI
            // element, build its summary from the cursor, and collect it along
            // with all others of this mime-type.
            info = new ActionInfo(dataId, packageName, mimeType);
            if (info.findMapping(mMappingCache)) {
                info.buildSummary(cursor);
                mActions.collect(info.mimeType, info);
            }

            // If phone number, also insert as text message action
            if (Phones.CONTENT_ITEM_TYPE.equals(mimeType)) {
                info = new ActionInfo(dataId, packageName, MIME_SMS_ADDRESS);
                if (info.findMapping(mMappingCache)) {
                    info.buildSummary(cursor);
                    mActions.collect(info.mimeType, info);
                }
            }
        }

        cursor.close();

        // Turn our list of actions into UI elements, starting with common types
        Set<String> containedTypes = mActions.keySet();
        for (String mimeType : ORDERED_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                mTrack.addView(inflateAction(mimeType));
                containedTypes.remove(mimeType);
            }
        }

        // Then continue with remaining mime-types in alphabetical order
        String[] remainingTypes = containedTypes.toArray(new String[containedTypes.size()]);
        Arrays.sort(remainingTypes);
        for (String mimeType : remainingTypes) {
            mTrack.addView(inflateAction(mimeType));
        }

        mHasActions = true;
        considerShowing();
    }

    /**
     * Inflate the in-track view for the action of the given mime-type. Will use
     * the icon provided by the {@link Mapping}.
     */
    private View inflateAction(String mimeType) {
        ImageView view = (ImageView)mInflater.inflate(R.layout.fasttrack_item, mTrack, false);

        // Add direct intent if single child, otherwise flag for multiple
        LinkedList<ActionInfo> children = mActions.get(mimeType);
        ActionInfo firstInfo = children.get(0);
        if (children.size() == 1) {
            view.setTag(firstInfo.buildIntent());
        } else {
            view.setTag(mimeType);
        }

        // Set icon and listen for clicks
        view.setImageBitmap(firstInfo.mapping.icon);
        view.setOnClickListener(this);
        return view;
    }

    /** {@inheritDoc} */
    public void onClick(View v) {
        final Object tag = v.getTag();
        if (tag instanceof Intent) {
            // Incoming tag is concrete intent, so launch
            try {
                mContext.startActivity((Intent)tag);
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, NOT_FOUND);
                Toast.makeText(mContext, NOT_FOUND, Toast.LENGTH_SHORT).show();
            }
        } else if (tag instanceof String) {
            // Incoming tag is a mime-type, so show resolution list
            LinkedList<ActionInfo> children = mActions.get(tag);

            // TODO: show drop-down resolution list
            Log.d(TAG, "would show list between several options here");

        }
    }

    /** {@inheritDoc} */
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mWindow.superDispatchKeyEvent(event);
    }

    /** {@inheritDoc} */
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // TODO: make this window accessible
        return false;
    }

    /** {@inheritDoc} */
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            dismiss();
            return true;
        }
        return mWindow.superDispatchTouchEvent(event);
    }

    /** {@inheritDoc} */
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return mWindow.superDispatchTrackballEvent(event);
    }

    /** {@inheritDoc} */
    public void onContentChanged() {
    }

    /** {@inheritDoc} */
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return false;
    }

    /** {@inheritDoc} */
    public View onCreatePanelView(int featureId) {
        return null;
    }

    /** {@inheritDoc} */
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean onMenuOpened(int featureId, Menu menu) {
        return false;
    }

    /** {@inheritDoc} */
    public void onPanelClosed(int featureId, Menu menu) {
    }

    /** {@inheritDoc} */
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean onSearchRequested() {
        return false;
    }

    /** {@inheritDoc} */
    public void onWindowAttributesChanged(android.view.WindowManager.LayoutParams attrs) {
        if (mDecor != null) {
            mWindowManager.updateViewLayout(mDecor, attrs);
        }
    }

    /** {@inheritDoc} */
    public void onWindowFocusChanged(boolean hasFocus) {
    }
}
