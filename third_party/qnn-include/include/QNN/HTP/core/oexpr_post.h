//==============================================================================
//
// Copyright (c) 2020,2022-2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OEXPR_POST_H_
#define OEXPR_POST_H_

#ifndef OEXPR_H_
#error "?? must include oexpr.h first"
#endif

#ifdef WITH_OPT_DEBUG
#include <sstream>
#endif
#include "build_options_pub.h"

#include "weak_linkage.h"
#include "macros_attribute.h"
#ifndef PREPARE_DISABLED
PUSH_VISIBILITY(default)

namespace oExp {

template <Variant VAR, size_t IDX, typename OPERANDS>
inline bool ALWAYSINLINE reduce_logop(ECtx &e, OPERANDS const &operands)
{
    static constexpr size_t N = std::tuple_size_v<OPERANDS>;
#ifdef WITH_OPT_DEBUG
    using TraceState = constraint_lib::Constraint::TraceState;
    TraceState save_state;
    if (IDX == 0 and e.curr_rule_info and e.curr_rule_info->is(hnnx::OptimFlags::trace_rule)) {
        save_state = e.trace;
        e.trace.depth += 1;
        e.trace.op = (VAR == Variant::lg_and ? "and" : VAR == Variant::lg_or ? "or" : "xor");
    }
    e.trace.clause = IDX;
    auto result = [&save_state, &e](bool result) {
        if (e.curr_rule_info and e.curr_rule_info->is(hnnx::OptimFlags::trace_rule))
            e.trace_vector.push_back(TraceState{e.trace.depth, IDX, e.trace.op, result});
        if (IDX == 0) e.trace = save_state;
        return result;
    };
#else
    auto result = [](bool r) { return r; };
#endif
    // find the first one.
    bool result_i = std::get<IDX>(operands).eval(e);
    if constexpr (IDX + 1 >= N) { // it's the last one .. we're done
        return result(result_i);
    } else if constexpr (VAR == Variant::lg_xor) {
        // must xor with the rest
        return result_i ^ reduce_logop<VAR, IDX + 1, OPERANDS>(e, operands);
    } else {
        if constexpr (VAR == Variant::lg_and) {
            if (!result_i) return result(false); // short-cut AND if false
        } else if constexpr (VAR == Variant::lg_or) {
            if (result_i) return result(true); // short-cut OR if true
        } else {
            static_assert(false && IDX, "bad variant!?");
        }
        // evaluate the rest.
        bool next = reduce_logop<VAR, IDX + 1, OPERANDS>(e, operands);
#ifdef WITH_OPT_DEBUG
        if (IDX == 0) e.trace = save_state;
#endif
        return next;
    }
}

// opdef_accessor is a friend class of Constraint;
// it forms a bridge to the operand map and op_def map; all
// oExpr use this to map operand tags to OpRef, OpDef and OutputDef
class opdef_accessor {
  public:
    API_EXPORT static OpRef lookup_operand(ECtx &e, hnnx::operand_tag_parm_t);
    API_EXPORT static OpDef const &get_opdef(ECtx &e, hnnx::operand_tag_parm_t optag);
    API_EXPORT static OpDef const &get_opdef(ECtx &e, OpRef);

    // this is defined below in same file - wrapper to select a ger_opdef by type.
    template <typename OEXPR_TYPE> static inline OpDef const &get_opdef_oexpr(ECtx &e, OEXPR_TYPE const &oexp);

    API_EXPORT static OutputDef const &get_outputdef(ECtx &e, hnnx::operand_tag_parm_t optag);
    API_EXPORT static OutputDef const &get_outputdef(ECtx &e, OpRef);
    API_EXPORT static Split_Context const &lookup_split(ECtx &e, hnnx::split_context_tag_t const &tag);

