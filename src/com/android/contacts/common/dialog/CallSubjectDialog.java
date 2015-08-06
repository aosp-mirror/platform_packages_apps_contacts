/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.common.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.R;
import com.android.contacts.common.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements a dialog which prompts for a call subject for an outgoing call.
 */
public class CallSubjectDialog extends DialogFragment {
    private static final String TAG = "CallSubjectDialog";
    private static final String FRAGMENT_TAG = "callSubject";
    private static final int CALL_SUBJECT_LIMIT = 16;
    private static final int CALL_SUBJECT_HISTORY_SIZE = 5;

    private static final int REQUEST_SUBJECT = 1001;

    public static final String PREF_KEY_SUBJECT_HISTORY_COUNT = "subject_history_count";
    public static final String PREF_KEY_SUBJECT_HISTORY_ITEM = "subject_history_item";

    /**
     * Fragment argument bundle keys:
     */
    public static final String ARG_PHOTO_ID = "PHOTO_ID";
    public static final String ARG_PHOTO_URI = "PHOTO_URI";
    public static final String ARG_CONTACT_URI = "CONTACT_URI";
    public static final String ARG_NAME_OR_NUMBER = "NAME_OR_NUMBER";
    public static final String ARG_IS_BUSINESS = "IS_BUSINESS";
    public static final String ARG_NUMBER = "NUMBER";
    public static final String ARG_DISPLAY_NUMBER = "DISPLAY_NUMBER";
    public static final String ARG_NUMBER_LABEL = "NUMBER_LABEL";
    public static final String ARG_PHONE_ACCOUNT_HANDLE = "PHONE_ACCOUNT_HANDLE";

    private Context mContext;
    private QuickContactBadge mContactPhoto;
    private TextView mNameView;
    private TextView mNumberView;
    private EditText mCallSubjectView;
    private TextView mCharacterLimitView;
    private View mHistoryButton;
    private View mSendAndCallButton;

    private int mLimit = CALL_SUBJECT_LIMIT;
    private int mPhotoSize;
    private SharedPreferences mPrefs;
    private List<String> mSubjectHistory;

    private long mPhotoID;
    private Uri mPhotoUri;
    private Uri mContactUri;
    private String mNameOrNumber;
    private boolean mIsBusiness;
    private String mNumber;
    private String mDisplayNumber;
    private String mNumberLabel;
    private PhoneAccountHandle mPhoneAccountHandle;

