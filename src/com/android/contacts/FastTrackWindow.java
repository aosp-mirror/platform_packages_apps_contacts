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
import com.android.contacts.SocialStreamActivity.Mapping;
import com.android.contacts.SocialStreamActivity.MappingCache;
import com.android.internal.policy.PolicyManager;

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.SocialContract;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.Aggregates;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Presence;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.Im.PresenceColumns;
import android.provider.SocialContract.Activities;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

/**
 * Window that shows fast-track contact details for a specific
 * {@link Aggregates#_ID}.
 */
public class FastTrackWindow implements Window.Callback, QueryCompleteListener, OnClickListener,
        AbsListView.OnItemClickListener {
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
    private Rect mAnchor;

    private boolean mHasSummary = false;
    private boolean mHasSocial = false;
    private boolean mHasActions = false;

    private View mArrowUp;
    private View mArrowDown;

    private ImageView mPhoto;
    private ImageView mPresence;
    private TextView mContent;
    private TextView mPublished;
    private HorizontalScrollView mTrackScroll;
    private ViewGroup mTrack;
    private ListView mResolveList;

    private String mDisplayName = null;
    private String mSocialTitle = null;

    private SpannableStringBuilder mBuilder = new SpannableStringBuilder();
    private CharacterStyle mStyleBold = new StyleSpan(android.graphics.Typeface.BOLD);
    private CharacterStyle mStyleBlack = new ForegroundColorSpan(Color.BLACK);

    /**
     * Set of {@link ActionInfo} that are associated with the aggregate
     * currently displayed by this fast-track window, represented as a map from
     * {@link String} mimetype to {@link ActionList}.
     */
    private ActionMap mActions = new ActionMap();

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
        Phones.CONTENT_ITEM_TYPE,
        Aggregates.CONTENT_ITEM_TYPE,
        MIME_SMS_ADDRESS,
        Email.CONTENT_ITEM_TYPE,
    };

    private static final boolean INCLUDE_PROFILE_ACTION = true;

    private static final int TOKEN_SUMMARY = 1;
    private static final int TOKEN_SOCIAL = 2;
    private static final int TOKEN_DATA = 3;

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

        mArrowUp = mWindow.findViewById(R.id.arrow_up);
        mArrowDown = mWindow.findViewById(R.id.arrow_down);

        mPhoto = (ImageView)mWindow.findViewById(R.id.photo);
        mPresence = (ImageView)mWindow.findViewById(R.id.presence);
        mContent = (TextView)mWindow.findViewById(R.id.content);
        mPublished = (TextView)mWindow.findViewById(R.id.published);
        mTrack = (ViewGroup)mWindow.findViewById(R.id.fasttrack);
        mTrackScroll = (HorizontalScrollView)mWindow.findViewById(R.id.scroll);
        mResolveList = (ListView)mWindow.findViewById(android.R.id.list);

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
        mapping.summaryColumn = Phone.TYPE;
        mapping.detailColumn = Phone.NUMBER;
        mapping.icon = BitmapFactory.decodeResource(res, android.R.drawable.sym_action_call);
        mMappingCache.addMapping(mapping);

        mapping = new Mapping(CommonDataKinds.PACKAGE_COMMON, MIME_SMS_ADDRESS);
        mapping.summaryColumn = Phone.TYPE;
        mapping.detailColumn = Phone.NUMBER;
        mapping.icon = BitmapFactory.decodeResource(res, R.drawable.sym_action_sms);
        mMappingCache.addMapping(mapping);

        mapping = new Mapping(CommonDataKinds.PACKAGE_COMMON, Email.CONTENT_ITEM_TYPE);
        mapping.summaryColumn = Email.TYPE;
        mapping.detailColumn = Email.DATA;
        mapping.icon = BitmapFactory.decodeResource(res, android.R.drawable.sym_action_email);
        mMappingCache.addMapping(mapping);

    }

    /**
     * Start showing a fast-track window for the given {@link Aggregate#_ID}
     * pointing towards the given location.
     */
    public void show(Uri aggUri, Rect anchor) {
        if (mShowing || mQuerying) {
            Log.w(TAG, "already in process of showing");
            return;
        }

        mAggId = ContentUris.parseId(aggUri);
        mAnchor = new Rect(anchor);
        mQuerying = true;

        Uri aggSummary = ContentUris.withAppendedId(
                ContactsContract.Aggregates.CONTENT_SUMMARY_URI, mAggId);
        Uri aggSocial = ContentUris.withAppendedId(
                SocialContract.Activities.CONTENT_AGGREGATE_STATUS_URI, mAggId);
        Uri aggData = Uri.withAppendedPath(aggUri,
                ContactsContract.Aggregates.Data.CONTENT_DIRECTORY);

        // Start data query in background
        mHandler = new NotifyingAsyncQueryHandler(mContext, this);
        mHandler.startQuery(TOKEN_SUMMARY, null, aggSummary, null, null, null, null);
        mHandler.startQuery(TOKEN_SOCIAL, null, aggSocial, null, null, null, null);
        mHandler.startQuery(TOKEN_DATA, null, aggData, null, null, null, null);

    }

    /**
     * Show the correct callout arrow based on a {@link R.id} reference.
     */
    private void showArrow(int whichArrow, int requestedX) {
        final View showArrow = (whichArrow == R.id.arrow_up) ? mArrowUp : mArrowDown;
        final View hideArrow = (whichArrow == R.id.arrow_up) ? mArrowDown : mArrowUp;

        final int arrowWidth = mArrowUp.getMeasuredWidth();

        showArrow.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams param = (LinearLayout.LayoutParams)showArrow.getLayoutParams();
        param.leftMargin = requestedX - arrowWidth / 2;

        hideArrow.setVisibility(View.INVISIBLE);
    }

    /**
     * Actual internal method to show this fast-track window. Called only by
     * {@link #considerShowing()} when all data requirements have been met.
     */
    private void showInternal() {
        mDecor = mWindow.getDecorView();
        WindowManager.LayoutParams l = mWindow.getAttributes();

        l.width = WindowManager.LayoutParams.FILL_PARENT;
        l.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Force layout measuring pass so we have baseline numbers
        mDecor.measure(l.width, l.height);

        final int blockHeight = mDecor.getMeasuredHeight();
        final int arrowHeight = mArrowUp.getHeight();

        l.gravity = Gravity.TOP | Gravity.LEFT;
        l.x = 0;

        if (mAnchor.top > blockHeight) {
            // Show downwards callout when enough room, aligning bottom block
            // edge with top of anchor area, and adjusting to inset arrow.
            showArrow(R.id.arrow_down, mAnchor.centerX());
            l.y = mAnchor.top - blockHeight + arrowHeight;

        } else {
            // Otherwise show upwards callout, aligning block top with bottom of
            // anchor area, and adjusting to inset arrow.
            showArrow(R.id.arrow_up, mAnchor.centerX());
            l.y = mAnchor.bottom - arrowHeight;

        }

        l.dimAmount = 0.6f;
        l.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
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
        mHandler.cancelOperation(TOKEN_SUMMARY);
        mHandler.cancelOperation(TOKEN_SOCIAL);
        mHandler.cancelOperation(TOKEN_DATA);

        // Reset all views to prepare for possible recycling
        mPhoto.setImageResource(R.drawable.ic_contact_list_picture);
        mPresence.setImageDrawable(null);
        mPublished.setText(null);
        mContent.setText(null);

        mDisplayName = null;
        mSocialTitle = null;

        // Clear track actions and scroll to hard left
        mActions.clear();
        mTrack.removeAllViews();
        mTrackScroll.fullScroll(View.FOCUS_LEFT);
        mWasDownArrow = false;

        showResolveList(View.GONE);

        mHasSocial = false;
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
        if (mHasSummary && mHasSocial && mHasActions && !mShowing) {
            // Now that all queries have been finished, build summary string.
            mBuilder.clear();
            mBuilder.append(mDisplayName);
            mBuilder.append(" ");
            mBuilder.append(mSocialTitle);
            mBuilder.setSpan(mStyleBold, 0, mDisplayName.length(), 0);
            mBuilder.setSpan(mStyleBlack, 0, mDisplayName.length(), 0);
            mContent.setText(mBuilder);

            showInternal();
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cursor == null) {
            return;
        } else if (token == TOKEN_SUMMARY) {
            handleSummary(cursor);
        } else if (token == TOKEN_SOCIAL) {
            handleSocial(cursor);
        } else if (token == TOKEN_DATA) {
            handleData(cursor);
        }
    }

    /**
     * Handle the result from the {@link TOKEN_SUMMARY} query.
     */
    private void handleSummary(Cursor cursor) {
        final int colDisplayName = cursor.getColumnIndex(Aggregates.DISPLAY_NAME);
        final int colStatus = cursor.getColumnIndex(PresenceColumns.PRESENCE_STATUS);

        if (cursor.moveToNext()) {
            mDisplayName = cursor.getString(colDisplayName);

            int status = cursor.getInt(colStatus);
            int statusRes = Presence.getPresenceIconResourceId(status);
            mPresence.setImageResource(statusRes);
        }

        mHasSummary = true;
        considerShowing();
    }

    /**
     * Handle the result from the {@link TOKEN_SOCIAL} query.
     */
    private void handleSocial(Cursor cursor) {
        final int colTitle = cursor.getColumnIndex(Activities.TITLE);
        final int colPublished = cursor.getColumnIndex(Activities.PUBLISHED);

        if (cursor.moveToNext()) {
            mSocialTitle = cursor.getString(colTitle);

            long published = cursor.getLong(colPublished);
            CharSequence relativePublished = DateUtils.getRelativeTimeSpanString(published,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            mPublished.setText(relativePublished);

        }

        mHasSocial = true;
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
        CharSequence summaryValue;
        String detailValue;

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
        public void buildDetails(Cursor cursor) {
            if (mapping == null) return;

            // Try finding common display label for this item, otherwise fall
            // back to reading from defined summary column.
            summaryValue = ContactsUtils.getDisplayLabel(mContext, mimeType, cursor);
            if (summaryValue == null && mapping.summaryColumn != null) {
                summaryValue = cursor.getString(cursor.getColumnIndex(mapping.summaryColumn));
            }

            // Read detailed value, if mapping was defined
            if (mapping.detailColumn != null) {
                detailValue = cursor.getString(cursor.getColumnIndex(mapping.detailColumn));
            }
        }

        /**
         * Build an {@link Intent} that will perform this action.
         */
        public Intent buildIntent() {
            // Handle well-known mime-types with special care
            if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                Uri callUri = Uri.parse("tel:" + Uri.encode(detailValue));
                return new Intent(Intent.ACTION_DIAL, callUri);

            } else if (MIME_SMS_ADDRESS.equals(mimeType)) {
                Uri smsUri = Uri.fromParts("smsto", detailValue, null);
                return new Intent(Intent.ACTION_SENDTO, smsUri);

            } else if (CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                Uri mailUri = Uri.fromParts("mailto", detailValue, null);
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
     * Provide a strongly-typed {@link LinkedList} that holds a list of
     * {@link ActionInfo} objects.
     */
    private class ActionList extends LinkedList<ActionInfo> {
    }

    /**
     * Provide a simple way of collecting one or more {@link ActionInfo} objects
     * under a mime-type key.
     */
    private class ActionMap extends HashMap<String, ActionList> {
        private void collect(String mimeType, ActionInfo info) {
            // Create list for this mimetype when needed
            ActionList collectList = get(mimeType);
            if (collectList == null) {
                collectList = new ActionList();
                put(mimeType, collectList);
            }
            collectList.add(info);
        }
    }

    /**
     * Handle the result from the {@link TOKEN_DATA} query.
     */
    private void handleData(Cursor cursor) {
        final int colId = cursor.getColumnIndex(Data._ID);
        final int colPackage = cursor.getColumnIndex(Contacts.PACKAGE);
        final int colMimeType = cursor.getColumnIndex(Data.MIMETYPE);
        final int colPhoto = cursor.getColumnIndex(Photo.PHOTO);

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
            final long dataId = cursor.getLong(colId);
            final String packageName = cursor.getString(colPackage);
            final String mimeType = cursor.getString(colMimeType);

            // Handle when a photo appears in the various data items
            // TODO: accept a photo only if its marked as primary
            if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                byte[] photoBlob = cursor.getBlob(colPhoto);
                Bitmap photoBitmap = BitmapFactory.decodeByteArray(photoBlob, 0, photoBlob.length);
                mPhoto.setImageBitmap(photoBitmap);
                continue;
            }

            // Build an action for this data entry, find a mapping to a UI
            // element, build its summary from the cursor, and collect it along
            // with all others of this mime-type.
            info = new ActionInfo(dataId, packageName, mimeType);
            if (info.findMapping(mMappingCache)) {
                info.buildDetails(cursor);
                mActions.collect(info.mimeType, info);
            }

            // If phone number, also insert as text message action
            if (Phones.CONTENT_ITEM_TYPE.equals(mimeType)) {
                info = new ActionInfo(dataId, packageName, MIME_SMS_ADDRESS);
                if (info.findMapping(mMappingCache)) {
                    info.buildDetails(cursor);
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
        ActionList children = mActions.get(mimeType);
        ActionInfo firstInfo = children.get(0);
        if (children.size() == 1) {
            view.setTag(firstInfo.buildIntent());
        } else {
            view.setTag(children);
        }

        // Set icon and listen for clicks
        view.setImageBitmap(firstInfo.mapping.icon);
        view.setOnClickListener(this);
        return view;
    }

    /** {@inheritDoc} */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Pass list item clicks along so that Intents are handled uniformly
        onClick(view);
    }

    /**
     * Flag indicating if {@link #mArrowDown} was visible during the last call
     * to {@link #showResolveList(int)}. Used to decide during a later call if
     * the arrow should be restored.
     */
    private boolean mWasDownArrow = false;

    /**
     * Helper for showing and hiding {@link #mResolveList}, which will correctly
     * manage {@link #mArrowDown} as needed.
     */
    private void showResolveList(int visibility) {
        // Show or hide the resolve list if needed
        if (mResolveList.getVisibility() != visibility) {
            mResolveList.setVisibility(visibility);
        }

        if (visibility == View.VISIBLE) {
            // If showing list, then hide and save state of down arrow
            mWasDownArrow = mWasDownArrow || (mArrowDown.getVisibility() == View.VISIBLE);
            mArrowDown.setVisibility(View.INVISIBLE);
        } else {
            // If hiding list, restore any down arrow state
            mArrowDown.setVisibility(mWasDownArrow ? View.VISIBLE : View.INVISIBLE);
        }

    }

    /** {@inheritDoc} */
    public void onClick(View v) {
        final Object tag = v.getTag();
        if (tag instanceof Intent) {
            // Hide the resolution list, if present
            showResolveList(View.GONE);

            // Incoming tag is concrete intent, so launch
            try {
                mContext.startActivity((Intent)tag);
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, NOT_FOUND);
                Toast.makeText(mContext, NOT_FOUND, Toast.LENGTH_SHORT).show();
            }
        } else if (tag instanceof ActionList) {
            // Incoming tag is a mime-type, so show resolution list
            final ActionList children = (ActionList)tag;

            // Show resolution list and set adapter
            showResolveList(View.VISIBLE);

            mResolveList.setOnItemClickListener(this);
            mResolveList.setAdapter(new BaseAdapter() {
                public int getCount() {
                    return children.size();
                }

                public Object getItem(int position) {
                    return children.get(position);
                }

                public long getItemId(int position) {
                    return position;
                }

                public View getView(int position, View convertView, ViewGroup parent) {
                    if (convertView == null) {
                        convertView = mInflater.inflate(R.layout.fasttrack_resolve_item, parent, false);
                    }

                    // Set action title based on summary value
                    ActionInfo info = (ActionInfo)getItem(position);

                    ImageView icon1 = (ImageView)convertView.findViewById(android.R.id.icon1);
                    TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
                    TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);

                    icon1.setImageBitmap(info.mapping.icon);
                    text1.setText(info.summaryValue);
                    text2.setText(info.detailValue);

                    convertView.setTag(info.buildIntent());
                    return convertView;
                }
            });

            // Make sure we resize to make room for ListView
            onWindowAttributesChanged(mWindow.getAttributes());

        }
    }

    /** {@inheritDoc} */
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // Back key will first dismiss any expanded resolve list, otherwise
            // it will close the entire dialog.
            if (mResolveList.getVisibility() == View.VISIBLE) {
                showResolveList(View.GONE);
            } else {
                dismiss();
            }
            return true;
        }
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
