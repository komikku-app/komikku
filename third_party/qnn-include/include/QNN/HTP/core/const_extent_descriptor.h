//==============================================================================
//
// Copyright (c) 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef CONST_EXTENT_DESCRIPTOR_H
#define CONST_EXTENT_DESCRIPTOR_H 1

#include <cstdio>
#include <vector>
#include <cassert>
#include <string>
#include "forward_classes.h"
#include "serialize_defs.h"
#include "pickle_header_tags.h"
#include "const_extent_shared.h"

namespace hnnx {

// This class is used, on both encoder and decoder, to contain a 'const extent descriptor' in its raw form, (just an array of uint32)
// and provide higher-level access to the contents.

class ConstExtentDesc {
  protected:
    using table_t = std::vector<uint32_t>;
    // The 'table' may or may not contain the 'padding' section at the end; this is not accessed,
    // and the serialize method will always generate the required padding.
    table_t table;
    // some values broken out from the header...
    unsigned extab_n = 0, extab_idx = 0; // number of extents, and word index where they start
    unsigned mptab_n = 0, mptab_idx = 0; // number of memory pools, and word index where they start.
    unsigned desc_len = 0; // length of the entire descriptor in bytes (0 if invalid descriptor)

    bool scan_table(); // sanity check, and unpacks the above; returns true if OK.

  public:
    ///
    /// @brief Header
    /// @details Composition of header of constant extent section ...
    ///
    ///     33222222 22221111 111111
    ///     10987654 32109876 54321098 76543210
    ///    +--------+--------+--------+--------+
    ///    |              magic                | 0
    ///    +--------+--------+--------+--------+
    ///    |hlen/4W |      desc_len/64B        | 1
    ///    +--------+--------+--------+--------+
    ///    |reserved|  flags |   num_extents   | 2
    ///    +--------+--------+--------+--------+
    ///    |reserved|     num_mempools         | 3
    ///    +--------+--------------------------+
    ///

    ///
    /// @brief LSB and width of various bitfields in header
    /// @warning It MUST MATCH the ASCII art of the header above!
    ///
    static size_t constexpr HEADER_DESC_LEN_BITFIELD_LSB = 0u;
    static size_t constexpr HEADER_DESC_LEN_BITFIELD_WIDTH = 24u;
    static size_t constexpr HEADER_LEN_BITFIELD_LSB = 24u;
    static size_t constexpr HEADER_LEN_BITFIELD_WIDTH = 8u;
    static size_t constexpr HEADER_NUM_EXTENTS_BITFIELD_LSB = 0u;
    static size_t constexpr HEADER_NUM_EXTENTS_BITFIELD_WIDTH = 16u;
    static size_t constexpr HEADER_FLAGS_BITFIELD_LSB = 16u;
    static size_t constexpr HEADER_FLAGS_BITFIELD_WIDTH = 8u;
    static size_t constexpr HEADER_NUM_MEMPOOLS_BITFIELD_LSB = 0u;
    static size_t constexpr HEADER_NUM_MEMPOOLS_BITFIELD_WIDTH = 24u;

    ///
    /// @brief Values for 8b flags in constant extent header
    ///
    static uint8_t constexpr HEADER_FLAG_RESERVED_0 = (1 << 0);
    static uint8_t constexpr HEADER_FLAG_RESERVED_1 = (1 << 1);
    static uint8_t constexpr HEADER_FLAG_RESERVED_2 = (1 << 2);
    static uint8_t constexpr HEADER_FLAG_IS_REPLACEABLE = (1 << 3); ///< Contents are replaceable weights
    static uint8_t constexpr HEADER_FLAG_IS_FAR_HINT = (1 << 4); ///< Contents maybe far
    static uint8_t constexpr HEADER_FLAG_RESERVED_5 = (1 << 5);
    static uint8_t constexpr HEADER_FLAG_RESERVED_6 = (1 << 6);
    static uint8_t constexpr HEADER_FLAG_RESERVED_7 = (1 << 7);

    static uint8_t constexpr EXTENT_FLAGS_BITFIELD_LSB = 8u;
    static uint8_t constexpr EXTENT_FLAGS_BITFIELD_WIDTH = 8u;

    ///
    /// @brief Values for 8b flags in extent record
    ///
    static uint8_t constexpr EXTENT_FLAG_RESERVED_0 = (1 << 0);
    static uint8_t constexpr EXTENT_FLAG_RESERVED_1 = (1 << 1);
    static uint8_t constexpr EXTENT_FLAG_RESERVED_2 = (1 << 2);
    static uint8_t constexpr EXTENT_FLAG_RESERVED_3 = (1 << 3);
    static uint8_t constexpr EXTENT_FLAG_IS_FAR_HINT = (1 << 4); ///< Contents maybe far
    static uint8_t constexpr EXTENT_FLAG_RESERVED_5 = (1 << 5);
    static uint8_t constexpr EXTENT_FLAG_RESERVED_6 = (1 << 6);
    static uint8_t constexpr EXTENT_FLAG_RESERVED_7 = (1 << 7);

    // Return from 'extent_info'.
    struct extab_entry {
        uint32_t extent_flags;
        uint32_t align; // a power of 2, >= 64
        uint64_t offset; // offset, in bytes, from the start of the descriptor, to where the data is.
        uint64_t length; // length of the data in bytes.
    };
    // Return from 'mempool_info'.
    // Note: if 'adjust_offset' is true, the 'offset' field from the containing extent will be added to offset,
    // so that the offset is from the start of the descriptor, instead of the start of the containing extent.
    struct mempool_entry {
        uint32_t mempool_id; // a mempool id >=2 indicating a const mempool
        uint32_t extent_id; // an extent_id, >=1
        uint64_t offset; // offset in bytes of the data from the start of the extent (see note above)
        uint64_t length; // length in bytes of the data
    };
    // optional name of the const_extent this descriptor corresponds to. Used for matching in weight_sharing.
    std::string name = std::string{};

