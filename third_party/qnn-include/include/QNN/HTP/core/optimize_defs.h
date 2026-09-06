//==============================================================================
//
// Copyright (c) 2020-2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef OPTIMIZE_DEFS_H
#define OPTIMIZE_DEFS_H 1

// this file contains #define that need to be seen by the optimization rule parser,
// in addition to the C++ code. Don't place any #include in here.
//

/**
 * \defgroup OptConstraint
 * @{
 */

#define IS_SCALAR(X) AND(EQ(DIM_BATCHES(X), 1), EQ(DIM_HEIGHT(X), 1), EQ(DIM_WIDTH(X), 1), EQ(DIM_DEPTH(X), 1))

#define IS_NOT_SCALAR(X) OR(NE(DIM_BATCHES(X), 1), NE(DIM_HEIGHT(X), 1), NE(DIM_WIDTH(X), 1), NE(DIM_DEPTH(X), 1))

#define IS_SHAPE_1x1x1xd(X) AND(EQ(DIM_BATCHES(X), 1), EQ(DIM_HEIGHT(X), 1), EQ(DIM_WIDTH(X), 1), NE(RANK_OF(X), 5))

#define IS_SHAPE_1x1x1x1xc(X)                                                                                          \
    AND(EQ(RANK_OF(X), 5), EQ(DIM_BATCHES(X), 1), EQ(DIM_HEIGHT(X), 1), EQ(DIM_WIDTH(X), 1), EQ(DIM_DEPTH(X), 1))

#define IS_1HD_H1D(A, B)                                                                                               \
    AND(EQ(DIM_HEIGHT(A), 1), NE(DIM_WIDTH(A), 1), EQ(DIM_WIDTH(B), 1), EQ(DIM_WIDTH(A), DIM_HEIGHT(B)),               \
        EQ(DIM_DEPTH(A), DIM_DEPTH(B)))

/// @brief A constant specifying the standard split for output depth
// NOTE: HEXNNVVV-330 workaround: stay at 32 channels or FP test starts to fail...
#define MIN_CHANNEL_SPLIT_SIZE 32
#define CHANNEL_SPLIT_SIZE     256

// TCM_SIZE and TCM_TOOBIG are renamed to TCM_MAXTENSOR_SIZE and TCM_MAXTENSOR_HALF_SIZE
// to help with tilling on 2x4mb auto hardware
// where total tcm size is multiple of tiling size
//#define TCM_SIZE OPTION_UINT("tcm_size")
//#define TCM_TOOBIG DIV(OPTION_UINT("tcm_size"), 2)

// TCM_MAXTENSOR_HALF_SIZE is one half of the TCM tiling size
#define TCM_MAXTENSOR_SIZE      OPTION_UINT("tcm_size_for_tiling")
#define TCM_MAXTENSOR_HALF_SIZE DIV(OPTION_UINT("tcm_size_for_tiling"), 2)

// Used in depth slicing, where height slicing has not been done
// Depth could be 1.
#define ELEMWISE_TILE_SIZE(ACT)                                                                                        \
    MUL(TILE_HEIGHT, ROUNDUP(MUL(DIM_WIDTH(ACT), ELEMENTSIZE_OF(ACT)), 8),                                             \
        ROUNDUP(MIN(CHANNEL_SPLIT_SIZE, DIM_DEPTH(ACT)), 32))

#define ELEMWISE_TOOBIG(A, B, OUT)                                                                                     \
    GT(ADD(ELEMWISE_TILE_SIZE(A), ELEMWISE_TILE_SIZE(B), ELEMWISE_TILE_SIZE(OUT)), TCM_MAXTENSOR_SIZE)

#define WEIGHT_STORAGE(WEIGHT, SPLIT)                                                                                  \
    MUL(ELEMENTSIZE_OF(WEIGHT), DIM_FILTHEIGHT(WEIGHT), DIM_FILTWIDTH(WEIGHT), ROUNDUP(DIM_FILTDEPTH(WEIGHT), 32),     \
        MIN(SPLIT, DIM_NFILTS(WEIGHT)))

#define ACT_STORAGE_EST(WEIGHT, ACT, SPLIT)                                                                            \
    ADD(MUL(ROUNDUP(ADD(7, DIM_FILTHEIGHT(WEIGHT)), 8), ROUNDUP(MUL(DIM_WIDTH(ACT), ELEMENTSIZE_OF(ACT)), 8),          \
            ROUNDUP(DIM_DEPTH(ACT), 32)),                                                                              \
        MUL(8, ROUNDUP(MUL(DIM_WIDTH("*"), ELEMENTSIZE_OF("*")), 8), SPLIT))

#define GOOD_WEIGHTS(WEIGHT, ACT, SPLIT)                                                                               \
    LT(ADD(WEIGHT_STORAGE(WEIGHT, SPLIT), ACT_STORAGE_EST(WEIGHT, ACT, SPLIT)),                                        \
       SELECT(EQ(ELEMENTSIZE_OF(WEIGHT), 1), TCM_MAXTENSOR_HALF_SIZE, MUL(DIV(TCM_MAXTENSOR_SIZE, 2048), 1041)))

/* controls when we can be more aggressive with tiling */
#define CAN_FINE_SPLIT EQ(OPTION_UINT("can_fine_split"), 1)

