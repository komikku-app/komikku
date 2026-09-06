//==============================================================================
//
// Copyright (c) 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef SERIALIZE_OPLIST
#define SERIALIZE_OPLIST 1
#include <cstdint>
#include <array>
#include <utility>

#include "forward_classes.h"
#include "bake_defs.h"

namespace hnnx {

class Checkpoints;

namespace bake {

template <unsigned X> static constexpr unsigned log2_ceil()
{
    if constexpr (X <= 16) {
        static_assert(X > 0, "log2_ceil<0> not valid!");
        return (X <= 2) ? (X - 1) : (X <= 4) ? 2 : (X <= 8) ? 3 : 4;
    } else {
        return log2_ceil<(X + 15) / 16>() + 4;
    }
}

// this
template <typename OpaqueT> inline constexpr unsigned encode_opaquet_size()
{
    // op_opaque_tgt_info<OpaqueT> must be specialized to provide length and alignment ('on target').
    constexpr unsigned length = op_opaque_tgt_info<OpaqueT>::length;
    if constexpr (length == 0) { // if 0, we don't care about the alignment
        return 2;
    } else {
        constexpr unsigned align = op_opaque_tgt_info<OpaqueT>::alignment;
        // otherwise, alignment must be a suitable power of 2, and length must be a multiple of it.
        static_assert(align >= 1 && align <= max_opaquet_align && (align & (align - 1)) == 0, "bad alignment value");
        static_assert(length % align == 0, "length must be a multiple of alignment");
        constexpr unsigned lower_bits = (align <= 4) ? 2 : log2_ceil<align>();
        return (length << 8) | lower_bits;
    }
}

} // namespace bake

class OpSerHandle;

class SerOpsInterface {
    friend class hnnx::OpSerHandle;

  protected:
    SerOpsInterface() = default;
    ~SerOpsInterface() = default;
    SerOpsInterface(SerOpsInterface const &) = delete;
    SerOpsInterface &operator=(const SerOpsInterface &) = delete;
    SerOpsInterface(SerOpsInterface &&) = delete;
    SerOpsInterface &operator=(SerOpsInterface &&) = delete;
    // Common handler for op_typical, op_variadic, op_typical_with_extra.
    // mode = 0 for op_typical
    //       = 1 for op_variadic
    //       = 3 for op_simpleop
    //   for op_typical_with_extra:
    //       lower 8 bits are log2(aligment) - must be >= 2, <= log2(max_opaquet_align)
    //       uppper 24 bits are size, multiple of alignment.
    //       If the size is 0, lower 8 bits are always 2.
    // So, codes 4..257 are available.
    static constexpr unsigned opMODE_typical = 0;
    static constexpr unsigned opMODE_variadic = 1;
    static constexpr unsigned opMODE_simpleop = 3;

    virtual void op_serialize_func(Op const *op, unsigned n_in, Tensor const *const *in_tens, unsigned n_out,
                                   uptr_Tensor const *out_tens, unsigned mode) = 0;
    // Used for ConstWrapperOp, ShapeWrapperOp, DummyN
    virtual void op_for_tensor_func(Op const *op, unsigned n_out, uptr_Tensor const *out_tens) = 0;

    virtual void prescan_ops_func(Op *const *seq_of_ops, unsigned n_ops, bool last = false) = 0;

  public:
    // 'top-level' sequencing calls
    // Before serializing the allocator,
    // (1) call 'graph_io_tensots' with prescan = true, just to prescan the tensors
    //    (no serialization is done)
    // (2) present all Ops to prescan_ops in the same order as they will be serialized.
    //     This may be done in more than one call, with 'last = true' on the last one.
    //     (or finish with a call to prescan_ops_done).
    inline void prescan_ops(std::vector<Op *> const &seq_of_ops, bool last = false)
    {
        prescan_ops_func(seq_of_ops.data(), seq_of_ops.size(), last);
    }
    inline void prescan_ops(Op *const *seq_of_ops, unsigned n_ops, bool last = false)
    {
        prescan_ops_func(seq_of_ops, n_ops, last);
    }
    inline void prescan_ops_done() { prescan_ops_func(nullptr, 0, true); }

