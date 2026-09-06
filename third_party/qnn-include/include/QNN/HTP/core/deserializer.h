//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef DESERIALIZER_H
#define DESERIALIZER_H 1

#include <cstdio>
#include <cassert>
#include <cstring>
#include <memory>
#include <string>
#include <tuple>
#include <type_traits>
#include <array>
#include <vector>
#include <map>
#include <typeinfo>
#include <typeindex>
#include <string_view>
#include "limits.h"
#include "dtype.h"
#include "log.h"
#include "allocator.h"
#include "op_extra_info.h"

#include "serialize_defs.h"
#include "forward_classes.h"
#include "deserialize_tensors.h"
#include "macros_attribute.h"
#include "const_extent_descriptor.h"
#include "weak_linkage.h"
#include "size_align_code.h"
#include "deser_concurrent.h"
#include "hexagon_nn_types.h"
#include "conditional_default_deleter.h"

namespace hnnx {
class DMA_Manager;
class Crate;
/**
 * @brief \ref Serializer and \ref Deserializer modules that provides
 * a mechanism to flatten (serialize) and reconstruct (deserialize)
 * primitive and user-defined data types. The initial objective
 * was to create an in-memory representation of the optimized
 * \ref Graph on x86 which can then be reconstructed and executed on
 * a qdsp target, essentially, a means to Graph caching.
 *
 */
using tensor_deserializer_fn = uptr_Tensor (*)(Deserz &);

using deserialize_op_func = void *(*)(void *, Deserz &); // Allocation function
using deserialize_dtor_func = void (*)(Graph *, void *); // Deallocation function
class SimpleOpBase;
using deserialize_make_unique = std::unique_ptr<SimpleOpBase> (*)();

struct op_deserializer_fn {
    op_deserializer_fn(deserialize_op_func init_func_in, const size_align_code_t sizeal_in)
        : init_func(init_func_in), size_align_code(sizeal_in)
    {
    }
    op_deserializer_fn(deserialize_op_func init_func_in, deserialize_dtor_func dtor_func_in,
                       const size_align_code_t sizeal_in)
        : dtor_func(dtor_func_in), init_func(init_func_in), size_align_code(sizeal_in){};
    op_deserializer_fn(const op_deserializer_fn &) = default;
    op_deserializer_fn(op_deserializer_fn &&) = default;
    op_deserializer_fn &operator=(const op_deserializer_fn &) = delete;
    deserialize_dtor_func dtor_func = nullptr;
    deserialize_op_func init_func = nullptr;
    const size_align_code_t size_align_code{};
    inline constexpr size_t get_size() const { return size_align_code.size(); }
    inline constexpr size_t get_align() const { return size_align_code.align(); }
};

// here's a quick and dirty way to make these maps go faster: compare string_view starting with len;
// and if the len is the same, then compare the middle character, and if that's the same,
// use memcmp. This avoids getting slowed down by a lot of long common prefixes in the type names.
// and we don't care about the weird ordering it generates.
//
struct trick_stringview_lt {
    bool operator()(std::string_view const &a, std::string_view const &b) const
    {
        unsigned const na = a.size();
        unsigned const nb = b.size();
        if (na != nb) return na < nb;
        char const *const pa = a.data();
        char const *const pb = b.data();
        if (pa == pb || na == 0) return false; // pa==pb is a  common case.
        unsigned const char_a = pa[na >> 1];
        unsigned const char_b = pb[na >> 1];
        if (char_a != char_b) return char_a < char_b;
        return ::memcmp(pa, pb, na) < 0;
    }
};

using op_deserializer_map_t = std::map<std::string_view, std::pair<op_deserializer_fn, bool>, trick_stringview_lt>;
using op_filename_map_t = std::map<std::string_view, std::string_view>;
using tensor_deserializer_map_t = std::map<std::string_view, tensor_deserializer_fn, trick_stringview_lt>;
using cexdesc_deserializer_map = std::map<std::string, ConstExtentDesc>;

using const_extent_t = std::pair<hexagon_nn_wide_address_t, uint64_t>;
using weight_buf_deserializer_map = std::map<std::string, const_extent_t>;

/**
 * @brief Deserializer class to reverse the serialization
 * process and reconstruct the data for specific types
 *
 */
class Deserz : public DeSerError {
    friend class Deserializer; // weirdly, sometimes a derived class needs to be a friend.
    friend class DeserTensorConn;