#define BIG_WIDTH_SIZE OPTION_UINT("big_width_split")

#define DO_BIG_WIDTH_SPLIT                                                                                             \
    AND(GT(DIM_WIDTH("*"), BIG_WIDTH_SIZE), CAN_FINE_SPLIT, GE(DIM_DEPTH("*"), 4), GT(DIM_HEIGHT("*"), 1))

#define AUTOTHREAD_ENABLED OPTION_INT("enable_autothread")

// Helper to decide if we should auto thread
#define SHOULD_AUTOTHREAD                                                                                              \
    AND(NOT(OPTION_BOOL("central_tiler")), GT(DATA_SIZE("*"), MUL(OPTION_INT("autothread_size_kb"), 1024)))
// When autothreading on width, beware of rounding based on element size
#define SHOULD_AUTOTHREAD1(ACT1)       AND(SHOULD_AUTOTHREAD, EQ(ELEMENTSIZE_OF("*"), ELEMENTSIZE_OF(ACT1)))
#define SHOULD_AUTOTHREAD2(ACT1, ACT2) AND(SHOULD_AUTOTHREAD1(ACT1), EQ(ELEMENTSIZE_OF("*"), ELEMENTSIZE_OF(ACT2)))
#define SHOULD_AUTOTHREAD3(ACT1, ACT2, ACT3)                                                                           \
    AND(SHOULD_AUTOTHREAD2(ACT1, ACT2), EQ(ELEMENTSIZE_OF("*"), ELEMENTSIZE_OF(ACT3)))

// This is used for some unary operators so that they are supertiled the same way
// as binary ops even though they have a smaller footprint
// In particular Sqrt(Mul(x,y))...
#define SHOULD_AUTOTHREAD_UNARY                                                                                        \
    AND(NOT(OPTION_BOOL("central_tiler")), GT(MUL(3, DATA_SIZE("*")), MUL(OPTION_INT("autothread_size_kb"), 2 * 1024)))
// When autothreading on width, beware of rounding based on element size
#define SHOULD_AUTOTHREAD_UNARY1(ACT) AND(SHOULD_AUTOTHREAD_UNARY, EQ(ELEMENTSIZE_OF("*"), ELEMENTSIZE_OF(ACT)))

/*
 * "Choose the maximum channel split size that doesn't make the slice of weights too big"
 */
#define SMART_CHANNEL_SIZE(WEIGHT_STR, ACT_STR)                                                                        \
    SELECT(GOOD_WEIGHTS(WEIGHT_STR, ACT_STR, CHANNEL_SPLIT_SIZE), CHANNEL_SPLIT_SIZE,                                  \
           SELECT(GOOD_WEIGHTS(WEIGHT_STR, ACT_STR, DIV(CHANNEL_SPLIT_SIZE, 2)), DIV(CHANNEL_SPLIT_SIZE, 2),           \
                  SELECT(GOOD_WEIGHTS(WEIGHT_STR, ACT_STR, DIV(CHANNEL_SPLIT_SIZE, 4)), DIV(CHANNEL_SPLIT_SIZE, 4),    \
                         32)))

// For HMX DWC
#define DWC_ACT_STORAGE_EST(WEIGHT, ACT, SPLIT)                                                                        \
    ADD(MUL(ROUNDUP(ADD(7, DIM_FILTHEIGHT(WEIGHT)), 8), ROUNDUP(MUL(DIM_WIDTH(ACT), ELEMENTSIZE_OF(ACT)), 8), SPLIT),  \
        MUL(8, ROUNDUP(MUL(DIM_WIDTH("*"), ELEMENTSIZE_OF("*")), 8), SPLIT))

#define DWC_GOOD_WEIGHTS(WEIGHT, ACT, SPLIT)                                                                           \
    LT(ADD(WEIGHT_STORAGE(WEIGHT, SPLIT), DWC_ACT_STORAGE_EST(WEIGHT, ACT, SPLIT)), DIV(TCM_MAXTENSOR_SIZE, 2))

#define DWC_SMART_CHANNEL_SIZE(WEIGHT_STR, ACT_STR)                                                                    \
    SELECT(DWC_GOOD_WEIGHTS(WEIGHT_STR, ACT_STR, CHANNEL_SPLIT_SIZE), CHANNEL_SPLIT_SIZE,                              \
           SELECT(DWC_GOOD_WEIGHTS(WEIGHT_STR, ACT_STR, DIV(CHANNEL_SPLIT_SIZE, 2)), DIV(CHANNEL_SPLIT_SIZE, 2),       \
                  SELECT(DWC_GOOD_WEIGHTS(WEIGHT_STR, ACT_STR, DIV(CHANNEL_SPLIT_SIZE, 4)),                            \
                         DIV(CHANNEL_SPLIT_SIZE, 4), 32)))

// For other depthwise ops
#define MAX_CHANNEL_SIZE(ACT)                                                                                          \
    ROUNDUP(MAX(1, DIV(TCM_MAXTENSOR_HALF_SIZE,                                                                        \
                       MUL(ROUNDUP(DIM_HEIGHT(ACT), 8), ROUNDUP(MUL(DIM_WIDTH(ACT), ELEMENTSIZE_OF(ACT)), 8)))),       \
            32)

