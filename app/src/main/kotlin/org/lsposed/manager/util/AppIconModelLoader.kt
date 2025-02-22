/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lsposed.manager.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.Px
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import me.zhanghai.android.appiconloader.AppIconLoader
import org.lsposed.manager.App

class AppIconModelLoader private constructor(
    @Px iconSize: Int, shrinkNonAdaptiveIcons: Boolean,
    context: Context
) : ModelLoader<PackageInfo?, Bitmap?> {
    private val mLoader: AppIconLoader
    private val mContext: Context

    init {
        mLoader = AppIconLoader(iconSize, shrinkNonAdaptiveIcons, context)
        mContext = context
    }

    override fun handles(model: PackageInfo): Boolean {
        return true
    }

    override fun buildLoadData(
        model: PackageInfo, width: Int, height: Int,
        options: Options
    ): LoadData<Bitmap?>? {
        val warpApplicationInfo = ApplicationInfo(model.applicationInfo)
        warpApplicationInfo.uid = warpApplicationInfo.uid % App.PER_USER_RANGE
        val warpPackageInfo = PackageInfo()
        warpPackageInfo.applicationInfo = warpApplicationInfo
        warpPackageInfo.versionCode = model.versionCode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            warpPackageInfo.setLongVersionCode(model.getLongVersionCode())
        }
        return LoadData<Bitmap?>(
            ObjectKey(AppIconLoader.getIconKey(warpPackageInfo, mContext)),
            Fetcher(mLoader, warpApplicationInfo)
        )
    }

    private class Fetcher(
        private val mLoader: AppIconLoader,
        private val mApplicationInfo: ApplicationInfo
    ) : DataFetcher<Bitmap?> {
        override fun loadData(
            priority: Priority,
            callback: DataFetcher.DataCallback<in Bitmap?>
        ) {
            try {
                val icon = mLoader.loadIcon(mApplicationInfo)
                callback.onDataReady(icon)
            } catch (e: Exception) {
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() {
        }

        override fun cancel() {
        }

        override fun getDataClass(): Class<Bitmap?> {
            return Bitmap::class.java as Class<Bitmap?>
        }

        override fun getDataSource(): DataSource {
            return DataSource.LOCAL
        }
    }

    class Factory(
        @field:Px @param:Px private val mIconSize: Int,
        private val mShrinkNonAdaptiveIcons: Boolean,
        context: Context
    ) : ModelLoaderFactory<PackageInfo?, Bitmap?> {
        private val mContext: Context

        init {
            mContext = context.getApplicationContext()
        }

        override fun build(
            multiFactory: MultiModelLoaderFactory
        ): ModelLoader<PackageInfo?, Bitmap?> {
            return AppIconModelLoader(mIconSize, mShrinkNonAdaptiveIcons, mContext)
        }

        override fun teardown() {
        }
    }
}
