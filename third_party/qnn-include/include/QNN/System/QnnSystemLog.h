//==============================================================================
//
//  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
//  All Rights Reserved.
//  Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

/**
 * @file
 * @brief   QNN System Log API component.
 *
 *          Provides means for QNN System to output logging data.
 */

#ifndef QNN_SYSTEM_LOG_H
#define QNN_SYSTEM_LOG_H

#include "QnnCommon.h"
#include "QnnLog.h"
#include "System/QnnSystemCommon.h"

#ifdef __cplusplus
extern "C" {
#endif

//=============================================================================
// Macros
//=============================================================================

//=============================================================================
// Data Types
//=============================================================================

//=============================================================================
// Public Functions
//=============================================================================
/**
 * @brief Create a handle to a logger object.
 *
 * @param[in] callback Callback to handle system library generated logging messages. NULL indicates
 *                     system library may direct log messages to the default log stream on the
 *                     target platform when possible (e.g. to logcat in case of Android).
 *
 * @param[in] maxLogLevel Maximum level of messages which the system library will generate.
 *
 * @param[out] logger The created log handle.
 *
 * @return Error code:
 *         - QNN_SUCCESS: if logging is successfully initialized.
 *         - QNN_COMMON_ERROR_NOT_SUPPORTED: logging is not supported.
 *         - QNN_LOG_ERROR_INVALID_ARGUMENT: if one or more arguments is invalid.
 *         - QNN_LOG_ERROR_MEM_ALLOC: for memory allocation errors.
 *         - QNN_LOG_ERROR_INITIALIZATION: log init failed.
 *
 * @note Use corresponding API through QnnSystemInterface_t.
 */
QNN_SYSTEM_API
Qnn_ErrorHandle_t QnnSystemLog_create(QnnLog_Callback_t callback,
                                      QnnLog_Level_t maxLogLevel,
                                      Qnn_LogHandle_t* logger);

/**
 * @brief A function to change the log level for the supplied log handle.
 *
 * @param[in] logger A log handle.
 *
 * @param[in] maxLogLevel New maximum log level.
 *
 * @return Error code:
 *         - QNN_SUCCESS: if the level is changed successfully.
 *         - QNN_LOG_ERROR_INVALID_ARGUMENT: if maxLogLevel is not a valid QnnLog_Level_t level.
 *         - QNN_LOG_ERROR_INVALID_HANDLE: _logHandle_ is not a valid handle
 *
 * @note Use corresponding API through QnnSystemInterface_t.
 */
QNN_SYSTEM_API
Qnn_ErrorHandle_t QnnSystemLog_setLogLevel(Qnn_LogHandle_t logger, QnnLog_Level_t maxLogLevel);

/**
 * @brief A function to free the memory associated with the log handle.
 *
 * @param[in] logger A log handle.
 *
 * @return Error code:
 *         - QNN_SUCCESS: indicates logging is terminated.
 *         - QNN_LOG_ERROR_MEM_ALLOC: for memory de-allocation errors.
 *         - QNN_LOG_ERROR_INVALID_HANDLE: _logHandle_ is not a valid handle
 *
 * @note Use corresponding API through QnnSystemInterface_t.
 */
QNN_SYSTEM_API
Qnn_ErrorHandle_t QnnSystemLog_free(Qnn_LogHandle_t logger);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // QNN_SYSTEM_LOG_H
