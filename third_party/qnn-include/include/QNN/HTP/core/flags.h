//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef HEXNN_FLAGS_H
#define HEXNN_FLAGS_H 1

#include "builtin_intrinsics.h"
#include <cstddef>

/*
 * Every flag needs a constant for what it refers to
 */
typedef unsigned long Flags_word;
enum class Flags : unsigned {
    IS_CONST = 0, // output doesn't change
    INHIBIT_CONST_PROP, // do not const-propagate this Op
    RESOURCE_HVX, // op needs an HVX thread (converted to spawn/validate)
    RESOURCE_HMX, // uses HMX
    RESOURCE_HLX, // uses HLX
    IS_DMA, // op issues dma, does not wait (converted to dma_start/dma_sync)
    FOR_HVX, // op is a spawn or validate for HVX (must combine with MOVE_*)
    FOR_HMX, // op is a spawn or validate for HMX (must combine with MOVE_*)
    FOR_HLX, // op is a spawn or validate for HLX (must combine with MOVE_*)
    FOR_DMA, // op is a dma_start or dma_sync (must combine with MOVE_*)
    MOVE_EARLY, // scheduler should move early (must combine with FOR_*)
    MOVE_LATE, // scheduler should move late (must combine with FOR_*)
    NULL_EXEC, // exec function does nothing
    IS_SPILL, // Op is a 'Spill', added during schedule/alloc
    IS_FILL, // Op is a 'Fill', added during schedule/alloc
    INPLACE_NOP, // A NULL_EXEC which is just a copy (see below).
    IS_COPY, // An op which is a copy (see below)
    IS_SYNC, // Op is a 'SyncOp'
    IS_SEND, // Op is a multi-core multicast send.
    IS_RECV, // Op is a multi-core receive.
    IS_PADZAP, // Op is a crouton padzap (same tensor type and shape in and out)
    IS_PRELOAD, // Op is a chunk preload op
    CAN_BE_SRC_DESTRUCTIVE, // Op will work correctly if input[0] and output[0] are at the same places in TCM
    IS_WEIGHT_FOR_BIT_REARRANGE, // Indicates Weight data will be used for bit rearrangement
    IS_PREDICATE_MARKER,
    XXX_LAST_FLAG
};
// NOTE: if you add new background op types (FOR_HVX, RESOURCE_HVX, etc) make sure to update
// Flags_RESOURCE_BACKGROUND, Flags_FOR_BACKGROUND in include/priv/flags_prepare.h

// INPLACE_NOP is a null_exec op that has 1 input and 1 output, with identical DType and quantization,
// shape, and tensor type; which could just be bypassed. Examples are the 'Padzap' with no work to do,
// and all ForceFormat_flat where the input is already flat (implemented by format_no_translate_flat).

// IS_COPY is a op that has 1 input and 1 output, with the same shape and tensor layout type; the memory
// class and dtype can be different (dtype must be the same #bytes); but the operation must be fulfilled
// by raw copy of the input block(s) to the output block(s). Mainly this is intended to mark const->TCM
// operations that can be replaced by 'const-fill'.

static_assert(static_cast<int>(Flags::XXX_LAST_FLAG) <= 64, "Too many flags");

/**
 * @brief Now we want to add flags to Ops (and maybe other things)
 * The default for all flags is 0.
 * Ideally, every op have one poitner/reference/function pointer/something per class (not per obj)
 * that would get the right flags.
 *
 * We could get that with a virtual function (entry in the vtable)
 * ... the default could be inherited
 * ... But how do we choose whether or not to override that function?
 * ... And how do we override the function conditionally?
 * We could get a static constexpr variable...
 * ... But how would we get at the static constexpr value from a pointer to base?
 *
 * We have get_flag_word(), virtual method of Op, which returns a Flags_word value.
 * And get_flag(Flag f), non-virtual; calls get_flag_word() and then tests the specified bit.
 * You can also call get_flag_word() once and test multiple bits using calls to test_flag_for().
 */

/*
 * We might be able to use bitset here, but bitset has limited constexpr for some reason...
 */

