// SPDX-FileCopyrightText: Copyright 2026 Lemon-Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.ui.modern

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.yuzu.yuzu_emu.R

/** Moonwitch palette on Lemon Project's original components: midnight surfaces and lunar violet. */
object LemonColors {
    var Background by mutableStateOf(Color(0xFF0B0E20))
        private set
    var Surface by mutableStateOf(Color(0xFF171C35))
        private set
    var SurfaceRaised by mutableStateOf(Color(0xFF242846))
        private set
    var Outline by mutableStateOf(Color(0xFF465170))
        private set
    var Text by mutableStateOf(Color(0xFFF5F0FF))
        private set
    var TextMuted by mutableStateOf(Color(0xFFBBB6D0))
        private set
    var Lemon by mutableStateOf(Color(0xFF55E6D2))
        private set
    var OnLemon by mutableStateOf(Color(0xFF0B0E20))
        private set
    val Red = Color(0xFFF0432E)

    // Called after applying the Activity theme; keeps all Moonwitch palette choices functional.
    fun apply(context: android.content.Context) {
        fun color(attr: Int): Color {
            val value = android.util.TypedValue()
            check(context.theme.resolveAttribute(attr, value, true))
            return Color(value.data)
        }
        Background = color(R.attr.mwBackground)
        Surface = color(R.attr.mwSurface)
        SurfaceRaised = color(R.attr.mwSurfaceHighest)
        Outline = color(R.attr.mwOutline)
        Text = color(R.attr.mwText)
        TextMuted = color(R.attr.mwMuted)
        Lemon = color(R.attr.mwAccent)
        OnLemon = Background
    }
}

/** Title face (Bricolage Grotesque) and body face (DM Sans), both variable fonts bundled in res/font. */
@OptIn(ExperimentalTextApi::class)
object LemonFonts {
    private fun face(resId: Int, weight: Int) = Font(
        resId = resId,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight))
    )

    val Title: FontFamily = FontFamily(
        face(R.font.bricolage_grotesque, 600),
        face(R.font.bricolage_grotesque, 700),
        face(R.font.bricolage_grotesque, 800)
    )
    val Body: FontFamily = FontFamily(
        face(R.font.dm_sans, 400),
        face(R.font.dm_sans, 500),
        face(R.font.dm_sans, 600),
        face(R.font.dm_sans, 700)
    )
}

object LemonType {
    val Display get() = TextStyle(
        fontFamily = LemonFonts.Title,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        letterSpacing = (-0.3).sp,
        color = LemonColors.Text
    )
    val Heading get() = TextStyle(
        fontFamily = LemonFonts.Title,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        color = LemonColors.Text
    )
    val Button get() = TextStyle(
        fontFamily = LemonFonts.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = LemonColors.Text
    )
    val Body get() = TextStyle(
        fontFamily = LemonFonts.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = LemonColors.TextMuted
    )
    val Label get() = TextStyle(
        fontFamily = LemonFonts.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        color = LemonColors.Lemon
    )
}
