/*
 * <!--This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 LSPosed Contributors-->
 */

package org.lsposed.manager.ui.fragment

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import org.lsposed.manager.App
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.databinding.FragmentSettingsBinding
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.ui.activity.MainActivity
import org.lsposed.manager.util.*
import rikka.core.util.ResourceUtils
import rikka.material.app.LocaleDelegate
import rikka.material.preference.MaterialSwitchPreference
import rikka.preference.SimpleMenuPreference
import rikka.widget.borderview.BorderRecyclerView
import java.time.LocalDateTime
import java.util.*
import androidx.core.content.edit
import org.lsposed.manager.util.BackupUtils
import org.lsposed.manager.util.CloudflareDNS
import org.lsposed.manager.util.NavUtil
import org.lsposed.manager.util.ShortcutUtil
import org.lsposed.manager.util.ThemeUtil

class SettingsFragment : BaseFragment() {
    private var binding: FragmentSettingsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        binding?.appBar?.setLiftable(true)
        setupToolbar(binding?.toolbar, binding?.clickView, R.string.Settings)
        binding?.toolbar?.setNavigationIcon(null)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction().add(R.id.setting_container, PreferenceFragment()).commitNow()
        }
        if (ConfigManager.isBinderAlive) {
            binding?.toolbar?.subtitle = String.format(
                LocaleDelegate.defaultLocale,
                "%s (%d) - %s",
                ConfigManager.xposedVersionName,
                ConfigManager.xposedVersionCode,
                ConfigManager.api
            )
        } else {
            binding?.toolbar?.subtitle = String.format(
                LocaleDelegate.defaultLocale,
                "%s (%d) - %s",
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                getString(R.string.not_installed)
            )
        }
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    class PreferenceFragment : PreferenceFragmentCompat() {
        private var parentFragment: SettingsFragment? = null

        private val backupLauncher: ActivityResultLauncher<String> =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
                if (uri == null || parentFragment == null) return@registerForActivityResult
                parentFragment?.runAsync {
                    try {
                        BackupUtils.backup(uri)
                    } catch (e: Exception) {
                        val text = App.instance?.getString(R.string.settings_backup_failed2, e.message)
                        parentFragment?.showHint(text.toString(), false)
                    }
                }
            }

        private val restoreLauncher: ActivityResultLauncher<Array<String>> =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null || parentFragment == null) return@registerForActivityResult
                parentFragment?.runAsync {
                    try {
                        BackupUtils.restore(uri)
                    } catch (e: Exception) {
                        val text = App.instance?.getString(R.string.settings_restore_failed2, e.message)
                        parentFragment?.showHint(text.toString(), false)
                    }
                }
            }

        override fun onAttach(context: Context) {
            super.onAttach(context)
            parentFragment = requireParentFragment() as SettingsFragment
        }

        override fun onDetach() {
            super.onDetach()
            parentFragment = null
        }

        private fun setNotificationPreferenceEnabled(
            notificationPreference: MaterialSwitchPreference?,
            preferenceEnabled: Boolean
        ): Boolean {
            val notificationEnabled = ConfigManager.enableStatusNotification()
            if (notificationPreference != null) {
                notificationPreference.isEnabled = !notificationEnabled || preferenceEnabled
                notificationPreference.summaryOn = if (preferenceEnabled) {
                    notificationPreference.context.getString(R.string.settings_enable_status_notification_summary)
                } else {
                    notificationPreference.context.getString(R.string.settings_enable_status_notification_summary) + "\n" +
                            notificationPreference.context.getString(R.string.disable_status_notification_error)
                }
            }
            return notificationEnabled
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val SYSTEM = "SYSTEM"

            addPreferencesFromResource(R.xml.prefs)

            val installed = ConfigManager.isBinderAlive
            val prefVerboseLogs: MaterialSwitchPreference? = findPreference("disable_verbose_log")
            if (prefVerboseLogs != null) {
                prefVerboseLogs.isEnabled = !BuildConfig.DEBUG && installed
                if (BuildConfig.DEBUG) ConfigManager.setVerboseLogEnabled(false)
                prefVerboseLogs.isChecked = !installed || ConfigManager.isVerboseLogEnabled
                prefVerboseLogs.setOnPreferenceChangeListener { _, newValue ->
                    ConfigManager.setVerboseLogEnabled(!(newValue as Boolean))
                }
            }

            val prefLogWatchDog: MaterialSwitchPreference? = findPreference("enable_log_watchdog")
            if (prefLogWatchDog != null) {
                prefLogWatchDog.isEnabled = !BuildConfig.DEBUG && installed
                if (BuildConfig.DEBUG) ConfigManager.setLogWatchdog(true)
                prefLogWatchDog.isChecked = !installed || ConfigManager.isLogWatchdogEnabled
                prefLogWatchDog.setOnPreferenceChangeListener { _, newValue ->
                    ConfigManager.setLogWatchdog(newValue as Boolean)
                }
            }

            val prefDexObfuscate: MaterialSwitchPreference? = findPreference("enable_dex_obfuscate")
            if (prefDexObfuscate != null) {
                prefDexObfuscate.isEnabled = installed
                prefDexObfuscate.isChecked = !installed || ConfigManager.isDexObfuscateEnabled
                prefDexObfuscate.setOnPreferenceChangeListener { _, newValue ->
                    parentFragment?.showHint(R.string.reboot_required, true, R.string.reboot) {
                        ConfigManager.reboot()
                    }
                    ConfigManager.setDexObfuscateEnabled(newValue as Boolean)
                }
            }

            val notificationPreference: MaterialSwitchPreference? = findPreference("enable_status_notification")
            if (notificationPreference != null) {
                notificationPreference.isVisible = installed
                if (installed) {
                    notificationPreference.isChecked =
                        setNotificationPreferenceEnabled(notificationPreference, !App.isParasitic || ShortcutUtil.isLaunchShortcutPinned)
                }
                notificationPreference.setOnPreferenceChangeListener { _, v ->
                    val succeeded = ConfigManager.setEnableStatusNotification(v as Boolean)
                    if (v && App.isParasitic && !ShortcutUtil.isLaunchShortcutPinned) {
                        setNotificationPreferenceEnabled(notificationPreference, false)
                    }
                    succeeded
                }
            }

            val shortcut: Preference? = findPreference("add_shortcut")
            if (shortcut != null) {
                shortcut.isVisible = App.isParasitic
                if (!ShortcutUtil.isRequestPinShortcutSupported(requireContext())) {
                    shortcut.isEnabled = false
                    shortcut.summary = getString(R.string.settings_unsupported_pin_shortcut_summary)
                }
                shortcut.setOnPreferenceClickListener {
                    if (!ShortcutUtil.requestPinLaunchShortcut {
                            setNotificationPreferenceEnabled(notificationPreference, true)
                            App.preferences.edit { putBoolean("never_show_welcome", true) }
                            parentFragment?.showHint(R.string.settings_shortcut_pinned_hint, false)
                        }) {
                        parentFragment?.showHint(R.string.settings_unsupported_pin_shortcut_summary, true)
                    }
                    true
                }
            }

            val backup: Preference? = findPreference("backup")
            if (backup != null) {
                backup.isEnabled = installed
                backup.setOnPreferenceClickListener {
                    val now = LocalDateTime.now()
                    try {
                        backupLauncher.launch(String.format(LocaleDelegate.defaultLocale, "LSPosed_%s.lsp", now.toString()))
                        true
                    } catch (e: ActivityNotFoundException) {
                        parentFragment?.showHint(R.string.enable_documentui, true)
                        false
                    }
                }
            }

            val restore: Preference? = findPreference("restore")
            if (restore != null) {
                restore.isEnabled = installed
                restore.setOnPreferenceClickListener {
                    try {
                        restoreLauncher.launch(arrayOf("*/*"))
                        true
                    } catch (e: ActivityNotFoundException) {
                        parentFragment?.showHint(R.string.enable_documentui, true)
                        false
                    }
                }
            }

            val theme: Preference? = findPreference("dark_theme")
            if (theme != null) {
                theme.setOnPreferenceChangeListener { _, newValue ->
                    if (App.preferences.getString("dark_theme", ThemeUtil.MODE_NIGHT_FOLLOW_SYSTEM) != newValue) {
                        AppCompatDelegate.setDefaultNightMode(ThemeUtil.getDarkTheme(newValue as String))
                    }
                    true
                }
            }

            val blackDarkTheme: Preference? = findPreference("black_dark_theme")
            if (blackDarkTheme != null) {
                blackDarkTheme.setOnPreferenceChangeListener { _, _ ->
                    val activity = activity as? MainActivity
                    if (activity != null && ResourceUtils.isNightMode(resources.configuration)) {
                        activity.restart()
                    }
                    true
                }
            }

            val primaryColor: Preference? = findPreference("theme_color")
            if (primaryColor != null) {
                primaryColor.setOnPreferenceChangeListener { _, _ ->
                    val activity = activity as? MainActivity
                    if (activity != null) {
                        activity.restart()
                    }
                    true
                }
            }

            val prefShowHiddenIcons: MaterialSwitchPreference? = findPreference("show_hidden_icon_apps_enabled")
            if (prefShowHiddenIcons != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ConfigManager.isBinderAlive) {
                    prefShowHiddenIcons.isEnabled = true
                    prefShowHiddenIcons.setOnPreferenceChangeListener { _, newValue ->
                        ConfigManager.setHiddenIcon(!(newValue as Boolean))
                    }
                }
                prefShowHiddenIcons.isChecked =
                    Settings.Global.getInt(requireActivity().contentResolver, "show_hidden_icon_apps_enabled", 1) != 0
            }

            val prefFollowSystemAccent: MaterialSwitchPreference? = findPreference("follow_system_accent")
            if (prefFollowSystemAccent != null && DynamicColors.isDynamicColorAvailable()) {
                if (primaryColor != null) {
                    primaryColor.isVisible = !prefFollowSystemAccent.isChecked
                }
                prefFollowSystemAccent.isVisible = true
                prefFollowSystemAccent.setOnPreferenceChangeListener { _, _ ->
                    val activity = activity as? MainActivity
                    if (activity != null) {
                        activity.restart()
                    }
                    true
                }
            }

            val prefDoH: MaterialSwitchPreference? = findPreference("doh")
            if (prefDoH != null) {
                val dns = App.okHttpClient?.dns as CloudflareDNS
                if (!dns.noProxy) {
                    prefDoH.isEnabled = false
                    prefDoH.isVisible = false
                    val group = prefDoH.parent
                    group?.isVisible = false
                }
                prefDoH.setOnPreferenceChangeListener { _, v ->
                    dns.DoH = v as Boolean
                    true
                }
            }

            val language: SimpleMenuPreference? = findPreference("language")
            if (language != null) {
                val tag = language.value
                val userLocale = App.getLocale(tag)
                val entries = ArrayList<CharSequence>()
                val lstLang = LangList.LOCALES
                for (lang in lstLang) {
                    if (lang == SYSTEM) {
                        entries.add(getString(rikka.core.R.string.follow_system))
                        continue
                    }
                    val locale = Locale.forLanguageTag(lang)
                    entries.add(HtmlCompat.fromHtml(locale.getDisplayName(locale), HtmlCompat.FROM_HTML_MODE_LEGACY))
                }
                language.entries = entries.toTypedArray()
                language.entryValues = lstLang
                if (TextUtils.isEmpty(tag) || SYSTEM == tag) {
                    language.summary = getString(rikka.core.R.string.follow_system)
                } else {
                    val locale = Locale.forLanguageTag(tag)
                    language.summary = if (!TextUtils.isEmpty(locale.script)) locale.getDisplayScript(userLocale) else locale.getDisplayName(userLocale)
                }
                language.setOnPreferenceChangeListener { _, newValue ->
                    val app = App.instance
                    val locale = App.getLocale(newValue as String)
                    val res = app?.resources
                    val config = res?.configuration
                    config?.setLocale(locale)
                    LocaleDelegate.defaultLocale
                    //noinspection deprecation
                    res?.updateConfiguration(config, res.displayMetrics)
                    val activity = activity as? MainActivity
                    if (activity != null) {
                        activity.restart()
                    }
                    true
                }
            }

            val translation: Preference? = findPreference("translation")
            if (translation != null) {
                translation.setOnPreferenceClickListener {
                    NavUtil.startURL(requireActivity(), "https://crowdin.com/project/lsposed_jingmatrix")
                    true
                }
                translation.summary = getString(R.string.settings_translation_summary, getString(R.string.app_name))
            }

            val translationContributors: Preference? = findPreference("translation_contributors")
            if (translationContributors != null) {
                val translators = HtmlCompat.fromHtml(getString(R.string.translators), HtmlCompat.FROM_HTML_MODE_LEGACY)
                if (translators.toString() == "null") {
                    translationContributors.isVisible = false
                } else {
                    translationContributors.summary = translators
                }
            }

            val channel: SimpleMenuPreference? = findPreference("update_channel")
            if (channel != null) {
                channel.setOnPreferenceChangeListener { _, newValue ->
                    val repoLoader = RepoLoader.instance
                    if (repoLoader != null) {
                        // 将 newValue 强制转换为 String
                        val channelValue = newValue as String
                        // 传递 onlineModules 和 channelValue
                        repoLoader.updateLatestVersion(repoLoader.onlineModules, channelValue)
                    }
                    true
                }
            }
        }

        override fun onCreateRecyclerView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            savedInstanceState: Bundle?
        ): RecyclerView {
            val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState) as BorderRecyclerView

            // 自定义边缘效果
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                recyclerView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
                    override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
                        return EdgeEffect(recyclerView.context).apply {
                            // 自定义边缘效果的颜色或行为
                            color = Color.RED // 例如，设置为红色
                        }
                    }
                }
            }

            recyclerView.borderViewDelegate.setBorderVisibilityChangedListener { top, _, _, _ ->
                parentFragment?.binding?.appBar?.setLifted(!top)
            }

            val fragment = parentFragment
            if (fragment is SettingsFragment) {
                val l = View.OnClickListener {
                    fragment.binding?.appBar?.setExpanded(true, true)
                    recyclerView.smoothScrollToPosition(0)
                }
                fragment.binding?.toolbar?.setOnClickListener(l)
                fragment.binding?.clickView?.setOnClickListener(l)
            }

            return recyclerView
        }

    }
}
