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

package com.android.contacts;

import com.android.contacts.NotifyingAsyncQueryHandler.QueryCompleteListener;
import com.android.contacts.FloatyListView.FloatyWindow;
import com.android.contacts.SocialStreamActivity.MappingCache;
import com.android.contacts.SocialStreamActivity.MappingCache.Mapping;
import com.android.providers.contacts2.ContactsContract;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds;
import com.android.providers.contacts2.ContactsContract.Data;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Email;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Im;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Phone;
import com.android.providers.contacts2.ContactsContract.CommonDataKinds.Postal;

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Gallery.LayoutParams;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * {@link PopupWindow} that shows fast-track details for a specific aggregate.
 * This window implements {@link FloatyWindow} so that it can be quickly
 * repositioned by someone like {@link FloatyListView}.
 */
public class FastTrackWindow extends PopupWindow implements QueryCompleteListener, FloatyWindow {
    private static final String TAG = "FastTrackWindow";

    private Context mContext;
    private View mParent;

    /** Mapping cache from mime-type to icons and actions */
    private MappingCache mMappingCache;

    private ViewGroup mContent;

    private Uri mDataUri;
    private NotifyingAsyncQueryHandler mHandler;

    private boolean mShowing = false;
    private boolean mHasPosition = false;
    private boolean mHasData = false;

    public static final int ICON_SIZE = 42;
    public static final int ICON_PADDING = 3;
    private static final int VERTICAL_OFFSET = 74;

    private int mFirstX;
    private int mFirstY;

    private static final int TOKEN = 1;

    private static final int GRAVITY = Gravity.LEFT | Gravity.TOP;

    /** Message to show when no activity is found to perform an action */
    // TODO: move this value into a resources string
    private static final String NOT_FOUND = "Couldn't find an app to handle this action";

    /** List of default mime-type icons */
    private static HashMap<String, Integer> sMimeIcons = new HashMap<String, Integer>();

    /** List of mime-type sorting scores */
    private static HashMap<String, Integer> sMimeScores = new HashMap<String, Integer>();

    static {
        sMimeIcons.put(Phone.CONTENT_ITEM_TYPE, android.R.drawable.sym_action_call);
        sMimeIcons.put(Email.CONTENT_ITEM_TYPE, android.R.drawable.sym_action_email);
        sMimeIcons.put(Im.CONTENT_ITEM_TYPE, android.R.drawable.sym_action_chat);
//        sMimeIcons.put(Phone.CONTENT_ITEM_TYPE, R.drawable.sym_action_sms);
        sMimeIcons.put(Postal.CONTENT_ITEM_TYPE, R.drawable.sym_action_map);

        // For scoring, put phone numbers and E-mail up front, and addresses last
        sMimeScores.put(Phone.CONTENT_ITEM_TYPE, -200);
        sMimeScores.put(Email.CONTENT_ITEM_TYPE, -100);
        sMimeScores.put(Postal.CONTENT_ITEM_TYPE, 100);
    }

    /**
     * Create a new fast-track window for the given aggregate, using the
     * provided {@link MappingCache} for icon as needed.
     */
    public FastTrackWindow(Context context, View parent, Uri aggUri, MappingCache mappingCache) {
        super(context);

        final Resources resources = context.getResources();

        mContext = context;
        mParent = parent;

        mMappingCache = mappingCache;

        // Inflate content view
        LayoutInflater inflater = (LayoutInflater)context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContent = (ViewGroup)inflater.inflate(R.layout.fasttrack, null, false);

        setContentView(mContent);
//        setAnimationStyle(android.R.style.Animation_LeftEdge);

        setBackgroundDrawable(resources.getDrawable(R.drawable.fasttrack));

        setWidth(LayoutParams.WRAP_CONTENT);
        setHeight(LayoutParams.WRAP_CONTENT);

        setClippingEnabled(false);
        setFocusable(false);

        // Start data query in background
        mDataUri = Uri.withAppendedPath(aggUri, ContactsContract.Aggregates.Data.CONTENT_DIRECTORY);

        mHandler = new NotifyingAsyncQueryHandler(context, this);
        mHandler.startQuery(TOKEN, null, mDataUri, null, null, null, null);

    }

