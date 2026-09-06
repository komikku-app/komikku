//==============================================================================
//
// Copyright (c) 2020-2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef INTRINSICS_H
#define INTRINSICS_H 1

#if !defined(__hexagon__) && !defined(_WIN32)
#include <sched.h>
#endif

#include "log.h"

#ifdef __hexagon__
#include "hexagon_types.h"
#endif
#include "hexagon_protos.h"

#include "afuncs.h"

#include "check_hvx.h"

#ifdef _WIN32
#include <thread>
#endif

///////// inline implementation of trivial HVX intrinsics, reduces code size significantly.
#if !defined(__hexagon__) && defined(Q6_V_lo_W)
namespace hnnx {
inline HVX_Vector q6op_V_lo_W(HVX_VectorPair const &w)
{
    return w.v[0];
}
inline HVX_Vector q6op_V_hi_W(HVX_VectorPair const &w)
{
    return w.v[1];
}
inline HVX_VectorPair q6op_W_vcombine_VV(HVX_Vector const &v1, HVX_Vector const &v0)
{
    HVX_VectorPair result;
    result.v[0] = v0;
    result.v[1] = v1;
    return result;
}
#undef Q6_V_lo_W
#define Q6_V_lo_W(W) hnnx::q6op_V_lo_W(W)
#undef Q6_V_hi_W
#define Q6_V_hi_W(W) hnnx::q6op_V_hi_W(W)
#undef Q6_W_vcombine_VV
#define Q6_W_vcombine_VV(V1, V0) hnnx::q6op_W_vcombine_VV(V1, V0)
} // namespace hnnx

//
// workaround for defect in libnative Q6_Vw_vadd_VwVwQ_carry, Q6_Vw_vsub_VwVwQ_carry [QTOOL-108346]
//
namespace hnnx {
template <bool SUBTRACT>
HVX_Vector do_Vw_vaddORsub_VwVwQ_carry(HVX_Vector const &vu, HVX_Vector const &vv, HVX_VectorPred *qcarry)
{
    using u64 = unsigned long long;
    HVX_Vector result;
    HVX_VectorPred carry_out; // use local for this, in case qcarry aliases one of the inputs.
    for (unsigned i = 0; i < 32; i++) {
        unsigned v_val = vv.uw[i];
        if (SUBTRACT) v_val = ~v_val;
        u64 sum = u64(vu.uw[i]) + u64(v_val) + (qcarry->uw[i] & 1u);
        result.uw[i] = unsigned(sum);
        carry_out.uw[i] = (sum > 0xFFFFFFFFu) ? 0x01010101 : 0;
    }
    *qcarry = carry_out;
    return result;
}
#undef Q6_Vw_vadd_VwVwQ_carry
#define Q6_Vw_vadd_VwVwQ_carry(VA, VB, QP) hnnx::do_Vw_vaddORsub_VwVwQ_carry<false>(VA, VB, QP)
#undef Q6_Vw_vsub_VwVwQ_carry
#define Q6_Vw_vsub_VwVwQ_carry(VA, VB, QP) hnnx::do_Vw_vaddORsub_VwVwQ_carry<true>(VA, VB, QP)
} // namespace hnnx
// end [QTOOL-108346]

#endif
////////////////////////////////////////

#include "hvx_mathops.h"
#include "macros_attribute.h"

typedef struct {
    HVX_Vector val[2];
} HVX_Vector_x2;

typedef struct {
    HVX_Vector val[3];
} HVX_Vector_x3;
typedef struct {
    HVX_Vector val[4];
} HVX_Vector_x4;

typedef struct {
    HVX_VectorPair val[2];
} HVX_VectorPair_x2;

typedef struct {
    HVX_VectorPair val[3];
} HVX_VectorPair_x3;

typedef struct {
    HVX_VectorPair val[4];
} HVX_VectorPair_x4;

// Splat 32b float to vector
inline ALWAYSINLINE HVX_Vector q6op_V_vsplat_float32(const float val)
{
    union {
        float as_f32;
        int32_t as_i32;
    } bitCast;
    bitCast.as_f32 = val;
    return Q6_V_vsplat_R(bitCast.as_i32);
}

// for splat 16b IEEE float to vector
union bitcast_fp16 {
    Float16 as_fp16;
    int16_t as_i16;
};

inline ALWAYSINLINE HVX_Vector q6op_V_vsplat_float16(const Float16 val)
{
    const bitcast_fp16 bitcast{val};
    return Q6_Vh_vsplat_R(bitcast.as_i16);
}

