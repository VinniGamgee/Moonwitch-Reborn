// SPDX-FileCopyrightText: 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.ui.modern

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import java.io.File

internal object MoonwitchArtworkChanges {
    var revision by mutableIntStateOf(0)
        private set
    fun notifyChanged() { revision++ }
}

/** Custom artwork is bounded and cached by Coil; file probing/decoding never blocks composition. */
@Composable
fun rememberMoonwitchArtwork(game: Game, kind: String): ImageBitmap? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) generation++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val bitmap by produceState<ImageBitmap?>(null, game.path, kind, generation, MoonwitchArtworkChanges.revision) {
        value = withContext(Dispatchers.IO) {
            val names = when (kind) {
                "hero" -> listOf("hero", "background", "cover", "poster", "boxart")
                "logo" -> listOf("logo")
                else -> listOf("cover", "poster", "boxart")
            }
            val dir = File(DirectoryInitialization.userDirectory, "moonwitch/metadata/${game.settingsName}")
            val file = names.asSequence().flatMap { name ->
                sequenceOf("jpg", "jpeg", "png", "webp").map { File(dir, "$name.$it") }
            }.firstOrNull { it.isFile } ?: return@withContext null
            context.imageLoader.execute(ImageRequest.Builder(context)
                .data(file).size(1280, 1280).allowHardware(false)
                .memoryCacheKey("moonwitch:${file.path}:${file.lastModified()}").build())
                .drawable?.toBitmap()?.asImageBitmap()
        }
    }
    return bitmap
}
