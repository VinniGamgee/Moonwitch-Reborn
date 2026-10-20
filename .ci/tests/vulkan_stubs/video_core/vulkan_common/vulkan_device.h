#pragma once
#include "video_core/vulkan_common/vulkan_wrapper.h"
namespace Vulkan { class Device { public: const vk::Device& GetLogical() const; bool SupportsGraphicsPipelineLibrary() const; }; }