// 32x32 fractional multiply - expands to two ops
//  equiv to :
//    p  = (a*b + (1<<30)) >> 31     [with rounding]
//    p  = a*b >> 31     			[without rounding]
// The 'sat' only takes effect when both inputs
// are -0x80000000 and causes the result to saturate to 0x7fffffff

inline HVX_Vector q6op_Vw_vmpy_VwVw_s1_rnd_sat(HVX_Vector vu, HVX_Vector vv)
{
    return Q6_Vw_vmpyoacc_VwVwVh_s1_rnd_sat_shift(Q6_Vw_vmpye_VwVuh(vu, vv), vu, vv);
}

inline HVX_Vector q6op_Vw_vmpy_VwVw_s1_sat(HVX_Vector vu, HVX_Vector vv)
{
    return Q6_Vw_vmpyoacc_VwVwVh_s1_sat_shift(Q6_Vw_vmpye_VwVuh(vu, vv), vu, vv);
}

#ifdef __hexagon__
// HEXAGON

//Unaligned vector load

inline HVX_Vector q6op_V_vldu_A(void const *addr)
{
#pragma pack(push, 1)
    struct varr {
        HVX_Vector v;
    } const *pp;
#pragma pack(pop)
    pp = (struct varr const *)addr;
    return pp->v;
}

// unaligned vector store.

inline void q6op_vstu_AV(void *addr, HVX_Vector v)
{
#pragma pack(push, 1)
    struct varr {
        HVX_Vector v;
    } * pp;
#pragma pack(pop)
    pp = (struct varr *)addr;
    pp->v = v;
}

// conditional unaligned vector store.

inline void q6op_vstu_QAV(HVX_VectorPred Qmask, void *addr, HVX_Vector v)
{
    unsigned int const bL = (uintptr_t)addr;
    HVX_Vector *addr_v = (HVX_Vector *)addr;
    HVX_Vector mask = Q6_V_vand_QR(Qmask, -1);
    HVX_Vector vzero = Q6_V_vzero();
    HVX_Vector vx = Q6_V_vlalign_VVR(v, v, bL);
    HVX_Vector maskL = Q6_V_vlalign_VVR(mask, vzero, bL);
    HVX_Vector maskH = Q6_V_vlalign_VVR(vzero, mask, bL);
    HVX_Vector QL = Q6_Q_vcmp_gt_VubVub(maskL, vzero);
    HVX_Vector QH = Q6_Q_vcmp_gt_VubVub(maskH, vzero);
    Q6_vmem_QRIV(QL, &addr_v[0], vx);
    if ((bL & 127) != 0) {
        Q6_vmem_QRIV(QH, &addr_v[1], vx);
    }
}

// Unaligned unaligned load/store:
// vmemu( void *) can be assigned to, or read from,
// and unaligned load/store will be used.
// vmem( void const *) can be read from.
//
#pragma pack(push, 1)
struct unaligned_vector_wrapper {
    HVX_Vector v;
    inline operator HVX_Vector() const { return v; };
    inline HVX_Vector operator=(HVX_Vector val)
    {
        v = val;
        return val;
    }
    inline HVX_Vector operator=(unaligned_vector_wrapper const &rhs)
    {
        if (this != &rhs) {
            v = rhs.v;
        }
        return *this;
    }
}; // <- so the struct is not considered aligned
#pragma pack(pop)
inline HVX_Vector vmemu(void const *addr)
{
    return ((unaligned_vector_wrapper const *)addr)->v;
}
inline unaligned_vector_wrapper &vmemu(void *addr)
{
    return *(unaligned_vector_wrapper *)addr;
}

// this stores the first n bytes from vector vin to address 'addr'.
// n must be in range 1..128, addr may have any alignment; does one or
// two masked stores

