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
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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

        public ValuesDelta valuesDelta;

        /**
         * Whether the photo is being displayed for the aggregate contact.
         * This may be because it is marked super primary or it is the one quick contacts picked
         * randomly to display because none is marked super primary.
         */
        public boolean primary;

        public long rawContactId;

        /**
         * Pointer back to the KindSectionDataList this photo came from.
         * See {@link CompactRawContactsEditorView#getPhotos}
         * See {@link CompactRawContactsEditorView#setPrimaryPhoto}
         */
        public int kindSectionDataListIndex = -1;
        public int valuesDeltaListIndex = -1;

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
            return mPhotos == null ? 0 : mPhotos.size();
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
        public View getView(int position, View convertView, ViewGroup parent) {
            if (mPhotos == null) return null;

            final View photoItemView;
            if (convertView == null) {
                photoItemView = mLayoutInflater.inflate(
                        R.layout.compact_photo_selection_item, /* root =*/ null);
            } else {
                photoItemView = convertView;
            }

            final Photo photo = mPhotos.get(position);

            // Bind the photo
            final ImageView imageView = (ImageView) photoItemView.findViewById(R.id.image);
            imageView.setImageBitmap(EditorUiUtils.getPhotoBitmap(photo.valuesDelta));

            // Add the account type icon
            final ImageView accountTypeImageView = (ImageView)
                    photoItemView.findViewById(R.id.account_type);
            accountTypeImageView.setImageDrawable(AccountType.getDisplayIcon(
                    mContext, photo.titleRes, photo.iconRes, photo.syncAdapterPackageName));

            // Display a check icon over the primary photo
            final ImageView checkImageView = (ImageView) photoItemView.findViewById(R.id.check);
            checkImageView.setVisibility(photo.primary ? View.VISIBLE : View.GONE);

            return photoItemView;
        }
    }

    private ArrayList<Photo> mPhotos;
    private int mPhotoMode;
    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setPhotos(ArrayList<Photo> photos, int photoMode) {
        mPhotos = photos;
        mPhotoMode = photoMode;
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
        final GridView gridView = (GridView) view.findViewById(R.id.grid_view);
        gridView.setAdapter(photoAdapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Call the host back so it can set the new photo as primary
                final Photo photo = (Photo) photoAdapter.getItem(position);
                if (mListener != null) {
                    mListener.onPhotoSelected(photo);
                }
            }
        });

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(STATE_PHOTOS, mPhotos);
        outState.putInt(STATE_PHOTO_MODE, mPhotoMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.edit_contact_photo, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
            case R.id.menu_photo:
                PhotoSourceDialogFragment.show(getActivity(), mPhotoMode);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}