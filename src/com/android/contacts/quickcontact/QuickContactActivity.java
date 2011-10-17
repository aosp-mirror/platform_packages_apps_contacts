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
import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.DataKind;
import com.android.contacts.util.DataStatus;
import com.android.contacts.util.NotifyingAsyncQueryHandler;
import com.android.contacts.util.NotifyingAsyncQueryHandler.AsyncQueryListener;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
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
import android.provider.ContactsContract.DisplayPhoto;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.SimpleOnPageChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: Save selected tab index during rotation

/**
 * Mostly translucent {@link Activity} that shows QuickContact dialog. It loads
 * data asynchronously, and then shows a popup with details centered around
 * {@link Intent#getSourceBounds()}.
 */
public class QuickContactActivity extends Activity {
    private static final String TAG = "QuickContact";

    private static final boolean TRACE_LAUNCH = false;
    private static final String TRACE_TAG = "quickcontact";

    @SuppressWarnings("deprecation")
    private static final String LEGACY_AUTHORITY = android.provider.Contacts.AUTHORITY;

    private NotifyingAsyncQueryHandler mHandler;

    private Uri mLookupUri;
    private String[] mExcludeMimes;
    private List<String> mSortedActionMimeTypes = Lists.newArrayList();

    private boolean mHasFinishedAnimatingIn = false;
    private boolean mHasStartedAnimatingOut = false;

    private FloatingChildLayout mFloatingLayout;

    private View mPhotoContainer;
    private ViewGroup mTrack;
    private HorizontalScrollView mTrackScroller;
    private View mSelectedTabRectangle;
    private View mLineAfterTrack;

    private ImageButton mOpenDetailsButton;
    private ImageButton mOpenDetailsPushLayerButton;
    private ViewPager mListPager;

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
     * {@link #LEADING_MIMETYPES} and {@link #TRAILING_MIMETYPES} are used to sort MIME-types.
     *
     * <p>The MIME-types in {@link #LEADING_MIMETYPES} appear in the front of the dialog,
     * in the order specified here.</p>
     *
     * <p>The ones in {@link #TRAILING_MIMETYPES} appear in the end of the dialog, in the order
     * specified here.</p>
     *
     * <p>The rest go between them, in the order in the array.</p>
     */
    private static final List<String> LEADING_MIMETYPES = Lists.newArrayList(
            Phone.CONTENT_ITEM_TYPE, SipAddress.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE);

    /** See {@link #LEADING_MIMETYPES}. */
    private static final List<String> TRAILING_MIMETYPES = Lists.newArrayList(
            StructuredPostal.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE);

    /** Id for the background handler that loads the data */
    private static final int HANDLER_ID_DATA = 1;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Show QuickContact in front of soft input
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        setContentView(R.layout.quickcontact_activity);

        mFloatingLayout = (FloatingChildLayout) findViewById(R.id.floating_layout);
        mTrack = (ViewGroup) findViewById(R.id.track);
        mTrackScroller = (HorizontalScrollView) findViewById(R.id.track_scroller);
        mOpenDetailsButton = (ImageButton) findViewById(R.id.open_details_button);
        mOpenDetailsPushLayerButton = (ImageButton) findViewById(R.id.open_details_push_layer);
        mListPager = (ViewPager) findViewById(R.id.item_list_pager);
        mSelectedTabRectangle = findViewById(R.id.selected_tab_rectangle);
        mLineAfterTrack = findViewById(R.id.line_after_track);

        mFloatingLayout.setOnOutsideTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleOutsideTouch();
            }
        });

