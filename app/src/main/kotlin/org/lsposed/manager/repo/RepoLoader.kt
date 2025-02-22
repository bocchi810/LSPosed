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
package org.lsposed.manager.repo

import android.content.res.Resources
import android.util.Log
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.repo.model.OnlineModule
import org.lsposed.manager.repo.model.Release
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap

class RepoLoader {
    internal var onlineModules: MutableMap<String?, OnlineModule?> =
        HashMap<String?, OnlineModule?>()
    private var latestVersion: MutableMap<String?, ModuleVersion?> =
        ConcurrentHashMap<String?, ModuleVersion?>()

    class ModuleVersion internal constructor(var versionCode: Long, var versionName: String?) {
        fun upgradable(versionCode: Long, versionName: String): Boolean {
            return this.versionCode > versionCode || (this.versionCode == versionCode && versionName.replace(
                ' ',
                '_'
            ) != this.versionName)
        }
    }

    private val repoFile: Path? =
        Paths.get(App.instance?.getFilesDir()?.getAbsolutePath(), "repo.json")
    private val listeners: MutableSet<RepoListener> = ConcurrentHashMap.newKeySet<RepoListener?>()
    var isRepoLoaded: Boolean = false
        private set
    private val resources: Resources? = App.instance?.getResources()
    private val channels: Array<out String?>? = resources?.getStringArray(R.array.update_channel_values)

    @Synchronized
    fun loadRemoteData() {
        this.isRepoLoaded = false
        try {
            App.okHttpClient?.newCall(Request.Builder().url(repoUrl + "modules.json").build())
                ?.execute().use { response ->
                    if (response?.isSuccessful == true) {
                        val body = response.body
                        if (body != null) {
                            try {
                                val bodyString = body.string()
                                Files.write(
                                    repoFile,
                                    bodyString.toByteArray(StandardCharsets.UTF_8)
                                )
                                loadLocalData(false)
                            } catch (t: Throwable) {
                                Log.e(App.TAG, Log.getStackTraceString(t))
                                for (listener in listeners) {
                                    listener.onThrowable(t)
                                }
                            }
                        }
                    }
                }
        } catch (e: Throwable) {
            Log.e(App.TAG, "load remote data", e)
            for (listener in listeners) {
                listener.onThrowable(e)
            }
            if (repoUrl == originRepoUrl) {
                repoUrl = backupRepoUrl
                loadRemoteData()
            } else if (repoUrl == backupRepoUrl) {
                repoUrl = secondBackupRepoUrl
                loadRemoteData()
            }
        }
    }

    @Synchronized
    fun loadLocalData(updateRemoteRepo: Boolean) {
        var updateRemoteRepo = updateRemoteRepo
        this.isRepoLoaded = false
        try {
            if (Files.notExists(repoFile)) {
                loadRemoteData()
                updateRemoteRepo = false
            }
            val encoded = Files.readAllBytes(repoFile)
            val bodyString = String(encoded, StandardCharsets.UTF_8)
            val gson = Gson()

            // 解析为 Array<OnlineModule>
            val repoModules: Array<OnlineModule> = gson.fromJson(bodyString, Array<OnlineModule>::class.java)

            // 将 Array<OnlineModule> 转换为 MutableMap<String?, OnlineModule?>
            val modules: MutableMap<String?, OnlineModule?> = HashMap()
            for (onlineModule in repoModules) {
                modules[onlineModule.name] = onlineModule
            }

            // 更新最新版本信息
            val channel = App.preferences.getString("update_channel", channels?.get(0))
            updateLatestVersion(modules, channel!!)

            // 更新 onlineModules
            onlineModules = modules
        } catch (t: Throwable) {
            Log.e(App.TAG, Log.getStackTraceString(t))
            for (listener in listeners) {
                listener.onThrowable(t)
            }
        } finally {
            this.isRepoLoaded = true
            for (listener in listeners) {
                listener.onRepoLoaded()
            }
            if (updateRemoteRepo) loadRemoteData()
        }
    }

    @Synchronized
    internal fun updateLatestVersion(onlineModules: MutableMap<String?, OnlineModule?>, channel: String) {
        this.isRepoLoaded = false
        val versions: MutableMap<String?, ModuleVersion?> =
            ConcurrentHashMap<String?, ModuleVersion?>()
        for (module in onlineModules.values) {
            // 检查 module 是否为 null
            if (module == null) continue

            var release = module.latestRelease
            if (channel == channels?.get(1) && module.latestBetaRelease != null && module.latestBetaRelease!!.isEmpty()) {
                release = module.latestBetaRelease
            } else if (channel == channels?.get(2)) {
                if (module.latestSnapshotRelease != null && module.latestSnapshotRelease!!.isEmpty()) {
                    release = module.latestSnapshotRelease
                } else if (module.latestBetaRelease != null && module.latestBetaRelease!!.isEmpty()) {
                    release = module.latestBetaRelease
                }
            }
            if (release == null || release.isEmpty()) continue
            val splits: Array<String?> = release.split("-".toRegex(), limit = 2).toTypedArray()
            if (splits.size < 2) continue
            val verCode: Long
            val verName: String?
            try {
                verCode = splits[0]!!.toLong()
                verName = splits[1]
            } catch (ignored: NumberFormatException) {
                continue
            }
            val pkgName = module.name
            versions.put(pkgName, ModuleVersion(verCode, verName))
        }
    }

