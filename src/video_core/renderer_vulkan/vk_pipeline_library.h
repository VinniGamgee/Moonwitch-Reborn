// SPDX-FileCopyrightText: Copyright 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <span>
#include <string>
#include <unordered_map>
#include <vector>

#include "video_core/vulkan_common/vulkan_wrapper.h"

namespace Vulkan {
class Device;

// Session-local, shared by the existing pipeline compilation workers. Inspired by
// Ryujinx Neo's four-part library cache; see docs/RYUJINX_NEO_PORTS.md.
class GraphicsPipelineLibraryCache {
public:
    vk::Pipeline Build(const Device& device, VkPipelineCache cache,
                       const VkGraphicsPipelineCreateInfo& info,
                       const std::array<std::vector<u32>, 5>& code, std::span<const u32> layout);

private:
    std::shared_ptr<vk::Pipeline> Get(const Device& device, VkPipelineCache cache,
                                      const VkGraphicsPipelineCreateInfo& info, std::string key);
    std::mutex mutex;
    std::unordered_map<std::string, std::shared_ptr<vk::Pipeline>> libraries;
    size_t key_bytes{};
    std::atomic_bool disabled{};
};
} // namespace Vulkan
