//==============================================================================
//
// Copyright (c) 2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================
#ifndef PCO_DECL_H
#define PCO_DECL_H
#include "dtype_enum.h"
#include "graph_status.h"
#include <cstddef>

#ifndef THIS_PKG_NAME_STR
#ifndef THIS_PKG_NAME
#define THIS_PKG_NAME
#define THIS_PKG_NAME_STR ""
#else
#define TO_STR(x)         #x
#define TO_STR2(x)        TO_STR(x)
#define THIS_PKG_NAME_STR TO_STR2(THIS_PKG_NAME)
#endif
#endif

//
// Interface for HTP op packages. This is a reduced subset of capability compared to QNN.
//

// Optional termination function.  Perform any shutdown and return success if
// OK.  May be ommitted.
typedef GraphStatus (*PackageOpTermFn_t)();

// Interface class.  An op package is dynamically loaded, then the special
// function op_pkg_init is loaded and called.  It takes a reference argument to
// a PackageOpIf.
//
// In addition to specifying the name and optional termination function, this
// function should perform any relevant op and optimization rule registration.
// It's possible that this function may be called more than once, though we try
// to avoid it.  So, to be on the safe side, it should return immediately with
// GraphStatus::Success if it's already been called.
//
// _name must be non-null and non-empty.  It's used as a unique key into the
// registry, to avoid duplicate loading of op packages, should one be specified
// more than once in the list of options.
//
// _term may be null.

struct PackageOpIf {
    const char *_name = nullptr;
    PackageOpTermFn_t _term = nullptr;
    const char *decl_json_ptr = nullptr;
    size_t decl_json_size = 0;
};

// Entry point function for the op package.
typedef GraphStatus (*PackageOpInitFn_t)(PackageOpIf &);

#define INIT_PKG_CORE_INIT_FUNC_WITH_JSON_DECLARATION()                                                                \
    static bool sg_init = false;                                                                                       \
    extern "C" int op_pkg_init(PackageOpIf &pkg_if)                                                                    \
    {                                                                                                                  \
        pkg_if._name = THIS_PKG_NAME_STR;                                                                              \
        if (sg_init) {                                                                                                 \
            return GraphStatus::Success;                                                                               \
        }                                                                                                              \
        REGISTER_PACKAGE_OPS();                                                                                        \
        REGISTER_PACKAGE_OPTIMIZATIONS();                                                                              \
        pkg_if.decl_json_ptr = decl_json;                                                                              \
        pkg_if.decl_json_size = decl_json_size;                                                                        \
        sg_init = true;                                                                                                \
        return GraphStatus::Success;                                                                                   \
    }

#endif
