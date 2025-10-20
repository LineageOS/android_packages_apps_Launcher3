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

import android.content.ContentResolver
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.icons.cache.BaseIconCache
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class HomeScreenFilesCachingLogicTest {
    @get:Rule val mockito = MockitoJUnit.rule()
    @Mock private lateinit var context: Context
    @Mock private lateinit var contentResolver: ContentResolver
    @Mock private lateinit var baseIconCache: BaseIconCache
    @Mock private lateinit var launcherIcons: LauncherIcons

    @Before
    fun setUp() {
        whenever(context.contentResolver).thenReturn(contentResolver)
        whenever(baseIconCache.iconFactory).thenReturn(launcherIcons)
    }

    @Test
    fun getComponent() {
        val hsf =
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "image/png",
                isDirectory = false,
                user = Process.myUserHandle(),
            )
        val component = HomeScreenFilesCachingLogic.getComponent(hsf)
        assertThat(component.packageName).isEqualTo("com.android.launcher3.homescreenfiles")
        assertThat(component.className).isEqualTo("content://media/external_primary/file/1")
    }

    @Test
    fun testGetImageThumbnailIcon() {
        testGetThumbnailIcon("image/png")
    }

    @Test
    fun testGetVideoThumbnailIcon() {
        testGetThumbnailIcon("video/mp4")
    }

    private fun testGetThumbnailIcon(mimeType: String) {
        val hsf =
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = mimeType,
                isDirectory = false,
                user = Process.myUserHandle(),
            )
        whenever(contentResolver.loadThumbnail(eq(hsf.uri), any(), isNull()))
            .thenReturn(BitmapInfo.LOW_RES_ICON)
        whenever(launcherIcons.createIconBitmap(any(), eq(true)))
            .thenReturn(BitmapInfo.LOW_RES_INFO)

        val icon = HomeScreenFilesCachingLogic.loadIcon(context, baseIconCache, hsf)
        assertThat(icon).isEqualTo(BitmapInfo.LOW_RES_INFO)
    }

    @Test
    fun testGetGenericMimeTypeIcon() {
        val hsf =
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "application/pdf",
                isDirectory = false,
                user = Process.myUserHandle(),
            )
        whenever(contentResolver.getTypeInfo(eq("application/pdf")))
            .thenReturn(ContentResolver.MimeTypeInfo(mock<Icon>(), "label", "contentDescription"))
        whenever(launcherIcons.createBadgedIconBitmap(anyOrNull(), any()))
            .thenReturn(BitmapInfo.LOW_RES_INFO)

        val icon = HomeScreenFilesCachingLogic.loadIcon(context, baseIconCache, hsf)
        assertThat(icon).isEqualTo(BitmapInfo.LOW_RES_INFO)
    }

    @Test
    fun testGetGenericMimeTypeIconWhenThumbnailFails() {
        val hsf =
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "image/png",
                isDirectory = false,
                user = Process.myUserHandle(),
            )
        whenever(contentResolver.loadThumbnail(eq(hsf.uri), any(), isNull()))
            .thenThrow(IOException("test"))
        whenever(contentResolver.getTypeInfo(eq("image/png")))
            .thenReturn(ContentResolver.MimeTypeInfo(mock<Icon>(), "label", "contentDescription"))
        whenever(launcherIcons.createBadgedIconBitmap(anyOrNull(), any()))
            .thenReturn(BitmapInfo.LOW_RES_INFO)

        val icon = HomeScreenFilesCachingLogic.loadIcon(context, baseIconCache, hsf)
        assertThat(icon).isEqualTo(BitmapInfo.LOW_RES_INFO)
    }
}