/// @brief SAME_QUANT("A", "B") -> true if the operands have the same stepsize and zero offset
#define SAME_QUANT(OPA, OPB)                                                                                           \
    OR(AND(EQ(STEPSIZE_OF(OPA), STEPSIZE_OF(OPB)), EQ(ZERO_OFFSET_OF(OPA), ZERO_OFFSET_OF(OPB))),                      \
       AND(IS_FLOAT16(OPA), IS_FLOAT16(OPB)), AND(IS_FLOAT32(OPA), IS_FLOAT32(OPB)))

/// @brief SAME_DTYPE_QUANT("A", "B") -> true if the operands have the same dtype, stepsize and zero offset
#define SAME_DTYPE_QUANT(OPA, OPB)                                                                                     \
    AND(EQ(DTYPE_OF(OPA), DTYPE_OF(OPB)), EQ(STEPSIZE_OF(OPA), STEPSIZE_OF(OPB)),                                      \
        EQ(ZERO_OFFSET_OF(OPA), ZERO_OFFSET_OF(OPB)), NOT(OPTION_BOOL("quant_is_updateable")))

/// @brief MIN_QU8(X) -> min of range defined by a scale/offset for a qu8 tensor
#define MIN_QU8(X) MUL(STEPSIZE_OF(X), MUL(-1.0f, ZERO_OFFSET_OF(X)))

/// @brief MAX_QU8(X) -> max of range defined by a scale/offset for a qu8 tensor
#define MAX_QU8(X) MUL(STEPSIZE_OF(X), SUB(255.0f, ZERO_OFFSET_OF(X)))

/// @brief OPCONST(X) enforces that op X is a Const during pattern matching
#define OPCONST(X) LET(X, Op("$Const"))

/// @brief OPCONST_DDR(X) enforces that op Name is a Const during pattern matching
///   the "constant_crouton_from_ddr" is discarded in final cleanup and the
///   constant is loaded from memory. It is used in contexts where crouton format
///   is expected.
#define OPCONST_DDR(Name)      Op("constant_crouton_from_ddr", Op("ForceFormat_Crouton", OPCONST(Name)))
#define OPCONST_FLAT_DDR(Name) Op("constant_flat_from_ddr", OPCONST(Name))

/// @brief OPCONST_TCM(X) enforces that op Name is a Const during pattern matching
///   the "constant_crouton_to_vtcm" will be converted to a sequence to load
///   the constant into TCM memory during final cloeanup
#define OPCONST_TCM(Name)      Op("constant_crouton_to_vtcm", OPCONST(Name))
#define OPCONST_FLAT_TCM(Name) Op("constant_flat_to_vtcm", OPCONST(Name))

// How wide should the output tile be?
// Well,
// * We have HEX_VTCM_MB - WEIGHT_STORAGE available from VTCM
// * Input is roughly (input height * input depth) * (1+filter-related-value) * WIDTH
// * Output is output depth * 8 * WIDTH
// So we should take (HEX_VTCM_MB-WEIGHT_STORAGE) and divide by
//     (input height * input depth) * (1+filter-related-value) + (OUTPUT DEPTH*8)
// And then round down to a multiple of 8 probably
// But we need to do at least 8 wide

// need "Too big" indication for constraint (which might be "big total width"), then
// need to tile into at least ~4 chunks to actually shrink size.

#define SUBS(a, b)      SELECT(GT(a, b), SUB(a, b), 0)
#define MIN_WIDTH_OF(A) SELECT(EQ(ELEMENTSIZE_OF(A), 1), 8, SELECT(EQ(ELEMENTSIZE_OF(A), 2), 4, 2))
#define MIN_WIDTH       8

#define ESTIMATE_TENSOR_SIZE(T)                                                                                        \
    MUL(DIM_BATCHES(T), ROUNDUP(DIM_HEIGHT(T), TILE_HEIGHT), ROUNDUP(MUL(DIM_WIDTH(T), ELEMENTSIZE_OF(T)), 8),         \
        ROUNDUP(DIM_DEPTH(T), 32))

#define ESTIMATE_SIZE(ACT, WEIGHTS, OUT)                                                                               \
    ADD(MUL(DIM_BATCHES(ACT),                                                                                          \
            ROUNDUP(ADD(DIM_HEIGHT(ACT), SELECT(EQ(DIM_FILTHEIGHT(WEIGHTS), 1), 0, 8)), TILE_HEIGHT),                  \
            ROUNDUP(ADD(MUL(DIM_WIDTH(ACT), ELEMENTSIZE_OF(ACT)), SELECT(EQ(DIM_FILTWIDTH(WEIGHTS), 1), 0, 8)), 8),    \
            ROUNDUP(DIM_DEPTH(ACT), 32)),                                                                              \
        ROUNDUP(WEIGHT_STORAGE(WEIGHTS, DIM_NFILTS(WEIGHTS)), 2048), ESTIMATE_TENSOR_SIZE(OUT),                        \
        ROUNDUP(MUL(8, ROUNDUP(DIM_DEPTH(OUT), 32)), 2048))

