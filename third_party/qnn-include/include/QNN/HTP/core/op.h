//==============================================================================
//
// Copyright (c) 2018-2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OP_H
#define OP_H

#include <typeinfo>
#include "flags.h"
#include "graph_status.h"
#include "op_def.h"
#include "executable.h"
#include "cost_funcs.h"
#include "unique_types.h"
#include "serialize_defs.h"
#include "serialize_oplist.h"
#include <set>
#include <vector>
#include "weak_linkage.h"
#include "macros_attribute.h"

class Graph;
class Tensor;
namespace hnnx {
class OpIoPtrs;
class SimpleOpBase;
class CostBasedFeatureDesc;
struct OpExtraInfo;
} // namespace hnnx
/*
 * What are the fundamentals of an op?
 *
 * It has an ID to be able to refer to it easily
 * It has zero or more inputs.  Inputs refer to an output of another op.
 * It has zero or more outputs.  Output definitions determine the max size of an op.
 * It can execute.  When an op executes, it uses the inputs to produce the outputs.
 *
 * There are also, probably some less important aspects to ops:
 * * Constructor / Destructor
 * * In Hexagon NN V2, we have a hook during graph preparation.  This isn't
 *   always necessary, and maybe we should strive to make it unnecessary?
 * * We sometimes use flags to indicate something about an op
 *
 * There will be some other aspects to ops eventually, dealing with when
 * to "wake up" and what other ops to notify when finished.  But for now
 * we can just run one op at a time.
 */

#include "weak_linkage.h"
PUSH_VISIBILITY(default)

// Flags used to describe the class of checkpoints we have.
enum ChkptStoreType {
    ChkptNormal = 0, // N, M
    ChkptNone = 1, // (-1 or 0) and (-1 or 1).
    ChkptNoGate = 2, // (-1 or 0), N
    ChkptNoDone = 3, // N, (-1 or 1)
    ChkptFlagShift = 2,
    ChkptOpFlagMask = 0x3,
    ChkptFlagMask = ((1 << ChkptFlagShift) - 1),
};

/*
 * FIXME: instead of deserialize function, we should have a constructor with arguments (const char **bufp, size_t *)
 */

/**
 * @class Op
 *
 * @brief Basic, minimal Op
 * Ops inherit from this class
 *
 * This is starting out minimal, we will extend this in the future
 *
 * Maybe ID should be here, maybe not.
 */

class Op : public hnnx::Executable {
    friend void hnnx::op_serialize_common(hnnx::Serializer &, Op const *, std::type_info const *);
    //! Interface to the external world is 32 bits for an Op ID (0 and above 0xF000_0000 are reserved for internal use).
    //! However, as we break ops down we want to have some semblance of the original op IDs while still maintaining unique IDs.
    //! So we make internal OpIDs 64 bits.
    //! Half of them can be the external OpID, and we can use a counter or something to uniquify in the other bits.
    //! We can accumulate performance information and such to still represent OpIDs on the interface.
  public:
    Op(){};
    API_EXPORT Op(Graph &graph_in, unsigned long long int my_id_in);
    API_EXPORT explicit Op(hnnx::Deserz &);
    Op(Op const &) = delete;
    Op &operator=(Op const &) = delete;
    // virtual destructor
    virtual ~Op() = default;
    // Use this if you need a destructor which has access to a Graph object.
    API_EXPORT virtual void clear(Graph *graph_in) {}
    API_EXPORT virtual GraphStatus prepare(hnnx::OpIoPtrs const &, bool tcm_available) = 0;
    API_EXPORT virtual GraphStatus allocate(Graph &graph_in) = 0;
    API_EXPORT OpId id(const Graph &graph_in) const noexcept;

    API_EXPORT ChkptStoreType get_chkpt_store_type(const Graph &graph_in) const;
    API_EXPORT OpStoreType get_op_store_type(const Graph &gr) const;
    API_EXPORT static OpStoreType get_op_store_type(uint32_t flags)
    {
        return OpStoreType((flags >> ChkptFlagShift) & ChkptOpFlagMask);
    };

    API_EXPORT void set_chkpts(Graph &graph_in, const std::pair<int, int> chkpts);
    API_EXPORT void set_chkpts(Graph &graph_in, int gate, int done)
    {
        set_chkpts(graph_in, std::make_pair(gate, done));
    }

    API_EXPORT const Tensor *get_input(size_t which) const { return get_input_output(which, true); }
    API_EXPORT const Tensor *get_output(size_t which) const { return get_input_output(which, false); }

    API_EXPORT virtual bool set_input(size_t which, const Tensor *tensor) { return false; }

