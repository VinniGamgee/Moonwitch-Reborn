// SPDX-FileCopyrightText: Copyright 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <chrono>
#include <cstddef>
#include "common/common_types.h"

namespace Common::MoonwitchUnleashed {

enum class ThreadRole : u32 {
    CPUCore,
    GPU,
    VulkanWorker,
};

struct FrameStats {
    double median_ms{};
    double p95_ms{};
    double p99_ms{};
    u32 sample_count{};
};

struct Telemetry {
    bool enabled{};
    bool kernel_supported{};
    bool boost_active{};
    u32 registered_threads{};
    u32 applied_threads{};
    u32 failed_threads{};
    u32 requested_util_min_percent{};
    u64 deadline_misses{};
    u64 boost_events{};
    FrameStats baseline{};
    FrameStats active{};
};

// Registers only the latency-critical emulation threads selected by Moonwitch. The original
// per-thread clamp is captured before any change and restored on thread exit or session shutdown.
void RegisterCurrentThread(ThreadRole role);

// Applies or restores the scheduler floor immediately for every registered critical thread.
void SetEnabled(bool enabled);

// Drives the short 90% burst after repeated deadline misses and records before/after samples.
void ReportFrameWorkDuration(std::chrono::nanoseconds actual,
                             std::chrono::nanoseconds target);

Telemetry GetTelemetry();

// Restores all captured scheduler values without changing the saved setting for the next session.
void EndSession();

} // namespace Common::MoonwitchUnleashed
