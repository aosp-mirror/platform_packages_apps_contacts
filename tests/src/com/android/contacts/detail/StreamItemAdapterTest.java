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
 * limitations under the License
 */

package com.android.contacts.detail;

import com.android.contacts.util.StreamItemEntry;
import com.android.contacts.util.StreamItemEntryBuilder;
import com.google.common.collect.Lists;

import android.test.AndroidTestCase;
import android.view.View;

import java.util.ArrayList;

// TODO: We should have tests for action, but that requires a mock sync-adapter that specifies
// an action or doesn't

// TODO Add test for photo click

/**
 * Unit tests for {@link StreamItemAdapter}.
 */
public class StreamItemAdapterTest extends AndroidTestCase {
    private StreamItemAdapter mAdapter;
    private FakeOnClickListener mListener;
    private FakeOnClickListener mPhotoListener;
    private View mView;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mListener = new FakeOnClickListener();
        mAdapter = new StreamItemAdapter(getContext(), mListener, mPhotoListener);
    }

    @Override
    protected void tearDown() throws Exception {
        mAdapter = null;
        mListener = null;
        super.tearDown();
    }

    public void testGetCount_Empty() {
        mAdapter.setStreamItems(createStreamItemList(0));
        // The header and title are gone when there are no stream items.
        assertEquals(0, mAdapter.getCount());
    }

    public void testGetCount_NonEmpty() {
        mAdapter.setStreamItems(createStreamItemList(3));
        // There is one extra view: the header.
        assertEquals(4, mAdapter.getCount());
    }

    public void testGetView_Header() {
        // Just check that we can inflate it correctly.
        mView = mAdapter.getView(0, null, null);
    }

    /** Counter used by {@link #createStreamItemEntryBuilder()} to create unique builders. */
    private int mCreateStreamItemEntryBuilderCounter = 0;

    /** Returns a stream item builder with basic information in it. */
    private StreamItemEntryBuilder createStreamItemEntryBuilder() {
        return new StreamItemEntryBuilder().setText(
                "text #" + mCreateStreamItemEntryBuilderCounter++);
    }

    /** Creates a list containing the given number of {@link StreamItemEntry}s. */
    private ArrayList<StreamItemEntry> createStreamItemList(int count) {
        ArrayList<StreamItemEntry> list = Lists.newArrayList();
        for (int index = 0; index < count; ++index) {
            list.add(createStreamItemEntryBuilder().build());
        }
        return list;
    }

    /** Checks that the stream item view has a click listener. */
    private void assertStreamItemViewHasOnClickListener() {
        assertFalse("listener should have not been invoked yet", mListener.clicked);
        mView.performClick();
        assertTrue("listener should have been invoked", mListener.clicked);
    }

    /** Checks that the stream item view does not have a click listener. */
    private void assertStreamItemViewHasNoOnClickListener() {
        assertFalse("listener should have not been invoked yet", mListener.clicked);
        mView.performClick();
        assertFalse("listener should have not been invoked", mListener.clicked);
    }

    /** Checks that the stream item view is clickable. */
    private void assertStreamItemViewFocusable() {
        assertNotNull("should have a stream item", mView);
        assertTrue("should be focusable", mView.isFocusable());
    }

    /** Asserts that there is a stream item but it is not clickable. */
    private void assertStreamItemViewNotFocusable() {
        assertNotNull("should have a stream item", mView);
        assertFalse("should not be focusable", mView.isFocusable());
    }

    /** Checks that the stream item view has the given stream item as its tag. */
    private void assertStreamItemViewHasTag(StreamItemEntry streamItem) {
        Object tag = mView.getTag();
        assertNotNull("should have a tag", tag);
        assertTrue("should be a StreamItemEntry", tag instanceof StreamItemEntry);
        StreamItemEntry streamItemTag = (StreamItemEntry) tag;
        // The streamItem itself should be in the tag.
        assertSame(streamItem, streamItemTag);
    }

    /** Checks that the stream item view has the given stream item as its tag. */
    private void assertStreamItemViewHasNoTag() {
        Object tag = mView.getTag();
        assertNull("should not have a tag", tag);
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
}
