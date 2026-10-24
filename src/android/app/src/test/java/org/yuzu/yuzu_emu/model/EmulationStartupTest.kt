// SPDX-FileCopyrightText: 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.model

import org.junit.Assert.*
import org.junit.Test

class EmulationStartupTest {
    @Test fun initializationAndCacheCompletionDoNotRevealBlackSurface() {
        val model = EmulationViewModel()
        model.setEmulationStarted(true)
        assertFalse(model.firstFrameDisplayed.value)
        model.prepareShaderCache()
        model.updateShaderCache(42, 100)
        assertEquals(42, model.startupProgress.value.percent)
        model.completeShaderCache()
        assertEquals(100, model.startupProgress.value.completed)
        assertEquals(100, model.startupProgress.value.percent)
        assertEquals(StartupStage.STARTING_GAME, model.startupProgress.value.stage)
        assertFalse(model.firstFrameDisplayed.value)
        model.setFirstFrameDisplayed()
        assertTrue(model.firstFrameDisplayed.value)
    }

    @Test fun emptyOrDisabledCacheCanReachTheGame() {
        val model = EmulationViewModel()
        model.setEmulationStarted(true)
        model.completeShaderCache()
        assertEquals(0, model.startupProgress.value.total)
        assertEquals(StartupStage.STARTING_GAME, model.startupProgress.value.stage)
        model.setFirstFrameDisplayed()
        assertTrue(model.firstFrameDisplayed.value)
    }

    @Test fun stoppingIgnoresLateFramesAndNextGameResetsCounters() {
        val model = EmulationViewModel()
        model.setEmulationStarted(true)
        model.updateShaderCache(5, 10)
        model.setIsEmulationStopping(true)
        model.setFirstFrameDisplayed()
        assertFalse(model.firstFrameDisplayed.value)
        model.setEmulationStopped(true)
        assertEquals(StartupProgress(), model.startupProgress.value)
        model.setIsEmulationStopping(false)
        model.setFirstFrameDisplayed()
        assertFalse(model.firstFrameDisplayed.value)
        model.setEmulationStopped(false)
        model.setEmulationStarted(true)
        model.prepareShaderCache()
        assertEquals(0, model.startupProgress.value.total)
    }

    @Test fun progressIsBoundedAndLargeCountsDoNotOverflow() {
        val model = EmulationViewModel()
        model.updateShaderCache(-1, -5)
        assertEquals(0, model.startupProgress.value.percent)
        model.updateShaderCache(15, 10)
        assertEquals(100, model.startupProgress.value.percent)
        model.updateShaderCache(Int.MAX_VALUE / 2, Int.MAX_VALUE)
        assertEquals(49, model.startupProgress.value.percent)
    }
}