  protected:
    Deserz(Deserializer *full_deser, char const *p, size_t n, Graph *g = nullptr);

  public:
    // I want to make this protected, but can't.
    // Even code which has access to a protected copy_ctor
    // of foo can't invoke .resize(n, foo_inst) on a std::vector<foo>. This
    // seems like a defect in C++. Applies to various 'emplace' methods too;
    // the 'emplace' can only ever use public ctors.
    Deserz(Deserz const &) = default;

  public:
    virtual ~Deserz(); // please keep this as first virtual method declared.

    // These three ONLY TO BE USED when setting up a Deserz to start processing a segment.
    void setup_source_span(deser_segment_span const &);
    void setup_dcrate_out(void *base, size_t len);
    void setup_next_tensor_index(unsigned const idx) { next_tensordef_index = idx; }

    typedef uint32_t object_identity_type;

    // Note, various accessor methods are defined as inlines below 'class Deserializer'.
    // true if this Deserz is really an instance of Deserializer.
    constexpr bool is_base_deser() const;

    using op_deserialize_fn_list_t = std::vector<op_deserializer_map_t::const_iterator>;
    using tensor_deserialize_fn_list_t = std::vector<tensor_deserializer_fn>;

    op_deserialize_fn_list_t &get_op_deserialize_fn_list();
    tensor_deserialize_fn_list_t &get_tensor_deserialize_fn_list();
    std::vector<void *const *> &get_blocktable_link_table();
    // when deserializing an op:
    //  - call deserialize_tensor_ref (or _refs) on all the input tensor pointers
    //  - pass all output tensor addresses to deserialize_tensor_def
    //  Sequence must match serialization; note that the deserialize-ctor of Tensor
    //  calls deserialize_tensor_def on itself; so there is no need to call it elsewhere,
    //   except for specialized types which are constructed otherwise during depickle (e.g.,
    //   types embedded in the Op).
    //
    // Some ops have multiple copies of some input tensor pointers; for these, it's possible
    // serialize just one reference, and the deserialize it using
    //     auto id = deserialize_object_identity()		// <- corresponds to serialize_tensor_ref
    //     need_tensor_fixup( id, &first_tensor_pointer);
    //      (other deserialize activity can happen here)
    //     need_tensor_fixup( id, &second_tensor_pointer);

    void deserialize_tensor_def(Tensor const *tensor_ptr);
    void deserialize_tensor_ref(Tensor const *&where);
    void deserialize_tensor_refs(Tensor const **ptrs, unsigned n);
    template <typename T> void deserialize_tensor_ref(T const *&where);
    template <typename T> void deserialize_tensor_refs(T const **ptrs, unsigned n);
    object_identity_type deserialize_object_identity();
    void need_tensor_fixup(object_identity_type oid, Tensor const **where);

    Graph &graph() const { return *graph_ptr; }
    Crate *crate() { return d_crate.crate(); }
    DCrate *dcrate() { return &d_crate; }
    DeserSegDescs const &get_segments() const; // gets ref to associated 'segments' object
    op_deserializer_map_t const &get_op_deser_map() const { return *op_deserializer_map; }

    bool is_aligned_const_format() const;
    bool has_pending_tensor_updates();

    bool is_shared_dynamic_tensor_shape_format() const;

    fa::RuntimeAllocator *allocator;
    DCrate d_crate; // contains a crate pointer

  protected:
    // hoist pointers to these maps into Deserializer to avoid static lock overhead
    op_deserializer_map_t const *op_deserializer_map;
    tensor_deserializer_map_t const *tensor_deserializer_map;
    Graph *graph_ptr{};
    Deserializer *full_deser;

    char const *bufstart; // start of current buffer
    char const *bufend; // first byte we can't read
    char const *bufp; // next to read
    char const *buf_limit; // <= bufend; where 'fill_buffer' needs to be called.
    size_t bytes_filled; // bytes previously filled

    uint32_t op_flags;
    OpExtraInfo op_extra_info;

