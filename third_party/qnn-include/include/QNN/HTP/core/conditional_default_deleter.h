#pragma once
///
/// @file conditional_default_deleter.h
/// @brief Implementation of a conditional (i.e. to destroy or to not destroy
/// managed object) deleter for use with smart pointers
///
/// Copyright (c) 2025 Qualcomm Technologies, Inc. All Rights Reserved.
/// Confidential and Proprietary - Qualcomm Technologies, Inc.
///

#include <new>
namespace hnnx {
///
/// @brief Conditional deleter for use with C++ smart pointers
/// @tparam T Type being managed by associated smart pointer
///
template <class T> struct conditional_default_deleter {
    ///
    /// @brief Constructor
    /// @param destroy Should instance of managed object be really destroyed via
    /// standard deallocator - delete or delete[]?
    ///
    constexpr conditional_default_deleter(bool destroy) : _must_destroy(destroy) {}

    ///
    /// @brief Copy constructor
    /// @param rhs Conditional deleter instance to copy from
    ///
    conditional_default_deleter(conditional_default_deleter const &from) : _must_destroy(from._must_destroy) {}

    ///
    /// @brief Move constructor
    /// @param [in] from Instance to move from
    /// @warning Required by static analyzer
    ///
    conditional_default_deleter(conditional_default_deleter<T> &&from) = default;

    ///
    /// @brief Copy assignment operator
    /// @param [in] from Instance to copy from
    /// @warning Required by static analyzer
    ///
    conditional_default_deleter &operator=(conditional_default_deleter const &from) = default;

    ///
    /// @brief Move assignment operator
    /// @details Not implemented!
    /// @warning Required by static analyzer
    ///
    conditional_default_deleter &operator=(conditional_default_deleter &&from) = default;

    ///
    /// @brief Destructor
    ///
    ~conditional_default_deleter() = default;

    ///
    /// @brief Function operator
    /// @param [in] ptr Pointer to be deleted
    ///
    void operator()(T *ptr) const
    {
        if (_must_destroy) {
            delete ptr;
        }
    }

    ///
    /// @brief Function operator
    /// @param [in] ptr Array to be deleted
    ///
    void operator()(T ptr) const
    {
        if (_must_destroy) {
            delete[] ptr;
        } else {
        }
    }

    ///
    /// @brief Should object managed by smart pointer be destroyed?
    ///
    bool const _must_destroy;
};
}; // namespace hnnx
