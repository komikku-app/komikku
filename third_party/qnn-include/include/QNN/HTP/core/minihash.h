//==============================================================================
//
// Copyright (c) 2020 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef MINIHASH_H_
#define MINIHASH_H_

#include <cassert>
#include <vector>
#include <utility>
#include <cstdint>
#include <cstring>
#include <type_traits>
#include <stdexcept>
#include "conversions.h"
#include "type_help.h"
#include "builtin_intrinsics.h"

#define XASSERT assert

namespace hnnx {

namespace minObj {

inline int ceiling_log2(size_t value)
{
    if (value < 2) {
        return 0;
    }
    if constexpr (sizeof(size_t) <= sizeof(unsigned long)) {
        int const clz = HEX_COUNT_LEADING_ZERO_UL((unsigned long)value - 1);
        return 8 * sizeof(unsigned long) - clz;
    } else {
        // LCOV_EXCL_START [SAFTYSWCCB-1736] device with 8 byte size_t
        int const clz = HEX_COUNT_LEADING_ZERO_ULL((unsigned long long)value - 1);
        return 8 * sizeof(unsigned long long) - clz;
    }
    // LCOV_EXCL_STOP
}

// hash T to uint32_t
//
template <typename T> struct findhash {
    // uint32_t operator() ( T ) const{...}
};

// define for 'unsigned'
// this is intended to be reasonably quick on hexagon
template <> struct findhash<unsigned> {
    uint32_t operator()(unsigned n) const
    {
        uint64_t const bigprod = uint64_t(n) * 0x740F1DE9;
        return (uint32_t)bigprod ^ (uint32_t)(bigprod >> 32);
    }
};
// define for 'unsigned long long' assuming it's 64 bits
template <> struct findhash<unsigned long long> {
    static_assert(sizeof(unsigned long long) == 8, "assumed true...?");
    uint32_t operator()(unsigned long long n) const
    {
        unsigned const upperhash = mulu32_modular(0x192E2101u, (unsigned)(n >> 32));
        return findhash<unsigned>()((unsigned)n ^ upperhash);
    }
};
// define for 'unsigned long', which could be either 32 or 64
template <>
struct findhash<unsigned long>
    : public findhash<
              std::conditional<(sizeof(unsigned long) > sizeof(unsigned)), unsigned long long, unsigned>::type> {
};
// this is useful for defining findhash<X> on other types in other headers,
// in terms of std::hash<X>,  without needing to include this header first.
inline uint32_t findhash_sizet(size_t val)
{
    return findhash<size_t>()(val);
}
//
// define for T*
//
template <typename T> struct findhash<T *> {
    inline uint32_t operator()(T *ptr) const { return findhash<size_t>()((size_t)ptr); }
};

template <> struct findhash<int> : public findhash<unsigned> {
};
template <> struct findhash<long> : public findhash<unsigned long> {
};
template <> struct findhash<long long> : public findhash<unsigned long long> {
};

// hashmap_traits<typename Key,bool ERASE_OK>:
//    bool valid:                     is this key OK
//  (only needed if !ERASE_OK):
//    static Key generate_null();		// make a 'null' entry
//    static bool is_null(Key);		// test if nul
template <typename Key, bool ERASE_OK> struct hashmap_traits { // defaults for ERASE_OK=true
    static constexpr bool valid = std::is_trivial<Key>::value;
};

template <typename Key> struct hashmap_traits<Key, false> { // defaults for ERASE_OK=false
    static constexpr bool valid =
            std::is_trivial<Key>::value && (std::is_integral<Key>::value || std::is_pointer<Key>::value);
    static inline Key generate_null() { return Key(0); }
    static inline bool is_null(Key k) { return k == 0; }
};

// fake instance of integer type IT
template <typename T> struct stuck_at_0 {
    stuck_at_0() {}
    stuck_at_0(T) {}
    void operator=(T) {}
    operator T() const { return 0; }
};

////////////////////////////////////////////////////////////////////
//
// minObj::hashmap<Key,T,ERASE_OK [,HASH]>
//
// typedefs:
//    minihash_noerase<Key,T>
//    minihash<Key,T>
//
//
// This implements some std::map<Key,T> functionality but
// storing the data in a contiguous array;  there are some constraints:
//
// - The key must be a trivial type, for which findhash()(Key) is workable,
//   and == is defined; for ERASE_OK = false, it must be an integer type of 32 or 64
//   bits, and the default hash is defined for those.
//   Pointer type is also acceptable.
//
//  - T() must be move-constructable (and ideally should be a simple type).
//   If it is expensive to move, maybe this type is not a good choice (occasional
//   'rehash' moves everything in the table).
//
// - if ERASE_OK is false, then
//     (a) key=0 is not allowed (it is used to mark reserved records)
//     (b) there is no way to delete entries (except clear()).
//
//  - iteration is possible; the iterators address a tuple<const Key,T>
//
//  - iteration is only possible in forward direction (since the order is indeterminate,
//    this is not really as issue)
//    *But* adding a key may invalidate all except the end() iterator.
//    shrink() may also do this.
//
//  - insertions are usually fast, but may sometimes take a while (if the hash table is enlarged).
//    The policy is that if it is more than half full, its size is increased by 4.
//
//
//  - (for ERASE_OK): deletions do not result in resizing, and do not invalidate iterators;
//    but there is a 'shrink()' method which may shrink the table (and invalidate iterators)
//    if there are enough dead nodes in it.
//
// The constructor can supply the initial size, which is rounded up to a power of 2
// (and at least 8); the default is to make an empty table which becomes n=64 on first insert.
//
//  methods supported:
//          hash.size()      ->  size_t
//          hash.empty()     ->  bool
//          hash.count(k)    ->  size_t
//          hash.contains(k) ->  bool
//          hash.clear()
//          hash[k]          -> T &
//          hash.at(k)       -> T &, T const &
//          hash.find(k)     -> iterator or const iter
//          hash.emplace( k, ... parms for T ctor )
//          hash.try_emplace( k, ... parms for T ctor )
//                (emplace and try_emplace are actually the same..)
//          hash.begin(), hash.end(), hash.cbegin(), hash.cend()
//          hash.swap( otherhash )
//        Special methods:
//          hash.shrink()->bool   (this does nothing when ERASE_OK is false).
//              shrink() will rehash if a significant number of deleted entries
//              exist; it returns true if it changed anything,
//          hash.count_deleted()->size_t
//              Returns the number of deleted entries. Always 0 when ERASE_OK = false.
//
//   Only if ERASE_OK:
//          hash.erase(k)				- erase any key 'k'   (returns 0 or 1)
//          hash.erase( iterator )		- erase iterator, which must be valid.
//                     returns 'next' iterator
//       Erase will not cause a 'rehash', so it invalidates any iterator to the
//       erased element, but no others. Erasing the last entry (so size()=0) may
//       cause all deleted entries to be cleared out.
//
// The hash table size HN is always a power of 2.
// for a given key, we find a uint32 hash h, and then the path through the buckets is
// defined by :
//   - start at bucket h % NH;
//   - increment by d mod NH, where d=2*(h>>16)+1.
//  Since d is odd, it is always relatively prime to HN, so the path will cover all
//   buckets.
//  Note,
//   if HN > 64K, a few 'h' bits are used for both the start position and d;
//   if HN > 128K, the 'd' values are limited to 1,3 .. 128K-1 so that space
//   is not fully covered. But, if the hash is uniform over the 32 bits these
//   should not be problematic for reasonable-size hashes.
//
//
template <typename Key> struct simple_key {
    Key first; // the key value
    using traits = hashmap_traits<Key, false>;
    simple_key() : first(traits::generate_null()) {}
    inline void clear_key() { first = traits::generate_null(); }
    inline bool is_inuse() const { return !traits::is_null(first); }
    inline bool is_deleted() const { return false; }
    inline bool is_never_used() const { return traits::is_null(first); }
};
// this is used as the raw_entry type when ERASE_OK=false;
// it should have the same size and layout as tuple<Key,T>
template <typename Key, typename T> struct simple_raw_entry : public simple_key<Key> {
    char value_slot alignas(T)[sizeof(T)];
    static inline bool is_null(Key k) { return simple_key<Key>::traits::is_null(k); }

    simple_raw_entry() : simple_key<Key>() {}
    // this is only used when destroying a whole table, so we don't
    // need to clear the key
    inline void destroy()
    {
        if (this->is_inuse()) reinterpret_cast<T *>(value_slot)->~T();
    }
    inline void clear_entry()
    {
        if (this->is_inuse()) {
            reinterpret_cast<T *>(value_slot)->~T();
            this->clear_key();
        }
    }
    inline T &value_field() { return *reinterpret_cast<T *>(value_slot); }
    inline T const &value_field() const { return *reinterpret_cast<T const *>(value_slot); }
    // move_from is only used when rehashing; the 'from' record is
    // known to be non-empty. We move-construct the value, and
    // delete the old one.
    inline void move_from(Key k, simple_raw_entry &from)
    {
        XASSERT(!is_null(k) && !this->is_inuse());
        this->first = k;
        new (value_slot) T(std::move(from.value_field()));
        reinterpret_cast<T *>(from.value_slot)->~T();
    }
    inline void copy_from(simple_raw_entry const &other)
    {
        this->first = other.first;
        if (other.first != 0) new (value_slot) T(other.value_field());
    }
    template <class... Args> inline void emplace_new(Key k, Args &&...args)
    {
        XASSERT(!is_null(k) && !this->is_inuse());
        this->first = k;
        new (value_slot) T(std::forward<Args>(args)...);
    }
};
// simple_raw_entry<Key,no_value>
// is used for set-of-Key.
struct no_value {
};

template <typename Key> struct simple_raw_entry<Key, no_value> : public simple_key<Key> {
    static inline bool is_null(Key k) { return simple_key<Key>::traits::is_null(k); }
    simple_raw_entry() : simple_key<Key>() {}
    inline void destroy() {}
    inline void clear_entry() { this->clear_key(); }
    inline void move_from(Key k, simple_raw_entry &from)
    {
        XASSERT(!is_null(k) && !this->is_inuse());
        this->first = k;
    }
    inline void copy_from(simple_raw_entry const &other) { this->first = other.first; }
    inline void emplace_new(Key k, no_value const & /*unused*/)
    {
        XASSERT(!is_null(k) && !this->is_inuse());
        this->first = k;
    }
};

// erasable_raw_entry; for deletable maps
// erasable_raw_entry<Key,no_value> is used
// for deletable sets (the 'empty' value_slot
// may use up a byte, but 'state' already does so it's
// unlikely to increase the size).
//
template <typename Key, typename T> struct erasable_raw_entry {
    Key first; // the key value
    char value_slot alignas(T)[sizeof(T)];
    signed char state;
    // state = 0: never used; 1 = in use; -1 = deleted.
    //
    inline erasable_raw_entry() : state(0) {}

    inline bool is_inuse() const { return state == 1; }
    inline bool is_deleted() const { return state < 0; }
    inline bool is_never_used() const { return state == 0; }
    // this is only used when destroying a whole table, so we don't
    // need to clear the state
    inline void destroy()
    {
        if (state == 1) reinterpret_cast<T *>(value_slot)->~T();
    }
    // this is used in 'clear'; all state to 0.
    inline void clear_entry()
    {
        if (state == 1) {
            reinterpret_cast<T *>(value_slot)->~T();
        }
        state = 0;
    }
    // this is used in 'erase'.
    inline void erase_entry()
    {
        XASSERT(state == 1);
        if (state == 1) {
            reinterpret_cast<T *>(value_slot)->~T();
        }
        state = -1;
    }
    inline T &value_field() { return *reinterpret_cast<T *>(value_slot); }
    inline T const &value_field() const { return *reinterpret_cast<T const *>(value_slot); }
    inline void move_from(Key k, erasable_raw_entry &from)
    {
        XASSERT(state <= 0);
        first = k;
        new (value_slot) T(std::move(from.value_field()));
        reinterpret_cast<T *>(from.value_slot)->~T();
        state = 1;
    }
    inline void copy_from(erasable_raw_entry const &other)
    {
        state = other.state;
        if (other.state == 1) {
            first = other.first;
            new (value_slot) T(other.value_field());
        }
    }

    template <class... Args> inline void emplace_new(Key k, Args &&...args)
    {
        XASSERT(state <= 0);
        first = k;
        new (value_slot) T(std::forward<Args>(args)...);
        state = 1;
    }
};

template <typename Key, typename T, bool ERASE_OK, typename HSH = findhash<Key>> class hashmap {
    using traits = hashmap_traits<Key, false>;
    static_assert(traits::valid, "Bad key type for hashmap");

    typedef typename std::conditional<ERASE_OK, erasable_raw_entry<Key, T>, simple_raw_entry<Key, T>>::type raw_entry_t;

    static constexpr bool T_needs_dtor = !std::is_trivially_destructible<T>::value;

    // data members
    size_t m_hashN; // size of the table (a power of 2)
    unsigned int m_log2N; // log2(hashN)
    size_t m_entries; // number of used entries, plus deleted entries.
    // number of deleted entries: 'stuck_at_0' when !ERASE_OK.
    typename std::conditional<ERASE_OK, size_t, stuck_at_0<size_t>>::type m_deleted; // deleted entries (if ERASE_OK)

    std::vector<raw_entry_t> m_table;

    typedef typename std::vector<raw_entry_t>::iterator table_iter_type;
    typedef typename std::vector<raw_entry_t>::const_iterator table_kiter_type;

    static constexpr bool is_set = std::is_same<T, no_value>::value;
    template <typename, typename, bool, typename> friend class hashmap;
    template <typename, bool, typename> friend class hashset;

  public:
    typedef Key key_type;
    typedef T mapped_type;
    typedef typename std::conditional<is_set, Key, std::pair<const Key, T>>::type value_type;
    typedef size_t size_type;

    static_assert(ERASE_OK || sizeof(raw_entry_t) == sizeof(value_type), "failed to make compatible layout");

  private:
    // iterator will be defined as a pointer to 'this'
    // and an index into the table, with 'end()' being an index
    // of -1; so we move backwards.
    class hmap_kiterator {
        friend class hashmap;
        template <typename, typename, bool, typename> friend class hashmap;
        //template <typename K2>
        //friend class hashmap<K2,T,ERASE_OK,HSH>;
      protected:
        hashmap *m_object;
        int m_posn;
        hmap_kiterator(hashmap const *obj, int posn) : m_object(const_cast<hashmap *>(obj)), m_posn(posn) {}
        hmap_kiterator(hashmap const *obj, table_iter_type posn)
            : m_object(const_cast<hashmap *>(obj)), m_posn(posn - obj->m_table.begin())
        {
        }

      public:
        hmap_kiterator() : m_object(nullptr), m_posn(-1) {}
        hmap_kiterator(hmap_kiterator const &) = default;
        hmap_kiterator &operator=(hmap_kiterator const &) = default;

        value_type const &operator*() const { return *reinterpret_cast<value_type *>(&m_object->m_table[m_posn]); }
        value_type const *operator->() const { return reinterpret_cast<value_type *>(&m_object->m_table[m_posn]); }
        hmap_kiterator &operator++()
        { // pre-inc
            m_posn = m_object->find_next_for_iter(m_posn);
            return *this;
        }

        hmap_kiterator operator++(int)
        { // post-inc
            hmap_kiterator prev{*this};
            m_posn = m_object->find_next_for_iter(m_posn);
            return prev;
        }
        bool operator==(hmap_kiterator const &other) const { return m_posn == other.m_posn; }
        bool operator!=(hmap_kiterator const &other) const { return m_posn != other.m_posn; }
    };
    class hmap_iterator : public hmap_kiterator {
        friend class hashmap;
        //template <typename K2>
        //friend class hashmap<K2,T,ERASE_OK,HSH>;
      protected:
        hmap_iterator(hashmap *obj, int posn) : hmap_kiterator(obj, posn) {}
        hmap_iterator(hashmap *obj, table_iter_type posn) : hmap_kiterator(obj, posn) {}
        explicit hmap_iterator(hmap_kiterator const &k) : hmap_kiterator(k) {}
        // coerce from an iterator of another type. This is safe if it is
        // coerced back to the proper type before use, and if the value_type are layout
        // compatible with the same T (and K of the same size).
        template <typename otherhash> hmap_iterator coerce_from(typename otherhash::hmap_kiterator const &other)
        {
            return hmap_iterator((hashmap *)other.m_object, other.m_posn);
        }

      public:
        using value_type = hashmap::value_type;
        using difference_type = std::ptrdiff_t;
        using iterator_category = std::forward_iterator_tag;
        using pointer = value_type *;
        using reference = value_type &;

        hmap_iterator() : hmap_kiterator() {}
        hmap_iterator(hmap_iterator const &) = default;
        hmap_iterator &operator=(hmap_iterator const &) = default;

        value_type &operator*() const { return const_cast<value_type &>(this->hmap_kiterator::operator*()); }
        value_type *operator->() const { return const_cast<value_type *>(this->hmap_kiterator::operator->()); }
        hmap_iterator &operator++()
        { // pre-inc
            return static_cast<hmap_iterator &>(this->hmap_kiterator::operator++());
        }
        hmap_iterator operator++(int)
        { // post-inc
            return hmap_iterator(this->hmap_kiterator::operator++(0));
        }
        // == and != inherit
    };

  protected:
    template <typename otherhash> static hmap_iterator coerce_iter(typename otherhash::hmap_kiterator const &other)
    {
        return hmap_iterator((hashmap *)other.m_object, other.m_posn);
    }

  public:
    typedef hmap_iterator iterator;
    typedef hmap_kiterator const_iterator;

    explicit hashmap(size_t n_entries) : m_hashN(0), m_log2N(-1), m_entries(0), m_deleted(0)
    {
        make_new_table(ceiling_log2(n_entries | 7));
    }
    hashmap() : m_hashN(0), m_log2N(-1), m_entries(0), m_deleted(0) {}

    inline size_t size() const { return m_entries - m_deleted; }
    inline bool empty() const { return m_entries == m_deleted; }
    inline size_t count_deleted() const { return m_deleted; }

    ~hashmap()
    {
        if constexpr (T_needs_dtor) clear();
    }

    bool shrink()
    {
        if constexpr (ERASE_OK) {
            if (m_deleted * 2 > m_entries) {
                rehash_table(false);
                return true;
            }
        }
        return false; // only does anything when ERASE_OK
    }

    hashmap(hashmap &&other) noexcept : hashmap() { this->swap(other); }
    // copy-ctor: create empty, then use copy_from
    hashmap(hashmap const &other) : hashmap() { copy_from(other); }
    // assignment: clear, then use copy_from.
    hashmap &operator=(hashmap const &other)
    {
        clear();
        copy_from(other);
        return *this;
    }
    // for this we can clear 'this' and then swap everything.
    hashmap &operator=(hashmap &&other) noexcept
    {
        clear();
        this->swap(other);
        return *this;
    }

    void swap(hashmap &other) noexcept
    {
        std::swap(m_hashN, other.m_hashN);
        std::swap(m_log2N, other.m_log2N);
        std::swap(m_entries, other.m_entries);
        if constexpr (ERASE_OK) std::swap(m_deleted, other.m_deleted);
        std::swap(m_table, other.m_table);
    }

    iterator begin() { return hmap_iterator{this, find_next_for_iter(m_hashN)}; }
    const_iterator cbegin() const { return hmap_kiterator{const_cast<hashmap *>(this), find_next_for_iter(m_hashN)}; }
    const_iterator begin() const { return cbegin(); }

    iterator end() { return hmap_iterator{this, -1}; }
    const_iterator cend() const { return hmap_kiterator{const_cast<hashmap *>(this), -1}; }
    const_iterator end() const { return cend(); }

    void clear()
    {
        if (m_entries == 0) return;
        raw_entry_t *const p = m_table.data();
        if constexpr (T_needs_dtor || sizeof(raw_entry_t) > 2 * sizeof(Key)) {
            for (size_t i = 0, n = m_hashN; i < n; i++) {
                p[i].clear_entry();
            }
        } else { // nuke the entire site from orbit
            memset(p, 0, m_hashN * sizeof(raw_entry_t));
        }
        m_entries = 0;
        m_deleted = 0;
    }

    size_t count(Key const &k) const
    {
        table_kiter_type const iter = find_key(k);
        return iter != m_table.end() ? 1 : 0;
    }
    bool contains(Key const &k) const
    {
        table_kiter_type const iter = find_key(k);
        return iter != m_table.end();
    }
    iterator find(Key const &k)
    {
        table_kiter_type const iter = find_key(k);
        return hmap_iterator{this, (iter == m_table.end()) ? -1 : int(iter - m_table.begin())};
    }
    const_iterator find(Key const &k) const
    {
        table_kiter_type const iter = find_key(k);
        return hmap_kiterator{this, (iter == m_table.end()) ? -1 : int(iter - m_table.begin())};
    }

    // this may invalidate iterators by enlarging the table,
    // if the key is not already there; but the one returned is always valid.
    template <class... Args> std::pair<iterator, bool> emplace(Key const &k, Args &&...args)
    {
        table_iter_type const titer = find_key_for_ins(k);
        std::pair<iterator, bool> result{iterator{this, titer}, false};

        if (!titer->is_inuse()) {
            [[maybe_unused]] bool const was_deleted = titer->is_deleted();
            result.second = true;
            titer->emplace_new(k, std::forward<Args>(args)...);
            if constexpr (ERASE_OK) {
                if (was_deleted)
                    m_deleted--;
                else
                    m_entries++;
            } else {
                m_entries++;
            }
        }
        return result;
    }
    template <class... Args> std::pair<iterator, bool> try_emplace(Key const &k, Args &&...args)
    {
        return emplace(k, std::forward<Args>(args)...);
    }

    T &at(Key const &k)
    {
        table_kiter_type const titer = find_key(k);
        if (titer == m_table.end()) throw std::out_of_range("minimap::at");
        return const_cast<T &>(titer->value_field());
    }
    T const &at(Key const &k) const { return const_cast<hashmap &>(*this).at(k); }

    T &operator[](Key const &k)
    {
        static_assert(std::is_constructible<T>::value, "map[] requires a null-constructible mapped_type");
        table_iter_type const titer = find_key_for_ins(k);
        if (!titer->is_inuse()) {
            bool const was_deleted = titer->is_deleted();
            titer->emplace_new(k);
            if constexpr (ERASE_OK) {
                if (was_deleted)
                    m_deleted--;
                else
                    m_entries++;
            } else {
                m_entries++;
            }
        }
        return titer->value_field();
    }

    size_t erase(Key const &k)
    {
        static_assert(ERASE_OK, "erase not supported in this type");
        if constexpr (ERASE_OK) {
            table_kiter_type iter = find_key(k);
            if (iter != m_table.end()) { // found
                erase_inner(iter);
                return 1;
            }
        }
        return 0;
    }
    iterator erase(iterator const &posn)
    {
        static_assert(ERASE_OK, "erase not supported in this type");
        if constexpr (ERASE_OK) {
            int const p = posn.m_posn;
            table_kiter_type const entp = m_table.cbegin() + p;
            assert(posn.m_object == this && (size_t)p < m_hashN && entp->is_inuse());
            erase_inner(entp);
            return hmap_iterator{this, find_next_for_iter(m_hashN)};
        } else {
            return end();
        }
    }

  protected:
    void erase_inner(table_kiter_type iter)
    {
        if constexpr (ERASE_OK) {
            const_cast<raw_entry_t &>(*iter).erase_entry();
            m_deleted++;
            if (m_deleted == m_entries) {
                clear_all_deleted();
            }
        }
    }
    // m_table assumed to be empty; resize it for 2^log2 entries
    void make_new_table(unsigned int log2n)
    {
        size_t const hn = size_t(1) << log2n;
        m_table.resize(hn);
        m_hashN = hn;
        m_log2N = log2n;
    }

    // locate a key; returns an iterator to where it is, or to where it would go,
    // if inserted. when 'INSERT' is true, never returns m_table.end().
    // when INSERT is false, it will not change anything and will return m_table_end()
    // if if doesn't find a proper entry.
    // This must not be called on empty table!

    template <bool INSERT = true> table_iter_type lookup_key_template(Key const &k)
    {
        size_t const mask = m_hashN - 1;
        uint32_t const hsh = HSH()(k);
        size_t probe_at = hsh & mask;
        size_t const delta = (hsh >> 15) | 1; // must be odd.
        //printf("       ...probing %llu in %zu at %zu, span=%zu", (unsigned long long)k, mask+1,probe_at,delta&mask);
        table_iter_type const t0 = m_table.begin();
        size_t remain = mask;
        if constexpr (!ERASE_OK) {
            assert(!raw_entry_t::is_null(k)); // this is not allowed
            if (raw_entry_t::is_null(k)) return t0;
            while (1) {
                table_iter_type titer = t0 + probe_at;
                if (!titer->is_inuse() || titer->first == k) {
                    //printf("  ..found in %zu probes\n", mask+1-remain);
                    if (!INSERT && !titer->is_inuse()) return m_table.end();
                    return titer;
                }
                probe_at = (probe_at + delta) & mask;
                if (--remain == 0) throw std::runtime_error("hash lookup failed");
            }
        } else {
            // stop when we find a 'never used' slot,or a matching entry.
            // But, if we are inserting, and we saw any deleted slots, return
            // the iterator of the first deleted slot we saw, so it can be
            // reused for the new key.
            table_iter_type titer = t0 + probe_at;
            table_iter_type titer_end = m_table.end();
            table_iter_type titerx = titer_end;
            while (1) {
                if (titer->is_never_used()) {
                    // end of chain. Return a previously seen deleted slot
                    // if there is one.
                    if (INSERT)
                        return (titerx == titer_end) ? titer : titerx;
                    else
                        return titer_end;
                }
                if (titer->is_inuse()) {
                    if (titer->first == k) {
                        //printf("  ..found in %zu probes\n", mask+1-remain);
                        return titer;
                    }
                } else { // is a deleted slot; if INSERT we want to keep that.
                    if (INSERT)
                        if (titerx == titer_end) titerx = titer;
                }
                probe_at = (probe_at + delta) & mask;
                if (--remain == 0) throw std::runtime_error("hash lookup failed");
                titer = t0 + probe_at;
            }
        }
    }

    // find_key_for_ins is a wrapper on lookup_key_template<true>;
    // It will return either iterator to existing key (and not rehash)
    // or an iterator to a not-in-use slot (and may have done a rehash).
    //
    // (1) find a spot to insert (or existing key)
    // (2) if it's a new insert that will increase the m_entries if used,
    //     and the table is too full, rehash and do it again.
    // (also, if initially empty, go directly to step 2).
    //
    table_iter_type find_key_for_ins(Key const &k)
    {
        size_t thr = m_entries * 2;
        while (1) { // at most 2 iterations.
            if (m_hashN > 0) {
                table_iter_type pos = lookup_key_template<true>(k);
                if (m_hashN >= thr || !pos->is_never_used()) {
                    return pos;
                }
            }
            thr = 0;
            rehash_table(true);
        }
    }

    // find_key is lookup_key_template with no insert;
    // always returns m_table.end() if key not found.
    table_kiter_type find_key(Key const &k) const
    {
        if (m_hashN == 0) return m_table.end(); // no table!
        return const_cast<hashmap *>(this)->lookup_key_template<false>(k);
    }

    // called when the last entry is erased; we can clear all the state
    // to 'never used'.
    //
    void clear_all_deleted()
    {
        if constexpr (ERASE_OK) {
            size_t mdel = m_deleted;
            XASSERT(mdel >= 1 && mdel == m_entries);
            raw_entry_t *const p = m_table.data();
            for (size_t i = 0, n = m_hashN; i < n; i++) {
                int const t = p[i].state;
                if (t != 0) {
                    XASSERT(t < 0);
                    p[i].state = 0;
                    if (--mdel == 0) break; // got them all
                }
            }
            m_entries = 0;
            m_deleted = 0;
        }
    }

    // change the table size
    // and refill from the old data (if any)
    // Used in 'find_key_for_ins' and 'shrink'.
    // 'growing' = true when called on insert, false
    // on shrink.
    void rehash_table(bool growing)
    {
        // how big to make it?
        unsigned const sz = size(); // number of non-deleted entries
        if (!ERASE_OK) growing = true; // don't need 'shrink' code path
        int const new_log2 = growing ? (ceiling_log2(sz | 15) + 2) : (ceiling_log2(sz + 3 + (sz >> 1)) + 1);
        std::vector<raw_entry_t> old_table;
        old_table.swap(m_table);
        make_new_table(new_log2);
        size_t new_count = 0;
        size_t const old_size = old_table.size();
        raw_entry_t *const old_table_p = old_table.data();
        for (size_t i = 0; i < old_size; i++) {
            raw_entry_t &old_entry = old_table_p[i];
            if (old_entry.is_inuse()) {
                new_count++;
                Key k = old_entry.first;
                table_iter_type const new_loc = lookup_key_template<true>(k);
                new_loc->move_from(k, old_entry);
            }
        }
        assert(new_count == m_entries - m_deleted);
        if (ERASE_OK) {
            m_entries = new_count;
            m_deleted = 0;
        }
    }
    // find the largest index 'i' of an in-use entry such
    // that i < posn; or return -1 if none.
    // ('pure' is intended to prevent an inlined erase(iter)
    // from calling or expanding this in cases where you don't use the
    // returned iterator; but I suspect that since the compiler can
    // see the code, it will just develop its own opinion).
    [[gnu::pure]] int find_next_for_iter(int posn) const
    {
        raw_entry_t const *const table_p = m_table.data();
        assert(posn <= int(m_hashN));
        while (--posn >= 0) {
            if (table_p[posn].is_inuse()) return posn;
        }
        return -1;
    }

    // copy from another hashmap.
    // this is used in copy-ctor;
    // and in operator = after 'clear'.
    // We assume the current map is empty (no deleted
    // items) but the table size could be anything.
    void copy_from(hashmap const &other)
    {
        size_t const n_other = other.m_hashN;
        if (n_other == 0) return;
        // adopt the other table size
        if (n_other != m_hashN) {
            make_new_table(other.m_log2N);
        }
        // if std::is_trivial<T>::value, maybe just memcpy?
        //
        raw_entry_t const *const other_p = other.m_table.data();
        raw_entry_t *const this_p = m_table.data();
        assert(m_table.size() == n_other);
        for (size_t i = 0; i < n_other; i++) {
            this_p[i].copy_from(other_p[i]);
        }
        m_entries = other.m_entries;
        m_deleted = other.m_deleted;
    }

}; // class hashmap

// A hashset<K> is made by implementing hashmap<K,no_value>
// and rewriting the interface a little with a private subclass.
// Since we only need sets of ints (32,64,unsigned,signed)
// and pointers, we map Key using value_proxy
// so that all hashset<K> are based on either hashmap<unsigned,no_value>
// or hashmap<unsigned long long,no_value>, and thus share most of
// the generated code across many types.

template <typename Key, bool ERASE_OK, typename HSH = findhash<typename value_proxy<Key, true>::type>>
class hashset : private hashmap<typename value_proxy<Key, true>::type, no_value, ERASE_OK, HSH> {
    using key_set_t = typename value_proxy<Key, true>::type;
    using hashimpl = hashmap<key_set_t, no_value, ERASE_OK, HSH>;
    using hashk = hashmap<Key, no_value, ERASE_OK, HSH>;
    using impl_iterator = typename hashimpl::iterator;
    using impl_kiterator = typename hashimpl::const_iterator;

