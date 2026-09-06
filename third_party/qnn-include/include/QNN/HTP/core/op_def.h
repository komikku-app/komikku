//==============================================================================
//
// Copyright (c) 2020,2022 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OP_DEF_H
#define OP_DEF_H 1

#include <cassert>
#include <functional>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "interface_defs.h"
#include "splithist.h"
#include "tensor.h"
#include "opname_tag.h"
#include "weak_linkage.h"
#include "macros_attribute.h"

// does an opname_tag_t start with the given string.
// this assumes that converting opname_tag to string_view is cheaper than to std::string
namespace hnnx {
inline bool starts_with(opname_tag_t const &opstr, std::string const &pref)
{
    std::string_view const sv{opstr};
    std::string_view sv2;
    auto found = sv.find("::");
    if (found != std::string_view::npos) {
        sv2 = sv.substr(found + 2);
    } else {
        sv2 = sv;
    }
    int const n = pref.size();
    return sv2.size() >= n && memcmp(sv2.data(), pref.c_str(), n) == 0;
}
inline bool starts_with(opname_tag_t const &opstr, char const *pref)
{
    std::string_view const sv{opstr};
    std::string_view sv2;
    auto found = sv.find("::");
    if (found != std::string_view::npos) {
        sv2 = sv.substr(found + 2);
    } else {
        sv2 = sv;
    }
    int const n = strlen(pref);
    return sv2.size() >= n && memcmp(sv2.data(), pref, n) == 0;
}

class OpIoPtrs;
} // namespace hnnx

// is_deleted:
//    OpDef has no downstream, and is slated for deletion
// is_hidden:
//    OpDef has been replaced or otherwise inactivated (always true if is_deleted).
// is_const:
//    OpDef has constant output
// is_volatile:
//    output is not invariant, even if inputs are
//    (or if no inputs).
//    Set for ops if no inputs, or if name starts with '*'.
// is_retain:
//    do not remove, even if it has no consumers.
//    Currently set the same as volatile
//
PUSH_VISIBILITY(default)

class OpDefFlags {
    constexpr static uint32_t BIT_deleted = 1; // deleted, pending removal
    constexpr static uint32_t BIT_hidden = 2; // replaced,hidden; not necessarily pending removal (>= deleted).
    constexpr static uint32_t BIT_const = 4; // is an OpDef_ConstBase, or an op with const output.
    constexpr static uint32_t BIT_volatile = 8; // is not invariant, even if all inputs are invariant.
    constexpr static uint32_t BIT_retain = 16; // do not remove even if all outputs are not used.
    constexpr static uint32_t BIT_dummy_out = 32; // is a $Out node
    constexpr static uint32_t BIT_constbase = 64; // if and only if it's a OpDef_ConstBase. immutable.
    constexpr static uint32_t BIT_in_constmap = 128; // set if it's in graph.const_map; <= constbase.
    // Considered mutable (can change via const ref),
    constexpr static uint32_t BIT_fake_unsigned = 256; // OpDef had dtype changed to unsigned during prepare
    constexpr static uint32_t BIT_custom_op = 512; // OpDef is a custom op
    // Rules for assigning flags at construction:
    //  OpDef_ConstBase subclasses get 'const' and 'constbase'.
    //  otherwise:
    //    - if the opstr starts with "#', set 'const'
    //    - otherwise if no inputs, has outputs
    //          set 'volatile'
    //    - otherwise if no outputs, or the the opstr starts with '*',
    //          set 'volatile' and 'retain'
    //  but for $Out nodes,we set retain | dummy_out.
    //
    // The following combinations never occur:
    //   deleted=1, and hidden = 0.
    //   constbase=1, and const=0
    //   constbase=0, and in_constmap = 1.
    //
    //
    uint16_t flags;

