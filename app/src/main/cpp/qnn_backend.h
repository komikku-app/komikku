#ifndef MIHON_QNN_BACKEND_H
#define MIHON_QNN_BACKEND_H

#include <atomic>
#include <cstdint>
#include <string>

namespace qnn_backend {

bool is_runtime_loadable();
int architecture();
bool initialize(const std::string &context_path, int padding);
bool is_initialized();
int process_rgba(const uint8_t *input, int width, int height, int input_stride,
                 uint8_t *output, int output_stride,
                 std::atomic<int> *progress,
                 const std::atomic<bool> *should_abort);
void shutdown();

} // namespace qnn_backend

#endif // MIHON_QNN_BACKEND_H
