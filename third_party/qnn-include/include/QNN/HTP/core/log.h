//==============================================================================
//
// Copyright (c) 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef LOG_H
#define LOG_H 1

#include "weak_linkage.h"
#include "macros_attribute.h"
#include <cstdarg>
#include <string>
#include <chrono>
//#include <fmt/format.h>

#if !defined(__PRETTY_FUNCTION__) && !defined(__GNUC__)
#define __FUNC_INFO__ __FUNCSIG__
#else
#define __FUNC_INFO__ __PRETTY_FUNCTION__
#endif

// GCC and Clang define a preprocessor macro which is just the basename of the current file.
#if defined(__FILE_NAME__)
#define FILE_BASENAME __FILE_NAME__
#else

// MSVC doesn't have this nice feature, so we have to do it manually.  Note that the entire path
// still ends up in the .rodata section, unfortunately.

// Constexpr that will strip the path off of the file for logging purposes
constexpr char const *stripFilePath(const char *path)
{
    const char *file = path;
    while (*path) {
        if (*path++ == '/') {
            file = path;
        }
    }
    return file;
}

#define FILE_BASENAME stripFilePath(__FILE__)

#endif // defined(__FILE_NAME__)

#define STRINGIZE_DETAIL(X) #X
#define STRINGIZE(X)        STRINGIZE_DETAIL(X)

#include "graph_status.h"
#include "cc_pp.h"

#ifdef __cplusplus
#include <cstdio>
#else
#include <stdio.h>
#endif

// If log level or the dynamic logging flag are defined but don't have a value,
// then consider them to be undefined.
#if ~(~NN_LOG_MAXLVL + 0) == 0 && ~(~NN_LOG_MAXLVL + 1) == 1
#undef NN_LOG_MAXLVL
#endif

#if ~(~NN_LOG_DYNLVL + 0) == 0 && ~(~NN_LOG_DYNLVL + 1) == 1
#undef NN_LOG_DYNLVL
#endif

/*
 * We have migrated using C++ features like iostream to printf strings.
 * Why?
 * * C++ iostream makes it more difficult to use mixed decimal/hex
 * * C++ iostream isn't easily compatible with on-target logging facilities
 * * C++ iostream is bad for code size, printf is much better
 */

//Log levels macro
#define NN_LOG_ERRORLVL         0 //Error log level is 0
#define NN_LOG_WARNLVL          1 //Warning log level is 1
#define NN_LOG_STATLVL          2 //Stats log level is 2
#define NN_LOG_INFOLVL          3 //Info log level is 3
#define NN_LOG_VERBOSELVL       4 //Verbose log level is from 4-10
#define NN_LOG_STATLVL_INTERNAL 8
#define NN_LOG_INFOLVL_INTERNAL 9
#define NN_LOG_DEBUGLVL         11 //Debug log level is > 10

typedef void (*DspLogCallbackFunc)(int level, const char *fmt, va_list args);

// Dynamically set the logging priority level.
PUSH_VISIBILITY(default)
EXTERN_C_BEGIN
extern "C" {

API_FUNC_EXPORT void SetLogPriorityLevel(int level);
API_FUNC_EXPORT int GetLogPriorityLevel();
API_FUNC_EXPORT void SetLogCallbackFunc(DspLogCallbackFunc fn);
API_FUNC_EXPORT DspLogCallbackFunc GetLogCallbackFunc();

// This prevents preemption if we're using the TID preemption mechanism.
// Enable format checking when we're ready to fix all of the broken formats!
//[[gnu::format(printf, 1, 2)]]
API_FUNC_EXPORT void nn_log_printf(const char *fmt, ...);
}
EXTERN_C_END
POP_VISIBILITY()

#ifdef __cplusplus
extern "C" {
#endif

// special log message for x86 that will log regardless logging level
void qnndsp_x86_log(const char *fmt, ...);

#ifdef __cplusplus
}
#endif

#if defined(NN_LOG_DYNLVL) && (NN_LOG_DYNLVL > 0)

// Dynamic logging level test function.
static inline bool log_condition(const int prio)
{
    return (prio <= GetLogPriorityLevel());
};

#elif defined(NN_LOG_MAXLVL)

// Logging level is fixed at compile time.
static inline bool log_condition(const int prio)
{
    return ((prio <= NN_LOG_MAXLVL) ? true : false);
};

#else

// Logging is completely disabled.
constexpr static bool log_condition(const int prio)
{
    return false;
};

#endif

#ifdef ENABLE_QNNDSP_LOG