  public:
    typedef Key key_type;
    typedef Key value_type;
    typedef size_t size_type;
    typedef typename hashk::iterator iterator;
    typedef typename hashk::const_iterator const_iterator;

  protected:
    static inline iterator iter_up(impl_iterator const &it) { return hashk::template coerce_iter<hashimpl>(it); }
    static inline const_iterator iter_up(impl_kiterator const &it) { return hashk::template coerce_iter<hashimpl>(it); }
    static inline impl_iterator iter_down(iterator const &it) { return hashimpl::template coerce_iter<hashk>(it); }
    // image conversion of key will allow us to implement e.g.
    // string_key-> size_t mapping, if string_key is a class
    // containing just a pointer.
    static inline key_set_t key_down(Key const &k)
    {
        static_assert(sizeof(k) == sizeof(key_set_t));
        union {
            Key k1;
            key_set_t k2;
        } const uu = {k};
        return uu.k2;
    }

  public:
    hashset() : hashimpl(){};
    explicit hashset(size_t n_entries) : hashimpl(n_entries) {}

    bool shrink() { return hashimpl::shrink(); }

    hashset(hashset &&other) noexcept : hashimpl(std::move(other)) {}
    hashset(hashset const &other) : hashimpl(other) {}
    hashset &operator=(hashset const &other)
    {
        hashimpl::operator=(static_cast<hashimpl const &>(other));
        return *this;
    }
    hashset &operator=(hashset &&other) noexcept
    {
        hashimpl::operator=(std::move(static_cast<hashimpl &>(other)));
        return *this;
    }

