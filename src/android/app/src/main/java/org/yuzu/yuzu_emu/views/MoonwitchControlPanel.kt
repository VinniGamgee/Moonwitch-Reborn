// SPDX-FileCopyrightText: 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Menu
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import org.yuzu.yuzu_emu.R

/** Menu actions retain their original IDs, callbacks and live state. */
class MoonwitchControlPanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    val menu: Menu = PopupMenu(context, this).menu.apply {
        android.view.MenuInflater(context).inflate(R.menu.menu_in_game, this)
    }
    private val header = inflate(context, R.layout.header_in_game, null)
    private val grid = GridLayout(context).apply { columnCount = 2; setPadding(dp(8), dp(8), dp(8), dp(20)) }
    private var listener: NavigationView.OnNavigationItemSelectedListener? = null
    private val buttons = mutableMapOf<Int, MaterialButton>()
    private val icons = mutableMapOf<Int, android.graphics.drawable.Drawable?>()
    private val update = ViewTreeObserver.OnPreDrawListener { if (isShown) sync(); true }

    init {
        isFocusableInTouchMode = true
        setBackgroundColor(0xFF0C1424.toInt())
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(grid)
        }
        addView(ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            val button = MaterialButton(context).apply {
                id = item.itemId
                text = item.title
                icon = item.icon
                iconGravity = MaterialButton.ICON_GRAVITY_TOP
                iconSize = dp(26)
                iconPadding = dp(8)
                cornerRadius = dp(20)
                strokeWidth = dp(1)
                strokeColor = ColorStateList.valueOf(0xFF415578.toInt())
                backgroundTintList = ColorStateList.valueOf(0xFF18243B.toInt())
                setTextColor(0xFFF1F5FF.toInt())
                iconTint = ColorStateList.valueOf(if (id == R.id.menu_exit) 0xFFFFA4B5.toInt() else 0xFF68E4D0.toInt())
                textSize = 13f
                isAllCaps = false
                minimumHeight = dp(104)
                insetTop = 0; insetBottom = 0
                setPadding(dp(8), dp(12), dp(8), dp(12))
                setOnClickListener {
                    if (item.isEnabled) listener?.onNavigationItemSelected(item)
                    sync()
                }
            }
            buttons[item.itemId] = button
            grid.addView(button, GridLayout.LayoutParams().apply {
                width = 0; height = LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        sync()
    }

    fun getHeaderView(index: Int): View { require(index == 0); return header }
    fun setNavigationItemSelectedListener(value: NavigationView.OnNavigationItemSelectedListener?) { listener = value }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun sync() {
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            val button = buttons[item.itemId] ?: continue
            if (button.text != item.title) button.text = item.title
            if (icons[item.itemId] !== item.icon) { icons[item.itemId] = item.icon; button.icon = item.icon }
            button.visibility = if (item.isVisible) View.VISIBLE else View.GONE
            button.isEnabled = item.isEnabled
            button.alpha = if (item.isEnabled) 1f else 0.45f
            button.isSelected = item.isChecked
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); viewTreeObserver.addOnPreDrawListener(update) }
    override fun onDetachedFromWindow() { viewTreeObserver.removeOnPreDrawListener(update); super.onDetachedFromWindow() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = minOf(MeasureSpec.getSize(widthMeasureSpec), dp(360))
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
    }
}
