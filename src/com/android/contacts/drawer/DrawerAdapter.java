/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.contacts.drawer;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity.ContactsView;
import com.android.contacts.group.GroupListItem;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.model.account.AccountDisplayInfo;
import com.android.contacts.model.account.AccountDisplayInfoFactory;
import com.android.contacts.util.SharedPreferenceUtil;

import java.util.ArrayList;
import java.util.List;

public class DrawerAdapter extends RecyclerView.Adapter {

    private static final int VIEW_TYPE_PRIMARY_ITEM = 0;
    private static final int VIEW_TYPE_MISC_ITEM = 1;
    private static final int VIEW_TYPE_HEADER_ITEM = 2;
    private static final int VIEW_TYPE_GROUP_ENTRY = 3;
    private static final int VIEW_TYPE_ACCOUNT_ENTRY = 4;
    private static final int VIEW_TYPE_CREATE_LABEL = 5;
    private static final int VIEW_TYPE_NAV_SPACER = 6;
    private static final int VIEW_TYPE_NAV_DIVIDER = 7;

    private static final int TYPEFACE_STYLE_ACTIVATE = R.style.DrawerItemTextActiveStyle;
    private static final int TYPEFACE_STYLE_INACTIVE = R.style.DrawerItemTextInactiveStyle;

    private final Activity mActivity;
    private final LayoutInflater mInflater;
    private ContactsView mSelectedView;
    private boolean mAreGroupWritableAccountsAvailable;

    // The group/account that was last clicked.
    private long mSelectedGroupId;
    private ContactListFilter mSelectedAccount;

    // Adapter elements, ordered in this way in the getItem() method. The ordering is based on:
    //  [Navigation spacer item]
    //  [Primary items] (Contacts, Suggestions)
    //  [Group Header]
    //  [Groups]
    //  [Create Label button]
    //  [Account Header]
    //  [Accounts]
    //  [Misc items] (a divider, Settings, Help & Feedback)
    //  [Navigation spacer item]
    private NavSpacerItem mNavSpacerItem = null;
    private List<PrimaryItem> mPrimaryItems = new ArrayList<>();
    private HeaderItem mGroupHeader = null;
    private List<GroupEntryItem> mGroupEntries = new ArrayList<>();
    private BaseDrawerItem mCreateLabelButton = null;
    private HeaderItem mAccountHeader = null;
    private List<AccountEntryItem> mAccountEntries = new ArrayList<>();
    private List<BaseDrawerItem> mMiscItems = new ArrayList<>();

    private List<BaseDrawerItem> mItemsList = new ArrayList<>();
    private OnClickListener mListener;
    private AccountDisplayInfoFactory mAccountDisplayFactory;

    public DrawerAdapter(Activity activity) {
        super();
        mInflater = LayoutInflater.from(activity);
        mActivity = activity;
        initializeDrawerMenuItems();
    }

    private void initializeDrawerMenuItems() {
        // Spacer item for dividing sections in drawer
        mNavSpacerItem = new NavSpacerItem(R.id.nav_drawer_spacer);
        // Primary items
        mPrimaryItems.add(new PrimaryItem(R.id.nav_all_contacts, R.string.contactsList,
                R.drawable.quantum_ic_account_circle_vd_theme_24, ContactsView.ALL_CONTACTS));
        mPrimaryItems.add(new PrimaryItem(R.id.nav_assistant, R.string.menu_assistant,
                R.drawable.quantum_ic_assistant_vd_theme_24, ContactsView.ASSISTANT));
        // Group Header
        mGroupHeader = new HeaderItem(R.id.nav_groups, R.string.menu_title_groups);
        // Account Header
        mAccountHeader = new HeaderItem(R.id.nav_filters, R.string.menu_title_filters);
        // Create Label Button
        mCreateLabelButton = new BaseDrawerItem(VIEW_TYPE_CREATE_LABEL, R.id.nav_create_label,
                R.string.menu_new_group_action_bar, R.drawable.quantum_ic_add_vd_theme_24);
        // Misc Items
        mMiscItems.add(new DividerItem());
        mMiscItems.add(new MiscItem(R.id.nav_settings, R.string.menu_settings,
                R.drawable.quantum_ic_settings_vd_theme_24));
        mMiscItems.add(new MiscItem(R.id.nav_help, R.string.menu_help,
                R.drawable.quantum_ic_help_vd_theme_24));
        rebuildItemsList();
    }

