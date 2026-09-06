//==============================================================================
//
// Copyright (c) 2020-2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OPTIMIZE_H
#define OPTIMIZE_H 1

/*
 * PLEASE LEAVE graph.h OUT OF THIS FILE
 */

#include "c_tricks.h"
#include "op_def.h"
#include "unique_types.h"

#include <array>
#include <cassert>
#include <functional>
#include <limits>
#include <map>
#include <string>
#include <tuple>
#include <utility>
#include <vector>
#include <iso646.h>
#include "optimize_defs.h"
#include "optimize_flags.h"
#include "optim_filter.h"
#include "match_op.h"
#include "oexpr.h"
#include "build_options_pub.h"
#include "op_package_name.h"
#include "tensor_info.h"
#include "macros_attribute.h"
#include "weak_linkage.h"

#ifndef PREPARE_DISABLED
/*
 * We want Match, Replacement, and Constraint to have mostly their own namespace,
 * so that things like "OP" can mean different things in different places.
 *
 * However, we want to be able to share things like the context
 * We could do this with extra state in each owned class, but that seems wasteful.
 *
 * Instead, we use private class members to give things unique namespaces, but
 * inherit to concatenate classes and values that should be shared.
 *
 *
 * A note about a trick:
 *  Each class (Match, Constraint, Replacement) has a templated function for
 *  UniqueType that's unused.  That lets us createa arbitrary member functions
 *  later.
 *
 *  If you're curious, the unique type comes from the current filename and line.
 *
 *  There's also a member function pointer that is used when creating instances
 *  of Match/Replacement/Constraint that we initialize to the arbitrary member
 *  functions that we're creating.
 *
 */

class Replacement;

using ReplFunc = OptFunction<OpRef(Replacement &, OpDef const &)>;

namespace hnnx {

class Match;

typedef std::function<bool(Match &, OpDef const &)> MatchFunc;

template <oExp::OpVnt V, typename T> ReplFunc wrap_as_replfunc(oExp::opexpr<V, T> &&opr)
{
    return ReplFunc::create([op{std::move(opr)}](Replacement &rpx, OpDef const &) -> OpRef { return op.eval(rpx); });
}
template <oExp::OpVnt V, typename T> ReplFunc wrap_as_replfunc(oExp::opexpr<V, T> const &op)
{
    return ReplFunc::create([op](Replacement &rpx, OpDef const &) -> OpRef { return op.eval(rpx); });
}
inline ReplFunc wrap_as_replfunc(ReplFunc &&rep)
{
    return std::move(rep);
}
inline ReplFunc wrap_as_replfunc(ReplFunc const &rep)
{
    return rep;
}

} // namespace hnnx

#include "weak_linkage.h"
PUSH_VISIBILITY(default)
namespace gxE {
class API_EXPORT GXEngine;
}
POP_VISIBILITY()

// these are function objects which return the various types,
// and are called with a const reference to 'Constraint'
//
// some of them will cheat and used a static-cast to look at the Split_Context;
// maybe it should be moved to Constraint class. Also, those that implement MESSAGE
// etc will cheat and call a non-const method of the Constraint class.
//

typedef oExp::sFunction<int> ReplFuncInt;
typedef oExp::sFunction<bool> ReplFuncBool;
typedef oExp::sFunction<DType> ReplFuncDType;
typedef oExp::sFunction<float> ReplFuncFloat;

typedef OpRef (*external_replace_funcp)(Replacement &, OpDef const &);

namespace hnnx {

PUSH_VISIBILITY(default)

// EJP: FIXME: Instead of separate optim_config things that require several changes in several places,
// we need to plumb through a way to get an option out of graph.options that comes from options.def

// the optim_config struct is visible within the namespace of
// a DEFOPT as 'Config', e.g. Config.tcm_size reads the tcm size.
//
// The actual values are kept in struct optim_config_values, which is instantiated
// within the optimization object.
//
// The struct is actually a static variable which contains instances of optim_configvar;
// each one contains a field pointer into optim_config_values. When these appear
// in an expression, they are converted to an oExp<config,T> containig a copy
// of the struct offset; i.e. the oExp can be built without an instance of optim_config_values existing.
//

struct optim_config_values {
    // values which are not directly available from 'Options'
    size_t tcm_size; // the current tcm_size
    size_t tcm_size_for_tiling; // tcm size to be used for tiling
};

// wrapper functions for graph access
API_EXPORT OpRef graph_gen_Const_int32_common_wrapper(GraphPrepare &graph_in, const OpDef &old,
                                                      const OutputDef &out_def, const uint8_t *data, size_t data_len);

template <DType DT>
API_EXPORT OpRef graph_gen_Const_scalar_wrapper(GraphPrepare &graph_in, const OpDef &old,
                                                typename dtype_traits<DT>::element_type constval);

// these are written as specializations.
template <>
API_EXPORT OpRef graph_gen_Const_scalar_wrapper<DType::Int32>(GraphPrepare &graph_in, const OpDef &old,
                                                              NN_INT32_T constval);
template <>
API_EXPORT OpRef graph_gen_Const_scalar_wrapper<DType::Float32>(GraphPrepare &graph_in, const OpDef &old,
                                                                float constval);
POP_VISIBILITY()

/* EJP: FIXME: A lot of stuff has accumulated here... const generation, helper functions, etc... */

/*
 * EJP: FIXME: see if we can change some of these functions to just return OpRef instead of
 * having to return a funcgtion<OpRef(OpDef &)> and all the lambda stuff
 */

// Need separate function because underlying std::map of opdef_map_t is protected
template <typename opdef_map_t, typename OpId_t> inline bool exists(opdef_map_t const &m, const OpId_t &test)
{
    return m.find(test) != m.end();
}

template <template <typename, typename> class C, typename K, typename V>
inline bool exists(C<K, V> const &m, const K &test)
{
    return m.find(test) != m.end();
}

template <template <typename, typename, typename, typename> class C, typename K, typename V, typename V1, typename V2>
inline bool exists(C<K, V, V1, V2> const &m, const K &test)
{
    return m.find(test) != m.end();
}

/*
 * EJP: FIXME: this stuff here at a global level should move somewhere.
 * Maybe even outline the functions...
 */

namespace opt_util {
// map_to_size_t(x)
// maps integer types to size_t;
// passes ReplFuncInt as-is
// This is used to minimize the number of distinct specializations
// of gen_Shape (each having its own lambda).
template <typename T> struct map_to_sizet_helper {
    static_assert(std::numeric_limits<T>::is_integer);
    static inline constexpr size_t convert(T x) { return x; }
};
template <oExp::Variant V, typename T> struct map_to_sizet_helper<oExp::expr<V, T>> {
    static inline ReplFuncInt convert(oExp::expr<V, T> &&x) { return oExp::wrap_as_function<int>(std::move(x)); }
};

template <typename T> inline auto map_to_size_t(T &&x)
{
    return map_to_sizet_helper<T>::convert(std::forward<T>(x));
}

inline size_t eval_size(oExp::ECtx &, size_t size)
{
    return (size_t)size;
}
inline size_t eval_size(oExp::ECtx &e, ReplFuncInt const &f)
{
    return (size_t)(f(e));
}

template <typename... Ts> inline ReplFunc gen_Shape_inner(Ts... sizes)
{
    return ReplFunc::create([=](Replacement &rpx, const OpDef &old) -> OpRef {
        OutputDef out_def = {
                sizeof...(Ts), //rank
                DType::Int32, //dtype
                {eval_size(rpx, sizes)...}, //max_sizes
                0, //zero_offset
                0, //stepsize
        };
        auto &g = old.graph();
        auto newref = graph_gen_Const_int32_common_wrapper(g, old, out_def, NULL, 0);
#if 0
        debuglog("Const shape %llx: rank=%zd (%zd,%zd,%zd,%zd...)",
                 newref.input_id, out_def.rank, out_def.max_sizes[0],
                 out_def.max_sizes[1], out_def.max_sizes[2],
                 out_def.max_sizes[3]);
#endif
        return newref;
    });
}

} // namespace opt_util

} // namespace hnnx

// This gen_Shape is intended for use in Replacement rules; the parameters
// are either integer constants or ReplFuncInt 's
// It returns a function.

/// \ingroup OptReplacement
/// @brief gen_Shape(..dims..) - construct an OpDef_Shape of the given dimensions.
///
/// The dimension parameters can be integers, but may also be one of
/// SPLIT_START("tag"), SPLIT_SIZE("tag"), SPLIT_DIM("tag"),  provided the expression
/// appears inside the operand of an AUTOSPLIT which uses the same tag
///
template <typename... Ts> inline ReplFunc gen_Shape(Ts... sizes)
{
    return hnnx::opt_util::gen_Shape_inner(hnnx::opt_util::map_to_size_t(std::move(sizes))...);
}

PUSH_VISIBILITY(default)

//
// 'QuickShape' can be returned from a SHAPEFN_APPLY function; returning
// a QuickShape is equivalent to returning a gen_Shape() with the same dimensions.
//
struct QuickShape {
    struct empty_rank {
        unsigned r;
    };

    static constexpr unsigned maxdims = 8;
    unsigned rank;
    size_t dims[maxdims];
    // make with specific rank and dimensions, up to 4
    explicit inline constexpr QuickShape(size_t d) : rank(1), dims{d} {}
    inline constexpr QuickShape(size_t d0, size_t d1) : rank(2), dims{d0, d1} {}
    inline constexpr QuickShape(size_t d0, size_t d1, size_t d2) : rank(3), dims{d0, d1, d2} {}
    inline constexpr QuickShape(size_t d0, size_t d1, size_t d2, size_t d3) : rank(4), dims{d0, d1, d2, d3} {}
    inline constexpr QuickShape(size_t d0, size_t d1, size_t d2, size_t d3, size_t d4)
        : rank(5), dims{d0, d1, d2, d3, d4}
    {
    }

    // build from an OutputDef's shape info
    QuickShape(OutputDef const &odef)
    {
        int const r = std::min((unsigned)odef.rank, maxdims);
        rank = r;
        for (int i = 0; i < r; i++) {
            dims[i] = odef.max_sizes[i];
        }
    }
    // take rank from first argument but fill with specified value
    QuickShape(OutputDef shape, size_t fill)
    {
        rank = shape.rank;
        for (int i = 0; i < rank; i++) {
            dims[i] = fill;
        }
    }

    // set an output def based on QuickShape. Only useful in implementing modifiers.
    API_EXPORT void to_outdef(OutputDef &odef) noexcept;
    explicit inline constexpr QuickShape(empty_rank const &erank) : rank(std::min((unsigned)erank.r, maxdims)), dims()
    {
    }
    // build with a given rank, and all zero dims
    static inline constexpr QuickShape make_empty(unsigned r) { return QuickShape(empty_rank{r}); }
    // shortcut for make_empty( odef.rank)
    API_EXPORT static inline QuickShape make_empty(OutputDef const &odef)
    {
        return QuickShape(empty_rank{unsigned(odef.rank)});
    }

    size_t &operator[](unsigned dim)
    {
        assert(dim < rank);
        return dims[dim];
    }
};

// This is an 'immediate' gen_Shape. Shape can be given as vararg ints,
// or as std::vector<size_t>.
//

template <typename... Ts> API_EXPORT OpRef gen_Shape_immed(const OpDef &some_op, Ts... sizes);
API_EXPORT OpRef gen_Shape_immed(const OpDef &some_op, std::vector<size_t> const &shape);

POP_VISIBILITY()

// this is intended to be used with an explicit <DType::Float32> or whatever.
// (gen_Const_scalar must be specialized for the supported types).
template <DType DT> inline OpRef gen_ConstScalar_imm(const OpDef &old, typename dtype_traits<DT>::element_type constval)
{
    auto &g = old.graph();
    return hnnx::graph_gen_Const_scalar_wrapper<DT>(g, old, constval);
}
// these are intended to be used in replacement rules, they return ReplFunc.

PUSH_VISIBILITY(default)
/// \ingroup OptReplacement
/// @brief gen_ConstScalar_f32(floatval) - Make an Opdef_Const with given scalar float value
API_EXPORT ReplFunc gen_ConstScalar_f32(float constval);
API_EXPORT ReplFunc gen_ConstScalar_f32_func(ReplFuncFloat &&constval_f);
POP_VISIBILITY()

template <typename T> inline ReplFunc gen_ConstScalar_f32(T &&expr)
{
    return gen_ConstScalar_f32_func(oExp::wrap_as_function<float>(std::forward<T>(expr)));
}

PUSH_VISIBILITY(default)
/// \ingroup OptReplacement
/// @brief gen_ConstScalar_i32(intval) - Make an Opdef_Const with given scalar float value
API_EXPORT ReplFunc gen_ConstScalar_i32(int constval);
API_EXPORT ReplFunc gen_ConstScalar_i32_func(ReplFuncInt &&constval_f);
POP_VISIBILITY()

template <typename T> inline ReplFunc gen_ConstScalar_i32(T &&expr)
{
    return gen_ConstScalar_i32_func(oExp::wrap_as_function<int>(std::forward<T>(expr)));
}

PUSH_VISIBILITY(default)
API_EXPORT ReplFunc gen_ConstArr_f32(float constval, size_t n);
API_EXPORT ReplFunc gen_ConstArr_f32_func(ReplFuncFloat &&val_func, ReplFuncInt &&n_func);
POP_VISIBILITY()

template <typename TVAL, typename TN> inline ReplFunc gen_ConstArr_f32(TVAL &&val, TN &&nn)
{
    return gen_ConstArr_f32_func(oExp::wrap_as_function<float>(std::forward<TVAL>(val)),
                                 oExp::wrap_as_function<int>(std::forward<TN>(nn)));
}

PUSH_VISIBILITY(default)
API_EXPORT ReplFunc gen_ConstArr_i32(NN_INT32_T constval, size_t n);
API_EXPORT ReplFunc gen_ConstArr_i32_func(ReplFuncInt &&val_func, ReplFuncInt &&n_func);
POP_VISIBILITY()

template <typename TVAL, typename TN> inline ReplFunc gen_ConstArr_i32(TVAL &&val, TN &&nn)
{
    return gen_ConstArr_i32_func(oExp::wrap_as_function<int>(std::forward<TVAL>(val)),
                                 oExp::wrap_as_function<int>(std::forward<TN>(nn)));
}

PUSH_VISIBILITY(default)
//
// gen_ConstArr_vals_i32( ... ) allows creation of a an int32 const array, shape [1,1,1,n],
// with the given set of values in it.

// this implementation only used when all the values are constants
API_EXPORT ReplFunc gen_ConstMat_i32__func(std::vector<NN_INT32_T> &&);
// this one is passed a std::vector of ReplFuncInt
API_EXPORT ReplFunc gen_ConstMat_i32__func(std::vector<ReplFuncInt> &&);
POP_VISIBILITY()

namespace hnnx {

// all_are_int<T,T,T,...>()  returns true if all of T,T .. are int,long or unsigned.
// or reference to.
//
template <typename... Ts> struct all_are_int_helper {
    static_assert(sizeof...(Ts) == 0, "template problem");
    static constexpr bool value = true;
};
template <typename T1, typename... Ts> struct all_are_int_helper<T1, Ts...> {
    using TX = std::remove_reference_t<T1>;
    static constexpr bool value = (std::is_same_v<TX, int> || std::is_same_v<TX, long> ||
                                   std::is_same_v<TX, unsigned>)&&all_are_int_helper<Ts...>::value;
};

template <typename... Ts> inline constexpr bool all_are_int()
{
    return all_are_int_helper<Ts...>::value;
}

} // namespace hnnx