    API_EXPORT virtual bool is_valid() const noexcept = 0; // Is this op valid in this situation?
    API_EXPORT void dependence_resolved() noexcept;
    API_EXPORT bool
    is_const() const noexcept; // Data for this op always available, execution and dependence tracking not needed.
    API_EXPORT virtual std::pair<size_t, size_t> num_inputs_outputs() const = 0;
    API_EXPORT inline size_t num_outputs() const { return num_inputs_outputs().second; }
    API_EXPORT inline size_t num_inputs() const { return num_inputs_outputs().first; }
    API_EXPORT const char *true_name() const;
    API_EXPORT virtual Flags_word get_flag_word() const { return hnnx::flags_for<Op>(); }
    virtual const char *get_docs() const { return hnnx::docs_for<Op>(); } //LCOV_EXCL_LINE [SAFTYSWCCB-1542]

    /// @brief
    ///     Gets the typeid mangled name of the kernel implementing this operator
    API_EXPORT const char *true_func() const noexcept;

    // get type, allowing for SimpleOpWrapper to get forwarded type.
    API_EXPORT std::type_info const *get_type_extended() const;
    API_EXPORT bool get_flag(Flags flag) const { return hnnx::test_flag_for(get_flag_word(), flag); }
    //LCOV_EXCL_START [SAFTYSWCCB-1542]
    API_EXPORT bool get_flag_and(Flags flag0, Flags flag1) const
    {
        return hnnx::test_flag_and(get_flag_word(), flag0, flag1);
    }
    //LCOV_EXCL_STOP
    API_EXPORT inline hnnx::blockid_set_t input_blocks(int mc_sel = -1) const
    {
        return input_output_blocks(true, mc_sel);
    }
    API_EXPORT inline hnnx::blockid_set_t input_blocks(MemoryClass mc) const
    {
        return input_output_blocks(true, int(mc));
    }
    API_EXPORT inline hnnx::blockid_set_t output_blocks(int mc_sel = -1) const
    {
        return input_output_blocks(false, mc_sel);
    }
    API_EXPORT inline hnnx::blockid_set_t output_blocks(MemoryClass mc) const
    {
        return input_output_blocks(false, int(mc));
    }

    API_EXPORT virtual void enumerate_blocks(hnnx::MemBlockEnumerator &en, bool is_input) const {}
    API_EXPORT inline void enumerate_input_blocks(hnnx::MemBlockEnumerator &en) const { enumerate_blocks(en, true); }
    API_EXPORT inline void enumerate_output_blocks(hnnx::MemBlockEnumerator &en) const { enumerate_blocks(en, false); }

    // The 'ef' parameter to these functions is a callable (function, lambda, std::function...)
    // compatible with MemBlockEnumerator::supply_blocks_func
    template <typename ENFUNC> API_EXPORT inline void enumerate_blocks_withfunc(ENFUNC &&ef, bool is_input) const
    {
        hnnx::MemBlockEnumWrapper<std::remove_reference_t<ENFUNC>> enumer(std::forward<ENFUNC>(ef));
        this->enumerate_blocks(enumer, is_input);
    }
    template <typename ENFUNC> API_EXPORT inline void enumerate_input_blocks_withfunc(ENFUNC &&ef) const
    {
        enumerate_blocks_withfunc(std::forward<ENFUNC>(ef), true);
    }
    template <typename ENFUNC> API_EXPORT inline void enumerate_output_blocks_withfunc(ENFUNC &&ef) const
    {
        enumerate_blocks_withfunc(std::forward<ENFUNC>(ef), false);
    }

    API_EXPORT virtual void serialize(hnnx::SerOpsInterface &) const = 0;
    using tensor_deserializer_register_func = int (*)();

    // there are fewer combinations of true_output_tuple_type than there are
    // TypicalOpIO, so it's better to return a function here than to make one.
    //
    static constexpr tensor_deserializer_register_func get_tensor_deserializer_register_func()
    {
        return hnnx::deserialize_tensor_tuple<std::tuple<>, false>::f_ptr();
    }
    API_EXPORT float cost(const Graph &) const;

