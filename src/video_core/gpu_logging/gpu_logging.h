// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <array>
#include <atomic>
#include <string_view>
#include <chrono>
#include <memory>
#include <mutex>
#include <set>
#include <span>
#include <string>
#include <ankerl/unordered_dense.h>
#include <vector>

#include "common/common_types.h"

// Forward declarations
namespace Common::FS {
class IOFile;
}

namespace Vulkan {
class Device;
}

namespace GPU::Logging {

enum class LogLevel : u8 {
    Off = 0,
    Errors = 1,
    Standard = 2,
    Verbose = 3,
    All = 4,
};

enum class DriverType : u8 {
    Unknown,
    Turnip,   // Mesa Turnip driver
    Qualcomm, // Qualcomm proprietary driver
};

// Ring buffer entry for tracking Vulkan API calls
struct VulkanCallEntry {
    std::chrono::microseconds timestamp;
    std::string call_name;    // e.g., "vkCmdDraw", "vkBeginRenderPass"
    std::string parameters;   // Serialized parameters
    int result;               // VkResult return code
    u32 thread_id;
};

// GPU memory allocation entry
struct MemoryAllocationEntry {
    uintptr_t memory_handle;
    u64 size;
    u32 memory_flags;
    std::chrono::microseconds timestamp;
    bool is_device_local;
    bool is_host_visible;
};

// GPU state snapshot for crash dumps
struct GPUStateSnapshot {
    std::vector<VulkanCallEntry> recent_calls;    // Last N API calls
    std::vector<std::string> active_shaders;      // Currently bound shaders
    std::string pipeline_state;                   // Current pipeline state
    std::string memory_status;                    // Current memory allocations
    std::string driver_debug_info;                // Driver-specific debug data
    std::chrono::microseconds timestamp;
    DriverType driver_type;
};

/// Main GPU logging system singleton
class GPULogger {
public:
    static GPULogger& GetInstance();

    // Prevent copying
    GPULogger(const GPULogger&) = delete;
    GPULogger& operator=(const GPULogger&) = delete;

    // Initialization and control
    void Initialize(LogLevel level, DriverType detected_driver = DriverType::Unknown);
    void Shutdown();

    // Logging API
    void LogVulkanCall(const std::string& call_name, const std::string& params, int result);
    void LogMemoryAllocation(uintptr_t memory, u64 size, u32 memory_flags);
    void LogMemoryDeallocation(uintptr_t memory);
    void LogShaderCompilation(const std::string& shader_name, const std::string& shader_info);
    void LogPipelineStateChange(const std::string& state_info);
    void LogDriverDebugInfo(const std::string& debug_info);

    // Extension usage tracking
    void LogExtensionUsage(const std::string& extension_name, const std::string& function_name);

    // Render pass logging
    void LogRenderPassBegin(const std::string& render_pass_info);
    void LogRenderPassEnd();

    // Pipeline binding logging
    void LogPipelineBind(bool is_compute, const std::string& pipeline_info);

    // Descriptor set binding logging
    void LogDescriptorSetBind(const std::string& descriptor_info);

    // Pipeline barrier logging
    void LogPipelineBarrier(const std::string& barrier_info);

    // Image operation logging
    void LogImageOperation(const std::string& operation, const std::string& image_info);

    // Clear operation logging
    void LogClearOperation(const std::string& clear_info);

    // Crash handling
    GPUStateSnapshot GetCurrentSnapshot();
    void DumpStateToFile(const std::string& crash_reason);

    // Settings
    void SetLogLevel(LogLevel level);
    void EnableVulkanCallTracking(bool enabled);
    void EnableMemoryTracking(bool enabled);
    void EnableDriverDebugInfo(bool enabled);
    void SetRingBufferSize(size_t entries);

    // Query
    LogLevel GetLogLevel() const;
    DriverType GetDriverType() const;
    std::string GetStatistics() const;
    bool IsInitialized() const;

private:
    GPULogger();
    ~GPULogger();