    unsigned next_tensordef_index = 1; // belongs to 'tensorconn' but needs to be in Deserz.
    // 'format version'. Currently only ones used are 0 = classic, 1 = July/2023
    // Only access through methods like .classic_format();
    // This is changed to non-zero value based on seeing certain Aux Data records
    // (which must appear before the allocator).
    int format_version = 0;

    // this is used in multi-thread decoding. It is important that
    // it remains null-constructed if the object is really a base of Deserializer;
    // it is only used in 'segment' Deserz instances.
    runlist_fixup_state seg_fixup_state{};

    /**
	 * @brief throws an error since deserializer detected
	 * deserialization on insufficient bytes i.e. an underflow
	 *
	 */
    API_EXPORT virtual char const *fill_buffer(); // called for underflow on short operation

    /**
	 * @brief Deserialize data of specified length and write into
	 * buffer provided by caller
	 *
	 * @param[out] p buffer to write to
	 * @param[in] len length of the \ref bufp to read from
	 * @param[in] align if true, skip input bytes to a boundary of 4
	 */
    API_EXPORT virtual void deserialize_fread(void *p, size_t len, bool align);

    /**
	 * @brief Get current position of buffer from which next data will be read
	 *
	 * @return size_t offset from buffer start
	 */
    size_t buffer_offset() const { return bufp - bufstart; }
    /**
	 * @brief Available buffer size remaining for deserialization
	 *
	 * @return size_t remaining bytes size
	 */
    size_t buffer_remain() const { return bufend - bufp; }

    /**
	 * @brief deserialize buffer for type T
	 *
	 * @retval T returs the deserialized value of type T
	 *
	 * Note: This is the templated API called by deserialize_T() functions
	 *
	 * Note: Cannot be used for more than 4 bytes, there is a specialized version to read u64.
	 */
    template <typename T> T simple_deserialize()
    {
        static_assert(sizeof(T) <= 4, "can only read sizeof(T) <= 4");
        constexpr size_t W = 4;
        char const *curr_p = bufp;
        if (curr_p >= buf_limit) {
            curr_p = fill_buffer();
        }
        T const val = *(T const *)(curr_p);
        bufp = curr_p + W;
        return val;
    }
    // see comment above deserialize_shared_obj.
    API_EXPORT std::pair<void const *, void const **> deserialize_shared_obj_func(void const **ptrloc);
    API_EXPORT uint64_t deser_u64_slowpath();
    void initial_l2fetch(); // called only from ctor

  public:
    inline constexpr bool classic_format() const { return format_version == 0; }
    /**
	 * @brief deserialize data of type which calls simple_deserialize
	 *
	 * @param val data to deserialize
	 *
	 * Note: the below are the only types supported for deserialize_type<T>
	 */
    API_EXPORT uint64_t deserialize_uint64(); // inline later
    inline float deserialize_float() { return simple_deserialize<float>(); }
    inline uint32_t deserialize_uint32() { return simple_deserialize<uint32_t>(); }
    inline NN_INT32_T deserialize_int32() { return simple_deserialize<NN_INT32_T>(); }
    inline int16_t deserialize_int16() { return simple_deserialize<int16_t>(); }
    inline uint16_t deserialize_uint16() { return simple_deserialize<uint16_t>(); }
    inline int8_t deserialize_int8() { return simple_deserialize<int8_t>(); }
    inline uint8_t deserialize_uint8() { return simple_deserialize<uint8_t>(); }

    inline uint64_t deserialize_namesig() { return deserialize_uint64(); }

    // note, this is defined as an inline in deserializer.cc and not available elsewhere
    tensor_deserializer_fn deserialize_tensor_identification(unsigned tensor_class_index);

    // deserialize string
    // **NOTE** will throe runtime error if called in a Deserz which is not really a Deserialize.
    API_EXPORT std::string_view deserialize_str();

    uint32_t get_op_flags() const { return op_flags; };
    void clear_op_flags() { op_flags = 0; };
    void set_op_flags(uint32_t f) { op_flags = f; };

    const OpExtraInfo &get_op_extra_info() const { return op_extra_info; };
    void clear_extra_info() { op_extra_info.clear(); };
    void set_op_extra_info(OpExtraInfo in_op_extra_info) { op_extra_info = in_op_extra_info; };

