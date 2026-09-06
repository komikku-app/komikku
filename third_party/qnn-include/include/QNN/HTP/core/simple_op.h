//==============================================================================
//
// Copyright (c) 2021 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef SIMPLE_OP_H
#define SIMPLE_OP_H

#include "graph_status.h"
#include "template_help.h"
#include "op_utils.h"
#include "template_help_tensor_ext.h"
#include "tensor.h"
#include "cost.h"
#include "weak_linkage.h"
#include "macros_attribute.h"

namespace hnnx {

PUSH_VISIBILITY(default)

// base class of SimpleOp
class SimpleOpBase {
    std::unique_ptr<SimpleOpBase> next_sop;

  public:
    SimpleOpBase() {}
    SimpleOpBase(const SimpleOpBase &) = delete;
    SimpleOpBase &operator=(const SimpleOpBase &) = delete;
    SimpleOpBase(SimpleOpBase &&) = delete;
    SimpleOpBase &operator=(SimpleOpBase &&) = delete;
    API_EXPORT virtual ~SimpleOpBase();
    API_EXPORT virtual std::type_info const *true_type() const { return &typeid(*this); }
    API_EXPORT virtual size_t get_n_inputs() const = 0;
    API_EXPORT virtual size_t get_n_outputs() const = 0;
    API_EXPORT virtual uint8_t const *get_input_tensor_types() const = 0;
    API_EXPORT virtual uint8_t const *get_output_tensor_types() const = 0;
    API_EXPORT virtual bool needs_tcm() const = 0;
    API_EXPORT virtual GraphStatus execute(Tensor const *const *inputs_p, unsigned n_in, uptr_Tensor const *outputs_p,
                                           unsigned n_out) const noexcept = 0;
    API_EXPORT static void release_chain(std::unique_ptr<SimpleOpBase> &listhead) noexcept;

