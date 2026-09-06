# Vendored QAIRT (QNN) headers

Compile-time only headers (`include/QNN/`) for the optional Qualcomm NPU image
enhancement backend in `app/src/main/cpp/qnn_backend.cpp` (QNN C API v2.27).

Source: https://github.com/qualcomm/geniex-qairt-plugin (`qnn-api/include`),
Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries,
BSD-3-Clause licensed.

The QNN *runtime* libraries (`libQnnHtp.so`, per-arch HTP stub/skel libs, …) are
NOT vendored here; they are packaged from the `com.qualcomm.qti:qnn-runtime:2.49.0`
Maven artifact. Runtime versions are backward compatible with this compile-time
API. Model context binaries live in `app/src/main/assets/qnn-contexts/`.
