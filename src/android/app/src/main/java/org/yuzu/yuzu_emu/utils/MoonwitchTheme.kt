// SPDX-FileCopyrightText: 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.utils

import android.content.Context
import androidx.preference.PreferenceManager
import org.yuzu.yuzu_emu.R

object MoonwitchTheme {
    const val KEY = "moonwitch_theme"
    private val styles = intArrayOf(
        R.style.Theme_Moonwitch_Main,
        R.style.Theme_Moonwitch_Gaming,
        R.style.Theme_Moonwitch_Amoled,
        R.style.Theme_Moonwitch_Cyberpunk,
        R.style.Theme_Moonwitch_Monochrome,
        R.style.Theme_Moonwitch_Atmosphere
    )
    fun selected(context: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(context).getInt(KEY, 0)
            .takeIf { it in styles.indices } ?: 0

    fun style(context: Context): Int = styles[selected(context)]

    fun select(context: Context, value: Int) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(KEY, value.takeIf { it in styles.indices } ?: 0)
            .remove("moonwitch_liquid_glass")
            .apply()
    }
}
