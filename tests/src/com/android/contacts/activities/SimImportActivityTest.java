package com.android.contacts.activities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.Suppress;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import com.android.contacts.database.SimContactDao;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.tests.AccountsTestHelper;
import com.android.contacts.tests.ContactsMatchers;
import com.android.contacts.tests.FakeSimContactDao;
import com.android.contacts.tests.StringableCursor;
import com.google.common.base.Functions;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.contacts.tests.ContactsMatchers.DataCursor.hasMimeType;
import static com.android.contacts.tests.ContactsMatchers.hasRowMatching;
import static com.android.contacts.tests.ContactsMatchers.hasValueForColumn;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertTrue;

/**
 * UI Tests for {@link SimImportActivity}
 *
 * These should probably be converted to espresso tests because espresso does a better job of
 * waiting for the app to be idle once espresso library is added
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
@TargetApi(Build.VERSION_CODES.M)
public class SimImportActivityTest {

    public static final int TIMEOUT = 1000;
    private Context mContext;
    private UiDevice mDevice;
    private Instrumentation mInstrumentation;
    private FakeSimContactDao mDao;
    private AccountsTestHelper mAccountHelper;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDao = new FakeSimContactDao();
        SimContactDao.setFactoryForTest(Functions.<SimContactDao>constant(mDao));
        mDevice = UiDevice.getInstance(mInstrumentation);

        // Add some test accounts so that account picker is exercised
        mAccountHelper = new AccountsTestHelper();
        mAccountHelper.addTestAccount(mAccountHelper.generateAccountName("SimImportActivity1_"));
        mAccountHelper.addTestAccount(mAccountHelper.generateAccountName("SimImportActivity2_"));
        mAccountHelper.addTestAccount(mAccountHelper.generateAccountName("SimImportActivity3_"));
    }

    @After
    public void tearDown() throws Exception {
        SimContactDao.setFactoryForTest(SimContactDao.DEFAULT_FACTORY);
        mAccountHelper.cleanup();
        AccountTypeManager.setInstanceForTest(null);
    }

    @Test
    public void shouldDisplaySimContacts() {
        mDao.addSim(someSimCard(),
                        new SimContact(1, "Sim One", "5550101"),
                        new SimContact(2, "Sim Two", null),
                        new SimContact(3, null, "5550103")
                );
        mInstrumentation.startActivitySync(new Intent(mContext, SimImportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        mDevice.waitForIdle();

        assertTrue(mDevice.hasObject(By.text("Sim One")));
        assertTrue(mDevice.hasObject(By.text("Sim Two")));
        assertTrue(mDevice.hasObject(By.text("5550103")));
}

    @Test
    public void shouldHaveEmptyState() {
        mDao.addSim(someSimCard());

        mInstrumentation.startActivitySync(new Intent(mContext, SimImportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        mDevice.waitForIdle();

        assertTrue(mDevice.hasObject(By.textStartsWith("No contacts")));
    }

    @Test
    public void smokeRotateInEmptyState() {
        mDao.addSim(someSimCard());

        final Activity activity = mInstrumentation.startActivitySync(
                new Intent(mContext, SimImportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        assertTrue(mDevice.wait(Until.hasObject(By.textStartsWith("No contacts")), TIMEOUT));

        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mDevice.waitForIdle();

        assertTrue(mDevice.hasObject(By.textStartsWith("No contacts")));
    }

    @Test
    public void smokeRotateInNonEmptyState() throws Exception {
        mDao.addSim(someSimCard(), new SimContact(1, "Name One", "5550101"),
                new SimContact(2, "Name Two", "5550102"));

        final Activity activity = mInstrumentation.startActivitySync(
                new Intent(mContext, SimImportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        assertTrue(mDevice.wait(Until.hasObject(By.textStartsWith("Name One")), TIMEOUT));

        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mDevice.waitForIdle();

        assertTrue(mDevice.hasObject(By.textStartsWith("Name One")));
    }


    // TODO: fix this test. This doesn't work because AccountTypeManager returns a stale account
    // list (it doesn't contain the accounts added during the current test run).
    // Could use MockAccountTypeManager but probably ought to look at improving how
    // AccountTypeManager updates it's account list.
    @Suppress
    @Test
    public void selectionsAreImportedAndDisabledOnSubsequentViews() throws Exception {
        // Clear out the instance so that it will have the most recent accounts when reloaded
        AccountTypeManager.setInstanceForTest(null);

        final AccountWithDataSet targetAccount = mAccountHelper.addTestAccount(
                mAccountHelper.generateAccountName("SimImportActivity_target_"));

        mDao.addSim(someSimCard(),
                new SimContact(1, "Import One", "5550101"),
                new SimContact(2, "Skip Two", "5550102"),
                new SimContact(3, "Import Three", "5550103"),
                new SimContact(4, "Skip Four", "5550104"),
                new SimContact(5, "Skip Five", "5550105"),
                new SimContact(6, "Import Six", "5550106"));

        final Activity activity = mInstrumentation.startActivitySync(
                new Intent(mContext, SimImportActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        assertTrue(mDevice.wait(Until.hasObject(By.desc("Show more")), TIMEOUT));

        mDevice.findObject(By.desc("Show more")).clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.findObject(By.textStartsWith("SimImportActivity_target_")).click();
        mDevice.waitForIdle();

        mDevice.findObject(By.text("Skip Two")).click();
        mDevice.findObject(By.text("Skip Five")).click();

        assertTrue(mDevice.hasObject(By.text("Skip Two").checked(false)));
        assertTrue(mDevice.hasObject(By.text("Skip Five").checked(false)));

        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mDevice.wait(Until.hasObject(By.text("Import One")), TIMEOUT);
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        mDevice.wait(Until.hasObject(By.text("Import One")), TIMEOUT);

        mDevice.findObject(By.text("IMPORT").clickable(true)).click();
        mDevice.waitForIdle();

        final Cursor cursor = new StringableCursor(
                mContext.getContentResolver().query(Data.CONTENT_URI, null,
                        ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " +
                                ContactsContract.RawContacts.ACCOUNT_TYPE+ "=?",
                        new String[] {
                                targetAccount.name,
                                targetAccount.type
                        }, null));
        // 3 contacts imported with one row for name and one for phone
        assertThat(cursor, ContactsMatchers.hasCount(3 * 2));

        assertThat(cursor, hasRowMatching(allOf(
                hasMimeType(Phone.CONTENT_ITEM_TYPE),
                hasValueForColumn(Phone.DISPLAY_NAME, "Import One"),
                hasValueForColumn(Phone.NUMBER, "5550101")
        )));
        assertThat(cursor, hasRowMatching(allOf(
                hasMimeType(Phone.CONTENT_ITEM_TYPE),
                hasValueForColumn(Phone.DISPLAY_NAME, "Import Three"),
                hasValueForColumn(Phone.NUMBER, "5550103")
        )));
        assertThat(cursor, hasRowMatching(allOf(
                hasMimeType(Phone.CONTENT_ITEM_TYPE),
                hasValueForColumn(Phone.DISPLAY_NAME, "Import Six"),
                hasValueForColumn(Phone.NUMBER, "5550106")
        )));

        cursor.close();


        mInstrumentation.startActivitySync(
                new Intent(mContext, SimImportActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        assertTrue(mDevice.wait(Until.hasObject(By.text("Import One")), TIMEOUT));

        mDevice.findObject(By.descStartsWith("Saving to")).clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.findObject(By.textContains(targetAccount.name)).click();
        mDevice.waitForIdle();

        assertTrue(mDevice.hasObject(By.text("Import One").checked(false).enabled(false)));
        assertTrue(mDevice.hasObject(By.text("Import Three").checked(false).enabled(false)));
        assertTrue(mDevice.hasObject(By.text("Import Six").checked(false).enabled(false)));
    }

    private SimCard someSimCard() {
        return new SimCard("id", 1, "Carrier", "SIM", "18005550101", "us");
    }

    private Matcher<SimContact> withContactId(final long id) {
        return new BaseMatcher<SimContact>() {
            @Override
            public boolean matches(Object o) {
                return (o instanceof SimContact) && ((SimContact) o).getId() == id;
            }


            @Override
            public void describeTo(Description description) {
                description.appendText("Expected SimContact with id=" + id);
            }
        };
    }
}
