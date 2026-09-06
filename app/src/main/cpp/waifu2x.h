// waifu2x implemented with ncnn library

#ifndef WAIFU2X_H
#define WAIFU2X_H

#include <atomic>
#include <mutex>
#include <string>

// ncnn
#include "gpu.h"
#include "layer.h"
#include "net.h"

class Waifu2x {
public:
  Waifu2x(int gpuid, bool tta_mode = false, int num_threads = 0,
          int precision_mode = 0, bool fp16_arithmetic = false);
  ~Waifu2x();

  int load(const std::string &parampath, const std::string &modelpath);

  // Unified process method: runs inference and writes directly to output
  // in: inimage (RGBA planar)
  // out: out_pixels (RGBA packed), out_stride
  // lock: The JNI lock, passed in to allow early release of the GPU.
  int process(const ncnn::Mat &inimage, void *out_pixels, int out_stride,
              bool input_has_alpha, std::unique_lock<std::mutex> &lock,
              std::atomic<int> *progress_ptr = nullptr) const;
  int process_gpu(const ncnn::Mat &packed_input, void *out_pixels,
                  int out_stride, bool input_has_alpha,
                  std::atomic<int> *progress_ptr = nullptr) const;
  bool has_gpu_pipeline() const { return gpu_pipeline_available; }

public:
  // waifu2x parameters
  int noise;
  int scale;
  int tilesize;
  int prepadding;
  std::atomic<int> *progress_ptr = nullptr;
  std::atomic<int> *ui_busy_ptr = nullptr;
  std::atomic<bool> *should_abort_ptr = nullptr;
  int tile_sleep_ms = 0; // Sleep between tiles for cooling (0 = full speed)
  bool is_snapdragon = false;
  bool disable_grayscale_check = false;
  int num_threads = 1;
  int precision_mode = 0; // 0 = fp16, 1 = fp32, 2 = int8, 3 = bf16
  bool fp16_arithmetic = false;

private:
  ncnn::VulkanDevice *vkdev;
  ncnn::Net net;
  ncnn::Pipeline *waifu2x_preproc;
  ncnn::Pipeline *waifu2x_postproc;
  ncnn::Pipeline *waifu2x_preproc_tta;
  ncnn::Pipeline *waifu2x_postproc_tta;
  ncnn::Layer *bicubic_2x;
  bool tta_mode;
  bool gpu_pipeline_available = false;
};

#endif // WAIFU2X_H
