//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

/*
 * crate.h
 *
 *  Created on: Aug 1, 2019
 *      Author: smithg
 */

#ifndef CRATE_H_
#define CRATE_H_
#include <cstddef>
#include <cstdint>
#include <utility>
#include <list>
#include <memory>
#include <vector>
#include <cstring>
#include <stdexcept>

#include "is_detected.h"
#include "forward_classes.h"
#include "macros_attribute.h"
#include "weak_linkage.h"
#include "size_align_code.h"

PUSH_VISIBILITY(default)

class Graph;
class Tensor;

/// @brief A 'Crate' allows construction of some number of different data types,
/// contiguously packed into a few large memory blocks.
///
/// Example:
///
///     Crate crt;
///     Thing  tp* = crt.emplace<Thing>( ... ctor parms for Thing ... )
///     AnotherThing tp2* = crt.emplace<AnotherThing>( ... ctor parms for AnotherThing ... )
///
/// When the crate is destroyed, all of the contained objects are destroyed in the reverse
/// order. You cannot 'remove' a single entry using
///
///  crt.erase has been deprecated
///
/// However, this is likely not going to free any memory; it will just call the dtor of the
/// object (and make sure it doesn't get called later, when the Crate is cleared or destroyed).
///
/// You can also emplace variable-sized arrays of trivially-destructable objects.
///
/// alloc_array does not initialize:
///
///     float * farr = crt.alloc_array<float>(n);
///
/// alloc_array_zero does zero-initializing:
///
///     int * farr = crt.alloc_array_zero<int>(n);
///
/// If an allocation needs space larger than CHUNKBYTES, it will get its own chunk.
///
// Each record containing an object has a non-null 'dtor' field; if the object is trivially destructible,
// this will be  (dtor_funcp)1, and the object is not on the linked-list.
//
// note:
//  A constructor may emplace additional records in the crate recursively. Likewise,
//  it's OK if the dtors call erase() on other objects. If this happens during a 'clear',
//  the erase calls are ignored since the other objects are going to get dtor'd anyhow (if they have not
//  been already).
// Important: if object A's constructor places B into the crate, then B will very likely get destroyed
//  first when the crate is cleared. Thus, A's destructor can't look at B (it can erase B, which is ignored
//  as described above).

//
// new 'raw' mode:
//  - when the crate is in 'raw' mode, no destructors are registered. inserting an object
//    increases 'alloc_count' in the chunk header, but does not increment 'nrec', nor any
//    does it increase Crate::m_records.
//  - raw mode is entered by enable_raw_mode(size_needed):
//      which does this in addition to enabling raw mode:
//         - if there is no current chunk, or if the current chunk doesn't have room for 'size_needed' bytes,
//           a new chunk is added which does.
//         - enable_raw_mode(size_needed) returns a chunk handle.
//
// Internally, raw_mode causes add_record_slot() to do the same thing, but it only moves alloc_count, it does
// not assign a slot index, and 'idx' is -1 in the returned struct.
// All callers of add_record_slot() *must* check for raw mode (can be done by checking idx < 0), and then avoid
// adding an dtor or doing '++m_records'.
//
// it's also possible to call .enable_raw_mode(), disable_raw_mode()
// but .enable_raw_mode() does nothing if there isn't at least one chunk allocated.
//

namespace hnnx {

//
// This is used to statically determine whether a type T has a clear(Graph&)
// method.  This is used as an additional destructor which takes a Graph
// reference.
//

template <typename T> using clear_t = decltype(std::declval<T &>().clear(std::declval<Graph &>()));

template <typename T> constexpr bool has_clear = is_detected_v<clear_t, T>;

class Deserz;
class DCrate;

class Crate {
    API_EXPORT static constexpr size_t CHUNKBYTES = (1 << 16);
    static_assert(CHUNKBYTES % 8 == 0 && CHUNKBYTES >= 128);
    typedef void (*dtor_funcp)(Graph *graph_in, void *);
    API_EXPORT static dtor_funcp DTOR_TRIVIAL() { return (dtor_funcp)1; }
    API_EXPORT static dtor_funcp DTOR_IN_PROCESS() { return (dtor_funcp)2; }