#define ESTIMATE_SIZE_QUANT(ACT, WEIGHTS, OUT)                                                                         \
    ADD(MUL(DIM_BATCHES(ACT),                                                                                          \
            ROUNDUP(ADD(DIM_HEIGHT(ACT), SELECT(EQ(DIM_FILTHEIGHT(WEIGHTS), 1), 0, 8)), TILE_HEIGHT),                  \
            ROUNDUP(ADD(MUL(DIM_WIDTH(ACT), ELEMENTSIZE_OF(ACT)), SELECT(EQ(DIM_FILTWIDTH(WEIGHTS), 1), 0, 8)), 8),    \
            ROUNDUP(DIM_DEPTH(ACT), 32)),                                                                              \
        ROUNDUP(MUL(WEIGHT_STORAGE(WEIGHTS, DIM_NFILTS(WEIGHTS)), 2), 2048), ESTIMATE_TENSOR_SIZE(OUT),                \
        ROUNDUP(MUL(8, ROUNDUP(DIM_DEPTH(OUT), 32)), 2048))

// minimum size required for convolution
#define GET_IN_SIZE(OUT_SIZE, FILT_SIZE, STRIDE, DILATION)                                                             \
    ADD(MUL(SUB(OUT_SIZE, 1), STRIDE), MUL(SUB(FILT_SIZE, 1), DILATION), 1)

#define ESTIMATE_MIN_SIZE(ACT, WEIGHTS, STRIDE, DIL_H, DIL_W)                                                          \
    ADD(MUL(ROUNDUP(ADD(GET_IN_SIZE(8, DIM_FILTHEIGHT(WEIGHTS), DIM_HEIGHT(STRIDE), DIL_H),                            \
                        SELECT(EQ(DIM_FILTHEIGHT(WEIGHTS), 1), 0, 8)),                                                 \
                    8),                                                                                                \
            ROUNDUP(ADD(MUL(GET_IN_SIZE(MIN_WIDTH_OF(ACT), DIM_FILTWIDTH(WEIGHTS), DIM_WIDTH(STRIDE), DIL_W),          \
                            ELEMENTSIZE_OF(ACT)),                                                                      \
                        SELECT(EQ(DIM_FILTWIDTH(WEIGHTS), 1), 0, 8)),                                                  \
                    8),                                                                                                \
            ROUNDUP(DIM_DEPTH(ACT), 32)),                                                                              \
        ROUNDUP(WEIGHT_STORAGE(WEIGHTS, 32), 2048), 2048, 2048)

#define ESTIMATE_SIZE_ALIGNED_SLICE(ACT, START, OUT)                                                                   \
    ADD(SELECT(EQ(DIM_WIDTH(START), 0), ESTIMATE_TENSOR_SIZE(ACT), MUL(ESTIMATE_TENSOR_SIZE(ACT), 2)),                 \
        ESTIMATE_TENSOR_SIZE(OUT))

// minimum width to make convolution fit into vtcm
#define MAX_GOOD_WIDTH_CONV(ACT_STR, WEIGHT_STR, OUT_STR, STRIDE, DILATION, TCMSIZE)                                   \
    MAX(ROUNDUP(DIV(SUBS(DIV(MUL(TCMSIZE, 7), 8),                                                                      \
                         ADD(ROUNDUP(WEIGHT_STORAGE(WEIGHT_STR, DIM_NFILTS(WEIGHT_STR)), 2048),                        \
                             MUL(ROUNDUP(DIM_HEIGHT(ACT_STR), 8), ROUNDUP(DIM_DEPTH(ACT_STR), 32),                     \
                                 SUB(DIM_FILTWIDTH(WEIGHT_STR), 1), DILATION, ELEMENTSIZE_OF(ACT_STR)))),              \
                    MUL(4, ADD(MUL(ELEMENTSIZE_OF(OUT_STR), ROUNDUP(DIM_DEPTH(OUT_STR), 32),                           \
                                   ROUNDUP(DIM_HEIGHT(OUT_STR), 8)),                                                   \
                               MUL(ELEMENTSIZE_OF(ACT_STR), ROUNDUP(DIM_HEIGHT(ACT_STR), 8), STRIDE,                   \
                                   ROUNDUP(DIM_DEPTH(ACT_STR), 32))))),                                                \
                MIN_WIDTH_OF(OUT_STR)),                                                                                \
        MIN_WIDTH_OF(OUT_STR))

#define MAX_GOOD_WIDTH_CONV_QUANT(ACT_STR, WEIGHT_STR, OUT_STR, STRIDE, DILATION, TCMSIZE)                             \
    MAX(ROUNDUP(DIV(SUBS(DIV(MUL(TCMSIZE, 7), 8),                                                                      \
                         ADD(ROUNDUP(MUL(WEIGHT_STORAGE(WEIGHT_STR, DIM_NFILTS(WEIGHT_STR)), 2), 2048),                \
                             MUL(ROUNDUP(DIM_HEIGHT(ACT_STR), 8), ROUNDUP(DIM_DEPTH(ACT_STR), 32),                     \
                                 SUB(DIM_FILTWIDTH(WEIGHT_STR), 1), DILATION, ELEMENTSIZE_OF(ACT_STR)))),              \
                    MUL(4, ADD(MUL(ELEMENTSIZE_OF(OUT_STR), ROUNDUP(DIM_DEPTH(OUT_STR), 32),                           \
                                   ROUNDUP(DIM_HEIGHT(OUT_STR), 8)),                                                   \
                               MUL(ELEMENTSIZE_OF(ACT_STR), ROUNDUP(DIM_HEIGHT(ACT_STR), 8), STRIDE,                   \
                                   ROUNDUP(DIM_DEPTH(ACT_STR), 32))))),                                                \
                MIN_WIDTH_OF(OUT_STR)),                                                                                \
        MIN_WIDTH_OF(OUT_STR))

