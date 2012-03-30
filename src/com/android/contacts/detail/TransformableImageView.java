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
package com.android.contacts.detail;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Extension to ImageView that handles cropping during resize animations.
 */
public class TransformableImageView extends ImageView {

    public TransformableImageView(Context context) {
        super(context);
    }

    public TransformableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TransformableImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int saveCount = canvas.getSaveCount();
        canvas.save();
        canvas.translate(mPaddingLeft, mPaddingTop);
        Matrix drawMatrix = new Matrix();
        int dwidth = getDrawable().getIntrinsicWidth();
        int dheight = getDrawable().getIntrinsicHeight();

        int vwidth = getWidth() - mPaddingLeft - mPaddingRight;
        int vheight = getHeight() - mPaddingTop - mPaddingBottom;
        float scale;
        float dx = 0, dy = 0;

        if (dwidth * vheight > vwidth * dheight) {
            scale = (float) vheight / (float) dheight;
            dx = (vwidth - dwidth * scale) * 0.5f;
        } else {
            scale = (float) vwidth / (float) dwidth;
            dy = (vheight - dheight * scale) * 0.5f;
        }

        drawMatrix.setScale(scale, scale);
        drawMatrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
        canvas.concat(drawMatrix);
        getDrawable().draw(canvas);
        canvas.restoreToCount(saveCount);
    }
}
