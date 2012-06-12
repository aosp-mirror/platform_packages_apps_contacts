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
 * limitations under the License
 */

package com.android.contacts.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.view.View;

import com.android.contacts.R;

/**
 * Manages creation and destruction of Dialogs that are to be shown by Views. Unlike how Dialogs
 * are regularly used, the Dialogs are not recycled but immediately destroyed after dismissal.
 * To be able to do that, two IDs are required which are used consecutively.
 * How to use:<ul>
 * <li>The owning Activity creates on instance of this class, passing itself and two Ids that are
 *    not used by other Dialogs of the Activity.</li>
 * <li>Views owning Dialogs must implement {@link DialogManager.DialogShowingView}</li>
 * <li>After creating the Views, configureManagingViews must be called to configure all views
 *    that implement {@link DialogManager.DialogShowingView}</li>
 * <li>In the implementation of {@link Activity#onCreateDialog}, calls for the
 *    ViewId are forwarded to {@link DialogManager#onCreateDialog(int, Bundle)}</li>
 * </ul>
 * To actually show a Dialog, the View uses {@link DialogManager#showDialogInView(View, Bundle)},
 * passing itself as a first parameter
 */
public class DialogManager {
    private final Activity mActivity;
    private boolean mUseDialogId2 = false;
    public final static String VIEW_ID_KEY = "view_id";

    public static final boolean isManagedId(int id) {
        return (id == R.id.dialog_manager_id_1) || (id == R.id.dialog_manager_id_2);
    }

    /**
     * Creates a new instance of this class for the given Activity.
     * @param activity The activity this object is used for
     */
    public DialogManager(final Activity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        mActivity = activity;
    }

    /**
     * Called by a View to show a dialog. It has to pass itself and a Bundle with extra information.
     * If the view can show several dialogs, it should distinguish them using an item in the Bundle.
     * The View needs to have a valid and unique Id. This function modifies the bundle by adding a
     * new item named {@link DialogManager#VIEW_ID_KEY}
     */
    public void showDialogInView(final View view, final Bundle bundle) {
        final int viewId = view.getId();
        if (bundle.containsKey(VIEW_ID_KEY)) {
            throw new IllegalArgumentException("Bundle already contains a " + VIEW_ID_KEY);
        }
        if (viewId == View.NO_ID) {
            throw new IllegalArgumentException("View does not have a proper ViewId");
        }
        bundle.putInt(VIEW_ID_KEY, viewId);
        int dialogId = mUseDialogId2 ? R.id.dialog_manager_id_2 : R.id.dialog_manager_id_1;
        mActivity.showDialog(dialogId, bundle);
    }

    /**
     * Callback function called by the Activity to handle View-managed Dialogs.
     * This function returns null if the id is not one of the two reserved Ids.
     */
    public Dialog onCreateDialog(final int id, final Bundle bundle) {
        if (id == R.id.dialog_manager_id_1) {
            mUseDialogId2 = true;
        } else if (id == R.id.dialog_manager_id_2) {
            mUseDialogId2 = false;
        } else {
            return null;
        }
        if (!bundle.containsKey(VIEW_ID_KEY)) {
            throw new IllegalArgumentException("Bundle does not contain a ViewId");
        }
        final int viewId = bundle.getInt(VIEW_ID_KEY);
        final View view = mActivity.findViewById(viewId);
        if (view == null || !(view instanceof DialogShowingView)) {
            return null;
        }
        final Dialog dialog = ((DialogShowingView)view).createDialog(bundle);
        if (dialog == null) {
            return dialog;
        }

        // As we will never re-use this dialog, we can completely kill it here
        dialog.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialogInterface) {
                mActivity.removeDialog(id);
            }
        });
        return dialog;
    }

    /**
     * Interface to implemented by Views that show Dialogs
     */
    public interface DialogShowingView {
        /**
         * Callback function to create a Dialog. Notice that the DialogManager overwrites the
         * OnDismissListener on the returned Dialog, so the View should not use this Listener itself
         */
        Dialog createDialog(Bundle bundle);
    }

    /**
     * Interface to implemented by Activities that host View-showing dialogs
     */
    public interface DialogShowingViewActivity {
        DialogManager getDialogManager();
    }
}
