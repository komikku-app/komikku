#include "qnn_backend.h"

#include <algorithm>
#include <android/log.h>
#include <chrono>
#include <cmath>
#include <cstring>
#include <dlfcn.h>
#include <fstream>
#include <string>
#include <vector>

#if MIHON_ENABLE_QNN
#include <HTP/QnnHtpDevice.h>
#include <QnnInterface.h>
#include <System/QnnSystemInterface.h>
#endif

#define TAG "QnnBackend"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace qnn_backend {

#if MIHON_ENABLE_QNN
namespace {

using GetProvidersFn = Qnn_ErrorHandle_t (*)(const QnnInterface_t ***, uint32_t *);
using GetSystemProvidersFn =
    Qnn_ErrorHandle_t (*)(const QnnSystemInterface_t ***, uint32_t *);

struct GraphMetadata {
  const char *name = nullptr;
  const Qnn_Tensor_t *inputs = nullptr;
  uint32_t input_count = 0;
  const Qnn_Tensor_t *outputs = nullptr;
  uint32_t output_count = 0;
};

bool get_graph_metadata(const QnnSystemContext_BinaryInfo_t *binary_info,
                        GraphMetadata &metadata) {
  if (!binary_info) {
    return false;
  }

  const QnnSystemContext_GraphInfo_t *graphs = nullptr;
  uint32_t graph_count = 0;
  switch (binary_info->version) {
  case QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_1:
    graphs = binary_info->contextBinaryInfoV1.graphs;
    graph_count = binary_info->contextBinaryInfoV1.numGraphs;
    break;
  case QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_2:
    graphs = binary_info->contextBinaryInfoV2.graphs;
    graph_count = binary_info->contextBinaryInfoV2.numGraphs;
    break;
  case QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_3:
    graphs = binary_info->contextBinaryInfoV3.graphs;
    graph_count = binary_info->contextBinaryInfoV3.numGraphs;
    break;
  default:
    return false;
  }
  if (!graphs || graph_count != 1) {
    LOGE("Expected one graph in context, found %u", graph_count);
    return false;
  }

  const auto &graph = graphs[0];
  switch (graph.version) {
  case QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_1:
    metadata = {graph.graphInfoV1.graphName, graph.graphInfoV1.graphInputs,
                graph.graphInfoV1.numGraphInputs,
                graph.graphInfoV1.graphOutputs,
                graph.graphInfoV1.numGraphOutputs};
    break;
  case QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_2:
    metadata = {graph.graphInfoV2.graphName, graph.graphInfoV2.graphInputs,
                graph.graphInfoV2.numGraphInputs,
                graph.graphInfoV2.graphOutputs,
                graph.graphInfoV2.numGraphOutputs};
    break;
  case QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_3:
    metadata = {graph.graphInfoV3.graphName, graph.graphInfoV3.graphInputs,
                graph.graphInfoV3.numGraphInputs,
                graph.graphInfoV3.graphOutputs,
                graph.graphInfoV3.numGraphOutputs};
    break;
  default:
    return false;
  }
  return metadata.name && metadata.inputs && metadata.outputs &&
         metadata.input_count == 1 && metadata.output_count == 1;
}

uint16_t float_to_half(float value) {
  uint32_t bits;
  std::memcpy(&bits, &value, sizeof(bits));
  const uint32_t sign = (bits >> 16) & 0x8000u;
  uint32_t mantissa = bits & 0x007fffffu;
  int exponent = static_cast<int>((bits >> 23) & 0xffu) - 127 + 15;

  if (exponent <= 0) {
    if (exponent < -10) {
      return static_cast<uint16_t>(sign);
    }
    mantissa = (mantissa | 0x00800000u) >> (1 - exponent);
    return static_cast<uint16_t>(sign + ((mantissa + 0x00001000u) >> 13));
  }
  if (exponent >= 31) {
    return static_cast<uint16_t>(sign | 0x7c00u);
  }
  return static_cast<uint16_t>(sign | (static_cast<uint32_t>(exponent) << 10) |
                               ((mantissa + 0x00001000u) >> 13));
}

float half_to_float(uint16_t value) {
  const uint32_t sign = static_cast<uint32_t>(value & 0x8000u) << 16;
  uint32_t exponent = (value >> 10) & 0x1fu;
  uint32_t mantissa = value & 0x03ffu;
  uint32_t bits;

  if (exponent == 0) {
    if (mantissa == 0) {
      bits = sign;
    } else {
      int shift = 0;
      while ((mantissa & 0x0400u) == 0) {
        mantissa <<= 1;
        ++shift;
      }
      mantissa &= 0x03ffu;
      bits = sign | (static_cast<uint32_t>(127 - 15 - shift) << 23) |
             (mantissa << 13);
    }
  } else if (exponent == 31) {
    bits = sign | 0x7f800000u | (mantissa << 13);
  } else {
    bits = sign | ((exponent + 112u) << 23) | (mantissa << 13);
  }

  float result;
  std::memcpy(&result, &bits, sizeof(result));
  return result;
}

int reflect_coordinate(int coordinate, int size) {
  if (size <= 1) {
    return 0;
  }
  while (coordinate < 0 || coordinate >= size) {
    coordinate = coordinate < 0 ? -coordinate : 2 * size - 2 - coordinate;
  }
  return coordinate;
}

class Runtime {
public:
  ~Runtime() { reset(); }

