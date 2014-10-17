/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.contacts.common.list;

import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPresenceIconUtil;
import com.android.contacts.common.ContactStatusUtil;
import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.R;

import java.util.ArrayList;

/**
 * Arranges contacts favorites according to provided {@link DisplayType}.
 * Also allows for a configurable number of columns and {@link DisplayType}
 */
public class ContactTileAdapter extends BaseAdapter {
    private static final String TAG = ContactTileAdapter.class.getSimpleName();

    private DisplayType mDisplayType;
    private ContactTileView.Listener mListener;
    private Context mContext;
    private Resources mResources;
    protected Cursor mContactCursor = null;
    private ContactPhotoManager mPhotoManager;
    protected int mNumFrequents;

    /**
     * Index of the first NON starred contact in the {@link Cursor}
     * Only valid when {@link DisplayType#STREQUENT} is true
     */
    private int mDividerPosition;
    protected int mColumnCount;
    private int mStarredIndex;

    protected int mIdIndex;
    protected int mLookupIndex;
    protected int mPhotoUriIndex;
    protected int mNameIndex;
    protected int mPresenceIndex;
    protected int mStatusIndex;

    private boolean mIsQuickContactEnabled = false;
    private final int mPaddingInPixels;
    private final int mWhitespaceStartEnd;

    /**
     * Configures the adapter to filter and display contacts using different view types.
     * TODO: Create Uris to support getting Starred_only and Frequent_only cursors.
     */
    public enum DisplayType {
        /**
         * Displays a mixed view type of starred and frequent contacts
         */
        STREQUENT,

        /**
         * Display only starred contacts
         */
        STARRED_ONLY,

        /**
         * Display only most frequently contacted
         */
        FREQUENT_ONLY,

        /**
         * Display all contacts from a group in the cursor
         * Use {@link com.android.contacts.GroupMemberLoader}
         * when passing {@link Cursor} into loadFromCusor method.
         *
         * Group member logic has been moved into GroupMemberTileAdapter.  This constant is still
         * needed by calling classes.
         */
        GROUP_MEMBERS
    }

    public ContactTileAdapter(Context context, ContactTileView.Listener listener, int numCols,
            DisplayType displayType) {
        mListener = listener;
        mContext = context;
        mResources = context.getResources();
        mColumnCount = (displayType == DisplayType.FREQUENT_ONLY ? 1 : numCols);
        mDisplayType = displayType;
        mNumFrequents = 0;

        // Converting padding in dips to padding in pixels
        mPaddingInPixels = mContext.getResources()
                .getDimensionPixelSize(R.dimen.contact_tile_divider_padding);
        mWhitespaceStartEnd = mContext.getResources()
                .getDimensionPixelSize(R.dimen.contact_tile_start_end_whitespace);

        bindColumnIndices();
    }

    public void setPhotoLoader(ContactPhotoManager photoLoader) {
        mPhotoManager = photoLoader;
    }

    public void setColumnCount(int columnCount) {
        mColumnCount = columnCount;
    }

    public void setDisplayType(DisplayType displayType) {
        mDisplayType = displayType;
    }

    public void enableQuickContact(boolean enableQuickContact) {
        mIsQuickContactEnabled = enableQuickContact;
    }

    /**
     * Sets the column indices for expected {@link Cursor}
     * based on {@link DisplayType}.
     */
    protected void bindColumnIndices() {
        mIdIndex = ContactTileLoaderFactory.CONTACT_ID;
        mLookupIndex = ContactTileLoaderFactory.LOOKUP_KEY;
        mPhotoUriIndex = ContactTileLoaderFactory.PHOTO_URI;
        mNameIndex = ContactTileLoaderFactory.DISPLAY_NAME;
        mStarredIndex = ContactTileLoaderFactory.STARRED;
        mPresenceIndex = ContactTileLoaderFactory.CONTACT_PRESENCE;
        mStatusIndex = ContactTileLoaderFactory.CONTACT_STATUS;
    }

    private static boolean cursorIsValid(Cursor cursor) {
        return cursor != null && !cursor.isClosed();
    }

    /**
     * Gets the number of frequents from the passed in cursor.
     *
     * This methods is needed so the GroupMemberTileAdapter can override this.
     *
     * @param cursor The cursor to get number of frequents from.
     */
    protected void saveNumFrequentsFromCursor(Cursor cursor) {

        // count the number of frequents
        switch (mDisplayType) {
            case STARRED_ONLY:
                mNumFrequents = 0;
                break;
            case STREQUENT:
                mNumFrequents = cursorIsValid(cursor) ?
                    cursor.getCount() - mDividerPosition : 0;
                break;
            case FREQUENT_ONLY:
                mNumFrequents = cursorIsValid(cursor) ? cursor.getCount() : 0;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized DisplayType " + mDisplayType);
        }
    }

