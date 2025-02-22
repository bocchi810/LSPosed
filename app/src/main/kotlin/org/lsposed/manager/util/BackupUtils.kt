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

import org.lsposed.manager.ui.adapters.ScopeAdapter.ApplicationWithEquals
import android.net.Uri
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import rikka.core.os.FileUtils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object BackupUtils {
    private const val VERSION = 2

    @JvmOverloads
    @Throws(IOException::class, JSONException::class)
    fun backup(uri: Uri, packageName: String? = null) {
        val rootObject = JSONObject()
        rootObject.put("version", VERSION)
        val modulesArray = JSONArray()
        val modules = ModuleUtil.instance?.modules
        if (modules == null) return
        for (module in modules.values) {
            if (packageName != null && module?.packageName != packageName) {
                continue
            }
            val moduleObject = JSONObject()
            moduleObject.put("enable", ModuleUtil.instance?.isModuleEnabled(module?.packageName))
            moduleObject.put("package", module?.packageName)
            val scope = ConfigManager.getModuleScope(module?.packageName)
            val scopeArray = JSONArray()
            for (s in scope) {
                val app = JSONObject()
                app.put("package", s?.packageName)
                app.put("userId", s?.userId)
                scopeArray.put(app)
            }
            moduleObject.put("scope", scopeArray)
            modulesArray.put(moduleObject)
        }
        rootObject.put("modules", modulesArray)
        GZIPOutputStream(
            App.instance?.getContentResolver()?.openOutputStream(uri)
        ).use { gzipOutputStream ->
            gzipOutputStream.write(rootObject.toString().toByteArray())
        }
    }

    @JvmOverloads
    @Throws(IOException::class, JSONException::class)
    fun restore(uri: Uri, packageName: String? = null) {
        GZIPInputStream(
            App.instance?.getContentResolver()?.openInputStream(uri),
            32
        ).use { gzipInputStream ->
            val string = StringBuilder()
            ByteArrayOutputStream().use { os ->
                FileUtils.copy(gzipInputStream, os)
                string.append(os)
            }
            gzipInputStream.close()
            val rootObject = JSONObject(string.toString())
            val version = rootObject.getInt("version")
            if (version == VERSION || version == 1) {
                val modules = rootObject.getJSONArray("modules")
                for (i in 0..<modules.length()) {
                    val moduleObject = modules.getJSONObject(i)
                    val name = moduleObject.getString("package")
                    if (packageName != null && name != packageName) {
                        continue
                    }
                    val module = ModuleUtil.instance?.getModule(name)
                    if (module != null) {
                        val enabled = moduleObject.getBoolean("enable")
                        ModuleUtil.instance?.setModuleEnabled(name, enabled)
                        if (!enabled) continue
                        val scopeArray = moduleObject.getJSONArray("scope")
                        val scope = HashSet<ApplicationWithEquals?>()
                        for (j in 0..<scopeArray.length()) {
                            if (version == VERSION) {
                                val app = scopeArray.getJSONObject(j)
                                scope.add(
                                    ApplicationWithEquals(
                                        app.getString("package"),
                                        app.getInt("userId")
                                    )
                                )
                            } else {
                                scope.add(ApplicationWithEquals(scopeArray.getString(j), 0))
                            }
                        }
                        ConfigManager.setModuleScope(name, module.legacy, scope)
                    }
                }
            } else {
                throw IllegalArgumentException("Unknown backup file version")
            }
        }
    }
}
