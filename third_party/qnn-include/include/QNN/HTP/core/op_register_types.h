//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OP_REGISTER_TYPES_H
#define OP_REGISTER_TYPES_H 1

#include "op_registry.h"
#include "serialize_register.h"
#include "cost_funcs.h"
#include "op_info.h"
#include "op_package_name.h"
#include "size_align_code.h"

#include <memory>
#include <string>
#include <utility>

#if !defined(ANDROID) && !(defined(_WIN32) && defined(_M_ARM64) && defined(PREPARE_DISABLED))
#define DESERIALIZATION_ENABLED 1
#else
#define DESERIALIZATION_ENABLED 0
#endif

namespace hnnx {

// package of info for op construction.
// LCOV_EXCL_START [SAFTYSWCCB-1736] constexprs resolved during compile time
struct op_reg_parms {
#ifndef PREPARE_DISABLED
    OpFactory newop;
    std::type_info const *tinf;
#endif
#if DESERIALIZATION_ENABLED == 1
    Op::tensor_deserializer_register_func deserializer_reg_func;
    deserialize_op_func deserialize_func;
    deserialize_dtor_func deserialize_dtor;
#endif
#ifndef PREPARE_DISABLED
    cost_function_t cost_f;
    Flags_word flags;
#endif
#if DESERIALIZATION_ENABLED == 1
    size_align_code_t size_align_code;
    inline constexpr size_t get_size() const { return size_align_code.size(); }
    inline constexpr size_t get_align() const { return size_align_code.align(); }
#endif
    template <typename Derived, int N> static constexpr op_reg_parms parms_for();
};

// generate an 'op_reg_parms' for a given Op type.
// this should be expanded only once for each Derived, so we want it inlined.
template <typename Derived, int N> [[gnu::always_inline]] constexpr op_reg_parms op_reg_parms::parms_for()
{
    return op_reg_parms
    {
#ifndef PREPARE_DISABLED
        Derived::create, &typeid(Derived),
#endif
#if DESERIALIZATION_ENABLED == 1
                Derived::get_tensor_deserializer_register_func(),
                test_flag_for(flags_for<Derived, N>(), Flags::IS_CONST) ? nullptr
                                                                        : alloc_func_for_op<Derived>::alloc_func,
                !std::is_trivially_destructible<Derived>::value ? dealloc_func_for_op<Derived>::func : nullptr,
#endif
#ifndef PREPARE_DISABLED
                get_costf<Derived>(), flags_for<Derived, N>(),
#endif
#if DESERIALIZATION_ENABLED == 1
                alloc_func_for_op<Derived>::op_size_align,
#endif
    };
}
// LCOV_EXCL_STOP

struct simop_reg_parms {
    SimpleOpFactory sim_newop;
    std::type_info const *tinf;
    Op::tensor_deserializer_register_func deserializer_reg_func;
    deserialize_op_func deserialize_func;
    cost_function_t cost_f;
    Flags_word flags;
    template <typename Derived, int N> static constexpr simop_reg_parms parms_for_simple();
};

template <typename Derived, int N> [[gnu::always_inline]] constexpr simop_reg_parms simop_reg_parms::parms_for_simple()
{
    return simop_reg_parms{Derived::create,
                           &typeid(Derived),
                           Derived::get_tensor_deserializer_register_func(),
                           alloc_func_for_op_ext<Derived>::alloc_func,
                           get_costf<Derived>(),
                           flags_for<Derived, N>()};
}
} // namespace hnnx
#endif
