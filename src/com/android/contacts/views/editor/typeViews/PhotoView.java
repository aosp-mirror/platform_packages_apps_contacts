/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.contacts.views.editor.typeViews;

import com.android.contacts.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class PhotoView extends LinearLayout {
    private ImageView mPhotoImageView;
    private ImageView mTakePhotoActionButton;
    private ImageView mGalleryActionButton;
    private Listener mListener;

    public PhotoView(Context context) {
        super(context);
    }

    public PhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PhotoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public static PhotoView inflate(LayoutInflater inflater, ViewGroup parent,
            boolean attachToRoot) {
        return (PhotoView) inflater.inflate(R.layout.list_edit_item_photo, parent, attachToRoot);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPhotoImageView = (ImageView) findViewById(R.id.photo);

        mTakePhotoActionButton = (ImageView) findViewById(R.id.action_icon);
        mTakePhotoActionButton.setOnClickListener(mClickListener);

        mGalleryActionButton = (ImageView) findViewById(R.id.secondary_action_button);
        mGalleryActionButton.setOnClickListener(mClickListener);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    public void setPhoto(Bitmap value) {
        mPhotoImageView.setImageBitmap(value);
    }

    private OnClickListener mClickListener = new OnClickListener() {
        public void onClick(View v) {
            if (mListener == null) return;
            switch (v.getId()) {
                case R.id.action_icon:
                    mListener.onTakePhotoClicked();
                    break;
                case R.id.secondary_action_button:
                    mListener.onChooseFromGalleryClicked();
                    break;
            }
        }
    };

    public static interface Listener {
        void onTakePhotoClicked();
        void onChooseFromGalleryClicked();
    }
}
