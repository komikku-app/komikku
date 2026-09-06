//==============================================================================
//
// Copyright (c) 2020-2021 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OP_REGISTER_H
#define OP_REGISTER_H 1

#include "c_tricks.h"
#include "op_registry.h"
#include "serialize_register.h"
#include "cost_funcs.h"
#include "op_info.h"
#include "op_register_types.h"
#include "op_package_name.h"
#include "template_help.h"
#include "weak_linkage.h"

#include <memory>
#include <string>
#include <utility>

namespace hnnx {
PUSH_VISIBILITY(default)

API_EXPORT OpFactory make_op_custom_internal(const std::string_view op_name_in, const std::string_view type_tag,
                                             op_reg_parms const &opreg_parms, bool is_external = false,
                                             const std::string_view file_name = "");

API_EXPORT OpFactory make_op_custom(const std::string_view op_name_in, std::string_view const type_tag,
                                    op_reg_parms const &opreg_parmsm, std::string_view const file_name = "");

POP_VISIBILITY()
template <bool IS_SIMPLE> struct item_return {
};

template <> struct item_return<false> {
    typedef op_reg_parms type;
};

template <> struct item_return<true> {
    typedef simop_reg_parms type;
};

// parms_for is wrapped in this class to avoid if constexpr implementation since
// the AUTOSAR checker doesn't evaluate if constexpr blocks properly
template <bool IS_SIMPLE> class GetParms {
  public:
    template <typename Derived, int I> constexpr static typename item_return<IS_SIMPLE>::type get();
    template <auto FP, int I> constexpr static typename item_return<IS_SIMPLE>::type get();
};

// LCOV_EXCL_START [SAFTYSWCCB-1736] constexprs resolved during compile time
// used in pub/impl/ops_opts_registration_defs.h for internal ops with constexpr lvalue
template <> class GetParms<false> {
  public:
    template <typename Derived, int I> constexpr static typename item_return<false>::type get()
    {
        return op_reg_parms::parms_for<Derived, FlagCounter<Derived, I>::get()>();
    }

    template <auto FP, int I> constexpr static typename item_return<false>::type get()
    {
        using Derived = typename DerivedType<FP>::type;
        return op_reg_parms::parms_for<Derived, FlagCounter<Derived, I>::get()>();
    }
};

template <> class GetParms<true> {
  public:
    template <typename Derived, int I> constexpr static typename item_return<true>::type get()
    {
        return simop_reg_parms::parms_for_simple<Derived, FlagCounter<Derived, I>::get()>();
    }

    template <auto FP, int I> constexpr static typename item_return<true>::type get()
    {
        using Derived = typename DerivedType<FP>::type;
        return simop_reg_parms::parms_for_simple<Derived, FlagCounter<Derived, I>::get()>();
    }
};
//LCOV_EXCL_STOP

} // namespace hnnx

/** ModifiedDerivedType is used to perform a transformation from
 * Tensor_TCM -> Tensor for different tensor types. Both FLAGS_FOR and
 * APPEND_REG_OP_ELEM use this metafunction to implement TCM folding for execute.
 * For more details, see docs/register-op-tcm-folding.md
 */
namespace fold {
template <auto, int> struct ModifiedDerivedType;
} //namespace fold
// Need the line number to avoid making the same template specialization
// multiple times
#define MDT(W, LINE)                                                                                                   \
    namespace fold {                                                                                                   \
    template <> struct ModifiedDerivedType<W, LINE> : public ModifiedDerivedTypeParent {                               \
        using Modified = typename DerivedType<W>::type;                                                                \
    };                                                                                                                 \
    } //namespace fold

/** @brief Create an Op type's type suffix from an optional name variant and argument types */
#define TYPE_SUFFIX(OP, NMVRT, ARGS)                                                                                   \
    (hnnx::ConcatStr<hnnx::ConstexprStrLen(OP), hnnx::ConstexprStrLen(NMVRT) + hnnx::ConstexprStrLen(ARGS)>(           \
            OP, (hnnx::ConcatStr<hnnx::ConstexprStrLen(NMVRT), hnnx::ConstexprStrLen(ARGS)>(NMVRT, ARGS).data())))