    // 'clone_mode' for Op::clone
    enum op_clonemode {
        opclone_auto, // opclone_dup if op has NULL_EXEC, otherwise opclone_realloc
        opclone_realloc, // when duplicating the output tensors, zero all block ids and reallocate
        opclone_dup // duplicate output with same block ids; and suppress ctor hooks.
    };
    //
    //
    // Clone an Op.
    // This makes an op with the same input tensors as the current Op, and the specified
    // OpId. The new op has new output tensors which are 'duplicate_clone' of the output
    // tensors of the existing Op.
    //
    // Caveats:
    //  - ALWAYS CHECK FOR NULL RETURN VALUE. There is no errlog if the clone fails, just a null return.
    //  - Not all Op can be cloned in this way; it applies only to Ops which can be created from OpDef.generate().
    //    So, no things like SpillOp or ValidateOp.
    //  - The Op's 'constructor hooks' are only called if 'opclone_realloc' mode is specified (or selected via opclone_auto)
    //  - 'prepare' is called with tcm_available = true; it is assumed that if new Op needs that, the original
    //    op needed it too.
    //  - You can pass an alternate Op type ('clone X but as Y'... ); use extreme caution, will only work if the
    //    number and types of input and output tensors are supported by Y.
    //
    API_EXPORT hnnx::uptr_Op clone(Graph &graph_in, OpId, op_clonemode opclonemode = opclone_auto,
                                   std::type_info const *as_type = nullptr) const;

    // these are not virtual, but are thin wrappers of swap_output so they act as if virtual.
    /// @brief remove output tensor from an op
    /// returns empty pointer on failure. Always fails on Op types which don't overload swap_output.
    API_EXPORT hnnx::uptr_Tensor steal_output(size_t which);
    /// @brief attach an output tensors to an Op.
    /// succeeds (and returns true) if val is not empty, 'which' is in range and the Op doesn't already have that
    //  output set; otherwise it returns false and val is unchanged.
    //  Always fails on Op types which don't overload swap_output.
    API_EXPORT bool install_output(size_t which, hnnx::uptr_Tensor &&val);

  protected:
    API_EXPORT virtual Tensor const *get_input_output(size_t which, bool is_input) const = 0;
    // swap_output underpins steal_output and install_output:
    // it should:
    //    return false, if these operations are not supported, or if the index is too large;
    //    otherwise:
    //       - if the incoming val is empty, treat it as 'steal_input'; if ok, swap and return true;
    //         perform any other side-effects which may be needed.
    //       - otherwise it's a 'set_output'. return false if the output is already set; otherwise
    //         swap and return true (and perform any side-effects).
    //
    API_EXPORT virtual bool swap_output(size_t which, hnnx::uptr_Tensor &val);

    //
    // These are used in enumerate_blocks implementations
    API_EXPORT void enumerate_op_input_blocks(hnnx::MemBlockEnumerator &en, Tensor const *const *inputs_p,
                                              unsigned n) const;
    API_EXPORT void enumerate_op_output_blocks(hnnx::MemBlockEnumerator &en, hnnx::uptr_Tensor const *outputs_p,
                                               unsigned n) const;
    template <typename VIN, typename VOUT>
    [[gnu::always_inline]] inline void enumerate_op_blocks(hnnx::MemBlockEnumerator &en, VIN const &vinputs,
                                                           VOUT const &voutputs, bool is_input) const
    {
        if (is_input) {
            enumerate_op_input_blocks(en, vinputs.data(), vinputs.size());
        } else {
            enumerate_op_output_blocks(en, voutputs.data(), voutputs.size());
        }
    }

    // legacy interface, implemented via enumerate_blocks
    API_EXPORT hnnx::blockid_set_t input_output_blocks(bool is_input, int mc_sel) const;

    // subclasses can forward enumerate_blocks to this method to reduce copy-pasta -
    // it just traverses the inputs (or outputs) using the virtual API and calls
    // enum_memory_blocks on all the tensors it discovers.
    API_EXPORT void enumerate_blocks_generic(hnnx::MemBlockEnumerator &en, bool is_input) const;

    // subclasses can forward 'allocate' to this method to reduce copy-pasta.
    // it just calls allocate on all of the outputs it discovers using the
    // virtual function API.
    // If allocator is null, it uses the alloc in the graph.
    API_EXPORT GraphStatus allocate_generic(hnnx::Allocator *alloc = nullptr);

    API_EXPORT void serialize_internal(hnnx::Serializer &sctx, ChkptStoreType st) const;
    API_EXPORT uint32_t get_serialize_flags(const Graph &, ChkptStoreType st) const;
};

/**
 * @brief All Op source files must invoke this macro at the top of the file,
 * before any COST_OF/REGISTER_OP/DEF_OPT calls.
 *
 */
#define BEGIN_OP_DEFINITION(NAME) INITIALIZE_TABLES()

/**
 * @brief All Op source files must invoke this macro at the bottom of the
 * file, after all COST_OF/REGISTER_OP/DEF_OPT calls.
 *
 */
#define END_OP_DEFINITION(NAME) FINALIZE_TABLES(NAME)

