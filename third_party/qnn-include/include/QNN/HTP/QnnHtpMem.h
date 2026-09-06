//==============================================================================
//
//  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
//  All Rights Reserved.
//  Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef QNN_HTP_MEMORY_INFRASTRUCTURE_2_H
#define QNN_HTP_MEMORY_INFRASTRUCTURE_2_H

#include "QnnCommon.h"

/**
 *  @file
 *  @brief QNN HTP Memory Infrastructure component API.
 */

#ifdef __cplusplus
extern "C" {
#endif

//=============================================================================
// VTCM
//=============================================================================

// clang-format off

/**
 * @brief Raw memory address that exists ONLY on the QURT
 * side.
 */
typedef uint32_t QnnHtpMem_QurtAddress_t;

/**
 * @brief Configuration for custom shared buffer memory type
 * This shared buffer is a contiguous chunk of memory identified
 * by a single file descriptor which will be used by multiple tensors
 * based on the offset provided
 * Each QnnMem_register call with different offset will return a
 * unique memory handle
 */
typedef struct {
  // File descriptor for memory, must be set to QNN_MEM_INVALID_FD if not applicable
  int32_t fd;
  // Offset to be used in contiguous shared buffer
  uint64_t offset;
} QnnHtpMem_SharedBufferConfig_t;

// clang-format off

/**
 * @brief QNN Memory Type
 */
typedef enum {
  QNN_HTP_MEM_QURT = 0,
  QNN_HTP_MEM_SHARED_BUFFER = 1,
  QNN_HTP_MEM_WEIGHTS_BUFFER = 2,
  QNN_HTP_MEM_SHARED_SPILLFILL_BUFFER = 3,
  QNN_HTP_MEM_UNDEFINED = 0x7FFFFFFF
} QnnHtpMem_Type_t;

// clang-format off

/**
 * @brief descriptor used for the QNN API
 */
typedef struct {
  // Memory type identified by QnnHtpMem_Type_t
  QnnHtpMem_Type_t type;
  // Total size of the buffer
  // For memory type QURT, it would be size of a tensor
  // For memory type SHARED BUFFER, it would be the total size of the buffer
  uint64_t size;

  union {
    QnnHtpMem_QurtAddress_t qurtAddress;
    QnnHtpMem_SharedBufferConfig_t sharedBufferConfig;
    QnnHtpMem_SharedBufferConfig_t weightsBufferConfig;
    QnnHtpMem_SharedBufferConfig_t sharedSpillfillBufferConfig;
  };
} QnnMemHtp_Descriptor_t;

// clang-format on
#ifdef __cplusplus
}  // extern "C"
#endif

#endif
