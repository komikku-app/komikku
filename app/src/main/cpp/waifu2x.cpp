#include "waifu2x.h"
#include "shaders.h"
#include "command.h"
#include "cpu.h"
#include <algorithm>
#include <android/log.h>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <functional>
#include <queue>
#include <sys/resource.h>
#include <sys/system_properties.h>
#include <thread>
#include <vector>

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#endif

#define TAG "Waifu2xNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
inline uint8x8_t floats_to_u8(float32x4_t low, float32x4_t high) {
  const float32x4_t zero = vdupq_n_f32(0.f);
  const float32x4_t max_value = vdupq_n_f32(255.f);
  low = vmaxq_f32(zero, vminq_f32(max_value, low));
  high = vmaxq_f32(zero, vminq_f32(max_value, high));
  const uint16x4_t low_u16 = vmovn_u32(vcvtq_u32_f32(low));
  const uint16x4_t high_u16 = vmovn_u32(vcvtq_u32_f32(high));
  return vmovn_u16(vcombine_u16(low_u16, high_u16));
}
#endif

class BoundedTaskPool {
public:
  BoundedTaskPool(size_t worker_count, size_t capacity)
      : capacity_(std::max<size_t>(1, capacity)) {
    for (size_t i = 0; i < std::max<size_t>(1, worker_count); i++) {
      workers_.emplace_back([this]() { run(); });
    }
  }

  ~BoundedTaskPool() {
    wait();
    {
      std::lock_guard<std::mutex> lock(mutex_);
      stopping_ = true;
    }
    work_available_.notify_all();
    for (std::thread &worker : workers_) {
      if (worker.joinable())
        worker.join();
    }
  }

  int64_t submit(std::function<void()> task) {
    const auto wait_start = std::chrono::steady_clock::now();
    std::unique_lock<std::mutex> lock(mutex_);
    capacity_available_.wait(lock, [this]() { return pending_ < capacity_; });
    const int64_t wait_us =
        std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now() - wait_start)
            .count();
    tasks_.push(std::move(task));
    pending_++;
    work_available_.notify_one();
    return wait_us;
  }

  bool add_second_worker() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (stopping_ || workers_.size() >= 2)
      return false;
    capacity_ = std::max<size_t>(capacity_, 3);
    workers_.emplace_back([this]() { run(); });
    capacity_available_.notify_all();
    work_available_.notify_one();
    return true;
  }

  void wait() {
    std::unique_lock<std::mutex> lock(mutex_);
    all_done_.wait(lock, [this]() { return pending_ == 0; });
  }

private:
  void run() {
    // Keep tile conversion behind Android's render and input threads.
    setpriority(PRIO_PROCESS, 0, 10);
    while (true) {
      std::function<void()> task;
      {
        std::unique_lock<std::mutex> lock(mutex_);
        work_available_.wait(lock,
                             [this]() { return stopping_ || !tasks_.empty(); });
        if (stopping_ && tasks_.empty())
          return;
        task = std::move(tasks_.front());
        tasks_.pop();
      }

      task();

      {
        std::lock_guard<std::mutex> lock(mutex_);
        pending_--;
      }
      capacity_available_.notify_one();
      all_done_.notify_all();
    }
  }

  size_t capacity_;
  std::vector<std::thread> workers_;
  std::queue<std::function<void()>> tasks_;
  std::mutex mutex_;
  std::condition_variable work_available_;
  std::condition_variable capacity_available_;
  std::condition_variable all_done_;
  size_t pending_ = 0;
  bool stopping_ = false;
};

} // namespace

Waifu2x::Waifu2x(int gpuid, bool _tta_mode, int _num_threads,
                 int _precision_mode, bool _fp16_arithmetic) {
  vkdev = gpuid == -1 ? 0 : ncnn::get_gpu_device(gpuid);
  const int detected_threads = ncnn::get_physical_big_cpu_count();
  num_threads = _num_threads > 0 ? std::clamp(_num_threads, 1, 4)
                                 : std::clamp(detected_threads - 1, 1, 3);
  LOGD("CPU helper threads: requested=%d detected_big=%d selected=%d",
       _num_threads, detected_threads, num_threads);
  precision_mode = _precision_mode;
  fp16_arithmetic = _fp16_arithmetic;
  net.opt.num_threads = num_threads;
  waifu2x_preproc = 0;
  waifu2x_postproc = 0;
  waifu2x_preproc_tta = 0;
  waifu2x_postproc_tta = 0;
  bicubic_2x = 0;
  tta_mode = _tta_mode;
  noise = 0;
  scale = 2;
  tilesize = 128;  // Balanced speed and memory
  prepadding = 18; // Slightly reduced padding for speed, safe for 256 tile size
  progress_ptr = nullptr;
}

