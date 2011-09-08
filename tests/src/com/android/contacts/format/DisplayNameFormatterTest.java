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

package com.android.contacts.format;


import android.provider.ContactsContract;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.TextView;

/**
 * Unit tests for {@link DisplayNameFormatter}.
 */
@SmallTest
public class DisplayNameFormatterTest extends AndroidTestCase {
    private static final int TEST_PREFIX_HIGHLIGHT_COLOR = 0xFF0000;
    /** The HTML code used to mark the start of the highlighted part. */
    private static final String START = "<font color =\"#1ff0000\">";
    /** The HTML code used to mark the end of the highlighted part. */
    private static final String END = "</font>";

    private PrefixHighlighter mPrefixHighlighter;
    /** The object under test. */
    private DisplayNameFormatter mDisplayNameFormatter;
    /** The view to on which the text is set. */
    private TextView mView;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPrefixHighlighter = new PrefixHighlighter(TEST_PREFIX_HIGHLIGHT_COLOR);
        mDisplayNameFormatter = new DisplayNameFormatter(mPrefixHighlighter);
        mView = new TextView(getContext());
        // This guarantees that the text will be stored as a Spannable so that we can determine
        // which styles have been applied to it.
        mView.setText("", TextView.BufferType.SPANNABLE);
    }

    public void testSetDisplayName_Simple() {
        setNames("John Doe", "Doe John");
        setDisplayName();
        SpannedTestUtils.checkHtmlText("John Doe", mView);
        setNames("Jean Pierre Doe", "Doe Jean Pierre");
        setDisplayName();
        SpannedTestUtils.checkHtmlText("Jean Pierre Doe", mView);
        setNames("John Doe Smith", "Doe Smith John");
        setDisplayName();
        SpannedTestUtils.checkHtmlText("John Doe Smith", mView);
    }
    public void testSetDisplayName_AccidentalOverlap() {
        // This is probably not what we want, but we assume that the two names differ only in the
        // order in which the two components are listed.
        setNames("Johnson John", "Johnson Smith");
        setDisplayName();
        SpannedTestUtils.checkHtmlText("Johnson John", mView);
    }

    public void testSetDisplayName_Reversed() {
        setNames("John Doe", "Doe John");
        setDisplayNameReversed();
        SpannedTestUtils.checkHtmlText("John Doe", mView);
        setNames("Jean Pierre Doe", "Doe Jean Pierre");
        setDisplayNameReversed();
        SpannedTestUtils.checkHtmlText("Jean Pierre Doe", mView);
        setNames("John Doe Smith", "Doe Smith John");
        setDisplayNameReversed();
        SpannedTestUtils.checkHtmlText("John Doe Smith", mView);
    }

    public void testSetDisplayName_NoOverlap() {
        setNames("John Smith", "Doe Albert");
        setDisplayName();
        SpannedTestUtils.checkHtmlText("John Smith", mView);
    }

    public void testSetDisplayName_Prefix() {
        setNames("John Doe", "Doe John");
        setDisplayNameWithPrefix("DO");
        SpannedTestUtils.checkHtmlText("John " + START + "Do" + END + "e", mView);
    }

    public void testSetDisplayName_PrefixFirstName() {
        setNames("John Doe", "Doe John");
        setDisplayNameWithPrefix("JO");
        SpannedTestUtils.checkHtmlText(START + "Jo" + END + "hn Doe", mView);
    }

    public void testSetDisplayName_PrefixMiddleName() {
        setNames("John Paul Doe", "Doe John Paul");
        setDisplayNameWithPrefix("PAU");
        SpannedTestUtils.checkHtmlText("John " + START + "Pau" + END + "l Doe",
                mView);
    }

    public void testSetDisplayName_ReversedPrefix() {
        setNames("John Doe", "Doe John");
        setDisplayNameReversedWithPrefix("DO");
        SpannedTestUtils.checkHtmlText("John " + START + "Do" + END + "e", mView);
    }

    public void testSetDisplayName_Empty() {
        setNames("", "");
        setDisplayName();
        SpannedTestUtils.checkHtmlText("", mView);
    }

    public void testSetDisplayName_Unknown() {
        mDisplayNameFormatter.setUnknownNameText("unknown");
        setNames("", "");
        setDisplayName();
        SpannedTestUtils.checkHtmlText("unknown", mView);
    }

    /**
     * Sets the name and alternate name on the formatter.
     *
     * @param name the name to be display
     * @param alternateName the alternate name to be displayed
     */
    private void setNames(String name, String alternateName) {
        FormatUtils.copyToCharArrayBuffer(name, mDisplayNameFormatter.getNameBuffer());
        FormatUtils.copyToCharArrayBuffer(alternateName,
                mDisplayNameFormatter.getAlternateNameBuffer());
    }

    /**
     * Sets the display name on the text view.
     */
    private void setDisplayName() {
        mDisplayNameFormatter.setDisplayName(mView,
                ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY, false, null);
    }

    /**
     * Sets the display name on the text view using the reverted order.
     */
    private void setDisplayNameReversed() {
        mDisplayNameFormatter.setDisplayName(mView,
                ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE, false, null);
    }

    /**
     * Sets the display name on the text view with prefix highlighting enabled.
     */
    private void setDisplayNameWithPrefix(String prefix) {
        mDisplayNameFormatter.setDisplayName(mView,
                ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY, false, prefix.toCharArray());
    }

    /**
     * Sets the display name reversed on the text view with prefix highlighting enabled.
     */
    private void setDisplayNameReversedWithPrefix(String prefix) {
        mDisplayNameFormatter.setDisplayName(mView,
                ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE, false,
                prefix.toCharArray());
    }

    /**
     * Sets the display name on the text view with highlighting enabled.
     */
    private void setDisplayNameWithHighlighting() {
        mDisplayNameFormatter.setDisplayName(mView,
                ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY, true, null);
    }
}
