
//==============================================================================
//
// Copyright (c) Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

/*
 * tile_extract.h
 *
 *  Created on: Nov 8, 2019
 *      Author: smithg
 */
#ifndef TILE_EXTRACT_H_
#define TILE_EXTRACT_H_

#include "intrinsics.h"
#include "dynamic_tensors.h"

/*
 *  This defines functions which are templated on Tensor subclasses,
 *  and which extract a tile of data from the tensor, and replace it.
 *  The tiles are normally 2K bytes;
 *
 *    - for qu8/qi8 data, the tile is 8x8x32 in 'flat' order.
 *    - for qu16/qi16 data, the tile is 8h x 4w x 32 in 'crouton' order,
 *         (on each row, the first 2 elements are in 32 {w0,w1} pairs,
 *         then the rest are in 32 {w2,w3} pairs.
 *       There is flag to force 'flat' order for qu16 data
 *
 *    - For qint32 data (and for int32,float), the default tile is 8x2x32; in order to
 *         match the 8x8x32 Crouton size, while keeping the tile in 2K bytes
 *
 * However, in all cases you can specify a specific tile height (in range 1..8) by or'ing
 * the value into the lower bits of the 'flags' word (a zero value gives the default for the element size).
 *
 *
 *  Operations accept these 'flags', or'd  together (and combined with an optional tile height)
 *    tileExt::copy       - forces a copy operation on tile_read, even when not needed.
 *    tileExt::unshuffled - this has no effect on qu8 data; for qu16,
 *                          the data will be unpacked into 'flat' order instead of shuffled
 *                          (when storing back, this refers to the order in which the data
 *                          is presented).
 *    tileExt::broadcast  - see below; supports broadcasting of input dimensions on read.
 *
 *
 * READING TILES
 * =============
 * The normal 'read' operation is to extract a tile at coordinates (b,h,w,d), which represent
 * the 'origin' of the tile. The tile of a shape [1,TH,TW,32] is extracted from
 *                   [ b, h ... h+TH-1,  w ... w + TW-1,  d ... d + 31]
 *
 * Here TH = Tile height (as specified by flags, or default by element size)
 *      TW = Tile width (depends on element size)
 *
 * The caller supplies a pointer to a vector-aligned buffer area of sufficient size.
 * The extract function will either extract the data into this area, or -- when possible --
 * will simply supply a pointer to where the data already is, in memory. If you use the 'copy'
 * flag, the data will always be copied to the work area, which can be useful if you want to
 * modify it in-place (such modification is not safe unless 'copy' is specified).
 *
 * EDGE TREATMENT
 * --------------
 *  If the specified region for a read falls outside the boundaries of the tensor, the corresponding
 *  portions of the result will contain 'garbage' data - except for 'broadcast' as below. It is a requirement
 *  that at least part of the tile falls within the bounds of the input tensor:
 *
 *     - 'd' coordinate must be >= -31 and  < input_depth
 *     - 'w' coordinate must be >= -(TW-1) and < input_width
 *     - 'h' coordinate must be >= -(TH-1) and < input_width
 *
 * Use of negative coordinates causes the data to be displaced to the right/down in the tile,
 * with the left/top filled with 'garbage'.
 * When reading from a 'crouton' tensor, the data may be gathered from as many as 8 actual tiles,
 * according to which of h,w,d dimensions are misaligned in the request.
 *
 *
 * BROADCAST
 * ---------
 *  The 'broadcast' flag only applies to 'read' operations, and it has the following effect:
 *     - if the input tensor has batches=1, the input 'b' parameter is ignored and treated as 0.
 *     - if the input tensor has height=1, the 'h' parameter is ignored, the single row of
 *           data will replicated to all TH rows of the extracted tile
 *     - if the input tensor has width=1, the width parameter is ignored, and the column
 *           of data will be replicated to all TW columns of the extracted tile.
 *     - if the input tensor has depth=1, the depth parameter is ignored, the
 *           data will be replicated to all 32 depths of the extracted tile.
 *
 * These are independent; so it may be that you have width broadcast, but height and depth
 * broadcast do not occur (since the conditions are not met for those dims) and therefore
 * there could still be 'garbage' bytes in the result.
 *
 * WRITING TILES
 * =============
 *  Caller supplies the data for a tile; function stores that to the tensor, respecting
 *  edges (data clipped as needed).
 *    - TH determined as per read_tile: specified in lower bits of flags, or default if zero;
 *    - 'unshuffled' flag applies (only affects 16-bit)
 *    - The ranges of the h and w coords are the same as for reading: tile must contain at least one
 *      value which falls into the tensor dims.
 *    - d must be a multiple of 32, 0 <= d < output_depth.  Thus, for crouton format,
 *      at most 4 actual tiles will be need to be written to (depending on h and w alignment).
 *      In cases where the output is a 'chunked' format such as crouton or d32, and the output
 *      depth is not a multiple of 32, the write extent of the last depth unit may be effectively
 *      padded out (i.e. garbage bytes will be written to a 'margin' area of the tensor). Likewise,
 *      garbage values may be stored into margin areas when the tile overlaps left or right in width dimension.
 *
 *   Another way to do writes, which allows computing the result directly into a crouton tensor:
 *      (1) before the operation, call
 *            void *ptr = tens->write_tile_strategy( flags, tmp_buffer, b,h,w,d );
 *       .. this has the same requirements as 'write_tile', but it will do nothing except either:
 *          (a) return pointer to where the data can be directly written; or
 *          (b) return 'tmp_buffer'.
 *      (2) perform the operation, writing the results to the address returned by write_tile_strategy
 *      (3) only if (ptr == tmp_buffer):
 *                 call tens->write_buffer( flags, tmp_buffer, b,h,w,d )
 *          (with the *exact* params used in the call to write_tile_strategy).
 *      Step (1) can be skipped if tile_support_direct() returns false for the output tensor (see below). Note also,
 *      'unshuffled' stores to 16-bit crouton may never be direct-mapped.
 *
 * Important: If you specify a particular height in the flags, do not exceed that when storing the output, if
 * using write_tile_strategy.  For instance, if TH=3 is specified in the flags, and write_tile_strategy
 * returns a direct pointer, the pointer may be to the last 3 rows of a crouton, so storing 4 rows will corrupt
 * some other data.
 *
 * CHECKING SUPPORT
 * ================
 * Tensors have the following virtual methods, which indicate capabilities of the tensor types:
 *     bool tile_support() const;
 *     bool tile_support_fast() const;
 *     bool tile_support_direct() const;
 *
 *  - tile_support():       if this returns false, the tile_read/write methods are not supported and will throw an assert.
 *    (if properly deployed, this should only happen where the dtype of the tensor is not supported by tiles)
 *  - tile_support_fast():    returns true if the tile support is at least better than a series of element-by-element virtual calls.
 *  - tile_support_direct():  if true, there is a possibility that a 'direct mapping' to the tile layout can occur, depending
 *                          on the tile position (i.e. it's a crouton layout). When false, you can skip calling write_tile_strategy()
 *                          since it will never succeed.
 *
 *  Implementation node: there is actually just one virtual method tile_support_bits() which returns 'unsigned'; the methods above test individual bits
 *  of that method's result.
 *
 */
