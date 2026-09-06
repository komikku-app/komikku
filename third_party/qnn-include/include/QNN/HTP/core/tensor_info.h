//==============================================================================
//
// Copyright (c) 2023-2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef TENSOR_INFO_H
#define TENSOR_INFO_H 1

#include "opname_tag.h"

#include <optional>
#include <vector>
#include <limits>

namespace hnnx {

struct TensorInfo {
    using TensorFlags = unsigned; // treatad a a bit vector
    using NameSet = unsigned; // a bit vector of input and output names
            // outputs are the first "num_outputs" bits

    hnnx::opname_tag_t op_name;
    unsigned num_outputs = 1; // at least 1 "*" but can be longer

    NameSet fixed = 0; // names which do not change, roughly flat&main_memory
    NameSet is_crouton = 0;
    NameSet is_flat = 0;
    NameSet is_main_memory = 0;
    NameSet is_tcm = 0;
    NameSet prefer_tcm = 0; // place in tcm if they fit
    NameSet fixed_constants = 0;
    NameSet unaligned_ok = 0;
    NameSet crouton_if = 0; // assign crouton if any input name in this set is
    unsigned flat_above = std::numeric_limits<unsigned>::max();
    unsigned main_memory_above = std::numeric_limits<unsigned>::max();
    unsigned tcm_above = std::numeric_limits<unsigned>::max();
    unsigned crouton_above = std::numeric_limits<unsigned>::max();
    unsigned unaligned_ok_above = std::numeric_limits<unsigned>::max();

    std::vector<std::tuple<TensorFlags, hnnx::opname_tag_t>> renames;
    std::vector<std::pair<NameSet, opname_tag_t>> early_renames;
    // std::vector<std::pair<unsigned, Flags>> outputs;
};
} // namespace hnnx

namespace DefProperties {
enum class PropertyFlags {
    TCM = 1,
    MAIN_MEMORY = 2,
    CROUTON = 4,
    FLAT = 8,
    INVARIANT = 16,
};

constexpr PropertyFlags FLAT = PropertyFlags::FLAT;
constexpr PropertyFlags MAIN_MEMORY = PropertyFlags::MAIN_MEMORY;
constexpr PropertyFlags CROUTON = PropertyFlags::CROUTON;
constexpr PropertyFlags TCM = PropertyFlags::TCM;
constexpr PropertyFlags INVARIANT = PropertyFlags::INVARIANT;
constexpr unsigned OTHERWISE = 0;

// Rename(flag-list, new_name) if after migration all the specified
// flags are set, the operator is renamted to new_name
// Example: Rename(CROUTON,TCM, "Add.tcm")
struct Rename {
    unsigned flags = 0;
    const char *name = nullptr;
    inline void update() {}
    inline void update1(const PropertyFlags flag) { flags |= static_cast<unsigned>(flag); }
    inline void update1(const unsigned flag) { assert(flag == OTHERWISE); }
    inline void update1(const char *const name_p) { name = name_p; }
    template <typename First, typename... Rest> void update(First first, Rest... rest)
    {
        update1(first);
        update(rest...);
    }
    template <typename... Flags> explicit Rename(Flags... flags_) { update(flags_...); }
};

struct FlatAboveArg {
    unsigned position;
    FlatAboveArg(const unsigned position_) : position(position_) {}
};

struct StringList {
    std::vector<const char *> names;
    inline void update() {}
    inline void update1(const char *const name) { names.push_back(name); }
    template <typename First, typename... Rest> void update(First first, Rest... rest)
    {
        update1(first);
        update(rest...);
    }
    template <typename... Args> explicit StringList(Args... args) { update(args...); }
};

struct Op : public StringList {
    template <typename... Args> explicit Op(Args... args) : StringList(args...) {}
};
struct Outputs : public StringList {
    template <typename... Args> explicit Outputs(Args... args) : StringList(args...) {}
};
struct Fixed : public StringList {
    template <typename... Args> explicit Fixed(Args... args) : StringList(args...) {}
};
struct Crouton : public StringList {
    template <typename... Args> explicit Crouton(Args... args) : StringList(args...) {}
};
struct Flat : public StringList {
    template <typename... Args> explicit Flat(Args... args) : StringList(args...) {}
};
struct MainMemory : public StringList {
    template <typename... Args> explicit MainMemory(Args... args) : StringList(args...) {}
};
struct Tcm : public StringList {
    template <typename... Args> explicit Tcm(Args... args) : StringList(args...) {}
};
struct PreferTcm : public StringList {
    template <typename... Args> explicit PreferTcm(Args... args) : StringList(args...) {}
};

struct FixedConstant : public StringList {
    template <typename... Args> explicit FixedConstant(Args... args) : StringList(args...) {}
};

struct UnalignedOk : public StringList {
    template <typename... Args> explicit UnalignedOk(Args... args) : StringList(args...) {}
};
struct WhenFitsIfTCM : public StringList {
    template <typename... Args> explicit WhenFitsIfTCM(Args... args) : StringList(args...) {}
};
struct CroutonIf : public StringList {
    template <typename... Args> explicit CroutonIf(Args... args) : StringList(args...) {}
};

struct TilingOnly {
};

// An object that is used to build a TensorInfo from a variadic constructor clal
struct TensorInfoBuilder : public hnnx::TensorInfo {
    const char *package;
    std::vector<const char *> names;
    std::vector<const char *> output_names;
    bool tiling_only = false;
    // convert to flags and record elipsis position
    API_FUNC_EXPORT std::pair<unsigned, std::optional<unsigned>> nameset(const StringList &list,
                                                                         bool input_only = false);
    inline void update() {}

