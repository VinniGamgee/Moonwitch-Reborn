// SPDX-FileCopyrightText: Copyright 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include "common/common_types.h"

namespace Common::MoonwitchCoreDirector {

enum class ThreadRole : u32 {
    CPUCore,
    GPU,
    VulkanWorker,
};

struct Telemetry {
    bool enabled{};
    bool snapdragon_detected{};
    bool topology_available{};
    u32 registered_threads{};
    u32 applied_threads{};
    u32 failed_threads{};
    u32 allowed_core_count{};
    u32 performance_core_count{};
    u64 performance_core_mask{};
    u32 prime_core{};
    u64 apply_events{};
    u64 drift_repairs{};
    u64 restore_events{};
};

// Captures the thread's original affinity and applies the Snapdragon performance mask live.
void RegisterCurrentThread(ThreadRole role);

// Enables or disables hard affinity. Disable always restores every captured original mask.
void SetEnabled(bool enabled);

// Re-verifies affinity periodically because some vendor kernels overwrite userspace masks.
void Maintain();

Telemetry GetTelemetry();

// Restores every tracked thread and forgets the topology at the end of a game session.
void EndSession();

} // namespace Common::MoonwitchCoreDirector