inline void q6op_vstu_variable_ARV(void *addr, int n, HVX_Vector vin)
{
    vin = Q6_V_vlalign_VVR(vin, vin, (size_t)addr); //rotate as needed.
    unsigned const left_off = (size_t)addr & 127;
    unsigned const right_off = left_off + n;
    HVX_VectorPred qL_not = Q6_Q_vsetq_R((size_t)addr);
    HVX_VectorPred qR = Q6_Q_vsetq2_R(right_off);
    if (right_off > 128) {
        Q6_vmaskedstorentq_QAV(qR, (HVX_Vector *)addr + 1, vin);
        qR = Q6_Q_vcmp_eq_VbVb(vin, vin); // all 1's
    }
    qL_not = Q6_Q_or_QQn(qL_not, qR);
    Q6_vmaskedstorentnq_QAV(qL_not, (HVX_Vector *)addr, vin);
}
// store 'n' bytes (1..128) from a vector to unaligned location 'ptr'.
// The bytes are extracted from value, starting at position 'pos0' (and wrapping around, if pos0+n > 128).
// Only the 7 lsbs of pos0 are used.
inline void q6op_vstu_variable_ARVR(void *addr, int n, HVX_Vector vin, int pos0)
{
    vin = Q6_V_vlalign_VVR(vin, vin, (size_t)addr - pos0); //rotate as needed.
    unsigned const left_off = (size_t)addr & 127;
    unsigned const right_off = left_off + n;
    HVX_VectorPred qL_not = Q6_Q_vsetq_R((size_t)addr);
    HVX_VectorPred qR = Q6_Q_vsetq2_R(right_off);
    if (right_off > 128) {
        Q6_vmaskedstorentq_QAV(qR, (HVX_Vector *)addr + 1, vin);
        qR = Q6_Q_vcmp_eq_VbVb(vin, vin); // all 1's
    }
    qL_not = Q6_Q_or_QQn(qL_not, qR);
    Q6_vmaskedstorentnq_QAV(qL_not, (HVX_Vector *)addr, vin);
}

#if 0
// store 'w' bytes (1..128) from a vector to unaligned location 'ptr'.
// The bytes are extracted from value, starting at position 'pos0' (and wrapping around, if pos0+w > 128).
// Only the 7 lsbs of pos0 are used.
// This is an alternate implementation, seenms to be about the same cost, but maybe in some loops it will
// be better depending on what else is in the loop. Probably not useful where 'w' is not a loop invariant.
inline void q6op_vstu_variable_ARVR_alt(void *ptr, unsigned w, HVX_Vector value,
                                        unsigned pos0 = 0)
{
    // make a mask with 1's in the first 'w' slots
    HVX_Vector msk0 = Q6_V_vand_QR(Q6_Q_vsetq2_R(w), 1);
    unsigned uptr = (size_t)ptr;
    unsigned offs = uptr & 127;
    // rotate data up according to 'offs' (and pos0)
    value = Q6_V_vlalign_VVR(value, value, uptr - pos0);
    // shift the mask up according to 'offs'
    HVX_Vector mlo = Q6_V_vlalign_VVR(msk0, Q6_V_vzero(), uptr);
    // and get upper part
    HVX_Vector mhi = Q6_V_vlalign_VVR(Q6_V_vzero(), msk0, uptr);
    Q6_vmaskedstorentq_QAV(Q6_Q_vcmp_gt_VubVub(mlo, Q6_V_vzero()), (char *)ptr,
                           value);
    if (offs + w > 128) {
        Q6_vmaskedstorentq_QAV(Q6_Q_vcmp_gt_VubVub(mhi, Q6_V_vzero()),
                               (char *)ptr + 128, value);
    }
}
#endif

#define PGSIZE (1024 * 1024)

inline void dcfetch(void const *addr)
{
    PUSH_WARNING()
    DISABLE_WARNING("-Wcast-qual", MSVC_NO_EQUIV)
    //    asm volatile(" dcfetch(%0) " : : "r"(addr));
    Q6_dcfetch_A(const_cast<void *>(addr));
    POP_WARNING()
}

inline void ALWAYSINLINE l2pref(const void *p, uint32_t height, uint32_t width, uint32_t stride)
{
    uint64_t const control = Q6_P_combine_RR(stride, Q6_R_combine_RlRl(width, height));
    asm volatile(" l2fetch(%0,%1) " : : "r"(p), "r"(control));
}

inline void ALWAYSINLINE pause_just_enough()
{
#if (__HEXAGON_ARCH__ >= 73)
    //    asm volatile("pause(#1023)");
    asm volatile("pause(#255)");
// LCOV_EXCL_START [SAFTYSWCCB-1735]
#elif (__HEXAGON_ARCH__ >= 69)
    asm volatile("pause(#128)");
#else
    int tmp = 0;
    asm volatile("%0 = add(pc,#8); jumpr %0;" : : "r"(tmp));
    asm volatile("%0 = add(pc,#8); jumpr %0;" : : "r"(tmp));
    asm volatile("%0 = add(pc,#8); jumpr %0;" : : "r"(tmp));
    asm volatile("%0 = add(pc,#8); jumpr %0;" : : "r"(tmp));
// LCOV_EXCL_STOP
#endif
}

#else

// PORTABLE
#include <cstring>

inline HVX_Vector &vmemu(void *addr)
{
    return *(HVX_Vector *)addr;
}

