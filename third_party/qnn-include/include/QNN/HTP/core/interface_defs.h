//==============================================================================
//
// Copyright (c) 2020 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef INTERFACE_DEFS_H
#define INTERFACE_DEFS_H 1

#include "dtype_enum.h"

#include <cstddef>
#include <cstdint>

typedef unsigned long long OpId;
typedef unsigned OpId_32;
typedef unsigned size_t_32;
#define MAX_DIMENSIONS 8

// must be the same layout as struct input
struct InputDef {
    uint32_t input_id;
    uint32_t output_idx;
};

struct OutputDef {
    NN_UINT32_T rank;
    DType dtype;
    size_t max_sizes[MAX_DIMENSIONS];
    NN_INT32_T zero_offset;
    float stepsize;
};

struct InputDef_CanFormat {
    OpId_32 input_id;
    size_t_32 output_idx;
};

struct OutputDef_CanFormat {
    NN_UINT32_T rank;
    size_t_32 max_sizes[MAX_DIMENSIONS];
    DType dtype;
    NN_INT32_T zero_offset;
    float stepsize;
};
struct Const_prefix_CanFormat {
    size_t_32 reclen;
    NN_UINT32_T rectype;
    OpId_32 node_id;
    size_t_32 rank;
    DType dtype;
    NN_INT32_T zero_offset;
    float stepsize;
    size_t_32 datalen;
};

#endif
