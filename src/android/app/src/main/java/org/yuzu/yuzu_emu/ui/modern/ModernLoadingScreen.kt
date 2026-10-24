// SPDX-FileCopyrightText: 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.ui.modern

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.model.StartupProgress
import org.yuzu.yuzu_emu.model.StartupStage

/** Moonwitch's startup status, driven by native cache progress and first-frame presentation. */
@Composable
fun MoonwitchLoadingScreen(game: Game?, progress: StartupProgress, stopping: Boolean) {
    val icon = game?.let { rememberGameIcon(it) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(LemonColors.Background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        val wide = maxWidth >= 600.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (wide && icon != null) {
                    Image(
                        icon, null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(144.dp).clip(RoundedCornerShape(20.dp))
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!wide && icon != null) {
                        Image(
                            icon, null, contentScale = ContentScale.Fit,
                            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(18.dp))
                        )
                    }
                    BasicText("MOONWITCH", style = LemonType.Label.copy(color = LemonColors.Lemon))
                    BasicText(
                        game?.title.orEmpty(),
                        style = LemonType.Heading.copy(fontSize = 24.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    if (!stopping) StartupSteps(progress.stage)
                    val message = when {
                        stopping -> R.string.shutting_down
                        progress.stage == StartupStage.READING_CACHE -> R.string.mw_startup_reading
                        progress.stage == StartupStage.BUILDING_SHADERS -> R.string.mw_startup_shaders
                        progress.stage == StartupStage.STARTING_GAME -> R.string.mw_startup_first_frame
                        else -> R.string.mw_startup_game
                    }
                    BasicText(stringResource(message), style = LemonType.Body)
                    val determinate = !stopping && progress.total > 0 &&
                        progress.stage in listOf(StartupStage.BUILDING_SHADERS, StartupStage.STARTING_GAME)
                    if (determinate) {
                        BasicText(
                            "${progress.percent}%",
                            style = LemonType.Display.copy(fontSize = 36.sp, color = LemonColors.Lemon)
                        )
                        BasicText(
                            stringResource(R.string.mw_startup_shader_count, progress.completed, progress.total),
                            style = LemonType.Body
                        )
                    }
                    StartupBar(if (determinate) progress.completed.toFloat() / progress.total else null)
                }
            }
        }
    }
}

@Composable
private fun StartupSteps(stage: StartupStage) {
    val active = when (stage) {
        StartupStage.INITIALIZING -> 0
        StartupStage.READING_CACHE, StartupStage.BUILDING_SHADERS -> 1
        StartupStage.STARTING_GAME -> 2
    }
    val labels = listOf(R.string.mw_startup_step_game, R.string.mw_startup_step_shaders, R.string.mw_startup_step_start)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(
                    if (index <= active) LemonColors.Lemon else LemonColors.Outline
                ))
                BasicText(
                    stringResource(label),
                    style = LemonType.Body.copy(fontSize = 12.sp,
                        color = if (index == active) LemonColors.Text else LemonColors.TextMuted)
                )
            }
        }
    }
}

@Composable
private fun StartupBar(fraction: Float?) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            .background(LemonColors.Outline)
            .semantics {
                progressBarRangeInfo = fraction?.let { ProgressBarRangeInfo(it.coerceIn(0f, 1f), 0f..1f) }
                    ?: ProgressBarRangeInfo.Indeterminate
            }
    ) {
        if (fraction != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(LemonColors.Lemon))
        } else {
            val offset by rememberInfiniteTransition(label = "startupWaiting").animateFloat(
                initialValue = -0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
                label = "startupWaitingPosition"
            )
            Box(Modifier.offset(x = maxWidth * offset).width(maxWidth * 0.3f)
                .fillMaxHeight().background(LemonColors.Lemon))
        }
    }
}