#include "weak_linkage.h"
#include "macros_attribute.h"
PUSH_VISIBILITY(default)

namespace tileExt {
enum tile_flags : unsigned {
    // lower 5 bits contain 'ht'. This must be 0 (to indicate 'default') or a number in range 1..8
    // The default is normally 8; for 32-bit tiles it is 2.
    tile_ht_mask = 31,
    copy = 32,
    unshuffled = 64,
    broadcast = 128,

    write_strategy = 256, // used internally only
    write_strategy_keep = unshuffled | tile_ht_mask
};

} //namespace tileExt

namespace hnnx {

namespace tileExt_priv {

// these are designed so that, for tensor types which don't support tile ops, the read_tile and write_tile
// methods can just jump to them.
API_EXPORT uint8_t const *unsupported_read(Tensor const *, unsigned flags_unused, uint8_t *buf);
API_EXPORT void unsupported_write(Tensor *);

template <typename STYPE, unsigned RANK>
API_EXPORT uint8_t const *generic_tile_read(Tensor const *, unsigned flags,
                                            uint8_t *tbuf, // caller-supplied buffer
                                            size_t b, int h, int w, int d);
template <typename STYPE, unsigned RANK>
API_EXPORT void generic_tile_write(Tensor *, unsigned flags,
                                   uint8_t const *tbuf, // caller-supplied buffer
                                   size_t b, int h, int w, int d);

template <unsigned FLAGS, typename T> struct tile_support_flags_for {
    static constexpr unsigned value = FLAGS | ((sizeof(T) == 1) ? Tensor::tile_8bit : 0) |
                                      ((sizeof(T) == 2) ? Tensor::tile_16bit : 0) |
                                      ((sizeof(T) == 4) ? Tensor::tile_32bit : 0);
    static_assert((value & Tensor::tile_any) != 0);
};

// Determine (based on 'Linfo') if the support for a tensor layout is supported generically;
// returns true if it is.
//  (and any such types, hopefully all, can have 'fast' support).
//
// We support:
//  - only with rank 4, and with supported storage_type
//  - any flat;
//  - no 'contiguous chunked' tensors; and no 'is_singular' tensors;
//  - any 'normal' crouton (i.e. no 'wide' crouton)
//
template <typename Linfo> constexpr bool tile_support_test_for_linfo()
{
    using storage_type = typename Linfo::storage_type;
    // Note: this condition could be 'tensor_traits<LayoutTensor<Linfo>>::is_singular'
    // but we can't instantiate LayoutTensor<Linfo> here, due to the way some gtests work.
    if constexpr (std::is_same_v<typename Linfo::Tlayout, SingularMemoryLayout<Linfo::Rank>>) {
        return false;
    }
    if constexpr (Linfo::Rank != 4 ||
                  !(std::is_same_v<storage_type, uint8_t> || std::is_same_v<storage_type, uint16_t> ||
                    std::is_same_v<storage_type, NN_UINT32_T>)) {
        return false;
    } else if constexpr (Linfo::is_chunked) {
        // only the 'normal' crouton layouts.
        if constexpr (std::is_same_v<Linfo, R4CroutonLayout> || std::is_same_v<Linfo, R4Crouton2Layout> ||
                      std::is_same_v<Linfo, R4Crouton4Layout>) {
            return true;
        } else {
            return false;
        }
    } else {
        return true; // flat tensor
    }
}

//
// Generic tile methods - forwards to generic operations,
// or to 'unsupported' when generic can't be used.
// We will specialize this class for cases which have specific support.
template <typename Linfo> struct tile_methods {
    // we can use a generic method if Rank=4 and storage_type is one of uint8, uint16, NN_UINT32_T
    using storage_type = typename Linfo::storage_type;
    using LayoutTensorType = LayoutTensor<Linfo>;
    static constexpr unsigned Rank = Linfo::Rank;
    static constexpr bool is_generic = tile_support_test_for_linfo<Linfo>();

    static constexpr bool tile_support_any = is_generic;
    static constexpr bool tile_support_fast = false;

    API_EXPORT static inline uint8_t const *tile_read(LayoutTensorType const *tensor, // tensor to read from
                                                      unsigned flags,
                                                      uint8_t *tbuf, // caller-supplied buffer
                                                      size_t b, int h, int w, int d) // coordinates
    {
        // If this fires, it likely means a new tensor layout has been added, for which 'tile_support_test_for_linfo'
        // returns true; you can either:
        //   (a) modify tile_support_test_for_linfo() to return false for that - and no get no tile support;
        //   (b) re-enable generic support (see WITH_GENERIC_TILE_SUPPORT in tile_support.cc) - and get slow tile support;
        //       (in that case you should remove this static_assert).
        //   (c) create a specialization of tile_methods<> for the new layout and add fast support.
        static_assert(!is_generic, "missing tile support");
        if constexpr (is_generic) {
            return tileExt_priv::generic_tile_read<storage_type, Rank>(tensor, flags, tbuf, b, h, w, d);
        } else {
            return unsupported_read(tensor, flags, tbuf);
        }
    }

    API_EXPORT static inline void tile_write(LayoutTensorType *tensor, // tensor to write to
                                             unsigned flags,
                                             uint8_t const *tbuf, // caller-supplied buffer
                                             size_t b, int h, int w, int d)
    {
        if constexpr (is_generic) {
            tileExt_priv::generic_tile_write<storage_type, Rank>(tensor, flags, tbuf, b, h, w, d);
        } else {
            unsupported_write(tensor);
        }
    }
    API_EXPORT static constexpr unsigned tile_support_bits()
    {
        if constexpr (is_generic) {
            return tile_support_flags_for<0, storage_type>::value;
        } else {
            return 0;
        }
    }
};
// specialize for 'flat', no-padding case
// Methods are defined in tile_extract.cc
template <typename Linfo> struct tile_methods_r4flat {
    using TensType = LayoutTensor<Linfo>;
    static constexpr bool tile_support_any = true;
    static constexpr bool tile_support_fast = true;

    API_EXPORT static uint8_t const *tile_read(TensType const *tensor, // tensor to read from
                                               unsigned flags,
                                               uint8_t *tbuf, // caller-supplied buffer
                                               size_t b, int h, int w, int d);
    API_EXPORT static void tile_write(TensType *tensor, // tensor to store to
                                      unsigned flags,
                                      uint8_t const *tbuf, // caller-supplied buffer
                                      size_t b, int h, int w, int d);
    API_EXPORT static constexpr unsigned tile_support_bits()
    {
        using storage_type = typename Linfo::storage_type;
        return tile_support_flags_for<Tensor::tile_fast, storage_type>::value;
    }
};
// specialize tile_methods for flat layout
template <> struct tile_methods<Ldefs::Flat_8> : public tile_methods_r4flat<Ldefs::Flat_8> {
};
template <> struct tile_methods<Ldefs::Flat_16> : public tile_methods_r4flat<Ldefs::Flat_16> {
};
template <> struct tile_methods<Ldefs::Flat_32> : public tile_methods_r4flat<Ldefs::Flat_32> {
};

// specialize for Crouton, padding case
// Methods are defined in tile_extract.cc
template <typename Linfo> struct tile_methods_r4crouton {
    using TensType = LayoutTensor<Linfo>;
    static constexpr bool tile_support_any = true;
    static constexpr bool tile_support_fast = true;
    API_EXPORT static uint8_t const *tile_read(TensType const *tensor, // tensor to read from
                                               unsigned flags,
                                               uint8_t *tbuf, // caller-supplied buffer
                                               size_t b, int h, int w, int d);
    API_EXPORT static void tile_write(TensType *tensor, // tensor to store to
                                      unsigned flags,
                                      uint8_t const *tbuf, // caller-supplied buffer
                                      size_t b, int h, int w, int d);
    API_EXPORT static constexpr unsigned tile_support_bits()
    {
        using storage_type = typename Linfo::storage_type;
        constexpr unsigned direct = Tensor::tile_direct;
        return tile_support_flags_for<Tensor::tile_fast | direct, storage_type>::value;
    }
};
// specialize tile_methods for crouton layout
// 8 bit
template <> struct tile_methods<Ldefs::Crouton_8> : public tile_methods_r4crouton<Ldefs::Crouton_8> {
};
// 16 bit (different layout!)
template <> struct tile_methods<Ldefs::Crouton_16> : public tile_methods_r4crouton<Ldefs::Crouton_16> {
};

// 32 bit
template <> struct tile_methods<Ldefs::Crouton_32> : public tile_methods_r4crouton<Ldefs::Crouton_32> {
};

} // namespace tileExt_priv

} // namespace hnnx