    /**
	 * @brief deserialize buffer for specified size
	 *
	 * @param[in] alloc_size number of bytes to read from \ref bufp
	 * @param[out] ptr destination buffer for the read bytes
	 * @return size_t number of bytes actually read
	 */
    API_EXPORT size_t deserialize_buf(size_t alloc_size, void *ptr);
    /**
	 * @brief similar to deserialize_buf but first deserialize a
	 * uint32_t size of bytes that should match the alloc_size
	 *
	 * @param[in] alloc_size number of bytes to read from \ref bufp
	 * @param[out] ptr destination buffer for the read bytes
	 * @return size_t number of bytes actually read
	 */
    API_EXPORT size_t deserialize_buf_withlen(size_t alloc_size, void *ptr);
    // deserialize a pointer as 64 bits
    inline void *deserialize_ptr() { return (void *)size_t(deserialize_uint64()); }

    template <typename T> T deserialize_type();

    template <typename RetT, size_t N, typename SerialT> std::array<RetT, N> deserialize_array();

    /**
	 * @brief convernience wrappers for deserialize fuctions that
	 * take in different number of arguments of uint32_t type
	 *
	 * @return std::tuple<uint32_t,uint32_t> (first, second) uint32_t data deserialized
	 */
    // convenience wrappers (to reduce inlined code size w/o much loss of speed)
    API_EXPORT std::tuple<uint32_t, uint32_t> deserialize_uint32_x2();
    API_EXPORT std::tuple<uint32_t, uint32_t, uint32_t> deserialize_uint32_x3();
    API_EXPORT std::tuple<uint32_t, uint32_t, uint32_t, uint32_t> deserialize_uint32_x4();

    API_EXPORT void deserialize_uint32_arr(uint32_t *p, size_t N);

    // to reduce code size in the templates, we can deserialize arrays of
    // N uint32 to sizet
    API_EXPORT void deserialize_uint32_arr_sizet(size_t *p, size_t N);

    /**
	 * @brief deserialize array containing uint32_t type date
	 *
	 * @tparam N size of the array
	 * @return std::array<size_t,N> array containing the deserialized values
	 */
    template <size_t N> std::array<size_t, N> deserialize_uint32_array_sizet()
    {
        std::array<size_t, N> res;
        deserialize_uint32_arr_sizet(&res[0], N);
        return res;
    }

    //
    // This is used for shared objects like Shape and Interface.
    // it deserializes the index, and decides if it's the first instance.
    //  - must always pass the address which needs to point to it; though it
    //    will be not be set by this function.
    //  - if retval.second is null, then the object was previously deserialized,
    //    and return.first is the pointer to it.
    //  - otherwise, caller must deserialize the instance, and store the pointer
    //    at *retval.second. retval.first will be null in this case.
    // In scenarios where delayed resolution is used, the return may be {token,null}
    // where 'token' is actually  delayed resolution token.
    //
    template <typename T>
    std::pair<T const *, T const **> // see above
    deserialize_shared_obj(T const **const loc)
    {
        auto const res = deserialize_shared_obj_func((void const **)loc);
        return {(T const *)res.first, (T const **)res.second};
    }

    // Increment tue current read position of internal buffer without reading anything
    void deserialize_skip_words(size_t nwords);

    // Apply the 'pointer fixups' contained within seg_info. This can
    // be called with 'this' being any Deserz or Deserializer associated
    // with the operation (it is only used to access tables in Deserializer).
    // This can only be done on a given segment when all previous have
    // been deserialized; so if we have one Deserz per thread, we need
    // to 'move' the seg_info object out of it after completing the segment,
    // and use this later to do the fixups.
    // Returns true if ok, false if failed.
    // Will leave the fixup list empty on success.
    bool apply_segment_fixups(runlist_fixup_state &seg_info) const;

    // Methods to move 'seg_fixup_state' object in or out.
    void install_seg_fixup_state(runlist_fixup_state &&src) { seg_fixup_state = std::move(src); }
    runlist_fixup_state extract_seg_fixup_state() { return std::move(seg_fixup_state); }
    void extract_seg_fixup_state_to(runlist_fixup_state &dest) { dest = std::move(seg_fixup_state); }