    API_EXPORT static OpRef get_input_of(ECtx &e, OpDef const *, int idx);
    API_EXPORT static OpRef get_output_of(ECtx &e, OpRef, int idx);
    template <typename T> API_EXPORT static T get_option(ECtx &e, hnnx::opname_tag_parm_t name);
    static void show_debug_message(ECtx &e, char const *why, char const *str)
    {
        if constexpr (build_options_pub::DefOptLog)
            const_cast<constraint_lib::Constraint &>(e).show_debug_message(why, str);
    }
};

//
// this can be used to extract the V and T from an opexpr<V,T>
// opexpr< opexpr<V,T>>::variant -> V
// opexpr< opexpr<V,T>>::type -> T

template <typename DUMMY> struct opexpr_types {
};
template <OpVnt V, typename T> struct opexpr_types<opexpr<V, T>> {
    static constexpr OpVnt variant = V;
    using parmtype = T;
};

/////////////////////////////////////////
// an opexpr which just contains a value.
/////////////////////////////////////////
template <> class opexpr<OpVnt::parm, void> : public opdef_accessor {
    const hnnx::operand_tag_t m_optag;

  public:
    opexpr(hnnx::operand_tag_parm_t optag) : m_optag(optag) {}
    OpRef eval(ECtx &e) const { return lookup_operand(e, m_optag); }
    hnnx::operand_tag_parm_t get_optag() const { return m_optag; }
};

template <typename OEXPR_TYPE> inline OpDef const &opdef_accessor::get_opdef_oexpr(ECtx &e, OEXPR_TYPE const &oexp)
{
    if constexpr (opexpr_types<OEXPR_TYPE>::variant == OpVnt::parm) {
        return get_opdef(e, oexp.get_optag());
    } else {
        return get_opdef(e, oexp.eval(e));
    }
}

// 'wrap_opexpr( something )'
// - turns string constant or operand_tag_t into opexpr<OpVnt::parm,void>
// - opexpr input is returned unchanged
// - others are invalid.
//
template <typename T> struct opwrapper_helper {
    static_assert(false && sizeof(T), "wrap_opexpr instantiated on unsupported type");
};

// wrapping a char const *
template <> struct opwrapper_helper<char const *> {
    static auto wrap(char const *p) { return opexpr<OpVnt::parm, void>(p); }
};
template <int N> struct opwrapper_helper<char const[N]> {
    static auto wrap(char const *p) { return opexpr<OpVnt::parm, void>(p); }
};

// wrapping an operand tag
template <> struct opwrapper_helper<hnnx::operand_tag_t> {
    static auto wrap(hnnx::operand_tag_parm_t p) { return opexpr<OpVnt::parm, void>(p); }
};
// wrapping an opexpr
template <OpVnt V, typename T> struct opwrapper_helper<opexpr<V, T>> {
    static constexpr auto wrap(opexpr<V, T> const &p) { return p; }
};

template <typename T> inline constexpr auto wrap_opexpr(T &&p)
{
    return opwrapper_helper<std::remove_reference_t<T>>::wrap(std::forward<T>(p));
}

/////////////////////////////////////////
// opexpr for INPUT_OF
/////////////////////////////////////////

template <typename OPA, typename EXPRB> class opexpr<OpVnt::input_of, std::tuple<OPA, EXPRB>> : public opdef_accessor {
    const OPA m_op;
    const EXPRB m_idx;

  public:
    constexpr opexpr(OPA const &a, EXPRB const &b) : m_op(a), m_idx(b) {}
    OpRef eval(ECtx &e) const
    {
        OpDef const &opd = get_opdef_oexpr<OPA>(e, m_op);
        int idx = m_idx.eval(e);
        return get_input_of(e, &opd, idx);
    }
};
/// \ingroup ingroupOptConstraint
/// @brief INPUT_OF("operand", index) - select the specified input of an op.
///
/// index must be in range 0 ... n-1, where n is the number of input the Op actually has.
///
///

template <typename TOP, typename TEXPR> auto INPUT_OF(TOP &&opa, TEXPR &&exprb)
{
    auto wa = wrap_opexpr(std::forward<TOP>(opa));
    auto wb = wrap_param_to<int>(std::forward<TEXPR>(exprb));
    return opexpr<OpVnt::input_of, std::tuple<decltype(wa), decltype(wb)>>(wa, wb);
}
/////////////////////////////////////////
// opexpr for OUTPUT_OF
/////////////////////////////////////////

template <typename OPA, typename EXPRB> class opexpr<OpVnt::output_of, std::tuple<OPA, EXPRB>> : public opdef_accessor {
    const OPA m_op;
    const EXPRB m_idx;

  public:
    constexpr opexpr(OPA const &a, EXPRB const &b) : m_op(a), m_idx(b) {}
    OpRef eval(ECtx &e) const
    {
        OpRef opr = m_op.eval(e);
        int idx = m_idx.eval(e);
        return get_output_of(e, opr, idx);
    }
};

/// \ingroup ingroupOptConstraint
/// @brief OUTPUT_OF("operand", index) - select the specified output of a multi-output Op.
///
/// The "operand" must refer to either (a) a multi-output Op, with at least index+1 outputs;
/// or (b) one of the $Out nodes of such an Op. The result is the $Out node for the output selected
/// by 'index' (if index=0, "operand" can refer to an single-output Op, in which case you just get
/// that Op).
///

template <typename TOP, typename TEXPR> auto OUTPUT_OF(TOP &&opa, TEXPR &&exprb)
{
    auto wa = wrap_opexpr(std::forward<TOP>(opa));
    auto wb = wrap_param_to<int>(std::forward<TEXPR>(exprb));
    return opexpr<OpVnt::output_of, std::tuple<decltype(wa), decltype(wb)>>(wa, wb);
}

////////////////////////// implement Config //////////////////////////////

template <typename T> class expr<Variant::config, T> : public opdef_accessor {
    const hnnx::operand_tag_t m_option_name;

  public:
    typedef T otype;
    expr(char const *optname) : m_option_name(optname) {}
    otype eval(ECtx &ectx) const { return opdef_accessor::get_option<T>(ectx, m_option_name); }
};
// specialize for bool: read as int, convert to bool.
template <> class expr<Variant::config, bool> : public opdef_accessor {
    const hnnx::operand_tag_t m_option_name;

  public:
    typedef bool otype;
    expr(char const *optname) : m_option_name(optname) {}
    otype eval(ECtx &ectx) const { return opdef_accessor::get_option<int>(ectx, m_option_name) != 0; }
};

////////////////////////////// properties of OpDef //////////////////////

// property extractors, implement RANK_OF, STEPSIZE_OF, DIM_OF etc.
//
// All of these have two 'eval' methods:
//    eval( ECtx &, operand_tag_parm_t parmtag)-> OpRef
//    eval( ECtx &, Opref oref )-> OpRef
//
// The first one is used when the extractor is applied directly to a named
// parameter as in DTYPE_OF("X"); the second is used in the general case
// e.g DTYPE_OF(INPUT_OF("X", SUB(INPUTS_OF("X"),1)))
//
// The reason for this: mapping from parmtag to OpDef & is considerably faster
// than the two-step mapping parmtag -> OpRef -> OpDef &
// This is due to a caching mechanism in the Match object. We don't want
// to defeat this speedup for the more common case.
// Note that the methods get_outputdef etc of opdef_accessor are overloaded for
// both cases, so the two eval methods tend to look the same.
//
//

struct property_extract_rank : private opdef_accessor {
    typedef size_t otype;
    inline otype eval(ECtx &e, hnnx::operand_tag_parm_t ptag) const { return get_outputdef(e, ptag).rank; }
    inline otype eval(ECtx &e, OpRef oref) const { return get_outputdef(e, oref).rank; }
};
struct property_extract_dtype : private opdef_accessor {
    typedef DType otype;
    inline otype eval(ECtx &e, hnnx::operand_tag_parm_t ptag) const { return get_outputdef(e, ptag).dtype; }
    inline otype eval(ECtx &e, OpRef oref) const { return get_outputdef(e, oref).dtype; }
};
struct property_extract_zero_offset : private opdef_accessor {
    typedef int otype;
    inline otype eval(ECtx &e, hnnx::operand_tag_parm_t ptag) const
    {
        auto const &od = get_outputdef(e, ptag);
        return (od.dtype == DType::Multi) ? 0 : od.zero_offset;
    }
    inline otype eval(ECtx &e, OpRef oref) const
    {
        auto const &od = get_outputdef(e, oref);
        return (od.dtype == DType::Multi) ? 0 : od.zero_offset;
    }
};
struct property_extract_n_outputs : private opdef_accessor {
    typedef int otype;
    API_EXPORT otype eval(ECtx &e, hnnx::operand_tag_parm_t ptag) const; // in oexpr.cc
    API_EXPORT otype eval(ECtx &e, OpRef oref) const; // in oexpr.cc
  private:
    API_EXPORT static otype eval_common(ECtx &e, OpDef const *od); // in oexpr.cc
};

struct property_extract_elementsize : private opdef_accessor {
    typedef size_t otype;
    API_EXPORT otype eval(ECtx &e, hnnx::operand_tag_parm_t optag) const; // in oexpr.cc
    API_EXPORT otype eval(ECtx &e, OpRef oref) const; // in oexpr.cc
};

struct property_extract_stepsize : private opdef_accessor {
    typedef float otype;
    inline otype eval(ECtx &e, hnnx::operand_tag_parm_t ptag) const { return get_outputdef(e, ptag).stepsize; }
    inline otype eval(ECtx &e, OpRef oref) const { return get_outputdef(e, oref).stepsize; }
};

struct property_extract_n_inputs : private opdef_accessor {
    typedef size_t otype;
    inline otype eval(ECtx &e, hnnx::operand_tag_parm_t ptag) const { return get_opdef(e, ptag).n_inputs(); }
    inline otype eval(ECtx &e, OpRef oref) const { return get_opdef(e, oref).n_inputs(); }
};

// can only be used embedded in dim_extractor
struct property_extract_dim : protected opdef_accessor {
    typedef size_t otype;
    inline otype eval(ECtx &e, hnnx::operand_tag_parm_t ptag, int i) const
    {
        OutputDef const &odef = get_outputdef(e, ptag);
        if ((unsigned)i > (unsigned)odef.rank) return 0;
        return odef.max_sizes[i];
    }
    inline otype eval(ECtx &e, OpRef oref, int i) const
    {
        OutputDef const &odef = get_outputdef(e, oref);
        if ((unsigned)i > (unsigned)odef.rank) return 0;
        return odef.max_sizes[i];
    }
};
template <typename ITYPE> struct dim_extractor : public property_extract_dim {
    ITYPE m_iexpr;
    inline size_t eval(ECtx &e, hnnx::operand_tag_parm_t ptag) const
    {
        int i = m_iexpr.eval(e);
        return property_extract_dim::eval(e, ptag, i);
    }
    inline size_t eval(ECtx &e, OpRef oref) const
    {
        int i = m_iexpr.eval(e);
        return property_extract_dim::eval(e, oref, i);
    }
    constexpr dim_extractor(ITYPE const &iexpr) : m_iexpr(wrap_param_to<int>(iexpr)) {}
};
template <> struct dim_extractor<int> : public property_extract_dim {
    int m_iexpr;
    inline size_t eval(ECtx &e, hnnx::operand_tag_parm_t ptag) const
    {
        int const i = m_iexpr;
        return property_extract_dim::eval(e, ptag, i);
    }
    inline size_t eval(ECtx &e, OpRef oref) const
    {
        int const i = m_iexpr;
        return property_extract_dim::eval(e, oref, i);
    }
    constexpr dim_extractor(int i) : m_iexpr(i) {}
};

struct property_data_size : private opdef_accessor {
    typedef int64_t otype;
    API_EXPORT int eval(ECtx &e, const OpDef &op) const;
    API_EXPORT int eval(ECtx &e, hnnx::operand_tag_parm_t ptag) const;
    API_EXPORT int eval(ECtx &e, OpRef oref) const;
};

template <typename OPEX, typename PROPEXT> class expr<Variant::property, std::tuple<OPEX, PROPEXT>> {
    const OPEX m_opexpr;
    PROPEXT m_propex;

  public:
    typedef typename PROPEXT::otype otype;
    constexpr expr(OPEX const &opexp) : m_opexpr(opexp) {}
    // this is for 'dim', to apply the index parameter
    template <typename T>
    constexpr expr(OPEX const &opexp, T &&xparm) : m_opexpr(opexp), m_propex(std::forward<T>(xparm))
    {
    }

    otype eval(ECtx &e) const
    {
        if constexpr (opexpr_types<OPEX>::variant == OpVnt::parm) {
            // use the operand tag directly.
            return m_propex.eval(e, m_opexpr.get_optag());
        } else {
            // get an OpRef by evaluating the m_opexpr
            return m_propex.eval(e, m_opexpr.eval(e));
        }
    }
};

/// \addtogroup OptConstraint
/// @{

//! RANK_OF("operand") - extract rank of output
template <typename TOP> inline constexpr auto RANK_OF(TOP &&op)
{
    auto op_wrapped = wrap_opexpr(std::forward<TOP>(op));
    return expr<Variant::property, std::tuple<decltype(op_wrapped), property_extract_rank>>(op_wrapped);
}

//! DTYPE_OF("operand") - extract dtype of output
template <typename TOP> inline constexpr auto DTYPE_OF(TOP &&op)
{
    auto op_wrapped = wrap_opexpr(std::forward<TOP>(op));
    return expr<Variant::property, std::tuple<decltype(op_wrapped), property_extract_dtype>>(op_wrapped);
}

//! ELEMENTSIZE_OF("operand") - extract dtype of output, return element size.
template <typename TOP> inline constexpr auto ELEMENTSIZE_OF(TOP &&op)
{
    auto op_wrapped = wrap_opexpr(std::forward<TOP>(op));
    return expr<Variant::property, std::tuple<decltype(op_wrapped), property_extract_elementsize>>(op_wrapped);
}

//! ZERO_OFFSET_OF("operand") - extract zero_offset of output quantization
template <typename TOP> inline constexpr auto ZERO_OFFSET_OF(TOP &&op)
{
    auto op_wrapped = wrap_opexpr(std::forward<TOP>(op));
    return expr<Variant::property, std::tuple<decltype(op_wrapped), property_extract_zero_offset>>(op_wrapped);
}

//! STEPSIZE_OF("operand") - extract step size of output quantization
template <typename TOP> inline constexpr auto STEPSIZE_OF(TOP &&op)
{
    auto op_wrapped = wrap_opexpr(std::forward<TOP>(op));
    return expr<Variant::property, std::tuple<decltype(op_wrapped), property_extract_stepsize>>(op_wrapped);
}

//! INPUTS_OF("operand") - get the number of inputs of an operand
template <typename TOP> inline constexpr auto INPUTS_OF(TOP &&op)
{
    auto op_wrapped = wrap_opexpr(std::forward<TOP>(op));
    return expr<Variant::property, std::tuple<decltype(op_wrapped), property_extract_n_inputs>>(op_wrapped);
}
//! OUTPUTS_OF("operand")- get the number of outputs of an operand
/// This can be applied to any OpDef, including $Out; for $Out or Multi Opdef
/// it will return the full # of outputs.
template <typename TOP> inline constexpr auto OUTPUTS_OF(TOP &&op)
{
    auto op_wrapped = wrap_opexpr(std::forward<TOP>(op));
    return expr<Variant::property, std::tuple<decltype(op_wrapped), property_extract_n_outputs>>(op_wrapped);
}
//! DIM_OF("operand", idx) - get the size of an output in dimension 'idx'

template <typename TOP, typename T> inline constexpr auto DIM_OF(TOP &&op, T &&index)
{
    auto op_wrapped = wrap_opexpr(std::forward<TOP>(op));
    return expr<Variant::property, std::tuple<decltype(op_wrapped), dim_extractor<std::remove_reference_t<T>>>>(
            op_wrapped, std::forward<T>(index));
}

/// }@

template <typename TOP> inline constexpr auto DATA_SIZE(TOP &&op)
{
    auto op_wrapped = wrap_opexpr(std::forward<TOP>(op));
    return expr<Variant::property, std::tuple<decltype(op_wrapped), property_data_size>>(op_wrapped);
}

struct op_compare_same_op {
    typedef bool otype;
    otype eval(ECtx &e, OpDef const *a, OpDef const *b) const { return a == b; }
};

struct op_compare_same_encoding {
    typedef bool otype;
    API_EXPORT otype eval(ECtx &e, OpDef const *a, OpDef const *b) const; // in oexpr.cc
};

struct op_compare_same_shape {
    typedef bool otype;
    API_EXPORT otype eval(ECtx &e, OpDef const *a, OpDef const *b) const; // in oexpr.cc
};

//
// compare two ops in some way.
//
template <typename OPEXA, typename OPEXB, typename OPCMP>
class expr<Variant::opcompare, std::tuple<OPEXA, OPEXB, OPCMP>> : public property_extract_dim {
    const OPEXA m_opexpra;
    const OPEXB m_opexprb;
    OPCMP m_opcmp;

