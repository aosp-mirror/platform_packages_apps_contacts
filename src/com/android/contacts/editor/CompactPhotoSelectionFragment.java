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

package com.android.contacts.editor;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;

import android.app.Fragment;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.util.ArrayList;

/**
 * Displays {@link Photo}s in a grid and calls back the host when one is clicked.
 */
public class CompactPhotoSelectionFragment extends Fragment {

    private static final String STATE_PHOTOS = "photos";
    private static final String STATE_PHOTO_MODE = "photoMode";
    private final int VIEW_TYPE_TAKE_PHOTO = 0;
    private final int VIEW_TYPE_ALL_PHOTOS = 1;
    private final int VIEW_TYPE_IMAGE = 2;

    /**
     * Callbacks hosts this Fragment.
     */
    public interface Listener {

        /**
         * Invoked when the user wants to change their photo.
         */
        void onPhotoSelected(Photo photo);
    }

    /**
     * Holds a photo {@link ValuesDelta} and {@link AccountType} information to draw
     * an account type icon over it.
     */
    public static final class Photo implements Parcelable {

        public static final Creator<Photo> CREATOR = new Creator<Photo>() {

            public Photo createFromParcel(Parcel in) {
                return new Photo(in);
            }

            public Photo[] newArray(int size) {
                return new Photo[size];
            }
        };

        public Photo() {
        }

        private Photo(Parcel source) {
            readFromParcel(source);
        }

        // From AccountType, everything we need to display the account type icon
        public int titleRes;
        public int iconRes;
        public String syncAdapterPackageName;

        public String contentDescription;
        public String contentDescriptionChecked; // Talkback announcement when the photo is checked
        public String accountType;
        public String accountName;

        public ValuesDelta valuesDelta;

        /**
         * Whether the photo is being displayed for the aggregate contact.
         * This may be because it is marked super primary or it is the one quick contacts picked
         * randomly to display because none is marked super primary.
         */
        public boolean primary;

        /**
         * Pointer back to the KindSectionDataList this photo came from.
         * See {@link CompactRawContactsEditorView#getPhotos}
         * See {@link CompactRawContactsEditorView#setPrimaryPhoto}
         */
        public int kindSectionDataListIndex = -1;
        public int valuesDeltaListIndex = -1;

        /** Newly taken or selected photo that has not yet been saved to CP2. */
        public Uri updatedPhotoUri;

        public long photoId;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(titleRes);
            dest.writeInt(iconRes);
            dest.writeString(syncAdapterPackageName);
            dest.writeParcelable(valuesDelta, flags);
            dest.writeInt(primary ? 1 : 0);
            dest.writeInt(kindSectionDataListIndex);
            dest.writeInt(valuesDeltaListIndex);
            dest.writeParcelable(updatedPhotoUri, flags);
            dest.writeLong(photoId);
        }

