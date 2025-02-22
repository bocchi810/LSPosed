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
package org.lsposed.manager

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import org.lsposed.lspd.ILSPManagerService
import org.lsposed.lspd.models.Application
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.receivers.LSPManagerServiceHolder.Companion.service
import org.lsposed.manager.ui.adapters.ScopeAdapter.ApplicationWithEquals
import java.io.File
import java.util.Arrays
import java.util.function.Consumer

object ConfigManager {
    val isBinderAlive: Boolean
        get() = service != null

    val xposedApiVersion: Int
        get() {
            try {
                return service!!.getXposedApiVersion()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return -1
            }
        }

    val xposedVersionName: String?
        get() {
            try {
                return service!!.getXposedVersionName()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return ""
            }
        }

    val xposedVersionCode: Int
        get() {
            try {
                return service!!.getXposedVersionCode()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return -1
            }
        }

    fun getInstalledPackagesFromAllUsers(
        flags: Int,
        filterNoProcess: Boolean
    ): MutableList<PackageInfo?> {
        val list: MutableList<PackageInfo?> = ArrayList<PackageInfo?>()
        try {
            list.addAll(
                service!!.getInstalledPackagesFromAllUsers(flags, filterNoProcess).getList()
            )
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
        }
        return list
    }

    val enabledModules: Array<String?>?
        get() {
            try {
                return service!!.enabledModules()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return arrayOfNulls<String>(0)
            }
        }