    private void rebuildItemsList() {
        mItemsList.clear();
        mItemsList.add(mNavSpacerItem);
        mItemsList.addAll(mPrimaryItems);
        if (mAreGroupWritableAccountsAvailable) {
            mItemsList.add(mGroupHeader);
        }
        mItemsList.addAll(mGroupEntries);
        if (mAreGroupWritableAccountsAvailable) {
            mItemsList.add(mCreateLabelButton);
        }
        if (mAccountEntries.size() > 0) {
            mItemsList.add(mAccountHeader);
        }
        mItemsList.addAll(mAccountEntries);
        mItemsList.addAll(mMiscItems);
        mItemsList.add(mNavSpacerItem);
    }

    public void setItemOnClickListener(OnClickListener listener) {
        mListener = listener;
    }

    public void setGroups(List<GroupListItem> groupListItems, boolean areGroupWritable) {
        final ArrayList<GroupEntryItem> groupEntries = new ArrayList<GroupEntryItem>();
        for (GroupListItem group : groupListItems) {
            groupEntries.add(new GroupEntryItem(R.id.nav_group, group));
        }
        mGroupEntries.clear();
        mGroupEntries.addAll(groupEntries);
        mAreGroupWritableAccountsAvailable = areGroupWritable;
        notifyChangeAndRebuildList();
    }

    public void setAccounts(List<ContactListFilter> accountFilterItems) {
        ArrayList<AccountEntryItem> accountItems = new ArrayList<AccountEntryItem>();
        for (ContactListFilter filter : accountFilterItems) {
            accountItems.add(new AccountEntryItem(R.id.nav_filter, filter));
        }
        mAccountDisplayFactory = AccountDisplayInfoFactory.fromListFilters(mActivity,
                accountFilterItems);
        mAccountEntries.clear();
        mAccountEntries.addAll(accountItems);
        // TODO investigate performance of calling notifyDataSetChanged
        notifyChangeAndRebuildList();
    }

    @Override
    public int getItemCount() {
        return  mItemsList.size();
    }