    //! A record in the index of a chunk
    struct index_rec {
        unsigned loc; ///< offset in bytes to the object
        dtor_funcp
                dtor; ///< pointer to dtor function (null if empty record; (DTOR_TRIVIAL if the object is trivial dtor)
    };
    //! A chunk record in the crate.
    ///
    /// Each chunk is created as an array of uint64_t, via make_unique<uint64_t[]>
    /// The memory in a chunk has a chunkhdr, which is followed by:
    ///
    ///    [Objects][Objects][Objects]--> free space   <--[Index records]
    ///
    /// 'alloc_count' is the next offset available to be allocated.
    /// index records are entered in reverse order from the end. So, the last nrec*sizeof(index_rec)
    /// bytes of the area, are the index.
    ///
    typedef std::unique_ptr<uint64_t[]> uptr_chunk_t;
    struct chunkhdr;
    API_EXPORT static chunkhdr *hdr_of(uptr_chunk_t &p) { return reinterpret_cast<chunkhdr *>(p.get()); }
    API_EXPORT static chunkhdr const *hdr_of(uptr_chunk_t const &p)
    {
        return reinterpret_cast<chunkhdr const *>(p.get());
    }
    /// The chunkhdr is the first portion of the chunk, and is immediately followed
    /// by data_len bytes, which is a multiple of 8.
    struct API_EXPORT alignas(8) chunkhdr {
        unsigned data_len; ///< length of the data area following header, bytes (>=CHUNKBYTES).
        unsigned nrec; ///< records in use (including deleted ones)
        unsigned alloc_count; ///< offset of first byte in 'free space'
        // init to a given length (header not included)
        void init(unsigned length)
        {
            data_len = length;
            nrec = 0;
            alloc_count = 0;
        }
        // reset (preserve data_len)
        void init()
        {
            nrec = 0;
            alloc_count = 0;
        }
        // pointer to 'offs ' within data area
        inline uint8_t *get_ptr(unsigned offs) { return (uint8_t *)(this + 1) + offs; }
        // pointer to end of  the allocation
        inline uint8_t *get_end_ptr() { return (uint8_t *)(this + 1) + data_len; }
        // amount of space remaining
        inline size_t space_avail() const { return data_len - alloc_count - nrec * sizeof(index_rec); }
        // get pointer to an index record.
        // record 0 is the last (oldest) one.
        index_rec *index_p(int idx) { return (index_rec *)get_end_ptr() - (idx + 1); }
        static uptr_chunk_t allocate(unsigned len);
    };
    std::vector<uptr_chunk_t> m_chunks; /// < chunks with data
    std::vector<uptr_chunk_t> m_free; /// < chunks without
    typedef std::vector<uptr_chunk_t>::iterator chunk_iter;

    bool m_rawmode = false;
    bool m_clearing = false; ///< set while clearing.
    size_t m_allrecords = 0; ///< includes removed and 'padding' records
    size_t m_records = 0; ///< only actual, non-erased records.

    //! Returned from add_record_slot (which is used to create a new record)
    struct recposn {
        chunkhdr *chunkp; ///< the chunk in which it was found
        void *objp; ///< pointer to the object
        int idx; ///< index within the chunk (= -1 if insert was done in raw mode)
    };
    API_EXPORT recposn add_record_slot(size_t bytes, size_t align);
    API_EXPORT void recover_ctor_throw(recposn const &) noexcept;
    API_EXPORT void install_dtor(recposn const &, dtor_funcp dtor_func);
    API_EXPORT void move_to_free(chunk_iter chunk_to_free);

  public:
    class ChunkHandle {
        friend class Crate;
        chunkhdr *chunkp;

      protected:
        ChunkHandle(chunkhdr *cp) : chunkp(cp){};

      public:
        ChunkHandle() : chunkp(nullptr) {} // null handle may only be assigned-to
        ChunkHandle(ChunkHandle const &) = default;
        ChunkHandle &operator=(ChunkHandle const &) = default;
        friend inline bool operator==(ChunkHandle const &a, ChunkHandle const &b) { return a.chunkp == b.chunkp; }
        std::pair<void *, size_t> get_memory_extent() const
        {
            size_t const len = chunkp->get_ptr(chunkp->alloc_count) - (uint8_t *)chunkp;
            return {chunkp, len};
        }
    };

    API_EXPORT Crate(); ///< Construct a new Crate
    Crate(Crate const &) = delete;
    Crate &operator=(Crate const &) = delete;

    // get the preload handle for the first chunk
    ChunkHandle first_chunk_handle() const
    {
        return ChunkHandle(m_chunks.empty() ? nullptr : hdr_of(const_cast<Crate &>(*this).m_chunks.front()));
    }
    // get the preload handle for the most recent chunk
    ChunkHandle last_chunk_handle() const
    {
        return ChunkHandle(m_chunks.empty() ? nullptr : hdr_of(const_cast<Crate &>(*this).m_chunks.back()));
    }
    // 'raw mode'
    ChunkHandle enable_raw_mode(unsigned bytes_needed);
    API_EXPORT void enable_raw_mode();
    void disable_raw_mode() { m_rawmode = false; }
    bool raw_mode() const { return m_rawmode; }

