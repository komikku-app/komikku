//==============================================================================
//
// Copyright (c) 2020 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef C_TRICKS_H
#define C_TRICKS_H 1

#define CTRICKS_PASTER2(A, B) A##B
#define CTRICKS_PASTER(A, B)  CTRICKS_PASTER2(A, B)

#define STRINGIFY(x) #x
#define TOSTRING(x)  STRINGIFY(x)

#define PROBABLY(x)  __builtin_expect(!(!(x)), 1)
#define YEAHRIGHT(x) __builtin_expect(!(!(x)), 1)

#endif