  protected:
    uint16_t opstr_hashval; //  hash of opstr.
    API_EXPORT static inline int flag_init(hnnx::opname_tag_parm_t opstr, int n_in, int n_out)
    {
        std::string_view const sv{opstr};
        char c0;
        auto found = sv.find("::");
        if (found != std::string_view::npos && !(sv.substr(found + 2).empty())) {
            c0 = sv[found + 2];
        } else if (found == std::string_view::npos && sv.size() > 0) {
            c0 = sv[0];
        } else {
            return 0;
        }
        return (c0 == '#')                ? BIT_const
               : (n_out == 0)             ? (BIT_retain | BIT_volatile)
               : (n_in == 0 || c0 == '*') ? (BIT_volatile)
               : (opstr == "$Out")        ? (BIT_retain | BIT_dummy_out)
                                          : 0;
    }
    // ctor used by OpDef (non-const)
    API_EXPORT OpDefFlags(hnnx::opname_tag_parm_t opstr, int n_in, int n_out)
        : flags((uint16_t)flag_init(opstr, n_in, n_out)), opstr_hashval((uint16_t)hnnx::find_opname_hash(opstr))
    {
    }
    // ctor used by OpDef_ConstBase
    API_EXPORT OpDefFlags(hnnx::opname_tag_parm_t opstr,
                          bool isconst) // is_const ignored; always true
        : flags(BIT_const | BIT_constbase), opstr_hashval((uint16_t)hnnx::find_opname_hash(opstr))
    {
    }
    template <unsigned F> bool set_flag_state(bool val)
    {
        unsigned const f = flags;
        if (val)
            flags = f | F;
        else
            flags = f & ~F;
        return (f & F) != 0;
    }

  public:
    API_EXPORT unsigned get_opstr_hash() const { return opstr_hashval; }
    API_EXPORT bool is_const() const { return (flags & BIT_const) != 0; }
    API_EXPORT bool is_retain() const { return (flags & BIT_retain) != 0; }
    API_EXPORT bool is_volatile() const { return (flags & BIT_volatile) != 0; }
    API_EXPORT bool is_deleted() const { return (flags & BIT_deleted) != 0; }
    API_EXPORT bool is_hidden() const { return (flags & BIT_hidden) != 0; }
    API_EXPORT bool is_dummy_out() const { return (flags & BIT_dummy_out) != 0; }
    API_EXPORT bool is_constbase() const { return (flags & BIT_constbase) != 0; }
    API_EXPORT bool is_in_constmap() const { return (flags & BIT_in_constmap) != 0; }
    API_EXPORT bool is_fake_unsigned() const { return (flags & BIT_fake_unsigned) != 0; }
    API_EXPORT bool is_custom_op() const { return (flags & BIT_custom_op) != 0; }
    // this is all we should need.
    API_EXPORT bool set_retain(bool val = true) { return set_flag_state<BIT_retain>(val); }
    API_EXPORT void set_deleted() { flags |= (BIT_hidden | BIT_deleted); }
    API_EXPORT void set_hidden() { flags |= BIT_hidden; }
    // this is allowed via 'const' ref
    API_EXPORT bool set_is_in_constmap(bool val = true) const
    {
        return const_cast<OpDefFlags &>(*this).set_flag_state<BIT_in_constmap>(val);
    }
    API_EXPORT bool set_fake_unsigned(bool val = true) { return set_flag_state<BIT_fake_unsigned>(val); }
    API_EXPORT void set_custom_op() { flags |= BIT_custom_op; }
    API_EXPORT void serialize(hnnx::Serializer &sctx) const;
    API_EXPORT OpDefFlags(hnnx::Deserializer &dctx);

  protected:
    // derived ctors can do this.
    API_EXPORT void set_const(bool val = true) { set_flag_state<BIT_const>(val); }
};

/**
 * @class OpDef
 *
 * Not sure how much we want to templatize here...
 *
 * We want to split out the definition of ops from their execution behavior.
 * Performance during definition / graph transformation is not as essential, but
 * we need to avoid big-O problems and ease-of-use is important.
 *
 * For pattern matching we'd like to have some facilities to consider an op definition
 * meet some things in typical usage
 *
 */

/*
 * An Op Reference refers to an OpID and Output Index in some Graph
 * This should maybe be called "OutRef" or "TensorRef" or something?
 *
 * We'll continue to have dereference() return an OpDef for compatibilty
 * But we often want the output definition, so add interfaces to get the
 * pointed-to output as well as information about that output for convenience.
 */

class Op;
class OpDef;
class GraphPrepare;

class RefersToGraph {
  public:
    std::reference_wrapper<GraphPrepare> m_graph;

    RefersToGraph(GraphPrepare &g) : m_graph(g) {}
    GraphPrepare &graph() const { return m_graph.get(); }
};
// this is used as the parameter to 'dereference' and 'output_def'
// so they can take Graph &, or anything based on RefersToGraph &,
// or a pointer to those.
class AnyGraphContext {
    GraphPrepare *m_graphp;

  public:
    AnyGraphContext(GraphPrepare &g) : m_graphp(&g) {}
    AnyGraphContext(GraphPrepare *gp) : m_graphp(gp) {}
    AnyGraphContext(RefersToGraph &rtg) : m_graphp(&rtg.graph()) {}
    AnyGraphContext(RefersToGraph *rtgp) : m_graphp(&rtgp->graph()) {}
    GraphPrepare &graph() const { return *m_graphp; }
};

