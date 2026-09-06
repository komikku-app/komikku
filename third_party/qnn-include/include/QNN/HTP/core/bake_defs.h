//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef BAKE_DEFS
#define BAKE_DEFS 1
#include <cstdint>
#include <algorithm>
#include <utility>
#include <tuple>

#include "executable.h"

// Contains defs for host-side and target side, so try not
// to add too many 'host only' things.

#ifdef __hexagon__
#define HNNX_ARCH_CAN_RUN_BAKED 1
#endif

namespace hnnx {

namespace bake {

using tgt_ptr_word = unsigned;
using tgt_sizet_word = unsigned;
static constexpr unsigned tgt_ptr_bytes = sizeof(tgt_ptr_word);
static constexpr unsigned tgt_sizet_bytes = sizeof(tgt_sizet_word);
static constexpr bool op_has_graphp = false;
static constexpr unsigned tensor_uptr_ptrs = 2;
static constexpr unsigned max_opaquet_align = 1024; // must be power of 2

// This should be OK as a first approx: includes hexagon and x86-32
static constexpr bool host_can_run_baked = sizeof(void *) == tgt_ptr_bytes;

inline unsigned constexpr round_up(unsigned x, unsigned m)
{
    return ((x + (m - 1)) / m) * m;
}

// functions to calculate size, align of various things. They
// are included in target build so we can static_assert that sizes are what we think they are.
// (all must be constexpr).

// {size, alignment} of typical_op
inline constexpr std::pair<unsigned, unsigned> typical_op_tgt_size_align(unsigned n_in, unsigned n_out)
{
    // 1 pointer per input, plus tensor_uptr_ptrs per output; but if n_in = n_out == 0, it's 1 pointer.
    // (for a 'fill' byte).
    unsigned num_io_ptrs = n_in + n_out * tensor_uptr_ptrs;
    if (num_io_ptrs == 0) num_io_ptrs = 1; // n_in = n_out = 0 case
    return {tgt_ptr_bytes * ((op_has_graphp ? 2 : 1) // vptr, and maybe Graph *
                             + num_io_ptrs), // inputs and outputs
            tgt_ptr_bytes}; // align
}

// 'tensor_op_tgt_size_align is used for crate accounting of ShapeWrapperOp, ConstWrapperOp, DummyOp<N>
// In a proper 'baked graph' we don't need to insert these, just the tensors...

inline constexpr std::pair<unsigned, unsigned> tensor_op_tgt_size_align(unsigned n_out)
{
    // happens to be the same as TypicalOp with no inputs...
    return typical_op_tgt_size_align(0, n_out);
}

// {size, alignment, extra} of typical_op_with_compiler
//    extra_len is the len of the extra data
//    extra_align is its alignment.
// The 3rd return value is the offset of the 'extra' within the image.
//
inline constexpr std::tuple<unsigned, unsigned, unsigned>
typical_op_extra_tgt_size_align(unsigned n_in, unsigned n_out, unsigned extra_len, unsigned extra_align)
{
    std::pair<unsigned, unsigned> base_size = typical_op_tgt_size_align(n_in, n_out);
    unsigned extra_offs = base_size.first;
    if (extra_len > 0) {
        extra_align = std::max(extra_align, base_size.second);
        extra_len = round_up(extra_len, extra_align);
        extra_offs = round_up(extra_offs, extra_align);
        base_size.first = extra_offs + extra_len;
        base_size.second = extra_align;
    }
    return {base_size.first, base_size.second, extra_offs};
}

// {size, alignment} of variadic op (without the in, out array contents)!
constexpr std::pair<unsigned, unsigned> variadic_op_tgt_size_align(unsigned n_in, unsigned n_out)
{
    const unsigned cratevec_words = 2;
    return {tgt_ptr_bytes * (1 // vptr
                             + (op_has_graphp ? 1 : 0) // Graph *
                             + 2 * cratevec_words), // two cratevecs
            tgt_ptr_bytes}; // align
}
// {size, alignment} of simple_op_wrapper (without the in, out array contents)!
constexpr std::pair<unsigned, unsigned> simplewrap_op_tgt_size_align(unsigned n_in, unsigned n_out)
{
    // this is just one more pointer than a variadic op...
    const auto var_result = variadic_op_tgt_size_align(n_in, n_out);
    return {var_result.first + tgt_ptr_bytes, var_result.second};
}

// {size, alignment} of a ChunkPreloadOp
constexpr std::pair<unsigned, unsigned> chunk_preload_op_tgt_size_align()
{
    return {tgt_ptr_bytes * (1 // vptr
                             + (op_has_graphp ? 1 : 0) // Graph *
                             + 2), // ptr, len;
            tgt_ptr_bytes}; // align
}

//
// {size_align} of Shape<RANK> object
//
constexpr std::pair<unsigned, unsigned> shape_tgt_size_align(unsigned rank)
{
    // tgt_sizet_bytes * (1 + 1 + 2 * rank) =
    //      vtable ptr
    //      shapeflag flags + padding[]
    //      std::array<size_t, Rank> dims
    //      std::array<size_t, Rank> max_dims
    //  + rank = std::array<uint8_t, Rank> pad
    return {round_up(tgt_sizet_bytes * (1 + 1 + 1 + 2 * rank) + rank, tgt_sizet_bytes), tgt_sizet_bytes};
}

//
// {size_align} of DynamicShape<RANK> object
//
constexpr std::pair<unsigned, unsigned> dynamic_shape_tgt_size_align(const unsigned rank)
{
    // std::array<size_t, Rank> dims == tgt_sizet_bytes * rank
    // (shapeflag flags + padding[]) + vtable ptr + dynamic_state =  (3 * tgt_sizet_bytes)
    return {round_up(tgt_sizet_bytes * rank + (4 * tgt_sizet_bytes), tgt_sizet_bytes), tgt_sizet_bytes};
}

//
// {size_align} of interface object (may or may not be quantized)
//
constexpr std::pair<unsigned, unsigned> interface_tgt_size_align(bool is_quantized)
{
    return {tgt_sizet_bytes + (is_quantized ? round_up(3 * 4, tgt_sizet_bytes) : 0), tgt_sizet_bytes};
}

// {size_align} of Tensors, of three different forms:
//
// 'general' tensor
//
constexpr std::pair<unsigned, unsigned> tensor_general_tgt_size_align()
{
    return {tgt_sizet_bytes * 4 + 2 * tgt_ptr_bytes, tgt_sizet_bytes};
}

// 'shape' tensor, of given rank.
//
constexpr std::pair<unsigned, unsigned> tensor_shape_tgt_size_align(unsigned rank)
{
    return {tgt_sizet_bytes * ((rank == 0 ? 1 : rank) + 1), tgt_sizet_bytes};
}

// 'scalar' tensor, need to know if the interface is 'quantized' or not
// Note, this assumes all value are <= size_t bytes.
//
constexpr std::pair<unsigned, unsigned> tensor_scalar_tgt_size_align(bool is_quantized)
{
    const unsigned ifc_size = interface_tgt_size_align(is_quantized).first;
    return {tgt_sizet_bytes * 2 + ifc_size, tgt_sizet_bytes};
}
// sizeof OpExtraInfo on target: {long long, 2 * unsigned, char *, 4 * padbyte}
constexpr std::pair<unsigned, unsigned> OpExtraInfo_size_align = {24, 8};

// The size of a SliceDispatchOp for the given number of slices.
// Currently it's always the same regardless of 'nslices'; We may introduce 'right-sized'
// value, in which case 'exact=true' will get the 'real' size; but exact = false will always
// give the full size.
constexpr std::pair<unsigned, unsigned> slice_dispatch_op_size_align(unsigned const nslices, bool const exact = false)
{
    return {tgt_sizet_bytes * ((op_has_graphp ? 5 : 4) + 3 * Executable::MAX_OP_SLICES), tgt_sizet_bytes};
}

// The size of a Predicated Op
constexpr std::pair<unsigned, unsigned> pred_op_size_align()
{
    return {tgt_sizet_bytes * ((op_has_graphp ? 5 : 4) + 3), tgt_sizet_bytes};
}

// this is used in e.g.
// if constexpr(host_can_run_baked) static_assert(size_align_matches<TypicalOp>(N_IN, N_OUT));

template <typename T, typename SZAL> constexpr bool size_align_matches(SZAL sz)
{
    return sizeof(T) == std::get<0>(sz) && alignof(T) == std::get<1>(sz);
}

// This is a utility to check that a type T has a given size and aligment, using static_assert;
// Just need to include a call to 'do-nothing' bake::check_size_align<T>::template check<SIZE,ALIGN>();
// The static assert is *disabled* unless compiling on hexagon (or compatible host).
//
// It's more complex than it needs to be, since it's designed to make sure the type and
// numbers wind up in the error message, e.g. you could end up with
//   error: static_assert failed due to requirement 'claimed(40) == actual(48)' "size not as claimed"
//        static_assert(claimed(CLAIMED_SIZE) == actual(ACTUAL_SIZE), "size not as claimed");
// ... note: in instantiation of function template specialization 'check_szal<MyType>::check_size_align<..., ...>'
//
template <typename T> struct check_size_align {
    static constexpr int claimed(int K) { return K; }
    static constexpr int actual(int K) { return K; }
    template <int CLAIMED_SIZE, int ACTUAL_SIZE = sizeof(T)> static constexpr bool check_size()
    {
        static_assert(claimed(CLAIMED_SIZE) == actual(ACTUAL_SIZE), "size not as claimed");
        return CLAIMED_SIZE == ACTUAL_SIZE;
    }
    template <int CLAIMED_ALIGN, int ACTUAL_ALIGN = alignof(T)> static constexpr bool check_align()
    {
        static_assert(claimed(CLAIMED_ALIGN) == actual(ACTUAL_ALIGN), "align not as claimed");
        return CLAIMED_ALIGN == ACTUAL_ALIGN;
    }

    template <int CLAIMED_SIZE, int CLAIMED_ALIGN> static constexpr bool check()
    {
        bool result = true;
        if constexpr (host_can_run_baked) {
            result = check_size<CLAIMED_SIZE>() && check_align<CLAIMED_ALIGN>();
        }
        return result;
    }
};

} // namespace bake

//
// op_opaque_tgt_info<OpaqueT> must be specialized for each OpaqueT used in TypicalOpWithCompiler
//
template <typename OpaqueT> struct op_opaque_tgt_info {
    // static constexpr unsigned length = ..; // length of the struct on target CPU
    // static constexpr unsigned alignment = ... // aligbment on target CPU
};

} // namespace hnnx

#endif // BAKE_DEFS
