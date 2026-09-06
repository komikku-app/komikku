//==============================================================================
//
// Copyright (c) 2020, 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef FLOAT16_H
#define FLOAT16_H

#include <algorithm>
#include <cassert>
#include <cstdint>
#include <cmath>
#include <limits>

#include "builtin_intrinsics.h"

#include "weak_linkage.h"
#include "macros_attribute.h"

PUSH_VISIBILITY(default)

struct API_EXPORT Float16 {
    constexpr Float16() : d(0) {}
    constexpr Float16(float f);
    constexpr Float16(const Float16 &f) : d(f.d) {}
    constexpr Float16 &operator=(Float16 f);

    constexpr bool is_zero() const;
    constexpr bool is_neg() const;
    constexpr bool is_inf() const;
    constexpr bool is_nan() const;
    constexpr bool is_subnorm() const;
    constexpr bool is_norm() const;
    constexpr bool is_finite() const;

    constexpr int16_t exp() const;
    constexpr int16_t frac() const;
    constexpr uint16_t raw() const { return d; }

    static constexpr int exp_max() { return 15; }
    static constexpr int exp_min() { return -14; }
    static constexpr int16_t bias() { return 15; }

    static constexpr Float16 zero(bool neg = false);
    static constexpr Float16 qnan();
    static constexpr Float16 snan();
    static constexpr Float16 inf(bool neg = false);

    static constexpr Float16 from_raw(uint16_t v);

    constexpr operator float() const;
    // same as ->float, but treats max. exp as a normal number
    // instead of inf/nan
    float to_float_alt() const;
    // same as from-float, but allows +/- 131008 range, using
    // exp=31 as normal.
    static Float16 from_float_alt(float v);

  private:
    explicit constexpr Float16(int sign, int exp, int frac);

    constexpr uint16_t sign_bit() const;
    constexpr uint16_t exp_bits() const;
    constexpr uint16_t frac_bits() const;

    static constexpr uint16_t make_exp_bits(uint16_t e);
    static constexpr uint16_t make_sign_bit(uint16_t s);
    static constexpr uint16_t make_frac_bits(uint16_t f);

    static constexpr uint16_t make_zero(bool neg);
    static constexpr uint16_t make_nan(bool quiet);
    static constexpr uint16_t make_inf(bool neg);
    static constexpr uint16_t make(int sign, int exp, int frac);

    static constexpr uint32_t round(uint32_t v, unsigned s);

    std::pair<int32_t, int32_t> force_norm() const;

    union {
        uint16_t d;
        struct {
            uint16_t mantissa : 10;
            uint16_t exponent : 5;
            uint16_t sign : 1;
        };
    };

    friend API_FUNC_EXPORT Float16 operator-(Float16 a);
    friend API_FUNC_EXPORT Float16 operator+(Float16 a, Float16 b);
    friend API_FUNC_EXPORT Float16 operator-(Float16 a, Float16 b);
    friend API_FUNC_EXPORT Float16 operator*(Float16 a, Float16 b);
};

POP_VISIBILITY()

inline constexpr Float16::Float16(float f) : d(0)
{
    union U {
        constexpr U(float f) : f(f) {}
        float f;
        uint32_t w;
    } const u(f);

    bool const neg = u.w & (uint32_t(1u) << 31u);
    int const exp_extract = (u.w >> 23u) & 0xFFu;
    uint32_t const frac_bits = u.w & 0x7FFFFFu;

    if (exp_extract == 0xFF) {
        if (frac_bits == 0)
            d = make_inf(neg);
        else
            d = make_nan(frac_bits & 0x400000u);
        return;
    }

    if (exp_extract == 0) {
        // It could be a subnormal number, but all single-precision subnormals
        // become 0 in half-precision.
        d = make_zero(neg);
        return;
    }

    int const exp = exp_extract - 127;
    int const frac = round(frac_bits | (uint32_t(1) << 23u), 23 - 10);
    d = make(neg, exp, frac);
}