    API_EXPORT inline void set_next(std::unique_ptr<SimpleOpBase> &&nextp) { next_sop = std::move(nextp); }
};

POP_VISIBILITY()

/*
 * SimpleOp class
 * used by external op packages
 * for the purpose of exposing fewer symbols
 */
template <auto F> class SimpleOp : public SimpleOpBase {
    using Ftype = std::remove_pointer_t<decltype(F)>;

  public:
    // the collection of input types, as pointers
    using input_ptr_tuple_type = typename ArgsTuples<Ftype>::input_ptr_tuple;
    // the collection of output types, as pointers
    using output_ptr_tuple_type = typename ArgsTuples<Ftype>::output_ptr_tuple;
    // the inputs as real types
    using input_tuple_defs = typename ArgsTuples<Ftype>::input_tuple;
    // the outputs as real types
    using output_tuple_defs = typename ArgsTuples<Ftype>::output_tuple;
    // A graph argument is not allowed
    using graph_ptr_tuple_type = typename ArgsTuples<Ftype>::graph_ptr_tuple;

    // numbers of inputs and outputs
    static constexpr size_t n_inputs = ArgsTuples<Ftype>::n_inputs;
    static constexpr size_t n_outputs = ArgsTuples<Ftype>::n_outputs;

    // indices representing input and outputs tensor types
    // only tensor types from AllTensors in template_help_tensor_ext.h are allowed to be used in SimpleOp
    static constexpr std::array<uint8_t, n_inputs> input_tensor_type_indices =
            tensors_to_indices<std::array<uint8_t, n_inputs>, input_tuple_defs>();
    static constexpr std::array<uint8_t, n_outputs> output_tensor_type_indices =
            tensors_to_indices<std::array<uint8_t, n_outputs>, output_tuple_defs>();
    // boolean representing whether all tensor types used in outputs are from AllTensors list
    static constexpr bool are_tensor_types_valid = check_tensor_types_valid<output_tuple_defs>();

    // number of graph parameter
    static constexpr size_t n_graphs = std::tuple_size<std::decay_t<graph_ptr_tuple_type>>::value;

    SimpleOp() : SimpleOpBase() {}
    SimpleOp(const SimpleOp &) = delete;
    SimpleOp &operator=(const SimpleOp &) = delete;
    SimpleOp(SimpleOp &&) = delete;
    SimpleOp &operator=(SimpleOp &&) = delete;

    ~SimpleOp() override = default;

    size_t get_n_inputs() const override { return n_inputs; }

    size_t get_n_outputs() const override { return n_outputs; }

    uint8_t const *get_input_tensor_types() const override { return input_tensor_type_indices.data(); }

    uint8_t const *get_output_tensor_types() const override { return output_tensor_type_indices.data(); }

    bool needs_tcm() const override
    {
        // replace with less dependency in the future
        static constexpr bool needs_tcm_t = has_memclass<MemoryClass::TCM, output_tuple_defs>::value;
        return needs_tcm_t;
    }

    static inline bool valid_construction(size_t n_inputs_in, size_t n_outputs_in, Tensor const *const *inputs_in,
                                          OutputDef const *const *outputs_in, Graph &graph_in)
    {
        if (n_inputs != n_inputs_in) return false;
        if (n_outputs != n_outputs_in) return false;
        if (!are_input_tensors_compatible<n_inputs, input_ptr_tuple_type>(graph_in, inputs_in)) return false;
        if (!are_output_defs_valid<n_outputs, output_tuple_defs>(outputs_in, graph_in)) return false;
        if (n_graphs) return false;
        return true;
    }

  protected:
    // generate parameter I (in range 0..parm_n_total-1) for calling the func within execute.
    // Return type is 'auto &' so it will always return a reference.
    template <size_t I>
    inline auto &get_exec_parm(Tensor const *const *const inputs_in, uptr_Tensor const *const outputs_in) const noexcept
    {
        if constexpr (I < n_outputs) { // output
            using output_ptr_t = std::tuple_element_t<I, output_ptr_tuple_type>;
            // extract output[I], downcast to output_ptr_t, return ref
            return *static_cast<output_ptr_t>(outputs_in[I].get());
        } else {
            static_assert(I < n_outputs + n_inputs);
            using input_ptr_t = std::tuple_element_t<I - n_outputs, input_ptr_tuple_type>;
            // extract input[I - n_inputs], downcast to output_ptr_t, return ref
            return *static_cast<input_ptr_t>(inputs_in[I - n_outputs]);
        }
    }
    template <size_t... I>
    inline GraphStatus call_with_parms(Ftype f, Tensor const *const *const inputs_in,
                                       uptr_Tensor const *const outputs_in, std::index_sequence<I...>) const noexcept
    {
        return GraphStatus(f(get_exec_parm<I>(inputs_in, outputs_in)...));
    }

  public:
    GraphStatus execute(Tensor const *const *const inputs_p, const unsigned n_in, uptr_Tensor const *const outputs_p,
                        const unsigned n_out) const noexcept override
    {
        // the SimpleOpWrapper ctor calls get_n_inputs and get_n_outputs to size its arrays, so
        // this correspondence should not need more than an assert.
        assert(n_in == n_inputs && n_out == n_outputs);
        return call_with_parms(F, inputs_p, outputs_p, std::make_index_sequence<n_outputs + n_inputs>{});
    }

    static std::unique_ptr<SimpleOpBase> create(size_t n_inputs_in, size_t n_outputs_in, Tensor const *const *inputs_in,
                                                OutputDef const *const *outputs_in, Graph &graph_in)
    {
        if (SimpleOp::valid_construction(n_inputs_in, n_outputs_in, inputs_in, outputs_in, graph_in)) {
            return std::move(std::make_unique<SimpleOp>());
        } else {
            return std::unique_ptr<SimpleOp>{};
        }
    }

    using tensor_deserializer_register_func = int (*)();

    static constexpr tensor_deserializer_register_func get_tensor_deserializer_register_func()
    {
        return hnnx::deserialize_tensor_tuple<output_tuple_defs, false>::f_ptr();
    }
};

} // namespace hnnx

/**
 * @brief All external Op source files must invoke this macro at the top of the file,
 * before any COST_OF/REGISTER_OP/DEF_OPT calls.
 *
 */
#define BEGIN_PKG_OP_DEFINITION(NAME) INITIALIZE_TABLES()

/**
 * @brief All external Op source files must invoke this macro at the bottom of the
 * file, after all COST_OF/REGISTER_OP/DEF_OPT calls.
 *
 */
#define END_PKG_OP_DEFINITION(NAME) FINALIZE_TABLES(NAME)

template <auto F> struct SimpleOpType {
    using type = hnnx::SimpleOp<F>;
};

template <auto F> struct DerivedType {
    using type = hnnx::SimpleOp<F>;
};

#endif // SIMPLE_OP_H
