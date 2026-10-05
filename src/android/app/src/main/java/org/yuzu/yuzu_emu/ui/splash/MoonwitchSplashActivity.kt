// SPDX-FileCopyrightText: 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.ui.splash

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import kotlin.math.min
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.ui.main.MainActivity
import org.yuzu.yuzu_emu.utils.DirectoryInitialization

/**
 * Moonwitch-owned startup screen.
 *
 * Android 12+ always draws a system splash before the first Activity. The manifest theme
 * makes that system layer blank; this Activity owns the visible branding so Android never
 * gets a chance to square/crop the Moonwitch logo.
 */
class MoonwitchSplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var splashView: MoonwitchSplashView
    private var animationFinished = false
    private var launchedMain = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        setTheme(org.yuzu.yuzu_emu.utils.MoonwitchTheme.style(this))
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        splashView = MoonwitchSplashView(this)
        setContentView(splashView)

        splashView.startAnimation {
            animationFinished = true
            continueWhenReady()
        }
    }

    private fun continueWhenReady() {
        if (launchedMain || !animationFinished) return

        if (!DirectoryInitialization.areDirectoriesReady) {
            handler.postDelayed(::continueWhenReady, 40L)
            return
        }

        launchedMain = true

        // Never forward the launcher Intent itself. Launcher/Game Booster flags such as
        // NEW_TASK/RESET_TASK_IF_NEEDED can reset the task when the splash finishes.
        // MainActivity must receive a clean in-app Intent.
        val destination = Intent(this, MainActivity::class.java)

        startActivity(destination)
        overridePendingTransition(0, 0)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::splashView.isInitialized) splashView.cancelAnimation()
        super.onDestroy()
    }
}

/** Animate the same vector used by the launcher and every app header. */
private class MoonwitchSplashView(context: Context) : View(context) {
    private val mark = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.ic_moonwitch_mark)!!
    private var progress = 0f
    private var animator: ValueAnimator? = null
    fun startAnimation(onFinished: () -> Unit) {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { onFinished() }
            })
            start()
        }
    }
    fun cancelAnimation() { animator?.removeAllListeners(); animator?.cancel(); animator = null }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(com.google.android.material.color.MaterialColors.getColor(this, R.attr.mwBackground))
        val size = (min(132f * resources.displayMetrics.density, min(width, height) * 0.4f) * (0.92f + 0.08f * progress)).toInt()
        val left = (width - size) / 2
        val top = (height - size) / 2
        mark.setBounds(left, top, left + size, top + size)
        mark.alpha = (255 * progress).toInt()
        mark.draw(canvas)
    }
}
