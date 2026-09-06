//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef SERIALIZE_REGISTER_H
#define SERIALIZE_REGISTER_H 1

#include <stdexcept>
#include "crate.h"
#include "op_info.h"
#include "macros_attribute.h"
#include "weak_linkage.h"
#include "size_align_code.h"
#include "dcrate_inlines.h"

namespace hnnx {

class SimpleOpWrapper;

template <typename T> struct deserialize_tensor_using_constructor {
    static uptr_Tensor deserialize(Deserz &dctx)
    {
        // put the deserialized
        // Tensor into the crate, using a 'Tensor_Deleter' which won't actually try to delete it.
        Tensor *const t_ptr = dctx.dcrate()->emplace0<T>(dctx);
        return std::unique_ptr<Tensor, Tensor_Deleter>(t_ptr, Tensor_Deleter(true));
    }
};

// Allocation/deallocation for Op

template <typename T> struct alloc_func_for_op {
    static void *alloc_func(void *ptr, Deserz &dctx) { return new (ptr) T(dctx); }
    // this is here so that specializations of deserialize_tensor_using_constructor
    // can be made which have size 0; this is only for 'ConatWrapper' and 'ShapeWrapper'.
    static constexpr size_align_code_t op_size_align = size_align_code_t::for_type<T>();
};

PUSH_VISIBILITY(default)
API_EXPORT void deserialize_simple_op_wrapper(void *, Deserz &dctx, std::unique_ptr<SimpleOpBase> sop_in);
POP_VISIBILITY()

template <typename T> struct alloc_func_for_op_ext {
    static void *alloc_func(void *ptr, Deserz &dctx)
    {
        auto sop = std::make_unique<T>();
        deserialize_simple_op_wrapper(ptr, dctx, std::move(sop));
        return ptr;
    }
};

template <typename T> struct dealloc_func_for_op {
    static void func(Graph *graph_in, void *ptr)
    {
        if constexpr (has_clear<T>) {
            static_cast<T *>(ptr)->clear(graph_in);
        }
        static_cast<T *>(ptr)->~T();
    }
};
// specialize for 'int'; used for all trivially-destructable types.
template <> struct dealloc_func_for_op<int> {
    static void func(Graph *graph_in, void *ptr) {}
};

template <typename T> //
inline constexpr deserialize_dtor_func get_dealloc_func_for_op()
{
    if constexpr (!std::is_trivially_destructible<T>::value) {
        return dealloc_func_for_op<T>::func;
    } else {
        // we only need one of these
        return dealloc_func_for_op<int>::func;
    }
}

template <typename OPTYPE> inline void register_framework_op(char const *opname)
{
    using alloc_func = alloc_func_for_op<OPTYPE>;
    register_op_info(typeid(OPTYPE), hnnx::cost_function_t(StandardCosts::FAST), 0, (SimpleOpFactory) nullptr, false,
                     opname);
    op_deserializer_fn const fn(alloc_func::alloc_func, get_dealloc_func_for_op<OPTYPE>(), alloc_func::op_size_align);
    deserialize_op_register(&typeid(OPTYPE), opname, fn);
}

} // namespace hnnx
#endif // SERIALIZE_REGISTER_H
