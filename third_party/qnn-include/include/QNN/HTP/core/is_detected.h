//==============================================================================
//
// Copyright (c) 2020-2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================
//
// This is a simple example implementation of is_detected which is implemented
// in std::experimental, but isn't supported by MSVC.
//

#ifndef IS_DETECTED_H
#define IS_DETECTED_H 1

namespace detail {

struct nonesuch {
    ~nonesuch() = delete;
    nonesuch(nonesuch const &) = delete;
    void operator=(nonesuch const &) = delete;
};

template <class Default, class AlwaysVoid, template <class...> class Op, class... Args> struct detector {
    using value_t = std::false_type;
    using type = Default;
};

template <class Default, template <class...> class Op, class... Args>
struct detector<Default, std::void_t<Op<Args...>>, Op, Args...> {
    using value_t = std::true_type;
    using type = Op<Args...>;
};

} // namespace detail

template <template <class...> class Op, class... Args>
using is_detected = typename detail::detector<detail::nonesuch, void, Op, Args...>::value_t;

template <template <class...> class Op, class... Args> constexpr bool is_detected_v = is_detected<Op, Args...>::value;

#endif // IS_DETECTED_H