  bool probe() {
    void *handle = dlopen("libQnnHtp.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
      LOGE("Unable to load libQnnHtp.so: %s", dlerror());
      return false;
    }
    const bool result = find_qnn_provider(handle) != nullptr;
    dlclose(handle);
    return result;
  }

  int architecture() {
    void *handle = dlopen("libQnnHtp.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
      LOGE("Unable to load libQnnHtp.so while detecting architecture: %s",
           dlerror());
      return 0;
    }
    const QnnInterface_t *provider = find_qnn_provider(handle);
    if (!provider) {
      dlclose(handle);
      return 0;
    }
    const auto &qnn = provider->QNN_INTERFACE_VER_NAME;
    if (!qnn.deviceGetPlatformInfo || !qnn.deviceFreePlatformInfo) {
      dlclose(handle);
      return 0;
    }

    const QnnDevice_PlatformInfo_t *platform_info = nullptr;
    int result = 0;
    if (qnn.deviceGetPlatformInfo(nullptr, &platform_info) == QNN_SUCCESS &&
        platform_info &&
        platform_info->version == QNN_DEVICE_PLATFORM_INFO_VERSION_1) {
      for (uint32_t index = 0; index < platform_info->v1.numHwDevices; ++index) {
        const auto &device = platform_info->v1.hwDevices[index];
        if (device.version != QNN_DEVICE_HARDWARE_DEVICE_INFO_VERSION_1 ||
            !device.v1.deviceInfoExtension) {
          continue;
        }
        const auto *extension =
            reinterpret_cast<const QnnHtpDevice_DeviceInfoExtension_t *>(
                device.v1.deviceInfoExtension);
        if (extension->devType == QNN_HTP_DEVICE_TYPE_ON_CHIP) {
          result = static_cast<int>(extension->onChipDevice.arch);
          break;
        }
      }
    }
    if (platform_info) {
      qnn.deviceFreePlatformInfo(nullptr, platform_info);
    }
    dlclose(handle);
    LOGD("Detected HTP architecture v%d from QNN platform info", result);
    return result;
  }

