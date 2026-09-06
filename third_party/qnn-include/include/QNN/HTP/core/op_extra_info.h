//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OP_EXTRA_INFO_H
#define OP_EXTRA_INFO_H 1

#include <utility>

#include "interface_defs.h"

namespace hnnx {

/*
    map Op* to a few properties, to avoid the need to keep them in the Op
    object. Currently contains the ID, the gate/done checkpoint indices, and
    the number of scratch outputs. This sctruct is part of the runlist and its 
    memory footprint is important. It is currently 24 bytes:
        opid: 8 bytes
        chkpts: 8 bytes
        op_tag: 4 bytes
        (alignment padding : 4)
*/

// OpExtraInfo - the 'extra_list' componen of runlists, as an array
// of these, so we want to keep them small; any attributes that are
// only needed at prepare time can go in the OpExtraAttrib which is a subclass.
// The 'mapped' type of m_op_extra_info_map is OpExtraAttrib.

struct OpExtraInfo {
    using Chkpts = std::pair<int, int>;

    OpId id;
    Chkpts chkpts;
    const char *op_tag;
    explicit OpExtraInfo(OpId id_in) : id(id_in), chkpts(-1, -1) {}
    OpExtraInfo(OpId id_in, int cg, int dc) : id(id_in), chkpts(cg, dc) {}
    OpExtraInfo() : OpExtraInfo(0) {}

    bool valid() const { return id != 0; };
    void clear() { id = 0; };
};

struct OpExtraAttrib : public OpExtraInfo {
    // fields below here are valid only at prepare time.
    bool for_hlx : 1; // HVX op to be moved to HLX
    unsigned int num_scratch_outputs : 5;
    unsigned int self_slicing_op_nslices : 4; // 0 means just 1 slice; otherwise >= 2
    unsigned int predicate_offset_sense : 24; // predicate_offset.23::sense.1

    // can construct from OpExtraInfo
    OpExtraAttrib() : OpExtraInfo() { clear_fields(); }
    OpExtraAttrib(OpExtraInfo const &baseval) : OpExtraInfo(baseval) { clear_fields(); }
    explicit OpExtraAttrib(OpId id_in) : OpExtraInfo(id_in) { clear_fields(); }
    OpExtraAttrib(OpId id_in, int cg, int dc) : OpExtraInfo(id_in, cg, dc) { clear_fields(); }

    void clear_fields()
    {
        for_hlx = false;
        num_scratch_outputs = 0;
        self_slicing_op_nslices = 0;
        predicate_offset_sense = 0;
    }
};

} // namespace hnnx

#endif // OP_EXTRA_INFO_H
