#pragma once
//==============================================================================
// @brief Collection of types used by various external/API headers
//
// Copyright (c) 2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================
#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// We need this so, as on Windows, long is just 32-bits.  This way, Long is consistently 64-bits on
// 64-bit architectures (x86, aarch64 on Linux, Android, Windows, QNX, etc.).
typedef ptrdiff_t Long;

///
/// @brief Max number of PMU events HexNN can sample
///
#define HEXAGON_NN_MAX_PMU_EVENTS 8

///
/// @brief Type for 32b (virtual) address
///
typedef uint32_t hexagon_nn_address_t;

///
/// @brief Type for 64b (virtual) address
///
typedef uint64_t hexagon_nn_wide_address_t;

///
/// @brief A visual marker for an address whose contents (the thing this points
/// to) are immutable
/// @details For example a pointer to a shared weights table. The table has a
/// list of near/far pointers whose contents (weights) are considered immutable
///
typedef uint64_t hexagon_nn_wide_address_const_t;

///
/// @brief Type for iovec with 32b pointer/address and size
///
typedef struct {
    hexagon_nn_address_t val;
    uint32_t len;
} hexagon_nn_iovec_t;

///
/// @brief Type for iovec with 64b pointer/address and size
///
typedef struct {
    hexagon_nn_wide_address_t val;
    uint64_t len;
} hexagon_nn_wide_iovec_t;

///
/// @brief Used to specify thread types when calling hexagon_nn_set_thread_count
/// and hexagon_nn_get_thread_count.
///
enum hexagon_nn_thread_type_t {
    // Use these enums to specify the type of thread for hexagon_nn_set_thread_count.
    VecThread = 0,
    MtxThread = 1,
    EltThread = 2,
    // Use this for `count` to specify that the maximum available number of threads should be used.
    MaxOsThreads = 1001,
};

///
/// @brief Type for specifying the preemption scheme
///
typedef enum { COOP, FORCED, DEFERRED, ORDERED_COOP } hexagon_nn_preemption_style_t;

enum MemContentType {
    Standard = 0,
    Weight = 1,
    WeightDLBC = 2,
    WeightReplaceable = 3,
    ExtendedRO, ///< Content mapped to far memory with read-only permissions
    ExtendedRW ///< Content mapped to far memory with read-write permissions
};

///
/// @brief A NULL wide IO vector
///
/// @details Equivalent to nullptr for a pointer instance. Can be used as
/// default value for arguments
///
static hexagon_nn_wide_iovec_t const NULL_IOVEC = {0ull, 0ull};

#ifdef __cplusplus
}
#endif
