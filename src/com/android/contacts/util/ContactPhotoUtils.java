/*
 * Copyright (C) 2012 The Android Open Source Project
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


package com.android.contacts.util;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utilities related to loading/saving contact photos.
 *
 */
public class ContactPhotoUtils {
    private static final String TAG = "ContactPhotoUtils";

    private static final String PHOTO_DATE_FORMAT = "'IMG'_yyyyMMdd_HHmmss";

    // TODO: /DCIM/Camera isn't the ideal place to stash cropped contact photos.
    //       Where is the right place?
    private static final File PHOTO_DIR = new File(
            Environment.getExternalStorageDirectory() + "/DCIM/Camera");

    /**
     * Generate a new, unique file to be used as an out-of-band communication
     * channel, since hi-res Bitmaps are too big to serialize into a Bundle.
     * This file will be passed to other activities (such as the gallery/camera/cropper/etc.),
     * and read by us once they are finished writing it.
     */
    public static File generateTempPhotoFile() {
        PHOTO_DIR.mkdirs();
        return new File(PHOTO_DIR, generateTempPhotoFileName());
    }

    private static String generateTempPhotoFileName() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat(PHOTO_DATE_FORMAT);
        return dateFormat.format(date) + ".jpg";
    }

    /**
     * Creates a byte[] containing the PNG-compressed bitmap, or null if
     * something goes wrong.
     */
    public static byte[] compressBitmap(Bitmap bitmap) {
        final int size = bitmap.getWidth() * bitmap.getHeight() * 4;
        final ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return out.toByteArray();
        } catch (IOException e) {
            Log.w(TAG, "Unable to serialize photo: " + e.toString());
            return null;
        }
    }
}


