// SPDX-FileCopyrightText: Copyright 2018 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#pragma once

#include <functional>
#include <optional>
#include <span>
#include <utility>
#include "common/common_types.h"
#include "common/polyfill_thread.h"
#include "common/settings.h"
#include "video_core/cache_types.h"
#include "video_core/engines/fermi_2d.h"
#include "video_core/gpu.h"
#include "video_core/query_cache/types.h"
#include "video_core/rasterizer_download_area.h"

namespace Tegra {
class MemoryManager;
namespace Engines {
class AccelerateDMAInterface;
}
namespace Control {
struct ChannelState;
}
} // namespace Tegra

namespace VideoCore {

enum class LoadCallbackStage { Prepare, Build, Complete };
using DiskResourceLoadCallback = std::function<void(LoadCallbackStage, std::size_t, std::size_t)>;

class RasterizerInterface {
public:
    virtual ~RasterizerInterface() = default;
    virtual void Draw(bool is_indexed, u32 instance_count) = 0;
    virtual void DrawIndirect() {}
    virtual void DrawTexture() = 0;
    virtual void Clear(u32 layer_count) = 0;
    virtual void DispatchCompute() = 0;
    virtual void ResetCounter(VideoCommon::QueryType type) = 0;
    virtual void Query(GPUVAddr gpu_addr, VideoCommon::QueryType type,
                       VideoCommon::QueryPropertiesFlags flags, u32 payload, u32 subreport) = 0;
    virtual void BindGraphicsUniformBuffer(size_t stage, u32 index, GPUVAddr gpu_addr, u32 size) = 0;
    virtual void DisableGraphicsUniformBuffer(size_t stage, u32 index) = 0;
    virtual void SignalFence(std::function<void()>&& func) = 0;
    virtual void SyncOperation(std::function<void()>&& func) = 0;
    virtual void SignalSyncPoint(u32 value) = 0;
    virtual void SignalReference() = 0;
    virtual void ReleaseFences(bool force = true) = 0;

    /// Wait for deferred rasterizer/fence operations to complete.
    virtual void WaitForFence() {
        ReleaseFences(true);
    }

    virtual void FlushAll() = 0;
    virtual void FlushRegion(DAddr addr, u64 size,
                             VideoCommon::CacheType which = VideoCommon::CacheType::All) = 0;
    virtual bool MustFlushRegion(DAddr addr, u64 size,
                                 VideoCommon::CacheType which = VideoCommon::CacheType::All) = 0;
    virtual RasterizerDownloadArea GetFlushArea(DAddr addr, u64 size) = 0;
    virtual void InvalidateRegion(DAddr addr, u64 size,
                                  VideoCommon::CacheType which = VideoCommon::CacheType::All) = 0;

    virtual void InnerInvalidation(std::span<const std::pair<DAddr, std::size_t>> sequences) {
        if (!Settings::values.skip_cpu_inner_invalidation.GetValue()) {
            for (const auto& [cpu_addr, size] : sequences) {
                InvalidateRegion(cpu_addr, size);
            }
        }
    }

    virtual void OnCacheInvalidation(PAddr addr, u64 size) = 0;
    virtual bool OnCPUWrite(PAddr addr, u64 size) = 0;
    virtual void InvalidateGPUCache() = 0;
    virtual void UnmapMemory(DAddr addr, u64 size) = 0;
    virtual void ModifyGPUMemory(size_t as_id, GPUVAddr addr, u64 size) = 0;
    virtual void FlushAndInvalidateRegion(
        DAddr addr, u64 size, VideoCommon::CacheType which = VideoCommon::CacheType::All) = 0;
    virtual void WaitForIdle() = 0;
    virtual void FragmentBarrier() = 0;
    virtual void TiledCacheBarrier() = 0;
    virtual void FlushCommands() = 0;
    virtual void TickFrame() = 0;
    virtual bool AccelerateConditionalRendering() { return false; }
    [[nodiscard]] virtual bool AccelerateSurfaceCopy(
        const Tegra::Engines::Fermi2D::Surface& src, const Tegra::Engines::Fermi2D::Surface& dst,
        const Tegra::Engines::Fermi2D::Config& copy_config) { return false; }
    [[nodiscard]] virtual Tegra::Engines::AccelerateDMAInterface& AccessAccelerateDMA() = 0;
    virtual void AccelerateInlineToMemory(GPUVAddr address, size_t copy_size,
                                          std::span<const u8> memory) = 0;
    virtual void LoadDiskResources(u64 title_id, std::stop_token stop_loading,
                                   const DiskResourceLoadCallback& callback) {}
    virtual void InitializeChannel(Tegra::Control::ChannelState& channel) {}
    virtual void BindChannel(Tegra::Control::ChannelState& channel) {}
    virtual void ReleaseChannel(s32 channel_id) {}
    virtual void RegisterTransformFeedback(GPUVAddr tfb_object_addr) {}
    virtual bool HasDrawTransformFeedback() { return false; }
};
} // namespace VideoCore