inline HVX_Vector vmemu(void const *v)
{
    return *(HVX_Vector const *)(v);
}
inline HVX_Vector q6op_V_vldu_A(void const *addr)
{
    return *(HVX_Vector const *)addr;
}
inline void q6op_vstu_AV(void *addr, HVX_Vector v)
{
    *(HVX_Vector *)addr = v;
}

inline void q6op_vstu_variable_ARV(void *addr, int n, HVX_Vector vin)
{
    check_hvx();

    typedef union {
        HVX_Vector v;
        uint8_t u8[128];
    } vec;

    vec v;
    v.v = vin;
    std::memcpy((uint8_t *)addr, v.u8, n);
}
inline void q6op_vstu_variable_ARVR(void *addr, int n, HVX_Vector vin, int pos0)
{
    q6op_vstu_variable_ARV(addr, n, Q6_V_vror_VR(vin, pos0));
}

inline void dcfetch(void const volatile *addr) {}
inline void l2pref(const void *p, uint32_t height, uint32_t width, uint32_t stride) {}

inline void pause_just_enough()
{
#ifndef _WIN32
    sched_yield();
#else
    std::this_thread::yield();
#endif
}

#endif

inline void dcfetch_block(const void *addr, int size)
{
    auto address = static_cast<const uint8_t *>(addr);

    for (int i = 0; i < size; i += 64) {
        dcfetch(address);
        address += 64;
    }
}

// unaligned load the lo part of HVX_VECTOR into pDst
inline void vmemu_lo(HVX_VectorPair &output, uint8_t *pDst)
{
    HVX_Vector output_lo = Q6_V_lo_W(output);
    q6op_vstu_AV(pDst, output_lo);
}

// unaligned load the hi part of HVX_VECTOR into pDst
inline void vmemu_hi(HVX_VectorPair &output, uint8_t *pDst)
{
    HVX_Vector output_hi = Q6_V_hi_W(output);
    q6op_vstu_AV(pDst, output_hi);
}

// This func conditional unaligned stores the first nwrite byte of vreg into addr.
inline void q6op_vmemu_partial(uint8_t *addr, HVX_Vector vreg, int nwrite)
{
    HVX_VectorPred cond = Q6_Q_vsetq2_R(std::min(128, nwrite));
    q6op_vstu_AV(addr, Q6_V_vmux_QVV(cond, vreg, vmemu(addr)));
}

// this is called with a dest pointer, two vectors, and 'bytes' in range 1..256.
// The first 'bytes' bytes from the vectors (v0 followed by v1) will be stored
// at the address, using  unaligned and masked stores as needed. If bytes <=0,
// nothing is stored; if bytes > 256, the effect is the same as bytes == 256 (all stored).
void hvx_store_vec_x2_unaligned(void *addr, HVX_Vector v0, HVX_Vector v1, int bytes) noexcept;

inline void hvx_store_vec_x2_unaligned_inline(void *addr, HVX_Vector v0, HVX_Vector v1, int bytes) noexcept
{
    check_hvx();

    static constexpr unsigned int vector_size = 128;
    HVX_Vector *outp = (HVX_Vector *)addr;
    if (bytes >= vector_size) {
        q6op_vstu_AV(outp, v0);
        outp++;
        bytes -= vector_size;
        v0 = v1;
    }
    if (bytes >= vector_size) {
        q6op_vstu_AV(outp, v0);
    } else if (bytes >= 1) {
        q6op_vstu_variable_ARV(outp, bytes, v0);
    }
}

// this is called with a dest pointer, four vectors, and 'bytes' in range 1..512.
// The first 'bytes' bytes from the vectors (v0...v3) will be stored
// at the address, using  unaligned and masked stores as needed. If bytes <=0,
// nothing is stored; if bytes > 512, the effect is the same as bytes == 512 (all stored).
void hvx_store_vec_x4_unaligned(void *addr, HVX_Vector v0, HVX_Vector v1, HVX_Vector v2, HVX_Vector v3,
                                int bytes) noexcept;

