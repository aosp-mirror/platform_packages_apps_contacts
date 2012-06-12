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

package com.android.contacts.detail;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.util.StreamItemEntry;
import com.android.contacts.util.StreamItemEntryBuilder;

/**
 * Unit tests for {@link ContactDetailDisplayUtils}.
 */
@SmallTest
public class ContactDetailDisplayUtilsTest extends AndroidTestCase {
    private static final String TEST_STREAM_ITEM_TEXT = "text";

    private LayoutInflater mLayoutInflater;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mLayoutInflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testAddStreamItemText_IncludesComments() {
        StreamItemEntry streamItem = getTestBuilder().setComment("1 comment").build(getContext());
        View streamItemView = addStreamItemText(streamItem);
        assertHasText(streamItemView, R.id.stream_item_comments, "1 comment");
    }

    public void testAddStreamItemText_IncludesHtmlComments() {
        StreamItemEntry streamItem = getTestBuilder().setComment("1 <b>comment</b>")
                .build(getContext());
        View streamItemView = addStreamItemText(streamItem);
        assertHasHtmlText(streamItemView, R.id.stream_item_comments, "1 <b>comment<b>");
    }

    public void testAddStreamItemText_NoComments() {
        StreamItemEntry streamItem = getTestBuilder().setComment(null).build(getContext());
        View streamItemView = addStreamItemText(streamItem);
        assertGone(streamItemView, R.id.stream_item_comments);
    }

    /** Checks that the given id corresponds to a visible text view with the expected text. */
    private void assertHasText(View parent, int textViewId, String expectedText) {
        TextView textView = (TextView) parent.findViewById(textViewId);
        assertNotNull(textView);
        assertEquals(View.VISIBLE, textView.getVisibility());
        assertEquals(expectedText, textView.getText().toString());
    }

    /** Checks that the given id corresponds to a visible text view with the expected HTML. */
    private void assertHasHtmlText(View parent, int textViewId, String expectedHtml) {
        TextView textView = (TextView) parent.findViewById(textViewId);
        assertNotNull(textView);
        assertEquals(View.VISIBLE, textView.getVisibility());
        assertSpannableEquals(Html.fromHtml(expectedHtml), textView.getText());
    }

    /**
     * Asserts that a char sequence is actually a {@link Spanned} matching the one expected.
     */
    private void assertSpannableEquals(Spanned expected, CharSequence actualCharSequence) {
        assertEquals(expected.toString(), actualCharSequence.toString());
        assertTrue("char sequence should be an instance of Spanned",
                actualCharSequence instanceof Spanned);
        Spanned actual = (Spanned) actualCharSequence;
        assertEquals(Html.toHtml(expected), Html.toHtml(actual));
    }

    /** Checks that the given id corresponds to a gone view. */
    private void assertGone(View parent, int textId) {
        View view = parent.findViewById(textId);
        assertNotNull(view);
        assertEquals(View.GONE, view.getVisibility());
    }

    /**
     * Calls {@link ContactDetailDisplayUtils#addStreamItemText(LayoutInflater, Context,
     * StreamItemEntry, View)} with the default parameters and the given stream item.
     */
    private View addStreamItemText(StreamItemEntry streamItem) {
        return ContactDetailDisplayUtils.addStreamItemText(getContext(), streamItem,
                mLayoutInflater.inflate(R.layout.stream_item_container, null));
    }

    private StreamItemEntryBuilder getTestBuilder() {
        return new StreamItemEntryBuilder().setText(TEST_STREAM_ITEM_TEXT);
    }
}
