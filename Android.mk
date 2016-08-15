LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

phone_common_dir := ../PhoneCommon

ifeq ($(TARGET_BUILD_APPS),)
support_library_root_dir := frameworks/support
else
support_library_root_dir := prebuilts/sdk/current/support
endif

src_dirs := src src-bind $(phone_common_dir)/src
res_dirs := res res-aosp res-icons $(phone_common_dir)/res
asset_dirs := assets

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    $(support_library_root_dir)/design/res \
    $(support_library_root_dir)/v7/appcompat/res \
    $(support_library_root_dir)/v7/cardview/res \
    $(support_library_root_dir)/v7/recyclerview/res
LOCAL_ASSET_DIR := $(addprefix $(LOCAL_PATH)/, $(asset_dirs))

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.contacts.common \
    --extra-packages com.android.phone.common \
    --extra-packages com.google.android.libraries.material.featurehighlight \
    --extra-packages android.support.design \
    --extra-packages android.support.v7.appcompat \
    --extra-packages android.support.v7.cardview \
    --extra-packages android.support.v7.recyclerview

LOCAL_STATIC_JAVA_AAR_LIBRARIES := aar_feature_highlight

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-common \
    android-support-design \
    android-support-v13 \
    android-support-v7-appcompat \
    android-support-v7-cardview \
    android-support-v7-recyclerview \
    android-support-v7-palette \
    android-support-v4 \
    com.android.vcard \
    guava \
    libphonenumber \
    lib_animation \
    lib_math \
    lib_navigation_finder \
    lib_path \
    lib_util_objects \
    lib_util_preconditions

LOCAL_PACKAGE_NAME := Contacts
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 21

include $(BUILD_PACKAGE)

#########################################################################################

include $(CLEAR_VARS)
# Import FeatureHighlight aar and its dependencies.
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := aar_feature_highlight:libs/featurehighlight.aar \
    lib_animation:libs/libanimation.jar \
    lib_math:libs/libmath.jar \
    lib_navigation_finder:libs/libappcompat.jar \
    lib_path:libs/libpath.jar \
    lib_util_objects:libs/libutil_Objects.jar \
    lib_util_preconditions:libs/libutil_Preconditions.jar

include $(BUILD_MULTI_PREBUILT)

#########################################################################################

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