//
// gen_ConstMat_i32( wid, ... wid*dep values ... ) -> [1,1,wid,dep] filled
// in with the values. 'wid' can zero, which is treated as 1.

template <typename TW, typename... Ts> inline ReplFunc gen_ConstMat_i32(TW &&wid, Ts &&...values)
{
    if constexpr (hnnx::all_are_int<TW, Ts...>()) {
        std::vector<NN_INT32_T> parms = {NN_INT32_T(wid), NN_INT32_T(values)...};
        return gen_ConstMat_i32__func(std::move(parms));
    } else {
        std::vector<ReplFuncInt> parms = {oExp::wrap_as_function<int>(std::forward<TW>(wid)),
                                          oExp::wrap_as_function<int>(std::forward<Ts>(values))...};
        return gen_ConstMat_i32__func(std::move(parms));
    }
}
// gen_ConstArr_vals_i32 is just a special case of gen_ConstMat_i32

template <typename... Ts> inline ReplFunc gen_ConstArr_vals_i32(Ts &&...values)
{
    return gen_ConstMat_i32(0, std::forward<Ts>(values)...);
}

struct Split_Context {
    int start;
    int size;
    int dim;
};

PUSH_VISIBILITY(default)

/**
 * \defgroup AutoSplitShapeFnApply  Functions for AUTOSPLIT_SHAPEFN_APPLY
 * \ingroup OptReplacement
 *
 * These are functions which may be used with SHAPEFN_APPLY.
 *
 * The first parameter is always Replacement &; the second is a Split_Context const & (obtained via the 'split_tag' parmeter
 * to the AUTOSPLIT_SHAPEFN_APPLY' and the remaining parameters are obtained from the AUTOSPLIT_SHAPEFN_APPLY, and may be
 * OpRef (mapped from "OperandTag" in the SHAPEFN_APPLY), or scalar values.
 *
 * The return value may be an OpRef representing a new graph object; instead, the function may return a QuickShape object
 * representing a shape, and the framework will convert this to an OpDef_Shape.
 *
 *
 * @{
 */
// :::EXTERNAL_SHAPEFN::: {  qshape simpledim_split_start(split,op,int); }

/// @brief make 'start' shape for 'simple' split (on specific dimension)
///
/// E.g. if dim= 2, and the SPLIT_START is 96, a shape { 0, 0, 96, 0} will be generated.
///
/// This is used within CHANGEDIM_SLICE
///
API_EXPORT QuickShape simpledim_split_start(Replacement &rpx, Split_Context const &splitinfo, OpRef const &orig,
                                            int dim);

// :::EXTERNAL_SHAPEFN::: {  qshape simpledim_split_size(split,op,int); }

/// @brief make 'size' shape for 'simple' split (on specific dimension)
///
/// E.g. if tdim=2, and the SPLIT_START is 30, a shape { b, h, 30, d} will be generated
/// (where b,h,d are the 'default' dims)
///
/// This is used within CHANGEDIM_SLICE
///
API_EXPORT QuickShape simpledim_split_size(Replacement &rpx, Split_Context const &splitinfo, OpRef const &orig,
                                           int dim);

// :::EXTERNAL_SHAPEFN::: {  qshape simple_split_start(split,op); }

/// @brief make 'start' shape for 'simple' split
///
/// E.g. if SPLIT_DIM=3, and the SPLIT_START is 96, a shape { 0, 0, 0, 96} will be generated.
///
/// This is used within TYPICAL_SLICE
///
API_EXPORT QuickShape simple_split_start(Replacement &rpx, Split_Context const &splitinfo, OpRef const &orig);

// :::EXTERNAL_SHAPEFN::: {  qshape simple_split_size(split,op); }

/// @brief make 'size' shape for 'simple' split
///
/// E.g. if SPLIT_DIM=3, and the SPLIT_SIZE is 30, a shape { b, h, w, 30} will be generated.
/// (where b,h,w are the 'default' dims)
///
/// This is used within TYPICAL_SLICE
///
API_EXPORT QuickShape simple_split_size(Replacement &rpx, Split_Context const &splitinfo, OpRef const &orig);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_valid_split_start(split,op,op); }

/// @brief make 'start' shape for splitting input to 'valid' convolution, where the input is being split along height or width
///
/// Generates shape {0, SPLIT_START * stride_h, 0, 0 }
/// or
/// Generates shape {0, 0, SPLIT_START * stride_w, 0 }
API_EXPORT QuickShape conv_valid_split_start(Replacement &rpx, Split_Context const &splitinfo, OpRef const &Act,
                                             OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_valid_split_size(split,op,op,int,int); }

/// @brief make 'size' shape for splitting input to 'valid' dilated convolution, where the input is being split along height or width
///
/// Generates shape {0, inrows, 0, 0} or {0, 0, incols, 0}
///
/// where inrows = stride_h * (SPLIT_SIZE-1) + (filter_h - 1) * dilation + 1
///       incols = stride_w * (SPLIT_SIZE-1) + (filter_w - 1) * dilation + 1
///
API_EXPORT QuickShape conv_valid_split_size(Replacement &rpx, Split_Context const &splitinfo, OpRef const &Act,
                                            OpRef const &stride, int window, int dilation);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_split_start_valid(split,op,op,op); }

/// @brief make 'start' shape for splitting input to 'valid' convolution, where the input is being split along height
///
/// Generates shape {0, SPLIT_START * stride_h, 0, 0 }

API_EXPORT QuickShape conv_split_start_valid(Replacement &rpx, Split_Context const &splitinfo, OpRef const &Act,
                                             OpRef const &weights, OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_split_size_valid(split,op,op,op); }

/// @brief make 'size' shape for splitting input to 'valid' convolution, where the input is being split along height
///
/// Generates shape {0, inrows, 0, 0 }
///
/// where inrows = stride_h * (SPLIT_SIZE-1) + filter_h
///
API_EXPORT QuickShape conv_split_size_valid(Replacement &rpx, Split_Context const &splitinfo, OpRef const &Act,
                                            OpRef const &weights, OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_split_size_valid_dil(split,op,op,op,op); }

/// @brief make 'size' shape for splitting input to 'valid' dilated convolution, where the input is being split along height
///
/// Generates shape {0, inrows, 0, 0 }
///
/// where inrows = stride_h * (SPLIT_SIZE-1) + (filter_h - 1) * dilation + 1
///
API_EXPORT QuickShape conv_split_size_valid_dil(Replacement &rpx, Split_Context const &splitinfo, OpRef const &Act,
                                                OpRef const &weights, OpRef const &stride, OpRef const &dilation);

// :::EXTERNAL_SHAPEFN::: {  qshape pool_split_start_valid(split,op,op,op); }

/// @brief make 'start' shape for splitting input to 'valid' Xpool, where the input is being split along height
///
/// Generates shape {0, SPLIT_START * stride_h, 0, 0 }
API_EXPORT QuickShape pool_split_start_valid(Replacement &rpx, Split_Context const &splitinfo, OpRef const &Act,
                                             OpRef const &window, OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape pool_split_size_valid(split,op,op,op); }

/// @brief make 'size' shape for splitting input to 'valid' Xpool, where the input is being split along height
///
/// Generates shape {0, inrows, 0, 0 }
///
/// where inrows = stride_h * (SPLIT_SIZE-1) + window_h
///
API_EXPORT QuickShape pool_split_size_valid(Replacement &rpx, Split_Context const &splitinfo, OpRef const &Act,
                                            OpRef const &window, OpRef const &stride);

/** @} */

namespace optim_extfunc { // in concat_opt.cc
API_EXPORT QuickShape offset_into_concat(Replacement &rpx, Split_Context const &splitinfo, OpRef const &concat,
                                         OpRef const &base_shape);
}

/**
 * \defgroup ShapeFnApply  Functions for SHAPEFN_APPLY
 * \ingroup OptReplacement
 *
 * These are functions which may be used with SHAPEFN_APPLY.
 *
 * The first parameter is always Replacement &; the remaining parameters are obtained from the SHAPEFN_APPLY, and may be
 * OpRef (mapped from "OperandTag" in the SHAPEFN_APPLY), or scalar values.
 *
 * The return value may be an OpRef representing a new graph object; instead, the function may return a QuickShape object
 * representing a shape, and the framework will convert this to an OpDef_Shape.
 *
 * @{
 */

// :::EXTERNAL_SHAPEFN::: {  qshape split_merge_start(op,op); }

API_EXPORT QuickShape split_merge_start(Replacement &rpx, OpRef const &inner, OpRef const &outer);

//@brief Create shape with extra amount added along some axis
// :::EXTERNAL_SHAPEFN::: { qshape shape_add_on_axis(op,op,int); }
API_EXPORT QuickShape shape_add_on_axis(Replacement &rpx, OpRef const &start, OpRef const &amt, int axis);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_same_padded_size(op,op,op); }
///@brief find padded shape for input to 'same' convolution
///
/// For a 'same' convolution, produce a shape the same as 'Act', but expanded in H and W dimensions to allow for the
/// padding needed (as determined by the given filter shape and stride)
///
API_EXPORT QuickShape conv_same_padded_size(Replacement &rpx, OpRef const &Act, OpRef const &weights,
                                            OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_same_padded_size(op,op,op,op); }
///@brief same as \conv_same_padded_size that support dilation, default should be {1,1}
API_EXPORT QuickShape conv_same_padded_size_dilation(Replacement &rpx, OpRef const &Act, OpRef const &weights,
                                                     OpRef const &stride, OpRef const &dilation);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_same_before(op,op,op); }

///@brief find padded offset (top/left margin) for input to 'same' convolution
///
/// For a 'same' convolution, produce a shape which indicates how the input needs to be padded on top and left to
/// be processed as 'valid' - as determined by the given filter shape and stride. The resulting shape will be
///
///   { 0, top_padding,  left_padding, 0 }
///
API_EXPORT QuickShape conv_same_before(Replacement &rpx, OpRef const &Act, OpRef const &weights, OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_same_before(op,op,op,op); }

///@brief same as \conv_same_before that support dilation, default should be {1,1}
API_EXPORT QuickShape conv_same_before_dilation(Replacement &rpx, OpRef const &Act, OpRef const &weights,
                                                OpRef const &stride, OpRef const &dilation);

// :::EXTERNAL_SHAPEFN::: {  qshape pool_same_padded_size(op,op,op); }

///@brief find padded shape for input to 'same' Xpool
///
/// For a 'same' Xpool, produce a shape the same as 'Act', but expanded in H and W dimensions to allow for the
/// padding needed (as determined by the given window shape and stride)
///
API_EXPORT QuickShape pool_same_padded_size(Replacement &rpx, OpRef const &Act, OpRef const &window,
                                            OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape pool_same_before(op,op,op); }

///@brief find padded offset (top/left margin) for input to 'same' Xpool
///
/// For a 'same' Xpool, produce a shape which indicates how the input needs to be padded on top and left to
/// be processed as 'valid' - as determined by the given window shape and stride. The resulting shape will be
///
///   { 0, top_padding,  left_padding, 0 }
///
API_EXPORT QuickShape pool_same_before(Replacement &rpx, OpRef const &Act, OpRef const &window, OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape conv_s2d_shape(op,op,op); }

/// @brief
///
/// Compute the out shape of a conv whose input has gone through a space to depth transformation
/// Effective input shape is roundup(input_shape, stride) / stride
/// Effective out shape is (eff in - filter + 1) (note that stride is changed to 1 after s2d)
/// Does not handle dilation (this is handled earlier on in the def opt path)
///
API_EXPORT QuickShape conv_s2d_shape(Replacement &rpx, OpRef const &Act, OpRef const &filter, OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape pad_total_for_qnn(op,op); }

///@brief use input pad_amount to calculate padded offset for input from 'QNN_Conv' to 'valid' convolution
///
/// For a 'QNN_Conv' convolution, produce a shape the same as 'Act', but expanded in H and W dimensions, which
/// is determined by the input pad_amount: [[h_before, h_after], [w_before, w_after]]
///
API_EXPORT QuickShape pad_total_for_qnn(Replacement &rpx, OpRef const &Act, OpRef const &pad_amount);

///@brief use input pad_amount to calculate padded offset for input to use 'valid' pooling
///
/// For QNN pool ops, produce a shape the same as 'Act', but expanded in H and W dimensions, which
/// is determined by the input pad_amount: [[h_before, h_after], [w_before, w_after]]
///
API_EXPORT QuickShape pad_total_for_qnn_round(Replacement &rpx, OpRef const &Act, OpRef const &Stride,
                                              OpRef const &pad_amount, OpRef const &rounding_mode);

// :::EXTERNAL_SHAPEFN::: {  qshape pad_before_for_qnn(op,op); }

///@brief use input pad_amount to get the (top/left margin) for input from 'QNN_Conv' to 'valid' convolution
///
/// For a 'QNN_Conv' convolution, produce the result shape of padded shape to
/// be processed as 'valid' - as determined by the given pad_amount. The resulting shape will be
///
///   { 0, top_padding,  left_padding, 0 }
///
API_EXPORT QuickShape pad_before_for_qnn(Replacement &rpx, OpRef const &Act, OpRef const &pad_amount);

// :::EXTERNAL_SHAPEFN::: {  qshape explicit_pad_for_qnn(op,op); }

API_EXPORT OpRef explicit_pad_for_qnn(Replacement &rpx, OpRef const &output, OpRef const &pad_amount);

// :::EXTERNAL_SHAPEFN::: {  qshape reshape_hw_to_4d(op); }

///@brief given a tensor representing [h, w] expand to [1, h, w, 1]
API_EXPORT QuickShape reshape_hw_to_4d(Replacement &rpx, OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape reshape_bhw_to_4d(op); }

///@brief given a tensor representing [b, h, w] expand to [b, h, w, 1]
API_EXPORT QuickShape reshape_bhw_to_4d(Replacement &rpx, OpRef const &stride);

// :::EXTERNAL_SHAPEFN::: {  qshape shape_after_transpose(op,op); }

///@brief gives the new shape of a tensor after a transpose has been applied to it
API_EXPORT QuickShape shape_after_transpose(Replacement &rpx, OpRef const &input, OpRef const &tx_control);

// :::EXTERNAL_SHAPEFN::: {  qshape shape_after_spaceToBatch(op,op); }
// :::EXTERNAL_SHAPEFN::: {  qshape shape_after_spaceToBatch_w_pad(op,op,op); }

///@brief gives the new shape of a tensor after a SpaceToBatch transformation
API_EXPORT QuickShape shape_after_spaceToBatch(Replacement &rpx, OpRef const &input, OpRef const &block_size);
API_EXPORT QuickShape shape_after_spaceToBatch_w_pad(Replacement &rpx, OpRef const &input, OpRef const &block_size,
                                                     OpRef const &pads);

// :::EXTERNAL_SHAPEFN::: {  qshape shape_after_depthToSpace(op,op); }

///@brief gives the new shape after depthToSpace transformation
API_EXPORT QuickShape shape_after_depthToSpace(Replacement &rpx, OpRef const &input, OpRef const &block_size);

// :::EXTERNAL_SHAPEFN::: {  qshape before_pad_shape(op); }

///@brief extracts before pads from pad tensor
API_EXPORT QuickShape before_pad_shape(Replacement &rpx, OpRef const &input, OpRef const &padding);
/** @} */

// :::EXTERNAL_SHAPEFN::: {  qshape after_pad_shape(op); }

///@brief extracts before pads from pad tensor
API_EXPORT QuickShape after_pad_shape(Replacement &rpx, OpRef const &input, OpRef const &padding);
/** @} */

