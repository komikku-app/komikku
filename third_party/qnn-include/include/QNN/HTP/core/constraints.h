//==============================================================================
//
// Copyright (c) 2020 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef CONSTRAINTS_H
#define CONSTRAINTS_H

#include "interface_defs.h"
#include "op_def.h"

#include <cstddef>
#include <cstdint>

namespace constraint_lib {

/** \defgroup OptConstraint Constraint Expressions for Optimization Rules
 * \ingroup OptimizationFuncs
 *
 * @{
 */
//! Find the chunksize of a given tensor type in a given dimension (a constant).
/// For instance, LAYOUT_CHUNKSIZE(QUint8CroutonTensor,3) gives size_t(32)
///
#define LAYOUT_CHUNKSIZE(TYPENAME, IDX) (TYPENAME::layout.ChunkSizes[(IDX)])

// some convenience wrappers...

//! IS_FLOAT16("operand") -> bool   (true if operand has Float16 output)
#define IS_FLOAT16(X) EQ(DTYPE_OF(X), DType::Float16)

//! IS_FLOAT32("operand") -> bool   (true if operand has float output)
#define IS_FLOAT32(X) EQ(DTYPE_OF(X), DType::Float32)

//! IS_FLOAT("operand") -> bool   (alias of IS_FLOAT32)
#define IS_FLOAT(X) IS_FLOAT32(X)

//! IS_QUINT8("operand") -> bool   (true if operand has 'QUInt8' output)
#define IS_QUINT8(X) EQ(DTYPE_OF(X), DType::QUInt8)

//! IS_QINT8("operand") -> bool (true if operand has 'QInt8' output)
#define IS_QINT8(X) EQ(DTYPE_OF(X), DType::QInt8)

//! IS_QINT16("operand") -> bool   (true if operand has 'QInt16' output)
#define IS_QINT16(X) EQ(DTYPE_OF(X), DType::QInt16)

//! IS_QUINT16("operand") -> bool   (true if operand has 'QUInt16' output)
#define IS_QUINT16(X) EQ(DTYPE_OF(X), DType::QUInt16)

//! IS_QINT32("operand") -> bool   (true if operand has 'QInt32' output)
#define IS_QINT32(X) EQ(DTYPE_OF(X), DType::QInt32)
//! IS_INT32("operand") -> bool   (true if operand has 'Int32' output)
#define IS_INT32(X) EQ(DTYPE_OF(X), DType::Int32)

//! IS_INT64("operand") -> bool   (true if operand has 'Int64' output)
#define IS_INT64(X) EQ(DTYPE_OF(X), DType::Int64)

//! IS_QUANT_TYPE("operand") -> bool (true if operand has 'Quantized' output)
#define IS_QUANT_TYPE(X) OR(IS_QUINT8(X), IS_QINT8(X), IS_QINT16(X), IS_QUINT16(X), IS_QINT32(X))
//! IS_QUANT_SIGNED("operand") -> bool (true if operand has 'Signed Quantized' output)
#define IS_QUANT_SIGNED(X) OR(IS_QINT32(X), IS_QINT16(X), IS_QINT8(X))
//! IS_SIGNED_SYMM("operand") -> bool (true if operand has 'Signed Quantized' output with offset == 0)
#define IS_SIGNED_SYMM(X) AND(IS_QUANT_SIGNED(X), EQ(ZERO_OFFSET_OF(X), 0))

// The problem with IS_SIGNED_SYMM is that it tends to get used as
//  AND( IS_QINT8(X), IS_SIGNED_SYMM(X))
// which expands to X.dtype==qint8 && ( (X.dtype ==qint32 || X.dtype == .. ) && X.zero_offs == 0)
// So, use IS_QINT8_SYMM(X) etc instead.

//! IS_QINT8_SYMM("operand") -> bool (true if operand has QINT8 output with offset == 0)
#define IS_QINT8_SYMM(X) AND(IS_QINT8(X), EQ(ZERO_OFFSET_OF(X), 0))
//! IS_QINT16_SYMM("operand") -> bool (true if operand has QINT16 output with offset == 0)
#define IS_QINT16_SYMM(X) AND(IS_QINT16(X), EQ(ZERO_OFFSET_OF(X), 0))
//! IS_QINT32_SYMM("operand") -> bool (true if operand has QINT32 output with offset == 0)
#define IS_QINT32_SYMM(X) AND(IS_QINT32(X), EQ(ZERO_OFFSET_OF(X), 0))

//! IS_FULLY_CONNECT_WEIGHT("operand") -> bool (true if operand is QUInt8 or (QInt8 and symmetrically quantized))
#define IS_FULLY_CONNECT_WEIGHT(X) OR(IS_QUINT8(X), IS_QINT8_SYMM(X))

//! IS_FLOAT16_BOTH("operand", "operand") -> bool (true if both operands are FP16 type)
#define IS_FLOAT16_BOTH(X, Y) AND(IS_FLOAT16(X), IS_FLOAT16(Y))
//! IS_FLOAT16_ALL("operand", ...) -> bool (true if all operands are FP16 type)
#define IS_FLOAT16_ALL(...) IS_DTYPE_ALL(DType::Float16, __VA_ARGS__)
//! IS_FLOAT32_ALL("operand", ...) -> bool (true if all operands are FP32 type)
#define IS_FLOAT32_ALL(...) IS_DTYPE_ALL(DType::Float32, __VA_ARGS__)

//! DIM_CHANNEL("operand") -> unsigned (extract depth dimension, #4)
#define DIM_CHANNEL(X) DIM_OF(X, 4)
//! DIM_DEPTH("operand") -> unsigned (extract depth dimension, #3)
#define DIM_DEPTH(X) DIM_OF(X, 3)
//! DIM_WIDTH("operand") -> unsigned (extract width dimension, #2)
#define DIM_WIDTH(X) DIM_OF(X, 2)
//! DIM_HEIGHT("operand") -> unsigned (extract height dimension, #1)
#define DIM_HEIGHT(X) DIM_OF(X, 1)
//! DIM_BATCHES("operand") -> unsigned (extract batches dimension, #0)
#define DIM_BATCHES(X) DIM_OF(X, 0)

//! DIM_NFILTS("operand") -> unsigned (extract 'output depth' dimension from filter weights, #3)
#define DIM_NFILTS(X) DIM_OF(X, 3)
//! DIM_FILTDEPTH("operand") -> unsigned (extract 'input depth' dimension from filter weights, #2)
#define DIM_FILTDEPTH(X) DIM_OF(X, 2)
//! DIM_FILTWIDTH("operand") -> unsigned (extract 'filter width' dimension from filter weights, #1)
#define DIM_FILTWIDTH(X) DIM_OF(X, 1)
//! DIM_FILTHEIGHT("operand") -> unsigned (extract 'filter height' dimension from filter weights, #0)
#define DIM_FILTHEIGHT(X) DIM_OF(X, 0)

#define MAX_SPARSE_ELEMENTS(X) DIM_OF(X, (MAX_DIMENSIONS - 1))

//! IS_EMPTY_DIM("operand", dim) -> bool (true if size of dim is 0)
#define IS_EMPTY_DIM(X, DIM) EQ(DIM_OF(X, DIM), 0)

//! IS_EMPTY("operand") -> bool (true if size of all dims is 0)
#define IS_EMPTY(X) AND(IS_EMPTY_DIM(X, 0), IS_EMPTY_DIM(X, 1), IS_EMPTY_DIM(X, 2), IS_EMPTY_DIM(X, 3))

} // namespace constraint_lib
/** @} */

#endif
