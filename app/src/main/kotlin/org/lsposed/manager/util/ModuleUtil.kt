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

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import android.util.Log
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.repo.RepoLoader
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Arrays
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.zip.ZipFile

class ModuleUtil private constructor() {
    private val pm: PackageManager
    private val listeners: MutableSet<ModuleListener?> =
        ConcurrentHashMap.newKeySet<ModuleListener?>()
    private var enabledModules = HashSet<String?>()
    private var users: MutableList<UserInfo?>? = ArrayList<UserInfo?>()
    private var installedModules: MutableMap<androidx.core.util.Pair<String?, Int?>?, InstalledModule?> =
        HashMap<androidx.core.util.Pair<String?, Int?>?, InstalledModule?>()
    var isModulesLoaded: Boolean = false
        private set

    init {
        pm = App.instance?.getPackageManager()!!
    }

    @Synchronized
    fun reloadInstalledModules() {
        this.isModulesLoaded = false
        if (!ConfigManager.isBinderAlive) {
            this.isModulesLoaded = true
            return
        }

        val modules: MutableMap<androidx.core.util.Pair<String?, Int?>?, InstalledModule?> =
            HashMap<androidx.core.util.Pair<String?, Int?>?, InstalledModule?>()
        val users = ConfigManager.users
        for (pkg in ConfigManager.getInstalledPackagesFromAllUsers(
            PackageManager.GET_META_DATA or MATCH_ALL_FLAGS,
            false
        )) {
            val app = pkg?.applicationInfo

            val modernApk: ZipFile? = Companion.getModernModuleApk(app!!)
            if (modernApk != null || Companion.isLegacyModule(app)) {
                modules.computeIfAbsent(
                    androidx.core.util.Pair.create<String?, Int?>(
                        pkg.packageName,
                        app.uid / App.PER_USER_RANGE
                    )
                ) { k: androidx.core.util.Pair<String?, Int?>? -> InstalledModule(pkg, modernApk) }
            }
        }

        installedModules = modules

        this.users = users

        val enabledModulesArray = ConfigManager.enabledModules ?: emptyArray()
        enabledModules = HashSet<String?>(Arrays.asList(*enabledModulesArray))
        this.isModulesLoaded = true
        listeners.forEach(Consumer { obj: ModuleListener? -> obj!!.onModulesReloaded() })
    }

    fun getUsers(): MutableList<UserInfo?>? {
        return if (this.isModulesLoaded) users else null
    }

    @JvmOverloads
    fun reloadSingleModule(
        packageName: String?,
        userId: Int,
        packageFullyRemoved: Boolean = false
    ): InstalledModule? {
        if (packageFullyRemoved && isModuleEnabled(packageName)) {
            enabledModules.remove(packageName)
            listeners.forEach(Consumer { obj: ModuleListener? -> obj!!.onModulesReloaded() })
        }
        val pkg: PackageInfo?

        try {
            pkg = ConfigManager.getPackageInfo(packageName, PackageManager.GET_META_DATA, userId)
        } catch (e: PackageManager.NameNotFoundException) {
            val old = installedModules.remove(
                androidx.core.util.Pair.create<String?, Int?>(
                    packageName,
                    userId
                )
            )
            if (old != null) listeners.forEach(Consumer { i: ModuleListener? ->
                i!!.onSingleModuleReloaded(
                    old
                )
            })
            return null
        }

        val app = pkg.applicationInfo
        val modernApk: ZipFile? = Companion.getModernModuleApk(app!!)
        if (modernApk != null || Companion.isLegacyModule(app)) {
            val module = InstalledModule(pkg, modernApk)
            installedModules.put(
                androidx.core.util.Pair.create<String?, Int?>(packageName, userId),
                module
            )
            listeners.forEach(Consumer { i: ModuleListener? -> i!!.onSingleModuleReloaded(module) })
            return module
        } else {
            val old = installedModules.remove(
                androidx.core.util.Pair.create<String?, Int?>(
                    packageName,
                    userId
                )
            )
            if (old != null) listeners.forEach(Consumer { i: ModuleListener? ->
                i!!.onSingleModuleReloaded(
                    old
                )
            })
            return null
        }
    }

