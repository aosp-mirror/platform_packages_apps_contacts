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
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.common.Collapser;
import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.model.dataitem.EmailDataItem;
import com.android.contacts.common.model.dataitem.ImDataItem;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.DataStatus;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.quickcontact.ExpandingEntryCardView.Entry;
import com.android.contacts.util.ImageViewDrawableSetter;
import com.android.contacts.util.SchedulingUtils;
import com.android.contacts.common.util.StopWatch;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.ArrayList;
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

    private View mPhotoContainer;

    private ImageView mPhotoView;
    private ImageView mEditOrAddContactImage;
    private ImageView mStarImage;
    private ExpandingEntryCardView mCommunicationCard;

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

    final OnClickListener mEditContactClickHandler = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(Intent.ACTION_EDIT, mLookupUri);
            mContactLoader.cacheResult();
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            startActivity(intent);
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

        mEditOrAddContactImage = (ImageView) findViewById(R.id.contact_edit_image);
        mStarImage = (ImageView) findViewById(R.id.quickcontact_star_button);
        mCommunicationCard = (ExpandingEntryCardView) findViewById(R.id.communication_card);
        mCommunicationCard.setTitle(getResources().getString(R.string.communication_card_title));

        mEditOrAddContactImage.setOnClickListener(mEditContactClickHandler);
        mCommunicationCard.setOnClickListener(mEntryClickHandler);

        // find and prepare correct header view
        mPhotoContainer = findViewById(R.id.photo_container);

        setHeaderNameText(R.id.name, R.string.missing_name);

        mPhotoView = (ImageView) mPhotoContainer.findViewById(R.id.photo);
        mPhotoView.setOnClickListener(mEditContactClickHandler);

        mStopWatch.lap("v"); // view initialized

        // TODO: Use some sort of fading in for the layout and content during animation
        /*SchedulingUtils.doAfterLayout(mFloatingLayout, new Runnable() {
            @Override
            public void run() {
                mFloatingLayout.fadeInBackground();
            }
        });*/

        mStopWatch.lap("cf"); // onCreate finished
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

        // List of Entry that makes up the ExpandingEntryCardView
        final List<Entry> entries = new ArrayList<>();
        // All the mime-types to add.
        final Set<String> containedTypes = new HashSet<String>(mActions.keySet());
        mSortedActionMimeTypes.clear();
        // First, add LEADING_MIMETYPES, which are most common.
        for (String mimeType : LEADING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                mSortedActionMimeTypes.add(mimeType);
                containedTypes.remove(mimeType);
                entries.addAll(actionsToEntries(mActions.get(mimeType)));
            }
        }

        // Add all the remaining ones that are not TRAILING
        for (String mimeType : containedTypes.toArray(new String[containedTypes.size()])) {
            if (!TRAILING_MIMETYPES.contains(mimeType)) {
                mSortedActionMimeTypes.add(mimeType);
                containedTypes.remove(mimeType);
                entries.addAll(actionsToEntries(mActions.get(mimeType)));
            }
        }

        // Then, add TRAILING_MIMETYPES, which are least common.
        for (String mimeType : TRAILING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                containedTypes.remove(mimeType);
                mSortedActionMimeTypes.add(mimeType);
                entries.addAll(actionsToEntries(mActions.get(mimeType)));
            }
        }
        mCommunicationCard.initialize(entries, /* numInitialVisibleEntries = */ 1,
                /* isExpanded = */ false, /* themeColor = */ 0);

        final boolean hasData = !mSortedActionMimeTypes.isEmpty();
        mCommunicationCard.setVisibility(hasData ? View.VISIBLE: View.GONE);
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
            mEditOrAddContactImage.setImageResource(R.drawable.ic_add_contact_holo_dark);
            mEditOrAddContactImage.setOnClickListener(mAddToContactsClickHandler);
            mPhotoView.setOnClickListener(mAddToContactsClickHandler);
        } else {
            mEditOrAddContactImage.setImageResource(R.drawable.ic_menu_compose_holo_dark);
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
            entries.add(new Entry(ResolveCache.getInstance(this).getIcon(action),
                    action.getMimeType(), action.getSubtitle().toString(),
                    action.getBody().toString(), action.getIntent(), /* isEditable= */ false));
        }
        return entries;
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
            // TODO: Add animation here
            /*SchedulingUtils.doAfterLayout(mFloatingLayout, new Runnable() {
                @Override
                public void run() {
                    mFloatingLayout.showContent(new Runnable() {
                        @Override
                        public void run() {
                            mContactLoader.upgradeToFullContact();
                        }
                    });
                }
            });*/
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
}
