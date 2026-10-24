// SPDX-FileCopyrightText: 2023 yuzu Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmulationViewModel : ViewModel() {
    val emulationStarted: StateFlow<Boolean> get() = _emulationStarted
    private val _emulationStarted = MutableStateFlow(false)

    val isEmulationStopping: StateFlow<Boolean> get() = _isEmulationStopping
    private val _isEmulationStopping = MutableStateFlow(false)

    private val _emulationStopped = MutableStateFlow(false)
    val emulationStopped = _emulationStopped.asStateFlow()

    private val _programChanged = MutableStateFlow(-1)
    val programChanged = _programChanged.asStateFlow()

    private val _startupProgress = MutableStateFlow(StartupProgress())
    val startupProgress = _startupProgress.asStateFlow()
    private val _firstFrameDisplayed = MutableStateFlow(false)
    val firstFrameDisplayed = _firstFrameDisplayed.asStateFlow()

    fun setFirstFrameDisplayed() {
        if (_emulationStarted.value && !_isEmulationStopping.value && !_emulationStopped.value) {
            _firstFrameDisplayed.value = true
        }
    }

    fun prepareShaderCache() {
        _startupProgress.value = StartupProgress(StartupStage.READING_CACHE)
    }

    fun updateShaderCache(completed: Int, total: Int) {
        _startupProgress.value = StartupProgress(
            StartupStage.BUILDING_SHADERS,
            completed.coerceIn(0, total.coerceAtLeast(0)),
            total.coerceAtLeast(0)
        )
    }

    fun completeShaderCache() {
        val previous = _startupProgress.value
        _startupProgress.value = previous.copy(
            stage = StartupStage.STARTING_GAME,
            completed = previous.total
        )
    }

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen = _drawerOpen.asStateFlow()

    fun setEmulationStarted(started: Boolean) {
        if (!started) {
            _firstFrameDisplayed.value = false
            _startupProgress.value = StartupProgress()
        }
        _emulationStarted.value = started
    }

    fun setIsEmulationStopping(value: Boolean) {
        _isEmulationStopping.value = value
    }

    fun setEmulationStopped(value: Boolean) {
        if (value) {
            setEmulationStarted(false)
        }
        _emulationStopped.value = value
    }

    fun setProgramChanged(programIndex: Int) {
        _programChanged.value = programIndex
    }

    fun setDrawerOpen(value: Boolean) {
        _drawerOpen.value = value
    }
}

enum class StartupStage { INITIALIZING, READING_CACHE, BUILDING_SHADERS, STARTING_GAME }

data class StartupProgress(
    val stage: StartupStage = StartupStage.INITIALIZING,
    val completed: Int = 0,
    val total: Int = 0
) {
    val percent: Int get() = if (total > 0) (completed.toLong() * 100 / total).toInt() else 0
}
