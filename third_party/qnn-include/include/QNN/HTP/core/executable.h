//==============================================================================
//
// Copyright (c) 2018-2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================
#ifndef EXECUTABLE_H
#define EXECUTABLE_H 1

#include "graph_status.h"
#include <tuple>
#include <cstdlib>
#include <stdint.h>

class Graph;

#include "weak_linkage.h"
#include "macros_attribute.h"
PUSH_VISIBILITY(default)

namespace hnnx {

// Passed to any Op function which specfies 'op_slice_spec' as a value parameter.
// This is called by its typedef 'hnnx::op_slice_spec' everywhere; it has a short name hnnx::OsS since it's
// mangled into all the Op execute method names.
// The parameter must be the last one (unless there is a Graph const &, in which case it's before that).
struct OsS {
  protected:
    // on hexagon:
    //   must be possible to pass this in a 32 bit register; and 'default constructor' must
    //   be equivalent to a 32-bit value of '1'.
    unsigned m_nslices : 16;
    unsigned m_slice_idx : 16;

  public:
    OsS(OsS const &) = default;
    OsS &operator=(OsS const &) = default;
    constexpr OsS() : m_nslices(1), m_slice_idx(0) {}
    constexpr OsS(unsigned const n, unsigned const i) : m_nslices(n), m_slice_idx(i) {}

    constexpr unsigned num_slices() const { return m_nslices; }
    constexpr unsigned slice_idx() const { return m_slice_idx; }

    // If you want to pass an op_slice_spec into an asm routine as 32-bits, use this;
    // it provides an integer with 'num_slices' in lower 16 bits and 'slice_idx' in upper 16,
    // and will do so even if we change the format of op_slice_spec (e.g. to add some extra bits)
    // so you won't need to change your asm.
    unsigned as_uint32() const
    {
        union {
            OsS ss;
            unsigned as_u;
        } uu = {*this};
        return uu.as_u;
    }

    void from_uint32(unsigned x)
    {
        union {
            unsigned u;
            OsS ss;
        } uu = {x};
        *this = uu.ss;
    }
};
using op_slice_spec = OsS;

#define EXECUTE_METHOD_PARMS Graph *gr [[maybe_unused]], hnnx::op_slice_spec

typedef volatile uint32_t *counter_t;
typedef volatile uint32_t *counter_nc_t;

/*
	 * We want to have an abstraction for things that can execute()
	 * so that we can treat them more abstractly than all the things in Op
	 *
	 * This is that interface.
	 *
	 * Note that an important optimization is to be able to obtain the address of the execute call.
	 *
	 * So....
	 * THE "execute()" VIRTUAL FUNCTION MUST BE THE 0th THING IN THE VTABLE
	 * THE "execute()" VIRTUAL FUNCTION MUST NOT CHANGE SIGNATURES
	 *
	 * This allows us to look into the structures to find out more concrete addresses.
	 */
// Note: do not change the class design in any way that requires up/down pointer casts
// (between Excecutable its subclasses) to change the pointer value.
class API_EXPORT Executable {
  public:
    static constexpr unsigned MAX_OP_SLICES = 4;

    using FuncType = GraphStatus (*)(const void *, EXECUTE_METHOD_PARMS);
    using ItemType = std::pair<FuncType, const void *>;
    struct alignas(16) ExecType { // alignment keeps it all in same cache line on hexagon.
        FuncType funcp;
        const void *datap;
        counter_t gate_cp;
        counter_t done_cp;
        ExecType(FuncType const f, const void *const d, counter_t const gc, counter_t const dc)
            : funcp(f), datap(d), gate_cp(gc), done_cp(dc)
        {
        }
        ExecType &operator=(ExecType const &rhs) = default;
        ExecType() : funcp{}, datap{}, gate_cp{}, done_cp{} {}
    };
    virtual GraphStatus execute(EXECUTE_METHOD_PARMS) const noexcept = 0; // Needs to be at vtable offset zero!!!
    virtual ItemType compile(Graph &graph_in) const; // Turn this Executable into a function pointer and data pointer.
    virtual bool
    check_constraint_for_recompile(Graph &graph_in) const; // allows for additional check for running during recompile
    virtual ~Executable() = default;
    static const size_t *vtable(Executable const *); // helper function: get vtable
    static size_t execute_address(Executable const *); // helper function: get address of execute() function

    static GraphStatus no_op_function(const void *, EXECUTE_METHOD_PARMS); // just returns Success.
    static ItemType null_item() { return {no_op_function, nullptr}; }
};

// to execute an Executable::ItemType...

inline GraphStatus execute_item(Graph *graph_in, Executable::ExecType const &itemt)
{
    return (*itemt.funcp)(itemt.datap, graph_in, op_slice_spec{});
}

}; // namespace hnnx

POP_VISIBILITY()

#endif
