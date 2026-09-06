//==============================================================================
//
// Copyright (c) 2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef CONST_EXTENT_SHARED_H_
#define CONST_EXTENT_SHARED_H_

namespace hnnx {
// definitions pertaining to the 'const extent descriptor'.

constexpr unsigned CONST_EXTENT_DESC_MAGIC = 0x71c43c9b;
// if a const extent descriptor has a 'cbname' in it, the last 32-bit slot
// is this value. The 0x3e, 0x00 is the ">\0" at the end of the cbname
constexpr unsigned CONST_EXTENT_CBNAME_TAG = 0xebbe003e;

// This must be a power of 2, and >= 64.
// This is effectively a 'quiet' minimum on options.serialize_const_alignment, which sets
// the actual alignment.
// It is not necessary for the decoder to know what value of alignment was used in the encoder.
constexpr unsigned CONST_EXTENT_MIN_ALIGN = 256;
//
// this is a (non-quiet) maximum on options.serialize_const_alignment
constexpr unsigned CONST_EXTENT_MAX_ALIGN = 1024 * 1024;

///
/// @brief Size of const extent descriptor header
///
constexpr unsigned CONST_EXTENT_HEADER_SIZE_WORDS = 4u;
constexpr unsigned CONST_EXTENT_HEADER_SIZE_BYTES = CONST_EXTENT_HEADER_SIZE_WORDS * 4u;

///
/// @brief Size of an extent record
/// @details Const extent descriptor contains a table of such records
///
constexpr unsigned CONST_EXTENT_RECORD_SIZE_WORDS = 4u;
constexpr unsigned CONST_EXTENT_RECORD_SIZE_BYTES = CONST_EXTENT_RECORD_SIZE_WORDS * 4u;

///
/// @brief Offset of extent record table relative to const extent descriptor
/// @details Both byte and words offsets are listed
///
constexpr unsigned CONST_EXTENT_RECORD_TAB_OFFSET_WORDS = 4u;
constexpr unsigned CONST_EXTENT_RECORD_TAB_OFFSET_BYTES = CONST_EXTENT_RECORD_TAB_OFFSET_WORDS * 4u;

///
/// @brief Size of mempool record in a const extent descriptor
/// @details Both byte and word sizes are provided
///
constexpr unsigned CONST_EXTENT_MEMPOOL_RECORD_SIZE_WORDS = 4u;
constexpr unsigned CONST_EXTENT_MEMPOOL_RECORD_SIZE_BYTES = CONST_EXTENT_MEMPOOL_RECORD_SIZE_WORDS * 4u;

// This function is used by deserializer to help it extract the extent-desc table (as a vector<uint32_t>) from some
// arbitrary point down the pickle. Parameter is a pointer to the first 4 words; the return value is
//  0 if the first two words do not look like CEDesc header;
//  n otherwise (where 'n' is the number of 32-bit words to extract).
//
inline unsigned const_extent_hdr_check(uint32_t const *const hdrp)
{
    if (hdrp[0] != CONST_EXTENT_DESC_MAGIC) return 0;
    const unsigned word0 = hdrp[1];
    const unsigned hdr_len16 = word0 >> 24u; // units of 16 bytes
    const unsigned desc_len64 = word0 & 0xFFFFFFu; // units of 64 bytes
    const unsigned n_extent = hdrp[2] & 0xFFFFFFu;
    const unsigned n_mempool = hdrp[3] & 0xFFFFFFu;
    // no. of words actually needed
    const unsigned desc_words = 4 * (hdr_len16 + n_extent + n_mempool);

    // note, n_extent == n_mempool == 0 is allowed.
    if (hdr_len16 == 0 || desc_len64 == 0 || n_extent > n_mempool || desc_words > desc_len64 * 16) {
        return -1;
    }
    return desc_words;
}

} // namespace hnnx

#endif
