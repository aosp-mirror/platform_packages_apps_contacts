package com.android.contacts.common.util;


import com.android.contacts.common.R;

import android.content.res.Resources;
import android.view.View;
import android.widget.ListView;

/**
 * Utilities for configuring ListViews with a card background.
 */
public class ContactListViewUtils {

    // These two constants will help add more padding for the text inside the card.
    private static final double TEXT_LEFT_PADDING_TO_CARD_PADDING_RATIO = 1.1;

    private static void addPaddingToView(ListView listView, int parentWidth,
            int listSpaceWeight, int listViewWeight)
    {
        if (listSpaceWeight > 0 && listViewWeight > 0) {
            double paddingPercent = (double) listSpaceWeight / (double)
                    (listSpaceWeight * 2 + listViewWeight);
            listView.setPadding(
                    (int) (parentWidth * paddingPercent * TEXT_LEFT_PADDING_TO_CARD_PADDING_RATIO),
                    listView.getPaddingTop(),
                    (int) (parentWidth * paddingPercent * TEXT_LEFT_PADDING_TO_CARD_PADDING_RATIO),
                    listView.getPaddingBottom());
            // The EdgeEffect and ScrollBar need to span to the edge of the ListView's padding.
            listView.setClipToPadding(false);
            listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        }
    }

    /**
     * Add padding to {@param listView} if this configuration has set both space weight and
     * view weight on the layout. Use this util method instead of defining the padding in the
     * layout file so that the {@param listView}'s padding can be set proportional to the card
     * padding.
     *
     * @param resources
     * @param listView ListView that we add padding to
     * @param rootLayout layout that contains ListView and R.id.list_card
     */
    public static void applyCardPaddingToView(Resources resources,
            final ListView listView, final View rootLayout) {
        // Set a padding on the list view so it appears in the center of the card
        // in the layout if required.
        final int listSpaceWeight = resources.getInteger(
                R.integer.contact_list_space_layout_weight);
        final int listViewWeight = resources.getInteger(
                R.integer.contact_list_card_layout_weight);
        if (listSpaceWeight > 0 && listViewWeight > 0) {
            rootLayout.setBackgroundResource(0);
            // Set the card view visible
            View mCardView = rootLayout.findViewById(R.id.list_card);
            if (mCardView == null) {
                throw new RuntimeException(
                        "Your content must have a list card view who can be turned visible " +
                                "whenever it is necessary.");
            }
            mCardView.setVisibility(View.VISIBLE);
            // Add extra padding to the list view to make them appear in the center of the card.
            // In order to avoid jumping, we skip drawing the next frame of the ListView.
            SchedulingUtils.doOnPreDraw(listView, /* drawNextFrame = */ false, new Runnable() {
                @Override
                public void run() {
                    // Use the rootLayout.getWidth() instead of listView.getWidth() since
                    // we sometimes hide the listView until we finish loading data. This would
                    // result in incorrect padding.
                    ContactListViewUtils.addPaddingToView(
                            listView, rootLayout.getWidth(),  listSpaceWeight, listViewWeight);
                }
            });
        }
    }
}
