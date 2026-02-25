/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.organizer.generator

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class FolderGenerationTest {

    private val launcherApps: LauncherApps = mock()
    private val activityInfoMap = mutableMapOf<ComponentName, LauncherActivityInfo>()
    private val context = spy(InstrumentationRegistry.getInstrumentation().targetContext)

    private lateinit var classifier: Classifier
    private lateinit var placer: FolderPlacer
    private lateinit var generator: FolderGenerator

    @Before
    fun setup() {
        whenever(context.getSystemService(LauncherApps::class.java)).thenReturn(launcherApps)
        whenever(launcherApps.resolveActivity(any(), any())).thenAnswer { invocation ->
            val intent = invocation.arguments[0] as Intent
            activityInfoMap[intent.component]
        }

        whenever(context.getString(R.string.topic_category_games)).thenReturn("Games")
        whenever(context.getString(R.string.topic_category_news)).thenReturn("News")

        classifier = PackageManagerClassifier(context)
        placer = FolderPlacer()
    }

    @Test
    fun generateFolderWithSingleTopic() = runBlocking {
        val item1 = createItem(1, "Game 1")
        val item2 = createItem(2, "Game 2")
        val item3 = createItem(3, "News 1")
        val items = listOf(item1, item2, item3)

        mockAppCategory(item1, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item2, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item3, ApplicationInfo.CATEGORY_NEWS)

        generator = FolderGenerator(classifier, placer, items)

        val folders = generator.generate(listOf(setOf("Games")))

        assertEquals(1, folders.size)
        val folderItems = folders[0]
        assertEquals(2, folderItems.size)
        assertEquals(1, folderItems[0].id)
        assertEquals(0, folderItems[0].rank)
        assertEquals(2, folderItems[1].id)
        assertEquals(1, folderItems[1].rank)
    }

    @Test
    fun generateFolderWithMultipleTopics() = runBlocking {
        val item1 = createItem(1, "Game 1")
        val item2 = createItem(2, "News 1")
        val items = listOf(item1, item2)

        mockAppCategory(item1, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item2, ApplicationInfo.CATEGORY_NEWS)

        generator = FolderGenerator(classifier, placer, items)

        val folders = generator.generate(listOf(setOf("Games", "News")))

        assertEquals(1, folders.size)
        val folderItems = folders[0]
        assertEquals(2, folderItems.size)
    }

    @Test
    fun generateMultipleFoldersWithDifferentTopics() = runBlocking {
        val item1 = createItem(1, "Game 1")
        val item2 = createItem(2, "News 1")
        val items = listOf(item1, item2)

        mockAppCategory(item1, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item2, ApplicationInfo.CATEGORY_NEWS)

        generator = FolderGenerator(classifier, placer, items)

        val folders = generator.generate(listOf(setOf("Games"), setOf("News")))

        assertEquals(2, folders.size)
        assertEquals(1, folders[0].size)
        assertEquals(1, folders[0][0].id) // Game 1
        assertEquals(1, folders[1].size)
        assertEquals(2, folders[1][0].id) // News 1
    }

    private fun createItem(id: Int, title: String): WorkspaceItemInfo {
        val appIntent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("pkg$id", "cls$id"))
        return WorkspaceItemInfo().apply {
            this.id = id
            this.title = title
            this.intent = appIntent
            this.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
        }
    }

    private fun mockAppCategory(item: ItemInfo, category: Int) {
        val applicationInfo =
            ApplicationInfo().apply {
                this.category = category
                this.packageName = item.getTargetPackage()
            }
        val launcherActivityInfo: LauncherActivityInfo = mock {
            on { getApplicationInfo() } doReturn applicationInfo
        }
        item.intent?.component?.let { activityInfoMap[it] = launcherActivityInfo }
    }
}