namespace hnnx {

template <Flags... idxs>
constexpr Flags_word flagval_generate = ((Flags_word(1) << static_cast<unsigned>(idxs)) | ... | 0);

constexpr int FLAG_FOLDING_LIMIT = 20;

template <typename T, int S = 1> inline constexpr Flags_word flags_for()
{
    return 0;
}

inline constexpr bool test_flag_for(Flags_word w, Flags which)
{
    return (safe_rshift(w, static_cast<unsigned>(which)) & 1u) != 0;
}

// LCOV_EXCL_START [SAFTYSWCCB-1542]
inline constexpr bool test_flag_and(Flags_word w, Flags which_a, Flags which_b)
{
    if ((safe_rshift(w, static_cast<unsigned>(which_a)) & 1u) == 0) return false;
    return (safe_rshift(w, static_cast<unsigned>(which_b)) & 1u) != 0;
}
//LCOV_EXCL_STOP

// LCOV_EXCL_START [SAFTYSWCCB-1736] constexprs resolved during compile time
// used in pub/impl/op_register.h for internal ops with constexpr lva
template <typename T, int S> class FlagCounter {
  public:
    constexpr static unsigned increment() { return 0U; }

    constexpr static unsigned get() { return increment() + FlagCounter<T, S - 1>::get(); }
};

template <typename T> class FlagCounter<T, -1> {
  public:
    constexpr static unsigned get() { return 0U; }
};
//LCOV_EXCL_STOP

#define DOCS_UNSET ""

// LCOV_EXCL_START [SAFTYSWCCB-1542]
template <typename T> [[maybe_unused]] static constexpr const char *docs_for()
{
    return DOCS_UNSET;
}
//LCOV_EXCL_STOP

} // namespace hnnx

/* Counts up from 0 each time a flag is added for this Type, specializing flags_for
 * with the value from the counter. To access the flag for the op, it's expected to be at
 * flags_for<Op>. The S value is any monotonically increasing value.
 */
#define FLAGS_FOR_IMPL(T, S, ...)                                                                                                                                                                                                   \
    using hnnx::FlagCounter;                                                                                                                                                                                                        \
    /* LCOV_EXCL_START [SAFTYSWCCB-1736] constexprs resolved during compile time */                                                                                                                                                 \
    template <> constexpr unsigned FlagCounter<T, S>::increment() { return 1U; }                                                                                                                                                    \
    /* LCOV_EXCL_STOP */                                                                                                                                                                                                            \
    template <> constexpr Flags_word hnnx::flags_for<T, FlagCounter<T, S>::get()>()                                                                                                                                                 \
    {                                                                                                                                                                                                                               \
        constexpr Flags_word flags = hnnx::flagval_generate<__VA_ARGS__>;                                                                                                                                                           \
        static_assert(                                                                                                                                                                                                              \
                FlagCounter<T, S>::get() <= hnnx::FLAG_FOLDING_LIMIT,                                                                                                                                                               \
                "Flag folding limit exceeded, this means you tried to register too many flags to the same type.");                                                                                                                  \
        static_assert(                                                                                                                                                                                                              \
                FlagCounter<T, S>::get() == 1 || flags == flags_for<T, FlagCounter<T, S>::get() - 1>(),                                                                                                                             \
                "Flags mismatch, this happens when different flags have been registered for the same type. Due to TCM folding, this can also happen when registering different flags for the TCM and non-TCM op implementations."); \
        return flags;                                                                                                                                                                                                               \
    }

#define FLAGS_FOR(F, ...)                   FLAGS_FOR_IMPL(F, __COUNTER__, __VA_ARGS__)
#define FLAGS_FOR_DT_NO_TCM_FOLDING(F, ...) FLAGS_FOR_IMPL(DerivedType<F>::type, __COUNTER__, __VA_ARGS__)

#if defined(PREPARE_DISABLED) && !defined(TCM_FOLDING_DISABLED)
// See register-op-tcm-folding.md
#define MOD_DER_TYPE(F, LINE) fold::ModifiedDerivedType<F, LINE>::Modified
#define FLAGS_FOR_DT_IMPL(F, UNIQUE_VAL, ...)                                                                          \
    MDT(F, UNIQUE_VAL)                                                                                                 \
    FLAGS_FOR_IMPL(MOD_DER_TYPE(F, UNIQUE_VAL), __COUNTER__, __VA_ARGS__)
#define FLAGS_FOR_DT(F, ...) FLAGS_FOR_DT_IMPL(F, __COUNTER__, __VA_ARGS__)
#else
#define FLAGS_FOR_DT(F, ...) FLAGS_FOR_IMPL(DerivedType<F>::type, __COUNTER__, __VA_ARGS__)
#endif

#define DOCS_FOR_DT(F, DOCSTRING) DOCS_FOR(DerivedType<F>::type, DOCSTRING)

#ifndef PREPARE_DISABLED
#define DOCS_FOR(F, DOCSTRING)                                                                                         \
    template <> constexpr const char *hnnx::docs_for<F>() { return DOCSTRING; }
#else
#define DOCS_FOR(F, DOCSTRING)
#endif

#endif
