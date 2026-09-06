//==============================================================================
//
// Copyright (c) 2018 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef CONVERSIONS_H
#define CONVERSIONS_H

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <cmath>
#include <limits>

#include "builtin_intrinsics.h"

#ifdef __hexagon__
#include "hexagon_protos.h"
#endif

#include "float16.h"

#if defined(__clang__)
#define ATTR_NO_SANITIZE(CATEGORY) __attribute__((no_sanitize(CATEGORY)))
#else
#define ATTR_NO_SANITIZE(CATEGORY) /*empty */
#endif

namespace hnnx {

namespace scast {

// for a given floating type F, and a integer type TI,
//  intrange_within_float<F,TI>::max()
// generates the largest value representable in type F which will fit into TI without overflow.
// in many cases this is F(std::numeric_limits<TI>::max()),
// but there are exceptions when the mantissa of F is narrower than TI; in those cases we
// want the representable value which is smaller than the integer's max value, not the nearest:
//     F        TI
//   Float16  int16   32752.0                (0x7ff0)
//   Float15  uint16  65504.0                (0xffe0)
//   float    int32   2147483520.0           (0x7fffff80)
//   float    uint32  4294967040.0           (0xFFFFFF00)
//   float    int64   9.223371487e18         (0x7fff_ff80_0000_0000)
//   float    uint64  1.844674297e+19        (0xFFFF_FF00__0000_0000)
//   double   int64   9223372036854774784.0  (0x7FFF_FFFF_FFFF_FC00)
//   double   uint64  18446744073709549568.0 (0xFFFF_FFFF_FFFF_F800)
//
// All of the 'min' limits are zero or powers of 2, so those can be converted
// directly from std::numeric_limits<TI>::min()
//
//
template <typename F, typename TI> struct intrange_within_float {
};

// LCOV_EXCL_START [SAFTYSWCCB-1736] constexprs resolved during compile time
template <typename TI> struct intrange_within_float<Float16, TI> {
    static_assert(std::numeric_limits<TI>::is_integer);
    static inline constexpr Float16 max()
    {
        if constexpr (sizeof(TI) < 2) {
            return Float16(std::numeric_limits<TI>::max());
        } else if constexpr (sizeof(TI) == 2) {
            return std::numeric_limits<TI>::is_signed ? Float16(32752.0f) : Float16(65504.0f);
        } else {
            return std::numeric_limits<TI>::is_signed ? Float16(-65504.0f) : Float16(65504.0f);
        }
    }
    // 'min' value of integer range is always exactly representable
    static inline constexpr Float16 min() { return Float16(std::numeric_limits<TI>::min()); }
};

template <typename TI> struct intrange_within_float<float, TI> {
    static_assert(std::numeric_limits<TI>::is_integer);
    static inline constexpr float max()
    {
        if constexpr (sizeof(TI) < 4) {
            return float(std::numeric_limits<TI>::max());
        } else if constexpr (sizeof(TI) == 4) {
            return std::numeric_limits<TI>::is_signed ? 2147483520.0f : 4294967040.0f;
        } else {
            static_assert(sizeof(TI) == 8);
            return std::numeric_limits<TI>::is_signed ? 9.223371487e18f : 1.844674297e+19f;
        }
    }
    // 'min' value of integer range is always exactly representable
    static inline constexpr float min() { return float(std::numeric_limits<TI>::min()); }
};

template <typename TI> struct intrange_within_float<double, TI> {
    static_assert(std::numeric_limits<TI>::is_integer);
    static inline constexpr double max()
    {
        if constexpr (sizeof(TI) < 8) {
            return double(std::numeric_limits<TI>::max());
        } else {
            static_assert(sizeof(TI) == 8);
            return std::numeric_limits<TI>::is_signed ? 9223372036854774784.0 : 18446744073709549568.0;
        }
    }
    // 'min' value of integer range is always exactly representable
    static inline constexpr float min() { return double(std::numeric_limits<TI>::min()); }
};
// LCOV_EXCL_STOP

template <typename TOUT, typename TIN> struct satcast_helper {
    static_assert(std::numeric_limits<TOUT>::is_specialized && std::numeric_limits<TIN>::is_specialized);
    static inline TOUT constexpr op(TIN val)
    {
        if constexpr (!std::numeric_limits<TOUT>::is_integer) { // convert to a float
            return TOUT(val);
        } else {
            constexpr bool OUTS = std::numeric_limits<TOUT>::is_signed;
            if constexpr (std::numeric_limits<TIN>::is_integer) {
                // integer to integer.
                // widening? or same width, same signedness?
                constexpr bool INS = std::numeric_limits<TIN>::is_signed;
                if (sizeof(TOUT) > sizeof(TIN) || (sizeof(TOUT) == sizeof(TIN) && OUTS == INS)) {
                    // if the output is unsigned and the input < 0, return 0
                    // otherwise it's a normal cast.
                    return (!OUTS && INS && val < 0) ? TOUT(0) : TOUT(val);
                } else if (sizeof(TOUT) == sizeof(TIN)) {
                    if (!OUTS) { // same size, different signs
                        return (val < 0) ? (TOUT)0 : (TOUT)val; // signed->unsigned
                    } else {
                        constexpr TIN lim = std::numeric_limits<TOUT>::max();
                        return (val > lim) ? (TOUT)lim : (TOUT)val;
                    }
                } else {
                    // narrowing conversion
                    if (!OUTS) {
                        constexpr TIN m = std::numeric_limits<TOUT>::max();
                        return (val < 0) ? TOUT(0) : (val > m) ? TOUT(m) : TOUT(val);
                    } else {
                        constexpr TIN mn = INS ? std::numeric_limits<TOUT>::min() : 0;
                        constexpr TIN mx = std::numeric_limits<TOUT>::max();
                        return (val < mn) ? TOUT(mn) : (val > mx) ? TOUT(mx) : TOUT(val);
                    }
                }
            } else { // float to integer
                if constexpr (sizeof(TOUT) <= sizeof(int32_t)) {
                    if constexpr (OUTS) {
                        constexpr TIN loval = intrange_within_float<TIN, int32_t>::min();
                        constexpr TIN hival = intrange_within_float<TIN, int32_t>::max();
                        int32_t const tmp = (int32_t)std::max(loval, std::min(hival, val));
                        return satcast_helper<TOUT, int32_t>::op(tmp);
                    } else {
                        constexpr TIN loval = 0.0;
                        constexpr TIN hival = intrange_within_float<TIN, uint32_t>::max();
                        uint32_t const tmp = (uint32_t)std::max(loval, std::min(hival, val));
                        return satcast_helper<TOUT, uint32_t>::op(tmp);
                    }
                } else { // 64-bit output assumed
                    constexpr TIN loval = intrange_within_float<TIN, TOUT>::min();
                    constexpr TIN hival = intrange_within_float<TIN, TOUT>::max();
                    return (TOUT)std::max(loval, std::min(hival, val));
                }
            }
        }
    }
};
// specialize for conversion to same
template <typename TT> struct satcast_helper<TT, TT> {
    static_assert(std::numeric_limits<TT>::is_specialized);
    static inline TT constexpr op(TT val) { return val; }
};

#ifdef __hexagon__

// saturate to types <= int.
template <typename T> struct q6_sat_int {
};
template <> struct q6_sat_int<int8_t> {
    static inline int op(int x) { return Q6_R_satb_R(x); }
};
template <> struct q6_sat_int<uint8_t> {
    static inline int op(int x) { return Q6_R_satub_R(x); }
};
template <> struct q6_sat_int<int16_t> {
    static inline int op(int x) { return Q6_R_sath_R(x); }
};
template <> struct q6_sat_int<uint16_t> {
    static inline int op(int x) { return Q6_R_satuh_R(x); }
};

// TODO: these should be done again for 'long' if long is also 32 bits.
#if 0 // NOTE: we can't really do this unless intrinsics are constexpr
template <> struct satcast_helper<uint8_t, int> {
    static inline uint8_t /*constexpr*/ op(int val)
    {
        return Q6_R_satub_R(val);
    }
};
template <> struct satcast_helper<int8_t, int> {
    static inline int8_t /*constexpr*/ op(int val) { return Q6_R_satb_R(val); }
};
template <> struct satcast_helper<uint16_t, int> {
    static inline uint16_t /*constexpr*/ op(int val)
    {
        return Q6_R_satuh_R(val);
    }
};
template <> struct satcast_helper<int16_t, int> {
    static inline int16_t /*constexpr*/ op(int val) { return Q6_R_sath_R(val); }
};
#endif

#endif
} // end namespace scast

} // namespace hnnx