inline void hvx_store_vec_x4_unaligned_inline(void *addr, HVX_Vector v0, HVX_Vector v1, HVX_Vector v2, HVX_Vector v3,
                                              int bytes) noexcept
{
    check_hvx();

    static constexpr unsigned int vector_size = 128;
    HVX_Vector *outp = (HVX_Vector *)addr;
    if (bytes >= vector_size) {
        q6op_vstu_AV(outp, v0);
        outp++;
        bytes -= vector_size;
        v0 = v1;
    }
    if (bytes >= vector_size) {
        q6op_vstu_AV(outp, v0);
        outp++;
        bytes -= vector_size;
        v0 = v2;
    }
    if (bytes >= vector_size) {
        q6op_vstu_AV(outp, v0);
        outp++;
        bytes -= vector_size;
        v0 = v3;
    }
    if (bytes >= vector_size) {
        q6op_vstu_AV(outp, v0);
    } else if (bytes >= 1) {
        q6op_vstu_variable_ARV(outp, bytes, v0);
    }
}

inline HVX_VectorPair addv_u64(HVX_VectorPair acc, HVX_Vector newdata)
{
    const HVX_Vector v_one = Q6_V_vsplat_R(1);
    HVX_Vector acc_lo = Q6_V_lo_W(acc);
    HVX_Vector acc_hi = Q6_V_hi_W(acc);
    // works for unsigned newdata since if acc_lo is >= 2^15 and we add
    // newdata (as unsigned), then we have either
    // 1) newdata < 2^15, in which case acc_lo will get 'less negative'
    //    thus decreasing the magnitude of the negative acc_lo (which makes it bigger as
    //    an unsigned int)
    // 2) newdata > 2^15, then both acc_lo and newdata are negative so it just adds the magnitude
    //   (as in -a + -b = -(a+b))
    HVX_Vector new_lo = Q6_Vw_vadd_VwVw(acc_lo, newdata);
    HVX_VectorPred ovf = Q6_Q_vcmp_gt_VuwVuw(newdata, new_lo);
    acc_hi = Q6_Vw_condacc_QVwVw(ovf, acc_hi, v_one);
    return Q6_W_vcombine_VV(acc_hi, new_lo);
}

inline HVX_VectorPair addw_u64(HVX_VectorPair acc, HVX_VectorPair addend)
{
    const HVX_Vector v_one = Q6_V_vsplat_R(1);
    HVX_Vector acc_lo = Q6_V_lo_W(acc);
    HVX_Vector acc_hi = Q6_V_hi_W(acc);
    HVX_Vector addend_hi = Q6_V_hi_W(addend);
    HVX_Vector addend_lo = Q6_V_lo_W(addend);
    HVX_Vector new_hi = Q6_Vw_vadd_VwVw(addend_hi, acc_hi);
    HVX_Vector new_lo = Q6_Vw_vadd_VwVw(acc_lo, addend_lo);
    HVX_VectorPred ovf = Q6_Q_vcmp_gt_VuwVuw(addend_lo, new_lo);
    new_hi = Q6_Vw_condacc_QVwVw(ovf, new_hi, v_one);
    return Q6_W_vcombine_VV(new_hi, new_lo);
}

// Utilities to convert from uint64 to qf32/sf
// Moved here so that they can be reused
//convert long long int into qfloat for sum and sum(squared)
inline HVX_Vector uint64_to_qfloat(HVX_Vector ll_hi, HVX_Vector ll_lo)
{
    HVX_Vector vzero = Q6_V_vzero();
    HVX_VectorPred q0;
    HVX_Vector v32 = Q6_V_vsplat_R(32);
    HVX_Vector qmask = Q6_V_vsplat_R(0xffffff00);
    HVX_Vector qexpmin = Q6_V_vsplat_R(0x0000009e); //^-9
    HVX_Vector qf32_out, hi, lo, exp0, mant0, exp;
    q0 = Q6_Q_vcmp_eq_VwVw(ll_hi, vzero); //if(!hi)
    hi = Q6_V_vmux_QVV(q0, ll_lo, ll_hi); //
    lo = Q6_V_vand_QnV(q0, ll_lo); //xxxx | xxxx or xxxx | 0000
    exp0 = Q6_Vuw_vcl0_Vuw(hi); //get size of value 32 or 64bit
    mant0 = Q6_Vw_vasl_VwVw(hi, exp0); //shift hi by size
    exp = Q6_Vw_vsub_VwVw(v32, exp0); //compute missing bit using oppisite shift on lo
    lo = Q6_Vw_vlsr_VwVw(lo, exp);
    mant0 = Q6_Vw_vadd_VwVw(mant0, lo); //combine lo and hi
    exp = Q6_V_vand_QnV(q0, v32); //adjust exp by 32 if 32 or 64bit ll
    exp0 = Q6_Vw_vsub_VwVw(exp0, exp); //convert to qfloat exponent
    mant0 = Q6_Vuw_vlsr_VuwR(mant0, 1); //make mant issa signed format
    mant0 = Q6_V_vand_VV(mant0, qmask);
    exp0 = Q6_Vw_vsub_VwVw(qexpmin, exp0); //merge mant and exponent
    qf32_out = Q6_V_vor_VV(mant0, exp0); //qfloat
    return (qf32_out);
}

