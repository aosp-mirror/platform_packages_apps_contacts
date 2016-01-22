package com.android.contacts.callblocking;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v13.app.FragmentCompat;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Space;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.callblocking.FilteredNumberAsyncQueryHandler.OnCheckBlockedListener;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.contacts.common.list.PinnedHeaderListView;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.widget.EmptyContentView;
import com.android.contacts.widget.SearchEditTextLayout;
import com.android.contacts.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;

import static android.Manifest.permission.READ_CONTACTS;

public class SearchFragment extends PhoneNumberPickerFragment
        implements OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback,
        BlockNumberDialogFragment.Callback{
    private static final String TAG  = SearchFragment.class.getSimpleName();

    public static final int PERMISSION_REQUEST_CODE = 1;
    private static final int SEARCH_DIRECTORY_RESULT_LIMIT = 5;
    // copied from packages/apps/InCallUI/src/com/android/incallui/Call.java
    public static final int INITIATION_REMOTE_DIRECTORY = 4;
    public static final int INITIATION_REGULAR_SEARCH = 6;

    private OnListFragmentScrolledListener mActivityScrollListener;
    private View.OnTouchListener mActivityOnTouchListener;
    private FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;
    private EditText mSearchView;

    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            setQueryString(s.toString(), false);
        }

        @Override
        public void afterTextChanged(Editable s) {}
    };

    private final SearchEditTextLayout.Callback mSearchLayoutCallback =
            new SearchEditTextLayout.Callback() {
                @Override
                public void onBackButtonClicked() {
                    getActivity().onBackPressed();
                }

                @Override
                public void onSearchViewClicked() {
                }
            };
    /**
     * Stores the untouched user-entered string that is used to populate the add to contacts
     * intent.
     */
    private String mAddToContactNumber;
    private int mActionBarHeight;
    private int mShadowHeight;
    private int mPaddingTop;

    /**
     * Used to resize the list view containing search results so that it fits the available space
     * above the dialpad. Does not have a user-visible effect in regular touch usage (since the
     * dialpad hides that portion of the ListView anyway), but improves usability in accessibility
     * mode.
     */
    private Space mSpacer;

    private HostInterface mActivity;

    protected EmptyContentView mEmptyView;

    public interface HostInterface {
        boolean isActionBarShowing();
        boolean isDialpadShown();
        int getDialpadHeight();
        int getActionBarHideOffset();
        int getActionBarHeight();
    }

    protected String mPermissionToRequest;

    public SearchFragment() {
        configureDirectorySearch();
    }

    public void configureDirectorySearch() {
        setDirectorySearchEnabled(true);
        setDirectoryResultLimit(SEARCH_DIRECTORY_RESULT_LIMIT);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setShowEmptyListForNullQuery(true);
        /*
         * Pass in the empty string here so ContactEntryListFragment#setQueryString interprets it as
         * an empty search query, rather than as an uninitalized value. In the latter case, the
         * adapter returned by #createListAdapter is used, which populates the view with contacts.
         * Passing in the empty string forces ContactEntryListFragment to interpret it as an empty
         * query, which results in showing an empty view
         */
        setQueryString(getQueryString() == null ? "" : getQueryString(), false);
        mFilteredNumberAsyncQueryHandler = new FilteredNumberAsyncQueryHandler(
                getContext().getContentResolver());
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setCustomView(R.layout.search_edittext);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);

        final SearchEditTextLayout searchEditTextLayout = (SearchEditTextLayout) actionBar
                .getCustomView().findViewById(R.id.search_view_container);
        searchEditTextLayout.expand(true);
        searchEditTextLayout.setCallback(mSearchLayoutCallback);
        searchEditTextLayout.setBackgroundDrawable(null);

        mSearchView = (EditText) searchEditTextLayout.findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
        mSearchView.setHint(R.string.block_number_search_hint);

        searchEditTextLayout.findViewById(R.id.search_box_expanded)
                .setBackgroundColor(getContext().getResources().getColor(android.R.color.white));

        if (!TextUtils.isEmpty(getQueryString())) {
            mSearchView.setText(getQueryString());
        }

        // TODO: Don't set custom text size; use default search text size.
        mSearchView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.blocked_number_search_text_size));
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
        ((PinnedHeaderListView) getListView()).setScrollToSectionOnHeaderTouch(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        setQuickContactEnabled(true);
        setAdjustSelectionBoundsEnabled(false);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(false /* opposite */));
        setUseCallableUri(true);

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            Log.d(TAG, activity.toString() + " doesn't implement OnListFragmentScrolledListener. " +
                    "Ignoring.");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isSearchMode()) {
            getAdapter().setHasHeader(0, false);
        }

        mActivity = (HostInterface) getActivity();

        final Resources res = getResources();
        mActionBarHeight = mActivity.getActionBarHeight();
        mShadowHeight  = res.getDrawable(R.drawable.search_shadow).getIntrinsicHeight();
        mPaddingTop = res.getDimensionPixelSize(R.dimen.search_list_padding_top);

        final View parentView = getView();

        final ListView listView = getListView();

        if (mEmptyView == null) {
            mEmptyView = new EmptyContentView(getActivity());
            ((ViewGroup) getListView().getParent()).addView(mEmptyView);
            getListView().setEmptyView(mEmptyView);
            setupEmptyView();
        }

        listView.setBackgroundColor(res.getColor(R.color.background_contacts_results));
        listView.setClipToPadding(false);
        setVisibleScrollbarEnabled(false);

        // Turn off accessibility live region as the list constantly update itself and spam
        // messages.
        listView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        ContentChangedFilter.addToParent(listView);

        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (mActivityScrollListener != null) {
                    mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                 int totalItemCount) {
            }
        });
        if (mActivityOnTouchListener != null) {
            listView.setOnTouchListener(mActivityOnTouchListener);
        }

        updatePosition();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewUtil.addBottomPaddingToListViewForFab(getListView(), getResources());
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        Animator animator = null;
        if (nextAnim != 0) {
            animator = AnimatorInflater.loadAnimator(getActivity(), nextAnim);
        }
        if (animator != null) {
            final View view = getView();
            final int oldLayerType = view.getLayerType();
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setLayerType(oldLayerType, null);
                }
            });
        }
        return animator;
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        // This hides the "All contacts with phone numbers" header in the search fragment
        final ContactEntryListAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.setHasHeader(0, false);
        }
    }

    public void setAddToContactNumber(String addToContactNumber) {
        mAddToContactNumber = addToContactNumber;
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        SearchAdapter adapter = new SearchAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        // Don't show SIP addresses.
        adapter.setUseCallableUri(false);
        // Keep in sync with the queryString set in #onCreate
        adapter.setQueryString(getQueryString() == null ? "" : getQueryString());
        return adapter;
    }

    protected void setupEmptyView() {
        if (mEmptyView != null && getActivity() != null) {
            final int imageResource;
            final int actionLabelResource;
            final int descriptionResource;
            final OnEmptyViewActionButtonClickedListener listener;
            if (!PermissionsUtil.hasPermission(getActivity(), READ_CONTACTS)) {
                imageResource = R.drawable.empty_contacts;
                actionLabelResource = R.string.permission_single_turn_on;
                descriptionResource = R.string.permission_no_search;
                listener = this;
                mPermissionToRequest = READ_CONTACTS;
            } else {
                imageResource = EmptyContentView.NO_IMAGE;
                actionLabelResource = EmptyContentView.NO_LABEL;
                descriptionResource = EmptyContentView.NO_LABEL;
                listener = null;
                mPermissionToRequest = null;
            }

            mEmptyView.setImage(imageResource);
            mEmptyView.setActionLabel(actionLabelResource);
            mEmptyView.setDescription(descriptionResource);
            if (listener != null) {
                mEmptyView.setActionClickedListener(listener);
            }
        }
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (READ_CONTACTS.equals(mPermissionToRequest)) {
            FragmentCompat.requestPermissions(this, new String[]{mPermissionToRequest},
                    PERMISSION_REQUEST_CODE);
        }
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        super.onItemClick(parent, view, position, id);
        final int adapterPosition = position - getListView().getHeaderViewsCount();
        final SearchAdapter adapter = (SearchAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(adapterPosition);
        final Integer blockId = (Integer) view.getTag(R.id.block_id);
        final String number;
        switch (shortcutType) {
            case SearchAdapter.SHORTCUT_INVALID:
                // Handles click on a search result, either contact or nearby places result.
                number = adapter.getPhoneNumber(adapterPosition);
                blockContactNumber(number, blockId);
                break;
            case SearchAdapter.SHORTCUT_BLOCK_NUMBER:
                // Handles click on 'Block number' shortcut to add the user query as a number.
                number = adapter.getQueryString();
                blockNumber(number);
                break;
            default:
                Log.w(TAG, "Ignoring unsupported shortcut type: " + shortcutType);
                break;
        }
    }

    private void blockNumber(final String number) {
        final String countryIso = GeoUtil.getCurrentCountryIso(getContext());
        final OnCheckBlockedListener onCheckListener = new OnCheckBlockedListener() {
            @Override
            public void onCheckComplete(Integer id) {
                if (id == null) {
                    BlockNumberDialogFragment.show(
                            id,
                            number,
                            countryIso,
                            PhoneNumberUtils.formatNumber(number, countryIso),
                            R.id.blocked_numbers_activity_container,
                            getFragmentManager(),
                            SearchFragment.this);
                } else {
                    Toast.makeText(getContext(),
                            ContactDisplayUtils.getTtsSpannedPhoneNumber(getResources(),
                                    R.string.alreadyBlocked, number),
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
        final boolean success = mFilteredNumberAsyncQueryHandler.isBlockedNumber(
                onCheckListener, number, countryIso);
        if (!success) {
            Toast.makeText(getContext(),
                    ContactDisplayUtils.getTtsSpannedPhoneNumber(
                            getResources(), R.string.invalidNumber, number),
                    Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onItemClick(int position, long id) {
        // Prevent super.onItemClicked(int position, long id) from being called.
    }

    /**
     * Updates the position and padding of the search fragment.
     */
    public void updatePosition() {
        int endTranslationValue = 0;
        // Prevents ListView from being translated down after a rotation when the ActionBar is up.
        if (mActivity.isActionBarShowing()) {
            endTranslationValue =
                    mActivity.isDialpadShown() ? 0 : mActionBarHeight - mShadowHeight;
        }
        getView().setTranslationY(endTranslationValue);
        resizeListView();

        // There is padding which should only be applied when the dialpad is not shown.
        int paddingTop = mActivity.isDialpadShown() ? 0 : mPaddingTop;
        final ListView listView = getListView();
        listView.setPaddingRelative(
                listView.getPaddingStart(),
                paddingTop,
                listView.getPaddingEnd(),
                listView.getPaddingBottom());
    }

    public void resizeListView() {
        if (mSpacer == null) {
            return;
        }
        int spacerHeight = mActivity.isDialpadShown() ? mActivity.getDialpadHeight() : 0;
        if (spacerHeight != mSpacer.getHeight()) {
            final LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) mSpacer.getLayoutParams();
            lp.height = spacerHeight;
            mSpacer.setLayoutParams(lp);
        }
    }

    @Override
    protected void startLoading() {
        if (getActivity() == null) {
            return;
        }

        if (PermissionsUtil.hasContactsPermissions(getActivity())) {
            super.startLoading();
        } else if (TextUtils.isEmpty(getQueryString())) {
            // Clear out any existing call shortcuts.
            final SearchAdapter adapter = (SearchAdapter) getAdapter();
            adapter.disableAllShortcuts();
        } else {
            // The contact list is not going to change (we have no results since permissions are
            // denied), but the shortcuts might because of the different query, so update the
            // list.
            getAdapter().notifyDataSetChanged();
        }

        setupEmptyView();
    }

    public void setOnTouchListener(View.OnTouchListener onTouchListener) {
        mActivityOnTouchListener = onTouchListener;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        final LinearLayout parent = (LinearLayout) super.inflateView(inflater, container);
        final int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            mSpacer = new Space(getActivity());
            parent.addView(mSpacer,
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
        }
        return parent;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            setupEmptyView();
            if (grantResults != null && grantResults.length == 1
                    && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                PermissionsUtil.notifyPermissionGranted(getActivity(), mPermissionToRequest);
            }
        }
    }

    @Override
    protected int getCallInitiationType(boolean isRemoteDirectory) {
        return isRemoteDirectory ? INITIATION_REMOTE_DIRECTORY : INITIATION_REGULAR_SEARCH;
    }

    @Override
    public void onFilterNumberSuccess() {
        goBack();
    }

    @Override
    public void onUnfilterNumberSuccess() {
        Log.wtf(TAG, "Unblocked a number from the SearchFragment");
        goBack();
    }

    private void goBack() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.onBackPressed();
    }

    @Override
    public void onChangeFilteredNumberUndo() {
        getAdapter().notifyDataSetChanged();
    }

    private void blockContactNumber(final String number, final Integer blockId) {
        if (blockId != null) {
            Toast.makeText(getContext(), ContactDisplayUtils.getTtsSpannedPhoneNumber(
                            getResources(), R.string.alreadyBlocked, number),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        com.android.contacts.callblocking.BlockNumberDialogFragment.show(
                blockId,
                number,
                com.android.contacts.common.GeoUtil.getCurrentCountryIso(getContext()),
                number,
                R.id.blocked_numbers_activity_container,
                getFragmentManager(),
                this);
    }
}
