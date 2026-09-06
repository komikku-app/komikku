//==============================================================================
//
// Copyright (c) 2020 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef UNIQUE_TYPES_H
#define UNIQUE_TYPES_H 1

#if 1
// simpler way ... generates smaller code
#define DEFINE_UNIQ_TY()                                                                                               \
    namespace {                                                                                                        \
    template <int K> struct UniqTy {                                                                                   \
    };                                                                                                                 \
    } // namespace
#define UNIQUE_TYPE UniqTy<__LINE__>

#else
/*
 * EJP: FIXME maybe
 * sizeof() is unsigned, so when we subtract constants we get unsigned results.
 * This means that instead of just checking for < 0, we need to check for >= sizeof(STR)
 * Or... we can cast to signed.  That seems to work.
 */

//#define STRINDEX(NUM,STR) ((((NUM) >= 0) && ((NUM) < sizeof(STR))) ? (STR[NUM]) : 0)

#define STRINDEX(NUM, STR) ((((signed)(NUM)) >= 0) ? (STR[NUM]) : 0)
#define EXPAND_LAST_16(SIZE, STR)                                                                                      \
    STRINDEX(((SIZE)-0xF), STR), STRINDEX(((SIZE)-0xE), STR), STRINDEX(((SIZE)-0xD), STR),                             \
            STRINDEX(((SIZE)-0xC), STR), STRINDEX(((SIZE)-0xB), STR), STRINDEX(((SIZE)-0xA), STR),                     \
            STRINDEX(((SIZE)-0x9), STR), STRINDEX(((SIZE)-0x8), STR), STRINDEX(((SIZE)-0x7), STR),                     \
            STRINDEX(((SIZE)-0x6), STR), STRINDEX(((SIZE)-0x5), STR), STRINDEX(((SIZE)-0x4), STR),                     \
            STRINDEX(((SIZE)-0x3), STR), STRINDEX(((SIZE)-0x2), STR), STRINDEX(((SIZE)-0x1), STR),                     \
            STRINDEX(((SIZE)-0x0), STR)

#define EXPAND_LAST_32(SIZE, STR) EXPAND_LAST_16((SIZE - 0x10), STR), EXPAND_LAST_16((SIZE - 0x00), STR)

#if 0
/* If we need bigger file names... */
#define EXPAND_LAST_64(SIZE, STR)                                                                                      \
    EXPAND_LAST_16((SIZE - 0x30), STR), EXPAND_LAST_16((SIZE - 0x20), STR), EXPAND_LAST_16((SIZE - 0x10), STR),        \
            EXPAND_LAST_16((SIZE - 0x00), STR)
#endif

/*
 * sizeof(STR)-1 is the trailing '\0'
 * So let's start at sizeof(STR)-2.
 */
#define EXPAND_STR(STR) EXPAND_LAST_32(sizeof(STR) - 2, STR)

/*
 * FIXME maybe: we could strip out zeros.
 */

namespace hnnx {

template <int line, char... file_chars> struct Unique_Identifier {
};

} // namespace hnnx

#define UNIQUE_TYPE hnnx::Unique_Identifier<__LINE__, EXPAND_STR(__FILE__)>
#endif
#endif
