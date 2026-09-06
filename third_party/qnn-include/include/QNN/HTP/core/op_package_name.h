//==============================================================================
//
// Copyright (c) 2020-2021, 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OP_PACKAGE_NAME_H
#define OP_PACKAGE_NAME_H

#ifndef THIS_PKG_NAME
#define THIS_PKG_NAME
#define THIS_PKG_NAME_STR ""
#else
#define TO_STR(x)         #x
#define TO_STR2(x)        TO_STR(x)
#define THIS_PKG_NAME_STR TO_STR2(THIS_PKG_NAME)
#endif

#include <cstring>
#include "weak_linkage.h"

namespace hnnx {

inline char const *get_opname_with_pkg_prefix(std::string &tmp, char const *opstr,
                                              char const *prefix = THIS_PKG_NAME_STR)
{
    if (!opstr || opstr[0] == '$' || strstr(opstr, "::") != nullptr) return opstr;
    // build result in 'tmp' and return pointer to it
    tmp = prefix;
    tmp += "::";
    tmp += opstr;
    return tmp.c_str();
}

// LCOV_EXCL_START [SAFTYSWCCB-1542]
inline bool opname_has_pkg_prefix(char const *opstr)
{
    return strstr(opstr, "::") != nullptr;
}
// LCOV_EXCL_STOP

API_C_FUNC std::string API_FUNC_NAME(get_default_pkg_name)();

} // namespace hnnx

#endif