    void swap(hashset &other) noexcept { hashimpl::swap(other); }

    inline size_t size() const noexcept { return hashimpl::size(); }
    inline bool empty() const noexcept { return hashimpl::empty(); }
    inline size_t count_deleted() const noexcept { return hashimpl::count_deleted(); }

    iterator begin() { return iter_up(hashimpl::begin()); }
    const_iterator cbegin() const { return iter_up(hashimpl::cbegin()); }
    const_iterator begin() const { return iter_up(hashimpl::cbegin()); }

    iterator end() { return iter_up(hashimpl::end()); }
    const_iterator cend() const { return iter_up(hashimpl::cend()); }
    const_iterator end() const { return iter_up(hashimpl::cend()); }

    void clear() { hashimpl::clear(); }

    size_t count(Key const &k) const { return hashimpl::count(key_down(k)); }
    bool contains(Key const &k) const { return hashimpl::contains(key_down(k)); }
    iterator find(Key const &k) { return iter_up(hashimpl::find(key_down(k))); }
    const_iterator find(Key const &k) const { return iter_up(hashimpl::find(key_down(k))); }

    // insert, emplace, try_emplace are all the same if k is trivial type.
    std::pair<iterator, bool> emplace(Key const &k) { return insert(k); }
    std::pair<iterator, bool> try_emplace(Key const &k) { return insert(k); }
    std::pair<iterator, bool> insert(Key const &k)
    {
        auto res = hashimpl::try_emplace(key_down(k), no_value{});
        return {iter_up(res.first), res.second};
    }
    size_t erase(Key const &k) { return hashimpl::erase(key_down(k)); }
    iterator erase(iterator const &posn) { return iter_up(hashimpl::erase(iter_down(posn))); }
};

template <typename Key, typename T, bool ERASE_OK, typename HSH = findhash<Key>>
void swap(hashmap<Key, T, ERASE_OK, HSH> &a, hashmap<Key, T, ERASE_OK, HSH> &b)
{
    a.swap(b);
}
template <typename Key, bool ERASE_OK, typename HSH = findhash<Key>>
void swap(hashset<Key, ERASE_OK, HSH> &a, hashset<Key, ERASE_OK, HSH> &b)
{
    a.swap(b);
}

} // namespace minObj

template <typename K, typename T> using minihash_noerase = minObj::hashmap<K, T, false>;
template <typename K, typename T> using minihash = minObj::hashmap<K, T, true>;

template <typename K> using miniset_noerase = minObj::hashset<K, false>;
template <typename K> using miniset = minObj::hashset<K, true>;

} // namespace hnnx

#endif /* MINIHASH_H_ */
