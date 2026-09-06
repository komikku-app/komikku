//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef DCRATE_INLINES_H
#define DCRATE_INLINES_H 1

#include <cstddef>
#include <cstdint>
#include <cassert>

#include "macros_attribute.h"
#include "deser_concurrent.h"
#include "crate.h"

namespace hnnx {

// alloc 'amount' bytes with given alignment.
inline void *DCrate::do_alloc(const size_t align, const size_t amount)
{
    size_t basep = size_t(nextp);
    if (align > 4) {
        basep = (basep + (align - 1)) & ~(align - 1);
    }
    size_t const next_base = basep + amount;
    if (next_base > (size_t)limitp) return nullptr;
    nextp = (void *)next_base; // update 'nextp' ...
    return (void *)basep;
}

template <typename T, bool DTOR_OK /*=false*/> inline T *DCrate::alloc_array(const size_t n)
{
    if (nextp != nullptr) {
        void *const allocp = do_alloc(alignof(T), sizeof(T) * n);
        if (allocp) return (T *)allocp;
    }
    return cratep->alloc_array<T, DTOR_OK>(n);
}

template <typename T, typename... Args> inline T *DCrate::emplace(Args &&...args)
{
    if (nextp != nullptr) {
        void *const allocp = do_alloc(alignof(T), sizeof(T));
        if (allocp) {
            new (allocp) T(std::forward<Args>(args)...);
            return (T *)allocp;
        }
    }
    return cratep->emplace<T>(std::forward<Args>(args)...);
}

template <>
inline void *DCrate::emplace_explicit(Deserz &dctx, deserialize_op_func const init_func,
                                      deserialize_dtor_func const dtor_func, size_align_code_t const size_al)
{
    if (nextp != nullptr) {
        void *const allocp = do_alloc(size_al.align(), size_al.size());
        if (allocp) {
            init_func(allocp, dctx);
            return allocp;
        }
    }
    return cratep->emplace_explicit(dctx, init_func, dtor_func, size_al);
}

// this will be used in place of 'emplace' when the constructor parms
// are just 'Deserz &'
template <typename T> inline T *DCrate::emplace0(Deserz &dctx)
{
    deserialize_op_func const ctor = [](void *const ptr, Deserz &dctx) -> void * {
        new (ptr) T(dctx);
        return ptr;
    };
    if (nextp != nullptr) {
        void *const allocp = do_alloc(alignof(T), sizeof(T));
        if (allocp) {
            (ctor)(allocp, dctx);
            return (T *)allocp;
        }
    }
    return (T *)cratep->emplace_explicit(dctx, ctor, nullptr, size_align_code_t::for_type<T>());
}
// init method of cratevec<T> using 'Dcrate' is declared here to avoid header inclusion madness.
//
template <typename T> inline void hnnx::cratevec<T>::init(hnnx::DCrate *crate_p, size_t n)
{
    assert(m_len == 0);
    if (n) {
        m_ptr = crate_p->alloc_array<T, true>(n);
        std::uninitialized_value_construct_n(m_ptr, n);
        m_len = n;
    }
}

} // namespace hnnx

#endif // DCRATE_INLINES_H