    ///
    /// @brief Various options for adjusting offset in mempool_info()
    ///
    enum offset_adjust_t {
        OFFSET_ADJUST_DESC_REL = 0, ///< Adjust offset relative to descriptor (default)
        OFFSET_ADJUST_FALSE =
                OFFSET_ADJUST_DESC_REL, ///< Alias to descriptor-relative address (default) - i.e. dont adjust
        OFFSET_ADJUST_EXTENT_REL = 1, ///< Adjust offset relative to extent
        OFFSET_ADJUST_TRUE = OFFSET_ADJUST_EXTENT_REL, ///< Alias to extent-relative offset - i.e. adjust
        OFFSET_ADJUST_IF_FAR, ///< Offset relative to extent if containing extent is far
    };

    ConstExtentDesc() {}
    ConstExtentDesc(table_t &&table_in);
    void serialize(Serializer &) const;
    inline bool load_table(table_t &&table_in)
    {
        table = std::move(table_in);
        return scan_table();
    }

    constexpr bool is_valid() const { return desc_len != 0; }

    constexpr unsigned descriptor_length() const { return desc_len; }

    constexpr unsigned num_extents() const { return extab_n; }
    constexpr unsigned num_mempools() const { return mptab_n; }

    // unpack a row of the extent table
    // NOTE: extent_id is 1-based, must be 1 .. num_extents()
    extab_entry extent_info(unsigned extent_id) const;

    ///
    /// @brief Get/unpack a mempool entry from mempool table in this constant extent
    /// descriptor
    /// @param [in] idx ID (1-based!) of the mempool entry to get. It is expected
    /// to be in range [1...num_mempools()]
    /// @param [in] adjust_offset Option to adjust offset
    /// @return Valid mempool entry
    ///
    mempool_entry mempool_info(unsigned idx, offset_adjust_t adjust_offset = OFFSET_ADJUST_FALSE) const;

    // The ordering of the data and the descriptors is such that:
    //
    // (1)  extent_info(1).offset >= descriptor_length()
    //      mempool_info(1,true).offset >= descriptor_length()
    // (2) for i >=2,
    //      extent_info(i).offset >= extent_info(i+1).offset + extent_info(i+1).length
    //      mempool_info(i,true).offset >= mempool_info(1-1,true).offset + mempool_info(1-1).length
    //

#if !defined(PREPARE_DISABLED)
    ///
    /// @brief Memory pool record iterator
    /// @details Use to iterator over records in memory pool table in constant
    /// extent descriptor
    ///
    class mempool_iterator {
      public:
        using iterator_category = std::input_iterator_tag;
        using value_type = ConstExtentDesc::mempool_entry;
        using difference_type = std::ptrdiff_t;
        using pointer = value_type *;
        using reference = value_type &;

        ///
        /// @brief Constructor
        /// @param [in] cedesc A valid constant extent descriptor instance
        /// @param [in] index Record index (zero-based!)
        ///
        explicit mempool_iterator(ConstExtentDesc const &cedesc, uint32_t index) : _cedesc(cedesc), _index(index) {}

        ///
        /// @brief Increment record
        /// @return Iterator
        ///
        mempool_iterator &operator++()
        {
            // Increment IFF valid constant extent descriptor and mempool record
            // index within range
            _index += (_cedesc.is_valid() && (_index < _cedesc.mptab_n)) ? 1 : 0;
            return *this;
        }

        ///
        /// @brief Equality operator
        /// @return true if iterators are equal
        ///
        bool operator==(mempool_iterator const &other) const { return _index == other._index; }

        ///
        /// @brief Inequality operator
        /// @return true if iterators are not equal
        ///
        bool operator!=(mempool_iterator const &other) const { return !(*this == other); }

        ///
        /// @brief Dereference iterator
        ///
        reference operator*();

      private:
        ///
        /// @brief Reference to a constant extent descriptor instance
        /// @details It contains the blob representing constant extent segment
        ///
        ConstExtentDesc const &_cedesc;

        ///
        /// @brief Current index
        ///
        uint32_t _index;

        ///
        /// @brief Mempool record entry
        /// @details It is assigned when on iterator dereference
        ///
        value_type _entry;
    };

    ///
    /// @brief Return mempool iterator initialized to the first record
    /// @return Mempool iterator
    ///
    mempool_iterator begin() { return mempool_iterator(*this, 0); }

    ///
    /// @brief Return mempool iterator beyond the last record
    /// @warning Intended to be used as a sentinel
    /// @return Mempool iterator
    ///
    mempool_iterator end() { return mempool_iterator(*this, mptab_n); }
#endif
};
#ifndef PREPARE_DISABLED
// Called at the end of serializing a graph, if 'const extent' mode is enabled.
// See comment in const_extent_descriptor.cc for full details.
// LCOV_EXCL_START [SAFTYSWCCB-1542]
size_t write_aligned_const_info(Graph const &gr, Serializer &sctx, unsigned buried_aux_n_words = 0);
#else
inline constexpr size_t write_aligned_const_info(Graph const &gr, Serializer const &sctx, unsigned = 0)
{
    return 0;
}
// LCOV_EXCL_STOP
#endif

} // namespace hnnx

#endif // CONST_EXTENT_DESCRIPTOR_H