// write_tile_strategy implementation (here, since it depends on the flag defs)

API_FUNC_EXPORT inline void *Tensor::write_tile_strategy(unsigned flags, void *buffer, size_t b, int h, int w, int d)
{
    unsigned const newflags = (flags & tileExt::write_strategy_keep) | tileExt::write_strategy;
    void const *const res = const_cast<Tensor &>(*this).read_tile(newflags, buffer, b, h, w, d);
    return const_cast<void *>(res);
}
//
// define the virtual methods for the tensor classes.
// These could be moved inside the classes, provided the "tile_read/write" functions are declared above that,
// and any specializations of it are defined before the tensor classes are specialized.
template <typename Linfo>
API_FUNC_EXPORT void const *LayoutTensor<Linfo>::read_tile(unsigned flags, void *buffer, size_t b, int h, int w,
                                                           int d) const
{
    return (void const *)hnnx::tileExt_priv::tile_methods<Linfo>::tile_read(this, flags, (uint8_t *)buffer, b, h, w, d);
}
template <typename Linfo>
API_FUNC_EXPORT void LayoutTensor<Linfo>::write_tile(unsigned flags, void const *buffer, size_t b, int h, int w, int d)
{
    hnnx::tileExt_priv::tile_methods<Linfo>::tile_write(this, flags, (uint8_t const *)buffer, b, h, w, d);
}
template <typename Linfo> API_FUNC_EXPORT unsigned LayoutTensor<Linfo>::tile_support_bits() const
{
    return hnnx::tileExt_priv::tile_methods<Linfo>::tile_support_bits();
}