/**
 * @brief saturate_cast<TOUT,TIN>( TIN val ) will work on any two numeric types;
 * if the input is outside the numeric range of the output type, it
 * will be range-limited.
 *
 * it works as follows:
 *   * if TOUT is a floating type, the operation is the same as the C++ cast.
 *   * if TOUT is integer and TIN is float, the input is first converted
 *    to one of int32,uint32, int64, uint64 ensuring that out-of-range values
 *    are clipped; and then converted to the output type as below (if it is smaller
 *    than 32 bits) (The 2-step conversion is intended to work well when things
 *    are specialized to support native hexagon ops).
 *  * Otherwise they are both integers.
 *    - If the output width is larger than the input (or if they are the same size
 *      and of the same signedness):
 *        * if the output is unsigned, and the input is < 0, the result is zero
 *        * otherwise the result is the same as a C++ cast (all values representable)
 *    - Otherwise, it is a saturating cast; values are limited to the range of TOUT.
 */
template <typename TOUT, typename TIN> inline constexpr TOUT saturate_cast(TIN val)
{
    return hnnx::scast::satcast_helper<TOUT, TIN>::op(val);
}

/**
 * @brief T saturate_round<T>( float val )
 * round val to nearest int, and saturate to range of T.
 *
 * T must be an integer type, at most 32 bits.
 */