  bool load(const std::string &context_path, int padding) {
    deactivate();
    if (!ensure_runtime()) {
      return false;
    }
    const auto &qnn = provider_->QNN_INTERFACE_VER_NAME;

    std::ifstream stream(context_path, std::ios::binary | std::ios::ate);
    if (!stream) {
      LOGE("Unable to open QNN context: %s", context_path.c_str());
      return false;
    }
    const auto length = stream.tellg();
    if (length <= 0) {
      return false;
    }
    binary_.resize(static_cast<size_t>(length));
    stream.seekg(0);
    if (!stream.read(reinterpret_cast<char *>(binary_.data()), length)) {
      LOGE("Unable to read QNN context: %s", context_path.c_str());
      return false;
    }

    GraphMetadata metadata;
    const auto &system = system_provider_->QNN_SYSTEM_INTERFACE_VER_NAME;
    QnnSystemContext_Handle_t system_context = nullptr;
    const QnnSystemContext_BinaryInfo_t *binary_info = nullptr;
    Qnn_ContextBinarySize_t binary_info_size = 0;
    if (!system.systemContextCreate || !system.systemContextGetBinaryInfo ||
        !system.systemContextFree ||
        system.systemContextCreate(&system_context) != QNN_SUCCESS ||
        system.systemContextGetBinaryInfo(system_context, binary_.data(),
                                          binary_.size(), &binary_info,
                                          &binary_info_size) != QNN_SUCCESS ||
        !get_graph_metadata(binary_info, metadata) ||
        !copy_tensor(metadata.inputs[0], input_, input_name_, input_dimensions_) ||
        !copy_tensor(metadata.outputs[0], output_, output_name_, output_dimensions_)) {
      LOGE("Unable to read QNN context graph metadata");
      if (system_context && system.systemContextFree) {
        system.systemContextFree(system_context);
      }
      deactivate();
      return false;
    }
    graph_name_ = metadata.name;
    system.systemContextFree(system_context);

    if (!validate_tensor(input_) || !validate_tensor(output_) ||
        input_.v2.dimensions[1] != input_.v2.dimensions[2] ||
        output_.v2.dimensions[1] % input_.v2.dimensions[1] != 0 ||
        output_.v2.dimensions[2] % input_.v2.dimensions[2] != 0 ||
        output_.v2.dimensions[1] / input_.v2.dimensions[1] !=
            output_.v2.dimensions[2] / input_.v2.dimensions[2]) {
      LOGE("QNN context tensor layout is not square NHWC with an integer scale");
      deactivate();
      return false;
    }
    tile_size_ = static_cast<int>(input_.v2.dimensions[1]);
    output_tile_size_ = static_cast<int>(output_.v2.dimensions[1]);
    scale_ = output_tile_size_ / tile_size_;
    if (scale_ < 2 || scale_ > 4) {
      LOGE("Unsupported QNN output scale: %d", scale_);
      deactivate();
      return false;
    }

    Qnn_ErrorHandle_t status =
        qnn.contextCreateFromBinary(backend_, device_, nullptr, binary_.data(),
                                    binary_.size(), &context_, nullptr);
    if (status != QNN_SUCCESS) {
      LOGE("QNN contextCreateFromBinary failed: %u", static_cast<unsigned>(status));
      deactivate();
      return false;
    }
    status = qnn.graphRetrieve(context_, graph_name_.c_str(), &graph_);
    if (status != QNN_SUCCESS) {
      LOGE("QNN graphRetrieve failed for %s: %u", graph_name_.c_str(),
           static_cast<unsigned>(status));
      deactivate();
      return false;
    }

    const size_t input_elements = static_cast<size_t>(tile_size_) * tile_size_ * 3u;
    const size_t output_elements =
        static_cast<size_t>(output_tile_size_) * output_tile_size_ * 3u;
    if (is_fp16_tensor(input_)) {
      input_fp16_buffer_.resize(input_elements);
      set_client_buffer(input_, input_fp16_buffer_.data(),
                        input_fp16_buffer_.size() * sizeof(uint16_t));
    } else if (is_quant16_tensor(input_)) {
      input_quant16_buffer_.resize(input_elements);
      set_client_buffer(input_, input_quant16_buffer_.data(),
                        input_quant16_buffer_.size() * sizeof(uint16_t));
    } else {
      input_quant8_buffer_.resize(input_elements);
      set_client_buffer(input_, input_quant8_buffer_.data(),
                        input_quant8_buffer_.size());
    }
    if (is_fp16_tensor(output_)) {
      output_fp16_buffer_.resize(output_elements);
      set_client_buffer(output_, output_fp16_buffer_.data(),
                        output_fp16_buffer_.size() * sizeof(uint16_t));
    } else if (is_quant16_tensor(output_)) {
      output_quant16_buffer_.resize(output_elements);
      set_client_buffer(output_, output_quant16_buffer_.data(),
                        output_quant16_buffer_.size() * sizeof(uint16_t));
    } else {
      output_quant8_buffer_.resize(output_elements);
      set_client_buffer(output_, output_quant8_buffer_.data(),
                        output_quant8_buffer_.size());
    }
    padding_ = std::clamp(padding, 0, 48);
    configure_performance();
    initialized_ = true;
    LOGD("Loaded QNN graph %s with tile %dx%d, scale %dx, %s IO and padding %d",
         graph_name_.c_str(), tile_size_, tile_size_, scale_,
         is_fp16_tensor(input_) ? "FP16" : (is_quant16_tensor(input_) ? "INT16" : "INT8"),
         padding_);
    return true;
  }

