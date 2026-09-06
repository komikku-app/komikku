//==============================================================================
//
// Copyright (c) 2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================
#ifndef BUILD_OPTIONS_PUB_H
#define BUILD_OPTIONS_PUB_H 1

namespace build_options_pub {

#ifdef WITH_OPT_DEBUG
#ifndef DEFOPT_LOG
#define DEFOPT_LOG 1
#endif
#endif

#ifdef DEFOPT_LOG
constexpr bool DefOptLog = true;
#else
constexpr bool DefOptLog = false;
#endif

#ifdef DEBUG_REGISTRY
constexpr bool DebugRegistry = true;
#else
constexpr bool DebugRegistry = false;
#endif
} // namespace build_options_pub

#endif // BUILD_OPTIONS_PUB_H
