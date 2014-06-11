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

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Trace;
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
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.common.Collapser;
import com.android.contacts.R;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.model.dataitem.EmailDataItem;
import com.android.contacts.common.model.dataitem.ImDataItem;
import com.android.contacts.common.model.dataitem.PhoneDataItem;
import com.android.contacts.common.util.DataStatus;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.interactions.ContactInteraction;
import com.android.contacts.interactions.SmsInteractionsLoader;
import com.android.contacts.quickcontact.Action;
import com.android.contacts.quickcontact.ExpandingEntryCardView.Entry;
import com.android.contacts.util.ImageViewDrawableSetter;
import com.android.contacts.util.SchedulingUtils;
import com.android.contacts.widget.MultiShrinkScroller;
import com.android.contacts.widget.MultiShrinkScroller.MultiShrinkScrollerListener;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mostly translucent {@link Activity} that shows QuickContact dialog. It loads
 * data asynchronously, and then shows a popup with details centered around
 * {@link Intent#getSourceBounds()}.
 */
public class QuickContactActivity extends Activity {

    /**
     * QuickContacts immediately takes up the full screen. All possible information is shown.
     * This value for {@link android.provider.ContactsContract.QuickContact#EXTRA_MODE}
     * should only be used by the Contacts app.
     */
    public static final int MODE_FULLY_EXPANDED = 4;

    private static final String TAG = "QuickContact";

    private static final int ANIMATION_SLIDE_OPEN_DURATION = 250;
    private static final int ANIMATION_STATUS_BAR_COLOR_CHANGE_DURATION = 75;
    private static final int REQUEST_CODE_CONTACT_EDITOR_ACTIVITY = 1;
    private static final float SYSTEM_BAR_BRIGHTNESS_FACTOR = 0.7f;
    private static final int SHIM_COLOR = Color.argb(0x7F, 0, 0, 0);


    @SuppressWarnings("deprecation")
    private static final String LEGACY_AUTHORITY = android.provider.Contacts.AUTHORITY;

    private Uri mLookupUri;
    private String[] mExcludeMimes;
    private int mExtraMode;
    private int mStatusBarColor;
    private boolean mHasAlreadyBeenOpened;

    private View mPhotoContainer;

    private ImageView mPhotoView;
    private ImageView mEditOrAddContactImage;
    private ImageView mStarImage;
    private ExpandingEntryCardView mCommunicationCard;
    private ExpandingEntryCardView mRecentCard;
    private MultiShrinkScroller mScroller;
    private AsyncTask<Void, Void, Void> mEntriesAndActionsTask;

    private static final int MIN_NUM_COMMUNICATION_ENTRIES_SHOWN = 3;
    private static final int MIN_NUM_COLLAPSED_RECENT_ENTRIES_SHOWN = 3;

    private Contact mContactData;
    private ContactLoader mContactLoader;

    private PorterDuffColorFilter mColorFilter;
    List<Drawable> mDrawablesToTint;

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

    /** Id for the background contact loader */
    private static final int LOADER_CONTACT_ID = 0;

    /** Id for the background Sms Loader */
    private static final int LOADER_SMS_ID = 1;
    private static final String KEY_LOADER_EXTRA_SMS_PHONES =
            QuickContactActivity.class.getCanonicalName() + ".KEY_LOADER_EXTRA_SMS_PHONES";
    private static final int MAX_SMS_RETRIEVE = 3;

    private static final int[] mRecentLoaderIds = new int[LOADER_SMS_ID];
    private Map<Integer, List<ContactInteraction>> mRecentLoaderResults;

    final OnClickListener mEditContactClickHandler = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(Intent.ACTION_EDIT, mLookupUri);
            mContactLoader.cacheResult();
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            startActivityForResult(intent, REQUEST_CODE_CONTACT_EDITOR_ACTIVITY);
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

    final OnClickListener mEntryClickHandler = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "mEntryClickHandler onClick");
            Object intent = v.getTag();
            if (intent == null || !(intent instanceof Intent)) {
                return;
            }
            startActivity((Intent) intent);
        }
    };

    final MultiShrinkScrollerListener mMultiShrinkScrollerListener
            = new MultiShrinkScrollerListener() {
        @Override
        public void onScrolledOffBottom() {
            onBackPressed();
        }

        @Override
        public void onEnterFullscreen() {
            updateStatusBarColor();
        }

        @Override
        public void onExitFullscreen() {
            updateStatusBarColor();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Trace.beginSection("onCreate()");
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        // Since we can't disable Window animations from the Launcher, we can minimize the
        // silliness of the animation by setting the navigation bar transparent.
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // Parse intent
        final Intent intent = getIntent();

        Uri lookupUri = intent.getData();

        // Check to see whether it comes from the old version.
        if (lookupUri != null && LEGACY_AUTHORITY.equals(lookupUri.getAuthority())) {
            final long rawContactId = ContentUris.parseId(lookupUri);
            lookupUri = RawContacts.getContactLookupUri(getContentResolver(),
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
        }

        mExtraMode = getIntent().getIntExtra(QuickContact.EXTRA_MODE,
                QuickContact.MODE_LARGE);

        mLookupUri = Preconditions.checkNotNull(lookupUri, "missing lookupUri");

        mExcludeMimes = intent.getStringArrayExtra(QuickContact.EXTRA_EXCLUDE_MIMES);

        mContactLoader = (ContactLoader) getLoaderManager().initLoader(
                LOADER_CONTACT_ID, null, mLoaderContactCallbacks);

        // Show QuickContact in front of soft input
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        setContentView(R.layout.quickcontact_activity);

        mEditOrAddContactImage = (ImageView) findViewById(R.id.contact_edit_image);
        mStarImage = (ImageView) findViewById(R.id.quickcontact_star_button);
        mCommunicationCard = (ExpandingEntryCardView) findViewById(R.id.communication_card);
        mRecentCard = (ExpandingEntryCardView) findViewById(R.id.recent_card);
        mScroller = (MultiShrinkScroller) findViewById(R.id.multiscroller);

        mEditOrAddContactImage.setOnClickListener(mEditContactClickHandler);

        mCommunicationCard.setOnClickListener(mEntryClickHandler);
        mCommunicationCard.setTitle(getResources().getString(R.string.communication_card_title));
        mCommunicationCard.setExpandButtonText(
        getResources().getString(R.string.expanding_entry_card_view_see_all));

        mRecentCard.setOnClickListener(mEntryClickHandler);
        mRecentCard.setTitle(getResources().getString(R.string.recent_card_title));

        // find and prepare correct header view
        mPhotoContainer = findViewById(R.id.photo_container);

        setHeaderNameText(R.id.name, R.string.missing_name);

        mPhotoView = (ImageView) mPhotoContainer.findViewById(R.id.photo);
        mPhotoView.setOnClickListener(mEditContactClickHandler);

        mHasAlreadyBeenOpened = savedInstanceState != null;

        final ColorDrawable windowShim = new ColorDrawable(SHIM_COLOR);
        getWindow().setBackgroundDrawable(windowShim);
        if (!mHasAlreadyBeenOpened) {
            final int duration = getResources().getInteger(android.R.integer.config_shortAnimTime);
            ObjectAnimator.ofInt(windowShim, "alpha", 0, 0xFF).setDuration(duration).start();
        }

        if (mScroller != null) {
            mScroller.initialize(mMultiShrinkScrollerListener);
            if (mHasAlreadyBeenOpened) {
                mScroller.setVisibility(View.VISIBLE);
                mScroller.setScroll(mScroller.getScrollNeededToBeFullScreen());
            } else {
                mScroller.setVisibility(View.GONE);
            }
        }


        mDrawablesToTint = new ArrayList<>();

        Trace.endSection();
    }

    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
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
    }

    private void runEntranceAnimation() {
        if (mHasAlreadyBeenOpened) {
            return;
        }
        mHasAlreadyBeenOpened = true;
        final int bottomScroll = mScroller.getScrollUntilOffBottom() - 1;
        final ObjectAnimator scrollAnimation
                = ObjectAnimator.ofInt(mScroller, "scroll", -bottomScroll,
                mExtraMode != MODE_FULLY_EXPANDED ? 0 : mScroller.getScrollNeededToBeFullScreen());
        scrollAnimation.setDuration(ANIMATION_SLIDE_OPEN_DURATION);
        scrollAnimation.start();
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
    private void bindContactData(final Contact data) {
        Trace.beginSection("bindContactData");
        mContactData = data;
        final Context context = this;

        mEditOrAddContactImage.setVisibility(isMimeExcluded(Contacts.CONTENT_ITEM_TYPE) ?
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

        Trace.endSection();
        Trace.beginSection("Set display photo & name");

        mPhotoSetter.setupContactPhoto(data, mPhotoView);
        extractAndApplyTintFromPhotoViewAsynchronously();
        setHeaderNameText(R.id.name, data.getDisplayName());

        Trace.endSection();

        final List<String> sortedActionMimeTypes = Lists.newArrayList();
        // Maintain a list of phone numbers to pass into SmsInteractionsLoader
        final List<String> phoneNumbers = Lists.newArrayList();
        // List of Entry that makes up the ExpandingEntryCardView
        final List<Entry> entries = Lists.newArrayList();

        mEntriesAndActionsTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                computeEntriesAndActions(data, phoneNumbers, sortedActionMimeTypes, entries);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                // Check that original AsyncTask parameters are still valid and the activity
                // is still running before binding to UI. A new intent could invalidate
                // the results, for example.
                if (data == mContactData && !isCancelled()) {
                    bindEntriesAndActions(entries, phoneNumbers, sortedActionMimeTypes);
                    showActivity();
                }
            }
        };
        mEntriesAndActionsTask.execute();
    }

    private void bindEntriesAndActions(List<Entry> entries,
            List<String> phoneNumbers,
            List<String> sortedActionMimeTypes) {
        Trace.beginSection("start sms loader");

        Bundle smsExtraBundle = new Bundle();
        smsExtraBundle.putStringArray(KEY_LOADER_EXTRA_SMS_PHONES,
                phoneNumbers.toArray(new String[phoneNumbers.size()]));
        getLoaderManager().initLoader(
                LOADER_SMS_ID,
                smsExtraBundle,
                mLoaderInteractionsCallbacks);

        Trace.endSection();
        Trace.beginSection("bind communicate card");

        if (entries.size() > 0) {
            mCommunicationCard.initialize(entries,
                    /* numInitialVisibleEntries = */ MIN_NUM_COMMUNICATION_ENTRIES_SHOWN,
                    /* isExpanded = */ false,
                    /* themeColor = */ 0);
        }

        final boolean hasData = !sortedActionMimeTypes.isEmpty();
        mCommunicationCard.setVisibility(hasData ? View.VISIBLE: View.GONE);

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

    private void computeEntriesAndActions(Contact data, List<String> phoneNumbers,
            List<String> sortedActionMimeTypes, List<Entry> entries) {
        Trace.beginSection("inflate entries and actions");

        final ResolveCache cache = ResolveCache.getInstance(this);
        for (RawContact rawContact : data.getRawContacts()) {
            for (DataItem dataItem : rawContact.getDataItems()) {
                final String mimeType = dataItem.getMimeType();
                final AccountType accountType = rawContact.getAccountType(this);
                final DataKind dataKind = AccountTypeManager.getInstance(this)
                        .getKindOrFallback(accountType, mimeType);

                if (dataItem instanceof PhoneDataItem) {
                    phoneNumbers.add(((PhoneDataItem) dataItem).getNormalizedNumber());
                }

                // Skip this data item if MIME-type excluded
                if (isMimeExcluded(mimeType)) continue;

                final long dataId = dataItem.getId();
                final boolean isPrimary = dataItem.isPrimary();
                final boolean isSuperPrimary = dataItem.isSuperPrimary();

                if (dataKind != null) {
                    // Build an action for this data entry, find a mapping to a UI
                    // element, build its summary from the cursor, and collect it
                    // along with all others of this MIME-type.
                    final Action action = new DataAction(getApplicationContext(),
                            dataItem, dataKind);
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
                        final DataAction action = new DataAction(getApplicationContext(),
                                im, dataKind);
                        action.setPresence(status.getPresence());
                        considerAdd(action, cache, isSuperPrimary);
                    }
                }
            }
        }

        Trace.endSection();
        Trace.beginSection("collapsing action list");

        // Collapse Action Lists (remove e.g. duplicate e-mail addresses from different sources)
        for (List<Action> actionChildren : mActions.values()) {
            Collapser.collapseList(actionChildren);
        }

        Trace.endSection();
        Trace.beginSection("sort mimetypes");

        // All the mime-types to add.
        final Set<String> containedTypes = new HashSet<String>(mActions.keySet());
        // First, add LEADING_MIMETYPES, which are most common.
        for (String mimeType : LEADING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                sortedActionMimeTypes.add(mimeType);
                containedTypes.remove(mimeType);
                entries.addAll(actionsToEntries(mActions.get(mimeType)));
            }
        }

        // Add all the remaining ones that are not TRAILING
        for (String mimeType : containedTypes.toArray(new String[containedTypes.size()])) {
            if (!TRAILING_MIMETYPES.contains(mimeType)) {
                sortedActionMimeTypes.add(mimeType);
                containedTypes.remove(mimeType);
                entries.addAll(actionsToEntries(mActions.get(mimeType)));
            }
        }

        // Then, add TRAILING_MIMETYPES, which are least common.
        for (String mimeType : TRAILING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                containedTypes.remove(mimeType);
                sortedActionMimeTypes.add(mimeType);
                entries.addAll(actionsToEntries(mActions.get(mimeType)));
            }
        }

        Trace.endSection();
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
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... params) {
                if (imageViewDrawable instanceof BitmapDrawable) {
                    final Bitmap bitmap = ((BitmapDrawable) imageViewDrawable).getBitmap();
                    return colorFromBitmap(bitmap);
                }
                if (imageViewDrawable instanceof LetterTileDrawable) {
                    // LetterTileDrawable doesn't normally draw unless it is visible. Therefore,
                    // we need to directly ask it for its color via getColor(). We could directly
                    // return this color. However, in the future Palette#generate() may incorporate
                    // saturation boosting. So I want to use Palette#generate() for the sake of
                    // consistency.
                    final LetterTileDrawable tileDrawable = (LetterTileDrawable) imageViewDrawable;
                    final int PALETTE_BITMAP_SIZE = 1;
                    final Bitmap bitmap = Bitmap.createBitmap(PALETTE_BITMAP_SIZE,
                            PALETTE_BITMAP_SIZE, Bitmap.Config.ARGB_8888);
                    // If Palette can not extract a primary color, our UX person says we are better
                    // off using the LetterTileDrawable's non vibrant color than falling back
                    // to the app's default color.
                    final int color = colorFromBitmap(bitmap);
                    if (color == 0) {
                        return tileDrawable.getColor();
                    } else {
                        return color;
                    }
                }
                return 0;
            }

            @Override
            protected void onPostExecute(Integer color) {
                super.onPostExecute(color);
                mColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                // Make sure the color is valid. Also check that the Photo has not changed. If it
                // has changed, the new tint color needs to be extracted
                if (color != 0 && imageViewDrawable == mPhotoView.getDrawable()) {
                    // TODO: animate from the previous tint.
                    mScroller.setHeaderTintColor(color);

                    // Create a darker version of the actionbar color. HSV is device dependent
                    // and not perceptually-linear. Therefore, we can't say mStatusBarColor is
                    // 70% as bright as the action bar color. We can only say: it is a bit darker.
                    final float hsvComponents[] = new float[3];
                    Color.colorToHSV(color, hsvComponents);
                    hsvComponents[2] *= SYSTEM_BAR_BRIGHTNESS_FACTOR;
                    mStatusBarColor = Color.HSVToColor(hsvComponents);

                    updateStatusBarColor();
                    for (Drawable drawable : mDrawablesToTint) {
                        applyThemeColorIfAvailable(drawable);
                    }
                    mDrawablesToTint.clear();
                }
            }
        }.execute();
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
        if (palette != null && palette.getVibrantColor() != null) {
            return palette.getVibrantColor().getRgb();
        }
        return 0;
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
            mEditOrAddContactImage.setImageResource(R.drawable.ic_person_add_24dp);
            mEditOrAddContactImage.setOnClickListener(mAddToContactsClickHandler);
            mPhotoView.setOnClickListener(mAddToContactsClickHandler);
        } else {
            mEditOrAddContactImage.setImageResource(R.drawable.ic_create_24dp);
            mEditOrAddContactImage.setOnClickListener(mEditContactClickHandler);
            mPhotoView.setOnClickListener(mEditContactClickHandler);
        }
    }

    /**
     * Converts a list of Action into a list of Entry
     * @param actions The list of Action to convert
     * @return The converted list of Entry
     */
    private List<Entry> actionsToEntries(List<Action> actions) {
        List<Entry> entries = new ArrayList<>();
        for (Action action :  actions) {
            String header = null;
            String body = null;
            String footer = null;
            Drawable icon = null;
            switch (action.getMimeType()) {
                case Phone.CONTENT_ITEM_TYPE:
                    header = action.getBody().toString();
                    footer = action.getSubtitle().toString();
                    icon = applyThemeColorIfAvailable(
                            getResources().getDrawable(R.drawable.ic_phone_24dp));
                    break;
                case Email.CONTENT_ITEM_TYPE:
                    header = action.getBody().toString();
                    footer = action.getSubtitle().toString();
                    icon = applyThemeColorIfAvailable(
                            getResources().getDrawable(R.drawable.ic_email_24dp));
                    break;
                case StructuredPostal.CONTENT_ITEM_TYPE:
                    header = action.getBody().toString();
                    footer = action.getSubtitle().toString();
                    icon = applyThemeColorIfAvailable(
                            getResources().getDrawable(R.drawable.ic_place_24dp));
                    break;
                default:
                    header = action.getSubtitle().toString();
                    footer = action.getBody().toString();
                    icon = ResolveCache.getInstance(this).getIcon(action);
            }
            entries.add(new Entry(icon, header, body, footer, action.getIntent(),
                    /* isEditable= */ false));

            // Add SMS in addition to phone calls
            if (action.getMimeType().equals(Phone.CONTENT_ITEM_TYPE)) {
                entries.add(new Entry(applyThemeColorIfAvailable(getResources().getDrawable(
                        R.drawable.ic_message_24dp)),
                        getResources().getString(R.string.send_message), null, header,
                        action.getAlternateIntent(), /* isEditable = */ false));
            }
        }
        return entries;
    }

    private List<Entry> contactInteractionsToEntries(List<ContactInteraction> interactions) {
        List<Entry> entries = new ArrayList<>();
        for (ContactInteraction interaction : interactions) {
            entries.add(new Entry(applyThemeColorIfAvailable(interaction.getIcon(this)),
                    interaction.getViewHeader(this),
                    interaction.getViewBody(this),
                    interaction.getBodyIcon(this),
                    interaction.getViewFooter(this),
                    interaction.getFooterIcon(this),
                    interaction.getIntent(),
                    /* isEditable = */ false));
        }
        return entries;
    }

    private LoaderCallbacks<Contact> mLoaderContactCallbacks =
            new LoaderCallbacks<Contact>() {
        @Override
        public void onLoaderReset(Loader<Contact> loader) {
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
            return new ContactLoader(getApplicationContext(), mLookupUri,
                    false /*loadGroupMetaData*/, false /*loadInvitableAccountTypes*/,
                    false /*postViewNotification*/, true /*computeFormattedPhoneNumber*/);
        }
    };

    @Override
    public void onBackPressed() {
        if (mScroller != null) {
            // TODO: implement exit animation if the scroller isn't already off the screen
            finish();
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

    private LoaderCallbacks<List<ContactInteraction>> mLoaderInteractionsCallbacks =
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
                            args.getStringArray(KEY_LOADER_EXTRA_SMS_PHONES),
                            MAX_SMS_RETRIEVE);
                    break;
            }
            return loader;
        }

        @Override
        public void onLoadFinished(Loader<List<ContactInteraction>> loader,
                List<ContactInteraction> data) {
            if (mRecentLoaderResults == null) {
                mRecentLoaderResults = new HashMap<Integer, List<ContactInteraction>>();
            }
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
        List<ContactInteraction> allInteractions = new ArrayList<>();
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

        if (allInteractions.size() > 0) {
            mRecentCard.initialize(contactInteractionsToEntries(allInteractions),
                    /* numInitialVisibleEntries = */ MIN_NUM_COLLAPSED_RECENT_ENTRIES_SHOWN,
                    /* isExpanded = */ false,
                    /* themeColor = */ 0);
            mRecentCard.setVisibility(View.VISIBLE);
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
     * Applies the theme color as extracted in
     * {@link extractAndApplyTintFromPhotoViewAsynchonously()} if available. If the color is not
     * available, store a reference to the drawable to tint when a color becomes available.
     */
    private Drawable applyThemeColorIfAvailable(Drawable drawable) {
        if (mColorFilter != null) {
            drawable.setColorFilter(mColorFilter);
        } else {
            mDrawablesToTint.add(drawable);
        }
        return drawable;
    }
}