// :::EXTERNAL_SHAPEFN::: {  qshape gen_null_shape(op); }

///@brief generate a shape of all 0s with same rank as input
API_EXPORT QuickShape gen_null_shape(Replacement &rpx, OpRef const &input);
/** @} */

// :::EXTERNAL_SHAPEFN::: {  qshape shape_after_pad(op,op); }

///@brief gives the new shape after pad applied
API_EXPORT QuickShape shape_after_pad(Replacement &rpx, OpRef const &input, OpRef const &padding);
/** @} */

// :::EXTERNAL_SHAPEFN::: {  qshape pad_after_shape(op,op,op); }

///@brief calculate the padding after a tensor
API_EXPORT QuickShape pad_after_shape(Replacement &rpx, OpRef const &input, OpRef const &before_pad,
                                      OpRef const &total_size);

/////////////////////////////////////////////////////////////
// Given a match rule like
//
//  Op("Add","X","B"),
// or
//  Op("Slice_shape",Op("Slice_shape","Input","inner_start","inner_size"),"outer_start","outer_size"),
//
// .. we make an MatchOp object that can match it and bind the named parameters
//
// This is done in two steps
//  (1) first, we 'parse' the rule, this is done by executing the code in the context of a MatchBuilder
//      member function. Each Op() returns a shared_ptr<MatchAstNode>.
//  (2) we look at that, and based on what it is, we construct something based on MatchOpBase,
//      which has a method in it to do the matching.
//      The return from (1) can then be discarded
//
//
// during matching:
///  - The matcher engine works by first checking all the Op types and parameter counts (in pre-order
//     traversal), and then going back to bind/check the named operands, all  by following a table.
//     There are no "operand_tag_t" involved in this process, since the indices into the output array
//     are baked into the tables in advance.
//   - in the process, all the OpRef  stored in an array of at most [MATCH_MAX_PATTERN], which is
//     stored in the Match object. The first will contain the 'root' Opref, the next 'n' are the matched subops,
//     and the rest are distinct matched parameter names. 'n' could be 0.
//   - Each instance of MatchOpBase has an array std::vector<pair<operand_tag_t,int>> m_operindex, which supplies
//     the 'operand' names for those matched refs, and maps them to indices in the array (the names are in order).
//   - On a complete match we use Match.set_current_matchop() to install a pointer to the MatchOpBase;
//     subsequently, in 'Constraint' and 'Replace' phases, that object's lookup_opertag() method is used to
//     map operand names to OpRef (it maps the m_operindex to an index into the array).
// TODO: we should probably also have a parallel array in Match of the corresponding OpDef pointers, which
// can be filled in lazily (starting with the ones already obtained during matching, with the rest init to NULL).
// This could reduce repeated lookups in op_def_map during Constraint and Replace phases.
//
//

#define OP_CSTR(op) ((op).c_str())

//
// The subclasses of MatchOpBase are declared and implemented in match_op.cc
//
// MatchOpBase
//      +---MatchOpSimple		    // for 1-level pattern with no duplicate operand names
//      +---MatchOpGeneral			// for all other cases.
//
//
namespace hnnx {

class MatchOpBase;
// These are the state vars within Match which belong to MatchOp.
struct MatchOpState {
    MatchOpBase const *current_matchop; // after a match, points to the matchop, which does operand lookups.
    // table of OpRef bound by the match; current_matchop->lookup_opertag is used to find the index
    // for a given operand tag.
    // (only the first 'n' are valid, where n is current_matchop->m_matchcount)
    OpRef bound_opref[MATCH_MAX_PATTERN];
    // These are either null or pointing to the OpDef indicated by bound_opref[i]
    // (only the first 'n' are valid, where n is current_matchop->m_matchcount)
    OpDef const *bound_opdef[MATCH_MAX_PATTERN];

    // This holds pointers to operands matched by MatchopIterator
    std::array<OpDef const *, MATCH_MAX_PATTERN> matched_opdef;

    API_EXPORT int lookup_opertag(operand_tag_parm_t optag) const;
    bool cse_candidate; // True for rules match Op (x, Op(...))
};

//
class MatchOpBase {
  protected:
    opname_tag_t m_opname0; // name of the root op.
    // 0 <= min <= max
    unsigned short m_min_inputs; // range of input counts on the root op
    unsigned short m_max_inputs;
    unsigned short m_matchcount; // size of table needed for match

    // A fixed list, mapping operand tags to indices in the mapped operands;
    // sorted by operand tag.
    // This is used in lookup_opertag()
    std::vector<std::pair<operand_tag_t, int>> m_operindex;
    //
    // This contains the char const * used for displaying the context
    // (see optim_trace.cc)
    // It may be empty, if this was not enabled in the build.
    std::unique_ptr<const char[]> match_debug_desc;

    API_EXPORT virtual bool do_subclass_match(Match &m, OpDef const &op) const = 0;

    API_EXPORT MatchOpBase(MatchAstNode const *, int matchcount,
                           std::vector<std::pair<operand_tag_t, int>> &&operindex);

    API_EXPORT static MatchOpState &matchop_state(Match &m);
    // lookup_ref:   transform an OpRef to OpDef using the methods in Match
    API_EXPORT OpDef const &lookup_ref(Match &m, OpRef const &op) const;

  public:
    // OpRef to the matched pattern Ops are stored in a linear array,
    // with [0] being the 'base' Op.
    //  For MatchOpSimple, the rest of the array is filled up with the Op's inputs.
    //  For MatchOpGeneral, starting in [1] the array is filled with refs
    //   to all the 'subordinate' Ops (in pre-order). There may be 0 of these.
    //   The rest of the array is filled with OpRef to he named input operands.
    //
    // A table of opdesc is used to match and gather the 'Ops'in the table. Results
    // are stored in order in the match list, starting at [1]
    struct opdesc {
        opname_tag_t opname; // name of the sub op
        unsigned short loc_idx; // index of previously matched containing op, in match table
        unsigned short in_idx; // which input do we look at
        unsigned short min_n, max_n; // range of input count
    };

  protected:
    // parm desc are used to gather the 'named' params
    // results are stored in order in match table.
    // Records with dup_ipx >0 are different: for these, the operand is
    // obtained, and checked to see if it's a dup of the one already at
    // dup_index. If it is not, the match fails; if it is, the matching
    // proceeds, and nothing is added to the output (note, it is not allowed
    // or useful to have an operand aliased to the root op, index 0).
    //
    struct parmdesc {
        unsigned short loc_idx; // index of previously matched op in match table
        unsigned short in_idx; // which input do we look at
        unsigned short dup_idx; // if !=0, must be a dup of dup_index-1
    };

  public:
    // this returns the m_matchcount; can be used for auto-sizing the bind array in match.
    // it needs to be at least as large as the get_mathcount of all the MatchOp.
    int get_matchcount() const { return m_matchcount; }
    // this builds a MatchOp of appropriate class from a MatchAst
    API_EXPORT static MatchOp_uptr build_MatchOp(MatchAstNode *);

    API_EXPORT virtual ~MatchOpBase();
    API_EXPORT bool do_match(Match &m, OpDef const &op) const;
    // lookup an operand tag in m_operindex
    // Returns -1 if not found, or the index (will be in range 0..get_matchcount()-1)
    //
    API_EXPORT int lookup_opertag(operand_tag_parm_t optag) const;
    // this is so we can organize rules based on the root opname.
    API_EXPORT opname_tag_parm_t get_root_opname() const { return m_opname0; }

    // these are used for WITH_OPT_DEBUG. When it is not defined. they return nullptr and empty-map.
    API_EXPORT char const *get_debug_desc() const { return match_debug_desc.get(); } // may return nullptr
    API_EXPORT std::map<OpId, operand_tag_parm_t> get_inverse_map(MatchOpState const &m) const;

    API_EXPORT const std::vector<std::pair<operand_tag_t, int>> &get_operindex() const { return m_operindex; };

    // Number of operators in match
    API_EXPORT virtual unsigned match_size() const = 0;
    API_EXPORT virtual const std::vector<opdesc> *get_opdesc() const = 0;
};

static void fail_lookup(operand_tag_parm_t optag)
{
    errlog("Parameter %s not found", optag.c_str());
    throw std::runtime_error("match parm not found");
}

API_FUNC_EXPORT inline int MatchOpState::lookup_opertag(operand_tag_parm_t optag) const
{
    int const idx = current_matchop->lookup_opertag(optag);
    if (idx < 0) fail_lookup(optag);
    return idx;
}

/////////////////////////////////////////////////////////////

/** \defgroup OptMatch Match-Pattern Expressions for Optimization Rules
 * \ingroup OptimizationFuncs
 *
 * These are the operations available for writing 'Match Pattern' expressions.
 */

/////////////////////////////////////////////////////////////

/** Base Class for Graph Optimization Context
 * This has the shared data elements and functionality, available to all parts of the optimization
 */

class GraphOptContext_Base : public RefersToGraph {
  protected:
    API_EXPORT GraphOptContext_Base(GraphPrepare &g) : RefersToGraph(g) {}
};

// this is a virtual base class which is used to implement MESSAGE dumps
// while running optimization; it abstracts away the difference between
// 'built-in' optimizations, and externally generated, via two different
// subclasses

class OptDebugBase {
  protected:
    GraphPrepare &m_graph;
    uint32_t m_saved_opid;
    OptDebugBase(GraphPrepare &g) : m_graph(g), m_saved_opid(0) {}
    OptDebugBase(GraphPrepare &g, uint32_t saved_opid) : m_graph(g), m_saved_opid(saved_opid) {}

  public:
    GraphPrepare &graph() const { return m_graph; }
    // these are stubs unless WITH_OPT_DEBUG is #defined
    API_EXPORT void show_optim(FILE *f, int indent); // show what a rule has matched
    API_EXPORT void show_optim_replace(FILE *f, OpId opid, int indent);
    API_EXPORT virtual ~OptDebugBase();

  protected:
    // these are used by show_optim, show_optim_replace to access the match context

    virtual char const *get_debug_desc() const = 0; // get the 'matchdesc' string for current optimization
    // get an OpRef of an op which is in the pattern at 'idx'
    virtual OpRef get_bound_opref(unsigned idx) const = 0;
    // get an OpDef * to to an op which is in the pattern at 'idx'
    virtual OpDef const *get_bound_opdef(unsigned idx) const = 0;

  public:
    API_EXPORT virtual uint32_t saved_opid() const { return m_saved_opid; }

    // get mapping from OpId->parm for all OpId in the match pattern; this is used
    // to show the replacement pattern.
    using id_to_parmname_map = std::map<OpId, operand_tag_parm_t>;
    API_EXPORT virtual id_to_parmname_map get_id_to_parmname_map() const = 0;
    API_EXPORT virtual std::string get_debug_filepos() const = 0;
};

/*
 * The Match class contains the functionality for the match functions
 * to implement pattern matching
 *
 * We want to write something like:
 *  Op("Relu",Op("ConvLayer","Act","Weights","Bias","Stride"))
 * Where the first parameter is the name of an operation
 * And the rest of the strings are names that match an input that we can use to
 * refer to the input
 *
 * We need to refer to inputs again even in matching: if we see the same string
 * twice it needs to be the same thing in both places.
 *
 * But primarily we will need to use these strings while during extra constraints
 * and replacement.
 */

class GraphOptInfo;

class Match : public GraphOptContext_Base {
    friend class GraphOptInfo;
    friend class MatchOpBase;
    friend class OptDebugForMatch;

  protected:
    OptimFilter optim_filter; // used for WITH_OPT_DEBUG; empty otherwise
    MatchOpState matchop_state;
    bool pending_show_replacement = false;

    // op_id_counter is saved here before 'replace'; after replace, any
    // OpId which are >= this in the upper 32 bits are 'new'.
    uint32_t save_op_id_counter;

    // optim config vars are set here.
    optim_config_values config_vars;

    Match(GraphPrepare &g) : GraphOptContext_Base(g), optim_filter(g) { set_config_vars(); }
    API_EXPORT void set_config_vars();

    // these are debug hooks; they are defined later as inlines
    API_EXPORT void constraint_begin(GraphOptInfo const &);
    API_EXPORT void replacement_fail();

  public:
    API_EXPORT void replacement_succeed(OpId newop);
    GraphOptInfo const *curr_rule_info = 0; // only used in WITH_OPT_DEBUG

  public:
    // this can be used to test whether an OpId was created since the replacement
    // rule started (though, not at all reliable for 'OpDef_ConstBase' ops).
    API_EXPORT inline bool opid_is_new(OpId op) const { return uint32_t(op >> 32) >= save_op_id_counter; }

    API_EXPORT hnnx::MatchOpState &get_matchop_state() { return matchop_state; }

    //template<typename UniqueType> bool match(OpDef &base);
    typedef MatchAst_uptr (*matchbuilder_type)();
    void record_op_id_counter();
    API_EXPORT optim_config_values const &get_config() const { return config_vars; }
    API_EXPORT void show_debug_message(char const *why,
                                       char const *str); // defined in optimize.cc if WITH_OPT_DEBUG is set
};

// these need to be defined after MatchOpBase and Match.
inline MatchOpState &MatchOpBase::matchop_state(Match &m)
{
    return m.matchop_state;
}
inline bool MatchOpBase::do_match(Match &m, OpDef const &op) const
{
    if (op.opstr != m_opname0) return false;
    int const nin = op.n_inputs();
    if (nin < m_min_inputs || nin > m_max_inputs) return false;
    bool const res = do_subclass_match(m, op);
    m.matchop_state.current_matchop = res ? this : nullptr;
    return res;
}

// Subclass of OptDebugBase for use with Match
class OptDebugForMatch : public OptDebugBase {
  protected:
    Match const &m_match;

  public:
    OptDebugForMatch(Match const &m) : OptDebugBase(m.graph(), m.save_op_id_counter), m_match(m) {}
    API_EXPORT virtual ~OptDebugForMatch() override;
    API_EXPORT virtual std::string get_debug_filepos() const override;

  protected:
    API_EXPORT virtual char const *get_debug_desc() const override;
    // get an OpRef of an op which is in the pattern at 'idx'
    API_EXPORT virtual OpRef get_bound_opref(unsigned idx) const override;
    // get an OpDef * to to an op which is in the pattern at 'idx'
    API_EXPORT virtual OpDef const *get_bound_opdef(unsigned idx) const override;
    API_EXPORT virtual id_to_parmname_map get_id_to_parmname_map() const override;
};

// define these debug hooks
inline void Match::constraint_begin(GraphOptInfo const &grinfo)
{
    if constexpr (not build_options_pub::DefOptLog) {
        return;
    }
    pending_show_replacement = false;
    curr_rule_info = &grinfo;
}
inline void Match::replacement_fail() {}
// Match::replacement_succeed(OpId newop) is in optimize.cc

} // namespace hnnx

namespace oExp {
class opdef_accessor;
}
namespace tiling {
class TileShapeBase;
}

/*
 * Constraints are an expression that can inspect a matched pattern
 * to see if the situation is actually valid
 *
 * EXTERNAL_CONSTRAINT is a hook that can be used to write your own constraint functions.
 */

namespace constraint_lib {

class Constraint : public hnnx::Match {
    friend class oExp::opdef_accessor;

  protected:
    Constraint(GraphPrepare &g) : Match(g) {}
    /* We can put arithmetic functions in a separate library, but we want the namespace here. */
    /* Functions that need things like the context to evaluate should probably go here */
    OpRef get_opref(hnnx::operand_tag_parm_t param_name) const
    {
        int const idx = matchop_state.lookup_opertag(param_name);
        return matchop_state.bound_opref[idx];
    }

