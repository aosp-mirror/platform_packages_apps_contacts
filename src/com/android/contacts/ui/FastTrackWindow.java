/*
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

package com.android.contacts.ui;

import com.android.contacts.NotifyingAsyncQueryHandler;
import com.android.contacts.R;
import com.android.contacts.NotifyingAsyncQueryHandler.QueryCompleteListener;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.internal.policy.PolicyManager;

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
import android.provider.ContactsContract;
import android.provider.SocialContract;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Presence;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.SocialContract.Activities;
import android.text.format.DateUtils;
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
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

/**
 * Window that shows fast-track contact details for a specific
 * {@link Contacts#_ID}.
 */
public class FastTrackWindow implements Window.Callback, QueryCompleteListener, OnClickListener,
        AbsListView.OnItemClickListener {
    private static final String TAG = "FastTrackWindow";

    /**
     * Interface used to allow the person showing a {@link FastTrackWindow} to
     * know when the window has been dismissed.
     */
    public interface OnDismissListener {
        public void onDismiss(FastTrackWindow dialog);
    }

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final LayoutInflater mInflater;
    private final WindowManager mWindowManager;
    private Window mWindow;
    private View mDecor;

    private boolean mQuerying = false;
    private boolean mShowing = false;

    private NotifyingAsyncQueryHandler mHandler;
    private OnDismissListener mDismissListener;

    private long mAggId;
    private Rect mAnchor;

    private boolean mHasSummary = false;
    private boolean mHasSocial = false;
    private boolean mHasActions = false;

    private ImageView mArrowUp;
    private ImageView mArrowDown;

    private View mHeader;
    private HorizontalScrollView mTrackScroll;
    private ViewGroup mTrack;
    private Animation mTrackAnim;
    private ListView mResolveList;

    /**
     * Set of {@link Action} that are associated with the aggregate currently
     * displayed by this fast-track window, represented as a map from
     * {@link String} MIME-type to {@link ActionList}.
     */
    private ActionMap mActions = new ActionMap();

    /**
     * Specific MIME-type for {@link Phone#CONTENT_ITEM_TYPE} entries that
     * distinguishes actions that should initiate a text message.
     */
    // TODO: We should move this to someplace more general as it is needed in a
    // few places in the app code.
    public static final String MIME_SMS_ADDRESS = "vnd.android.cursor.item/sms-address";

    private static final String SCHEME_TEL = "tel";
    private static final String SCHEME_SMSTO = "smsto";
    private static final String SCHEME_MAILTO = "mailto";

    /**
     * Specific mime-types that should be bumped to the front of the fast-track.
     * Other mime-types not appearing in this list follow in alphabetic order.
     */
    private static final String[] ORDERED_MIMETYPES = new String[] {
        Phones.CONTENT_ITEM_TYPE,
        Contacts.CONTENT_ITEM_TYPE,
        MIME_SMS_ADDRESS,
        Email.CONTENT_ITEM_TYPE,
    };

    private static final int TOKEN_SUMMARY = 1;
    private static final int TOKEN_SOCIAL = 2;
    private static final int TOKEN_DATA = 3;

    /**
     * Prepare a fast-track window to show in the given {@link Context}.
     */
    public FastTrackWindow(Context context) {
        mContext = new ContextThemeWrapper(context, R.style.FastTrack);
        mPackageManager = context.getPackageManager();
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

        mWindow = PolicyManager.makeNewWindow(mContext);
        mWindow.setCallback(this);
        mWindow.setWindowManager(mWindowManager, null, null);

        mWindow.setContentView(R.layout.fasttrack);

        mArrowUp = (ImageView)mWindow.findViewById(R.id.arrow_up);
        mArrowDown = (ImageView)mWindow.findViewById(R.id.arrow_down);

        mTrack = (ViewGroup)mWindow.findViewById(R.id.fasttrack);
        mTrackScroll = (HorizontalScrollView)mWindow.findViewById(R.id.scroll);
        mResolveList = (ListView)mWindow.findViewById(android.R.id.list);

        // Prepare track entrance animation
        mTrackAnim = AnimationUtils.loadAnimation(mContext, R.anim.fasttrack);
        mTrackAnim.setInterpolator(new Interpolator() {
            public float getInterpolation(float t) {
                // Pushes past the target area, then snaps back into place.
                // Equation for graphing: 1.2-((x*1.6)-1.1)^2
                final float inner = (t * 1.55f) - 1.1f;
                return 1.2f - inner * inner;
            }
        });
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

    private View getHeaderView(int mode) {
        View header = null;
        switch (mode) {
            case Intents.MODE_SMALL:
                header = mWindow.findViewById(R.id.header_small);
                break;
            case Intents.MODE_MEDIUM:
                header = mWindow.findViewById(R.id.header_medium);
                break;
            case Intents.MODE_LARGE:
                header = mWindow.findViewById(R.id.header_large);
                break;
        }

        if (header instanceof ViewStub) {
            // Inflate actual header if we picked a stub
            final ViewStub stub = (ViewStub)header;
            header = stub.inflate();
        } else {
            header.setVisibility(View.VISIBLE);
        }

        return header;
    }

    /**
     * Start showing a fast-track window for the given {@link Contacts#_ID}
     * pointing towards the given location.
     */
    public void show(Uri aggUri, Rect anchor, int mode) {
        if (mShowing || mQuerying) {
            Log.w(TAG, "already in process of showing");
            return;
        }

        // Prepare header view for requested mode
        mHeader = getHeaderView(mode);

        setHeaderText(R.id.name, R.string.fasttrack_missing_name);
        setHeaderText(R.id.status, R.string.fasttrack_missing_status);
        setHeaderText(R.id.published, null);
        setHeaderImage(R.id.presence, null);

        mAggId = ContentUris.parseId(aggUri);
        mAnchor = new Rect(anchor);
        mQuerying = true;

        Uri aggSummary = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_SUMMARY_URI, mAggId);
        Uri aggSocial = ContentUris.withAppendedId(
                SocialContract.Activities.CONTENT_CONTACT_STATUS_URI, mAggId);
        Uri aggData = Uri.withAppendedPath(aggUri,
                ContactsContract.Contacts.Data.CONTENT_DIRECTORY);

        // Start data query in background
        mHandler = new NotifyingAsyncQueryHandler(mContext, this);
        mHandler.startQuery(TOKEN_SUMMARY, null, aggSummary, null, null, null, null);
        mHandler.startQuery(TOKEN_SOCIAL, null, aggSocial, null, null, null, null);
        mHandler.startQuery(TOKEN_DATA, null, aggData, null, null, null, null);
    }

    /**
     * Show the correct call-out arrow based on a {@link R.id} reference.
     */
    private void showArrow(int whichArrow, int requestedX) {
        final View showArrow = (whichArrow == R.id.arrow_up) ? mArrowUp : mArrowDown;
        final View hideArrow = (whichArrow == R.id.arrow_up) ? mArrowDown : mArrowUp;

        final int arrowWidth = mArrowUp.getMeasuredWidth();

        showArrow.setVisibility(View.VISIBLE);
        ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams)showArrow.getLayoutParams();
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
        final int arrowHeight = mArrowUp.getDrawable().getIntrinsicHeight();

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

        l.dimAmount = 0.0f;
        l.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;

        mWindowManager.addView(mDecor, l);
        mShowing = true;
        mQuerying = false;

        mTrack.startAnimation(mTrackAnim);
    }

    /**
     * Dismiss this fast-track window if showing.
     */
    public void dismiss() {
        if (!isShowing()) {
            Log.d(TAG, "not visible, ignore");
            return;
        }

        // Completely hide header from current mode
        mHeader.setVisibility(View.GONE);

        // Cancel any pending queries
        mHandler.cancelOperation(TOKEN_SUMMARY);
        mHandler.cancelOperation(TOKEN_SOCIAL);
        mHandler.cancelOperation(TOKEN_DATA);

        // Clear track actions and scroll to hard left
        mActions.clear();
        mTrack.removeViews(1, mTrack.getChildCount() - 2);
        mTrackScroll.fullScroll(View.FOCUS_LEFT);
        mWasDownArrow = false;

        showResolveList(View.GONE);

        mQuerying = false;
        mHasSummary = false;
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
            showInternal();
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cursor == null) {
            // Problem while running query, so bail without showing
            Log.w(TAG, "Missing cursor for token=" + token);
            this.dismiss();
            return;
        }

        switch (token) {
            case TOKEN_SUMMARY:
                handleSummary(cursor);
                mHasSummary = true;
                break;
            case TOKEN_SOCIAL:
                handleSocial(cursor);
                mHasSocial = true;
                break;
            case TOKEN_DATA:
                handleData(cursor);
                mHasActions = true;
                break;
        }

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
        }
    }

    /** Assign this image to the view, if found in {@link #mHeader}. */
    private void setHeaderImage(int id, int resId) {
        setHeaderImage(id, mContext.getResources().getDrawable(resId));
    }

    /** Assign this image to the view, if found in {@link #mHeader}. */
    private void setHeaderImage(int id, Drawable drawable) {
        final View view = mHeader.findViewById(id);
        if (view instanceof ImageView) {
            ((ImageView)view).setImageDrawable(drawable);
        }
    }

    /**
     * Handle the result from the {@link #TOKEN_SUMMARY} query.
     */
    private void handleSummary(Cursor cursor) {
        if (cursor == null || !cursor.moveToNext()) return;

        final String name = getAsString(cursor, Contacts.DISPLAY_NAME);
        final int status = getAsInteger(cursor, Contacts.PRESENCE_STATUS);
        final Drawable statusIcon = getPresenceIcon(status);

        setHeaderText(R.id.name, name);
        setHeaderImage(R.id.presence, statusIcon);
    }

    /**
     * Handle the result from the {@link #TOKEN_SOCIAL} query.
     */
    private void handleSocial(Cursor cursor) {
        if (cursor == null || !cursor.moveToNext()) return;

        final String status = getAsString(cursor, Activities.TITLE);
        final long published = getAsLong(cursor, Activities.PUBLISHED);
        final CharSequence relativePublished = DateUtils.getRelativeTimeSpanString(published,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);

        setHeaderText(R.id.status, status);
        setHeaderText(R.id.published, relativePublished);
    }

    /**
     * Find the presence icon for showing in summary header.
     */
    private Drawable getPresenceIcon(int status) {
        int resId = -1;
        switch (status) {
            case Presence.AVAILABLE:
                resId = android.R.drawable.presence_online;
                break;
            case Presence.IDLE:
            case Presence.AWAY:
                resId = android.R.drawable.presence_away;
                break;
            case Presence.DO_NOT_DISTURB:
                resId = android.R.drawable.presence_busy;
                break;
        }
        if (resId > 0) {
            return mContext.getResources().getDrawable(resId);
        } else {
            return null;
        }
    }

    /**
     * Find the Fast-Track-specific presence icon for showing in chiclets.
     */
    private Drawable getTrackPresenceIcon(int status) {
        int resId = -1;
        switch (status) {
            case Presence.AVAILABLE:
                resId = R.drawable.fasttrack_slider_presence_active;
                break;
            case Presence.IDLE:
            case Presence.AWAY:
                resId = R.drawable.fasttrack_slider_presence_away;
                break;
            case Presence.DO_NOT_DISTURB:
                resId = R.drawable.fasttrack_slider_presence_busy;
                break;
            case Presence.INVISIBLE:
                resId = R.drawable.fasttrack_slider_presence_inactive;
                break;
            case Presence.OFFLINE:
            default:
                resId = R.drawable.fasttrack_slider_presence_inactive;
        }
        return mContext.getResources().getDrawable(resId);
    }

    /** Read {@link String} from the given {@link Cursor}. */
    private static String getAsString(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        return cursor.getString(index);
    }

    /** Read int from the given {@link Cursor}. */
    private static int getAsInteger(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        return cursor.getInt(index);
    }

    /** Read long from the given {@link Cursor}. */
    private static long getAsLong(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        return cursor.getLong(index);
    }

    /**
     * Abstract definition of an action that could be performed, along with
     * string description and icon.
     */
    private interface Action {
        public CharSequence getHeader();
        public CharSequence getBody();
        public Drawable getIcon();

        /**
         * Build an {@link Intent} that will perform this action.
         */
        public Intent getIntent();
    }

    /**
     * Description of a specific {@link Data#_ID} item, with style information
     * defined by a {@link DataKind}.
     */
    private static class DataAction implements Action {
        private final Context mContext;
        private final ContactsSource mSource;
        private final DataKind mKind;

        private CharSequence mHeader;
        private CharSequence mBody;
        private Intent mIntent;

        private boolean mAlternate;

        /**
         * Create an action from common {@link Data} elements.
         */
        public DataAction(Context context, ContactsSource source, String mimeType, DataKind kind, Cursor cursor) {
            mContext = context;
            mSource = source;
            mKind = kind;

            // Inflate strings from cursor
            mAlternate = MIME_SMS_ADDRESS.equals(mimeType);
            if (mAlternate && mKind.actionAltHeader != null) {
                mHeader = mKind.actionAltHeader.inflateUsing(context, cursor);
            } else if (mKind.actionHeader != null) {
                mHeader = mKind.actionHeader.inflateUsing(context, cursor);
            }

            if (mKind.actionBody != null) {
                mBody = mKind.actionBody.inflateUsing(context, cursor);
            }

            // Handle well-known MIME-types with special care
            if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final String number = getAsString(cursor, Phone.NUMBER);
                final Uri callUri = Uri.fromParts(SCHEME_TEL, number, null);
                mIntent = new Intent(Intent.ACTION_DIAL, callUri);

            } else if (MIME_SMS_ADDRESS.equals(mimeType)) {
                final String number = getAsString(cursor, Phone.NUMBER);
                final Uri smsUri = Uri.fromParts(SCHEME_SMSTO, number, null);
                mIntent = new Intent(Intent.ACTION_SENDTO, smsUri);

            } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final String address = getAsString(cursor, Email.DATA);
                final Uri mailUri = Uri.fromParts(SCHEME_MAILTO, address, null);
                mIntent = new Intent(Intent.ACTION_SENDTO, mailUri);

            } else {
                // Otherwise fall back to default VIEW action
                final long dataId = getAsLong(cursor, Data._ID);
                final Uri dataUri = ContentUris.withAppendedId(Data.CONTENT_URI, dataId);
                mIntent = new Intent(Intent.ACTION_VIEW, dataUri);
            }
        }

        /** {@inheritDoc} */
        public CharSequence getHeader() {
            return mHeader;
        }

        /** {@inheritDoc} */
        public CharSequence getBody() {
            return mBody;
        }

        /** {@inheritDoc} */
        public Drawable getIcon() {
            // Bail early if no valid resources
            if (mSource.resPackageName == null) return null;

            final PackageManager pm = mContext.getPackageManager();
            if (mAlternate && mKind.iconAltRes > 0) {
                return pm.getDrawable(mSource.resPackageName, mKind.iconAltRes, null);
            } else if (mKind.iconRes > 0) {
                return pm.getDrawable(mSource.resPackageName, mKind.iconRes, null);
            } else {
                return null;
            }
        }

        /** {@inheritDoc} */
        public Intent getIntent() {
            return mIntent;
        }
    }

    private static class ProfileAction implements Action {
        private final Context mContext;
        private final long mId;

        public ProfileAction(Context context, long contactId) {
            mContext = context;
            mId = contactId;
        }

        /** {@inheritDoc} */
        public CharSequence getHeader() {
            return null;
        }

        /** {@inheritDoc} */
        public CharSequence getBody() {
            return null;
        }

        /** {@inheritDoc} */
        public Drawable getIcon() {
            return mContext.getResources().getDrawable(R.drawable.ic_contacts_details);
        }

        /** {@inheritDoc} */
        public Intent getIntent() {
            final Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, mId);
            return new Intent(Intent.ACTION_VIEW, contactUri);
        }
    }

    /**
     * Provide a strongly-typed {@link LinkedList} that holds a list of
     * {@link Action} objects.
     */
    private class ActionList extends LinkedList<Action> {
    }

    /**
     * Provide a simple way of collecting one or more {@link Action} objects
     * under a MIME-type key.
     */
    private class ActionMap extends HashMap<String, ActionList> {
        private void collect(String mimeType, Action info) {
            // Create list for this MIME-type when needed
            ActionList collectList = get(mimeType);
            if (collectList == null) {
                collectList = new ActionList();
                put(mimeType, collectList);
            }
            collectList.add(info);
        }
    }

    /**
     * Handle the result from the {@link #TOKEN_DATA} query.
     */
    private void handleData(Cursor cursor) {
        if (cursor == null) return;

        final ContactsSource defaultSource = Sources.getInstance(mContext).getSourceForType(
                Sources.ACCOUNT_TYPE_GOOGLE);

        {
            // Add the profile shortcut action
            final Action action = new ProfileAction(mContext, mAggId);
            mActions.collect(Contacts.CONTENT_ITEM_TYPE, action);
        }

        final ImageView photoView = (ImageView)mHeader.findViewById(R.id.photo);

        while (cursor.moveToNext()) {
            final String accountType = getAsString(cursor, RawContacts.ACCOUNT_TYPE);
            final String resPackage = getAsString(cursor, Data.RES_PACKAGE);
            final String mimeType = getAsString(cursor, Data.MIMETYPE);

            // Handle when a photo appears in the various data items
            // TODO: accept a photo only if its marked as primary
            // TODO: move to using photo thumbnail columns, when they exist
            if (photoView != null && Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final int colPhoto = cursor.getColumnIndex(Photo.PHOTO);
                final byte[] photoBlob = cursor.getBlob(colPhoto);
                final Bitmap photoBitmap = BitmapFactory.decodeByteArray(photoBlob, 0,
                        photoBlob.length);
                photoView.setImageBitmap(photoBitmap);
                continue;
            }

            // TODO: find the ContactsSource for this, either from accountType,
            // or through lazy-loading when resPackage is set, or default.
            final ContactsSource source = defaultSource;
            final DataKind kind = source.getKindForMimetype(mimeType);

            if (kind != null) {
                // Build an action for this data entry, find a mapping to a UI
                // element, build its summary from the cursor, and collect it
                // along with all others of this MIME-type.
                final Action action = new DataAction(mContext, source, mimeType, kind, cursor);
                considerAdd(action, mimeType);
            }

            // If phone number, also insert as text message action
            if (Phones.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final Action action = new DataAction(mContext, source, MIME_SMS_ADDRESS, kind,
                        cursor);
                considerAdd(action, MIME_SMS_ADDRESS);
            }
        }

        // Turn our list of actions into UI elements, starting with common types
        final Set<String> containedTypes = mActions.keySet();
        for (String mimeType : ORDERED_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                final int index = mTrack.getChildCount() - 1;
                mTrack.addView(inflateAction(mimeType), index);
                containedTypes.remove(mimeType);
            }
        }

        // Then continue with remaining MIME-types in alphabetical order
        final String[] remainingTypes = containedTypes.toArray(new String[containedTypes.size()]);
        Arrays.sort(remainingTypes);
        for (String mimeType : remainingTypes) {
            final int index = mTrack.getChildCount() - 1;
            mTrack.addView(inflateAction(mimeType), index);
        }
    }

    /**
     * Consider adding the given {@link Action}, which will only happen if
     * {@link PackageManager} finds an application to handle
     * {@link Action#getIntent()}.
     */
    private void considerAdd(Action action, String mimeType) {
        final Intent intent = action.getIntent();
        final boolean intentHandled = mPackageManager.queryIntentActivities(intent, 0).size() > 0;
        if (intentHandled) {
            mActions.collect(mimeType, action);
        }
    }

    /**
     * Inflate the in-track view for the action of the given MIME-type. Will use
     * the icon provided by the {@link DataKind}.
     */
    private View inflateAction(String mimeType) {
        ImageView view = (ImageView)mInflater.inflate(R.layout.fasttrack_item, mTrack, false);

        // Add direct intent if single child, otherwise flag for multiple
        ActionList children = mActions.get(mimeType);
        Action firstInfo = children.get(0);
        if (children.size() == 1) {
            view.setTag(firstInfo.getIntent());
        } else {
            view.setTag(children);
        }

        // Set icon and listen for clicks
        view.setImageDrawable(firstInfo.getIcon());
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

            // Dismiss track entirely if switching to dialer
            final Intent intent = (Intent)tag;
            final String action = intent.getAction();
            if (Intent.ACTION_DIAL.equals(action)) {
                this.dismiss();
            }

            try {
                // Incoming tag is concrete intent, so try launching
                mContext.startActivity((Intent)tag);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mContext, R.string.fasttrack_missing_app, Toast.LENGTH_SHORT).show();
            }
        } else if (tag instanceof ActionList) {
            // Incoming tag is a MIME-type, so show resolution list
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
                    final Action action = (Action)getItem(position);

                    ImageView icon1 = (ImageView)convertView.findViewById(android.R.id.icon1);
                    TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
                    TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);

                    icon1.setImageDrawable(action.getIcon());
                    text1.setText(action.getHeader());
                    text2.setText(action.getBody());

                    convertView.setTag(action.getIntent());
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