    virtual void graph_io_tensors(unsigned n_in, uptr_Tensor const *in_tensors, unsigned n_out,
                                  uptr_Tensor const *out_tensors, bool is_prescan = false) = 0;
    virtual void checkpoints_table(hnnx::Checkpoints const &) = 0;
    virtual void before_runlists(unsigned nops_norun, unsigned nops_main, unsigned nops_vector, unsigned nops_mtx,
                                 unsigned nops_elt) = 0; // call before serializing 'non-runlist'
    virtual void after_non_runlist() = 0; // call after serializing 'non_runlist', before 'combined runlist'
    virtual void after_runlist() = 0; // call after runlist complete.

    // tensor_serialize_func needs to know what basic thing it's dealing with, and it then
    // can discover everything else via virtual calls.
    static constexpr unsigned tensMODE_fail = 0; // used for tensors which can't serialize
    static constexpr unsigned tensMODE_general = 1; // Concrete tensor, all cases
    static constexpr unsigned tensMODE_shape = 2; // TensorShape<Rank>
    static constexpr unsigned tensMODE_scalar = 3; // TensorSlcrDT<DT>

    // This is called to serialize each op; op_seqno is the 0-based index
    // (i.e. the number of ops previously serialized).
    virtual void serialize_op(Op const &, unsigned op_seqno) = 0;
    // to be called from TypicalOpIoBase<N_OUT, N_IN>::serialize
    template <size_t N_IN, size_t N_OUT>
    inline void op_typical(Op const *op, std::array<const Tensor *, N_IN> const &inputs,
                           std::array<uptr_Tensor, N_OUT> const &outputs)
    {
        op_serialize_func(op, N_IN, inputs.data(), N_OUT, outputs.data(), opMODE_typical);
    }
    // to be called from TypicalOpWithCompiler<F, OpaqueT>::serialize, with OpaqueT explicitly specified
    template <typename OpaqueT, size_t N_IN, size_t N_OUT>
    inline void op_typical_with_extra(Op const *op, std::array<const Tensor *, N_IN> const &inputs,
                                      std::array<uptr_Tensor, N_OUT> const &outputs)
    {
        op_serialize_func(op, N_IN, inputs.data(), N_OUT, outputs.data(), bake::encode_opaquet_size<OpaqueT>());
    }

    // to be called from VariadicOpBase::serialize
    template <typename V_IN, typename V_OUT>
    inline void op_variadic(Op const *op, V_IN const &inputs, V_OUT const &outputs)
    {
        op_serialize_func(op, inputs.size(), inputs.data(), outputs.size(), outputs.data(), opMODE_variadic);
    }

    // to be used for SimpleOpWrapper::serialize; op_serialize_func will dynamic-cast to SimpleOpWrapper
    // and then obtain the proper type.
    template <typename V_IN, typename V_OUT>
    inline void op_simpleop(Op const *op, V_IN const &inputs, V_OUT const &outputs)
    {
        op_serialize_func(op, inputs.size(), inputs.data(), outputs.size(), outputs.data(), opMODE_simpleop);
    }

    // Used for ConstWrapperOp, ShapeWrapperOp, DummyN
    inline void op_for_tensor(Op const *op, unsigned n_out, uptr_Tensor const *out_tens)
    {
        op_for_tensor_func(op, n_out, out_tens);
    }
    inline void op_for_tensor(Op const *op, uptr_Tensor const &out_tens) { op_for_tensor_func(op, 1, &out_tens); }

    // Each call to serialize method of Tensor goes to tensor_serialize.
    // Normally, these occur within an 'Op' serialize method, but 'graph in/out' tensors are also
    // serialized.
    virtual void tensor_serialize(Tensor const *tens) = 0;

