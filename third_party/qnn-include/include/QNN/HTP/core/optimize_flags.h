//==============================================================================
//
// Copyright (c) 2020 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OPTIMIZE_FLAGS_H_
#define OPTIMIZE_FLAGS_H_

#include <stdint.h>
#include "weak_linkage.h"

#ifndef PREPARE_DISABLED

PUSH_VISIBILITY(default)

namespace hnnx {

struct OptimFlags {
    typedef uint32_t flags_t; // can change to uint64 if needed

    template <int N> struct flagbit {
        static constexpr flags_t val = flags_t(1) << N;
    };

    // :::>OPTIMFLAG_SYMBOLS{(\w+)\s*=\s*flagbit<(\d+)>::val}
    enum f : flags_t {
        any_rule = flagbit<0>::val, // all rules have this bit forced on.
        cse_after_always = flagbit<1>::val, // always do CSE after a pass containing this rule
        cse_after_if = flagbit<2>::val, // always do CSE after the pass, if the rule succeeds.
        cse_set_triggerA = flagbit<3>::val, // trigger CSE when this rule succeeds.
        cse_before_if_triggerA = flagbit<4>::val, // if triggered, CSE before pass containing this rule
        cse_set_triggerB = flagbit<5>::val, // trigger CSE when this rule succeeds.
        cse_before_if_triggerB = flagbit<6>::val, // if triggered, CSE before pass containing this rule
        fold_relu_flag = flagbit<
                7>::val, // if overall flag for folding relu is enabled all rules containing this flag will be triggered
        cp_after_always = flagbit<8>::val, // always do CP after a pass containing this rule
        hmx_short_conv_flag = flagbit<9>::
                val, // if overall flag for using hmx to do short conv is enabled all rules containing this flag will be triggered
        relaxed_precision_flag = flagbit<10>::
                val, // if overall flag for relaxed precision is enabled all rules containing this flag will be triggered

        // An AUTOSPLIT rule that is not part of central tiling and is executed
        // when central tiling is enabled or not.
        explicit_autosplit_flag = flagbit<11>::val,
        // A rule with an AUTOSPLIT that should be ignored when central tiling
        // is enabled.
        central_autosplit_flag = flagbit<12>::val,
        // A rule which is only used by the central tiler and is never executed
        // when central tiling is disabled.
        central_only_autosplit_flag = flagbit<13>::val,
        // A rule which is ignored completely when central tiling is enabled
        central_ignore_autosplit_flag = flagbit<14>::val,

        // This is used to disable rules which are centralized into a common TCM migration pass
        tcm_migration_old_flag = flagbit<15>::val,
        // This is used to specify rules only used when centralized TCM is in use.
        tcm_migration_new_flag = flagbit<16>::val,
        cp_after_if = flagbit<17>::val, // trigger CP when the rule succeeds
        prepare_aux_graph = flagbit<18>::val, // is this an aux graph prepare?
        autothread_flag = flagbit<19>::val, // Always set the autothread flag
        trace_rule = flagbit<20>::val, // extra diagnosic tracing on a rule.
    };

    /*
The trace_rule flag is used to enable extra debugging
for a rule to debug predicates. It should only be used
in development and not commited to the mainline branch.

It requires WITH_OPT_DEBUG to be defined.
Adding trace_rule to a rule such as

DEF_OPTIM(SPATIAL_RESHAPE+100, trace_rule,
	Op("ConvLayer","Activations","Weights","Stride","Bias","Scale","ConvCtrl","OutCtrl"),
	AND(
		OR( EQ(ZERO_OFFSET_OF("Weights"), 128), OPTION_BOOL("hmx_short_conv_flag")),
		OR(IS_QUINT8("Activations"), IS_QUINT16("Activations")),
		AND(
			NOT( AND( EQ(DIM_HEIGHT("Stride"),1), EQ(DIM_WIDTH("Stride"),1)) ),
			NOT( AND( EQ(DIM_HEIGHT("Stride"),2), EQ(DIM_WIDTH("Stride"),2)) ),
			NOT( AND( EQ(DIM_HEIGHT("Stride"),4), EQ(DIM_WIDTH("Stride"),4), EQ( DIM_FILTHEIGHT("Weights"), 3), EQ( DIM_FILTWIDTH("Weights"), 3)))
		)
	),

might generate this output:

optimize.cc:101:conv.cc:1875 attempt 0x140d0000001d q::ConvLayer
optimize.cc:115:0x140d0000001d predicate depth 2 or clause 1 result=1
optimize.cc:115:0x140d0000001d predicate depth 2 or clause 0 result=1
optimize.cc:115:0x140d0000001d predicate depth 3 and clause 1 result=1
optimize.cc:115:0x140d0000001d predicate depth 2 and clause 0 result=0
optimize.cc:115:0x140d0000001d predicate depth 1 and clause 2 result=0

here the "depth" field indicate the position in the expression tree of 
and AND or OR operator with the outermost such operation being a 1. 
The clause term identifies the operator of that operator numbered left-to-right
starting with 0. Result is the result of evaluating that cluase.

Note that we stop evaluating an "AND" or "OR" once we determine its result.
Thus, for an "AND" we generally will list only one clause and all preceding
clauses are known to be "true". Similarly we list only one clause for an "OR"
and preceding clauses are known to be "false"

Happy hunting

*/

