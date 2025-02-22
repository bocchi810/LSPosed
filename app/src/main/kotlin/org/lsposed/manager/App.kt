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
package org.lsposed.manager

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue.IdleHandler
import android.os.Parcelable
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.system.Os
import android.text.TextUtils
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.lsposed.manager.receivers.LSPManagerServiceHolder.Companion.service
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.ui.adapters.AppHelper.getAppLabel
import org.lsposed.manager.ui.adapters.AppHelper.getAppList
import org.lsposed.manager.ui.adapters.AppHelper.getDenyList
import org.lsposed.manager.util.CloudflareDNS
import org.lsposed.manager.util.ModuleUtil
import org.lsposed.manager.util.ThemeUtil.darkTheme
import org.lsposed.manager.util.UpdateUtil.loadRemoteVersion
import rikka.core.os.FileUtils
import rikka.material.app.LocaleDelegate
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import androidx.core.content.edit

class App : Application() {
    private var pref: SharedPreferences? = null
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        val map = HashMap<String?, String?>(1)
        map.put("isParasitic", isParasitic.toString())
        val am = getSystemService<ActivityManager>(ActivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            map.clear()
            val reasons = am.getHistoricalProcessExitReasons(null, 0, 1)
            if (reasons.size == 1) {
                map.put("description", reasons.get(0)!!.getDescription())
                map.put("importance", reasons.get(0)!!.getImportance().toString())
                map.put("process", reasons.get(0)!!.getProcessName())
                map.put("reason", reasons.get(0)!!.getReason().toString())
                map.put("status", reasons.get(0)!!.getStatus().toString())
            }
        }
    }

    private fun setCrashReport() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler setDefaultUncaughtExceptionHandler@{ thread: Thread?, throwable: Throwable? ->
            val time = OffsetDateTime.now()
            val dir = File(getCacheDir(), "crash")
            dir.mkdir()
            val file = File(dir, time.toEpochSecond().toString() + ".log")
            try {
                PrintWriter(file).use { pw ->
                    pw.println(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
                    pw.println(time)
                    pw.println("pid: " + Os.getpid() + " uid: " + Os.getuid())
                    throwable!!.printStackTrace(pw)
                }
            } catch (ignored: IOException) {
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val table = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues()
                values.put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    "LSPosed_crash_report" + time.toEpochSecond() + ".zip"
                )
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                val cr = getContentResolver()
                val uri = cr.insert(table, values)
                if (uri == null) return@setDefaultUncaughtExceptionHandler
                try {
                    cr.openFileDescriptor(uri, "wt").use { zipFd ->
                        service!!.getLogs(zipFd)
                    }
                } catch (ignored: Exception) {
                    cr.delete(uri, null, null)
                }
            }
            if (handler != null) {
                if (thread != null) {
                    if (throwable != null) {
                        handler.uncaughtException(thread, throwable)
                    }
                }
            }
        })
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        setCrashReport()
        pref = PreferenceManager.getDefaultSharedPreferences(this)
        if (!pref!!.contains("doh")) {
            val name = "private_dns_mode"
            if ("hostname" == Settings.Global.getString(getContentResolver(), name)) {
                pref!!.edit { putBoolean("doh", false) }
            } else {
                pref!!.edit { putBoolean("doh", true) }
            }
        }
        AppCompatDelegate.setDefaultNightMode(darkTheme)
        LocaleDelegate.defaultLocale
        val res = getResources()
        val config = res.getConfiguration()
        config.setLocale(LocaleDelegate.defaultLocale)
        res.updateConfiguration(config, res.getDisplayMetrics())

        val intentFilter = IntentFilter()
        intentFilter.addAction("org.lsposed.manager.NOTIFICATION")
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, inIntent: Intent) {
                val intent =
                    inIntent.getParcelableExtra<Parcelable?>(Intent.EXTRA_INTENT) as Intent?
                Log.d(TAG, "onReceive: " + intent)
                when (intent!!.getAction()) {
                    Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_FULLY_REMOVED, Intent.ACTION_UID_REMOVED -> {
                        val userId = intent.getIntExtra(Intent.EXTRA_USER, 0)
                        val packageName = intent.getStringExtra("android.intent.extra.PACKAGES")
                        val packageRemovedForAllUsers = intent.getBooleanExtra(
                            EXTRA_REMOVED_FOR_ALL_USERS, false
                        )
                        val isXposedModule = intent.getBooleanExtra("isXposedModule", false)
                        if (packageName != null) {
                            if (isXposedModule) ModuleUtil.instance!!.reloadSingleModule(
                                packageName,
                                userId,
                                packageRemovedForAllUsers
                            )
                            else executorService!!.submit<MutableList<PackageInfo>?>(Callable {
                                getAppList(
                                    true
                                )
                            })
                        }
                    }

                    ACTION_USER_ADDED, ACTION_USER_REMOVED, ACTION_USER_INFO_CHANGED -> executorService!!.submit(
                        Runnable { ModuleUtil.instance!!.reloadInstalledModules() })
                }
            }
        }, intentFilter, RECEIVER_NOT_EXPORTED)

        loadRemoteVersion()
    }

    companion object {
        const val PER_USER_RANGE: Int = 100000
        val HTML_TEMPLATE: FutureTask<String?> =
            FutureTask<String?>(Callable { readWebviewHTML("template.html") })
        val HTML_TEMPLATE_DARK: FutureTask<String?> =
            FutureTask<String?>(Callable { readWebviewHTML("template_dark.html") })

        private fun readWebviewHTML(name: String?): String {
            try {
                val input: InputStream = instance!!.getAssets().open("webview/" + name)
                val result = ByteArrayOutputStream(1024)
                FileUtils.copy(input, result)
                return result.toString(StandardCharsets.UTF_8.name())
            } catch (e: IOException) {
                Log.e(TAG, "read webview HTML", e)
                return "<html dir\"@dir@\"><body>@body@</body></html>"
            }
        }

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("")
            }
            Looper.myQueue().addIdleHandler(IdleHandler addIdleHandler@{
                if (instance == null || executorService == null) return@addIdleHandler true
                executorService.submit(Runnable {
                    val list: MutableList<PackageInfo?>? = getAppList(false) as MutableList<PackageInfo?>?
                    val pm: PackageManager = instance!!.getPackageManager()
                    list!!.parallelStream().forEach { i: PackageInfo? -> getAppLabel(i, pm) }
                    getDenyList(false)
                    ModuleUtil.instance
                    RepoLoader.instance
                })
                executorService.submit(HTML_TEMPLATE)
                executorService.submit(HTML_TEMPLATE_DARK)
                false
            })
        }

        const val TAG: String = "LSPosedManager"
        private const val ACTION_USER_ADDED = "android.intent.action.USER_ADDED"
        private const val ACTION_USER_REMOVED = "android.intent.action.USER_REMOVED"
        private const val ACTION_USER_INFO_CHANGED = "android.intent.action.USER_INFO_CHANGED"
        private const val EXTRA_REMOVED_FOR_ALL_USERS = "android.intent.extra.REMOVED_FOR_ALL_USERS"
        var instance: App? = null
            private set
        var okHttpClient: OkHttpClient? = null
            get() {
                if (field != null) return field
                val builder = OkHttpClient.Builder()
                    .cache(okHttpCache)
                    .dns(CloudflareDNS())
                if (BuildConfig.DEBUG) {
                    val log = HttpLoggingInterceptor()
                    log.setLevel(HttpLoggingInterceptor.Level.HEADERS)
                    builder.addInterceptor(log)
                }
                field = builder.build()
                return field
            }
            private set
        var okHttpCache: Cache? = null
            get() {
                if (field != null) return field
                val size50MiB = (50 * 1024 * 1024).toLong()
                field = Cache(
                    File(
                        instance!!.getCacheDir(),
                        "http_cache"
                    ), size50MiB
                )
                return field
            }
            private set
        val executorService: ExecutorService? = Executors.newCachedThreadPool()
        val mainHandler: Handler = Handler(Looper.getMainLooper())

        val preferences: SharedPreferences
            get() = instance!!.pref!!

        val isParasitic: Boolean = !Process.isApplicationUid(Process.myUid())

        fun getLocale(tag: String?): Locale {
            if (TextUtils.isEmpty(tag) || "SYSTEM" == tag) {
                return LocaleDelegate.systemLocale
            }
            return Locale.forLanguageTag(tag)
        }

        val locale: Locale
            get() {
                val tag: String? =
                    preferences.getString("language", null)
                return getLocale(tag)
            }
    }
}