class OpRef final {
  public:
    unsigned long long int input_id;

    explicit OpRef(unsigned long long int in_id) // from id
        : input_id(in_id)
    {
    }
    OpRef(unsigned long long int in_id,
          size_t out_idx) // from (id, idx) - legacy
        : input_id(in_id)
    {
    }
    OpRef() : input_id() {}
    ~OpRef() = default;
    OpRef(const OpRef &) = default;
    OpRef(OpRef &&) = default;
    OpRef &operator=(OpRef const &) = default;
    OpRef &operator=(OpRef &&) = default;
    API_EXPORT OpDef &dereference(AnyGraphContext) const;
    API_EXPORT const OutputDef &output_def(AnyGraphContext) const;

    // note,  these ops all do a lookup via output_def()
    API_EXPORT size_t rank(AnyGraphContext c) const { return output_def(c).rank; }
    API_EXPORT DType dtype(AnyGraphContext c) const { return output_def(c).dtype; }
    API_EXPORT size_t dim(AnyGraphContext c, size_t idx) const
    {
        const OutputDef &od = output_def(c);
        assert(idx < od.rank);
        return od.max_sizes[idx];
    }
    API_EXPORT int32_t zero_offset(AnyGraphContext c) const { return output_def(c).zero_offset; }
    API_EXPORT float stepsize(AnyGraphContext c) const { return output_def(c).stepsize; }

    bool operator==(const OpRef &ref) const { return input_id == ref.input_id; }
    bool operator!=(const OpRef &ref) const { return !operator==(ref); }
};

class OpDef : public OpDefFlags {
  protected:
    hnnx::splithist_t splithist;
    const std::reference_wrapper<GraphPrepare> graphref;
    class ForConst {
    };
    // this constructor is for OpDef_ConstBase
    // it sets the flags to just 'is_const | is_constbase'.
    API_EXPORT OpDef(GraphPrepare &graph_in, OpId my_id_in, hnnx::opname_tag_parm_t opstr_in, OutputDef const &odef,
                     ForConst const &)
        : OpDefFlags(opstr_in, true), splithist(), graphref(graph_in), id(my_id_in), opstr(opstr_in), input_defs(),
          output_def(odef)
    {
    }

  public:
    OpId id;
    hnnx::opname_tag_t opstr;
    API_EXPORT void change_opstr_internal(hnnx::opname_tag_t new_opstr)
    {
        opstr = new_opstr;
        opstr_hashval = hnnx::find_opname_hash(new_opstr);
    }

    std::vector<OpRef> input_defs; // These should be mutable, we mess with them during optimization
    OutputDef output_def;

    API_EXPORT inline hnnx::splithist_t get_splithist() const { return splithist; }
    API_EXPORT inline void set_splithist(hnnx::splithist_t val) { splithist = val; }
    API_EXPORT inline void set_splithist(OpDef const &other) { splithist = other.splithist; }
    API_EXPORT inline void inherit_memos(AnyGraphContext, OpDef const &other);
    // with 0 or 1 output (output_def_in may be null)
    API_EXPORT OpDef(GraphPrepare &graph_in, OpId my_id_in, hnnx::opname_tag_parm_t opstr_in,
                     std::vector<OpRef> &&input_defs_in, OutputDef const *output_def_in, hnnx::splithist_t sl)
        : OpDefFlags(opstr_in, input_defs_in.size(), (output_def_in == nullptr) ? 0 : 1), splithist(sl),
          graphref(graph_in), id(my_id_in), opstr(opstr_in), input_defs(std::move(input_defs_in)), output_def()
    {
        if (output_def_in != nullptr) {
            output_def = *output_def_in;
        } else {
            output_def.dtype = DType::None;
        }
    }

    API_EXPORT OpDef(GraphPrepare &graph_in, OpId my_id_in, hnnx::opname_tag_parm_t opstr_in,
                     std::vector<OpRef> &&input_defs_in, OutputDef const *output_def_in)
        : OpDefFlags(opstr_in, input_defs_in.size(), (output_def_in == nullptr) ? 0 : 1), splithist(),
          graphref(graph_in), id(my_id_in), opstr(opstr_in), input_defs(std::move(input_defs_in)), output_def()
    {
        if (output_def_in != nullptr) {
            output_def = *output_def_in;
        } else {
            output_def.dtype = DType::None;
        }
    }

