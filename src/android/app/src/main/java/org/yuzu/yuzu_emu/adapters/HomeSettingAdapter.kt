// SPDX-FileCopyrightText: Copyright 2025 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

// SPDX-FileCopyrightText: 2023 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

package org.yuzu.yuzu_emu.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.yuzu.yuzu_emu.databinding.CardHomeOptionBinding
import org.yuzu.yuzu_emu.fragments.MessageDialogFragment
import org.yuzu.yuzu_emu.model.HomeSetting
import org.yuzu.yuzu_emu.utils.ViewUtils.marquee
import org.yuzu.yuzu_emu.utils.ViewUtils.setVisible
import org.yuzu.yuzu_emu.utils.collect
import org.yuzu.yuzu_emu.viewholder.AbstractViewHolder

class HomeSettingAdapter(
    private val activity: AppCompatActivity,
    private val viewLifecycle: LifecycleOwner,
    options: List<HomeSetting>
) : AbstractListAdapter<HomeSetting, HomeSettingAdapter.HomeOptionViewHolder>(options) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeOptionViewHolder {
        CardHomeOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            .also { return HomeOptionViewHolder(it) }
    }

    override fun onViewRecycled(holder: HomeOptionViewHolder) {
        holder.detailJob?.cancel()
        super.onViewRecycled(holder)
    }

    inner class HomeOptionViewHolder(val binding: CardHomeOptionBinding) :
        AbstractViewHolder<HomeSetting>(binding) {
        var detailJob: Job? = null
        override fun bind(model: HomeSetting) {
            detailJob?.cancel()
            binding.optionTitle.text = activity.resources.getString(model.titleId)
            binding.optionDescription.text = activity.resources.getString(model.descriptionId)
            binding.optionIcon.setImageDrawable(
                ResourcesCompat.getDrawable(
                    activity.resources,
                    model.iconId,
                    activity.theme
                )
            )

            val alpha = if (model.isEnabled.invoke()) 1f else 0.5f
            binding.optionTitle.alpha = alpha
            binding.optionDescription.alpha = alpha
            binding.optionIcon.alpha = alpha
            binding.optionDetail.text = model.details.value
            binding.optionDetail.setVisible(model.details.value.isNotBlank())
            detailJob = viewLifecycle.lifecycleScope.launch {
                viewLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    model.details.collect { detail ->
                        binding.optionDetail.text = detail
                        binding.optionDetail.setVisible(detail.isNotBlank())
                    }
                }
            }

            binding.root.setOnClickListener { onClick(model) }
        }

        private fun onClick(model: HomeSetting) {
            if (model.isEnabled.invoke()) {
                model.onClick.invoke()
            } else {
                MessageDialogFragment.newInstance(
                    activity,
                    titleId = model.disabledTitleId,
                    descriptionId = model.disabledMessageId
                ).show(activity.supportFragmentManager, MessageDialogFragment.TAG)
            }
        }

        private fun updateOptionDetails(detailString: String) {
            if (detailString.isNotEmpty()) {
                binding.optionDetail.text = detailString
                binding.optionDetail.setVisible(true)
            }
        }
    }
}