  int process(const uint8_t *input, int width, int height, int input_stride,
              uint8_t *output, int output_stride, std::atomic<int> *progress,
              const std::atomic<bool> *should_abort) {
    if (!initialized_ || !input || !output || width <= 0 || height <= 0) {
      return -1;
    }
    const int core = tile_size_ - 2 * padding_;
    if (core <= 0) {
      return -1;
    }
    const int columns = (width + core - 1) / core;
    const int rows = (height + core - 1) / core;
    const int total_tiles = columns * rows;
    int completed = 0;
    std::chrono::nanoseconds prepare_time{0};
    std::chrono::nanoseconds execute_time{0};
    std::chrono::nanoseconds output_time{0};

    for (int tile_y = 0; tile_y < height; tile_y += core) {
      for (int tile_x = 0; tile_x < width; tile_x += core) {
        if (should_abort && should_abort->load()) {
          return -2;
        }
        auto stage_start = std::chrono::steady_clock::now();
        fill_input_tile(input, width, height, input_stride, tile_x - padding_,
                        tile_y - padding_);
        prepare_time += std::chrono::steady_clock::now() - stage_start;
        const auto &qnn = provider_->QNN_INTERFACE_VER_NAME;
        stage_start = std::chrono::steady_clock::now();
        const Qnn_ErrorHandle_t status =
            qnn.graphExecute(graph_, &input_, 1, &output_, 1, nullptr, nullptr);
        execute_time += std::chrono::steady_clock::now() - stage_start;
        if (status != QNN_SUCCESS) {
          LOGE("QNN graphExecute failed: %u", static_cast<unsigned>(status));
          return -1;
        }

        const int copy_width = std::min(core, width - tile_x) * scale_;
        const int copy_height = std::min(core, height - tile_y) * scale_;
        stage_start = std::chrono::steady_clock::now();
        write_output_tile(output, output_stride, tile_x * scale_,
                          tile_y * scale_,
                          copy_width, copy_height, input, width, height,
                          input_stride);
        output_time += std::chrono::steady_clock::now() - stage_start;
        ++completed;
        if (progress) {
          progress->store(std::min(99, completed * 100 / total_tiles));
        }
      }
    }
    if (progress) {
      progress->store(100);
    }
    LOGD("QNN tile profile: count=%d tile=%d core=%d prepare=%lldms "
         "execute=%lldms output=%lldms",
         total_tiles, tile_size_, core,
         static_cast<long long>(
             std::chrono::duration_cast<std::chrono::milliseconds>(prepare_time)
                 .count()),
         static_cast<long long>(
             std::chrono::duration_cast<std::chrono::milliseconds>(execute_time)
                 .count()),
         static_cast<long long>(
             std::chrono::duration_cast<std::chrono::milliseconds>(output_time)
                 .count()));
    return 0;
  }

  bool initialized() const { return initialized_; }

  void deactivate() {
    initialized_ = false;
    if (provider_) {
      const auto &qnn = provider_->QNN_INTERFACE_VER_NAME;
      if (context_ && qnn.contextFree) {
        qnn.contextFree(context_, nullptr);
      }
    }
    release_performance();
    graph_ = nullptr;
    context_ = nullptr;
    input_fp16_buffer_.clear();
    output_fp16_buffer_.clear();
    input_quant16_buffer_.clear();
    output_quant16_buffer_.clear();
    input_quant8_buffer_.clear();
    output_quant8_buffer_.clear();
    binary_.clear();
    tile_size_ = 0;
    output_tile_size_ = 0;
    scale_ = 0;
  }

