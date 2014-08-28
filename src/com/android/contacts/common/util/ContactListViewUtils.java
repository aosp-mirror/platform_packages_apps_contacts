package com.android.contacts.common.util;


import android.content.res.Resources;
import android.widget.ListView;
import com.android.contacts.common.R;

/**
 * Utilities for loading contact list view.
 */
public class ContactListViewUtils {

    // These two constants will help add more padding for the text inside the card.
    private static final double TEXT_LEFT_PADDING_TO_CARD_PADDING_RATIO = 1.1;

    /**
     * Add padding to the given list view if the given resources has set
     * both space weight and view weight on the layout. Use this util method
     * instead of defining in the layout file so that the list view padding
     * can be set proportional to the card padding.
     */
    public static void addPaddingToView(ListView listView, int listSpaceWeight, int listViewWeight)
    {
        if (listSpaceWeight > 0 && listViewWeight > 0) {
            double paddingPercent = (double) listSpaceWeight / (double)
                    (listSpaceWeight * 2 + listViewWeight);
            int width = listView.getWidth();
            listView.setPadding(
                    (int) (width * paddingPercent * TEXT_LEFT_PADDING_TO_CARD_PADDING_RATIO),
                    listView.getPaddingTop(),
                    (int) (width * paddingPercent * TEXT_LEFT_PADDING_TO_CARD_PADDING_RATIO),
                    listView.getPaddingBottom());
        }
    }
}