  private:
    const OpDef &get_opdef_from_idx(int idx)
    {
        OpDef const *odp = matchop_state.bound_opdef[idx];
        if (odp == nullptr) {
            odp = &matchop_state.bound_opref[idx].dereference(this);
            matchop_state.bound_opdef[idx] = odp;
        }
        return *odp;
    }
    const OpDef &get_opdef(hnnx::operand_tag_parm_t param_name)
    {
        return get_opdef_from_idx(matchop_state.lookup_opertag(param_name));
    }
    const OutputDef &get_outdef(hnnx::operand_tag_parm_t param_name)
    {
        int const idx = matchop_state.lookup_opertag(param_name);
        OpDef const &def = get_opdef_from_idx(idx);
        return def.get_outputdef();
    }
    // this method is used by oExp::opdef_accessor; the definition
    // is in oexpr.cc (it can't be inlined here because it needs Graph).
    API_EXPORT OpDef const &lookup_opdef(OpId oid) const;

  public:
    template <typename UniqueType> static ReplFuncBool constraint();
    typedef ReplFuncBool (*constraintfn_type)();
    friend class tiling::TileShapeBase;

#ifdef WITH_OPT_DEBUG
    // This is current state associated with
    // evaluation of a logical operator (and, or)
    // inside a predicated
    struct TraceState {
        unsigned depth; // depth in the expression tree, 1 is the outermost and or or
        unsigned clause; // cluse number within that and/or, starting with 0
        const char *op; // operator type, "and" or "or"
        bool result; // result
    };
    // Current evaluate state
    TraceState trace{0, 0, "", false};
    // History of evaluations
    std::vector<TraceState> trace_vector;
#endif
};

} // namespace constraint_lib

using Constraint = constraint_lib::Constraint;

/** \defgroup OptReplacement Replacement-Rule Expressions for Optimization Rules
 * \ingroup OptimizationFuncs
 *
 * These are the operations available for writing 'Replacement Rule' expressions. Certain of these
 * accept scalar inputs; for these you can use constant values, or 'constraint' expressions.
 *
 * Note: the operations in this group which appear to return a graph element ( Op, gen_Shape, etc)
 * actually return a ReplFunc, which is a std::function that is called to generate the graph element.
 *
 * Likewise, SPLIT_START, SPLIT_SIZE, SPLIT_DIM actually return ReplFuncInt, a std::function which is called
 * to generate the integer result, which changes as the autosplit is iterated.
 */

/*
 * The Replacement generates the new pattern.
 *
 * EJP: FIXME: maybe we can make things simpler here....
 *
 * Once we've passed the Match and Constraint phase, we want to generate a new
 * set of Ops to replace the sequence.
 *
 * We use the same Op() syntax to generate new things, we use "strings" to
 * refer to matched items, and things typically work nicely.
 *
 * Well, sometimes anyway.
 *
 * It's common to want to do things like slicing, where we want to generate
 * lots of ops... so adding some extra things to be able to slice into multiple
 * things and concatenate them is helpful.
 *
 * But when we try to do that, we run into problems where the items in the
 * dictionary are evaluated before we put them in.  So we do a lot of work
 * with these deferred std::function returns.  Then we just copy what woks
 * to do it again... but I think it might be wasteful.
 *
 * As we're generating these new ops, we start off with the output definition
 * of the thing we're replacing.  That works fine for doing a simple substitution
 * like Op(Relu,Op(ConvLayer,Act,W,B,S)) --> Op(ConvLayer_relu,Act,W,B,S)
 * But if you want to (for example) split weights or pad activations, you need
 * to change the sizes of inputs, not just keep inheriting the output's output def.
 *
 * So we have this WITH_SIZE and friends, but there's probably a better
 * system that we could concieve of.
 *
 * Beyond that, it seems like a lot of the size / quant parameter / slicing
 * code might be kind of common, so maybe some more library code that hides the
 * ugliness is good enough to make the common cases simple.
 *
 */
class Replacement : public Constraint {
    friend class gxE::GXEngine;

    // Thiis only used to suppress some AUTOSPLIT rules when we are using
    // the centalilzed tiler. It should not land.
    class SkipAutosplit : std::exception {
        virtual const char *what() const noexcept { return "autothread skipped"; }
    };

  protected:
    OpDef const *m_curr_op; // used as id reference in 'APPLY'
    API_EXPORT_IMPORT static std::string pkg_flag;
    Replacement(GraphPrepare &g) : Constraint(g), m_curr_op(NULL) {}

  public:
    struct ReplacedId {
      private:
        OpId replaced_id = 0;

      public:
        ReplacedId() {} // = default;

        bool inline is_set() const { return replaced_id != 0; }
        bool inline is_clear() const { return replaced_id == 0; }

        void inline set(OpId replaced_id_in)
        {
            assert(replaced_id_in != 0);
            assert(is_clear());
            replaced_id = replaced_id_in;
        }
        void inline clear()
        {
            assert(is_set());
            replaced_id = 0;
        }
        OpId inline get() const
        {
            assert(is_set());
            return replaced_id;
        }
    };

    API_EXPORT auto find_context(hnnx::split_context_tag_t tag)
    {
        auto cur = split_context.rbegin();
        auto end = split_context.rend();
        for (; cur != end; cur++)
            if (cur->first == tag) return cur;
        errlog("no context found for %s", tag.c_str());
        return split_context.rend();
    }
    API_EXPORT const Split_Context &lookup_split(hnnx::split_context_tag_t tag) const
    {
        return const_cast<Replacement *>(this)->find_context(tag)->second;
    }
    hnnx::MatchOpState &get_matchop_state() { return matchop_state; }
    OpRef match_root() const { return matchop_state.bound_opref[0]; }
    API_EXPORT OpRef do_replacement(const OpDef &oldop, ReplFunc const &replace_func);
    API_EXPORT static void set_pkg_flag(std::string &s) { pkg_flag = s; }

  private:
    std::vector<std::pair<hnnx::split_context_tag_t, Split_Context>> split_context;
    Split_Context &push_split(hnnx::split_context_tag_t tag)
    {
        if (split_context.capacity() < 8) split_context.reserve(8);
        assert(split_context.size() < split_context.capacity());
        return split_context.emplace_back(tag, Split_Context{}).second;
    }
    void pop_split() { split_context.pop_back(); }
    Split_Context &lookup_split(hnnx::split_context_tag_t tag) { return find_context(tag)->second; }
    // apply_param_adapter is a gasket for parameters to SHAPEFN_APPLY and similar:
    //   int, size_t, float, dtype -> same
    //   OpRef -> same;
    //   operand_tag -> lookup OpRef;
    //   ReplFunc -> call it to get OpRef.
    API_EXPORT inline int apply_param_adapter(const OpDef &base, int val) { return val; }
    API_EXPORT inline size_t apply_param_adapter(const OpDef &base, size_t val) { return val; }
    API_EXPORT inline float apply_param_adapter(const OpDef &base, float val) { return val; }
    API_EXPORT inline DType apply_param_adapter(const OpDef &base, DType val) { return val; }
    API_EXPORT inline OpRef apply_param_adapter(const OpDef &base, hnnx::operand_tag_parm_t str)
    {
        return get_opref(str);
    }
    API_EXPORT inline OpRef apply_param_adapter(const OpDef &base, OpRef ref) { return ref; }
    API_EXPORT inline OpRef apply_param_adapter(const OpDef &base, ReplFunc const &f) { return f(*this, base); }

    template <oExp::Variant V, typename T>
    API_EXPORT inline auto apply_param_adapter(const OpDef &base, oExp::expr<V, T> const &expn)
    {
        return expn.eval(*this);
    }
    template <oExp::OpVnt V, typename T>
    API_EXPORT inline OpRef apply_param_adapter(const OpDef &base, oExp::opexpr<V, T> const &expn)
    {
        return expn.eval(*this);
    }

    // 'runtime' of ResizeDim
    API_EXPORT OpRef do_ResizeDim(OpDef const &old, int dim, int size, ReplFunc const &f, bool reduce_dim = false,
                                  hnnx::splithist_t const *new_splithist = nullptr);

    // A thin subclass of ReplFunc, which can be constructed from a ReplFunc, but also
    // from an opexpr<V,T>
    struct ReplFunc_general : ReplFunc {
        ReplFunc_general(ReplFunc &&f) : ReplFunc(std::move(f)) {}
        ReplFunc_general(ReplFunc_general &&src) = default;
        ReplFunc_general(ReplFunc_general const &) = default;
        template <oExp::OpVnt V, typename T>
        ReplFunc_general(oExp::opexpr<V, T> &&op) : ReplFunc(hnnx::wrap_as_replfunc(op))
        {
        }
        template <oExp::OpVnt V, typename T>
        ReplFunc_general(oExp::opexpr<V, T> const &op) : ReplFunc(hnnx::wrap_as_replfunc(op))
        {
        }
    };
    // A thin subclass of ReplFunc, which can be constructed from a ReplFunc, but also
    // from an operand tag, or string (via Operand()), or a fixed OpRef (this is to support OUTPUT_OF and similar)
    struct ReplFunc_or_Operand : ReplFunc {
        ReplFunc_or_Operand(ReplFunc &&f) : ReplFunc(std::move(f)) {}
        ReplFunc_or_Operand(ReplFunc_or_Operand &&src) = default;
        ReplFunc_or_Operand(ReplFunc_or_Operand const &) = default;
        ReplFunc_or_Operand(hnnx::operand_tag_parm_t str) : ReplFunc(Operand(str)) {}
        ReplFunc_or_Operand(char const *str) : ReplFunc(Operand(str)) {}
        API_EXPORT ReplFunc_or_Operand(OpRef const &);

        template <oExp::OpVnt V, typename T>
        ReplFunc_or_Operand(oExp::opexpr<V, T> &&op) : ReplFunc(hnnx::wrap_as_replfunc(op))
        {
        }
        template <oExp::OpVnt V, typename T>
        ReplFunc_or_Operand(oExp::opexpr<V, T> const &op) : ReplFunc(hnnx::wrap_as_replfunc(op))
        {
        }
    };

    /// \ingroup OptReplacement
    /// @brief ResizeDim(dim,size, expr) - evaluate 'expr' with a modification of reference shape
    ///
    /// The reference shape used to evaluate 'expr' is modified from the default by changing dimension 'dim' to size'
    ///
    API_EXPORT static ReplFunc ResizeDim(int dim, int size, ReplFunc_general &&f);
    //
    // Modifiers (e.g. WITH_SIZE( ref, target )
    //  work like this:
    //     (a) evaluate the 'ref' subtree using the current reference OpDef as reference;
    //     (b) execute the modifier. This creates a temporary OpDef object, which combines
    //         attributes of the original ref object, and the one constructed from ref;
    //         e.g. WITH_SIZE takes rank and shape from 'ref' and the dtype etc from previoud ref
    //     (c) now, execute the 'target' subtree using this temporary object as the reference.
    //         The result of that is the result of the modifier. The temporary OpDef is discarded.
    //
    // this does WITH_SIZE, WITH_TYPE, WITH_SAME_OUTPUT
    static const int mode_with_size = 1;
    static const int mode_with_type = 2;
    static const int mode_with_same_output = mode_with_size | mode_with_type;

    // immed_modifier does step (b) above; it makes the temp object from the ref result and the current opdef
    // The lambda inside WITH_output_like does steps (a) and (c).
    //
    API_EXPORT OpDef immed_modifier(OpRef const &ref, OpDef const &old, int mode);

    API_EXPORT static ReplFunc WITH_output_like(ReplFunc_or_Operand &&ref, ReplFunc &&f, int mode);

    // implements WrapOp and WrapOpAlways
    API_EXPORT static ReplFunc WrapOp_internal(char const *op_name, char const *package, ReplFunc_or_Operand &&in_op,
                                               bool is_idem); // True for WrapOp, false for WrapOpAlways

    // implements WrapOp("op", "parmname") specifically
    API_EXPORT static ReplFunc WrapOp_quick(char const *op_name, char const *package, char const *parm);

  public:
    API_EXPORT OpDef immed_modifier_OPID(OpRef const &ref, OpDef const &old);

  private:
    /// \ingroup OptReplacement
    /// @brief WITH_SAME_ID(refexp, expr) - evaluate 'expr' using 'refexp' for the reference opid
    API_HIDDEN inline static ReplFunc WITH_SAME_ID(ReplFunc_or_Operand &&ref, ReplFunc_general &&f)
    {
        return ReplFunc::create([=](Replacement &rpx, const OpDef &old) -> OpRef {
            OpDef const new_def = rpx.immed_modifier_OPID(ref(rpx, old), old);
            return f(rpx, new_def);
        });
    }

    /// \ingroup OptReplacement
    /// @brief WITH_SPLIT_HISTORY(refexp, expr) - evaluate 'expr' using 'refexp' for the split history
    API_HIDDEN inline static ReplFunc WITH_SPLIT_HISTORY(ReplFunc_or_Operand &&ref, ReplFunc_general &&f)
    {
        return ReplFunc::create([=](Replacement &rpx, const OpDef &old) -> OpRef {
            OpRef new_id = f(rpx, old);
            OpDef &new_def = new_id.dereference(rpx.graph());
            OpRef const ref_id = ref(rpx, old);
            new_def.set_splithist(ref_id.dereference(rpx.graph()).get_splithist());
            return new_id;
        });
    }

    API_EXPORT void do_SPLIT_HISTORY(const OpDef &Src, int dim, OpDef &expr);

    /// \ingroup OptReplacement
    /// @brief WITH_SPLIT_HISTORY(refexp, dim, expr) - evaluate 'expr' using 'refexp' for the split history
    //
    // Add a new entry to split history table using refexp as the parent, dim as dimension.
    // expr is expected to be a Concat or a InstanceNorm.SumAndSquares_TileReduce.
    // The number of splits is determined by the number of children of expr
    // The chunksize is determined by the first non-constant child
    API_HIDDEN inline static ReplFunc WITH_SPLIT_HISTORY(ReplFunc_or_Operand &&ref, int dim, ReplFunc_general &&f)
    {
        return ReplFunc::create([=](Replacement &rpx, const OpDef &old) -> OpRef {
            OpRef const ref_id = ref(rpx, old);
            OpRef new_id = f(rpx, old);
            rpx.do_SPLIT_HISTORY(ref_id.dereference(rpx), dim, new_id.dereference(rpx));
            return new_id;
        });
    }

  public:
    /// \ingroup OptReplacement
    /// @brief WITH_SIZE(refexp, expr) - evaluate 'expr' using 'refexp' for the reference output size
    API_HIDDEN inline static ReplFunc WITH_SIZE(ReplFunc_or_Operand &&shape, ReplFunc_general &&f)
    {
        return WITH_output_like(std::move(shape), std::move(f), mode_with_size);
    }

  private:
    /// \ingroup OptReplacement
    /// @brief WITH_TYPE(refexp, expr) - evaluate 'expr' using 'refexp' for the reference output type
    API_HIDDEN inline static ReplFunc WITH_TYPE(ReplFunc_or_Operand &&type, ReplFunc_general &&f)
    {
        return WITH_output_like(std::move(type), std::move(f), mode_with_type);
    }
    /// \ingroup OptReplacement
    /// @brief WITH_SAME_OUTPUT(refexp, expr) - evaluate 'expr' using 'refexp' for the reference output type and size
    API_HIDDEN inline static ReplFunc WITH_SAME_OUTPUT(ReplFunc_or_Operand &&ref, ReplFunc_general &&f)
    {
        return WITH_output_like(std::move(ref), std::move(f), mode_with_type | mode_with_size);
    }

