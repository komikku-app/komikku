//==============================================================================
// Copyright (c) 2020-2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OEXPR_H_
#define OEXPR_H_

#include <algorithm>
#include <functional>
#include <utility>
#include <numeric>
#include "dtype_enum.h"
#include "macros_attribute.h"
#include "opname_tag.h"
#include "weak_linkage.h"

#ifndef PREPARE_DISABLED
// This file is expected to be #included at top of optimize.h,
// with oexp_post.h included farther down.
// #include "optimize.h"

// expression mechanism for constraints etc.
// This supports deferred execution, so if used in replacement
// rules under autosplit, you can use SPLIT_START etc in
// expressions (e.g. ADD(SPLIT_START("I"), 2))
// ... but only in contexts where a late-evaluation result is OK
// (which includes inputs to gen_Shape, and it should include the first
//  parameter of SELECT( cond, .. ops.. , ..ops ... )
//
//
// All of the ADD() MUL() GE() etc
// return objects which are specializations of expr,
// and which contain the entire expression; these
// are generally built at compile time.
//
// These objects all have a method .eval(ctx&) which
// evaluates the expression. All expr specializations
//  have a type 'otype' which defines what type eval()
//  returns.
// The mechanism supports short-circuit evaluation of
// && ||, and SELECT, since nothing is actually done when the
//  original expression is evaluated, only when the eval() is called.
//
//
// The inputs to ADD() MUL() etc can be:
//
//   - specializations of expr containing smaller expressions.
//   - scalar values (int,float etc)which will be coerced
//     to a expr<Variant::value,T> that simply contains the valu
//.
//   - std::function<T(ctx&)> objects, for supported T types;
//     these will be packed into expr<Variant::function,T> objects,
//     which are not called until the .eval() is done (@@@ this is being removed)
//
//   Things like RANK_OF("PARM") are implemented by constructing
//   an expr variant containing the "PARM" string; the eval() method
//   will get the rank and return it. Thus, these are not called
//   if they are skipped by AND, OR or SELECT conditions.
//
//  SPLIT_START etc, construct an expr variant containing the context
//  name.
//
// For any expr<> object, we could construct it, bind it to a lambda which calls
// its eval() method, and then that lambda can be converted to a std::function.
//
// We can't fully support C++ casts though.
// What if someone writes  float( DIM_OF("x",2) ) ??
// We can't write an 'operator float' which doesn't return a float.
// Will need to do it with unary 'cast' ops.
//
// When this is used for constraints, it will be something like this:
//  bool constraint_function() {
//          auto result = AND( ..  the whole expression ... )
//          return result.eval(*this)
//   }
// In this case there are no std::function objects; the compiler will
// expand the whole expression and then evaluate its 'eval' method; and
// so it will be as if the expressions were directly pasted (and, we
// get short-cut eval of && ||, SELECT).
//
// Actually, I don't think we need to support std::function as input,
// since we are replacing all of that.
// But we do need to be able to make a std::function from an expression.
//
// This can be done using wrap_as_function, e.g. for an 'int' return type
//
//    std::function< int(ECtx&)> as_func = wrap_as_function<int>(  anything );
//
//  where the parameter is an expr object, or anything which will convert to one.
// If it has a different return type, a conversion will be inserted in the wrapper.
//
//
namespace constraint_lib {
class Constraint;
}
namespace hnnx {
template <typename T> class optim_configvar;
}

PUSH_VISIBILITY(default)

API_EXPORT hnnx::Crate *get_lambda_crate();
template <typename R> class OptFunction;

template <typename R, typename... Args> class OptFunction<R(Args...)> {
  public:
    using thisType = OptFunction<R(Args...)>;
    using OptFunctionTType = R (*)(void *, Args...);
    using OptFunctionType = R (*)(Args...);

    template <typename L> API_EXPORT static R LambdaWrapper(void *t, Args... args)
    {
        L *const obj = (L *)t;
        return obj->operator()(args...);
    }
    API_EXPORT static R FunctionWrapper(void *t, Args... args)
    {
        OptFunctionType const obj = (OptFunctionType)t;
        return obj(args...);
    }
    template <typename L>
    API_EXPORT static typename std::enable_if<!std::is_lvalue_reference_v<L>, thisType>::type create(L &&lambda)
    {
        L *const l = get_lambda_crate()->emplace<L>(std::forward<L>(lambda));
        return thisType(LambdaWrapper<L>, l);
    }

    OptFunctionTType mFunc;
    void *mObj;
    API_EXPORT OptFunction() : mFunc(nullptr), mObj(nullptr){};
    API_EXPORT OptFunction(OptFunctionTType f, void *o) : mFunc(f), mObj(o){};

    API_EXPORT R operator()(Args... args) const { return mFunc(mObj, args...); }
    API_EXPORT operator bool() const { return (mFunc != nullptr); }
};