  void reset() {
    deactivate();
    if (provider_) {
      const auto &qnn = provider_->QNN_INTERFACE_VER_NAME;
      if (device_ && qnn.deviceFree) {
        qnn.deviceFree(device_);
      }
      if (backend_ && qnn.backendFree) {
        qnn.backendFree(backend_);
      }
    }
    device_ = nullptr;
    backend_ = nullptr;
    provider_ = nullptr;
    system_provider_ = nullptr;
    if (system_library_) {
      dlclose(system_library_);
      system_library_ = nullptr;
    }
    if (backend_library_) {
      dlclose(backend_library_);
      backend_library_ = nullptr;
    }
  }

private:
  bool ensure_runtime() {
    if (backend_ && provider_ && system_provider_) {
      return true;
    }
    reset();
    backend_library_ = dlopen("libQnnHtp.so", RTLD_NOW | RTLD_LOCAL);
    system_library_ = dlopen("libQnnSystem.so", RTLD_NOW | RTLD_LOCAL);
    if (!backend_library_ || !system_library_) {
      LOGE("Unable to load QNN libraries: %s", dlerror());
      reset();
      return false;
    }
    provider_ = find_qnn_provider(backend_library_);
    system_provider_ = find_system_provider(system_library_);
    if (!provider_ || !system_provider_) {
      LOGE("Compatible QNN providers were not found");
      reset();
      return false;
    }

    const auto &qnn = provider_->QNN_INTERFACE_VER_NAME;
    if (!qnn.backendCreate || !qnn.contextCreateFromBinary ||
        !qnn.graphRetrieve || !qnn.graphExecute) {
      LOGE("QNN HTP provider is missing required APIs");
      reset();
      return false;
    }
    Qnn_ErrorHandle_t status = qnn.backendCreate(nullptr, nullptr, &backend_);
    if (status != QNN_SUCCESS) {
      LOGE("QNN backendCreate failed: %u", static_cast<unsigned>(status));
      reset();
      return false;
    }
    if (qnn.deviceCreate) {
      status = qnn.deviceCreate(nullptr, nullptr, &device_);
      if (status == QNN_DEVICE_ERROR_UNSUPPORTED_FEATURE ||
          status == QNN_DEVICE_ERROR_INVALID_CONFIG) {
        LOGD("QNN deviceCreate returned %u; using the backend default device",
             static_cast<unsigned>(status));
        device_ = nullptr;
      } else if (status != QNN_SUCCESS) {
        LOGE("QNN deviceCreate failed: %u", static_cast<unsigned>(status));
        reset();
        return false;
      }
    }
    return true;
  }

  void configure_performance() {
    const auto &qnn = provider_->QNN_INTERFACE_VER_NAME;
    if (!qnn.deviceGetInfrastructure) {
      return;
    }
    QnnDevice_Infrastructure_t device_infrastructure = nullptr;
    Qnn_ErrorHandle_t status =
        qnn.deviceGetInfrastructure(&device_infrastructure);
    if (status != QNN_SUCCESS || !device_infrastructure) {
      LOGE("QNN deviceGetInfrastructure failed: %u",
           static_cast<unsigned>(status));
      return;
    }
    auto *htp_infrastructure = reinterpret_cast<QnnHtpDevice_Infrastructure_t *>(
        device_infrastructure);
    if (htp_infrastructure->infraType !=
        QNN_HTP_DEVICE_INFRASTRUCTURE_TYPE_PERF) {
      return;
    }
    performance_infrastructure_ = htp_infrastructure->perfInfra;
    if (!performance_infrastructure_.createPowerConfigId ||
        !performance_infrastructure_.setPowerConfig ||
        !performance_infrastructure_.destroyPowerConfigId) {
      return;
    }
    status = performance_infrastructure_.createPowerConfigId(
        0, 0, &power_config_id_);
    if (status != QNN_SUCCESS) {
      power_config_id_ = 0;
      LOGE("QNN createPowerConfigId failed: %u",
           static_cast<unsigned>(status));
      return;
    }

    QnnHtpPerfInfrastructure_PowerConfig_t dcvs =
        QNN_HTP_PERF_INFRASTRUCTURE_POWER_CONFIG_INIT;
    dcvs.option = QNN_HTP_PERF_INFRASTRUCTURE_POWER_CONFIGOPTION_DCVS_V3;
    dcvs.dcvsV3Config.contextId = power_config_id_;
    dcvs.dcvsV3Config.setDcvsEnable = 1;
    dcvs.dcvsV3Config.dcvsEnable = 0;
    dcvs.dcvsV3Config.powerMode =
        QNN_HTP_PERF_INFRASTRUCTURE_POWERMODE_PERFORMANCE_MODE;
    dcvs.dcvsV3Config.setSleepLatency = 1;
    dcvs.dcvsV3Config.sleepLatency = 40;
    dcvs.dcvsV3Config.setSleepDisable = 1;
    dcvs.dcvsV3Config.sleepDisable = 1;
    dcvs.dcvsV3Config.setBusParams = 1;
    dcvs.dcvsV3Config.busVoltageCornerMin =
        DCVS_VOLTAGE_VCORNER_MAX_VOLTAGE_CORNER;
    dcvs.dcvsV3Config.busVoltageCornerTarget =
        DCVS_VOLTAGE_VCORNER_MAX_VOLTAGE_CORNER;
    dcvs.dcvsV3Config.busVoltageCornerMax =
        DCVS_VOLTAGE_VCORNER_MAX_VOLTAGE_CORNER;
    dcvs.dcvsV3Config.setCoreParams = 1;
    dcvs.dcvsV3Config.coreVoltageCornerMin =
        DCVS_VOLTAGE_VCORNER_MAX_VOLTAGE_CORNER;
    dcvs.dcvsV3Config.coreVoltageCornerTarget =
        DCVS_VOLTAGE_VCORNER_MAX_VOLTAGE_CORNER;
    dcvs.dcvsV3Config.coreVoltageCornerMax =
        DCVS_VOLTAGE_VCORNER_MAX_VOLTAGE_CORNER;

    QnnHtpPerfInfrastructure_PowerConfig_t rpc_latency =
        QNN_HTP_PERF_INFRASTRUCTURE_POWER_CONFIG_INIT;
    rpc_latency.option =
        QNN_HTP_PERF_INFRASTRUCTURE_POWER_CONFIGOPTION_RPC_CONTROL_LATENCY;
    rpc_latency.rpcControlLatencyConfig = 100;
    QnnHtpPerfInfrastructure_PowerConfig_t rpc_polling =
        QNN_HTP_PERF_INFRASTRUCTURE_POWER_CONFIG_INIT;
    rpc_polling.option =
        QNN_HTP_PERF_INFRASTRUCTURE_POWER_CONFIGOPTION_RPC_POLLING_TIME;
    rpc_polling.rpcPollingTimeConfig =
        QNN_HTP_PERF_INFRASTRUCTURE_POWER_CONFIG_MAX_RPC_POLLING_TIME;
    const QnnHtpPerfInfrastructure_PowerConfig_t *configs[] = {
        &dcvs, &rpc_latency, &rpc_polling, nullptr};
    status = performance_infrastructure_.setPowerConfig(power_config_id_,
                                                         configs);
    if (status != QNN_SUCCESS) {
      LOGE("QNN setPowerConfig failed: %u", static_cast<unsigned>(status));
      release_performance();
      return;
    }
    LOGD("QNN HTP burst performance configuration enabled");
  }