    /**
     * Consider showing this window, which requires both a given position and
     * completed query results.
     */
    private synchronized void considerShowing() {
        if (mHasData && mHasPosition && !mShowing) {
            mShowing = true;
            showAtLocation(mParent, GRAVITY, mFirstX, mFirstY);
        }
    }

    /** {@inheritDoc} */
    public void showAt(int x, int y) {
        // Adjust vertical position by height
        y -= VERTICAL_OFFSET;

        // Show dialog or update existing location
        if (!mShowing) {
            mFirstX = x;
            mFirstY = y;
            mHasPosition = true;
            considerShowing();
        } else {
            update(x, y, -1, -1, true);
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        final ViewGroup fastTrack = (ViewGroup)mContent.findViewById(R.id.fasttrack);

        // Build list of actions for this contact, this could be done better in
        // the future using an Adapter
        ArrayList<ImageView> list = new ArrayList<ImageView>(cursor.getCount());

        final int COL_ID = cursor.getColumnIndex(Data._ID);
        final int COL_PACKAGE = cursor.getColumnIndex(Data.PACKAGE);
        final int COL_MIMETYPE = cursor.getColumnIndex(Data.MIMETYPE);

        while (cursor.moveToNext()) {
            final long dataId = cursor.getLong(COL_ID);
            final String packageName = cursor.getString(COL_PACKAGE);
            final String mimeType = cursor.getString(COL_MIMETYPE);

            ImageView action;

            // First, try looking in mapping cache for possible icon match
            Mapping mapping = mMappingCache.getMapping(packageName, mimeType);
            if (mapping != null && mapping.icon != null) {
                action = new ImageView(mContext);
                action.setImageBitmap(mapping.icon);

            } else if (sMimeIcons.containsKey(mimeType)) {
                // Otherwise fall back to generic icons
                int icon = sMimeIcons.get(mimeType);
                action = new ImageView(mContext);
                action.setImageResource(icon);

            } else {
                // No icon found, so don't insert any action button
                continue;

            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ICON_SIZE, ICON_SIZE);
            params.rightMargin = ICON_PADDING;
            action.setLayoutParams(params);

            // Find the sorting score for this mime-type, otherwise allocate a
            // new one to make sure the same types are grouped together.
            if (!sMimeScores.containsKey(mimeType)) {
                sMimeScores.put(mimeType, sMimeScores.size());
            }

            int mimeScore = sMimeScores.get(mimeType);
            action.setTag(mimeScore);

            final Intent intent = buildIntentForMime(dataId, mimeType, cursor);
            action.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    try {
                        mContext.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.w(TAG, NOT_FOUND, e);
                        Toast.makeText(mContext, NOT_FOUND, Toast.LENGTH_SHORT).show();
                    }
                }
            });

            list.add(action);
        }

        cursor.close();

        // Sort the final list based on mime-type scores
        Collections.sort(list, new Comparator<ImageView>() {
            public int compare(ImageView object1, ImageView object2) {
                return (Integer)object1.getTag() - (Integer)object2.getTag();
            }
        });

        for (ImageView action : list) {
            fastTrack.addView(action);
        }

        mHasData = true;
        considerShowing();
    }

    /**
     * Build an {@link Intent} that will trigger the action described by the
     * given {@link Cursor} and mime-type.
     */
    public Intent buildIntentForMime(long dataId, String mimeType, Cursor cursor) {
        if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final String data = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
            Uri callUri = Uri.parse("tel:" + Uri.encode(data));
            return new Intent(Intent.ACTION_DIAL, callUri);

        } else if (CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final String data = cursor.getString(cursor.getColumnIndex(Email.DATA));
            return new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", data, null));

//        } else if (CommonDataKinds.Im.CONTENT_ITEM_TYPE.equals(mimeType)) {
//            return new Intent(Intent.ACTION_SENDTO, constructImToUrl(host, data));

        } else if (CommonDataKinds.Postal.CONTENT_ITEM_TYPE.equals(mimeType)) {
            final String data = cursor.getString(cursor.getColumnIndex(Postal.DATA));
            Uri mapsUri = Uri.parse("geo:0,0?q=" + Uri.encode(data));
            return new Intent(Intent.ACTION_VIEW, mapsUri);

        }

        // Otherwise fall back to default VIEW action
        Uri dataUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(dataUri);

        return intent;
    }
}
