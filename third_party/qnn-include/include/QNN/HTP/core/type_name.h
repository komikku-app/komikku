//==============================================================================
//
// Copyright (c) 2021 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef TYPE_NAME_H
#define TYPE_NAME_H 1

#include <array>
#include <string_view>

template <typename T> constexpr const char *type_name()
{
    return "unknown";
}

// Macros called from tensor.h when declaring a new tensor type whcih creates a map from op code to
// typename
template <typename> struct TensorTypeStruct;
#define DEFINE_TYPENAME(TYPE, TYPENAME)                                                                                \
    template <> struct TensorTypeStruct<TYPE> {                                                                        \
        static constexpr const char *name = "CODE_TO_TENSORTYPE:" TYPENAME " " #TYPE;                                  \
    };                                                                                                                 \
    template <> constexpr const char *type_name<TYPE>() { return TYPENAME; }

#define DEFINE_TYPENAME_V(TYPE, TYPENAME)                                                                              \
    template <> constexpr const char *type_name<TYPE>() { return TYPENAME; }

/* use DEFINE_TYPENAME to define the typename for classes
e.g.
DEFINE_TYPENAME(MyTensor8, mt8);
DEFINE_TYPENAME(MyTensor16, mt16);
*/
// DEFINE_TYPENAME(int, int);
// DEFINE_TYPENAME(float, float);

// LCOV_EXCL_START [SAFTYSWCCB-1736] constexprs resolved during compile time
// used in /pub/impl/template_help.h with constexpr lvalue
template <typename T> constexpr void AddTypeNameSize(size_t &size)
{
    std::string_view const name = type_name<std::remove_cv_t<std::remove_reference_t<T>>>();
    size += 1; //add space for "." or "@"
    size += name.size();
}

template <typename... TYPES> constexpr size_t GetTypeNamesTotalSize()
{
    size_t size = 0;
    (AddTypeNameSize<TYPES>(size), ...);
    return size;
}

template <typename T> constexpr void AppendTypeName(char *des, size_t &offset, size_t &duplicate, size_t &left)
{
    left--;
    std::string_view const name = type_name<std::remove_cv_t<std::remove_reference_t<T>>>();
    size_t i = offset;
    bool same = false;
    if (offset != 0) { //if not the first name
        same = true;
        des[i++] = '.'; //add delimiter
        size_t const len = name.size();
        for (int j = 0; j < len; j++) {
            if (des[offset - 1 - j] != name[len - 1 - j]) {
                same = false;
                break;
            }
        }
        if (same && des[offset - len - 1] != '.' && des[offset - len - 1] != '@') {
            same = false;
        }
        if (same) duplicate += 1;
    } else
        des[i++] = '@';
    if (!same) {
        if (offset != 0) {
            if (duplicate > 1) {
                des[i - 1] = '*';
                if (duplicate >= 10) {
                    des[i++] = 48 + duplicate / 10;
                    des[i++] = 48 + duplicate % 10;
                } else {
                    des[i++] = 48 + duplicate;
                }
                des[i++] = '.';
            }
            duplicate = 1; //add delimiter
        }
        for (auto n : name)
            des[i++] = n;
        des[i] = 0;
        offset = i;
    }
    if (left == 0 && duplicate > 1) {
        des[i - 1] = '*';
        if (duplicate >= 10) {
            des[i++] = 48 + duplicate / 10;
            des[i++] = 48 + duplicate % 10;
        } else {
            des[i++] = 48 + duplicate;
        }
    }
}

template <typename... TYPES> constexpr auto GetTypeNames()
{
    std::array<char, GetTypeNamesTotalSize<TYPES...>() + 1> result{};
    char *des = result.data();
    size_t offset = 0;
    size_t duplicate = 1;
    size_t left = sizeof...(TYPES);
    (AppendTypeName<TYPES>(des, offset, duplicate, left), ...);
    return result;
}

template <typename TYPESTUPLE, std::size_t... I> constexpr auto GetTypeNames(std::index_sequence<I...>)
{
    std::array<char, GetTypeNamesTotalSize<std::tuple_element_t<I, TYPESTUPLE>...>() + 1> result{};
    char *const des = result.data();
    size_t offset = 0;
    size_t duplicate = 1;
    size_t left = sizeof...(I);
    (AppendTypeName<std::tuple_element_t<I, TYPESTUPLE>>(des, offset, duplicate, left), ...);
    return result;
}

// LCOV_EXCL_STOP

#endif