  public:
    typedef typename OPCMP::otype otype; // probably bool
    expr(OPEXA const &opexpa, OPEXB const &opexpb) : m_opexpra(opexpa), m_opexprb(opexpb) {}

    otype eval(ECtx &e) const
    {
        OpDef const &opa = get_opdef_oexpr<OPEXA>(e, m_opexpra);
        OpDef const &opb = get_opdef_oexpr<OPEXB>(e, m_opexprb);
        return m_opcmp.eval(e, &opa, &opb);
    }
};

//! SAME_OP("operanda", "operandb") - same opid.
//
template <typename TOPA, typename TOPB> inline auto SAME_OP(TOPA &&opa, TOPB &&opb)
{
    auto opa_wrapped = wrap_opexpr(std::forward<TOPA>(opa));
    auto opb_wrapped = wrap_opexpr(std::forward<TOPB>(opb));

    return expr<Variant::opcompare, std::tuple<decltype(opa_wrapped), decltype(opb_wrapped), op_compare_same_op>>(
            opa_wrapped, opb_wrapped);
}

//! SAME_ENCODING("operanda", "operandb") - same dtype, and same quant. when applicable.
//
template <typename TOPA, typename TOPB> inline auto SAME_ENCODING(TOPA &&opa, TOPB &&opb)
{
    auto opa_wrapped = wrap_opexpr(std::forward<TOPA>(opa));
    auto opb_wrapped = wrap_opexpr(std::forward<TOPB>(opb));

    return expr<Variant::opcompare, std::tuple<decltype(opa_wrapped), decltype(opb_wrapped), op_compare_same_encoding>>(
            opa_wrapped, opb_wrapped);
}

//! SAME_SHAPE("operanda", "operandb") - same shape.
//
template <typename TOPA, typename TOPB> inline auto SAME_SHAPE(TOPA &&opa, TOPB &&opb)
{
    auto opa_wrapped = wrap_opexpr(std::forward<TOPA>(opa));
    auto opb_wrapped = wrap_opexpr(std::forward<TOPB>(opb));

    return expr<Variant::opcompare, std::tuple<decltype(opa_wrapped), decltype(opb_wrapped), op_compare_same_shape>>(
            opa_wrapped, opb_wrapped);
}

///////////////////////////// slice dimensions ////////////////////
// The 'expr' contains a tag name, and a pointer-to-member-data
//

template <typename T> // always int...
struct expr<Variant::slicedim, T> : private opdef_accessor {
    hnnx::split_context_tag_t m_tag;
    typedef T otype;
    T const Split_Context::*m_which;
    inline T eval(ECtx &e) const { return lookup_split(e, m_tag).*m_which; }
    expr(hnnx::split_context_tag_t const &tag, int const Split_Context::*which) : m_tag(tag), m_which(which) {}
};

/// \addtogroup OptConstraint
/// @{

//! SPLIT_START("splitname") - get current split start
inline auto SPLIT_START(hnnx::split_context_tag_t spl)
{
    return expr<Variant::slicedim, int>(spl, &Split_Context::start);
}

//! ITER_VAR("splitname") - get current split start value
/// synonym for SPLIT_START (for use in OP_ITER)
inline auto ITER_VAR(hnnx::split_context_tag_t spl)
{
    return expr<Variant::slicedim, int>(spl, &Split_Context::start);
}

//! SPLIT_SIZE("splitname") - get current split size
inline auto SPLIT_SIZE(hnnx::split_context_tag_t spl)
{
    return expr<Variant::slicedim, int>(spl, &Split_Context::size);
}

//! SPLIT_DIM("splitname") - get current split dimension
inline auto SPLIT_DIM(hnnx::split_context_tag_t spl)
{
    return expr<Variant::slicedim, int>(spl, &Split_Context::dim);
}

/// }@

PUSH_VISIBILITY(default)

// function classes for Variant::getconst
//
struct getconst_int {
    typedef NN_INT32_T otype;
    static NN_INT32_T eval(ECtx &e, OpDef const &opdef, int index)
    {
        return hnnx::getconst_int_impl(e.graph(), opdef, index).first;
    }
};
struct getconst_int2 {
    typedef NN_INT32_T otype;
    static NN_INT32_T eval(ECtx &e, OpDef const &opdef, int index, int index2)
    {
        return hnnx::getconst_int_impl(e.graph(), opdef, index, index2).first;
    }
};

struct getconst_int_valid {
    typedef bool otype;
    static bool eval(ECtx &e, OpDef const &opdef, int index)
    {
        return hnnx::getconst_int_impl(e.graph(), opdef, index).second;
    }
};
struct getconst_int2_valid {
    typedef bool otype;
    static bool eval(ECtx &e, OpDef const &opdef, int index, int index2)
    {
        return hnnx::getconst_int_impl(e.graph(), opdef, index, index2).second;
    }
};

struct getconst_float {
    typedef float otype;
    static float eval(ECtx &e, OpDef const &opdef, int index)
    {
        return hnnx::getconst_float_impl(e.graph(), opdef, index).first;
    }
};
struct getconst_float_valid {
    typedef bool otype;
    static bool eval(ECtx &e, OpDef const &opdef, int index)
    {
        return hnnx::getconst_float_impl(e.graph(), opdef, index).second;
    }
};

POP_VISIBILITY()
//
// for CONSTVAL_INT etc
//

template <typename GETCONST, typename OPEXPR, typename IEXPR>
class expr<Variant::getconst, tuple<GETCONST, OPEXPR, IEXPR>> : private opdef_accessor {
    OPEXPR m_opexpr;
    IEXPR m_iexpr;

