// SPDX-FileCopyrightText: Copyright 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include "common/moonwitch_core_director.h"
#include "common/moonwitch_unleashed.h"
#include "common/thread.h"

namespace Common {
enum class PerformanceThreadRole { CPUCore, GPU, VulkanWorker };

inline void RegisterPerformanceThread(PerformanceThreadRole role) {
    // Base placement is the normal emulator policy. Core Director captures this
    // mask and restores it when disabled; it exclusively owns optional hard pinning.
    // Unleashed owns only utilization clamps, preserving priority and affinity.
    SetCurrentThreadToPerformanceCores();
    switch (role) {
    case PerformanceThreadRole::CPUCore:
        MoonwitchUnleashed::RegisterCurrentThread(MoonwitchUnleashed::ThreadRole::CPUCore);
        MoonwitchCoreDirector::RegisterCurrentThread(MoonwitchCoreDirector::ThreadRole::CPUCore);
        break;
    case PerformanceThreadRole::GPU:
        MoonwitchUnleashed::RegisterCurrentThread(MoonwitchUnleashed::ThreadRole::GPU);
        MoonwitchCoreDirector::RegisterCurrentThread(MoonwitchCoreDirector::ThreadRole::GPU);
        break;
    case PerformanceThreadRole::VulkanWorker:
        MoonwitchUnleashed::RegisterCurrentThread(MoonwitchUnleashed::ThreadRole::VulkanWorker);
        MoonwitchCoreDirector::RegisterCurrentThread(MoonwitchCoreDirector::ThreadRole::VulkanWorker);
        break;
    }
}
} // namespace Common
