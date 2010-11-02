/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.views.editor;

import com.android.contacts.R;
import com.android.contacts.datepicker.DatePicker;
import com.android.contacts.datepicker.DatePickerDialog;
import com.android.contacts.datepicker.DatePickerDialog.OnDateSetListener;
import com.android.contacts.model.BaseAccountType.DataKind;
import com.android.contacts.model.BaseAccountType.EditField;
import com.android.contacts.model.BaseAccountType.EventEditType;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.util.DateUtils;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;

/**
 * Editor that allows editing Events using a {@link DatePickerDialog}
 */
public class EventFieldEditorView extends LabeledEditorView {
    /**
     * Exchange requires 8:00 for birthdays
     */
    private final int DEFAULT_HOUR = 8;

    private TextView mDateView;

    public EventFieldEditorView(Context context) {
        super(context);
    }

    public EventFieldEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EventFieldEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        int l1 = getPaddingLeft();
        int t1 = getPaddingTop();
        int r1 = getMeasuredWidth() - getPaddingRight();
        int b1 = getMeasuredHeight() - getPaddingBottom();

        // Fields
        // Subtract buttons left and right if necessary
        final int l2 = (getLabel() != null) ? l1 + getLabel().getMeasuredWidth() : l1;
        final int r2 = r1 - ((getDelete() != null) ? getDelete().getMeasuredWidth() : 0);
        if (mDateView != null) mDateView.layout(l2, t1, r2, b1);
    }

    @Override
    protected int getEditorHeight() {
        return mDateView != null ? mDateView.getMeasuredHeight() : 0;
    }

    @Override
    protected void requestFocusForFirstEditField() {
        if (mDateView != null) mDateView.requestFocus();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (mDateView != null) mDateView.setEnabled(enabled);
    }

    @Override
    public void setValues(DataKind kind, ValuesDelta entry, EntityDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        if (kind.fieldList.size() != 1) throw new IllegalStateException("kind must have 1 field");
        super.setValues(kind, entry, state, readOnly, vig);

        if (mDateView == null) {
            mDateView = new TextView(getContext(), null, android.R.attr.textAppearanceMedium);
            mDateView.setFocusable(true);
            mDateView.setBackgroundResource(R.drawable.edit_button_field_background);
            mDateView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            mDateView.setEnabled(!readOnly);
            mDateView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDialog(R.id.dialog_event_date_picker);
                }
            });
            addView(mDateView);
        }

        rebuildDateView();
    }

    private void rebuildDateView() {
        final EditField editField = getKind().fieldList.get(0);
        final String column = editField.column;
        final String data = DateUtils.formatDate(getContext(), getEntry().getAsString(column));
        mDateView.setText(data);
    }

    @Override
    public Dialog createDialog(Bundle bundle) {
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        int dialogId = bundle.getInt(DIALOG_ID_KEY);
        switch (dialogId) {
            case R.id.dialog_event_date_picker:
                return createDatePickerDialog();
            default:
                return super.createDialog(bundle);
        }
    }

    @Override
    protected EventEditType getType() {
        return (EventEditType) super.getType();
    }

    @Override
    protected void onLabelRebuilt() {
        // if we changed to a type that requires a year, ensure that it is actually set
        final String column = getKind().fieldList.get(0).column;
        final String oldValue = getEntry().getAsString(column);
        final DataKind kind = getKind();

        final Calendar calendar = Calendar.getInstance();
        final int defaultYear = calendar.get(Calendar.YEAR);

        // Check whether the year is optional
        final boolean isYearOptional = getType().isYearOptional();

        if (!isYearOptional) {
            final ParsePosition position = new ParsePosition(0);
            final Date date2 = kind.dateFormatWithoutYear.parse(oldValue, position);

            // Don't understand the date, lets not change it
            if (date2 == null) return;

            // This value is missing the year. Add it now
            calendar.setTime(date2);
            calendar.set(defaultYear, calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH), DEFAULT_HOUR, 0, 0);

            onFieldChanged(column, kind.dateFormatWithYear.format(calendar.getTime()));
            rebuildDateView();
        }
    }

    /**
     * Prepare dialog for entering a date
     */
    private Dialog createDatePickerDialog() {
        final String column = getKind().fieldList.get(0).column;
        final String oldValue = getEntry().getAsString(column);
        final DataKind kind = getKind();

        final Calendar calendar = Calendar.getInstance();
        final int defaultYear = calendar.get(Calendar.YEAR);

        // Check whether the year is optional
        final boolean isYearOptional = getType().isYearOptional();

        final int oldYear, oldMonth, oldDay;
        if (TextUtils.isEmpty(oldValue)) {
            // Default to January first, 30 years ago
            oldYear = defaultYear;
            oldMonth = 0;
            oldDay = 1;
        } else {
            final ParsePosition position = new ParsePosition(0);
            // Try parsing with year
            final Date date1 = kind.dateFormatWithYear.parse(oldValue, position);
            if (date1 != null) {
                calendar.setTime(date1);
                oldYear = calendar.get(Calendar.YEAR);
                oldMonth = calendar.get(Calendar.MONTH);
                oldDay = calendar.get(Calendar.DAY_OF_MONTH);
            } else {
                final Date date2 = kind.dateFormatWithoutYear.parse(oldValue, position);
                // Don't understand the date, lets not change it
                if (date2 == null) return null;
                calendar.setTime(date2);
                oldYear = isYearOptional ? 0 : defaultYear;
                oldMonth = calendar.get(Calendar.MONTH);
                oldDay = calendar.get(Calendar.DAY_OF_MONTH);
            }
        }
        final OnDateSetListener callBack = new OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                if (year == 0 && !isYearOptional) throw new IllegalStateException();
                final Calendar outCalendar = Calendar.getInstance();

                // If no year specified, set it to 1900. The format string will ignore that year
                // For formats other than Exchange, the time of the day is ignored
                outCalendar.clear();
                outCalendar.set(year == 0 ? 1900 : year, monthOfYear, dayOfMonth,
                        DEFAULT_HOUR, 0, 0);

                final String resultString;
                if (year == 0) {
                    resultString = kind.dateFormatWithoutYear.format(outCalendar.getTime());
                } else {
                    resultString = kind.dateFormatWithYear.format(outCalendar.getTime());
                }
                onFieldChanged(column, resultString);
                rebuildDateView();
            }
        };
        final DatePickerDialog resultDialog = new DatePickerDialog(getContext(), callBack,
                oldYear, oldMonth, oldDay, isYearOptional);
        return resultDialog;
    }
}
