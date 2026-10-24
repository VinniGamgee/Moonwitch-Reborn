// SPDX-FileCopyrightText: Copyright 2026 Lemon-Project
// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.utils

import android.content.Context
import org.yuzu.yuzu_emu.R

object PlayTimeUtils {
    fun formatReadable(context: Context, seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> "$hours${context.getString(R.string.hours_abbr)} " +
                "$minutes${context.getString(R.string.minutes_abbr)} " +
                "$secs${context.getString(R.string.seconds_abbr)}"
            minutes > 0 -> "$minutes${context.getString(R.string.minutes_abbr)} " +
                "$secs${context.getString(R.string.seconds_abbr)}"
            else -> "$secs${context.getString(R.string.seconds_abbr)}"
        }
    }
}
