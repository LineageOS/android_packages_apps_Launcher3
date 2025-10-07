/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.homescreenfiles

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.UserHandle
import android.util.Log
import android.util.Size
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.cache.BaseIconCache
import com.android.launcher3.icons.cache.CachingLogic
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * Helper class used by [com.android.launcher3.icons.IconCache] and
 * [com.android.launcher3.icons.cache.BaseIconCache] to provide icons for [WorkspaceItemInfo]
 * instances of type [com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE]
 * and [com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER].
 */
object HomeScreenFilesCachingLogic : CachingLogic<HomeScreenFile> {
    override fun getComponent(item: HomeScreenFile): ComponentName {
        // A workaround to generate a unique cache key, as file system items don't point to a
        // concrete stable package.
        return ComponentName(COMPONENT_NAME_PACKAGE, item.uri.toString())
    }

    override fun getUser(item: HomeScreenFile): UserHandle {
        return item.user
    }

    override fun getLabel(item: HomeScreenFile): CharSequence? {
        return item.displayName
    }

    override fun getApplicationInfo(item: HomeScreenFile): ApplicationInfo? {
        return null
    }

    override fun loadIcon(
        context: Context,
        cache: BaseIconCache,
        item: HomeScreenFile,
    ): BitmapInfo {
        cache.iconFactory.use { iconFactory ->
            if (item.mimeType.isNullOrEmpty()) {
                return iconFactory.createBadgedIconBitmap(null)
            }

            // Load thumbnail for images and videos.
            if (supportsThumbnails(item.mimeType)) {
                try {
                    val thumbnail =
                        context.contentResolver.loadThumbnail(
                            item.uri,
                            Size(iconFactory.iconBitmapSize, iconFactory.iconBitmapSize),
                            null,
                        )
                    return iconFactory.createIconBitmap(
                        thumbnail.cropToSquare(),
                        isFullBleed = true,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load thumbnail icon", e)
                }
            }

            // Load generic MIME type icon for other MIME types or as a fallback when loading a
            // thumbnail has failed.
            return kotlin
                .runCatching {
                    context.contentResolver.getTypeInfo(item.mimeType).icon.loadDrawable(context)
                }
                .map { iconFactory.createBadgedIconBitmap(it) }
                .getOrElse {
                    Log.e(TAG, "Failed to load generic icon", it)
                    iconFactory.createBadgedIconBitmap(null)
                }
        }
    }

    override fun getFreshnessIdentifier(item: HomeScreenFile, iconProvider: IconProvider): String? {
        return null
    }

    private fun Bitmap.cropToSquare(): Bitmap {
        val w = this.width
        val h = this.height
        if (w == h) {
            return this
        }
        val size = w.coerceAtMost(h)
        val x = (w - size) / 2
        val y = (h - size) / 2
        val croppedBitmap = Bitmap.createBitmap(this, x, y, size, size)
        this.recycle()
        return croppedBitmap
    }

    private fun supportsThumbnails(mimeType: String) =
        THUMBNAILABLE_MIME_TYPE_PREFIXES.any { mimeType.startsWith(it, ignoreCase = true) }

    private const val COMPONENT_NAME_PACKAGE = "com.android.launcher3.homescreenfiles"
    private const val TAG = "HomeScreenFilesCachingLogic"
    private val THUMBNAILABLE_MIME_TYPE_PREFIXES = listOf("image/", "video/")
}