    // :::<OPTIMFLAG_SYMBOLS

    // To clarify cse_set_triggerA etc:
    // If rule 'X' has cse_set_triggerA, and rule 'Y' in a *later* pass has cse_before_if_triggerA,
    // then if X gets applied, there will always be at least one subsequent CSE
    // operation before the pass containing rule Y starts. Likewise for B.

    // this is the union of the flags which need to be collected across all rules
    // when building the optimization table; the result is stored in GraphOptPass.flags

    static constexpr flags_t combine_over_pass =
            cse_after_always | cse_before_if_triggerA | cse_before_if_triggerA | cp_after_always;

    // Engine to make decision to do CSE after a pass.
    // (and others can be added as needed)
    // Lifetime is the full optimization process.
    // After each pass (except the last), update() method is called,
    // and then need_cse()  returns a bool indicating if CSE should be done.
    // Note, this is not called after the final pass since we do CSE anyway.

    // In the 'm_trigger' word, cse_set_triggerA and cse_set_triggerB bits are
    // set when these triggers have happened in a previous pass;
    // they are cleared whenever we decided to do CSE.
    //
    // The 'any_rule' bit in curr_trigger is a special case (all rules have this bit in their flags):
    // we clear it whenever we decide to do CSE, and we set it in any other case when any rule has been executed.
    // So, if it's clear when we are called, and the success_flags don't have it, nothing has changed since the previous CSE,
    // and we can ignore all the other CSE conditions.

    class OptFlagState {
        flags_t m_trigger; // triggers are held here.
        bool m_need_cse;
        bool m_need_cp;

      public:
        OptFlagState() : m_trigger(0), m_need_cse(false), m_need_cp(false) {}
        API_EXPORT inline void update(flags_t previous_pass_flags, // GraphOptPass.flags from the previous pass
                                      flags_t next_pass_flags, // GraphOptPass.flags from the next pass
                                      flags_t success_flags) // 'or' of all rules which succeeded in previous pass.
        {
            flags_t const trigs = m_trigger;
            flags_t next_trigs = trigs | (success_flags & (any_rule | cse_set_triggerA | cse_set_triggerB));
            bool const do_cse =
                    ((next_trigs & any_rule) != 0 &&
                     ((success_flags & cse_after_if) != 0 || (previous_pass_flags & cse_after_always) != 0 ||
                      ((next_trigs & cse_set_triggerA) != 0 && (next_pass_flags & cse_before_if_triggerA) != 0) ||
                      ((next_trigs & cse_set_triggerB) != 0 && (next_pass_flags & cse_before_if_triggerB) != 0)));
            bool const do_cp = ((next_trigs & any_rule) != 0 &&
                                ((success_flags & cp_after_if) != 0 || (previous_pass_flags & cp_after_always) != 0));
            if (do_cse) {
                // we are going to do cse; so reset all triggers and return true
                next_trigs = 0;
            }
            if (do_cp) {
                // we are going to do cse; so reset all triggers and return true
                next_trigs = 0;
            }
            // if not doing CSE, accumulate any triggers
            m_trigger = next_trigs;
            m_need_cse = do_cse;
            m_need_cp = do_cp;
        }

        API_EXPORT inline bool need_cse() const { return m_need_cse; }
        API_EXPORT inline bool need_cp() const { return m_need_cp; }
    };

    // this is a trick to allow flags to be used in rules without namespace prefix
    // i.e. if the flags are "flagname1 | flagname2"
    // the #define will expand it to
    //  inline constexpr OptimFlags::flag_eval<SOMETYPE>() {  return any_rule | (flagname1| flagname2); }
    // .. and then call that to get the value.
    //

    template <typename U> static constexpr flags_t flag_evaluate() noexcept
    {
        static_assert(false && sizeof(U), "must be specialized");
        return any_rule;
    }
};

} // namespace hnnx

POP_VISIBILITY()

#endif
#endif /* OPTIMIZE_FLAGS_H_ */
