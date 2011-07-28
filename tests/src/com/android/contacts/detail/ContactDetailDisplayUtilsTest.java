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

import com.android.contacts.R;
import com.android.contacts.util.StreamItemEntry;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Unit tests for {@link ContactDetailDisplayUtils}.
 */
@SmallTest
public class ContactDetailDisplayUtilsTest extends AndroidTestCase {
    private static final String TEST_STREAM_ITEM_TEXT = "text";

    private LinearLayout mParent;
    private LayoutInflater mLayoutInflater;
    private FakeOnClickListener mListener = new FakeOnClickListener();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mParent = new LinearLayout(getContext());
        mLayoutInflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testAddStreamItemText_IncludesComments() {
        StreamItemEntry streamItem = getTestBuilder().setComment("1 comment").build();
        View streamItemView = addStreamItemText(streamItem);
        assertHasText(streamItemView, R.id.stream_item_comments, "1 comment");
    }

    public void testAddStreamItemText_IncludesHtmlComments() {
        StreamItemEntry streamItem = getTestBuilder().setComment("1 <b>comment</b>").build();
        View streamItemView = addStreamItemText(streamItem);
        assertHasHtmlText(streamItemView, R.id.stream_item_comments, "1 <b>comment<b>");
    }

    public void testAddStreamItemText_NoComments() {
        StreamItemEntry streamItem = getTestBuilder().setComment(null).build();
        View streamItemView = addStreamItemText(streamItem);
        assertGone(streamItemView, R.id.stream_item_comments);
    }

    public void testAddStreamItemToContainer_NoAction() {
        StreamItemEntry streamItem = getTestBuilder()
                .setAction(null)
                .setActionUri(null)
                .build();
        addStreamItemToContainer(streamItem, mListener);
        assertStreamItemNotClickable();
    }

    public void testAddStreamItemToContainer_WithActionButNoActionUri() {
        StreamItemEntry streamItem = getTestBuilder()
                .setAction("action")
                .setActionUri(null)
                .build();
        addStreamItemToContainer(streamItem, mListener);
        assertStreamItemNotClickable();
    }

    public void testAddStreamItemToContainer_WithActionUriButNoAction() {
        StreamItemEntry streamItem = getTestBuilder()
                .setAction(null)
                .setActionUri("http://www.google.com")
                .build();
        addStreamItemToContainer(streamItem, mListener);
        assertStreamItemNotClickable();
    }

    public void testAddStreamItemToContainer_WithActionAndActionUri() {
        StreamItemEntry streamItem = getTestBuilder()
                .setAction("action")
                .setActionUri("http://www.google.com")
                .build();
        addStreamItemToContainer(streamItem, mListener);
        assertStreamItemClickable();
        assertStreamItemHasOnClickListener();
        assertStreamItemHasTag(streamItem);
    }

    /** Checks that the stream item view is clickable. */
    private void assertStreamItemClickable() {
        View streamItemView = mParent.findViewById(R.id.stream_item_content);
        assertNotNull("should have a stream item", streamItemView);
        assertTrue("should be clickable", streamItemView.isClickable());
        assertTrue("should be focusable", streamItemView.isFocusable());
    }

    /** Asserts that there is a stream item but it is not clickable. */
    private void assertStreamItemNotClickable() {
        View streamItemView = mParent.findViewById(R.id.stream_item_content);
        assertNotNull("should have a stream item", streamItemView);
        assertFalse("should not be clickable", streamItemView.isClickable());
        assertFalse("should not be focusable", streamItemView.isFocusable());
    }

    /** Checks that the stream item view has a click listener. */
    private void assertStreamItemHasOnClickListener() {
        // Check that the on-click listener is invoked when clicked.
        View streamItemView = mParent.findViewById(R.id.stream_item_content);
        assertFalse("listener should have not been invoked yet", mListener.clicked);
        streamItemView.performClick();
        assertTrue("listener should have been invoked", mListener.clicked);
    }

    /** Checks that the stream item view has the given stream item as its tag. */
    private void assertStreamItemHasTag(StreamItemEntry streamItem) {
        // The view's tag should point to the stream item entry for this view.
        View streamItemView = mParent.findViewById(R.id.stream_item_content);
        Object tag = streamItemView.getTag();
        assertNotNull("should have a tag", tag);
        assertTrue("should be a StreamItemEntry", tag instanceof StreamItemEntry);
        StreamItemEntry streamItemTag = (StreamItemEntry) tag;
        // The streamItem itself should be in the tag.
        assertSame(streamItem, streamItemTag);
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
     * StreamItemEntry, ViewGroup)} with the default parameters and the given stream item.
     */
    private View addStreamItemText(StreamItemEntry streamItem) {
        return ContactDetailDisplayUtils.addStreamItemText(
                mLayoutInflater, getContext(), streamItem, mParent);
    }

    /**
     * Calls {@link ContactDetailDisplayUtils#addStreamItemToContainer(LayoutInflater,
     * Context,StreamItemEntry, LinearLayout, android.view.View.OnClickListener)} with the default
     * parameters and the given stream item and listener.
     */
    private void addStreamItemToContainer(StreamItemEntry streamItem,
            View.OnClickListener listener) {
        ContactDetailDisplayUtils.addStreamItemToContainer(mLayoutInflater, getContext(),
                streamItem, mParent, listener);
    }

    /**
     * Simple fake implementation of {@link View.OnClickListener} which sets a member variable to
     * true when clicked.
     */
    private final class FakeOnClickListener implements View.OnClickListener {
        public boolean clicked = false;

        @Override
        public void onClick(View view) {
            clicked = true;
        }
    }

    private static class StreamItemEntryBuilder {
        private long mId;
        private String mText;
        private String mComment;
        private long mTimestamp;
        private String mAction;
        private String mActionUri;
        private String mResPackage;
        private int mIconRes;
        private int mLabelRes;

        public StreamItemEntryBuilder() {}

        public StreamItemEntryBuilder setText(String text) {
            mText = text;
            return this;
        }

        public StreamItemEntryBuilder setComment(String comment) {
            mComment = comment;
            return this;
        }

        public StreamItemEntryBuilder setAction(String action) {
            mAction = action;
            return this;
        }

        public StreamItemEntryBuilder setActionUri(String actionUri) {
            mActionUri = actionUri;
            return this;
        }

        public StreamItemEntry build() {
            return new StreamItemEntry(mId, mText, mComment, mTimestamp, mAction, mActionUri,
                    mResPackage, mIconRes, mLabelRes);
        }
    }

    private StreamItemEntryBuilder getTestBuilder() {
        return new StreamItemEntryBuilder().setText(TEST_STREAM_ITEM_TEXT);
    }
}