/**
 * @brief Op Cost return types
 * As of now we support 3 types of cost for Ops
 */

struct StandardCosts {
    static constexpr float GLACIAL = 0x1.0p48; // 2**48 cycles
    static constexpr float SNAIL = 0x1.0p32; // 2**32 cycles
    static constexpr float FAST = 0x1.0p8; // 256 cycles
    static constexpr float FREE = 0x1.0p-64;
    static constexpr float DISABLE = 0x1.0p50; // 2**50 cycles, worse than GLACIAL, don't select this.
};

/*
 * EJP: FIXME: Cost here is a simple fixed cost.
 * Having simple costs available and a slow fixed cost available by default is great.
 *
 * But to accurately reflect cost, we need be able to inspect the details of the op definition.
 * For example, a const of a convolution will depend on the types and shapes of
 * weights and activations.
 *
 */

namespace hnnx {

/**
 * Return the cost_function_t object for the Op.
 * The Ops need to specialize this class
 * if its cost differs from the default one.
 */

template <typename ConcreteOp> constexpr hnnx::cost_function_t get_costf()
{
    return hnnx::cost_function_t(StandardCosts::GLACIAL);
}

/*
 * For concrete version of an op, see typical_op.h
 */

/*
 * Not the typical Const op, but a wrapper around a Tensor that someone has formed...
 */

class ConstWrapperOp : public Op {
    uptr_Tensor owned_tensor;

  public:
    API_EXPORT ConstWrapperOp(Graph &graph_in, OpId my_id_in, const OpDef *op_def_in);
    API_EXPORT ConstWrapperOp(Graph &graph_in, OpId my_id_in, uptr_Tensor owned_tensor_in);
    API_EXPORT explicit ConstWrapperOp(hnnx::Deserz &dctx);
    // make a persistent Flat tensor with the given type, shape, data,
    // and wrap it in a ConstWrapperOp. May not support all DTtype, but definitely
    // Float32 and Int32, and QUint8. See implementaton in op.cc
    API_EXPORT ConstWrapperOp(Graph &graph_in, OpId my_id_in, const OutputDef &def, void const *data_in);

    API_EXPORT void clear(Graph *graph_in) override;
    API_EXPORT virtual GraphStatus execute(EXECUTE_METHOD_PARMS) const noexcept override
    {
        return GraphStatus::Success;
    }
    API_EXPORT virtual hnnx::Executable::ItemType compile(Graph &graph_in) const noexcept override
    {
        return hnnx::Executable::null_item();
    }
    //LCOV_EXCL_START [SAFTYSWCCB-1542]
    API_EXPORT virtual GraphStatus prepare(hnnx::OpIoPtrs const &, bool tcm_available) override
    {
        return GraphStatus::Success;
    }
    API_EXPORT virtual GraphStatus allocate(Graph &graph_in) override { return GraphStatus::Success; }
    //LCOV_EXCL_STOP
    API_EXPORT virtual std::pair<size_t, size_t> num_inputs_outputs() const override { return {0, 1}; }
    API_EXPORT virtual bool is_valid() const noexcept override { return true; }

    API_EXPORT const Tensor *tensor_p() const { return owned_tensor.get(); }
    API_EXPORT virtual void serialize(hnnx::SerOpsInterface &sctx) const override;

  protected:
    API_EXPORT virtual const Tensor *get_input_output(size_t which, bool is_input) const override
    {
        return is_input ? nullptr : tensor_p();
    }
};

class ShapeWrapperOp : public Op {
    uptr_Tensor shape; // must actually be a TensorShape
  public:
    API_EXPORT ShapeWrapperOp(Graph &graph_in, OpId my_id_in, const OpDef *op_def_in);
    API_EXPORT ShapeWrapperOp(Graph &graph_in, OpId my_id_in, uptr_Tensor owned_tensor_in);
    API_EXPORT explicit ShapeWrapperOp(hnnx::Deserz &);
    API_EXPORT virtual GraphStatus execute(EXECUTE_METHOD_PARMS) const noexcept override
    {
        return GraphStatus::Success;
    }
    API_EXPORT virtual hnnx::Executable::ItemType compile(Graph &graph_in) const noexcept override
    {
        return hnnx::Executable::null_item();
    }
    //LCOV_EXCL_START [SAFTYSWCCB-1542]
    API_EXPORT virtual GraphStatus prepare(hnnx::OpIoPtrs const &, bool tcm_available) override
    {
        return GraphStatus::Success;
    }
    API_EXPORT virtual GraphStatus allocate(Graph &graph_in) override { return GraphStatus::Success; }
    //LCOV_EXCL_STOP
    API_EXPORT virtual std::pair<size_t, size_t> num_inputs_outputs() const override { return {0, 1}; }
    API_EXPORT virtual bool is_valid() const noexcept override { return true; }

