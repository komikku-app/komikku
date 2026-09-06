//==============================================================================
//
// Copyright (c) 2020 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef CC_PP_H
#define CC_PP_H 1

/*
 * C++ Preprocessor Definitions
 */

#ifdef __cplusplus
#define EXTERN_C_BEGIN extern "C" {
#define EXTERN_C_END                                                                                                   \
    }                                                                                                                  \
    ;
#else
#define EXTERN_C_BEGIN /* NOTHING */
#define EXTERN_C_END   /* NOTHING */
#endif

#endif
