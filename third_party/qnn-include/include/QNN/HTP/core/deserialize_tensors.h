//==============================================================================
//
// Copyright (c) 2021-2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef DESERIALIZE_TENSORS_H
#define DESERIALIZE_TENSORS_H 1

#include <cstdio>
#include <cassert>
#include <cstring>
#include <memory>
#include <vector>
#include <tuple>
#include "limits.h"
#include "log.h"

#include "forward_classes.h"
#include "serdes_tensors.h"

namespace hnnx {

// see comment in serdes_tensors.h for overview of how this works.

class Deserializer;

class DeserTensorConn : public SerTensorConnDefs {
    typedef unsigned tensor_idx;
    typedef Tensor const *ptr_type;

    // this collects all of the tensor_def we have seen. index is seq_index-1.
    std::vector<ptr_type> defined_tensors;

  public:
    DeserTensorConn() {}
    // process a tensor definition
    void tensor_def(Deserz &, ptr_type);
    // process n tensor refs.
    void tensor_refs(Deserz &, ptr_type *ptrs, unsigned num);
    // process a tensor ref
    void tensor_ref(Deserz &dctx, ptr_type &ptr) { tensor_refs(dctx, &ptr, 1); }

    // TODO: remove these two, we don't use them, and should not.
    // read an identity (for use in subsequent need_fixup)
    tensor_idx read_identity(Deserz &);
    // apply the identity to 'fix' a tensor pointer (usually now, sometimes later
    void need_fixup(tensor_idx ident, ptr_type *dst);

    // 'reserve' the defined tensors to avoid allocation overhead...
    inline void reserve_tensors(const size_t n) { defined_tensors.reserve(n); }
    // resize the 'defined tensors' table to its full capacity (specified).
    // Used only in multi-thread deserialize, prior to deserializing the runlist.
    inline void resize_tensordef_table(const size_t n) { defined_tensors.resize(n); }

    // this is for use by 'reference fixup' code, in concurrent deserialize.
    std::vector<ptr_type> const &get_defined_tensors() const { return defined_tensors; }

  protected:
    tensor_idx read_identity_inline(Deserz &);
    void apply_fixup_inline(tensor_idx idx, ptr_type *dst);
};

} // namespace hnnx

#endif // DESERIALIZE_TENSORS_H
