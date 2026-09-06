//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef DESER_CONCURRENT_H
#define DESER_CONCURRENT_H 1

#include <cstddef>
#include <cstdint>
#include <cassert>
#include <cstring>
#include <memory>
#include <vector>
#include <tuple>

#include "deser_concurrent_defs.h"

// this is intended to be included only in "deserialize.h"

struct PreloadInfo;

namespace hnnx {
struct runlist_seg_descriptor;
class Crate;
class Deserz;
class fixup_supplemental_recs;
class InitTimeSchedule;

// describes a 'span' of the deserialized data
struct deser_segment_span {
    void *base;
    void *limit;
};

// This describes a partially-decoded segment; includes fixups.
// This should stay small so we can place it inside Deserz, and std::move it
// out (to keep the fixup list) when done with the segment.
struct runlist_fixup_state {
    unsigned segno = 0;
    size_t *crate_begin = nullptr; // where the data starts in the crate
    runlist_seg_descriptor *seg_desc = nullptr; // Corresponding 'runlist_seg_descriptor' for reference.
    // The next three are copied from the runlist_auxdata_seg_desc
    uint32_t base_tensor_index = 0; // first tensor index defined this segment
    uint32_t base_blocktable_index = 0; // first blocktable index defined in this segment
    uint32_t base_sharedobj_index = 0; // first 'shared_object' index defined in this segment
    // fixup data
    size_t *fixup_list_head = nullptr; // head of the 'fixup list', or null if none.
    fixup_supplemental_recs *fixup_supplemental; // supplemental fixup list

    runlist_fixup_state() = default;
    ~runlist_fixup_state() = default;
    runlist_fixup_state(runlist_fixup_state const &) = default;
    // *Some* implementations of c++lib require this to have operator= (non-move)
    // in order for std::vector containing it to be constructed via resize.
    runlist_fixup_state &operator=(runlist_fixup_state const &) = default;
    // the move-ctor and move-assign must leave the source with no fixup list,
    // and segno = 0.
    runlist_fixup_state(runlist_fixup_state &&from) { do_move_from(std::move(from)); }
    runlist_fixup_state &operator=(runlist_fixup_state &&from)
    {
        do_move_from(std::move(from));
        return *this;
    }

  private:
    // this is used in move-constructor and move-assign; it will always leave 'from'
    // with certain 'null' values to trap cases where we're using the wrong instance.
    void do_move_from(runlist_fixup_state &&from)
    {
        segno = from.segno;
        crate_begin = from.crate_begin;
        seg_desc = from.seg_desc;
        base_tensor_index = from.base_tensor_index;
        base_blocktable_index = from.base_blocktable_index;
        base_sharedobj_index = from.base_sharedobj_index;
        fixup_list_head = from.fixup_list_head;
        fixup_supplemental = from.fixup_supplemental;
        from.segno = 0;
        from.seg_desc = nullptr;
        from.fixup_list_head = nullptr;
    }
};
//
// This contains 'supplemental' fixup records for a segment;  there is one instance in each runlist_seg_descriptor,
// and a pointer to in the runlist_fixup_state. When the 'runlist_fixup_state' is moved in or out of the Deserz,
// the pointer to this remains.
// To avoid the overhead of vec_push_back, this // has a static array into which values are recorded;
// when this is full (or near full), all the records within are appended to the vector in a single operation.
// At the end of the operation, any remaining records are appended to the vector, but only if the vector
// is not empty (we can read the records out of the fixed array, if they all fit).
//
// The append() is not safe unless 'ensure_room_for' is checked first; you can e.g. do ensure_room_for(3)
// ahead of doing up to 3 append
// It is best to use a constant as parameter to ensure_room_for, i.e. ahead of code which may append
// *up to* 4 values, use ensure_room_for(4); this simplifies the inline expansion of 'ensure_room_for',
// and makes very little difference to performance compared to using the exact value.
//
class fixup_supplemental_recs {
    static constexpr unsigned ARR_SIZE = 64;
    unsigned num_in_arr = 0;
    uint32_t fixed_arr[ARR_SIZE];
    std::vector<uint32_t> var_arr;
    unsigned n_vec = 0; // = var_arr.size()

