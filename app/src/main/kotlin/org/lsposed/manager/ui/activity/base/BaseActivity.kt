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
package org.lsposed.manager.ui.activity.base

import android.app.ActivityManager
import android.app.ActivityManager.TaskDescription
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.util.ThemeUtil
import rikka.material.app.MaterialActivity
import androidx.core.graphics.createBitmap

open class BaseActivity : MaterialActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        if (!App.isParasitic) return
        for (task in getSystemService<ActivityManager?>(ActivityManager::class.java).getAppTasks()) {
            task.setExcludeFromRecents(false)
        }
        if (icon == null) {
            val drawable = getApplicationInfo().loadIcon(getPackageManager())
            if (drawable is BitmapDrawable) {
                icon = drawable.getBitmap()
            } else if (drawable is AdaptiveIconDrawable) {
                icon = createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight())
                val canvas = Canvas(icon!!)
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
                drawable.draw(canvas)
            }
        }
        setTaskDescription(
            TaskDescription(
                getTitle().toString(),
                icon,
                getColor(R.color.ic_launcher_background)
            )
        )
    }

    override fun onApplyUserThemeResource(theme: Resources.Theme, isDecorView: Boolean) {
        if (!ThemeUtil.isSystemAccent) {
            theme.applyStyle(ThemeUtil.colorThemeStyleRes, true)
        }
        theme.applyStyle(ThemeUtil.getNightThemeStyleRes(this), true)
        theme.applyStyle(
            rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference,
            true
        )
    }

    public override fun computeUserThemeKey(): String? {
        return ThemeUtil.colorTheme + ThemeUtil.getNightTheme(this)
    }

    public override fun onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars()
        val window = getWindow()
        window.setStatusBarColor(Color.TRANSPARENT)
        window.setNavigationBarColor(Color.TRANSPARENT)
    }

    companion object {
        private var icon: Bitmap? = null
    }
}
