//==============================================================================
//
//  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
//  All rights reserved.
//  Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

/**
 *  @file
 *  @brief  QNN System Context API.
 *
 *          This is a system API header to provide
 *          Deep Learning Container (DLC) services to users.
 */

#ifndef QNN_SYSTEM_DLC_H
#define QNN_SYSTEM_DLC_H

#include "QnnInterface.h"
#include "QnnTypes.h"
#include "System/QnnSystemCommon.h"
#include "System/QnnSystemContext.h"
#include "System/QnnSystemLog.h"

#ifdef __cplusplus
extern "C" {
#endif

//=============================================================================
// Error Codes
//=============================================================================

/**
 * @brief QNN System Context API result / error codes.
 */
typedef enum {
  QNN_SYSTEM_DLC_MINERROR = QNN_MIN_ERROR_SYSTEM,
  //////////////////////////////////////////

  /// Qnn System Context success
  QNN_SYSTEM_DLC_NO_ERROR = QNN_SUCCESS,
  /// There is optional API component that is not supported yet.
  QNN_SYSTEM_DLC_ERROR_UNSUPPORTED_FEATURE = QNN_COMMON_ERROR_NOT_SUPPORTED,
  /// QNN System DLC invalid handle
  QNN_SYSTEM_DLC_ERROR_INVALID_HANDLE = QNN_SYSTEM_DLC_MINERROR + 0,
  /// One or more arguments to a System DLC API is/are NULL/invalid.
  QNN_SYSTEM_DLC_ERROR_INVALID_ARGUMENT = QNN_SYSTEM_DLC_MINERROR + 1,
  /// Generic Failure in achieving the objective of a System DLC API
  QNN_SYSTEM_DLC_ERROR_OPERATION_FAILED = QNN_SYSTEM_DLC_MINERROR + 2,


  /// Malformed DLC Binary
  QNN_SYSTEM_DLC_ERROR_MALFORMED_BINARY = QNN_SYSTEM_DLC_MINERROR + 10,
  //////////////////////////////////////////
  QNN_SYSTEM_DLC_MAXERROR = QNN_MAX_ERROR_SYSTEM
} QnnSystemDlc_Error_t;

//=============================================================================
// Data Types
//=============================================================================

/// Version of the graph config info
typedef enum {
  QNN_SYSTEM_DLC_GRAPH_CONFIG_INFO_VERSION_1 = 0x01,
  // Unused, present to ensure 32 bits.
  QNN_SYSTEM_DLC_GRAPH_CONFIG_INFO_UNDEFINED = 0x7FFFFFFF
} QnnSystemContext_GraphConfigInfoVersion_t;

typedef struct {
  const char* graphName;
  const QnnGraph_Config_t** graphConfigs;
  uint32_t numConfigs;
} QnnSystemDlc_GraphConfigInfoV1_t;

/// @brief structure to define
typedef struct {
  QnnSystemContext_GraphConfigInfoVersion_t version;
  union UNNAMED {
    QnnSystemDlc_GraphConfigInfoV1_t v1;
  };
} QnnSystemDlc_GraphConfigInfo_t;

/**
 * @brief A typedef to indicate a QNN System DLC handle
 */
typedef void* QnnSystemDlc_Handle_t;

//=============================================================================
// Public Functions
//=============================================================================

/**
 * @brief A function to create an instance of the DLC from a file
 *
 * @param[in] dlcPath path the DLC
 * @param[in] logger a log handle produced from QnnSystemLog_create(). Can be NULL
 * @param[out] dlcHandle A handle to the created instance of a systemContext entity
 *
 * @return Error code
 *         - QNN_SUCCESS: Successfully created a systemContext entity
 *         - QNN_SYSTEM_DLC_ERROR_INVALID_ARGUMENT: sysCtxHandle is NULL
 *         - QNN_COMMON_ERROR_MEM_ALLOC: Error encountered in allocating memory for
 *           systemContext instance
 *         - QNN_SYSTEM_DLC_ERROR_UNSUPPORTED_FEATURE: system context features not supported
 */
QNN_SYSTEM_API
Qnn_ErrorHandle_t QnnSystemDlc_createFromFile(Qnn_LogHandle_t logger, const char* dlcPath, QnnSystemDlc_Handle_t* dlcHandle);

/**
 * @brief A function to create an instance of the DLC from a binary buffer
 *
 * @param[in]  buffer pointer to buffer representing the DLC
 * @param[in]  logger a log handle produced from QnnSystemLog_create(). Can be NULL
 * @param[in]  bufferSize size of the binary buffer
 * @param[out] dlcHandle A handle to the created instance of a systemContext entity
 *
 * @return Error code
 *         - QNN_SUCCESS: Successfully created a systemContext entity
 *         - QNN_SYSTEM_DLC_ERROR_INVALID_ARGUMENT: sysCtxHandle is NULL
 *         - QNN_COMMON_ERROR_MEM_ALLOC: Error encountered in allocating memory for
 *           systemContext instance
 *         - QNN_SYSTEM_DLC_ERROR_UNSUPPORTED_FEATURE: system context features not supported
 */
QNN_SYSTEM_API
Qnn_ErrorHandle_t QnnSystemDlc_createFromBinary(Qnn_LogHandle_t logger, const uint8_t* buffer,
                                                const Qnn_ContextBinarySize_t bufferSize, QnnSystemDlc_Handle_t* dlcHandle);


/**
 * @brief A function to compose graphs from a DLC on a particular backend, __backend__, through
 *        an interface __interface__. Memory allocated in __graphs__ is owned by clients and may
 *        be released with calls to free().
 *
 * @param[in]  dlcHandle the DLC to retrieve graphs from
 * @param[in]  graphConfigs the graph configuration information for a particular graph
 * @param[in]  numGraphConfigs number of graph configurations
 * @param[in] backend the backend on which to compose the graphs
 * @param[in]  context the context on which to compose the graphs
 * @param[in]  interface the interface used to compose the graph.
 * @param[in]  logger a log handle produced by QnnSystemLog_create()
 * @param[in] graphVersion version of the graph info structure to be returned
 * @param[out] graphs An array of graph information representing what was created with the backend.
 * @param[out] numGraphs the number of created graphs
 *
 * @return Error code
 *         - QNN_SUCCESS: Successfully composed graphs.
 *         - QNN_SYSTEM_DLC_ERROR_INVALID_ARGUMENT: Argument is NULL
 *         - QNN_COMMON_ERROR_MEM_ALLOC: Error encountered in allocating memory for
 *         - QNN_SYSTEM_DLC_ERROR_INVALID_HANDLE: Invalid Dlc handle to free
 *         - QNN_SYSTEM_DLC_ERROR_UNSUPPORTED_FEATURE: DLC features not supported
 *
 */
QNN_SYSTEM_API
Qnn_ErrorHandle_t QnnSystemDlc_composeGraphs(QnnSystemDlc_Handle_t dlcHandle,
                                             const QnnSystemDlc_GraphConfigInfo_t** graphConfigs,
                                             const uint32_t numGraphConfigs,
                                             Qnn_BackendHandle_t backend,
                                             Qnn_ContextHandle_t context,
                                             QnnInterface_t interface,
                                             QnnSystemContext_GraphInfoVersion_t graphVersion,
                                             QnnSystemContext_GraphInfo_t** graphs,
                                             uint32_t* numGraphs);
/**
 * @brief A function to retrieve Op Mapping information from a DLC
 *
 * @param[in]  dlcHandle Handle to the DLC
 * @param[out] opMappings a list of op mappings. The memory allocated here is owned by the System
 *             library and is released when the corresponding DLC Handle is freed.
 * @param[out] numOpMappings the number of opMappings
 *
 * @return Error code
 *         - QNN_SUCCESS: Successfully freed instance of System Context
 *         - QNN_SYSTEM_DLC_ERROR_INVALID_HANDLE: Invalid Dlc handle to free
 *         - QNN_SYSTEM_DLC_ERROR_UNSUPPORTED_FEATURE: not supported
 */
QNN_SYSTEM_API
Qnn_ErrorHandle_t QnnSystemDlc_getOpMappings(QnnSystemDlc_Handle_t dlcHandle,
                                             const Qnn_OpMapping_t** opMappings,
                                             uint32_t* numOpMappings);

/**
 * @brief A function to free the instance of the System Context object.
 *        This API clears any intermediate memory allocated and associated
 *        with a valid handle.
 *
 * @param[in] sysCtxHandle Handle to the System Context object
 *
 * @return Error code
 *         - QNN_SUCCESS: Successfully freed instance of System Context
 *         - QNN_SYSTEM_DLC_ERROR_INVALID_HANDLE: Invalid System Context handle to free
 *         - QNN_SYSTEM_DLC_ERROR_UNSUPPORTED_FEATURE: not supported
 */
QNN_SYSTEM_API
Qnn_ErrorHandle_t QnnSystemDlc_free(QnnSystemDlc_Handle_t dlcHandle);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // QNN_SYSTEM_DLC_H
