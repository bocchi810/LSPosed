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
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */
package org.lsposed.manager.ui.fragment

import android.app.Dialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.FragmentManager
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.databinding.FragmentCompileDialogBinding
import org.lsposed.manager.receivers.LSPManagerServiceHolder
import org.lsposed.manager.ui.dialog.BlurBehindDialogBuilder
import java.lang.ref.WeakReference

@Suppress("deprecation")
class CompileDialogFragment : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = getArguments()
        val appInfo =
            if (arguments != null) arguments.getParcelable<ApplicationInfo?>("appInfo") else null
        checkNotNull(appInfo) { "appInfo should not be null." }

        val binding = FragmentCompileDialogBinding.inflate(
            LayoutInflater.from(requireActivity()),
            null,
            false
        )
        val pm = requireContext().getPackageManager()
        val builder = BlurBehindDialogBuilder(requireActivity())
            .setIcon(appInfo.loadIcon(pm))
            .setTitle(appInfo.loadLabel(pm))
            .setView(binding.getRoot())

        val alertDialog = builder.create()
        CompileTask(this).executeOnExecutor(App.executorService, appInfo.packageName)
        return alertDialog
    }

    private class CompileTask(fragment: CompileDialogFragment?) :
        AsyncTask<String?, Void?, Throwable?>() {
        var outerRef: WeakReference<CompileDialogFragment?>

        init {
            outerRef = WeakReference<CompileDialogFragment?>(fragment)
        }

        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg commands: String?): Throwable? {
            try {
                LSPManagerServiceHolder.service?.clearApplicationProfileData(commands[0])
                if (LSPManagerServiceHolder.service?.performDexOptMode(commands[0]) == true) {
                    return null
                } else {
                    return UnknownError()
                }
            } catch (e: Throwable) {
                return e
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: Throwable?) {
            val context: App? = App.instance
            val text: String?
            if (result != null) {
                if (result is UnknownError) {
                    text = context?.getString(R.string.compile_failed)
                } else {
                    text = context?.getString(R.string.compile_failed_with_info) + result
                }
            } else {
                text = context?.getString(R.string.compile_done)
            }
            try {
                val fragment = outerRef.get()
                if (fragment != null) {
                    fragment.dismissAllowingStateLoss()
                    val parent = fragment.getParentFragment()
                    if (parent is BaseFragment) {
                        parent.showHint(text.toString(), true)
                    }
                }
            } catch (ignored: IllegalStateException) {
            }
        }
    }

    companion object {
        @JvmStatic
        fun speed(fragmentManager: FragmentManager, info: ApplicationInfo?) {
            val fragment = CompileDialogFragment()
            fragment.setCancelable(false)
            val bundle = Bundle()
            bundle.putParcelable("appInfo", info)
            fragment.setArguments(bundle)
            fragment.show(fragmentManager, "compile_dialog")
        }
    }
}
