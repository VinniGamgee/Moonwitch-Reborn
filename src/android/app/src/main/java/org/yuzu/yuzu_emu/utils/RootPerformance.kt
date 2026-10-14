// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.utils

import android.content.Context
import android.os.Process
import androidx.preference.PreferenceManager
import org.yuzu.yuzu_emu.BuildConfig
import java.io.File
import java.util.UUID

/** One root controller per native emulation session, independent of Activity recreation. */
object RootPerformance {
    const val ENABLED = "moonwitch_root_performance"
    const val REPORT = "moonwitch_root_report"
    val available: Boolean get() = BuildConfig.FLAVOR == "overclockRoot"
    private var lease: File? = null

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        if (!available || lease != null || !prefs.getBoolean(ENABLED, false)) return
        val token = File(app.filesDir, "root-session-${UUID.randomUUID()}")
        try {
            token.writeText("active")
        } catch (e: Exception) {
            prefs.edit().putString(REPORT, "Controller failed: ${e.message}").apply()
            return
        }
        lease = token
        prefs.edit().putString(REPORT, "Waiting for Magisk root access…").apply()
        Thread({
            val report = ArrayDeque<String>()
            fun record(line: String) {
                if (report.size >= 80) report.removeFirst()
                report.addLast(line)
                Log.info("[Performance Engine] $line")
                prefs.edit().putString(REPORT, report.joinToString("\n")).apply()
            }
            try {
                // Unique script avoids replacing a script that an older session is executing.
                val script = File(app.filesDir, "${token.name}.sh")
                try {
                    app.assets.open("performance/root-session.sh").use { input ->
                        script.outputStream().use { input.copyTo(it) }
                    }
                    if (token.exists()) {
                        val command = "sh ${quote(script.absolutePath)} ${Process.myPid()} ${quote(token.absolutePath)}"
                        val process = ProcessBuilder("su", "-c", command)
                            .redirectErrorStream(true).start()
                        process.outputStream.close()
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { record(it) }
                        }
                        record("Controller exit: ${process.waitFor()}")
                    }
                } finally {
                    script.delete()
                }
            } catch (e: Exception) {
                record("Root controller unavailable: ${e.message}")
            } finally {
                token.delete()
                synchronized(this) { if (lease == token) lease = null }
            }
        }, "MoonwitchRootPerformance").start()
    }

    @Synchronized
    fun stop() {
        // The root process restores the snapshot on lease removal or app process death.
        lease?.delete()
        lease = null
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
