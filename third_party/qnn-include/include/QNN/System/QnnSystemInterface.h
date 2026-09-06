//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
// All rights reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

/**
 *  @file
 *  @brief  QNN System Interface API
 *
 *          QNN System Interface is an abstraction combining all QNN System APIs.
 *          QNN System Interface provides typedef variant of QNN System APIs and
 *          API to get QNN System interface object(s).
 *          QNN System Interface API can coexist with QNN System APIs. Visibility
 *          of Interface and System APIs is determined by build configuration,
 *          specifically by QNN_SYSTEM_API and QNN_SYSTEM_INTERFACE macro definitions.
 */

#ifndef QNN_SYSTEM_INTERFACE_H
#define QNN_SYSTEM_INTERFACE_H

#include "System/QnnSystemCommon.h"

// QNN System API headers
#include "System/QnnSystemContext.h"
#include "System/QnnSystemTensor.h"
#include "System/QnnSystemLog.h"
#include "System/QnnSystemDlc.h"

#ifdef __cplusplus
extern "C" {
#endif

//=============================================================================
// Macros
//=============================================================================

// Macro controlling visibility of QNN System Interface API
#ifndef QNN_SYSTEM_INTERFACE
#define QNN_SYSTEM_INTERFACE
#endif

// Utility macros for version and name construction
#define QNN_SYSTEM_INTERFACE_VER_EVAL(major, minor)          QNN_PASTE_THREE(major, _, minor)
#define QNN_SYSTEM_INTERFACE_NAME_EVAL(prefix, body, suffix) QNN_PASTE_THREE(prefix, body, suffix)

// Construct interface type name from version, e.g. QnnSystemInterface_ImplementationV0_0_t
#define QNN_SYSTEM_INTERFACE_VER_TYPE_EVAL(ver_major, ver_minor) \
  QNN_SYSTEM_INTERFACE_NAME_EVAL(                                \
      QnnSystemInterface_ImplementationV, QNN_SYSTEM_INTERFACE_VER_EVAL(ver_major, ver_minor), _t)

// Construct interface name from version, e.g. v0_0
#define QNN_SYSTEM_INTERFACE_VER_NAME_EVAL(ver_major, ver_minor) \
  QNN_SYSTEM_INTERFACE_NAME_EVAL(v, QNN_SYSTEM_INTERFACE_VER_EVAL(ver_major, ver_minor), )

// Interface type name for current API version
#define QNN_SYSTEM_INTERFACE_VER_TYPE \
  QNN_SYSTEM_INTERFACE_VER_TYPE_EVAL(QNN_SYSTEM_API_VERSION_MAJOR, QNN_SYSTEM_API_VERSION_MINOR)

// Interface name for current API version
#define QNN_SYSTEM_INTERFACE_VER_NAME \
  QNN_SYSTEM_INTERFACE_VER_NAME_EVAL(QNN_SYSTEM_API_VERSION_MAJOR, QNN_SYSTEM_API_VERSION_MINOR)

//=============================================================================
// Data Types
//=============================================================================

/**
 * @brief QNN System Interface API result / error codes
 */
typedef enum {
  QNN_SYSTEM_INTERFACE_MIN_ERROR = QNN_MIN_ERROR_SYSTEM,
  ////////////////////////////////////////

  QNN_SYSTEM_INTERFACE_NO_ERROR                = QNN_SUCCESS,
  QNN_SYSTEM_INTERFACE_ERROR_NOT_SUPPORTED     = QNN_COMMON_ERROR_NOT_SUPPORTED,
  QNN_SYSTEM_INTERFACE_ERROR_INVALID_PARAMETER = QNN_COMMON_ERROR_INVALID_ARGUMENT,

  ////////////////////////////////////////
  QNN_SYSTEM_INTERFACE_MAX_ERROR = QNN_MAX_ERROR_SYSTEM
} QnnSystemInterface_Error_t;

//
// From QnnSystemContext.h
//

/** @brief See QnnSystemContext_create()*/
typedef Qnn_ErrorHandle_t (*QnnSystemContext_CreateFn_t)(QnnSystemContext_Handle_t* sysCtxHandle);

/** @brief See QnnSystemContext_getBinaryInfo()*/
typedef Qnn_ErrorHandle_t (*QnnSystemContext_GetBinaryInfoFn_t)(
    QnnSystemContext_Handle_t sysCtxHandle,
    void* binaryBuffer,
    uint64_t binaryBufferSize,
    const QnnSystemContext_BinaryInfo_t** binaryInfo,
    Qnn_ContextBinarySize_t* binaryInfoSize);

/** @brief See QnnSystemContext_getMetadata()*/
typedef Qnn_ErrorHandle_t (*QnnSystemContext_GetMetaDataFn_t)(
    QnnSystemContext_Handle_t sysCtxHandle,
    const void* binaryBuffer,
    uint64_t binaryBufferSize,
    const QnnSystemContext_BinaryInfo_t** binaryInfo);

/** @brief See QnnSystemContext_free()*/
typedef Qnn_ErrorHandle_t (*QnnSystemContext_FreeFn_t)(QnnSystemContext_Handle_t sysCtxHandle);

//
// From QnnSystemTensor.h
//

/** @brief See QnnSystemTensor_getMemoryFootprint()*/
typedef Qnn_ErrorHandle_t (*QnnSystemTensor_getMemoryFootprintFn_t)(Qnn_Tensor_t tensor,
                                                                    uint64_t* footprint);

//
// From QnnSystemLog.h
//

/** @brief See QnnSystemLog_create()*/
typedef Qnn_ErrorHandle_t (*QnnSystemLog_createFn_t)(QnnLog_Callback_t callback,
                                                     QnnLog_Level_t maxLogLevel,
                                                     Qnn_LogHandle_t* logger);

/** @brief See QnnSystemLog_setLogLevel()*/
typedef Qnn_ErrorHandle_t (*QnnSystemLog_setLogLevelFn_t)(Qnn_LogHandle_t logger,
                                                          QnnLog_Level_t maxLogLevel);

/** @brief See QnnSystemLog_free()*/
typedef Qnn_ErrorHandle_t (*QnnSystemLog_freeFn_t)(Qnn_LogHandle_t logger);
// clang-format off

//
// From QnnSystemDlc.h
//

/** @brief See QnnSystemDlc_createFromFile()*/
typedef Qnn_ErrorHandle_t (*QnnSystemDlc_createFromFileFn_t)(Qnn_LogHandle_t logger,
                                                             const char* dlcPath,
                                                             QnnSystemDlc_Handle_t* dlcHandle);
/** @brief See QnnSystemDlc_createFromBinary()*/
typedef Qnn_ErrorHandle_t (*QnnSystemDlc_createFromBinaryFn_t)(Qnn_LogHandle_t logger,
                                                               const uint8_t* buffer,
                                                               const Qnn_ContextBinarySize_t bufferSize,
                                                               QnnSystemDlc_Handle_t* dlcHandle);

/** @brief See QnnSystemDlc_composeGraphs()*/
typedef Qnn_ErrorHandle_t (*QnnSystemDlc_composeGraphsFn_t)(QnnSystemDlc_Handle_t dlcHandle,
                                                            const QnnSystemDlc_GraphConfigInfo_t** graphConfigs,
                                                            const uint32_t numGraphConfigs,
                                                            Qnn_BackendHandle_t backend,
                                                            Qnn_ContextHandle_t context,
                                                            QnnInterface_t interface,
                                                            QnnSystemContext_GraphInfoVersion_t graphVersion,
                                                            QnnSystemContext_GraphInfo_t** graphs,
                                                            uint32_t* numGraphs);
/** @brief See QnnSystemDlc_getOpMappings()*/
typedef Qnn_ErrorHandle_t (*QnnSystemDlc_getOpMappingsFn_t)(QnnSystemDlc_Handle_t dlcHandle,
                                                          const Qnn_OpMapping_t** opMappings,
                                                          uint32_t* numOpMappings);

/** @brief See QnnSystemDlc_free()*/
typedef Qnn_ErrorHandle_t (*QnnSystemDlc_freeFn_t)(QnnSystemDlc_Handle_t dlcHandle);

/**
 * @brief This struct defines Qnn system interface specific to version.
 *        Interface functions are allowed to be NULL if not supported/available.
 *
 */
typedef struct {
  QnnSystemContext_CreateFn_t            systemContextCreate;
  QnnSystemContext_GetBinaryInfoFn_t     systemContextGetBinaryInfo;
  QnnSystemContext_GetMetaDataFn_t       systemContextGetMetaData;
  QnnSystemContext_FreeFn_t              systemContextFree;
  QnnSystemTensor_getMemoryFootprintFn_t systemTensorGetMemoryFootprint;
  QnnSystemLog_createFn_t                systemLogCreate;
  QnnSystemLog_setLogLevelFn_t           systemLogSetLogLevel;
  QnnSystemLog_freeFn_t                  systemLogFree;
  QnnSystemDlc_createFromFileFn_t        systemDlcCreateFromFile;
  QnnSystemDlc_createFromBinaryFn_t      systemDlcCreateFromBinary;
  QnnSystemDlc_composeGraphsFn_t         systemDlcComposeGraphs;
  QnnSystemDlc_getOpMappingsFn_t         systemDlcGetOpMappings;
  QnnSystemDlc_freeFn_t                  systemDlcFree;
} QNN_SYSTEM_INTERFACE_VER_TYPE;

/// QNN_INTERFACE_VER_TYPE initializer macro
#define QNN_SYSTEM_INTERFACE_VER_TYPE_INIT { \
  NULL, /*systemContextCreate*/ \
  NULL, /*systemContextGetBinaryInfo*/ \
  NULL, /*systemContextGetMetaData*/ \
  NULL, /*systemContextFree*/ \
  NULL, /*systemTensorGetMemoryFootprint*/ \
  NULL, /*systemLogCreate*/ \
  NULL, /*systemLogSetLogLevel*/ \
  NULL, /*systemLogFree*/ \
  NULL, /*systemDlcCreateFromFile*/ \
  NULL, /*systemDlcCreateFromBinary*/ \
  NULL, /*systemDlcComposeGraphs*/ \
  NULL, /*systemDlcGetOpMappings*/ \
  NULL, /*systemDlcFree*/ \
}

