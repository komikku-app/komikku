//==============================================================================
//
// Copyright (c) 2021-2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef SERDES_TENSORS_H
#define SERDES_TENSORS_H 1

#include "forward_classes.h"

namespace hnnx {

// common header SerTensorConn, DeserTensorConn
// these are classes, stored within the Serialize and Deserialize objects,
// which deal with the serialization of connectivity.
//

////////////////////////////
// Assuming no forward refs:
// -------------------------
//     ser.tensor_def(tp)  Associates the next sequential index (starting with 1) with tp,
//                               but does not store it;.
//     ser.tensor_ref(tp)  Stores the index previously associated with tp
//
//  On deserializing:
//     deser.tensor_def(tp)  Associates the next sequential index (starting with 1) with tp,
//                               by appending tp to a vector;
//     deser.tensor_ref(Tensor *&tp)
//                              reads the index, reads the tp from a table.
//
// To support forward refs
// -----------------------------------
//
// We assign 'forward index' codes to tensor_ref for which there is yet no tensor_def.
// This is described first in terms of decoder actions, which are simpler:
//
//   (1) deser.tensor_ref reads a word containing a tensor index. If it has bit 30 set, it is instead the first
//       word of an update sequence (described below), below, which is folled by an index word.
//       - If the index word has "00" in bits 31:30, then it's the index of a tensor previously defined by tensor_def
//       - otherwise, msbs are "10", and the word is a 'forward index, 0x80000000+k; the decoder stores the
//         adddress of the tensor pointer to be updated in a linear table at offset [k]. The first k seen will be 0;
//         each subsequent will be most 1 greater than any any previous.
//
//   (2) deser.tensor_def defines a tensor; its address is appended to an array used to resolve the normal tensor_ref.
//
//   (3) as mentioned, sometimes when expecting the index for tensor_deser, we obtain a word with bit 30 set. This flags
//       a sequence of one of more 'update records', followed by the index.
//       Each update record encodes a tensor index (previously defined via tensor_def) and one or more 'forward indices'
//       which are resolved by that tensor. The decoder sets all of the corresponding pointers immediately, since forward
//       reference indices resolved may be reused.
//
// The enooder thus acts as follows:
//
//   We have a vector<int> "forward_allocation_table" for the forward index 'k' values; this contains a set of linked-lists,
//   each value is actually the index  of the next value in the chain, with -1 marking the end; there is a free list.
//   Each entry in the list at [k] (except free records) represents an unresolved forward reference which was encoded as
//   0x80000000+k. Each linked-list corresponds to a specific tensor, the head being in tensor_index_map.
//
//   There is a map<Tensor *,unsigned>  "tensor_index_map" which represents what index is assigned to each tensor.
//   When the value has "00" in the upper  bits, it means tensor_def has been done, and that's the assigned index value.
//   Otherwise it has "10" in upper bits, it is the most recent forward index 0x80000000+k, and 'k' is the 'head' pointer
//   into the forward index table.
//
//   When a tensor_def is done:
//      - tensor is assigned the next sequential index.
//      - if there is already an entry, it must be a forward index: this, and the new index, are
//         placed on 'pending_update_pairs' list to generate a resolution record at the next opportunity.
//      - in any case the tensor is assigned the next sequential index, and tensor_index_map is updates.
//
//   When a tensor_ref is done:
//      First, any pending update records are processed, described below [see note [+]]
//      if the tensor has been defined via tensor_def, we simply encode the index assigned to it.
//      Otherwise:
//         - we assign an available forward index to it, by taking one from the free list, or
//           growing the forward index table.
//         - if the tensor is not in the map already,y the new entry will have a link of -1, otherwise
//           the new entry links to the existing chain.
//         - in either case, the tensor_index_map will now point to the index of the new entry in the chain.
//
//     To process a pending update record for a forward index 'k', and a associated tensor index:
//          - encode the new index value, the forward index 'k', and any subsequent indicies in the table,
//            following the chain; and in the process, we put all the table entries in the free list.
//
// note [+]: a forward index freed in this step, may be reused immediately after to represente a different
// tensor's forward reference; so the decoder must resolve them immediately.
//
// one little problem ... if the deserialize interface uses 'need_fixup' to apply one fixup
// to two or more tensors, there's no way to represent that in the data above.. I think the proper
// way to fix this is to eliminate this practice from the code base, so that the 'serialize' process will
// be able to do all the work - in the meantime I've made 'pending_tensor_updates' table a pair of pointers,
// so that we can have up to two tensor pointers set from each deserialize event, with little overhead.

class SerTensorConnDefs {
  public:
    typedef unsigned tensor_idx;
    typedef Tensor const *ptr_type;
    static constexpr tensor_idx LOWER_BIT_MASK = 0x3FFFFFFF;
};

} // namespace hnnx

#endif // SERDES_TENSORS_H
