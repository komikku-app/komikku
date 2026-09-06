//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OP_UTILS_H
#define OP_UTILS_H 1

#include "interface_defs.h"
#include "op_def.h"
#include "tensor.h"
#include "build_options_pub.h"

#include <memory>
#include <tuple>
#include <utility>
#include <vector>

namespace hnnx {

template <typename T> static inline bool is_output_def_valid(const OutputDef &output_def, Graph &graph_in)
{
    return tensor_generator_valid<T>(nullptr, output_def, graph_in);
}

template <typename T>
static inline bool is_input_tensor_compatible(Graph &graph_in, Tensor const *tensor, unsigned position)
{
    // dynamic_cast below is used to realise 'std::is_base_of' check with tensor object
    // the cast uses Run Time Type Identification (RTTI) mechanism
    // to infer the true object type to downcast when valid
    // else returns a nullptr
    if (!tensor || !dynamic_cast<T>(tensor)) {
        if constexpr (build_options_pub::DebugRegistry)
            debuglog("input tensor is %p of type %s in position %d, dynamic cast to %s failed", tensor,
                     typeid(*tensor).name(), position, typeid(T).name());
        return false;
    }
    return true;
}

template <typename TupType, size_t... I>
static inline bool are_output_defs_valid_helper(std::index_sequence<I...>, OutputDef const *const *outputs_in,
                                                Graph &graph_in)
{
    //  tensor_generator below returns a unique pointer which will be released on return (i.e. when object goes out of scope)
    // this check preferably should be done with boolean valid_tensor() method instead of creating an actual tensor
    // but for now to limit generation of more template code this approach should suffice.
    return (is_output_def_valid<std::tuple_element_t<I, TupType>>(*outputs_in[I], graph_in) && ...);
}

template <size_t N, typename TupType>
static inline bool are_output_defs_valid(OutputDef const *const *outputs_in, Graph &graph_in)
{
    return are_output_defs_valid_helper<TupType>(std::make_index_sequence<N>{}, outputs_in, graph_in);
}

template <typename TupType, size_t... I>
static inline bool are_input_tensors_compatible_helper(std::index_sequence<I...>, Graph &graph_in,
                                                       Tensor const *const *inputs_in)
{
    return ((is_input_tensor_compatible<std::tuple_element_t<I, TupType>>(graph_in, inputs_in[I], I)) && ...);
}

template <size_t N, typename TupType>
static inline bool are_input_tensors_compatible(Graph &graph_in, Tensor const *const *inputs_in)
{
    return are_input_tensors_compatible_helper<TupType>(std::make_index_sequence<N>{}, graph_in, inputs_in);
}

template <typename T>
std::unique_ptr<Tensor> tensor_output_alloc(const Op *producer_in, const OutputDef &output_def, Graph &graph_in)
{
    return std::move(tensor_generator<T>(producer_in, output_def, graph_in));
}

// a pointer to a tensor_generator<T>() function, for some T
typedef std::unique_ptr<Tensor> (*tensor_generate_fp)(Op const *, OutputDef const &, Graph &);
//
// for TupType being a tuple of N tensor-types:
//  tensor_gen_array<TupType> returns a constexpr array of N tensor_generate_fp.
//
template <typename TupType, size_t N, size_t... I>
inline constexpr std::array<tensor_generate_fp, N> tensor_gen_array_helper(std::index_sequence<I...>)
{
    return {tensor_generator<std::tuple_element_t<I, TupType>>...};
}
template <typename TupType>
inline constexpr std::array<tensor_generate_fp, std::tuple_size_v<TupType>> tensor_gen_array()
{
    constexpr size_t N = std::tuple_size_v<TupType>;
    return tensor_gen_array_helper<TupType, N>(std::make_index_sequence<N>{});
}
// and tensor_gen_array_ptr<TupType> returns a pointer to such an array
template <typename TupType> inline tensor_generate_fp const *tensor_gen_array_ptr()
{
    if constexpr (std::tuple_size_v<TupType> != 0) {
        static constexpr std::array<tensor_generate_fp, std::tuple_size_v<TupType>> ptr_array =
                tensor_gen_array<TupType>();
        return ptr_array.data();
    } else {
        return nullptr;
    }
}

////////////////
// Code to generate a table of {dtype, rank} pairs, for the 'scratch' tensors in an op.
struct dt_rank_pair {
    DType dt;
    unsigned rank;
};

// map a Tensor type to a dt_rank_pair: dt_rank_pair_for_tens<T>::value.
// General case fails.
template <typename Tens> struct dt_rank_pair_for_tens {
    static_assert(int(sizeof(Tens)) < 0, "Can't use Tens as 'scratch' output type, only Concrete Tensor");
};
// Specialized for Concrete<Tensor> only.
template <typename Tinfo> struct dt_rank_pair_for_tens<ConcreteTensor<Tinfo>> {
  private:
    using CT_traits = tensor_traits<ConcreteTensor<Tinfo>>;

  public:
    static constexpr dt_rank_pair value = {CT_traits::dtype, CT_traits::rank};
};
template <typename TupType, size_t N, size_t... I>
inline constexpr std::array<dt_rank_pair, N> tensor_dt_rank_array_helper(std::index_sequence<I...>)
{
    return {dt_rank_pair_for_tens<std::tuple_element_t<I, TupType>>::value...};
}
template <typename TupType> // make and return the array...
inline constexpr std::array<dt_rank_pair, std::tuple_size_v<TupType>> tensor_dt_rank_array()
{
    constexpr size_t N = std::tuple_size_v<TupType>;
    return tensor_dt_rank_array_helper<TupType, N>(std::make_index_sequence<N>{});
}
// and tensor_dt_rank_array_ptr<TupType> returns a pointer to such an array
template <typename TupType> inline dt_rank_pair const *tensor_dt_rank_array_ptr()
{
    if constexpr (std::tuple_size_v<TupType> != 0) {
        static constexpr std::array<dt_rank_pair, std::tuple_size_v<TupType>> dt_array =
                tensor_dt_rank_array<TupType>();
        return dt_array.data();
    } else {
        return nullptr;
    }
}
// A mechanism for invoking tensor_dt_rank_array_ptr<..> with the *last* tuple types
// in tup-types, after removing the first NPREFIX (and, a goal here is to avoid creating
// more of the 'static constexpr dt_array' above than needed; any cases with the same
// tail will invoke the same array)
template <unsigned NPREFIX, typename TupOfTens, bool FINAL = (NPREFIX == 0)> struct tensor_dt_rank_array_for_scratch;

// case with NPREFIX = 0
template <typename... Tts> struct tensor_dt_rank_array_for_scratch<0, std::tuple<Tts...>, true> {
    static inline dt_rank_pair const *table_p() { return tensor_dt_rank_array_ptr<std::tuple<Tts...>>(); }
};
// other cases with NPREFIX >= 1
template <unsigned NPREFIX, typename T1, typename... Tts>
struct tensor_dt_rank_array_for_scratch<NPREFIX, std::tuple<T1, Tts...>, false>
    : public tensor_dt_rank_array_for_scratch<NPREFIX - 1, std::tuple<Tts...>> {
    // inherit table_p()
};
////////////////

//
// given tensor type, get spatial mask
//
template <typename Ttype> inline uint32_t get_spatial_mask()
{
    // NOLINTNEXTLINE(misc-const-correctness): Don't const this variable
    uint32_t spatial_mask = 0x38; //0b111000
    if constexpr (std::is_base_of<LayoutWideCrouton_8, Ttype>::value) spatial_mask = 0x20; //0b100000
    return spatial_mask;
}

} // namespace hnnx

//
// find dim + (stride-1))/stride
// avoid a divide when stride = 1..4
inline size_t stride_divide(size_t dim, size_t stride)
{
    if (stride >= 2) {
        dim += stride - 1;
        switch (stride) {
        case 2:
            return dim >> 1;
        case 3:
            return dim / 3u; // compiler will have a trick for this.
        case 4:
            return dim >> 2;
        default:
            return dim / stride;
        }
    }
    return dim;
}

//
// given input size, window size, and stride, find the output size and the 'prepad' (padding on
// left/top needed to align first output.
// 'same_shape' is false for 'valid', true for 'same'
//
static inline std::tuple<size_t, size_t> find_output_size_and_prepad(bool same_shape, size_t insize, size_t winsize,
                                                                     size_t stride)
{
    size_t outsize;
    size_t prepad;
    if (same_shape) {
        outsize = insize;
        prepad = (winsize - 1) / 2;
    } else {
        outsize = insize - (winsize - 1);
        prepad = 0;
    }
    // 'outsize' is correct for stride=1; adjust for general stride
    outsize = stride_divide(outsize, stride);
    return std::tuple<size_t, size_t>(outsize, prepad);
}

#endif
