// SPDX-FileCopyrightText: Copyright 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <algorithm>
#include <chrono>
#include <jni.h>

#include "common/adaptive_frame_pacing.h"
#include "common/adpf.h"
#include "common/moonwitch_unleashed.h"
#include "common/moonwitch_core_director.h"
#include "common/smart_adaptive_frame_skip.h"
#include "native.h"

extern "C" {

jdoubleArray Java_org_yuzu_yuzu_1emu_utils_PerformanceLabNative_getRecentFrameTimeStats(
    JNIEnv* env, jobject /*instance*/) {
    constexpr jsize StatCount = 63;
    constexpr std::size_t LiveStatCount = 33;
    jdoubleArray j_stats = env->NewDoubleArray(StatCount);
    if (j_stats == nullptr) {
        return j_stats;
    }

    jdouble values[StatCount]{};
    const auto unleashed = Common::MoonwitchUnleashed::GetTelemetry();
    values[33] = unleashed.enabled ? 1.0 : 0.0;
    values[34] = unleashed.kernel_supported ? 1.0 : 0.0;
    values[35] = unleashed.boost_active ? 1.0 : 0.0;
    values[36] = static_cast<double>(unleashed.registered_threads);
    values[37] = static_cast<double>(unleashed.applied_threads);
    values[38] = static_cast<double>(unleashed.failed_threads);
    values[39] = static_cast<double>(unleashed.requested_util_min_percent);
    values[40] = static_cast<double>(unleashed.deadline_misses);
    values[41] = static_cast<double>(unleashed.boost_events);
    values[42] = unleashed.baseline.median_ms;
    values[43] = unleashed.baseline.p95_ms;
    values[44] = unleashed.baseline.p99_ms;
    values[45] = static_cast<double>(unleashed.baseline.sample_count);
    values[46] = unleashed.active.median_ms;
    values[47] = unleashed.active.p95_ms;
    values[48] = unleashed.active.p99_ms;
    values[49] = static_cast<double>(unleashed.active.sample_count);

    const auto core_director = Common::MoonwitchCoreDirector::GetTelemetry();
    values[50] = core_director.enabled ? 1.0 : 0.0;
    values[51] = core_director.snapdragon_detected ? 1.0 : 0.0;
    values[52] = core_director.topology_available ? 1.0 : 0.0;
    values[53] = static_cast<double>(core_director.registered_threads);
    values[54] = static_cast<double>(core_director.applied_threads);
    values[55] = static_cast<double>(core_director.failed_threads);
    values[56] = static_cast<double>(core_director.allowed_core_count);
    values[57] = static_cast<double>(core_director.performance_core_count);
    values[58] = static_cast<double>(core_director.performance_core_mask);
    values[59] = static_cast<double>(core_director.prime_core);
    values[60] = static_cast<double>(core_director.apply_events);
    values[61] = static_cast<double>(core_director.drift_repairs);
    values[62] = static_cast<double>(core_director.restore_events);

    if (!EmulationSession::GetInstance().IsRunning()) {
        env->SetDoubleArrayRegion(j_stats, 0, StatCount, values);
        return j_stats;
    }

    const auto stats =
        EmulationSession::GetInstance().System().GetPerfStats().GetRecentFrameTimeStats();
    const auto adpf = Common::ADPF::GetTelemetry();
    const auto pacing = Common::AdaptiveFramePacing::GetTelemetry();
    const auto frame_skip = Common::SmartAdaptiveFrameSkip::GetTelemetry();
    const double target_ms =
        std::chrono::duration<double, std::milli>(adpf.target_work_duration).count();
    const double actual_ms =
        std::chrono::duration<double, std::milli>(adpf.last_actual_work_duration).count();

    const double live_values[LiveStatCount] = {
        stats.mean_ms,
        stats.median_ms,
        stats.p95_ms,
        stats.p99_ms,
        stats.max_ms,
        static_cast<double>(stats.sample_count),
        static_cast<double>(stats.total_system_frames),
        adpf.available ? 1.0 : 0.0,
        adpf.render_active ? 1.0 : 0.0,
        adpf.background_active ? 1.0 : 0.0,
        static_cast<double>(adpf.render_thread_count),
        static_cast<double>(adpf.background_thread_count),
        static_cast<double>(adpf.successful_reports),
        target_ms,
        actual_ms,
        pacing.active ? 1.0 : 0.0,
        pacing.target_fps,
        pacing.producer_fps,
        static_cast<double>(pacing.paced_frames),
        static_cast<double>(pacing.resyncs),
        pacing.last_delay_ms,
        frame_skip.enabled ? 1.0 : 0.0,
        frame_skip.eligible ? 1.0 : 0.0,
        frame_skip.pressure_active ? 1.0 : 0.0,
        static_cast<double>(frame_skip.rendered_frames),
        static_cast<double>(frame_skip.skipped_frames),
        static_cast<double>(frame_skip.pressure_score),
        static_cast<double>(frame_skip.cooldown_frames),
        frame_skip.target_fps,
        frame_skip.estimated_composite_ms,
        static_cast<double>(frame_skip.gpu_backlog),
        static_cast<double>(frame_skip.presentation_backlog),
        static_cast<double>(frame_skip.presentation_capacity),
    };
    std::copy_n(live_values, LiveStatCount, values);
    env->SetDoubleArrayRegion(j_stats, 0, StatCount, values);
    return j_stats;
}

} // extern "C"
