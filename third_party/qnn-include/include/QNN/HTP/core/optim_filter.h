//==============================================================================
//
// Copyright (c) 2021 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OPTIM_FILTER_H
#define OPTIM_FILTER_H 1
#include <memory>

// OptimFilter is built from a string, and implements a filter
// indicating which which optimizations you want logged for debug.
// it contains a pointer to a subclass of OptimFilterImplBase;
// different subclasses could be built, depending on the string.
class Graph;
namespace hnnx {
class GraphOptInfo;
class Match;
} // namespace hnnx

namespace hnnx {

#if defined(WITH_OPT_DEBUG)

class OptimFilterImplBase {
  public:
    virtual ~OptimFilterImplBase();
    virtual bool test_optim(hnnx::GraphOptInfo const &, Match const &) const = 0;
};

class OptimFilter {
    std::unique_ptr<OptimFilterImplBase> p_impl; // null means never match
  public:
    OptimFilter(std::string const &filter_string);
    OptimFilter(GraphPrepare const &g); // delegates to the other ctor; solves a header ordering problem.
    OptimFilter(OptimFilter &&) = default;
    ~OptimFilter();
    bool test_optim(hnnx::GraphOptInfo const &gi, Match const &m) const
    {
        auto const *p = p_impl.get();
        if (p) return p->test_optim(gi, m);
        return false;
    }
};
#else

//dummy implementation

class OptimFilter { // this is an empty class when !WITH_OPT_DEBUG
  public:
    OptimFilter(std::string const &filter_string) {}
    OptimFilter(GraphPrepare const &) {}
    OptimFilter(OptimFilter &&) = default;
    bool test_optim(hnnx::GraphOptInfo const &gi, Match const &m) const { return false; }
};
#endif

} //namespace hnnx

#endif // OPTIM_FILTER_H
