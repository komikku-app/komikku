//==============================================================================
//
// Copyright (c) 2018, 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef STRING_REGISTRY_TWO
#define STRING_REGISTRY_TWO 1
#include <array>
#include <string>
#include <string_view>
#include <map>
#include <list>
#include <vector>
#include <stdexcept>
#include <cstring>
#include "weak_linkage.h"

//
// 'string registry'
//
// maps std::string -> string_key (which is a pointer)
//  and back.
// Each 'new'  string returns a new key; each previously seen string
// gives the same key as before.
// The empty string always maps to a specific value, a statically allocated entity which can be used for static init
// of string_key objects.
//
// The object pointed to is a pair <stringview, hashval> -- and the stringview pointer is guaranteed to be
// null-terminated - so the conversion from string_key to char * (or to its hash) is very quick.

// NOTE: entries cannot be deleted. the destructor of the string_registry_template<> will free everything.
// Exception: you can also call clear(), which deletes everything except the entry for empty string.
//       Of course this forgets the previous mapping completely.

PUSH_VISIBILITY(default)

namespace hnnx {

template <int K = 0x13121> struct polynomial_string_hash {
    API_EXPORT unsigned operator()(char const *s, size_t n) const
    {
        unsigned h = 0;
        for (int i = 0; i < (int)n; i++) {
            h = h * (unsigned(K) | 1) + s[i];
        }
        return h;
    }
};

//
// The data structure is:
//
//     std::map<std::string_view,hashval_t>  m_fwd_map;
//
//         This maps 'known' strings to their hashes. the string_view references memory in
//         'bulk storage' (see below), each string is null-terminated.
//         This could also be an unordered_map
//
//    std::list< std::array< char, BULKN> > m_bulk;	 // list of memory chunks for strings.
//    char * m_bulk_current;						// points to m_bulk.back()[0] (or null when none)
//    size_t m_bulk_pos;
//            No. of bytes used in m_bulk_current.
//    NOTE: the nodes in m_bulk  cannot be moved, since the m_fwd_map keys point to them.
//
//

template <class HASHFUNC> class string_registry_two {
    typedef unsigned hashval_t;
    typedef std::pair<const std::string_view, hashval_t> mapval_t;

  public:
    typedef mapval_t const *string_key;

  protected:
    static constexpr int BULKN = 4096 - 2 * sizeof(void *);

    HASHFUNC hasher;

    typedef std::array<char, BULKN> bulkarray;
    std::list<bulkarray> m_bulk;
    char *m_bulk_current;
    size_t m_bulk_pos;
    std::map<std::string_view, hashval_t> m_fwd_map;

    API_EXPORT unsigned get_hash(std::string_view s) { return hasher(s.data(), s.size()); }

    API_EXPORT char *need_bulk(size_t n)
    {
        if (n > BULKN) throw std::length_error("string too long");
        if (m_bulk_current == nullptr || m_bulk_pos + n > BULKN) {
            m_bulk.emplace_back();
            m_bulk_current = &m_bulk.back()[0];
            m_bulk_pos = 0;
        }
        char *const res = m_bulk_current + m_bulk_pos;
        m_bulk_pos += n;
        return res;
    }

    static mapval_t empty_string_node;

  public:
    API_EXPORT string_registry_two();
    string_registry_two(string_registry_two<HASHFUNC> const &) = delete;
    string_registry_two &operator=(string_registry_two<HASHFUNC> const &) = delete;

    // the number of entries (not counting empty string)

    API_EXPORT size_t size() const { return m_fwd_map.size(); }
    API_EXPORT void clear(); // forget everything; free all memory
    // forward map: string to key
    API_EXPORT string_key map_str(std::string_view s);
    API_EXPORT string_key map_str(std::string const &s);
    API_EXPORT string_key map_str(char const *s);

    // like map_str_(s), but if the string is not in the map already,
    // will *not* add it; it will return the string_key for "".
    API_EXPORT string_key map_str_checked(std::string_view s) const;

    // reverse map: to read-only C string.
    API_EXPORT static char const *c_str(string_key sk) { return sk->first.data(); }
    // reverse map to std::string or view.
    API_EXPORT static std::string unmap(string_key sk) { return std::string(sk->first); }
    API_EXPORT static std::string_view const &unmap_sv(string_key sk) { return sk->first; }
    // this is the string_key for "", which is a statically allocated value.
    // Use NOINLINE to avoid "definition of dllimport static field " and "unresolved external symbol" errors on Windows
    API_EXPORT NOINLINE static string_key map_empty_str() { return &empty_string_node; };
};

template <class HASHFUNC>
typename string_registry_two<HASHFUNC>::mapval_t hnnx::string_registry_two<HASHFUNC>::empty_string_node = {{"", 0}, 0};

template <class HASHFUNC>
API_EXPORT hnnx::string_registry_two<HASHFUNC>::string_registry_two() : m_bulk_current(nullptr), m_bulk_pos(0)
{
}

template <class HASHFUNC> API_EXPORT void hnnx::string_registry_two<HASHFUNC>::clear()
{
    // clear the rev maps
    // clear the fwd map
    m_fwd_map.clear();
    // clear all the bulk storage (but leave one, if there is one)
    if (m_bulk.size() > 0) {
        while (m_bulk.size() > 1)
            m_bulk.pop_back();
        m_bulk_current = &m_bulk.back()[0];
        m_bulk_pos = 0;
    }
}

template <class HASHFUNC>
typename string_registry_two<HASHFUNC>::string_key hnnx::string_registry_two<HASHFUNC>::map_str(char const *s)
{
    return map_str(std::string_view(s));
}
template <class HASHFUNC>
typename string_registry_two<HASHFUNC>::string_key hnnx::string_registry_two<HASHFUNC>::map_str(std::string const &s)
{
    return map_str(std::string_view(s));
}

template <class HASHFUNC>
typename string_registry_two<HASHFUNC>::string_key hnnx::string_registry_two<HASHFUNC>::map_str(std::string_view s)
{
    size_t const slen = s.size();
    if (slen == 0) return &empty_string_node; // empty string

    // (1) try to find key in the current map.
    // if it's there, and it usually should be, we don't need to do anything else.

    auto found = m_fwd_map.lower_bound(s);
    if (found != m_fwd_map.end() && s == found->first) return &*found;

    // ok, now we have to do an insert. first put the string in bulk storage
    //
    char *const dst = need_bulk(slen + 1);
    memcpy(dst, s.data(), slen);
    dst[slen] = '\0';

    unsigned const hash = get_hash(s);

    auto ins_iter = m_fwd_map.emplace_hint(found, std::make_pair(std::string_view(dst, slen), hash));
    return &*ins_iter;
}
template <class HASHFUNC>
typename string_registry_two<HASHFUNC>::string_key API_FUNC_EXPORT
hnnx::string_registry_two<HASHFUNC>::map_str_checked(std::string_view s) const
{
    if (s.size() != 0) {
        auto found = m_fwd_map.lower_bound(s);
        if (found != m_fwd_map.end() && s == found->first) return &*found;
    }
    return &empty_string_node;
}
} // namespace hnnx

POP_VISIBILITY()

#endif // STRING_REGISTRY_TWO
