//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef TEMPLATE_HELP_TENSOR_EXT_H
#define TEMPLATE_HELP_TENSOR_EXT_H

#include "tensor.h"
#include "macros_attribute.h"
#include "weak_linkage.h"

namespace hnnx {

int tensor_deserializer_register_ext(size_t n_out, uint8_t const *out_indices);

/*
 * mapping each predefined tensor type to an index
 * used by SimpleOp and SimpleOpWrapper to support op packages
 */

template <typename T> static constexpr uint8_t tensor_idx = 0;

template <> inline constexpr uint8_t tensor_idx<Tensor> = 1;
template <> inline constexpr uint8_t tensor_idx<PlainFloatTensor> = 2;
template <> inline constexpr uint8_t tensor_idx<PlainFloatTensor_TCM> = 3;
template <> inline constexpr uint8_t tensor_idx<PlainFloat16Tensor> = 4;
template <> inline constexpr uint8_t tensor_idx<PlainFloat16Tensor_TCM> = 5;
// REMOVED template <> inline constexpr uint8_t tensor_idx<D32FloatTensor> = 6;
// REMOVED template <> inline constexpr uint8_t tensor_idx<D32PaddedFloatTensor> = 7;
template <> inline constexpr uint8_t tensor_idx<Int32Tensor> = 8;
template <> inline constexpr uint8_t tensor_idx<Int32Tensor_TCM> = 9;
template <> inline constexpr uint8_t tensor_idx<Int32CroutonTensor> = 10;
template <> inline constexpr uint8_t tensor_idx<Int32CroutonTensor_TCM> = 11;
template <> inline constexpr uint8_t tensor_idx<QuantUint8Tensor> = 12;
template <> inline constexpr uint8_t tensor_idx<QuantUint8Tensor_TCM> = 13;
template <> inline constexpr uint8_t tensor_idx<QuantInt8Tensor> = 14;
template <> inline constexpr uint8_t tensor_idx<QuantInt8Tensor_TCM> = 15;
template <> inline constexpr uint8_t tensor_idx<QuantUint16Tensor> = 16;
template <> inline constexpr uint8_t tensor_idx<QuantUint16Tensor_TCM> = 17;
template <> inline constexpr uint8_t tensor_idx<QuantInt16Tensor> = 18;
template <> inline constexpr uint8_t tensor_idx<QuantInt16Tensor_TCM> = 19;
template <> inline constexpr uint8_t tensor_idx<QuantInt32Tensor> = 20;
template <> inline constexpr uint8_t tensor_idx<QuantInt32Tensor_TCM> = 21;
template <> inline constexpr uint8_t tensor_idx<QUint8CroutonTensor> = 22;
template <> inline constexpr uint8_t tensor_idx<QUint8CroutonTensor_TCM> = 23;
template <> inline constexpr uint8_t tensor_idx<QInt8CroutonTensor> = 24;
template <> inline constexpr uint8_t tensor_idx<QInt8CroutonTensor_TCM> = 25;
template <> inline constexpr uint8_t tensor_idx<QUint8Crouton4x1Tensor> = 26;
template <> inline constexpr uint8_t tensor_idx<QUint8Crouton4x1Tensor_TCM> = 27;
template <> inline constexpr uint8_t tensor_idx<QUint8Crouton2x2Tensor> = 28;
template <> inline constexpr uint8_t tensor_idx<QUint8Crouton2x2Tensor_TCM> = 29;
template <> inline constexpr uint8_t tensor_idx<QUint8WideCroutonTensor> = 30;
template <> inline constexpr uint8_t tensor_idx<QUint8WideCroutonTensor_TCM> = 31;
template <> inline constexpr uint8_t tensor_idx<QUint8WideCrouton2x2Tensor> = 32;
template <> inline constexpr uint8_t tensor_idx<QUint8WideCrouton2x2Tensor_TCM> = 33;
template <> inline constexpr uint8_t tensor_idx<QUint16CroutonTensor> = 34;
template <> inline constexpr uint8_t tensor_idx<QUint16CroutonTensor_TCM> = 35;
template <> inline constexpr uint8_t tensor_idx<QInt32CroutonTensor> = 36;
template <> inline constexpr uint8_t tensor_idx<QInt32CroutonTensor_TCM> = 37;
template <> inline constexpr uint8_t tensor_idx<QInt32WideCroutonTensor> = 38;
template <> inline constexpr uint8_t tensor_idx<QInt32WideCroutonTensor_TCM> = 39;

template <> inline constexpr uint8_t tensor_idx<TensorShape<4>> = 40;

template <> inline constexpr uint8_t tensor_idx<F16CroutonTensor> = 41;
template <> inline constexpr uint8_t tensor_idx<F16CroutonTensor_TCM> = 42;

template <> inline constexpr uint8_t tensor_idx<PredicateTensor> = 43;

// all tensor types supported in op package ops
// clang-format off
using AllTensors =
        std::tuple<Tensor, Tensor, PlainFloatTensor, PlainFloatTensor_TCM, PlainFloat16Tensor, PlainFloat16Tensor_TCM,
                   Tensor, Tensor, // REMOVED: D32FloatTensor, D32PaddedFloatTensor,
                   Int32Tensor, Int32Tensor_TCM, Int32CroutonTensor,
                   Int32CroutonTensor_TCM, QuantUint8Tensor, QuantUint8Tensor_TCM, QuantInt8Tensor, QuantInt8Tensor_TCM,
                   QuantUint16Tensor, QuantUint16Tensor_TCM, QuantInt16Tensor, QuantInt16Tensor_TCM, QuantInt32Tensor,
                   QuantInt32Tensor_TCM, QUint8CroutonTensor, QUint8CroutonTensor_TCM, QInt8CroutonTensor,
                   QInt8CroutonTensor_TCM, QUint8Crouton4x1Tensor, QUint8Crouton4x1Tensor_TCM, QUint8Crouton2x2Tensor,
                   QUint8Crouton2x2Tensor_TCM, QUint8WideCroutonTensor, QUint8WideCroutonTensor_TCM,
                   QUint8WideCrouton2x2Tensor, QUint8WideCrouton2x2Tensor_TCM, QUint16CroutonTensor,
                   QUint16CroutonTensor_TCM, QInt32CroutonTensor, QInt32CroutonTensor_TCM, QInt32WideCroutonTensor,
                   QInt32WideCroutonTensor_TCM, TensorShape<4>, F16CroutonTensor, F16CroutonTensor_TCM
                   , PredicateTensor
                   >;
// clang-format on

struct tensor_info {
    std::type_info const *tid;
    bool needs_des;
    tensor_deserializer_fn desf;
    tensor_generate_fp genf;
};

// returns a map : tensor index -> tensor_info
PUSH_VISIBILITY(default)
API_EXPORT std::map<uint8_t, tensor_info> &get_tensor_info_map();
POP_VISIBILITY()

//LCOV_EXCL_START [SAFTYSWCCB-1736] constexprs resolved during compile time
// used in pub/impl/simple_op.h for indices representing input and outputs tensor types
// and have lvalue constexpr
template <typename AggType, class Tup, size_t... I>
static inline constexpr AggType tensors_to_indices_helper(std::index_sequence<I...>)
{
    return AggType{tensor_idx<std::tuple_element_t<I, Tup>>...};
}

// converts a tuple of tensor types to a vector of corresponding indices
template <typename AggType, class Tup> static inline constexpr AggType tensors_to_indices()
{
    return tensors_to_indices_helper<AggType, Tup>(
            std::make_index_sequence<std::tuple_size<std::decay_t<Tup>>::value>{});
}
//LCOV_EXCL_STOP

template <class Tup, size_t... I>
static inline constexpr bool check_tensor_types_valid_helper(std::index_sequence<I...>)
{
    return (((bool)tensor_idx<std::tuple_element_t<I, Tup>>)&&...);
}

// checks tensor types in a tuple are all from AllTensors list
template <class Tup> static inline constexpr bool check_tensor_types_valid()
{
    return check_tensor_types_valid_helper<Tup>(std::make_index_sequence<std::tuple_size<std::decay_t<Tup>>::value>{});
}

} // namespace hnnx

#endif // TEMPLATE_HELP_TENSOR_EXT_H
