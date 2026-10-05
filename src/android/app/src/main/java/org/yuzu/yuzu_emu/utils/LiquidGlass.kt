// SPDX-FileCopyrightText: 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.graphics.drawable.BitmapDrawable
import org.yuzu.yuzu_emu.R
import java.util.WeakHashMap
import kotlin.math.max

/** Shared, downsampled backdrop blur. Foreground controls are never blurred. */
object LiquidGlass {
    fun isEnabled(context: Context) = context.getSharedPreferences(
        context.packageName + "_preferences", Context.MODE_PRIVATE
    ).getBoolean("moonwitch_liquid_glass", false)

    fun install(application: Application) {
        val sessions = WeakHashMap<Activity, Session>()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                sessions.remove(activity)?.stop()
                if (isEnabled(activity)) sessions[activity] = Session(activity).also { it.start() }
            }
            override fun onActivityPaused(activity: Activity) { sessions.remove(activity)?.stop() }
            override fun onActivityDestroyed(activity: Activity) { sessions.remove(activity)?.stop() }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        })
    }

    private class Session(val activity: Activity) : Runnable {
        private val root = activity.window.decorView
        private val handler = Handler(Looper.getMainLooper())
        private val originals = WeakHashMap<View, Drawable?>()
        private val tintOriginals = WeakHashMap<View, ColorStateList?>()
        private val panes = WeakHashMap<View, Glass>()
        private val iconOriginals = WeakHashMap<Button, ColorStateList?>()
        private val textOriginals = WeakHashMap<Button, ColorStateList>()
        private val softImages = WeakHashMap<Bitmap, Bitmap>()
        private var frame: Bitmap? = null
        private var gameFrame: Bitmap? = null
        private var copying = false
        private var active = false
        private val density = root.resources.displayMetrics.density
        fun start() { active = true; handler.post(this) }
        fun stop() {
            active = false; handler.removeCallbacks(this)
            originals.forEach { (view, background) -> view.background = background }
            tintOriginals.forEach { (view, tint) -> view.backgroundTintList = tint }
            iconOriginals.forEach { (button, colors) ->
                runCatching { button.javaClass.getMethod("setIconTint", ColorStateList::class.java).invoke(button, colors) }
            }
            iconOriginals.clear()
            textOriginals.forEach { (button, colors) -> button.setTextColor(colors) }
            originals.clear(); panes.clear(); tintOriginals.clear(); textOriginals.clear(); softImages.clear()
            frame = null; gameFrame = null
        }
        private fun walk(view: View, action: (View) -> Unit) {
            action(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), action)
        }
        private fun visible(view: View): Boolean = view.isShown && view.getGlobalVisibleRect(Rect())
        override fun run() {
            if (!active) return
            if (root.width > 0 && root.height > 0) {
                var surface: SurfaceView? = null
                walk(root) { view ->
                    if (view is SurfaceView && visible(view)) surface = view
                    if (view.width <= 0 || view.height <= 0 || originals.containsKey(view)) return@walk
                    val name = view.javaClass.simpleName
                    val panel = name.contains("CardView") || name.contains("Toolbar") ||
                        name.contains("NavigationView") || name == "MoonwitchControlPanel" ||
                        view is Button || (view is ViewGroup && view.isClickable && view.background != null)
                    if (panel) {
                        originals[view] = view.background
                        tintOriginals[view] = view.backgroundTintList
                        view.backgroundTintList = null
                        if (view is Button) {
                            textOriginals[view] = view.textColors
                            runCatching {
                                iconOriginals[view] = view.javaClass.getMethod("getIconTint").invoke(view) as? ColorStateList
                                view.javaClass.getMethod("setIconTint", ColorStateList::class.java).invoke(view, ColorStateList.valueOf(0xFFE1FFF7.toInt()))
                            }
                            view.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(0xFF8392A7.toInt(), 0xFFF0FBFF.toInt())))
                        }
                        val glass = Glass(view)
                        panes[view] = glass
                        view.background = RippleDrawable(ColorStateList.valueOf(0x306CE5CF), glass, null)
                    } else if (view is ViewGroup && view.background != null &&
                        view.width >= root.width * 0.75 && view.height >= root.height * 0.65 &&
                        !name.contains("Surface")) {
                        originals[view] = view.background
                        view.background = activity.getDrawable(R.drawable.mw_glass_canvas)
                    }
                }
                val visiblePanes = panes.keys.filter { visible(it) }
                // No capture work while only the emulated game is visible.
                if (visiblePanes.isNotEmpty()) {
                    capture(visiblePanes, surface)
                    visiblePanes.forEach { it.invalidate() }
                }
            }
            handler.postDelayed(this, 200)
        }
        private fun capture(visiblePanes: List<View>, surface: SurfaceView?) {
            val width = max(1, root.width / 12)
            val height = max(1, root.height / 12)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(width.toFloat() / root.width, height.toFloat() / root.height)
            canvas.drawColor(0xFF152638.toInt())
            val oldAlpha = visiblePanes.map { it.alpha }
            val images = mutableMapOf<ImageView, Drawable>()
            walk(root) { view ->
                if (view is ImageView && visible(view)) {
                    val drawable = view.drawable as? BitmapDrawable
                    val source = drawable?.bitmap
                    if (source != null && source.config == Bitmap.Config.HARDWARE) {
                        val soft = softImages[source] ?: source.copy(Bitmap.Config.ARGB_8888, false)?.also { softImages[source] = it }
                        if (soft != null) { images[view] = drawable; view.setImageBitmap(soft) }
                    }
                }
            }
            try {
                visiblePanes.forEach { it.alpha = 0f }
                root.draw(canvas)
            } catch (_: IllegalArgumentException) {
                // Some vendor views cannot draw into a software canvas. Keep a readable tint.
                canvas.drawColor(0xFF152638.toInt())
            } finally {
                visiblePanes.forEachIndexed { i, view -> view.alpha = oldAlpha[i] }
                images.forEach { (view, drawable) -> view.setImageDrawable(drawable) }
            }
            val game = gameFrame
            if (surface != null && game != null) {
                val location = IntArray(2); val origin = IntArray(2)
                surface.getLocationOnScreen(location); root.getLocationOnScreen(origin)
                val x = (location[0] - origin[0]).toFloat(); val y = (location[1] - origin[1]).toFloat()
                canvas.drawBitmap(game, null, RectF(x, y, x + surface.width, y + surface.height), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            blur(bitmap)
            frame = bitmap
            if (surface != null && surface.holder.surface.isValid && !copying) {
                copying = true
                val copy = Bitmap.createBitmap(max(1, surface.width / 12), max(1, surface.height / 12), Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(surface, copy, { result ->
                        if (active && result == PixelCopy.SUCCESS) gameFrame = copy
                        copying = false
                    }, handler)
                } catch (_: IllegalArgumentException) { copying = false }
            }
        }
        // Separable box blur, repeated twice, on the small shared backdrop.
        private fun blur(bitmap: Bitmap) {
            val w = bitmap.width; val h = bitmap.height
            var source = IntArray(w * h); var target = IntArray(w * h)
            bitmap.getPixels(source, 0, w, 0, 0, w, h)
            repeat(2) {
                for (horizontal in listOf(true, false)) {
                    for (y in 0 until h) for (x in 0 until w) {
                        var red = 0; var green = 0; var blue = 0
                        for (offset in -3..3) {
                            val px = if (horizontal) (x + offset).coerceIn(0, w - 1) else x
                            val py = if (horizontal) y else (y + offset).coerceIn(0, h - 1)
                            val color = source[py * w + px]
                            red += Color.red(color); green += Color.green(color); blue += Color.blue(color)
                        }
                        target[y * w + x] = Color.rgb(red / 7, green / 7, blue / 7)
                    }
                    val swap = source; source = target; target = swap
                }
            }
            bitmap.setPixels(source, 0, w, 0, 0, w, h)
        }
        private inner class Glass(val view: View) : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            override fun draw(canvas: Canvas) {
                val rect = RectF(bounds)
                val radius = minOf(20 * density, rect.height() / 2)
                val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
                canvas.save(); canvas.clipPath(path)
                frame?.let { bitmap ->
                    val location = IntArray(2); val origin = IntArray(2)
                    view.getLocationOnScreen(location); root.getLocationOnScreen(origin)
                    val x = (location[0] - origin[0]).toFloat(); val y = (location[1] - origin[1]).toFloat()
                    // Slightly magnified backdrop supplies the lens-like edge treatment.
                    val dst = RectF(-x - 2 * density, -y - 2 * density, root.width - x + 2 * density, root.height - y + 2 * density)
                    paint.shader = null; paint.color = Color.WHITE; paint.style = Paint.Style.FILL
                    canvas.drawBitmap(bitmap, null, dst, paint)
                }
                paint.shader = LinearGradient(0f, 0f, rect.width(), rect.height(), intArrayOf(0xA02C4560.toInt(), 0xC0101B31.toInt(), 0xB021414C.toInt()), null, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.shader = null; paint.color = 0x609FE9DF; paint.style = Paint.Style.STROKE; paint.strokeWidth = density
                rect.inset(density / 2, density / 2); canvas.drawRoundRect(rect, radius, radius, paint)
                paint.style = Paint.Style.FILL
                canvas.restore()
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(filter: ColorFilter?) {}
            @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }
}
