//==============================================================================
//
// Copyright (c) 2024 Qualcomm Technologies, Inc.
// All Rights Reserved.
// Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================

#ifndef DYNAMIC_TENSOR_H
#define DYNAMIC_TENSOR_H

#ifdef __cplusplus
struct DynamicStatus {
#endif // __cplusplus
    enum DynamicTensorErrorCode {
        ValidData = 0,
        SemiValidData = 1,
        InvalidData = 2,
        InPlace = 3,
        Fallback = 4,
        NonInplace = 4, // alias to fallback
        InvalidConfig = 5
    };
#ifdef __cplusplus
    static bool skip_execute(const DynamicStatus ec)
    {
        bool retVal;
        switch (DynamicTensorErrorCode(ec)) {
        case ValidData:
        case SemiValidData:
        case Fallback:
            retVal = false;
            break;
        default:
            retVal = true;
            break;
        }
        return retVal;
    }
    DynamicStatus(const DynamicTensorErrorCode ec) : error_code(ec) {}
    explicit DynamicStatus(const int ec) : error_code(static_cast<DynamicTensorErrorCode>(ec)) {}
    bool operator==(const DynamicTensorErrorCode ec) const { return error_code == ec; }
    bool operator!=(const DynamicTensorErrorCode ec) const { return error_code != ec; }
    int to_int() const { return static_cast<int>(error_code); }
    explicit operator DynamicTensorErrorCode() const { return error_code; }
    explicit operator bool() const { return !skip_execute(error_code); }
    static bool failed_execute(const DynamicStatus ec) { return DynamicTensorErrorCode(ec) == InvalidConfig; }

  private:
    DynamicTensorErrorCode error_code;
};

#endif // __cplusplus

#endif // DYNAMIC_TENSOR_H