#define MAX_GOOD_WIDTH(ACT_STR, WEIGHT_STR, OUT_STR, TCMSIZE)                                                          \
    MAX_GOOD_WIDTH_CONV(ACT_STR, WEIGHT_STR, OUT_STR, 1, 1, TCMSIZE)

// Tile CURRENT into equal-size chunks, less than or equalt to size TARGET
#define EVEN_TILE_UNDER_CUSTOM(CURRENT, TARGET, ROUNDER) ROUNDUP(DIV(CURRENT, ADD(DIV(CURRENT, TARGET), 1)), ROUNDER)

// Stays adaptive until CURRENT = 2*TARGET, then just tiles to size TARGET consistently
// Balances the need to avoid the horrible case where you tile 260 into 256, with the benefits of consistent tiling
// (that is, maybe avoiding lots of concat - retile operations in a large network
#define EVEN_TILE_UNDER_CUTOFF2_CUSTOM(CURRENT, TARGET, ROUNDER)                                                       \
    SELECT(GT(CURRENT, MUL(2, TARGET)), TARGET, EVEN_TILE_UNDER_CUSTOM(CURRENT, TARGET, ROUNDER))

// Gotta be small enough to fit into VTCM, and then another factor of 4 so that we exploit parallelism.
// Using half of VTCM instead of the full VTCM, as a precaution  / to let other ops run in parallel
// Gotta use DIM_CHANNEL(ACT_STR) since there's no channel-tiling the activation unless it's DWC, and stride 2 isn't.
// Has to be rounded up to 16, because it must be a multiple of 8 in the end, and space2depth divides width by 2
// Default is 256; smaller tiles usually get better performance, and size 256 doesn't incur much overhead.
// Also, for the activation, multiplying height by 2 (becase that's how it'll get tiled if "*" is tiled to TILE_HEIGHT)
// and multiplying the whole thing by 2 (because whatever we tile "*" to, we tile ACT to 2x, due to stride).
//
// Sometimes, we need to tile all the way down to 8; hence, we can specify whether to round to 16 or to 8
#define SMART_EARLY_WIDTH_S2(ACT_STR, WEIGHT_STR, OUT_STR, TCMSIZE, ROUNDER)                                           \
    EVEN_TILE_UNDER_CUTOFF2_CUSTOM(                                                                                    \
            DIM_WIDTH(OUT_STR),                                                                                        \
            MIN(256, ROUNDUP(MAX(16, DIV(SUBS(DIV(MUL(TCMSIZE, 7), 16),                                                \
                                              ROUNDUP(WEIGHT_STORAGE(WEIGHT_STR,                                       \
                                                                     MIN(DIM_NFILTS(WEIGHT_STR), CHANNEL_SPLIT_SIZE)), \
                                                      2048)),                                                          \
                                         ADD(MUL(2, ELEMENTSIZE_OF(ACT_STR), DIM_BATCHES(ACT_STR),                     \
                                                 MUL(TILE_HEIGHT, 2), ROUNDUP(DIM_DEPTH(ACT_STR), 32)),                \
                                             MUL(ELEMENTSIZE_OF(OUT_STR), DIM_BATCHES(ACT_STR), TILE_HEIGHT,           \
                                                 MIN_CHANNEL_SPLIT_SIZE)))),                                           \
                             ROUNDER)),                                                                                \
            ROUNDER)

#define SMART_EARLY_WIDTH_ADAPTIVE_ROUNDING_S2(ACT_STR, WEIGHT_STR, OUT_STR, TCMSIZE)                                  \
    SELECT(AND(IS_QUINT8(WEIGHT_STR), IS_QUINT16(ACT_STR)),                                                            \
           SMART_EARLY_WIDTH_S2(ACT_STR, WEIGHT_STR, OUT_STR, TCMSIZE, 8),                                             \
           SMART_EARLY_WIDTH_S2(ACT_STR, WEIGHT_STR, OUT_STR, TCMSIZE, 16))

#define FLAT_TENSOR_SIZE(T)                                                                                            \
    ROUNDUP(MUL(ELEMENTSIZE_OF(T), DIM_BATCHES(T), DIM_HEIGHT(T), DIM_WIDTH(T), DIM_DEPTH(T)), 2048)

// Tile the width based on the input and output size
// for channel_shuffle op.
#define MAX_GOOD_WIDTH_CHANSHUF(ACT_STR, OUT_STR, TCMSIZE)                                                             \
    MAX(MIN_WIDTH,                                                                                                     \
        ROUNDUP(DIV(DIV(MUL(TCMSIZE, 6), 8), MUL(4, ADD(MUL(ELEMENTSIZE_OF(ACT_STR), ROUNDUP(DIM_HEIGHT(ACT_STR), 8),  \
                                                            ROUNDUP(DIM_DEPTH(ACT_STR), 32)),                          \
                                                        MUL(ELEMENTSIZE_OF(OUT_STR), ROUNDUP(DIM_HEIGHT(OUT_STR), 8),  \
                                                            ROUNDUP(DIM_DEPTH(OUT_STR), 32))))),                       \
                8))