    /**
     * Creates {@link ContactTileView}s for each item in {@link Cursor}.
     *
     * Else use {@link ContactTileLoaderFactory}
     */
    public void setContactCursor(Cursor cursor) {
        mContactCursor = cursor;
        mDividerPosition = getDividerPosition(cursor);

        saveNumFrequentsFromCursor(cursor);

        // cause a refresh of any views that rely on this data
        notifyDataSetChanged();
    }

    /**
     * Iterates over the {@link Cursor}
     * Returns position of the first NON Starred Contact
     * Returns -1 if {@link DisplayType#STARRED_ONLY}
     * Returns 0 if {@link DisplayType#FREQUENT_ONLY}
     */
    protected int getDividerPosition(Cursor cursor) {
        switch (mDisplayType) {
            case STREQUENT:
                if (!cursorIsValid(cursor)) {
                    return 0;
                }
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    if (cursor.getInt(mStarredIndex) == 0) {
                        return cursor.getPosition();
                    }
                }

                // There are not NON Starred contacts in cursor
                // Set divider positon to end
                return cursor.getCount();
            case STARRED_ONLY:
                // There is no divider
                return -1;
            case FREQUENT_ONLY:
                // Divider is first
                return 0;
            default:
                throw new IllegalStateException("Unrecognized DisplayType " + mDisplayType);
        }
    }

    protected ContactEntry createContactEntryFromCursor(Cursor cursor, int position) {
        // If the loader was canceled we will be given a null cursor.
        // In that case, show an empty list of contacts.
        if (!cursorIsValid(cursor) || cursor.getCount() <= position) {
            return null;
        }

        cursor.moveToPosition(position);
        long id = cursor.getLong(mIdIndex);
        String photoUri = cursor.getString(mPhotoUriIndex);
        String lookupKey = cursor.getString(mLookupIndex);

        ContactEntry contact = new ContactEntry();
        String name = cursor.getString(mNameIndex);
        contact.name = (name != null) ? name : mResources.getString(R.string.missing_name);
        contact.status = cursor.getString(mStatusIndex);
        contact.photoUri = (photoUri != null ? Uri.parse(photoUri) : null);
        contact.lookupKey = lookupKey;
        contact.lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), id);
        contact.isFavorite = cursor.getInt(mStarredIndex) > 0;

        // Set presence icon and status message
        Drawable icon = null;
        int presence = 0;
        if (!cursor.isNull(mPresenceIndex)) {
            presence = cursor.getInt(mPresenceIndex);
            icon = ContactPresenceIconUtil.getPresenceIcon(mContext, presence);
        }
        contact.presenceIcon = icon;

        String statusMessage = null;
        if (mStatusIndex != 0 && !cursor.isNull(mStatusIndex)) {
            statusMessage = cursor.getString(mStatusIndex);
        }
        // If there is no status message from the contact, but there was a presence value,
        // then use the default status message string
        if (statusMessage == null && presence != 0) {
            statusMessage = ContactStatusUtil.getStatusString(mContext, presence);
        }
        contact.status = statusMessage;

        return contact;
    }

    /**
     * Returns the number of frequents that will be displayed in the list.
     */
    public int getNumFrequents() {
        return mNumFrequents;
    }

    @Override
    public int getCount() {
        if (!cursorIsValid(mContactCursor)) {
            return 0;
        }

        switch (mDisplayType) {
            case STARRED_ONLY:
                return getRowCount(mContactCursor.getCount());
            case STREQUENT:
                // Takes numbers of rows the Starred Contacts Occupy
                int starredRowCount = getRowCount(mDividerPosition);

                // Compute the frequent row count which is 1 plus the number of frequents
                // (to account for the divider) or 0 if there are no frequents.
                int frequentRowCount = mNumFrequents == 0 ? 0 : mNumFrequents + 1;

                // Return the number of starred plus frequent rows
                return starredRowCount + frequentRowCount;
            case FREQUENT_ONLY:
                // Number of frequent contacts
                return mContactCursor.getCount();
            default:
                throw new IllegalArgumentException("Unrecognized DisplayType " + mDisplayType);
        }
    }

    /**
     * Returns the number of rows required to show the provided number of entries
     * with the current number of columns.
     */
    protected int getRowCount(int entryCount) {
        return entryCount == 0 ? 0 : ((entryCount - 1) / mColumnCount) + 1;
    }

    public int getColumnCount() {
        return mColumnCount;
    }

    /**
     * Returns an ArrayList of the {@link ContactEntry}s that are to appear
     * on the row for the given position.
     */
    @Override
    public ArrayList<ContactEntry> getItem(int position) {
        ArrayList<ContactEntry> resultList = new ArrayList<ContactEntry>(mColumnCount);
        int contactIndex = position * mColumnCount;

        switch (mDisplayType) {
            case FREQUENT_ONLY:
                resultList.add(createContactEntryFromCursor(mContactCursor, position));
                break;
            case STARRED_ONLY:
                for (int columnCounter = 0; columnCounter < mColumnCount; columnCounter++) {
                    resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
                    contactIndex++;
                }
                break;
            case STREQUENT:
                if (position < getRowCount(mDividerPosition)) {
                    for (int columnCounter = 0; columnCounter < mColumnCount &&
                            contactIndex != mDividerPosition; columnCounter++) {
                        resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
                        contactIndex++;
                    }
                } else {
                    /*
                     * Current position minus how many rows are before the divider and
                     * Minus 1 for the divider itself provides the relative index of the frequent
                     * contact being displayed. Then add the dividerPostion to give the offset
                     * into the contacts cursor to get the absoulte index.
                     */
                    contactIndex = position - getRowCount(mDividerPosition) - 1 + mDividerPosition;
                    resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
                }
                break;
            default:
                throw new IllegalStateException("Unrecognized DisplayType " + mDisplayType);
        }
        return resultList;
    }

    @Override
    public long getItemId(int position) {
        // As we show several selectable items for each ListView row,
        // we can not determine a stable id. But as we don't rely on ListView's selection,
        // this should not be a problem.
        return position;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return (mDisplayType != DisplayType.STREQUENT);
    }

    @Override
    public boolean isEnabled(int position) {
        return position != getRowCount(mDividerPosition);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int itemViewType = getItemViewType(position);

        if (itemViewType == ViewTypes.DIVIDER) {
            // Checking For Divider First so not to cast convertView
            final TextView textView = (TextView) (convertView == null ? getDivider() : convertView);
            setDividerPadding(textView, position == 0);
            return textView;
        }

        ContactTileRow contactTileRowView = (ContactTileRow) convertView;
        ArrayList<ContactEntry> contactList = getItem(position);

        if (contactTileRowView == null) {
            // Creating new row if needed
            contactTileRowView = new ContactTileRow(mContext, itemViewType);
        }

        contactTileRowView.configureRow(contactList, position == getCount() - 1);
        return contactTileRowView;
    }

    /**
     * Divider uses a list_seperator.xml along with text to denote
     * the most frequently contacted contacts.
     */
    private TextView getDivider() {
        return MoreContactUtils.createHeaderView(mContext, R.string.favoritesFrequentContacted);
    }

    private void setDividerPadding(TextView headerTextView, boolean isFirstRow) {
        MoreContactUtils.setHeaderViewBottomPadding(mContext, headerTextView, isFirstRow);
    }

    private int getLayoutResourceId(int viewType) {
        switch (viewType) {
            case ViewTypes.STARRED:
                return mIsQuickContactEnabled ?
                        R.layout.contact_tile_starred_quick_contact : R.layout.contact_tile_starred;
            case ViewTypes.FREQUENT:
                return R.layout.contact_tile_frequent;
            default:
                throw new IllegalArgumentException("Unrecognized viewType " + viewType);
        }
    }
    @Override
    public int getViewTypeCount() {
        return ViewTypes.COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        /*
         * Returns view type based on {@link DisplayType}.
         * {@link DisplayType#STARRED_ONLY} and {@link DisplayType#GROUP_MEMBERS}
         * are {@link ViewTypes#STARRED}.
         * {@link DisplayType#FREQUENT_ONLY} is {@link ViewTypes#FREQUENT}.
         * {@link DisplayType#STREQUENT} mixes both {@link ViewTypes}
         * and also adds in {@link ViewTypes#DIVIDER}.
         */
        switch (mDisplayType) {
            case STREQUENT:
                if (position < getRowCount(mDividerPosition)) {
                    return ViewTypes.STARRED;
                } else if (position == getRowCount(mDividerPosition)) {
                    return ViewTypes.DIVIDER;
                } else {
                    return ViewTypes.FREQUENT;
                }
            case STARRED_ONLY:
                return ViewTypes.STARRED;
            case FREQUENT_ONLY:
                return ViewTypes.FREQUENT;
            default:
                throw new IllegalStateException("Unrecognized DisplayType " + mDisplayType);
        }
    }

    /**
     * Returns the "frequent header" position. Only available when STREQUENT or
     * STREQUENT_PHONE_ONLY is used for its display type.
     */
    public int getFrequentHeaderPosition() {
        return getRowCount(mDividerPosition);
    }

    /**
     * Acts as a row item composed of {@link ContactTileView}
     *
     * TODO: FREQUENT doesn't really need it.  Just let {@link #getView} return
     */
    private class ContactTileRow extends FrameLayout {
        private int mItemViewType;
        private int mLayoutResId;

        public ContactTileRow(Context context, int itemViewType) {
            super(context);
            mItemViewType = itemViewType;
            mLayoutResId = getLayoutResourceId(mItemViewType);

            // Remove row (but not children) from accessibility node tree.
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /**
         * Configures the row to add {@link ContactEntry}s information to the views
         */
        public void configureRow(ArrayList<ContactEntry> list, boolean isLastRow) {
            int columnCount = mItemViewType == ViewTypes.FREQUENT ? 1 : mColumnCount;

            // Adding tiles to row and filling in contact information
            for (int columnCounter = 0; columnCounter < columnCount; columnCounter++) {
                ContactEntry entry =
                        columnCounter < list.size() ? list.get(columnCounter) : null;
                addTileFromEntry(entry, columnCounter, isLastRow);
            }
        }

        private void addTileFromEntry(ContactEntry entry, int childIndex, boolean isLastRow) {
            final ContactTileView contactTile;

            if (getChildCount() <= childIndex) {
                contactTile = (ContactTileView) inflate(mContext, mLayoutResId, null);
                // Note: the layoutparam set here is only actually used for FREQUENT.
                // We override onMeasure() for STARRED and we don't care the layout param there.
                Resources resources = mContext.getResources();
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(
                        mWhitespaceStartEnd,
                        0,
                        mWhitespaceStartEnd,
                        0);
                contactTile.setLayoutParams(params);
                contactTile.setPhotoManager(mPhotoManager);
                contactTile.setListener(mListener);
                addView(contactTile);
            } else {
                contactTile = (ContactTileView) getChildAt(childIndex);
            }
            contactTile.loadFromContact(entry);

            switch (mItemViewType) {
                case ViewTypes.STARRED:
                    // Set padding between tiles. Divide mPaddingInPixels between left and right
                    // tiles as evenly as possible.
                    contactTile.setPaddingRelative(
                            (mPaddingInPixels + 1) / 2, 0,
                            mPaddingInPixels
                            / 2, 0);
                    break;
                case ViewTypes.FREQUENT:
                    contactTile.setHorizontalDividerVisibility(
                            isLastRow ? View.GONE : View.VISIBLE);
                    break;
                default:
                    break;
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            switch (mItemViewType) {
                case ViewTypes.STARRED:
                    onLayoutForTiles();
                    return;
                default:
                    super.onLayout(changed, left, top, right, bottom);
                    return;
            }
        }

        private void onLayoutForTiles() {
            final int count = getChildCount();

            // Amount of margin needed on the left is based on difference between offset and padding
            int childLeft = mWhitespaceStartEnd - (mPaddingInPixels + 1) / 2;

            // Just line up children horizontally.
            for (int i = 0; i < count; i++) {
                final int rtlAdjustedIndex = isLayoutRtl() ? count - i - 1 : i;
                final View child = getChildAt(rtlAdjustedIndex);

                // Note MeasuredWidth includes the padding.
                final int childWidth = child.getMeasuredWidth();
                child.layout(childLeft, 0, childLeft + childWidth, child.getMeasuredHeight());
                childLeft += childWidth;
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            switch (mItemViewType) {
                case ViewTypes.STARRED:
                    onMeasureForTiles(widthMeasureSpec);
                    return;
                default:
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    return;
            }
        }

        private void onMeasureForTiles(int widthMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);

            final int childCount = getChildCount();
            if (childCount == 0) {
                // Just in case...
                setMeasuredDimension(width, 0);
                return;
            }

            // 1. Calculate image size.
            //      = ([total width] - [total whitespace]) / [child count]
            //
            // 2. Set it to width/height of each children.
            //    If we have a remainder, some tiles will have 1 pixel larger width than its height.
            //
            // 3. Set the dimensions of itself.
            //    Let width = given width.
            //    Let height = wrap content.

            final int totalWhitespaceInPixels = (mColumnCount - 1) * mPaddingInPixels
                    + mWhitespaceStartEnd * 2;

            // Preferred width / height for images (excluding the padding).
            // The actual width may be 1 pixel larger than this if we have a remainder.
            final int imageSize = (width - totalWhitespaceInPixels) / mColumnCount;
            final int remainder = width - (imageSize * mColumnCount) - totalWhitespaceInPixels;

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final int childWidth = imageSize + child.getPaddingRight() + child.getPaddingLeft()
                        // Compensate for the remainder
                        + (i < remainder ? 1 : 0);

                child.measure(
                        MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                        );
            }
            setMeasuredDimension(width, getChildAt(0).getMeasuredHeight());
        }
    }

    protected static class ViewTypes {
        public static final int COUNT = 4;
        public static final int STARRED = 0;
        public static final int DIVIDER = 1;
        public static final int FREQUENT = 2;
    }
}