    fun getModule(packageName: String?, userId: Int): InstalledModule? {
        return if (this.isModulesLoaded) installedModules.get(
            androidx.core.util.Pair.create<String?, Int?>(
                packageName,
                userId
            )
        ) else null
    }

    fun getModule(packageName: String?): InstalledModule? {
        return getModule(packageName, 0)
    }

    @get:Synchronized
    val modules: MutableMap<Pair<String?, Int?>?, InstalledModule?>?
        get() = (if (this.isModulesLoaded) installedModules else null) as MutableMap<Pair<String?, Int?>?, InstalledModule?>?

    fun setModuleEnabled(packageName: String?, enabled: Boolean): Boolean {
        if (!ConfigManager.setModuleEnabled(packageName, enabled)) {
            return false
        }
        if (enabled) {
            enabledModules.add(packageName)
        } else {
            enabledModules.remove(packageName)
        }
        return true
    }

    fun isModuleEnabled(packageName: String?): Boolean {
        return enabledModules.contains(packageName)
    }

    val enabledModulesCount: Int
        get() = if (this.isModulesLoaded) enabledModules.size else -1

    fun addListener(listener: ModuleListener?) {
        listeners.add(listener)
    }

    fun removeListener(listener: ModuleListener?) {
        listeners.remove(listener)
    }

    interface ModuleListener {
        /**
         * Called whenever one (previously or now) installed module has been
         * reloaded
         */
        fun onSingleModuleReloaded(module: InstalledModule?) {
        }

        fun onModulesReloaded() {
        }
    }

    inner class InstalledModule internal constructor(
        val packageInfo: PackageInfo,
        modernModuleApk: ZipFile?
    ) {
        //private static final int FLAG_FORWARD_LOCK = 1 << 29;
        val userId: Int
        val packageName: String
        val versionName: String?
        var versionCode: Long = 0
        val legacy: Boolean
        var minVersion: Int = 0
        var targetVersion: Int = 0
        var staticScope: Boolean = false
        val installTime: Long
        val updateTime: Long
        val app: ApplicationInfo?
        var appName: String? = null // loaded lazily
            get() {
                if (field == null) field = app!!.loadLabel(pm).toString()
                return field
            }
            private set
        var description: String? = null // loaded lazily
            get() {
                if (field != null) return field
                var descriptionTmp = ""
                if (legacy) {
                    val descriptionRaw = app!!.metaData.get("xposeddescription")
                    if (descriptionRaw is String) {
                        descriptionTmp = descriptionRaw.trim { it <= ' ' }
                    } else if (descriptionRaw is Int) {
                        try {
                            val resId = descriptionRaw
                            if (resId != 0) descriptionTmp =
                                pm.getResourcesForApplication(app).getString(resId)
                                    .trim { it <= ' ' }
                        } catch (ignored: Exception) {
                        }
                    }
                } else {
                    val des = app!!.loadDescription(pm)
                    if (des != null) descriptionTmp = des.toString()
                }
                field = descriptionTmp
                return field
            }
            private set
        internal var scopeList: MutableList<String?>? = null // loaded lazily

        init {
            app = packageInfo.applicationInfo
            userId = packageInfo.applicationInfo!!.uid / App.PER_USER_RANGE
            packageName = packageInfo.packageName
            versionName = packageInfo.versionName
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                versionCode = packageInfo.versionCode.toLong()
            } else {
                versionCode = packageInfo.getLongVersionCode()
            }
            installTime = packageInfo.firstInstallTime
            updateTime = packageInfo.lastUpdateTime
            legacy = modernModuleApk == null

            if (legacy) {
                val minVersionRaw = app!!.metaData.get("xposedminversion")
                if (minVersionRaw is Int) {
                    minVersion = minVersionRaw
                } else if (minVersionRaw is String) {
                    minVersion = extractIntPart(minVersionRaw)
                } else {
                    minVersion = 0
                }
                targetVersion = minVersion // legacy modules don't have a target version
                staticScope = false
            } else {
                var minVersion = 100
                var targetVersion = 100
                var staticScope = false
                try {
                    modernModuleApk.use {
                        val propEntry = modernModuleApk.getEntry("META-INF/xposed/module.prop")
                        if (propEntry != null) {
                            val prop = Properties()
                            prop.load(modernModuleApk.getInputStream(propEntry))
                            minVersion = extractIntPart(prop.getProperty("minApiVersion"))
                            targetVersion = extractIntPart(prop.getProperty("targetApiVersion"))
                            staticScope = TextUtils.equals(prop.getProperty("staticScope"), "true")
                        }
                        val scopeEntry = modernModuleApk.getEntry("META-INF/xposed/scope.list")
                        if (scopeEntry != null) {
                            BufferedReader(
                                InputStreamReader(
                                    modernModuleApk.getInputStream(
                                        scopeEntry
                                    )
                                )
                            ).use { reader ->
                                scopeList = reader.lines().collect(
                                    Collectors.toList()
                                )
                            }
                        } else {
                            scopeList = mutableListOf<String?>()
                        }
                    }
                } catch (e: IOException) {
                    Log.e(App.TAG, "Error while closing modern module APK", e)
                } catch (e: OutOfMemoryError) {
                    Log.e(App.TAG, "Error while closing modern module APK", e)
                }
                this.minVersion = minVersion
                this.targetVersion = targetVersion
                this.staticScope = staticScope
            }
        }

