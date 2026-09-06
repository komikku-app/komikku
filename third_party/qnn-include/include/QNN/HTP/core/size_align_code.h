//=============================================================================
//
//  Copyright (c) Qualcomm Technologies, Inc.
//  All Rights Reserved.
//  Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//============================================================================

#ifndef SIZE_ALIGN_CODE_H
#define SIZE_ALIGN_CODE_H

#include <cstddef>
#include <cstdint>

PUSH_VISIBILITY(default)

namespace hnnx {

// LCOV_EXCL_START [SAFTYSWCCB-1736] constexprs resolved during compile time
template <size_t N> unsigned constexpr log2_floor_of()
{
    if constexpr (N < 4) {
        return N >= 2 ? 1 : 0; // note, 0->0
    }
    size_t x = N;
    unsigned res = 2;
    while (x >= 8) {
        x >>= 1;
        res++;
    }
    return res;
}

// LCOV_EXCL_STOP

// size_align_code_t combines the size and alignment of an op in a size_t word.

class size_align_code_t {
    // this has 'K' in lower 4 bits, and 'W' in upper bits;
    // the alignment is 1 << K, and the size is W << K.
    size_t code;

  public:
    constexpr size_align_code_t() : code(0) {}
    constexpr size_align_code_t(size_align_code_t const &) = default;
    constexpr size_align_code_t(size_align_code_t &&) = default;
    constexpr size_align_code_t &operator=(size_align_code_t const &) = default;
    constexpr size_align_code_t &operator=(size_align_code_t &&) = default;
    ~size_align_code_t() = default;

    // construct for a given op type T, e.g. size_align_code_t::for_type<OpType>();
    template <typename T> static constexpr size_align_code_t for_type()
    {
        size_align_code_t result{};
        constexpr size_t sz = sizeof(T);
        constexpr size_t algn = alignof(T);
        static_assert(algn >= 1u && algn <= 32768u && (algn & (algn - 1)) == 0, "bad alignment");
        static_assert(sz > 0 && sz % algn == 0, "bad size");
        constexpr unsigned log2a = log2_floor_of<algn>();
        result.code = (sz / algn) * 16 | log2a;
        return result;
    }
    size_t constexpr size() const { return (code >> 4u) << (code & 0xFu); }
    size_t constexpr align() const { return size_t(1) << (code & 0xFu); }
    bool constexpr is_null() const { return code == 0; }
};

} // namespace hnnx

POP_VISIBILITY()

#endif
