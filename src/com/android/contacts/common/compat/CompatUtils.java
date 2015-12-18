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
 * limitations under the License.
 */
package com.android.contacts.common.compat;

import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.contacts.common.model.CPOWrapper;

public final class CompatUtils {

    private static final String TAG = CompatUtils.class.getSimpleName();

    /**
     * These 4 variables are copied from ContentProviderOperation for compatibility.
     */
    public final static int TYPE_INSERT = 1;

    public final static int TYPE_UPDATE = 2;

    public final static int TYPE_DELETE = 3;

    public final static int TYPE_ASSERT = 4;

    /**
     * Returns whether the operation in CPOWrapper is of TYPE_INSERT;
     */
    public static boolean isInsertCompat(CPOWrapper cpoWrapper) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            return cpoWrapper.getOperation().isInsert();
        } else {
            return (cpoWrapper.getType() == TYPE_INSERT);
        }
    }

    /**
     * PrioritizedMimeType is added in API level 23.
     */
    public static boolean hasPrioritizedMimeType() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M)
                >= Build.VERSION_CODES.M;
    }

    /**
     * Determines if this version is compatible with multi-SIM and the phone account APIs.
     * Can also force the version to be lower through SdkVersionOverride.
     *
     * @return {@code true} if multi-SIM capability is available, {@code false} otherwise.
     */
    public static boolean isMSIMCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    /**
     * Determines if this version is compatible with video calling. Can also force the version to be
     * lower through SdkVersionOverride.
     *
     * @return {@code true} if video calling is allowed, {@code false} otherwise.
     */
    public static boolean isVideoCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M;
    }

    /**
     * Determines if this version is compatible with call subject. Can also force the version to
     * be lower through SdkVersionOverride.
     *
     * @return {@code true} if call subject is a feature on this device, {@code false} otherwise.
     */
    public static boolean isCallSubjectCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M;
    }

    /**
     * Determines if this version is compatible with Lollipop Mr1-specific APIs. Can also force the
     * version to be lower through SdkVersionOverride.
     *
     * @return {@code true} if call subject is a feature on this device, {@code false} otherwise.
     */
    public static boolean isLollipopMr1Compatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP_MR1)
                >= Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    /**
     * Determines if this version is compatible with Marshmallow-specific APIs. Can also force the
     * version to be lower through SdkVersionOverride.
     *
     * @return {@code true} if call subject is a feature on this device, {@code false} otherwise.
     */
    public static boolean isMarshmallowCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M;
    }

    /**
     * Determines if the given class is available. Can be used to check if system apis exist at
     * runtime.
     *
     * @param className the name of the class to look for.
     * @return {@code true} if the given class is available, {@code false} otherwise or if className
     *    is null.
     */
    public static boolean isClassAvailable(@Nullable String className) {
        if (className == null) {
            return false;
        }
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected exception when checking if class exists at runtime", t);
            return false;
        }
    }

    /**
     * Determines if this version is compatible with Lollipop-specific APIs. Can also force the
     * version to be lower through SdkVersionOverride.
     *
     * @return {@code true} if call subject is a feature on this device, {@code false} otherwise.
     */
    public static boolean isLollipopCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.LOLLIPOP;
    }
}