  void release_performance() {
    if (power_config_id_ != 0 &&
        performance_infrastructure_.destroyPowerConfigId) {
      performance_infrastructure_.destroyPowerConfigId(power_config_id_);
    }
    power_config_id_ = 0;
    performance_infrastructure_ = QNN_HTP_DEVICE_PERF_INFRASTRUCTURE_INIT;
  }

  static const QnnInterface_t *find_qnn_provider(void *library) {
    auto get_providers = reinterpret_cast<GetProvidersFn>(
        dlsym(library, "QnnInterface_getProviders"));
    if (!get_providers) {
      return nullptr;
    }
    const QnnInterface_t **providers = nullptr;
    uint32_t count = 0;
    if (get_providers(&providers, &count) != QNN_SUCCESS || !providers) {
      return nullptr;
    }
    for (uint32_t i = 0; i < count; ++i) {
      const auto &version = providers[i]->apiVersion.coreApiVersion;
      if (version.major == QNN_API_VERSION_MAJOR &&
          version.minor >= QNN_API_VERSION_MINOR) {
        return providers[i];
      }
    }
    return nullptr;
  }

  static const QnnSystemInterface_t *find_system_provider(void *library) {
    auto get_providers = reinterpret_cast<GetSystemProvidersFn>(
        dlsym(library, "QnnSystemInterface_getProviders"));
    if (!get_providers) {
      return nullptr;
    }
    const QnnSystemInterface_t **providers = nullptr;
    uint32_t count = 0;
    if (get_providers(&providers, &count) != QNN_SUCCESS || !providers) {
      return nullptr;
    }
    for (uint32_t i = 0; i < count; ++i) {
      const auto &version = providers[i]->systemApiVersion;
      if (version.major == QNN_SYSTEM_API_VERSION_MAJOR &&
          version.minor >= QNN_SYSTEM_API_VERSION_MINOR) {
        return providers[i];
      }
    }
    return nullptr;
  }

  static bool copy_tensor(const Qnn_Tensor_t &source, Qnn_Tensor_t &destination,
                          std::string &name, std::vector<uint32_t> &dimensions) {
    if (source.version != QNN_TENSOR_VERSION_2 || !source.v2.name ||
        !source.v2.dimensions || source.v2.rank == 0) {
      return false;
    }
    destination = source;
    name = source.v2.name;
    dimensions.assign(source.v2.dimensions,
                      source.v2.dimensions + source.v2.rank);
    destination.v2.name = name.c_str();
    destination.v2.dimensions = dimensions.data();
    destination.v2.memType = QNN_TENSORMEMTYPE_RAW;
    destination.v2.isDynamicDimensions = nullptr;
    return true;
  }