    fun getModuleLatestVersion(packageName: String?): ModuleVersion? {
        return if (this.isRepoLoaded) latestVersion.getOrDefault(packageName, null) else null
    }

    fun getReleases(packageName: String?): MutableList<Release?>? {
        val channel = App.preferences.getString("update_channel", channels?.get(0))
        var releases: MutableList<Release?>? = ArrayList<Release?>()
        if (this.isRepoLoaded) {
            val module: OnlineModule? = onlineModules.get(packageName)
            if (module != null) {
                releases = module.releases
                if (!module.releasesLoaded) {
                    if (channel == channels?.get(1) && !(module.betaReleases != null && module.betaReleases!!
                            .isEmpty())
                    ) {
                        releases = module.betaReleases
                    } else if (channel == channels?.get(2)) if (!(module.snapshotReleases != null && module.snapshotReleases!!
                            .isEmpty())
                    ) releases = module.snapshotReleases
                    else if (!(module.betaReleases != null && module.betaReleases!!
                            .isEmpty())
                    ) releases = module.betaReleases
                }
            }
        }
        return releases
    }

    fun getLatestReleaseTime(packageName: String?, channel: String): String? {
        var releaseTime: String? = null
        if (this.isRepoLoaded) {
            val module: OnlineModule? = onlineModules.get(packageName)
            if (module != null) {
                releaseTime = module.latestReleaseTime
                if (channel == channels?.get(1) && module.latestBetaReleaseTime != null) {
                    releaseTime = module.latestBetaReleaseTime
                } else if (channel == channels?.get(2)) if (module.latestSnapshotReleaseTime != null) releaseTime =
                    module.latestSnapshotReleaseTime
                else if (module.latestBetaReleaseTime != null) releaseTime =
                    module.latestBetaReleaseTime
            }
        }
        return releaseTime
    }

    fun loadRemoteReleases(packageName: String) {
        App.okHttpClient?.newCall(
            Request.Builder().url(String.format(repoUrl + "module/%s.json", packageName)).build()
        )?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(App.TAG, call.request().url.toString() + e.message)
                if (repoUrl == originRepoUrl) {
                    repoUrl = backupRepoUrl
                    loadRemoteReleases(packageName)
                } else if (repoUrl == backupRepoUrl) {
                    repoUrl = secondBackupRepoUrl
                    loadRemoteReleases(packageName)
                } else {
                    for (listener in listeners) {
                        listener.onThrowable(e)
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        try {
                            val bodyString = body.string()
                            val gson = Gson()
                            val module =
                                gson.fromJson<OnlineModule>(bodyString, OnlineModule::class.java)
                            module.releasesLoaded = true
                            onlineModules.replace(packageName, module)
                            for (listener in listeners) {
                                listener.onModuleReleasesLoaded(module)
                            }
                        } catch (t: Throwable) {
                            Log.e(App.TAG, Log.getStackTraceString(t))
                            for (listener in listeners) {
                                listener.onThrowable(t)
                            }
                        }
                    }
                }
            }
        })
    }

    fun addListener(listener: RepoListener?) {
        listeners.add(listener!!)
    }

    fun removeListener(listener: RepoListener?) {
        listeners.remove(listener)
    }

    fun getOnlineModule(packageName: String?): OnlineModule? {
        return if (this.isRepoLoaded && packageName != null) onlineModules.get(packageName) else null
    }

    fun getOnlineModules(): MutableCollection<OnlineModule?>? {
        return if (this.isRepoLoaded) onlineModules.values else null
    }

    interface RepoListener {
        fun onRepoLoaded() {
        }

        fun onModuleReleasesLoaded(module: OnlineModule?) {
        }

        fun onThrowable(t: Throwable?) {
            Log.e(App.TAG, "load repo failed", t)
        }
    }

    companion object {
        @JvmStatic
        @get:Synchronized
        var instance: RepoLoader? = null
            get() {
                if (field == null) {
                    field = RepoLoader()
                    App.executorService
                        ?.submit(Runnable { field!!.loadLocalData(true) })
                }
                return field
            }
            private set
        private const val originRepoUrl = "https://modules.lsposed.org/"
        private const val backupRepoUrl = "https://modules-blogcdn.lsposed.org/"

        private const val secondBackupRepoUrl = "https://modules-cloudflare.lsposed.org/"
        private var repoUrl: String = originRepoUrl
    }
}