    // and a read_only accessor
    runlist_fixup_state const &fixup_state() const { return seg_fixup_state; }

    // for Tensor::deserialize_blocktable
    inline bool fixup_encode_for_blocktable(uint32_t const idx, uint32_t const table_offs, void **const ptrloc)
    {
        return hnnx::fixup_encode_for_blocktable(seg_fixup_state, idx, table_offs, ptrloc);
    }
};

/////////////////

class Deserializer : public Deserz {
    friend class Deserz;

  public:
    /**
	 * @brief Construct a new Deserializer object
	 *
	 * @param[in] p buffer that needs to be deserialized
	 * @param[in] n length of the buffer
	 * @param[in] g pointer Graph object to deserialize (usually null, since object
	 *              is being passed to the Graph::Graph ctor to deserialize; that ctor
	 *              must immediately call dctx.set_graph(*this) )
	 */
    API_EXPORT Deserializer(char const *p, size_t n, Graph *g = nullptr);
    API_EXPORT virtual ~Deserializer(); // please keep this as first virtual method declared.

    void set_graph(Graph &g);

    inline void deserialize_tensor_def(Tensor const *tensor_ptr) { tensorconn.tensor_def(*this, tensor_ptr); }
    inline void deserialize_tensor_ref(Tensor const *&where) { tensorconn.tensor_ref(*this, where); }
    inline void deserialize_tensor_refs(Tensor const **ptrs, unsigned n) { tensorconn.tensor_refs(*this, ptrs, n); }
    template <typename T> inline void deserialize_tensor_ref(T const *&where)
    {
        static_assert(std::is_base_of<Tensor, T>::value);
        tensorconn.tensor_ref(*this, *(Tensor const **)&where);
    }
    template <typename T> void deserialize_tensor_refs(T const **ptrs, unsigned n)
    {
        static_assert(std::is_base_of<Tensor, T>::value);
        tensorconn.tensor_refs(*this, (Tensor const **)ptrs, n);
    }
    inline object_identity_type deserialize_object_identity() { return tensorconn.read_identity(*this); }

    inline void need_tensor_fixup(object_identity_type oid, Tensor const **where) { tensorconn.need_fixup(oid, where); }
    inline void resolve_fixups()
    {
        [[maybe_unused]] const object_identity_type newval = tensorconn.read_identity(*this);
        assert(newval == 0);
    }

    constexpr bool is_aligned_const_format() const { return aligned_const_format_flag; }
    void set_aligned_const_format(const bool v = true) { aligned_const_format_flag = v; }

    constexpr bool is_shared_dynamic_tensor_shape_format() const { return shared_dynamic_tensor_shape; }
    void set_shared_dynamic_tensor_shape_format(const bool v = true) { shared_dynamic_tensor_shape = v; }

    void set_shared_io_buffer(const bool v = true) { shared_io_buffer = v; }

    PUSH_WARNING()
    DISABLE_WARNING("-Wcast-qual", MSVC_NO_EQUIV)
    // valid when the entire pickle, in const_extent format, is loaded as a single, persistent dma buffer
    inline unsigned char *get_weight_pointer() { return ((unsigned char *)bufstart) + (4 * pickle_len_words); };
    POP_WARNING()
    inline size_t get_weight_size() { return (bufend - bufstart) - (4 * pickle_len_words); };

    inline op_deserialize_fn_list_t &get_op_deserialize_fn_list() { return op_deserialize_fn_list; }
    inline tensor_deserialize_fn_list_t &get_tensor_deserialize_fn_list() { return tensor_deserialize_fn_list; }

    // Next 4 methods are used to support 'deserialize_by_segments'.
    // 'get_forward_span' returns a 'deser_segment_span' (pair of pointers) for a region of deserialized data
    // from 'ref + start' up to 'ref + end', where start and end (0 <= start < end) are byte offsets
    // relative to some position 'ref' in the deserialized data, and 'ref' is the value which bytes_consumed()
    // returned at that reference point. All should be multiples of 4.
    deser_segment_span get_forward_span(size_t ref, size_t start, size_t end);
    // used to get a reference point for bytes_consumed
    size_t bytes_consumed() const { return bufp - bufstart; }
    // used to skip past the last 'get_forward_span' we did
    void skip_to_after_span(deser_segment_span const &);
    // resize tables: tensor, shared_obj, linktable, according to info in final_segdesc
    void resize_object_tables(runlist_auxdata_seg_desc const &final_desc);