  public:
    typedef typename GETCONST::otype otype;
    inline otype eval(ECtx &e) const
    {
        OpDef const &opd = get_opdef_oexpr<OPEXPR>(e, m_opexpr);
        int i = m_iexpr.eval(e);
        return GETCONST::eval(e, opd, i);
    }
    constexpr expr(OPEXPR const &oexp, IEXPR const &iexpr) : m_opexpr(oexp), m_iexpr(iexpr) {}
};

template <typename GETCONST, typename OPEXPR, typename IEXPR, typename IEXPR2>
class expr<Variant::getconst, tuple<GETCONST, OPEXPR, IEXPR, IEXPR2>> : private opdef_accessor {
    OPEXPR m_opexpr;
    IEXPR m_iexpr;
    IEXPR2 m_iexpr2;

  public:
    typedef typename GETCONST::otype otype;
    inline otype eval(ECtx &e) const
    {
        OpDef const &opd = get_opdef_oexpr<OPEXPR>(e, m_opexpr);
        int i = m_iexpr.eval(e);
        int i2 = m_iexpr2.eval(e);
        return GETCONST::eval(e, opd, i, i2);
    }
    expr(OPEXPR const &oexp, IEXPR const &iexpr, IEXPR2 const &iexpr2)
        : m_opexpr(oexp), m_iexpr(iexpr), m_iexpr2(iexpr2)
    {
    }
};

template <typename GETCONST, typename OPEXPR, typename IEXPR>
inline constexpr auto make_getconst_expr(OPEXPR &&opexp, IEXPR &&iexpr)
{
    return expr<Variant::getconst, tuple<GETCONST, std::remove_reference_t<OPEXPR>, std::remove_reference_t<IEXPR>>>(
            std::forward<OPEXPR>(opexp), std::forward<IEXPR>(iexpr));
}

template <typename GETCONST, typename OPEXPR, typename IEXPR, typename IEXPR2>
inline constexpr auto make_getconst_expr(OPEXPR &&opexp, IEXPR &&iexpr, IEXPR2 &&iexpr2)
{
    return expr<Variant::getconst, tuple<GETCONST, std::remove_reference_t<OPEXPR>, std::remove_reference_t<IEXPR>,
                                         std::remove_reference_t<IEXPR2>>>(
            std::forward<OPEXPR>(opexp), std::forward<IEXPR>(iexpr), std::forward<IEXPR2>(iexpr2));
}

/// \addtogroup OptConstraint
/// @{

//! CONSTVAL_INT("operand",idx) - extract int value from const at given index
template <typename OPEXPR, typename IEXPR> constexpr auto CONSTVAL_INT(OPEXPR &&opexp, IEXPR &&idx)
{
    auto op_wrapped = wrap_opexpr(std::forward<OPEXPR>(opexp));
    auto wi = wrap_param(std::forward<IEXPR>(idx));
    return make_getconst_expr<getconst_int>(std::move(op_wrapped), std::move(wi));
}

//! CONSTVAL_INT("operand",idx, idx2) - extract int value from const at given index
template <typename OPEXPR, typename IEXPR, typename IEXPR2>
constexpr auto CONSTVAL_INT(OPEXPR &&opexp, IEXPR &&idx, IEXPR2 &&idx2)
{
    auto op_wrapped = wrap_opexpr(std::forward<OPEXPR>(opexp));
    auto wi = wrap_param(std::forward<IEXPR>(idx));
    auto wi2 = wrap_param(std::forward<IEXPR2>(idx2));
    return make_getconst_expr<getconst_int2>(std::move(op_wrapped), std::move(wi), std::move(wi2));
}

//! CONSTVAL_INT_VALID("operand",idx) - determine if CONSTVAL_INT("operand",idx) is valid
template <typename OPEXPR, typename IEXPR> constexpr auto CONSTVAL_INT_VALID(OPEXPR &&opexp, IEXPR &&idx)
{
    auto op_wrapped = wrap_opexpr(std::forward<OPEXPR>(opexp));
    auto wi = wrap_param(std::forward<IEXPR>(idx));
    return make_getconst_expr<getconst_int_valid>(std::move(op_wrapped), std::move(wi));
}

//! CONSTVAL_INT_VALID("operand",idx,idx2) - determine if CONSTVAL_INT("operand",idx,idx2) is valid
template <typename OPEXPR, typename IEXPR, typename IEXPR2>
constexpr auto CONSTVAL_INT_VALID(OPEXPR &&opexp, IEXPR &&idx, IEXPR2 &&idx2)
{
    auto op_wrapped = wrap_opexpr(std::forward<OPEXPR>(opexp));
    auto wi = wrap_param(std::forward<IEXPR>(idx));
    auto wi2 = wrap_param(std::forward<IEXPR2>(idx2));
    return make_getconst_expr<getconst_int2_valid>(std::move(op_wrapped), std::move(wi), std::move(wi2));
}

//! CONSTVAL_FLOAT("operand",idx) - extract float value from const at given index
template <typename OPEXPR, typename IEXPR> constexpr auto CONSTVAL_FLOAT(OPEXPR &&opexp, IEXPR &&idx)
{
    auto op_wrapped = wrap_opexpr(std::forward<OPEXPR>(opexp));
    auto wi = wrap_param(std::forward<IEXPR>(idx));
    return make_getconst_expr<getconst_float>(std::move(op_wrapped), std::move(wi));
}

//! CONSTVAL_FLOAT_VALID("operand",idx) - determine if CONSTVAL_FLOAT("operand",idx) is valid
template <typename OPEXPR, typename IEXPR> constexpr auto CONSTVAL_FLOAT_VALID(OPEXPR &&opexp, IEXPR &&idx)
{
    auto op_wrapped = wrap_opexpr(std::forward<OPEXPR>(opexp));
    auto wi = wrap_param(std::forward<IEXPR>(idx));
    return make_getconst_expr<getconst_float_valid>(std::move(op_wrapped), std::move(wi));
}

// SELECT( bool, op,op )

template <typename CONDEXP, typename OPA, typename OPB> class opexpr<OpVnt::select, tuple<CONDEXP, OPA, OPB>> {
    CONDEXP m_cond;
    OPA m_opa;
    OPB m_opb;

  public:
    inline OpRef eval(ECtx &e) const
    {
        bool sel = m_cond.eval(e);
        if (sel)
            return m_opa.eval(e);
        else
            return m_opb.eval(e);
    }
    constexpr opexpr(CONDEXP const &sel, OPA const &opa, OPB const &opb) : m_cond(sel), m_opa(opa), m_opb(opb) {}
};
template <typename SEL, typename A, typename B> inline constexpr auto make_opselect(SEL s, A a, B b)
{
    return opexpr<OpVnt::select, tuple<SEL, A, B>>(s, a, b);
}

//! SELECT(cond, A,B) - cond?A:B
template <typename SEL, typename A, typename B> inline constexpr auto SELECT(SEL &&s, A &&a, B &&b)
{
    // Op select, or numeric select?
    // note, the 'Op' variant will not be used in a Replacement rule, since
    // it's intercepted (in Replacement::SELECT) by a ReplFunc implementation.
    if constexpr (Replacement::is_Op_type<A>() || Replacement::is_Op_type<B>()) {
        static_assert(Replacement::is_Op_type<A>() && Replacement::is_Op_type<B>(), "bad SELECT parameters");
        auto ws = wrap_param_to<bool>(std::forward<SEL>(s));
        auto wa = wrap_opexpr(std::forward<A>(a));
        auto wb = wrap_opexpr(std::forward<B>(b));
        return make_opselect(ws, wa, wb);
    } else {
        auto ws = wrap_param_to<bool>(std::forward<SEL>(s));
        auto wa = wrap_param(std::forward<A>(a));
        auto wb = wrap_param(std::forward<B>(b));
        return make_select(ws, wa, wb);
    }
}

//! OPTION_INT("option_name") - get the option value as int
inline expr<Variant::config, int> OPTION_INT(char const *optname)
{
    return expr<Variant::config, int>(optname);
}
//! OPTION_UINT("option_name") - get the option value as size_t
inline expr<Variant::config, size_t> OPTION_UINT(char const *optname)
{
    return expr<Variant::config, size_t>(optname);
}

//! OPTION_FLOAT("option_name") - get the option value as float
inline expr<Variant::config, float> OPTION_FLOAT(char const *optname)
{
    return expr<Variant::config, float>(optname);
}
//! OPTION_BOOL("option_name") - get the option value as bool
inline expr<Variant::config, bool> OPTION_BOOL(char const *optname)
{
    return expr<Variant::config, bool>(optname);
}

/// }@

////////////////////// MESSAGE ///////////////////////////////////////
//  MESSAGE(".. message ..")
//   - evaluates as true; records message and triggers debug info.
//
//   Messages only happen when WITH_OPT_DEBUG is defined; when it is not, we have
//   MESSAGE("...") -> true
//   MESSAGE_IF(cond, "...") -> cond
//   MESSAGE_IFNOT(cond, "...") -> cond
//   MESSAGE_VALUE(valud, "...") -> value
//
#ifdef WITH_OPT_DEBUG
//
// Variant::message is a MESSAGE, MESSAGE_IF, or MESSAGE_IFNOT expression.
// When MESSAGE, CONDEXPR is 'char'.
//
struct msg_message {
};
struct msg_message_if {
};
struct msg_message_ifnot {
};
struct msg_message_value {
};
// MESSAGE(value, "...") -> value

template <typename MODE, typename CONDEXPR>
struct expr<Variant::message, tuple<MODE, CONDEXPR>> : private opdef_accessor {
    CONDEXPR m_condition;
    std::string m_message;

