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
package org.lsposed.manager.util

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import org.lsposed.manager.App
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.ConfigManager
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import androidx.core.content.edit

object UpdateUtil {
    @JvmStatic
    fun loadRemoteVersion() {
        val request = Request.Builder()
            .url("https://api.github.com/repos/JingMatrix/LSPosed/releases/latest")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .build()
        val callback: Callback? = object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) return
                val body = response.body
                if (body == null) return
                val api = if (ConfigManager.isBinderAlive) ConfigManager.api else "riru"
                try {
                    val info = JsonParser.parseReader(body.charStream()).getAsJsonObject()
                    val notes = info.get("body").getAsString()
                    val assetsArray = info.getAsJsonArray("assets")
                    for (assets in assetsArray) {
                        checkAssets(assets.getAsJsonObject(), notes, api?.lowercase())
                    }
                } catch (t: Throwable) {
                    Log.e(App.TAG, t.message, t)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e(App.TAG, "loadRemoteVersion: " + e.message)
                val pref = App.preferences
                if (pref.getBoolean("checked", false)) return
                pref.edit { putBoolean("checked", true) }
            }
        }
        App.okHttpClient?.newCall(request)?.enqueue(callback!!)
    }

    private fun checkAssets(assets: JsonObject, releaseNotes: String?, api: String?) {
        val pref = App.preferences
        val name = assets.get("name").getAsString()
        val splitName: Array<String?> =
            name.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (splitName[3] != api) return
        pref.edit()
            .putInt("latest_version", splitName[2]!!.toInt())
            .putLong("latest_check", Instant.now().getEpochSecond())
            .putString("release_notes", releaseNotes)
            .putString("zip_file", null)
            .putBoolean("checked", true)
            .apply()
        val updatedAt = Instant.parse(assets.get("updated_at").getAsString())
        val downloadUrl = assets.get("browser_download_url").getAsString()
        val zipTime = pref.getLong("zip_time", 0)
        if (updatedAt != Instant.ofEpochSecond(zipTime)) {
            val zip = downloadNewZipSync(downloadUrl, name)
            val size = assets.get("size").getAsLong()
            if (zip != null && zip.length() == size) {
                pref.edit()
                    .putLong("zip_time", updatedAt.getEpochSecond())
                    .putString("zip_file", zip.getAbsolutePath())
                    .apply()
            }
        }
    }

    fun needUpdate(): Boolean {
        val pref = App.preferences
        if (!pref.getBoolean("checked", false)) return false
        val now = Instant.now()
        val buildTime = Instant.ofEpochSecond(BuildConfig.BUILD_TIME)
        val check = pref.getLong("latest_check", 0)
        if (check > 0) {
            val checkTime = Instant.ofEpochSecond(check)
            if (checkTime.atOffset(ZoneOffset.UTC).plusDays(30).toInstant()
                    .isBefore(now)
            ) return true
            val code = pref.getInt("latest_version", 0)
            return code > BuildConfig.VERSION_CODE
        }
        return buildTime.atOffset(ZoneOffset.UTC).plusDays(30).toInstant().isBefore(now)
    }

    private fun downloadNewZipSync(url: String, name: String): File? {
        val request = Request.Builder().url(url).build()
        val zip = File(App.instance?.getCacheDir(), name)
        try {
            App.okHttpClient?.newCall(request)?.execute().use { response ->
                val body = response?.body
                if (response?.isSuccessful == true || body == null) return null
                body.source().use { source ->
                    zip.sink().buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(App.TAG, "downloadNewZipSync: " + e.message)
            return null
        }
        return zip
    }

    fun canInstall(): Boolean {
        if (!ConfigManager.isBinderAlive) return false
        val pref = App.preferences
        val zip = pref.getString("zip_file", null)
        return zip != null && File(zip).isFile()
    }
}