    // Note that the destructor doesn't do anything.  You have to call clear() manually.
    API_EXPORT ~Crate();
    //! The number of objects in the crate.
    size_t size() const { return m_records; }
    //! The number of chunks in use
    size_t chunk_count() const { return m_chunks.size(); }
    //! The amount of space left in the current chunk, approximately.
    /// DO NOT CALL unless chunk_count() > 0
    size_t current_chunk_space_remain() const { return hdr_of(this->m_chunks.back())->space_avail(); }
    //! Delete all objects. Does not necessarily free all storage to the
    /// system; but all retained storage is availabe for re-use in the crate.
    /// Note that this is no longer called by the destructor- it must be called explicitly.
    API_EXPORT void clear(Graph *graph_in);
    // Special entry for deserialzing in segments.
    // If it is possible to allocate, in current raw-mode chunk, everything from offset 'start'
    // up to but not including 'limit', this is done, and the base address of that region is returned.
    // otherwise does nothing and returns null.
    API_EXPORT void *allocate_bulk(size_t start, size_t limit);

    //! Construct an object of type T into the crate, using the
    /// parameters of any constructor of T. It is acceptable for the
    /// constructor to call the emplace method to add other objects to
    /// the crate.
    template <typename T, typename... Args> API_HIDDEN T *emplace(Args &&...args)
    {
        recposn const pos = add_record_slot(sizeof(T), alignof(T));
        // construct the object
        if constexpr (std::is_nothrow_constructible<T, Args...>::value) {
            new (pos.objp) T(std::forward<Args>(args)...);
        } else {
            try {
                new (pos.objp) T(std::forward<Args>(args)...);
            } catch (const std::exception &e) {
                recover_ctor_throw(pos);
                throw;
            }
        }
        if (pos.idx >= 0) {
            // register destructor
            if constexpr (!std::is_trivially_destructible<T>::value) {
                // Obtain a callable '~T()' function.
                // this typically generates a jump, or a small inline; lambda can
                // be cast to a function pointer since it has no state.
                auto dtor_func = [](Graph *graph_in, void *obj) {
                    if constexpr (has_clear<T>) {
                        static_cast<T *>(obj)->clear(graph_in);
                    }
                    static_cast<T *>(obj)->~T();
                };
                install_dtor(pos, (dtor_funcp)dtor_func);
            } else {
                ++m_records; // note, install_dtor does this too.
            }
        }
        return static_cast<T *>(pos.objp);
    }

    using deserialize_op_func = void *(*)(void *, Deserz &);
    using deserialize_dtor_func = void (*)(Graph *, void *);

    // Alternate interface to cut down on template instantations:
    // init_func is used to initialize the memory, and dtor_func
    // is is used to register the destructor.  It's up to the user
    // to provide the correct size and alignment.

    API_EXPORT void *emplace_explicit(Deserz &dctx, deserialize_op_func init_func, deserialize_dtor_func dtor_func,
                                      size_align_code_t size_al);