    uint32_t crate_size_according_to_segments() const;

  protected:
    ///
    /// @brief Type for a unique readonly block-of-bytes (32b array)
    ///
    typedef std::unique_ptr<const uint32_t[], conditional_default_deleter<const uint32_t[]>> unique_readonly_blob_t;

    std::vector<void const *> objindex; // index of pointers to shape, etc.
    // the state of the 'tensor connectivity' deserialize engine.
    DeserTensorConn tensorconn;
    bool aligned_const_format_flag = false;
    bool shared_dynamic_tensor_shape = false;
    bool shared_io_buffer = false;

    // this is used in 'deserialize_str', so it ideally should be in Deserz; but
    // it's pretty large; so, put it here and forbid calling deserialize_str
    // on a Derserz which not really a Deserialize. We only use it to decode
    // 'classic' pickles, so this is ok.
    char name_buf[4096]; // used for string view

    // do the reference fixups on a segment. Return true if OK.
    // See Deserz::apply_segment_fixups for public API.
    static bool do_segment_fixups(runlist_fixup_state &seginfo, Deserz const &dctx0);

    ///
    /// @brief Function to load header part of constant extent section
    /// @param [in] ptr Pointer to constant extent section
    /// @return Unique readonly blob pointing to header
    ///
    unique_readonly_blob_t load_header(hexagon_nn_wide_address_const_t const addr);

  public:
    inline constexpr bool classic_format() const { return format_version == 0; }
    inline void set_format_2307() { format_version = 1; }

    // This is called when a 'class index' Aux Data is encountered.
    // It must deserialize exactly the indicated number of payload words.
    // is_tensor = false for "Co" (op class index), and true for "Ct" (tensor class index)
    API_EXPORT void auxdata_class_index(unsigned payload_words, bool is_tensor);
    //
    // called when an 'Nt' Aux data is encountered, which provides some array sizes for the
    // deserialization.
    // It must deserialize exactly the indicated number of payload words.
    API_EXPORT void auxdata_temparr_sizes(unsigned payload_words);
    // Called when a 'AuxTag_deserializeSegments' is encountered. If it likes
    // the record, it will set up the 'segments' object.
    API_EXPORT void auxdata_deserialize_segments(unsigned payload_words);

    // called when a 'KS' Aux data is encountered, which provides a const_extent_descriptor
    // It must deserialize exactly the indicated number of payload words.
    API_EXPORT int auxdata_read_const_extent_descriptor(const unsigned payload_words);
    // helper for above. payload_words is the length WITH PADDING
    API_EXPORT int extract_const_extent_name(const unsigned payload_words, std::string &retVal);

    // Extract a std::vector<uint32_t> containing the 'const extent descriptor table,
    // from a given offset (in units of 32-bit words) relative to the start of the pickle.
    // or separate pointer (if separate buffer for the weights was passed in).
    // This does not affect the current position.
    // If there is a problem, it returns an empty vector; caller *must* check and report.
    // This uses hnnx::const_extent_hdr_check to understand how much it should read,
    // and to do basic check.
    API_EXPORT std::vector<uint32_t> extract_const_extent_table(size_t posn_in_words);
    std::vector<uint32_t> extract_const_extent_table(hexagon_nn_wide_address_const_t weight_data,
                                                     const uint64_t weight_size);
    // given a destination char pointer, prefilled with \null, fills it in with the name of the const_extent
    // caller must provide destination of sufficient length
    std::string name_from_weight_data(hexagon_nn_wide_address_const_t weight_data, const uint64_t weight_length);