inline HVX_Vector uint64_to_qfloat(HVX_VectorPair bigval)
{
    return uint64_to_qfloat(Q6_V_hi_W(bigval), Q6_V_lo_W(bigval));
}

//This function returns in IEEE FP32 format
inline HVX_Vector uint64_to_float(HVX_Vector ll_hi, HVX_Vector ll_lo)
{
    HVX_Vector vzero = Q6_V_vzero();
    HVX_Vector v32 = Q6_V_vsplat_R(32);

    HVX_Vector exponent = Q6_V_vsplat_R(32);
    HVX_VectorPred q0 = Q6_Q_vcmp_eq_VwVw(ll_hi, vzero);
    exponent = Q6_V_vmux_QVV(q0, vzero, exponent);
    HVX_Vector hi = Q6_V_vmux_QVV(q0, ll_lo, ll_hi);
    HVX_Vector lo = Q6_V_vand_QnV(q0, ll_lo);

    HVX_Vector msb_position = Q6_Vw_vsub_VwVw(v32, Q6_Vuw_vcl0_Vuw(hi));
    HVX_Vector shft_amt = Q6_Vw_vsub_VwVw(Q6_V_vsplat_R(33), msb_position);

    // compute the mantissa (fractional part) of the floating-point representation
    HVX_Vector mantissa = Q6_Vw_vasl_VwVw(hi, shft_amt);
    HVX_Vector temp1 = Q6_Vw_vsub_VwVw(v32, shft_amt);
    temp1 = Q6_Vw_vlsr_VwVw(lo, temp1); //low_bits are shifted right by (32 - shift_amount)
    mantissa = Q6_V_vor_VV(mantissa, temp1); //combines shifted values to create the mantissa
    mantissa = Q6_Vuw_vlsr_VuwR(Q6_Vw_vadd_VwVw(mantissa, Q6_V_vsplat_R(0x00000100)),
                                9); //equivalent to: ((mantissa >> 8) + 1) >> 1;

    // compute the exponent
    exponent = Q6_Vw_vadd_VwVw(msb_position, exponent);
    exponent = Q6_Vw_vadd_VwVw(exponent, Q6_V_vsplat_R(126)); //apply exponent bias
    exponent =
            Q6_Vw_vasl_VwVw(exponent, Q6_V_vsplat_R(23)); //align the exponent part of the floating-point representation

    HVX_Vector result = Q6_Vw_vadd_VwVw(exponent, mantissa);
    // handling float conversion for 0 manually
    const HVX_Vector maskZero = Q6_V_vand_QV(Q6_Q_vcmp_eq_VwVw(ll_lo, vzero), Q6_Q_vcmp_eq_VwVw(ll_hi, vzero));
    result = Q6_V_vmux_QVV(maskZero, vzero, result);

    return result;
}

inline HVX_Vector uint64_to_float(HVX_VectorPair bigval)
{
#if HEX_ARCH >= 73
    return uint64_to_float(Q6_V_hi_W(bigval), Q6_V_lo_W(bigval));
#else
    // LCOV_EXCL_START [SAFTYSWCCB-1735]
    return Q6_Vsf_equals_Vqf32(uint64_to_qfloat(Q6_V_hi_W(bigval), Q6_V_lo_W(bigval)));
    // LCOV_EXCL_STOP
#endif
}

inline HVX_Vector int32_to_qfloat(HVX_Vector const in)
{
    HVX_Vector const vzero = Q6_V_vzero();
    HVX_VectorPred is_zero = Q6_Q_vcmp_eq_VwVw(in, vzero);
    HVX_Vector lshift = Q6_Vw_vnormamt_Vw(in);
    HVX_Vector normalized = Q6_Vw_vasl_VwVw(in, lshift);
    HVX_Vector vexp = Q6_Vw_vsub_VwVw(Q6_V_vsplat_R(0x7f + 30), lshift);
    HVX_Vector mant = Q6_V_vand_VV(Q6_V_vsplat_R(0xFFFFFF00), normalized);
    HVX_Vector ret = Q6_V_vand_QnV(is_zero, Q6_Vw_vadd_VwVw(mant, vexp));
    return ret;
}

inline HVX_Vector int32_to_float(HVX_Vector const in)
{
    return Q6_Vsf_equals_Vqf32(int32_to_qfloat(in));
}

