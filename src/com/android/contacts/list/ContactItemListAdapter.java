/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.list;

import com.android.contacts.ContactPresenceIconUtil;
import com.android.contacts.ContactsListActivity;
import com.android.contacts.ContactsSectionIndexer;
import com.android.contacts.R;
import com.android.contacts.widget.TextWithHighlighting;

import android.app.patterns.CursorLoader;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.Cursor;
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
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

@Deprecated
public class ContactItemListAdapter extends ContactEntryListAdapter {

    private final ContactsListActivity contactsListActivity;
    private boolean mLoading = true;
    protected CharSequence mUnknownNameText;
    protected boolean mDisplayPhotos = false;
    private boolean mDisplayCallButton = false;
    protected boolean mDisplayAdditionalData = true;
    private int mFrequentSeparatorPos = ListView.INVALID_POSITION;

    public ContactItemListAdapter(ContactsListActivity contactsListActivity) {
        super(contactsListActivity);
        this.contactsListActivity = contactsListActivity;

        mUnknownNameText = contactsListActivity.getText(android.R.string.unknownName);

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
    }

    @Override
    public void setDisplayPhotos(boolean flag) {
        mDisplayPhotos = flag;
    }

    /**
     * Callback on the UI thread when the content observer on the backing cursor fires.
     * Instead of calling requery we need to do an async query so that the requery doesn't
     * block the UI thread for a long time.
     */
    @Override
    public void onContentChanged() {
        CharSequence constraint = getQueryString();
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
            return TextUtils.isEmpty(getQueryString());
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

        int realPosition = getRealPosition(position);
        if (!mCursor.moveToPosition(realPosition)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }

        boolean newView;
        View v;
        if (convertView == null || convertView.getTag() == null) {
            newView = true;
            v = newView(getContext(), mCursor, parent);
        } else {
            newView = false;
            v = convertView;
        }
        bindView(v, getContext(), mCursor);
        bindSectionHeader(v, realPosition, isSectionHeaderDisplayEnabled());
        return v;
    }

    private View getTotalContactCountView(ViewGroup parent) {
        final LayoutInflater inflater = contactsListActivity.getLayoutInflater();
        View view = inflater.inflate(R.layout.total_contacts, parent, false);

        TextView totalContacts = (TextView) view.findViewById(R.id.totalContactsText);

        String text;
        int count = getRealCount();

        if (contactsListActivity.mSearchMode
                && !TextUtils.isEmpty(getQueryString())) {
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

    @Override
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
        return view;
    }

    @Override
    public void bindView(View itemView, Context context, Cursor cursor) {
        final ContactListItemView view = (ContactListItemView)itemView;

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
                highlightingEnabled = isNameHighlightingEnabled()
                        && contactsListActivity.mMode != ContactsListActivity.MODE_STREQUENT;
            }
        }

        // Set the name
        cursor.copyStringToBuffer(nameColumnIndex, view.nameBuffer);
        TextView nameView = view.getNameTextView();
        int size = view.nameBuffer.sizeCopied;
        if (size != 0) {
            if (highlightingEnabled) {
                if (view.textWithHighlighting == null) {
                    view.textWithHighlighting =
                            getTextWithHighlightingFactory().createTextWithHighlighting();
                }
                buildDisplayNameWithHighlighting(nameView, cursor, view.nameBuffer,
                        view.highlightedTextBuffer, view.textWithHighlighting);
            } else {
                nameView.setText(view.nameBuffer.data, 0, size);
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

            getPhotoLoader().loadPhoto(viewToUse, photoId);
        }

        if ((contactsListActivity.mMode & ContactsListActivity.MODE_MASK_NO_PRESENCE) == 0) {
            // Set the proper icon (star or presence or nothing)
            int serverStatus;
            if (!cursor.isNull(ContactsListActivity.SUMMARY_PRESENCE_STATUS_COLUMN_INDEX)) {
                serverStatus =
                        cursor.getInt(ContactsListActivity.SUMMARY_PRESENCE_STATUS_COLUMN_INDEX);
                Drawable icon = ContactPresenceIconUtil.getPresenceIcon(getContext(), serverStatus);
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
                cursor.copyStringToBuffer(phoneticNameColumnIndex, view.phoneticNameBuffer);
                int phoneticNameSize = view.phoneticNameBuffer.sizeCopied;
                if (phoneticNameSize != 0) {
                    view.setLabel(view.phoneticNameBuffer.data, phoneticNameSize);
                } else {
                    view.setLabel(null);
                }
            } else {
                view.setLabel(null);
            }
            return;
        }

        // Set the data.
        cursor.copyStringToBuffer(dataColumnIndex, view.dataBuffer);

        size = view.dataBuffer.sizeCopied;
        view.setData(view.dataBuffer.data, size);

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
    protected void buildDisplayNameWithHighlighting(TextView textView, Cursor cursor,
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
        if (!displaySectionHeaders) {
            view.setSectionHeader(null);
            view.setDividerVisible(true);
        } else {
            final int section = getSectionForPosition(position);
            if (getPositionForSection(section) == position) {
                String title = (String)getSections()[section];
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
            prepareEmptyView();
        }

        super.changeCursor(cursor);

        // Update the indexer for the fast scroll widget
        updateIndexer(cursor);
    }

    protected void prepareEmptyView() {
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
                false,
                false);
    }

    private void updateIndexer(Cursor cursor) {
        if (cursor == null) {
            setIndexer(null);
            return;
        }

        Bundle bundle = cursor.getExtras();
        if (bundle.containsKey(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)) {
            String sections[] =
                bundle.getStringArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
            int counts[] = bundle.getIntArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
            setIndexer(new ContactsSectionIndexer(sections, counts));
        } else {
            setIndexer(null);
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

    @Override
    protected int getCursorPosition(int position) {
        return getRealPosition(position);
    }

    protected int getRealPosition(int pos) {
        if (contactsListActivity.mShowNumberOfContacts) {
            pos--;
        }

        if ((contactsListActivity.mMode & ContactsListActivity.MODE_MASK_CREATE_NEW) != 0
                && !contactsListActivity.mSearchMode) {
            return pos - 1;
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

    @Override
    public void configureLoader(CursorLoader loader) {
    }

    @Override
    public String getContactDisplayName() {
        return "TODO";
    }

    public boolean isContactStarred() {
        return false;
    }
}