POP_VISIBILITY()

namespace oExp {

using std::forward;
using std::tuple;

enum class Variant : int {
    // Core functionality...
    // template params of expr:
    value, // <value,T> : a value of type T
    unop, // <unop,tuple<A,UFUNC>>  : UFUNC(A)
    binop, // <binop, tuple<A,B,BFUNC>  : BFUNC(A,B)
    binop1, // <binop, tuple<A,B,BFUNC>  : BFUNC(A,B) (where B is a scalar type, not an oExp)
    lg_or, // <lg_or, tuple<A,B,...> :  A || B || ...
    lg_and, // <lg_and, tuple<A,B,...> :  A && B && ...
    lg_xor, // <lg_xor, tuple<A,B,...> :  A ^ B ^ ...
    select, // <select, tuple<S,A,B>:   S?A:B
    message, // <message, <mode,CONDITION>>   'mode' is an empty class
    message_value, // <message, <mode,VALUE>>   'mode' is an empty class

    // extensions which allow access to OpDef etc

    property, // <property, tuple<OPEXP, prop_extractor_class>>  - extracts a property or dim of OpDef
    opcompare, // <opcompare,tuple<OPEXPA,OPEXPB,op_compare_class>> - compares two ops in some way.
    slicedim, // <slicedim,int>		- contains a slice name and a ptr-to-member var.
    getconst, // <getconst, tuple<getconst_class,A>> - contains operand name and index expression.
    external, // <external, tuple<FUNC, tuple<extra..>>>		- calls external-constraint
    config, // <config,   T>  - reads a config var from optim_config_values.
    producer_for,
    eq_opstr, //  -- compares the opstr on a node to a constant

