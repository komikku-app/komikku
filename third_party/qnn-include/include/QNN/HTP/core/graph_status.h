//==============================================================================
//
// Copyright (c) 2020, 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef GRAPH_STATUS_H
#define GRAPH_STATUS_H

#ifdef __cplusplus
struct GraphStatus {
#endif // __cplusplus
    enum GraphErrorCode {
        Success = 0,
        ErrorPickleSkipped = 1,
        ErrorDimensions = 2,
        ErrorPrecision = 3,
        ErrorNAN = 4,
        ErrorNoTCM = 5,
        ErrorNoSpace = 6,
        ErrorUnsupported = 7,
        ErrorSequence = 8, // e.g. adding a node after prepare
        ErrorBadID = 9, // source ref was 0 or not defined in graph; node ID was 0 or duplicate.
        ErrorBadInput = 10,
        ErrorInvalidTCM = 11,
        ErrorFatalSchdule = 12,
        ErrorFatalTCMRequest = 13,
        ErrorFatalAllocate = 14,
        ErrorFatalCheck = 15, // preprocess in prepare, e.g. clear the opid_alias_map, check connectivity, order_nodes
        ErrorBadOpName = 16,
        ErrorFatalOptimize = 17,
        ErrorFatalCSE = 18, // steps that combined with CSE e.g. dead_code_removal_and_cse, const_prop_and_cse()
        ErrorFatalInsert = 19, // when inject DMA spill/fill to fix any oversubscription of TCM
        ErrorFatalReschedule = 20,
        ErrorEmptyList = 21,
        ErrorFatalExecute = 22,
        ErrorFatalExecuteLastRun = 23,
        ErrorTCMAcquire = 24, // we can recover from TCM acquire failures (when tcm was locked by a different client)
        ErrorHMXAcquire = 25,
        ErrorHMXPower = 26,
        ErrorBadPMU = 27,
        ErrorThreadCounts = 28,
        ErrorClobberedPMU = 29, // Something clobbered our expected PMU event.
        ErrorWeightsCompressedNoAperture = 30, // Weights are DLBC compressed, but failed to acquire aperture for it
        ErrorRank = 31,
        ErrorHMXRelease = 32,
        ErrorTCMRelease = 33,
        ErrorWeightsCompressedBadFormat = 34, // Weights are DLBC compressed, but compression format is not supported
        ErrorTCMReleaseReacquire = 35,

        ErrorFatalMcMetaData = 93,
        ErrorFatalApiRecVersion = 94,
        ErrorFatalDeserialize = 95,
        ErrorFatalBlobVersion = 96,
        ErrorFatalBlobVtcmSize = 97,
        ErrorFatalUnusableGraph = 98,
        ErrorFatalException = 99,
        NotApplicable = 100, // used for internal signaling, should not be returned from API
        Yielding = 101,
        AbortSuccess = 102,
        ErrorBadDynamicOp = 103,
        ErrorNot2kAlignedVTCMReq = 104,
        ErrorFatal = -1,
    };
#ifdef __cplusplus
    GraphStatus(const GraphStatus &) = default;
    GraphStatus &operator=(const GraphStatus &) = default;
    GraphStatus(GraphErrorCode ec) : error_code(ec) {}
    explicit GraphStatus(int ec) : error_code(static_cast<GraphErrorCode>(ec)) {}
    int to_int() const { return static_cast<int>(error_code); }
    operator bool() const { return error_code != Success; }

    bool operator==(GraphErrorCode ec) const { return error_code == ec; }
    bool operator!=(GraphErrorCode ec) const { return error_code != ec; }

  private:
    GraphErrorCode error_code;
};
#endif // __cplusplus

#endif // GRAPH_STATUS
