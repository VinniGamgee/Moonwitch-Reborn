// SPDX-FileCopyrightText: Copyright 2026 Lemon-Project
// SPDX-FileCopyrightText: 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.*
import androidx.navigation.fragment.findNavController
import org.yuzu.yuzu_emu.HomeNavigationDirections
import org.yuzu.yuzu_emu.model.*
import org.yuzu.yuzu_emu.ui.modern.*
import org.yuzu.yuzu_emu.utils.GameStatsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StatisticsFragment : Fragment() {
    private val gamesViewModel: GamesViewModel by activityViewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val games by gamesViewModel.games.collectAsState()
                val entries by produceState<List<GameStatEntry>>(emptyList(), games) {
                    value = withContext(Dispatchers.IO) { games.map {
                        GameStatEntry(it, GameStatsUtils.getPlayTimeSeconds(it),
                            GameStatsUtils.getLastPlayedMillis(context, it),
                            GameStatsUtils.getSessionCount(context, it))
                    } }
                }
                ModernStatistics(entries, StatisticsActions(
                    onBack = { findNavController().popBackStack() },
                    onOpenGame = { findNavController().navigate(HomeNavigationDirections.actionGlobalPerGamePropertiesFragment(it.game)) }
                ))
            }
        }
}