        val isInstalledOnExternalStorage: Boolean
            get() = (app!!.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0

        fun getScopeList(): MutableList<String?>? {
            if (scopeList != null) return scopeList
            var list: MutableList<String?>? = null
            try {
                val scopeListResourceId = app!!.metaData.getInt("xposedscope")
                if (scopeListResourceId != 0) {
                    list = Arrays.asList<String?>(
                        *pm.getResourcesForApplication(app).getStringArray(scopeListResourceId)
                    )
                } else {
                    val scopeListString = app.metaData.getString("xposedscope")
                    if (scopeListString != null) list = Arrays.asList<String?>(
                        *scopeListString.split(";".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray())
                }
            } catch (ignored: Exception) {
            }
            if (list == null) {
                val module = RepoLoader.instance?.getOnlineModule(packageName)
                if (module != null && module.scope != null) {
                    list = module.scope
                }
            }
            if (list != null) {
                //For historical reasons, legacy modules use the opposite name.
                //https://github.com/rovo89/XposedBridge/commit/6b49688c929a7768f3113b4c65b429c7a7032afa
                list.replaceAll { s: String? ->
                    when (s) {
                        "android" -> "system"
                        "system" -> "android"
                        else -> s
                    }
                }
                scopeList = list
            }
            return scopeList
        }

        override fun toString(): String {
            return this.appName!!
        }
    }

    companion object {
        // xposedminversion below this
        var MIN_MODULE_VERSION: Int = 2 // reject modules with

        @JvmStatic
        @get:Synchronized
        var instance: ModuleUtil? = null
            get() {
                if (field == null) {
                    field = ModuleUtil()
                    App.executorService
                        ?.submit(Runnable { field!!.reloadInstalledModules() })
                }
                return field
            }
            private set
        const val MATCH_ANY_USER: Int = 0x00400000 // PackageManager.MATCH_ANY_USER

        val MATCH_ALL_FLAGS: Int =
            PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE or PackageManager.MATCH_UNINSTALLED_PACKAGES or MATCH_ANY_USER

        fun extractIntPart(str: String): Int {
            var result = 0
            val length = str.length
            for (offset in 0..<length) {
                val c = str.get(offset)
                if ('0' <= c && c <= '9') result = result * 10 + (c.code - '0'.code)
                else break
            }
            return result
        }

        fun getModernModuleApk(info: ApplicationInfo): ZipFile? {
            val apks: Array<String?>?
            if (info.splitSourceDirs != null) {
                apks = (info.splitSourceDirs as Array<String>).copyOf<String>(info.splitSourceDirs!!.size + 1)
                apks.set(info.splitSourceDirs!!.size, info.sourceDir)
            } else apks = arrayOf<String?>(info.sourceDir)
            var zip: ZipFile? = null
            for (apk in apks) {
                try {
                    zip = ZipFile(apk)
                    if (zip.getEntry("META-INF/xposed/java_init.list") != null) {
                        return zip
                    }
                    zip.close()
                    zip = null
                } catch (ignored: IOException) {
                }
            }
            return zip
        }

        fun isLegacyModule(info: ApplicationInfo): Boolean {
            return info.metaData != null && info.metaData.containsKey("xposedminversion")
        }
    }
}
