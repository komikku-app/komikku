//==============================================================================
//
// Copyright (c) 2018,2021 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef PERF_TIMING_H
#define PERF_TIMING_H 1

#include <stdint.h>
#include "weak_linkage.h"
#include "macros_attribute.h"

PUSH_VISIBILITY(default)

class PcyclePoint {
  public:
    API_EXPORT PcyclePoint(bool enable);
    API_EXPORT void stop();
    API_EXPORT uint64_t get_total() const { return end > start ? (end - start) : 0; }
    API_EXPORT uint64_t get_start() const { return start; }
    API_EXPORT uint64_t get_end() const { return end; }
    //private:
    uint64_t start;
    uint64_t end;
};

POP_VISIBILITY()

#endif //PERF_TIMING_H
