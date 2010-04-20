// Copyright 2010 Google Inc. All Rights Reserved.

package com.android.contacts.list;

import com.android.contacts.ContactListItemView;
import com.android.contacts.ContactPresenceIconUtil;
import com.android.contacts.ContactsListActivity;
import com.android.contacts.ContactsSectionIndexer;
import com.android.contacts.PinnedHeaderListView;
import com.android.contacts.R;
import com.android.contacts.ContactsListActivity.ContactListItemCache;
import com.android.contacts.ContactsListActivity.PinnedHeaderCache;
import com.android.contacts.TextHighlightingAnimation.TextWithHighlighting;

import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;

public class ContactItemListAdapter extends CursorAdapter
        implements SectionIndexer, OnScrollListener, PinnedHeaderListView.PinnedHeaderAdapter {

    private final ContactsListActivity contactsListActivity;
    private SectionIndexer mIndexer;
    private boolean mLoading = true;
    private CharSequence mUnknownNameText;
    private boolean mDisplayPhotos = false;
    private boolean mDisplayCallButton = false;
    private boolean mDisplayAdditionalData = true;
    private int mFrequentSeparatorPos = ListView.INVALID_POSITION;
    private boolean mDisplaySectionHeaders = true;

    public ContactItemListAdapter(ContactsListActivity contactsListActivity) {
        super(contactsListActivity, null, false);
        this.contactsListActivity = contactsListActivity;

        mUnknownNameText = contactsListActivity.getText(android.R.string.unknownName);
        switch (contactsListActivity.mMode) {
            case ContactsListActivity.MODE_LEGACY_PICK_POSTAL:
            case ContactsListActivity.MODE_PICK_POSTAL:
            case ContactsListActivity.MODE_LEGACY_PICK_PHONE:
            case ContactsListActivity.MODE_PICK_PHONE:
            case ContactsListActivity.MODE_STREQUENT:
            case ContactsListActivity.MODE_FREQUENT:
                mDisplaySectionHeaders = false;
                break;
        }

        if (contactsListActivity.mSearchMode) {
            mDisplaySectionHeaders = false;
        }

        // Do not display the second line of text if in a specific SEARCH query mode, usually for
        // matching a specific E-mail or phone number. Any contact details
        // shown would be identical, and columns might not even be present
        // in the returned cursor.
        if (contactsListActivity.mMode != ContactsListActivity.MODE_QUERY_PICK_PHONE
                && contactsListActivity.mQueryMode != ContactsListActivity.QUERY_MODE_NONE) {
            mDisplayAdditionalData = false;
        }

        if ((contactsListActivity.mMode & ContactsListActivity.MODE_MASK_NO_DATA) ==
                ContactsListActivity.MODE_MASK_NO_DATA) {
            mDisplayAdditionalData = false;
        }

        if ((contactsListActivity.mMode & ContactsListActivity.MODE_MASK_SHOW_CALL_BUTTON) ==
                ContactsListActivity.MODE_MASK_SHOW_CALL_BUTTON) {
            mDisplayCallButton = true;
        }

        if ((contactsListActivity.mMode & ContactsListActivity.MODE_MASK_SHOW_PHOTOS) ==
                ContactsListActivity.MODE_MASK_SHOW_PHOTOS) {
            mDisplayPhotos = true;
        }
    }

    public boolean getDisplaySectionHeadersEnabled() {
        return mDisplaySectionHeaders;
    }

    /**
     * Callback on the UI thread when the content observer on the backing cursor fires.
     * Instead of calling requery we need to do an async query so that the requery doesn't
     * block the UI thread for a long time.
     */
    @Override
    public void onContentChanged() {
        CharSequence constraint = contactsListActivity.getTextFilter();
        if (!TextUtils.isEmpty(constraint)) {
            // Reset the filter state then start an async filter operation
            Filter filter = getFilter();
            filter.filter(constraint);
        } else {
            // Start an async query
            contactsListActivity.startQuery();
        }
    }

    public void setLoading(boolean loading) {
        mLoading = loading;
    }

    @Override
    public boolean isEmpty() {
        if (contactsListActivity.mProviderStatus != ProviderStatus.STATUS_NORMAL) {
            return true;
        }

        if (contactsListActivity.mSearchMode) {
            return TextUtils.isEmpty(contactsListActivity.getTextFilter());
        } else if ((contactsListActivity.mMode & ContactsListActivity.MODE_MASK_CREATE_NEW) ==
                ContactsListActivity.MODE_MASK_CREATE_NEW) {
            // This mode mask adds a header and we always want it to show up, even
            // if the list is empty, so always claim the list is not empty.
            return false;
        } else {
            if (mCursor == null || mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 && (contactsListActivity.mShowNumberOfContacts ||
                (contactsListActivity.mMode & ContactsListActivity.MODE_MASK_CREATE_NEW) != 0)) {
            return IGNORE_ITEM_VIEW_TYPE;
        }

        if (isSearchAllContactsItemPosition(position)) {
            return IGNORE_ITEM_VIEW_TYPE;
        }

        if (getSeparatorId(position) != 0) {
            // We don't want the separator view to be recycled.
            return IGNORE_ITEM_VIEW_TYPE;
        }
        if (contactsListActivity.mMode == ContactsListActivity.MODE_PICK_MULTIPLE_PHONES
                && position < contactsListActivity.mPhoneNumberAdapter.getCount()) {
            return contactsListActivity.mPhoneNumberAdapter.getItemViewType(position);
        }
        return super.getItemViewType(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (!mDataValid) {
            throw new IllegalStateException(
                    "this should only be called when the cursor is valid");
        }

        // handle the total contacts item
        if (position == 0 && contactsListActivity.mShowNumberOfContacts) {
            return getTotalContactCountView(parent);
        }

        if (position == 0
                && (contactsListActivity.mMode & ContactsListActivity.MODE_MASK_CREATE_NEW) != 0) {
            // Add the header for creating a new contact
            return contactsListActivity.getLayoutInflater().inflate(R.layout.create_new_contact,
                    parent, false);
        }

        if (isSearchAllContactsItemPosition(position)) {
            return contactsListActivity.getLayoutInflater().
                    inflate(R.layout.contacts_list_search_all_item, parent, false);
        }

        // Handle the separator specially
        int separatorId = getSeparatorId(position);
        if (separatorId != 0) {
            TextView view = (TextView) contactsListActivity.getLayoutInflater().
                    inflate(R.layout.list_separator, parent, false);
            view.setText(separatorId);
            return view;
        }

        // Check whether this view should be retrieved from mPhoneNumberAdapter
        if (contactsListActivity.mMode == ContactsListActivity.MODE_PICK_MULTIPLE_PHONES
                && position < contactsListActivity.mPhoneNumberAdapter.getCount()) {
            return contactsListActivity.mPhoneNumberAdapter.getView(position, convertView, parent);
        }

        int realPosition = getRealPosition(position);
        if (!mCursor.moveToPosition(realPosition)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }

        boolean newView;
        View v;
        if (convertView == null || convertView.getTag() == null) {
            newView = true;
            v = newView(mContext, mCursor, parent);
        } else {
            newView = false;
            v = convertView;
        }
        bindView(v, mContext, mCursor);
        bindSectionHeader(v, realPosition, mDisplaySectionHeaders);
        return v;
    }

    private View getTotalContactCountView(ViewGroup parent) {
        final LayoutInflater inflater = contactsListActivity.getLayoutInflater();
        View view = inflater.inflate(R.layout.total_contacts, parent, false);

        TextView totalContacts = (TextView) view.findViewById(R.id.totalContactsText);

        String text;
        int count = getRealCount();

        if (contactsListActivity.mSearchMode
                && !TextUtils.isEmpty(contactsListActivity.getTextFilter())) {
            text = contactsListActivity.getQuantityText(count, R.string.listFoundAllContactsZero,
                    R.plurals.searchFoundContacts);
        } else {
            if (contactsListActivity.mDisplayOnlyPhones) {
                text = contactsListActivity.getQuantityText(count,
                        R.string.listTotalPhoneContactsZero, R.plurals.listTotalPhoneContacts);
            } else {
                text = contactsListActivity.getQuantityText(count,
                        R.string.listTotalAllContactsZero, R.plurals.listTotalAllContacts);
            }
        }
        totalContacts.setText(text);
        return view;
    }

    public boolean isSearchAllContactsItemPosition(int position) {
        return contactsListActivity.mSearchMode && contactsListActivity.mMode != ContactsListActivity.MODE_PICK_MULTIPLE_PHONES && position == getCount() - 1;
    }

    private int getSeparatorId(int position) {
        int separatorId = 0;
        if (position == mFrequentSeparatorPos) {
            separatorId = R.string.favoritesFrquentSeparator;
        }
        return separatorId;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setOnCallButtonClickListener(contactsListActivity);
        view.setOnCheckBoxClickListener(contactsListActivity.mCheckBoxClickerListener);
        view.setTag(new ContactsListActivity.ContactListItemCache());
        return view;
    }

    @Override
    public void bindView(View itemView, Context context, Cursor cursor) {
        final ContactListItemView view = (ContactListItemView)itemView;
        final ContactListItemCache cache = (ContactListItemCache) view.getTag();

        int typeColumnIndex;
        int dataColumnIndex;
        int labelColumnIndex;
        int defaultType;
        int nameColumnIndex;
        int phoneticNameColumnIndex;
        int photoColumnIndex = ContactsListActivity.SUMMARY_PHOTO_ID_COLUMN_INDEX;
        boolean displayAdditionalData = mDisplayAdditionalData;
        boolean highlightingEnabled = false;
        switch(contactsListActivity.mMode) {
            case ContactsListActivity.MODE_PICK_MULTIPLE_PHONES:
            case ContactsListActivity.MODE_PICK_PHONE:
            case ContactsListActivity.MODE_LEGACY_PICK_PHONE:
            case ContactsListActivity.MODE_QUERY_PICK_PHONE: {
                nameColumnIndex = ContactsListActivity.PHONE_DISPLAY_NAME_COLUMN_INDEX;
                phoneticNameColumnIndex = -1;
                dataColumnIndex = ContactsListActivity.PHONE_NUMBER_COLUMN_INDEX;
                typeColumnIndex = ContactsListActivity.PHONE_TYPE_COLUMN_INDEX;
                labelColumnIndex = ContactsListActivity.PHONE_LABEL_COLUMN_INDEX;
                defaultType = Phone.TYPE_HOME;
                photoColumnIndex = ContactsListActivity.PHONE_PHOTO_ID_COLUMN_INDEX;
                break;
            }
            case ContactsListActivity.MODE_PICK_POSTAL:
            case ContactsListActivity.MODE_LEGACY_PICK_POSTAL: {
                nameColumnIndex = ContactsListActivity.POSTAL_DISPLAY_NAME_COLUMN_INDEX;
                phoneticNameColumnIndex = -1;
                dataColumnIndex = ContactsListActivity.POSTAL_ADDRESS_COLUMN_INDEX;
                typeColumnIndex = ContactsListActivity.POSTAL_TYPE_COLUMN_INDEX;
                labelColumnIndex = ContactsListActivity.POSTAL_LABEL_COLUMN_INDEX;
                defaultType = StructuredPostal.TYPE_HOME;
                break;
            }
            default: {
                nameColumnIndex = contactsListActivity.getSummaryDisplayNameColumnIndex();
                if (contactsListActivity.mMode == ContactsListActivity.MODE_LEGACY_PICK_PERSON
                        || contactsListActivity.mMode ==
                            ContactsListActivity.MODE_LEGACY_PICK_OR_CREATE_PERSON) {
                    phoneticNameColumnIndex = -1;
                } else {
                    phoneticNameColumnIndex =
                        ContactsListActivity.SUMMARY_PHONETIC_NAME_COLUMN_INDEX;
                }
                dataColumnIndex = -1;
                typeColumnIndex = -1;
                labelColumnIndex = -1;
                defaultType = Phone.TYPE_HOME;
                displayAdditionalData = false;
                highlightingEnabled = contactsListActivity.mHighlightWhenScrolling
                        && contactsListActivity.mMode != ContactsListActivity.MODE_STREQUENT;
            }
        }

        if (contactsListActivity.mMode == ContactsListActivity.MODE_PICK_MULTIPLE_PHONES) {
            cache.phoneId =
                Long.valueOf(cursor.getLong(ContactsListActivity.PHONE_ID_COLUMN_INDEX));
            CheckBox checkBox = view.getCheckBoxView();
            checkBox.setChecked(contactsListActivity.mUserSelection.isSelected(cache.phoneId));
            checkBox.setTag(cache);
            int color = contactsListActivity.getChipColor(cursor
                    .getLong(ContactsListActivity.PHONE_CONTACT_ID_COLUMN_INDEX));
            view.getChipView().setBackgroundResource(color);
        }

        // Set the name
        cursor.copyStringToBuffer(nameColumnIndex, cache.nameBuffer);
        TextView nameView = view.getNameTextView();
        int size = cache.nameBuffer.sizeCopied;
        if (size != 0) {
            if (highlightingEnabled) {
                if (cache.textWithHighlighting == null) {
                    cache.textWithHighlighting =
                            contactsListActivity.mHighlightingAnimation.createTextWithHighlighting();
                }
                buildDisplayNameWithHighlighting(nameView, cursor, cache.nameBuffer,
                        cache.highlightedTextBuffer, cache.textWithHighlighting);
            } else {
                nameView.setText(cache.nameBuffer.data, 0, size);
            }
        } else {
            nameView.setText(mUnknownNameText);
        }

        // Make the call button visible if requested.
        if (mDisplayCallButton
                && cursor.getColumnCount() > ContactsListActivity.SUMMARY_HAS_PHONE_COLUMN_INDEX
                && cursor.getInt(ContactsListActivity.SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0) {
            int pos = cursor.getPosition();
            view.showCallButton(android.R.id.button1, pos);
        } else {
            view.hideCallButton();
        }

        // Set the photo, if requested
        if (mDisplayPhotos) {
            boolean useQuickContact = (contactsListActivity.mMode
                    & ContactsListActivity.MODE_MASK_DISABLE_QUIKCCONTACT) == 0;

            long photoId = 0;
            if (!cursor.isNull(photoColumnIndex)) {
                photoId = cursor.getLong(photoColumnIndex);
            }

            ImageView viewToUse;
            if (useQuickContact) {
                // Build soft lookup reference
                final long contactId =
                        cursor.getLong(ContactsListActivity.SUMMARY_ID_COLUMN_INDEX);
                final String lookupKey =
                        cursor.getString(ContactsListActivity.SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                QuickContactBadge quickContact = view.getQuickContact();
                quickContact.assignContactUri(Contacts.getLookupUri(contactId, lookupKey));
                viewToUse = quickContact;
            } else {
                viewToUse = view.getPhotoView();
            }

            final int position = cursor.getPosition();
            contactsListActivity.mPhotoLoader.loadPhoto(viewToUse, photoId);
        }

        if ((contactsListActivity.mMode & ContactsListActivity.MODE_MASK_NO_PRESENCE) == 0) {
            // Set the proper icon (star or presence or nothing)
            int serverStatus;
            if (!cursor.isNull(ContactsListActivity.SUMMARY_PRESENCE_STATUS_COLUMN_INDEX)) {
                serverStatus =
                        cursor.getInt(ContactsListActivity.SUMMARY_PRESENCE_STATUS_COLUMN_INDEX);
                Drawable icon = ContactPresenceIconUtil.getPresenceIcon(mContext, serverStatus);
                if (icon != null) {
                    view.setPresence(icon);
                } else {
                    view.setPresence(null);
                }
            } else {
                view.setPresence(null);
            }
        } else {
            view.setPresence(null);
        }

        if (contactsListActivity.mShowSearchSnippets) {
            boolean showSnippet = false;
            String snippetMimeType =
                    cursor.getString(ContactsListActivity.SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX);
            if (Email.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
                String email =
                        cursor.getString(ContactsListActivity.SUMMARY_SNIPPET_DATA1_COLUMN_INDEX);
                if (!TextUtils.isEmpty(email)) {
                    view.setSnippet(email);
                    showSnippet = true;
                }
            } else if (Organization.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
                String company =
                        cursor.getString(ContactsListActivity.SUMMARY_SNIPPET_DATA1_COLUMN_INDEX);
                String title =
                        cursor.getString(ContactsListActivity.SUMMARY_SNIPPET_DATA4_COLUMN_INDEX);
                if (!TextUtils.isEmpty(company)) {
                    if (!TextUtils.isEmpty(title)) {
                        view.setSnippet(company + " / " + title);
                    } else {
                        view.setSnippet(company);
                    }
                    showSnippet = true;
                } else if (!TextUtils.isEmpty(title)) {
                    view.setSnippet(title);
                    showSnippet = true;
                }
            } else if (Nickname.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
                String nickname =
                        cursor.getString(ContactsListActivity.SUMMARY_SNIPPET_DATA1_COLUMN_INDEX);
                if (!TextUtils.isEmpty(nickname)) {
                    view.setSnippet(nickname);
                    showSnippet = true;
                }
            }

            if (!showSnippet) {
                view.setSnippet(null);
            }
        }

        if (!displayAdditionalData) {
            if (phoneticNameColumnIndex != -1) {

                // Set the name
                cursor.copyStringToBuffer(phoneticNameColumnIndex, cache.phoneticNameBuffer);
                int phoneticNameSize = cache.phoneticNameBuffer.sizeCopied;
                if (phoneticNameSize != 0) {
                    view.setLabel(cache.phoneticNameBuffer.data, phoneticNameSize);
                } else {
                    view.setLabel(null);
                }
            } else {
                view.setLabel(null);
            }
            return;
        }

        // Set the data.
        cursor.copyStringToBuffer(dataColumnIndex, cache.dataBuffer);

        size = cache.dataBuffer.sizeCopied;
        view.setData(cache.dataBuffer.data, size);

        // Set the label.
        if (!cursor.isNull(typeColumnIndex)) {
            final int type = cursor.getInt(typeColumnIndex);
            final String label = cursor.getString(labelColumnIndex);

            if (contactsListActivity.mMode == ContactsListActivity.MODE_LEGACY_PICK_POSTAL
                    || contactsListActivity.mMode == ContactsListActivity.MODE_PICK_POSTAL) {
                // TODO cache
                view.setLabel(StructuredPostal.getTypeLabel(context.getResources(), type,
                        label));
            } else {
                // TODO cache
                view.setLabel(Phone.getTypeLabel(context.getResources(), type, label));
            }
        } else {
            view.setLabel(null);
        }
    }

    /**
     * Computes the span of the display name that has highlighted parts and configures
     * the display name text view accordingly.
     */
    private void buildDisplayNameWithHighlighting(TextView textView, Cursor cursor,
            CharArrayBuffer buffer1, CharArrayBuffer buffer2,
            TextWithHighlighting textWithHighlighting) {
        int oppositeDisplayOrderColumnIndex;
        if (contactsListActivity.mDisplayOrder ==
                ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            oppositeDisplayOrderColumnIndex =
                    ContactsListActivity.SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
        } else {
            oppositeDisplayOrderColumnIndex =
                    ContactsListActivity.SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
        }
        cursor.copyStringToBuffer(oppositeDisplayOrderColumnIndex, buffer2);

        textWithHighlighting.setText(buffer1, buffer2);
        textView.setText(textWithHighlighting);
    }

    protected void bindSectionHeader(View itemView, int position, boolean displaySectionHeaders) {
        final ContactListItemView view = (ContactListItemView)itemView;
        final ContactListItemCache cache = (ContactListItemCache) view.getTag();
        if (!displaySectionHeaders) {
            view.setSectionHeader(null);
            view.setDividerVisible(true);
        } else {
            final int section = getSectionForPosition(position);
            if (getPositionForSection(section) == position) {
                String title = (String)mIndexer.getSections()[section];
                view.setSectionHeader(title);
            } else {
                view.setDividerVisible(false);
                view.setSectionHeader(null);
            }

            // move the divider for the last item in a section
            if (getPositionForSection(section + 1) - 1 == position) {
                view.setDividerVisible(false);
            } else {
                view.setDividerVisible(true);
            }
        }
    }

    @Override
    public void changeCursor(Cursor cursor) {
        if (cursor != null) {
            setLoading(false);
        }

        // Get the split between starred and frequent items, if the mode is strequent
        mFrequentSeparatorPos = ListView.INVALID_POSITION;
        int cursorCount = 0;
        if (cursor != null && (cursorCount = cursor.getCount()) > 0
                && contactsListActivity.mMode == ContactsListActivity.MODE_STREQUENT) {
            cursor.move(-1);
            for (int i = 0; cursor.moveToNext(); i++) {
                int starred = cursor.getInt(ContactsListActivity.SUMMARY_STARRED_COLUMN_INDEX);
                if (starred == 0) {
                    if (i > 0) {
                        // Only add the separator when there are starred items present
                        mFrequentSeparatorPos = i;
                    }
                    break;
                }
            }
        }

        if (cursor != null && contactsListActivity.mSearchResultsMode) {
            TextView foundContactsText = (TextView)contactsListActivity
                    .findViewById(R.id.search_results_found);
            String text = contactsListActivity.getQuantityText(cursor.getCount(),
                    R.string.listFoundAllContactsZero, R.plurals.listFoundAllContacts);
            foundContactsText.setText(text);
        }

        if (contactsListActivity.mEmptyView != null && (cursor == null || cursor.getCount() == 0)) {
            contactsListActivity.mEmptyView.show(contactsListActivity.mSearchMode,
                    contactsListActivity.mDisplayOnlyPhones,
                    contactsListActivity.mMode == ContactsListActivity.MODE_STREQUENT
                    || contactsListActivity.mMode == ContactsListActivity.MODE_STARRED,
                    contactsListActivity.mMode == ContactsListActivity.MODE_QUERY
                    || contactsListActivity.mMode == ContactsListActivity.MODE_QUERY_PICK
                    || contactsListActivity.mMode == ContactsListActivity.MODE_QUERY_PICK_PHONE
                    || contactsListActivity.mMode == ContactsListActivity.MODE_QUERY_PICK_TO_VIEW
                    || contactsListActivity.mMode == ContactsListActivity.MODE_QUERY_PICK_TO_EDIT,
                    contactsListActivity.mShortcutAction != null,
                    contactsListActivity.mMode == ContactsListActivity.MODE_PICK_MULTIPLE_PHONES,
                    contactsListActivity.mShowSelectedOnly);
        }

        super.changeCursor(cursor);

        // Update the indexer for the fast scroll widget
        updateIndexer(cursor);

        if (contactsListActivity.mMode == ContactsListActivity.MODE_PICK_MULTIPLE_PHONES) {
            contactsListActivity.updateChipColor(cursor);
        }
    }

    private void updateIndexer(Cursor cursor) {
        if (cursor == null) {
            mIndexer = null;
            return;
        }

        Bundle bundle = cursor.getExtras();
        if (bundle.containsKey(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)) {
            String sections[] =
                bundle.getStringArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
            int counts[] = bundle.getIntArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
            mIndexer = new ContactsSectionIndexer(sections, counts);
        } else {
            mIndexer = null;
        }
    }

    /**
     * Run the query on a helper thread. Beware that this code does not run
     * on the main UI thread!
     */
    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        return contactsListActivity.doFilter(constraint.toString());
    }

    public Object [] getSections() {
        if (mIndexer == null) {
            return new String[] { " " };
        } else {
            return mIndexer.getSections();
        }
    }

    public int getPositionForSection(int sectionIndex) {
        if (mIndexer == null) {
            return -1;
        }

        return mIndexer.getPositionForSection(sectionIndex);
    }

    public int getSectionForPosition(int position) {
        if (mIndexer == null) {
            return -1;
        }

        return mIndexer.getSectionForPosition(position);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return contactsListActivity.mMode != ContactsListActivity.MODE_STARRED
            && !contactsListActivity.mShowNumberOfContacts;
    }

    @Override
    public boolean isEnabled(int position) {
        if (contactsListActivity.mShowNumberOfContacts) {
            if (position == 0) {
                return false;
            }
            position--;
        }
        return position != mFrequentSeparatorPos;
    }

    @Override
    public int getCount() {
        if (!mDataValid) {
            return 0;
        }
        int superCount = super.getCount();

        if (contactsListActivity.mShowNumberOfContacts
                && (contactsListActivity.mSearchMode || superCount > 0)) {
            // We don't want to count this header if it's the only thing visible, so that
            // the empty text will display.
            superCount++;
        }

        if (contactsListActivity.mSearchMode
                && contactsListActivity.mMode != ContactsListActivity.MODE_PICK_MULTIPLE_PHONES) {
            // Last element in the list is the "Find
            superCount++;
        }

        // We do not show the "Create New" button in Search mode
        if ((contactsListActivity.mMode & ContactsListActivity.MODE_MASK_CREATE_NEW) != 0
                && !contactsListActivity.mSearchMode) {
            // Count the "Create new contact" line
            superCount++;
        }

        if (contactsListActivity.mMode == ContactsListActivity.MODE_PICK_MULTIPLE_PHONES) {
            superCount += contactsListActivity.mPhoneNumberAdapter.getCount();
        }

        if (mFrequentSeparatorPos != ListView.INVALID_POSITION) {
            // When showing strequent list, we have an additional list item - the separator.
            return superCount + 1;
        } else {
            return superCount;
        }
    }

    /**
     * Gets the actual count of contacts and excludes all the headers.
     */
    public int getRealCount() {
        return super.getCount();
    }

    private int getRealPosition(int pos) {
        if (contactsListActivity.mShowNumberOfContacts) {
            pos--;
        }

        if ((contactsListActivity.mMode & ContactsListActivity.MODE_MASK_CREATE_NEW) != 0
                && !contactsListActivity.mSearchMode) {
            return pos - 1;
        }

        if (contactsListActivity.mMode == ContactsListActivity.MODE_PICK_MULTIPLE_PHONES) {
            pos -= contactsListActivity.mPhoneNumberAdapter.getCount();
        }

        if (mFrequentSeparatorPos == ListView.INVALID_POSITION) {
            // No separator, identity map
            return pos;
        } else if (pos <= mFrequentSeparatorPos) {
            // Before or at the separator, identity map
            return pos;
        } else {
            // After the separator, remove 1 from the pos to get the real underlying pos
            return pos - 1;
        }
    }

    @Override
    public Object getItem(int pos) {
        if (isSearchAllContactsItemPosition(pos)){
            return null;
        } else {
            int realPosition = getRealPosition(pos);
            if (realPosition < 0) {
                return null;
            }
            return super.getItem(realPosition);
        }
    }

    @Override
    public long getItemId(int pos) {
        if (isSearchAllContactsItemPosition(pos)) {
            return 0;
        }
        int realPosition = getRealPosition(pos);
        if (realPosition < 0) {
            return 0;
        }
        return super.getItemId(realPosition);
    }

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        if (view instanceof PinnedHeaderListView) {
            ((PinnedHeaderListView)view).configureHeaderView(firstVisibleItem);
        }
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (contactsListActivity.mHighlightWhenScrolling) {
            if (scrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                contactsListActivity.mHighlightingAnimation.startHighlighting();
            } else {
                contactsListActivity.mHighlightingAnimation.stopHighlighting();
            }
        }

        if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
            contactsListActivity.mPhotoLoader.pause();
        } else if (mDisplayPhotos) {
            contactsListActivity.mPhotoLoader.resume();
        }
    }

    /**
     * Computes the state of the pinned header.  It can be invisible, fully
     * visible or partially pushed up out of the view.
     */
    public int getPinnedHeaderState(int position) {
        if (mIndexer == null || mCursor == null || mCursor.getCount() == 0) {
            return PINNED_HEADER_GONE;
        }

        int realPosition = getRealPosition(position);
        if (realPosition < 0) {
            return PINNED_HEADER_GONE;
        }

        // The header should get pushed up if the top item shown
        // is the last item in a section for a particular letter.
        int section = getSectionForPosition(realPosition);
        int nextSectionPosition = getPositionForSection(section + 1);
        if (nextSectionPosition != -1 && realPosition == nextSectionPosition - 1) {
            return PINNED_HEADER_PUSHED_UP;
        }

        return PINNED_HEADER_VISIBLE;
    }

    /**
     * Configures the pinned header by setting the appropriate text label
     * and also adjusting color if necessary.  The color needs to be
     * adjusted when the pinned header is being pushed up from the view.
     */
    public void configurePinnedHeader(View header, int position, int alpha) {
        PinnedHeaderCache cache = (PinnedHeaderCache)header.getTag();
        if (cache == null) {
            cache = new ContactsListActivity.PinnedHeaderCache();
            cache.titleView = (TextView)header.findViewById(R.id.header_text);
            cache.textColor = cache.titleView.getTextColors();
            cache.background = header.getBackground();
            header.setTag(cache);
        }

        int realPosition = getRealPosition(position);
        int section = getSectionForPosition(realPosition);

        String title = (String)mIndexer.getSections()[section];
        cache.titleView.setText(title);

        if (alpha == 255) {
            // Opaque: use the default background, and the original text color
            header.setBackgroundDrawable(cache.background);
            cache.titleView.setTextColor(cache.textColor);
        } else {
            // Faded: use a solid color approximation of the background, and
            // a translucent text color
            header.setBackgroundColor(Color.rgb(
                    Color.red(contactsListActivity.mPinnedHeaderBackgroundColor) * alpha / 255,
                    Color.green(contactsListActivity.mPinnedHeaderBackgroundColor) * alpha / 255,
                    Color.blue(contactsListActivity.mPinnedHeaderBackgroundColor) * alpha / 255));

            int textColor = cache.textColor.getDefaultColor();
            cache.titleView.setTextColor(Color.argb(alpha,
                    Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
        }
    }
}