    /// \ingroup OptReplacement
    /// @brief Doesn't change anything in graph, just returns the op and adds it to const_tracker if enabled
    /// Does NOT support tracking scalars
    /// Please only use quant dependent constants, and avoid double tracking the same const
    API_HIDDEN inline static ReplFunc MARK_REPLACEABLE_QCONST(ReplFunc_or_Operand &&op)
    {
        return ReplFunc::create([op](Replacement &rpx, const OpDef &old) -> OpRef {
            return rpx.add_TRACKED_OP(rpx, old, std::move(op));
        });
    }

    // what are comments
    OpRef add_TRACKED_OP(Replacement &rpx, const OpDef &old, const ReplFunc_or_Operand &&op);

    API_HIDDEN inline static ReplFunc WrapOp(char const *opname, ReplFunc_or_Operand &&f)
    {
        return WrapOp_internal(opname, pkg_flag.c_str(), std::move(f), true);
    }
    API_HIDDEN inline static ReplFunc WrapOp(char const *opname, char const *operand)
    {
        return WrapOp_quick(opname, pkg_flag.c_str(), operand);
    }
    API_HIDDEN inline static ReplFunc WrapOpAlways(char const *opname, ReplFunc_or_Operand &&f)
    {
        return WrapOp_internal(opname, pkg_flag.c_str(), std::move(f), false);
    }

    API_EXPORT OpRef immed_gen_ShapeOf(OpRef const &shaperef, OpDef const &old);
    /// \ingroup OptReplacement
    /// @brief gen_ShapeOf(any_oper) - Construct an OpDef_Shape with the shape taken from the given graph operation.
    API_EXPORT static ReplFunc gen_ShapeOf(ReplFunc_or_Operand &&shape);

    API_EXPORT static inline OpDef immed_modifier_OUTPUT_TYPE(OpDef const &old, DType dtype, int32_t zero_offset,
                                                              float stepsize)
    {
        OutputDef temp{};
        temp.dtype = dtype;
        temp.zero_offset = zero_offset;
        temp.stepsize = stepsize;
        return old.make_output_exemplar(nullptr, &temp);
    }

    API_EXPORT static ReplFunc WITH_OUTPUT_TYPE_func(ReplFuncDType &&dtype, ReplFuncInt &&zero_offset,
                                                     ReplFuncFloat &&stepsize, ReplFunc &&f);

  public:
    /// \ingroup OptReplacement
    /// @brief WITH_OUTPUT_TYPE(dtype,zero_offset,stepsize,expr) - evaluate 'expr' but using the specified output type.
    ///
    /// A temporary reference is created which specifies the given dtype, step, and offset instead of the
    /// default; this is used to evaluate 'expr'. If the dtype is not quantized, use 0 and 1.0f for zero_offset and stepsize.
    API_EXPORT static ReplFunc WITH_OUTPUT_TYPE(DType dtype, int32_t zero_offset, float stepsize, ReplFunc_general &&f);

    // adapter to allow  WITH_OUTPUT_TYPE to be called with some mixture of literals and oExp, and have them all converted to
    // function objects, which are forwarded to WITH_OUTPUT_TYPE_func
    template <typename TDT, typename TZO, typename TSS>
    static inline ReplFunc WITH_OUTPUT_TYPE(TDT &&dtype, TZO &&zero_offset, TSS &&stepsize, ReplFunc_general &&f)
    {
        return WITH_OUTPUT_TYPE_func(oExp::wrap_as_function<DType>(std::forward<TDT>(dtype)),
                                     oExp::wrap_as_function<int>(std::forward<TZO>(zero_offset)),
                                     oExp::wrap_as_function<float>(std::forward<TSS>(stepsize)), std::move(f));
    }

  private:
    /// \ingroup OptReplacement
    /// @brief WITH_MULT_OUT(int num_outputs, expr) - evaluate 'expr' with 'DType::Multi' for 'num_outputs' outputs
    ///
    /// A temporary reference is created with OutputDef configured to make an Multi-Output op with the given number
    /// of outputs. This is used to evaluate 'expr'. num_outputs must be >=2.
    ///
    API_EXPORT static ReplFunc WITH_MULTI_OUT(unsigned num_outputs, ReplFunc_general &&f);

    /// immed_WITH_MULTI_OUT makes the OpDef used in WITH_MULTI_OUT.
    API_EXPORT static OpDef immed_WITH_MULTI_OUT(OpDef const &old, unsigned num_outputs);

    static OpRef shapefn_adapt_result(const OpDef &old, OpRef const &inp) { return inp; };
    API_EXPORT static OpRef shapefn_adapt_result(const OpDef &old, QuickShape const &inp);

    template <typename F_T, typename... Arg_Ts>
    API_HIDDEN OpRef immed_SHAPEFN_APPLY(const OpDef &old, F_T f, Arg_Ts &&...args)
    {
        OpDef const *const keep = m_curr_op;
        m_curr_op = &old;
        OpRef result = shapefn_adapt_result(old, f(*this, std::forward<Arg_Ts>(args)...));
        m_curr_op = keep;
        return result;
    }
    /// \ingroup OptReplacement
    /// @brief SHAPEFN_APPLY(function,parms...) - generate a shape object by calling a specified function.
    ///
    /// The named function is called, with specified parameters. These can be strings (assumed to be be operand
    /// references, and converted to OpRef), or scalar expressions.
    ///
    /// See also: \ref ShapeFnApply
    template <typename F_T, typename... Arg_Ts> API_HIDDEN static ReplFunc SHAPEFN_APPLY(F_T f, Arg_Ts... args)
    {
        return ReplFunc::create([=](Replacement &rpx, const OpDef &old) -> OpRef {
            /* Call f(rpx,args...) */
            return rpx.immed_SHAPEFN_APPLY(old, f, rpx.apply_param_adapter(old, args)...);
        });
    }

    /// \ingroup OptReplacement
    /// @brief AUTOSPLIT_SHAPEFN_APPLY(function, "split_tag", parms...) - generate a shape object by calling a specified function.
    ///
    /// The named function is called, with specified parameters. These can be strings (assumed to be be operand
    /// references, and converted to OpRef), or scalar expressions.
    /// The 'split_tag' parameter is converted to a reference to a Split_Context
    ///
    /// See also: \ref AutoSplitShapeFnApply
    template <typename F_T, typename... Arg_Ts>
    API_HIDDEN static ReplFunc AUTOSPLIT_SHAPEFN_APPLY(F_T f, hnnx::split_context_tag_t whatsplit, Arg_Ts... args)
    {
        return ReplFunc::create([=](Replacement &rpx, const OpDef &old) -> OpRef {
            /* Call f(rpx,split_context.at(whatsplit),args...) */
            return rpx.immed_SHAPEFN_APPLY(old, f, rpx.lookup_split(whatsplit), rpx.apply_param_adapter(old, args)...);
        });
    }
    // AUTOSPLIT_SLICE, TYPICAL_SLICE, CHANGEDIM_SLICE
    // are 'macro' operations - same effect as inserting a more
    // complex expression in the source rule.

    /// \ingroup OptReplacement
    /// @brief AUTOSPLIT_SLICE(in, start, size ) -> WITH_SIZE( size, WITH_TYPE( in, Op("Slice_shape", in, start, size)))
    ///
    /// This generates a "Slice_shape" op applied to the given input 'in', with the given 'start' and 'size' shapes. The
    /// output shape is configured to match 'size', and the output type is always the same as 'in'
    ///
    API_EXPORT static ReplFunc AUTOSPLIT_SLICE(ReplFunc_or_Operand &&in, ReplFunc_or_Operand &&start,
                                               ReplFunc_or_Operand &&size);

    /// \ingroup OptReplacement
    /// @brief Create a slice of an autosplit via a simple split along a dimension.
    ///
    /// This does an 'AUTOSPLIT_SLICE' where the size and start are calculated by
    /// simple_split_start, and simple_split_size, i.e. the split is done exactly as the output split,
    /// in the same dimension, with no overlap.
    ///
    /// Equivalent to the following:
    ///
    ///     TYPICAL_SLICE(in, "tag" ) ->
    ///       AUTOSPLIT_SLICE( in,
    ///          AUTOSPLIT_SHAPEFN_APPLY( simple_split_start, tag, in ),
    ///          AUTOSPLIT_SHAPEFN_APPLY( simple_split_size, tag, in ))
    API_EXPORT static ReplFunc TYPICAL_SLICE(ReplFunc_or_Operand &&in, hnnx::split_context_tag_t whatsplit);

    /// \ingroup OptReplacement
    /// @brief Create a slice of an autosplit
    ///
    /// This does an 'AUTOSPLIT_SLICE' where the size and start are calculated by
    /// simpledim_split_start, and simpledim_split_size, i.e. the split is done as the output split
    /// with no overlap, but it may be applied to a different axis than that specified
    /// in the AUTOSPLIT.
    ///
    /// Equivalent to the following:
    ///
    ///     CHANGEDIM_SLICE(in, "tag", int newdim ) ->
    ///       AUTOSPLIT_SLICE( in,
    ///          AUTOSPLIT_SHAPEFN_APPLY( simpledim_split_start, tag, in, newdim ),
    ///          AUTOSPLIT_SHAPEFN_APPLY( simpledim_split_size, tag, in, newdim ))
    API_EXPORT static ReplFunc CHANGEDIM_SLICE(ReplFunc_or_Operand &&in, hnnx::split_context_tag_t whatsplit,
                                               int newdim);

    // Pretty much all TYPICAL_SLICE and CHANGEDIM_SLICE are just ("string", "string") .. so this wrapper
    // will save some code space at the call sites.
    API_EXPORT static ReplFunc CHANGEDIM_SLICE(char const *in_parm, char const *whatsplit, int newdim);

  public:
    static ReplFunc TYPICAL_SLICE(char const *in_parm, char const *whatsplit)
    {
        return CHANGEDIM_SLICE(in_parm, whatsplit, -1);
    }

  public:
    // this actually implements TYPICAL_SLICE (with newdim=-1) and CHANGEDIM_SLICE (with newdim >=0)
    API_EXPORT OpRef do_TYPICAL_SLICE(OpDef const &old, OpRef input_op, hnnx::split_context_tag_t whatsplit, int newdim,
                                      bool reduce_dim = false);

    API_EXPORT OpRef do_AUTOSPLIT(OpDef const &old, int dim, Split_Context &splitinfo, int chunksize, ReplFunc const &f,
                                  bool reduce_dim = false, bool autothread = false);

    API_EXPORT OpRef do_AUTOTHREAD(OpDef const &old, int dim, hnnx::split_context_tag_t varname, int ntiles,
                                   ReplFunc const &f);

    /// \ingroup OptReplacement
    /// @brief Expand an expression by splitting on some dimension.
    ///
    /// AUTOSPLIT( dim, "tag", size,  <repl_expression> ) causes the operation to be split into
    /// slices along dimension dim, with each slice being of 'size' (or possibly smaller, for the last one).
    ///
    /// This done by
    ///
    ///   * Repeatedly evaluating the 'repl_expression', once for each slice
    ///   * Using a 'Concat' on the specified dimension to join the results.
    ///   * Within each iteration, SPLIT_START("tag") and SPLIT_SIZE"tag"), when evaluated within <repl_expression>,
    ///    will reflect the extent of the current split in the output, and thus can be used to construct the corresponding
    ///    slices of the input. SLICE_DIM("tag") will always give the value supplied to the AUTOSPLIT as 'dim'. Normally, this is all done within
    ///    functions invoked via AUTOSPLIT_SHAPEFN_APPLY.
    ///
    /// Rules with AUTOSPLIT should have a constraint to prevent them from being applied where the split dimension does not exceed size.
    ///
    /// @param dim        Dimension on which to split
    /// @param varname    A string indentifying the split context
    /// @param chunksize  The size of each slice of the output
    /// @param f          The subexpression to generate each part of the split
  public:
    API_EXPORT static ReplFunc AUTOSPLIT(int dim, hnnx::split_context_tag_t varname, int chunksize,
                                         ReplFunc_general &&f);

  private:
    API_EXPORT static ReplFunc AUTOSPLIT_func(ReplFuncInt &&dim, hnnx::split_context_tag_t varname,
                                              ReplFuncInt &&chunksize, ReplFunc &&f);

    template <oExp::Variant V1, typename T1, oExp::Variant V2, typename T2>
    API_HIDDEN inline static ReplFunc AUTOSPLIT(oExp::expr<V1, T1> &&dim, hnnx::split_context_tag_t varname,
                                                oExp::expr<V2, T2> &&chunksize, ReplFunc_general &&f)
    {
        return AUTOSPLIT_func(oExp::wrap_as_function<int>(std::move(dim)), varname,
                              oExp::wrap_as_function<int>(std::move(chunksize)), std::move(f));
    }
    // TODO: need a better way to do this (map 'int' or oExp which is int, to ReplFuncInt).
    template <oExp::Variant V2, typename T2>
    API_HIDDEN inline static ReplFunc AUTOSPLIT(int dim, hnnx::split_context_tag_t varname,
                                                oExp::expr<V2, T2> &&chunksize, ReplFunc_general &&f)
    {
        return AUTOSPLIT_func(oExp::make_literal_sfunction<int>(dim), varname,
                              oExp::wrap_as_function<int>(std::move(chunksize)), std::move(f));
    }
    template <oExp::Variant V1, typename T1>
    API_HIDDEN inline static ReplFunc AUTOSPLIT(oExp::expr<V1, T1> &&dim, hnnx::split_context_tag_t varname,
                                                int chunksize, ReplFunc_general &&f)
    {
        return AUTOSPLIT_func(oExp::wrap_as_function<int>(std::move(dim)), varname,
                              oExp::make_literal_sfunction<int>(chunksize), std::move(f));
    }

  public:
    // Performs AUTOSPLIT in the specified dimension to create at most options.autothread_hvx_ntiles splits
    // that will not be further autothreaded.
    API_EXPORT static ReplFunc AUTOTHREAD_HVX(int dim, hnnx::split_context_tag_t varname, ReplFunc_general &&f);
    // Same for options.autothread_hmx_ntiles.
    API_EXPORT static ReplFunc AUTOTHREAD_HMX(int dim, hnnx::split_context_tag_t varname, ReplFunc_general &&f);

    static ReplFunc first_AUTOSPLIT(int const dim, hnnx::split_context_tag_t const varname, int const chunksize,
                                    ReplFunc const &f);

  private:
    static ReplFunc first_AUTOSPLIT(int const dim, hnnx::split_context_tag_t const varname,
                                    ReplFuncInt const &&chunksize, ReplFunc const &f);

    template <oExp::Variant V2, typename T2>
    API_FUNC_HIDDEN inline static ReplFunc AUTOSPLIT_FIRST(int dim, hnnx::split_context_tag_t varname,
                                                           oExp::expr<V2, T2> &&chunksize, ReplFunc_general &&f)
    {
        return Replacement::first_AUTOSPLIT(dim, varname, oExp::wrap_as_function<int>(std::move(chunksize)), f);
    }

    API_FUNC_HIDDEN inline static ReplFunc AUTOSPLIT_FIRST(int const dim, hnnx::split_context_tag_t const varname,
                                                           int const chunksize, ReplFunc_general const &&f)
    {
        return Replacement::first_AUTOSPLIT(dim, varname, chunksize, f);
    }

  private:
    /// AUTOSPLIT and reduce the dim
    API_EXPORT static ReplFunc AUTOSPLIT_REDUCE(int dim, hnnx::split_context_tag_t varname, ReplFunc_general &&f);

