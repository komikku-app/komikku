//==============================================================================
//
// Copyright (c) 2018-2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef MATCH_OP_H_
#define MATCH_OP_H_ 1

#include <vector>
#include <stdexcept>
#include "op_package_name.h"
#include "opname_tag.h"
#include "macros_attribute.h"
#include "weak_linkage.h"

#ifndef PREPARE_DISABLED
//
//
// Classes to make a 'MatchOp'
//
// Executing the match rule, in the namespace of MatchBuilder, causes it to
// return a MatchAstNode; that is analyzed to generate an instance of a subclass of MatchOpBase.
//
// MatchAstNode
//    - node of an Abstract Syntax tree which is built of the rule.
// MatchAstSubNode
//    - represents a single input to an Op in an MatchAstNode. May or may not contain a pointer
//      to a nested MatchAstNode.
// MatchBuilder
//   - just provides a namespace, in whicb 'executing' the text
//     of the rule causes it to return a MatchAstNode
//

// Limit of match params. This is the sum of
//   - all Op or OpVarIn the pattern, including the root; whether or not LET attached
//   - number of distinct operand names which occur in the pattern. Names which appear
//    in LET are not counted, even if they also appear outside of LET.
//
// This limit only affects the array sizes used in the Match object (only one instantiated,
// during optimization phase) so we can be generous

PUSH_VISIBILITY(default)

namespace hnnx {

static constexpr int MATCH_MAX_PATTERN = 80;

//
class MatchOpBase;
using MatchOp_uptr = std::unique_ptr<MatchOpBase>;
class MatchAstNode;
using MatchAst_uptr = std::unique_ptr<MatchAstNode>;

/// @brief MatchAstNode represents an input to an Op in a Match Pattern.
///
/// Only used as element of m_subnodes array in MatchAstNode.

class MatchAstSubnode {
    friend MatchAstNode;

  protected:
    // since most of the descendants are operand_tag_t, we keep those right in the
    // subnode list. If m_sub is empty, then m_optag should be non-empty, and vice versa
    MatchAst_uptr m_sub; ///<  points to a MatchAstNode if the input is a contained Op; empty otherwise
    operand_tag_t m_optag; ///<   m_optag contains the operand tag if the input is not a contained op ; "" otherwise
  public:
    MatchAstSubnode(MatchAst_uptr &&ptr) : m_sub(std::move(ptr)) {}
    MatchAstSubnode(operand_tag_parm_t otag) : m_optag(otag) {}
    MatchAstSubnode(char const *otag) : m_optag(otag) {}
    bool is_optag() const { return m_sub.get() == nullptr; }
    MatchAstNode const *get_subnode_p() const { return m_sub.get(); }
    MatchAstNode *get_subnode_p() { return m_sub.get(); }
    operand_tag_t get_optag() const { return m_optag; }
};

/// @brief MatchAstNode represents an Op in a match rule
///
/// A tree of these is built by executing the match rule in a MatchBuilder context, and
/// exists only long enough to be analyzed so that a MatchOp for the rule can be built.
///

class MatchAstNode {
    friend MatchOpBase;

  public:
    enum node_variant { is_Op, is_OpVarIn };
    virtual ~MatchAstNode() {}
    operand_tag_t m_optag; ///< tag assigned via LET, or empty if none ("*" at root)
    /// true if the node or subnode has any ref to optag (including its own m_optag)
    API_EXPORT bool contains_optag(operand_tag_parm_t optag) const;
    MatchAstNode &operator=(MatchAstNode const &) = delete;
    MatchAstNode(MatchAstNode const &) = delete;

  protected:
    opname_tag_t m_opname; ///< From first param of Op
    node_variant m_opvariant; ///< is Op or OpVarIn?
    std::vector<MatchAstSubnode> m_subnodes; ///< One for each input in the pattern

    /// Count the ops, and put their LET names in the map.
    /// Returns >1 if ok, -1 if error
    API_EXPORT int enumerate_ops(std::map<operand_tag_t, int> &op_tag_to_idx_map, int opcount);

    // if WITH_OPT_DEBUG is not defined, this returns an empty pointer.
    API_EXPORT std::unique_ptr<char[]> make_debug_desc(std::map<operand_tag_t, int> const &opertag_to_idx_map) const;

  public:
    API_EXPORT MatchAstNode(char const *, char const *, node_variant, int n_subnodes, MatchAstSubnode *subnodes);
};

} // namespace hnnx

//
// A rule definition looks like this, after pre-processing:
//
//template<>
//MatchOp_uptr MatchBuilder::matcher<SomeUniqueClass>( )
// {
//	return build_matcher( Op("Foo1", Op("Abc","X"),"Y", "Z"));
// }

class MatchBuilder {