typedef struct {
  /// Backend identifier. See QnnCommon.h for details.
  /// Allowed to be QNN_BACKEND_ID_NULL in case of single backend library or a dedicated system
  /// library, in which case clients can deduce backend identifier based on library being loaded.
  uint32_t backendId;
  /// Interface provider name. Allowed to be NULL.
  const char* providerName;
  // API version for provided interface
  Qnn_Version_t systemApiVersion;
  union UNNAMED {
    // Core interface type and name: e.g. QnnSystemInterface_ImplementationV0_0_t v0_0;
    QNN_SYSTEM_INTERFACE_VER_TYPE  QNN_SYSTEM_INTERFACE_VER_NAME;
  };
} QnnSystemInterface_t;

/// QnnSystemInterface_t initializer macro
#define QNN_SYSTEM_INTERFACE_INIT                                          \
  {                                                                        \
    QNN_BACKEND_ID_NULL,     /*backendId*/                                 \
    NULL,                    /*providerName*/                              \
    QNN_VERSION_INIT,        /*apiVersion*/                                \
    {                                                                      \
      QNN_SYSTEM_INTERFACE_VER_TYPE_INIT /*QNN_SYSTEM_INTERFACE_VER_NAME*/ \
    }                                                                      \
  }

// clang-format on

//=============================================================================
// Public Functions
//=============================================================================

/**
 * @brief Get list of available interface providers.
 *
 * @param[out] providerList A pointer to an array of available interface providers.
 *                          The lifetime of returned interface object pointers
 *                          corresponds to the lifetime of the provider library.
 *                          Contents are to be considered invalid if the provider
 *                          library is terminated/unloaded.
 *                          This function can be called immediately after provider
 *                          library has been loaded.
 * @param[out] numProviders Number of available interface objects in _providerList_.
 *
 * @return Error code:
 *         - QNN_SUCCESS: No error.
 *         - QNN_SYSTEM_INTERFACE_INVALID_PARAMETER: Invalid parameter was provided.
 *           Either _providerList_ or _numProviders_ was NULL.
 *         - QNN_SYSTEM_INTERFACE_ERROR_NOT_SUPPORTED: API not supported.
 */
QNN_SYSTEM_INTERFACE
Qnn_ErrorHandle_t QnnSystemInterface_getProviders(const QnnSystemInterface_t*** providerList,
                                                  uint32_t* numProviders);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // QNN_SYSTEM_INTERFACE_H