    API_EXPORT static ReplFunc TYPICAL_SLICE_REDUCE(ReplFunc_or_Operand &&in, hnnx::split_context_tag_t whatsplit);

    /// \ingroup OptReplacement
    /// @brief Create a multi-output Op by iterating over expression
    ///
    /// OP_ITER( op_base, "tag", lo_index, hi_index, <repl_expression> )
    ///
    /// The operation will iterate for "I" >= lo_index, < hi_index; for each value, the repl_expression
    /// is evaluated, and a new Op is created which
    ///
    ///  - has the same opstr as op_base, and the same inputs, plus additions inputs generated by
    ///    the iteration
    ///  - the OutputDef of the new op is defined by the context of the ITER_OP, and may be different
    ///    from that of the op_base.
    ///
    /// if lo_index <= hi_index, no iteration is done, and the built Op has the same inputs
    /// as op_base. Rules with OP_ITER should have a constraint to prevent them from being
    /// applied where this could be incorrect.
    ///
    /// @param op_base    'Reference' Op supplying the name and fixed inputs
    /// @param varname    A string indentifying the split context
    /// @param lo_index   the first input index
    /// @param hi_index   the last input index+1
    /// @param f          The subexpression to iterate.
    ///
    API_EXPORT static ReplFunc OP_ITER(ReplFunc &&op_base, hnnx::split_context_tag_t varname, int lo_index,
                                       int hi_index, ReplFunc_general &&f);

    // same with ReplFuncInt for the index values, so they can be expressions
    API_EXPORT static ReplFunc OP_ITER_func(ReplFunc &&op_base, hnnx::split_context_tag_t const &varname,
                                            ReplFuncInt &&lo_index, ReplFuncInt &&hi_index, ReplFunc &&f);

    // template to map expressions to ReplFuncInt
    template <typename TLO, typename THI>
    API_HIDDEN inline static ReplFunc OP_ITER(ReplFunc &&op_base, hnnx::split_context_tag_t varname, TLO &&lo_index,
                                              THI &&hi_index, ReplFunc_general &&f)
    {
        return OP_ITER_func(std::move(op_base), varname, oExp::wrap_as_function<int>(std::forward<TLO>(lo_index)),
                            oExp::wrap_as_function<int>(std::forward<THI>(hi_index)), std::move(f));
    }

    API_EXPORT OpRef do_OP_ITER(OpDef const &old, OpDef const &base_op, Split_Context &splitinfo, int lo_index,
                                int hi_index, ReplFunc const &f);

    /// \ingroup OptReplacement
    /// @brief INHERIT_MEMOS_FROM(refexp, expr) - evaluate 'expr' inheriting any persistent memos from 'refexp'

    API_EXPORT void do_INHERIT_MEMOS(const OpDef &old, OpDef &newdef);

    API_HIDDEN inline static ReplFunc INHERIT_MEMOS_FROM(ReplFunc_or_Operand &&ref, ReplFunc_general &&f)
    {
        return ReplFunc::create([=](Replacement &rpx, const OpDef &old) -> OpRef {
            OpRef const ref_id = ref(rpx, old);
            OpRef new_id = f(rpx, old);
            rpx.do_INHERIT_MEMOS(ref_id.dereference(rpx), new_id.dereference(rpx));
            return new_id;
        });
    }

    /// \ingroup OptReplacement
    /// @brief WITH_MEMOS(refexp, expr) - evaluate 'expr' using 'refexp' for persistant memos

    API_EXPORT void do_WITH_MEMOS(const OpDef &old, OpDef &newdef);

    API_HIDDEN inline static ReplFunc WITH_MEMOS(ReplFunc_or_Operand &&ref, ReplFunc_general &&f)
    {
        return ReplFunc::create([=](Replacement &rpx, const OpDef &old) -> OpRef {
            OpRef const ref_id = ref(rpx, old);
            OpRef new_id = f(rpx, old);
            rpx.do_WITH_MEMOS(ref_id.dereference(rpx), new_id.dereference(rpx));
            return new_id;
        });
    }

    // this is basically a SHAPEFN_APPLY for a function with no other inputs.
    // We still want to bind it into a std::function.

    /// \ingroup OptReplacement
    /// @brief Generate replacement by calling an external function
    ///
    /// The function must be: OpRef function( Replacement &, OpDef const &op);
    ///
    /// ... where 'op' is the OpDef being replaced.
    /// The return value is the OpRef of the replacement. If it's the same as the OpRef of 'op', it is assumed
    /// that the rule has no effect in this situation.
    ///
    API_HIDDEN static ReplFunc EXTERNAL_REPLACE(external_replace_funcp f)
    {
        return ReplFunc(ReplFunc::FunctionWrapper, (void *)f);
    }

    /// \ingroup OptReplacement
    /// @brief Define a new mult-output Op, with one of its outputs
    ///
    ///  OpMultiOut( n_out, outno, "opstr", ...inputs... )
    ///  is equivalent to
    ///     Op( "$Out",  WITH_MULTI_OUT( n_out, Op("opstr", ... inputs...),
    ///        gen_Shape(0,0,n_out,outno))
    ///
    /// If any of the inputs have Op in them, they will need to have WITH_ modifiers
    /// for shape and type enclosing them.
    ///
    template <typename... Ts>
    API_HIDDEN inline static ReplFunc OpMultiOut(unsigned n_out, unsigned outno, char const *opstr, Ts &&...ts)
    {
        assert(n_out >= 2 && outno < n_out);
        return Op("$Out", WITH_MULTI_OUT(n_out, Op(opstr, std::forward<Ts>(ts)...)),
                  gen_Shape(0, 0, size_t(n_out), size_t(outno)));
    }

  public:
    /// \ingroup OptReplacement
    /// @brief Generate reference to an operand in the match rule: Operand("opname")
    ///
    /// This need not be written in rules; if "X" appears in any part of a replacement rule
    /// where an 'Op' expression is needed, it will be interpreted as Operand("X"). Including
    /// the case where the entire replacement rule is just "X" (i.e. the rule 'bypasses' input X to
    /// the output).
    ///
    static ReplFunc Operand(hnnx::operand_tag_parm_t str)
    {
        return ReplFunc::create([=](Replacement &rpx, const OpDef &old) -> OpRef { return rpx.get_opref(str); });
    }
    static ReplFunc Operand(ReplFunc const &opf) { return opf; }
    static ReplFunc Operand(ReplFunc &&opf) { return std::move(opf); }
    template <oExp::OpVnt V, typename T> static ReplFunc Operand(oExp::opexpr<V, T> &&op)
    {
        return hnnx::wrap_as_replfunc(op);
    }
    template <oExp::OpVnt V, typename T> static ReplFunc Operand(oExp::opexpr<V, T> const &op)
    {
        return hnnx::wrap_as_replfunc(op);
    }

    API_EXPORT static ReplFunc Op_inner(char const *str, char const *package, int n_in, ReplFunc const *ifuncs);

    // all of the Ts for Op should be either an operand_tag_t (or convertible to one)
    // or should be a ReplFunc
    // This Op() just maps all the operand tags to  ReplFunc
    // (by passing the them all through Operand(), which has no effect on functions)
    // They are placed in an array, passed to Op_inner.
    //
    /// \ingroup OptReplacement
    /// @brief Generate a new Op in a replacement rule: Op("opname", ... inputs ... )
    ///
    /// The inputs can be any 'replacement' expressions, or operand tags; the shape and type of the Op output
    /// are inherited from the replaced Op -- or from the innermost modifier, if the Op appears
    /// within a modifier.
    ///

    template <typename... Ts> API_HIDDEN static ReplFunc Op(char const *str, Ts... ts)
    {
        std::array<ReplFunc, sizeof...(Ts)> input_funcs = {Replacement::Operand(ts)...};
        return Op_inner(str, pkg_flag.c_str(), sizeof...(Ts), input_funcs.data());
    }

  private:
    // this is to include oExp::SELECT, on an equal namespace footing with these other select.
    template <typename TS, typename TA, typename TB> static inline auto SELECT(TS &&sel, TA &&a, TB &&b)
    {
        // compiler wants to use this for Repl inputs, too...
        // send those to SELECT_func
        if constexpr (std::is_constructible<ReplFunc_or_Operand, TA>::value ||
                      std::is_constructible<ReplFunc_or_Operand, TB>::value) {
            return SELECT_func(oExp::wrap_as_function<bool>(std::forward<TS>(sel)),
                               ReplFunc_or_Operand(std::forward<TA>(a)), ReplFunc_or_Operand(std::forward<TB>(b)));
        } else {
            return oExp::SELECT(std::forward<TS>(sel), std::forward<TA>(a), std::forward<TB>(b));
        }
    }

    // this is to implement all of the select cases where ?: works, where the result is not ReplFunc
    /* removed - I doubt this is safe
	template <typename TA, typename TB>
	static auto SELECT( bool sel, TA &&iftrue, TB &&iffalse) -> decltype(sel?iftrue:iffalse) {
		return sel? std::forward<TA>(iftrue): std::forward<TB>(iffalse);
	} */
    // SELECT ReplFunc with immediate execution
    // The second one allows "Parmname" as one operand, third allows two.
    API_EXPORT static ReplFunc SELECT(bool sel, ReplFunc_general &&iftrue, ReplFunc_general &&iffalse);
    API_EXPORT static ReplFunc SELECT(bool sel, ReplFunc_or_Operand &&iftrue, ReplFunc_or_Operand &&iffalse);
    API_EXPORT static ReplFunc SELECT(bool sel, char const *iftrue, char const *iffalse);
    // SELECT ReplFunc with deferred execution (returned function will call sel(), and then one
    // of the functions).
    API_EXPORT static ReplFunc SELECT_func(ReplFuncBool &&sel, ReplFunc_or_Operand &&iftrue,
                                           ReplFunc_or_Operand &&iffalse);

    template <oExp::Variant V, typename T>
    static ReplFunc SELECT(oExp::expr<V, T> &&condn, ReplFunc_or_Operand &&iftrue, ReplFunc_or_Operand &&iffalse)
    {
        return SELECT_func(oExp::wrap_as_function<bool>(std::move(condn)), std::move(iftrue), std::move(iffalse));
    }

    /*
	OpRef do_replacement(const OpDef & oldop, ReplFunc const & f)
	{
		return f(*this,oldop);
	}
	OpRef do_replacement(const OpDef & oldop, hnnx::operand_tag_parm_t str)
	{
		return get_opref(str);
	}*/
  public:
    OpDef const &curr_op() const { return *m_curr_op; }

    API_EXPORT static OpRef gen_node(const hnnx::opname_tag_t str, size_t n_in, OpRef const *inputs, const OpDef &old,
                                     char const *package_name = THIS_PKG_NAME_STR, const OpDef *model = nullptr);
    static inline OpRef gen_node(const hnnx::opname_tag_t str, std::vector<OpRef> const &inputs, const OpDef &old,
                                 char const *package_name = THIS_PKG_NAME_STR, const OpDef *model = nullptr)
    {
        return gen_node(str, inputs.size(), inputs.data(), old, package_name, model);
    }
    // allow {opref1, opref2} for 'inputs' (without becoming std::vector)
    static inline OpRef gen_node(const hnnx::opname_tag_t str, std::initializer_list<OpRef> inputs, const OpDef &old,
                                 char const *package_name = THIS_PKG_NAME_STR, const OpDef *model = nullptr)
    {
        return gen_node(str, inputs.size(), inputs.begin(), old, package_name, model);
    }
    template <size_t N>
    static inline OpRef gen_node(const hnnx::opname_tag_t str, std::array<OpRef, N> const &inputs, const OpDef &old,
                                 char const *package_name = THIS_PKG_NAME_STR, const OpDef *model = nullptr)
    {
        return gen_node(str, N, inputs.data(), old, package_name, model);
    }

    API_EXPORT OpRef gen_Shape_in_graph(const OpDef &old, int rank, size_t const *sizes);

    template <DType DT>
    API_EXPORT OpRef gen_Const_scalar(const OpDef &old, typename dtype_traits<DT>::element_type constval);

    template <DType DT>
    API_EXPORT OpRef gen_Const_1D_array(const OpDef &old, typename dtype_traits<DT>::element_type const *vals,
                                        size_t n);

    template <DType DT>
    API_EXPORT OpRef gen_Const_mD_array(const OpDef &old, typename dtype_traits<DT>::element_type const *vals, size_t n,
                                        size_t m);

    API_EXPORT OpRef gen_Const_int32_common(const OpDef &old, const OutputDef &out_def, const uint8_t *data,
                                            size_t data_len);

    API_EXPORT OpRef gen_Const_float_common(const OpDef &old, const OutputDef &out_def, const uint8_t *data,
                                            size_t data_len);

    template <typename UniqueType> API_EXPORT static ReplFunc first_replacement();
    template <typename UniqueType> API_EXPORT static ReplFunc replacement();
    //typedef OpRef (Replacement::*replacementfn_type)(const OpDef &oldop);
    typedef ReplFunc (*replacementfn_type)();

    template <typename T> static constexpr bool is_Op_type()
    {
        return std::is_constructible<ReplFunc_or_Operand, T>::value;
    }
};

namespace hnnx {

//
// These implement CONSTVAL_INT, CONSTVAL_INT_VALID
// and GETCONST_FLOAT, CONSTVAL_FLOAT_VALID
// The first part of the return value is the result from CONSTVAL_INT(op,idx)
// The second part is the return from CONSTVAL_INT_VALID
API_EXPORT std::pair<NN_INT32_T, bool> getconst_int_impl(GraphPrepare &g, OpDef const &opdef, int index);
API_EXPORT std::pair<NN_INT32_T, bool> getconst_int_impl(GraphPrepare &g, OpDef const &opdef, int index, int index2);
API_EXPORT std::pair<float, bool> getconst_float_impl(GraphPrepare &g, OpDef const &opdef, int index);
API_EXPORT std::pair<float, bool> getconst_float_impl(GraphPrepare &g, OpDef const &opdef, int index, int index2);
API_EXPORT bool producer_for_impl(OpDef const &opdef, const hnnx::opname_tag_t consumer_opname);

class GraphOptInfo;
/*
 * A GraphOptContext ties these all together, along with the 'attempt' method
 */
class GraphOptContext : public Replacement {
  public:
    GraphOptContext(GraphPrepare &g) : Replacement(g) {}
    void set_rule(GraphOptInfo const *const Rule) { curr_rule_info = Rule; }
    API_EXPORT OpId attempt(GraphOptInfo const &, OpDef &oldop);
};

class entire_defopt {
  public:
    hnnx::MatchAst_uptr matcher;
    ReplFuncBool constraint;
    ReplFunc replacement;
    ReplFunc replacement_first;
    void (*register_tiling)(GraphOptInfo *);
};

using get_entire_defopt_t = entire_defopt (*)();

template <typename T> entire_defopt get_entire_defopt();

class GraphOptPass;

// GraphOpInfo: contains pointers to all the specialized methods.
// These are all created as global variables, and they are linked together
// in a linked list; optimization_passes will be populated with pointers
// to them.

class GraphOptInfo {
    friend class GraphOptContext;

    int priority;
    OptimFlags::flags_t flags;
    /** @brief defopt A pointer to the function that generates the
     *  match, constraint, and replacement characterizing this optimization
     */
    get_entire_defopt_t defopt_fn;

  public:
    MatchOp_uptr matchop_ptr; //stores the built matchop.
    ReplFuncBool constraint_func; // function object for the constraint.
    ReplFunc replace_func; // fucntion object for replacement.
    ReplFunc replace_first_func;
    GraphOptInfo const *next_in_pass = nullptr; // next opt for the same opstr in the same pass.

