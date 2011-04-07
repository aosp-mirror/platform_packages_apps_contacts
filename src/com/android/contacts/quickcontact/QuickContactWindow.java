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
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.DataKind;
import com.android.contacts.util.Constants;
import com.android.contacts.util.DataStatus;
import com.android.contacts.util.NotifyingAsyncQueryHandler;
import com.google.android.collect.Lists;
import com.google.common.base.Preconditions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
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
import android.os.Bundle;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mostly translucent {@link Activity} that shows QuickContact dialog. It loads
 * data asynchronously, and then shows a popup with details centered around
 * {@link Intent#getSourceBounds()}.
 */
public class QuickContactWindow extends Activity implements
        NotifyingAsyncQueryHandler.AsyncQueryListener, View.OnClickListener,
        AbsListView.OnItemClickListener {
    private static final String TAG = "QuickContact";

    private static final boolean TRACE_LAUNCH = false;
    private static final String TRACE_TAG = "quickcontact";

    private static final int ANIMATION_FADE_IN_TIME = 100;
    private static final int ANIMATION_FADE_OUT_TIME = 100;
    private static final int ANIMATION_EXPAND_TIME = 100;
    private static final int ANIMATION_COLLAPSE_TIME = 100;

    private NotifyingAsyncQueryHandler mHandler;

    private Uri mLookupUri;
    private int mMode;
    private String[] mExcludeMimes;

    private boolean mHasValidSocial = false;

    private FloatingChildLayout mFloatingLayout;
    private QuickContactBackgroundDrawable mBackground;

    private View mHeader;
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

    /**
     * Set of {@link Action} that are associated with the aggregate currently
     * displayed by this dialog, represented as a map from {@link String}
     * MIME-type to a list of {@link Action}.
     */
    private ActionMultiMap mActions = new ActionMultiMap();

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
            Constants.MIME_TYPE_SMS_ADDRESS,
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
            Im.CONTENT_ITEM_TYPE,
            Constants.MIME_TYPE_SMS_ADDRESS,
            Constants.MIME_TYPE_VIDEO_CHAT,
    });
    private static final int TOKEN_DATA = 1;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.quickcontact_activity);

        mBackground = new QuickContactBackgroundDrawable(getResources());

        mFloatingLayout = findTypedViewById(R.id.floating_layout);
        mFloatingLayout.getChild().setBackgroundDrawable(mBackground);
        mFloatingLayout.setOnOutsideTouchListener(mOnOutsideTouchListener);

        mTrack = findTypedViewById(R.id.quickcontact);
        mFooter = findTypedViewById(R.id.footer);
        mFooterDisambig = findTypedViewById(R.id.footer_disambig);
        mFooterClearDefaults = findTypedViewById(R.id.footer_clear_defaults);
        mResolveList = findTypedViewById(android.R.id.list);
        mSetPrimaryCheckBox = findTypedViewById(android.R.id.checkbox);

        mDefaultsListView = findTypedViewById(R.id.defaults_list);

        mClearDefaultsButton = findTypedViewById(R.id.clear_defaults_button);
        mClearDefaultsButton.setOnClickListener(mOnClearDefaultsClickListener);

        mResolveList.setOnItemClickListener(this);

        mHandler = new NotifyingAsyncQueryHandler(this, this);

        show();
    }

    private View getHeaderView(int mode) {
        View header = null;
        switch (mode) {
            case QuickContact.MODE_SMALL:
                header = findViewById(R.id.header_small);
                break;
            case QuickContact.MODE_MEDIUM:
                header = findViewById(R.id.header_medium);
                break;
            case QuickContact.MODE_LARGE:
                header = findViewById(R.id.header_large);
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

    private void show() {

        if (TRACE_LAUNCH) {
            android.os.Debug.startMethodTracing(TRACE_TAG);
        }

        final Intent intent = getIntent();

        Uri lookupUri = intent.getData();

        // Check to see whether it comes from the old version.
        if (android.provider.Contacts.AUTHORITY.equals(lookupUri.getAuthority())) {
            final long rawContactId = ContentUris.parseId(lookupUri);
            lookupUri = RawContacts.getContactLookupUri(getContentResolver(),
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
        }

        mLookupUri = Preconditions.checkNotNull(lookupUri, "missing lookupUri");

        // Read requested parameters for displaying
        final Rect targetScreen = intent.getSourceBounds();
        Preconditions.checkNotNull(targetScreen, "missing targetScreen");
        mFloatingLayout.setChildTargetScreen(targetScreen);

        mMode = intent.getIntExtra(QuickContact.EXTRA_MODE, QuickContact.MODE_MEDIUM);
        mExcludeMimes = intent.getStringArrayExtra(QuickContact.EXTRA_EXCLUDE_MIMES);

        switch (mMode) {
            case QuickContact.MODE_SMALL:
            case QuickContact.MODE_MEDIUM:
            case QuickContact.MODE_LARGE:
                break;
            default:
                throw new IllegalArgumentException("Unexpected mode: " + mMode);
        }

        // find and prepare correct header view
        mHeader = getHeaderView(mMode);
        setHeaderText(R.id.name, R.string.quickcontact_missing_name);
        setHeaderText(R.id.status, null);
        setHeaderText(R.id.timestamp, null);
        setHeaderImage(R.id.presence, null);

        // Start background query for data, but only select photo rows when they
        // directly match the super-primary PHOTO_ID.
        final Uri dataUri = Uri.withAppendedPath(lookupUri, Contacts.Data.CONTENT_DIRECTORY);
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

    @SuppressWarnings("unchecked")
    private <T> T findTypedViewById(int id) {
        return (T) super.findViewById(id);
    }

    private View.OnTouchListener mOnOutsideTouchListener = new View.OnTouchListener() {
        /** {@inheritDoc} */
        public boolean onTouch(View v, MotionEvent event) {
            hide(true);
            return true;
        }
    };

    private View.OnClickListener mOnClearDefaultsClickListener = new View.OnClickListener() {
        /** {@inheritDoc} */
        public void onClick(View v) {
            clearDefaults();
        }
    };

    private void hide(boolean withAnimation) {
        // cancel any pending queries
        mHandler.cancelOperation(TOKEN_DATA);

        if (withAnimation) {
            mFloatingLayout.hideChild(new Runnable() {
                /** {@inheritDoc} */
                public void run() {
                    finish();
                }
            });
        } else {
            mFloatingLayout.hideChild(null);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        hide(true);
    }

    /** {@inheritDoc} */
    public synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
        try {
            if (isFinishing()) {
                hide(false);
                return;
            } else if (cursor == null || cursor.getCount() == 0) {
                Toast.makeText(this, R.string.invalidContactMessage, Toast.LENGTH_LONG).show();
                hide(false);
                return;
            }

            bindData(cursor);

            if (mMode == QuickContact.MODE_MEDIUM && !mHasValidSocial) {
                // Missing valid social, swap medium for small header
                mHeader.setVisibility(View.GONE);
                mHeader = getHeaderView(QuickContact.MODE_SMALL);
            }

            if (TRACE_LAUNCH) {
                android.os.Debug.stopMethodTracing();
            }

            // data bound and ready, pull curtain to show
            mFloatingLayout.showChild();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /** Assign this string to the view, if found in {@link #mHeader}. */
    private void setHeaderText(int id, int resId) {
        setHeaderText(id, getText(resId));
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
    private void bindData(Cursor cursor) {
        final ResolveCache cache = ResolveCache.getInstance(this);
        final Context context = this;

        if (!isMimeExcluded(Contacts.CONTENT_ITEM_TYPE)) {
            // Add the profile shortcut action
            final Action action = new ProfileAction(context, mLookupUri);
            mActions.put(Contacts.CONTENT_ITEM_TYPE, action);
        }

        mDefaultsMap.clear();

        final DataStatus status = new DataStatus();
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(
                context.getApplicationContext());
        final ImageView photoView = (ImageView) mHeader.findViewById(R.id.photo);

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

            final DataKind kind = accountTypes.getKindOrFallback(accountType, mimeType);

            if (kind != null) {
                // Build an action for this data entry, find a mapping to a UI
                // element, build its summary from the cursor, and collect it
                // along with all others of this MIME-type.
                final Action action = new DataAction(context, mimeType, kind, dataId, cursor);
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
                final DataAction action = new DataAction(context, Constants.MIME_TYPE_SMS_ADDRESS,
                        kind, dataId, cursor);
                considerAdd(action, cache);
            }

            boolean isIm = Im.CONTENT_ITEM_TYPE.equals(mimeType);

            // Handle Email rows with presence data as Im entry
            final boolean hasPresence = !cursor.isNull(DataQuery.PRESENCE);
            if (hasPresence && Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final DataKind imKind = accountTypes.getKindOrFallback(accountType,
                        Im.CONTENT_ITEM_TYPE);
                if (imKind != null) {
                    final DataAction action = new DataAction(context, Im.CONTENT_ITEM_TYPE, imKind,
                            dataId, cursor);
                    considerAdd(action, cache);
                    isIm = true;
                }
            }

            if (hasPresence && isIm) {
                int chatCapability = cursor.getInt(DataQuery.CHAT_CAPABILITY);
                if ((chatCapability & Im.CAPABILITY_HAS_CAMERA) != 0) {
                    final DataKind imKind = accountTypes.getKindOrFallback(accountType,
                            Im.CONTENT_ITEM_TYPE);
                    if (imKind != null) {
                        final DataAction chatAction = new DataAction(context,
                                Constants.MIME_TYPE_VIDEO_CHAT, imKind, dataId, cursor);
                        considerAdd(chatAction, cache);
                    }
                }
            }
        }

        // Collapse Action Lists (remove e.g. duplicate e-mail addresses from different sources)
        for (ArrayList<Action> actionChildren : mActions.values()) {
            Collapser.collapseList(actionChildren);
        }

        // Make sure that we only display the "clear default" action if there
        // are actually several items to chose from
        boolean shouldDisplayClearDefaults = false;
        for (String mimetype : mDefaultsMap.keySet()) {
            if (mActions.get(mimetype).size() > 1) {
                shouldDisplayClearDefaults = true;
                break;
            }
        }

        if (shouldDisplayClearDefaults) {
            final Action action = new ClearDefaultsAction();
            mActions.put(action.getMimeType(), action);
        }

        if (cursor.moveToLast()) {
            // Read contact information from last data row
            final String name = cursor.getString(DataQuery.DISPLAY_NAME);
            final int presence = cursor.getInt(DataQuery.CONTACT_PRESENCE);
            final int chatCapability = cursor.getInt(DataQuery.CONTACT_CHAT_CAPABILITY);
            final Drawable statusIcon = ContactPresenceIconUtil.getChatCapabilityIcon(
                    context, presence, chatCapability);

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
            setHeaderText(R.id.timestamp, status.getTimestampLabel(context));
        }

        // Turn our list of actions into UI elements

        // All the mime-types to add.
        final Set<String> containedTypes = new HashSet<String>(mActions.keySet());

        boolean hasData = false;

        // First, add PRECEDING_MIMETYPES, which are most common.
        for (String mimeType : PRECEDING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                hasData = true;
                mTrack.addView(inflateAction(mimeType, cache, mTrack));
                containedTypes.remove(mimeType);
            }
        }

        // Keep the current index to append non PRECEDING/FOLLOWING items.
        final int indexAfterPreceding = mTrack.getChildCount() - 1;

        // Then, add FOLLOWING_MIMETYPES, which are least common.
        for (String mimeType : FOLLOWING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                hasData = true;
                mTrack.addView(inflateAction(mimeType, cache, mTrack));
                containedTypes.remove(mimeType);
            }
        }

        // Show the clear-defaults button? If yes, it goes to the end of the list
        if (containedTypes.contains(ClearDefaultsAction.PSEUDO_MIME_TYPE)) {
            final ClearDefaultsAction action = (ClearDefaultsAction) mActions.get(
                    ClearDefaultsAction.PSEUDO_MIME_TYPE).get(0);
            final CheckableImageView view = (CheckableImageView) getLayoutInflater().inflate(
                    R.layout.quickcontact_item, mTrack, false);

            view.setChecked(false);
            final String description = context.getResources().getString(
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
            mTrack.addView(inflateAction(mimeType, cache, mTrack), index++);
        }

        if (!hasData) {
            // When there is no data to display, add a TextView to show the user there's no data
            View view = getLayoutInflater().inflate(
                    R.layout.quickcontact_item_nodata, mTrack, false);
            mTrack.addView(view, index++);
        }
    }

    /**
     * Clears the defaults currently set on the Contact
     */
    private void clearDefaults() {
        final Context context = this;
        final Set<String> mimeTypesKeySet = mDefaultsMap.keySet();

        // Copy to array so that we can modify the HashMap below
        final String[] mimeTypes = new String[mimeTypesKeySet.size()];
        mimeTypesKeySet.toArray(mimeTypes);

        // Send clear default Intents, one by one
        for (String mimeType : mimeTypes) {
            final Action action = mDefaultsMap.get(mimeType);
            final Intent intent = ContactSaveService.createClearPrimaryIntent(
                    context, action.getDataId());
            context.startService(intent);
            mDefaultsMap.remove(mimeType);
        }

        // Close up and remove the configure default button
        animateCollapse(new Runnable() {
            @Override
            public void run() {
                for (int i = mTrack.getChildCount() - 1; i >= 0; i--) {
                    final CheckableImageView button = (CheckableImageView) mTrack.getChildAt(i);
                    if (button.getTag() instanceof ClearDefaultsAction) {
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
     * Inflate the in-track view for the action of the given MIME-type, collapsing duplicate values.
     * Will use the icon provided by the {@link DataKind}.
     */
    private View inflateAction(String mimeType, ResolveCache resolveCache, ViewGroup root) {
        final CheckableImageView view = (CheckableImageView) getLayoutInflater().inflate(
                R.layout.quickcontact_item, root, false);

        // Add direct intent if single child, otherwise flag for multiple
        List<Action> children = mActions.get(mimeType);
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

                expandAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mBackground.clearBottomOverride();
                    }
                });

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
        final Context context = this;

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
                            if (convertView == null) {
                                convertView = getLayoutInflater().inflate(
                                        R.layout.quickcontact_default_item, parent, false);
                            }

                            // Set action title based on summary value
                            final Action defaultAction = actions[position];

                            final TextView text1 = (TextView) convertView.findViewById(
                                    android.R.id.text1);
                            final TextView text2 = (TextView) convertView.findViewById(
                                    android.R.id.text2);

                            text1.setText(defaultAction.getHeader());
                            text2.setText(defaultAction.getBody());

                            convertView.setTag(defaultAction);
                            return convertView;
                        }
                    });

                    animateExpand(true);
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
                        context.startActivity(action.getIntent());
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(context, R.string.quickcontact_missing_app,
                                Toast.LENGTH_SHORT).show();
                    }

                    // Hide the resolution list, if present
                    setNewActionViewChecked(null);

                    // Set default?
                    final long dataId = action.getDataId();
                    if (makePrimary && dataId != -1) {
                        Intent serviceIntent = ContactSaveService.createSetSuperPrimaryIntent(
                                context, dataId);
                        context.startService(serviceIntent);
                    }

                    hide(false);
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
                        if (convertView == null) {
                            convertView = getLayoutInflater().inflate(
                                    R.layout.quickcontact_resolve_item, parent, false);
                        }

                        // Set action title based on summary value
                        final Action listAction = actionList.get(position);

                        final TextView text1 = (TextView) convertView.findViewById(
                                android.R.id.text1);
                        final TextView text2 = (TextView) convertView.findViewById(
                                android.R.id.text2);

                        text1.setText(listAction.getHeader());
                        text2.setText(listAction.getBody());

                        convertView.setTag(listAction);
                        return convertView;
                    }
                });

                animateExpand(false);
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


    private interface DataQuery {
        final String[] PROJECTION = new String[] {
                Data._ID,

                RawContacts.ACCOUNT_TYPE,
                Contacts.STARRED,
                Contacts.DISPLAY_NAME,
                Contacts.CONTACT_PRESENCE,
                Contacts.CONTACT_CHAT_CAPABILITY,

                Data.STATUS,
                Data.STATUS_RES_PACKAGE,
                Data.STATUS_ICON,
                Data.STATUS_LABEL,
                Data.STATUS_TIMESTAMP,
                Data.PRESENCE,
                Data.CHAT_CAPABILITY,

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
        final int CONTACT_CHAT_CAPABILITY = 5;

        final int STATUS = 6;
        final int STATUS_RES_PACKAGE = 7;
        final int STATUS_ICON = 8;
        final int STATUS_LABEL = 9;
        final int STATUS_TIMESTAMP = 10;
        final int PRESENCE = 11;
        final int CHAT_CAPABILITY = 12;

        final int RES_PACKAGE = 13;
        final int MIMETYPE = 14;
        final int IS_PRIMARY = 15;
        final int IS_SUPER_PRIMARY = 16;
    }
}