    fun setModuleEnabled(packageName: String?, enable: Boolean): Boolean {
        try {
            return if (enable) service!!.enableModule(packageName) else service!!.disableModule(
                packageName
            )
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    fun setModuleScope(
        packageName: String?,
        legacy: Boolean,
        applications: MutableSet<ApplicationWithEquals?>
    ): Boolean {
        try {
            val list: MutableList<Application?> = ArrayList<Application?>()
            applications.forEach(Consumer { application: ApplicationWithEquals? ->
                val app = Application()
                app.userId = application!!.userId
                app.packageName = application.packageName
                list.add(app)
            })
            if (legacy) {
                val app = Application()
                app.userId = 0
                app.packageName = packageName
                list.add(app)
            }
            return service!!.setModuleScope(packageName, list)
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    fun getModuleScope(packageName: String?): MutableList<ApplicationWithEquals?> {
        val list: MutableList<ApplicationWithEquals?> = ArrayList<ApplicationWithEquals?>()
        try {
            val applications = service!!.getModuleScope(packageName)
            if (applications == null) {
                return list
            }
            applications.forEach(Consumer { application: Application? ->
                if (application!!.packageName != packageName) {
                    list.add(ApplicationWithEquals(application))
                }
            })
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
        }
        return list
    }

    fun enableStatusNotification(): Boolean {
        try {
            return service!!.enableStatusNotification()
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    fun setEnableStatusNotification(enabled: Boolean): Boolean {
        try {
            service!!.setEnableStatusNotification(enabled)
            return true
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    val isVerboseLogEnabled: Boolean
        get() {
            try {
                return service!!.isVerboseLog()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return false
            }
        }

    fun setVerboseLogEnabled(enabled: Boolean): Boolean {
        try {
            service!!.setVerboseLog(enabled)
            return true
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    val isLogWatchdogEnabled: Boolean
        get() {
            try {
                return service!!.isLogWatchdogEnabled()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return false
            }
        }

    fun setLogWatchdog(enabled: Boolean): Boolean {
        try {
            service!!.setLogWatchdog(enabled)
            return true
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    fun getLog(verbose: Boolean): ParcelFileDescriptor? {
        try {
            return if (verbose) service!!.getVerboseLog() else service!!.getModulesLog()
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return null
        }
    }

    fun clearLogs(verbose: Boolean): Boolean {
        try {
            return service!!.clearLogs(verbose)
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    @Throws(PackageManager.NameNotFoundException::class)
    fun getPackageInfo(packageName: String?, flags: Int, userId: Int): PackageInfo {
        try {
            val info = service!!.getPackageInfo(packageName, flags, userId)
            if (info == null) throw PackageManager.NameNotFoundException()
            return info
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            throw PackageManager.NameNotFoundException()
        }
    }

    fun forceStopPackage(packageName: String?, userId: Int): Boolean {
        try {
            service!!.forceStopPackage(packageName, userId)
            return true
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    fun reboot(): Boolean {
        try {
            service!!.reboot()
            return true
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    fun uninstallPackage(packageName: String?, userId: Int): Boolean {
        try {
            return service!!.uninstallPackage(packageName, userId)
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    val isSepolicyLoaded: Boolean
        get() {
            try {
                return service!!.isSepolicyLoaded()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return false
            }
        }

    val users: MutableList<UserInfo?>?
        get() {
            try {
                return service!!.getUsers()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return null
            }
        }

    fun installExistingPackageAsUser(packageName: String?, userId: Int): Boolean {
        val INSTALL_SUCCEEDED = 1
        try {
            val ret = service!!.installExistingPackageAsUser(packageName, userId)
            return ret == INSTALL_SUCCEEDED
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    val isMagiskInstalled: Boolean
        get() {
            val path = System.getenv("PATH")
            if (path == null) return false
            else return Arrays.stream<String?>(
                path.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray())
                .anyMatch { str: String? -> File(str, "magisk").exists() }
        }

    fun systemServerRequested(): Boolean {
        try {
            return service!!.systemServerRequested()
        } catch (e: RemoteException) {
            return false
        }
    }

    fun dex2oatFlagsLoaded(): Boolean {
        try {
            return service!!.dex2oatFlagsLoaded()
        } catch (e: RemoteException) {
            return false
        }
    }

    fun startActivityAsUserWithFeature(intent: Intent?, userId: Int): Int {
        try {
            return service!!.startActivityAsUserWithFeature(intent, userId)
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return -1
        }
    }

    fun queryIntentActivitiesAsUser(
        intent: Intent?,
        flags: Int,
        userId: Int
    ): MutableList<ResolveInfo?> {
        val list: MutableList<ResolveInfo?> = ArrayList<ResolveInfo?>()
        try {
            list.addAll(service!!.queryIntentActivitiesAsUser(intent, flags, userId).getList())
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
        }
        return list
    }

    fun setHiddenIcon(hide: Boolean): Boolean {
        try {
            service!!.setHiddenIcon(hide)
            return true
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    val api: String?
        get() {
            try {
                return service!!.getApi()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return e.toString()
            }
        }

    val denyListPackages: MutableList<String?>
        get() {
            val list: MutableList<String?> =
                ArrayList<String?>()
            try {
                list.addAll(service!!.getDenyListPackages())
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
            }
            return list
        }

    fun flashZip(zipPath: String?, outputStream: ParcelFileDescriptor?) {
        try {
            service!!.flashZip(zipPath, outputStream)
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
        }
    }

    val isDexObfuscateEnabled: Boolean
        get() {
            try {
                return service!!.getDexObfuscate()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return false
            }
        }

    fun setDexObfuscateEnabled(enabled: Boolean): Boolean {
        try {
            service!!.setDexObfuscate(enabled)
            return true
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    val dex2OatWrapperCompatibility: Int
        get() {
            try {
                return service!!.getDex2OatWrapperCompatibility()
            } catch (e: RemoteException) {
                Log.e(
                    App.TAG,
                    Log.getStackTraceString(e)
                )
                return ILSPManagerService.DEX2OAT_CRASHED
            }
        }

    fun getAutoInclude(packageName: String?): Boolean {
        try {
            return service!!.getAutoInclude(packageName)
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }

    fun setAutoInclude(packageName: String?, enable: Boolean): Boolean {
        try {
            service!!.setAutoInclude(packageName, enable)
            return true
        } catch (e: RemoteException) {
            Log.e(App.TAG, Log.getStackTraceString(e))
            return false
        }
    }
}