    // note, WITH OPT_DEBUG must be consistent across a build now, otherwise you
    // should get a link error (at least on "add_package_opt").
    char const *debug_filename = nullptr;
    int debug_lineno = 0;

    bool is(unsigned flag) const { return flags & flag; }

  protected:
    // this is done in populate_optimization_map, for all optims.
    virtual void build_matchop()
    {
        entire_defopt defopt;
        defopt = defopt_fn();

        matchop_ptr = MatchBuilder::build_matcher(defopt.matcher);
        // build the constraint function too
        // If the actual constraint function is detected to be the 'always true'
        // function, we leave constraint_func empty.
        ReplFuncBool const cfunc = defopt.constraint;
        int const check = oExp::check_sfunction_bool(cfunc);
        if (check != 1) constraint_func = cfunc;
        replace_func = defopt.replacement;
        replace_first_func = defopt.replacement_first;
        if (defopt.register_tiling && (get_flags() & (OptimFlags::flags_t(OptimFlags::central_autosplit_flag) |
                                                      OptimFlags::flags_t(OptimFlags::central_only_autosplit_flag))))
            defopt.register_tiling(this);
    }

  public:
    API_EXPORT GraphOptInfo(int priority, OptimFlags::flags_t flags_in, get_entire_defopt_t defopt_in);
    GraphOptInfo(int priority, OptimFlags::flags_t flags_in);
    API_EXPORT virtual ~GraphOptInfo() = default;

    // This fills in the optimization map.
    API_EXPORT static void insert_optimization(std::map<int, GraphOptPass> &opt_passes, GraphOptInfo *p);
    API_EXPORT static void populate_package_optimization_map(std::vector<std::unique_ptr<GraphOptInfo>> &opts);

    API_EXPORT inline bool test_constraint(Constraint &cst) const
    {
        // an empty constraint_func means 'always'
        return constraint_func ? constraint_func(cst) : true;
    }
    API_EXPORT GraphOptInfo const *next_optim() const { return next_in_pass; }
    API_EXPORT void set_next_in_pass(const GraphOptInfo *next) { next_in_pass = next; }

    API_EXPORT MatchOpBase &get_matchop() const { return *matchop_ptr.get(); }

    API_EXPORT OptimFlags::flags_t get_flags() const { return flags; }
    API_EXPORT bool has_flags(OptimFlags::flags_t v) const { return (flags & v) != 0; }
    API_EXPORT inline void add_debug_info(char const *const filename, const int lineno)
    {
        debug_filename = filename;
        debug_lineno = lineno;
    }
    API_EXPORT inline char const *get_filename() const { return debug_filename; }
    // get the filename.cc:lineo as a string
    API_EXPORT std::string get_debug_filepos() const;

#ifndef PREPARE_DISABLED
    template <typename T> static void declare_tiling_rule(GraphOptInfo *);

    // Register a tiling rule (defined in tiler.cc)
    static unsigned declare_tiling_rule(unsigned dim, const char *Var, GraphOptInfo *, const char *filename,
                                        unsigned lineno);
#endif // PREPARE_DISABLED
    ReplFunc get_replacement() const { return replace_func; }
    ReplFunc get_replacement_first() const { return replace_first_func; }
    API_EXPORT int get_priority() const { return priority; }
#ifndef PREPAREA_DISABLED
    void set_priority(int new_priority) const { const_cast<GraphOptInfo *>(this)->priority = new_priority; }
#endif
};

#ifndef DEF_OPT_COMPILE
#define DEF_AUTOSPLIT_COMMON(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, dim, var, CHUNKSIZE, REPLACE)                 \
    template <> hnnx::MatchAst_uptr MatchBuilder::matcher<UNIQUE_TYPE>() { return MATCHCODE; }                         \
    template <> ReplFuncBool Constraint::constraint<UNIQUE_TYPE>()                                                     \
    {                                                                                                                  \
        using namespace oExp_for_cst;                                                                                  \
        using oExp::INT;                                                                                               \
        using oExp::UINT;                                                                                              \
        auto result = oExp::wrap_param_to<bool>(                                                                       \
                AND(CONSTRAINTCODE, OR(OPTION_BOOL("ignore_chunksize"), GT(DIM_OF("*", dim), CHUNKSIZE))));            \
        return oExp::wrap_as_function<bool>(result);                                                                   \
    }                                                                                                                  \
    template <> ReplFunc Replacement::replacement<UNIQUE_TYPE>()                                                       \
    {                                                                                                                  \
        using namespace oExp_for_repl;                                                                                 \
        using oExp::INT;                                                                                               \
        using oExp::UINT;                                                                                              \
        pkg_flag = THIS_PKG_NAME_STR;                                                                                  \
        return Operand(AUTOSPLIT(dim, var, CHUNKSIZE, REPLACE));                                                       \
    }                                                                                                                  \
    template <> ReplFunc Replacement::first_replacement<UNIQUE_TYPE>()                                                 \
    {                                                                                                                  \
        using namespace oExp_for_repl;                                                                                 \
        using oExp::INT;                                                                                               \
        using oExp::UINT;                                                                                              \
        pkg_flag = THIS_PKG_NAME_STR;                                                                                  \
        return Operand(REPLACE);                                                                                       \
    }                                                                                                                  \
    template <> inline constexpr hnnx::OptimFlags::flags_t hnnx::OptimFlags::flag_evaluate<UNIQUE_TYPE>() noexcept     \
    {                                                                                                                  \
        return static_cast<uint32_t>(any_rule) | static_cast<uint32_t>(FLAGS);                                         \
    }                                                                                                                  \
    template <> void hnnx::GraphOptInfo::declare_tiling_rule<UNIQUE_TYPE>(GraphOptInfo * info)                         \
    {                                                                                                                  \
        declare_tiling_rule(dim, var, info, __FILE__, __LINE__);                                                       \
    }                                                                                                                  \
    template <> hnnx::entire_defopt hnnx::get_entire_defopt<UNIQUE_TYPE>()                                             \
    {                                                                                                                  \
        return hnnx::entire_defopt{MatchBuilder::matcher<UNIQUE_TYPE>(), Constraint::constraint<UNIQUE_TYPE>(),        \
                                   Replacement::replacement<UNIQUE_TYPE>(),                                            \
                                   Replacement::first_replacement<UNIQUE_TYPE>(),                                      \
                                   GraphOptInfo::declare_tiling_rule<UNIQUE_TYPE>};                                    \
    }                                                                                                                  \
    REGISTER_INTERNAL_PACKAGE_OPT((PRIORITY), hnnx::OptimFlags::flag_evaluate<UNIQUE_TYPE>(),                          \
                                  &hnnx::get_entire_defopt<UNIQUE_TYPE>);
#else
#define DEF_AUTOSPLIT_COMMON(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, dim, var, CHUNKSIZE, REPLACE)                 \
    __def_opt__(PRIORITY, FLAGS, MATCHCODE, AND(CONSTRAINTCODE, GT(DIM_OF("*", dim), CHUNKSIZE)),                      \
                AUTOSPLIT(dim, var, CHUNKSIZE, REPLACE))<<<__FILE__, __LINE__>>>
//  ---> the format of this line must agree with the assumption in scripts/rewrite/hash_rule.py
#endif

#define DEF_AUTOSPLIT(PRIORITY, MATCHCODE, CONSTRAINTCODE, dim, var, CHUNKSIZE, REPLACE)                               \
    DEF_AUTOSPLIT_COMMON(PRIORITY, 0, MATCHCODE, CONSTRAINTCODE, dim, var, CHUNKSIZE, REPLACE)
#define DEF_AUTOSPLITIM(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, dim, var, CHUNKSIZE, REPLACE)                      \
    DEF_AUTOSPLIT_COMMON(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, dim, var, CHUNKSIZE, REPLACE)
#define DEF_AUTOSPLIT_ORDERED(PRIORITY, MATCHCODE, CONSTRAINTCODE, dim, var, CHUNKSIZE, REPLACE)                       \
    DEF_AUTOSPLIT_COMMON(PRIORITY, ordered_autosplit_flag, MATCHCODE, CONSTRAINTCODE, dim, var, CHUNKSIZE, REPLACE)
#define DEF_AUTOSPLIT_TYPICAL(PRIORITY, OPSTR, ARITY, dim, CHUNKSIZE)                                                  \
    DEF_AUTOSPLIT_COMMON(PRIORITY, central_autosplit_flag, OpVarIn(OPSTR), OK, dim, "I", CHUNKSIZE,                    \
                         OP_ITER(Op(OPSTR), "J", 0, INPUTS_OF("*"),                                                    \
                                 SELECT(GE(SPLIT_START("J"), ARITY), ITER_INPUT_OF("*", "J"),                          \
                                        TYPICAL_SLICE(ITER_INPUT_OF("*", "J"), "I"))))

// This class organizes the rules that which
// are part of the same optimization pass (priority, phase,...)
// These are grouped into collections which have the same root
// type in the match. For each group, we maintain a vector
// the rules in the order in which they should be attempted.
// This vector is augmented with additional information
// that guides selection of the next rule to be attempted based
// on previous information about the match.
class GraphOptPass {

    // This type is the type of the byte codes used in the
    // matching enging.
    using code_t = unsigned short;
    // This structure holds the vector of rules with
    // a common priority and root operator type.
    struct MatcherState {
        std::vector<const GraphOptInfo *> rules;
        std::vector<code_t> codes; // byte-codes to drive match checking
        std::vector<opname_tag_t> opstrs; // opstr values needed in operand matches
        API_EXPORT int dump() const;
    };
    class StateBuilder;

    int priority; // common priority (pass number, phase) for this pass.
    OptimFlags::flags_t flags; // 'or' of certain flags in the whole pass
    // use a minhash_noerase for the rules, if we can:
    using rules_map_t =
            std::conditional_t<std::is_same_v<opname_tag_t, string_tag_t>, minihash_noerase<opname_tag_t, MatcherState>,
                               std::map<opname_tag_t, MatcherState>>;
    // Map of type level match name to associated MatcherState
    rules_map_t rules;
    // for each name in the rules, bit  find_opname_hash(name)&63
    // is set in set_bitmap, so we don't even need to probe the map
    // unless we see that bit.
    uint64_t set_bitmap;

    // hash an opstr to a single-bit bit-mask.
    static uint64_t hash(hnnx::opname_tag_t opstr) { return uint64_t(1) << (find_opname_hash(opstr) & 63); }

    // This class provides iteration over the matche returning each candidate.
    // A nullptr in "current" is a sentinel for no more rules to attempt.
    class MatchIterator {
        MatchOpState &matchop_state; // State carried between match attempts
        const MatcherState *matcher = nullptr; // the rules and byte codes for matching
        unsigned state = 0; // the current state of the match
        const GraphOptInfo *current = nullptr; // the current rule

        API_EXPORT void advance(); // advance to the next rule and update state
        API_EXPORT void advance_select(); // advance to the next rule by testing an input operand

      public:
        MatchIterator(MatchOpState &matchop_state, const MatcherState &matcher, unsigned state)
            : matchop_state(matchop_state), matcher(&matcher), state(state)
        {
            advance();
        }
        // This constructor is used for "end" iterators and just sets current to nullptr
        MatchIterator(MatchOpState &matchop_state) : matchop_state(matchop_state) {}

        // These operators are intended only for use in range-for constructs
        const GraphOptInfo &operator*() { return *current; }
        bool operator!=(const MatchIterator &other) { return current != other.current; }
        void operator++() { advance(); }
    };

  public:
    explicit GraphOptPass(int pri) : priority(pri), flags(0), set_bitmap(0) {}
    GraphOptPass(GraphOptPass &&) = default;

    // Add a rule in evaluation order...
    API_EXPORT void add_optim(GraphOptInfo *p);

    // Build the codes and opstrs for each MatcherState after
    // all rules have been added.
    API_EXPORT void build_matchers();

    // return the priority for thi pass
    API_EXPORT int get_priority() const { return priority; }
    // return the combined flags for rules in this pass
    API_EXPORT OptimFlags::flags_t get_flags() const { return flags; }

    // used in introspect.cc only
    API_EXPORT const rules_map_t &get_rules() const { return rules; }

    // This instance is used whenever there are no matches to
    // the root of an operator
    API_EXPORT_IMPORT static MatcherState empty_matcher;

    // Return the matcher state associated with rules that
    // might match opdef.
    API_EXPORT const MatcherState &get_optims(OpDef const *opdef) const
    {
        // avoid the map if the the filter test failes...
        if (not(set_bitmap & hash(opdef->opstr))) return empty_matcher;
        auto iter = rules.find(opdef->opstr);
        return (iter == rules.end()) ? empty_matcher : iter->second;
    }

    // This class is just an adapter so we can return information
    // about a rule in a form that is suitable for use in a range for.
    class RuleList {
        MatchOpState &matchop_state;
        const MatcherState &state;

      public:
        RuleList(MatchOpState &matchop_state, const MatcherState &state) : matchop_state(matchop_state), state(state) {}
        API_EXPORT MatchIterator begin() const noexcept { return MatchIterator(matchop_state, state, 0); }
        API_EXPORT MatchIterator end() const noexcept { return MatchIterator(matchop_state); }
    };

    // Return the rules which might match 'opdef' using 'matchop_state'
    // to cachine opdef looksups.
    API_EXPORT RuleList optims(MatchOpState &matchop_state, const OpDef *opdef) const
    {
        matchop_state.matched_opdef[0] = opdef;
        return RuleList(matchop_state, get_optims(opdef));
    }
};

} // namespace hnnx

POP_VISIBILITY()

#include "oexpr_post.h"

//
//

PUSH_VISIBILITY(default)

namespace hnnx {

API_EXPORT std::map<int, GraphOptPass> &get_optimization_passes();

API_EXPORT std::map<std::string, std::vector<std::unique_ptr<GraphOptInfo>> *> &get_pkg_opt_tmp_map();

API_EXPORT void add_package_opt(std::vector<std::unique_ptr<GraphOptInfo>> &opts, int priority,
                                OptimFlags::flags_t flags_in, get_entire_defopt_t defopt_in, char const *const fname,
                                const int lineno);
// This entry is only for backwards ABI compatibility for exising op packages
// compiled when fname and line number were not in the default build.
API_EXPORT void add_package_opt(std::vector<std::unique_ptr<GraphOptInfo>> &opts, int priority,
                                OptimFlags::flags_t flags_in, get_entire_defopt_t defopt_in);

API_EXPORT std::string get_opname_with_default_pkg_prefix(char const *opname);

} // namespace hnnx

POP_VISIBILITY()

#define INIT_PACKAGE_OPTIMIZATION_DEF()                                                                                \
    API_HIDDEN std::vector<std::unique_ptr<hnnx::GraphOptInfo>> &current_package_opts_storage_vec_func()               \
    {                                                                                                                  \
        static std::vector<std::unique_ptr<hnnx::GraphOptInfo>> optv;                                                  \
        return optv;                                                                                                   \
    }                                                                                                                  \
    extern "C" {                                                                                                       \
    void clearPackageOptStorageVecFunc() { current_package_opts_storage_vec_func().clear(); }                          \
    }

#define DECLARE_PACKAGE_OPTIMIZATION_DEF()                                                                             \
    API_HIDDEN std::vector<std::unique_ptr<hnnx::GraphOptInfo>> &current_package_opts_storage_vec_func();

#define REGISTER_EXTERNAL_PACKAGE_OPT(PRIORITY, FLAGS, DEFOPT) APPEND_REG_OPT_ELEM(PRIORITY, FLAGS, DEFOPT, __LINE__)