// For general C platform, we need to clip the range before converting to int;
// for hexagon the conversions saturate.
//
#ifndef __hexagon__
template <typename TOUT> inline TOUT saturate_round(float val)
{
    static_assert(sizeof(TOUT) <= 8 && std::numeric_limits<TOUT>::is_integer);
    return saturate_cast<TOUT>(std::nearbyintf(val));
}

#else
template <typename TOUT> inline TOUT saturate_round(float val)
{
    static_assert(sizeof(TOUT) <= 8 && std::numeric_limits<TOUT>::is_integer);
    if constexpr ((sizeof(TOUT) == 8) && !std::numeric_limits<TOUT>::is_signed) {
        // convert to unsigned u64, rounding, saturating
        return Q6_P_convert_sf2ud_R(val);
    } else if constexpr ((sizeof(TOUT) == 8) && std::numeric_limits<TOUT>::is_signed) {
        // convert to int64, rounding
        return Q6_P_convert_sf2d_R(val);
    } else if constexpr ((sizeof(TOUT) == 4) && !std::numeric_limits<TOUT>::is_signed) {
        // convert to unsigned u32, rounding, saturating
        return Q6_R_convert_sf2uw_R(val);
    } else {
        // convert to int32,rounding;
        int const r = Q6_R_convert_sf2w_R(val);
        if constexpr (sizeof(TOUT) < 4) return static_cast<TOUT>(hnnx::scast::q6_sat_int<TOUT>::op(r));
        return static_cast<TOUT>(r); // LCOV_EXCL_LINE [SAFTYSWCCB-1736]
    }
}
#endif