    public BaseDrawerItem getItem(int position) {
        return mItemsList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).viewType;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_NAV_SPACER:
                return getBaseViewHolder(R.layout.nav_drawer_spacer, parent);
            case VIEW_TYPE_PRIMARY_ITEM:
                return getPrimaryItemView(parent);
            case VIEW_TYPE_HEADER_ITEM:
                return getHeaderItemViewHolder(parent);
            case VIEW_TYPE_GROUP_ENTRY:
            case VIEW_TYPE_CREATE_LABEL:
            case VIEW_TYPE_ACCOUNT_ENTRY:
            case VIEW_TYPE_MISC_ITEM:
                return getDrawerItemViewHolder(parent);
            case VIEW_TYPE_NAV_DIVIDER:
                return getBaseViewHolder(R.layout.drawer_horizontal_divider, parent);
        }
        throw new IllegalStateException("Unknown view type " + viewType);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        final BaseDrawerItem item = getItem(position);
        switch (item.viewType) {
            case VIEW_TYPE_PRIMARY_ITEM:
                bindPrimaryItemViewHolder((PrimaryItemViewHolder) holder, (PrimaryItem) item);
                break;
            case VIEW_TYPE_HEADER_ITEM:
                bindHeaderItemViewHolder((HeaderItemViewHolder) holder, (HeaderItem) item);
                break;
            case VIEW_TYPE_GROUP_ENTRY:
                bindGroupItemViewHolder((DrawerItemViewHolder) holder, (GroupEntryItem) item);
                break;
            case VIEW_TYPE_ACCOUNT_ENTRY:
                bindAccountViewHolder((DrawerItemViewHolder) holder, (AccountEntryItem) item);
                break;
            case VIEW_TYPE_CREATE_LABEL:
            case VIEW_TYPE_MISC_ITEM:
                bindDrawerItemViewHolder((DrawerItemViewHolder) holder, item);
                break;
        }
    }

    private void bindPrimaryItemViewHolder(PrimaryItemViewHolder viewHolder, PrimaryItem item) {
        viewHolder.titleView.setText(item.text);
        viewHolder.iconView.setImageResource(item.icon);
        viewHolder.itemView.setId(item.id);
        viewHolder.itemView.setOnClickListener(mListener);
        final boolean showWelcomeBadge = !SharedPreferenceUtil.isWelcomeCardDismissed(mActivity);
        if (item.contactsView == ContactsView.ASSISTANT && showWelcomeBadge) {
            viewHolder.newBadge.setVisibility(View.VISIBLE);
        } else {
            viewHolder.newBadge.setVisibility(View.GONE);
        }
        viewHolder.itemView.setActivated(item.contactsView == mSelectedView);
        updateSelectedStatus(viewHolder);
    }

    private void bindHeaderItemViewHolder(HeaderItemViewHolder viewHolder, HeaderItem item) {
        viewHolder.titleView.setText(item.text);
        viewHolder.itemView.setId(item.id);
    }

    private void bindGroupItemViewHolder(DrawerItemViewHolder viewHolder, GroupEntryItem item) {
        final GroupListItem group = item.group;
        viewHolder.titleView.setText(group.getTitle());
        viewHolder.iconView.setImageResource(R.drawable.quantum_ic_label_vd_theme_24);
        viewHolder.itemView.setId(item.id);
        viewHolder.itemView.setTag(group);
        viewHolder.itemView.setOnClickListener(mListener);
        viewHolder.itemView.setContentDescription(
                mActivity.getString(R.string.navigation_drawer_label, group.getTitle()));
        viewHolder.itemView.setActivated(group.getGroupId() == mSelectedGroupId
                && mSelectedView == ContactsView.GROUP_VIEW);
        updateSelectedStatus(viewHolder);
    }

    private void bindAccountViewHolder(DrawerItemViewHolder viewHolder, AccountEntryItem item) {
        final ContactListFilter account = item.account;
        viewHolder.titleView.setText(account.accountName);
        final AccountDisplayInfo displayableAccount =
                mAccountDisplayFactory.getAccountDisplayInfoFor(item.account);
        viewHolder.iconView.setImageDrawable(displayableAccount.getIcon());
        viewHolder.iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewHolder.itemView.setId(item.id);
        viewHolder.itemView.setTag(account);
        viewHolder.itemView.setOnClickListener(mListener);
        viewHolder.itemView.setContentDescription(
                displayableAccount.getTypeLabel() + " " + account.accountName);
        viewHolder.itemView.setActivated(account.equals(mSelectedAccount)
                && mSelectedView == ContactsView.ACCOUNT_VIEW);
        updateSelectedStatus(viewHolder);
        viewHolder.iconView.clearColorFilter();
    }

    private void bindDrawerItemViewHolder(DrawerItemViewHolder viewHolder, BaseDrawerItem item) {
        viewHolder.titleView.setText(item.text);
        viewHolder.iconView.setImageResource(item.icon);
        viewHolder.itemView.setId(item.id);
        viewHolder.itemView.setOnClickListener(mListener);
        viewHolder.itemView.setActivated(false);
        updateSelectedStatus(viewHolder);
    }

    private void updateSelectedStatus(DrawerItemViewHolder viewHolder) {
        final boolean activated = viewHolder.itemView.isActivated();
        viewHolder.titleView.setTextAppearance(mActivity, activated
                ? TYPEFACE_STYLE_ACTIVATE : TYPEFACE_STYLE_INACTIVE);
        if (activated) {
            viewHolder.iconView.setColorFilter(mActivity.getResources().getColor(R.color
                    .primary_color), PorterDuff.Mode.SRC_ATOP);
        } else {
            viewHolder.iconView.clearColorFilter();
        }
    }

    private BaseViewHolder getBaseViewHolder(@LayoutRes int layoutResID, ViewGroup parent) {
        final View view = mInflater.inflate(layoutResID, parent, false);
        return new BaseViewHolder(view);
    }

    private HeaderItemViewHolder getHeaderItemViewHolder(ViewGroup parent) {
        final View view = mInflater.inflate(R.layout.drawer_header, parent, false);
        return new HeaderItemViewHolder(view);
    }

    private DrawerItemViewHolder getDrawerItemViewHolder(ViewGroup parent) {
        final View view = mInflater.inflate(R.layout.drawer_item, parent, false);
        return new DrawerItemViewHolder(view);
    }

    private PrimaryItemViewHolder getPrimaryItemView(ViewGroup parent) {
        final View view = mInflater.inflate(R.layout.drawer_primary_item, parent, false);
        return new PrimaryItemViewHolder(view);
    }

    private void notifyChangeAndRebuildList() {
        notifyDataSetChanged();
        rebuildItemsList();
    }

    public void setSelectedContactsView(ContactsView contactsView) {
        if (mSelectedView == contactsView) {
            return;
        }
        mSelectedView = contactsView;
        notifyChangeAndRebuildList();
    }


    public void setSelectedGroupId(long groupId) {
        if (mSelectedGroupId == groupId) {
            return;
        }
        mSelectedGroupId = groupId;
        notifyChangeAndRebuildList();
    }

    public long getSelectedGroupId() {
        return mSelectedGroupId;
    }

    public void setSelectedAccount(ContactListFilter filter) {
        if (mSelectedAccount == filter) {
            return;
        }
        mSelectedAccount = filter;
        notifyChangeAndRebuildList();
    }

    public ContactListFilter getSelectedAccount() {
        return mSelectedAccount;
    }

    public static class BaseDrawerItem {
        public final int viewType;
        public final int id;
        public final int text;
        public final int icon;

        public BaseDrawerItem(int adapterViewType, int viewId, int textResId, int iconResId) {
            viewType = adapterViewType;
            id = viewId;
            text = textResId;
            icon = iconResId;
        }
    }

    // Navigation drawer item for Contacts or Suggestions view which contains a name, an icon and
    // contacts view.
    public static class PrimaryItem extends BaseDrawerItem {
        public final ContactsView contactsView;

        public PrimaryItem(int id, int pageName, int iconId, ContactsView contactsView) {
            super(VIEW_TYPE_PRIMARY_ITEM, id, pageName, iconId);
            this.contactsView = contactsView;
        }
    }

    // Navigation drawer item for Settings, help and feedback, etc.
    public static class MiscItem extends BaseDrawerItem {
        public MiscItem(int id, int textId, int iconId) {
            super(VIEW_TYPE_MISC_ITEM, id, textId, iconId);
        }
    }

    // Header for a list of sub-items in the drawer.
    public static class HeaderItem extends BaseDrawerItem {
        public HeaderItem(int id, int textId) {
            super(VIEW_TYPE_HEADER_ITEM, id, textId, /* iconResId */ 0);
        }
    }

    // Navigation drawer item for spacer item for dividing sections in the drawer.
    public static class NavSpacerItem extends BaseDrawerItem {
        public NavSpacerItem(int id) {
            super(VIEW_TYPE_NAV_SPACER, id, /* textResId */ 0, /* iconResId */ 0);
        }
    }

    // Divider for drawing a line between sections in the drawer.
    public static class DividerItem extends BaseDrawerItem {
        public DividerItem() {
            super(VIEW_TYPE_NAV_DIVIDER, /* id */ 0, /* textResId */ 0, /* iconResId */ 0);
        }
    }

    // Navigation drawer item for a group.
    public static class GroupEntryItem extends BaseDrawerItem {
        private final GroupListItem group;

        public GroupEntryItem(int id, GroupListItem group) {
            super(VIEW_TYPE_GROUP_ENTRY, id, /* textResId */ 0, /* iconResId */ 0);
            this.group = group;
        }
    }

    // Navigation drawer item for an account.
    public static class AccountEntryItem extends BaseDrawerItem {
        private final ContactListFilter account;

        public AccountEntryItem(int id, ContactListFilter account) {
            super(VIEW_TYPE_ACCOUNT_ENTRY, id, /* textResId */ 0, /* iconResId */ 0);
            this.account = account;
        }
    }

    /**
     * ViewHolder classes
     */
    public static class BaseViewHolder extends RecyclerView.ViewHolder {
        public BaseViewHolder(View itemView) {
            super(itemView);
        }
    }

    public static class HeaderItemViewHolder extends BaseViewHolder {
        public final TextView titleView;

        public HeaderItemViewHolder(View itemView) {
            super(itemView);
            titleView = (TextView) itemView.findViewById(R.id.title);
        }
    }

    public class DrawerItemViewHolder extends HeaderItemViewHolder {
        public final ImageView iconView;

        public DrawerItemViewHolder(View itemView) {
            super(itemView);
            iconView = (ImageView) itemView.findViewById(R.id.icon);
        }
    }

    public class PrimaryItemViewHolder extends DrawerItemViewHolder {
        public final TextView newBadge;

        public PrimaryItemViewHolder(View itemView) {
            super(itemView);
            newBadge = (TextView) itemView.findViewById(R.id.assistant_new_badge);
        }
    }
}
