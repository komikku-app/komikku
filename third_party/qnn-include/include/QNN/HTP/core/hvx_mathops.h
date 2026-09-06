//==============================================================================
//
// Copyright (c) 2021 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef HVX_MATHOPS_H_
#define HVX_MATHOPS_H_ 1

#include "intrinsics.h"

namespace hnnx {
//
// Conversion of qf to int16, with a specific number of fractional bits,
// and rounding/saturation. The number of fractional bits is set in range -2 .. 9
// by a template parameter.
//  E.g. a value of 51.0 will convert to 51 when FBITS=0, to 408 when FBITS=3,
// and to 13 when FBITS=-2 (rounded from 12.75).
//
// This has been sweep-tested over all possible inputs.
// This respects the 'hvx extended' hf, where exponent=31 is a normal range.
// (but, you only see that extra range when FBITS =-2; otherwise those values
// are saturated).
// Any input values which are exactly halfway between integers are rounded
// away from 0; others are rounded to nearest.
//
//
// This should really work for larger FBITS, but for 10 or more, internal
// rounding errors show up in the output somehow. Instead of scaling the input
// directly according to FBITS, I also tried reducing the exponent of the added value,
// i.e. use Q6_V_vsplat_R( 0x48400000 - (FBITS<<23) )
// In principle this allows FBITS up to 28 or so, but results are not as expected, and
// that approach only works for FBITS <= 8.
//

template <int FBITS, bool RND> inline HVX_Vector s16_from_hf_core(HVX_Vector vin)
{
    // convert to qf32, multiplying by 1.0 in the process.
    HVX_Vector result = {0};
    HVX_VectorPair v32 = Q6_Wqf32_vmpy_VhfVhf(vin, Q6_Vh_vsplat_R(0x3C00 + FBITS * 0x400));
    // 'in-range' values are +/32752.
    // add 192K to it, convert to sf
    HVX_Vector v192K = Q6_V_vsplat_R(0x48400000);
    HVX_Vector vsf_0 = Q6_Vsf_equals_Vqf32(Q6_Vqf32_vadd_Vqf32Vsf(Q6_V_lo_W(v32), v192K));
    HVX_Vector vsf_1 = Q6_Vsf_equals_Vqf32(Q6_Vqf32_vadd_Vqf32Vsf(Q6_V_hi_W(v32), v192K));
    // for in-range cases, result is {163858... 229360} so the exponent is always 144.
    // if we extract bits 21..0 as a signed quantity, and round 6 bits off, that will be the answer.
    // Start by <<10 to get the final 'sign' bit in bit 15...
    vsf_0 = Q6_Vw_vasl_VwR(vsf_0, 10);
    vsf_1 = Q6_Vw_vasl_VwR(vsf_1, 10);
    // now round down to 16
    if constexpr (RND) {
        result = Q6_Vh_vround_VwVw_sat(vsf_1, vsf_0);
    } else {
        // Q6_Vh_vround_VwVw_sat has an implicit right shift by 16, so mimic that
        result = Q6_Vh_vsat_VwVw(Q6_Vw_vasr_VwR(vsf_1, 16), Q6_Vw_vasr_VwR(vsf_0, 16));
    }
    // but we need to also take care of out-of-range inputs; any with original exponent exceeding
    // 29-FBITS. This is only possible when FBITS is -1 or more.
    if (FBITS > -2) {
        HVX_Vector tmp = Q6_Vh_vadd_VhVh(vin, vin); // shift out sign bit
        HVX_Vector thrsh = Q6_Vh_vsplat_R((30 - FBITS) * 0x800); // must be <this
        HVX_VectorPred n_overflow = Q6_Q_vcmp_gt_VuhVuh(thrsh, tmp);
        HVX_Vector saturated = Q6_Vh_vlut4_VuhPh(vin, 0x800080007fff7fffULL);
        result = Q6_V_vmux_QVV(n_overflow, result, saturated);
    }
    return result;
}

template <int FBITS> inline HVX_Vector s16_from_hf_rnd_sat(HVX_Vector vin)
{
    return s16_from_hf_core<FBITS, 1>(vin);
}

template <int FBITS> inline HVX_Vector s16_from_hf_sat(HVX_Vector vin)
{
    return s16_from_hf_core<FBITS, 0>(vin);
}

} //namespace hnnx

#endif /* HVX_MATHOPS_H_ */
