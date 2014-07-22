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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ParseException;
import android.net.Uri;
import android.net.WebAddress;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Trace;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Identity;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.DataUsageFeedback;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.NfcHandler;
import com.android.contacts.R;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.Collapser;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.contacts.common.list.ShortcutIntentBuilder;
import com.android.contacts.common.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.model.dataitem.EmailDataItem;
import com.android.contacts.common.model.dataitem.EventDataItem;
import com.android.contacts.common.model.dataitem.ImDataItem;
import com.android.contacts.common.model.dataitem.NicknameDataItem;
import com.android.contacts.common.model.dataitem.NoteDataItem;
import com.android.contacts.common.model.dataitem.OrganizationDataItem;
import com.android.contacts.common.model.dataitem.PhoneDataItem;
import com.android.contacts.common.model.dataitem.RelationDataItem;
import com.android.contacts.common.model.dataitem.SipAddressDataItem;
import com.android.contacts.common.model.dataitem.StructuredNameDataItem;
import com.android.contacts.common.model.dataitem.StructuredPostalDataItem;
import com.android.contacts.common.model.dataitem.WebsiteDataItem;
import com.android.contacts.common.util.DateUtils;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.detail.ContactDisplayUtils;
import com.android.contacts.interactions.CalendarInteractionsLoader;
import com.android.contacts.interactions.CallLogInteractionsLoader;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.interactions.ContactInteraction;
import com.android.contacts.interactions.SmsInteractionsLoader;
import com.android.contacts.quickcontact.ExpandingEntryCardView.Entry;
import com.android.contacts.quickcontact.ExpandingEntryCardView.ExpandingEntryCardViewListener;
import com.android.contacts.util.ImageViewDrawableSetter;
import com.android.contacts.util.PhoneCapabilityTester;
import com.android.contacts.util.SchedulingUtils;
import com.android.contacts.util.StructuredPostalUtils;
import com.android.contacts.widget.MultiShrinkScroller;
import com.android.contacts.widget.MultiShrinkScroller.MultiShrinkScrollerListener;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mostly translucent {@link Activity} that shows QuickContact dialog. It loads
 * data asynchronously, and then shows a popup with details centered around
 * {@link Intent#getSourceBounds()}.
 */
public class QuickContactActivity extends ContactsActivity {

    /**
     * QuickContacts immediately takes up the full screen. All possible information is shown.
     * This value for {@link android.provider.ContactsContract.QuickContact#EXTRA_MODE}
     * should only be used by the Contacts app.
     */
    public static final int MODE_FULLY_EXPANDED = 4;

    private static final String TAG = "QuickContact";

    private static final String KEY_THEME_COLOR = "theme_color";

    private static final int ANIMATION_STATUS_BAR_COLOR_CHANGE_DURATION = 150;
    private static final int REQUEST_CODE_CONTACT_EDITOR_ACTIVITY = 1;
    private static final int SCRIM_COLOR = Color.argb(0xB2, 0, 0, 0);
    private static final String MIMETYPE_SMS = "vnd.android-dir/mms-sms";

    /** This is the Intent action to install a shortcut in the launcher. */
    private static final String ACTION_INSTALL_SHORTCUT =
            "com.android.launcher.action.INSTALL_SHORTCUT";

    @SuppressWarnings("deprecation")
    private static final String LEGACY_AUTHORITY = android.provider.Contacts.AUTHORITY;

    private static final String MIMETYPE_GPLUS_PROFILE =
            "vnd.android.cursor.item/vnd.googleplus.profile";
    private static final String INTENT_DATA_GPLUS_PROFILE_ADD_TO_CIRCLE = "Add to circle";
    private static final String MIMETYPE_HANGOUTS =
            "vnd.android.cursor.item/vnd.googleplus.profile.comm";
    private static final String INTENT_DATA_HANGOUTS_VIDEO = "Start video call";

    private Uri mLookupUri;
    private String[] mExcludeMimes;
    private int mExtraMode;
    private int mStatusBarColor;
    private boolean mHasAlreadyBeenOpened;

    private ImageView mPhotoView;
    private View mTransparentView;
    private ExpandingEntryCardView mContactCard;
    private ExpandingEntryCardView mNoContactDetailsCard;
    private ExpandingEntryCardView mRecentCard;
    private ExpandingEntryCardView mAboutCard;
    /**
     * This list contains all the {@link DataItem}s. Each nested list contains all data items of a
     * specific mimetype in sorted order, using mWithinMimeTypeDataItemComparator. The mimetype
     * lists are sorted using mAmongstMimeTypeDataItemComparator.
     */
    private List<List<DataItem>> mDataItemsList;
    /**
     * A map between a mimetype string and the corresponding list of data items. The data items
     * are in sorted order using mWithinMimeTypeDataItemComparator.
     */
    private Map<String, List<DataItem>> mDataItemsMap;
    private MultiShrinkScroller mScroller;
    private SelectAccountDialogFragmentListener mSelectAccountFragmentListener;
    private AsyncTask<Void, Void, Pair<List<List<DataItem>>, Map<String, List<DataItem>>>>
            mEntriesAndActionsTask;
    private ColorDrawable mWindowScrim;
    private MaterialColorMapUtils mMaterialColorMapUtils;
    private boolean mIsWaitingForOtherPieceOfExitAnimation;
    private boolean mIsExitAnimationInProgress;
    private boolean mHasComputedThemeColor;

    private Contact mContactData;
    private ContactLoader mContactLoader;
    private PorterDuffColorFilter mColorFilter;

    private final ImageViewDrawableSetter mPhotoSetter = new ImageViewDrawableSetter();

    /**
     * {@link #LEADING_MIMETYPES} is used to sort MIME-types.
     *
     * <p>The MIME-types in {@link #LEADING_MIMETYPES} appear in the front of the dialog,
     * in the order specified here.</p>
     */
    private static final List<String> LEADING_MIMETYPES = Lists.newArrayList(
            Phone.CONTENT_ITEM_TYPE, SipAddress.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE,
            StructuredPostal.CONTENT_ITEM_TYPE);

    private static final List<String> ABOUT_CARD_MIMETYPES = Lists.newArrayList(
            Event.CONTENT_ITEM_TYPE, GroupMembership.CONTENT_ITEM_TYPE, Identity.CONTENT_ITEM_TYPE,
            Im.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE,
            Organization.CONTENT_ITEM_TYPE, Relation.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE);

    /** Id for the background contact loader */
    private static final int LOADER_CONTACT_ID = 0;

    private static final String KEY_LOADER_EXTRA_PHONES =
            QuickContactActivity.class.getCanonicalName() + ".KEY_LOADER_EXTRA_PHONES";

    /** Id for the background Sms Loader */
    private static final int LOADER_SMS_ID = 1;
    private static final int MAX_SMS_RETRIEVE = 3;

    /** Id for the back Calendar Loader */
    private static final int LOADER_CALENDAR_ID = 2;
    private static final String KEY_LOADER_EXTRA_EMAILS =
            QuickContactActivity.class.getCanonicalName() + ".KEY_LOADER_EXTRA_EMAILS";
    private static final int MAX_PAST_CALENDAR_RETRIEVE = 3;
    private static final int MAX_FUTURE_CALENDAR_RETRIEVE = 3;
    private static final long PAST_MILLISECOND_TO_SEARCH_LOCAL_CALENDAR =
            180L * 24L * 60L * 60L * 1000L /* 180 days */;
    private static final long FUTURE_MILLISECOND_TO_SEARCH_LOCAL_CALENDAR =
            36L * 60L * 60L * 1000L /* 36 hours */;

    /** Id for the background Call Log Loader */
    private static final int LOADER_CALL_LOG_ID = 3;
    private static final int MAX_CALL_LOG_RETRIEVE = 3;
    private static final int MIN_NUM_CONTACT_ENTRIES_SHOWN = 3;
    private static final int MIN_NUM_COLLAPSED_RECENT_ENTRIES_SHOWN = 3;
    private static final int CARD_ENTRY_ID_EDIT_CONTACT = -2;


    private static final int[] mRecentLoaderIds = new int[]{
        LOADER_SMS_ID,
        LOADER_CALENDAR_ID,
        LOADER_CALL_LOG_ID};
    private Map<Integer, List<ContactInteraction>> mRecentLoaderResults = new HashMap<>();

    private static final String FRAGMENT_TAG_SELECT_ACCOUNT = "select_account_fragment";

    final OnClickListener mEntryClickHandler = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // Data Id is stored as the entry view id
            final int dataId = v.getId();
            if (dataId == CARD_ENTRY_ID_EDIT_CONTACT) {
                editContact();
                return;
            }
            final Object intentObject = v.getTag();
            if (intentObject == null || !(intentObject instanceof Intent)) {
                Log.w(TAG, "Intent tag was not used correctly");
                return;
            }
            final Intent intent = (Intent) intentObject;

            // Default to USAGE_TYPE_CALL. Usage is summed among all types for sorting each data id
            // so the exact usage type is not necessary in all cases
            String usageType = DataUsageFeedback.USAGE_TYPE_CALL;

            final String scheme = intent.getData().getScheme();
            if ((scheme != null && scheme.equals(CallUtil.SCHEME_SMSTO)) ||
                    (intent.getType() != null && intent.getType().equals(MIMETYPE_SMS))) {
                usageType = DataUsageFeedback.USAGE_TYPE_SHORT_TEXT;
            }

            // Data IDs start at 1 so anything less is invalid
            if (dataId > 0) {
                final Uri uri = DataUsageFeedback.FEEDBACK_URI.buildUpon()
                        .appendPath(String.valueOf(dataId))
                        .appendQueryParameter(DataUsageFeedback.USAGE_TYPE, usageType)
                        .build();
                final boolean successful = getContentResolver().update(
                        uri, new ContentValues(), null, null) > 0;
                if (!successful) {
                    Log.w(TAG, "DataUsageFeedback increment failed");
                }
            } else {
                Log.w(TAG, "Invalid Data ID");
            }

            startActivity(intent);
        }
    };

    final ExpandingEntryCardViewListener mExpandingEntryCardViewListener
            = new ExpandingEntryCardViewListener() {
        @Override
        public void onCollapse(int heightDelta) {
            mScroller.prepareForShrinkingScrollChild(heightDelta);
        }
    };

    /**
     * Headless fragment used to handle account selection callbacks invoked from
     * {@link DirectoryContactUtil}.
     */
    public static class SelectAccountDialogFragmentListener extends Fragment
            implements SelectAccountDialogFragment.Listener {

        private QuickContactActivity mQuickContactActivity;

        public SelectAccountDialogFragmentListener() {}

        @Override
        public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
            DirectoryContactUtil.createCopy(mQuickContactActivity.mContactData.getContentValues(),
                    account, mQuickContactActivity);
        }

        @Override
        public void onAccountSelectorCancelled() {}

        /**
         * Set the parent activity. Since rotation can cause this fragment to be used across
         * more than one activity instance, we need to explicitly set this value instead
         * of making this class non-static.
         */
        public void setQuickContactActivity(QuickContactActivity quickContactActivity) {
            mQuickContactActivity = quickContactActivity;
        }
    }

    final MultiShrinkScrollerListener mMultiShrinkScrollerListener
            = new MultiShrinkScrollerListener() {
        @Override
        public void onScrolledOffBottom() {
            if (!mIsWaitingForOtherPieceOfExitAnimation) {
                finish();
                return;
            }
            mIsWaitingForOtherPieceOfExitAnimation = false;
        }

        @Override
        public void onEnterFullscreen() {
            updateStatusBarColor();
        }

        @Override
        public void onExitFullscreen() {
            updateStatusBarColor();
        }

        @Override
        public void onStartScrollOffBottom() {
            // Remove the window shim now that we are starting an Activity exit animation.
            final int duration = getResources().getInteger(android.R.integer.config_shortAnimTime);
            final ObjectAnimator animator = ObjectAnimator.ofInt(mWindowScrim, "alpha", 0xFF, 0);
            animator.addListener(mExitWindowShimAnimationListener);
            animator.setDuration(duration).start();
            mIsWaitingForOtherPieceOfExitAnimation = true;
            mIsExitAnimationInProgress = true;
        }
    };

    final AnimatorListener mExitWindowShimAnimationListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mIsWaitingForOtherPieceOfExitAnimation) {
                finish();
                return;
            }
            mIsWaitingForOtherPieceOfExitAnimation = false;
        }
    };


    /**
     * Data items are compared to the same mimetype based off of three qualities:
     * 1. Super primary
     * 2. Primary
     * 3. Times used
     */
    private final Comparator<DataItem> mWithinMimeTypeDataItemComparator =
            new Comparator<DataItem>() {
        @Override
        public int compare(DataItem lhs, DataItem rhs) {
            if (!lhs.getMimeType().equals(rhs.getMimeType())) {
                Log.wtf(TAG, "Comparing DataItems with different mimetypes lhs.getMimeType(): " +
                        lhs.getMimeType() + " rhs.getMimeType(): " + rhs.getMimeType());
                return 0;
            }

            if (lhs.isSuperPrimary()) {
                return -1;
            } else if (rhs.isSuperPrimary()) {
                return 1;
            } else if (lhs.isPrimary() && !rhs.isPrimary()) {
                return -1;
            } else if (!lhs.isPrimary() && rhs.isPrimary()) {
                return 1;
            } else {
                final int lhsTimesUsed =
                        lhs.getTimesUsed() == null ? 0 : lhs.getTimesUsed();
                final int rhsTimesUsed =
                        rhs.getTimesUsed() == null ? 0 : rhs.getTimesUsed();

                return rhsTimesUsed - lhsTimesUsed;
            }
        }
    };

    /**
     * Sorts among different mimetypes based off:
     * 1. Times used
     * 2. Last time used
     * 3. Statically defined
     */
    private final Comparator<List<DataItem>> mAmongstMimeTypeDataItemComparator =
            new Comparator<List<DataItem>> () {
        @Override
        public int compare(List<DataItem> lhsList, List<DataItem> rhsList) {
            DataItem lhs = lhsList.get(0);
            DataItem rhs = rhsList.get(0);
            final int lhsTimesUsed = lhs.getTimesUsed() == null ? 0 : lhs.getTimesUsed();
            final int rhsTimesUsed = rhs.getTimesUsed() == null ? 0 : rhs.getTimesUsed();
            final int timesUsedDifference = rhsTimesUsed - lhsTimesUsed;
            if (timesUsedDifference != 0) {
                return timesUsedDifference;
            }

            final long lhsLastTimeUsed =
                    lhs.getLastTimeUsed() == null ? 0 : lhs.getLastTimeUsed();
            final long rhsLastTimeUsed =
                    rhs.getLastTimeUsed() == null ? 0 : rhs.getLastTimeUsed();
            final long lastTimeUsedDifference = rhsLastTimeUsed - lhsLastTimeUsed;
            if (lastTimeUsedDifference > 0) {
                return 1;
            } else if (lastTimeUsedDifference < 0) {
                return -1;
            }

            // Times used and last time used are the same. Resort to statically defined.
            final String lhsMimeType = lhs.getMimeType();
            final String rhsMimeType = rhs.getMimeType();
            for (String mimeType : LEADING_MIMETYPES) {
                if (lhsMimeType.equals(mimeType)) {
                    return -1;
                } else if (rhsMimeType.equals(mimeType)) {
                    return 1;
                }
            }
            return 0;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Trace.beginSection("onCreate()");
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.TRANSPARENT);

        processIntent(getIntent());

        // Show QuickContact in front of soft input
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        setContentView(R.layout.quickcontact_activity);

        mMaterialColorMapUtils = new MaterialColorMapUtils(getResources());

        mContactCard = (ExpandingEntryCardView) findViewById(R.id.communication_card);
        mNoContactDetailsCard = (ExpandingEntryCardView) findViewById(R.id.no_contact_data_card);
        mRecentCard = (ExpandingEntryCardView) findViewById(R.id.recent_card);
        mAboutCard = (ExpandingEntryCardView) findViewById(R.id.about_card);
        mScroller = (MultiShrinkScroller) findViewById(R.id.multiscroller);

        mNoContactDetailsCard.setOnClickListener(mEntryClickHandler);
        mContactCard.setOnClickListener(mEntryClickHandler);
        mContactCard.setExpandButtonText(
        getResources().getString(R.string.expanding_entry_card_view_see_all));

        mRecentCard.setOnClickListener(mEntryClickHandler);
        mRecentCard.setTitle(getResources().getString(R.string.recent_card_title));

        mAboutCard.setOnClickListener(mEntryClickHandler);

        mPhotoView = (ImageView) findViewById(R.id.photo);
        mTransparentView = findViewById(R.id.transparent_view);
        if (mScroller != null) {
            mTransparentView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mScroller.scrollOffBottom();
                }
            });
        }

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
        getActionBar().setTitle(null);
        // Put a TextView with a known resource id into the ActionBar. This allows us to easily
        // find the correct TextView location & size later.
        toolbar.addView(getLayoutInflater().inflate(R.layout.quickcontact_title_placeholder, null));

        mHasAlreadyBeenOpened = savedInstanceState != null;

        mWindowScrim = new ColorDrawable(SCRIM_COLOR);
        getWindow().setBackgroundDrawable(mWindowScrim);
        if (!mHasAlreadyBeenOpened) {
            final int duration = getResources().getInteger(android.R.integer.config_shortAnimTime);
            ObjectAnimator.ofInt(mWindowScrim, "alpha", 0, 0xFF).setDuration(duration).start();
        }

        mScroller.initialize(mMultiShrinkScrollerListener, mExtraMode == MODE_FULLY_EXPANDED);
        // mScroller needs to perform asynchronous measurements after initalize(), therefore
        // we can't mark this as GONE.
        mScroller.setVisibility(View.INVISIBLE);

        setHeaderNameText(R.string.missing_name);

        mSelectAccountFragmentListener= (SelectAccountDialogFragmentListener) getFragmentManager()
                .findFragmentByTag(FRAGMENT_TAG_SELECT_ACCOUNT);
        if (mSelectAccountFragmentListener == null) {
            mSelectAccountFragmentListener = new SelectAccountDialogFragmentListener();
            getFragmentManager().beginTransaction().add(0, mSelectAccountFragmentListener,
                    FRAGMENT_TAG_SELECT_ACCOUNT).commit();
            mSelectAccountFragmentListener.setRetainInstance(true);
        }
        mSelectAccountFragmentListener.setQuickContactActivity(this);

        if (savedInstanceState != null) {
            final int color = savedInstanceState.getInt(KEY_THEME_COLOR, 0);
            SchedulingUtils.doOnPreDraw(mScroller, /* drawNextFrame = */ false,
                    new Runnable() {
                        @Override
                        public void run() {
                            // Need to wait for the pre draw before setting the initial scroll
                            // value. Prior to pre draw all scroll values are invalid.
                            if (mHasAlreadyBeenOpened) {
                                mScroller.setVisibility(View.VISIBLE);
                                mScroller.setScroll(mScroller.getScrollNeededToBeFullScreen());
                            }
                            // Need to wait for pre draw for setting the theme color. Setting the
                            // header tint before the MultiShrinkScroller has been measured will
                            // cause incorrect tinting calculations.
                            if (color != 0) {
                                setThemeColor(mMaterialColorMapUtils
                                        .calculatePrimaryAndSecondaryColor(color));
                            }
                        }
                    });
        }

        Trace.endSection();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CONTACT_EDITOR_ACTIVITY &&
                resultCode == ContactDeletionInteraction.RESULT_CODE_DELETED) {
            // The contact that we were showing has been deleted.
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mHasAlreadyBeenOpened = true;
        mHasComputedThemeColor = false;
        processIntent(intent);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (mColorFilter != null) {
            savedInstanceState.putInt(KEY_THEME_COLOR, mColorFilter.getColor());
        }
    }

    private void processIntent(Intent intent) {
        Uri lookupUri = intent.getData();

        // Check to see whether it comes from the old version.
        if (lookupUri != null && LEGACY_AUTHORITY.equals(lookupUri.getAuthority())) {
            final long rawContactId = ContentUris.parseId(lookupUri);
            lookupUri = RawContacts.getContactLookupUri(getContentResolver(),
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
        }
        mExtraMode = getIntent().getIntExtra(QuickContact.EXTRA_MODE,
                QuickContact.MODE_LARGE);
        final Uri oldLookupUri = mLookupUri;

        mLookupUri = Preconditions.checkNotNull(lookupUri, "missing lookupUri");
        mExcludeMimes = intent.getStringArrayExtra(QuickContact.EXTRA_EXCLUDE_MIMES);
        if (oldLookupUri == null) {
            mContactLoader = (ContactLoader) getLoaderManager().initLoader(
                    LOADER_CONTACT_ID, null, mLoaderContactCallbacks);
        } else if (oldLookupUri != mLookupUri) {
            // After copying a directory contact, the contact URI changes. Therefore,
            // we need to restart the loader and reload the new contact.
            for (int interactionLoaderId : mRecentLoaderIds) {
                getLoaderManager().destroyLoader(interactionLoaderId);
            }
            mContactLoader = (ContactLoader) getLoaderManager().restartLoader(
                    LOADER_CONTACT_ID, null, mLoaderContactCallbacks);
        }

        NfcHandler.register(this, mLookupUri);
    }

    private void runEntranceAnimation() {
        if (mHasAlreadyBeenOpened) {
            return;
        }
        mHasAlreadyBeenOpened = true;
        mScroller.scrollUpForEntranceAnimation(mExtraMode != MODE_FULLY_EXPANDED);
    }

    /** Assign this string to the view if it is not empty. */
    private void setHeaderNameText(int resId) {
        if (mScroller != null) {
            mScroller.setTitle(getText(resId) == null ? null : getText(resId).toString());
        }
    }

    /** Assign this string to the view if it is not empty. */
    private void setHeaderNameText(String value) {
        if (!TextUtils.isEmpty(value)) {
            if (mScroller != null) {
                mScroller.setTitle(value);
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
    private void bindContactData(final Contact data) {
        Trace.beginSection("bindContactData");
        mContactData = data;
        invalidateOptionsMenu();

        Trace.endSection();
        Trace.beginSection("Set display photo & name");

        mPhotoSetter.setupContactPhoto(data, mPhotoView);
        extractAndApplyTintFromPhotoViewAsynchronously();
        analyzeWhitenessOfPhotoAsynchronously();
        setHeaderNameText(ContactDisplayUtils.getDisplayName(this, data).toString());

        Trace.endSection();

        mEntriesAndActionsTask = new AsyncTask<Void, Void,
                Pair<List<List<DataItem>>, Map<String, List<DataItem>>>>() {

            @Override
            protected Pair<List<List<DataItem>>, Map<String, List<DataItem>>> doInBackground(
                    Void... params) {
                return generateDataModelFromContact(data);
            }

            @Override
            protected void onPostExecute(Pair<List<List<DataItem>>,
                    Map<String, List<DataItem>>> dataItemsPair) {
                super.onPostExecute(dataItemsPair);
                mDataItemsList = dataItemsPair.first;
                mDataItemsMap = dataItemsPair.second;
                // Check that original AsyncTask parameters are still valid and the activity
                // is still running before binding to UI. A new intent could invalidate
                // the results, for example.
                if (data == mContactData && !isCancelled()) {
                    bindDataToCards();
                    showActivity();
                }
            }
        };
        mEntriesAndActionsTask.execute();
    }

    private void bindDataToCards() {
        startInteractionLoaders();
        populateContactAndAboutCard();
    }

    private void startInteractionLoaders() {
        final List<DataItem> phoneDataItems = mDataItemsMap.get(Phone.CONTENT_ITEM_TYPE);
        String[] phoneNumbers = null;
        if (phoneDataItems != null) {
            phoneNumbers = new String[phoneDataItems.size()];
            for (int i = 0; i < phoneDataItems.size(); ++i) {
                phoneNumbers[i] = ((PhoneDataItem) phoneDataItems.get(i)).getNumber();
            }
        }
        final Bundle phonesExtraBundle = new Bundle();
        phonesExtraBundle.putStringArray(KEY_LOADER_EXTRA_PHONES, phoneNumbers);

        Trace.beginSection("start sms loader");
        getLoaderManager().initLoader(
                LOADER_SMS_ID,
                phonesExtraBundle,
                mLoaderInteractionsCallbacks);
        Trace.endSection();

        Trace.beginSection("start call log loader");
        getLoaderManager().initLoader(
                LOADER_CALL_LOG_ID,
                phonesExtraBundle,
                mLoaderInteractionsCallbacks);
        Trace.endSection();


        Trace.beginSection("start calendar loader");
        final List<DataItem> emailDataItems = mDataItemsMap.get(Email.CONTENT_ITEM_TYPE);
        String[] emailAddresses = null;
        if (emailDataItems != null) {
            emailAddresses = new String[emailDataItems.size()];
            for (int i = 0; i < emailDataItems.size(); ++i) {
                emailAddresses[i] = ((EmailDataItem) emailDataItems.get(i)).getAddress();
            }
        }
        final Bundle emailsExtraBundle = new Bundle();
        emailsExtraBundle.putStringArray(KEY_LOADER_EXTRA_EMAILS, emailAddresses);
        getLoaderManager().initLoader(
                LOADER_CALENDAR_ID,
                emailsExtraBundle,
                mLoaderInteractionsCallbacks);
        Trace.endSection();
    }

    private void showActivity() {
        if (mScroller != null) {
            mScroller.setVisibility(View.VISIBLE);
            SchedulingUtils.doOnPreDraw(mScroller, /* drawNextFrame = */ false,
                    new Runnable() {
                        @Override
                        public void run() {
                            runEntranceAnimation();
                        }
                    });
        }
    }

    private void populateContactAndAboutCard() {
        Trace.beginSection("bind contact card");

        final List<List<Entry>> contactCardEntries = new ArrayList<>();
        final List<List<Entry>> aboutCardEntries = new ArrayList<>();

        for (int i = 0; i < mDataItemsList.size(); ++i) {
            final List<DataItem> dataItemsByMimeType = mDataItemsList.get(i);
            final DataItem topDataItem = dataItemsByMimeType.get(0);
            if (ABOUT_CARD_MIMETYPES.contains(topDataItem.getMimeType())) {
                List<Entry> aboutEntries = dataItemsToEntries(mDataItemsList.get(i));
                if (aboutEntries.size() > 0) {
                    aboutCardEntries.add(aboutEntries);
                }
            } else {
                List<Entry> contactEntries = dataItemsToEntries(mDataItemsList.get(i));
                if (contactEntries.size() > 0) {
                    contactCardEntries.add(contactEntries);
                }
            }
        }

        if (contactCardEntries.size() > 0) {
            mContactCard.initialize(contactCardEntries,
                    /* numInitialVisibleEntries = */ MIN_NUM_CONTACT_ENTRIES_SHOWN,
                    /* isExpanded = */ mContactCard.isExpanded(),
                    /* isAlwaysExpanded = */ false,
                    mExpandingEntryCardViewListener);
            mContactCard.setVisibility(View.VISIBLE);
        } else {
            mContactCard.setVisibility(View.GONE);
        }
        Trace.endSection();

        Trace.beginSection("bind about card");
        // Phonetic name is not a data item, so the entry needs to be created separately
        final String phoneticName = mContactData.getPhoneticName();
        if (!TextUtils.isEmpty(phoneticName)) {
            Entry phoneticEntry = new Entry(/* viewId = */ -1,
                    /* icon = */ null,
                    getResources().getString(R.string.name_phonetic),
                    phoneticName,
                    /* text = */ null,
                    /* intent = */ null,
                    /* alternateIcon = */ null,
                    /* alternateIntent = */ null,
                    /* alternateContentDescription = */ null,
                    /* shouldApplyColor = */ false,
                    /* isEditable = */ false
                    );
            List<Entry> phoneticList = new ArrayList<>();
            phoneticList.add(phoneticEntry);
            aboutCardEntries.add(0, phoneticList);
        }

        mAboutCard.initialize(aboutCardEntries,
                /* numInitialVisibleEntries = */ 1,
                /* isExpanded = */ true,
                /* isAlwaysExpanded = */ true,
                mExpandingEntryCardViewListener);

        if (contactCardEntries.size() == 0 && aboutCardEntries.size() == 0) {
            initializeNoContactDetailCard();
        } else {
            mNoContactDetailsCard.setVisibility(View.GONE);
        }

        // If the Recent card is already initialized (all recent data is loaded), show the About
        // card if it has entries. Otherwise About card visibility will be set in bindRecentData()
        if (isAllRecentDataLoaded() && aboutCardEntries.size() > 0) {
            mAboutCard.setVisibility(View.VISIBLE);
        }
        Trace.endSection();
    }

    /**
     * Create a card that shows "Add email" and "Add phone number" entries in grey.
     */
    private void initializeNoContactDetailCard() {
        final Drawable phoneIcon = getResources().getDrawable(
                R.drawable.ic_phone_24dp).mutate();
        final Entry phonePromptEntry = new Entry(CARD_ENTRY_ID_EDIT_CONTACT,
                phoneIcon, getString(R.string.quickcontact_add_phone_number),
                /* subHeader = */ null, /* text = */ null, getEditContactIntent(),
                /* alternateIcon = */ null, /* alternateIntent = */ null,
                /* alternateContentDescription = */ null, /* shouldApplyColor = */ true,
                /* isEditable = */ false);

        final Drawable emailIcon = getResources().getDrawable(
                R.drawable.ic_email_24dp).mutate();
        final Entry emailPromptEntry = new Entry(CARD_ENTRY_ID_EDIT_CONTACT,
                emailIcon, getString(R.string.quickcontact_add_email), /* subHeader = */ null,
                /* text = */ null, getEditContactIntent(), /* alternateIcon = */ null,
                /* alternateIntent = */ null, /* alternateContentDescription = */ null,
                /* shouldApplyColor = */ true, /* isEditable = */ false);

        final List<List<Entry>> promptEntries = new ArrayList<>();
        promptEntries.add(new ArrayList<Entry>(1));
        promptEntries.add(new ArrayList<Entry>(1));
        promptEntries.get(0).add(phonePromptEntry);
        promptEntries.get(1).add(emailPromptEntry);

        final int subHeaderTextColor = getResources().getColor(
                R.color.quickcontact_entry_sub_header_text_color);
        final PorterDuffColorFilter greyColorFilter =
                new PorterDuffColorFilter(subHeaderTextColor, PorterDuff.Mode.SRC_ATOP);
        mNoContactDetailsCard.initialize(promptEntries, 2, /* isExpanded = */ true,
                /* isAlwaysExpanded = */ true, mExpandingEntryCardViewListener);
        mNoContactDetailsCard.setVisibility(View.VISIBLE);
        mNoContactDetailsCard.setEntryHeaderColor(subHeaderTextColor);
        mNoContactDetailsCard.setColorAndFilter(subHeaderTextColor, greyColorFilter);
    }

    /**
     * Builds the {@link DataItem}s Map out of the Contact.
     * @param data The contact to build the data from.
     * @return A pair containing a list of data items sorted within mimetype and sorted
     *  amongst mimetype. The map goes from mimetype string to the sorted list of data items within
     *  mimetype
     */
    private Pair<List<List<DataItem>>, Map<String, List<DataItem>>> generateDataModelFromContact(
            Contact data) {
        Trace.beginSection("Build data items map");

        final Map<String, List<DataItem>> dataItemsMap = new HashMap<>();

        final ResolveCache cache = ResolveCache.getInstance(this);
        for (RawContact rawContact : data.getRawContacts()) {
            for (DataItem dataItem : rawContact.getDataItems()) {
                dataItem.setRawContactId(rawContact.getId());

                final String mimeType = dataItem.getMimeType();
                if (mimeType == null) continue;

                final AccountType accountType = rawContact.getAccountType(this);
                final DataKind dataKind = AccountTypeManager.getInstance(this)
                        .getKindOrFallback(accountType, mimeType);
                if (dataKind == null) continue;

                dataItem.setDataKind(dataKind);

                final boolean hasData = !TextUtils.isEmpty(dataItem.buildDataString(this,
                        dataKind));

                if (isMimeExcluded(mimeType) || !hasData) continue;

                List<DataItem> dataItemListByType = dataItemsMap.get(mimeType);
                if (dataItemListByType == null) {
                    dataItemListByType = new ArrayList<>();
                    dataItemsMap.put(mimeType, dataItemListByType);
                }
                dataItemListByType.add(dataItem);
            }
        }
        Trace.endSection();

        Trace.beginSection("sort within mimetypes");
        /*
         * Sorting is a multi part step. The end result is to a have a sorted list of the most
         * used data items, one per mimetype. Then, within each mimetype, the list of data items
         * for that type is also sorted, based off of {super primary, primary, times used} in that
         * order.
         */
        final List<List<DataItem>> dataItemsList = new ArrayList<>();
        for (List<DataItem> mimeTypeDataItems : dataItemsMap.values()) {
            // Remove duplicate data items
            Collapser.collapseList(mimeTypeDataItems, this);
            // Sort within mimetype
            Collections.sort(mimeTypeDataItems, mWithinMimeTypeDataItemComparator);
            // Add to the list of data item lists
            dataItemsList.add(mimeTypeDataItems);
        }
        Trace.endSection();

        Trace.beginSection("sort amongst mimetypes");
        // Sort amongst mimetypes to bubble up the top data items for the contact card
        Collections.sort(dataItemsList, mAmongstMimeTypeDataItemComparator);
        Trace.endSection();

        return new Pair<>(dataItemsList, dataItemsMap);
    }

    /**
     * Converts a {@link DataItem} into an {@link ExpandingEntryCardView.Entry} for display.
     * If the {@link ExpandingEntryCardView.Entry} has no visual elements, null is returned.
     * @param dataItem The {@link DataItem} to convert.
     * @return The {@link ExpandingEntryCardView.Entry}, or null if no visual elements are present.
     */
    private Entry dataItemToEntry(DataItem dataItem) {
        Drawable icon = null;
        String header = null;
        String subHeader = null;
        Drawable subHeaderIcon = null;
        String text = null;
        Drawable textIcon = null;
        Intent intent = null;
        boolean shouldApplyColor = true;
        Drawable alternateIcon = null;
        Intent alternateIntent = null;
        String alternateContentDescription = null;
        final boolean isEditable = false;

        DataKind kind = dataItem.getDataKind();

        if (dataItem instanceof ImDataItem) {
            final ImDataItem im = (ImDataItem) dataItem;
            intent = ContactsUtils.buildImIntent(this, im).first;
            header = getResources().getString(R.string.header_im_entry);
            final boolean isEmail = im.isCreatedFromEmail();
            final int protocol = isEmail ? Im.PROTOCOL_GOOGLE_TALK : im.getProtocol();
            subHeader = Im.getProtocolLabel(getResources(), protocol,
                    im.getCustomProtocol()).toString();
        } else if (dataItem instanceof OrganizationDataItem) {
            final OrganizationDataItem organization = (OrganizationDataItem) dataItem;
            header = getResources().getString(R.string.header_organization_entry);
            subHeader = organization.getCompany();
            text = organization.getTitle();
        } else if (dataItem instanceof NicknameDataItem) {
            final NicknameDataItem nickname = (NicknameDataItem) dataItem;
            // Build nickname entries
            final boolean isNameRawContact =
                (mContactData.getNameRawContactId() == dataItem.getRawContactId());

            final boolean duplicatesTitle =
                isNameRawContact
                && mContactData.getDisplayNameSource() == DisplayNameSources.NICKNAME;

            if (!duplicatesTitle) {
                header = getResources().getString(R.string.header_nickname_entry);
                subHeader = nickname.getName();
            }
        } else if (dataItem instanceof NoteDataItem) {
            final NoteDataItem note = (NoteDataItem) dataItem;
            header = getResources().getString(R.string.header_note_entry);
            subHeader = note.getNote();
        } else if (dataItem instanceof WebsiteDataItem) {
            final WebsiteDataItem website = (WebsiteDataItem) dataItem;
            header = getResources().getString(R.string.header_website_entry);
            subHeader = website.getUrl();
            try {
                final WebAddress webAddress = new WebAddress(website.buildDataString(this, kind));
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webAddress.toString()));
            } catch (final ParseException e) {
                Log.e(TAG, "Couldn't parse website: " + website.buildDataString(this, kind));
            }
        } else if (dataItem instanceof EventDataItem) {
            final EventDataItem event = (EventDataItem) dataItem;
            final String dataString = event.buildDataString(this, kind);
            final Calendar cal = DateUtils.parseDate(dataString, false);
            if (cal != null) {
                final Date nextAnniversary =
                        DateUtils.getNextAnnualDate(cal);
                final Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                builder.appendPath("time");
                ContentUris.appendId(builder, nextAnniversary.getTime());
                intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
            }
            header = getResources().getString(R.string.header_event_entry);
            if (event.hasKindTypeColumn(kind)) {
                subHeader = getResources().getString(Event.getTypeResource(
                        event.getKindTypeColumn(kind)));
            }
            text = DateUtils.formatDate(this, dataString);
        } else if (dataItem instanceof RelationDataItem) {
            final RelationDataItem relation = (RelationDataItem) dataItem;
            final String dataString = relation.buildDataString(this, kind);
            if (!TextUtils.isEmpty(dataString)) {
                intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(SearchManager.QUERY, dataString);
                intent.setType(Contacts.CONTENT_TYPE);
            }
            header = getResources().getString(R.string.header_relation_entry);
            subHeader = relation.getName();
            if (relation.hasKindTypeColumn(kind)) {
                text = Relation.getTypeLabel(getResources(), relation.getKindTypeColumn(kind),
                        relation.getLabel()).toString();
            }
        } else if (dataItem instanceof PhoneDataItem) {
            final PhoneDataItem phone = (PhoneDataItem) dataItem;
            if (!TextUtils.isEmpty(phone.getNumber())) {
                header = phone.buildDataString(this, kind);
                if (phone.hasKindTypeColumn(kind)) {
                    text = Phone.getTypeLabel(getResources(), phone.getKindTypeColumn(kind),
                            phone.getLabel()).toString();
                }
                icon = getResources().getDrawable(R.drawable.ic_phone_24dp);
                if (PhoneCapabilityTester.isPhone(this)) {
                    intent = CallUtil.getCallIntent(phone.getNumber());
                }
                alternateIntent = new Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts(CallUtil.SCHEME_SMSTO, phone.getNumber(), null));
                alternateIcon = getResources().getDrawable(R.drawable.ic_message_24dp);
                alternateContentDescription = getResources().getString(R.string.sms_other);
            }
        } else if (dataItem instanceof EmailDataItem) {
            final EmailDataItem email = (EmailDataItem) dataItem;
            final String address = email.getData();
            if (!TextUtils.isEmpty(address)) {
                final Uri mailUri = Uri.fromParts(CallUtil.SCHEME_MAILTO, address, null);
                intent = new Intent(Intent.ACTION_SENDTO, mailUri);
                header = email.getAddress();
                if (email.hasKindTypeColumn(kind)) {
                    text = Email.getTypeLabel(getResources(), email.getKindTypeColumn(kind),
                            email.getLabel()).toString();
                }
                icon = getResources().getDrawable(R.drawable.ic_email_24dp);
            }
        } else if (dataItem instanceof StructuredPostalDataItem) {
            StructuredPostalDataItem postal = (StructuredPostalDataItem) dataItem;
            final String postalAddress = postal.getFormattedAddress();
            if (!TextUtils.isEmpty(postalAddress)) {
                intent = StructuredPostalUtils.getViewPostalAddressIntent(postalAddress);
                header = postal.getFormattedAddress();
                if (postal.hasKindTypeColumn(kind)) {
                    text = StructuredPostal.getTypeLabel(getResources(),
                            postal.getKindTypeColumn(kind), postal.getLabel()).toString();
                }
                icon = getResources().getDrawable(R.drawable.ic_place_24dp);
            }
        } else if (dataItem instanceof SipAddressDataItem) {
            if (PhoneCapabilityTester.isSipPhone(this)) {
                final SipAddressDataItem sip = (SipAddressDataItem) dataItem;
                final String address = sip.getSipAddress();
                if (!TextUtils.isEmpty(address)) {
                    final Uri callUri = Uri.fromParts(CallUtil.SCHEME_SIP, address, null);
                    intent = CallUtil.getCallIntent(callUri);
                    header = address;
                    if (sip.hasKindTypeColumn(kind)) {
                        text = SipAddress.getTypeLabel(getResources(), sip.getKindTypeColumn(kind),
                            sip.getLabel()).toString();
                    }
                    // Note that this item will get a SIP-specific variant
                    // of the "call phone" icon, rather than the standard
                    // app icon for the Phone app (which we show for
                    // regular phone numbers.)  That's because the phone
                    // app explicitly specifies an android:icon attribute
                    // for the SIP-related intent-filters in its manifest.
                    icon = ResolveCache.getInstance(this).getIcon(sip.getMimeType(), intent);
                    // Call mutate to create a new Drawable.ConstantState for color filtering
                    if (icon != null) {
                        icon.mutate();
                    }
                }
            }
        } else if (dataItem instanceof StructuredNameDataItem) {
            final String givenName = ((StructuredNameDataItem) dataItem).getGivenName();
            if (!TextUtils.isEmpty(givenName)) {
                mAboutCard.setTitle(getResources().getString(R.string.about_card_title) +
                        " " + givenName);
            } else {
                mAboutCard.setTitle(getResources().getString(R.string.about_card_title));
            }
        } else {
            // Custom DataItem
            header = dataItem.buildDataStringForDisplay(this, kind);
            text = kind.typeColumn;
            intent = new Intent(Intent.ACTION_VIEW);
            final Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, dataItem.getId());
            intent.setDataAndType(uri, dataItem.getMimeType());

            if (intent != null) {
                final String mimetype = intent.getType();

                // Attempt to use known icons for known 3p types. Otherwise default to ResolveCache
                switch (mimetype) {
                    case MIMETYPE_GPLUS_PROFILE:
                        if (INTENT_DATA_GPLUS_PROFILE_ADD_TO_CIRCLE.equals(
                                intent.getDataString())) {
                            icon = getResources().getDrawable(
                                    R.drawable.ic_add_to_circles_black_24);
                        } else {
                            icon = getResources().getDrawable(R.drawable.ic_google_plus_24dp);
                        }
                        break;
                    case MIMETYPE_HANGOUTS:
                        if (INTENT_DATA_HANGOUTS_VIDEO.equals(intent.getDataString())) {
                            icon = getResources().getDrawable(R.drawable.ic_hangout_video_24dp);
                        } else {
                            icon = getResources().getDrawable(R.drawable.ic_hangout_24dp);
                        }
                        break;
                    default:
                        icon = ResolveCache.getInstance(this).getIcon(
                                dataItem.getMimeType(), intent);
                        // Call mutate to create a new Drawable.ConstantState for color filtering
                        if (icon != null) {
                            icon.mutate();
                        }
                        shouldApplyColor = false;
                }
            }
        }

        if (intent != null) {
            // Do not set the intent is there are no resolves
            if (!PhoneCapabilityTester.isIntentRegistered(this, intent)) {
                intent = null;
            }
        }

        if (alternateIntent != null) {
            // Do not set the alternate intent is there are no resolves
            if (!PhoneCapabilityTester.isIntentRegistered(this, alternateIntent)) {
                alternateIntent = null;
            }

            // Attempt to use package manager to find a suitable content description if needed
            if (TextUtils.isEmpty(alternateContentDescription)) {
                alternateContentDescription = getIntentResolveLabel(alternateIntent);
            }
        }

        // If the Entry has no visual elements, return null
        if (icon == null && TextUtils.isEmpty(header) && TextUtils.isEmpty(subHeader) &&
                subHeaderIcon == null && TextUtils.isEmpty(text) && textIcon == null) {
            return null;
        }

        final int dataId = dataItem.getId() > Integer.MAX_VALUE ?
                -1 : (int) dataItem.getId();

        return new Entry(dataId, icon, header, subHeader, subHeaderIcon, text, textIcon, intent,
                alternateIcon, alternateIntent, alternateContentDescription, shouldApplyColor,
                isEditable);
    }

    private List<Entry> dataItemsToEntries(List<DataItem> dataItems) {
        final List<Entry> entries = new ArrayList<>();
        for (DataItem dataItem : dataItems) {
            final Entry entry = dataItemToEntry(dataItem);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private String getIntentResolveLabel(Intent intent) {
        final List<ResolveInfo> matches = getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);

        // Pick first match, otherwise best found
        ResolveInfo bestResolve = null;
        final int size = matches.size();
        if (size == 1) {
            bestResolve = matches.get(0);
        } else if (size > 1) {
            bestResolve = ResolveCache.getInstance(this).getBestResolve(intent, matches);
        }

        if (bestResolve == null) {
            return null;
        }

        return String.valueOf(bestResolve.loadLabel(getPackageManager()));
    }

    /**
     * Asynchronously extract the most vibrant color from the PhotoView. Once extracted,
     * apply this tint to {@link MultiShrinkScroller}. This operation takes about 20-30ms
     * on a Nexus 5.
     */
    private void extractAndApplyTintFromPhotoViewAsynchronously() {
        if (mScroller == null) {
            return;
        }
        final Drawable imageViewDrawable = mPhotoView.getDrawable();
        new AsyncTask<Void, Void, MaterialPalette>() {
            @Override
            protected MaterialPalette doInBackground(Void... params) {

                if (imageViewDrawable instanceof BitmapDrawable) {
                    final Bitmap bitmap = ((BitmapDrawable) imageViewDrawable).getBitmap();
                    final int primaryColor = colorFromBitmap(bitmap);
                    if (primaryColor != 0) {
                        return mMaterialColorMapUtils.calculatePrimaryAndSecondaryColor(
                                primaryColor);
                    }
                }
                if (imageViewDrawable instanceof LetterTileDrawable) {
                    final int primaryColor = ((LetterTileDrawable) imageViewDrawable).getColor();
                    return mMaterialColorMapUtils.calculatePrimaryAndSecondaryColor(primaryColor);
                }
                return MaterialColorMapUtils.getDefaultPrimaryAndSecondaryColors(getResources());
            }

            @Override
            protected void onPostExecute(MaterialPalette palette) {
                super.onPostExecute(palette);
                if (mHasComputedThemeColor) {
                    // If we had previously computed a theme color from the contact photo,
                    // then do not update the theme color. Changing the theme color several
                    // seconds after QC has started, as a result of an updated/upgraded photo,
                    // is a jarring experience. On the other hand, changing the theme color after
                    // a rotation or onNewIntent() is perfectly fine.
                    return;
                }
                // Check that the Photo has not changed. If it has changed, the new tint
                // color needs to be extracted
                if (imageViewDrawable == mPhotoView.getDrawable()) {
                    mHasComputedThemeColor = true;
                    setThemeColor(palette);
                }
            }
        }.execute();
    }

    /**
     * Examine how many white pixels are in the bitmap in order to determine whether or not
     * we need gradient overlays on top of the image.
     */
    private void analyzeWhitenessOfPhotoAsynchronously() {
        final Drawable imageViewDrawable = mPhotoView.getDrawable();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                if (imageViewDrawable instanceof BitmapDrawable) {
                    final Bitmap bitmap = ((BitmapDrawable) imageViewDrawable).getBitmap();
                    return WhitenessUtils.isBitmapWhiteAtTopOrBottom(bitmap);
                }
                return !(imageViewDrawable instanceof LetterTileDrawable);
            }

            @Override
            protected void onPostExecute(Boolean isWhite) {
                super.onPostExecute(isWhite);
                mScroller.setUseGradient(isWhite);
            }
        }.execute();
    }

    private void setThemeColor(MaterialPalette palette) {
        // If the color is invalid, use the predefined default
        final int primaryColor = palette.mPrimaryColor;
        mScroller.setHeaderTintColor(primaryColor);
        mStatusBarColor = palette.mSecondaryColor;
        updateStatusBarColor();

        mColorFilter =
                new PorterDuffColorFilter(primaryColor, PorterDuff.Mode.SRC_ATOP);
        mContactCard.setColorAndFilter(primaryColor, mColorFilter);
        mRecentCard.setColorAndFilter(primaryColor, mColorFilter);
        mAboutCard.setColorAndFilter(primaryColor, mColorFilter);
    }

    private void updateStatusBarColor() {
        if (mScroller == null) {
            return;
        }
        final int desiredStatusBarColor;
        // Only use a custom status bar color if QuickContacts touches the top of the viewport.
        if (mScroller.getScrollNeededToBeFullScreen() <= 0) {
            desiredStatusBarColor = mStatusBarColor;
        } else {
            desiredStatusBarColor = Color.TRANSPARENT;
        }
        // Animate to the new color.
        if (desiredStatusBarColor != getWindow().getStatusBarColor()) {
            final ObjectAnimator animation = ObjectAnimator.ofInt(getWindow(), "statusBarColor",
                    getWindow().getStatusBarColor(), desiredStatusBarColor);
            animation.setDuration(ANIMATION_STATUS_BAR_COLOR_CHANGE_DURATION);
            animation.setEvaluator(new ArgbEvaluator());
            animation.start();
        }
    }

    private int colorFromBitmap(Bitmap bitmap) {
        // Author of Palette recommends using 24 colors when analyzing profile photos.
        final int NUMBER_OF_PALETTE_COLORS = 24;
        final Palette palette = Palette.generate(bitmap, NUMBER_OF_PALETTE_COLORS);
        if (palette != null && palette.getVibrantSwatch() != null) {
            return palette.getVibrantSwatch().getRgb();
        }
        return 0;
    }

    private List<Entry> contactInteractionsToEntries(List<ContactInteraction> interactions) {
        final List<Entry> entries = new ArrayList<>();
        for (ContactInteraction interaction : interactions) {
            entries.add(new Entry(/* id = */ -1,
                    interaction.getIcon(this),
                    interaction.getViewHeader(this),
                    interaction.getViewBody(this),
                    interaction.getBodyIcon(this),
                    interaction.getViewFooter(this),
                    interaction.getFooterIcon(this),
                    interaction.getIntent(),
                    /* alternateIcon = */ null,
                    /* alternateIntent = */ null,
                    /* alternateContentDescription = */ null,
                    /* shouldApplyColor = */ true,
                    /* isEditable = */ false));
        }
        return entries;
    }

    private final LoaderCallbacks<Contact> mLoaderContactCallbacks =
            new LoaderCallbacks<Contact>() {
        @Override
        public void onLoaderReset(Loader<Contact> loader) {
            mContactData = null;
        }

        @Override
        public void onLoadFinished(Loader<Contact> loader, Contact data) {
            Trace.beginSection("onLoadFinished()");

            if (isFinishing()) {
                return;
            }
            if (data.isError()) {
                // This shouldn't ever happen, so throw an exception. The {@link ContactLoader}
                // should log the actual exception.
                throw new IllegalStateException("Failed to load contact", data.getException());
            }
            if (data.isNotFound()) {
                if (mHasAlreadyBeenOpened) {
                    finish();
                } else {
                    Log.i(TAG, "No contact found: " + ((ContactLoader)loader).getLookupUri());
                    Toast.makeText(QuickContactActivity.this, R.string.invalidContactMessage,
                            Toast.LENGTH_LONG).show();
                }
                return;
            }

            bindContactData(data);

            Trace.endSection();
        }

        @Override
        public Loader<Contact> onCreateLoader(int id, Bundle args) {
            if (mLookupUri == null) {
                Log.wtf(TAG, "Lookup uri wasn't initialized. Loader was started too early");
            }
            // Load all contact data. We need loadGroupMetaData=true to determine whether the
            // contact is invisible. If it is, we need to display an "Add to Contacts" MenuItem.
            return new ContactLoader(getApplicationContext(), mLookupUri,
                    true /*loadGroupMetaData*/, false /*loadInvitableAccountTypes*/,
                    true /*postViewNotification*/, true /*computeFormattedPhoneNumber*/);
        }
    };

    @Override
    public void onBackPressed() {
        if (mScroller != null) {
            if (!mIsExitAnimationInProgress) {
                mScroller.scrollOffBottom();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        super.finish();

        // override transitions to skip the standard window animations
        overridePendingTransition(0, 0);
    }

    private final LoaderCallbacks<List<ContactInteraction>> mLoaderInteractionsCallbacks =
            new LoaderCallbacks<List<ContactInteraction>>() {

        @Override
        public Loader<List<ContactInteraction>> onCreateLoader(int id, Bundle args) {
            Log.v(TAG, "onCreateLoader");
            Loader<List<ContactInteraction>> loader = null;
            switch (id) {
                case LOADER_SMS_ID:
                    Log.v(TAG, "LOADER_SMS_ID");
                    loader = new SmsInteractionsLoader(
                            QuickContactActivity.this,
                            args.getStringArray(KEY_LOADER_EXTRA_PHONES),
                            MAX_SMS_RETRIEVE);
                    break;
                case LOADER_CALENDAR_ID:
                    Log.v(TAG, "LOADER_CALENDAR_ID");
                    final String[] emailsArray = args.getStringArray(KEY_LOADER_EXTRA_EMAILS);
                    List<String> emailsList = null;
                    if (emailsArray != null) {
                        emailsList = Arrays.asList(args.getStringArray(KEY_LOADER_EXTRA_EMAILS));
                    }
                    loader = new CalendarInteractionsLoader(
                            QuickContactActivity.this,
                            emailsList,
                            MAX_FUTURE_CALENDAR_RETRIEVE,
                            MAX_PAST_CALENDAR_RETRIEVE,
                            FUTURE_MILLISECOND_TO_SEARCH_LOCAL_CALENDAR,
                            PAST_MILLISECOND_TO_SEARCH_LOCAL_CALENDAR);
                    break;
                case LOADER_CALL_LOG_ID:
                    Log.v(TAG, "LOADER_CALL_LOG_ID");
                    loader = new CallLogInteractionsLoader(
                            QuickContactActivity.this,
                            args.getStringArray(KEY_LOADER_EXTRA_PHONES),
                            MAX_CALL_LOG_RETRIEVE);
            }
            return loader;
        }

        @Override
        public void onLoadFinished(Loader<List<ContactInteraction>> loader,
                List<ContactInteraction> data) {
            mRecentLoaderResults.put(loader.getId(), data);

            if (isAllRecentDataLoaded()) {
                bindRecentData();
            }
        }

        @Override
        public void onLoaderReset(Loader<List<ContactInteraction>> loader) {
            mRecentLoaderResults.remove(loader.getId());
        }
    };

    private boolean isAllRecentDataLoaded() {
        return mRecentLoaderResults.size() == mRecentLoaderIds.length;
    }

    private void bindRecentData() {
        final List<ContactInteraction> allInteractions = new ArrayList<>();
        for (List<ContactInteraction> loaderInteractions : mRecentLoaderResults.values()) {
            allInteractions.addAll(loaderInteractions);
        }

        // Sort the interactions by most recent
        Collections.sort(allInteractions, new Comparator<ContactInteraction>() {
            @Override
            public int compare(ContactInteraction a, ContactInteraction b) {
                return a.getInteractionDate() >= b.getInteractionDate() ? -1 : 1;
            }
        });


        List<List<Entry>> interactionsWrapper = new ArrayList<>();
        interactionsWrapper.add(contactInteractionsToEntries(allInteractions));
        if (allInteractions.size() > 0) {
            mRecentCard.initialize(interactionsWrapper,
                    /* numInitialVisibleEntries = */ MIN_NUM_COLLAPSED_RECENT_ENTRIES_SHOWN,
                    /* isExpanded = */ mRecentCard.isExpanded(), /* isAlwaysExpanded = */ false,
                    mExpandingEntryCardViewListener);
            mRecentCard.setVisibility(View.VISIBLE);
        }

        // About card is initialized along with the contact card, but since it appears after
        // the recent card in the UI, we hold off until making it visible until the recent card
        // is also ready to avoid stuttering.
        if (mAboutCard.shouldShow()) {
            mAboutCard.setVisibility(View.VISIBLE);
        } else {
            mAboutCard.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mEntriesAndActionsTask != null) {
            // Once the activity is stopped, we will no longer want to bind mEntriesAndActionsTask's
            // results on the UI thread. In some circumstances Activities are killed without
            // onStop() being called. This is not a problem, because in these circumstances
            // the entire process will be killed.
            mEntriesAndActionsTask.cancel(/* mayInterruptIfRunning = */ false);
        }
    }

    /**
     * Returns true if it is possible to edit the current contact.
     */
    private boolean isContactEditable() {
        return mContactData != null && !mContactData.isDirectoryEntry();
    }

    private Intent getEditContactIntent() {
        final Intent intent = new Intent(Intent.ACTION_EDIT, mLookupUri);
        mContactLoader.cacheResult();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        return intent;
    }

    private void editContact() {
        startActivityForResult(getEditContactIntent(), REQUEST_CODE_CONTACT_EDITOR_ACTIVITY);
    }

    private void toggleStar(MenuItem starredMenuItem) {
        // Make sure there is a contact
        if (mLookupUri != null) {
            // Read the current starred value from the UI instead of using the last
            // loaded state. This allows rapid tapping without writing the same
            // value several times
            final boolean isStarred = starredMenuItem.isChecked();

            // To improve responsiveness, swap out the picture (and tag) in the UI already
            ContactDisplayUtils.configureStarredMenuItem(starredMenuItem,
                    mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                    !isStarred);

            // Now perform the real save
            final Intent intent = ContactSaveService.createSetStarredIntent(
                    QuickContactActivity.this, mLookupUri, !isStarred);
            startService(intent);

            final CharSequence accessibilityText = !isStarred
                    ? getResources().getText(R.string.description_action_menu_add_star)
                    : getResources().getText(R.string.description_action_menu_remove_star);
            // Accessibility actions need to have an associated view. We can't access the MenuItem's
            // underlying view, so put this accessibility action on the root view.
            mScroller.announceForAccessibility(accessibilityText);
        }
    }

    /**
     * Calls into the contacts provider to get a pre-authorized version of the given URI.
     */
    private Uri getPreAuthorizedUri(Uri uri) {
        final Bundle uriBundle = new Bundle();
        uriBundle.putParcelable(ContactsContract.Authorization.KEY_URI_TO_AUTHORIZE, uri);
        final Bundle authResponse = getContentResolver().call(
                ContactsContract.AUTHORITY_URI,
                ContactsContract.Authorization.AUTHORIZATION_METHOD,
                null,
                uriBundle);
        if (authResponse != null) {
            return (Uri) authResponse.getParcelable(
                    ContactsContract.Authorization.KEY_AUTHORIZED_URI);
        } else {
            return uri;
        }
    }

    private void shareContact() {
        final String lookupKey = mContactData.getLookupKey();
        Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);
        if (mContactData.isUserProfile()) {
            // User is sharing the profile.  We don't want to force the receiver to have
            // the highly-privileged READ_PROFILE permission, so we need to request a
            // pre-authorized URI from the provider.
            shareUri = getPreAuthorizedUri(shareUri);
        }

        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(Contacts.CONTENT_VCARD_TYPE);
        intent.putExtra(Intent.EXTRA_STREAM, shareUri);

        // Launch chooser to share contact via
        final CharSequence chooseTitle = getText(R.string.share_via);
        final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);

        try {
            this.startActivity(chooseIntent);
        } catch (final ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Creates a launcher shortcut with the current contact.
     */
    private void createLauncherShortcutWithContact() {
        final ShortcutIntentBuilder builder = new ShortcutIntentBuilder(this,
                new OnShortcutIntentCreatedListener() {

                    @Override
                    public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
                        // Broadcast the shortcutIntent to the launcher to create a
                        // shortcut to this contact
                        shortcutIntent.setAction(ACTION_INSTALL_SHORTCUT);
                        QuickContactActivity.this.sendBroadcast(shortcutIntent);

                        // Send a toast to give feedback to the user that a shortcut to this
                        // contact was added to the launcher.
                        Toast.makeText(QuickContactActivity.this,
                                R.string.createContactShortcutSuccessful,
                                Toast.LENGTH_SHORT).show();
                    }

                });
        builder.createContactShortcutIntent(mLookupUri);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.quickcontact, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mContactData != null) {
            final MenuItem starredMenuItem = menu.findItem(R.id.menu_star);
            ContactDisplayUtils.configureStarredMenuItem(starredMenuItem,
                    mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                    mContactData.getStarred());
            // Configure edit MenuItem
            final MenuItem editMenuItem = menu.findItem(R.id.menu_edit);
            editMenuItem.setVisible(true);
            if (DirectoryContactUtil.isDirectoryContact(mContactData) || InvisibleContactUtil
                    .isInvisibleAndAddable(mContactData, this)) {
                editMenuItem.setIcon(R.drawable.ic_person_add_tinted_24dp);
                editMenuItem.setTitle(R.string.menu_add_contact);
            } else if (isContactEditable()) {
                editMenuItem.setIcon(R.drawable.ic_create_24dp);
                editMenuItem.setTitle(R.string.menu_editContact);
            } else {
                editMenuItem.setVisible(false);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_star:
                toggleStar(item);
                return true;
            case R.id.menu_edit:
                if (DirectoryContactUtil.isDirectoryContact(mContactData)) {
                    DirectoryContactUtil.addToMyContacts(mContactData, this, getFragmentManager(),
                            mSelectAccountFragmentListener);
                } else if (InvisibleContactUtil.isInvisibleAndAddable(mContactData, this)) {
                    InvisibleContactUtil.addToDefaultGroup(mContactData, this);
                } else if (isContactEditable()) {
                    editContact();
                }
                return true;
            case R.id.menu_share:
                shareContact();
                return true;
            case R.id.menu_create_contact_shortcut:
                createLauncherShortcutWithContact();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