  static bool is_fp16_tensor(const Qnn_Tensor_t &tensor) {
    return tensor.v2.dataType == QNN_DATATYPE_FLOAT_16;
  }

  static bool is_quant8_tensor(const Qnn_Tensor_t &tensor) {
    return tensor.v2.dataType == QNN_DATATYPE_UFIXED_POINT_8 &&
           tensor.v2.quantizeParams.encodingDefinition == QNN_DEFINITION_DEFINED &&
           tensor.v2.quantizeParams.quantizationEncoding ==
               QNN_QUANTIZATION_ENCODING_SCALE_OFFSET &&
           tensor.v2.quantizeParams.scaleOffsetEncoding.scale > 0.0f;
  }

  static bool is_quant16_tensor(const Qnn_Tensor_t &tensor) {
    return tensor.v2.dataType == QNN_DATATYPE_UFIXED_POINT_16 &&
           tensor.v2.quantizeParams.encodingDefinition == QNN_DEFINITION_DEFINED &&
           tensor.v2.quantizeParams.quantizationEncoding ==
               QNN_QUANTIZATION_ENCODING_SCALE_OFFSET &&
           tensor.v2.quantizeParams.scaleOffsetEncoding.scale > 0.0f;
  }

  static bool validate_tensor(const Qnn_Tensor_t &tensor) {
    return tensor.version == QNN_TENSOR_VERSION_2 && tensor.v2.rank == 4 &&
           (is_fp16_tensor(tensor) || is_quant8_tensor(tensor) ||
            is_quant16_tensor(tensor)) &&
           tensor.v2.dimensions[0] == 1 && tensor.v2.dimensions[1] > 0 &&
           tensor.v2.dimensions[2] > 0 && tensor.v2.dimensions[3] == 3;
  }

  static uint8_t quantize_uint8(float value, const Qnn_Tensor_t &tensor) {
    const auto &encoding = tensor.v2.quantizeParams.scaleOffsetEncoding;
    const float quantized = value / encoding.scale - encoding.offset;
    return static_cast<uint8_t>(
        std::clamp(static_cast<int>(std::lround(quantized)), 0, 255));
  }

  static float dequantize_uint8(uint8_t value, const Qnn_Tensor_t &tensor) {
    const auto &encoding = tensor.v2.quantizeParams.scaleOffsetEncoding;
    return (static_cast<int>(value) + encoding.offset) * encoding.scale;
  }

  static uint16_t quantize_uint16(float value, const Qnn_Tensor_t &tensor) {
    const auto &encoding = tensor.v2.quantizeParams.scaleOffsetEncoding;
    const long quantized = std::lround(value / encoding.scale - encoding.offset);
    return static_cast<uint16_t>(std::clamp(quantized, 0L, 65535L));
  }

  static float dequantize_uint16(uint16_t value, const Qnn_Tensor_t &tensor) {
    const auto &encoding = tensor.v2.quantizeParams.scaleOffsetEncoding;
    return (static_cast<int32_t>(value) + encoding.offset) * encoding.scale;
  }

  static void set_client_buffer(Qnn_Tensor_t &tensor, void *data, uint32_t size) {
    tensor.v2.clientBuf.data = data;
    tensor.v2.clientBuf.dataSize = size;
  }

  void fill_input_tile(const uint8_t *input, int width, int height, int stride,
                       int origin_x, int origin_y) {
    size_t index = 0;
    for (int y = 0; y < tile_size_; ++y) {
      const int source_y = reflect_coordinate(origin_y + y, height);
      const uint8_t *row = input + static_cast<size_t>(source_y) * stride;
      for (int x = 0; x < tile_size_; ++x) {
        const int source_x = reflect_coordinate(origin_x + x, width);
        const uint8_t *pixel = row + static_cast<size_t>(source_x) * 4;
        for (int channel = 0; channel < 3; ++channel) {
          const float value = pixel[channel] / 255.0f;
          if (is_fp16_tensor(input_)) {
            input_fp16_buffer_[index] = float_to_half(value);
          } else if (is_quant16_tensor(input_)) {
            input_quant16_buffer_[index] = quantize_uint16(value, input_);
          } else {
            input_quant8_buffer_[index] = quantize_uint8(value, input_);
          }
          ++index;
        }
      }
    }
  }

