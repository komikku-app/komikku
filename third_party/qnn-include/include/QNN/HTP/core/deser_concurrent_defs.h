//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef DESER_CONCURRENT_DEFS_H
#define DESER_CONCURRENT_DEFS_H 1

#include <cstddef>
#include <cstdint>

namespace hnnx {

// NOTE: this file contains defs for concurrent deserialize which are needed on both decode and prepare
// side; mostly just the format of the Aux Data records.
// Defs needed only on decode side are in 'deser_concurrent.h', which #includes this file.

constexpr unsigned DesConcur_MIN_SEGMENTS = 8; // can't have less than this number.

// This is the number of runlist slots in the runlist_auxdata_seg_desc format.
// It must be >= the actual number. This number is coded into the start of the AuxData
// payload. If the number gets bigger, the reader of the aux-data
// record will need to be able to cope with the older, smaller value.

constexpr unsigned DesConcur_MAX_RUNLISTS = 4;

// The 'Aux Data' record describing the runlist partition has a payload formed of
// a runlist_auxdata_header, followed immediately by N+1 of runlist_auxdata_seg_desc.
// The number N is in the header; there may be additional words after, which can be
// ignored
//
// Aux Data header record.
// The 'record_version' is reserved to flag changes in the format, so that
//   if it changes, new skel can understand old records.
//    Currently, It has this format; most changes will expand one of the fields
//    so following this may be adequate to capture version changes; if it is not,
//   add flags in the upper bits.
//   bits 31 ..13 : reserved, 0
//   bit  12: set of crate sizes are calculated based on 'dynamic tensor' sizes
//   bits 11..8 length of the header in uint32's
//   bits 7..3 length of 'segment' record, in uint32's
//   bits 2..0 .. value of DesConcur_MAX_RUNLISTS
//
struct runlist_auxdata_header {
    unsigned record_version; // see above
    unsigned numsegs : 16; // number of segments; >= 8, likely <= 64 but who knows
    unsigned hdrflags : 16; // reserved for flags
    unsigned runlist_offset; // see below
};

// 'runlist_offset' is the offset, in u32's units, from the 'num_in_tensors' word
// to the 'n_ops_total' word. This is needed by 'weight share' processing in order to
// adjust the deser_offset values to accommodate changes in the encoding length of pointers.

// The N segments are described by an array of N+1 of runlist_auxdata_seg_desc;
// segment i is defined by arr[i] (start) and arr[i+1] (end).
// An exception is 'crate_seg_len'- this may be less than arr[i+1].crate_offset - arr[i].crate_offset
//  due to padding.
// In the final record arr[N]:
//    - crate_seg_len is not used (0)
//    - The *_list_posn records are the total length of the runlists
//    - the four 'base_*_index' values are all 1 greater than any index used in the graph
//
struct runlist_auxdata_seg_desc {
    uint32_t deser_offset; // where the input (pickle) data begins - reference point is the start of 'Runlist' as
    //                     // defined in docs/pickle_format.md, i.e. the location of 'n_ops_total' word
    uint32_t crate_offset; // offset in crate
    uint32_t crate_seg_len; // crate length needed (not used in final entry)
    uint32_t runlist_posn[DesConcur_MAX_RUNLISTS]; // where the segment starts in Op* runlist
    uint32_t execlist_posn[DesConcur_MAX_RUNLISTS]; // where the segment starts in 'execlist'
    uint32_t base_opseq_index; // first 'op_sequence_marker' index used in the segment.
    uint32_t base_tensor_index; // first tensor index defined this segment
    uint32_t base_blocktable_index; // first blocktable index defined in this segment
    uint32_t base_sharedobj_index; // first 'shared_object' index defined in this segment
};

// Bit in the header version indicating crate sizes allow for 'dynamic shapes'.
// NOTE: if that gets backed out later, leave this here but remove it from DesConcur_AUXDATA_REC_VERSION
//
constexpr unsigned DesConcur_AUXDATA_REC_VERSION_DYNSHAPE_SIZES = 4096;

constexpr unsigned DesConcur_AUXDATA_REC_VERSION = // composed of:
        ((sizeof(runlist_auxdata_header) / sizeof(uint32_t)) * 256 // header size
         + (sizeof(runlist_auxdata_seg_desc) / sizeof(uint32_t)) * 8 // seg desc len
         + DesConcur_MAX_RUNLISTS) |
        DesConcur_AUXDATA_REC_VERSION_DYNSHAPE_SIZES;

// values to be used to 'grow' old crate estimate to compensate for 'dyn shape' mismatch
constexpr unsigned DesConcur_CrateGrowPerTensor = 2; // number of words per 'tensor'
constexpr unsigned DesConcur_CrateGrowPerShared = 2; // number of words per 'shared object'

} // namespace hnnx

#endif // DESER_CONCURRENT_DEFS_H
