cmake_minimum_required(VERSION 3.18)
project(waifu2x-jni)

set(CMAKE_CXX_STANDARD 17)

# KMK -->
# Image upscaling (NCNN + Vulkan): ported from HaoweiLi97/mihon_img_upscale.
# The Qualcomm QNN backend is optional; without the QNN SDK the library builds
# Vulkan-only (MIHON_ENABLE_QNN=0) and falls back at runtime.
set(QNN_SDK_DIR "${QNN_SDK_DIR}" CACHE PATH "Path to the Qualcomm AI Runtime SDK")
set(QNN_HTP_ARCH "69" CACHE STRING "Hexagon HTP architecture version")
# KMK <--

# Allow Gradle or the environment to provide the ncnn Android SDK path.
set(NCNN_SDK_DIR "${NCNN_SDK_DIR}" CACHE PATH "Path to the ncnn Android Vulkan SDK")

if(NOT NCNN_SDK_DIR AND DEFINED ENV{NCNN_SDK_DIR})
    set(NCNN_SDK_DIR "$ENV{NCNN_SDK_DIR}")
endif()

if(NOT NCNN_SDK_DIR)
    message(FATAL_ERROR
        "NCNN_SDK_DIR is not set. Define it via local.properties (ncnn.sdk.dir=...), "
        "a Gradle property (-PncnnSdkDir=...), the NCNN_SDK_DIR environment variable, "
        "or place the SDK under third_party/ncnn-20260113-android-vulkan."
    )
endif()

set(ncnn_DIR ${NCNN_SDK_DIR}/${ANDROID_ABI}/lib/cmake/ncnn)

if(NOT EXISTS "${ncnn_DIR}/ncnnConfig.cmake" AND NOT EXISTS "${ncnn_DIR}/ncnn-config.cmake")
    message(FATAL_ERROR
        "Could not find ncnn CMake package for ABI ${ANDROID_ABI} under ${ncnn_DIR}. "
        "Check that NCNN_SDK_DIR points to the root of the ncnn Android Vulkan SDK."
    )
endif()

find_package(ncnn REQUIRED)

file(GLOB GLSLC_CANDIDATES
    "${ANDROID_NDK}/shader-tools/*/glslc"
    "${ANDROID_NDK}/shader-tools/*/glslc.exe"
)
if(GLSLC_CANDIDATES)
    list(GET GLSLC_CANDIDATES 0 GLSLC_EXECUTABLE)
else()
    find_program(GLSLC_EXECUTABLE glslc REQUIRED)
endif()

set(GENERATED_SHADER_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated-shaders")
file(MAKE_DIRECTORY "${GENERATED_SHADER_DIR}")

set(FUSED_PREPROC_SHADER "${CMAKE_CURRENT_SOURCE_DIR}/waifu2x_fused_preproc.comp")
set(FUSED_POSTPROC_SHADER "${CMAKE_CURRENT_SOURCE_DIR}/waifu2x_fused_postproc.comp")
set(FUSED_SHADER_OUTPUTS
    "${GENERATED_SHADER_DIR}/waifu2x_fused_preproc_fp16.inc"
    "${GENERATED_SHADER_DIR}/waifu2x_fused_postproc_fp16.inc"
    "${GENERATED_SHADER_DIR}/waifu2x_fused_preproc_fp32.inc"
    "${GENERATED_SHADER_DIR}/waifu2x_fused_postproc_fp32.inc"
)

add_custom_command(
    OUTPUT ${FUSED_SHADER_OUTPUTS}
    COMMAND "${GLSLC_EXECUTABLE}" -g -fshader-stage=compute --target-env=vulkan1.0 -DFUSED_FP16=1 -mfmt=c "${FUSED_PREPROC_SHADER}" -o "${GENERATED_SHADER_DIR}/waifu2x_fused_preproc_fp16.inc"
    COMMAND "${GLSLC_EXECUTABLE}" -g -fshader-stage=compute --target-env=vulkan1.0 -DFUSED_FP16=1 -mfmt=c "${FUSED_POSTPROC_SHADER}" -o "${GENERATED_SHADER_DIR}/waifu2x_fused_postproc_fp16.inc"
    COMMAND "${GLSLC_EXECUTABLE}" -g -fshader-stage=compute --target-env=vulkan1.0 -mfmt=c "${FUSED_PREPROC_SHADER}" -o "${GENERATED_SHADER_DIR}/waifu2x_fused_preproc_fp32.inc"
    COMMAND "${GLSLC_EXECUTABLE}" -g -fshader-stage=compute --target-env=vulkan1.0 -mfmt=c "${FUSED_POSTPROC_SHADER}" -o "${GENERATED_SHADER_DIR}/waifu2x_fused_postproc_fp32.inc"
    DEPENDS "${FUSED_PREPROC_SHADER}" "${FUSED_POSTPROC_SHADER}"
    VERBATIM
)
add_custom_target(waifu2x-fused-shaders DEPENDS ${FUSED_SHADER_OUTPUTS})

# Source files
set(WAIFU2X_SOURCES
    waifu2x.cpp
    anime4k.cpp
    qnn_backend.cpp
    waifu2x_jni.cpp
)

# Create shared library
add_library(waifu2x-jni SHARED ${WAIFU2X_SOURCES})
add_dependencies(waifu2x-jni waifu2x-fused-shaders)

# Include directories
target_include_directories(waifu2x-jni PRIVATE
    ${CMAKE_SOURCE_DIR}
    ${GENERATED_SHADER_DIR}
)

if(QNN_SDK_DIR AND ANDROID_ABI STREQUAL "arm64-v8a")
    if(NOT EXISTS "${QNN_SDK_DIR}/include/QNN/QnnInterface.h")
        message(FATAL_ERROR "QNN headers not found under ${QNN_SDK_DIR}/include/QNN")
    endif()

    target_compile_definitions(waifu2x-jni PRIVATE MIHON_ENABLE_QNN=1)
    target_include_directories(waifu2x-jni PRIVATE "${QNN_SDK_DIR}/include/QNN")
else()
    target_compile_definitions(waifu2x-jni PRIVATE MIHON_ENABLE_QNN=0)
endif()

# Link libraries
target_link_libraries(waifu2x-jni
    ncnn
    android
    log
    jnigraphics
    GLESv3
    EGL
    dl
)