    OpDef(OpDef const &) = delete; // use the .copy() method
    OpDef(OpDef &&) = default; // we can return them though
    OpDef &operator=(OpDef const &) = delete;
    OpDef &operator=(OpDef &&) = delete;
    API_EXPORT GraphPrepare &graph() const { return graphref.get(); }
    // make a copy with the same output and no inputs; then
    // copy shape from 'shape_from' (if not null) and output
    //  spec from 'outp_from' (if not null)
    API_EXPORT OpDef make_output_exemplar(OutputDef const *size_from, OutputDef const *outp_from) const;
    // make a copy with the same output and no inputs
    API_EXPORT inline OpDef make_output_exemplar() const { return make_output_exemplar(nullptr, nullptr); }

    API_EXPORT size_t n_inputs() const { return input_defs.size(); }
    API_EXPORT size_t n_outputs() const { return output_def.dtype == DType::None ? 0 : 1; }
    //> true if the OpDef has outputs
    API_EXPORT bool has_outputs() const { return !(output_def.dtype == DType::None); }
    //> true if the node is a 'sink' for the purposes of sheduler
    /// If we add special nodes with no outputs that are not graph sinks, they can be excluded here.
    //
    API_EXPORT bool is_graph_sink() const { return !has_outputs(); }
    //> True if the OpDef has multiple outputs (has 'Multi' output type)
    API_EXPORT bool has_multiple_outputs() const { return output_def.dtype == DType::Multi; }
    API_EXPORT OpRef reference() const { return OpRef{id, 0}; }
    // these are only safe to use when has_outputs()
    API_EXPORT OutputDef &get_outputdef() { return output_def; } //use when need to modify output_def
    API_EXPORT OutputDef const &get_outputdef() const { return output_def; } //return read-only output_def

    API_EXPORT virtual hnnx::uptr_Op generate(hnnx::OpIoPtrs const &) const;
    API_EXPORT virtual const uint8_t *const_data_ptr() const { return nullptr; }
    API_EXPORT virtual size_t const_data_len() const { return 0; }
    // By convention, op names that start with '#' are constant regardless of input
    // This is useful (for example) to get quantization parameters out of output defs
    API_EXPORT virtual const Tensor *get_tensor() const { return nullptr; }
    API_EXPORT virtual void release_memory() {}
    virtual ~OpDef() = default;
    API_EXPORT static bool compare_less(const OpDef &lhs, const OpDef &rhs);

    API_EXPORT static bool compare_eq(const OpDef &lhs, const OpDef &rhs);

    struct compare_less_ptr_functor {
        bool operator()(const OpDef *lhs, const OpDef *rhs) const { return OpDef::compare_less(*lhs, *rhs); }
    };
    API_EXPORT bool exact_same_as(const OpDef &rhs);
    API_EXPORT virtual void nndebug_serialize(hnnx::Serializer &sctx) const;
    API_EXPORT void serialize(hnnx::Serializer &sctx) const;
    API_EXPORT OpDef(GraphPrepare &graph_in, hnnx::Deserializer &dctx);
};

namespace hnnx {

API_FUNC_EXPORT bool compare_eq(OutputDef const &, OutputDef const &);

// common base for OpDef_Const and OpDef_Shape.
// these are the OpDef which will be kept in const_map, keyed by the content_hash.
// compare_eq() is used to check exact match amongst any two instances; compare_less may be
// used to order them.
//
// the ordering will be a little arbitrary - if two have different hashes, they will first be ordered
// according to the hashes; otherwise we will go through a multi-key compare of the OpDef attributes
// and finally call tensor_compare (if both are OpDef_Const). If a more rational ordering is needed,
// this can be added, but it will tend to be slower than the hashed compare if you have a lot of Const
// with matching shapes.
// If A and B are both OpDefConstBase, then OpDef::compare_less(A,B) will be the same as OpDef_ConstBase::compare_less(A,B).
//
// The content_hash is never 0; zero is used to mark 'unknown'; the next time get_content_hash() is called, find_content_hash()
// will be called to determine the hash, and then it will be stored in content_hash for next time.
//
class OpDef_ConstBase : public OpDef {
    mutable uint32_t content_hash = 0;

  protected:
    OpDef_ConstBase(GraphPrepare &graph_in, OpId my_id_in, opname_tag_parm_t opstr, OutputDef const &output_def)
        : OpDef(graph_in, my_id_in, opstr, output_def, OpDef::ForConst{})
    {
    }

