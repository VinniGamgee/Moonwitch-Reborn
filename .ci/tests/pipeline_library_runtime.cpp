// SPDX-FileCopyrightText: Copyright 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later
// Compiles production cache/worker code with the real Vulkan handle wrapper and a fake driver.
#include <cassert>
#include <future>
#include <iostream>
#include <unordered_set>
#include "video_core/renderer_vulkan/vk_pipeline_library.cpp"

namespace {
std::mutex driver_mutex;
std::unordered_set<VkPipeline> alive;
uintptr_t next_handle = 1;
int library_builds{};
std::atomic_bool fail_optimization{};
std::promise<void> failed_attempt;
std::promise<void> optimization_started, allow_optimization;
auto optimization_gate = allow_optimization.get_future().share();
std::atomic_bool block_first{true};
Vulkan::vk::DeviceDispatch dispatch{};
}
namespace Common {
void SetCurrentThreadName(const char*) {}
void SetCurrentThreadPriority(ThreadPriority) {}
void SetCurrentThreadToEfficiencyCores() {}
void SetCurrentThreadToBackgroundWork() {}
void SetCurrentThreadToAllCores() {}
}
namespace Vulkan {
const vk::Device& Device::GetLogical() const { static vk::Device device; return device; }
bool Device::SupportsGraphicsPipelineLibrary() const { return true; }
namespace vk {
const char* Exception::what() const noexcept { return "fake driver failure"; }
void Destroy(VkDevice, const InstanceDispatch&) noexcept {}
void Destroy(VkDevice, VkPipeline handle, const DeviceDispatch&) noexcept {
    std::scoped_lock lock{driver_mutex}; assert(alive.erase(handle) == 1);
}
Pipeline Device::CreateGraphicsPipeline(const VkGraphicsPipelineCreateInfo& ci, VkPipelineCache) const {
    if (ci.flags & VK_PIPELINE_CREATE_LINK_TIME_OPTIMIZATION_BIT_EXT) {
        if (block_first.exchange(false)) {
            optimization_started.set_value();
            optimization_gate.wait();
        }
        if (fail_optimization) {
            failed_attempt.set_value();
            throw Exception(VK_ERROR_OUT_OF_DEVICE_MEMORY);
        }
    }
    std::scoped_lock lock{driver_mutex};
    if (ci.flags & VK_PIPELINE_CREATE_LIBRARY_BIT_KHR) {
        ++library_builds;
        assert(ci.flags & VK_PIPELINE_CREATE_RETAIN_LINK_TIME_OPTIMIZATION_INFO_BIT_EXT);
    } else {
        const auto* link=static_cast<const VkPipelineLibraryCreateInfoKHR*>(ci.pNext);
        assert(link && link->libraryCount == 4);
        for (u32 i=0;i<4;++i) assert(alive.contains(link->pLibraries[i]));
    }
    const auto handle=reinterpret_cast<VkPipeline>(next_handle++);
    alive.insert(handle);
    return Pipeline{handle, nullptr, dispatch};
}
}
}
int main() {
    VkPipelineVertexInputStateCreateInfo vi{};
    VkPipelineInputAssemblyStateCreateInfo ia{};
    VkPipelineTessellationStateCreateInfo ts{};
    VkPipelineViewportStateCreateInfo vp{};
    VkPipelineRasterizationStateCreateInfo rs{};
    VkPipelineMultisampleStateCreateInfo ms{}; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineDepthStencilStateCreateInfo ds{};
    VkPipelineColorBlendStateCreateInfo cb{};
    VkPipelineDynamicStateCreateInfo dy{};
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[0].pName=stages[1].pName="main";
    VkGraphicsPipelineCreateInfo info{};
    info.stageCount=2; info.pStages=stages; info.pVertexInputState=&vi; info.pInputAssemblyState=&ia;
    info.pTessellationState=&ts; info.pViewportState=&vp; info.pRasterizationState=&rs;
    info.pMultisampleState=&ms; info.pDepthStencilState=&ds; info.pColorBlendState=&cb; info.pDynamicState=&dy;
    std::array<std::vector<u32>,5> code; code[0]={1,2,3};code[4]={4,5,6};
    std::vector<u32> layout{0,0,0};
    Vulkan::Device device;
    {
        Vulkan::GraphicsPipelineLibraryCache cache;
        std::promise<Vulkan::vk::Pipeline> published;
        auto fast=cache.Build(device, {}, info, code, layout, [&](auto value) { published.set_value(std::move(value)); });
        assert(fast); // Fast linking returns while the optimized link is still blocked.
        optimization_started.get_future().wait();
        { std::scoped_lock lock{driver_mutex}; assert(alive.contains(*fast)); assert(library_builds==4); }
        allow_optimization.set_value();
        auto optimized=published.get_future().get();
        assert(optimized && *optimized!=*fast);
        cache.StopOptimization();
        { std::scoped_lock lock{driver_mutex}; assert(alive.contains(*fast)); assert(alive.contains(*optimized)); }
    }
    assert(alive.empty());
    {
        Vulkan::GraphicsPipelineLibraryCache cache;
        fail_optimization=true;
        bool published=false;
        auto fast=cache.Build(device, {}, info, code, layout, [&](auto) {published=true;});
        failed_attempt.get_future().wait();
        cache.StopOptimization(); // Joins the failed worker before inspecting the callback.
        assert(fast && !published);
    }
    assert(alive.empty());
    std::cout << "PASS: fast-first, retained libraries, optimized publication, failure/teardown lifetime\n";
}
