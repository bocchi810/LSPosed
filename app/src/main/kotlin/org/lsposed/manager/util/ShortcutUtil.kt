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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Build
import org.lsposed.manager.App
import org.lsposed.manager.R
import java.util.UUID
import androidx.core.graphics.createBitmap

object ShortcutUtil {
    private const val SHORTCUT_ID = "org.lsposed.manager.shortcut"

    private fun getBitmap(context: App?, id: Int): Bitmap? {
        val r = context?.getResources()
        var res = r?.getDrawable(id, context.getTheme())
        if (res is BitmapDrawable) {
            return res.getBitmap()
        } else {
            if (res is AdaptiveIconDrawable) {
                val layers = arrayOf<Drawable?>(
                    res.getBackground(),
                    res.getForeground()
                )
                res = LayerDrawable(layers)
            }
            val bitmap = res?.getIntrinsicWidth()?.let { res.getIntrinsicHeight().let { height -> createBitmap(it, height) } }
            val canvas = bitmap?.let { Canvas(it) }
            canvas?.getWidth()?.let { canvas.getHeight().let { bottom -> res.setBounds(0, 0, it, bottom) } }
            canvas?.let { res.draw(it) }
            return bitmap
        }
    }


    private fun getLaunchIntent(context: App?): Intent? {
        val pm = context?.getPackageManager()
        val pkg = context?.getPackageName()
        var intent = pm?.getLaunchIntentForPackage(pkg.toString())
        if (intent == null) {
            try {
                val pkgInfo = pm?.getPackageInfo(pkg.toString(), PackageManager.GET_ACTIVITIES)
                if (pkgInfo?.activities != null) {
                    for (activityInfo in pkgInfo.activities) {
                        if (activityInfo.processName == activityInfo.packageName) {
                            intent = Intent(Intent.ACTION_MAIN)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.setComponent(ComponentName(pkg.toString(), activityInfo.name))
                            break
                        }
                    }
                }
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
        if (intent != null) {
            val categories = intent.getCategories()
            if (categories != null) {
                categories.clear()
            }
            intent.addCategory("org.lsposed.manager.LAUNCH_MANAGER")
            intent.setPackage(pkg)
        }
        return intent
    }

    @SuppressLint("InlinedApi")
    private fun registerReceiver(context: Context, task: Runnable?): IntentSender? {
        if (task == null) return null
        val uuid = UUID.randomUUID().toString()
        val filter = IntentFilter(uuid)
        val permission = "android.permission.CREATE_USERS"
        val receiver: BroadcastReceiver? = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent) {
                if (uuid != intent.getAction()) return
                context.unregisterReceiver(this)
                task.run()
            }
        }
        context.registerReceiver(
            receiver, filter, permission,
            null,  /* main thread */Context.RECEIVER_EXPORTED
        )

        val intent = Intent(uuid)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags).getIntentSender()
    }

    private fun getShortcutBuilder(context: App?): ShortcutInfo.Builder {
        val builder = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel(context?.getString(R.string.app_name).toString())
            .setIntent(getLaunchIntent(context)!!)
            .setIcon(
                Icon.createWithAdaptiveBitmap(
                    getBitmap(
                        context,
                        R.drawable.ic_launcher
                    )
                )
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val activity = ComponentName(
                context?.getPackageName().toString(),
                "android.app.AppDetailsActivity"
            )
            builder.setActivity(activity)
        }
        return builder
    }

    @Throws(RuntimeException::class)
    fun isRequestPinShortcutSupported(context: Context): Boolean {
        val sm = context.getSystemService<ShortcutManager>(ShortcutManager::class.java)
        return sm.isRequestPinShortcutSupported()
    }

    fun requestPinLaunchShortcut(afterPinned: Runnable?): Boolean {
        if (!App.isParasitic) throw RuntimeException()
        val context = App.instance
        val sm = context?.getSystemService<ShortcutManager>(ShortcutManager::class.java)
        if (sm?.isRequestPinShortcutSupported() == true) return false
        return sm?.requestPinShortcut(
            getShortcutBuilder(context).build(),
            registerReceiver(context, afterPinned)
        ) == true
    }

    fun updateShortcut(): Boolean {
        if (!isLaunchShortcutPinned) return false
        val context = App.instance
        val sm = context?.getSystemService<ShortcutManager>(ShortcutManager::class.java)
        val shortcutInfoList: MutableList<ShortcutInfo?> = ArrayList<ShortcutInfo?>()
        shortcutInfoList.add(getShortcutBuilder(context).build())
        return sm?.updateShortcuts(shortcutInfoList) == true
    }

    val isLaunchShortcutPinned: Boolean
        get() {
            val context = App.instance
            val sm =
                context?.getSystemService<ShortcutManager>(ShortcutManager::class.java)
            for (info in sm?.getPinnedShortcuts()!!) {
                if (SHORTCUT_ID == info.getId()) {
                    return true
                }
            }
            return false
        }
}
