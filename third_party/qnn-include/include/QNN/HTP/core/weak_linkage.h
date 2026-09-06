//==============================================================================
//
// Copyright (c) 2021-2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef WEAK_LINKAGE_H
#define WEAK_LINKAGE_H 1

#include "c_tricks.h"

#if defined(IMPORT_SYMBOLS) && defined(ENABLE_WEAK_LINKAGE)
#define API_C_FUNC       extern
#define API_FUNC_NAME(N) (*N)
#else
#define API_C_FUNC
#define API_FUNC_NAME(N) N
#endif

// Macro API_FUNC_EXPORT to export symbols
#if defined(_MSC_VER)
#define API_FUNC_EXPORT __declspec(dllexport)
#else
#define API_FUNC_EXPORT __attribute__((visibility("default")))
#endif // _MSC_VER

// Macro API_EXPORT_IMPORT to export class static variables
#if defined(_MSC_VER)
#if defined(BUILD_OP_PACKAGE)
#define API_EXPORT_IMPORT __declspec(dllimport)
#else // not BUILD_OP_PACKAGE, export symbols for building library
#define API_EXPORT_IMPORT __declspec(dllexport)
#endif
#else // not define _MSC_VER
#define API_EXPORT_IMPORT __attribute__((visibility("default")))
#endif // _MSC_VER

// Macro API_FUNC_HIDDEN to hide symbols
#if defined(_MSC_VER)
#define API_FUNC_HIDDEN
#else
#define API_FUNC_HIDDEN __attribute__((visibility("hidden")))
#endif // _MSC_VER

// Add new macros to support #pragma GCC visibility push/pop
#if defined(_MSC_VER)
#define PUSH_VISIBILITY(kind)
#define POP_VISIBILITY()
#else
#define PUSH_VISIBILITY(kind) _Pragma(TOSTRING(GCC visibility push(kind)))
#define POP_VISIBILITY()      _Pragma("GCC visibility pop")
#endif

#endif // WEAK_LINKAGE_H