namespace tileExt {

template <typename T> struct layout_def_of {
};
template <typename L> struct layout_def_of<LayoutTensor<L>> {
    using type = L;
};
//
// a way to tell at compile time if a tensor has tile support. It must
// be a layout tensor, or subclass of.
//    tileExt::tile_support_test<T>::support_any    <- any support at all, including 'generic'
//    tileExt::tile_support_test<T>::support_fast    <- support better than 'generic'.
template <typename TENST> class tile_support_test {
    using LTYPE = typename tensor_traits<TENST>::layouttensor_type;
    using methods = hnnx::tileExt_priv::tile_methods<typename layout_def_of<LTYPE>::type>;

  public:
    static constexpr bool support_any = methods::tile_support_any;
    static constexpr bool support_fast = methods::tile_support_fast;
};

/////////////////////////////////////////
// 'aligned_buffer' classes
// On hexagon we can make the compiler align
// it by putting an HVX vector in the union;
// on x86 it's done manually
/////////////////////////////////////////
template <unsigned NVECS> struct aligned_buffer_base {
    static_assert(NVECS >= 1);

  protected:
#ifdef __hexagon__
    static constexpr bool manual_align = false;
#else
    static constexpr bool manual_align = true;
#endif
    union {
        uint32_t u32arr[NVECS * 32 + (manual_align ? 31 : 0)];
#ifdef __hexagon__
        HVX_Vector varr[NVECS];
#endif
    };
    API_EXPORT void *arr_addr() const
    {
        if constexpr (manual_align) {
            size_t tmp = size_t(&u32arr[0]);
            tmp = (tmp + 127) & ~size_t(127);
            return (void *)tmp;
        } else {
            return (void *)&u32arr[0];
        }
    }
};

// useful subclasses of tile_buffer_template:

template <unsigned NVECS> struct tile_buffer_template : public aligned_buffer_base<NVECS> {
  public:
    uint8_t *buf() { return reinterpret_cast<uint8_t *>(this->arr_addr()); };
    uint8_t const *buf() const { return reinterpret_cast<uint8_t const *>(this->arr_addr()); };
};
// aligned buffer of 2K
using tile_buffer = tile_buffer_template<16>;
// aligned buffer of 1K
using tile_half_buffer = tile_buffer_template<8>;
// aligned buffer of 4K (for 4x8 32-bit tile)
using tile_double_buffer = tile_buffer_template<32>;
// aligned buffer of 8K (for 8x8 32-bit tile)
using tile_quad_buffer = tile_buffer_template<64>;

//
// 'arrays' of NBUFS tile buffers...
//  call 'buf(i)' method, with  i in range 0..NBUFS-1, to get a pointer to one of the buffers.
//
template <unsigned NBUFS, unsigned NVECS> struct tile_buffers_template : public aligned_buffer_base<NBUFS * NVECS> {
    using Parent = aligned_buffer_base<NBUFS * NVECS>;

  public:
#ifdef SAFE_ALLOC
    // For safety, clear everything to 0 so that if we load less then the size
    // of a tile, memory will have a deterministic value.
    tile_buffers_template()
    {
        // Clear memory if compiled with the SAFE_ALLOC option.
        memset(Parent::u32arr, 0, sizeof(Parent::u32arr));
    }
#endif

    API_EXPORT uint8_t *buf(unsigned i = 0) { return reinterpret_cast<uint8_t *>(this->arr_addr()) + NVECS * 128 * i; };
    API_EXPORT uint8_t const *buf(unsigned i = 0) const
    {
        return reinterpret_cast<uint8_t const *>(this->arr_addr()) + NVECS * 128 * i;
    };
};

template <unsigned NBUFS> using tile_buffers = tile_buffers_template<NBUFS, 16>;

template <unsigned NBUFS> using tile_half_buffers = tile_buffers_template<NBUFS, 8>;

template <unsigned NBUFS> using tile_double_buffers = tile_buffers_template<NBUFS, 32>;

template <unsigned NBUFS> using tile_quad_buffers = tile_buffers_template<NBUFS, 64>;

////////////////////////////////////////////////////
/// TileStoreWindow<int ELBYTES>
////////////////////////////////////////////////////
// (not really part of the tile_extract interface, but closely related).
// This is a class to manage storing tiles directly to a 'window'
// of the output tensor, or any flat tensor, using the same write_tile
// interface, but relative to (and clipped to) a predetermined window.
//
template <unsigned int RANK = 4> class TileStoreWindowBase {
  protected:
    void *ptr;
    void *ptrw; // pointer to window start.
    unsigned elsize; // element bytes
    unsigned dims[RANK]; // dimensions of the output
    size_t winsize[RANK]; // window to store to
    unsigned winoffs[RANK]; // offset of the window.
    size_t strides[RANK];

  public:
    API_EXPORT inline unsigned win_dim(int i) const { return winsize[i]; }
    API_EXPORT inline unsigned full_dim(int i) const { return dims[i]; }
    API_EXPORT inline size_t stride(int i) const { return strides[i]; }
    API_EXPORT void *addr_base() const { return ptr; }
    API_EXPORT void *win_base() const { return ptrw; }
    // this is to support Tensor::get_dims()
    API_EXPORT std::pair<size_t const *, size_t> get_windims() const noexcept { return {winsize, RANK}; }

    // set the descriptor up with  specified 'flat' tensor
    // for the output (described as pointer and oshape)

    API_EXPORT TileStoreWindowBase(Tensor &otensor, TensorShape<RANK> const &out_shape, unsigned elbytes)
    {
        ptr = ptrw = otensor.raw_data();
        size_t stride = elbytes;
        for (int i = RANK - 1; i >= 0; --i) {
            unsigned const dim = out_shape.dim(i);
            dims[i] = winsize[i] = dim;
            winoffs[i] = 0;
            strides[i] = stride;
            stride *= dim;
        }
        elsize = elbytes;
    }

    API_EXPORT TileStoreWindowBase(Tensor &otensor, std::array<size_t, RANK> out_dims, unsigned elbytes)
    {
        ptr = ptrw = otensor.raw_data();
        size_t stride = elbytes;
        for (int i = RANK - 1; i >= 0; --i) {
            size_t const dim = out_dims[i];
            dims[i] = winsize[i] = dim;
            winoffs[i] = 0;
            strides[i] = stride;
            stride *= dim;
        }
        elsize = elbytes;
    }
    // set output tensor and window all at once.
    // might be worth writing this out as a single 'for' loop.

    template <typename ITType>
    API_EXPORT TileStoreWindowBase(Tensor &otensor, ITType const &itens, TensorShape<RANK> const &offset,
                                   TensorShape<RANK> const &out_shape, unsigned elbytes)
        : TileStoreWindowBase(otensor, out_shape, elbytes)
    {
        set_window(itens, offset);
    }

    template <typename ITType>
    TileStoreWindowBase(Tensor &otensor, ITType const &itens, TensorShape<RANK> const &offset,
                        std::array<size_t, RANK> const &out_shape, unsigned elbytes)
        : TileStoreWindowBase(otensor, out_shape, elbytes)
    {
        set_window(itens, offset);
    }

    // set a window with the size taken from the given tensor, and the offset
    // from the given ShapeTensor.
    // 'tens' can also be a Shape<4>.
    //
    template <typename TType> API_EXPORT inline void set_window(TType const &tens, TensorShape<RANK> const &offset)
    {
        size_t const *windims;
        size_t tens_rank = 0;
        if constexpr (std::is_same<TType, Shape<4>>::value || std::is_same<TType, Shape<5>>::value) {
            windims = tens.dims.data();
            tens_rank = tens.RankVal;
        } else {
            windims = tens.get_dims().first;
            tens_rank = tens.rank();
        }
        size_t delta = 0;
        int dim_offset = 0;
        if (tens_rank + 1 == RANK) {
            winsize[0] = 1;
            dim_offset = 1;
        }

        unsigned len = 0;
        for (int i = 0; i < RANK; ++i) {
            unsigned const offs = offset.dim(i);

            if (1 == dim_offset) {
                len = (0 == i) ? 1 : windims[i - dim_offset];
            } else {
                len = windims[i];
            }
            assert(len > 0 && offs + len <= dims[i]);
            winoffs[i] = offs;
            winsize[i] = len;
            delta += offs * strides[i];
        }
        ptrw = (void *)((char *)ptr + delta);
    }

    // It may make make sense to add other ctors, for other uses of the
    // same kind of thing.
    // Once the structure is set up, only winsize[], strides[] and ptrw are used
    // by the write_tile method.
};

template <unsigned ELBYTES, unsigned RANK = 4> class TileStoreWindow : public TileStoreWindowBase<RANK> {
  public:
    API_EXPORT TileStoreWindow(Tensor &otensor, TensorShape<RANK> const &out_shape)
        : TileStoreWindowBase<RANK>(otensor, out_shape, ELBYTES)
    {
    }
    API_EXPORT TileStoreWindow(Tensor &otensor, std::array<size_t, RANK> out_dims)
        : TileStoreWindowBase<RANK>(otensor, out_dims, ELBYTES)
    {
    }
    template <typename ITType>
    API_EXPORT TileStoreWindow(Tensor &otensor, ITType const &itens, TensorShape<RANK> const &offset,
                               TensorShape<RANK> const &out_shape)
        : TileStoreWindowBase<RANK>(otensor, itens, offset, out_shape, ELBYTES)
    {
    }

    template <typename ITType>
    TileStoreWindow(Tensor &otensor, ITType const &itens, TensorShape<RANK> const &offset,
                    std::array<size_t, RANK> const &out_shape)
        : TileStoreWindowBase<RANK>(otensor, itens, offset, out_shape, ELBYTES)
    {
    }
    // store a tile to the window, at the given b,h,w,d
    // These are relative to the window size.
    //
    //  b,h,w,d can be any value >=0 and < the window size in that dim; exceptions being
    //   (1) h and w can be <0 (but some of the tile must still fall in range; so they
    //       must be at least -(tht-1) and -(TW-1) resp.
    //   (2) 'd' must be a multiple of TD=32.
    //
    // Tile dims are TW=8 (or TW=4 for ELBYTES=2), TD=32,
    // Tile height tht is adjustable, coded into the lower 5 bits of 'flags',
    // and <=8; if the lower 5 bits are zero, the default tile height is used, which
    // is 8.
    //
    // The input 'tiledata' must be a vector aligned pointer to 'tht' tile row.
    // For ELBYTES=1,2 or 4, a tile row is 256 bytes;.
    // (i.e. a tile row is TW*TD*ELBYTES bytes).
    // The only other thing in flags is optional tileExt::unshuffled, which
    // applies only when ELBYTES=2, and indicates that the tiledata is not shuffled
    // i.e it is 4*32*int16, rather than 2 x { 32*2*int16}.
    //
    API_EXPORT void write_tile(unsigned flags, void const *tiledata, size_t b, int h, int w, int d);

    // this is to support element_addr virtual method in  TileStoreWindowTensor
    API_EXPORT void *element_addr(SIdx const indices[RANK]) const
    {
        int offset = 1;
        for (int i = 0; i < RANK - 1; ++i) {
            offset += indices[i] * TileStoreWindowBase<RANK>::strides[i];
        }
        offset += indices[RANK - 1] * ELBYTES;
        return (void *)((char *)TileStoreWindowBase<RANK>::ptrw + offset);
    }
};

// this is the same as a TileStoreWindow, but also supports a general Tensor
// output interface via virtual methods.
// It must be constructed with reference to a TensorContiguous of rank 4 and matching dtype.
// It internally creates a reference to that tensor's interface object, so
// that t(b,h,w,d) works. .
//
// The only reason you need to have the exact dtype is to make get_dtype_intfc work,
// and so that get_raw and get_raw_addr have the expected return types. If you use a different
// dtype of the correct element size, everything else will work (and get_raw will work with a different
// return type of the same size).
// So there is a way to make a more generic one of these based on element size
// only, to support use cases where we want the same code to handle QUint8 and QUint8 for instance.
// The t(b,h,w,d) method will still convert correctly to/from float, since the interface object is taken
// from 'otensor':
//     TileStoreWindowTensorGeneric<ELBYTES>
//

template <DType DT> class TileStoreWindowTensor : public FakeTensor {
    using element_type = typename dtype_traits<DT>::element_type;
    static constexpr unsigned Rank = 4;
    TileStoreWindow<sizeof(element_type)> ts_window;
    hnnx::InterfaceRef const intfc;

  public:
    struct traits {
        static constexpr DType dtype = DT;
        using element_type = typename dtype_traits<dtype>::element_type;
        using raw_type = typename dtype_traits<dtype>::raw_type;
    };
    API_EXPORT TileStoreWindowTensor(Tensor &otensor, TensorShape<4> const &out_shape)
        : FakeTensor(nullptr), ts_window(otensor, out_shape), intfc(otensor.interface())
    {
    }

    template <typename ITType>
    API_EXPORT TileStoreWindowTensor(Tensor &otensor, ITType const &itens, TensorShape<4> const &offset,
                                     TensorShape<4> const &out_shape)
        : FakeTensor(nullptr), ts_window(otensor, itens, offset, out_shape), intfc(otensor.interface())
    {
    }

    template <typename TType> API_EXPORT inline void set_window(TType const &tens, TensorShape<4> const &offset)
    {
        ts_window.template set_window<TType>(tens, offset);
    }
    template <typename... ind_types> API_EXPORT inline element_type *get_raw_addr(ind_types... inds)
    {
        static_assert(Rank == (sizeof...(ind_types)), "# of coords must match Rank");
        const std::array<SIdx, Rank> coords = {{static_cast<SIdx>(inds)...}};
        return (element_type *)element_ptr(Rank, coords.data());
    }
    template <typename... ind_types> API_EXPORT inline element_type &get_raw(ind_types... inds)
    {
        static_assert(Rank == (sizeof...(ind_types)), "# of coords must match Rank");
        const std::array<SIdx, Rank> coords = {{static_cast<SIdx>(inds)...}};
        return *(element_type *)this->element_addr(Rank, coords.data());
    }

  protected:
    API_EXPORT virtual void *element_addr(size_t rank, SIdx const coords_in[],
                                          hnnx::InterfaceRef *iref = nullptr) const noexcept override
    {
        assert(rank == Rank);
        if (iref) *iref = intfc;
        return ts_window.element_addr(coords_in);
    }

  public:
    API_EXPORT virtual size_t rank() const noexcept override { return Rank; }
    API_EXPORT virtual hnnx::InterfaceRef interface() const noexcept override final { return intfc; }
    API_EXPORT virtual size_t dim(size_t index) const noexcept override { return ts_window.win_dim(index); }
    API_EXPORT virtual std::pair<size_t const *, size_t> get_dims() const noexcept override
    {
        return ts_window.get_windims();
    }

    API_EXPORT virtual inline bool set_dims(const size_t dims[]) override final
    {
        for (int i = 0; i < Rank; i++) {
            assert(dims[i] == ts_window.win_dim(i));
        }
        return false;
    }
    API_EXPORT virtual inline bool set_dims(const Tensor &prototype) override final
    {
        auto [dims_p, dims_n] = prototype.get_dims();
        assert(dims_n == Rank);
        return set_dims(dims_p);
    }

    API_EXPORT virtual inline void set_valid_dims(const size_t new_dims[]) override final
    {
        for (int i = 0; i < Rank; i++) {
            assert(new_dims[i] <= ts_window.win_dim(i));
        }
        // AMINE TODO: update TileStoreWindowBase to handle valid dims correctly
    }
    // AMINE TODO: update TileStoreWindowBase to handle valid dims correctly
    virtual inline DynamicStatus get_dynamic_state() const override { return DynamicStatus::ValidData; }

    API_EXPORT virtual DTypeScaleOff get_dtype_intfc() const noexcept override
    {
        // @@FIXME - could be resolved at compile time by mapping DT->Interface_t
        return DTypeScaleOff(DT, *intfc.get_qparms());
    }

    API_EXPORT virtual void write_tile(unsigned flags, void const *buffer, size_t b, int h, int w, int d) override final
    {
        ts_window.write_tile(flags, buffer, b, h, w, d);
    }
    // We don't support actually doing read_tile, but we need to implement it in case someone calls
    // write_tile_strategy.
    API_EXPORT virtual void const *read_tile(unsigned flags, void *buffer, size_t b, int h, int w,
                                             int d) const override final
    {
        assert((flags & write_strategy) != 0);
        return buffer; // always fail on write_tile_strategy.
    }
    API_EXPORT virtual unsigned tile_support_bits() const override final
    {
        return hnnx::tileExt_priv::tile_support_flags_for<tile_fast, element_type>::value;
    }
};

template <unsigned ELBYTES> class TileStoreWindowTensorGeneric {
    static_assert(false && ELBYTES, "not specialized for this value of ELBYTES");
};

template <> class TileStoreWindowTensorGeneric<1> : public TileStoreWindowTensor<DType::QUInt8> {
};
template <> class TileStoreWindowTensorGeneric<2> : public TileStoreWindowTensor<DType::QUInt16> {
};
template <> class TileStoreWindowTensorGeneric<4> : public TileStoreWindowTensor<DType::Int32> {
};

//
// generic utilities:
// raw_copy_via_tiles<ELBYTES>: this copies 'raw data' from 'in' to 'out' using tile operations.
// Caller must ensure that both tensors have ELBYTES per element, and both types support the tile
// interface.
// All tile operations are 8 rows high, even on 32-bit tiles.
//
// This is instantiated for ELBYTES = 1,2,4 in tile_extract.cc
template <unsigned ELBYTES> API_FUNC_EXPORT int raw_copy_by_tiles(Tensor &out, Tensor const &in, unsigned flags = 0);

} // namespace tileExt

POP_VISIBILITY()

#endif /* TILE_EXTRACT_H_ */