  public:
    // finds content_hash ( if not already found ) and return it
    API_EXPORT uint32_t get_content_hash() const
    {
        return (content_hash == 0) ? get_content_hash_func() : content_hash;
    }
    API_EXPORT void invalidate_content_hash() { content_hash = 0; }
    // does it have a content_hash?
    API_EXPORT inline bool has_content_hash() const { return content_hash != 0; }
    API_EXPORT inline OpDef_ConstBase(GraphPrepare &graph_in, Deserializer &dctx)
        : OpDef(graph_in, dctx), content_hash(0)
    {
    }

  protected:
    API_EXPORT uint32_t find_basic_hash() const noexcept; // find the hash of opstr and OutputDef.
    API_EXPORT uint32_t get_content_hash_func() const noexcept;
    API_EXPORT virtual uint32_t find_content_hash() const noexcept = 0;
};

API_FUNC_EXPORT int compare_constbase(const OpDef_ConstBase &lhs, const OpDef_ConstBase &rhs);
API_FUNC_EXPORT inline bool compare_constbase_eq(const OpDef_ConstBase &lhs, const OpDef_ConstBase &rhs)
{
    return lhs.get_content_hash() == rhs.get_content_hash() && compare_constbase(lhs, rhs) == 0;
}

class OpDef_Const : public OpDef_ConstBase {
  public:
    std::unique_ptr<Tensor> const_data;
    OpDef_Const(OpDef_Const const &) = delete;
    OpDef_Const &operator=(const OpDef_Const &) = delete;
    OpDef_Const(OpDef_Const &&) = delete;
    OpDef_Const &operator=(OpDef_Const &&) = delete;
    API_EXPORT OpDef_Const(GraphPrepare &graph_in, OpId my_id_in, OutputDef const &output_def, const uint8_t *data_in,
                           size_t len);
    API_EXPORT OpDef_Const(GraphPrepare &graph_in, OpId my_id_in, std::unique_ptr<Tensor> tensor_in);
    API_EXPORT virtual ~OpDef_Const();
    API_EXPORT virtual const uint8_t *const_data_ptr() const override;
    API_EXPORT virtual size_t const_data_len() const override;
    API_EXPORT virtual uptr_Op generate(OpIoPtrs const &) const override;
    API_EXPORT virtual const Tensor *get_tensor() const override { return const_data.get(); }
    API_EXPORT virtual void release_memory() override;
    API_EXPORT void serialize(Serializer &sctx) const;
    API_EXPORT OpDef_Const(GraphPrepare &graph_in, Deserializer &dctx);

  protected:
    API_EXPORT virtual uint32_t find_content_hash() const noexcept override;
};

class OpDef_Shape : public OpDef_ConstBase {
  public:
    API_EXPORT OpDef_Shape(GraphPrepare &graph_in, OpId my_id_in, OutputDef const &output_def)
        : OpDef_ConstBase(graph_in, my_id_in, "$Shape", output_def)
    {
    }
    API_EXPORT virtual const uint8_t *const_data_ptr() const override { return nullptr; }
    API_EXPORT virtual size_t const_data_len() const override { return 0; }
    API_EXPORT virtual uptr_Op generate(OpIoPtrs const &) const override;
    API_EXPORT void serialize(Serializer &sctx) const;
    API_EXPORT OpDef_Shape(GraphPrepare &graph_in, Deserializer &dctx);

  protected:
    // hash of a shape includes only the basic hash.
    API_EXPORT virtual uint32_t find_content_hash() const noexcept override;
};

// This implemnts SAME_ENCODING in optimization constraints.
API_FUNC_EXPORT inline bool same_encoding(OutputDef const &oda, OutputDef const &odb)
{
    DType const d = oda.dtype;
    if (odb.dtype != d) return false;
    // done if not quantized
    if (!DType_info(d).is_quant) return true;
    return (oda.stepsize == odb.stepsize && oda.zero_offset == odb.zero_offset);
}

// This implements SAME_SHAPE in optimization constraints.
API_FUNC_EXPORT inline bool same_shape(OutputDef const &oda, OutputDef const &odb)
{
    NN_UINT32_T const rankA = oda.rank;
    NN_UINT32_T const rankB = odb.rank;

    if (rankA != rankB) {
        return false;
    }

    for (size_t idx = 0; idx < rankA; idx++) {
        if (oda.max_sizes[idx] != odb.max_sizes[idx]) return false;
    }

    return true;
}

} // namespace hnnx

POP_VISIBILITY()

#endif