  public:
    void clear();
    unsigned constexpr size() const { return num_in_arr + n_vec; }
    void reserve(unsigned const n) { var_arr.reserve(n); }
    inline void ensure_room_for(unsigned const n)
    {
        assert(n <= ARR_SIZE);
        if (num_in_arr > ARR_SIZE - n) flush_to_vec();
    }
    // append allowed only when preceded by 'ensure_room_for'
    inline void append(uint32_t const val)
    {
        assert(num_in_arr < ARR_SIZE);
        fixed_arr[num_in_arr++] = val;
    }
    // use instead of 'ensure_room_for(1); push_back(n)'
    inline void push_back(uint32_t const val)
    {
        if (num_in_arr > ARR_SIZE - 1) flush_to_vec();
        fixed_arr[num_in_arr++] = val;
    }
    // After all push_back() done, do a 'finish'
    // and then get_limits() can be used to traverse the data.
    void finish(); // flushes, but only if the vec is not empty.
    std::pair<uint32_t const *, uint32_t const *> get_limits() const;

  protected:
    void flush_to_vec();
};

// An array of these (size N+1) is used to hold the
// information used in deserializing each each segment.
// The [N+1] is partially used; some operations may use
// e.g. arr[i+1].auxinfo.some_field to find out where something
// ends for the current segment, using the start of the next segment;
// so N-1 entry needs a next.

struct runlist_seg_descriptor {
    runlist_auxdata_seg_desc auxinfo; // the data from the 'aux_data' record for this segment
    runlist_fixup_state segfixup; // the deserialization state (moved in and out of Deserz as needed)
    fixup_supplemental_recs fixup_supp; // fixup supplemental recs.
    deser_segment_span span_to_deser = {};
    // These are used to configure the last preload in each segment, which preloads a region
    // which is either partially, or entirely, in the next segment. So, the first two entries
    // below are actually set at the end of deserialization of the previous segment; the end_preload
    // is set by the current segment deserialize.
    // The information stored in [N] is for configuring
    // the last preload in the last segment, with end_preload set to 'end of crate'; in this case
    // start_preload could be <= the end of the crate, and then we don't configure it.
    // likewise the information in [0] is only 'end_preload', which can be used to configure
    // 'Graph::m_initial_preload' (it should go from start-of-crate to seg[0].end_preload).
    // In some cases (hopefully, only in testing) we may have segments with no preloads in them,
    // in which case null pointers will appear in some of these; the ChunkPreload ops need to
    // configured by getting info from adjacent segments.
    PreloadInfo *prev_seg_final_preload{}; // points to the prev segments' final PreloadInfo
    char *start_preload{}; // the preload start address for prev seg's final preload
    char *end_preload{}; // end address  for prev seg's final preload
};

// One instance of this is in Deserializer, called segments.
// It is created 'empty', and populated when we encounter the valid
// Aux Data record.
//
class DeserSegDescs {
    unsigned n_segs = 0;
    // points to an array of n_seg + 1, if n_segs > 0
    std::unique_ptr<runlist_seg_descriptor[]> seg_arr;

  public:
    DeserSegDescs() = default;
    ~DeserSegDescs() = default;
    DeserSegDescs(DeserSegDescs const &) = delete;
    DeserSegDescs(DeserSegDescs &&) = default;
    DeserSegDescs &operator=(DeserSegDescs const &) = delete;
    DeserSegDescs &operator=(DeserSegDescs &&) = default;

    // these two are used to create the array
    void set_size(unsigned const n); // used to create sized, empty array
    runlist_seg_descriptor *data() { return seg_arr.get(); }

    constexpr unsigned num_segs() const { return n_segs; }
    constexpr bool is_active() const { return n_segs != 0; }
    // note: 'i' may be 0 .. num_segs(); only can use when 'is_active'.
    runlist_seg_descriptor &operator[](unsigned const i) { return seg_arr[i]; }
    runlist_seg_descriptor const &operator[](unsigned const i) const { return seg_arr[i]; }

    // We can add other data in here, to manage the concurrent deserialization.
    unsigned n_threads = 0; // set when allocating the 'Deserz' array
    std::vector<Deserz> deserz_arr; // sized as 'n_threads'.

