//==============================================================================
//
// Copyright (c) 2020, 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OP_PACKAGE_FEATURE_SUPPORT_H
#define OP_PACKAGE_FEATURE_SUPPORT_H

/*
 * Used by external op packages
 * for specifying orders of op parameters
 * and listing axis parameters
 * and listing per channel scale ops
 *
 * Using any of the following features/macros on HTP default package is invalid
 * For any op package using HTP (internal) default package name,
 *   axis parameters listed using macros below will be ignored
 *   if there are parameter orders and/or per-channel ops listed using macros below, op package registration will fail
 */

#include <string>
#include <string_view>
#include <set>
#include <vector>
#include <unordered_map>
#include <cstdarg>

#include "op_package_name.h"
#include "weak_linkage.h"
#include "macros_attribute.h"

PUSH_VISIBILITY(default)

namespace hnnx {

// configuration of each op package op parameter
typedef struct {
    std::string name;
    bool isMandatory;
    void *defaultVal;

} ParamConfig_t;

typedef std::unordered_map<std::string, std::vector<ParamConfig_t>> ParamMap_t; // pkg::op name -> vector<param>

API_EXPORT API_C_FUNC std::string API_FUNC_NAME(combine_pkg_op_name)(const char *package_name, const char *op_name);

/*
 * adds a new op entry into an op parameter map
 * returns nullptr if this op already exists in the map
 */
API_EXPORT std::vector<ParamConfig_t> *add_to_param_map(ParamMap_t &pmap, std::string_view package_op_name);

// adds a new op parameter entry into a vector of parameter configs
API_EXPORT void add_package_individual_param_config(std::vector<ParamConfig_t> *pvec, const char *param_name,
                                                    bool mandatory, void *default_val);

// base conditon for hnnx::add_package_param_configs_base
API_EXPORT void add_package_param_configs_base(std::vector<ParamConfig_t> *pvec);

// adds a variable number of ParamConfig_t constructed from [param_name, mandatory, default_val] to a ParamConfig_t vector
template <typename... T>
API_EXPORT void add_package_param_configs_base(std::vector<ParamConfig_t> *pvec, const char *param_name, bool mandatory,
                                               void *default_val, T &&...args)
{
    add_package_individual_param_config(pvec, param_name, mandatory, default_val);
    add_package_param_configs_base(pvec, std::forward<T>(args)...);
}

// inserts a new  op entry into a ParamMap_t and adds a variable number of ParamConfig_t
template <typename... T>
API_EXPORT void add_package_param_configs(ParamMap_t &pmap, std::string_view package_op_name, const char *param_name,
                                          bool mandatory, void *default_val, T &&...args)
{
    std::vector<ParamConfig_t> *v = add_to_param_map(pmap, package_op_name);
    if (!v) return;

    add_package_individual_param_config(v, param_name, mandatory, default_val);
    add_package_param_configs_base(v, std::forward<T>(args)...);
}

//  base conditon for hnnx::add_package_axis_params
API_EXPORT void add_package_axis_params(std::set<std::string> &aset);

// adds a variable number of axis parameter names into a set of axis parameter names
template <typename... T>
API_EXPORT void add_package_axis_params(std::set<std::string> &aset, const char *param_name, T &&...args)
{
    aset.insert(std::string(param_name));
    add_package_axis_params(aset, std::forward<T>(args)...);
}

//  base conditon for hnnx::add_package_per_channel_ops
API_EXPORT void add_package_per_channel_ops(std::set<std::string> &oset);

// adds a variable number of per-channel scaled package_name::op_name into a set of package_name::op_name
template <typename... T>
API_EXPORT void add_package_per_channel_ops(std::set<std::string> &oset, const char *op_name, T &&...args)
{
    oset.insert(combine_pkg_op_name(THIS_PKG_NAME_STR, op_name));
    add_package_per_channel_ops(oset, std::forward<T>(args)...);
}

} // namespace hnnx