PUSH_VISIBILITY(default)
API_FUNC_EXPORT API_C_FUNC void API_FUNC_NAME(SetLogCallback)(DspLogCallbackFunc cbFn, int logPriority);

extern "C" {
API_FUNC_EXPORT void qnndsp_log(int prio, const char *FMT, ...);

API_FUNC_EXPORT void hv3_load_log_functions(decltype(SetLogCallback) **SetLogCallback_f);
}
POP_VISIBILITY()

#define MAKE_LOG_FMT_WITH_PREFIX(FMT, ...)                                                                             \
    "%s"                                                                                                               \
    ":" STRINGIZE(__LINE__) ":" FMT "\n",                                                                              \
            FILE_BASENAME, ##__VA_ARGS__

#define HV3_LOG(PRIO, FMT, ...) qnndsp_log(PRIO, FMT, ##__VA_ARGS__)

#else // ENABLE_QNNDSP_LOG

#define MAKE_LOG_FMT_WITH_PREFIX(FMT, ...) FILE_BASENAME ":" STRINGIZE(__LINE__) ":" FMT "\n", ##__VA_ARGS__
#define HV3_LOG(PRIO, FMT, ...)            nn_log_printf(FMT, ##__VA_ARGS__)

#endif // ENABLE_QNNDSP_LOG

// These are conditional, where the condition is set via compile flags.  Note that these are
// template functions so that we can exclude them from coverage using lcov commands.

template <typename... Types> inline void logmsgraw(const int prio, char const *fmt, Types... args)
{
    // LCOV_EXCL_START [SAFTYSWCCB-996]
    if (log_condition(prio)) {
        HV3_LOG(prio, fmt, args...);
    }
    // LCOV_EXCL_STOP
}

// These macros are what are used in actual code, so that the line and filename macros will expand
// properly to show where the macro is invoked.

