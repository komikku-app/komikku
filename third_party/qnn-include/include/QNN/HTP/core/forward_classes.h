//==============================================================================
//
// Copyright (c) 2020, 2023 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef HEXNN_FORWARD_CLASSES_H
#define HEXNN_FORWARD_CLASSES_H 1

#include <memory>

#include "weak_linkage.h"
PUSH_VISIBILITY(default)

class Graph;
class Op;
class OpDef;
class Tensor;
class Interface;
template <unsigned TRank> class TensorShape;
template <typename T> class PlainInterface;
template <typename T> class ScaleOffsetInterface;

namespace hnnx {

class Serializer;
class Deserializer;
struct ShapeFlags;

// this is a deleter for class T, for use in uniqe_ptr, by default it has the same
// effect as default_delete, but it can be created
// with a parameter 'true' that will cause it to do nothing instead of normal deletion.
template <typename T> class DeleterWithDisable {
    bool skip_delete;

  public:
    API_FUNC_EXPORT DeleterWithDisable() : skip_delete(false) {}
    API_FUNC_EXPORT explicit DeleterWithDisable(bool skip) : skip_delete(skip) {}
    API_FUNC_EXPORT DeleterWithDisable(DeleterWithDisable const &) = default;
    API_FUNC_EXPORT DeleterWithDisable &operator=(DeleterWithDisable const &) = default;
    // this conversion allows us to convert a unique_ptr<T> to unique_ptr<T,DeleterWithDisable<T> >
    API_FUNC_EXPORT DeleterWithDisable(std::default_delete<T> const &) : skip_delete(false) {}
    API_FUNC_EXPORT void operator()(T const *p) const;
    API_FUNC_EXPORT inline bool delete_disabled() const { return skip_delete; }
};
template <typename T> API_FUNC_EXPORT void DeleterWithDisable<T>::operator()(T const *p) const
{
    if (!skip_delete) delete p;
}

extern template class DeleterWithDisable<Op>;
extern template class DeleterWithDisable<Tensor>;

typedef DeleterWithDisable<Op> Op_Deleter;
typedef DeleterWithDisable<Tensor> Tensor_Deleter;
typedef std::unique_ptr<Op, Op_Deleter> uptr_Op;
typedef std::unique_ptr<Tensor, Tensor_Deleter> uptr_Tensor;

// this can be applied to a uptr_Op or uptr_Tensor;
// it will return true if the skip flag is set (i.e the object
// is in a crate).
//
template <typename TA, typename TB>
API_FUNC_EXPORT inline bool is_in_crate(std::unique_ptr<TA, DeleterWithDisable<TB>> &tp)
{
    return tp.get() != nullptr && tp.get_deleter().delete_disabled();
}

} // namespace hnnx

POP_VISIBILITY()

#endif
