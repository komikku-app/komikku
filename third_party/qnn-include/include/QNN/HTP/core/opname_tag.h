//==============================================================================
//
// Copyright (c) 2019-2021,2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OPNAME_TAG_H
#define OPNAME_TAG_H 1

// uncomment this to get string_tag_t for 'opname_tag_t'
#define WITH_STRING_REG_OPSTR 1
// uncomment this to get string_tag_t for operand_tag_t and split_context_t
#define WITH_STRING_REG_OPERAND 1

#include <string>
#include <string_view>
#include "conversions.h"
#include "macros_attribute.h"
#include "weak_linkage.h"

#if defined(WITH_STRING_REG_OPSTR) || defined(WITH_STRING_REG_OPERAND)
#include "string_registry2.h"
#endif
#include "op_package_name.h"

struct Options;

namespace hnnx {

namespace opname_hash_ns {
// a 16-bit string hash; used to speed up optimization passes.
// must match that used in 'offline' rule builder.
inline unsigned opname_hash_impl(char const *s, unsigned n)
{
    unsigned h = 0;
    for (int i = 0; i < (int)n; i++) {
        h = muladdu32_modular(0x381u, h, (unsigned char)s[i]);
    }
    return h & 0xFFFF;
}
} // namespace opname_hash_ns

#ifndef WITH_STRING_REG_OPSTR // not deployed; keep using std::string
typedef std::string opname_tag_t;
// opname_tag_parm_t is an opname_tag_t const & if opname_tag_t is 'heavy',
// and the same as opname_tag_t if 'light'
typedef std::string const &opname_tag_parm_t;

inline unsigned find_opname_hash(std::string const &nm)
{
    return opname_hash_ns::opname_hash_impl(nm.data(), nm.size());
}
#endif

#ifndef WITH_STRING_REG_OPERAND // not deployed; keep using std::string
typedef std::string operand_tag_t;
typedef std::string const &operand_tag_parm_t;
typedef std::string split_context_tag_t;
typedef std::string const &split_context_tag_parm_t;
#endif

#if defined(WITH_STRING_REG_OPSTR) || defined(WITH_STRING_REG_OPERAND)

PUSH_VISIBILITY(default)
namespace opname_hash_ns {
struct opname_hash_functor {
    unsigned operator()(char const *s, size_t n) const { return opname_hash_impl(s, n); }
};

// type for the string registry

typedef string_registry_two<opname_hash_functor> StringRegistry;
typedef StringRegistry::string_key string_key;

} // namespace opname_hash_ns

// declare that this specialization exists in a C++ file
extern template class string_registry_two<opname_hash_ns::opname_hash_functor>;

//
// string_tag_t needs to have the following:
//   - null_ctor
//   - copy-ctor, move_ctor, op= and op=move
//   - construct/assign from: std::string_view, std::string, char const *
//   - operator ==, != and < , to self, for maps
//   - specialization of std::hash to support unordered_map
//   - c_str()  returns char const *
//   - conversion to std::string (explicit)
//   - conversion to std::string_view (explicit)
//   - ideally, == and != comparison to char const*, std::string, and std::string_view
//     should be possible without converting the other to string_tag_t
//   - size(), length()
//   - operator [](size_t), unchecked, read-only
//
//

class string_tag_t {
    using string_key = opname_hash_ns::string_key;
    using registry_t = opname_hash_ns::StringRegistry;
    string_key skey; // <-- this is the only data item. It's a pointer.

    // initially-null pointer to the global string reg, which is a static locsl.
    API_EXPORT static registry_t *globalStringReg;
    // function to get its address
    API_EXPORT static registry_t &get_stringreg_func();
    static inline registry_t &get_stringreg() { return globalStringReg ? *globalStringReg : get_stringreg_func(); }
    // these are just implementations of get_stringreg().map_str( ..various .. );
    API_EXPORT static string_key map_str(std::string_view s);
    API_EXPORT static string_key map_str(std::string const &s);
    API_EXPORT static string_key map_str(char const *s);

  public:
    ~string_tag_t() = default;
    // same as string_tag_t(string_view), but if the name is not already in the registry,
    // it will return string_tag for "" (can check with result.empty())
    API_EXPORT static string_tag_t map_str_checked(std::string_view);

