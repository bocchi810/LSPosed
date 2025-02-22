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

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.DialogFragment
import org.lsposed.lspd.ILSPManagerService
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.databinding.DialogAboutBinding
import org.lsposed.manager.databinding.FragmentHomeBinding
import org.lsposed.manager.ui.dialog.BlurBehindDialogBuilder
import org.lsposed.manager.ui.dialog.FlashDialogBuilder
import org.lsposed.manager.ui.dialog.WelcomeDialog.Companion.showIfNeed
import org.lsposed.manager.util.NavUtil
import org.lsposed.manager.util.UpdateUtil
import org.lsposed.manager.util.chrome.LinkTransformationMethod
import rikka.core.util.ClipboardUtils
import rikka.material.app.LocaleDelegate
import rikka.widget.borderview.BorderView.OnBorderVisibilityChangedListener
import java.io.IOException
import java.lang.String.format
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class HomeFragment : BaseFragment(), MenuProvider {
    private var binding: FragmentHomeBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showIfNeed(getChildFragmentManager())
    }

    override fun onPrepareMenu(menu: Menu) {
        menu.findItem(R.id.menu_about)
            .setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener { v: MenuItem? ->
                showAbout()
                true
            })
        menu.findItem(R.id.menu_issue)
            .setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener { v: MenuItem? ->
                NavUtil.startURL(
                    requireActivity(),
                    "https://github.com/JingMatrix/LSPosed/issues/new/choose"
                )
                true
            })
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupToolbar(binding!!.toolbar, binding!!.clickView, R.string.app_name, R.menu.menu_home)
        binding!!.toolbar.setNavigationIcon(null)
        binding!!.toolbar.setOnClickListener(View.OnClickListener { v: View? -> showAbout() })
        binding!!.clickView.setOnClickListener(View.OnClickListener { v: View? -> showAbout() })
        binding!!.appBar.setLiftable(true)
        binding!!.nestedScrollView.getBorderViewDelegate()
            .setBorderVisibilityChangedListener(OnBorderVisibilityChangedListener { top: Boolean, oldTop: Boolean, bottom: Boolean, oldBottom: Boolean ->
                binding!!.appBar.setLifted(!top)
            })

        updateStates(requireActivity(), ConfigManager.isBinderAlive, UpdateUtil.needUpdate())

        return binding!!.getRoot()
    }

    private fun updateStates(activity: Activity, binderAlive: Boolean, needUpdate: Boolean) {
        if (binderAlive) {
            if (needUpdate) {
                binding!!.updateTitle.setText(R.string.need_update)
                binding!!.updateSummary.setText(getString(R.string.please_update_summary))
                binding!!.statusIcon.setImageResource(R.drawable.ic_round_update_24)
                binding!!.updateBtn.setOnClickListener(View.OnClickListener { v: View? ->
                    if (UpdateUtil.canInstall()) {
                        FlashDialogBuilder(activity, null).show()
                    } else {
                        NavUtil.startURL(activity, getString(R.string.latest_url))
                    }
                })
                binding!!.updateCard.setVisibility(View.VISIBLE)
            } else {
                binding!!.updateCard.setVisibility(View.GONE)
            }
            val dex2oatAbnormal =
                ConfigManager.dex2OatWrapperCompatibility != ILSPManagerService.DEX2OAT_OK && !ConfigManager.dex2oatFlagsLoaded()
            val sepolicyAbnormal = !ConfigManager.isSepolicyLoaded
            val systemServerAbnormal = !ConfigManager.systemServerRequested()
            if (sepolicyAbnormal || systemServerAbnormal || dex2oatAbnormal) {
                binding!!.statusTitle.setText(R.string.partial_activated)
                binding!!.statusIcon.setImageResource(R.drawable.ic_round_warning_24)
                binding!!.warningCard.setVisibility(View.VISIBLE)
                if (sepolicyAbnormal) {
                    binding!!.warningTitle.setText(R.string.selinux_policy_not_loaded_summary)
                    binding!!.warningSummary.setText(
                        HtmlCompat.fromHtml(
                            getString(R.string.selinux_policy_not_loaded),
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    )
                }
                if (systemServerAbnormal) {
                    binding!!.warningTitle.setText(R.string.system_inject_fail_summary)
                    binding!!.warningSummary.setText(
                        HtmlCompat.fromHtml(
                            getString(R.string.system_inject_fail),
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    )
                }
                if (dex2oatAbnormal) {
                    binding!!.warningTitle.setText(R.string.system_prop_incorrect_summary)
                    binding!!.warningSummary.setText(
                        HtmlCompat.fromHtml(
                            getString(R.string.system_prop_incorrect),
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    )
                }
            } else {
                binding!!.warningCard.setVisibility(View.GONE)
                binding!!.statusTitle.setText(R.string.activated)
                binding!!.statusIcon.setImageResource(R.drawable.ic_round_check_circle_24)
            }
            binding!!.statusSummary.setText(
                format(
                    LocaleDelegate.defaultLocale,
                    "%s (%d) - %s",
                    ConfigManager.xposedVersionName,
                    ConfigManager.xposedVersionCode,
                    ConfigManager.api
                )
            )
            binding!!.developerWarningCard.setVisibility(if (this.isDeveloper) View.VISIBLE else View.GONE)
        } else {
            val isMagiskInstalled = ConfigManager.isMagiskInstalled
            if (isMagiskInstalled) {
                binding!!.updateTitle.setText(R.string.install)
                binding!!.updateSummary.setText(R.string.install_summary)
                binding!!.statusIcon.setImageResource(R.drawable.ic_round_error_outline_24)
                binding!!.updateBtn.setOnClickListener(View.OnClickListener { v: View? ->
                    if (UpdateUtil.canInstall()) {
                        FlashDialogBuilder(activity, null).show()
                    } else {
                        NavUtil.startURL(activity, getString(R.string.install_url))
                    }
                })
                binding!!.updateCard.setVisibility(View.VISIBLE)
            } else {
                binding!!.updateCard.setVisibility(View.GONE)
            }
            binding!!.warningCard.setVisibility(View.GONE)
            binding!!.statusTitle.setText(R.string.not_installed)
            binding!!.statusSummary.setText(R.string.not_install_summary)
        }

        if (ConfigManager.isBinderAlive) {
            binding!!.apiVersion.setText(ConfigManager.xposedApiVersion.toString())
            binding!!.api.setText(if (ConfigManager.isDexObfuscateEnabled) R.string.enabled else R.string.not_enabled)
            binding!!.frameworkVersion.setText(
                format(
                    LocaleDelegate.defaultLocale,
                    "%1\$s (%2\$d)",
                    ConfigManager.xposedVersionName,
                    ConfigManager.xposedVersionCode
                )
            )
            binding!!.managerPackageName.setText(activity.getPackageName())
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                binding!!.dex2oatWrapper.setText(
                    format(
                        LocaleDelegate.defaultLocale,
                        "%s (%s)",
                        getString(R.string.unsupported),
                        getString(R.string.android_version_unsatisfied)
                    )
                )
            } else when (ConfigManager.dex2OatWrapperCompatibility) {
                ILSPManagerService.DEX2OAT_OK -> binding!!.dex2oatWrapper.setText(R.string.supported)
                ILSPManagerService.DEX2OAT_CRASHED -> binding!!.dex2oatWrapper.setText(
                    format(
                        LocaleDelegate.defaultLocale,
                        "%s (%s)",
                        getString(R.string.unsupported),
                        getString(R.string.crashed)
                    )
                )

                ILSPManagerService.DEX2OAT_MOUNT_FAILED -> binding!!.dex2oatWrapper.setText(
                    format(
                        LocaleDelegate.defaultLocale,
                        "%s (%s)",
                        getString(R.string.unsupported),
                        getString(R.string.mount_failed)
                    )
                )

                ILSPManagerService.DEX2OAT_SELINUX_PERMISSIVE -> binding!!.dex2oatWrapper.setText(
                    format(
                        LocaleDelegate.defaultLocale,
                        "%s (%s)",
                        getString(R.string.unsupported),
                        getString(R.string.selinux_permissive)
                    )
                )

                ILSPManagerService.DEX2OAT_SEPOLICY_INCORRECT -> binding!!.dex2oatWrapper.setText(
                    format(
                        LocaleDelegate.defaultLocale,
                        "%s (%s)",
                        getString(R.string.unsupported),
                        getString(R.string.sepolicy_incorrect)
                    )
                )
            }
        } else {
            binding!!.apiVersion.setText(R.string.not_installed)
            binding!!.api.setText(R.string.not_installed)
            binding!!.frameworkVersion.setText(R.string.not_installed)
            binding!!.managerPackageName.setText(activity.getPackageName())
        }

        if (Build.VERSION.PREVIEW_SDK_INT != 0) {
            binding!!.systemVersion.setText(
                format(
                    LocaleDelegate.defaultLocale,
                    "%1\$s Preview (API %2\$d)",
                    Build.VERSION.CODENAME,
                    Build.VERSION.SDK_INT
                )
            )
        } else {
            binding!!.systemVersion.setText(
                format(
                    LocaleDelegate.defaultLocale,
                    "%1\$s (API %2\$d)",
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT
                )
            )
        }

        binding!!.device.setText(this.device)
        binding!!.systemAbi.setText(Build.SUPPORTED_ABIS[0])
        val info = activity.getString(R.string.info_api_version) +
                "\n" +
                binding!!.apiVersion.getText() +
                "\n\n" +
                activity.getString(R.string.settings_xposed_api_call_protection) +
                "\n" +
                binding!!.api.getText() +
                "\n\n" +
                activity.getString(R.string.info_dex2oat_wrapper) +
                "\n" +
                binding!!.dex2oatWrapper.getText() +
                "\n\n" +
                activity.getString(R.string.info_framework_version) +
                "\n" +
                binding!!.frameworkVersion.getText() +
                "\n\n" +
                activity.getString(R.string.info_manager_package_name) +
                "\n" +
                binding!!.managerPackageName.getText() +
                "\n\n" +
                activity.getString(R.string.info_system_version) +
                "\n" +
                binding!!.systemVersion.getText() +
                "\n\n" +
                activity.getString(R.string.info_device) +
                "\n" +
                binding!!.device.getText() +
                "\n\n" +
                activity.getString(R.string.info_system_abi) +
                "\n" +
                binding!!.systemAbi.getText()
        val map = HashMap<String?, String?>()
        map.put("apiVersion", binding!!.apiVersion.getText().toString())
        map.put("api", binding!!.api.getText().toString())
        map.put("frameworkVersion", binding!!.frameworkVersion.getText().toString())
        map.put("systemAbi", Build.SUPPORTED_ABIS.contentToString())
        binding!!.copyInfo.setOnClickListener(View.OnClickListener { v: View? ->
            ClipboardUtils.put(activity, info)
            showHint(R.string.info_copied, false)
        })
    }

    private val device: String
        get() {
            var manufacturer = Build.MANUFACTURER.get(0).uppercaseChar()
                .toString() + Build.MANUFACTURER.substring(1)
            if (Build.BRAND != Build.MANUFACTURER) {
                manufacturer += " " + Build.BRAND.get(0)
                    .uppercaseChar() + Build.BRAND.substring(1)
            }
            manufacturer += " " + Build.MODEL + " "
            return manufacturer
        }

    private val isDeveloper: Boolean
        get() {
            val developer =
                AtomicBoolean(false)
            val pids = Paths.get("/data/local/tmp/.studio/ipids")
            try {
                Files.list(pids).use { dir ->
                    dir.findFirst()
                        .ifPresent(Consumer { name: Path? ->
                            val pid = name!!.getFileName().toString().toInt()
                            try {
                                Os.kill(pid, 0)
                                developer.set(true)
                            } catch (e: ErrnoException) {
                                if (e.errno == OsConstants.ESRCH) {
                                    try {
                                        Files.delete(name)
                                    } catch (ignored: IOException) {
                                    }
                                } else {
                                    developer.set(true)
                                }
                            }
                        })
                }
            } catch (e: IOException) {
                return false
            }
            return developer.get()
        }

    class AboutDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val binding = DialogAboutBinding.inflate(getLayoutInflater(), null, false)
            binding.designAboutTitle.setText(R.string.app_name)
            binding.designAboutInfo.setMovementMethod(LinkMovementMethod.getInstance())
            binding.designAboutInfo.setTransformationMethod(LinkTransformationMethod(requireActivity()))
            binding.designAboutInfo.setText(
                HtmlCompat.fromHtml(
                    getString(
                        R.string.about_view_source_code,
                        "<b><a href=\"https://github.com/JingMatrix/LSPosed\">GitHub</a></b>",
                        "<b><a href=\"https://t.me/LSPosed\">Telegram</a></b>"
                    ), HtmlCompat.FROM_HTML_MODE_LEGACY
                )
            )
            binding.designAboutVersion.setText(
                format(
                    LocaleDelegate.defaultLocale,
                    "%s (%d)",
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
            )
            return BlurBehindDialogBuilder(requireContext())
                .setView(binding.getRoot()).create()
        }
    }

    private fun showAbout() {
        AboutDialog().show(getChildFragmentManager(), "about")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