    typedef bool otype;
    expr(char const *m, CONDEXPR &&cond) : m_condition(std::move(cond)), m_message(m) {}
    expr(char const *m, CONDEXPR const &cond) : m_condition(cond), m_message(m) {}
    inline bool eval(ECtx &e) const
    {
        if constexpr (std::is_same_v<MODE, msg_message>) {
            show_debug_message(e, "MESSAGE", m_message.c_str());
            return true;
        } else {
            bool result = m_condition.eval(e);
            if constexpr (std::is_same_v<MODE, msg_message_if>) {
                if (result) show_debug_message(e, "MESSAGE_IF", m_message.c_str());
            } else {
                if (!result) show_debug_message(e, "MESSAGE_IFNOT", m_message.c_str());
            }
            return result;
        }
    }
};
template <typename MODE, typename CONDEXPR>
struct expr<Variant::message_value, tuple<MODE, CONDEXPR>> : private opdef_accessor {
    CONDEXPR m_condition;
    std::string m_message;

    typedef bool otype;
    expr(char const *m, CONDEXPR &&cond) : m_condition(std::move(cond)), m_message(m) {}
    expr(char const *m, CONDEXPR const &cond) : m_condition(cond), m_message(m) {}
    inline int eval(ECtx &e) const
    {
        int result = m_condition.eval(e);
        std::stringstream ss;
        ss << m_message << " = " << result;
        show_debug_message(e, "MESSAGE", ss.str().c_str());
        return result;
    }
};
template <typename MODE, typename CND> inline auto make_message_expr(char const *str, CND &&cond)
{
    return expr<Variant::message, tuple<MODE, std::remove_reference_t<CND>>>(str, std::forward<CND>(cond));
}
#endif

#ifndef WITH_OPT_DEBUG
// dummy functions (which are constexpr); just generate the bool
inline constexpr auto MESSAGE(char const *str)
{
    return expr<Variant::value, bool>(true);
}
template <typename COND> inline constexpr auto MESSAGE_IF(COND a, char const *str)
{
    return wrap_param_to<bool>(a);
}
template <typename COND> inline constexpr auto MESSAGE_IFNOT(COND a, char const *str)
{
    return wrap_param_to<bool>(a);
}
template <typename VALUE> inline auto MESSAGE_VALUE(VALUE v, char const *str)
{
    return wrap_param_to<int>(v);
}
#else
// actual functions for WITH_OPT_DEBUG - cannot be constexpr
inline auto MESSAGE(char const *str)
{
    return expr<Variant::message, tuple<msg_message, char>>(str, '.');
}
template <typename VALUE> inline auto MESSAGE_VALUE(VALUE v, char const *str)
{
    auto wv = wrap_param_to<int>(v);
    return expr<Variant::message_value, tuple<msg_message_value, std::remove_reference_t<VALUE>>>(str,
                                                                                                  forward<VALUE>(wv));
}
template <typename COND> inline auto MESSAGE_IF(COND a, char const *str)
{
    auto wa = wrap_param_to<bool>(a);
    return make_message_expr<msg_message_if>(str, wa);
}
template <typename COND> inline auto MESSAGE_IFNOT(COND a, char const *str)
{
    auto wa = wrap_param_to<bool>(a);
    return make_message_expr<msg_message_ifnot>(str, wa);
}

#endif // WITH_OPT_DEBUG

////////////////////// external constraint ///////////////////////////

// This supports external constraint functions of the form
//   T function ( Constraint & , OpRef, ... any scalar ...)
// T *must* be one of the supported basic scalar types (and is usually bool...)
//

template <typename FUNC, typename ARGPACK, size_t... I>
inline auto apply_cst_function(ECtx &e, FUNC f, OpRef tgt, ARGPACK args, std::index_sequence<I...>)
{
    // allow the function to see a non-const ref
    auto &cstobj = const_cast<std::remove_const_t<ECtx> &>(e);
    return (*f)(cstobj, tgt, std::get<I>(args).eval(e)...);
}

//  The expression type is
//    expr<Variant::external, tuple< FUNC, tuple<...> >>
// .. where ... are the types of 0 or or more scalar parameters,
//   each of which is actually an expr object.
//
template <typename FUNC, typename INPARMS>
struct expr<Variant::external, tuple<FUNC, INPARMS>> : private opdef_accessor {
    static constexpr size_t NPARMS = std::tuple_size_v<INPARMS>;
    FUNC m_function; // pointer to function
    hnnx::operand_tag_t m_optag; // one of the function params
    INPARMS m_other_operands; // other params (0 or more; all as expr<> objects)

