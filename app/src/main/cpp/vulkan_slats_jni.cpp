// JNI bridge for the Slats Vulkan preview: Atmo's one-pass engine
// (vulkan_one_pass_engine) with a procedural Slats fragment shader.
//
// The whole clock face is drawn by the fragment shader (rings, slats,
// capsule); glyphs come from one Canvas-rasterised atlas bitmap uploaded
// as the only texture. The widget keeps the Canvas renderer — Vulkan
// cannot go in a widget — and the in-app preview falls back to Canvas if
// any of this fails.
#include "vulkan_one_pass_engine.h"

#include <android/asset_manager_jni.h>

#include <cstddef>
#include <cstdint>
#include <new>

namespace {

constexpr char kVertexShader[] = "shaders/slats/slats.vert.spv";
constexpr char kFragmentShader[] = "shaders/slats/slats.frag.spv";
constexpr uint32_t kAtlasBinding = 0;

// Must match the Push block in slats.frag exactly (7 floats = 28 bytes).
struct SlatsPush {
    float hourAngle;
    float minuteAngle;
    float capHour;
    float capMinute;
    float aspect;
    float resX;
    float resY;
};
static_assert(sizeof(SlatsPush) == 28);

struct SlatsHandle {
    atmo::vulkan::OnePassHandle engine = nullptr;
    bool surfaceReady = false;
};

SlatsHandle* fromHandle(jlong handle) {
    return reinterpret_cast<SlatsHandle*>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_app_dialclock_vulkan_SlatsVulkanNative_nativeProbeRuntime(
    JNIEnv*,
    jobject
) {
    return static_cast<jint>(atmo::vulkan::probeRuntime());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_app_dialclock_vulkan_SlatsVulkanNative_nativeCreate(
    JNIEnv* env,
    jobject,
    jobject assetManager
) {
    if (assetManager == nullptr) return 0;
    const atmo::vulkan::OnePassConfig config{
        "Dial Slats",
        kVertexShader,
        kFragmentShader,
        1,          // textureBindingCount: the glyph atlas
        0,          // optionalTextureMask
        sizeof(SlatsPush),
        atmo::vulkan::kNoUniformBinding,
        0,          // uniformSize
        0,          // mipmappedTextureMask
        true        // transparent: preview sits on app UI
    };
    atmo::vulkan::OnePassHandle engine =
        atmo::vulkan::createOnePass(env, assetManager, config);
    if (engine == nullptr) return 0;
    auto* handle = new (std::nothrow) SlatsHandle{engine};
    if (handle == nullptr) {
        atmo::vulkan::destroyOnePass(env, engine);
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_app_dialclock_vulkan_SlatsVulkanNative_nativeSetSurface(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject surface,
    jint width,
    jint height
) {
    SlatsHandle* slats = fromHandle(handle);
    if (slats == nullptr || width <= 0 || height <= 0) return JNI_FALSE;
    slats->surfaceReady = false;
    const bool created = atmo::vulkan::setSurface(
        slats->engine,
        env,
        surface,
        static_cast<uint32_t>(width),
        static_cast<uint32_t>(height)
    );
    if (!created) return JNI_FALSE;
    slats->surfaceReady = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_app_dialclock_vulkan_SlatsVulkanNative_nativeUploadAtlas(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject bitmap
) {
    SlatsHandle* slats = fromHandle(handle);
    return slats != nullptr &&
        atmo::vulkan::uploadBitmap(
            slats->engine, env, bitmap, kAtlasBinding
        ) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_app_dialclock_vulkan_SlatsVulkanNative_nativeSetParams(
    JNIEnv*,
    jobject,
    jlong handle,
    jfloat hourAngle,
    jfloat minuteAngle,
    jfloat capHour,
    jfloat capMinute,
    jfloat aspect,
    jfloat resX,
    jfloat resY
) {
    SlatsHandle* slats = fromHandle(handle);
    if (slats == nullptr) return JNI_FALSE;
    const SlatsPush push{
        hourAngle, minuteAngle, capHour, capMinute, aspect, resX, resY
    };
    return atmo::vulkan::setPushConstants(
        slats->engine, &push, sizeof(push)
    ) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_app_dialclock_vulkan_SlatsVulkanNative_nativeRender(
    JNIEnv*,
    jobject,
    jlong handle
) {
    SlatsHandle* slats = fromHandle(handle);
    if (slats == nullptr || !slats->surfaceReady) return -1;
    return atmo::vulkan::render(slats->engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_dialclock_vulkan_SlatsVulkanNative_nativeDestroySurface(
    JNIEnv*,
    jobject,
    jlong handle
) {
    SlatsHandle* slats = fromHandle(handle);
    if (slats != nullptr) {
        slats->surfaceReady = false;
        atmo::vulkan::destroySurface(slats->engine);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_dialclock_vulkan_SlatsVulkanNative_nativeDestroy(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    SlatsHandle* slats = fromHandle(handle);
    if (slats == nullptr) return;
    atmo::vulkan::destroyOnePass(env, slats->engine);
    delete slats;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_app_dialclock_vulkan_SlatsVulkanNative_nativeDrainDiagnostics(
    JNIEnv* env,
    jobject
) {
    return env->NewStringUTF(atmo::vulkan::drainDiagnostics().c_str());
}