  void write_output_tile(uint8_t *output, int output_stride, int target_x,
                         int target_y, int copy_width, int copy_height,
                         const uint8_t *input, int width, int height,
                         int input_stride) const {
    const int source_offset = padding_ * scale_;
    for (int y = 0; y < copy_height; ++y) {
      uint8_t *row = output + static_cast<size_t>(target_y + y) * output_stride +
                     static_cast<size_t>(target_x) * 4;
      for (int x = 0; x < copy_width; ++x) {
        const size_t source_index =
            (static_cast<size_t>(source_offset + y) * output_tile_size_ +
             source_offset + x) * 3;
        for (int channel = 0; channel < 3; ++channel) {
          const size_t index = source_index + channel;
          const float raw_value =
              is_fp16_tensor(output_)
                  ? half_to_float(output_fp16_buffer_[index])
                  : (is_quant16_tensor(output_)
                         ? dequantize_uint16(output_quant16_buffer_[index], output_)
                         : dequantize_uint8(output_quant8_buffer_[index], output_));
          const float value = std::clamp(raw_value, 0.0f, 1.0f);
          row[x * 4 + channel] = static_cast<uint8_t>(value * 255.0f + 0.5f);
        }
        const int alpha_x = std::min(width - 1, (target_x + x) / scale_);
        const int alpha_y = std::min(height - 1, (target_y + y) / scale_);
        row[x * 4 + 3] = input[static_cast<size_t>(alpha_y) * input_stride +
                                 static_cast<size_t>(alpha_x) * 4 + 3];
      }
    }
  }

  void *backend_library_ = nullptr;
  void *system_library_ = nullptr;
  const QnnInterface_t *provider_ = nullptr;
  const QnnSystemInterface_t *system_provider_ = nullptr;
  Qnn_BackendHandle_t backend_ = nullptr;
  Qnn_DeviceHandle_t device_ = nullptr;
  Qnn_ContextHandle_t context_ = nullptr;
  Qnn_GraphHandle_t graph_ = nullptr;
  Qnn_Tensor_t input_ = QNN_TENSOR_INIT;
  Qnn_Tensor_t output_ = QNN_TENSOR_INIT;
  std::string graph_name_;
  std::string input_name_;
  std::string output_name_;
  std::vector<uint32_t> input_dimensions_;
  std::vector<uint32_t> output_dimensions_;
  std::vector<uint8_t> binary_;
  std::vector<uint16_t> input_fp16_buffer_;
  std::vector<uint16_t> output_fp16_buffer_;
  std::vector<uint16_t> input_quant16_buffer_;
  std::vector<uint16_t> output_quant16_buffer_;
  std::vector<uint8_t> input_quant8_buffer_;
  std::vector<uint8_t> output_quant8_buffer_;
  int padding_ = 0;
  int tile_size_ = 0;
  int output_tile_size_ = 0;
  int scale_ = 0;
  QnnHtpDevice_PerfInfrastructure_t performance_infrastructure_ =
      QNN_HTP_DEVICE_PERF_INFRASTRUCTURE_INIT;
  uint32_t power_config_id_ = 0;
  bool initialized_ = false;
};

Runtime runtime;

} // namespace
#endif

bool is_runtime_loadable() {
#if MIHON_ENABLE_QNN
  return runtime.probe();
#else
  return false;
#endif
}

int architecture() {
#if MIHON_ENABLE_QNN
  return runtime.architecture();
#else
  return 0;
#endif
}

bool initialize(const std::string &context_path, int padding) {
#if MIHON_ENABLE_QNN
  return runtime.load(context_path, padding);
#else
  (void)context_path;
  (void)padding;
  return false;
#endif
}

bool is_initialized() {
#if MIHON_ENABLE_QNN
  return runtime.initialized();
#else
  return false;
#endif
}

int process_rgba(const uint8_t *input, int width, int height, int input_stride,
                 uint8_t *output, int output_stride,
                 std::atomic<int> *progress,
                 const std::atomic<bool> *should_abort) {
#if MIHON_ENABLE_QNN
  return runtime.process(input, width, height, input_stride, output,
                         output_stride, progress, should_abort);
#else
  (void)input;
  (void)width;
  (void)height;
  (void)input_stride;
  (void)output;
  (void)output_stride;
  (void)progress;
  (void)should_abort;
  return -1;
#endif
}

void shutdown() {
#if MIHON_ENABLE_QNN
  runtime.deactivate();
#endif
}

} // namespace qnn_backend