    // Internal Exprs used for for D2RTR
    d2rtr_constraint,
};
// in this namespace, ECtx is the type whose reference gets passed
// to all the eval methods and std::function objects.
typedef constraint_lib::Constraint ECtx;
// sFunction<T>:  std::function returning T (using ECtx as a parameter)
//
template <typename T> using sFunction = OptFunction<T(ECtx &)>;

template <Variant V, typename ARG> class expr {
};

////////////////////////////
// map a 'scalar' value to an expr.
////////////////////////////
template <typename T> class expr<Variant::value, T> {
    const T m_val;

  public:
    typedef T otype;
    constexpr expr(T v) : m_val(v) {}
    constexpr otype eval(ECtx &) const { return m_val; }
    constexpr T getval() const { return m_val; }
};

//---------------------------------
// this is an (uncallable) template function which returns
// the same type as the eval() method of an expr; for use in decltype
//  i.e. decltype(fake_eval(x)) will give you the return type of x.eval(ECtx&)
//
template <Variant V, typename T> inline auto fake_eval(expr<V, T> const &a)
{
    extern ECtx This_should_not_be_referenced;
    return a.eval(This_should_not_be_referenced);
}
inline ALWAYSINLINE ECtx &fake_ectx()
{
    extern ECtx This_should_not_be_referenced;
    return This_should_not_be_referenced;
}

//---------------------------------

// wrap_param() is used to convert template parameters of things like ADD to an expr.
// - any expr is unchanged
// - scalar types are converted to the applicale 'value' (including coercion
// from e.g. double to float)

// an adapter which converts a scalar to an expr.
template <typename T> struct wrapper_helper {
};
// direct conversions
template <> struct wrapper_helper<bool> {
    static constexpr auto wrap(bool x) { return expr<Variant::value, bool>(x); }
};
template <> struct wrapper_helper<int> {
    static constexpr auto wrap(int x) { return expr<Variant::value, int>(x); }
};
template <> struct wrapper_helper<float> {
    static constexpr auto wrap(float x) { return expr<Variant::value, float>(x); }
};
template <> struct wrapper_helper<DType> {
    static constexpr auto wrap(DType x) { return expr<Variant::value, DType>(x); }
};

// double->float
template <> struct wrapper_helper<double> {
    static constexpr auto wrap(double x) { return expr<Variant::value, float>(float(x)); }
};

// unsigned, unsigned long, unsigned long long all-> size_t.
// presumably one of them is identical to size_t on any given platform.
template <> struct wrapper_helper<unsigned> {
    static constexpr auto wrap(unsigned x) { return expr<Variant::value, size_t>(size_t(x)); }
};
template <> struct wrapper_helper<unsigned long> {
    static constexpr auto wrap(unsigned long x) { return expr<Variant::value, size_t>(size_t(x)); }
};
template <> struct wrapper_helper<unsigned long long> {
    static constexpr auto wrap(unsigned long long x) { return expr<Variant::value, size_t>(size_t(x)); }
};

// a variant is unchanged when wrapped.
template <Variant V, typename T> struct wrapper_helper<expr<V, T>> {
    static constexpr auto wrap(expr<V, T> const &x) { return x; }
};

template <typename T> inline constexpr auto wrap_param(T &&p)
{
    return wrapper_helper<std::remove_const_t<std::remove_reference_t<T>>>::wrap(std::forward<T>(p));
}

// We can also try to wrap something as a specific scalar type (e.g. to bool for SELECT).
// here, T2 is always a scalar type
// (currently, only works for bool; maybe it will be useful coerce consts to a common type
// before ADD, e.g.).
//

template <typename T2, typename T> inline constexpr auto wrap_param_to(T &&p)
{
    typedef std::remove_reference_t<T> Tparam;
    if constexpr (std::is_same_v<bool, T2> && std::is_arithmetic_v<Tparam>) {
        // convert e.g. int to bool directly
        return wrapper_helper<bool>::wrap(p != 0);
    } else {
        return wrapper_helper<Tparam>::wrap(std::forward<T>(p));
    }
}

/** \defgroup OptConstraint Constraint Expressions for Optimization Rules
 * \ingroup OptimizationFuncs
 *
 * These are the operations available for writing Constraint expressions.
 * These may also be used in Replacement rules, e.g. to compute gen_Shape object dimensions.
 *
 * The following conversion operations may also be used in constraint expressions:
 *
 *     INT(expr)  UINT(expr) FLOAT(expr)  DTYPE(expr)
 *
 * Avoid using C-style casts, e.g. (int)expr; for conversion to bool use NE(exp,0)
 *
 * @{
 * @}
 *
 */

///////////////////////////////////////////////////////////////////////////////////////
//
// a unary operator
//
template <typename A, typename OPER> class expr<Variant::unop, tuple<A, OPER>> {
    const A m_a;
    const OPER m_op;

  public:
    using otype = decltype(m_op(fake_eval(m_a)));
    constexpr expr(A a, OPER op) : m_a(a), m_op(op) {}
    constexpr otype eval(ECtx &e) const { return m_op(m_a.eval(e)); }
};
template <typename A, typename OPER> inline constexpr auto make_unop(A a, OPER op)
{
    return expr<Variant::unop, tuple<A, OPER>>(a, op);
}
// specialize for case where input is value (constant folding)
template <typename TA, typename OPER> inline constexpr auto make_unop(expr<Variant::value, TA> a, OPER op)
{
    return wrap_param(op(a.getval()));
}

// a binary operator
//
template <typename A, typename B, typename OPER> class expr<Variant::binop, tuple<A, B, OPER>> {
    const A m_a;
    const B m_b;
    const OPER m_op;

  public:
    using otype = decltype(m_op(fake_eval(m_a), fake_eval(m_b)));
    constexpr expr(A a, B b, OPER op) : m_a(a), m_b(b), m_op(op) {}
    constexpr otype eval(ECtx &e) const { return m_op(m_a.eval(e), m_b.eval(e)); }
};
template <typename A, typename B, typename OPER> inline constexpr auto make_binop(A a, B b, OPER op)
{
    return expr<Variant::binop, tuple<A, B, OPER>>(a, b, op);
}
// specialize for the case where both inputs to binop are value (constant folding)
template <typename TA, typename TB, typename OPER>
inline constexpr auto make_binop(expr<Variant::value, TA> a, expr<Variant::value, TB> b, OPER op)
{
    auto constexpr res = op(a.getval(), b.getval());
    return wrap_param(res);
}

// a binary operator where the RHS is a known value (these
// are very common, so hopefully this reduces complexity of the generated code)
//
template <typename A, typename TB, typename OPER> class expr<Variant::binop1, tuple<A, TB, OPER>> {
    const A m_a; // an oExp
    const TB m_b; // a scalar
    const OPER m_op;

  public:
    using otype = decltype(m_op(fake_eval(m_a), m_b));
    constexpr expr(A a, TB b, OPER op) : m_a(a), m_b(b), m_op(op) {}
    constexpr otype eval(ECtx &e) const { return m_op(m_a.eval(e), m_b); }
};
// specialize make_binop for the case where the second input is 'value'.
template <typename A, typename TB, typename OPER>
inline constexpr auto make_binop(A a, expr<Variant::value, TB> b, OPER op)
{
    using TBX = std::common_type_t<typename A::otype, TB>;
    return expr<Variant::binop1, tuple<A, TBX, OPER>>(a, TBX(b.getval()), op);
}

// 'opers' is a tuple of N various expr objects which
// are the inputs to AND,OR or XOR;
// 'IDX' is in range 0..N-1
// find the logical operation applied over inputs I..N-1,
// expands recursively until IDX=N-1
//
template <Variant VAR, size_t IDX, typename OPERANDS>
inline bool ALWAYSINLINE reduce_logop(ECtx &e, OPERANDS const &operands);

// || operator (delayed eval of subexpressions)
//
template <typename OPERANDS> // operands is tuple<expr<> ...>
class expr<Variant::lg_or, OPERANDS> {
    const OPERANDS m_operands;