    // helper func for above. return -1 if name not present.
    std::string get_name(hexagon_nn_wide_address_const_t weight_data, const uint64_t weight_length);
    // give a vector of weight_data buffers, stores them all in the appropriate map
    void store_named_weight_bufs(const hexagon_nn_wide_address_const_t *const buffers, const uint64_t *const lengths,
                                 const unsigned num_buffers);
    void store_named_weight_bufs(std::vector<hexagon_nn_wide_iovec_t const *> const &named_weights);
    //
    // copy 'len' bytes of data at offset offs_bytes in the pickle into location dstp.
    // returns true if it's possible. You can maybe pass a DMA_Manager to have it queued...
    // offs_bytes defined as uint64_t to support possible 'far' data on hexagon.
    API_EXPORT bool extract_const_extent_data(uint64_t offs_bytes, size_t len, void *dstp, DMA_Manager *dma = nullptr);
    // same, using an external const_extent
    bool extract_const_extent_data(uint64_t offs_bytes, size_t len, void *dstp,
                                   hexagon_nn_wide_address_const_t weight_data, const uint64_t weight_length);

    // This extracts the 'objindex', when it is needed e.g. to 'patch' interfaces.
    // Must be done only after deserializing, and can only be done once.
    std::vector<void const *> extract_objindex() { return std::move(objindex); }

    DeserSegDescs segments; // array of runlist_seg_descriptor, empty if not doing multiseg.

    // this is used to pass the offset of the const-extent-descriptor (recorded as pickle_len)
    // to the alloc->deserialize.
    size_t pickle_len_words;

    // OPTIONAL maps from weight buffer names to the descriptors and the buffers, respectively
    cexdesc_deserializer_map named_cexdescs;
    weight_buf_deserializer_map named_weight_bufs;

    void *uncached_ptr;
    uint32_t uncached_len;

    std::vector<op_deserializer_map_t::const_iterator> op_deserialize_fn_list;
    std::vector<tensor_deserializer_fn> tensor_deserialize_fn_list;

