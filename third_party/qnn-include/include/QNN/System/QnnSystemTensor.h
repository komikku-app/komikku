//==============================================================================
//
//  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
//  All rights reserved.
//  Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

/**
 *  @file
 *  @brief  QNN System Tensor API.
 *
 *          This is a system API header dedicated to extensions to QnnTensor
 *          that provide backend-agnostic services to users.
 */

#ifndef QNN_SYSTEM_TENSOR_H
#define QNN_SYSTEM_TENSOR_H

#include "QnnTypes.h"
#include "System/QnnSystemCommon.h"

#ifdef __cplusplus
extern "C" {
#endif

//=============================================================================
// Error Codes
//=============================================================================

/**
 * @brief QNN System Tensor API result / error codes.
 */
typedef enum {
  QNN_SYSTEM_TENSOR_MIN_ERROR = QNN_MIN_ERROR_SYSTEM,
  //////////////////////////////////////////

  /// Qnn System Tensor success
  QNN_SYSTEM_TENSOR_NO_ERROR = QNN_SUCCESS,
  /// Qnn System Tensor API is not supported yet
  QNN_SYSTEM_TENSOR_ERROR_UNSUPPORTED_FEATURE = QNN_COMMON_ERROR_NOT_SUPPORTED,
  /// One or more arguments to a System Tensor API is/are NULL/invalid.
  QNN_SYSTEM_TENSOR_ERROR_INVALID_ARGUMENT = QNN_SYSTEM_TENSOR_MIN_ERROR + 1,
  /// A Qnn_Tensor_t data structure in invalid
  QNN_SYSTEM_TENSOR_ERROR_INVALID_TENSOR = QNN_SYSTEM_TENSOR_MIN_ERROR + 2,
  //////////////////////////////////////////
  QNN_SYSTEM_TENSOR_MAX_ERROR = QNN_MAX_ERROR_SYSTEM
} QnnSystemTensor_Error_t;

//=============================================================================
// Public Functions
//=============================================================================

/**
 * @brief A function to compute the maximum amount of memory in bytes required to contain tensor data.
 *        Currently supported data formats are:
 *        - QNN_DATA_FORMAT_DENSE
 *
 * @param[in] tensor A Qnn_Tensor_t data structure.
 *
 * @param[out] footprint The maximum amount of memory required to fully contain tensor data.
 *
 * @return Error code
 *         - QNN_SUCCESS: Successfully compute the tensor memory extent
 *         - QNN_SYSTEM_TENSOR_ERROR_INVALID_ARGUMENT: extent is NULL
 *         - QNN_SYSTEM_TENSOR_ERROR_INVALID_TENSOR: tensor is ill-configured
 *         - QNN_SYSTEM_TENSOR_ERROR_UNSUPPORTED_FEATURE: this API is not supported yet
 */
QNN_SYSTEM_API
Qnn_ErrorHandle_t QnnSystemTensor_getMemoryFootprint(Qnn_Tensor_t tensor, uint64_t* footprint);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // QNN_SYSTEM_TENSOR_H