#ifndef OP_REG_COMPILE
#define DEF_NATIVE_OP(F, OP, LINE) DEF_NATIVE_OP_NMVRT(F, F, OP, "", LINE)

#define DEF_NATIVE_OP_NO_TCM_FOLDING(F, OP) DEF_NATIVE_OP_NMVRT_NO_TCM_FOLDING(F, F, OP, "")

#define DEF_NATIVE_OP_NMVRT(F, W, OP, NMVRT, LINE)                                                                     \
    MDT(F, LINE)                                                                                                       \
    APPEND_REG_OP_ELEM(W, THIS_PKG_NAME_STR "::" OP, TYPE_SUFFIX(OP, NMVRT, hnnx::ArgsTuples2<F>::inputTypeNames), LINE)

#define DEF_NATIVE_OP_NMVRT_NO_TCM_FOLDING(F, W, OP, NMVRT)                                                            \
    APPEND_REG_OP_ELEM_NO_TCM_FOLDING(W, THIS_PKG_NAME_STR "::" OP,                                                    \
                                      TYPE_SUFFIX(OP, NMVRT, hnnx::ArgsTuples2<F>::inputTypeNames), false)

#else
#define DEF_NATIVE_OP(F, OP, LINE)                          __reg_op__(F, OP)<<<__FILE__, __LINE__>>>
#define DEF_NATIVE_OP_NO_TCM_FOLDING(F, OP)                 __reg_op__(F, OP)<<<__FILE__, __LINE__>>>
#define DEF_NATIVE_OP_NMVRT(F, W, OP, NMVRT, LINE)          __reg_op__(F, OP)<<<__FILE__, __LINE__>>>
#define DEF_NATIVE_OP_NMVRT_NO_TCM_FOLDING(F, W, OP, NMVRT) __reg_op__(F, OP)<<<__FILE__, __LINE__>>>
#endif

// TCM folding is an optimization to reduce skel size, so we only need it for execute.
#if defined(PREPARE_DISABLED) && !defined(TCM_FOLDING_DISABLED)
#define REGISTER_OP(F, STR) DEF_NATIVE_OP(F, STR, __LINE__)
#else
#define REGISTER_OP(F, STR) DEF_NATIVE_OP_NO_TCM_FOLDING(F, STR)
#endif

// see register-op-tcm-folding.md
#define REGISTER_OP_NO_TCM_FOLDING(F, STR)    DEF_NATIVE_OP_NO_TCM_FOLDING(F, STR)
#define REGISTER_OP_WRAPPER(F, W, STR, NMVRT) DEF_NATIVE_OP_NMVRT_NO_TCM_FOLDING(F, W, STR, NMVRT)

#define REGISTER_OP_EXT(F, STR, NMVRT) REGISTER_OP_WRAPPER(F, F, STR, NMVRT)

#define REGISTER_OP_HVX_EXT(F, STR, NMVRT)                                                                             \
    FLAGS_FOR_DT_NO_TCM_FOLDING(F, Flags::RESOURCE_HVX)                                                                \
    REGISTER_OP_EXT(F, STR, NMVRT)

#define REGISTER_OP_HVX(F, STR)                                                                                        \
    FLAGS_FOR_DT(F, Flags::RESOURCE_HVX)                                                                               \
    REGISTER_OP(F, STR)

#define REGISTER_OP_HVX_NO_TCM_FOLDING(F, STR)                                                                         \
    FLAGS_FOR_DT_NO_TCM_FOLDING(F, Flags::RESOURCE_HVX)                                                                \
    REGISTER_OP_NO_TCM_FOLDING(F, STR)

#define REGISTER_OP_HVX_COPY(F, STR)                                                                                   \
    FLAGS_FOR_DT(F, Flags::RESOURCE_HVX, Flags::IS_COPY);                                                              \
    REGISTER_OP(F, STR)