  public:
    using otype = bool;
    inline constexpr expr(OPERANDS &&op) : m_operands(std::move(op)) {}
    inline constexpr expr(OPERANDS const &op) : m_operands(op) {}
    constexpr otype eval(ECtx &e) const { return reduce_logop<Variant::lg_or, 0, OPERANDS>(e, m_operands); }
};
template <typename OPERANDS> // operands is tuple<expr<> ...>
class expr<Variant::lg_and, OPERANDS> {
    const OPERANDS m_operands;

  public:
    using otype = bool;
    inline constexpr expr(OPERANDS &&op) : m_operands(std::move(op)) {}
    inline constexpr expr(OPERANDS const &op) : m_operands(op) {}
    constexpr otype eval(ECtx &e) const { return reduce_logop<Variant::lg_and, 0, OPERANDS>(e, m_operands); }
};
template <typename OPERANDS> // operands is tuple<expr<> ...>
class expr<Variant::lg_xor, OPERANDS> {
    const OPERANDS m_operands;

  public:
    using otype = bool;
    inline constexpr expr(OPERANDS &&op) : m_operands(std::move(op)) {}
    inline constexpr expr(OPERANDS const &op) : m_operands(op) {}
    constexpr otype eval(ECtx &e) const { return reduce_logop<Variant::lg_xor, 0, OPERANDS>(e, m_operands); }
};
template <Variant WHICH, typename OPERANDS> inline constexpr auto make_logop(OPERANDS &&opers)
{
    return expr<WHICH, std::remove_reference_t<OPERANDS>>(std::forward<OPERANDS>(opers));
}
// select
template <typename SEL, typename A, typename B> class expr<Variant::select, tuple<SEL, A, B>> {
    const SEL m_sel;
    const A m_a;
    const B m_b;

  public:
    using otype = decltype(bool() ? fake_eval(m_a) : fake_eval(m_b));
    constexpr expr(SEL s, A a, B b) : m_sel(s), m_a(a), m_b(b) {}
    constexpr otype eval(ECtx &e) const { return m_sel.eval(e) ? m_a.eval(e) : m_b.eval(e); }
};
template <typename SEL, typename A, typename B> inline constexpr auto make_select(SEL s, A a, B b)
{
    return expr<Variant::select, tuple<SEL, A, B>>(s, a, b);
}

// CLST must be a template class name with <type> missing; so
// we can use std::plus etc.
#define OEXP_ARITH(FNAME, CLST)                                                                                        \
    template <typename A, typename B> inline constexpr auto FNAME(A &&pa, B &&pb)                                      \
    {                                                                                                                  \
        if constexpr (std::is_arithmetic_v<std::remove_reference_t<A>> &&                                              \
                      std::is_arithmetic_v<std::remove_reference_t<B>>) {                                              \
            /* 'constant folding' ... */                                                                               \
            using common_t = std::common_type_t<std::remove_reference_t<A>, std::remove_reference_t<B>>;               \
            return CLST<common_t>()(pa, pb);                                                                           \
        } else {                                                                                                       \
            auto wa = wrap_param(std::forward<A>(pa));                                                                 \
            auto wb = wrap_param(std::forward<B>(pb));                                                                 \
            using atype = decltype(fake_eval(wa));                                                                     \
            using btype = decltype(fake_eval(wb));                                                                     \
            using common_t = std::common_type_t<atype, btype>;                                                         \
            return make_binop(wa, wb, CLST<common_t>());                                                               \
        }                                                                                                              \
    }

/// \addtogroup OptConstraint

// Formal definition of CEILDIV(a, b)
template <typename T> struct ceil_div {
    static_assert(std::is_integral_v<T>, "CEILDIV can only apply to integer types");
    inline T operator()(T a, T b) const
    {
        T res = (a + b - 1) / b;
        return res;
    }
};

/// @{

//! ADD(a,b):  A+B
OEXP_ARITH(ADD, std::plus)

//! SUB(a,b):  A-B
OEXP_ARITH(SUB, std::minus)

//! MUL(a,b):  A*B
OEXP_ARITH(MUL, std::multiplies)

//! DIV(a,b):  A/B
OEXP_ARITH(DIV, std::divides)

//!REM(a,b): A%B
/// for signed types REM(a,b) is either 0 or has the same sign as a.
OEXP_ARITH(REM, std::modulus)

//! CEILDIV(a,b) round the result of division toward positive infinity
OEXP_ARITH(CEILDIV, ceil_div)

/// @}

template <typename T> struct true_modulus {
    static_assert(std::is_integral_v<T>, "MOD can only apply to integer types");
    inline T operator()(T a, T b) const
    {
        T res = a % b;
        // if res is non-zero and has opposite sign to b, add b to it.
        if ((b < 0) ? (res > 0) : (res < 0)) res += b;
        return res;
    }
};

// Formal definition of ROUNDUP(a,b):
// T can be int or unsigned ('size_t')
// For b == 0, round to next larger power of two
// UNDEFINED for b < 0 ( you will get 0, and maybe a runtime warning, some day?)
// roundup(a,1) = a
// for b>=2: result is a rounded up (towards +inf) to a multiple of b.
//  So, roundup(15,10) = 20, roundup(-15,10) = -10
// When b is a power of 2, this is consistent with (a+(b-1))&~(b-1) for both
// signed and unsigned cases.
//
template <typename T> struct func_roundup {
    static_assert(std::is_integral_v<T>, "ROUNDUP can only apply to integer types");
    inline T roundup_pow2(T a) const
    {
        if (a <= 1) return a;
        a -= 1;
        a |= a >> 1;
        a |= a >> 2;
        a |= a >> 4;
        a |= a >> 8;
        a |= a >> 16;
        a += 1;
        return a;
    }
    inline T operator()(T a, T b) const
    {
        if (b < 0) return 0;
        if (b == 0) return roundup_pow2(a);
        if (b == 1) return a;
        if ((b & (b - 1)) == 0) return (a + (b - 1)) & (~(b - 1));
        // avoid overflow (except in cases where the rounded-up value overflows).
        T rem = a % b;
        if (rem == 0) return a;
        // rem is 1..b-1 for  a > 0
        // and -(b-1) ... -1 for a < 0
        T anew = a - rem; // rounded towards 0;
        if (a >= 0) anew += b;
        return anew;
    }
};

/// \addtogroup OptConstraint
/// @{

//! MOD(a,b) is either 0 or has the same sign as b.
OEXP_ARITH(MOD, true_modulus)

//! ROUNDUP(a,b): 'a' rounded up to a multiple of b.
// - undefined if b <=0  (result will be 0, and you may get a runtime warning or something)
// - for signed int: ROUNDUP(15,10)-> 20 but ROUNDUP(-15,10) -> -10.
//
OEXP_ARITH(ROUNDUP, func_roundup)

/// @}

template <typename T> struct min_func {
    inline T operator()(T a, T b) const { return std::min(a, b); }
};
template <typename T> struct max_func {
    inline T operator()(T a, T b) const { return std::max(a, b); }
};

/// \addtogroup OptConstraint
/// @{

//! MIN(a,b) minimum.
OEXP_ARITH(MIN, min_func)
//! MIN(a,b) maximum.
OEXP_ARITH(MAX, max_func)

/// @}

template <typename T> struct lcm_func {
    inline T operator()(T a, T b) const { return std::lcm(a, b); }
};

/// \addtogroup OptConstraint
/// @{

//! LCM(a,b): lcm(a,b)
// return least common multiple
OEXP_ARITH(LCM, lcm_func)

/// @}

/// \addtogroup OptConstraint
/// @{
// If this is needed, we should also add BIT_AND BIT_XOR BIT_COMPL
//! BIT_OR(a,b):  A|B
OEXP_ARITH(BIT_OR, std::bit_or)
//! BIT_ANDR(a,b):  A&B
OEXP_ARITH(BIT_AND, std::bit_and)
/// @}

#define OEXP_COMPARE(FNAME, CLST)                                                                                      \
    template <typename A, typename B> inline constexpr auto FNAME(A &&pa, B &&pb)                                      \
    {                                                                                                                  \
        auto wa = wrap_param(std::forward<A>(pa));                                                                     \
        auto wb = wrap_param(std::forward<B>(pb));                                                                     \
        using atype = decltype(fake_eval(wa));                                                                         \
        using btype = decltype(fake_eval(wb));                                                                         \
        using common_t = std::common_type_t<atype, btype>;                                                             \
        return oExp::make_binop(wa, wb, CLST<common_t>());                                                             \
    }

/// \addtogroup OptConstraint
/// @{

//! EQ(a,b)  - compare equal
OEXP_COMPARE(EQ, std::equal_to);
//! NE(a,b)  - compare not-equal
OEXP_COMPARE(NE, std::not_equal_to);
//! GT(a,b)  - compare greater-than
OEXP_COMPARE(GT, std::greater);
//! GE(a,b)  - compare greater-than-or-equal
OEXP_COMPARE(GE, std::greater_equal);

//! LT(a,b)  - compare less-than
template <typename A, typename B> inline constexpr auto LT(A &&a, B &&b)
{
    return GT(std::forward<B>(b), std::forward<A>(a));
}
//! LE(a,b)  - compare less-than-or-equal
template <typename A, typename B> inline constexpr auto LE(A &&a, B &&b)
{
    return GE(std::forward<B>(b), std::forward<A>(a));
}

/// @}

#define OEXP_UNARYMATH(FNAME, CLSNAME)                                                                                 \
    template <typename A> inline constexpr auto FNAME(A &&pa)                                                          \
    {                                                                                                                  \
        auto wa = wrap_param(std::forward<A>(pa));                                                                     \
        using atype = decltype(fake_eval(wa));                                                                         \
        return make_unop(wa, CLSNAME<atype>());                                                                        \
    }

#define OEXP_PREDICATE(FNAME, CODE)                                                                                    \
    template <typename A> inline constexpr auto FNAME(A &&pa)                                                          \
    {                                                                                                                  \
        auto wa = wrap_param(std::forward<A>(pa));                                                                     \
        using atype = decltype(fake_eval(wa));                                                                         \
        return make_unop(wa, [](atype a) -> bool { return (CODE); });                                                  \
    }

template <typename T> inline bool is_pow2_func(T a)
{
    static_assert(std::is_integral_v<T>, "IS_POW2 can only apply to integer types");
    return (a & (a - 1)) == 0;
}
template <typename T> struct abs_func {
    inline T operator()(T a) const { return std::abs(a); }
};

/// \addtogroup OptConstraint
/// @{

//! NEG(x) - negation
OEXP_UNARYMATH(NEG, std::negate)

//! NOT(x) - boolean inversion
OEXP_PREDICATE(NOT, !a)

//! IS_POW2(x) - is x a power of 2 (assuming x >= 1)
OEXP_PREDICATE(IS_POW2, is_pow2_func(a))

//! ABS(x) - abs value
OEXP_UNARYMATH(ABS, abs_func)

/// @}

// casts
template <typename TTO, typename TFROM> struct cast_oper {
    inline constexpr TTO operator()(TFROM x) const { return TTO(x); }
};

template <typename TTO, typename SOMEEXP> inline constexpr auto make_cast_oper(SOMEEXP &&exp)
{
    typedef std::remove_reference_t<SOMEEXP> EXPT;
    typedef typename EXPT::otype from_type;
    typedef cast_oper<TTO, from_type> OPER;
    return expr<Variant::unop, tuple<EXPT, OPER>>(std::move(exp), OPER());
}

/// \addtogroup OptConstraint
/// @{

//! INT(x) - convert to int
template <typename TA> static inline auto constexpr INT(TA &&a)
{
    auto wa = wrap_param(std::forward<TA>(a));
    if constexpr (std::is_same_v<typename decltype(wa)::otype, int>) {
        return wa; // already is
    } else {
        return make_cast_oper<int>(std::move(wa));
    }
}
// specialize for when applied to 'value'
template <typename TI> static inline auto constexpr INT(expr<Variant::value, TI> const &val)
{
    return expr<Variant::value, int>(int(val.getval()));
}

//! UINT(x) - convert to unsigned (i.e. size_t)
template <typename TA> static inline auto constexpr UINT(TA &&a)
{
    auto wa = wrap_param(std::forward<TA>(a));
    if constexpr (std::is_same_v<typename decltype(wa)::otype, size_t>) {
        return wa; // already is
    } else {
        return make_cast_oper<size_t>(std::move(wa));
    }
}
template <typename TI> static inline auto constexpr UINT(expr<Variant::value, TI> const &val)
{
    return expr<Variant::value, size_t>(size_t(val.getval()));
}

//! DTYPE(x) - convert to DType
template <typename TA> static inline auto constexpr DTYPE(TA &&a)
{
    auto wa = wrap_param(std::forward<TA>(a));
    if constexpr (std::is_same_v<typename decltype(wa)::otype, DType>) {
        return wa; // already is
    } else {
        return make_cast_oper<DType>(std::move(wa));
    }
}
template <typename TI> static inline auto constexpr DTYPE(expr<Variant::value, TI> const &val)
{
    return expr<Variant::value, DType>(DType(val.getval()));
}

//! FLOAT(x) - convert to float
template <typename TA> static inline auto constexpr FLOAT(TA &&a)
{
    auto wa = wrap_param(std::forward<TA>(a));
    if constexpr (std::is_same_v<typename decltype(wa)::otype, float>) {
        return wa; // already is
    } else {
        return make_cast_oper<float>(std::move(wa));
    }
}
template <typename TI> static inline auto constexpr FLOAT(expr<Variant::value, TI> const &val)
{
    return expr<Variant::value, float>(float(val.getval()));
}

/// @}

// do we need a BOOL cast?

/// \addtogroup OptConstraint
/// @{

//! OR(a,b, ...) - logical OR; evaluation stops after first 'true' operand
template <typename TA, typename TB, typename... Ts> inline constexpr auto OR(TA &&a, TB &&b, Ts &&...ts)
{
    auto parms = std::make_tuple(wrap_param_to<bool>(std::forward<TA>(a)), wrap_param_to<bool>(std::forward<TB>(b)),
                                 wrap_param_to<bool>(std::forward<Ts>(ts))...);
    return make_logop<Variant::lg_or>(parms);
}

//! AND(a,b, ...) - logical AND; evaluation stops after first 'false' operand
template <typename TA, typename TB, typename... Ts> inline constexpr auto AND(TA &&a, TB &&b, Ts &&...ts)
{
    auto parms = std::make_tuple(wrap_param_to<bool>(std::forward<TA>(a)), wrap_param_to<bool>(std::forward<TB>(b)),
                                 wrap_param_to<bool>(std::forward<Ts>(ts))...);
    return make_logop<Variant::lg_and>(parms);
}

//! AND(a,b, ...) - logical AND; evaluation stops after first 'false' operand
template <typename TA> inline constexpr auto AND(TA &&a)
{
    return AND(std::forward<TA>(a), true);
}

//! XOR(a,b, ...) - logical XOR
template <typename TA, typename TB, typename... Ts> inline constexpr auto XOR(TA &&a, TB &&b, Ts &&...ts)
{
    auto parms = std::make_tuple(wrap_param_to<bool>(std::forward<TA>(a)), wrap_param_to<bool>(std::forward<TB>(b)),
                                 wrap_param_to<bool>(std::forward<Ts>(ts))...);
    return make_logop<Variant::lg_xor>(parms);
}

//! ADD(a,b,c...) - equivalent to ADD( ADD(a,b), c ...)
template <typename TA, typename TB, typename... Ts> inline constexpr auto ADD(TA &&a, TB &&b, Ts &&...ts)
{
    return ADD(ADD(std::forward<TA>(a), std::forward<TB>(b)), std::forward<Ts>(ts)...);
}

//! MUL(a,b,c...) - equivalent to MUL( MUL(a,b), c ...)
template <typename TA, typename TB, typename... Ts> inline constexpr auto MUL(TA &&a, TB &&b, Ts &&...ts)
{
    return MUL(MUL(std::forward<TA>(a), std::forward<TB>(b)), std::forward<Ts>(ts)...);
}

//! MIN(a,b,c...) - equivalent to MIN( MIN(a,b), c ...)
template <typename TA, typename TB, typename... Ts> inline constexpr auto MIN(TA &&a, TB &&b, Ts &&...ts)
{
    return MIN(MIN(std::forward<TA>(a), std::forward<TB>(b)), std::forward<Ts>(ts)...);
}
//! MAX(a,b,c...) - equivalent to MAX( MAX(a,b), c ...)
template <typename TA, typename TB, typename... Ts> inline constexpr auto MAX(TA &&a, TB &&b, Ts &&...ts)
{
    return MAX(MAX(std::forward<TA>(a), std::forward<TB>(b)), std::forward<Ts>(ts)...);
}

#if 0 // this is in oexpr_post.h now, since it needs to handle opexpr too
//  ! SELECT(cond, A,B) - cond?A:B
template <typename SEL, typename A, typename B>
inline constexpr auto SELECT(SEL &&s, A &&a, B &&b)
{
    auto ws = wrap_param_to<bool>(std::forward<SEL>(s));
    auto wa = wrap_param(std::forward<A>(a));
    auto wb = wrap_param(std::forward<B>(b));
    return make_select(ws, wa, wb);
}
#endif

template <typename SEL, typename A, typename B> constexpr auto SELECT(SEL &&s, A &&a, B &&b);

/// @}

// make an sFunction<T> which contains and returns a specific value.
// These are self-contained std::function objects;
// Hopefully can reduce the number of distinct std::function objects by using
// this in all such cases...
template <typename SCALART> sFunction<SCALART> make_literal_sfunction(SCALART val)
{
    return sFunction<SCALART>::create([val](ECtx &) -> SCALART { return val; });
}

// make_literal_sfunction<bool> is a special case: we make a function
// object bound to one of sfunc_bool_false() or sfunc_bool_true()
// This makes check_sfunction_bool() possible.
//

inline constexpr bool sfunc_bool_false(ECtx &)
{
    return false;
}
inline constexpr bool sfunc_bool_true(ECtx &)
{
    return true;
}

template <> inline sFunction<bool> make_literal_sfunction<bool>(bool val)
{
    return sFunction<bool>(sFunction<bool>::FunctionWrapper, (void *)(val ? sfunc_bool_true : sfunc_bool_false));
}
// given an sFunction<bool>, return
//  0 if it will always return false,
//  1 if it will always return true,
// -1 if we can't tell.
int check_sfunction_bool(sFunction<bool> const &);

//// Convert to std::function
// make any expr (or anything that converts to expr ) into a std::function
// of the *specified* type, which should be one of the basic types bool,float,int,size_t, DType.
//
// Shortcuts:
// if the input is a function, it's returned;
// if the input is a scalar type, we use make_literal_sfunction.
//
template <typename T, typename EX> sFunction<T> wrap_as_function(EX &&a)
{
    // don't change it if it's already a function
    typedef std::remove_reference_t<EX> EXT;
    if constexpr (std::is_same_v<EXT, sFunction<T>>) {
        return std::forward<EX>(a);
    } else if constexpr (std::is_same_v<std::remove_const_t<EXT>, T> || std::is_arithmetic_v<EXT>) {
        return make_literal_sfunction<T>(a);
    } else if constexpr (std::is_same_v<EXT, expr<Variant::value, T>>) {
        // is an expr<value,T>
        return make_literal_sfunction<T>(a.getval());
    } else {
        auto ftmp = wrap_param_to<T>(std::forward<EX>(a));
        return sFunction<T>::create([ftmp](ECtx &e) -> T { return ftmp.eval(e); });
    }
}

// if wrap_as_function is given a expr which is 'value', it can just
// snarf the value out of it and call make_literal_sfunction.
// This requires that OTHERT can quietly convert to T.
// (note, this doesn't work, the template above seems to be always used, so a
// case was added there for T and expr<value,T>
/*
template <typename T, typename OTHERT>
inline sFunction<T> wrap_as_function( expr<Variant::value,OTHERT> const & a ){
	return make_literal_sfunction<T>( a.getval());
}
*/

#undef OEXP_ARITH
#undef OEXP_COMPARE
#undef OEXP_UNARYMATH
#undef OEXP_PREDICATE

//this class allows encapsulation of things which evaluate to an Op
// without the need to construct any Op; e.g,
//  INPUT_OF("operandname",int-expr)
//
//  .. so that e.g. you can write
//   DTYPE_OF( INPUT_OF( "operand", ITER_VAR("I")))
// .. in a replacement rule, and can also use INPUT_OF in a constraint.
//
//

enum class OpVnt : int {
    // template params of opexpr:
    parm, // <parm,void> : an operand_tag_t is within.
    input_of, // <input_of,tuple<OPA,EXPRB>>  : INPUT_OF(A,B)
    output_of, // <output_of,tuple<OPA,EXPRB>>  : OUTPUT_OF(A,B)
    select, // <select,tuple<COND,OPA,OPB>	 : SELECT(A,B)
};
template <OpVnt V, typename ARG> class opexpr {
};

} // namespace oExp

// The definitions in oExp namespace which depend on types
// defined in optimize.h are in the header 'oexpr_post.h'

#endif /* !PREPARE_DISABLED */
#endif /* OEXPR_H_ */
