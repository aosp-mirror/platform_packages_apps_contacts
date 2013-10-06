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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
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
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.common.Collapser;
import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.model.Contact;
import com.android.contacts.model.ContactLoader;
import com.android.contacts.model.RawContact;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.model.dataitem.EmailDataItem;
import com.android.contacts.model.dataitem.ImDataItem;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.util.DataStatus;
import com.android.contacts.util.ImageViewDrawableSetter;
import com.android.contacts.util.SchedulingUtils;
import com.android.contacts.common.util.StopWatch;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

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
    private static final int POST_DRAW_WAIT_DURATION = 60;
    private static final boolean ENABLE_STOPWATCH = false;


    @SuppressWarnings("deprecation")
    private static final String LEGACY_AUTHORITY = android.provider.Contacts.AUTHORITY;

    private Uri mLookupUri;
    private String[] mExcludeMimes;
    private List<String> mSortedActionMimeTypes = Lists.newArrayList();

    private FloatingChildLayout mFloatingLayout;

    private View mPhotoContainer;
    private ViewGroup mTrack;
    private HorizontalScrollView mTrackScroller;
    private View mSelectedTabRectangle;
    private View mLineAfterTrack;

    private ImageView mPhotoView;
    private ImageView mOpenDetailsOrAddContactImage;
    private ImageView mStarImage;
    private ViewPager mListPager;
    private ViewPagerAdapter mPagerAdapter;

    private Contact mContactData;
    private ContactLoader mContactLoader;

    private final ImageViewDrawableSetter mPhotoSetter = new ImageViewDrawableSetter();

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

    /** Id for the background loader */
    private static final int LOADER_ID = 0;

    private StopWatch mStopWatch = ENABLE_STOPWATCH
            ? StopWatch.start("QuickContact") : StopWatch.getNullStopWatch();

    final OnClickListener mOpenDetailsClickHandler = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(Intent.ACTION_VIEW, mLookupUri);
            mContactLoader.cacheResult();
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            startActivity(intent);
            close(false);
        }
    };

    final OnClickListener mAddToContactsClickHandler = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mContactData == null) {
                Log.e(TAG, "Empty contact data when trying to add to contact");
                return;
            }
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);

            // Only pre-fill the name field if the provided display name is an organization
            // name or better (e.g. structured name, nickname)
            if (mContactData.getDisplayNameSource() >= DisplayNameSources.ORGANIZATION) {
                intent.putExtra(Insert.NAME, mContactData.getDisplayName());
            }
            intent.putExtra(Insert.DATA, mContactData.getContentValues());
            startActivity(intent);
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        mStopWatch.lap("c"); // create start
        super.onCreate(icicle);

        mStopWatch.lap("sc"); // super.onCreate

        if (TRACE_LAUNCH) android.os.Debug.startMethodTracing(TRACE_TAG);

        // Parse intent
        final Intent intent = getIntent();

        Uri lookupUri = intent.getData();

        // Check to see whether it comes from the old version.
        if (lookupUri != null && LEGACY_AUTHORITY.equals(lookupUri.getAuthority())) {
            final long rawContactId = ContentUris.parseId(lookupUri);
            lookupUri = RawContacts.getContactLookupUri(getContentResolver(),
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
        }

        mLookupUri = Preconditions.checkNotNull(lookupUri, "missing lookupUri");

        mExcludeMimes = intent.getStringArrayExtra(QuickContact.EXTRA_EXCLUDE_MIMES);

        mStopWatch.lap("i"); // intent parsed

        mContactLoader = (ContactLoader) getLoaderManager().initLoader(
                LOADER_ID, null, mLoaderCallbacks);

        mStopWatch.lap("ld"); // loader started

        // Show QuickContact in front of soft input
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        setContentView(R.layout.quickcontact_activity);

        mStopWatch.lap("l"); // layout inflated

        mFloatingLayout = (FloatingChildLayout) findViewById(R.id.floating_layout);
        mTrack = (ViewGroup) findViewById(R.id.track);
        mTrackScroller = (HorizontalScrollView) findViewById(R.id.track_scroller);
        mOpenDetailsOrAddContactImage = (ImageView) findViewById(R.id.contact_details_image);
        mStarImage = (ImageView) findViewById(R.id.quickcontact_star_button);
        mListPager = (ViewPager) findViewById(R.id.item_list_pager);
        mSelectedTabRectangle = findViewById(R.id.selected_tab_rectangle);
        mLineAfterTrack = findViewById(R.id.line_after_track);

        mFloatingLayout.setOnOutsideTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleOutsideTouch();
                return true;
            }
        });

        mOpenDetailsOrAddContactImage.setOnClickListener(mOpenDetailsClickHandler);

        mPagerAdapter = new ViewPagerAdapter(getFragmentManager());
        mListPager.setAdapter(mPagerAdapter);
        mListPager.setOnPageChangeListener(new PageChangeListener());

        final Rect sourceBounds = intent.getSourceBounds();
        if (sourceBounds != null) {
            mFloatingLayout.setChildTargetScreen(sourceBounds);
        }

        // find and prepare correct header view
        mPhotoContainer = findViewById(R.id.photo_container);

        setHeaderNameText(R.id.name, R.string.missing_name);

        mPhotoView = (ImageView) mPhotoContainer.findViewById(R.id.photo);
        mPhotoView.setOnClickListener(mOpenDetailsClickHandler);

        mStopWatch.lap("v"); // view initialized

        SchedulingUtils.doAfterLayout(mFloatingLayout, new Runnable() {
            @Override
            public void run() {
                mFloatingLayout.fadeInBackground();
            }
        });

        mStopWatch.lap("cf"); // onCreate finished
    }

    private void handleOutsideTouch() {
        if (mFloatingLayout.isContentFullyVisible()) {
            close(true);
        }
    }

    private void close(boolean withAnimation) {
        // cancel any pending queries
        getLoaderManager().destroyLoader(LOADER_ID);

        if (withAnimation) {
            mFloatingLayout.fadeOutBackground();
            final boolean animated = mFloatingLayout.hideContent(new Runnable() {
                @Override
                public void run() {
                    // Wait until the final animation frame has been drawn, otherwise
                    // there is jank as the framework transitions to the next Activity.
                    SchedulingUtils.doAfterDraw(mFloatingLayout, new Runnable() {
                        @Override
                        public void run() {
                            // Unfortunately, we need to also use postDelayed() to wait a moment
                            // for the frame to be drawn, else the framework's activity-transition
                            // animation will kick in before the final frame is available to it.
                            // This seems unavoidable.  The problem isn't merely that there is no
                            // post-draw listener API; if that were so, it would be sufficient to
                            // call post() instead of postDelayed().
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    finish();
                                }
                            }, POST_DRAW_WAIT_DURATION);
                        }
                    });
                }
            });
            if (!animated) {
                // If we were in the wrong state, simply quit (this can happen for example
                // if the user pushes BACK before anything has loaded)
                finish();
            }
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        close(true);
    }

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
     * Handle the result from the ContactLoader
     */
    private void bindData(Contact data) {
        mContactData = data;
        final ResolveCache cache = ResolveCache.getInstance(this);
        final Context context = this;

        mOpenDetailsOrAddContactImage.setVisibility(isMimeExcluded(Contacts.CONTENT_ITEM_TYPE) ?
                View.GONE : View.VISIBLE);
        final boolean isStarred = data.getStarred();
        if (isStarred) {
            mStarImage.setImageResource(R.drawable.ic_favorite_on_lt);
            mStarImage.setContentDescription(
                getResources().getString(R.string.menu_removeStar));
        } else {
            mStarImage.setImageResource(R.drawable.ic_favorite_off_lt);
            mStarImage.setContentDescription(
                getResources().getString(R.string.menu_addStar));
        }
        final Uri lookupUri = data.getLookupUri();

        // If this is a json encoded URI, there is no local contact to star
        if (UriUtils.isEncodedContactUri(lookupUri)) {
            mStarImage.setVisibility(View.GONE);

            // If directory export support is not allowed, then don't allow the user to add
            // to contacts
            if (mContactData.getDirectoryExportSupport() == Directory.EXPORT_SUPPORT_NONE) {
                configureHeaderClickActions(false);
            } else {
                configureHeaderClickActions(true);
            }
        } else {
            configureHeaderClickActions(false);
            mStarImage.setVisibility(View.VISIBLE);
            mStarImage.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Toggle "starred" state
                    // Make sure there is a contact
                    if (lookupUri != null) {
                        // Changes the state of the image already before sending updates to the
                        // database
                        if (isStarred) {
                            mStarImage.setImageResource(R.drawable.ic_favorite_off_lt);
                        } else {
                            mStarImage.setImageResource(R.drawable.ic_favorite_on_lt);
                        }

                        // Now perform the real save
                        final Intent intent = ContactSaveService.createSetStarredIntent(context,
                                lookupUri, !isStarred);
                        context.startService(intent);
                    }
                }
            });
        }

        mDefaultsMap.clear();

        mStopWatch.lap("sph"); // Start photo setting

        mPhotoSetter.setupContactPhoto(data, mPhotoView);

        mStopWatch.lap("ph"); // Photo set

        for (RawContact rawContact : data.getRawContacts()) {
            for (DataItem dataItem : rawContact.getDataItems()) {
                final String mimeType = dataItem.getMimeType();
                final AccountType accountType = rawContact.getAccountType(this);
                final DataKind dataKind = AccountTypeManager.getInstance(this)
                        .getKindOrFallback(accountType, mimeType);

                // Skip this data item if MIME-type excluded
                if (isMimeExcluded(mimeType)) continue;

                final long dataId = dataItem.getId();
                final boolean isPrimary = dataItem.isPrimary();
                final boolean isSuperPrimary = dataItem.isSuperPrimary();

                if (dataKind != null) {
                    // Build an action for this data entry, find a mapping to a UI
                    // element, build its summary from the cursor, and collect it
                    // along with all others of this MIME-type.
                    final Action action = new DataAction(context, dataItem, dataKind);
                    final boolean wasAdded = considerAdd(action, cache, isSuperPrimary);
                    if (wasAdded) {
                        // Remember the default
                        if (isSuperPrimary || (isPrimary && (mDefaultsMap.get(mimeType) == null))) {
                            mDefaultsMap.put(mimeType, action);
                        }
                    }
                }

                // Handle Email rows with presence data as Im entry
                final DataStatus status = data.getStatuses().get(dataId);
                if (status != null && dataItem instanceof EmailDataItem) {
                    final EmailDataItem email = (EmailDataItem) dataItem;
                    final ImDataItem im = ImDataItem.createFromEmail(email);
                    if (dataKind != null) {
                        final DataAction action = new DataAction(context, im, dataKind);
                        action.setPresence(status.getPresence());
                        considerAdd(action, cache, isSuperPrimary);
                    }
                }
            }
        }

        mStopWatch.lap("e"); // Entities inflated

        // Collapse Action Lists (remove e.g. duplicate e-mail addresses from different sources)
        for (List<Action> actionChildren : mActions.values()) {
            Collapser.collapseList(actionChildren);
        }

        mStopWatch.lap("c"); // List collapsed

        setHeaderNameText(R.id.name, data.getDisplayName());

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
        mPagerAdapter.notifyDataSetChanged();

        mStopWatch.lap("mt"); // Mime types initialized

        // Add buttons for each mimetype
        mTrack.removeAllViews();
        for (String mimeType : mSortedActionMimeTypes) {
            final View actionView = inflateAction(mimeType, cache, mTrack, data.getDisplayName());
            mTrack.addView(actionView);
        }

        mStopWatch.lap("mt"); // Buttons added

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
     * @param action the action to handle
     * @param resolveCache cache of applications that can handle actions
     * @param front indicates whether to add the action to the front of the list
     * @return true if action has been added
     */
    private boolean considerAdd(Action action, ResolveCache resolveCache, boolean front) {
        if (resolveCache.hasResolve(action)) {
            mActions.put(action.getMimeType(), action, front);
            return true;
        }
        return false;
    }

    /**
     * Bind the correct image resource and click handlers to the header views
     *
     * @param canAdd Whether or not the user can directly add information in this quick contact
     * to their local contacts
     */
    private void configureHeaderClickActions(boolean canAdd) {
        if (canAdd) {
            mOpenDetailsOrAddContactImage.setImageResource(R.drawable.ic_add_contact_holo_dark);
            mOpenDetailsOrAddContactImage.setOnClickListener(mAddToContactsClickHandler);
            mPhotoView.setOnClickListener(mAddToContactsClickHandler);
        } else {
            mOpenDetailsOrAddContactImage.setImageResource(R.drawable.ic_contacts_holo_dark);
            mOpenDetailsOrAddContactImage.setOnClickListener(mOpenDetailsClickHandler);
            mPhotoView.setOnClickListener(mOpenDetailsClickHandler);
        }
    }

    /**
     * Inflate the in-track view for the action of the given MIME-type, collapsing duplicate values.
     * Will use the icon provided by the {@link DataKind}.
     */
    private View inflateAction(String mimeType, ResolveCache resolveCache,
                               ViewGroup root, String name) {
        final CheckableImageView typeView = (CheckableImageView) getLayoutInflater().inflate(
                R.layout.quickcontact_track_button, root, false);

        List<Action> children = mActions.get(mimeType);
        typeView.setTag(mimeType);
        final Action firstInfo = children.get(0);

        // Set icon and listen for clicks
        final CharSequence descrip = resolveCache.getDescription(firstInfo, name);
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

    private LoaderCallbacks<Contact> mLoaderCallbacks =
            new LoaderCallbacks<Contact>() {
        @Override
        public void onLoaderReset(Loader<Contact> loader) {
        }

        @Override
        public void onLoadFinished(Loader<Contact> loader, Contact data) {
            mStopWatch.lap("lf"); // onLoadFinished
            if (isFinishing()) {
                close(false);
                return;
            }
            if (data.isError()) {
                // This shouldn't ever happen, so throw an exception. The {@link ContactLoader}
                // should log the actual exception.
                throw new IllegalStateException("Failed to load contact", data.getException());
            }
            if (data.isNotFound()) {
                Log.i(TAG, "No contact found: " + ((ContactLoader)loader).getLookupUri());
                Toast.makeText(QuickContactActivity.this, R.string.invalidContactMessage,
                        Toast.LENGTH_LONG).show();
                close(false);
                return;
            }

            bindData(data);

            mStopWatch.lap("bd"); // bindData finished

            if (TRACE_LAUNCH) android.os.Debug.stopMethodTracing();
            if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
                Log.d(Constants.PERFORMANCE_TAG, "QuickContact shown");
            }

            // Data bound and ready, pull curtain to show. Put this on the Handler to ensure
            // that the layout passes are completed
            SchedulingUtils.doAfterLayout(mFloatingLayout, new Runnable() {
                @Override
                public void run() {
                    mFloatingLayout.showContent(new Runnable() {
                        @Override
                        public void run() {
                            mContactLoader.upgradeToFullContact();
                        }
                    });
                }
            });
            mStopWatch.stopAndLog(TAG, 0);
            mStopWatch = StopWatch.getNullStopWatch(); // We're done with it.
        }

        @Override
        public Loader<Contact> onCreateLoader(int id, Bundle args) {
            if (mLookupUri == null) {
                Log.wtf(TAG, "Lookup uri wasn't initialized. Loader was started too early");
            }
            return new ContactLoader(getApplicationContext(), mLookupUri,
                    false /*loadGroupMetaData*/, false /*loadInvitableAccountTypes*/,
                    false /*postViewNotification*/, true /*computeFormattedPhoneNumber*/);
        }
    };

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
            final String mimeType = mSortedActionMimeTypes.get(position);
            QuickContactListFragment fragment = new QuickContactListFragment(mimeType);
            final List<Action> actions = mActions.get(mimeType);
            fragment.setActions(actions);
            return fragment;
        }

        @Override
        public int getCount() {
            return mSortedActionMimeTypes.size();
        }

        @Override
        public int getItemPosition(Object object) {
            final QuickContactListFragment fragment = (QuickContactListFragment) object;
            final String mimeType = fragment.getMimeType();
            for (int i = 0; i < mSortedActionMimeTypes.size(); i++) {
                if (mimeType.equals(mSortedActionMimeTypes.get(i))) {
                    return i;
                }
            }
            return PagerAdapter.POSITION_NONE;
        }
    }

    private class PageChangeListener extends SimpleOnPageChangeListener {
        private int mScrollingState = ViewPager.SCROLL_STATE_IDLE;

        @Override
        public void onPageSelected(int position) {
            final CheckableImageView actionView = getActionViewAt(position);
            mTrackScroller.requestChildRectangleOnScreen(actionView,
                    new Rect(0, 0, actionView.getWidth(), actionView.getHeight()), false);
            // Don't render rectangle if we are currently scrolling to prevent it from flickering
            if (mScrollingState == ViewPager.SCROLL_STATE_IDLE) {
                renderSelectedRectangle(position, 0);
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            super.onPageScrollStateChanged(state);
            mScrollingState = state;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            renderSelectedRectangle(position, positionOffset);
        }

        private void renderSelectedRectangle(int position, float positionOffset) {
            final RelativeLayout.LayoutParams layoutParams =
                    (RelativeLayout.LayoutParams) mSelectedTabRectangle.getLayoutParams();
            final int width = layoutParams.width;
            layoutParams.setMarginStart((int) ((position + positionOffset) * width));
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

                    close(false);
                }
            };
            // Defer the action to make the window properly repaint
            new Handler().post(startAppRunnable);
        }
    };
}
