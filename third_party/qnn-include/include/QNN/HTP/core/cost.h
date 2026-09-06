//==============================================================================
//
// Copyright (c) 2020 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef COST_H
#define COST_H 1

// NOTE: WHATCOST may be something like SNAIL/128
#define COST_OF(FUNC, WHATCOST)     COST_OF_OP(typename DerivedType<(FUNC)>::type, WHATCOST)
#define COST_OF_F(FUNC, WHATCOSTFN) COST_OF_OP_F(typename DerivedType<(FUNC)>::type, WHATCOSTFN)

#ifdef PREPARE_DISABLED
#define COST_OF_OP(OP, WHATCOST)
#define COST_OF_OP_F(OP, WHATCOSTFN)
#else
#define COST_OF_OP(OP, WHATCOST)                                                                                       \
    template <> [[maybe_unused]] constexpr hnnx::cost_function_t hnnx::get_costf<OP>()                                 \
    {                                                                                                                  \
        return hnnx::cost_function_t(float(StandardCosts::WHATCOST));                                                  \
    }

#define COST_OF_OP_F(OP, WHATCOSTFN)                                                                                   \
    template <>                                                                                                        \
    float hnnx::cost_function_t::cfunc<OP>(hnnx::cost_function_t const &, const Graph &graph_in, const Op *op)         \
    {                                                                                                                  \
        return WHATCOSTFN(graph_in, op);                                                                               \
    }                                                                                                                  \
    template <> [[maybe_unused]] constexpr hnnx::cost_function_t hnnx::get_costf<OP>()                                 \
    {                                                                                                                  \
        return hnnx::cost_function_t(hnnx::cost_function_t::cfunc<OP>, 1.0);                                           \
    }
#endif

#endif