#define REGISTER_OP_HVX_COPY_NO_TCM_FOLDING(F, STR)                                                                    \
    FLAGS_FOR_DT_NO_TCM_FOLDING(F, Flags::RESOURCE_HVX, Flags::IS_COPY)                                                \
    REGISTER_OP_NO_TCM_FOLDING(F, STR)

#define REGISTER_OP_HLX_EXT(F, STR, NMVRT)                                                                             \
    FLAGS_FOR_DT_NO_TCM_FOLDING(F, Flags::RESOURCE_HLX)                                                                \
    REGISTER_OP_EXT(F, STR, NMVRT)

#define REGISTER_OP_HLX(F, STR)                                                                                        \
    FLAGS_FOR_DT(F, Flags::RESOURCE_HLX)                                                                               \
    REGISTER_OP(F, STR)

#define REGISTER_OP_HLX_NO_TCM_FOLDING(F, STR)                                                                         \
    FLAGS_FOR_DT_NO_TCM_FOLDING(F, Flags::RESOURCE_HLX)                                                                \
    REGISTER_OP_NO_TCM_FOLDING(F, STR)

#define REGISTER_OP_HLX_COPY(F, STR)                                                                                   \
    FLAGS_FOR_DT(F, Flags::RESOURCE_HLX, Flags::IS_COPY);                                                              \
    REGISTER_OP(F, STR)

#define REGISTER_OP_HLX_COPY_NO_TCM_FOLDING(F, STR)                                                                    \
    FLAGS_FOR_DT_NO_TCM_FOLDING(F, Flags::RESOURCE_HLX, Flags::IS_COPY)                                                \
    REGISTER_OP_NO_TCM_FOLDING(F, STR)

#define REGISTER_OP_HMX(F, STR)                                                                                        \
    FLAGS_FOR_DT(F, Flags::RESOURCE_HMX);                                                                              \
    REGISTER_OP(F, STR)

#define REGISTER_OP_HMX_NO_TCM_FOLDING(F, STR)                                                                         \
    FLAGS_FOR_DT_NO_TCM_FOLDING(F, Flags::RESOURCE_HMX);                                                               \
    REGISTER_OP_NO_TCM_FOLDING(F, STR)

#define REGISTER_OP_HVX_SRC_DESTRUCTIVE(F, STR)                                                                        \
    FLAGS_FOR_DT(F, Flags::RESOURCE_HVX, Flags::CAN_BE_SRC_DESTRUCTIVE);                                               \
    REGISTER_OP(F, STR)

#define REGISTER_OP_HLX_SRC_DESTRUCTIVE(F, STR)                                                                        \
    FLAGS_FOR_DT(F, Flags::RESOURCE_HLX, Flags::CAN_BE_SRC_DESTRUCTIVE);                                               \
    REGISTER_OP(F, STR)

#define REGISTER_OP_HVX_SRC_DESTRUCTIVE_NO_TCM_FOLDING(F, STR)                                                         \
    FLAGS_FOR_DT_NO_TCM_FOLDING(F, Flags::RESOURCE_HVX, Flags::CAN_BE_SRC_DESTRUCTIVE);                                \
    REGISTER_OP_NO_TCM_FOLDING(F, STR)

//Register Ops which are never serialized, because they will be removed in const propagation
#define REGISTER_OP_CONST_HVX(F, STR)                                                                                  \
    FLAGS_FOR_DT(F, Flags::RESOURCE_HVX, Flags::IS_CONST);                                                             \
    REGISTER_OP(F, STR)

#define REGISTER_OP_CONST_HVX_NO_TCM_FOLDING(F, STR)                                                                   \
    FLAGS_FOR_DT_NO_TCM_FOLDING(F, Flags::RESOURCE_HVX, Flags::IS_CONST);                                              \
    REGISTER_OP_NO_TCM_FOLDING(F, STR)

#define REGISTER_OP_CONST(F, STR)                                                                                      \
    FLAGS_FOR_DT(F, Flags::IS_CONST);                                                                                  \
    REGISTER_OP(F, STR)

#endif
