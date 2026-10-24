// SPDX-FileCopyrightText: Copyright 2026 Lemon-Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import org.yuzu.yuzu_emu.HomeNavigationDirections
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.features.settings.ui.SettingsSubscreen
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.model.GamesViewModel
import org.yuzu.yuzu_emu.model.HomeViewModel
import org.yuzu.yuzu_emu.ui.main.MainActivity
import org.yuzu.yuzu_emu.ui.modern.LibraryActions
import org.yuzu.yuzu_emu.ui.modern.ModernLibraryScreen
import org.yuzu.yuzu_emu.ui.modern.PullToRefresh
import org.yuzu.yuzu_emu.utils.GameHelper
import org.yuzu.yuzu_emu.utils.GameIconUtils
import org.yuzu.yuzu_emu.fragments.LaunchGameDialogFragment
import androidx.lifecycle.Lifecycle
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import org.yuzu.yuzu_emu.utils.GameStatsUtils

/** The redesigned library (see [ModernLibraryScreen]); [GamesFragment] stays as the classic one. */
class ModernGamesFragment : Fragment() {
    private val gamesViewModel: GamesViewModel by activityViewModels()
    private val homeViewModel: HomeViewModel by activityViewModels()

    private val getGamesDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
            if (result != null) {
                (requireActivity() as MainActivity).processGamesDir(result, true)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val games by gamesViewModel.games.collectAsState()
            val reloading by gamesViewModel.isReloading.collectAsState()
            val preferences = remember {
                PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext)
            }
            val lastPlayedPath = remember(games) {
                GameStatsUtils.findLastPlayed(requireContext(), games)?.path
            }
            val actions = remember { buildActions() }

            Box(Modifier.fillMaxSize()) {
                PullToRefresh(
                    refreshing = reloading,
                    onRefresh = { gamesViewModel.reloadGames(false) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    ModernLibraryScreen(
                        games = games,
                        loading = reloading,
                        preferences = preferences,
                        initialSelectedPath = lastPlayedPath,
                        showQLaunch = BooleanSetting.ENABLE_QLAUNCH_BUTTON.getBoolean() && NativeLibrary.isFirmwareAvailable(),
                        actions = actions
                    )
                }

            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeViewModel.setStatusBarShadeVisibility(false)
    }

    private fun buildActions() = LibraryActions(
        onSelected = { game ->
            selectedAudioGame = game
            if (game == null) org.yuzu.yuzu_emu.utils.GameFrontendAudio.stop()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && game != null)
                org.yuzu.yuzu_emu.utils.GameFrontendAudio.play(requireContext(), game)
        },
        onLaunch = { game ->
            openGame(game)
        },
        onDetails = { game ->
            findNavController().navigate(
                HomeNavigationDirections.actionGlobalPerGamePropertiesFragment(game)
            )
        },
        onToggleFavorite = { game ->
            val preferences =
                PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext)
            preferences.edit {
                putBoolean(game.keyFavorite, !preferences.getBoolean(game.keyFavorite, false))
            }
        },
        onSettings = { findNavController().navigate(R.id.action_gamesFragment_to_homeSettingsFragment) },
        onStatistics = { findNavController().navigate(R.id.action_global_statisticsFragment) },
        onAddGames = { getGamesDirectory.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).data) },
        onManageFolders = {
            findNavController().navigate(
                HomeNavigationDirections.actionGlobalSettingsSubscreenActivity(
                    SettingsSubscreen.GAME_FOLDERS,
                    null
                )
            )
        },
        onInstallContent = {
            findNavController().navigate(
                HomeNavigationDirections.actionGlobalSettingsSubscreenActivity(
                    SettingsSubscreen.INSTALLABLE,
                    null
                )
            )
        },
        onLaunchQLaunch = ::launchQLaunch,
        onPerformance = { game ->
            findNavController().navigate(HomeNavigationDirections.actionGlobalSettingsActivity(game, org.yuzu.yuzu_emu.features.settings.model.Settings.MenuTag.SECTION_MOONWITCH_PERFORMANCE))
        },
        onDriverSettings = { game ->
            findNavController().navigate(
                HomeNavigationDirections.actionGlobalSettingsActivity(
                    game,
                    org.yuzu.yuzu_emu.features.settings.model.Settings.MenuTag.SECTION_DRIVERS_COMPONENTS
                )
            )
        },
        onAddShortcut = ::requestPinShortcut,
        hasDriverOption = true,
        canPinShortcut = ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())
    )

    private fun requestPinShortcut(game: Game) {
        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val shortcut = ShortcutInfoCompat.Builder(activity, "pin_${game.path}")
                    .setShortLabel(game.title)
                    .setIcon(GameIconUtils.getShortcutIcon(activity, game))
                    .setIntent(game.launchIntent)
                    .build()
                ShortcutManagerCompat.requestPinShortcut(activity, shortcut, null)
            }
        }
    }

    private var selectedAudioGame: Game? = null
    override fun onResume() {
        super.onResume()
        selectedAudioGame?.let { org.yuzu.yuzu_emu.utils.GameFrontendAudio.play(requireContext(), it) }
    }
    override fun onPause() {
        org.yuzu.yuzu_emu.utils.GameFrontendAudio.stop()
        super.onPause()
    }
    private var openingGame = false

    private fun openGame(game: Game) {
        if (openingGame) return
        openingGame = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val exists = withContext(Dispatchers.IO) {
                    runCatching { DocumentFile.fromSingleUri(requireContext(), Uri.parse(game.path))?.exists() == true }.getOrDefault(false)
                }
                if (!exists) {
                    Toast.makeText(requireContext(), R.string.loader_error_file_not_found, Toast.LENGTH_LONG).show()
                    gamesViewModel.reloadGames(true)
                    return@launch
                }
                if (GameHelper.cachedGameList.isEmpty()) {
                    withContext(Dispatchers.IO) { GameHelper.restoreContentForGame(game) }
                }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    LaunchGameDialogFragment.newInstance(game).show(childFragmentManager, LaunchGameDialogFragment.TAG)
                }
            } finally {
                openingGame = false
            }
        }
    }

    private fun launchQLaunch() {
        try {
            val applet = org.yuzu.yuzu_emu.model.AppletInfo.QLaunch
            val path = NativeLibrary.getAppletLaunchPath(applet.entryId)
            val qlaunchGame = if (path.isEmpty()) null else Game(title = getString(R.string.qlaunch_applet), path = path)
            if (qlaunchGame == null) {
                Toast.makeText(requireContext(), R.string.applets_error_applet, Toast.LENGTH_SHORT).show()
                return
            }
            NativeLibrary.setCurrentAppletId(applet.appletId)
            findNavController().navigate(HomeNavigationDirections.actionGlobalEmulationActivity(qlaunchGame))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Failed to launch QLaunch: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
