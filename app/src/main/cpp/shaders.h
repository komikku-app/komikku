#ifndef WAIFU2X_SHADERS_H
#define WAIFU2X_SHADERS_H

#include <cstdint>

static const uint32_t waifu2x_fused_preproc_fp16_spv[] =
#include "waifu2x_fused_preproc_fp16.inc"
;
static const uint32_t waifu2x_fused_postproc_fp16_spv[] =
#include "waifu2x_fused_postproc_fp16.inc"
;
static const uint32_t waifu2x_fused_preproc_fp32_spv[] =
#include "waifu2x_fused_preproc_fp32.inc"
;
static const uint32_t waifu2x_fused_postproc_fp32_spv[] =
#include "waifu2x_fused_postproc_fp32.inc"
;

// TTA preproc shader (placeholder - not commonly used)
static const char waifu2x_preproc_tta_comp_data[] = R"(
#version 450
layout (local_size_x_id = 233, local_size_y_id = 234, local_size_z_id = 235) in;
layout (constant_id = 0) const int bgr = 0;
layout (binding = 0) readonly buffer bottom_blob { float bottom_blob_data[]; };
layout (binding = 1) writeonly buffer top_blob0 { float top_blob0_data[]; };
layout (binding = 2) writeonly buffer top_blob1 { float top_blob1_data[]; };
layout (binding = 3) writeonly buffer top_blob2 { float top_blob2_data[]; };
layout (binding = 4) writeonly buffer top_blob3 { float top_blob3_data[]; };
layout (binding = 5) writeonly buffer top_blob4 { float top_blob4_data[]; };
layout (binding = 6) writeonly buffer top_blob5 { float top_blob5_data[]; };
layout (binding = 7) writeonly buffer top_blob6 { float top_blob6_data[]; };
layout (binding = 8) writeonly buffer top_blob7 { float top_blob7_data[]; };
layout (binding = 9) writeonly buffer alpha_blob { float alpha_blob_data[]; };
layout (push_constant) uniform parameter { int w; int h; int cstep; int outw; int outh; int outcstep; int pad_top; int pad_left; int crop_x; int crop_y; int channels; int alphaw; int alphah; } p;
void main() {
    int gx = int(gl_GlobalInvocationID.x);
    int gy = int(gl_GlobalInvocationID.y);
    int gz = int(gl_GlobalInvocationID.z);
    if (gx >= p.outw || gy >= p.outh || gz >= p.channels) return;
    
    int x = clamp(gx + p.crop_x - p.pad_left, 0, p.w - 1);
    int y = clamp(gy + p.crop_y - p.pad_top, 0, p.h - 1);
    
    int pixel_idx = y * p.w + x;
    int channel_idx = gz;
    if (bgr == 1 && gz < 3) channel_idx = 2 - gz;
    
    float v = bottom_blob_data[pixel_idx * 4 + channel_idx];
    
    if (gz == 3) {
        int ax = gx - p.pad_left;
        int ay = gy - p.pad_top;
        if (ax >= 0 && ax < p.alphaw && ay >= 0 && ay < p.alphah)
            alpha_blob_data[ay * p.alphaw + ax] = v;
    } else {
        const float norm_val = 1.0 / 255.0;
        v = v * norm_val;
        int gzi = gz * p.outcstep;
        top_blob0_data[gzi + gy * p.outw + gx] = v;
        top_blob1_data[gzi + gy * p.outw + (p.outw - 1 - gx)] = v;
        top_blob2_data[gzi + (p.outh - 1 - gy) * p.outw + (p.outw - 1 - gx)] = v;
        top_blob3_data[gzi + (p.outh - 1 - gy) * p.outw + gx] = v;
        top_blob4_data[gzi + gx * p.outh + gy] = v;
        top_blob5_data[gzi + gx * p.outh + (p.outh - 1 - gy)] = v;
        top_blob6_data[gzi + (p.outw - 1 - gx) * p.outh + (p.outh - 1 - gy)] = v;
        top_blob7_data[gzi + (p.outw - 1 - gx) * p.outh + gy] = v;
    }
}
)";

// TTA postproc shader (placeholder - not commonly used)
static const char waifu2x_postproc_tta_comp_data[] = R"(
#version 450
layout (local_size_x_id = 233, local_size_y_id = 234, local_size_z_id = 235) in;
layout (constant_id = 0) const int bgr = 0;
layout (binding = 0) readonly buffer bottom_blob0 { float bottom_blob0_data[]; };
layout (binding = 1) readonly buffer bottom_blob1 { float bottom_blob1_data[]; };
layout (binding = 2) readonly buffer bottom_blob2 { float bottom_blob2_data[]; };
layout (binding = 3) readonly buffer bottom_blob3 { float bottom_blob3_data[]; };
layout (binding = 4) readonly buffer bottom_blob4 { float bottom_blob4_data[]; };
layout (binding = 5) readonly buffer bottom_blob5 { float bottom_blob5_data[]; };
layout (binding = 6) readonly buffer bottom_blob6 { float bottom_blob6_data[]; };
layout (binding = 7) readonly buffer bottom_blob7 { float bottom_blob7_data[]; };
layout (binding = 8) readonly buffer alpha_blob { float alpha_blob_data[]; };
layout (binding = 9) writeonly buffer top_blob { float top_blob_data[]; };
layout (push_constant) uniform parameter { int w; int h; int cstep; int outw; int outh; int outcstep; int offset_x; int gx_max; int channels; int alphaw; int alphah; } p;
void main() {
    int gx = int(gl_GlobalInvocationID.x);
    int gy = int(gl_GlobalInvocationID.y);
    int gz = int(gl_GlobalInvocationID.z);
    if (gx >= p.gx_max || gy >= p.outh || gz >= p.channels) return;
    
    float v;
    if (gz == 3) {
        v = alpha_blob_data[gy * p.alphaw + gx];
    } else {
        int gzi = gz;
        if (bgr == 1) gzi = 2 - gz;
        gzi = gzi * p.cstep;
        float v0 = bottom_blob0_data[gzi + gy * p.w + gx];
        float v1 = bottom_blob1_data[gzi + gy * p.w + (p.w - 1 - gx)];
        float v2 = bottom_blob2_data[gzi + (p.h - 1 - gy) * p.w + (p.w - 1 - gx)];
        float v3 = bottom_blob3_data[gzi + (p.h - 1 - gy) * p.w + gx];
        float v4 = bottom_blob4_data[gzi + gx * p.h + gy];
        float v5 = bottom_blob5_data[gzi + gx * p.h + (p.h - 1 - gy)];
        float v6 = bottom_blob6_data[gzi + (p.w - 1 - gx) * p.h + (p.h - 1 - gy)];
        float v7 = bottom_blob7_data[gzi + (p.w - 1 - gx) * p.h + gy];
        v = (v0 + v1 + v2 + v3 + v4 + v5 + v6 + v7) * 0.125;
        v = v * 255.0;
    }
    
    v = clamp(v + 0.5, 0.0, 255.0);
    int out_pixel_idx = gy * p.outw + gx + p.offset_x;
    top_blob_data[out_pixel_idx * 4 + gz] = v;
}
)";

#endif