// Tile the width based on the input and output size
// for quantize op.
#define MAX_GOOD_WIDTH_QUANTIZE(IN, OUT, TCMSIZE)                                                                      \
    MAX(MIN_WIDTH_OF(OUT),                                                                                             \
        DIV(TCMSIZE, MUL(4, ADD(MUL(ELEMENTSIZE_OF(IN), ROUNDUP(DIM_HEIGHT(IN), 8), ROUNDUP(DIM_DEPTH(IN), 32)),       \
                                MUL(ELEMENTSIZE_OF(OUT), ROUNDUP(DIM_HEIGHT(OUT), 8), ROUNDUP(DIM_DEPTH(IN), 32))))))

#define MAX_GOOD_WIDTH_ELEMWISE(FIRST_IN_STR, SECOND_IN_STR, OUT_STR, TCMSIZE)                                         \
    MAX(MIN_WIDTH, ROUNDUP(DIV(DIV(MUL(TCMSIZE, 6), 8),                                                                \
                               MUL(4, ADD(MUL(ELEMENTSIZE_OF(FIRST_IN_STR), ROUNDUP(DIM_HEIGHT(FIRST_IN_STR), 8),      \
                                              ROUNDUP(DIM_DEPTH(FIRST_IN_STR), 32)),                                   \
                                          MUL(ELEMENTSIZE_OF(SECOND_IN_STR), ROUNDUP(DIM_HEIGHT(SECOND_IN_STR), 8),    \
                                              ROUNDUP(DIM_DEPTH(SECOND_IN_STR), 32)),                                  \
                                          MUL(ELEMENTSIZE_OF(OUT_STR), ROUNDUP(DIM_HEIGHT(OUT_STR), 8),                \
                                              ROUNDUP(DIM_DEPTH(OUT_STR), 32))))),                                     \
                           8))

#define ESTIMATE_H1_MATMUL_SIZE(A, B)                                                                                  \
    ADD(MUL(8, ROUNDUP(DIM_WIDTH(A), 8), ROUNDUP(DIM_DEPTH(A), 32), ELEMENTSIZE_OF(A)),                                \
        MUL(1, 1, ROUNDUP(DIM_WIDTH(B), 32), ROUNDUP(DIM_DEPTH(B), 32), ELEMENTSIZE_OF(B)),                            \
        MUL(8, ROUNDUP(DIM_WIDTH("*"), 8), ROUNDUP(DIM_DEPTH("*"), 32), ELEMENTSIZE_OF("*")))

// Smartly select a suitable width for FP16 batchnorm operation.
// starting from initial width and then halving it.
#define SMART_BATCHNORM_WIDTH(IN_STR, WEIGHTS, OUT_STR, INITIAL_WIDTH)                                                 \
    SELECT(OR(GT(DIM_WIDTH(IN_STR), INITIAL_WIDTH),                                                                    \
              NOT(AND(GT(DIM_WIDTH(IN_STR), DIV(INITIAL_WIDTH, 2)),                                                    \
                      GT(ADD(ESTIMATE_TENSOR_SIZE(OUT_STR), ESTIMATE_TENSOR_SIZE(IN_STR),                              \
                             ESTIMATE_TENSOR_SIZE(WEIGHTS)),                                                           \
                         TCM_MAXTENSOR_SIZE)))),                                                                       \
           INITIAL_WIDTH, DIV(INITIAL_WIDTH, 2))

/** @} */

#define CONST_ZERO_OFF(OPERAND) gen_ConstScalar_i32(ZERO_OFFSET_OF(OPERAND))

// wrap tile_height option for better usability

#define TILE_HEIGHT OPTION_UINT("tile_height")

// These are used to help optimize graphs when the relaxed_precision_flag is set
#define CAST_TO_DTYPE(X, DTYPE) WITH_OUTPUT_TYPE(DTYPE, 0, 1.0f, Op(FROM_DEFAULT_PACKAGE("QNN_Cast"), X))

#define CAST_TO_FP16(X) WITH_SIZE(X, CAST_TO_DTYPE(X, DType::Float16))

#define CAST_TO_FP32(X) CAST_TO_DTYPE(X, DType::Float32)

#define MAKE_OP_FP16_AND_INSERT_CAST(OP) CAST_TO_FP32(WITH_SIZE("*", WITH_OUTPUT_TYPE(DType::Float16, 0, 1.0f, OP)))

#define IS_BINARY_FP16(A, B, Out) AND(IS_FLOAT16(A), IS_FLOAT16(B), IS_FLOAT16(Out))

#define IS_BINARY_FP32(A, B, Out) AND(IS_FLOAT32(A), IS_FLOAT32(B), IS_FLOAT32(Out))

#define FP16_CONST_CAST(X, Y) LET(X, Op(FROM_DEFAULT_PACKAGE("Cast_fp32_to_fp16_plain"), Y))

#define FP16_CONST_CASTSLICE(X, Y, Z)                                                                                  \
    LET(X, Op(FROM_DEFAULT_PACKAGE("SlicePad_shape_inplace"),                                                          \
              LET(Y, Op(FROM_DEFAULT_PACKAGE("Cast_fp32_to_fp16_plain"), Z)), "Before", "Start", "Out", "Zero"))

