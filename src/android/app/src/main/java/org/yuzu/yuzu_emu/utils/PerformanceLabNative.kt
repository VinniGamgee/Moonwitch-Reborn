// SPDX-FileCopyrightText: Copyright 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.utils

/**
 * Lightweight JNI bridge for Moonwitch Performance Lab metrics.
 *
 * Frame-time values come from individual system-frame measurements in the native core. ADPF and
 * frame-pacing/frame-skip telemetry are read from the native systems that actually drive the
 * emulator, so the overlay reports real behavior rather than frontend switch state.
 */
object PerformanceLabNative {
    data class FrameTimeSnapshot(
        val meanMs: Double,
        val medianMs: Double,
        val p95Ms: Double,
        val p99Ms: Double,
        val maxMs: Double,
        val samples: Int,
        val totalFrames: Long,
        val adpfAvailable: Boolean,
        val adpfRenderActive: Boolean,
        val adpfBackgroundActive: Boolean,
        val adpfRenderThreads: Int,
        val adpfBackgroundThreads: Int,
        val adpfReports: Long,
        val adpfTargetMs: Double,
        val adpfActualMs: Double,
        val pacingActive: Boolean,
        val pacingTargetFps: Double,
        val pacingProducerFps: Double,
        val pacingFrames: Long,
        val pacingResyncs: Long,
        val pacingDelayMs: Double,
        val frameSkipEnabled: Boolean,
        val frameSkipEligible: Boolean,
        val frameSkipPressureActive: Boolean,
        val frameSkipRenderedFrames: Long,
        val frameSkipSkippedFrames: Long,
        val frameSkipPressureScore: Int,
        val frameSkipCooldownFrames: Int,
        val frameSkipTargetFps: Double,
        val frameSkipEstimatedCompositeMs: Double,
        val frameSkipGpuBacklog: Long,
        val frameSkipPresentationBacklog: Long,
        val frameSkipPresentationCapacity: Long,
        val unleashedEnabled: Boolean,
        val unleashedKernelSupported: Boolean,
        val unleashedBoostActive: Boolean,
        val unleashedRegisteredThreads: Int,
        val unleashedAppliedThreads: Int,
        val unleashedFailedThreads: Int,
        val unleashedUtilMinPercent: Int,
        val unleashedDeadlineMisses: Long,
        val unleashedBoostEvents: Long,
        val unleashedBaselineMedianMs: Double,
        val unleashedBaselineP95Ms: Double,
        val unleashedBaselineP99Ms: Double,
        val unleashedBaselineSamples: Int,
        val unleashedActiveMedianMs: Double,
        val unleashedActiveP95Ms: Double,
        val unleashedActiveP99Ms: Double,
        val unleashedActiveSamples: Int,
        val coreDirectorEnabled: Boolean,
        val coreDirectorSnapdragonDetected: Boolean,
        val coreDirectorTopologyAvailable: Boolean,
        val coreDirectorRegisteredThreads: Int,
        val coreDirectorAppliedThreads: Int,
        val coreDirectorFailedThreads: Int,
        val coreDirectorAllowedCores: Int,
        val coreDirectorPerformanceCores: Int,
        val coreDirectorPerformanceMask: Long,
        val coreDirectorPrimeCore: Int,
        val coreDirectorApplyEvents: Long,
        val coreDirectorDriftRepairs: Long,
        val coreDirectorRestoreEvents: Long
    ) {
        val ready: Boolean
            get() = samples >= 120

        val fullWindow: Boolean
            get() = samples >= 600
    }

    external fun getRecentFrameTimeStats(): DoubleArray

    fun snapshot(): FrameTimeSnapshot {
        val values = getRecentFrameTimeStats()
        if (values.size < 63) {
            return FrameTimeSnapshot(
                0.0, 0.0, 0.0, 0.0, 0.0, 0, 0L,
                false, false, false, 0, 0, 0L, 0.0, 0.0,
                false, 0.0, 0.0, 0L, 0L, 0.0,
                false, false, false, 0L, 0L, 0, 0, 0.0, 0.0, 0L, 0L, 0L,
                false, false, false, 0, 0, 0, 0, 0L, 0L,
                0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0,
                false, false, false, 0, 0, 0, 0, 0, 0L, 0, 0L, 0L, 0L
            )
        }

        return FrameTimeSnapshot(
            meanMs = values[0],
            medianMs = values[1],
            p95Ms = values[2],
            p99Ms = values[3],
            maxMs = values[4],
            samples = values[5].toInt(),
            totalFrames = values[6].toLong(),
            adpfAvailable = values[7] != 0.0,
            adpfRenderActive = values[8] != 0.0,
            adpfBackgroundActive = values[9] != 0.0,
            adpfRenderThreads = values[10].toInt(),
            adpfBackgroundThreads = values[11].toInt(),
            adpfReports = values[12].toLong(),
            adpfTargetMs = values[13],
            adpfActualMs = values[14],
            pacingActive = values[15] != 0.0,
            pacingTargetFps = values[16],
            pacingProducerFps = values[17],
            pacingFrames = values[18].toLong(),
            pacingResyncs = values[19].toLong(),
            pacingDelayMs = values[20],
            frameSkipEnabled = values[21] != 0.0,
            frameSkipEligible = values[22] != 0.0,
            frameSkipPressureActive = values[23] != 0.0,
            frameSkipRenderedFrames = values[24].toLong(),
            frameSkipSkippedFrames = values[25].toLong(),
            frameSkipPressureScore = values[26].toInt(),
            frameSkipCooldownFrames = values[27].toInt(),
            frameSkipTargetFps = values[28],
            frameSkipEstimatedCompositeMs = values[29],
            frameSkipGpuBacklog = values[30].toLong(),
            frameSkipPresentationBacklog = values[31].toLong(),
            frameSkipPresentationCapacity = values[32].toLong(),
            unleashedEnabled = values[33] != 0.0,
            unleashedKernelSupported = values[34] != 0.0,
            unleashedBoostActive = values[35] != 0.0,
            unleashedRegisteredThreads = values[36].toInt(),
            unleashedAppliedThreads = values[37].toInt(),
            unleashedFailedThreads = values[38].toInt(),
            unleashedUtilMinPercent = values[39].toInt(),
            unleashedDeadlineMisses = values[40].toLong(),
            unleashedBoostEvents = values[41].toLong(),
            unleashedBaselineMedianMs = values[42],
            unleashedBaselineP95Ms = values[43],
            unleashedBaselineP99Ms = values[44],
            unleashedBaselineSamples = values[45].toInt(),
            unleashedActiveMedianMs = values[46],
            unleashedActiveP95Ms = values[47],
            unleashedActiveP99Ms = values[48],
            unleashedActiveSamples = values[49].toInt(),
            coreDirectorEnabled = values[50] != 0.0,
            coreDirectorSnapdragonDetected = values[51] != 0.0,
            coreDirectorTopologyAvailable = values[52] != 0.0,
            coreDirectorRegisteredThreads = values[53].toInt(),
            coreDirectorAppliedThreads = values[54].toInt(),
            coreDirectorFailedThreads = values[55].toInt(),
            coreDirectorAllowedCores = values[56].toInt(),
            coreDirectorPerformanceCores = values[57].toInt(),
            coreDirectorPerformanceMask = values[58].toLong(),
            coreDirectorPrimeCore = values[59].toInt(),
            coreDirectorApplyEvents = values[60].toLong(),
            coreDirectorDriftRepairs = values[61].toLong(),
            coreDirectorRestoreEvents = values[62].toLong()
        )
    }
}
