// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.dialogs

import android.content.Context
import java.io.File
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.utils.RootPerformance

object PerformanceEngineDialog {
    fun show(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val choices = arrayOf(context.getString(R.string.root_engine_off), context.getString(R.string.root_engine_max))
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.root_engine_title)
            .setSingleChoiceItems(choices, if (prefs.getBoolean(RootPerformance.ENABLED, false)) 1 else 0) { _, index ->
                prefs.edit().putBoolean(RootPerformance.ENABLED, index == 1).apply()
                if (index == 0) RootPerformance.stop()
            }
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.root_engine_report) { _, _ ->
                val log = File(context.filesDir, RootPerformance.LOG_FILE)
                val report = runCatching {
                    if (log.exists()) log.readLines().takeLast(80).joinToString("\n") else null
                }.getOrNull()
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.root_engine_report)
                    .setMessage(report?.takeIf { it.isNotBlank() } ?: prefs.getString(RootPerformance.REPORT, context.getString(R.string.root_engine_idle)))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            .show()
    }
}
