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
package org.lsposed.manager.ui.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Parcel
import android.view.MenuItem
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.ToLongFunction
import kotlin.Boolean
import kotlin.CharSequence
import kotlin.Comparator
import kotlin.Int
import kotlin.String

object AppHelper {
    const val SETTINGS_CATEGORY: String = "de.robv.android.xposed.category.MODULE_SETTINGS"
    const val FLAG_SHOW_FOR_ALL_USERS: Int = 0x0400
    private var denyList: MutableList<String?>? = null
    private var appList: MutableList<PackageInfo>? = null
    private val appLabel = ConcurrentHashMap<PackageInfo?, CharSequence?>()

    @SuppressLint("WrongConstant")
    fun getSettingsIntent(packageName: String?, userId: Int): Intent? {
        val intentToResolve = Intent(Intent.ACTION_MAIN)
        intentToResolve.addCategory(SETTINGS_CATEGORY)
        intentToResolve.setPackage(packageName)

        val ris = ConfigManager.queryIntentActivitiesAsUser(intentToResolve, 0, userId)

        if (ris.size == 0) {
            return getLaunchIntentForPackage(packageName, userId)
        }

        val intent = Intent(intentToResolve)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setClassName(
            ris.get(0)!!.activityInfo.packageName,
            ris.get(0)!!.activityInfo.name
        )
        intent.putExtra(
            "lsp_no_switch_to_user",
            (ris.get(0)!!.activityInfo.flags and FLAG_SHOW_FOR_ALL_USERS) != 0
        )
        return intent
    }

    @SuppressLint("WrongConstant")
    fun getLaunchIntentForPackage(packageName: String?, userId: Int): Intent? {
        val intentToResolve = Intent(Intent.ACTION_MAIN)
        intentToResolve.addCategory(Intent.CATEGORY_INFO)
        intentToResolve.setPackage(packageName)
        var ris = ConfigManager.queryIntentActivitiesAsUser(intentToResolve, 0, userId)

        if (ris.size == 0) {
            intentToResolve.removeCategory(Intent.CATEGORY_INFO)
            intentToResolve.addCategory(Intent.CATEGORY_LAUNCHER)
            intentToResolve.setPackage(packageName)
            ris = ConfigManager.queryIntentActivitiesAsUser(intentToResolve, 0, userId)
        }

        if (ris.size == 0) {
            return null
        }

        val intent = Intent(intentToResolve)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setClassName(
            ris.get(0)!!.activityInfo.packageName,
            ris.get(0)!!.activityInfo.name
        )
        intent.putExtra(
            "lsp_no_switch_to_user",
            (ris.get(0)!!.activityInfo.flags and FLAG_SHOW_FOR_ALL_USERS) != 0
        )
        return intent
    }

    fun onOptionsItemSelected(item: MenuItem, preferences: SharedPreferences): Boolean {
        val itemId = item.getItemId()
        var i = preferences.getInt("list_sort", 0)
        if (itemId == R.id.item_sort_by_name) {
            i = if (i % 2 == 0) 0 else 1
        } else if (itemId == R.id.item_sort_by_package_name) {
            i = if (i % 2 == 0) 2 else 3
        } else if (itemId == R.id.item_sort_by_install_time) {
            i = if (i % 2 == 0) 4 else 5
        } else if (itemId == R.id.item_sort_by_update_time) {
            i = if (i % 2 == 0) 6 else 7
        } else if (itemId == R.id.reverse) {
            if (i % 2 == 0) i++
            else i--
        } else {
            return false
        }
        preferences.edit().putInt("list_sort", i).apply()
        if (item.isCheckable()) item.setChecked(!item.isChecked())
        return true
    }

    fun getAppListComparator(sort: Int, pm: PackageManager?): Comparator<PackageInfo?>? {
        val displayNameComparator = ApplicationInfo.DisplayNameComparator(pm)
        return when (sort) {
            7 -> Collections.reverseOrder<PackageInfo?>(
                Comparator.comparingLong<PackageInfo?>(
                    ToLongFunction { a: PackageInfo? -> a!!.lastUpdateTime })
            )

            6 -> Comparator.comparingLong<PackageInfo?>(ToLongFunction { a: PackageInfo? -> a!!.lastUpdateTime })
            5 -> Collections.reverseOrder<PackageInfo?>(
                Comparator.comparingLong<PackageInfo?>(
                    ToLongFunction { a: PackageInfo? -> a!!.firstInstallTime })
            )

            4 -> Comparator.comparingLong<PackageInfo?>(ToLongFunction { a: PackageInfo? -> a!!.firstInstallTime })
            3 -> Collections.reverseOrder<PackageInfo?>(
                Comparator.comparing<PackageInfo?, String?>(
                    Function { a: PackageInfo? -> a!!.packageName })
            )

            2 -> Comparator.comparing<PackageInfo?, String?>(Function { a: PackageInfo? -> a!!.packageName })
            1 -> Collections.reverseOrder<PackageInfo?>(Comparator { a: PackageInfo?, b: PackageInfo? ->
                displayNameComparator.compare(
                    a!!.applicationInfo,
                    b!!.applicationInfo
                )
            })

            else -> Comparator { a: PackageInfo?, b: PackageInfo? ->
                displayNameComparator.compare(
                    a!!.applicationInfo,
                    b!!.applicationInfo
                )
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun getAppList(force: Boolean): MutableList<PackageInfo>? {
        if (appList == null || force) {
            appList = ConfigManager.getInstalledPackagesFromAllUsers(
                PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES,
                true
            ) as MutableList<PackageInfo>?
            var system: PackageInfo? = null
            for (app in appList!!) {
                if ("android" == app.packageName) {
                    val p = Parcel.obtain()
                    app.writeToParcel(p, 0)
                    p.setDataPosition(0)
                    system = PackageInfo.CREATOR.createFromParcel(p)
                    system.packageName = "system"
                    system.applicationInfo!!.packageName = system.packageName
                    break
                }
            }
            if (system != null) {
                appList!!.add(system)
            }
        }
        return appList
    }

    @JvmStatic
    @Synchronized
    fun getDenyList(force: Boolean): MutableList<String?> {
        if (denyList == null || force) {
            denyList = ConfigManager.denyListPackages
        }
        return denyList!!
    }

    @JvmStatic
    fun getAppLabel(info: PackageInfo?, pm: PackageManager): CharSequence? {
        if (info == null || info.applicationInfo == null) return null
        return appLabel.computeIfAbsent(info) { i: PackageInfo? ->
            i!!.applicationInfo!!.loadLabel(
                pm
            )
        }
    }
}