  protected:
    /// \ingroup OptMatch
    /// @brief define an Op to match: Op("opname", ...inputs... )
    /// The inputs can be operand tags (strings), or nested Op (or OpVarIn)
    ///

    template <typename... S> using are_strings = std::conjunction<std::is_same<const char *, S>...>;

    template <typename... S> using have_matchast_uptr = std::disjunction<std::is_same<hnnx::MatchAst_uptr, S>...>;

    // just convert each 'input' to a MatchAstSubnode, and build a MatchAstNode from that.
    template <typename... Ts>
    API_EXPORT static typename std::enable_if<have_matchast_uptr<Ts...>::value, hnnx::MatchAst_uptr>::type
    Op(char const *opname, Ts &&...ts)
    {
        std::array<hnnx::MatchAstSubnode, sizeof...(Ts)> subnodes = {std::move(std::forward<Ts>(ts))...};
        return std::make_unique<hnnx::MatchAstNode>(opname, pkg_flag.c_str(), hnnx::MatchAstNode::is_Op, sizeof...(Ts),
                                                    subnodes.data());
    }

    template <typename... Ts>
    API_EXPORT static typename std::enable_if<are_strings<Ts...>::value, hnnx::MatchAst_uptr>::type
    Op(char const *opname, Ts... ts)
    {
        std::array<hnnx::MatchAstSubnode, sizeof...(Ts)> subnodes = {(ts)...};
        return std::make_unique<hnnx::MatchAstNode>(opname, pkg_flag.c_str(), hnnx::MatchAstNode::is_Op, sizeof...(Ts),
                                                    subnodes.data());
    }

    template <typename... Ts>
    API_EXPORT static typename std::enable_if<are_strings<Ts...>::value, hnnx::MatchAst_uptr>::type
    Op(char const *opname, hnnx::MatchAst_uptr &&t1, Ts... ts)
    {
        hnnx::MatchAstSubnode subnodes[sizeof...(Ts) + 1] = {std::move(t1), (ts)...};
        return std::make_unique<hnnx::MatchAstNode>(opname, pkg_flag.c_str(), hnnx::MatchAstNode::is_Op,
                                                    sizeof...(Ts) + 1, subnodes);
    }

    /// \ingroup OptMatch
    /// @brief define an Op to match, with at least the specified number of inputs: OpVarIn("opname", ..inputs..)
    /// This will match the same as Op, but will also accept additional (unspecified) inputs.
    /// The number of inputs may be zero: OpVarIn("opname") matches any op "opname".
    ///
    template <typename... Ts>
    API_EXPORT static typename std::enable_if<have_matchast_uptr<Ts...>::value, hnnx::MatchAst_uptr>::type
    OpVarIn(char const *opname, Ts &&...ts)
    {
        std::array<hnnx::MatchAstSubnode, sizeof...(Ts)> subnodes = {std::move(std::forward<Ts>(ts))...};
        return std::make_unique<hnnx::MatchAstNode>(opname, pkg_flag.c_str(), hnnx::MatchAstNode::is_OpVarIn,
                                                    sizeof...(Ts), subnodes.data());
    }

    template <typename... Ts>
    API_EXPORT static typename std::enable_if<are_strings<Ts...>::value, hnnx::MatchAst_uptr>::type
    OpVarIn(char const *opname, Ts... ts)
    {
        std::array<hnnx::MatchAstSubnode, sizeof...(Ts)> subnodes = {(ts)...};
        return std::make_unique<hnnx::MatchAstNode>(opname, pkg_flag.c_str(), hnnx::MatchAstNode::is_OpVarIn,
                                                    sizeof...(Ts), subnodes.data());
    }
    /// \ingroup OptMatch
    /// @brief give an Op in a pattern a tag: LET("tag", Op("opname", ...inputs... ))
    /// Second parameter must be Op or OpVarIn, and must not contain the same tag.
    /// The same tag can be used elsewhere in the pattern, but only in one LET.
    ///
    API_EXPORT static hnnx::MatchAst_uptr LET(hnnx::operand_tag_parm_t optag, hnnx::MatchAst_uptr &&subnode)
    {
        implement_LET(optag, subnode);
        return std::move(subnode);
    }
    /// @brief internal implementation for LET.
    /// just adds 'optag' to the node, and complains if it already has a tag.
    /// Maybe it also checks that the name doesn't appear inside.
    //
    API_EXPORT static void implement_LET(hnnx::operand_tag_parm_t optag, hnnx::MatchAst_uptr &subnode);

  public:
    template <typename T> API_EXPORT static hnnx::MatchAst_uptr matcher();
    API_EXPORT static hnnx::MatchOp_uptr build_matcher(hnnx::MatchAst_uptr &matchast);
    API_EXPORT_IMPORT static std::string pkg_flag;
};

POP_VISIBILITY()

#endif //* !PREPARE_DISABLED */
#endif /* MATCH_OP_H_ */