namespace hnnx {

/**
 * @brief 'proper' compare of any two integer types
 *  proper_gt( a, b) => a > b;
 *    E.g. if a is unsigned and b is signed, the operation checks to see if b is < 0;
 *    if so, the result is true; otherwise an unsigned compare is done: a > (unsigned)b
 *
 */
namespace prpercmp {

/**
 * @brief if both A and B are either *int*, or smaller than int,
 *   then promote them both to int and compare them.
 *
 * otherwise, if TA is wider than TB, (or the same, with TA unsigned):
 *    promote b to TA, and then compare them.
 *    Exception, if TA is unsigned and TB is signed and b < 0; then a<b always.
 * otherwise, TB is wider than TA (or the same with TA signed):
 *   promote a to TB, and then compare them.
 *   Exception, if TB is unsigned and TA is signed and a < 0.
 *
 */

template <typename TA, typename TB> struct proper_cmp_helper {
    static_assert(std::numeric_limits<TA>::is_integer && std::numeric_limits<TB>::is_integer);
    static const bool ASIGNED = std::numeric_limits<TA>::is_signed;
    static const bool BSIGNED = std::numeric_limits<TB>::is_signed;

    // compare by promoting both to int, when...
    static const bool CMP_AS_INT = (sizeof(TA) < sizeof(int) || (sizeof(TA) == sizeof(int) && ASIGNED)) &&
                                   (sizeof(TB) < sizeof(int) || (sizeof(TB) == sizeof(int) && BSIGNED));
    // otherwise, compare by promoting B to A when ...
    static const bool B_TO_A = sizeof(TA) > sizeof(TB) || (sizeof(TA) == sizeof(TB) && !ASIGNED);
    // otherwise, compare by promoting A to B

    static inline bool constexpr eq(TA a, TB b)
    {
        if (CMP_AS_INT) {
            return (int)a == (int)b;
        } else if (B_TO_A) {
            if (!ASIGNED && BSIGNED && b < 0) return false;
            return a == (TA)b;
        } else {
            if (!BSIGNED && ASIGNED && a < 0) return false;
            return (TB)a == b;
        }
    }
    static inline bool constexpr lt(TA a, TB b)
    {
        if (CMP_AS_INT) {
            return (int)a < (int)b;
        } else if (B_TO_A) {
            if (!ASIGNED && BSIGNED && b < 0) return false; // a < b  always false if  b<0
            return a < (TA)b;
        } else {
            if (!BSIGNED && ASIGNED && a < 0) return true; // a < b  always true if  a<0
            return (TB)a < b;
        }
    }
};
/**
 * @brief specialize for comparison to same type
 */
template <typename T> struct proper_cmp_helper<T, T> {
    static_assert(std::numeric_limits<T>::is_integer);
    static inline bool constexpr eq(T a, T b) { return a == b; }
    static inline bool constexpr lt(T a, T b) { return a < b; }
};

} // end namespace prpercmp

} // namespace hnnx

/**
 * @brief 'proper' compare of any two integer types, respecting signedness and actual numeric value.
 *  proper_eq(a,b) => a == b;
 *
 * E.g. if a is signed and <0, and b is unsigned, result will always be false.
 *
 */

template <typename TA, typename TB> inline bool constexpr proper_eq(TA a, TB b)
{
    return hnnx::prpercmp::proper_cmp_helper<TA, TB>::eq(a, b);
}
/**
 * @brief 'proper' compare of any two integer types, respecting signedness and actual numeric value
 *  proper_ne(a,b) => !proper_eq(a,b);
 */
template <typename TA, typename TB> inline bool constexpr proper_ne(TA a, TB b)
{
    return !hnnx::prpercmp::proper_cmp_helper<TA, TB>::eq(a, b);
}
/**
 * @brief 'proper' compare of any two integer types, respecting signedness and actual numeric value
 *  proper_lt(a,b) => a<b;
 */
template <typename TA, typename TB> inline bool constexpr proper_lt(TA a, TB b)
{
    return hnnx::prpercmp::proper_cmp_helper<TA, TB>::lt(a, b);
}
/**
 * @brief 'proper' compare of any two integer types, respecting signedness and actual numeric value
 *  proper_ge(a,b) => a>=b;
 */
