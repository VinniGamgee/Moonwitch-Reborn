// SPDX-FileCopyrightText: Copyright 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later

#include <stdexcept>
#include <string_view>
#include <type_traits>
#include "common/logging.h"
#include "video_core/renderer_vulkan/vk_pipeline_library.h"
#include "video_core/vulkan_common/vulkan_device.h"

namespace Vulkan {
namespace {
// Serialize values, never struct padding or pointers. Exact equality after the
// unordered_map hash prevents shader-hash collisions from reusing wrong code.
struct Key {
    std::string bytes;
    template <typename... T>
    void Add(const T&... values) {
        static_assert(
            ((std::is_arithmetic_v<T> || std::is_enum_v<T> || std::is_pointer_v<T>) && ...));
        (bytes.append(reinterpret_cast<const char*>(&values), sizeof(values)), ...);
    }
    template <typename T>
    void Array(const T* data, size_t size) {
        Add(size);
        for (size_t i = 0; i < size; ++i)
            Add(data[i]);
    }
    void Extensions(const void* next) {
        for (auto p = static_cast<const VkBaseInStructure*>(next); p; p = p->pNext) {
            Add(p->sType);
            switch (p->sType) {
            case VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_DIVISOR_STATE_CREATE_INFO_EXT: {
                const auto& s =
                    *reinterpret_cast<const VkPipelineVertexInputDivisorStateCreateInfoEXT*>(p);
                Add(s.vertexBindingDivisorCount);
                for (u32 i = 0; i < s.vertexBindingDivisorCount; ++i)
                    Add(s.pVertexBindingDivisors[i].binding, s.pVertexBindingDivisors[i].divisor);
                break;
            }
            case VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_DEPTH_CLIP_CONTROL_CREATE_INFO_EXT: {
                const auto& s =
                    *reinterpret_cast<const VkPipelineViewportDepthClipControlCreateInfoEXT*>(p);
                Add(s.negativeOneToOne);
                break;
            }
            case VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_SWIZZLE_STATE_CREATE_INFO_NV: {
                const auto& s =
                    *reinterpret_cast<const VkPipelineViewportSwizzleStateCreateInfoNV*>(p);
                Add(s.flags, s.viewportCount);
                for (u32 i = 0; i < s.viewportCount; ++i) {
                    const auto& v = s.pViewportSwizzles[i];
                    Add(v.x, v.y, v.z, v.w);
                }
                break;
            }
            case VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_LINE_STATE_CREATE_INFO_EXT: {
                const auto& s =
                    *reinterpret_cast<const VkPipelineRasterizationLineStateCreateInfoEXT*>(p);
                Add(s.lineRasterizationMode, s.stippledLineEnable, s.lineStippleFactor,
                    s.lineStipplePattern);
                break;
            }
            case VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_CONSERVATIVE_STATE_CREATE_INFO_EXT: {
                const auto& s =
                    *reinterpret_cast<const VkPipelineRasterizationConservativeStateCreateInfoEXT*>(
                        p);
                Add(s.flags, s.conservativeRasterizationMode, s.extraPrimitiveOverestimationSize);
                break;
            }
            case VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_PROVOKING_VERTEX_STATE_CREATE_INFO_EXT: {
                const auto& s = *reinterpret_cast<
                    const VkPipelineRasterizationProvokingVertexStateCreateInfoEXT*>(p);
                Add(s.provokingVertexMode);
                break;
            }
            default:
                // New state must be explicitly keyed before libraries can reuse it.
                throw std::invalid_argument("unkeyed graphics pipeline extension");
            }
        }
        Add(VK_STRUCTURE_TYPE_MAX_ENUM);
    }
    void Multisample(const VkPipelineMultisampleStateCreateInfo& s) {
        Extensions(s.pNext);
        Add(s.flags, s.rasterizationSamples, s.sampleShadingEnable, s.minSampleShading,
            s.alphaToCoverageEnable, s.alphaToOneEnable, s.pSampleMask != nullptr);
        if (s.pSampleMask)
            Array(s.pSampleMask, (static_cast<u32>(s.rasterizationSamples) + 31) / 32);
    }
    void Stencil(const VkStencilOpState& s) {
        Add(s.failOp, s.passOp, s.depthFailOp, s.compareOp, s.compareMask, s.writeMask,
            s.reference);
    }
};

std::array<std::string, 4> MakeKeys(const VkGraphicsPipelineCreateInfo& info,
                                    const std::array<std::vector<u32>, 5>& code,
                                    std::span<const u32> layout) {
    std::array<Key, 4> keys;
    for (u32 i = 0; i < keys.size(); ++i) {
        auto& k = keys[i];
        k.Add(i, info.flags);
        k.Extensions(info.pNext);
        k.Extensions(info.pDynamicState->pNext);
        k.Add(info.pDynamicState->flags);
        k.Array(info.pDynamicState->pDynamicStates, info.pDynamicState->dynamicStateCount);
        if (i != 0)
            k.Add(info.renderPass, info.subpass);
        if (i == 1 || i == 2)
            k.Array(layout.data(), layout.size());
    }
    auto& vi = keys[0];
    const auto& v = *info.pVertexInputState;
    vi.Extensions(v.pNext);
    vi.Add(v.flags, v.vertexBindingDescriptionCount);
    for (u32 i = 0; i < v.vertexBindingDescriptionCount; ++i) {
        const auto& b = v.pVertexBindingDescriptions[i];
        vi.Add(b.binding, b.stride, b.inputRate);
    }
    vi.Add(v.vertexAttributeDescriptionCount);
    for (u32 i = 0; i < v.vertexAttributeDescriptionCount; ++i) {
        const auto& a = v.pVertexAttributeDescriptions[i];
        vi.Add(a.location, a.binding, a.format, a.offset);
    }
    const auto& ia = *info.pInputAssemblyState;
    vi.Extensions(ia.pNext);
    vi.Add(ia.flags, ia.topology, ia.primitiveRestartEnable);
    for (u32 i = 0; i < info.stageCount; ++i) {
        const auto& s = info.pStages[i];
        auto& k = keys[s.stage == VK_SHADER_STAGE_FRAGMENT_BIT ? 2 : 1];
        if (s.pSpecializationInfo || std::string_view(s.pName) != "main")
            throw std::invalid_argument("unkeyed graphics shader specialization");
        k.Extensions(s.pNext);
        k.Add(s.flags, s.stage);
        // Stage index equals log2(VkShaderStageFlagBits) for these five stages.
        size_t index = 0;
        while ((1U << index) != static_cast<u32>(s.stage) && index < 5)
            ++index;
        if (index >= 5 || code[index].empty())
            throw std::invalid_argument("missing shader identity");
        k.Array(code[index].data(), code[index].size());
    }
    auto& pre = keys[1];
    if (info.pTessellationState) {
        const auto& t = *info.pTessellationState;
        pre.Extensions(t.pNext);
        pre.Add(t.flags, t.patchControlPoints);
    }
    const auto& vp = *info.pViewportState;
    // Moonwitch supplies viewport/scissor dynamically. Refuse future static arrays.
    if (vp.pViewports || vp.pScissors)
        throw std::invalid_argument("static viewport arrays");
    pre.Extensions(vp.pNext);
    pre.Add(vp.flags, vp.viewportCount, vp.scissorCount);
    const auto& r = *info.pRasterizationState;
    pre.Extensions(r.pNext);
    pre.Add(r.flags, r.depthClampEnable, r.rasterizerDiscardEnable, r.polygonMode, r.cullMode,
            r.frontFace, r.depthBiasEnable, r.depthBiasConstantFactor, r.depthBiasClamp,
            r.depthBiasSlopeFactor, r.lineWidth);
    auto& frag = keys[2];
    frag.Multisample(*info.pMultisampleState);
    const auto& d = *info.pDepthStencilState;
    frag.Extensions(d.pNext);
    frag.Add(d.flags, d.depthTestEnable, d.depthWriteEnable, d.depthCompareOp,
             d.depthBoundsTestEnable, d.stencilTestEnable, d.minDepthBounds, d.maxDepthBounds);
    frag.Stencil(d.front);
    frag.Stencil(d.back);
    auto& output = keys[3];
    output.Multisample(*info.pMultisampleState);
    const auto& c = *info.pColorBlendState;
    output.Extensions(c.pNext);
    output.Add(c.flags, c.logicOpEnable, c.logicOp, c.attachmentCount);
    output.Array(c.blendConstants, 4);
    for (u32 i = 0; i < c.attachmentCount; ++i) {
        const auto& a = c.pAttachments[i];
        output.Add(a.blendEnable, a.srcColorBlendFactor, a.dstColorBlendFactor, a.colorBlendOp,
                   a.srcAlphaBlendFactor, a.dstAlphaBlendFactor, a.alphaBlendOp, a.colorWriteMask);
    }
    return {std::move(keys[0].bytes), std::move(keys[1].bytes), std::move(keys[2].bytes),
            std::move(keys[3].bytes)};
}
} // namespace

std::shared_ptr<vk::Pipeline> GraphicsPipelineLibraryCache::Get(
    const Device& device, VkPipelineCache cache, const VkGraphicsPipelineCreateInfo& info,
    std::string key) {
    return libraries.GetOrCreate(std::move(key), [&] {
        return std::make_shared<vk::Pipeline>(
            device.GetLogical().CreateGraphicsPipeline(info, cache));
    });
}

void GraphicsPipelineLibraryCache::StopOptimization() {
    // Called after the foreground compilation workers have stopped producing jobs.
    optimizer.reset();
}

vk::Pipeline GraphicsPipelineLibraryCache::Build(const Device& device, VkPipelineCache cache,
                                                 const VkGraphicsPipelineCreateInfo& info,
                                                 const std::array<std::vector<u32>, 5>& code,
                                                 std::span<const u32> layout,
                                                 std::function<void(vk::Pipeline)> publish_optimized) {
    if (disabled.load(std::memory_order_relaxed) || !device.SupportsGraphicsPipelineLibrary() ||
        info.pRasterizationState->rasterizerDiscardEnable)
        return {};
    try {
        auto keys = MakeKeys(info, code, layout);
        constexpr std::array<VkGraphicsPipelineLibraryFlagsEXT, 4> subsets{
            VK_GRAPHICS_PIPELINE_LIBRARY_VERTEX_INPUT_INTERFACE_BIT_EXT,
            VK_GRAPHICS_PIPELINE_LIBRARY_PRE_RASTERIZATION_SHADERS_BIT_EXT,
            VK_GRAPHICS_PIPELINE_LIBRARY_FRAGMENT_SHADER_BIT_EXT,
            VK_GRAPHICS_PIPELINE_LIBRARY_FRAGMENT_OUTPUT_INTERFACE_BIT_EXT};
        std::vector<VkPipelineShaderStageCreateInfo> pre_stages, fragment_stages;
        bool tessellation = false;
        for (u32 i = 0; i < info.stageCount; ++i) {
            const auto& s = info.pStages[i];
            (s.stage == VK_SHADER_STAGE_FRAGMENT_BIT ? fragment_stages : pre_stages).push_back(s);
            tessellation |= s.stage == VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT;
        }
        std::array<std::shared_ptr<vk::Pipeline>, 4> retained;
        std::array<VkPipeline, 4> handles;
        for (size_t i = 0; i < subsets.size(); ++i) {
            const VkGraphicsPipelineLibraryCreateInfoEXT subset{
                VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_LIBRARY_CREATE_INFO_EXT, nullptr, subsets[i]};
            VkGraphicsPipelineCreateInfo part{};
            part.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
            part.pNext = &subset;
            part.flags = info.flags | VK_PIPELINE_CREATE_LIBRARY_BIT_KHR |
                         VK_PIPELINE_CREATE_RETAIN_LINK_TIME_OPTIMIZATION_INFO_BIT_EXT;
            part.pDynamicState = info.pDynamicState;
            part.basePipelineIndex = -1;
            if (i != 0) {
                part.renderPass = info.renderPass;
                part.subpass = info.subpass;
            }
            if (i == 0) {
                part.pVertexInputState = info.pVertexInputState;
                part.pInputAssemblyState = info.pInputAssemblyState;
            } else if (i == 1) {
                part.stageCount = static_cast<u32>(pre_stages.size());
                part.pStages = pre_stages.data();
                part.layout = info.layout;
                part.pTessellationState = tessellation ? info.pTessellationState : nullptr;
                part.pViewportState = info.pViewportState;
                part.pRasterizationState = info.pRasterizationState;
            } else if (i == 2) {
                part.stageCount = static_cast<u32>(fragment_stages.size());
                part.pStages = fragment_stages.data();
                part.layout = info.layout;
                part.pMultisampleState = info.pMultisampleState;
                part.pDepthStencilState = info.pDepthStencilState;
            } else {
                part.pMultisampleState = info.pMultisampleState;
                part.pColorBlendState = info.pColorBlendState;
            }
            retained[i] = Get(device, cache, part, std::move(keys[i]));
            handles[i] = **retained[i];
        }
        const VkPipelineLibraryCreateInfoKHR link{
            VK_STRUCTURE_TYPE_PIPELINE_LIBRARY_CREATE_INFO_KHR, nullptr,
            static_cast<u32>(handles.size()), handles.data()};
        VkGraphicsPipelineCreateInfo linked{};
        linked.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
        linked.pNext = &link;
        linked.flags = info.flags;
        linked.layout = info.layout;
        linked.renderPass = info.renderPass;
        linked.subpass = info.subpass;
        linked.basePipelineIndex = -1;
        auto fast = device.GetLogical().CreateGraphicsPipeline(linked, cache);
        // Capture only persistent handles/scalars and owning library references. No
        // pointers into MakePipeline's stack may escape to the optimization worker.
        // Bound additional retained library objects during large shader-cache loads.
        // Optimization is opportunistic: fast pipelines remain usable under pressure.
        size_t pending = pending_optimizations.load(std::memory_order_relaxed);
        do {
            if (pending >= 64 || optimization_failed.load(std::memory_order_relaxed)) return fast;
        } while (!pending_optimizations.compare_exchange_weak(
            pending, pending + 1, std::memory_order_relaxed));
        std::call_once(optimizer_once, [this] {
            optimizer.reset(new Common::ThreadWorker(1, "PipelineOptimize", {},
                                                     Common::ThreadPlacement::Background));
        });
        optimizer->QueueWork(
            [this, &device, cache, retained = std::move(retained), flags = info.flags,
             layout_handle = info.layout, render_pass = info.renderPass, subpass = info.subpass,
             publish = std::move(publish_optimized)] {
                if (optimization_failed.load(std::memory_order_relaxed)) {
                    pending_optimizations.fetch_sub(1, std::memory_order_relaxed);
                    return;
                }
                try {
                    std::array<VkPipeline, 4> handles;
                    for (size_t i = 0; i < handles.size(); ++i) handles[i] = **retained[i];
                    const VkPipelineLibraryCreateInfoKHR link{
                        VK_STRUCTURE_TYPE_PIPELINE_LIBRARY_CREATE_INFO_KHR, nullptr,
                        static_cast<u32>(handles.size()), handles.data()};
                    VkGraphicsPipelineCreateInfo optimized{};
                    optimized.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
                    optimized.pNext = &link;
                    optimized.flags = flags | VK_PIPELINE_CREATE_LINK_TIME_OPTIMIZATION_BIT_EXT;
                    optimized.layout = layout_handle;
                    optimized.renderPass = render_pass;
                    optimized.subpass = subpass;
                    optimized.basePipelineIndex = -1;
                    publish(device.GetLogical().CreateGraphicsPipeline(optimized, cache));
                } catch (const vk::Exception& e) {
                    optimization_failed.store(true, std::memory_order_relaxed);
                    LOG_WARNING(Render_Vulkan, "Pipeline optimization failed; retaining fast pipelines: {}", e.what());
                }
                pending_optimizations.fetch_sub(1, std::memory_order_relaxed);
            }, Common::ThreadWorkerPriority::Background);
        return fast;
    } catch (const vk::Exception& e) {
        disabled.store(true, std::memory_order_relaxed);
        LOG_WARNING(Render_Vulkan, "Pipeline libraries failed; using monolithic pipelines: {}",
                    e.what());
    } catch (const std::invalid_argument& e) {
        disabled.store(true, std::memory_order_relaxed);
        LOG_WARNING(Render_Vulkan, "Pipeline libraries unsupported state: {}", e.what());
    }
    return {};
}
} // namespace Vulkan
