//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef ALLOCATOR_H
#define ALLOCATOR_H 1

#include <cstddef>
#include <algorithm>
#include <memory>
#include "dtype_enum.h"
#include "weak_linkage.h"
#include "macros_attribute.h"
#include "forward_classes.h"
#include "hexagon_nn_types.h"

enum class MemoryClass {
    Plain,
    TCM,
    UnCached, // for spill/fill DDR
    XXX_LAST_MEMORY_TYPE,
    Default = Plain
};

PUSH_VISIBILITY(default)

extern bool TrackedAllocError;

class Graph;
class HexagonNNEnv;
namespace fa {
struct PoolDesc;
struct BigBuff;
struct RuntimeAllocator;
} // namespace fa
namespace hnnx {

class Serializer;
class Deserializer;

// some options flags (powers of 2) for calls to Tensor::allocate
enum AllocOptions {
    AllocOpts_packed = 0x1 // allocation will be packed
};

/*
 * Maybe FIXME: It seems like FancyAllocator has just about all the same interfaces as Allocator,
 * is all this pimpl stuff needed, or could we just inherit Allocator and have a unique_ptr<Allocator>
 * in our graph?
 */

class Allocator {
  public:
    // MIN_ALIGN, MAX_ALIGN:
    //  - both must be powers of 2
    //  -  8 <= MIN_ALIGN <= MAX_ALIGN
    // All allocations will be aligned to at least MIN_ALIGN, both start and end of each region.
    // This includes sub-allocations in memory pools.
    // Alignment requests > MAX_ALIGN may be treated as MAX_ALIGN if allocated in DDR.
    //
    static constexpr unsigned MIN_ALIGN = 256;
    static constexpr unsigned MAX_ALIGN = 256;

    // The alignment used by TCM allocation; >= MIN_ALIGN
    static constexpr unsigned TCM_ALLOC_ALIGN = 2048;

    static void *vacant() { return (void *)2; } // special value for 'vacant' slot.
    enum Mode { AllocVirtual, AllocPhysical, AllocTemp, AllocTempEnd, AllocComplete, LastMode = AllocComplete };

    // AllocTemp/AllocTempEnd are used in Virtual mode, to set a 'Temp Physical' mode
    // where allocation is done to physical memory, but into memory blocks which
    // are discarded when we return via AllocTempEnd (So, AllocTempEnd is not possible as an actual
    // current mode).
    // This is intended to support nesting (multiple levels of AllocTemp; each
    // AllocTempEnd discards all allocs since the matching AllocTemp; but
    // currently nesting is not supported, so AllocTemp must be followed by AllocTempEnd,
    // which actually takes you back to AllocVirtual
    // AllocComplete allows no further allocations. A deserialized allocator
    // is in this state.

    API_EXPORT Allocator(Mode mode_in, Graph &graph_in) : graph(graph_in), mode(mode_in){};
    API_EXPORT virtual ~Allocator() = 0;

    Graph &graph;

    // Either allocates enough, or dips into a buffer (and changes the buffer pointer and size parameter accordingly).
    // al is an alignment parameter; it must be a power of 2 or the code below won't work.
    API_EXPORT void *tracked_aligned_alloc(size_t al, size_t bytes, fa::BigBuff *const bb = nullptr);
    API_EXPORT void tracked_free(void *aligned_ptr) noexcept;

    API_EXPORT virtual void allocate_n(void **arrp, size_t n, size_t block_size, size_t alignment, MemoryClass memclass,
                                       unsigned options, DType dtype);

    // options for allocate_persistent_blocks.
    // if 'allnew' is *not* present, it is assumed that all of the pointers
    //   are either null, or point to existing persistent blocks. The 'null' ones
    //   are replaced with new allocations, and the ref counts are increased in both cases.
    // with 'allnew': pointers are assumed to contain garbage. Equivalent to zeroing the
    //   pointer table first.
    //
    // zoneB: with this, ref counts are update in 'B' zone instead of A
    //
    // incref: ovverides 'allnew'; all of the existing pointers are required to be valid persistent
    //    blocks; the ref counts are increased by 1
    // decref: overrides 'incref and allnew'; all of the pointers are required to be valid persistent
    //    blocks; the ref counts are reduced by 1. If total refs are zero, block is freed.
    //    the pointer table is not updated.
    //
    // infinite: newly alloc'd blocks get refcount set to a huge number, instead of 1.
    // Currently this is used when deserializing, since we can't free things immediately when in Crate.
    //
    enum persistent_options {
        allnew = 1u, // assume existing pointers are garbage, allocate them all.
        zoneB = 2u, // reference count in zone B instead of A.
        incref = 4u, // enforce that all existing are persistnent; incref them.
        decref = 8u,
        infinite = 16u, // refcounts on new blocks, set to a huge # instead of 1.
    };

    // allocate n 'persistent' blocks of the given size/alignment, and update the table.
    API_EXPORT virtual void allocate_persistent_blocks(void **table, size_t nblocks, size_t block_size,
                                                       size_t alignment, unsigned options);

