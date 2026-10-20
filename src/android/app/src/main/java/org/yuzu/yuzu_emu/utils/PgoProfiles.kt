// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.BuildConfig
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.YuzuApplication
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PgoProfiles {
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized || !NativeLibrary.isPgoTraining()) return
        val directory = File(YuzuApplication.appContext.filesDir, "pgo/${BuildConfig.VERSION_CODE}")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create PGO directory" }
        // No %m merging: each process overwrites its own cumulative snapshot. Repeated
        // exports must not add the same counters multiple times to the raw profile.
        initialized = NativeLibrary.configurePgo(
            File(directory, "process-${UUID.randomUUID()}.profraw").absolutePath
        )
    }

    fun saveSession() {
        if (!NativeLibrary.isPgoTraining()) return
        Thread({
            runCatching {
                synchronized(this) {
                    initialize()
                    check(NativeLibrary.writePgoProfile()) { "PGO profile write failed" }
                }
            }.onFailure { Log.warning("[PGO] ${it.message}") }
        }, "PgoProfileWriter").start()
    }

    fun export(fragment: Fragment) {
        val context = fragment.requireContext().applicationContext
        fragment.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { createArchive(context) }
            }
            if (!fragment.isAdded) return@launch
            result.onSuccess { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                fragment.startActivity(Intent.createChooser(intent, context.getString(R.string.pgo_export)))
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.pgo_export_failed), Toast.LENGTH_LONG).show()
                Log.warning("[PGO] ${it.message}")
            }
        }
    }

    @Synchronized
    private fun createArchive(context: Context): File {
        initialize()
        check(NativeLibrary.writePgoProfile()) { "PGO write failed" }
        val profiles = File(context.filesDir, "pgo/${BuildConfig.VERSION_CODE}").listFiles()
            ?.filter { it.extension == "profraw" && it.length() > 0 }.orEmpty()
        check(profiles.isNotEmpty()) { "No PGO profiles" }
        val output = File(context.cacheDir, "Moonwitch-PGO-${System.currentTimeMillis()}.zip")
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("build.txt"))
            zip.write(("version=${BuildConfig.VERSION_NAME}\ncode=${BuildConfig.VERSION_CODE}\n" +
                "native=${NativeLibrary.getBuildVersion()}\ndevice=${Build.MANUFACTURER} ${Build.MODEL}\nndk=28.2.13676358\n" +
                "abi=${Build.SUPPORTED_ABIS.joinToString()}\n").toByteArray())
            zip.closeEntry()
            profiles.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output
    }
}
