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
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PackageManagerClassifierTest {

    private val launcherApps: LauncherApps = mock()
    private val context: Context = mock {
        on { getSystemService(LauncherApps::class.java) } doReturn launcherApps
    }

    private val activityInfoMap = mutableMapOf<ComponentName, LauncherActivityInfo>()
    private lateinit var classifier: PackageManagerClassifier

    @Before
    fun setup() {
        classifier = PackageManagerClassifier(context)

        whenever(launcherApps.resolveActivity(any(), any())).thenAnswer { invocation ->
            val intent = invocation.arguments[0] as Intent
            activityInfoMap[intent.component]
        }

        // Mock string resources
        whenever(context.getString(R.string.topic_category_games)).thenReturn("Games")
        whenever(context.getString(R.string.topic_category_social)).thenReturn("Social")
        whenever(context.getString(R.string.topic_category_productivity)).thenReturn("Productivity")
    }

    @Test
    fun classifyItemsByCategory() = runBlocking {
        val item1 = createItem(1, "pkg1")
        val item2 = createItem(2, "pkg2")
        val item3 = createItem(3, "pkg3")

        mockAppCategory(item1, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item2, ApplicationInfo.CATEGORY_SOCIAL)
        mockAppCategory(item3, ApplicationInfo.CATEGORY_PRODUCTIVITY)

        val result = classifier.classify(listOf(item1, item2, item3))

        assertEquals(3, result.size)
        assertEquals("Games", result.find { it.itemInfo.id == 1 }?.topic)
        assertEquals("Social", result.find { it.itemInfo.id == 2 }?.topic)
        assertEquals("Productivity", result.find { it.itemInfo.id == 3 }?.topic)
    }

    @Test
    fun filterOutItemsWithoutResolvedActivity() = runBlocking {
        val item = createItem(1, "pkg1")
        // No mock for this item's component

        val result = classifier.classify(listOf(item))

        assertTrue("Items without resolved activity should be ignored", result.isEmpty())
    }

    @Test
    fun filterOutItemsWithUnmappedCategory() = runBlocking {
        val item = createItem(1, "pkg1")
        mockAppCategory(item, ApplicationInfo.CATEGORY_UNDEFINED)

        val result = classifier.classify(listOf(item))

        assertTrue("Items with unmapped categories should be ignored", result.isEmpty())
    }

    @Test
    fun filterOutItemsWithoutIntent() = runBlocking {
        val item = ItemInfo().apply { id = 1 } // No intent

        val result = classifier.classify(listOf(item))

        assertTrue("Items without intent should be ignored", result.isEmpty())
    }

    private fun createItem(id: Int, packageName: String): WorkspaceItemInfo {
        return WorkspaceItemInfo().apply {
            this.id = id
            this.intent =
                Intent(Intent.ACTION_MAIN).setComponent(ComponentName(packageName, "Activity$id"))
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
