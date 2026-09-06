//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef SERIALIZE_DEFS_H
#define SERIALIZE_DEFS_H 1

#include <cstdint>
#include <typeinfo>

// General class of op: foreground for main thread, vec for HVX thread(s), mtx
// for HMX thread(s).
enum OpStoreType {
    OpStoreFg,
    OpStoreVec, // HVX (vector)
    OpStoreMtx, // HMX (matrix)
    OpStoreElt, // HLX (element-wise long vector)
};

class Op;

namespace hnnx {

class Serializer;
class Deserializer;
class Deserz;

/**
 * @brief Common base to register error in Serialize/Deserialize
 * Calling register_error sets the error string (unless there is one already).
 * any_error() should be checked after each full use of serialize/deserialize
 * Note: register_error must be called with a string constant, or other
 * persistent string, since the contents of the string are not copied.
 *
 */
class DeSerError {
    char const *errstr = nullptr; // null if no error
  public:
    void reset_error() { errstr = nullptr; }
    bool any_error() const { return errstr != nullptr; }
    void register_error(char const *estr)
    { // must be a persistent string!
        if (errstr == nullptr) errstr = estr;
    }
    char const *error_string() const { return errstr; }
};

// We allow 4 bits of extra flag storage when storing an Op type, using
// the upper four bits of the index.
constexpr uint32_t SerializeOpFlagMask = 0xf0000000u;
constexpr uint32_t SerializeOpFlagShift = 28u;

void op_serialize_common(Serializer &sctx, Op const *op, std::type_info const *actual_type = nullptr);

static constexpr unsigned OP_SEQNO_MARKER_XOR = 0x1303ee71u;
static constexpr unsigned OP_SEQNO_MARKER_MASK = 0x1FFFFFFFu; // upper 3 bits reserved for flags.
static constexpr unsigned OP_SEQNO_PRELOAD_FLAG = 0x80000000u;
// if this bit is set in the sequence word, it means one or more 'extended attribute'
// words follow.
static constexpr unsigned OP_SEQNO_EXTATTR_FLAG = 0x40000000u;
// The general format for 'extended attribute' word is;
//  bits 30..24 tell you what it means, and
//   bit 31 tells you it's not the last one.
static constexpr unsigned OP_EXTATTR_SELF_SLICING = 0x1; // 8 LSBs = # of slices (>=2)

// bits 23..0 points to the index of predicate conditions
// bits 30..24 tell you what it means
// bit 31 tell you that you are are not the last one
static constexpr unsigned OP_EXTATTR_PREDICATE = 0x2;
// Getting the last 24 bits. It contains the predicate offset and the sense
static constexpr unsigned OP_PRED_OFFSET_SENSE_MASK = 0x00ffffff;

} // namespace hnnx

#endif // SERIALIZE_DEFS_H
