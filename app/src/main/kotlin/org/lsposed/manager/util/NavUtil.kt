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
package org.lsposed.manager.util

import android.R
import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import rikka.core.util.ResourceUtils

object NavUtil {
    fun startURL(activity: Activity, uri: Uri) {
        val customTabsIntent = CustomTabsIntent.Builder()
        customTabsIntent.setShowTitle(true)
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(
                ResourceUtils.resolveColor(
                    activity.getTheme(),
                    R.attr.colorBackground
                )
            )
            .setNavigationBarColor(
                ResourceUtils.resolveColor(
                    activity.getTheme(),
                    R.attr.navigationBarColor
                )
            )
            .setNavigationBarDividerColor(0)
            .build()
        customTabsIntent.setDefaultColorSchemeParams(params)
        val night = ResourceUtils.isNightMode(activity.getResources().getConfiguration())
        customTabsIntent.setColorScheme(if (night) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
        try {
            customTabsIntent.build().launchUrl(activity, uri)
        } catch (ignored: ActivityNotFoundException) {
            Toast.makeText(activity, uri.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun startURL(activity: Activity, url: String?) {
        startURL(activity, Uri.parse(url))
    }
}
