/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep

import android.content.Context
import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.R
import com.android.launcher3.util.TestDispatcherProvider
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.util.TaskKeyCache
import com.android.systemui.shared.recents.model.ThumbnailData
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class TaskThumbnailCacheTest {
    private val resource = mock<Resources>()

    private val context = mock<Context>().apply { whenever(resources).thenReturn(resource) }

    private val taskKeyCache = mock<TaskKeyCache<ThumbnailData>>()

    private val testDispatcherProvider: DispatcherProvider =
        TestDispatcherProvider(UnconfinedTestDispatcher(scheduler = null, name = null))

    @Test
    fun increaseCacheSize() {
        // Mock a cache size increase from 3 to 8
        whenever(taskKeyCache.getMaxSize()).thenReturn(3)
        whenever(resource.getInteger((R.integer.recentsThumbnailCacheSize))).thenReturn(8)
        val thumbnailCache =
            TaskThumbnailCache(context, mock<Executor>(), taskKeyCache, testDispatcherProvider)

        // Preload is needed when increasing size
        assertTrue(thumbnailCache.updateCacheSizeAndRemoveExcess())
        verify(taskKeyCache, times(1)).updateCacheSizeAndRemoveExcess(8)
    }

    @Test
    fun decreaseCacheSize() {
        // Mock a cache size decrease from 8 to 3
        whenever(taskKeyCache.getMaxSize()).thenReturn(8)
        whenever(resource.getInteger((R.integer.recentsThumbnailCacheSize))).thenReturn(3)
        val thumbnailCache =
            TaskThumbnailCache(context, mock<Executor>(), taskKeyCache, testDispatcherProvider)
        // Preload is not needed when decreasing size
        assertFalse(thumbnailCache.updateCacheSizeAndRemoveExcess())
        verify(taskKeyCache, times(1)).updateCacheSizeAndRemoveExcess(3)
    }

    @Test
    fun keepSameCacheSize() {
        whenever(taskKeyCache.getMaxSize()).thenReturn(3)
        whenever(resource.getInteger((R.integer.recentsThumbnailCacheSize))).thenReturn(3)
        val thumbnailCache =
            TaskThumbnailCache(context, mock<Executor>(), taskKeyCache, testDispatcherProvider)
        // Preload is not needed when it has the same cache size
        assertFalse(thumbnailCache.updateCacheSizeAndRemoveExcess())
        verify(taskKeyCache, never()).updateCacheSizeAndRemoveExcess(anyInt())
    }
}
