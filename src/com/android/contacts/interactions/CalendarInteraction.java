package com.android.contacts.interactions;

import com.android.contacts.R;

import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;

/**
 * Represents a calendar event interaction, wrapping the columns in
 * {@link android.provider.CalendarContract.Attendees}.
 */
public class CalendarInteraction implements ContactInteraction {
    private static final String TAG = CalendarInteraction.class.getSimpleName();

    private static final int CALENDAR_ICON_RES = R.drawable.ic_event_24dp;

    private ContentValues mValues;

    public CalendarInteraction(ContentValues values) {
        mValues = values;
    }

    @Override
    public Intent getIntent() {
        return new Intent(Intent.ACTION_VIEW).setData(
                ContentUris.withAppendedId(Events.CONTENT_URI, getEventId()));
    }

    @Override
    public long getInteractionDate() {
        return getDtstart();
    }

    @Override
    public String getViewHeader(Context context) {
        String title = getTitle();
        if (TextUtils.isEmpty(title)) {
            return context.getResources().getString(R.string.untitled_event);
        }
        return title;
    }

    @Override
    public String getViewBody(Context context) {
        return null;
    }

    @Override
    public String getViewFooter(Context context) {
        // Pulled from com.android.calendar.EventInfoFragment.updateEvent(View view)
        // TODO: build callback to update time zone if different than preferences
        String localTimezone = Time.getCurrentTimezone();

        String displayedDatetime = CalendarInteractionUtils.getDisplayedDatetime(
                getDtstart(), getDtend(), System.currentTimeMillis(), localTimezone,
                getAllDay(), context);

        return displayedDatetime;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getResources().getDrawable(CALENDAR_ICON_RES);
    }

    @Override
    public Drawable getBodyIcon(Context context) {
        return null;
    }

    @Override
    public Drawable getFooterIcon(Context context) {
        return null;
    }

    public String getAttendeeEmail() {
        return mValues.getAsString(Attendees.ATTENDEE_EMAIL);
    }

    public String getAttendeeIdentity() {
        return mValues.getAsString(Attendees.ATTENDEE_IDENTITY);
    }

    public String getAttendeeIdNamespace() {
        return mValues.getAsString(Attendees.ATTENDEE_ID_NAMESPACE);
    }

    public String getAttendeeName() {
        return mValues.getAsString(Attendees.ATTENDEE_NAME);
    }

    public int getAttendeeRelationship() {
        return mValues.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP);
    }

    public int getAttendeeStatus() {
        return mValues.getAsInteger(Attendees.ATTENDEE_STATUS);
    }

    public int getAttendeeType() {
        return mValues.getAsInteger(Attendees.ATTENDEE_TYPE);
    }

    public int getEventId() {
        return mValues.getAsInteger(Attendees.EVENT_ID);
    }

    public int getAccessLevel() {
        return mValues.getAsInteger(Attendees.ACCESS_LEVEL);
    }

    public boolean getAllDay() {
        return mValues.getAsBoolean(Attendees.ALL_DAY);
    }

    public int getAvailability() {
        return mValues.getAsInteger(Attendees.AVAILABILITY);
    }

    public int getCalendarId() {
        return mValues.getAsInteger(Attendees.CALENDAR_ID);
    }

    public boolean getCanInviteOthers() {
        return mValues.getAsBoolean(Attendees.CAN_INVITE_OTHERS);
    }

    public String getCustomAppPackage() {
        return mValues.getAsString(Attendees.CUSTOM_APP_PACKAGE);
    }

    public String getCustomAppUri() {
        return mValues.getAsString(Attendees.CUSTOM_APP_URI);
    }

    public String getDescription() {
        return mValues.getAsString(Attendees.DESCRIPTION);
    }

    public int getDisplayColor() {
        return mValues.getAsInteger(Attendees.DISPLAY_COLOR);
    }

    public long getDtend() {
        return mValues.getAsLong(Attendees.DTEND);
    }

    public long getDtstart() {
        return mValues.getAsLong(Attendees.DTSTART);
    }

    public String getDuration() {
        return mValues.getAsString(Attendees.DURATION);
    }

    public int getEventColor() {
        return mValues.getAsInteger(Attendees.EVENT_COLOR);
    }

    public String getEventColorKey() {
        return mValues.getAsString(Attendees.EVENT_COLOR_KEY);
    }

    public String getEventEndTimezone() {
        return mValues.getAsString(Attendees.EVENT_END_TIMEZONE);
    }

    public String getEventLocation() {
        return mValues.getAsString(Attendees.EVENT_LOCATION);
    }

    public String getExdate() {
        return mValues.getAsString(Attendees.EXDATE);
    }

    public String getExrule() {
        return mValues.getAsString(Attendees.EXRULE);
    }

    public boolean getGuestsCanInviteOthers() {
        return mValues.getAsBoolean(Attendees.GUESTS_CAN_INVITE_OTHERS);
    }

    public boolean getGuestsCanModify() {
        return mValues.getAsBoolean(Attendees.GUESTS_CAN_MODIFY);
    }

    public boolean getGuestsCanSeeGuests() {
        return mValues.getAsBoolean(Attendees.GUESTS_CAN_SEE_GUESTS);
    }

    public boolean getHasAlarm() {
        return mValues.getAsBoolean(Attendees.HAS_ALARM);
    }

    public boolean getHasAttendeeData() {
        return mValues.getAsBoolean(Attendees.HAS_ATTENDEE_DATA);
    }

    public boolean getHasExtendedProperties() {
        return mValues.getAsBoolean(Attendees.HAS_EXTENDED_PROPERTIES);
    }

    public String getIsOrganizer() {
        return mValues.getAsString(Attendees.IS_ORGANIZER);
    }

    public long getLastDate() {
        return mValues.getAsLong(Attendees.LAST_DATE);
    }

    public boolean getLastSynced() {
        return mValues.getAsBoolean(Attendees.LAST_SYNCED);
    }

    public String getOrganizer() {
        return mValues.getAsString(Attendees.ORGANIZER);
    }

    public boolean getOriginalAllDay() {
        return mValues.getAsBoolean(Attendees.ORIGINAL_ALL_DAY);
    }

    public String getOriginalId() {
        return mValues.getAsString(Attendees.ORIGINAL_ID);
    }

    public long getOriginalInstanceTime() {
        return mValues.getAsLong(Attendees.ORIGINAL_INSTANCE_TIME);
    }

    public String getOriginalSyncId() {
        return mValues.getAsString(Attendees.ORIGINAL_SYNC_ID);
    }

    public String getRdate() {
        return mValues.getAsString(Attendees.RDATE);
    }

    public String getRrule() {
        return mValues.getAsString(Attendees.RRULE);
    }

    public int getSelfAttendeeStatus() {
        return mValues.getAsInteger(Attendees.SELF_ATTENDEE_STATUS);
    }

    public int getStatus() {
        return mValues.getAsInteger(Attendees.STATUS);
    }

    public String getTitle() {
        return mValues.getAsString(Attendees.TITLE);
    }

    public String getUid2445() {
        return mValues.getAsString(Attendees.UID_2445);
    }
}