    API_EXPORT virtual void serialize(hnnx::SerOpsInterface &sctx) const override;

  protected:
    API_EXPORT virtual const Tensor *get_input_output(size_t which, bool is_input) const override
    {
        return is_input ? nullptr : shape.get();
    }
};

// MetaOpBase is a shim which provides empty defs for all of the =0 virtual methods,
// so that internal Ops (e.g. PreloadOp) can be based on this and not need to define any they don't need
//
class MetaOpBase : public Op {
  public:
    MetaOpBase(){};
    MetaOpBase(Graph &graph_in, unsigned long long int my_id_in) : Op(graph_in, my_id_in) {}
    explicit MetaOpBase(hnnx::Deserz &dctx) : Op(dctx) {}

    API_EXPORT virtual GraphStatus prepare(hnnx::OpIoPtrs const &,
                                           bool tcm_available) override; //{ return GraphStatus::Success;}
    API_EXPORT virtual GraphStatus allocate(Graph &graph_in) override; // { return GraphStatus::Success;}

    API_EXPORT virtual bool is_valid() const noexcept override; // {return false;}
    API_EXPORT virtual std::pair<size_t, size_t> num_inputs_outputs() const override; //{ return {0,0};}
    API_EXPORT virtual Tensor const *get_input_output(size_t which,
                                                      bool is_input) const override; // {return nullptr;}
    API_EXPORT virtual void serialize(hnnx::SerOpsInterface &) const override; // {}
    API_EXPORT virtual uptr_Op clone_meta(Graph &graph_in, OpId new_opid) const; // {return uptr_Op(nullptr);}
};

// SpecialPrepOpBase is a shim (based on MetaOpBase) which provides some new virtual methods
// that are queried during GraphDeps stage of preparation.
// This is intended for things like SuperTileOp which want to add these discovery methods.
//
// LCOV_EXCL_START [SAFTYSWCCB-1542]

class SpecialPrepOpBase : public MetaOpBase {
  public:
    SpecialPrepOpBase(){};
    SpecialPrepOpBase(Graph &graph_in, unsigned long long int my_id_in) : MetaOpBase(graph_in, my_id_in) {}
    explicit SpecialPrepOpBase(hnnx::Deserz &dctx) : MetaOpBase(dctx) {}

    // new virtual methods to populate the OpDesc for the op:
    // These return 'true' if the result was changed, and 'false' if unchanged; the caller can set
    // the variable to reasonable default before calling, and then ignore the result.
    API_EXPORT virtual bool get_opdef_name(OpId opid, opname_tag_t &result) const; // {return false} in op.cc
    API_EXPORT virtual bool get_splithist(OpId opid, splithist_t &result) const { return false; }
    API_EXPORT virtual bool get_is_volatile(OpId opid, bool &result) const { return false; }
    API_EXPORT virtual bool get_cost(const Graph &, OpId opid, float &result) const { return false; }
    API_EXPORT virtual bool get_flags_word(OpId opid, Flags_word &result) const { return false; }

    // make a CostBasedFeatureDesc. If 'false' is returned, it should be obtained 'in the usual manner'.
    API_EXPORT virtual bool get_costbased_feature(OpId opid, CostBasedFeatureDesc &result) const { return false; }
};
// LCOV_EXCL_STOP

// this is a base class for adding hooks on construction of Ops.
// May not have data members or dtor - so it's just a vtable pointer, and is constexpr constructable
// All methods must be const, and return GraphStatus; the 'default' methods do nothing and return GraphNotApplicable.
// So, we can allow two or more hooks to be attached to an Op; when calling a method,
// we will call it on the first one, and if it returns NotApplicable, we will try the next
// one, etc (so they are 'layered', in effect).
//
class OpHookBase {
  public:
    API_EXPORT virtual GraphStatus pre_output_prep(OpIoPtrs const &, Op &) const;
    API_EXPORT virtual GraphStatus pre_allocate(OpIoPtrs const &, Op &) const;
};

// if the indicated Op is a SpawnOp, get its inner op ptr, otherwise null.

using SimpleOpFactory = std::unique_ptr<SimpleOpBase> (*)(size_t n_inputs_in, size_t n_outputs_in,
                                                          Tensor const *const *inputs_in,
                                                          OutputDef const *const *outputs_in, Graph &graph_in);

} // namespace hnnx

POP_VISIBILITY()

#endif /*OP_H*/
