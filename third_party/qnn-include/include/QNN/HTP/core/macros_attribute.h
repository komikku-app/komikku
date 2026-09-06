//==============================================================================
//
// Copyright (c) 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef MACROS_MSVC_CPP17_H
#define MACROS_MSVC_CPP17_H

// Macros to compatible with MSVC and clang
#if defined(_MSC_VER)

// Macros to tells the compiler to never inline a particular member function
#define NOINLINE __declspec(noinline)
// Macros to force a function to be inlined, meaning that the function call is replaced with the function body at compile time
#define ALWAYSINLINE __forceinline

#ifndef API_EXPORT
//Macros to export symbol from a library
#define API_EXPORT __declspec(dllexport)
#endif
#ifndef API_HIDDEN
//Macros to hidden symbol from a library
#define API_HIDDEN

//Macros to tell the compiler that the function returns an object that is not aliased, that is, referenced by any other pointers.
#define RESTRICT_VAR __restrict

#endif
#else

#define NOINLINE     __attribute__((noinline))
#define ALWAYSINLINE __attribute((always_inline))

#ifndef API_EXPORT
/**
 * @brief The definition of API_EXPORT is commented out for
 * now, because it was causing problems with static analysis.
 * As an alternative, use the PUSH_VISIBILITY(default)/POP_VISIBILITY()
 * macros to ensure that symbols are exported on Linux.
*/
#define API_EXPORT /* [[gnu::visibility("default")]] */
#endif
#ifndef API_HIDDEN
#define API_HIDDEN [[gnu::visibility("hidden")]]
#endif

#define RESTRICT_VAR __restrict__

#endif // _MSC_VER

/**
 * @brief The following macros: [PUSH|POP|ENABLE|DISABLE]_WARNING,
 * allow for in-code enabing and disabling of compiler warnings in
 * a portable fashion.
*/
#define DO_PRAGMA(x) _Pragma(#x)

#define MSVC_NO_EQUIV
#define GNU_NO_EQUIV ""

#if defined(__clang__)
#define PUSH_WARNING()             DO_PRAGMA(clang diagnostic push)
#define POP_WARNING()              DO_PRAGMA(clang diagnostic pop)
#define ENABLE_WARNING(gnu, msvc)  DO_PRAGMA(clang diagnostic warning gnu)
#define DISABLE_WARNING(gnu, msvc) DO_PRAGMA(clang diagnostic ignored gnu)
#elif defined(__GNUG__)
#define PUSH_WARNING()             DO_PRAGMA(GCC diagnostic push)
#define POP_WARNING()              DO_PRAGMA(GCC diagnostic pop)
#define ENABLE_WARNING(gnu, msvc)  DO_PRAGMA(GCC diagnostic warning gnu)
#define DISABLE_WARNING(gnu, msvc) DO_PRAGMA(GCC diagnostic ignored gnu)
#elif defined(_MSC_VER)
#define PUSH_WARNING()             DO_PRAGMA(warning(push))
#define POP_WARNING()              DO_PRAGMA(warning(pop))
#define ENABLE_WARNING(gnu, msvc)  DO_PRAGMA(warning(default : msvc))
#define DISABLE_WARNING(gnu, msvc) DO_PRAGMA(warning(disable : msvc))
#else
#define PUSH_WARNING()
#define POP_WARNING()
#define ENABLE_WARNING(gnu, msvc)
#define DISABLE_WARNING(gnu, msvc)
#endif

#endif //MACROS_MSVC_H