    // used to 'link' shared blocktables during deser.
    std::vector<void *const *> blocktable_link_table;
};

/////////////////

// true if this Deserz is really an instance of Deserializer.
inline constexpr bool Deserz::is_base_deser() const
{
    return static_cast<Deserz const *>(full_deser) == this;
}

inline bool Deserz::is_aligned_const_format() const
{
    return full_deser->aligned_const_format_flag;
}
inline bool Deserz::is_shared_dynamic_tensor_shape_format() const
{
    return full_deser->shared_dynamic_tensor_shape;
}
inline Deserz::op_deserialize_fn_list_t &Deserz::get_op_deserialize_fn_list()
{
    return full_deser->op_deserialize_fn_list;
}
inline Deserz::tensor_deserialize_fn_list_t &Deserz::get_tensor_deserialize_fn_list()
{
    return full_deser->tensor_deserialize_fn_list;
}
inline std::vector<void *const *> &Deserz::get_blocktable_link_table()
{
    return full_deser->blocktable_link_table;
}
// For these in Deserz, we must call the corresponding methods on the
// tensorconn in 'full_deser', but must pass 'this' as first parameter.
inline void Deserz::deserialize_tensor_def(Tensor const *const tensor_ptr)
{
    full_deser->tensorconn.tensor_def(*this, tensor_ptr);
}
inline void Deserz::deserialize_tensor_ref(Tensor const *&where)
{
    full_deser->tensorconn.tensor_ref(*this, where);
}
inline void Deserz::deserialize_tensor_refs(Tensor const **const ptrs, const unsigned n)
{
    full_deser->tensorconn.tensor_refs(*this, ptrs, n);
}
inline DeserSegDescs const &Deserz::get_segments() const
{
    return full_deser->segments;
}

// unaligned read of 64-bits (two 32-bit aligned reads)
template <> inline uint64_t Deserz::simple_deserialize<uint64_t>()
{
    char const *const curr_p = bufp;
    if (curr_p + 8u > buf_limit) {
        return deser_u64_slowpath();
    }
    uint32_t const *const p = (uint32_t const *)(curr_p);
    bufp = curr_p + 8u;
    return p[0] + ((uint64_t)p[1] << 32);
}
inline uint64_t Deserz::deserialize_uint64()
{
    return simple_deserialize<uint64_t>();
}

template <> inline uint64_t Deserz::deserialize_type<uint64_t>()
{
    return deserialize_uint64();
}
template <> inline float Deserz::deserialize_type<float>()
{
    return deserialize_float();
}
// sometimes uint32_t is unsigned long, sometimes it's unsigned
// sometimes unsigned long is uint64. Hopefully this should cover it all.
#if ULONG_MAX == UINT_MAX
template <> inline unsigned long Deserz::deserialize_type<unsigned long>()
{
    return deserialize_uint32();
}
template <> inline long Deserz::deserialize_type<long>()
{
    return deserialize_int32();
}
#endif
template <> inline unsigned Deserz::deserialize_type<unsigned>()
{
    return deserialize_uint32();
}
template <> inline int Deserz::deserialize_type<int>()
{
    return deserialize_int32();
}
template <> inline int16_t Deserz::deserialize_type<int16_t>()
{
    return deserialize_int16();
}
template <> inline uint16_t Deserz::deserialize_type<uint16_t>()
{
    return deserialize_uint16();
}
template <> inline int8_t Deserz::deserialize_type<int8_t>()
{
    return deserialize_int8();
}
template <> inline uint8_t Deserz::deserialize_type<uint8_t>()
{
    return deserialize_uint8();
}

// assert( dctx.deserialize_uint32() == SOME_CONST );
// is not safe, since if you turn off asserts, it will no longer read the 4 bytes. This is to allow that to work
#define DESERIALIZE_ASSERT_UINT32(DCTX, VAL)                                                                           \
    do {                                                                                                               \
        uint32_t const tmp [[gnu::unused]] = (DCTX).deserialize_uint32();                                              \
        assert(tmp == (VAL));                                                                                          \
    } while (0)

#include "weak_linkage.h"
PUSH_VISIBILITY(default)

/**
 * @brief register the deserialization function for each \ref Op
 * TypicalOp and VariadicOp derived classes are instantiated via
 * template and hence the need to create a map of deserialize functions
 * for each Op when they are generated at library initialization
 *
 * @param[in] tinf Op type_info that is used to key the map
 * @param[in] fn Deserialize function
 */
API_EXPORT void deserialize_op_register(std::type_info const *tinf, const std::string_view type_tag,
                                        const op_deserializer_fn &fn, bool is_external = false,
                                        std::string_view filename = "");
/**
 * @brief register the deserialization function for each \ref Tensor
 * Since \ref Tensor derived classes are instantiated via templates, there
 * is a need to create a map of deserialize function for each Tensor at runtime
 *
 * @param[in] type_tag Tensor type tag that is used to key the map
 * @param[in] fn Deserialize function
 */
API_FUNC_EXPORT void deserialize_tensor_register(std::type_info const &tinf, const char *type_tag,
                                                 tensor_deserializer_fn fn);

POP_VISIBILITY()

// this is fully defined in serialize_register.h
template <typename T> struct deserialize_tensor_using_constructor;

// this is fully defined in serialize_register.h
template <typename T> struct alloc_func_for_op;
template <typename T> struct dealloc_func_for_op;

//////////////////////
// Forward decls of things defined in template_help.h
//
// contains_type< tuple<a,b,c>, x >::value: true if x is in a,b,c ...
// no 'remove ref' etc is done.
template <typename TUPLET, typename T> struct contains_type;
template <typename TUPLET, typename T> struct not_contains_type;
template <template <typename> typename Pred, typename...> struct TupFilter;

PUSH_VISIBILITY(default)

// 'slow path' for deserialize_op_idx, used when the value is not aready in the table.
API_EXPORT uint32_t deserialize_op_idx_slow(Deserz &dctx, uint32_t op_idx);

/**
 * @brief deserialize a \ref Tensor. The implementation makes use of the map
 * created during \ref deserialize_tensor_register to construct the Tensor.
 *
 * @param[in] producer \ref Op that will produce this tensor
 * @param[in] dctx \ref Deserializer context that has the buffer to read from
 * @param[in] graph_in \ref Graph context where this Tensor lives.
 * @return uptr_Tensor unique_ptr of \ref Tensor type
 */
API_EXPORT uptr_Tensor deserialize_tensor(Deserz &dctx);

POP_VISIBILITY()

} // namespace hnnx

#endif // DESERIALIZER_H