    expr(FUNC f, hnnx::operand_tag_parm_t optag, INPARMS &&etc)
        : m_function(f), m_optag(optag), m_other_operands(std::move(etc))
    {
    }

    using otype = decltype(apply_cst_function<FUNC, INPARMS>(fake_ectx(), m_function, OpRef(), m_other_operands,
                                                             std::make_index_sequence<NPARMS>()));
    // TODO: we should ensure here that 'otype' is one of the allowed types (and maybe apply_cse_function
    // should do minor coercions, e.g. from short to int).

    inline otype eval(ECtx &e) const
    {
        OpRef target = lookup_operand(e, m_optag);
        return apply_cst_function<FUNC, INPARMS>(e, m_function, target, m_other_operands,
                                                 std::make_index_sequence<NPARMS>());
    }
};

// this EXTERNAL_CONSTRAINT requires that the second parameter is a literal operand name;
// I don't think that's a problem.
template <typename FUNC, typename... Args>
auto EXTERNAL_CONSTRAINT(FUNC f, hnnx::operand_tag_parm_t optag, Args &&...args)
{
    auto parmpack = std::make_tuple(wrap_param(std::forward<Args>(args))...);
    return expr<Variant::external, tuple<FUNC, decltype(parmpack)>>(f, optag, std::move(parmpack));
}

template <typename OPEX> struct expr<Variant::producer_for, OPEX> : private opdef_accessor {
    std::string m_consumer_opname;
    const OPEX m_prod_opexpr;
    inline bool eval(ECtx &e) const
    {
        std::string prefix_consumer_opname;
        const char *const opname = hnnx::get_opname_with_pkg_prefix(prefix_consumer_opname, m_consumer_opname.c_str());
        OpDef const &prod_opdef = get_opdef_oexpr<OPEX>(e, m_prod_opexpr);
        return hnnx::producer_for_impl(prod_opdef, opname);
    }
    expr(OPEX const &prod_opexpr, char const *consumer_opname)
        : m_consumer_opname(consumer_opname), m_prod_opexpr(prod_opexpr)
    {
    }
};

//! PRODUCER_FOR("operand", "opname") - check if "operand" has consumer with name "opname"
template <typename OPEX> auto PRODUCER_FOR(OPEX &&producer, char const *consumer_opname)
{
    auto producer_wrapped = wrap_opexpr(std::forward<OPEX>(producer));
    return expr<Variant::producer_for, decltype(producer_wrapped)>(producer_wrapped, consumer_opname);
}

template <typename OPEX> struct expr<Variant::eq_opstr, OPEX> : private opdef_accessor {
    std::string m_opname;
    const OPEX m_opexpr;
    inline bool eval(ECtx &e) const
    {
        // TODO -- can this be moved to the constructor?
        std::string prefix_opname;
        const char *opname = hnnx::get_opname_with_pkg_prefix(prefix_opname, m_opname.c_str());
        OpDef const &opdef = get_opdef_oexpr<OPEX>(e, m_opexpr);
        return opdef.opstr == opname;
    }
    expr(OPEX const &op_opexpr, char const *opname) : m_opname(opname), m_opexpr(op_opexpr) {}
};

//! IS_OP("op", "opname") - check if "op" does not opstr equal to  "opname"
template <typename OPEX> auto IS_OP(OPEX &&op, char const *opname)
{
    auto op_wrapped = wrap_opexpr(std::forward<OPEX>(op));
    return expr<Variant::eq_opstr, decltype(op_wrapped)>(op_wrapped, opname);
}

//! IS_DTYPE_ALL(Dtype, "operand", ...) -> bool (true if all operands are Dtype)
template <typename Tdtype, typename Ta, typename... Ts>
inline constexpr auto IS_DTYPE_ALL(Tdtype &&dtype, Ta &&a, Ts &&...ts)
{
    return AND(EQ(DTYPE_OF(std::forward<Ta>(a)), std::forward<Tdtype>(dtype)),
               IS_DTYPE_ALL(std::forward<Tdtype>(dtype), std::forward<Ts>(ts))...);
}
} // namespace oExp