// Hexagon toolchain 19.0 adds support for this function as a macro. The inline function
// declaration is not needed in that case.
#if !defined(Q6_Vqf32_equals_Vsf)
// Convert IEEE 754 float to Qualcomm 32-bit float (qf32)
[[maybe_unused]] static inline ALWAYSINLINE HVX_Vector Q6_Vqf32_equals_Vsf(HVX_Vector vin)
{
    return Q6_Vqf32_vadd_VsfVsf(vin, Q6_V_vzero());
}
#endif

[[maybe_unused]] static inline ALWAYSINLINE HVX_Vector Q6_Vqf32_from_int(HVX_Vector vin)
{
    HVX_Vector const_126 = Q6_V_vsplat_R(0x0000007e);
    HVX_Vector const31 = Q6_V_vsplat_R(31);
    HVX_Vector mant = vin;
    HVX_Vector exp = Q6_Vw_vnormamt_Vw(mant);
    mant = Q6_Vw_vasl_VwVw(mant, exp);
    exp = Q6_Vw_vsub_VwVw(const31, exp);
    exp = Q6_Vw_vadd_VwVw(exp, const_126);
    return Q6_V_vor_VV(mant, exp);
}

//Convert INT32 to return IEEE FP32
[[maybe_unused]] static inline ALWAYSINLINE HVX_Vector int32_to_fp32(HVX_Vector vin)
{
    HVX_Vector v32 = Q6_V_vsplat_R(32);
    const HVX_Vector Zerofp32 = Q6_V_vsplat_R(0x00000000); // 0.0 in IEEE FP32
    const HVX_Vector maskSign = Q6_V_vsplat_R(0x80000000);
    const HVX_Vector vinSgn = Q6_V_vand_VV(vin, maskSign);
    vin = Q6_Vuw_vabsdiff_VwVw(vin, Q6_V_vzero());

    HVX_Vector msb_position = Q6_Vw_vsub_VwVw(v32, Q6_Vuw_vcl0_Vuw(vin));
    HVX_Vector shft_amt = Q6_Vw_vsub_VwVw(Q6_V_vsplat_R(33), msb_position);
    HVX_Vector mantissa = Q6_Vw_vasl_VwVw(vin, shft_amt);
    mantissa = Q6_Vuw_vlsr_VuwR(Q6_Vw_vadd_VwVw(mantissa, Q6_V_vsplat_R(0x00000100)), 9);

    HVX_Vector exponent = Q6_Vw_vadd_VwVw(msb_position, Q6_V_vsplat_R(126));
    exponent =
            Q6_Vw_vasl_VwVw(exponent, Q6_V_vsplat_R(23)); //align the exponent part of the floating-point representation

    HVX_Vector result = Q6_V_vor_VV(vinSgn, Q6_Vw_vadd_VwVw(exponent, mantissa));
    // handling float conversion for 0 manually
    const HVX_VectorPred maskZero = Q6_Q_vcmp_eq_VwVw(vin, Zerofp32);
    result = Q6_V_vmux_QVV(maskZero, Zerofp32, result);

    return result;
}

template <bool RND> static inline HVX_Vector convert_sf_to_s32_core(HVX_Vector vals)
{
    if constexpr (RND) {
        HVX_Vector const sign = Q6_V_vand_VV(vals, Q6_V_vsplat_R(0x80000000));
        HVX_Vector vqfadd = Q6_Vqf32_vadd_VsfVsf(vals, Q6_V_vor_VV(sign, q6op_V_vsplat_float32(0.5f)));
        vals = Q6_Vsf_equals_Vqf32(vqfadd);
    }
#if HEX_ARCH >= 73
    // Can use the fancy new intrinsic for this!
    return Q6_Vw_equals_Vsf(vals);
#else
    // LCOV_EXCL_START [SAFTYSWCCB-1735]
    const HVX_Vector const_zero = Q6_V_vzero();
    const HVX_Vector const_7fffff = Q6_V_vsplat_R(0x7fffff);
    const HVX_Vector const_800000 = Q6_V_vsplat_R(0x800000);
    const HVX_Vector const_00ff = Q6_V_vsplat_R(0x00ff);
    const HVX_Vector const_150 = Q6_V_vsplat_R(127 + 23);
    const HVX_Vector const_n32 = Q6_V_vsplat_R(-32);
    const HVX_Vector const_7 = Q6_V_vsplat_R(7);
    const HVX_Vector const_7fffffff = Q6_V_vsplat_R(0x7fffffff);

    HVX_VectorPred p_neg, p_overflow;
    HVX_Vector mant, exp, shift;

    /* Check for negative values */
    p_neg = Q6_Q_vcmp_gt_VwVw(const_zero, vals);
    /* Extract exponent and mantissa, add back hidden 1 */
    exp = Q6_Vuw_vlsr_VuwR(vals, 23);
    exp = Q6_V_vand_VV(exp, const_00ff);
    mant = Q6_V_vand_VV(vals, const_7fffff);
    mant = Q6_V_vor_VV(mant, const_800000);

    /* shift and round to get integer bits */
    shift = Q6_Vw_vmax_VwVw(Q6_Vw_vsub_VwVw(exp, const_150), const_n32);
    mant = Q6_Vw_vasl_VwVw(mant, shift);

    p_overflow = Q6_Q_vcmp_gt_VhVh(shift, const_7);
    mant = Q6_V_vmux_QVV(p_overflow, const_7fffffff, mant);

    /* Turn negative values into two's complement negative values */
    HVX_Vector v_neg = Q6_V_vand_QR(p_neg, -1); // 0xFFFFFFFF in -ve lanes, 0 in >=0
    mant = Q6_V_vxor_VV(Q6_Vw_vadd_VwVw(mant, v_neg), v_neg);
    return mant;
    // LCOV_EXCL_STOP
#endif // HEX_ARCH >= 73
}