inline constexpr Float16 &Float16::operator=(const Float16 f)
{
    d = f.d;
    return *this;
}

inline constexpr bool Float16::is_zero() const
{
    return (exp_bits() | frac_bits()) == 0x0000;
}

inline constexpr bool Float16::is_neg() const
{
    return sign_bit();
}

inline constexpr bool Float16::is_inf() const
{
    return exp_bits() == make_exp_bits(0x001F) && frac_bits() == 0x0000;
}

inline constexpr bool Float16::is_nan() const
{
    return exp_bits() == make_exp_bits(0x001F) && frac_bits() != 0x0000;
}

inline constexpr bool Float16::is_subnorm() const
{
    return exp_bits() == make_exp_bits(0x0000) && frac_bits() != 0x0000;
}

inline constexpr bool Float16::is_norm() const
{
    if (is_zero()) return true;
    return exp_bits() > make_exp_bits(0x0000) && exp_bits() < make_exp_bits(0x001F);
}

inline constexpr bool Float16::is_finite() const
{
    return is_norm() || is_subnorm();
}

inline constexpr int16_t Float16::exp() const
{
    assert(is_finite());
    int16_t const e = static_cast<int16_t>(exp_bits() >> 10u);
    return e != 0 ? e - bias() : e - bias() + 1;
}

inline constexpr int16_t Float16::frac() const
{
    assert(is_finite());
    uint16_t f = frac_bits();
    if (is_norm()) f |= uint32_t(1) << 10u;
    return static_cast<int16_t>(f);
}

inline constexpr Float16 Float16::zero(bool neg)
{
    return Float16::from_raw(make_zero(neg));
}

inline constexpr Float16 Float16::qnan()
{
    return Float16::from_raw(make_nan(true));
}

inline constexpr Float16 Float16::snan()
{
    return Float16::from_raw(make_nan(false));
}

inline constexpr Float16 Float16::inf(bool neg)
{
    return Float16::from_raw(make_inf(neg));
}

inline constexpr Float16 Float16::from_raw(uint16_t v)
{
    Float16 f;
    f.d = v;
    return f;
}

inline constexpr Float16::operator float() const
{
    uint32_t const sign = is_neg();

    // Reproduce the right type of inf/nan.
    if (exp_bits() == make_exp_bits(0x001F)) {
        union {
            uint32_t w;
            float f;
        } u{};
        u.w = 0;
        u.w |= sign << 31u;
        u.w |= uint32_t(0xFF) << 23u;
        // Copy over the msb of the fractional part.
        uint16_t const frac = frac_bits();
        uint32_t frac_msb = frac & (uint32_t(1u) << 9u);
        frac_msb <<= 12u; // RHS = 21 - 9
        u.w |= frac_msb;
        // Make sure the frac part doesn't become 0 for signaling NaNs.
        if ((frac & (frac_msb - 1)) != 0) u.w |= 1u;
        return u.f;
    }

    auto [e, f] = force_norm();
    if (f == 0) return sign != 0 ? -0.0f : 0.0f;

    float const v = ldexpf(f, e - 10);
    return sign ? -v : v;
}

inline constexpr Float16::Float16(int sign, int exp, int frac) : d(make(sign, exp, frac)) {}

inline constexpr uint16_t Float16::sign_bit() const
{
    return d & 0x8000u;
}

inline constexpr uint16_t Float16::exp_bits() const
{
    return d & 0x7C00u;
}

inline constexpr uint16_t Float16::frac_bits() const
{
    return d & 0x03FFu;
}

inline constexpr uint16_t Float16::make_sign_bit(uint16_t s)
{
    return static_cast<uint16_t>(!!s) << 15u;
}

inline constexpr uint16_t Float16::make_exp_bits(uint16_t e)
{
    return (e & 0x001Fu) << 10u;
}

inline constexpr uint16_t Float16::make_frac_bits(uint16_t f)
{
    return f & 0x03FFu;
}