#define CONVERT_BINARY_OP_TO_FP16(OP, A, B)                                                                            \
    DEF_OPTIM(CLEANUP_GRAPH+130, relaxed_precision_flag, Op(OP, A, B), IS_BINARY_FP32(A, B, "*"),                          \
              MAKE_OP_FP16_AND_INSERT_CAST(Op(OP, CAST_TO_FP16(A), CAST_TO_FP16(B))))

// These are used to reshape 1xHx1xD or 1x1xWxD QUint8CroutonTensor/QUint16CroutonTensor
#define SHAPE_FROM_W1(A)                                                                                               \
    SELECT(IS_QUINT8(A), gen_Shape(DIM_BATCHES(A), DIV(ADD(DIM_HEIGHT(A), 7), 8), 8, DIM_DEPTH(A)),                    \
           gen_Shape(DIM_BATCHES(A), DIV(ADD(DIM_HEIGHT(A), 3), 4), 4, DIM_DEPTH(A)))

#define REARRANGE_FROM_W1(A) WITH_SIZE(SHAPE_FROM_W1(A), WITH_TYPE(WITH_SAME_ID("*", A), Op("space_rearrange", A)))

// Use this instead of the one above when "A" is a text string instead of an operator constructor
#define REARRANGE_FROM_W1_OP(A) WITH_SIZE(SHAPE_FROM_W1(A), WITH_TYPE(A, Op("space_rearrange", A)))

#define REARRANGE_TO_W1(OP) Op("space_rearrange", WITH_SIZE(SHAPE_FROM_W1("*"), WITH_TYPE("*", OP)))

#define SHAPE_FROM_H1(A)                                                                                               \
    SELECT(IS_QUINT8(A),                                                                                               \
           gen_Shape(DIM_BATCHES(A), MIN(8, DIV(ADD(DIM_WIDTH(A), 7), 8)), MUL(DIV(ADD(DIM_WIDTH(A), 63), 64), 8),     \
                     DIM_DEPTH(A)),                                                                                    \
           gen_Shape(DIM_BATCHES(A), MIN(8, DIV(ADD(DIM_WIDTH(A), 3), 4)), MUL(DIV(ADD(DIM_WIDTH(A), 31), 32), 4),     \
                     DIM_DEPTH(A)))

#define REARRANGE_FROM_H1(A) WITH_SIZE(SHAPE_FROM_H1(A), WITH_TYPE(A, Op("space_rearrange", A)))

#define REARRANGE_TO_H1(OP) Op("space_rearrange", WITH_SIZE(SHAPE_FROM_H1("*"), WITH_TYPE("*", OP)))

// This is intended to be seen only by the external parser, not by the C++ compiler.
// DEF_OPTIM is mapped to DEF_OPTIM_PARSE(...), with the PRIO and FLAGS parameter both
// string-quoted -- this allows these to have non-conformant (non-lispy) syntax,
// without complicating the parser;
// and DEF_OPT(PRIO,PAT...) is just DEF_OPRIM_PARSE("prio","0"...)

#ifdef EXTERNAL_DEFOPT_PARSER
#define DEF_OPTIM(PRIO, FLAGS, PAT, CST, REP) DEF_OPTIM_PARSE(#PRIO, #FLAGS, PAT, CST, REP)
#define DEF_OPT(PRIO, PAT, CST, REP)          DEF_OPTIM_PARSE(#PRIO, "0", PAT, CST, REP)

// Some DEF_OPT use this
#define MAX_DIMENSIONS 8

// FIXME - maybe the parser should understand FROM_DEFAULT_PACKAGE("opname") directly
#define FROM_DEFAULT_PACKAGE(name) name

#endif

// ------------- Batch to Space/Depth to Space Ops ----------------

// Checks whether the given width slice factor is enough
// or should it be halved.
#define GOOD_WIDTH_DEPTHTOSPACE_CHECK(IN_STR, OUT_STR, SLICE)                                                          \
    AND(GT(DIM_WIDTH(OUT_STR), SLICE),                                                                                 \
        GT(ADD(ESTIMATE_TENSOR_SIZE(IN_STR), ESTIMATE_TENSOR_SIZE(OUT_STR)), TCM_MAXTENSOR_SIZE))

// Searches for a good slice factor starting from the
// initial slice factor (WIDTH_SLICE_FACTOR) and
// halves it until the slice does not fit into vtcm.
// This macro is supposed to start with initial slice factor of 128,
// then check for 64 and then 32 if required.
#define SMART_WIDTH_DEPTHTOSPACE(IN_STR, OUT_STR, WIDTH_SLICE_FACTOR)                                                  \
    SELECT(GOOD_WIDTH_DEPTHTOSPACE_CHECK(IN_STR, OUT_STR, WIDTH_SLICE_FACTOR), WIDTH_SLICE_FACTOR,                     \
           SELECT(GOOD_WIDTH_DEPTHTOSPACE_CHECK(IN_STR, OUT_STR, DIV(WIDTH_SLICE_FACTOR, 2)),                          \
                  DIV(WIDTH_SLICE_FACTOR, 2), DIV(WIDTH_SLICE_FACTOR, 4)))