    //! Allocate 'n' of type T in the crate.
    /// Will initially be garbage; T must be trivially destructable (unless waived)
    template <typename T, bool DTOR_OK = false> T *alloc_array(size_t n)
    {
        static_assert(DTOR_OK || std::is_trivially_destructible<T>::value);
        if (n == 0) return nullptr;
        recposn const pos = add_record_slot(sizeof(T) * n, alignof(T));
        if (pos.idx >= 0) m_records++;
        return static_cast<T *>(pos.objp);
    }
    //! Allocate 'n' of type T in the crate.
    /// Will be zero-filled; T must be trivially destructable.
    template <typename T> T *alloc_array_zero(size_t n)
    {
        T *const res = alloc_array<T>(n);
        if (n != 0) ::memset(res, 0, sizeof(T) * n);
        return res;
    }
    //! Allocate 'n' of type T in the crate.
    /// Will be "value constructed"; in case of things like int and pointer,
    /// this means they will be zeroed.
    ///
    /// T must be trivially destructable.
    template <typename T> T *alloc_array_value(size_t n)
    {
        T *res = alloc_array<T>(n);
        if (n != 0) std::uninitialized_value_construct_n(res, n);
        return res;
    }
};

/*
 * EJP: This seems silly, but I don't know how to get visibility into Graph into a templated Tensor because of include hell.
 */

API_EXPORT Crate *graph_crate(Graph &graph_in);

//
// replacement for vector, for use in ops;

//
// limited options for constructor:
//   (1) copy, or move, from vector<T> - need Graph *;
//   (2) create with a given size, null-initialized; - need Graph *;
//   (3) create empty, and then fill in later
//       using init( Graph* , std::vector const &)
//       or init( Graph* , std::vector &&)
//       or init( Graph *, size )
//       or init( Graph *, T const *ptr, size );
//       or init_move( Graph *, T *ptr, size );

// With option 3, it assumed that the 'init' is done during the constructor of
// a host object - this is needed during deserialize, for instance.
// the 'len' is 32 bits so this type occupies 2 pointers, vs. 3 for std::vector.
//
// If 'T' has a destructor, the cratevec's destructor will invoke that on
// each element of the vector, in reverse order.
// when the 'move-from' mechanisms to init from 'std::vector && are used,
// the supplied vector will not be cleared; but its elements will all be
// 'moved-from'.

template <typename T> class cratevec {
    T *m_ptr;
    unsigned m_len;
    using vec_t = std::vector<T>;
    static constexpr bool need_dtor = !std::is_trivially_destructible<T>::value;

  public:
    using iterator = T *;
    using const_iterator = T const *;
    using value_type = T;
    using size_type = size_t;
    using difference_type = ptrdiff_t;
    using reference = T &;
    using const_reference = T const &;

    cratevec() : m_ptr(nullptr), m_len(0) {}
    cratevec(Graph *g, vec_t const &v) : cratevec()
    {
        if (!v.empty()) init(g, v.data(), v.size());
    }
    cratevec(Graph *g, vec_t &&v) : cratevec()
    {
        if (!v.empty()) init_move(g, v.data(), v.size());
    }
    cratevec(Graph *g, size_t n) : cratevec() { init(g, n); }
    cratevec(cratevec const &) = delete;
    cratevec(cratevec &&) = delete;
    ~cratevec()
    {
        if constexpr (need_dtor) {
            if (m_len > 0) {
                T *const ptr0 = m_ptr;
                T *ptr = ptr0 + m_len;
                do {
                    ptr--;
                    ptr->~T();
                } while (ptr > ptr0);
            }
        }
    }

    cratevec &operator=(cratevec const &) = delete;
    cratevec &operator=(cratevec &&) = delete;

    void init(Graph *g, T const *data, size_t n)
    {
        assert(m_len == 0);
        if (n) {
            m_ptr = graph_crate(*g)->alloc_array<T, true>(n);
            std::uninitialized_copy_n(data, n, m_ptr);
            m_len = n;
        }
    }
    void init_move(Graph *g, T *data, size_t n)
    {
        assert(m_len == 0);
        if (n) {
            m_ptr = graph_crate(*g)->alloc_array<T, true>(n);
            std::uninitialized_move_n(data, n, m_ptr);
            m_len = n;
        }
    }
    // these methods get used during deserialize, so allow it to pass crate in directly.
    void init(hnnx::Crate *const crate_p, size_t const n)
    {
        assert(m_len == 0);
        if (n) {
            m_ptr = crate_p->alloc_array<T, true>(n);
            std::uninitialized_value_construct_n(m_ptr, n);
            m_len = n;
        }
    }
    // The DCrate version is defined in dcrate_inlines.h
    void init(hnnx::DCrate *crate_p, size_t n);

    void init(Graph *const g, size_t const n) { init(graph_crate(*g), n); }
    void init(Graph *const g, vec_t const &v) { init(g, v.data(), v.size()); }
    void init(Graph *const g, vec_t &&v) { init_move(g, v.data(), v.size()); }

    iterator begin() noexcept { return m_ptr; }
    iterator end() noexcept { return m_ptr + m_len; }
    const_iterator begin() const noexcept { return m_ptr; }
    const_iterator end() const noexcept { return m_ptr + m_len; }
    const_iterator cbegin() const noexcept { return m_ptr; }
    const_iterator cend() const noexcept { return m_ptr + m_len; }
    size_type size() const noexcept { return m_len; }
    T *data() noexcept { return m_ptr; }
    T const *data() const noexcept { return m_ptr; }
    bool empty() const noexcept { return m_len == 0; }
    reference operator[](size_type idx) { return m_ptr[idx]; }
    const_reference operator[](size_type idx) const { return m_ptr[idx]; }
    reference at(size_type idx)
    {
        if (idx >= m_len) throw std::range_error("cratevec");
        return m_ptr[idx];
    }
    const_reference at(size_type idx) const { return const_cast<cratevec &>(*this).at(idx); }
    reference front() { return m_ptr[0]; }
    const_reference front() const { return m_ptr[0]; }
    reference back() { return m_ptr[m_len - 1]; }
    const_reference back() const { return m_ptr[m_len - 1]; }
};

} // namespace hnnx

POP_VISIBILITY()

#endif /* CRATE_H_ */
