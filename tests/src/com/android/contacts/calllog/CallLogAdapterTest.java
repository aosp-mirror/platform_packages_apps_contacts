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

package com.android.contacts.calllog;

import com.google.common.collect.Lists;

import android.content.Context;
import android.database.MatrixCursor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;

import java.util.List;

/**
 * Unit tests for {@link CallLogAdapter}.
 */
@SmallTest
public class CallLogAdapterTest extends AndroidTestCase {
    private static final String TEST_NUMBER = "12345678";
    private static final String TEST_NAME = "name";
    private static final String TEST_NUMBER_LABEL = "label";
    private static final int TEST_NUMBER_TYPE = 1;
    private static final String TEST_COUNTRY_ISO = "US";

    /** The object under test. */
    private TestCallLogAdapter mAdapter;

    private MatrixCursor mCursor;
    private View mView;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Use a call fetcher that does not do anything.
        CallLogAdapter.CallFetcher fakeCallFetcher = new CallLogAdapter.CallFetcher() {
            @Override
            public void fetchCalls() {}
        };

        ContactInfoHelper fakeContactInfoHelper =
                new ContactInfoHelper(getContext(), TEST_COUNTRY_ISO) {
                    @Override
                    public ContactInfo lookupNumber(String number, String countryIso) {
                        ContactInfo info = new ContactInfo();
                        info.number = number;
                        info.formattedNumber = number;
                        return info;
                    }
                };

        mAdapter = new TestCallLogAdapter(getContext(), fakeCallFetcher, fakeContactInfoHelper);
        // The cursor used in the tests to store the entries to display.
        mCursor = new MatrixCursor(CallLogQuery.EXTENDED_PROJECTION);
        mCursor.moveToFirst();
        // The views into which to store the data.
        mView = new View(getContext());
        mView.setTag(CallLogListItemViews.createForTest(getContext()));
    }

    @Override
    protected void tearDown() throws Exception {
        mAdapter = null;
        mCursor = null;
        mView = null;
        super.tearDown();
    }

    public void testBindView_NoCallLogCacheNorMemoryCache_EnqueueRequest() {
        mCursor.addRow(createCallLogEntry());

        // Bind the views of a single row.
        mAdapter.bindStandAloneView(mView, getContext(), mCursor);

        // There is one request for contact details.
        assertEquals(1, mAdapter.requests.size());

        TestCallLogAdapter.Request request = mAdapter.requests.get(0);
        // It is for the number we need to show.
        assertEquals(TEST_NUMBER, request.number);
        // It has the right country.
        assertEquals(TEST_COUNTRY_ISO, request.countryIso);
        // Since there is nothing in the cache, it is an immediate request.
        assertTrue("should be immediate", request.immediate);
    }

    public void testBindView_CallLogCacheButNoMemoryCache_EnqueueRequest() {
        mCursor.addRow(createCallLogEntryWithCachedValues());

        // Bind the views of a single row.
        mAdapter.bindStandAloneView(mView, getContext(), mCursor);

        // There is one request for contact details.
        assertEquals(1, mAdapter.requests.size());

        TestCallLogAdapter.Request request = mAdapter.requests.get(0);
        // The values passed to the request, match the ones in the call log cache.
        assertEquals(TEST_NAME, request.callLogInfo.name);
        assertEquals(1, request.callLogInfo.type);
        assertEquals(TEST_NUMBER_LABEL, request.callLogInfo.label);
    }


    public void testBindView_NoCallLogButMemoryCache_EnqueueRequest() {
        mCursor.addRow(createCallLogEntry());
        mAdapter.injectContactInfoForTest(TEST_NUMBER, TEST_COUNTRY_ISO, createContactInfo());

        // Bind the views of a single row.
        mAdapter.bindStandAloneView(mView, getContext(), mCursor);

        // There is one request for contact details.
        assertEquals(1, mAdapter.requests.size());

        TestCallLogAdapter.Request request = mAdapter.requests.get(0);
        // Since there is something in the cache, it is not an immediate request.
        assertFalse("should not be immediate", request.immediate);
    }

    public void testBindView_BothCallLogAndMemoryCache_NoEnqueueRequest() {
        mCursor.addRow(createCallLogEntryWithCachedValues());
        mAdapter.injectContactInfoForTest(TEST_NUMBER, TEST_COUNTRY_ISO, createContactInfo());

        // Bind the views of a single row.
        mAdapter.bindStandAloneView(mView, getContext(), mCursor);

        // Cache and call log are up-to-date: no need to request update.
        assertEquals(0, mAdapter.requests.size());
    }

    public void testBindView_MismatchBetwenCallLogAndMemoryCache_EnqueueRequest() {
        mCursor.addRow(createCallLogEntryWithCachedValues());

        // Contact info contains a different name.
        ContactInfo info = createContactInfo();
        info.name = "new name";
        mAdapter.injectContactInfoForTest(TEST_NUMBER, TEST_COUNTRY_ISO, info);

        // Bind the views of a single row.
        mAdapter.bindStandAloneView(mView, getContext(), mCursor);

        // There is one request for contact details.
        assertEquals(1, mAdapter.requests.size());

        TestCallLogAdapter.Request request = mAdapter.requests.get(0);
        // Since there is something in the cache, it is not an immediate request.
        assertFalse("should not be immediate", request.immediate);
    }

    /** Returns a contact info with default values. */
    private ContactInfo createContactInfo() {
        ContactInfo info = new ContactInfo();
        info.number = TEST_NUMBER;
        info.name = TEST_NAME;
        info.type = TEST_NUMBER_TYPE;
        info.label = TEST_NUMBER_LABEL;
        return info;
    }

    /** Returns a call log entry without cached values. */
    private Object[] createCallLogEntry() {
        Object[] values = CallLogQueryTestUtils.createTestExtendedValues();
        values[CallLogQuery.NUMBER] = TEST_NUMBER;
        values[CallLogQuery.COUNTRY_ISO] = TEST_COUNTRY_ISO;
        return values;
    }

    /** Returns a call log entry with a cached values. */
    private Object[] createCallLogEntryWithCachedValues() {
        Object[] values = createCallLogEntry();
        values[CallLogQuery.CACHED_NAME] = TEST_NAME;
        values[CallLogQuery.CACHED_NUMBER_TYPE] = TEST_NUMBER_TYPE;
        values[CallLogQuery.CACHED_NUMBER_LABEL] = TEST_NUMBER_LABEL;
        return values;
    }

    /**
     * Subclass of {@link CallLogAdapter} used in tests to intercept certain calls.
     */
    // TODO: This would be better done by splitting the contact lookup into a collaborator class
    // instead.
    private static final class TestCallLogAdapter extends CallLogAdapter {
        public static class Request {
            public final String number;
            public final String countryIso;
            public final ContactInfo callLogInfo;
            public final boolean immediate;

            public Request(String number, String countryIso, ContactInfo callLogInfo,
                    boolean immediate) {
                this.number = number;
                this.countryIso = countryIso;
                this.callLogInfo = callLogInfo;
                this.immediate = immediate;
            }
        }

        public final List<Request> requests = Lists.newArrayList();

        public TestCallLogAdapter(Context context, CallFetcher callFetcher,
                ContactInfoHelper contactInfoHelper) {
            super(context, callFetcher, contactInfoHelper);
        }

        @Override
        void enqueueRequest(String number, String countryIso, ContactInfo callLogInfo,
                boolean immediate) {
            requests.add(new Request(number, countryIso, callLogInfo, immediate));
        }
    }
}