// 1) conv + act fusion should only happen if conv doesnt feed any other op
// 2) User can override this with the "force_conv_fusion" set opt
// 3) In the case that the graph is a QNN model with --debug (per layer outputs) enabled
// we still want the fusion to happen so that the final graph output matches the non --debug version
// QNN's --debug is used purely for accuracy debugging, so the performance impact from the duplication
// of the conv op isnt a concern
#define ACT_FUSION_MULTI_OUT_CHECK(OP)                                                                                 \
    OR(EXTERNAL_CONSTRAINT(has_only_one_consumer, OP), OPTION_BOOL("force_conv_fusion"),                               \
       AND(PRODUCER_FOR(OP, "*Output"), EXTERNAL_CONSTRAINT(has_n_consumers, OP, 2), PRODUCER_FOR("*", "*Output")))

// For central tiler, just return true but for legacy evaluate the
// conjunction of the arguments

// This should be used to separate predicate into semantic and tiling preference options
// where the tiling preferences (typically references to target TCM size) should be
// wrappered in this macro.
#define SHOULD_TILE(...) OR(OPTION_BOOL("central_tiler"), AND(__VA_ARGS__))

// if u8, w>8 && w%8 == 0
// if u16, h == 1 && (w == 8 || w == 16), reshape into h_new = w/4, w_new = 4, in order to utilize
// the low power implementation of convolution
// else
// if u16, w>4 && w%4 == 0, not fully utilizing the crouton, but still much better performance
// TODO: Will remove the second rule once the space rearrange is fully implemented to reshape
// the entire model from Input toward the output
#define WIDTH_TO_HEIGHTX_CONSTRAINT(OPSTR)                                                                             \
    OR(AND(GT(DIM_WIDTH(OPSTR), TILE_HEIGHT), EQ(REM(DIM_WIDTH(OPSTR), TILE_HEIGHT), 0)),                              \
       AND(IS_QUINT16(OPSTR), GT(DIM_WIDTH(OPSTR), 4), EQ(REM(DIM_WIDTH(OPSTR), 4), 0)))

#define HEIGHTX_SHAPE(OPSTR)                                                                                           \
    SELECT(EQ(REM(DIM_WIDTH(OPSTR), TILE_HEIGHT), 0),                                                                  \
           SELECT(AND(EQ(DIM_HEIGHT(OPSTR), 1), LE(DIM_WIDTH(OPSTR), 16), IS_QUINT16(OPSTR),                           \
                      NOT(OPTION_BOOL("dynamic_graph_input"))),                                                        \
                  gen_Shape(DIM_BATCHES(OPSTR), DIV(DIM_WIDTH(OPSTR), 4), 4, DIM_DEPTH(OPSTR)),                        \
                  gen_Shape(DIM_BATCHES(OPSTR), MUL(DIM_HEIGHT(OPSTR), TILE_HEIGHT),                                   \
                            DIV(DIM_WIDTH(OPSTR), TILE_HEIGHT), DIM_DEPTH(OPSTR))),                                    \
           SELECT(EQ(REM(DIM_WIDTH(OPSTR), 4), 0),                                                                     \
                  gen_Shape(DIM_BATCHES(OPSTR), MUL(DIM_HEIGHT(OPSTR), 4), DIV(DIM_WIDTH(OPSTR), 4),                   \
                            DIM_DEPTH(OPSTR)),                                                                         \
                  gen_Shape(DIM_BATCHES(OPSTR), DIM_HEIGHT(OPSTR), DIM_WIDTH(OPSTR), DIM_DEPTH(OPSTR))))

#define HEIGHT84_SHAPE(OPSTR)                                                                                          \
    SELECT(EQ(REM(DIM_WIDTH(OPSTR), TILE_HEIGHT), 0),                                                                  \
           SELECT(AND(EQ(DIM_HEIGHT(OPSTR), 1), LE(DIM_WIDTH(OPSTR), 16), IS_QUINT16(OPSTR),                           \
                      NOT(OPTION_BOOL("dynamic_graph_input"))),                                                        \
                  gen_Shape(DIM_BATCHES(OPSTR), DIV(DIM_WIDTH(OPSTR), 4), 4, DIM_DEPTH(OPSTR)),                        \
                  gen_Shape(DIM_BATCHES(OPSTR), TILE_HEIGHT, DIV(DIM_WIDTH(OPSTR), TILE_HEIGHT), DIM_DEPTH(OPSTR))),   \
           gen_Shape(DIM_BATCHES(OPSTR), 4, DIV(DIM_WIDTH(OPSTR), 4), DIM_DEPTH(OPSTR)))

// Only perform reshape from height to width when the height is huge
#define HEIGHT_TO_WIDTH_SHAPE(OPSTR)                                                                                   \
    gen_Shape(DIM_BATCHES(OPSTR), TILE_HEIGHT, DIV(DIM_HEIGHT(OPSTR), TILE_HEIGHT), DIM_DEPTH(OPSTR))
#define HEIGHT_TO_WIDTH_CONSTRAINT(OPSTR)                                                                              \
    AND(GE(DIM_HEIGHT(OPSTR), 8192), EQ(REM(DIM_HEIGHT(OPSTR), TILE_HEIGHT), 0), EQ(DIM_WIDTH(OPSTR), 1),              \
        EQ(REM(DIM_DEPTH(OPSTR), 32), 0))

#endif
