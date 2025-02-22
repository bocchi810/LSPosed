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
 * Copyright (C) 2021 LSPosed Contributors
 */
package org.lsposed.manager.ui.dialog

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.os.Build
import android.util.Log
import android.view.SurfaceControl
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.lsposed.manager.App
import java.util.function.Consumer

open class BlurBehindDialogBuilder : MaterialAlertDialogBuilder {
    constructor(context: Context) : super(context)

    constructor(context: Context, overrideThemeResId: Int) : super(context, overrideThemeResId)

    override fun create(): AlertDialog {
        val dialog = super.create()
        setupWindowBlurListener(dialog)
        return dialog
    }

    private fun setupWindowBlurListener(dialog: AlertDialog) {
        val window = dialog.getWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window!!.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val windowBlurEnabledListener =
                Consumer { enabled: Boolean? -> updateWindowForBlurs(window, enabled!!) }
            window.getDecorView().addOnAttachStateChangeListener(
                object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        window.getWindowManager().addCrossWindowBlurEnabledListener(
                            windowBlurEnabledListener
                        )
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        window.getWindowManager().removeCrossWindowBlurEnabledListener(
                            windowBlurEnabledListener
                        )
                    }
                })
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            dialog.setOnShowListener(OnShowListener { d: DialogInterface? ->
                updateWindowForBlurs(
                    window!!,
                    supportBlur
                )
            })
        }
    }

    private fun updateWindowForBlurs(window: Window, blursEnabled: Boolean) {
        val mDimAmountWithBlur = 0.1f
        val mDimAmountNoBlur = 0.32f
        window.setDimAmount(if (blursEnabled) mDimAmountWithBlur else mDimAmountNoBlur)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.getAttributes().setBlurBehindRadius(20)
            window.setAttributes(window.getAttributes())
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            if (blursEnabled) {
                val view = window.getDecorView()
                val animator = ValueAnimator.ofInt(1, 53)
                animator.setInterpolator(DecelerateInterpolator())
                try {
                    val viewRootImpl = view.javaClass.getMethod("getViewRootImpl").invoke(view)
                    if (viewRootImpl == null) {
                        return
                    }
                    val surfaceControl = viewRootImpl.javaClass.getMethod("getSurfaceControl")
                        .invoke(viewRootImpl) as SurfaceControl?

                    @SuppressLint("BlockedPrivateApi") val setBackgroundBlurRadius =
                        SurfaceControl.Transaction::class.java.getDeclaredMethod(
                            "setBackgroundBlurRadius",
                            SurfaceControl::class.java,
                            Int::class.javaPrimitiveType
                        )
                    animator.addUpdateListener(AnimatorUpdateListener { animation: ValueAnimator? ->
                        try {
                            val transaction = SurfaceControl.Transaction()
                            val animatedValue = animation!!.getAnimatedValue()
                            if (animatedValue != null) {
                                setBackgroundBlurRadius.invoke(
                                    transaction,
                                    surfaceControl,
                                    animatedValue as Int
                                )
                            }
                            transaction.apply()
                        } catch (t: Throwable) {
                            Log.e(App.TAG, "Blur behind dialog builder", t)
                        }
                    })
                } catch (t: Throwable) {
                    Log.e(App.TAG, "Blur behind dialog builder", t)
                }
                view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        animator.cancel()
                    }
                })
                animator.start()
            }
        }
    }

    companion object {
        private val supportBlur = getSystemProperty(
            "ro.surface_flinger.supports_background_blur",
            false
        ) && !getSystemProperty("persist.sys.sf.disable_blurs", false)

        fun getSystemProperty(key: String?, defaultValue: Boolean): Boolean {
            var value = defaultValue
            try {
                val c = Class.forName("android.os.SystemProperties")
                val get =
                    c.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                value = get.invoke(c, key, defaultValue) as Boolean
            } catch (e: Exception) {
                Log.e(App.TAG, "Blur behind dialog builder get system property", e)
            }
            return value
        }
    }
}