inline constexpr uint16_t Float16::make_zero(bool neg)
{
    return make_sign_bit(neg) | make_exp_bits(0) | make_frac_bits(0);
}

inline constexpr uint16_t Float16::make_nan(bool quiet)
{
    uint16_t const f = quiet ? 0x0200 : 0x0100;
    return make_sign_bit(0) | make_exp_bits(0x001F) | make_frac_bits(f);
}

inline constexpr uint16_t Float16::make_inf(bool neg)
{
    return make_sign_bit(neg) | make_exp_bits(0x001F) | make_frac_bits(0x0000);
}

inline constexpr uint16_t Float16::make(int sign, int exp, int frac)
{
    // Treat frac as a fixed-point value with 10 fraction bits.
    if (frac == 0) {
        // Signed zero.
        return make_zero(sign);
    }
    assert(frac > 0);
    unsigned const clz = HEX_COUNT_LEADING_ZERO(frac);
    // For a finite, normalized non-zero number, clz should be 16+(16-11) = 21.
    int exp_inc = 21 - clz;
    if (exp + exp_inc > exp_max()) {
        // Number has a magnitude that is too large.
        return make_inf(sign);
    }
    if (exp + exp_inc < exp_min()) {
        // This number can become subnormal or zero.
        // safe_rshift will hit an assert if the shift is out of range
        // If we had an out of range shift, then we should just clip it to the range
        // Which should cause the frac to become 0 in either case
        int mask = static_cast<int>(hnnx::get_safe_shift_mask<int>());
        int shift_amount = exp_min() - exp - exp_inc;
        shift_amount = (shift_amount > mask) ? mask : shift_amount;
        frac = hnnx::safe_rshift(static_cast<unsigned>(frac), shift_amount);
        return make_sign_bit(static_cast<uint16_t>(sign)) | make_exp_bits(0) |
               make_frac_bits(static_cast<uint16_t>(frac));
    }

    if (exp_inc < 0) {
        frac = hnnx::safe_lshift(static_cast<unsigned>(frac), -exp_inc);
    } else if (exp_inc > 0) {
        frac = round(static_cast<uint32_t>(frac), exp_inc);
        // Rounding can change the most significant bit, so check it again.
        unsigned const clzr = HEX_COUNT_LEADING_ZERO(frac);
        assert(clzr == 20 || clzr == 21);
        if (clzr < 21) {
            frac = hnnx::safe_rshift(frac, (21 - clzr));
            exp_inc += (21 - clzr);
            // And the exponent check one more time...
            if (exp + exp_inc > exp_max()) return make_inf(sign);
        }
    }
    exp += exp_inc;
    exp += bias();
    return make_sign_bit(static_cast<uint16_t>(sign)) | make_exp_bits(static_cast<uint16_t>(exp)) |
           make_frac_bits(static_cast<uint16_t>(frac));
}

inline constexpr uint32_t Float16::round(uint32_t v, unsigned s)
{
    if (s == 0) return v;
    unsigned const out_msb = hnnx::safe_lshift(1u, (s - 1));
    if ((v & out_msb) == 0) {
        // Round down.
        return hnnx::safe_rshift(v, s);
    }
    if ((v & (out_msb - 1)) == 0) {
        // It's a tie, round to even.
        v = hnnx::safe_rshift(v, s);
        return v & 1u ? v + 1 : v;
    }
    // Round up.
    return hnnx::safe_rshift(v, s) + 1;
}

inline std::pair<int32_t, int32_t> Float16::force_norm() const
{
    if (is_zero()) return std::make_pair(0, 0);
    uint32_t f = frac_bits();
    int32_t e = static_cast<int32_t>(exp_bits() >> 10u);
    if (e == 0) {
        // Subnormal number.
        assert(f != 0);
        unsigned const clz = HEX_COUNT_LEADING_ZERO(f) - 16; // Pretend we have 16 bits.
        // Shift f left so that the first bit 1 is at position 10 from lsb
        // (assuming that lsb is at 0).
        e = -14 - (clz - 5);
        f = hnnx::safe_lshift(f, clz - 5);
    } else {
        e -= bias();
        f |= uint32_t(1) << 10u;
    }
    return std::make_pair(e, f);
}

