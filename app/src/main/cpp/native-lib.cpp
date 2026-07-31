#include <jni.h>
#include <android/bitmap.h>
#include <algorithm>
#include <cstring>

// Helper inline per clampare i valori float a uint8
inline uint8_t floatToUint8(float val) {
    float scaled = val * 255.0f;
    if (scaled <= 0.0f) return 0;
    if (scaled >= 255.0f) return 255;
    return static_cast<uint8_t>(scaled + 0.5f);
}

extern "C" JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_util_upscale_NativePixelOps_writeBitmapToBufferNHWC(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap,
        jobject directBuffer,
        jint bufferPixelOffset,
        jint tileSize
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return;
    }

    auto* src = static_cast<const uint8_t*>(pixels);
    auto* dstBuffer = static_cast<float*>(env->GetDirectBufferAddress(directBuffer));
    float* dst = dstBuffer + (bufferPixelOffset * 3);

    int totalPixels = tileSize * tileSize;
    for (int i = 0; i < totalPixels; ++i) {
        int srcIdx = i * 4;
        int dstIdx = i * 3;
        dst[dstIdx + 0] = src[srcIdx + 0] / 255.0f; // R
        dst[dstIdx + 1] = src[srcIdx + 1] / 255.0f; // G
        dst[dstIdx + 2] = src[srcIdx + 2] / 255.0f; // B
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_util_upscale_NativePixelOps_writeBitmapToBufferNCHW(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap,
        jobject directBuffer,
        jint bufferPixelOffset,
        jint tileSize
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return;
    }

    auto* src = static_cast<const uint8_t*>(pixels);
    auto* dstBuffer = static_cast<float*>(env->GetDirectBufferAddress(directBuffer));
    float* dst = dstBuffer + (bufferPixelOffset * 3);

    int totalPixels = tileSize * tileSize;
    float* rPlane = dst;
    float* gPlane = dst + totalPixels;
    float* bPlane = dst + totalPixels * 2;

    for (int i = 0; i < totalPixels; ++i) {
        int srcIdx = i * 4;
        rPlane[i] = src[srcIdx + 0] / 255.0f;
        gPlane[i] = src[srcIdx + 1] / 255.0f;
        bPlane[i] = src[srcIdx + 2] / 255.0f;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_util_upscale_NativePixelOps_readBufferToBitmapNHWC(
        JNIEnv* env,
        jobject /* this */,
        jobject directBuffer,
        jint bufferPixelOffset,
        jobject targetBitmap,
        jint outSize
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, targetBitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return;
    }
    if (AndroidBitmap_lockPixels(env, targetBitmap, &pixels) < 0) {
        return;
    }

    auto* dst = static_cast<uint8_t*>(pixels);
    const float* srcBuffer = static_cast<const float*>(env->GetDirectBufferAddress(directBuffer));
    const float* src = srcBuffer + (bufferPixelOffset * 3);

    int totalPixels = outSize * outSize;
    for (int i = 0; i < totalPixels; ++i) {
        int srcIdx = i * 3;
        int dstIdx = i * 4;
        dst[dstIdx + 0] = floatToUint8(src[srcIdx + 0]); // R
        dst[dstIdx + 1] = floatToUint8(src[srcIdx + 1]); // G
        dst[dstIdx + 2] = floatToUint8(src[srcIdx + 2]); // B
        dst[dstIdx + 3] = 255;                            // Alpha
    }

    AndroidBitmap_unlockPixels(env, targetBitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_util_upscale_NativePixelOps_readBufferToBitmapNCHW(
        JNIEnv* env,
        jobject /* this */,
        jobject directBuffer,
        jint bufferPixelOffset,
        jobject targetBitmap,
        jint outSize
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, targetBitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return;
    }
    if (AndroidBitmap_lockPixels(env, targetBitmap, &pixels) < 0) {
        return;
    }

    auto* dst = static_cast<uint8_t*>(pixels);
    const float* srcBuffer = static_cast<const float*>(env->GetDirectBufferAddress(directBuffer));
    const float* src = srcBuffer + (bufferPixelOffset * 3);

    int totalPixels = outSize * outSize;
    const float* rPlane = src;
    const float* gPlane = src + totalPixels;
    const float* bPlane = src + totalPixels * 2;

    for (int i = 0; i < totalPixels; ++i) {
        int dstIdx = i * 4;
        dst[dstIdx + 0] = floatToUint8(rPlane[i]);
        dst[dstIdx + 1] = floatToUint8(gPlane[i]);
        dst[dstIdx + 2] = floatToUint8(bPlane[i]);
        dst[dstIdx + 3] = 255;
    }

    AndroidBitmap_unlockPixels(env, targetBitmap);
}