        private void readFromParcel(Parcel source) {
            final ClassLoader classLoader = getClass().getClassLoader();
            titleRes = source.readInt();
            iconRes = source.readInt();
            syncAdapterPackageName = source.readString();
            valuesDelta = source.readParcelable(classLoader);
            primary = source.readInt() == 1;
            kindSectionDataListIndex = source.readInt();
            valuesDeltaListIndex = source.readInt();
            updatedPhotoUri = source.readParcelable(classLoader);
            photoId = source.readLong();
        }
    }

    private final class PhotoAdapter extends BaseAdapter {

        private final Context mContext;
        private final LayoutInflater mLayoutInflater;

        public PhotoAdapter() {
            mContext = getContext();
            mLayoutInflater = LayoutInflater.from(mContext);
        }

        @Override
        public int getCount() {
            return mPhotos == null ? 2 : mPhotos.size() + 2;
        }

        @Override
        public Object getItem(int index) {
            return mPhotos == null ? null : mPhotos.get(index);
        }

        @Override
        public long getItemId(int index) {
            return index;
        }

        @Override
        public int getItemViewType(int index) {
            if (index == 0) {
                return VIEW_TYPE_TAKE_PHOTO;
            } else if (index == 1) {
                return VIEW_TYPE_ALL_PHOTOS;
            } else {
                return VIEW_TYPE_IMAGE;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (mPhotos == null) return null;

            // when position is 0 or 1, we should make sure account_type *is not* in convertView
            // before reusing it.
            if (getItemViewType(position) == 0){
                if (convertView == null || convertView.findViewById(R.id.account_type) != null) {
                    return mLayoutInflater.inflate(R.layout.take_a_photo_button, /* root =*/ null);
                }
                return convertView;
            }

            if (getItemViewType(position) == 1) {
                if (convertView == null || convertView.findViewById(R.id.account_type) != null) {
                    return mLayoutInflater.inflate(R.layout.all_photos_button, /* root =*/ null);
                }
                return convertView;
            }

            // when position greater than 1, we should make sure account_type *is* in convertView
            // before reusing it.
            position -= 2;

            final View photoItemView;
            if (convertView == null || convertView.findViewById(R.id.account_type) == null) {
                photoItemView = mLayoutInflater.inflate(
                        R.layout.compact_photo_selection_item, /* root =*/ null);
            } else {
                photoItemView = convertView;
            }

            final Photo photo = mPhotos.get(position);

            // Bind the photo
            final ImageView imageView = (ImageView) photoItemView.findViewById(R.id.image);
            if (photo.updatedPhotoUri != null) {
                EditorUiUtils.loadPhoto(ContactPhotoManager.getInstance(mContext),
                        imageView, photo.updatedPhotoUri);
            } else {
                final Long photoFileId = EditorUiUtils.getPhotoFileId(photo.valuesDelta);
                if (photoFileId != null) {
                    final Uri photoUri = ContactsContract.DisplayPhoto.CONTENT_URI.buildUpon()
                            .appendPath(photoFileId.toString()).build();
                    EditorUiUtils.loadPhoto(ContactPhotoManager.getInstance(mContext),
                            imageView, photoUri);
                } else {
                    imageView.setImageBitmap(EditorUiUtils.getPhotoBitmap(photo.valuesDelta));
                }
            }

            // Add the account type icon
            final ImageView accountTypeImageView = (ImageView)
                    photoItemView.findViewById(R.id.account_type);
            accountTypeImageView.setImageDrawable(AccountType.getDisplayIcon(
                    mContext, photo.titleRes, photo.iconRes, photo.syncAdapterPackageName));

            // Display a check icon over the primary photo
            final ImageView checkImageView = (ImageView) photoItemView.findViewById(R.id.check);
            checkImageView.setVisibility(photo.primary ? View.VISIBLE : View.GONE);

            photoItemView.setContentDescription(photo.contentDescription);

            return photoItemView;
        }
    }

    private ArrayList<Photo> mPhotos;
    private int mPhotoMode;
    private Listener mListener;
    private GridView mGridView;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setPhotos(ArrayList<Photo> photos, int photoMode) {
        mPhotos = photos;
        mPhotoMode = photoMode;
        mGridView.setAccessibilityDelegate(new View.AccessibilityDelegate() {});
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mPhotos = savedInstanceState.getParcelableArrayList(STATE_PHOTOS);
            mPhotoMode = savedInstanceState.getInt(STATE_PHOTO_MODE, 0);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);

        final PhotoAdapter photoAdapter = new PhotoAdapter();

        final View view = inflater.inflate(R.layout.compact_photo_selection_fragment,
                container, false);
        mGridView = (GridView) view.findViewById(R.id.grid_view);
        mGridView.setAdapter(photoAdapter);
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final PhotoSourceDialogFragment.Listener listener =
                        (PhotoSourceDialogFragment.Listener) getActivity();
                if (position == 0) {
                    listener.onTakePhotoChosen();
                } else if (position == 1) {
                    listener.onPickFromGalleryChosen();
                } else {
                    // Call the host back so it can set the new photo as primary
                    final Photo photo = (Photo) photoAdapter.getItem(position - 2);
                    if (mListener != null) {
                        mListener.onPhotoSelected(photo);
                    }
                    handleAccessibility(photo, position);
                }
            }
        });

        final Display display = getActivity().getWindowManager().getDefaultDisplay();
        final DisplayMetrics outMetrics = new DisplayMetrics ();
        display.getRealMetrics(outMetrics); // real metrics include the navigation Bar

        final float numColumns = outMetrics.widthPixels /
                getResources().getDimension(R.dimen.photo_picker_item_ideal_width);
        mGridView.setNumColumns(Math.round(numColumns));

        return view;
    }

    private void handleAccessibility(Photo photo, int position) {
        // Use custom AccessibilityDelegate when closing this fragment to suppress event.
        mGridView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public boolean onRequestSendAccessibilityEvent(
                    ViewGroup host, View child,AccessibilityEvent event) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    return false;
                }
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });
        final ViewGroup clickedView = (ViewGroup) mGridView.getChildAt(position);
        clickedView.announceForAccessibility(photo.contentDescriptionChecked);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(STATE_PHOTOS, mPhotos);
        outState.putInt(STATE_PHOTO_MODE, mPhotoMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }
}