    // Given a pointer to a ShapeFlags which is really a Shape<RANK>, serialize its content.
    // Does not include 'shared object' protocol (only called when we have a new one)
    virtual void shape_serialize(ShapeFlags const *basep, unsigned rank) = 0;

    // used to handle framework ops.
    // The serialize method calls methods of the returned handle (which call protected .spcl_XX virtual methods)
    // and then .spcl_done is called when the handle is deleted. So you can do the whole thing in one line,
    // e.g.
    //   sctx.op_special(this).data_u32({val1, val2}}.size_vec(ptr);
    //
    // It is expected that no other serialization activity occurs between the call to .op_special(),
    // and the call to spcl_done (when the handle is deleted).
    //
    virtual OpSerHandle op_special(Op const *op) = 0;

  protected:
    // methods called by methods of the 'OpSerHandle'
    // See OpSerHandle to see what they do.
    virtual void spcl_done(OpSerHandle &) = 0; // called by ~OpSerHandle
    virtual void spcl_add_u32(OpSerHandle &, uint32_t const *p, unsigned n) = 0;
    virtual void spcl_add_sized_vec(OpSerHandle &, uint32_t const *data, bool extra) = 0;
    virtual void spcl_fill_nullptr(OpSerHandle &, unsigned n) = 0;

    OpSerHandle make_opser_handle(unsigned info);
};

class OpSerHandle {
    friend class SerOpsInterface;

  protected:
    SerOpsInterface &owner;
    unsigned info;
    OpSerHandle(SerOpsInterface &owner_in, unsigned info_in) : owner(owner_in), info(info_in) {}

  public:
    inline ~OpSerHandle() { owner.spcl_done(*this); }
    OpSerHandle(const OpSerHandle &) = delete;
    OpSerHandle &operator=(const OpSerHandle &) = delete;
    OpSerHandle(OpSerHandle &&) = delete;
    OpSerHandle &operator=(OpSerHandle &&) = delete;
    //////////////////////////////
    // add literal u32 values
    // (1) generic ptr/offs
    inline OpSerHandle &data_u32(uint32_t const *p, unsigned n)
    {
        owner.spcl_add_u32(*this, p, n);
        return *this;
    }
    // (2) { vals, ... }
    inline OpSerHandle &data_u32(std::initializer_list<uint32_t> vals)
    {
        owner.spcl_add_u32(*this, vals.begin(), vals.size());
        return *this;
    }
    // (3) single value
    inline OpSerHandle &data_u32(uint32_t val)
    {
        owner.spcl_add_u32(*this, &val, 1);
        return *this;
    }
    ///////////////////////////
    // Add an 'outboard' array of literal u32, as in Spill/Fill/BlockZap/McSend.
    // The first word must be the len of the remaining values (in bytes; >=4 and a multiple of 4).
    // pickle format consists of writing the entire array (with the first word serving as the len).
    // If 'extra' is true, it means that array has an extra word at the end, not in the count,
    // and not serialized (such as the 0-marker at end of blockzap).
    inline OpSerHandle &sized_vec(uint32_t const *arr_data, bool extra = false)
    {
        owner.spcl_add_sized_vec(*this, arr_data, extra);
        return *this;
    }
    ///////////////////////////
    // add one or more 'null pointer fill', this has no effect on the pickle but it reserves
    // pointer slot(s) in the baked op image.
    inline OpSerHandle &fill_nullptr(unsigned n = 1)
    {
        owner.spcl_fill_nullptr(*this, n);
        return *this;
    }
};
// This is a way for subclasses of SerOpsInterface to make an OpSerHandle via its protected ctor.
//
inline OpSerHandle SerOpsInterface::make_opser_handle(unsigned info)
{
    return OpSerHandle(*this, info);
}

} // namespace hnnx

#endif // SERIALIZE_OPLIST
