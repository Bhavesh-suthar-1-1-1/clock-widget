LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := dial_slats_vulkan
LOCAL_SRC_FILES := \
    vulkan_one_pass_engine.cpp \
    vulkan_slats_jni.cpp
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra -Wno-missing-field-initializers
LOCAL_LDLIBS := -landroid -ljnigraphics -llog -lvulkan
include $(BUILD_SHARED_LIBRARY)