constexpr Float16 operator""_f16(long double v)
{
    return Float16(static_cast<float>(v));
}

PUSH_VISIBILITY(default)

template <> class API_EXPORT std::numeric_limits<Float16> {
  public:
    static constexpr bool is_specialized = true;
    static constexpr bool is_signed = true;
    static constexpr bool is_integer = false;
    static constexpr bool is_exact = false;
    static constexpr bool has_infinity = true;
    static constexpr bool has_quiet_NaN = true;
    static constexpr bool has_signaling_NaN = true;
    static constexpr auto has_denorm = std::denorm_present;
    static constexpr bool has_denorm_loss = false; // libc++
    static constexpr auto round_style = std::round_to_nearest;
    static constexpr bool is_iec559 = true;
    static constexpr bool is_bounded = true;
    static constexpr bool is_modulo = false;
    static constexpr int digits = 11;
    static constexpr int digits10 = 3; // floor((digits-1) * log10(2))
    static constexpr int max_digits10 = 5; // ceil(digits * log10(2) + 1)
    static constexpr int radix = 2;
    static constexpr int min_exponent = -13;
    static constexpr int min_exponent10 = -4; // min normal =~ 0.000061035
    static constexpr int max_exponent = 15;
    static constexpr int max_exponent10 = 5; // largest finite val = 65504
    static constexpr bool traps = false;
    static constexpr bool tinyness_before = false; // libc++

    static constexpr Float16 min() noexcept; // returns min positive normal
    static constexpr Float16 lowest() noexcept; // returns true min
    static constexpr Float16 max() noexcept; // max positive
    static constexpr Float16 epsilon() noexcept; // step at 1.0
    static constexpr Float16 round_error() noexcept; // 0.5
    static constexpr Float16 infinity() noexcept;
    static constexpr Float16 quiet_NaN() noexcept;
    static constexpr Float16 signaling_NaN() noexcept;
    static constexpr Float16 denorm_min() noexcept; // min positive denorm
};

POP_VISIBILITY()

constexpr Float16 std::numeric_limits<Float16>::min() noexcept
{
    // 2^-14 * (1 + 0/1024)     ; 0 00001 0000000000
    return Float16::from_raw(0x0400);
}

constexpr Float16 std::numeric_limits<Float16>::lowest() noexcept
{
    // -2^15 * (1 + 1023/1024)  ; 1 11110 1111111111
    return Float16::from_raw(0xfbff); // -65504
}

constexpr Float16 std::numeric_limits<Float16>::max() noexcept
{
    // 2^15 * (1 + 1023/1024)   ; 0 11110 1111111111
    return Float16::from_raw(0x7bff); // 65504
}

constexpr Float16 std::numeric_limits<Float16>::epsilon() noexcept
{
    // 2^-10 * (1 + 0/1024)     ; 0 00101 0000000000
    return Float16::from_raw(0x1400); // next_after_1.0 - 1.0
}

constexpr Float16 std::numeric_limits<Float16>::round_error() noexcept
{
    // 2^-1 * (1 + 0/1024)      ; 0 01110 0000000000
    return Float16::from_raw(0x3800); // 0.5
}

constexpr Float16 std::numeric_limits<Float16>::infinity() noexcept
{
    return Float16::inf(false);
}

constexpr Float16 std::numeric_limits<Float16>::quiet_NaN() noexcept
{
    return Float16::qnan();
}

constexpr Float16 std::numeric_limits<Float16>::signaling_NaN() noexcept
{
    return Float16::snan();
}

constexpr Float16 std::numeric_limits<Float16>::denorm_min() noexcept
{
    return Float16::from_raw(0x0001);
}

#endif // FLOAT16_H
