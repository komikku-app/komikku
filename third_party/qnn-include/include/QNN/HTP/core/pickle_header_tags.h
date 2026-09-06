//==============================================================================
//
// Copyright (c) 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef PICKLE_HEADER_TAGS_H_
#define PICKLE_HEADER_TAGS_H_
#include <algorithm> // std::min(...)

enum HTP_header_const {
    Hdr_MAGIC = 0x7309F72B,
    Hdr_MAGIC_MULTI = 0x3790FA5C, // magic # for a 'multi-pickle' main header
    HdrVersion_VERSION = 1,
    HdrVersion_GRAPH_PATCH_VERSION = 1,
    HdrVersion_VERSION_FLAG_MULTI_NSP = 0x8000, // 'or' to version in multi-pickle header.
    MULTI_SER_ALIGN = 4096, // all blobs in multi-pickle are padded out to multiple of this
    HdrTag_IDENT = 'I' + 256 * 'd',
    HdrTag_SIZE = 'S' + 256 * 'z',
    HdrTag_VERSION = 'V' + 256 * 'r',
    HdrTag_OPTIONS = 'O' + 256 * 'p',
    HdrTag_SHARES = 'W' + 256 * 's', // list of cbnames for weight-sharing
    HdrTag_MEMORY = 'M' + 256 * 'm', // 'memory' usage info
    HdrTag_CONTENTS = 'T' + 256 * 'c', // 'table of contents' in multi-pickle header.
    HdrTag_MULTI = 'M' + 256 * 'u', // size info for multi-pickle header
    HdrTag_IOSPEC = 'I' + 256 * 'o',
    HdrTag_EMPTY = 'E' + 256 * 'm',
    HdrTag_PATCH_METADATA = 0x2AFF5A2B,
    HdrTag_CONSTPOOL = 'C' + 256 * 'p',
    HdrTag_QUANT_PARAM_UPDATE = 'Q' + 256 * 'u',
    HdrTag_QUANT_PARAM_UPDATE_MC = 'Q' + 256 * 'm', // Quant param for multicore
    HdrTag_ENDHDR = 'Z' + 256 * 'z',

    // size of field, in bytes, specifying the names within the Sw tag
    CBNAME_LEN = 45,
    // size of field, in bytes, including final NULL, specifying the names within the Sw tag
    LEN_SHARED_BUFFER_NAME = CBNAME_LEN + 1,

    POOLDESC_IS_REPLACEABLE = 8, // const pool constructed as 'replaceable'
    POOLDESC_IS_DEHYDRATED = 64, // pool requires const graph rehydration
    POOLDESC_MC_CONST = 15, // represent Const data
};

#ifdef __cplusplus
extern "C" {
#endif

constexpr inline bool htp_header_is_valid_MAGIC(const unsigned val)
{
    return val == Hdr_MAGIC || val == Hdr_MAGIC_MULTI;
}
constexpr inline unsigned htp_header_get_MAGIC(void const *const p)
{
    return *(unsigned const *)p;
}

//
// Given a pointer to an in-memory header, locate the payload field corresponding to 'tag'.
// If found, returns the length of the payload (which is >=0), after setting *payload_ptr.
// If not found, returns -1.
//
//  hdr, buflen:  where the header is in memory.
//      'hdr' must be 32-bit aligned; will not access beyond' buflen' bytes.
//  tag:
//      the 16-bit tag you're looking for.
//
inline int htp_header_locate_field(const void *hdr, const size_t buflen, const unsigned tag, void **const payload_ptr)
{
    if (buflen < 12) return -1; // not large enough for any fields.
    const unsigned *rp = (const unsigned *)hdr;
    if (!htp_header_is_valid_MAGIC(*rp)) return -1;
    size_t const hwords = rp[1] & 0xFFFFu;
    size_t const max_hdr_words = std::min(hwords, buflen / 4);
    // do not look at this, or past.
    unsigned const *const limitp = rp + max_hdr_words;
    rp += 2; // point to first tag
    while (rp < limitp) {
        unsigned const recdesc = *rp;
        unsigned const rlen = recdesc & 0xFFFFu;
        if (rlen < 1 || rp + rlen > limitp) break; // bad record
        if ((recdesc >> 16u) == tag) { // found it...
            *payload_ptr = (void *)(rp + 1);
            return (rlen - 1) * sizeof(unsigned);
        }
        rp += rlen;
    }
    return -1;
}

#ifdef __cplusplus
} // extern "C"
#endif // __cplusplus

#endif // PICKLE_HEADER_TAGS_H_