// Convert float to int32, round to nearest, with 0.5 rounded away from 0
// ie np.copysign(0.5,f).astype('int32')
static inline HVX_Vector convert_sf_to_s32_rnd(HVX_Vector vals)
{
    return convert_sf_to_s32_core<true>(vals);
}

// Convert float32 to int32, round toward 0
// ie f.astype('int32')
static inline HVX_Vector convert_sf_to_s32(HVX_Vector vals)
{
    return convert_sf_to_s32_core<false>(vals);
}

template <bool RND> static inline HVX_Vector convert_hf_to_s16_core(HVX_Vector vals)
{
#if HEX_ARCH >= 73
    if constexpr (RND) {
        return hnnx::s16_from_hf_rnd_sat<0>(vals);
    } else {
        return Q6_Vh_equals_Vhf(vals);
    }
#else
    // LCOV_EXCL_START [SAFTYSWCCB-1735]
    if constexpr (RND) {
        return hnnx::s16_from_hf_rnd_sat<0>(vals);
    } else {
        return hnnx::s16_from_hf_sat<0>(vals);
    }
    // LCOV_EXCL_STOP
#endif // HEX_ARCH >= 73
}

static inline HVX_Vector convert_hf_to_s16(HVX_Vector vals)
{
    return convert_hf_to_s16_core<0>(vals);
}

static inline HVX_Vector convert_hf_to_s16_rnd(HVX_Vector vals)
{
    return convert_hf_to_s16_core<1>(vals);
}

// Some existing graphs seem to be sensitive to modifications of int32_to_float
// So this is added in order to work around that
static inline HVX_Vector convert_s32_to_sf(const HVX_Vector vals)
{
#if HEX_ARCH >= 73
    return Q6_Vsf_equals_Vw(vals);
#else
    // LCOV_EXCL_START [SAFTYSWCCB-1735]
    return int32_to_float(vals);
    // LCOV_EXCL_STOP
#endif
}

#if defined(__hexagon__)
#define SCATTER_TYPE(_a) (intptr_t) _a
inline ALWAYSINLINE void scatter_release_and_stall(const void *p) // must point to TCM
{
    asm volatile("vmem(%0+#0):scatter_release" : : "r"(p));
    // issue load to same address; will stall until vscatters complete
    *(HVX_Vector const volatile *)p;
}
#else
#define SCATTER_TYPE(_a) (HVX_Vector *)_a
[[maybe_unused]] inline ALWAYSINLINE void scatter_release_and_stall(const void *p)
{
    check_hvx();
    return; // empty function def on non-hexagon targets
}
#endif

/*=======================================*/
/* Helper Function in assembly        */
/*=======================================*/
extern "C" {
void vmemcpy_h(void *dst, const void *src, size_t len);

void vmemset_h(void *dst, int value, size_t len);

}; // extern "C"

#ifndef __hexagon__
// map to std. library for x86
inline void vmemcpy_h(void *dst, const void *src, size_t len)
{
    check_hvx();
    memcpy(dst, src, len);
}
inline void vmemset_h(void *dst, int val, size_t len)
{
    check_hvx();
    memset(dst, val, len);
}
#endif //__hexagon__

#endif // INTRINSICS_H