Waifu2x::~Waifu2x() {
  delete waifu2x_preproc;
  delete waifu2x_postproc;
  delete waifu2x_preproc_tta;
  delete waifu2x_postproc_tta;
  if (bicubic_2x) {
    bicubic_2x->destroy_pipeline(net.opt);
    delete bicubic_2x;
  }
}

int Waifu2x::load(const std::string &parampath, const std::string &modelpath) {
  net.opt.use_vulkan_compute = vkdev ? true : false;
  net.opt.use_fp16_packed = false;
  net.opt.use_fp16_storage = false;
  net.opt.use_fp16_arithmetic = false;
  net.opt.use_int8_inference = false;
  net.opt.use_int8_packed = false;
  net.opt.use_int8_storage = false;
  net.opt.use_int8_arithmetic = false;
  net.opt.use_bf16_storage = false;
  net.opt.use_bf16_packed = false;

  switch (precision_mode) {
  case 1: // FP32
    break;
  case 2: // INT8
    net.opt.use_int8_inference = true;
    net.opt.use_int8_packed = true;
    net.opt.use_int8_storage = true;
    net.opt.use_int8_arithmetic = true;
    break;
  case 3: // BF16
    net.opt.use_bf16_storage = true;
    net.opt.use_bf16_packed = true;
    break;
  case 0: // FP16
  default:
    net.opt.use_fp16_packed = true;
    net.opt.use_fp16_storage = true;
    net.opt.use_fp16_arithmetic =
        fp16_arithmetic && vkdev && vkdev->info.support_fp16_arithmetic();
    break;
  }
  const bool fp16_arithmetic_supported =
      vkdev && vkdev->info.support_fp16_arithmetic();
  LOGD("Precision=%d FP16 arithmetic requested=%d supported=%d enabled=%d",
       precision_mode, fp16_arithmetic ? 1 : 0,
       fp16_arithmetic_supported ? 1 : 0,
       net.opt.use_fp16_arithmetic ? 1 : 0);

  // Packed RGBA8 is transported as one uint per pixel, so the fused path does
  // not require 8-bit shader storage or alter the model precision.
  gpu_pipeline_available = vkdev &&
                           (precision_mode == 1 ||
                            (precision_mode == 0 &&
                             vkdev->info.support_fp16_storage()));
  net.opt.use_packing_layout = true; // Enable packing for better performance

  // Additional optimizations (safe for all devices)
  net.opt.use_sgemm_convolution = true;    // Use SGEMM for convolution
  net.opt.use_winograd_convolution = true; // Winograd convolution
  net.opt.use_local_pool_allocator = true; // Better memory allocation
  net.opt.use_shader_local_memory = true;  // Use shader local memory

  net.opt.num_threads = num_threads;

  // Hardware-specific optimizations are already set in constructor
  // (use_subgroup_ops, use_cooperative_matrix, num_threads)
  // No need to override them here

  net.set_vulkan_device(vkdev);

  if (net.load_param(parampath.c_str()) != 0) {
    LOGE("Failed to load param: %s", parampath.c_str());
    return -1;
  }
  if (net.load_model(modelpath.c_str()) != 0) {
    LOGE("Failed to load model: %s", modelpath.c_str());
    return -1;
  }

  if (gpu_pipeline_available) {
    const bool fp16_shader = precision_mode == 0;
    const uint32_t *preproc_spirv = fp16_shader
                                        ? waifu2x_fused_preproc_fp16_spv
                                        : waifu2x_fused_preproc_fp32_spv;
    const size_t preproc_spirv_size =
        fp16_shader ? sizeof(waifu2x_fused_preproc_fp16_spv)
                    : sizeof(waifu2x_fused_preproc_fp32_spv);
    const uint32_t *postproc_spirv = fp16_shader
                                         ? waifu2x_fused_postproc_fp16_spv
                                         : waifu2x_fused_postproc_fp32_spv;
    const size_t postproc_spirv_size =
        fp16_shader ? sizeof(waifu2x_fused_postproc_fp16_spv)
                    : sizeof(waifu2x_fused_postproc_fp32_spv);

    const std::vector<ncnn::vk_specialization_type> specializations;
    waifu2x_preproc = new ncnn::Pipeline(vkdev);
    waifu2x_preproc->set_optimal_local_size_xyz(8, 8, 4);
    const int preproc_pipeline_result = waifu2x_preproc->create(
        preproc_spirv, preproc_spirv_size, specializations);

    waifu2x_postproc = new ncnn::Pipeline(vkdev);
    waifu2x_postproc->set_optimal_local_size_xyz(8, 8, 1);
    const int postproc_pipeline_result = waifu2x_postproc->create(
        postproc_spirv, postproc_spirv_size, specializations);
    LOGD("Embedded fused pipeline create: precision=%s preproc=%d postproc=%d",
         fp16_shader ? "fp16" : "fp32", preproc_pipeline_result,
         postproc_pipeline_result);
    gpu_pipeline_available =
        preproc_pipeline_result == 0 && postproc_pipeline_result == 0;

    if (!gpu_pipeline_available) {
      delete waifu2x_preproc;
      delete waifu2x_postproc;
      waifu2x_preproc = nullptr;
      waifu2x_postproc = nullptr;
      LOGE("GPU pre/post pipeline unavailable; using staged fallback");
    }
  }

  LOGD("Fused Vulkan pipeline %s",
       gpu_pipeline_available ? "enabled" : "disabled");

  // Create interp layer for bicubic alpha scaling
  bicubic_2x = ncnn::create_layer("Interp");
  if (!bicubic_2x) {
    LOGE("Failed to create Interp layer!");
    return -1;
  }

  bicubic_2x->vkdev = vkdev;
  ncnn::ParamDict pd;
  pd.set(0, 3); // bicubic
  pd.set(1, (float)scale);
  pd.set(2, (float)scale);
  bicubic_2x->load_param(pd);
  if (bicubic_2x->create_pipeline(net.opt) != 0) {
    LOGE("Failed to create Interp pipeline");
    return -1;
  }

  return 0;
}