    /**
     * Handles changes to the text in the subject box.  Ensures the character limit is updated.
     */
    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // no-op
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateCharacterLimit();
        }

        @Override
        public void afterTextChanged(Editable s) {
            // no-op
        }
    };

    /**
     * Handles displaying the list of past call subjects.
     */
    private final View.OnClickListener mHistoryOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            hideSoftKeyboard(mContext, mCallSubjectView);
            Intent intent = new Intent(mContext, CallSubjectHistory.class);
            setTargetFragment(CallSubjectDialog.this, REQUEST_SUBJECT);
            startActivityForResult(intent, REQUEST_SUBJECT);
        }
    };

    /**
     * Handles starting a call with a call subject specified.
     */
    private final View.OnClickListener mSendAndCallOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            hideSoftKeyboard(mContext, mCallSubjectView);
            String subject = mCallSubjectView.getText().toString();
            Intent intent = CallUtil.getCallWithSubjectIntent(mNumber, mPhoneAccountHandle,
                    subject);

            final TelecomManager tm =
                    (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            tm.placeCall(intent.getData(), intent.getExtras());

            mSubjectHistory.add(subject);
            saveSubjectHistory(mSubjectHistory);
            getDialog().dismiss();
        }
    };

    /**
     * Show the call subhect dialog given a phone number to dial (e.g. from the dialpad).
     *
     * @param activity The activity.
     * @param number The number to dial.
     */
    public static void start(Activity activity, String number) {
        start(activity,
                -1 /* photoId */,
                null /* photoUri */,
                null /* contactUri */,
                number /* nameOrNumber */,
                false /* isBusiness */,
                number /* number */,
                null /* displayNumber */,
                null /* numberLabel */,
                null /* phoneAccountHandle */);
    }

    /**
     * Creates a call subject dialog.
     *
     * @param activity The current activity.
     * @param photoId The photo ID (used to populate contact photo).
     * @param photoUri The photo Uri (used to populate contact photo).
     * @param contactUri The Contact URI (used so quick contact can be invoked from contact photo).
     * @param nameOrNumber The name or number of the callee.
     * @param isBusiness {@code true} if a business is being called (used for contact photo).
     * @param number The raw number to dial.
     * @param displayNumber The number to dial, formatted for display.
     * @param numberLabel The label for the number (if from a contact).
     * @param phoneAccountHandle The phone account handle.
     */
    public static void start(Activity activity, long photoId, Uri photoUri, Uri contactUri,
            String nameOrNumber, boolean isBusiness, String number, String displayNumber,
            String numberLabel, PhoneAccountHandle phoneAccountHandle) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_PHOTO_ID, photoId);
        arguments.putParcelable(ARG_PHOTO_URI, photoUri);
        arguments.putParcelable(ARG_CONTACT_URI, contactUri);
        arguments.putString(ARG_NAME_OR_NUMBER, nameOrNumber);
        arguments.putBoolean(ARG_IS_BUSINESS, isBusiness);
        arguments.putString(ARG_NUMBER, number);
        arguments.putString(ARG_DISPLAY_NUMBER, displayNumber);
        arguments.putString(ARG_NUMBER_LABEL, numberLabel);
        arguments.putParcelable(ARG_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        start(activity, arguments);
    }

    /**
     * Shows the call subject dialog given a Bundle containing all the arguments required to
     * display the dialog (e.g. from Quick Contacts).
     *
     * @param activity The activity.
     * @param arguments The arguments bundle.
     */
    public static void start(Activity activity, Bundle arguments) {
        final FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
        final CallSubjectDialog fragment = new CallSubjectDialog();
        fragment.setArguments(arguments);
        fragment.show(ft, FRAGMENT_TAG);
    }

    /**
     * Creates the dialog, inflating the layout and populating it with the name and phone number.
     *
     * @param savedInstanceState The last saved instance state of the Fragment,
     * or null if this is a freshly created Fragment.
     *
     * @return Dialog instance.
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mContext = getActivity();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPhotoSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.call_subject_dialog_contact_photo_size);
        readArguments();
        mSubjectHistory = loadSubjectHistory(mPrefs);

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_call_subject, null);

        mContactPhoto = (QuickContactBadge) view.findViewById(R.id.contact_photo);
        mNameView = (TextView) view.findViewById(R.id.name);
        mNumberView = (TextView) view.findViewById(R.id.number);
        mCallSubjectView = (EditText) view.findViewById(R.id.call_subject);
        mCallSubjectView.addTextChangedListener(mTextWatcher);
        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(mLimit);
        mCallSubjectView.setFilters(filters);

        mCharacterLimitView = (TextView) view.findViewById(R.id.character_limit);
        mHistoryButton = view.findViewById(R.id.history_button);
        mHistoryButton.setOnClickListener(mHistoryOnClickListener);
        mHistoryButton.setVisibility(mSubjectHistory.isEmpty() ? View.GONE : View.VISIBLE);
        mSendAndCallButton = view.findViewById(R.id.send_and_call_button);
        mSendAndCallButton.setOnClickListener(mSendAndCallOnClickListener);

        showSoftKeyboard(mContext, mCallSubjectView);
        updateContactInfo();
        updateCharacterLimit();

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
        dialogBuilder.setView(view);
        return dialogBuilder.create();
    }

    /**
     * Populates the contact info fields based on the current contact information.
     */
    private void updateContactInfo() {
        if (mContactUri != null) {
            setPhoto(mPhotoID, mPhotoUri, mContactUri, mNameOrNumber, mIsBusiness);
        } else {
            mContactPhoto.setVisibility(View.GONE);
        }
        mNameView.setText(mNameOrNumber);
        if (!TextUtils.isEmpty(mNumberLabel) && !TextUtils.isEmpty(mDisplayNumber)) {
            mNumberView.setVisibility(View.VISIBLE);
            mNumberView.setText(mContext.getString(R.string.call_subject_type_and_number, mNumberLabel,
                    mDisplayNumber));
        } else {
            mNumberView.setVisibility(View.GONE);
            mNumberView.setText(null);
        }
    }

    /**
     * Reads arguments from the fragment arguments and populates the necessary instance variables.
     */
    private void readArguments() {
        Bundle arguments = getArguments();
        if (arguments == null) {
            Log.e(TAG, "Arguments cannot be null.");
            return;
        }
        mPhotoID = arguments.getLong(ARG_PHOTO_ID);
        mPhotoUri = arguments.getParcelable(ARG_PHOTO_URI);
        mContactUri = arguments.getParcelable(ARG_CONTACT_URI);
        mNameOrNumber = arguments.getString(ARG_NAME_OR_NUMBER);
        mIsBusiness = arguments.getBoolean(ARG_IS_BUSINESS);
        mNumber = arguments.getString(ARG_NUMBER);
        mDisplayNumber = arguments.getString(ARG_DISPLAY_NUMBER);
        mNumberLabel = arguments.getString(ARG_NUMBER_LABEL);
        mPhoneAccountHandle = arguments.getParcelable(ARG_PHONE_ACCOUNT_HANDLE);
    }

    /**
     * Updates the character limit display, coloring the text RED when the limit is reached or
     * exceeded.
     */
    private void updateCharacterLimit() {
        int length = mCallSubjectView.length();
        mCharacterLimitView.setText(
                mContext.getString(R.string.call_subject_limit, length, mLimit));
        if (length >= mLimit) {
            mCharacterLimitView.setTextColor(mContext.getResources().getColor(
                    R.color.call_subject_limit_exceeded));
        } else {
            mCharacterLimitView.setTextColor(mContext.getResources().getColor(
                    R.color.dialtacts_secondary_text_color));
        }
    }

    /**
     * Sets the photo on the quick contact photo.
     *
     * @param photoId
     * @param photoUri
     * @param contactUri
     * @param displayName
     * @param isBusiness
     */
    private void setPhoto(long photoId, Uri photoUri, Uri contactUri, String displayName,
            boolean isBusiness) {
        mContactPhoto.assignContactUri(contactUri);
        mContactPhoto.setOverlay(null);

        int contactType;
        if (isBusiness) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        } else {
            contactType = ContactPhotoManager.TYPE_DEFAULT;
        }

        String lookupKey = null;
        if (contactUri != null) {
            lookupKey = UriUtils.getLookupKeyFromUri(contactUri);
        }

        ContactPhotoManager.DefaultImageRequest
                request = new ContactPhotoManager.DefaultImageRequest(
                displayName, lookupKey, contactType, true /* isCircular */);

        if (photoId == 0 && photoUri != null) {
            ContactPhotoManager.getInstance(mContext).loadPhoto(mContactPhoto, photoUri,
                    mPhotoSize, false /* darkTheme */, true /* isCircular */, request);
        } else {
            ContactPhotoManager.getInstance(mContext).loadThumbnail(mContactPhoto, photoId,
                    false /* darkTheme */, true /* isCircular */, request);
        }
    }

    /**
     * Loads the subject history from shared preferences.
     *
     * @param prefs Shared preferences.
     * @return List of subject history strings.
     */
    public static List<String> loadSubjectHistory(SharedPreferences prefs) {
        int historySize = prefs.getInt(PREF_KEY_SUBJECT_HISTORY_COUNT, 0);
        List<String> subjects = new ArrayList(historySize);

        for (int ix = 0 ; ix < historySize; ix++) {
            String historyItem = prefs.getString(PREF_KEY_SUBJECT_HISTORY_ITEM + ix, null);
            if (!TextUtils.isEmpty(historyItem)) {
                subjects.add(historyItem);
            }
        }

        return subjects;
    }

    /**
     * Saves the subject history list to shared prefs, removing older items so that there are only
     * {@link #CALL_SUBJECT_HISTORY_SIZE} items at most.
     *
     * @param history The history.
     */
    private void saveSubjectHistory(List<String> history) {
        // Remove oldest subject(s).
        while (history.size() > CALL_SUBJECT_HISTORY_SIZE) {
            history.remove(0);
        }

        SharedPreferences.Editor editor = mPrefs.edit();
        int historyCount = 0;
        for (String subject : history) {
            if (!TextUtils.isEmpty(subject)) {
                editor.putString(PREF_KEY_SUBJECT_HISTORY_ITEM + historyCount,
                        subject);
                historyCount++;
            }
        }
        editor.putInt(PREF_KEY_SUBJECT_HISTORY_COUNT, historyCount);
        editor.apply();
    }

    /**
     * Handles results from the CallSubjectHistory activity.
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode The integer result code returned by the child activity
     *                   through its setResult().
     * @param data An Intent, which can return result data to the caller
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_SUBJECT || data == null) {
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        String chosenSubject = data.getStringExtra(CallSubjectHistory.EXTRA_CHOSEN_SUBJECT);
        mCallSubjectView.setText(chosenSubject);
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Hide software keyboard for the given {@link View}.
     */
    public void hideSoftKeyboard(Context context, View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Show software keyboard for the given {@link View}.
     */
    public void showSoftKeyboard(Context context, View view) {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,
                    InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }
}