template <typename TA, typename TB> inline bool constexpr proper_ge(TA a, TB b)
{
    return !hnnx::prpercmp::proper_cmp_helper<TA, TB>::lt(a, b);
}
/**
 * @brief 'proper' compare of any two integer types, respecting signedness and actual numeric value
 *  proper_gt(a,b) => a>b;
 */
template <typename TA, typename TB> inline bool constexpr proper_gt(TA a, TB b)
{
    return hnnx::prpercmp::proper_cmp_helper<TB, TA>::lt(b, a);
}
/**
 * @brief 'proper' compare of any two integer types, respecting signedness and actual numeric value
 *  proper_le(a,b) => a<=b;
 */
template <typename TA, typename TB> inline bool constexpr proper_le(TA a, TB b)
{
    return !hnnx::prpercmp::proper_cmp_helper<TB, TA>::lt(b, a);
}
/**
 * @brief x >= lo && x < limit, using proper compares
 */
template <typename TA, typename TB, typename TC> inline bool constexpr proper_inrange(TA x, TB lo, TC limit)
{
    return proper_ge<TA, TB>(x, lo) && proper_lt<TA, TC>(x, limit);
}

/**
 * @brief x >= lo && x <= hi, using proper compares
 */
template <typename TA, typename TB, typename TC> inline bool constexpr proper_inrange_closed(TA x, TB lo, TC hi)
{
    return proper_ge<TA, TB>(x, lo) && proper_le<TA, TC>(x, hi);
}

/**
 * @brief find the 'width' of an unsigned value (# of bits needed to contain it)
 * this is floor( log2(x))+1
 *  (and 0 when x = 0)
 *
 */
inline int constexpr binary_bitwidth(unsigned x)
{
    return (x == 0) ? 0 : (sizeof(unsigned) * 8 - HEX_COUNT_LEADING_ZERO(x));
}
/**
 * @brief find the 'width' of an unsigned long value (# of bits needed to contain it)
 * this is floor( log2(x))+1
 *  (and 0 when x = 0)
 *
 */
inline int constexpr binary_bitwidth(unsigned long x)
{
    return (x == 0) ? 0 : (sizeof(unsigned long) * 8 - HEX_COUNT_LEADING_ZERO_UL(x));
}
/**
 * @brief find the 'width' of an unsigned long long value (# of bits needed to contain it)
 * this is floor( log2(x))+1
 *  (and 0 when x = 0)
 *
 */
inline int constexpr binary_bitwidth(unsigned long long x)
{
    return (x == 0) ? 0 : (sizeof(unsigned long long) * 8 - HEX_COUNT_LEADING_ZERO_ULL(x));
}
/**
 * @brief saturating u32+u32 add
 */
inline uint32_t /*constexpr*/ addu32_sat(uint32_t a, uint32_t b)
{
    uint64_t const prod = (uint64_t)a + b;
    return saturate_cast<uint32_t>(prod);
}

/**
 * @brief saturating i32+i32 add
 */
inline int32_t /*constexpr*/ addi32_sat(int32_t a, int32_t b)
{
#ifdef __hexagon__
    return Q6_R_add_RR_sat(a, b);
#else
    int64_t prod = (int64_t)a + b;
    return saturate_cast<int32_t>(prod);
#endif
}

/**
 * @brief saturating u32xu32 multiply
 */
inline uint32_t constexpr mulu32_sat(uint32_t a, uint32_t b)
{
    uint64_t const prod = (uint64_t)a * b;
    return saturate_cast<uint32_t>(prod);
}

/**
 * @brief saturating i32xi32 multiply
 */
inline int32_t constexpr muli32_sat(int32_t a, int32_t b)
{
    int64_t const prod = (int64_t)a * b;
    return saturate_cast<int32_t>(prod);
}

/**
 * @brief saturating u64xu64 multiply
 */
inline uint64_t /*constexpr*/ mulu64_sat(uint64_t a, uint64_t b)
{
    uint64_t prod = 0;
    if (HEX_MUL_OVERFLOW(a, b, &prod)) {
        prod = std::numeric_limits<uint64_t>::max();
    }
    return prod;
}