int Waifu2x::process_gpu(const ncnn::Mat &packed_input, void *out_pixels,
                         int out_stride, bool input_has_alpha,
                         std::atomic<int> *progress_ptr) const {
  if (!gpu_pipeline_available || !vkdev || packed_input.empty() ||
      packed_input.elemsize != 4u) {
    return -1;
  }

  const int w = packed_input.w;
  const int h = packed_input.h;
  const int target_w = w * scale;
  const int target_h = h * scale;
  if (out_stride != target_w * 4)
    return -1;

  bool is_grayscale = !disable_grayscale_check;
  if (is_grayscale) {
    const unsigned char *pixels =
        static_cast<const unsigned char *>(packed_input.data);
    int color_pixels = 0;
    const int color_threshold = w * h / 200;
    for (int i = 0; i < w * h; i++) {
      const unsigned char *pixel = pixels + i * 4;
      if (std::abs((int)pixel[0] - (int)pixel[1]) > 5 ||
          std::abs((int)pixel[0] - (int)pixel[2]) > 5) {
        if (++color_pixels > color_threshold) {
          is_grayscale = false;
          break;
        }
      }
    }
  }

  ncnn::VkAllocator *blob_vkallocator = vkdev->acquire_blob_allocator();
  ncnn::VkAllocator *staging_vkallocator = vkdev->acquire_staging_allocator();
  if (!blob_vkallocator || !staging_vkallocator) {
    if (blob_vkallocator)
      vkdev->reclaim_blob_allocator(blob_vkallocator);
    if (staging_vkallocator)
      vkdev->reclaim_staging_allocator(staging_vkallocator);
    return -1;
  }

  struct AllocatorGuard {
    const ncnn::VulkanDevice *device;
    ncnn::VkAllocator *blob;
    ncnn::VkAllocator *staging;
    ~AllocatorGuard() {
      device->reclaim_blob_allocator(blob);
      device->reclaim_staging_allocator(staging);
    }
  } allocator_guard{vkdev, blob_vkallocator, staging_vkallocator};

  ncnn::Option opt = net.opt;
  opt.blob_vkallocator = blob_vkallocator;
  opt.workspace_vkallocator = blob_vkallocator;
  opt.staging_vkallocator = staging_vkallocator;

  ncnn::VkMat input_gpu;
  {
    ncnn::VkCompute upload(vkdev);
    upload.record_clone(packed_input, input_gpu, opt);
    const int upload_result = upload.submit_and_wait();
    if (upload_result != 0 || input_gpu.empty()) {
      LOGE("Fused upload failed: result=%d empty=%d", upload_result,
           input_gpu.empty() ? 1 : 0);
      return -1;
    }
  }

  ncnn::VkMat output_gpu;
  output_gpu.create(target_w, target_h, (size_t)4u, 1, blob_vkallocator);
  if (output_gpu.empty())
    return -1;

  const int xtiles = (w + tilesize - 1) / tilesize;
  const int ytiles = (h + tilesize - 1) / tilesize;
  const int tile_count = xtiles * ytiles;
  const size_t tile_elemsize =
      (opt.use_fp16_storage || opt.use_fp16_packed) ? 2u : 4u;
  const uint32_t heap_budget_mb = vkdev->get_heap_budget();
  int batch_capacity = 2;
  if (tile_sleep_ms > 0) {
    batch_capacity = 1;
  } else if (heap_budget_mb >= 768) {
    batch_capacity = 4;
  } else if (heap_budget_mb >= 384) {
    batch_capacity = 3;
  }
  batch_capacity = std::min(batch_capacity, tile_count);

  // Start with a short command buffer so the first UI interaction cannot be
  // trapped behind several tiles. Grow only when measured submissions are
  // short enough to remain friendly to frame scheduling.
  int batch_target = 1;
  LOGD("Fused Vulkan scheduling: heap_budget=%uMB batch_capacity=%d "
       "initial_batch=%d tiles=%d",
       heap_budget_mb, batch_capacity, batch_target, tile_count);

  const int input_tile_w = tilesize + prepadding * 2;
  const int input_tile_h = tilesize + prepadding * 2;
  std::vector<ncnn::VkMat> input_tile_slots(batch_capacity);
  const ncnn::Extractor empty_extractor = net.create_extractor();
  std::vector<ncnn::Extractor> extractor_slots;
  extractor_slots.reserve(batch_capacity);
  for (int i = 0; i < batch_capacity; i++) {
    input_tile_slots[i].create(input_tile_w, input_tile_h, 3, tile_elemsize,
                               1, blob_vkallocator);
    if (input_tile_slots[i].empty())
      return -1;
    extractor_slots.push_back(empty_extractor);
  }

  int batch_size = 0;
  int completed_tiles = 0;
  ncnn::VkCompute command(vkdev);
  std::vector<ncnn::VkMat> retained_mats;
  retained_mats.reserve(batch_capacity);

  if (net.input_indexes().empty() || net.output_indexes().empty())
    return -1;
  const int input_index = net.input_indexes()[0];
  const int output_index = net.output_indexes().back();

  for (int yi = 0; yi < ytiles; yi++) {
    for (int xi = 0; xi < xtiles; xi++) {
      bool paused_for_ui = false;
      while (ui_busy_ptr && ui_busy_ptr->load()) {
        paused_for_ui = true;
        if (should_abort_ptr && should_abort_ptr->load())
          return -1;
        std::this_thread::sleep_for(std::chrono::milliseconds(8));
      }
      if (paused_for_ui)
        batch_target = 1;
      if (should_abort_ptr && should_abort_ptr->load())
        return -1;

      const int x = xi * tilesize;
      const int y = yi * tilesize;
      const int tile_w = std::min(tilesize, w - x);
      const int tile_h = std::min(tilesize, h - y);
      ncnn::VkMat &input_tile_gpu = input_tile_slots[batch_size];

      {
        std::vector<ncnn::VkMat> bindings(2);
        bindings[0] = input_gpu;
        bindings[1] = input_tile_gpu;
        std::vector<ncnn::vk_constant_type> constants(13);
        constants[0].i = input_gpu.w;
        constants[1].i = input_gpu.h;
        constants[2].i = input_gpu.cstep;
        constants[3].i = input_tile_gpu.w;
        constants[4].i = input_tile_gpu.h;
        constants[5].i = input_tile_gpu.cstep;
        constants[6].i = prepadding;
        constants[7].i = prepadding;
        constants[8].i = x;
        constants[9].i = y;
        constants[10].i = 4;
        constants[11].i = 0;
        constants[12].i = 0;
        ncnn::VkMat dispatcher;
        dispatcher.w = input_tile_gpu.w;
        dispatcher.h = input_tile_gpu.h;
        dispatcher.c = 3;
        command.record_pipeline(waifu2x_preproc, bindings, constants,
                                dispatcher);
      }

      ncnn::VkMat output_tile_gpu;
      {
        ncnn::Extractor &extractor = extractor_slots[batch_size];
        extractor.set_blob_vkallocator(blob_vkallocator);
        extractor.set_workspace_vkallocator(blob_vkallocator);
        extractor.set_staging_vkallocator(staging_vkallocator);
        const int input_result = extractor.input(input_index, input_tile_gpu);
        const int extract_result =
            input_result == 0
                ? extractor.extract(output_index, output_tile_gpu, command)
                : -1;
        if (input_result != 0 || extract_result != 0 ||
            output_tile_gpu.empty()) {
          LOGE("Fused inference record failed: tile=%d slot=%d input=%d "
               "extract=%d empty=%d",
               yi * xtiles + xi, batch_size, input_result, extract_result,
               output_tile_gpu.empty() ? 1 : 0);
          return -1;
        }
      }

      const int output_tile_w = tile_w * scale;
      const int output_tile_h = tile_h * scale;
      const int expected_w = input_tile_w * scale;
      const int expected_h = input_tile_h * scale;
      const int full_output_tile_w = tilesize * scale;
      const int full_output_tile_h = tilesize * scale;
      int source_offset_x = 0;
      int source_offset_y = 0;
      if (output_tile_gpu.w >= expected_w &&
          output_tile_gpu.h >= expected_h) {
        source_offset_x = prepadding * scale;
        source_offset_y = prepadding * scale;
      } else if (output_tile_gpu.w >= full_output_tile_w &&
                 output_tile_gpu.h >= full_output_tile_h) {
        source_offset_x = 0;
        source_offset_y = 0;
      } else if (output_tile_gpu.w >= output_tile_w &&
                 output_tile_gpu.h >= output_tile_h) {
        source_offset_x = (output_tile_gpu.w - output_tile_w) / 2;
        source_offset_y = (output_tile_gpu.h - output_tile_h) / 2;
      }

      const int copy_w =
          std::min(output_tile_w, output_tile_gpu.w - source_offset_x);
      const int copy_h =
          std::min(output_tile_h, output_tile_gpu.h - source_offset_y);
      if (copy_w != output_tile_w || copy_h != output_tile_h) {
        LOGE("Fused tile shape mismatch: tile=%d output=%dx%d copy=%dx%d "
             "wanted=%dx%d offset=%d,%d",
             yi * xtiles + xi, output_tile_gpu.w, output_tile_gpu.h, copy_w,
             copy_h, output_tile_w, output_tile_h, source_offset_x,
             source_offset_y);
        return -1;
      }

      {
        std::vector<ncnn::VkMat> bindings(3);
        bindings[0] = output_tile_gpu;
        bindings[1] = output_gpu;
        bindings[2] = input_gpu;
        std::vector<ncnn::vk_constant_type> constants(16);
        constants[0].i = output_tile_gpu.w;
        constants[1].i = output_tile_gpu.h;
        constants[2].i = output_tile_gpu.cstep;
        constants[3].i = output_gpu.w;
        constants[4].i = output_gpu.h;
        constants[5].i = source_offset_x;
        constants[6].i = source_offset_y;
        constants[7].i = x * scale;
        constants[8].i = y * scale;
        constants[9].i = copy_w;
        constants[10].i = copy_h;
        constants[11].i = 4;
        constants[12].i = input_gpu.w;
        constants[13].i = input_gpu.h;
        constants[14].i = input_has_alpha ? 1 : 0;
        constants[15].i = is_grayscale ? 1 : 0;
        ncnn::VkMat dispatcher;
        dispatcher.w = copy_w;
        dispatcher.h = copy_h;
        dispatcher.c = 1;
        command.record_pipeline(waifu2x_postproc, bindings, constants,
                                dispatcher);
      }

      retained_mats.push_back(output_tile_gpu);
      batch_size++;

      const bool is_last_tile = xi == xtiles - 1 && yi == ytiles - 1;
      if (batch_size >= batch_target || is_last_tile) {
        bool paused_before_submit = false;
        while (ui_busy_ptr && ui_busy_ptr->load()) {
          paused_before_submit = true;
          if (should_abort_ptr && should_abort_ptr->load())
            return -1;
          std::this_thread::sleep_for(std::chrono::milliseconds(8));
        }
        if (paused_before_submit)
          batch_target = 1;

        const auto submit_start = std::chrono::steady_clock::now();
        const int submit_result = command.submit_and_wait();
        const int64_t submit_us =
            std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now() - submit_start)
                .count();
        if (submit_result != 0) {
          LOGE("Fused batch submit failed: completed=%d batch=%d result=%d",
               completed_tiles, batch_size, submit_result);
          return -1;
        }
        completed_tiles += batch_size;
        if (progress_ptr)
          progress_ptr->store(completed_tiles * 99 / tile_count);
        command.reset();
        retained_mats.clear();
        for (int i = 0; i < batch_size; i++)
          extractor_slots[i] = empty_extractor;
        batch_size = 0;

        const int previous_batch_target = batch_target;
        if (ui_busy_ptr && ui_busy_ptr->load()) {
          batch_target = 1;
        } else if (submit_us >= 24000 && batch_target > 1) {
          batch_target = std::max(1, batch_target / 2);
        } else if (submit_us <= 12000 && batch_target < batch_capacity) {
          batch_target++;
        }
        if (batch_target != previous_batch_target) {
          LOGD("Fused Vulkan adaptive batch: %d -> %d after %lldus submit",
               previous_batch_target, batch_target,
               static_cast<long long>(submit_us));
        }

        if (should_abort_ptr && should_abort_ptr->load())
          return -1;
        if (tile_sleep_ms > 0 && !is_last_tile) {
          std::this_thread::sleep_for(
              std::chrono::milliseconds(tile_sleep_ms));
        }
      }
    }
  }

  ncnn::Mat packed_output(target_w, target_h, out_pixels, (size_t)4u, 1);
  {
    while (ui_busy_ptr && ui_busy_ptr->load()) {
      if (should_abort_ptr && should_abort_ptr->load())
        return -1;
      std::this_thread::sleep_for(std::chrono::milliseconds(8));
    }
    ncnn::VkCompute download(vkdev);
    download.record_clone(output_gpu, packed_output, opt);
    const int download_result = download.submit_and_wait();
    if (download_result != 0) {
      LOGE("Fused download failed: result=%d", download_result);
      return -1;
    }
  }

  if (progress_ptr)
    progress_ptr->store(100);
  return 0;
}

