// SPDX-FileCopyrightText: Copyright 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <array>
#include <atomic>
#include <memory>
#include <functional>
#include <mutex>
#include "common/single_flight_cache.h"
#include "common/thread_worker.h"
#include <span>
#include <string>
#include <vector>

#include "video_core/vulkan_common/vulkan_wrapper.h"

namespace Vulkan {
class Device;

// Session-local, shared by the existing pipeline compilation workers. Inspired by
// Ryujinx Neo's four-part library cache; see docs/RYUJINX_NEO_PORTS.md.
class GraphicsPipelineLibraryCache {
public:
    GraphicsPipelineLibraryCache() = default;
    void StopOptimization();

    vk::Pipeline Build(const Device& device, VkPipelineCache cache,
                       const VkGraphicsPipelineCreateInfo& info,
                       const std::array<std::vector<u32>, 5>& code, std::span<const u32> layout,
                       std::function<void(vk::Pipeline)> publish_optimized);

private:
    std::shared_ptr<vk::Pipeline> Get(const Device& device, VkPipelineCache cache,
                                      const VkGraphicsPipelineCreateInfo& info, std::string key);
    Common::SingleFlightCache<vk::Pipeline> libraries{2048, 64 * 1024 * 1024};
    std::atomic_bool disabled{};
    std::atomic_bool optimization_failed{};
    std::atomic<size_t> pending_optimizations{};
    std::once_flag optimizer_once;
    // Last member: cancel queued work and join the active optimization before cache destruction.
    std::unique_ptr<Common::ThreadWorker> optimizer;
};
} // namespace Vulkan
