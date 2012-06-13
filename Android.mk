LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_LIBRARIES := telephony-common
LOCAL_STATIC_JAVA_LIBRARIES := \
    com.android.phone.common \
    com.android.vcard \
    android-common \
    guava \
    android-support-v13 \
    android-support-v4 \
    android-ex-variablespeed \

LOCAL_REQUIRED_MODULES := libvariablespeed

LOCAL_PACKAGE_NAME := Contacts
LOCAL_CERTIFICATE := shared

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
