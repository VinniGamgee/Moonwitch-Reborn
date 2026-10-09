// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.fragments

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.documentfile.provider.DocumentFile
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doOnTextChanged
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.transition.MaterialSharedAxis
import org.yuzu.yuzu_emu.HomeNavigationDirections
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.YuzuApplication
import org.yuzu.yuzu_emu.adapters.HomeSettingAdapter
import org.yuzu.yuzu_emu.databinding.FragmentHomeSettingsBinding
import org.yuzu.yuzu_emu.features.DocumentProvider
import org.yuzu.yuzu_emu.features.fetcher.SpacingItemDecoration
import org.yuzu.yuzu_emu.features.settings.model.Settings
import org.yuzu.yuzu_emu.features.settings.ui.SettingsSubscreen
import org.yuzu.yuzu_emu.model.HomeSetting
import org.yuzu.yuzu_emu.model.HomeViewModel
import org.yuzu.yuzu_emu.ui.main.MainActivity
import org.yuzu.yuzu_emu.utils.FileUtil
import org.yuzu.yuzu_emu.utils.Log
import org.yuzu.yuzu_emu.utils.ViewUtils.updateMargins

class HomeSettingsFragment : Fragment() {
    private var _binding: FragmentHomeSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var mainActivity: MainActivity
    private var allOptions: List<HomeSetting> = emptyList()
    private var rootOptions: List<HomeSetting> = emptyList()
    private var currentOptions: List<HomeSetting> = emptyList()
    private var groupTitle: Int? = null
    private lateinit var groupBack: OnBackPressedCallback


    private val homeViewModel: HomeViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeSettingsBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeViewModel.setStatusBarShadeVisibility(visible = false)
        mainActivity = requireActivity() as MainActivity
        binding.toolbarHomeSettings.title = getString(R.string.mw_reform_settings)