    string_tag_t() : skey(opname_hash_ns::StringRegistry::map_empty_str()) {}
    string_tag_t(string_tag_t const &x) = default;
    string_tag_t(string_tag_t &&x) = default;
    string_tag_t(std::string_view x) : skey(map_str(x)) {}
    string_tag_t(std::string const &x) : skey(map_str(x)) {}
    string_tag_t(char const *x) : skey(map_str(x)) {}
    string_tag_t &operator=(string_tag_t const &) = default;
    string_tag_t &operator=(string_tag_t &&) = default;
    string_tag_t &operator=(std::string_view x)
    {
        skey = map_str(x);
        return *this;
    }
    string_tag_t &operator=(std::string const &x)
    {
        skey = map_str(x);
        return *this;
    }
    string_tag_t &operator=(char const *x)
    {
        skey = map_str(x);
        return *this;
    }
    bool operator==(string_tag_t const &rhs) const { return skey == rhs.skey; }
    bool operator<(string_tag_t const &rhs) const { return skey < rhs.skey; }
    char const *c_str() const { return registry_t::c_str(skey); }
    // the 'explicit' on conversion to string could be removed, but I'd prefer to have implicit
    // conversions flagged, they can usually be modified to something that doesn't allocate memory.
    explicit operator std::string() const { return registry_t::unmap(skey); }
    operator std::string_view() const { return registry_t::unmap_sv(skey); }
    char operator[](size_t i) const { return c_str()[i]; }
    size_t size() const { return skey->first.size(); }
    size_t length() const { return size(); }
    bool empty() const { return skey == registry_t::map_empty_str(); }
    API_EXPORT bool operator==(std::string_view x) const; //{ return registry_t::unmap_sv(skey)==x;}
    API_EXPORT bool operator==(std::string const &x) const; // { return registry_t::unmap_sv(skey)==x;}
    API_EXPORT bool operator==(char const *x) const; //{ return registry_t::unmap_sv(skey)==std::string_view(x);}
    template <class T> bool operator!=(T &&other) const { return !this->operator==(std::forward<T>(other)); }
    // string_tag_t::nullobj() returns a 'null' object which is mostly unusable; it can
    // be copied; also == and != will work. null object are equal to each other and
    // different from other string_tag_t.
    API_EXPORT static inline string_tag_t nullobj() { return string_tag_t((string_key) nullptr); }

  protected:
    explicit string_tag_t(string_key k) : skey(k) {}
    friend struct std::hash<string_tag_t>;
    friend unsigned find_opname_hash(string_tag_t const &nm);
    // this  meets the requirements for std::hash: mapping may change from run to run.
    API_EXPORT size_t std_hash_val() const noexcept { return size_t(skey); }
};
POP_VISIBILITY()

inline bool operator==(std::string_view a, string_tag_t const &b)
{
    return b == a;
}
inline bool operator==(std::string const &a, string_tag_t const &b)
{
    return b == a;
}
inline bool operator==(char const *a, string_tag_t const &b)
{
    return b == a;
}
inline bool operator!=(std::string_view a, string_tag_t const &b)
{
    return !(b == a);
}
inline bool operator!=(std::string const &a, string_tag_t const &b)
{
    return !(b == a);
}
inline bool operator!=(char const *a, string_tag_t const &b)
{
    return !(b == a);
}
#endif

#ifdef WITH_STRING_REG_OPSTR // string reg deployed for opname
typedef string_tag_t opname_tag_t;
typedef string_tag_t opname_tag_parm_t;
inline unsigned find_opname_hash(string_tag_t const &nm)
{
    return nm.skey->second & 0xFFFF;
}
#endif

#ifdef WITH_STRING_REG_OPERAND // deployed for operand_tag and split_context_tag
typedef string_tag_t operand_tag_t;
typedef string_tag_t operand_tag_parm_t;
typedef string_tag_t split_context_tag_t;
typedef string_tag_t split_context_parm_t;
#endif

PUSH_VISIBILITY(default)
// this is for use in code where we need to transform a literal name to opstr,
// e.g. opname_tag_t opname_Concat = make_opname( "Concat");
//
API_EXPORT opname_tag_t make_opname(char const *opname, char const *prefix = THIS_PKG_NAME_STR);
POP_VISIBILITY()

// For retrieving an option value from within DEF_OPT rules, converted to T.
// Returns true if the option exists and can convert to T;
// if it doesn't, return false and sets result to 0.
// This is only implemented for T = int, size_t, float.
// 'bool' option values can be obtained as int or size_t, will be 0 or 1.
// 'string' values are treated as bool (false if empty).
template <typename T> bool get_option_value(Options const &ops, hnnx::opname_tag_parm_t optname, T &result);

} // namespace hnnx

#if defined(WITH_STRING_REG_OPSTR) || defined(WITH_STRING_REG_OPERAND)
namespace std {
template <> struct hash<hnnx::string_tag_t> {
    typedef hnnx::string_tag_t argument_type;
    typedef std::size_t result_type;
    result_type operator()(argument_type const &s) const noexcept { return s.std_hash_val(); }
};
} // namespace std

// these declarations make it possible to use a string_tag_t as the key
// in a minihash or miniset
namespace hnnx {
namespace minObj {
template <typename Key, bool ERASE_OK> struct hashmap_traits;
template <typename T> struct findhash;
uint32_t findhash_sizet(size_t);
template <> struct hashmap_traits<hnnx::string_tag_t, true> {
    static constexpr bool valid = true;
};
template <> struct hashmap_traits<hnnx::string_tag_t, false> {
    static constexpr bool valid = true;
    static inline hnnx::string_tag_t generate_null() { return hnnx::string_tag_t::nullobj(); }
    static inline bool is_null(hnnx::string_tag_t k) { return k == hnnx::string_tag_t::nullobj(); }
};
template <> struct findhash<hnnx::string_tag_t> {
    inline uint32_t operator()(hnnx::string_tag_t s) const
    {
        return findhash_sizet(std::hash<hnnx::string_tag_t>()(s));
    }
};
} // namespace minObj
} // namespace hnnx

#endif

#endif // OPNAME_TAG_H
