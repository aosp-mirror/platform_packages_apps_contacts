/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.activities;

import com.android.contacts.R;
import com.android.contacts.calllog.CallLogFragment;
import com.android.contacts.calllog.CallLogFragment.ContactInfo;
import com.android.contacts.calllog.CallLogListItemViews;
import com.android.internal.telephony.CallerInfo;

import android.content.res.Resources;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.widget.FrameLayout;

import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Random;

/**
 * Tests for the contact call list activity.
 *
 * Running all tests:
 *
 *   runtest contacts
 * or
 *   adb shell am instrument \
 *     -w com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@LargeTest
public class CallLogActivityTests
        extends ActivityInstrumentationTestCase2<CallLogActivity> {
    private static final String TAG = "CallLogActivityTests";

    private static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.COUNTRY_ISO,
    };
    private static final int RAND_DURATION = -1;
    private static final long NOW = -1L;

    /** A test value for the person id of a contact. */
    private static final long TEST_PERSON_ID = 1;
    /** A test value for the photo id of a contact. */
    private static final long TEST_PHOTO_ID = 2;
    /** A test value for the lookup key for contacts. */
    private static final String TEST_LOOKUP_KEY = "contact_id";
    /** A test value for the country ISO of the phone number in the call log. */
    private static final String TEST_COUNTRY_ISO = "US";
    /** A phone number to be used in tests. */
    private static final String TEST_NUMBER = "12125551000";
    /** The formatted version of {@link #TEST_NUMBER}. */
    private static final String TEST_FORMATTED_NUMBER = "1 212-555-1000";

    // We get the call list activity and assign is a frame to build
    // its list.  mAdapter is an inner class of
    // CallLogActivity to build the rows (view) in the call
    // list. We reuse it with our own in-mem DB.
    private CallLogActivity mActivity;
    private CallLogFragment mFragment;
    private FrameLayout mParentView;
    private CallLogFragment.CallLogAdapter mAdapter;
    private String mVoicemail;

    // In memory array to hold the rows corresponding to the 'calls' table.
    private MatrixCursor mCursor;
    private int mIndex;  // Of the next row.

    private Random mRnd;

    // References to the icons bitmaps used to build the list are stored in a
    // map mIcons. The keys to retrieve the icons are:
    // Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE and Calls.MISSED_TYPE.
    private HashMap<Integer, Bitmap> mCallTypeIcons;

    // An item in the call list. All the methods performing checks use it.
    private CallLogListItemViews mItem;
    // The list of views representing the data in the DB. View are in
    // reverse order compare to the DB.
    private View[] mList;

    public CallLogActivityTests() {
        super("com.android.contacts", CallLogActivity.class);
        mIndex = 1;
        mRnd = new Random();
    }

    @Override
    public void setUp() {
        mActivity = getActivity();
        mFragment = mActivity.getFragment();
        mVoicemail = mFragment.getVoiceMailNumber();
        mAdapter = mFragment.getAdapter();
        // Do not process requests for details during tests. This would start a background thread,
        // which makes the tests flaky.
        mAdapter.disableRequestProcessingForTest();
        mAdapter.stopRequestProcessing();
        mParentView = new FrameLayout(mActivity);
        mCursor = new MatrixCursor(CALL_LOG_PROJECTION);
        buildIconMap();
    }

    /**
     * Checks that the call icon is not visible for private and
     * unknown numbers.
     * Use 2 passes, one where new views are created and one where
     * half of the total views are updated and the other half created.
     */
    @MediumTest
    public void testCallViewIsNotVisibleForPrivateAndUnknownNumbers() {
        final int SIZE = 100;
        mList = new View[SIZE];

        // Insert the first batch of entries.
        mCursor.moveToFirst();
        insertRandomEntries(SIZE / 2);
        int startOfSecondBatch = mCursor.getPosition();

        buildViewListFromDb();
        checkCallStatus();

        // Append the rest of the entries. We keep the first set of
        // views around so they get updated and not built from
        // scratch, this exposes some bugs that are not there when the
        // call log is launched for the 1st time but show up when the
        // call log gets updated afterwards.
        mCursor.move(startOfSecondBatch);
        insertRandomEntries(SIZE / 2);

        buildViewListFromDb();
        checkCallStatus();
    }

    @MediumTest
    public void testCallAndGroupViews_GroupView() {
        mCursor.moveToFirst();
        insert(CallerInfo.PRIVATE_NUMBER, NOW, 0, Calls.INCOMING_TYPE);
        insert(CallerInfo.PRIVATE_NUMBER, NOW, 0, Calls.INCOMING_TYPE);
        insert(CallerInfo.PRIVATE_NUMBER, NOW, 0, Calls.INCOMING_TYPE);
        View view = mAdapter.newGroupView(getActivity(), mParentView);
        mAdapter.bindGroupView(view, getActivity(), mCursor, 3, false);
        assertNotNull(view.findViewById(R.id.call_icon));
    }

    @MediumTest
    public void testCallAndGroupViews_StandAloneView() {
        mCursor.moveToFirst();
        insert(CallerInfo.PRIVATE_NUMBER, NOW, 0, Calls.INCOMING_TYPE);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindStandAloneView(view, getActivity(), mCursor);
        assertNotNull(view.findViewById(R.id.call_icon));
        assertNull(view.findViewById(R.id.groupIndicator));
    }

    @MediumTest
    public void testCallAndGroupViews_ChildView() {
        mCursor.moveToFirst();
        insert(CallerInfo.PRIVATE_NUMBER, NOW, 0, Calls.INCOMING_TYPE);
        View view = mAdapter.newChildView(getActivity(), mParentView);
        mAdapter.bindChildView(view, getActivity(), mCursor);
        assertNotNull(view.findViewById(R.id.call_icon));
        assertNull(view.findViewById(R.id.groupIndicator));
    }

    @MediumTest
    public void testBindView_NumberOnly() {
        mCursor.moveToFirst();
        insert(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindStandAloneView(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, TEST_FORMATTED_NUMBER);
        assertNumberAndLabelAreGone(views);
    }

    @MediumTest
    public void testBindView_WithCachedName() {
        mCursor.moveToFirst();
        insertWithCachedValues(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_HOME, "");
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindStandAloneView(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertNumberAndLabelAre(views, TEST_FORMATTED_NUMBER, getTypeLabel(Phone.TYPE_HOME));
    }

    @MediumTest
    public void testBindView_UriNumber() {
        mCursor.moveToFirst();
        insertWithCachedValues("sip:johndoe@gmail.com", NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_HOME, "");
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindStandAloneView(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertNumberAndLabelAre(views, "sip:johndoe@gmail.com", null);
    }

    @MediumTest
    public void testBindView_HomeLabel() {
        mCursor.moveToFirst();
        insertWithCachedValues(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_HOME, "");
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindStandAloneView(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertNumberAndLabelAre(views, TEST_FORMATTED_NUMBER, getTypeLabel(Phone.TYPE_HOME));
    }

    @MediumTest
    public void testBindView_WorkLabel() {
        mCursor.moveToFirst();
        insertWithCachedValues(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_WORK, "");
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindStandAloneView(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertNumberAndLabelAre(views, TEST_FORMATTED_NUMBER, getTypeLabel(Phone.TYPE_WORK));
    }

    @MediumTest
    public void testBindView_CustomLabel() {
        mCursor.moveToFirst();
        String numberLabel = "My label";
        insertWithCachedValues(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_CUSTOM, numberLabel);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindStandAloneView(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertNumberAndLabelAre(views, TEST_FORMATTED_NUMBER, numberLabel);
    }

    /** Returns the label associated with a given phone type. */
    private CharSequence getTypeLabel(int phoneType) {
        return Phone.getTypeLabel(getActivity().getResources(), phoneType, "");
    }

    //
    // HELPERS to check conditions on the DB/views
    //
    /**
     * Go over all the views in the list and check that the Call
     * icon's visibility matches the nature of the number.
     */
    private void checkCallStatus() {
        for (int i = 0; i < mList.length; i++) {
            if (null == mList[i]) {
                break;
            }
            mItem = (CallLogListItemViews) mList[i].getTag();

            // callView tag is the phone number.
            String number = (String) mItem.callView.getTag();

            if (CallerInfo.PRIVATE_NUMBER.equals(number) ||
                CallerInfo.UNKNOWN_NUMBER.equals(number)) {
                assertFalse(View.VISIBLE == mItem.callView.getVisibility());
            } else {
                assertEquals(View.VISIBLE, mItem.callView.getVisibility());
            }
        }
    }


    //
    // HELPERS to setup the tests.
    //

    /**
     * Get the Bitmap from the icons in the contacts package.
     */
    private Bitmap getBitmap(String resName) {
        Resources r = mActivity.getResources();
        int resid = r.getIdentifier(resName, "drawable", "com.android.contacts");
        BitmapDrawable d = (BitmapDrawable) r.getDrawable(resid);
        assertNotNull(d);
        return d.getBitmap();
    }

    /**
     * Fetch all the icons we need in tests from the contacts app and store them in a map.
     */
    private void buildIconMap() {
        mCallTypeIcons = new HashMap<Integer, Bitmap>(3);

        mCallTypeIcons.put(Calls.INCOMING_TYPE, getBitmap("ic_call_incoming_holo_dark"));
        mCallTypeIcons.put(Calls.MISSED_TYPE, getBitmap("ic_call_missed_holo_dark"));
        mCallTypeIcons.put(Calls.OUTGOING_TYPE, getBitmap("ic_call_outgoing_holo_dark"));
    }

    //
    // HELPERS to build/update the call entries (views) from the DB.
    //

    /**
     * Read the DB and foreach call either update the existing view if
     * one exists already otherwise create one.
     * The list is build from a DESC view of the DB (last inserted entry is first).
     */
    private void buildViewListFromDb() {
        int i = 0;
        mCursor.moveToLast();
        while(!mCursor.isBeforeFirst()) {
            if (null == mList[i]) {
                mList[i] = mAdapter.newStandAloneView(mActivity, mParentView);
            }
            mAdapter.bindStandAloneView(mList[i], mActivity, mCursor);
            mCursor.moveToPrevious();
            i++;
        }
    }

    //
    // HELPERS to insert numbers in the call log DB.
    //

    /**
     * Insert a certain number of random numbers in the DB. Makes sure
     * there is at least one private and one unknown number in the DB.
     * @param num Of entries to be inserted.
     */
    private void insertRandomEntries(int num) {
        if (num < 10) {
            throw new IllegalArgumentException("num should be >= 10");
        }
        boolean privateOrUnknownOrVm[];
        privateOrUnknownOrVm = insertRandomRange(0, num - 2);

        if (privateOrUnknownOrVm[0] && privateOrUnknownOrVm[1]) {
            insertRandomRange(num - 2, num);
        } else {
            insertPrivate(NOW, RAND_DURATION);
            insertUnknown(NOW, RAND_DURATION);
        }
    }

    /**
     * Insert a new call entry in the test DB.
     *
     * It includes the values for the cached contact associated with the number.
     *
     * @param number The phone number. For unknown and private numbers,
     *               use CallerInfo.UNKNOWN_NUMBER or CallerInfo.PRIVATE_NUMBER.
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     * @param type Either Call.OUTGOING_TYPE or Call.INCOMING_TYPE or Call.MISSED_TYPE.
     * @param cachedName the name of the contact with this number
     * @param cachedNumberType the type of the number, from the contact with this number
     * @param cachedNumberLabel the label of the number, from the contact with this number
     */
    private void insertWithCachedValues(String number, long date, int duration, int type,
            String cachedName, int cachedNumberType, String cachedNumberLabel) {
        insert(number, date, duration, type);
        ContactInfo contactInfo = new ContactInfo();
        contactInfo.personId = TEST_PERSON_ID;
        contactInfo.name = cachedName;
        contactInfo.type = cachedNumberType;
        contactInfo.label = cachedNumberLabel;
        String formattedNumber = PhoneNumberUtils.formatNumber(number, TEST_COUNTRY_ISO);
        if (formattedNumber == null) {
            formattedNumber = number;
        }
        contactInfo.formattedNumber = formattedNumber;
        contactInfo.normalizedNumber = number;
        contactInfo.photoId = TEST_PHOTO_ID;
        contactInfo.lookupKey = TEST_LOOKUP_KEY;
        mAdapter.injectContactInfoForTest(number, contactInfo);
    }

    /**
     * Insert a new call entry in the test DB.
     * @param number The phone number. For unknown and private numbers,
     *               use CallerInfo.UNKNOWN_NUMBER or CallerInfo.PRIVATE_NUMBER.
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     * @param type Either Call.OUTGOING_TYPE or Call.INCOMING_TYPE or Call.MISSED_TYPE.
     */
    private void insert(String number, long date, int duration, int type) {
        MatrixCursor.RowBuilder row = mCursor.newRow();
        row.add(mIndex);
        mIndex ++;
        row.add(number);
        if (NOW == date) {
            row.add(new Date().getTime());
        } else {
            row.add(date);
        }
        if (duration < 0) {
            duration = mRnd.nextInt(10 * 60);  // 0 - 10 minutes random.
        }
        row.add(duration);  // duration
        if (mVoicemail != null && mVoicemail.equals(number)) {
            assertEquals(Calls.OUTGOING_TYPE, type);
        }
        row.add(type);  // type
        row.add(TEST_COUNTRY_ISO);  // country ISO
    }

    /**
     * Insert a new private call entry in the test DB.
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     */
    private void insertPrivate(long date, int duration) {
        insert(CallerInfo.PRIVATE_NUMBER, date, duration, Calls.INCOMING_TYPE);
    }

    /**
     * Insert a new unknown call entry in the test DB.
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     */
    private void insertUnknown(long date, int duration) {
        insert(CallerInfo.UNKNOWN_NUMBER, date, duration, Calls.INCOMING_TYPE);
    }

    /**
     * Insert a new voicemail call entry in the test DB.
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     */
    private void insertVoicemail(long date, int duration) {
        // mVoicemail may be null
        if (mVoicemail != null) {
            insert(mVoicemail, date, duration, Calls.OUTGOING_TYPE);
        }
    }

    /**
     * Insert a range [start, end) of random numbers in the DB. For
     * each row, there is a 1/10 probability that the number will be
     * marked as PRIVATE or UNKNOWN or VOICEMAIL. For regular numbers, a number is
     * inserted, its last 4 digits will be the number of the iteration
     * in the range.
     * @param start Of the range.
     * @param end Of the range (excluded).
     * @return An array with 2 booleans [0 = private number, 1 =
     * unknown number, 2 = voicemail] to indicate if at least one
     * private or unknown or voicemail number has been inserted. Since
     * the numbers are random some tests may want to enforce the
     * insertion of such numbers.
     */
    // TODO: Should insert numbers with contact entries too.
    private boolean[] insertRandomRange(int start, int end) {
        boolean[] privateOrUnknownOrVm = new boolean[] {false, false, false};

        for (int i = start; i < end; i++ ) {
            int type = mRnd.nextInt(10);

            if (0 == type) {
                insertPrivate(NOW, RAND_DURATION);
                privateOrUnknownOrVm[0] = true;
            } else if (1 == type) {
                insertUnknown(NOW, RAND_DURATION);
                privateOrUnknownOrVm[1] = true;
            } else if (2 == type) {
                insertVoicemail(NOW, RAND_DURATION);
                privateOrUnknownOrVm[2] = true;
            } else {
                int inout = mRnd.nextBoolean() ? Calls.OUTGOING_TYPE :  Calls.INCOMING_TYPE;
                String number = new Formatter().format("1800123%04d", i).toString();
                insert(number, NOW, RAND_DURATION, inout);
            }
        }
        return privateOrUnknownOrVm;
    }

    /** Asserts that the name text view is shown and contains the given text. */
    private void assertNameIs(CallLogListItemViews views, String name) {
        assertEquals(View.VISIBLE, views.phoneCallDetailsViews.nameView.getVisibility());
        assertEquals(name, views.phoneCallDetailsViews.nameView.getText());
    }

    /** Asserts that the number and label text view contains the given text. */
    private void assertNumberAndLabelAre(CallLogListItemViews views, CharSequence number,
            CharSequence numberLabel) {
        assertEquals(View.VISIBLE, views.phoneCallDetailsViews.numberView.getVisibility());
        final CharSequence expectedText;
        if (numberLabel == null) {
            expectedText = number;
        } else {
            expectedText = numberLabel + " " + number;
        }
        assertEquals(expectedText, views.phoneCallDetailsViews.numberView.getText().toString());
    }

    /** Asserts that the number and label text view is gone. */
    private void assertNumberAndLabelAreGone(CallLogListItemViews views) {
        assertEquals(View.GONE, views.phoneCallDetailsViews.numberView.getVisibility());
    }
}