#define _rawlog_(FMT, ...)  HV3_LOG(NN_LOG_ERRORLVL, FMT, ##__VA_ARGS__)
#define _okaylog_(FMT, ...) HV3_LOG(NN_LOG_ERRORLVL, MAKE_LOG_FMT_WITH_PREFIX(FMT, ##__VA_ARGS__))
#define _errlog_(FMT, ...)                                                                                             \
    HV3_LOG(NN_LOG_ERRORLVL, MAKE_LOG_FMT_WITH_PREFIX(":ERROR:" FMT, ##__VA_ARGS__)), GraphStatus::ErrorFatal
#define _logmsg_(PRIO, FMT, ...) logmsgraw(PRIO, MAKE_LOG_FMT_WITH_PREFIX(FMT, ##__VA_ARGS__))
#define _warnlog_(FMT, ...)      logmsgraw(NN_LOG_WARNLVL, MAKE_LOG_FMT_WITH_PREFIX("WARNING:" FMT, ##__VA_ARGS__))
#define _statlog_(statname, statvalue, dummy)                                                                          \
    logmsgraw(NN_LOG_STATLVL, MAKE_LOG_FMT_WITH_PREFIX("STAT: %s=%lld", statname, (long long)statvalue))
#define _i_statlog_(statname, statvalue, dummy)                                                                        \
    logmsgraw(NN_LOG_STATLVL_INTERNAL, MAKE_LOG_FMT_WITH_PREFIX("STAT: %s=%lld", statname, (long long)statvalue))
#define _statslog_(statname, statvalue, dummy)                                                                         \
    logmsgraw(NN_LOG_STATLVL, MAKE_LOG_FMT_WITH_PREFIX("STAT: %s=%s", statname, statvalue))
#define _i_statslog_(statname, statvalue, dummy)                                                                       \
    logmsgraw(NN_LOG_STATLVL_INTERNAL, MAKE_LOG_FMT_WITH_PREFIX("STAT: %s=%s", statname, (statvalue)))
#define _infolog_(FMT, ...)    logmsgraw(NN_LOG_INFOLVL, MAKE_LOG_FMT_WITH_PREFIX(FMT, ##__VA_ARGS__))
#define _i_infolog_(FMT, ...)  logmsgraw(NN_LOG_INFOLVL_INTERNAL, MAKE_LOG_FMT_WITH_PREFIX(FMT, ##__VA_ARGS__))
#define _debuglog_(FMT, ...)   logmsgraw(NN_LOG_DEBUGLVL, MAKE_LOG_FMT_WITH_PREFIX(FMT, ##__VA_ARGS__))
#define _verboselog_(FMT, ...) logmsgraw(NN_LOG_VERBOSELVL, MAKE_LOG_FMT_WITH_PREFIX(FMT, ##__VA_ARGS__))

template <class T> constexpr const char *format_type_check = "";

// This compile-time expression ensures that we always apply the -Wformat type-safety check.
#define FORMAT_TYPE_CHECK(...) (format_type_check<decltype(printf(__VA_ARGS__))>)

#define rawlog(...)       _rawlog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define okaylog(...)      _okaylog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define errlog(...)       _errlog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define logmsg(PRIO, ...) _logmsg_(PRIO, __VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define warnlog(...)      _warnlog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define statlog(...)      _statlog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define i_statlog(...)    _i_statlog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define statslog(...)     _statslog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define i_statslog(...)   _i_statslog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define infolog(...)      _infolog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define i_infolog(...)    _i_infolog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define _debuglog(...)    _debuglog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))
#define verboselog(...)   _verboselog_(__VA_ARGS__, FORMAT_TYPE_CHECK(__VA_ARGS__))

// Extra hook for debuglog.  This allows files to redefine it in order to add extra compile-time
// hooks for removing it.
#define debuglog(...) _debuglog(__VA_ARGS__)

//
// BCK:  Temporarily removing fmtlib logging due to QNN build issues.
//
#if 0
//
// These are logging variants which use fmtlib.
//

// Internal formatter function which sends data to stdout, FARF, etc.
void vlogmsg_fmt(fmt::string_view fmt, fmt::format_args args);

template <typename... T> inline void logmsg_fmt(const int prio, fmt::format_string<T...> fmt, T &&...args)
{
    // LCOV_EXCL_START [SAFTYSWCCB-996]
    if (log_condition(prio)) {
        vlogmsg_fmt(fmt, fmt::make_format_args(args...));
    }
    // LCOV_EXCL_STOP
}

#define errlogf(...)          logmsg_fmt(NN_LOG_ERRORLVL, "", MAKE_LOG_FMT_WITH_PREFIX(":ERROR:" FMT, ##__VA_ARGS__))
#define warnlogf(...)         logmsg_fmt(NN_LOG_WARNLVL, "", MAKE_LOG_FMT_WITH_PREFIX(":WARNING:" FMT, ##__VA_ARGS__))
#define infologf(FMT, ...)    logmsg_fmt(NN_LOG_INFOLVL, MAKE_LOG_FMT_WITH_PREFIX(FMT, ##__VA_ARGS__))
#define verboselogf(FMT, ...) logmsg_fmt(NN_LOG_VERBOSELVL, MAKE_LOG_FMT_WITH_PREFIX(FMT, ##__VA_ARGS__))
#define debuglogf(FMT, ...)   logmsg_fmt(NN_LOG_DEBUGLVL, "", MAKE_LOG_FMT_WITH_PREFIX(FMT, ##__VA_ARGS__))
#endif // 0

#ifdef NN_LOG_MAXLVL
#define LOG_STAT()    ((NN_LOG_MAXLVL) >= NN_LOG_STATLVL)
#define LOG_INFO()    ((NN_LOG_MAXLVL) >= NN_LOG_INFOLVL)
#define LOG_DEBUG()   ((NN_LOG_MAXLVL) >= NN_LOG_DEBUGLVL)
#define LOG_VERBOSE() ((NN_LOG_MAXLVL) >= NN_LOG_VERBOSELVL)
#else
#define LOG_STAT()    (1)
#define LOG_INFO()    (1)
#define LOG_DEBUG()   (1)
#define LOG_VERBOSE() (1)
#endif //#ifdef NN_LOG_MAXLVL

class ExternalProgressLogger {

  public:
    static void start(const char *stage_name);

    static void update_progress(unsigned int numerator, unsigned int denominator);

    static void end(const char *stage_name, const char *duration);
};

class ExternalTimePoint {
    using TimePoint = std::chrono::high_resolution_clock::time_point;
    const std::string stage_name;
    const TimePoint start_time;
    unsigned int numerator = 1;
    unsigned int denominator = 1;
    bool done = false;

  public:
    explicit ExternalTimePoint(const std::string &&stage_name);

    void update_progress(unsigned int new_numerator, unsigned int new_denominator);

    void close();

    // Custom destructor
    ExternalTimePoint() = delete;
    ExternalTimePoint(const ExternalTimePoint &) = delete;
    ExternalTimePoint &operator=(ExternalTimePoint &t) = delete;
    ExternalTimePoint(ExternalTimePoint &&) = delete;
    ExternalTimePoint &operator=(ExternalTimePoint &&t) = delete;
    ~ExternalTimePoint() { close(); } // LCOV_EXCL_LINE [SAFTYSWCCB-1542]
};

#endif //#ifndef LOG_H