    // Old style, just the root name
    inline void update1(const char *const name)
    {
        assert(names.empty());
        op_name = name;
        names.push_back("*");
    }
    // new style Op(root_name, ...)
    inline void update1(Op names_p)
    {
        assert(names.empty());
        names = std::move(names_p.names);
        assert((std::all_of(names.begin(), names.end(), [](const char *name) { return strcmp(name, "...") != 0; })) and
               "Invalid name '...'");
        op_name = names[0];
        names[0] = "*";
    }
    inline void update1(Outputs names_p)
    {
        assert(output_names.empty());
        assert(not names_p.names.empty());
        num_outputs = names_p.names.size();
        output_names = std::move(names_p.names);
        assert((std::all_of(output_names.begin(), output_names.end(),
                            [](const char *name) { return strcmp(name, "...") != 0; })) and
               "Invalid name '...'");
    }
    inline void update_flags(unsigned flags_, unsigned &true_, const unsigned false_)
    {
        flags_ &= ~(true_ | false_ | fixed); // don't change a position alreay set
        true_ |= flags_;
    }
    inline void update1(const Fixed fixed_p)
    {
        const unsigned fixed_names = nameset(fixed_p).first;
        const unsigned really_fixed = fixed_names & ~(is_flat | is_crouton | is_tcm | prefer_tcm | is_main_memory);
        update_flags(fixed_names, is_flat, is_crouton);
        update_flags(fixed_names, is_main_memory, is_tcm | prefer_tcm);
        fixed |= really_fixed;
    }
    inline void update1(const PreferTcm operand_list)
    {
        prefer_tcm |= nameset(operand_list, /*input_only*/ true).first;
    }
    inline void update1(const Flat operand_list)
    {
        auto [f, elipsis] = nameset(operand_list);
        update_flags(f, is_flat, is_crouton);
        if (elipsis.has_value()) flat_above = *elipsis;
    }
    inline void update1(const Crouton operand_list)
    {
        auto [f, elipsis] = nameset(operand_list);
        update_flags(f, is_crouton, is_flat);
        if (elipsis.has_value()) crouton_above = *elipsis;
    }
    inline void update1(const MainMemory operand_list)
    {
        auto [f, elipsis] = nameset(operand_list);
        update_flags(f, is_main_memory, is_tcm | prefer_tcm);
        if (elipsis.has_value()) main_memory_above = *elipsis;
    }
    inline void update1(const Tcm operand_list)
    {
        auto [f, elipsis] = nameset(operand_list);
        update_flags(f, is_tcm, is_main_memory);
        if (elipsis.has_value()) tcm_above = *elipsis;
    }
    inline void update1(const UnalignedOk operand_list)
    {
        auto [f, elipsis] = nameset(operand_list);
        unaligned_ok |= f;
        if (elipsis.has_value()) unaligned_ok_above = *elipsis;
    }
    inline void update1(const FixedConstant operand_list)
    {
        const unsigned f = nameset(operand_list, /*input_only*/ true).first;
        fixed_constants |= f;
    }
    inline void update1(const CroutonIf operand_list)
    {
        const unsigned f = nameset(operand_list, /*input_only*/ true).first;
        crouton_if |= f;
    }
    inline void update1(const TilingOnly) { tiling_only = true; }
    API_FUNC_EXPORT void update1(Rename rename); // in tcm_migration.cc
    // void update1(Output output) { outputs.emplace_back(output.index, output.flags); }
    void update1(WhenFitsIfTCM rename);

    // void update1(NoPropagationToArgs stops_p) { stops |= stops_p.positions; }
    // void update1(NoPropagationAboveArg above) { stops |= 0xffff << (above.position + 1); }
    inline void update1(const FlatAboveArg flat_above_p) { flat_above = flat_above_p.position; }
    // inline void update1(const unsigned flag) { flags |= flag; }
    template <typename First, typename... Rest> void update(First first, Rest... rest)
    {
        update1(first);
        update(rest...);
        assert(not names.empty() and "No name specified for DEF_TENSOR_PROPERTIES");
    }

    template <typename... Args> TensorInfoBuilder(const char *package_, Args... args) : package(package_)
    {
        update(args...);
    }
    TensorInfoBuilder() = default;
};

} // namespace DefProperties

namespace hnnx {
// In tcm_migration.cc
extern API_FUNC_EXPORT bool register_tensor_properties(const char *package, DefProperties::TensorInfoBuilder);
} // namespace hnnx

#endif // TENSOR_INFO_H
