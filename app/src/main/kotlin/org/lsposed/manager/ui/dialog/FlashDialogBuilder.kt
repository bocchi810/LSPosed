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

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.ParcelFileDescriptor
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textview.MaterialTextView
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.databinding.DialogTitleBinding
import org.lsposed.manager.databinding.ScrollableDialogBinding
import rikka.widget.borderview.BorderNestedScrollView
import rikka.widget.borderview.BorderView.OnBorderVisibilityChangedListener
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class FlashDialogBuilder(context: Context, cancel: DialogInterface.OnClickListener?) :
    BlurBehindDialogBuilder(
        context,
        R.style.ThemeOverlay_MaterialAlertDialog_Centered_FullWidthButtons
    ) {
    private val zipPath: String?
    private val textView: TextView
    private val rootView: BorderNestedScrollView

    init {
        val pref = App.preferences
        val notes: String = pref.getString("release_notes", "")!!
        this.zipPath = pref.getString("zip_file", null)
        val inflater = LayoutInflater.from(context)

        val title = DialogTitleBinding.inflate(inflater).getRoot()
        title.setText(R.string.update_lsposed)
        setCustomTitle(title)

        textView = MaterialTextView(context)
        val text = notes + "\n\n\n" + context.getString(R.string.update_lsposed_msg) + "\n\n"
        textView.setText(text)
        textView.setMovementMethod(LinkMovementMethod.getInstance())
        textView.setTextIsSelectable(true)

        val binding = ScrollableDialogBinding.inflate(inflater, null, false)
        binding.dialogContainer.addView(textView)
        rootView = binding.getRoot()
        setView(rootView)
        title.setOnClickListener(View.OnClickListener { v: View? -> rootView.smoothScrollTo(0, 0) })

        setNegativeButton(android.R.string.cancel, cancel)
        setPositiveButton(R.string.install, null)
        setCancelable(false)
    }

    override fun show(): AlertDialog {
        val dialog = super.show()
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        rootView.setBorderVisibilityChangedListener(OnBorderVisibilityChangedListener { t: Boolean, ot: Boolean, b: Boolean, ob: Boolean ->
            button.setEnabled(
                !b
            )
        })
        button.setOnClickListener(View.OnClickListener { v: View? ->
            rootView.setBorderVisibilityChangedListener(null)
            setFlashView(v!!, dialog)
        })
        return dialog
    }

    private fun setFlashView(view: View, dialog: AlertDialog) {
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        positiveButton.setEnabled(false)
        positiveButton.setText(android.R.string.ok)
        positiveButton.setOnClickListener(View.OnClickListener { v: View? -> dialog.dismiss() })
        negativeButton.setVisibility(View.GONE)

        textView.setText("")
        textView.setTypeface(Typeface.MONOSPACE)
        App.executorService?.submit(Runnable { flash(view, positiveButton) })
    }

    private fun flash(view: View, button: Button) {
        try {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            val readSide = pipe[0]
            val writeSide: ParcelFileDescriptor = pipe[1]!!

            ConfigManager.flashZip(zipPath, writeSide)
            writeSide.close()

            val inputStream = ParcelFileDescriptor.AutoCloseInputStream(readSide)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String? = ""
            while (line != null) {
                if (line.length > 0) {
                    val showLine = line + "\n"
                    view.post(Runnable {
                        textView.append(showLine)
                        rootView.fullScroll(View.FOCUS_DOWN)
                    })
                }
                line = reader.readLine()
            }

            reader.close()
        } catch (e: IOException) {
            Log.e(App.TAG, "flash", e)
            view.post(Runnable { textView.append("\n\n" + e.message) })
            rootView.fullScroll(View.FOCUS_DOWN)
        }

        view.post(Runnable { button.setEnabled(true) })
    }
}