#define REGISTER_INTERNAL_PACKAGE_OPT(PRIORITY, FLAGS, DEFOPT) APPEND_REG_OPT_ELEM(PRIORITY, FLAGS, DEFOPT, __LINE__)

#define DEF_PACKAGE_OPTIMIZATION(PRIORITY, MATCHCODE, CONSTRAINTCODE, REPLACECODE)                                     \
    DEF_PACKAGE_OPTIMIZATION_WITH_FLAGS(PRIORITY, 0, MATCHCODE, CONSTRAINTCODE, REPLACECODE)

#define DEF_PACKAGE_OPTIMIZATION_WITH_FLAGS(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)                   \
    DEF_PACKAGE_OPTIMIZATION_COMMON(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)                           \
    REGISTER_EXTERNAL_PACKAGE_OPT((PRIORITY), hnnx::OptimFlags::flag_evaluate<UNIQUE_TYPE>(),                          \
                                  &hnnx::get_entire_defopt<UNIQUE_TYPE>);

#define DEF_INTERNAL_PACKAGE_OPTIMIZATION(PRIORITY, MATCHCODE, CONSTRAINTCODE, REPLACECODE)                            \
    DEF_INTERNAL_PACKAGE_OPTIMIZATION_WITH_FLAGS(PRIORITY, 0, MATCHCODE, CONSTRAINTCODE, REPLACECODE)

#define DEF_INTERNAL_PACKAGE_OPTIMIZATION_WITH_FLAGS(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)          \
    DEF_PACKAGE_OPTIMIZATION_COMMON(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)                           \
    REGISTER_INTERNAL_PACKAGE_OPT((PRIORITY), hnnx::OptimFlags::flag_evaluate<UNIQUE_TYPE>(),                          \
                                  &hnnx::get_entire_defopt<UNIQUE_TYPE>);

#ifndef DEF_OPT_COMPILE
#define DEF_PACKAGE_OPTIMIZATION_COMMON(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)                       \
    template <> [[gnu::always_inline, gnu::cold]] hnnx::MatchAst_uptr MatchBuilder::matcher<UNIQUE_TYPE>()             \
    {                                                                                                                  \
        return MATCHCODE;                                                                                              \
    }                                                                                                                  \
    template <> [[gnu::always_inline, gnu::cold]] ReplFuncBool Constraint::constraint<UNIQUE_TYPE>()                   \
    {                                                                                                                  \
        using namespace oExp_for_cst;                                                                                  \
        using oExp::INT;                                                                                               \
        using oExp::UINT;                                                                                              \
        auto result = oExp::wrap_param_to<bool>(CONSTRAINTCODE);                                                       \
        return oExp::wrap_as_function<bool>(result);                                                                   \
    }                                                                                                                  \
    template <> [[gnu::always_inline, gnu::cold]] ReplFunc Replacement::replacement<UNIQUE_TYPE>()                     \
    {                                                                                                                  \
        using namespace oExp_for_repl;                                                                                 \
        using oExp::INT;                                                                                               \
        using oExp::UINT;                                                                                              \
        pkg_flag = THIS_PKG_NAME_STR;                                                                                  \
        return Operand(REPLACECODE);                                                                                   \
    }                                                                                                                  \
    template <> inline constexpr hnnx::OptimFlags::flags_t hnnx::OptimFlags::flag_evaluate<UNIQUE_TYPE>() noexcept     \
    {                                                                                                                  \
        return static_cast<uint32_t>(any_rule) | static_cast<uint32_t>(FLAGS);                                         \
    }                                                                                                                  \
    template <> hnnx::entire_defopt hnnx::get_entire_defopt<UNIQUE_TYPE>()                                             \
    {                                                                                                                  \
        return hnnx::entire_defopt{MatchBuilder::matcher<UNIQUE_TYPE>(),                                               \
                                   Constraint::constraint<UNIQUE_TYPE>(),                                              \
                                   Replacement::replacement<UNIQUE_TYPE>(),                                            \
                                   {},                                                                                 \
                                   nullptr};                                                                           \
    }
#else
#define DEF_PACKAGE_OPTIMIZATION_COMMON(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)                       \
    __def_opt__(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)<<<__FILE__, __LINE__>>>
//  ---> the format of this line must agree with the assumption in scripts/rewrite/hash_rule.py
#endif

#define REGISTER_PACKAGE_OPTIMIZATIONS()                                                                               \
    {                                                                                                                  \
        auto &pkg_opt_map = hnnx::get_pkg_opt_tmp_map(); /* package registration map */                                \
        auto [iter, ok] = pkg_opt_map.try_emplace(std::string(THIS_PKG_NAME_STR),                                      \
                                                  nullptr); /*see if we can insert an empty one */                     \
        if (ok) iter->second = &current_package_opts_storage_vec_func();                                               \
    } /* if so, replace it with this */

#define DEF_OPTIM(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)                                             \
    DEF_INTERNAL_PACKAGE_OPTIMIZATION_WITH_FLAGS(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)

#define DEF_OPT(PRIORITY, MATCHCODE, CONSTRAINTCODE, REPLACECODE)                                                      \
    DEF_INTERNAL_PACKAGE_OPTIMIZATION(PRIORITY, MATCHCODE, CONSTRAINTCODE, REPLACECODE)

#define FROM_DEFAULT_PACKAGE(OP) hnnx::get_opname_with_default_pkg_prefix(OP).c_str()

DECLARE_PACKAGE_OPTIMIZATION_DEF()

// [DEPRECATED] Old Pass Phases
// see docs/def_opt_migration.md
// #define BEGIN         0
// #define GRAPH_CLEANUP 100
// #define PRE_QNN       500
// #define QNN           1000
// #define EARLY         2000
// #define MIDDLE        3000
// #define LATE          4000

// New Pass Phases
// see docs/def_opt_migration.md to understand how DEF_OPT
// rules were initially put into these ranges.

// Anything that we need to do before we start optimizing things away
#define PRE_OPTIMIZATION 50

// Rewriting that needs to happen to clean up to prepare for translation
#define CLEANUP_GRAPH 100

// Other rewriting that needs to occur to prepare for translation or avoid special cases
#define PRE_TRANSLATE 1000

// Translate from upper level op definitions to our internal ops and op patterns.
// This was called "QNN" before
#define TRANSLATE 2000

// Any rules that need to run fairly early to figure out what's going on in the graph
#define ANALYSIS 3000

// Fixes for quantization in the graph
#define QUANT_FIXES 4000

// Replace ops with other ops to simplify the graph, before dimension reshaping.
// Some of "EARLY" goes here.  Often fission and fusion will go here.
#define PRE_RESHAPE_OP_SIMPLIFY 5000

// Reshaping spatial dimension to help performance
#define SPATIAL_RESHAPE 6000

// Exchanging space and depth to help performance
#define SPACE_DEPTH 7000

// Replace ops with other ops to simplify the graph, post dimension reshaping.
// A lot of "EARLY" goes here.  Often fission and fusion will go here.
#define POST_RESHAPE_OP_SIMPLIFY 8000

// Anything that needs to happen before tiling
#define PRE_TILING 10000

// Tiling large ops to make them smaller
#define TILING 11000

// This is the phase that central tiling logically runs at
// TODO(charcall) remove this and just run before TILE_CLEANUP
#define CENTRAL_TILING 11900

// Clean up the graph after tiling.  Slice-of-concat, etc.
#define TILE_CLEANUP 12000

// Passes that should happen after tiling
#define POST_TILING 13000

// Graph rewriting for actual op implementations, specializations, and their requirements.
// What was once LATE+0 through LATE+9 is often this kind of thing.
#define HARD_OPS 20000

// Move data to TCM and remove unnecessary data moves.
// Perhaps, eventually accomplished by different infrastructure.
#define BEFORE_TCM_MIGRATION 20800

// Move data to TCM and remove unnecessary data moves.
// Perhaps, eventually accomplished by different infrastructure.
#define TCM_MIGRATION 21000

// Passes that run after TCM migration ops are inserted
#define POST_TCM 22000

// Anything that needs to be simplified at the very end
#define FINAL_CLEANUP 23000

// do we need to do any aux graph specific cleanup
#define AUX_CLEANUP 25000

// Some DEF_AUTOSPLIT rules are only used by the central tiler
// and the chunk size is not used. We give a symbolic name for this case
#define CHUNK_NOT_USED 0

// LEGACY support for OLD pass phase names
// EXTERNAL use only in OpPackages and at the QNN-level
// see docs/def_opt_migration.md
// DO NOT USE THESE ON HTP CORE!
// HTP Core developers should read docs/def_opt_migration.md
#ifndef DISABLE_LEGACY_PASS_SYMBOLS
#define BEGIN         0 // (CLEANUP_GRAPH)
#define GRAPH_CLEANUP 50 // (CLEANUP_GRAPH + 50)
#define PRE_QNN       1050 // (PRE_TRANSLATE + 50)
#define QNN           2050 // (TRANSLATE + 50)
#define EARLY         3050 // (ANALYSIS + 50)
#define MIDDLE        20050 // (HARD_OPS + 50)
#define LATE          21050 // (TCM_MIGRATION + 50)
// For Upcoming centralized LAYOUT_AND_PLACEMENT changes
#define LAYOUT_AND_PLACEMENT 21100 // (TCM_MIGRATION + 100)
#endif

#define GET_DILVALUE(arg1, arg2, ...) arg2

#define TYPICAL_CONV_SLICE(in, tag, stride, filt_taps, ...)                                                            \
    AUTOSPLIT_SLICE(in, AUTOSPLIT_SHAPEFN_APPLY(conv_valid_split_start, tag, in, stride),                              \
                    AUTOSPLIT_SHAPEFN_APPLY(conv_valid_split_size, tag, in, stride, filt_taps,                         \
                                            GET_DILVALUE(dummy, ##__VA_ARGS__, 1)))

#ifndef DTP_COMPILE
#define DEF_TENSOR_PROPERTIES(...)                                                                                     \
    namespace DefProperties {                                                                                          \
    [[maybe_unused]] static bool CTRICKS_PASTER(opdef_proprety, __LINE__) =                                            \
            hnnx::register_tensor_properties(THIS_PKG_NAME_STR, TensorInfoBuilder(THIS_PKG_NAME_STR, __VA_ARGS__));    \
    }
#else
#define DEF_TENSOR_PROPERTIES(...) __dtp__(__VA_ARGS__)<<<__FILE__, __LINE__>>>
#endif
#else
#define DEF_TENSOR_PROPERTIES(...)
#define DEF_AUTOSPLIT(...)
#define DEF_AUTOSPLITIM(...)
#define DEF_AUTOSPLIT_ORDERED(...)
#define DEF_AUTOSPLIT_TYPICAL(...)
#define DEF_PACKAGE_OPTIMIZATION(PRIORITY, MATCHCODE, CONSTRAINTCODE, REPLACECODE)
#define DEF_PACKAGE_OPTIMIZATION_WITH_FLAGS(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)
#define DEF_OPTIM(PRIORITY, FLAGS, MATCHCODE, CONSTRAINTCODE, REPLACECODE)
#define DEF_OPT(PRIORITY, MATCHCODE, CONSTRAINTCODE, REPLACECODE)
#define INIT_PACKAGE_OPTIMIZATION_DEF()                                                                                \
    /* Provide no-op definition so clearPkgStorage still works */                                                      \
    extern "C" void clearPackageOptStorageVecFunc() {}
#define REGISTER_PACKAGE_OPTIMIZATIONS()
#endif // PREPARE_DISABLED

struct Recompilable_param {
    const Op *op_ptr = nullptr;
    Recompilable_param *next = nullptr;
};

#define COMPILER_FOR_UPDATEABLE_QUANT_WITH_CHECKS(XXF, FUNC, PARA, PRE, POST)                                          \
    template <> constexpr bool has_compile_method<XXF> = true;                                                         \
    template <> struct OpaqueT_FOR<XXF> {                                                                              \
        using type = PARA;                                                                                             \
    };                                                                                                                 \
    template <> bool hnnx::TypicalOpWithCompiler<XXF, PARA>::check_constraint_for_recompile(Graph &graph_in) const     \
    {                                                                                                                  \
        return POST(graph_in, this);                                                                                   \
    }                                                                                                                  \
    template <> hnnx::Executable::ItemType hnnx::TypicalOpWithCompiler<XXF, PARA>::compile(Graph &graph_in) const      \
    {                                                                                                                  \
        static_assert(check_szal());                                                                                   \
        auto [f, v] = FUNC(graph_in, this);                                                                            \
        auto pre_check_res = PRE(graph_in, this);                                                                      \
        Recompilable_param *const recomp_ptr = (pre_check_res) ? (Recompilable_param *)(&this->opaque) : nullptr;      \
        if (!graph_in.recompile_for_updated_quant && pre_check_res) {                                                  \
            recomp_ptr->op_ptr = this;                                                                                 \
            recomp_ptr->next = nullptr;                                                                                \
            hnnx::nn_mutex_lock(&(graph_in.linked_params_lock));                                                       \
            param_list &cur_list = graph_in.linked_params[graph_in.linked_params_cur_ind];                             \
            if (cur_list.head == nullptr) {                                                                            \
                cur_list.head = recomp_ptr;                                                                            \
                cur_list.tail = recomp_ptr;                                                                            \
            } else {                                                                                                   \
                cur_list.tail->next = recomp_ptr;                                                                      \
                cur_list.tail = recomp_ptr;                                                                            \
            }                                                                                                          \
            graph_in.linked_params_cur_ind =                                                                           \
                    (graph_in.linked_params_cur_ind + 1) % graph_in.num_threads_for_recompile;                         \
            hnnx::nn_mutex_unlock(&(graph_in.linked_params_lock));                                                     \
        }                                                                                                              \
        return hnnx::Executable::ItemType(f, v);                                                                       \
    }

template <typename T> bool default_pre_check_for_recompile(Graph &graph_in, T *const op)
{
    return true;
}

// the precheck is useful here for disabling recompile, set the precheck return to false
template <typename T> bool pre_check_for_non_updateable(Graph &graph_in, T *const op)
{
    return false;
}

template <typename T> bool default_post_check_for_recompile(Graph &graph_in, T *const op)
{
    return op->hnnx::Executable::check_constraint_for_recompile(graph_in);
}

// only performs post check
#define COMPILER_FOR_UPDATEABLE_QUANT_WITH_POST(XXF, FUNC, PARA, POST)                                                 \
    COMPILER_FOR_UPDATEABLE_QUANT_WITH_CHECKS(XXF, FUNC, PARA, default_pre_check_for_recompile, POST)

// only performs pre check
#define COMPILER_FOR_UPDATEABLE_QUANT_WITH_PRE(XXF, FUNC, PARA, PRE)                                                   \
    COMPILER_FOR_UPDATEABLE_QUANT_WITH_CHECKS(XXF, FUNC, PARA, PRE, default_post_check_for_recompile)

// performs no check
#define COMPILER_FOR_UPDATEABLE_QUANT(XXF, FUNC, PARA)                                                                 \
    COMPILER_FOR_UPDATEABLE_QUANT_WITH_CHECKS(XXF, FUNC, PARA, default_pre_check_for_recompile,                        \
                                              default_post_check_for_recompile)

#define COMPILER_FOR(XXF, FUNC, PARA)                                                                                  \
    COMPILER_FOR_UPDATEABLE_QUANT_WITH_CHECKS(XXF, FUNC, PARA, pre_check_for_non_updateable,                           \
                                              default_post_check_for_recompile)

#endif // OPTIMIZE_H