        final OnClickListener openDetailsClickHandler = new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(Intent.ACTION_VIEW, mLookupUri);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                hide(false);
            }
        };
        mOpenDetailsButton.setOnClickListener(openDetailsClickHandler);
        mOpenDetailsPushLayerButton.setOnClickListener(openDetailsClickHandler);
        mListPager.setAdapter(new ViewPagerAdapter(getFragmentManager()));
        mListPager.setOnPageChangeListener(new PageChangeListener());

        mHandler = new NotifyingAsyncQueryHandler(this, mQueryListener);

        show();
    }

    private void show() {

        if (TRACE_LAUNCH) {
            android.os.Debug.startMethodTracing(TRACE_TAG);
        }

        final Intent intent = getIntent();

        Uri lookupUri = intent.getData();

        // Check to see whether it comes from the old version.
        if (LEGACY_AUTHORITY.equals(lookupUri.getAuthority())) {
            final long rawContactId = ContentUris.parseId(lookupUri);
            lookupUri = RawContacts.getContactLookupUri(getContentResolver(),
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
        }

        mLookupUri = Preconditions.checkNotNull(lookupUri, "missing lookupUri");

        // Read requested parameters for displaying
        final Rect targetScreen = intent.getSourceBounds();
        Preconditions.checkNotNull(targetScreen, "missing targetScreen");
        mFloatingLayout.setChildTargetScreen(targetScreen);

        mExcludeMimes = intent.getStringArrayExtra(QuickContact.EXTRA_EXCLUDE_MIMES);

        // find and prepare correct header view
        mPhotoContainer = findViewById(R.id.photo_container);
        setHeaderNameText(R.id.name, R.string.missing_name);

        // Start background query for data, but only select photo rows when they
        // directly match the super-primary PHOTO_ID.
        final Uri dataUri = Uri.withAppendedPath(lookupUri, Contacts.Data.CONTENT_DIRECTORY);
        mHandler.cancelOperation(HANDLER_ID_DATA);

        // Select all data items of the contact (except for photos, where we only select the display
        // photo)
        mHandler.startQuery(HANDLER_ID_DATA, lookupUri, dataUri, DataQuery.PROJECTION, Data.MIMETYPE
                + "!=? OR (" + Data.MIMETYPE + "=? AND " + Data._ID + "=" + Contacts.PHOTO_ID
                + ")", new String[] { Photo.CONTENT_ITEM_TYPE, Photo.CONTENT_ITEM_TYPE }, null);
    }

    private boolean handleOutsideTouch() {
        if (!mHasFinishedAnimatingIn) return false;
        if (mHasStartedAnimatingOut) return false;

        mHasStartedAnimatingOut = true;
        hide(true);
        return true;
    }

    private void hide(boolean withAnimation) {
        // cancel any pending queries
        mHandler.cancelOperation(HANDLER_ID_DATA);

        if (withAnimation) {
            mFloatingLayout.hideChild(new Runnable() {
                @Override
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

    private final AsyncQueryListener mQueryListener = new AsyncQueryListener() {
        @Override
        public synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
            try {
                if (isFinishing()) {
                    hide(false);
                    return;
                } else if (cursor == null || cursor.getCount() == 0) {
                    Toast.makeText(QuickContactActivity.this, R.string.invalidContactMessage,
                            Toast.LENGTH_LONG).show();
                    hide(false);
                    return;
                }

                bindData(cursor);

                if (TRACE_LAUNCH) {
                    android.os.Debug.stopMethodTracing();
                }

                // Data bound and ready, pull curtain to show. Put this on the Handler to ensure
                // that the layout passes are completed
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mFloatingLayout.showChild(new Runnable() {
                            @Override
                            public void run() {
                                mHasFinishedAnimatingIn = true;
                            }
                        });
                    }
                });
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    };

    /** Assign this string to the view if it is not empty. */
    private void setHeaderNameText(int id, int resId) {
        setHeaderNameText(id, getText(resId));
    }

    /** Assign this string to the view if it is not empty. */
    private void setHeaderNameText(int id, CharSequence value) {
        final View view = mPhotoContainer.findViewById(id);
        if (view instanceof TextView) {
            if (!TextUtils.isEmpty(value)) {
                ((TextView)view).setText(value);
            }
        }
    }

    /**
     * Assign this string to the view (if found in {@link #mPhotoContainer}), or hiding this view
     * if there is no string.
     */
    private void setHeaderText(int id, int resId) {
        setHeaderText(id, getText(resId));
    }

    /**
     * Assign this string to the view (if found in {@link #mPhotoContainer}), or hiding this view
     * if there is no string.
     */
    private void setHeaderText(int id, CharSequence value) {
        final View view = mPhotoContainer.findViewById(id);
        if (view instanceof TextView) {
            ((TextView)view).setText(value);
            view.setVisibility(TextUtils.isEmpty(value) ? View.GONE : View.VISIBLE);
        }
    }

    /** Assign this image to the view, if found in {@link #mPhotoContainer}. */
    private void setHeaderImage(int id, Drawable drawable) {
        final View view = mPhotoContainer.findViewById(id);
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

        mOpenDetailsButton.setVisibility(isMimeExcluded(Contacts.CONTENT_ITEM_TYPE) ? View.GONE
                : View.VISIBLE);

        mDefaultsMap.clear();

        final DataStatus status = new DataStatus();
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(
                context.getApplicationContext());
        final ImageView photoView = (ImageView) mPhotoContainer.findViewById(R.id.photo);

        Bitmap photoBitmap = null;
        while (cursor.moveToNext()) {
            // Handle any social status updates from this row
            status.possibleUpdate(cursor);

            final String mimeType = cursor.getString(DataQuery.MIMETYPE);

            // Skip this data item if MIME-type excluded
            if (isMimeExcluded(mimeType)) continue;

            final long dataId = cursor.getLong(DataQuery._ID);
            final String accountType = cursor.getString(DataQuery.ACCOUNT_TYPE);
            final String dataSet = cursor.getString(DataQuery.DATA_SET);
            final boolean isPrimary = cursor.getInt(DataQuery.IS_PRIMARY) != 0;
            final boolean isSuperPrimary = cursor.getInt(DataQuery.IS_SUPER_PRIMARY) != 0;

            // Handle photos included as data row
            if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final int displayPhotoColumnIndex = cursor.getColumnIndex(Photo.PHOTO_FILE_ID);
                final boolean hasDisplayPhoto = !cursor.isNull(displayPhotoColumnIndex);
                if (hasDisplayPhoto) {
                    final long displayPhotoId = cursor.getLong(displayPhotoColumnIndex);
                    final Uri displayPhotoUri = ContentUris.withAppendedId(
                            DisplayPhoto.CONTENT_URI, displayPhotoId);
                    // Fetch and JPEG uncompress on the background thread
                    new AsyncTask<Void, Void, Bitmap>() {
                        @Override
                        protected Bitmap doInBackground(Void... params) {
                            try {
                                AssetFileDescriptor fd = getContentResolver()
                                        .openAssetFileDescriptor(displayPhotoUri, "r");
                                return BitmapFactory.decodeStream(fd.createInputStream());
                            } catch (IOException e) {
                                Log.e(TAG, "Error getting display photo. Ignoring, as we already " +
                                        "have the thumbnail", e);
                                return null;
                            }
                        }

                        @Override
                        protected void onPostExecute(Bitmap result) {
                            if (result == null) return;
                            photoView.setImageBitmap(result);
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                }
                final int photoColumnIndex = cursor.getColumnIndex(Photo.PHOTO);
                final byte[] photoBlob = cursor.getBlob(photoColumnIndex);
                if (photoBlob != null) {
                    photoBitmap = BitmapFactory.decodeByteArray(photoBlob, 0, photoBlob.length);
                }
                continue;
            }

            final DataKind kind = accountTypes.getKindOrFallback(accountType, dataSet, mimeType);

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

            // Handle Email rows with presence data as Im entry
            final boolean hasPresence = !cursor.isNull(DataQuery.PRESENCE);
            if (hasPresence && Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final DataKind imKind = accountTypes.getKindOrFallback(accountType, dataSet,
                        Im.CONTENT_ITEM_TYPE);
                if (imKind != null) {
                    final DataAction action = new DataAction(context, Im.CONTENT_ITEM_TYPE, imKind,
                            dataId, cursor);
                    considerAdd(action, cache);
                }
            }
        }

        // Collapse Action Lists (remove e.g. duplicate e-mail addresses from different sources)
        for (List<Action> actionChildren : mActions.values()) {
            Collapser.collapseList(actionChildren);
        }

        if (cursor.moveToLast()) {
            // Read contact name from last data row
            final String name = cursor.getString(DataQuery.DISPLAY_NAME);
            setHeaderNameText(R.id.name, name);
        }

        if (photoView != null) {
            // Place photo when discovered in data, otherwise show generic avatar
            if (photoBitmap != null) {
                photoView.setImageBitmap(photoBitmap);
            } else {
                photoView.setImageResource(ContactPhotoManager.getDefaultAvatarResId(true, false));
            }
        }

        // All the mime-types to add.
        final Set<String> containedTypes = new HashSet<String>(mActions.keySet());
        mSortedActionMimeTypes.clear();
        // First, add LEADING_MIMETYPES, which are most common.
        for (String mimeType : LEADING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                mSortedActionMimeTypes.add(mimeType);
                containedTypes.remove(mimeType);
            }
        }

        // Add all the remaining ones that are not TRAILING
        for (String mimeType : containedTypes.toArray(new String[containedTypes.size()])) {
            if (!TRAILING_MIMETYPES.contains(mimeType)) {
                mSortedActionMimeTypes.add(mimeType);
                containedTypes.remove(mimeType);
            }
        }

        // Then, add TRAILING_MIMETYPES, which are least common.
        for (String mimeType : TRAILING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                containedTypes.remove(mimeType);
                mSortedActionMimeTypes.add(mimeType);
            }
        }

        // Add buttons for each mimetype
        for (String mimeType : mSortedActionMimeTypes) {
            final View actionView = inflateAction(mimeType, cache, mTrack);
            mTrack.addView(actionView);
        }

        final boolean hasData = !mSortedActionMimeTypes.isEmpty();
        mTrackScroller.setVisibility(hasData ? View.VISIBLE : View.GONE);
        mSelectedTabRectangle.setVisibility(hasData ? View.VISIBLE : View.GONE);
        mLineAfterTrack.setVisibility(hasData ? View.VISIBLE : View.GONE);
        mListPager.setVisibility(hasData ? View.VISIBLE : View.GONE);
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
        final CheckableImageView typeView = (CheckableImageView) getLayoutInflater().inflate(
                R.layout.quickcontact_track_button, root, false);

        List<Action> children = mActions.get(mimeType);
        typeView.setTag(mimeType);
        final Action firstInfo = children.get(0);

        // Set icon and listen for clicks
        final CharSequence descrip = resolveCache.getDescription(firstInfo);
        final Drawable icon = resolveCache.getIcon(firstInfo);
        typeView.setChecked(false);
        typeView.setContentDescription(descrip);
        typeView.setImageDrawable(icon);
        typeView.setOnClickListener(mTypeViewClickListener);

        return typeView;
    }

    private CheckableImageView getActionViewAt(int position) {
        return (CheckableImageView) mTrack.getChildAt(position);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        final QuickContactListFragment listFragment = (QuickContactListFragment) fragment;
        listFragment.setListener(mListFragmentListener);
    }

    /** A type (e.g. Call/Addresses was clicked) */
    private final OnClickListener mTypeViewClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            final CheckableImageView actionView = (CheckableImageView)view;
            final String mimeType = (String) actionView.getTag();
            int index = mSortedActionMimeTypes.indexOf(mimeType);
            mListPager.setCurrentItem(index, true);
        }
    };

    private class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int position) {
            QuickContactListFragment fragment = new QuickContactListFragment();
            final String mimeType = mSortedActionMimeTypes.get(position);
            final List<Action> actions = mActions.get(mimeType);
            fragment.setActions(actions);
            return fragment;
        }

        @Override
        public int getCount() {
            return mSortedActionMimeTypes.size();
        }
    }

    private class PageChangeListener extends SimpleOnPageChangeListener {
        @Override
        public void onPageSelected(int position) {
            final CheckableImageView actionView = getActionViewAt(position);
            mTrackScroller.requestChildRectangleOnScreen(actionView,
                    new Rect(0, 0, actionView.getWidth(), actionView.getHeight()), false);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            final RelativeLayout.LayoutParams layoutParams =
                    (RelativeLayout.LayoutParams) mSelectedTabRectangle.getLayoutParams();
            final int width = mSelectedTabRectangle.getWidth();
            layoutParams.leftMargin = (int) ((position + positionOffset) * width);
            mSelectedTabRectangle.setLayoutParams(layoutParams);
        }
    }

    private final QuickContactListFragment.Listener mListFragmentListener =
            new QuickContactListFragment.Listener() {
        @Override
        public void onOutsideClick() {
            // If there is no background, we want to dismiss, because to the user it seems
            // like he had touched outside. If the ViewPager is solid however, those taps
            // must be ignored
            final boolean isTransparent = mListPager.getBackground() == null;
            if (isTransparent) handleOutsideTouch();
        }

        @Override
        public void onItemClicked(final Action action, final boolean alternate) {
            final Runnable startAppRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(alternate ? action.getAlternateIntent() : action.getIntent());
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(QuickContactActivity.this, R.string.quickcontact_missing_app,
                                Toast.LENGTH_SHORT).show();
                    }

                    hide(false);
                }
            };
            // Defer the action to make the window properly repaint
            new Handler().post(startAppRunnable);
        }
    };

    private interface DataQuery {
        final String[] PROJECTION = new String[] {
                Data._ID,

                RawContacts.ACCOUNT_TYPE,
                RawContacts.DATA_SET,
                Contacts.STARRED,
                Contacts.DISPLAY_NAME,

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
        final int DATA_SET = 2;
        final int STARRED = 3;
        final int DISPLAY_NAME = 4;

        final int STATUS = 5;
        final int STATUS_RES_PACKAGE = 6;
        final int STATUS_ICON = 7;
        final int STATUS_LABEL = 8;
        final int STATUS_TIMESTAMP = 9;
        final int PRESENCE = 10;
        final int CHAT_CAPABILITY = 11;

        final int RES_PACKAGE = 12;
        final int MIMETYPE = 13;
        final int IS_PRIMARY = 14;
        final int IS_SUPER_PRIMARY = 15;
    }
}
