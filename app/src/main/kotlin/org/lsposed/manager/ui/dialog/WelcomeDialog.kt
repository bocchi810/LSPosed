/*
 * This file is part of LSPosed.
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
 * Copyright (C) 2022 LSPosed Contributors
 */
package org.lsposed.manager.ui.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.ui.fragment.BaseFragment
import org.lsposed.manager.util.ShortcutUtil
import androidx.core.content.edit

class WelcomeDialog : DialogFragment() {
    private fun parasiticDialog(builder: BlurBehindDialogBuilder): Dialog {
        val shortcutSupported = ShortcutUtil.isRequestPinShortcutSupported(requireContext())
        builder
            .setTitle(R.string.parasitic_welcome)
            .setMessage(if (shortcutSupported) R.string.parasitic_welcome_summary else R.string.parasitic_welcome_summary_no_shortcut_support)
            .setNegativeButton(
                R.string.never_show,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    App.preferences.edit { putBoolean("never_show_welcome", true) }
                })
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(
                R.string.create_shortcut,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    val home = getParentFragment() as BaseFragment?
                    if (!ShortcutUtil.requestPinLaunchShortcut(Runnable {
                            App.preferences.edit {
                                putBoolean("never_show_welcome", true)
                            }
                            if (home != null) {
                                home.showHint(R.string.settings_shortcut_pinned_hint, false)
                            }
                        })) {
                        if (home != null) {
                            home.showHint(R.string.settings_unsupported_pin_shortcut_summary, false)
                        }
                    }
                })
        return builder.create()
    }

    private fun appDialog(builder: BlurBehindDialogBuilder): Dialog {
        return builder
            .setTitle(R.string.app_welcome)
            .setMessage(R.string.app_welcome_summary)
            .setNegativeButton(
                R.string.never_show,
                DialogInterface.OnClickListener { d: DialogInterface?, w: Int ->
                    App.preferences.edit { putBoolean("never_show_welcome", true) }
                })
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = BlurBehindDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_MaterialAlertDialog_Centered_FullWidthButtons
        )
        if (App.isParasitic) {
            return parasiticDialog(builder)
        } else {
            return appDialog(builder)
        }
    }

    companion object {
        private var shown = false

        @JvmStatic
        fun showIfNeed(fm: FragmentManager) {
            if (shown) return
            if (!ConfigManager.isBinderAlive ||
                App.preferences.getBoolean("never_show_welcome", false) ||
                (App.isParasitic && ShortcutUtil.isLaunchShortcutPinned)
            ) {
                shown = true
                return
            }
            WelcomeDialog().show(fm, "welcome")
            shown = true
        }
    }
}
