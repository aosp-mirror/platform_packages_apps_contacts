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

package com.android.contacts.quickcontact;

import com.android.contacts.Collapser;
import com.android.contacts.ContactPresenceIconUtil;
import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.model.AccountType.DataKind;
import com.android.contacts.model.AccountTypes;
import com.android.contacts.util.Constants;
import com.android.contacts.util.DataStatus;
import com.android.contacts.util.NotifyingAsyncQueryHandler;
import com.android.internal.policy.PolicyManager;
import com.google.android.collect.Lists;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Window that shows QuickContact dialog for a specific {@link Contacts#_ID}.
 */
public class QuickContactWindow implements Window.Callback,
        NotifyingAsyncQueryHandler.AsyncQueryListener, View.OnClickListener,
        AbsListView.OnItemClickListener, KeyEvent.Callback, OnGlobalLayoutListener,
        QuickContactRootLayout.Listener {
    private static final String TAG = "QuickContactWindow";

    /**
     * Interface used to allow the person showing a {@link QuickContactWindow} to
     * know when the window has been dismissed.
     */
    public interface OnDismissListener {
        public void onDismiss(QuickContactWindow dialog);
    }

    private final static int ANIMATION_FADE_IN_TIME = 100;
    private final static int ANIMATION_FADE_OUT_TIME = 100;
    private final static int ANIMATION_EXPAND_TIME = 100;
    private final static int ANIMATION_COLLAPSE_TIME = 100;

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final WindowManager mWindowManager;
    private Window mWindow;
    private View mDecor;
    private final Rect mRect = new Rect();

    private boolean mDismissed = false;
    private boolean mQuerying = false;
    private boolean mShowing = false;

    private NotifyingAsyncQueryHandler mHandler;
    private OnDismissListener mDismissListener;

    private Uri mLookupUri;
    private Rect mAnchor;

    private int mScreenWidth;
    private int mUseableScreenHeight;
    private int mRequestedY;

    private boolean mHasValidSocial = false;

    private int mMode;
    private QuickContactRootLayout mRootView;
    private QuickContactBackgroundDrawable mBackground;
    private View mHeader;
    private HorizontalScrollView mTrackScroll;
    private ViewGroup mTrack;

    private FrameLayout mFooter;
    private LinearLayout mFooterDisambig;
    private LinearLayout mFooterClearDefaults;
    private ListView mResolveList;
    private CheckableImageView mLastAction;
    private CheckBox mSetPrimaryCheckBox;
    private ListView mDefaultsListView;
    private Button mClearDefaultsButton;

    /**
     * Keeps the default action per mimetype. Empty if no default actions are set
     */
    private HashMap<String, Action> mDefaultsMap = new HashMap<String, Action>();

    private int mWindowRecycled = 0;
    private int mActionRecycled = 0;

    /**
     * Set of {@link Action} that are associated with the aggregate currently
     * displayed by this dialog, represented as a map from {@link String}
     * MIME-type to a list of {@link Action}.
     */
    private ActionMultiMap mActions = new ActionMultiMap();

    /**
     * Pool of unused {@link CheckableImageView} that have previously been
     * inflated, and are ready to be recycled through {@link #obtainView()}.
     */
    private LinkedList<CheckableImageView> mActionPool = new LinkedList<CheckableImageView>();

    private String[] mExcludeMimes;

    /**
     * {@link #PRECEDING_MIMETYPES} and {@link #FOLLOWING_MIMETYPES} are used to sort MIME-types.
     *
     * <p>The MIME-types in {@link #PRECEDING_MIMETYPES} appear in the front of the dialog,
     * in the order in the array.
     *
     * <p>The ones in {@link #FOLLOWING_MIMETYPES} appear in the end of the dialog, in alphabetical
     * order.
     *
     * <p>The rest go between them, in the order in the array.
     */
    private static final String[] PRECEDING_MIMETYPES = new String[] {
            Phone.CONTENT_ITEM_TYPE,
            SipAddress.CONTENT_ITEM_TYPE,
            Contacts.CONTENT_ITEM_TYPE,
            Constants.MIME_SMS_ADDRESS,
            Email.CONTENT_ITEM_TYPE,
    };

    /**
     * See {@link #PRECEDING_MIMETYPES}.
     */
    private static final String[] FOLLOWING_MIMETYPES = new String[] {
            StructuredPostal.CONTENT_ITEM_TYPE,
            Website.CONTENT_ITEM_TYPE,
    };

    /**
     * List of MIMETYPES that do not represent real data rows and can therefore not be set
     * as defaults
     */
    private static final ArrayList<String> VIRTUAL_MIMETYPES = Lists.newArrayList(new String[] {
            Im.CONTENT_ITEM_TYPE, Constants.MIME_SMS_ADDRESS
    });
    private static final int TOKEN_DATA = 1;

    static final boolean TRACE_LAUNCH = false;
    static final String TRACE_TAG = "quickcontact";

    /**
     * Prepare a dialog to show in the given {@link Context}.
     */
    public QuickContactWindow(Context context) {
        mContext = new ContextThemeWrapper(context, R.style.QuickContact);
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

        mWindow = PolicyManager.makeNewWindow(mContext);
        mWindow.setCallback(this);
        mWindow.setWindowManager(mWindowManager, null, null);
        mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);

        mWindow.setContentView(R.layout.quickcontact);

        mRootView = (QuickContactRootLayout)mWindow.findViewById(R.id.root);
        mRootView.setListener(this);
        mRootView.setFocusable(true);
        mRootView.setFocusableInTouchMode(true);
        mRootView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        mBackground = new QuickContactBackgroundDrawable();
        mRootView.setBackgroundDrawable(mBackground);

        mScreenWidth = mWindowManager.getDefaultDisplay().getWidth();
        // Status bar height
        final int screenMarginBottom = context.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.screen_margin_bottom);
        mUseableScreenHeight = mWindowManager.getDefaultDisplay().getHeight() - screenMarginBottom;

        mTrack = (ViewGroup) mWindow.findViewById(R.id.quickcontact);
        mTrackScroll = (HorizontalScrollView) mWindow.findViewById(R.id.scroll);

        mFooter = (FrameLayout) mWindow.findViewById(R.id.footer);
        mFooterDisambig = (LinearLayout) mWindow.findViewById(R.id.footer_disambig);
        mFooterClearDefaults = (LinearLayout) mWindow.findViewById(R.id.footer_clear_defaults);
        mResolveList = (ListView) mWindow.findViewById(android.R.id.list);
        mSetPrimaryCheckBox = (CheckBox) mWindow.findViewById(android.R.id.checkbox);

        mDefaultsListView = (ListView) mWindow.findViewById(R.id.defaults_list);
        mClearDefaultsButton = (Button) mWindow.findViewById(R.id.clear_defaults_button);
        mClearDefaultsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearDefaults();
            }
        });

        mResolveList.setOnItemClickListener(QuickContactWindow.this);

        mHandler = new NotifyingAsyncQueryHandler(mContext, this);
    }

    /**
     * Prepare a dialog to show in the given {@link Context}, and notify the
     * given {@link OnDismissListener} each time this dialog is dismissed.
     */
    public QuickContactWindow(Context context, OnDismissListener dismissListener) {
        this(context);
        mDismissListener = dismissListener;
    }

    private View getHeaderView(int mode) {
        View header = null;
        switch (mode) {
            case QuickContact.MODE_SMALL:
                header = mWindow.findViewById(R.id.header_small);
                break;
            case QuickContact.MODE_MEDIUM:
                header = mWindow.findViewById(R.id.header_medium);
                break;
            case QuickContact.MODE_LARGE:
                header = mWindow.findViewById(R.id.header_large);
                break;
        }

        if (header instanceof ViewStub) {
            // Inflate actual header if we picked a stub
            final ViewStub stub = (ViewStub)header;
            header = stub.inflate();
        } else if (header != null) {
            header.setVisibility(View.VISIBLE);
        }

        return header;
    }

    /**
     * Start showing a dialog for the given {@link Contacts#_ID} pointing
     * towards the given location.
     */
    public synchronized void show(Uri lookupUri, Rect anchor, int mode, String[] excludeMimes) {
        if (mQuerying || mShowing) {
            Log.w(TAG, "dismissing before showing");
            dismissInternal();
        }

        if (TRACE_LAUNCH && !android.os.Debug.isMethodTracingActive()) {
            android.os.Debug.startMethodTracing(TRACE_TAG);
        }

        // Validate incoming parameters
        final boolean validMode = (mode == QuickContact.MODE_SMALL
                || mode == QuickContact.MODE_MEDIUM || mode == QuickContact.MODE_LARGE);
        if (!validMode) {
            throw new IllegalArgumentException("Invalid mode, expecting MODE_LARGE, "
                    + "MODE_MEDIUM, or MODE_SMALL");
        }

        if (anchor == null) {
            throw new IllegalArgumentException("Missing anchor rectangle");
        }

        // Prepare header view for requested mode
        mLookupUri = lookupUri;
        mAnchor = new Rect(anchor);
        mMode = mode;
        mExcludeMimes = excludeMimes;

        mHeader = getHeaderView(mode);

        setHeaderText(R.id.name, R.string.quickcontact_missing_name);

        setHeaderText(R.id.status, null);
        setHeaderText(R.id.timestamp, null);

        setHeaderImage(R.id.presence, null);

        resetTrack();

        // We need to have a focused view inside the QuickContact window so
        // that the BACK key event can be intercepted
        mRootView.requestFocus();

        mHasValidSocial = false;
        mDismissed = false;
        mQuerying = true;

        // Start background query for data, but only select photo rows when they
        // directly match the super-primary PHOTO_ID.
        final Uri dataUri = getDataUri(lookupUri);
        mHandler.cancelOperation(TOKEN_DATA);

        // Only request photo data when required by mode
        if (mMode == QuickContact.MODE_LARGE) {
            // Select photos, but only super-primary
            mHandler.startQuery(TOKEN_DATA, lookupUri, dataUri, DataQuery.PROJECTION, Data.MIMETYPE
                    + "!=? OR (" + Data.MIMETYPE + "=? AND " + Data._ID + "=" + Contacts.PHOTO_ID
                    + ")", new String[] { Photo.CONTENT_ITEM_TYPE, Photo.CONTENT_ITEM_TYPE }, null);
        } else {
            // Exclude all photos from cursor
            mHandler.startQuery(TOKEN_DATA, lookupUri, dataUri, DataQuery.PROJECTION, Data.MIMETYPE
                    + "!=?", new String[] { Photo.CONTENT_ITEM_TYPE }, null);
        }
    }

    /**
     * Build a {@link Uri} into the {@link Data} table for the requested
     * {@link Contacts#CONTENT_LOOKUP_URI} style {@link Uri}.
     */
    private Uri getDataUri(Uri lookupUri) {
        // TODO: Formalize method of extracting LOOKUP_KEY
        final List<String> path = lookupUri.getPathSegments();
        final boolean validLookup = path.size() >= 3 && "lookup".equals(path.get(1));
        if (!validLookup) {
            // We only accept valid lookup-style Uris
            throw new IllegalArgumentException("Expecting lookup-style Uri");
        } else if (path.size() == 3) {
            // No direct _ID provided, so force a lookup
            lookupUri = Contacts.lookupContact(mContext.getContentResolver(), lookupUri);
        }

        final long contactId = ContentUris.parseId(lookupUri);
        return Uri.withAppendedPath(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
                Contacts.Data.CONTENT_DIRECTORY);
    }

    /**
     * Creates and configures the background resource
     */
    private void configureBackground(boolean arrowUp, int requestedX) {
        mBackground.configure(mContext.getResources(), arrowUp, requestedX);
    }

    /**
     * Actual internal method to show this dialog. Called only by
     * {@link #considerShowing()} when all data requirements have been met.
     */
    private void showInternal() {
        mDecor = mWindow.getDecorView();
        mDecor.getViewTreeObserver().addOnGlobalLayoutListener(this);
        WindowManager.LayoutParams layoutParams = mWindow.getAttributes();

        layoutParams.width = mContext.getResources().getDimensionPixelSize(
                R.dimen.quick_contact_width);
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Try to left align with the anchor control
        if (mAnchor.left + layoutParams.width <= mScreenWidth) {
            layoutParams.x = mAnchor.left;
        } else {
            // Not enough space. Try to right align to the anchor
            if (mAnchor.right - layoutParams.width >= 0) {
                layoutParams.x = mAnchor.right - layoutParams.width;
            } else {
                // Also not enough space. Use the whole screen width available
                layoutParams.x = 0;
                layoutParams.width = mScreenWidth;
            }
        }

        // Force layout measuring pass so we have baseline numbers
        mDecor.measure(layoutParams.width, layoutParams.height);
        final int blockHeight = mDecor.getMeasuredHeight();

        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;

        if (mUseableScreenHeight - mAnchor.bottom > blockHeight) {
            // Show downwards callout when enough room, aligning block top with bottom of
            // anchor area, and adjusting to inset arrow.
            configureBackground(true, mAnchor.centerX() - layoutParams.x);
            layoutParams.y = mAnchor.bottom;
            layoutParams.windowAnimations = R.style.QuickContactBelowAnimation;
        } else {
            // Show upwards callout, aligning bottom block
            // edge with top of anchor area, and adjusting to inset arrow.
            configureBackground(false, mAnchor.centerX() - layoutParams.x);
            layoutParams.y = mAnchor.top - blockHeight;
            layoutParams.windowAnimations = R.style.QuickContactAboveAnimation;
        }

        layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        mRequestedY = layoutParams.y;
        mWindowManager.addView(mDecor, layoutParams);
        mShowing = true;
        mQuerying = false;
        mDismissed = false;

        if (TRACE_LAUNCH) {
            android.os.Debug.stopMethodTracing();
            Log.d(TAG, "Window recycled " + mWindowRecycled + " times, chiclets "
                    + mActionRecycled + " times");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onGlobalLayout() {
        layoutInScreen();
    }

    /**
     * Adjust vertical {@link WindowManager.LayoutParams} to fit window as best
     * as possible, shifting up to display content as needed.
     */
    private void layoutInScreen() {
        if (!mShowing) return;

        final WindowManager.LayoutParams l = mWindow.getAttributes();
        final int originalY = l.y;

        final int blockHeight = mDecor.getHeight();

        l.y = mRequestedY;
        if (mRequestedY + blockHeight > mUseableScreenHeight) {
            // Shift up from bottom when overflowing
            l.y = mUseableScreenHeight - blockHeight;
        }

        if (originalY != l.y) {
            // Only update when value is changed
            mWindow.setAttributes(l);
        }
    }

    /**
     * Dismiss this dialog if showing.
     */
    public synchronized void dismiss() {
        // Notify any listeners that we've been dismissed
        if (mDismissListener != null) {
            mDismissListener.onDismiss(this);
        }

        dismissInternal();
    }

    private void dismissInternal() {
        // Remove any attached window decor for recycling
        boolean hadDecor = mDecor != null;
        if (hadDecor) {
            mWindowManager.removeView(mDecor);
            mWindowRecycled++;
            mDecor.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            mDecor = null;
            mWindow.closeAllPanels();
        }
        mShowing = false;
        mDismissed = true;

        // Cancel any pending queries
        mHandler.cancelOperation(TOKEN_DATA);
        mQuerying = false;

        // Completely hide header and reset track
        mHeader.setVisibility(View.GONE);
        resetTrack();
    }

    /**
     * Reset track to initial state, recycling any chiclets.
     */
    private void resetTrack() {
        // Clear background height-animation override
        mBackground.clearBottomOverride();

        // Release reference to last chiclet
        mLastAction = null;

        // Clear track actions and scroll to hard left
        mActions.clear();

        // Recycle any chiclets in use
        for (int i = mTrack.getChildCount() - 1; i >= 0; i--) {
            releaseView((CheckableImageView)mTrack.getChildAt(i));
            mTrack.removeViewAt(i);
        }

        mTrackScroll.fullScroll(View.FOCUS_LEFT);

        // Clear any primary requests
        mSetPrimaryCheckBox.setChecked(false);

        setNewActionViewChecked(null);
        mFooter.setVisibility(View.GONE);
    }

    /**
     * Consider showing this window, which will only call through to
     * {@link #showInternal()} when all data items are present.
     */
    private void considerShowing() {
        if (!mShowing && !mDismissed) {
            if (mMode == QuickContact.MODE_MEDIUM && !mHasValidSocial) {
                // Missing valid social, swap medium for small header
                mHeader.setVisibility(View.GONE);
                mHeader = getHeaderView(QuickContact.MODE_SMALL);
            }

            // All queries have returned, pull curtain
            showInternal();
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
        // Bail early when query is stale
        if (cookie != mLookupUri) return;

        if (cursor == null) {
            // Problem while running query, so bail without showing
            Log.w(TAG, "Missing cursor for token=" + token);
            this.dismiss();
            return;
        }

        handleData(cursor);

        if (!cursor.isClosed()) {
            cursor.close();
        }

        considerShowing();
    }

    /** Assign this string to the view, if found in {@link #mHeader}. */
    private void setHeaderText(int id, int resId) {
        setHeaderText(id, mContext.getResources().getText(resId));
    }

    /** Assign this string to the view, if found in {@link #mHeader}. */
    private void setHeaderText(int id, CharSequence value) {
        final View view = mHeader.findViewById(id);
        if (view instanceof TextView) {
            ((TextView)view).setText(value);
            view.setVisibility(TextUtils.isEmpty(value) ? View.GONE : View.VISIBLE);
        }
    }

    /** Assign this image to the view, if found in {@link #mHeader}. */
    private void setHeaderImage(int id, Drawable drawable) {
        final View view = mHeader.findViewById(id);
        if (view instanceof ImageView) {
            ((ImageView)view).setImageDrawable(drawable);
            view.setVisibility(drawable == null ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Check if the given MIME-type appears in the list of excluded MIME-types
     * that the most-recent caller requested.
     */
    private boolean isMimeExcluded(String mimeType) {
        if (mExcludeMimes == null) return false;
        for (String excludedMime : mExcludeMimes) {
            if (TextUtils.equals(excludedMime, mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle the result from the {@link #TOKEN_DATA} query.
     */
    private void handleData(Cursor cursor) {
        final ResolveCache cache = ResolveCache.getInstance(mContext.getPackageManager());
        if (cursor == null) return;
        if (cursor.getCount() == 0) {
            Toast.makeText(mContext, R.string.invalidContactMessage, Toast.LENGTH_LONG).show();
            dismiss();
            return;
        }

        if (!isMimeExcluded(Contacts.CONTENT_ITEM_TYPE)) {
            // Add the profile shortcut action
            final Action action = new ProfileAction(mContext, mLookupUri);
            mActions.put(Contacts.CONTENT_ITEM_TYPE, action);
        }

        mDefaultsMap.clear();

        final DataStatus status = new DataStatus();
        final AccountTypes accountTypes = AccountTypes.getInstance(mContext);
        final ImageView photoView = (ImageView)mHeader.findViewById(R.id.photo);

        Bitmap photoBitmap = null;
        while (cursor.moveToNext()) {
            final long dataId = cursor.getLong(DataQuery._ID);
            final String accountType = cursor.getString(DataQuery.ACCOUNT_TYPE);
            final String mimeType = cursor.getString(DataQuery.MIMETYPE);
            final boolean isPrimary = cursor.getInt(DataQuery.IS_PRIMARY) != 0;
            final boolean isSuperPrimary = cursor.getInt(DataQuery.IS_SUPER_PRIMARY) != 0;

            // Handle any social status updates from this row
            status.possibleUpdate(cursor);

            // Skip this data item if MIME-type excluded
            if (isMimeExcluded(mimeType)) continue;

            // Handle photos included as data row
            if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final int colPhoto = cursor.getColumnIndex(Photo.PHOTO);
                final byte[] photoBlob = cursor.getBlob(colPhoto);
                if (photoBlob != null) {
                    photoBitmap = BitmapFactory.decodeByteArray(photoBlob, 0, photoBlob.length);
                }
                continue;
            }

            final DataKind kind = accountTypes.getKindOrFallback(accountType, mimeType, mContext);

            if (kind != null) {
                // Build an action for this data entry, find a mapping to a UI
                // element, build its summary from the cursor, and collect it
                // along with all others of this MIME-type.
                final Action action = new DataAction(mContext, mimeType, kind, dataId, cursor);
                final boolean wasAdded = considerAdd(action, cache);
                if (wasAdded) {
                    // Remember the default
                    if (isSuperPrimary || (isPrimary && (mDefaultsMap.get(mimeType) == null))) {
                        mDefaultsMap.put(mimeType, action);
                    }
                }
            }

            // If phone number, also insert as text message action
            if (Phone.CONTENT_ITEM_TYPE.equals(mimeType) && kind != null) {
                final DataAction action = new DataAction(mContext, Constants.MIME_SMS_ADDRESS,
                        kind, dataId, cursor);
                considerAdd(action, cache);
            }

            // Handle Email rows with presence data as Im entry
            final boolean hasPresence = !cursor.isNull(DataQuery.PRESENCE);
            if (hasPresence && Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final DataKind imKind = accountTypes.getKindOrFallback(accountType,
                        Im.CONTENT_ITEM_TYPE, mContext);
                if (imKind != null) {
                    final DataAction action = new DataAction(mContext, Im.CONTENT_ITEM_TYPE, imKind,
                            dataId, cursor);
                    considerAdd(action, cache);
                }
            }
        }

        if (mDefaultsMap.size() != 0) {
            final Action action = new ClearDefaultsAction();
            mActions.put(action.getMimeType(), action);
        }

        if (cursor.moveToLast()) {
            // Read contact information from last data row
            final String name = cursor.getString(DataQuery.DISPLAY_NAME);
            final int presence = cursor.getInt(DataQuery.CONTACT_PRESENCE);
            final Drawable statusIcon = ContactPresenceIconUtil.getPresenceIcon(mContext, presence);

            setHeaderText(R.id.name, name);
            setHeaderImage(R.id.presence, statusIcon);
        }

        if (photoView != null) {
            // Place photo when discovered in data, otherwise hide
            photoView.setVisibility(photoBitmap != null ? View.VISIBLE : View.GONE);
            photoView.setImageBitmap(photoBitmap);
        }

        mHasValidSocial = status.isValid();
        if (mHasValidSocial && mMode != QuickContact.MODE_SMALL) {
            // Update status when valid was found
            setHeaderText(R.id.status, status.getStatus());
            setHeaderText(R.id.timestamp, status.getTimestampLabel(mContext));
        }

        // Turn our list of actions into UI elements

        // All the mime-types to add.
        final Set<String> containedTypes = new HashSet<String>(mActions.keySet());

        boolean hasData = false;

        // First, add PRECEDING_MIMETYPES, which are most common.
        for (String mimeType : PRECEDING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                hasData = true;
                mTrack.addView(inflateAction(mimeType, cache));
                containedTypes.remove(mimeType);
            }
        }

        // Keep the current index to append non PRECEDING/FOLLOWING items.
        final int indexAfterPreceding = mTrack.getChildCount() - 1;

        // Then, add FOLLOWING_MIMETYPES, which are least common.
        for (String mimeType : FOLLOWING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                hasData = true;
                mTrack.addView(inflateAction(mimeType, cache));
                containedTypes.remove(mimeType);
            }
        }

        // Show the clear-defaults button? If yes, it goes to the end of the list
        if (containedTypes.contains(ClearDefaultsAction.PSEUDO_MIME_TYPE)) {
            final ClearDefaultsAction action = (ClearDefaultsAction) mActions.get(
                    ClearDefaultsAction.PSEUDO_MIME_TYPE).get(0);
            final CheckableImageView view = obtainView();
            view.setChecked(false);
            final String description = mContext.getResources().getString(
                    R.string.quickcontact_clear_defaults_description);
            view.setContentDescription(description);
            view.setImageResource(R.drawable.ic_menu_settings_holo_light);
            view.setOnClickListener(this);
            view.setTag(action);
            mTrack.addView(view);
            containedTypes.remove(ClearDefaultsAction.PSEUDO_MIME_TYPE);
        }

        // Go back to just after PRECEDING_MIMETYPES, and append the rest.
        int index = indexAfterPreceding;
        final String[] remainingTypes = containedTypes.toArray(new String[containedTypes.size()]);
        if (remainingTypes.length > 0) hasData = true;
        Arrays.sort(remainingTypes);
        for (String mimeType : remainingTypes) {
            mTrack.addView(inflateAction(mimeType, cache), index++);
        }

        if (!hasData) {
            // When there is no data to display, add a TextView to show the user there's no data
            View view = mInflater.inflate(R.layout.quickcontact_item_nodata, mTrack, false);
            mTrack.addView(view, index++);
        }
    }

    /**
     * Clears the defaults currently set on the Contact
     */
    private void clearDefaults() {
        final Set<String> mimeTypesKeySet = mDefaultsMap.keySet();
        // Copy to array so that we can modify the HashMap below
        final String[] mimeTypes = new String[mimeTypesKeySet.size()];
        mimeTypesKeySet.toArray(mimeTypes);

        // Send clear default Intents, one by one
        for (String mimeType : mimeTypes) {
            final Action action = mDefaultsMap.get(mimeType);
            final Intent intent =
                    ContactSaveService.createClearPrimaryIntent(mContext, action.getDataId());
            mContext.startService(intent);
            mDefaultsMap.remove(mimeType);
        }

        // Close up and remove the configure default button
        animateCollapse(new Runnable() {
            @Override
            public void run() {
                for (int i = mTrack.getChildCount() - 1; i >= 0; i--) {
                    final CheckableImageView button = (CheckableImageView) mTrack.getChildAt(i);
                    if (button.getTag() instanceof ClearDefaultsAction) {
                        releaseView(button);
                        mTrack.removeViewAt(i);
                        break;
                    }
                }
            }
        });
    }

    /**
     * Consider adding the given {@link Action}, which will only happen if
     * {@link PackageManager} finds an application to handle
     * {@link Action#getIntent()}.
     * @return true if action has been added
     */
    private boolean considerAdd(Action action, ResolveCache resolveCache) {
        if (resolveCache.hasResolve(action)) {
            mActions.put(action.getMimeType(), action);
            return true;
        }
        return false;
    }

    /**
     * Obtain a new {@link CheckableImageView} for a new chiclet, either by
     * recycling one from {@link #mActionPool}, or by inflating a new one. When
     * finished, use {@link #releaseView(CheckableImageView)} to return back into the pool for
     * later recycling.
     */
    private synchronized CheckableImageView obtainView() {
        CheckableImageView view = mActionPool.poll();
        if (view == null || QuickContactActivity.FORCE_CREATE) {
            view = (CheckableImageView) mInflater.inflate(R.layout.quickcontact_item, mTrack,
                    false);
        }
        return view;
    }

    /**
     * Return the given {@link CheckableImageView} into our internal pool for
     * possible recycling during another pass.
     */
    private synchronized void releaseView(CheckableImageView view) {
        mActionPool.offer(view);
        mActionRecycled++;
    }

    /**
     * Inflate the in-track view for the action of the given MIME-type, collapsing duplicate values.
     * Will use the icon provided by the {@link DataKind}.
     */
    private View inflateAction(String mimeType, ResolveCache resolveCache) {
        final CheckableImageView view = obtainView();

        // Add direct intent if single child, otherwise flag for multiple
        List<Action> children = mActions.get(mimeType);
        if (children.size() > 1) {
            Collapser.collapseList(children);
        }
        view.setTag(mimeType);
        final Action firstInfo = children.get(0);

        // Set icon and listen for clicks
        final CharSequence descrip = resolveCache.getDescription(firstInfo);
        final Drawable icon = resolveCache.getIcon(firstInfo);
        view.setChecked(false);
        view.setContentDescription(descrip);
        view.setImageDrawable(icon);
        view.setOnClickListener(this);
        return view;
    }

    /** {@inheritDoc} */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Pass list item clicks along so that Intents are handled uniformly
        onClick(view);
    }

    /**
     * Helper for checking an action view
     */
    private void setNewActionViewChecked(CheckableImageView actionView) {
        if (mLastAction != null) mLastAction.setChecked(false);
        if (actionView != null) actionView.setChecked(true);
        mLastAction = actionView;
    }

    /**
     * Animates collpasing of the disambig area. When done, it expands again to the new size
     */
    private void animateCollapse(final Runnable whenDone) {
        final int oldBottom = mBackground.getBounds().bottom;
        mBackground.setBottomOverride(oldBottom);

        final ObjectAnimator fadeOutAnimator = ObjectAnimator.ofFloat(mFooter, "alpha",
                1.0f, 0.0f);
        fadeOutAnimator.setDuration(ANIMATION_FADE_OUT_TIME);
        fadeOutAnimator.start();

        final ObjectAnimator collapseAnimator = ObjectAnimator.ofInt(mBackground,
                "bottomOverride", oldBottom, oldBottom - mFooter.getHeight());
        collapseAnimator.setDuration(ANIMATION_COLLAPSE_TIME);
        collapseAnimator.setStartDelay(ANIMATION_FADE_OUT_TIME);
        collapseAnimator.start();

        collapseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mFooter.setVisibility(View.GONE);
                new Handler().post(whenDone);
            }
        });
    }

    /**
     * Animates expansion of the disambig area.
     * @param showClearDefaults true to expand to clear defaults. false to expand to intent disambig
     */
    private void animateExpand(boolean showClearDefaults) {
        mFooter.setVisibility(View.VISIBLE);
        mFooterDisambig.setVisibility(showClearDefaults ? View.GONE : View.VISIBLE);
        mFooterClearDefaults.setVisibility(showClearDefaults ? View.VISIBLE : View.GONE);
        final int oldBottom = mBackground.getBounds().bottom;
        mBackground.setBottomOverride(oldBottom);
        mFooter.setAlpha(0.0f);
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                final int newBottom = mBackground.getBounds().bottom;
                final ObjectAnimator expandAnimator = ObjectAnimator.ofInt(mBackground,
                        "bottomOverride", oldBottom, newBottom);
                expandAnimator.setDuration(ANIMATION_EXPAND_TIME);
                expandAnimator.start();

                final ObjectAnimator fadeInAnimator = ObjectAnimator.ofFloat(mFooter,
                        "alpha", 0.0f, 1.0f);
                fadeInAnimator.setDuration(ANIMATION_FADE_IN_TIME);
                fadeInAnimator.setStartDelay(ANIMATION_EXPAND_TIME);
                fadeInAnimator.start();
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public void onClick(View view) {
        final boolean isActionView = (view instanceof CheckableImageView);
        final CheckableImageView actionView = isActionView ? (CheckableImageView)view : null;
        final Object tag = view.getTag();
        if (tag instanceof ClearDefaultsAction) {
            // Do nothing if already open
            if (actionView == mLastAction) return;
            // collapse any disambig that may already be open. the open clearing-defaults
            final Runnable expandClearDefaultsRunnable = new Runnable() {
                @Override
                public void run() {
                    // Show resolution list and set adapter
                    setNewActionViewChecked(actionView);
                    final Action[] actions = new Action[mDefaultsMap.size()];
                    mDefaultsMap.values().toArray(actions);

                    mDefaultsListView.setAdapter(new BaseAdapter() {
                        @Override
                        public int getCount() {
                            return actions.length;
                        }

                        @Override
                        public Object getItem(int position) {
                            return actions[position];
                        }

                        @Override
                        public long getItemId(int position) {
                            return position;
                        }

                        @Override
                        public boolean areAllItemsEnabled() {
                            return false;
                        }

                        @Override
                        public boolean isEnabled(int position) {
                            return false;
                        }

                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            final View result = convertView != null ? convertView :
                                    mInflater.inflate(R.layout.quickcontact_default_item,
                                    parent, false);
                            // Set action title based on summary value
                            final Action defaultAction = actions[position];

                            TextView text1 = (TextView)result.findViewById(android.R.id.text1);
                            TextView text2 = (TextView)result.findViewById(android.R.id.text2);

                            text1.setText(defaultAction.getHeader());
                            text2.setText(defaultAction.getBody());

                            result.setTag(defaultAction);
                            return result;
                        }
                    });

                    animateExpand(true);
                    // Make sure we resize to make room for ListView
                    if (mDecor != null) {
                        mDecor.forceLayout();
                        mDecor.invalidate();
                    }
                }
            };
            if (mFooter.getVisibility() == View.VISIBLE) {
                animateCollapse(expandClearDefaultsRunnable);
            } else {
                new Handler().post(expandClearDefaultsRunnable);
            }
            return;
        }

        // Determine whether to launch a specific Action or show a disambig-List
        final Action action;
        final List<Action> actionList;
        final boolean fromDisambigList;
        final String mimeType;
        if (tag instanceof Action) {
            action = (Action) tag;
            actionList = null;
            fromDisambigList = true;
            mimeType = action.getMimeType();
        } else if (tag instanceof String) {
            mimeType = (String) tag;
            actionList = mActions.get(mimeType);

            if (actionList.size() == 1) {
                // Just one item? Pick that one
                action = actionList.get(0);
            } else if (mDefaultsMap.containsKey(mimeType)) {
                // Default item? pick that one
                action = mDefaultsMap.get(mimeType);
            } else {
                // Several actions and none is default.
                action = null;
            }
            fromDisambigList = false;
        } else {
            throw new IllegalStateException("tag is neither Action nor (mimetype-) String");
        }

        if (action != null) {
            final boolean isVirtual = VIRTUAL_MIMETYPES.contains(mimeType);
            final boolean makePrimary = fromDisambigList && mSetPrimaryCheckBox.isChecked() &&
                    !isVirtual;
            final Runnable startAppRunnable = new Runnable() {
                @Override
                public void run() {
                    // Incoming tag is concrete intent, so try launching
                    try {
                        mContext.startActivity(action.getIntent());
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(mContext, R.string.quickcontact_missing_app,
                                Toast.LENGTH_SHORT).show();
                    }

                    // Hide the resolution list, if present
                    setNewActionViewChecked(null);
                    dismiss();
                    mFooter.setVisibility(View.GONE);

                    // Set default?
                    final long dataId = action.getDataId();
                    if (makePrimary && dataId != -1) {
                        Intent serviceIntent = ContactSaveService.createSetSuperPrimaryIntent(
                                mContext, dataId);
                        mContext.startService(serviceIntent);
                    }
                }
            };
            if (isActionView && mFooter.getVisibility() == View.VISIBLE) {
                // If the footer is currently opened, animate its collapse and then
                // execute the target app
                animateCollapse(startAppRunnable);
            } else {
                // Defer the action to make the window properly repaint
                new Handler().post(startAppRunnable);
            }
            return;
        }

        // This was not a specific Action. Expand the disambig-list
        if (actionList == null) throw new IllegalStateException();

        // Don't do anything if already open
        if (actionView == mLastAction) return;
        final Runnable configureListRunnable = new Runnable() {
            @Override
            public void run() {
                // Show resolution list and set adapter
                setNewActionViewChecked(actionView);
                final boolean isVirtual = VIRTUAL_MIMETYPES.contains(mimeType);
                mSetPrimaryCheckBox.setVisibility(isVirtual ? View.GONE : View.VISIBLE);

                mResolveList.setAdapter(new BaseAdapter() {
                    @Override
                    public int getCount() {
                        return actionList.size();
                    }

                    @Override
                    public Object getItem(int position) {
                        return actionList.get(position);
                    }

                    @Override
                    public long getItemId(int position) {
                        return position;
                    }

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        final View result = convertView != null ? convertView :
                                mInflater.inflate(R.layout.quickcontact_resolve_item,
                                parent, false);
                        // Set action title based on summary value
                        final Action listAction = actionList.get(position);

                        TextView text1 = (TextView)result.findViewById(android.R.id.text1);
                        TextView text2 = (TextView)result.findViewById(android.R.id.text2);

                        text1.setText(listAction.getHeader());
                        text2.setText(listAction.getBody());

                        result.setTag(listAction);
                        return result;
                    }
                });

                animateExpand(false);
                // Make sure we resize to make room for ListView
                if (mDecor != null) {
                    mDecor.forceLayout();
                    mDecor.invalidate();
                }
            }
        };
        if (mFooter.getVisibility() == View.VISIBLE) {
            // If the expansion list is currently opened, animate its collapse and then
            // execute the target app
            animateCollapse(configureListRunnable);
        } else {
            // Defer the action to make the window properly repaint
            configureListRunnable.run();
        }
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mWindow.superDispatchKeyEvent(event)) {
            return true;
        }
        return event.dispatch(this, mDecor != null
                ? mDecor.getKeyDispatcherState() : null, this);
    }

    /** {@inheritDoc} */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking();
            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking()
                && !event.isCanceled()) {
            onBackPressed();
            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // TODO: make this window accessible
        return false;
    }

    /**
     * Detect if the given {@link MotionEvent} is outside the boundaries of this
     * window, which usually means we should dismiss.
     */
    protected void detectEventOutside(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && mDecor != null) {
            // Only try detecting outside events on down-press
            mDecor.getHitRect(mRect);
            final int x = (int)event.getX();
            final int y = (int)event.getY();
            if (!mRect.contains(x, y)) {
                event.setAction(MotionEvent.ACTION_OUTSIDE);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        detectEventOutside(event);
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            dismiss();
            return true;
        }
        return mWindow.superDispatchTouchEvent(event);
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return mWindow.superDispatchTrackballEvent(event);
    }

    /** {@inheritDoc} */
    @Override
    public void onContentChanged() {
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public View onCreatePanelView(int featureId) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void onPanelClosed(int featureId, Menu menu) {
    }

    /** {@inheritDoc} */
    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onSearchRequested() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void onWindowAttributesChanged(android.view.WindowManager.LayoutParams attrs) {
        if (mDecor != null) {
            mWindowManager.updateViewLayout(mDecor, attrs);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
    }

    /** {@inheritDoc} */
    @Override
    public void onAttachedToWindow() {
        // No actions
    }

    /** {@inheritDoc} */
    @Override
    public void onDetachedFromWindow() {
        // No actions
    }

    /** {@inheritDoc} */
    @Override
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return null;
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
    }

    private interface DataQuery {
        final String[] PROJECTION = new String[] {
                Data._ID,

                RawContacts.ACCOUNT_TYPE,
                Contacts.STARRED,
                Contacts.DISPLAY_NAME,
                Contacts.CONTACT_PRESENCE,

                Data.STATUS,
                Data.STATUS_RES_PACKAGE,
                Data.STATUS_ICON,
                Data.STATUS_LABEL,
                Data.STATUS_TIMESTAMP,
                Data.PRESENCE,

                Data.RES_PACKAGE,
                Data.MIMETYPE,
                Data.IS_PRIMARY,
                Data.IS_SUPER_PRIMARY,
                Data.RAW_CONTACT_ID,

                Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5,
                Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10, Data.DATA11,
                Data.DATA12, Data.DATA13, Data.DATA14, Data.DATA15,
        };

        final int _ID = 0;

        final int ACCOUNT_TYPE = 1;
        final int STARRED = 2;
        final int DISPLAY_NAME = 3;
        final int CONTACT_PRESENCE = 4;

        final int STATUS = 5;
        final int STATUS_RES_PACKAGE = 6;
        final int STATUS_ICON = 7;
        final int STATUS_LABEL = 8;
        final int STATUS_TIMESTAMP = 9;
        final int PRESENCE = 10;

        final int RES_PACKAGE = 11;
        final int MIMETYPE = 12;
        final int IS_PRIMARY = 13;
        final int IS_SUPER_PRIMARY = 14;
    }
}