    // start-of-crate, rounded to a multiple of 32; Calculated before any multi-thread
    // operations. Use to configure Graph::m_initial_preload.
    void *crate_preload_start_boundary;
    // end-of-crate, rounded up to multiple of 32. Calculated before any multi-thread
    // operations. No 'ChunkPreloadOp' will exceed this.
    void *crate_preload_final_boundary;

    InitTimeSchedule *initSchedule;
};

// A 'DCrate' is a proxy object stored within Deserz.
// It has some of the same methods as Crate; but if nextp is not null,
// it will allocated into the space at 'nextp', limited by 'limitp'
// Otherwise it will use the Crate.
// Most methods are defined as inlines in dcrate_inlines,h
//
class DCrate {
    // these are either both null, or both non-null and 4-aligned.
    void *nextp = nullptr;
    void *limitp = nullptr;
    Crate *cratep = nullptr;

  public:
    DCrate() {}
    ~DCrate() {}
    DCrate(DCrate const &) = default;
    DCrate(DCrate &&) = default;
    DCrate &operator=(DCrate const &) = default;
    DCrate &operator=(DCrate &&) = default;
    explicit DCrate(Crate &c) : cratep(&c) {}
    void set_crate(Crate &c) { cratep = &c; }
    Crate *crate() { return cratep; }
    bool is_active() const { return nextp != nullptr; }

    constexpr size_t bytes_remaining() const { return (char *)limitp - (char *)nextp; }
    char *next_loc() { return (char *)nextp; }
    std::pair<char *, char *> range_remain() { return {(char *)nextp, (char *)limitp}; }

    void set_memory_range(void *base, unsigned len)
    {
        nextp = base;
        limitp = (void *)((char *)base + len);
    }
    void remove_memory_range()
    {
        nextp = nullptr;
        limitp = nullptr;
    }

    // Methods of Crate we want to support (See crate.h for more more detail).
    // Note that the constructors invoked in 'emplace' and 'emplace_explicit'
    // can and will recursively call 'emplace' to construct their sub-objects.
    template <typename T, typename... Args> T *emplace(Args &&...args);
    // variant of 'emplace' which can use the 'emplace_explicit' call to avoid
    // instantiating the constructor twice
    template <typename T> T *emplace0(Deserz &dctx);
    // (this is defined with 'template' args, only so it can be declared here without
    // forward refs. All are pass-by-value. Only one specialization will be defined).
    template <typename FI, typename FD, typename SA> void *emplace_explicit(Deserz &dctx, FI, FD, SA);
    // array allocation, used to make all arrays in crate during deserialize.
    template <typename T, bool DTOR_OK = false> T *alloc_array(size_t n);

  private:
    // reserve the specified data in the range, and return pointer to start; or
    // return null if not possible.
    void *do_alloc(size_t align, size_t amount);
};

// defines the encoding in the upper 3 bits of the last word of a 'multi-word' supplemental record
// all must be 4..7, since a 0 in the msb indicates a 'short' record.

constexpr unsigned SUPPFIXUP_CAT_tensor = 4;
constexpr unsigned SUPPFIXUP_CAT_sharedobj = 5;
constexpr unsigned SUPPFIXUP_CAT_blocktable = 6; // with indices packed in one word
constexpr unsigned SUPPFIXUP_CAT_blocktable_full = 7; // .. in two words
constexpr unsigned SUPPFIXUP_CAT_SHIFT = 29u;

bool fixup_encode_for_blocktable(runlist_fixup_state &seginfo, uint32_t idx, uint32_t table_offs, void **ptrloc);

// high-level operations in the 'deserialize by segments' code.

GraphStatus do_multiseg_deser(Deserializer &dctx, size_t ref_deser_pos);
GraphStatus segmentjob_deserialize_ops(Deserializer &dctx, unsigned segno, unsigned threadno);
GraphStatus segmentjob_process_fixups(Deserializer &dctx, unsigned segno, unsigned threadno);
GraphStatus segmentjob_compile_ops(Deserializer &dctx, unsigned segno, unsigned threadno);
void resolve_chunk_preload_after_multiseg_deser(Deserializer &dctx);

} // namespace hnnx

#endif // DESER_CONCURRENT_H