/**
 * @brief saturating i64xi64 multiply
 */
inline int64_t /*constexpr*/ muli64_sat(int64_t a, int64_t b)
{
    int64_t prod = 0;
    if (HEX_MUL_OVERFLOW(a, b, &prod)) {
        prod = (int64_t(uint64_t(a) ^ uint64_t(b)) >= 0) ? std::numeric_limits<int64_t>::max()
                                                         : std::numeric_limits<int64_t>::min();
    }
    return prod;
}
/**
 * @brief add unsigned+unsigned->unsigned, escaping 'unsigned overflow' checks
 */
ATTR_NO_SANITIZE("unsigned-integer-overflow")
inline unsigned constexpr addu32_modular(unsigned a, unsigned b)
{
    return a + b;
}
/**
 * @brief subtract unsigned-unsigned->unsigned, escaping 'unsigned overflow' checks
 * For '-unsigned_var', use subu32_modular(0,unsigned_var)
 */
ATTR_NO_SANITIZE("unsigned-integer-overflow")
inline unsigned constexpr subu32_modular(unsigned a, unsigned b)
{
    return a - b;
}
/**
 * @brief multiply unsigned*unsigned->unsigned, escaping 'unsigned overflow' checks
 */
ATTR_NO_SANITIZE("unsigned-integer-overflow")
inline unsigned constexpr mulu32_modular(unsigned a, unsigned b)
{
    return a * b;
}
/**
 * @brief mul-add u32*u32+u32->u32, escaping 'unsigned overflow' checks
 */
ATTR_NO_SANITIZE("unsigned-integer-overflow")
inline unsigned constexpr muladdu32_modular(unsigned a, unsigned b, unsigned c)
{
    return a * b + c;
}

/**
 * @brief add u64+u64->u64, escaping 'unsigned overflow' checks
 */
ATTR_NO_SANITIZE("unsigned-integer-overflow")
inline uint64_t constexpr addu64_modular(uint64_t a, uint64_t b)
{
    return a + b;
}

/**
 * @brief subtract u64-u64->u64, escaping 'unsigned overflow' checks
 */
ATTR_NO_SANITIZE("unsigned-integer-overflow")
inline uint64_t constexpr subu64_modular(uint64_t a, uint64_t b)
{
    return a - b;
}
/**
 * @brief mul u64*u64->u64, escaping 'unsigned overflow' checks
 */
ATTR_NO_SANITIZE("unsigned-integer-overflow")
inline uint64_t constexpr mulu64_modular(uint64_t a, uint64_t b)
{
    return a * b;
}

/**
 * @brief 'image' conversion from TIN to TOUT (which must be the same size)
 * e.g. image_convert<unsigned,float>( 1.25f) -> 0x3fa00000
 */

template <typename TOUT, typename TIN> inline constexpr TOUT image_convert(TIN x)
{
    static_assert(sizeof(TOUT) == sizeof(TIN));
    static_assert(std::is_trivially_copyable_v<TOUT>);
    static_assert(std::is_trivially_copyable_v<TIN>);
    static_assert(std::is_trivially_constructible_v<TOUT>);
    TOUT out;
    std::memcpy(&out, &x, sizeof(TOUT));
    return out;
}

// round up A to a multiple of B.
// b is expected to be > 0 even if signed.

template <typename TD> inline constexpr size_t round_up(size_t a, TD b)
{
    static_assert(std::is_integral_v<TD>, "round_up can only apply to integer types");
    // for b being  a power of 2, this should compile as (a+(b-1)) &~(b-1)
    return b * ((a + (b - 1)) / b);
}
// for int, b is expected to be > 0;
// this will work for negative a, e.g. round_up(-53,10) -> -50
template <typename TD> inline constexpr size_t round_up(int a, TD b)
{
    static_assert(std::is_integral_v<TD>, "round_up can only apply to integer types");
    int const bi = b;
    int const tmp = a + ((a > 0) ? (bi - 1) : 0);
    return bi * (tmp / bi);
}

#endif /*CONVERSIONS_H*/
