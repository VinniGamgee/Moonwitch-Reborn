// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-FileCopyrightText: 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doOnTextChanged
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.HomeNavigationDirections
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.adapters.LibraryPagerAdapter
import org.yuzu.yuzu_emu.databinding.FragmentGamesBinding
import org.yuzu.yuzu_emu.fragments.LaunchGameDialogFragment
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.model.GamesViewModel
import org.yuzu.yuzu_emu.model.HomeViewModel
import org.yuzu.yuzu_emu.ui.main.MainActivity
import org.yuzu.yuzu_emu.utils.GameFrontendAudio
import org.yuzu.yuzu_emu.utils.GameHelper
import org.yuzu.yuzu_emu.utils.collect
import java.util.Locale

/** One measured viewport per game. No fixed pixel sizes or legacy grid/carousel state. */
class GamesFragment : Fragment() {
    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!
    private val gamesViewModel: GamesViewModel by activityViewModels()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private var pagerAdapter: LibraryPagerAdapter? = null
    private var selectedPath: String? = null
    private var filter = R.id.alphabetical
    private var submission = 0
    private var openingGame = false
    private val getDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) (requireActivity() as MainActivity).processGamesDir(uri, true)
    }
    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            pagerAdapter?.currentList?.getOrNull(position)?.let {
                selectedPath = it.path
                preferences.edit { putString(SELECTED, it.path) }
            }
        }
        override fun onPageScrollStateChanged(state: Int) {
            if (state == ViewPager2.SCROLL_STATE_IDLE) playSelectedAudio()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGamesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        homeViewModel.setStatusBarShadeVisibility(false)
        selectedPath = savedInstanceState?.getString(SELECTED) ?: preferences.getString(SELECTED, null)
        filter = preferences.getInt(FILTER, R.id.alphabetical)
        pagerAdapter = LibraryPagerAdapter(viewLifecycleOwner, ::openGame, ::toggleFavorite) { delta ->
            val count = pagerAdapter?.itemCount ?: 0
            if (count > 0) binding.gamePager.setCurrentItem((binding.gamePager.currentItem + delta).coerceIn(0, count - 1), true)
        }
        binding.gamePager.adapter = pagerAdapter
        binding.gamePager.registerOnPageChangeCallback(pageCallback)
        // Default ViewPager2 translation keeps the title and artwork attached to the finger.
        // Keep its normal recycling policy rather than retaining full-size pages in memory.
        binding.libraryNavigation?.apply {
            selectedItemId = R.id.mw_nav_library
            setOnItemSelectedListener {
                if (it.itemId == R.id.mw_nav_settings) navigateToSettings()
                it.itemId == R.id.mw_nav_library
            }
        }
        binding.settingsButton?.setOnClickListener { navigateToSettings() }
        binding.searchText.setText(savedInstanceState?.getString(SEARCH).orEmpty())
        binding.searchText.isVisible = !binding.searchText.text.isNullOrEmpty()
        binding.searchText.doOnTextChanged { _, _, _, _ -> filterGames() }
        binding.searchButton.setOnClickListener {
            binding.searchText.isVisible = !binding.searchText.isVisible
            if (binding.searchText.isVisible) {
                binding.searchText.requestFocus()
                (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(binding.searchText, InputMethodManager.SHOW_IMPLICIT)
            } else {
                binding.searchText.setText("")
                ViewCompat.getWindowInsetsController(binding.root)?.hide(WindowInsetsCompat.Type.ime())
            }
        }
        binding.filterButton.setOnClickListener(::showLibraryMenu)
        binding.addDirectory.setOnClickListener { getDirectory.launch(null) }
        gamesViewModel.games.collect(viewLifecycleOwner) { filterGames() }
        gamesViewModel.isReloading.collect(viewLifecycleOwner) {
            binding.loading.isVisible = it
            updateEmptyState()
        }
        gamesViewModel.shouldSwapData.collect(viewLifecycleOwner, resetState = { gamesViewModel.setShouldSwapData(false) }) {
            if (it) filterGames()
        }
        gamesViewModel.shouldScrollToTop.collect(viewLifecycleOwner, resetState = { gamesViewModel.setShouldScrollToTop(false) }) {
            if (it) binding.gamePager.setCurrentItem(0, false)
        }
        gamesViewModel.shouldScrollAfterReload.collect(viewLifecycleOwner) {
            if (it) gamesViewModel.setShouldScrollAfterReload(false)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.updatePadding(left = safe.left, top = safe.top, right = safe.right, bottom = safe.bottom)
            // The root owns insets; do not add the navigation bar a second time in Material views.
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun navigateToSettings() {
        if (findNavController().currentDestination?.id == R.id.gamesFragment) {
            findNavController().navigate(R.id.action_gamesFragment_to_homeSettingsFragment)
        }
    }

    private fun filterGames() {
        if (_binding == null) return
        val query = binding.searchText.text.toString().trim().lowercase(Locale.getDefault())
        val all = gamesViewModel.games.value
        val filtered = when (filter) {
            R.id.filter_favorites -> all.filter { preferences.getBoolean(it.keyFavorite, false) }.sortedBy { it.title.lowercase(Locale.getDefault()) }
            R.id.filter_recently_played -> all.filter { preferences.getLong(it.keyLastPlayedTime, 0) > 0 }.sortedByDescending { preferences.getLong(it.keyLastPlayedTime, 0) }
            R.id.filter_recently_added -> all.sortedByDescending { preferences.getLong(it.keyAddedToLibraryTime, 0) }
            else -> all.sortedBy { it.title.lowercase(Locale.getDefault()) }
        }.filter { query.isEmpty() || it.title.lowercase(Locale.getDefault()).contains(query) }
        gamesViewModel.setFilteredGames(filtered)
        val generation = ++submission
        val pathToRestore = selectedPath
        pagerAdapter?.submitList(filtered) {
            if (_binding == null || generation != submission) return@submitList
            val position = filtered.indexOfFirst { it.path == pathToRestore }.coerceAtLeast(0)
            if (filtered.isNotEmpty()) {
                binding.gamePager.setCurrentItem(position, false)
                selectedPath = filtered[position].path
            } else {
                GameFrontendAudio.stop()
            }
            updateEmptyState()
            playSelectedAudio()
        }
    }

    private fun updateEmptyState() {
        val empty = pagerAdapter?.itemCount == 0 && !gamesViewModel.isReloading.value
        binding.emptyState.isVisible = empty
        binding.gamePager.isVisible = !empty
        val noLibrary = gamesViewModel.games.value.isEmpty()
        binding.emptyTitle.setText(if (noLibrary) R.string.mw_reform_empty else R.string.mw_reform_no_results)
        binding.emptyHint.setText(if (noLibrary) R.string.mw_reform_empty_hint else R.string.mw_reform_no_results_hint)
        binding.addDirectory.isVisible = noLibrary
    }

    private fun showLibraryMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_game_filters, menu)
            menu.findItem(filter)?.isChecked = true
            menu.add(0, MENU_ADD, 100, R.string.mw_add_games)
            menu.add(0, MENU_REFRESH, 101, R.string.mw_reform_refresh)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_ADD -> getDirectory.launch(null)
                    MENU_REFRESH -> gamesViewModel.reloadGames(false)
                    else -> {
                        filter = item.itemId
                        preferences.edit { putInt(FILTER, filter) }
                        filterGames()
                    }
                }
                true
            }
            show()
        }
    }

    private fun toggleFavorite(game: Game) {
        preferences.edit { putBoolean(game.keyFavorite, !preferences.getBoolean(game.keyFavorite, false)) }
        if (filter == R.id.filter_favorites) filterGames()
        else pagerAdapter?.refreshFavorites()
    }

    private fun openGame(game: Game, play: Boolean) {
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
                if (play) {
                    if (GameHelper.cachedGameList.isEmpty()) {
                        withContext(Dispatchers.IO) { GameHelper.restoreContentForGame(game) }
                    }
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        LaunchGameDialogFragment.newInstance(game).show(childFragmentManager, LaunchGameDialogFragment.TAG)
                    }
                } else if (findNavController().currentDestination?.id == R.id.gamesFragment) {
                    findNavController().navigate(HomeNavigationDirections.actionGlobalPerGamePropertiesFragment(game))
                }
            } finally {
                openingGame = false
            }
        }
    }

    private fun playSelectedAudio() {
        if (_binding == null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        pagerAdapter?.currentList?.getOrNull(binding.gamePager.currentItem)?.let { GameFrontendAudio.play(requireContext(), it) }
    }
    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            filterGames()
            pagerAdapter?.refreshArtwork()
        }
    }
    override fun onPause() { GameFrontendAudio.stop(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SELECTED, selectedPath)
        outState.putString(SEARCH, _binding?.searchText?.text?.toString())
    }
    override fun onDestroyView() {
        GameFrontendAudio.stop()
        binding.gamePager.unregisterOnPageChangeCallback(pageCallback)
        binding.gamePager.adapter = null
        pagerAdapter?.close()
        pagerAdapter = null
        _binding = null
        super.onDestroyView()
    }
    companion object {
        private const val SELECTED = "MoonwitchSelectedGame"
        private const val SEARCH = "SearchText"
        private const val FILTER = "GamesSortType"
        private const val MENU_ADD = 9001
        private const val MENU_REFRESH = 9002
    }
}