        val optionsList: MutableList<HomeSetting> = mutableListOf<HomeSetting>().apply {
            add(
                HomeSetting(
                    R.string.mw_ui_performance,
                    R.string.mw_ui_perf_desc,
                    R.drawable.ic_mw_gauge,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsActivity(
                            null,
                            Settings.MenuTag.SECTION_MOONWITCH_PERFORMANCE
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.mw_ui_graphics,
                    R.string.mw_ui_graphics_desc,
                    R.drawable.ic_graphics,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsActivity(
                            null,
                            Settings.MenuTag.SECTION_RENDERER
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.mw_ui_audio,
                    R.string.mw_ui_audio_desc,
                    R.drawable.ic_audio,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsActivity(
                            null,
                            Settings.MenuTag.SECTION_AUDIO
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.advanced_settings,
                    R.string.settings_description,
                    R.drawable.ic_settings,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsActivity(
                            null,
                            Settings.MenuTag.SECTION_ROOT
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.app_settings,
                    R.string.app_settings_description,
                    R.drawable.ic_palette,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsActivity(
                            null,
                            Settings.MenuTag.SECTION_APP_SETTINGS
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.preferences_controls,
                    R.string.preferences_controls_description,
                    R.drawable.ic_controller,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsActivity(
                            null,
                            Settings.MenuTag.SECTION_INPUT
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.post_processing,
                    R.string.post_processing_description,
                    R.drawable.ic_post_processing,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsActivity(
                            null,
                            Settings.MenuTag.SECTION_POST_PROCESSING
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.profile_manager,
                    R.string.profile_manager_description,
                    R.drawable.ic_account_circle,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsSubscreenActivity(
                            SettingsSubscreen.PROFILE_MANAGER,
                            null
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.mw_drivers_components,
                    R.string.mw_drivers_components_desc,
                    R.drawable.ic_build,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsActivity(
                            null,
                            Settings.MenuTag.SECTION_DRIVERS_COMPONENTS
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.multiplayer,
                    R.string.multiplayer_description,
                    R.drawable.ic_two_users,
                    {
                        mainActivity.displayMultiplayerDialog()
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.applets,
                    R.string.applets_description,
                    R.drawable.ic_applet,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsSubscreenActivity(
                            SettingsSubscreen.APPLET_LAUNCHER,
                            null
                        )
                        binding.root.findNavController().navigate(action)
                    },
                    { NativeLibrary.isFirmwareAvailable() },
                    R.string.applets_error_firmware,
                    R.string.applets_error_description
                )
            )
            add(
                HomeSetting(
                    R.string.manage_yuzu_data,
                    R.string.manage_yuzu_data_description,
                    R.drawable.ic_install,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsSubscreenActivity(
                            SettingsSubscreen.INSTALLABLE,
                            null
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.manage_game_folders,
                    R.string.select_games_folder_description,
                    R.drawable.ic_add,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsSubscreenActivity(
                            SettingsSubscreen.GAME_FOLDERS,
                            null
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.verify_installed_content,
                    R.string.verify_installed_content_description,
                    R.drawable.ic_check_circle,
                    {
                        ProgressDialogFragment.newInstance(
                            requireActivity(),
                            titleId = R.string.verifying,
                            cancellable = true
                        ) { progressCallback, _ ->
                            val result = NativeLibrary.verifyInstalledContents(progressCallback)
                            return@newInstance if (progressCallback.invoke(100, 100)) {
                                // Invoke the progress callback to check if the process was cancelled
                                MessageDialogFragment.newInstance(
                                    titleId = R.string.verify_no_result,
                                    descriptionId = R.string.verify_no_result_description
                                )
                            } else if (result.isEmpty()) {
                                MessageDialogFragment.newInstance(
                                    titleId = R.string.verify_success,
                                    descriptionId = R.string.operation_completed_successfully
                                )
                            } else {
                                val failedNames = result.joinToString("\n")
                                val errorMessage = YuzuApplication.appContext.getString(
                                    R.string.verification_failed_for,
                                    failedNames
                                )
                                MessageDialogFragment.newInstance(
                                    titleId = R.string.verify_failure,
                                    descriptionString = errorMessage
                                )
                            }
                        }.show(parentFragmentManager, ProgressDialogFragment.TAG)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.share_log,
                    R.string.share_log_description,
                    R.drawable.ic_log,
                    { shareLog() }
                )
            )
            add(
                HomeSetting(
                    R.string.share_gpu_log,
                    R.string.share_gpu_log_description,
                    R.drawable.ic_log,
                    { shareGpuLog() }
                )
            )
            add(
                HomeSetting(
                    R.string.open_user_folder,
                    R.string.open_user_folder_description,
                    R.drawable.ic_folder_open,
                    { openFileManager() }
                )
            )
            add(
                HomeSetting(
                    R.string.system_information,
                    R.string.system_information_description,
                    R.drawable.ic_system,
                    {
                        SystemInfoDialogFragment.newInstance()
                            .show(parentFragmentManager, SystemInfoDialogFragment.TAG)
                    }
                )
            )
            add(
                HomeSetting(
                    R.string.about,
                    R.string.about_description,
                    R.drawable.ic_info_outline,
                    {
                        val action = HomeNavigationDirections.actionGlobalSettingsSubscreenActivity(
                            SettingsSubscreen.ABOUT,
                            null
                        )
                        binding.root.findNavController().navigate(action)
                    }
                )
            )
        }

        allOptions = optionsList
        fun pick(vararg ids: Int): List<HomeSetting> =
            ids.asIterable().mapNotNull { id -> allOptions.firstOrNull { it.titleId == id } }
        fun group(title: Int, description: Int, icon: Int, options: List<HomeSetting>) =
            HomeSetting(title, description, icon, { showOptions(options, title) })
        val systemOptions = listOf(
            HomeSetting(R.string.preferences_system, R.string.preferences_system_description, R.drawable.ic_system, {
                findNavController().navigate(HomeNavigationDirections.actionGlobalSettingsActivity(null, Settings.MenuTag.SECTION_SYSTEM))
            }),
            HomeSetting(R.string.mw_cat_general, R.string.mw_cat_general_desc, R.drawable.ic_settings, {
                findNavController().navigate(HomeNavigationDirections.actionGlobalSettingsActivity(null, Settings.MenuTag.SECTION_GENERAL))
            })
        ) + pick(R.string.profile_manager, R.string.multiplayer, R.string.applets, R.string.advanced_settings)
        val dataOptions = pick(R.string.manage_game_folders, R.string.manage_yuzu_data, R.string.verify_installed_content, R.string.open_user_folder)
        val supportOptions = pick(R.string.system_information, R.string.share_log, R.string.share_gpu_log, R.string.about)
        rootOptions = pick(R.string.mw_ui_graphics, R.string.mw_ui_performance, R.string.mw_ui_audio, R.string.preferences_controls, R.string.mw_drivers_components) +
            group(R.string.mw_cat_system, R.string.mw_cat_system_desc, R.drawable.ic_system, systemOptions) +
            pick(R.string.app_settings) +
            group(R.string.mw_reform_data, R.string.mw_ui_folders_desc, R.drawable.ic_folder_open, dataOptions) +
            group(R.string.mw_reform_support, R.string.mw_help_center_desc, R.drawable.ic_info_outline, supportOptions)
        allOptions = allOptions + systemOptions.take(2)
        groupBack = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { showOptions(rootOptions, null) }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, groupBack)
        binding.toolbarHomeSettings.setNavigationOnClickListener {
            if (groupTitle != null) showOptions(rootOptions, null) else findNavController().popBackStack()
        }
        binding.homeSettingsList.layoutManager = GridLayoutManager(requireContext(), 1)
        binding.settingsSearch.doOnTextChanged { text, _, _, _ ->
            val query = text.toString().trim()
            val options = if (query.isBlank()) currentOptions else allOptions.filter {
                getString(it.titleId).contains(query, ignoreCase = true) || getString(it.descriptionId).contains(query, ignoreCase = true)
            }
            binding.homeSettingsList.adapter = HomeSettingAdapter(requireActivity() as AppCompatActivity, viewLifecycleOwner, options)
        }
        binding.settingsNavigation.selectedItemId = R.id.mw_nav_settings
        binding.settingsNavigation.setOnItemSelectedListener {
            if (it.itemId == R.id.mw_nav_library) findNavController().popBackStack(R.id.gamesFragment, false)
            it.itemId == R.id.mw_nav_settings
        }
        showOptions(rootOptions, null)

        setInsets()
    }

    private fun showOptions(options: List<HomeSetting>, title: Int?) {
        groupTitle = title
        currentOptions = options
        groupBack.isEnabled = title != null
        binding.toolbarHomeSettings.setTitle(title ?: R.string.mw_reform_settings)
        binding.toolbarHomeSettings.navigationIcon = if (title == null) null else
            androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(), R.drawable.ic_back)
        binding.settingsNavigation.isVisible = title == null
        binding.settingsSearch.setText("")
        binding.homeSettingsList.adapter = HomeSettingAdapter(requireActivity() as AppCompatActivity, viewLifecycleOwner, options)
        binding.scrollViewSettings.scrollTo(0, 0)
    }

    override fun onStart() {
        super.onStart()
        exitTransition = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openFileManager() {
        // First, try to open the user data folder directly
        try {
            startActivity(getFileManagerIntentOnDocumentProvider(Intent.ACTION_VIEW))
            return
        } catch (_: ActivityNotFoundException) {
        }

        try {
            startActivity(getFileManagerIntentOnDocumentProvider("android.provider.action.BROWSE"))
            return
        } catch (_: ActivityNotFoundException) {
        }

        // Just try to open the file manager, try the package name used on "normal" phones
        try {
            startActivity(getFileManagerIntent("com.google.android.documentsui"))
            showNoLinkNotification()
            return
        } catch (_: ActivityNotFoundException) {
        }

        try {
            // Next, try the AOSP package name
            startActivity(getFileManagerIntent("com.android.documentsui"))
            showNoLinkNotification()
            return
        } catch (_: ActivityNotFoundException) {
        }

        Toast.makeText(
            requireContext(),
            resources.getString(R.string.no_file_manager),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun getFileManagerIntent(packageName: String): Intent {
        // Fragile, but some phones don't expose the system file manager in any better way
        val intent = Intent(Intent.ACTION_MAIN)
        intent.setClassName(packageName, "com.android.documentsui.files.FilesActivity")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }

    private fun getFileManagerIntentOnDocumentProvider(action: String): Intent {
        val authority = "${requireContext().packageName}.user"
        val intent = Intent(action)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.data = DocumentsContract.buildRootUri(authority, DocumentProvider.ROOT_ID)
        intent.addFlags(
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        return intent
    }

    private fun showNoLinkNotification() {
        val builder = NotificationCompat.Builder(
            requireContext(),
            getString(R.string.notice_notification_channel_id)
        )
            .setSmallIcon(R.drawable.ic_stat_notification_logo)
            .setContentTitle(getString(R.string.notification_no_directory_link))
            .setContentText(getString(R.string.notification_no_directory_link_description))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        // TODO: Make the click action for this notification lead to a help article

        with(NotificationManagerCompat.from(requireContext())) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    requireContext(),
                    resources.getString(R.string.notification_permission_not_granted),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            notify(0, builder.build())
        }
    }

    // Share the current log if we just returned from a game but share the old log
    // if we just started the app and the old log exists.
    private fun shareLog() {
        val currentLog = DocumentFile.fromSingleUri(
            mainActivity,
            DocumentsContract.buildDocumentUri(
                DocumentProvider.AUTHORITY,
                "${DocumentProvider.ROOT_ID}/log/eden_log.txt"
            )
        )!!
        val oldLog = DocumentFile.fromSingleUri(
            mainActivity,
            DocumentsContract.buildDocumentUri(
                DocumentProvider.AUTHORITY,
                "${DocumentProvider.ROOT_ID}/log/eden_log.txt.old.txt"
            )
        )!!

        val intent = Intent(Intent.ACTION_SEND)
            .setDataAndType(currentLog.uri, FileUtil.TEXT_PLAIN)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!Log.gameLaunched && oldLog.exists()) {
            intent.putExtra(Intent.EXTRA_STREAM, oldLog.uri)
            startActivity(Intent.createChooser(intent, getText(R.string.share_log)))
        } else if (currentLog.exists()) {
            intent.putExtra(Intent.EXTRA_STREAM, currentLog.uri)
            startActivity(Intent.createChooser(intent, getText(R.string.share_log)))
        } else {
            Toast.makeText(
                requireContext(),
                getText(R.string.share_log_missing),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareGpuLog() {
        val currentLog = DocumentFile.fromSingleUri(
            mainActivity,
            DocumentsContract.buildDocumentUri(
                DocumentProvider.AUTHORITY,
                "${DocumentProvider.ROOT_ID}/log/eden_gpu.log"
            )
        )!!
        val oldLog = DocumentFile.fromSingleUri(
            mainActivity,
            DocumentsContract.buildDocumentUri(
                DocumentProvider.AUTHORITY,
                "${DocumentProvider.ROOT_ID}/log/eden_gpu.log.old.txt"
            )
        )!!

        val intent = Intent(Intent.ACTION_SEND)
            .setDataAndType(currentLog.uri, FileUtil.TEXT_PLAIN)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!Log.gameLaunched && oldLog.exists()) {
            intent.putExtra(Intent.EXTRA_STREAM, oldLog.uri)
            startActivity(Intent.createChooser(intent, getText(R.string.share_gpu_log)))
        } else if (currentLog.exists()) {
            intent.putExtra(Intent.EXTRA_STREAM, currentLog.uri)
            startActivity(Intent.createChooser(intent, getText(R.string.share_gpu_log)))
        } else {
            Toast.makeText(
                requireContext(),
                getText(R.string.share_gpu_log_missing),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.updatePadding(left = safe.left, top = safe.top, right = safe.right, bottom = safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
}
