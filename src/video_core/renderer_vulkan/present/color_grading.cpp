// SPDX-FileCopyrightText: Copyright 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <algorithm>
#include <tuple>
#include <vector>

#include "common/settings.h"
#include "video_core/host_shaders/moonwitch_color_grading_frag_spv.h"
#include "video_core/host_shaders/vulkan_fidelityfx_fsr_vert_spv.h"
#include "video_core/renderer_vulkan/present/color_grading.h"
#include "video_core/renderer_vulkan/present/util.h"
#include "video_core/renderer_vulkan/vk_scheduler.h"
#include "video_core/vulkan_common/vulkan_device.h"

namespace Vulkan {

struct ColorGradingPushConstants {
    s32 mode;
    f32 strength;
};

MoonwitchColorGrading::MoonwitchColorGrading(const Device& device,
                                             MemoryAllocator& allocator,
                                             size_t image_count_, VkExtent2D extent_)
    : extent{extent_}, image_count{static_cast<u32>(image_count_)} {
    CreateImages(device, allocator);
    CreateRenderPasses(device);
    CreateSampler(device);
    CreateShaders(device);
    CreateDescriptorPool(device);
    CreateDescriptorSetLayout(device);
    CreateDescriptorSets(device);
    CreatePipelineLayout(device);
    CreatePipeline(device);
}

MoonwitchColorGrading::~MoonwitchColorGrading() = default;

void MoonwitchColorGrading::CreateImages(const Device& device,
                                         MemoryAllocator& allocator) {
    images.resize(image_count);
    for (auto& image : images) {
        image.image =
            CreateWrappedImage(allocator, extent, VK_FORMAT_R16G16B16A16_SFLOAT);
        image.image_view = CreateWrappedImageView(device, image.image,
                                                  VK_FORMAT_R16G16B16A16_SFLOAT);
    }
}

void MoonwitchColorGrading::CreateRenderPasses(const Device& device) {
    renderpass = CreateWrappedRenderPass(device, VK_FORMAT_R16G16B16A16_SFLOAT);
    for (auto& image : images) {
        image.framebuffer =
            CreateWrappedFramebuffer(device, renderpass, image.image_view, extent);
    }
}

void MoonwitchColorGrading::CreateSampler(const Device& device) {
    sampler = CreateNearestNeighborSampler(device);
}

void MoonwitchColorGrading::CreateShaders(const Device& device) {
    vertex_shader =
        CreateWrappedShaderModule(device, VULKAN_FIDELITYFX_FSR_VERT_SPV);
    fragment_shader =
        CreateWrappedShaderModule(device, MOONWITCH_COLOR_GRADING_FRAG_SPV);
}

void MoonwitchColorGrading::CreateDescriptorPool(const Device& device) {
    descriptor_pool = CreateWrappedDescriptorPool(device, image_count, image_count);
}

void MoonwitchColorGrading::CreateDescriptorSetLayout(const Device& device) {
    descriptor_set_layout = CreateWrappedDescriptorSetLayout(
        device, {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER});
}

void MoonwitchColorGrading::CreateDescriptorSets(const Device& device) {
    (void)device;
    const VkDescriptorSetLayout layout = *descriptor_set_layout;
    for (auto& image : images) {
        image.descriptor_sets = CreateWrappedDescriptorSets(descriptor_pool, {layout});
    }
}

void MoonwitchColorGrading::CreatePipelineLayout(const Device& device) {
    const VkPushConstantRange range{
        .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT,
        .offset = 0,
        .size = sizeof(ColorGradingPushConstants),
    };
    const VkPipelineLayoutCreateInfo ci{
        .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
        .pNext = nullptr,
        .flags = 0,
        .setLayoutCount = 1,
        .pSetLayouts = descriptor_set_layout.address(),
        .pushConstantRangeCount = 1,
        .pPushConstantRanges = &range,
    };
    pipeline_layout = device.GetLogical().CreatePipelineLayout(ci);
}

void MoonwitchColorGrading::CreatePipeline(const Device& device) {
    pipeline = CreateWrappedPipeline(device, renderpass, pipeline_layout,
                                     std::tie(vertex_shader, fragment_shader));
}

void MoonwitchColorGrading::UpdateDescriptorSet(const Device& device,
                                                VkImageView source_image_view,
                                                size_t image_index) {
    auto& image = images[image_index];
    std::vector<VkDescriptorImageInfo> image_infos;
    image_infos.reserve(1);
    std::vector<VkWriteDescriptorSet> updates{CreateWriteDescriptorSet(
        image_infos, *sampler, source_image_view, image.descriptor_sets[0], 0)};
    device.GetLogical().UpdateDescriptorSets(updates, {});
}

void MoonwitchColorGrading::UploadImages(const Device& device, Scheduler& scheduler) {
    if (images_ready) {
        return;
    }
    scheduler.Record([&](vk::CommandBuffer cmdbuf) {
        for (auto& image : images) {
            ClearColorImage(cmdbuf, *image.image);
        }
    });
    scheduler.Finish();
    images_ready = true;
}

VkImageView MoonwitchColorGrading::Draw(const Device& device, Scheduler& scheduler,
                                        size_t image_index,
                                        VkImageView source_image_view) {
    auto& image = images[image_index];
    const s32 mode = std::clamp(
        Settings::values.moonwitch_color_grading_mode.GetValue(), 0, 8);
    const f32 strength = std::clamp(
        static_cast<f32>(Settings::values.moonwitch_color_grading_strength.GetValue()) /
            100.0f,
        0.0f, 1.0f);
    const ColorGradingPushConstants push_constants{
        .mode = mode,
        .strength = strength,
    };

    UploadImages(device, scheduler);
    UpdateDescriptorSet(device, source_image_view, image_index);

    const VkImage output_image = *image.image;
    const VkFramebuffer framebuffer = *image.framebuffer;
    const VkRenderPass current_renderpass = *renderpass;
    const VkPipeline current_pipeline = *pipeline;
    const VkPipelineLayout current_layout = *pipeline_layout;
    const VkDescriptorSet descriptor_set = image.descriptor_sets[0];
    const VkExtent2D current_extent = extent;

    scheduler.RequestOutsideRenderPassOperationContext();
    scheduler.Record([=](vk::CommandBuffer cmdbuf) {
        TransitionImageLayout(cmdbuf, output_image, VK_IMAGE_LAYOUT_GENERAL);
        BeginRenderPass(cmdbuf, current_renderpass, framebuffer, current_extent);
        cmdbuf.BindPipeline(VK_PIPELINE_BIND_POINT_GRAPHICS, current_pipeline);
        cmdbuf.BindDescriptorSets(VK_PIPELINE_BIND_POINT_GRAPHICS, current_layout, 0,
                                  descriptor_set, {});
        cmdbuf.PushConstants(current_layout, VK_SHADER_STAGE_FRAGMENT_BIT,
                             push_constants);
        cmdbuf.Draw(3, 1, 0, 0);
        cmdbuf.EndRenderPass();
        TransitionImageLayout(cmdbuf, output_image, VK_IMAGE_LAYOUT_GENERAL);
    });

    return *image.image_view;
}

bool MoonwitchColorGrading::NeedsRecreation(VkExtent2D new_extent) const {
    return new_extent.width != extent.width || new_extent.height != extent.height;
}

} // namespace Vulkan