    // Helper functions
    void WriteToLog(const std::string& message);
    void RotateLogFile();
    std::string FormatTimestamp(std::chrono::microseconds timestamp) const;
    std::string FormatMemorySize(u64 bytes) const;

    // State
    bool initialized = false;
    LogLevel current_level = LogLevel::Off;
    DriverType detected_driver = DriverType::Unknown;

    // Ring buffer for API calls
    std::vector<VulkanCallEntry> call_ring_buffer;
    size_t ring_buffer_index = 0;
    size_t ring_buffer_size = 512;
    mutable std::mutex ring_buffer_mutex;

    // Memory tracking
    ankerl::unordered_dense::map<uintptr_t, MemoryAllocationEntry> memory_allocations;
    mutable std::mutex memory_mutex;

    // Statistics
    u64 total_vulkan_calls = 0;
    u64 total_allocations = 0;
    u64 total_deallocations = 0;
    u64 current_allocated_bytes = 0;
    u64 peak_allocated_bytes = 0;

    // File backend for GPU logs
    std::unique_ptr<Common::FS::IOFile> gpu_log_file;
    mutable std::mutex file_mutex;
    u64 bytes_written = 0;

    // Feature flags
    bool track_vulkan_calls = true;
    bool track_memory = false;
    bool capture_driver_debug = false;

    // Extension usage tracking
    std::set<std::string> used_extensions;
    mutable std::mutex extension_mutex;

    // Stored state for crash dumps
    std::string stored_driver_debug_info;
    std::string stored_pipeline_state;
    mutable std::mutex state_mutex;
};

[[nodiscard]] bool IsActive() noexcept;

void DumpSpirvShader(u64 shader_hash, std::span<const u32> spirv_code);

// Helper to get stage name from index
inline const char* GetShaderStageName(size_t stage_index) {
    static constexpr std::array<const char*, 5> stage_names{
        "vertex", "tess_control", "tess_eval", "geometry", "fragment"
    };
    return stage_index < stage_names.size() ? stage_names[stage_index] : "unknown";
}

/**
 * Independent Vulkan black-box recorder.
 *
 * This deliberately does not depend on GPULogger log level. It keeps a bounded
 * in-memory history of cache/resource lifecycle events and only touches the
 * filesystem when an anomaly is detected or a dump is explicitly requested.
 */
class BlackBoxRecorder {
public:
    static BlackBoxRecorder& GetInstance();

    BlackBoxRecorder(const BlackBoxRecorder&) = delete;
    BlackBoxRecorder& operator=(const BlackBoxRecorder&) = delete;

    void Record(std::string_view event, u64 address = 0, u64 size = 0, u64 extra = 0);
    void Frame();
    void TriggerDump(std::string_view reason);
    bool IsEnabled() const noexcept;

private:
    struct Entry {
        std::chrono::microseconds timestamp{};
        u64 frame = 0;
        u64 address = 0;
        u64 size = 0;
        u64 extra = 0;
        std::array<char, 24> event{};
    };

    BlackBoxRecorder() = default;
    ~BlackBoxRecorder() = default;

    void Dump(std::string_view reason, std::span<const Entry> entries);
    bool ShouldAutoDump(std::chrono::microseconds now, std::string_view event);

    static constexpr size_t RING_SIZE = 65536;
    static constexpr std::chrono::seconds HISTORY = std::chrono::seconds(60);
    static constexpr std::chrono::milliseconds BURST_WINDOW =
        std::chrono::milliseconds(500);
    static constexpr u32 BURST_THRESHOLD = 512;

    std::array<Entry, RING_SIZE> ring{};
    size_t ring_index = 0;
    size_t entry_count = 0;
    u64 frame_counter = 0;
    std::chrono::microseconds last_auto_dump{};
    u32 burst_count = 0;
    std::chrono::microseconds burst_start{};
    mutable std::mutex mutex;
    std::atomic<bool> dumping{false};
};

} // namespace GPU::Logging