// Initialize ParamAxes and ChannelQuantizedOps maps as well
#define INIT_PACKAGE_PARAM_ORDER_DEF()                                                                                 \
    API_HIDDEN hnnx::ParamMap_t &current_package_param_order_storage_map_func()                                        \
    {                                                                                                                  \
        static hnnx::ParamMap_t pm;                                                                                    \
        return pm;                                                                                                     \
    }                                                                                                                  \
    API_HIDDEN std::set<std::string> &currentPackageParamAxesSetFunc()                                                 \
    {                                                                                                                  \
        static std::set<std::string> axes;                                                                             \
        return axes;                                                                                                   \
    }                                                                                                                  \
    API_HIDDEN std::set<std::string> &currentPackagePerChannelQuantizedOpsSetFunc()                                    \
    {                                                                                                                  \
        static std::set<std::string> per_channel_ops;                                                                  \
        return per_channel_ops;                                                                                        \
    }                                                                                                                  \
    extern "C" {                                                                                                       \
    void clearPackageParamOrderStorageMapFunc() { current_package_param_order_storage_map_func().clear(); }            \
    void clearPackageParamAxesSetFunc() { currentPackageParamAxesSetFunc().clear(); }                                  \
    void clearPackagePerChannelQuantizedOpsSetFunc() { currentPackagePerChannelQuantizedOpsSetFunc().clear(); }        \
    }                                                                                                                  \
    std::unordered_map<std::string, hnnx::ParamMap_t *> &getPkgParamTmpMap();                                          \
    std::unordered_map<std::string, std::set<std::string> *> &getPkgParamAxesTmpMap();                                 \
    std::unordered_map<std::string, std::set<std::string> *> &getPkgPerChannelOpsTmpMap();                             \
    void clearPkgStorage()                                                                                             \
    {                                                                                                                  \
        clearPackageOpsStorageVecFunc();                                                                               \
        clearPackageOptStorageVecFunc();                                                                               \
        clearPackageParamOrderStorageMapFunc();                                                                        \
        clearPackageParamAxesSetFunc();                                                                                \
        clearPackagePerChannelQuantizedOpsSetFunc();                                                                   \
    }

#define DECLARE_PACKAGE_PARAM_ORDER_DEF() API_HIDDEN hnnx::ParamMap_t &current_package_param_order_storage_map_func();

#define DEF_PACKAGE_PARAM_ORDER(OP, PARAM1, MANDATORY1, DEFAULT1, ...)                                                 \
    [[maybe_unused]] static bool CTRICKS_PASTER(_PKG_PARAM_ORDER_REG_, __LINE__) =                                     \
            (hnnx::add_package_param_configs(current_package_param_order_storage_map_func(),                           \
                                             hnnx::combine_pkg_op_name(THIS_PKG_NAME_STR, OP), PARAM1, MANDATORY1,     \
                                             DEFAULT1, ##__VA_ARGS__),                                                 \
             true);

// clean all op_pkg storage during process exit
#define REGISTER_PACKAGE_PARAM_ORDERS()                                                                                \
    if (getPkgParamTmpMap().find(std::string(THIS_PKG_NAME_STR)) == getPkgParamTmpMap().end())                         \
        getPkgParamTmpMap()[std::string(THIS_PKG_NAME_STR)] = &current_package_param_order_storage_map_func();

#define LIST_PACKAGE_AXIS_PARAMS(...)                                                                                  \
    [[maybe_unused]] static bool CTRICKS_PASTER(_PKG_AXIS_PARAMS_, __LINE__) =                                         \
            (hnnx::add_package_axis_params(currentPackageParamAxesSetFunc(), ##__VA_ARGS__), true);

#define REGISTER_PACKAGE_AXIS_PARAMS()                                                                                 \
    if (getPkgParamAxesTmpMap().find(std::string(THIS_PKG_NAME_STR)) == getPkgParamAxesTmpMap().end())                 \
        getPkgParamAxesTmpMap()[std::string(THIS_PKG_NAME_STR)] = &currentPackageParamAxesSetFunc();

#define LIST_PACKAGE_PER_CHANNEL_QUANTIZED_OPS(...)                                                                    \
    [[maybe_unused]] static bool CTRICKS_PASTER(_PKG_PER_CHANNEL_OPS_, __LINE__) =                                     \
            (hnnx::add_package_per_channel_ops(currentPackagePerChannelQuantizedOpsSetFunc(), ##__VA_ARGS__), true);

#define REGISTER_PACKAGE_PER_CHANNEL_QUANTIZED_OPS()                                                                   \
    if (getPkgPerChannelOpsTmpMap().find(std::string(THIS_PKG_NAME_STR)) == getPkgPerChannelOpsTmpMap().end())         \
        getPkgPerChannelOpsTmpMap()[std::string(THIS_PKG_NAME_STR)] = &currentPackagePerChannelQuantizedOpsSetFunc();

DECLARE_PACKAGE_PARAM_ORDER_DEF()

POP_VISIBILITY()

#endif // OP_PACKAGE_FEATURE_SUPPORT_H