// create namespaces visible in constraints, and in replacements.
// 'constraint' namespace can't see SPLIT_START etc.

namespace oExp_for_cst {

#ifndef PREPARE_DISABLED
using oExp::ADD, oExp::SUB, oExp::MUL, oExp::DIV, oExp::NEG;
using oExp::AND, oExp::OR, oExp::XOR, oExp::NOT;
using oExp::DATA_SIZE;
using oExp::DIM_OF, oExp::INPUTS_OF, oExp::OUTPUTS_OF, oExp::ELEMENTSIZE_OF;
using oExp::EQ, oExp::NE, oExp::LT, oExp::GT, oExp::LE, oExp::GE;
using oExp::EXTERNAL_CONSTRAINT;
using oExp::INPUT_OF, oExp::OUTPUT_OF;
using oExp::IS_POW2;
using oExp::MESSAGE, oExp::MESSAGE_IF, oExp::MESSAGE_IFNOT, oExp::MESSAGE_VALUE;
using oExp::MIN, oExp::MAX, oExp::ABS;
using oExp::RANK_OF, oExp::ZERO_OFFSET_OF, oExp::STEPSIZE_OF, oExp::DTYPE_OF;
using oExp::REM, oExp::MOD;
using oExp::ROUNDUP;
using oExp::SAME_OP, oExp::SAME_ENCODING, oExp::SAME_SHAPE;
using oExp::SELECT;

using oExp::INT, oExp::UINT, oExp::FLOAT, oExp::DTYPE; // 'cast' operators

using oExp::CONSTVAL_FLOAT, oExp::CONSTVAL_FLOAT_VALID;
using oExp::CONSTVAL_INT, oExp::CONSTVAL_INT_VALID;

using oExp::IS_DTYPE_ALL;
using oExp::IS_OP;
using oExp::OPTION_INT, oExp::OPTION_UINT, oExp::OPTION_FLOAT, oExp::OPTION_BOOL;
using oExp::PRODUCER_FOR;
#endif

/// \ingroupOptConstraint
/// @brief OK can be used when no constraint is needed
static constexpr bool OK = true;

/// \ingroupOptConstraint
/// @brief  INF: use for inf in constraints and replacement rules.
static constexpr float INF = std::numeric_limits<float>::infinity();
/// \ingroupOptConstraint
/// @brief  NEG_INF: use for -inf in constraints and replacement rules.
static constexpr float NEG_INF = -std::numeric_limits<float>::infinity();

/// \ingroupOptConstraint
/// @brief  INF: use for inf in constraints and replacement rules.
static constexpr float INF_INT = std::numeric_limits<int32_t>::infinity();
/// \ingroupOptConstraint
/// @brief  NEG_INF: use for -inf in constraints and replacement rules.
static constexpr float NEG_INF_INT = -std::numeric_limits<int32_t>::infinity();
} // namespace oExp_for_cst

namespace oExp_for_repl {
#ifndef PREPARE_DISABLED
using namespace oExp_for_cst;
using oExp::ITER_VAR;
using oExp::SPLIT_START, oExp::SPLIT_SIZE, oExp::SPLIT_DIM;
#endif

// ITER_INPUT_OF( op, spl ) -> INPUT_OF( op, ITER_VAR(spl))

/// \ingroup OptReplacement
/// @brief ITER_INPUT_OF( <some_op>, "split") - extract the input from <someop> selected by ITER_VAR(split)
///
template <typename OPER> inline auto ITER_INPUT_OF(OPER &&oper, hnnx::split_context_tag_t whatsplit)
{
    return oExp::INPUT_OF(std::forward<OPER>(oper), oExp::ITER_VAR(whatsplit));
}

} // namespace oExp_for_repl

POP_VISIBILITY()

#endif /* !PREPARE_DISABLED */
#endif /* OEXPR_POST_H_ */