int Waifu2x::process(const ncnn::Mat &inimage, void *out_pixels, int out_stride,
                     bool input_has_alpha, std::unique_lock<std::mutex> &lock,
                     std::atomic<int> *progress_ptr) const {
  // Input: planar RGBA float Mat with values 0-255 from from_pixels
  // inimage has dims=3, w=width, h=height, c=4 (RGBA)

  int orig_w = inimage.w;
  int orig_h = inimage.h;

  // Use original resolution for full quality per user request
  const ncnn::Mat &work_img = inimage;

  int w = work_img.w;
  int h = work_img.h;
  int target_w = w * scale;
  int target_h = h * scale;

  LOGD("Processing image %dx%d (orig %dx%d) -> %dx%d", w, h, orig_w, orig_h,
       target_w, target_h);

  // Normalization using work_img
  ncnn::Mat rgb_normalized(w, h, 3);
  bool is_grayscale = true;

  // Channel mapping from work_img
  const float *in_r = work_img.channel(0);
  const float *in_g = work_img.channel(1);
  const float *in_b = work_img.channel(2);

  float *out_b = rgb_normalized.channel(0); // B goes to channel 0
  float *out_g = rgb_normalized.channel(1); // G stays at channel 1
  float *out_r = rgb_normalized.channel(2); // R goes to channel 2

  const float norm = 1.0f / 255.0f;

  // Robust grayscale detection: allow up to 0.5% of pixels to be "colorful"
  // (noise tolerance)
  int color_pixel_count = 0;
  int color_threshold_count = w * h / 200; // 0.5%
  const bool detect_grayscale = !disable_grayscale_check;

  for (int i = 0; i < w * h; i++) {
    // Detect if pixel has significant color
    if (detect_grayscale &&
        (std::abs(in_r[i] - in_g[i]) > 5.0f ||
         std::abs(in_r[i] - in_b[i]) > 5.0f)) {
      color_pixel_count++;
    }

    out_b[i] = in_b[i] * norm;
    out_g[i] = in_g[i] * norm;
    out_r[i] = in_r[i] * norm;
  }

  if (!detect_grayscale || color_pixel_count > color_threshold_count) {
    is_grayscale = false;
  }

  if (is_grayscale)
    LOGD("Grayscale image detected, forcing pure grayscale output.");

  // PRE-PROCESS ALPHA CHANNEL (moved to start)
  // We need full alpha map to merge tiles on the fly
  ncnn::Mat alpha_out;
  bool has_alpha = input_has_alpha && inimage.c >= 4;
  const float *alpha_data = nullptr;

  if (has_alpha) {
    ncnn::Mat alpha_in = inimage.channel_range(3, 1);
    ncnn::Option alpha_opt;
    alpha_opt.num_threads = num_threads;
    ncnn::resize_bicubic(alpha_in, alpha_out, target_w, target_h, alpha_opt);
    alpha_data = (const float *)alpha_out.data;
  }

  // Tiling parameters
  const int TILE_SIZE_X = tilesize;
  const int TILE_SIZE_Y = tilesize;

  // Create padded input to handle borders easily
  ncnn::Mat padded_input;
  ncnn::copy_make_border(rgb_normalized, padded_input, prepadding, prepadding,
                         prepadding, prepadding, ncnn::BORDER_REPLICATE, 0.f,
                         net.opt);

  // NO huge model_out allocation needed anymore!

  const int xtiles = (w + TILE_SIZE_X - 1) / TILE_SIZE_X;
  const int ytiles = (h + TILE_SIZE_Y - 1) / TILE_SIZE_Y;

  // One fixed post-processing worker overlaps CPU conversion with GPU inference
  // without creating a new thread for every tile. A second worker is enabled
  // only when queue backpressure shows that conversion is the bottleneck.
  BoundedTaskPool postprocess(1, 2);
  int64_t postprocess_wait_us = 0;
  bool second_postprocess_worker = false;

  for (int yi = 0; yi < ytiles; yi++) {
    for (int xi = 0; xi < xtiles; xi++) {
      while (ui_busy_ptr && ui_busy_ptr->load()) {
        if (should_abort_ptr && should_abort_ptr->load()) {
          postprocess.wait();
          return -1;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(8));
      }
      if (should_abort_ptr && should_abort_ptr->load()) {
        postprocess.wait();
        return -1;
      }

      int x = xi * TILE_SIZE_X;
      int y = yi * TILE_SIZE_Y;

      int w_tile = std::min(TILE_SIZE_X, w - x);
      int h_tile = std::min(TILE_SIZE_Y, h - y);

      int in_tile_w = w_tile + 2 * prepadding;
      int in_tile_h = h_tile + 2 * prepadding;

      // Extract tile from padded_input
      ncnn::Mat in_tile(in_tile_w, in_tile_h, 3);
      for (int c = 0; c < 3; c++) {
        const float *ptr = padded_input.channel(c).row(y) + x;
        float *outptr = in_tile.channel(c);

        for (int i = 0; i < in_tile_h; i++) {
          memcpy(outptr, ptr, in_tile_w * sizeof(float));
          ptr += padded_input.w;
          outptr += in_tile.w;
        }
      }

      // Run inference on tile (GPU WORK)
      ncnn::Mat out_tile;
      {
        ncnn::Extractor ex = net.create_extractor();
        ex.set_light_mode(true);
        if (net.input_indexes().empty() || net.output_indexes().empty()) {
          LOGE("Model has no inputs or outputs!");
          return -1;
        }
        ex.input(net.input_indexes()[0], in_tile);
        ex.extract(net.output_indexes()[net.output_indexes().size() - 1],
                   out_tile);
      }

      if (out_tile.empty() || out_tile.c < 3) {
        LOGE("Inference tile failed or invalid channels (c=%d) at %d,%d",
             out_tile.c, xi, yi);
        continue;
      }

      // Debug logging for first tile to diagnose x3/x4 issues
      if (xi == 0 && yi == 0) {
        int expected_w = (std::min(TILE_SIZE_X, w) + 2 * prepadding) * scale;
        int expected_h = (std::min(TILE_SIZE_Y, h) + 2 * prepadding) * scale;
        LOGD("Tile debug: scale=%d, prepadding=%d", scale, prepadding);
        LOGD("  in_tile: %dx%dx%d", in_tile.w, in_tile.h, in_tile.c);
        LOGD("  out_tile: %dx%dx%d (expected ~%dx%d)", out_tile.w, out_tile.h,
             out_tile.c, expected_w, expected_h);
      }

      // Update progress IMMEDIATELY after GPU inference to show activity
      if (progress_ptr) {
        int p =
            (xi + yi * xtiles) * 99 / (xtiles * ytiles) + 1; // Slight offset
        progress_ptr->store(p);
      }

      // Capture by value [=] ensures all local variables needed for conversion
      // are copied. ncnn::Mat out_tile is ref-counted, so copy is fast.
      const int64_t queue_wait_us =
          postprocess.submit([=, out_tile_captured = out_tile]() {
        int out_x = x * scale;
        int out_y = y * scale;
        int out_w_tile = w_tile * scale;
        int out_h_tile = h_tile * scale;
        int out_pad = prepadding * scale;

        // Calculate source offset based on actual model output
        // Models typically output: input_tile * scale (without additional
        // padding) For full tiles, this equals (tilesize + 2*prepadding) *
        // scale For edge tiles, the output may be smaller
        int expected_out_w = in_tile_w * scale;
        int expected_out_h = in_tile_h * scale;

        int src_offset_x, src_offset_y;

        if (out_tile_captured.w >= expected_out_w &&
            out_tile_captured.h >= expected_out_h) {
          // Model output includes padding - use standard offset
          src_offset_x = out_pad;
          src_offset_y = out_pad;
        } else if (out_tile_captured.w >= out_w_tile &&
                   out_tile_captured.h >= out_h_tile) {
          // Model output is content-only (stripped padding) - calculate
          // center offset
          src_offset_x = (out_tile_captured.w - out_w_tile) / 2;
          src_offset_y = (out_tile_captured.h - out_h_tile) / 2;
        } else {
          // Output smaller than content - use from beginning (edge case)
          src_offset_x = 0;
          src_offset_y = 0;
        }

        const float *tile_b = out_tile_captured.channel(0);
        const float *tile_g = out_tile_captured.channel(1);
        const float *tile_r = out_tile_captured.channel(2);

        // Iterate over valid output rows for this tile
        for (int i = 0; i < out_h_tile; i++) {
          int dst_y = out_y + i;
          int src_y = src_offset_y + i;

          if (dst_y >= target_h)
            break;
          if (src_y >= out_tile_captured.h)
            break;

          unsigned char *dst_row =
              (unsigned char *)out_pixels + dst_y * out_stride;

          // Pointers into the tile data
          int src_row_offset = src_y * out_tile_captured.w;
          const float *ptr_b = tile_b + src_row_offset + src_offset_x;
          const float *ptr_g = tile_g + src_row_offset + src_offset_x;
          const float *ptr_r = tile_r + src_row_offset + src_offset_x;

          // Pointer into global alpha data
          const float *ptr_a = nullptr;
          if (alpha_data) {
            ptr_a = alpha_data + dst_y * target_w + out_x;
          }

          int copy_w = out_w_tile;
          if (out_x + copy_w > target_w)
            copy_w = target_w - out_x;
          if (src_offset_x + copy_w > out_tile_captured.w)
            copy_w = out_tile_captured.w - src_offset_x;

          int j = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
          const float32x4_t scale_255 = vdupq_n_f32(255.f);
          const float32x4_t gray_scale = vdupq_n_f32(1.f / 3.f);
          for (; j + 8 <= copy_w; j += 8) {
            float32x4_t r0 = vmulq_f32(vld1q_f32(ptr_r + j), scale_255);
            float32x4_t r1 = vmulq_f32(vld1q_f32(ptr_r + j + 4), scale_255);
            float32x4_t g0 = vmulq_f32(vld1q_f32(ptr_g + j), scale_255);
            float32x4_t g1 = vmulq_f32(vld1q_f32(ptr_g + j + 4), scale_255);
            float32x4_t b0 = vmulq_f32(vld1q_f32(ptr_b + j), scale_255);
            float32x4_t b1 = vmulq_f32(vld1q_f32(ptr_b + j + 4), scale_255);

            if (is_grayscale) {
              r0 = vmulq_f32(vaddq_f32(vaddq_f32(r0, g0), b0), gray_scale);
              r1 = vmulq_f32(vaddq_f32(vaddq_f32(r1, g1), b1), gray_scale);
              g0 = b0 = r0;
              g1 = b1 = r1;
            }

            uint8x8x4_t rgba;
            rgba.val[0] = floats_to_u8(r0, r1);
            rgba.val[1] = floats_to_u8(g0, g1);
            rgba.val[2] = floats_to_u8(b0, b1);
            if (ptr_a) {
              rgba.val[3] = floats_to_u8(vld1q_f32(ptr_a + j),
                                          vld1q_f32(ptr_a + j + 4));
            } else {
              rgba.val[3] = vdup_n_u8(255);
            }
            vst4_u8(dst_row + (out_x + j) * 4, rgba);
          }
#endif
          for (; j < copy_w; j++) {
            float r = ptr_r[j] * 255.0f;
            float g = ptr_g[j] * 255.0f;
            float b = ptr_b[j] * 255.0f;

            if (is_grayscale) {
              float gray = (r + g + b) * 0.333333f;
              r = g = b = gray;
            }

            int dst_x = out_x + j;
            int dst_idx = dst_x * 4;

            dst_row[dst_idx + 0] =
                (unsigned char)std::max(0.0f, std::min(255.0f, r));
            dst_row[dst_idx + 1] =
                (unsigned char)std::max(0.0f, std::min(255.0f, g));
            dst_row[dst_idx + 2] =
                (unsigned char)std::max(0.0f, std::min(255.0f, b));
            float a = ptr_a ? ptr_a[j] : 255.0f;
            dst_row[dst_idx + 3] =
                (unsigned char)std::max(0.0f, std::min(255.0f, a));
          }
        }

        // Update progress after this tile is fully written to UI
        if (progress_ptr) {
          int p = (xi + yi * xtiles + 1) * 99 / (xtiles * ytiles);
          progress_ptr->store(p);
        }
      });

      if (!second_postprocess_worker && queue_wait_us >= 1000) {
        postprocess_wait_us += queue_wait_us;
        if (postprocess_wait_us >= 4000 && postprocess.add_second_worker()) {
          second_postprocess_worker = true;
          LOGD("Staged postprocess expanded to 2 workers after %lldus queue "
               "wait",
               static_cast<long long>(postprocess_wait_us));
        }
      }

      // Check for abort signal
      if (should_abort_ptr && should_abort_ptr->load()) {
        LOGD("Waifu2x process aborted by signal");
        postprocess.wait();
        return -1;
      }

      // Skip sleep for the last few tiles
      bool is_near_end = (xi + yi * xtiles) > (xtiles * ytiles - 5);
      if (tile_sleep_ms > 0 && !is_near_end) {
        std::this_thread::sleep_for(std::chrono::milliseconds(tile_sleep_ms));
      }
    }
  }

  postprocess.wait();

  if (progress_ptr) {
    progress_ptr->store(100);
  }

  LOGD("Processing complete: %dx%d (Native side finished)", target_w, target_h);

  return 0;
}