    API_EXPORT inline void *allocate(const void *oldval, size_t block_size, size_t alignment, MemoryClass memclass,
                                     unsigned options, DType dtype)
    {
        PUSH_WARNING()
        DISABLE_WARNING("-Wcast-qual", MSVC_NO_EQUIV)
        void *tmp = const_cast<void *>(oldval);
        POP_WARNING()
        allocate_n(&tmp, 1, block_size, alignment, memclass, options, dtype);
        return tmp;
    }

    API_EXPORT Mode get_mode() const { return mode; }
    API_EXPORT virtual void set_mode(Mode new_mode);

    API_EXPORT virtual void set_tcm_pool(void *base, size_t size);

    API_EXPORT virtual void set_largest_memory_alloc_size(size_t size);

    /*
	 * Serialize all the internal data for the allocator.
	 * Memory regions / pools, etc.
	 */
    API_EXPORT virtual void serialize(Serializer &) const;
    /*
     * Deserialize the allocator, restore internal data from buffer.
     */
    API_EXPORT virtual void deserialize(HexagonNNEnv &env, Deserializer &dctx,
                                        hexagon_nn_wide_address_const_t params_weights = 0U,
                                        const size_t params_weights_length = 0,
                                        hexagon_nn_wide_iovec_t const &weights = NULL_IOVEC);

    API_EXPORT virtual int find_replaceable_mempool(unsigned const replaceable_pool_seq,
                                                    fa::PoolDesc &found_pool) const;

    // LCOV_EXCL_START [SAFTYSWCCB-1542]
    API_EXPORT static inline constexpr size_t fixup_alignment(size_t align)
    {
        static_assert(MIN_ALIGN >= 8 && (MIN_ALIGN & (MIN_ALIGN - 1)) == 0, "bad MIN_ALIGN");
        static_assert(MAX_ALIGN >= MIN_ALIGN && (MAX_ALIGN & (MAX_ALIGN - 1)) == 0, "bad MAX_ALIGN");
        if (MIN_ALIGN < MAX_ALIGN) {
            return std::max<size_t>(MIN_ALIGN, std::min<size_t>(MAX_ALIGN, align));
        } else {
            return MIN_ALIGN;
        }
    }
    // LCOV_EXCL_STOP

    API_EXPORT static inline constexpr size_t round_up_align(size_t n, size_t align)
    {
        return (n + (align - 1)) & ~(align - 1);
    }
    template <typename T> API_EXPORT static inline T *round_up_align(T *p, size_t align)
    {
        return (T *)round_up_align((size_t)p, align);
    }

  protected:
    Mode mode = AllocVirtual;
};

//
// this is s 'shim' class to help in making dummy allocators. It defines overrides
// for all of the pure-virtual methods, so you don't need to
//
class FakeAllocator : public Allocator {
  public:
    API_EXPORT FakeAllocator(Allocator::Mode mode_in, Graph &graph_in) : Allocator(mode_in, graph_in){};
    API_EXPORT virtual ~FakeAllocator();
};

// this is an accessor which is used by the Dma 'Fill' operation
// to get a source pointer for reading const, based on (pool_id, offset).
// It also holds the base pointer for ddr spill area.
// Maybe other things could be added later.

class MemPoolRunTimeAccessor {
    hexagon_nn_wide_address_t spill_area;
    fa::PoolDesc const *pool_table; // pool_table[0] is for poolid=1
    unsigned max_pool_id;

  public:
    API_EXPORT MemPoolRunTimeAccessor(hexagon_nn_wide_address_const_t spill_area_in, fa::PoolDesc const *const pt,
                                      unsigned const pt_size)
        : spill_area(spill_area_in), pool_table(pt), max_pool_id(pt_size)
    {
    }
    API_EXPORT MemPoolRunTimeAccessor() : spill_area(0), pool_table(nullptr), max_pool_id(0) {}
    API_EXPORT MemPoolRunTimeAccessor(MemPoolRunTimeAccessor const &) = default;
    API_EXPORT MemPoolRunTimeAccessor &operator=(MemPoolRunTimeAccessor const &) = default;

    // pool ids are >= 1, <= num_pools
    API_EXPORT constexpr unsigned num_pools() const { return max_pool_id; } //LCOV_EXCL_LINE [SAFTYSWCCB-1542]
    // map pool_id to base address of the data, for persistent pool; also get 'is_weights' flag.
    // implementation in runtime_alloc.h
    std::pair<hexagon_nn_wide_address_t, bool> get_persistent_pool_base_iswts(unsigned pool_id) const;
    API_EXPORT hexagon_nn_wide_address_t get_spill_area() const { return spill_area; }

    // used to construct the ConstExtentDescriptor during prep
    // implementation in fa_alloc.h
    API_EXPORT fa::PoolDesc const *get_descriptor(unsigned pool_id) const;

    // get the id of first DDR mempool
    API_EXPORT unsigned get_first_ddr_pool_id() const;
};

} // namespace hnnx

POP_VISIBILITY()

#endif
