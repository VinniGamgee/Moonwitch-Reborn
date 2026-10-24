// SPDX-FileCopyrightText: Copyright 2026 Lemon-Project
// SPDX-FileCopyrightText: Copyright 2025 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

// SPDX-FileCopyrightText: 2023 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

package org.yuzu.yuzu_emu.adapters

import android.text.Html
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import org.yuzu.yuzu_emu.databinding.PageSetupModernBinding
import org.yuzu.yuzu_emu.ui.modern.ModernSetupFinish
import org.yuzu.yuzu_emu.ui.modern.ModernSetupPage
import org.yuzu.yuzu_emu.ui.modern.SetupSummaryItem
import org.yuzu.yuzu_emu.ui.modern.SetupCardData
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.yuzu.yuzu_emu.model.PageState
import org.yuzu.yuzu_emu.model.SetupCallback
import org.yuzu.yuzu_emu.model.SetupPage
import org.yuzu.yuzu_emu.viewholder.AbstractViewHolder
import org.yuzu.yuzu_emu.model.ButtonState

class SetupAdapter(val activity: AppCompatActivity, pages: List<SetupPage>) :
    AbstractListAdapter<SetupPage, AbstractViewHolder<SetupPage>>(pages) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder<SetupPage> {
        val inflater = LayoutInflater.from(parent.context)
        return ModernSetupPageViewHolder(PageSetupModernBinding.inflate(inflater, parent, false))
    }

    // Bumped whenever something may have been completed or a step comes into view, so every card
    // (and the final recap) re-reads its state instead of showing what it saw when it was created.
    private var refresh by mutableIntStateOf(0)

    fun refreshStates() {
        refresh++
    }

    inner class ModernSetupPageViewHolder(val binding: PageSetupModernBinding) :
        AbstractViewHolder<SetupPage>(binding), SetupCallback {
        override fun bind(model: SetupPage) {
            val position = bindingAdapterPosition.coerceAtLeast(0)
            val isLast = position == itemCount - 1
            binding.composePage.setContent {
                if (isLast) {
                    // The closing step: a recap of everything the earlier steps could set up, and
                    // the button that finishes setup.
                    val version = refresh
                    val summary = remember(version) {
                        currentList.dropLast(1)
                            .flatMap { it.pageButtons ?: emptyList() }
                            .filter { it.buttonState.invoke() != ButtonState.BUTTON_ACTION_UNDEFINED }
                            .map {
                                SetupSummaryItem(
                                    activity.getString(it.titleId),
                                    it.buttonState.invoke() == ButtonState.BUTTON_ACTION_COMPLETE
                                )
                            }
                    }
                    val action = model.pageButtons?.firstOrNull()
                    ModernSetupFinish(
                        title = activity.getString(model.titleId),
                        description = Html.fromHtml(activity.getString(model.descriptionId), 0)
                            .toString().trim(),
                        summary = summary,
                        actionText = action?.let { activity.getString(it.titleId) } ?: "",
                        onAction = { action?.buttonAction?.invoke(this@ModernSetupPageViewHolder) }
                    )
                    return@setContent
                }
                // Reading `refresh` here is what makes a completed step update on screen.
                val version = refresh
                val cards = remember(version) {
                    (model.pageButtons ?: emptyList()).map { button ->
                        val state = button.buttonState.invoke()
                        SetupCardData(
                            iconRes = button.iconId,
                            title = activity.getString(button.titleId),
                            subtitle = if (button.descriptionId != 0) {
                                Html.fromHtml(activity.getString(button.descriptionId), 0)
                                    .toString().trim()
                            } else {
                                ""
                            },
                            done = state == ButtonState.BUTTON_ACTION_COMPLETE,
                            required = button.isUnskippable,
                            isAction = state == ButtonState.BUTTON_ACTION_UNDEFINED,
                            onClick = { button.buttonAction.invoke(this@ModernSetupPageViewHolder) }
                        )
                    }
                }
                val pageDone = remember(version) { model.pageSteps.invoke() == PageState.COMPLETE }
                ModernSetupPage(
                    stepNumber = position + 1,
                    stepCount = itemCount,
                    iconRes = model.iconId,
                    title = activity.getString(model.titleId),
                    description = Html.fromHtml(activity.getString(model.descriptionId), 0)
                        .toString().trim(),
                    cards = cards,
                    pageDone = pageDone
                )
            }
        }

        override fun onStepCompleted(pageButtonId: Int, pageFullyCompleted: Boolean) {
            refresh++
        }
    }

}
