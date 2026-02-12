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
import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.CellAndSpan
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
class ScreenGenerationTest {

    private val launcherApps: LauncherApps = mock()
    private val activityInfoMap = mutableMapOf<ComponentName, LauncherActivityInfo>()
    private val context = spy(InstrumentationRegistry.getInstrumentation().targetContext)

    private lateinit var classifier: Classifier
    private lateinit var placer: Placer
    private lateinit var generator: ScreenGenerator

    @Before
    fun setup() {
        whenever(context.getSystemService(LauncherApps::class.java)).thenReturn(launcherApps)
        whenever(launcherApps.resolveActivity(any(), any())).thenAnswer { invocation ->
            val intent = invocation.arguments[0] as Intent
            activityInfoMap[intent.component]
        }

        // Mock string resources
        whenever(context.getString(R.string.topic_category_games)).thenReturn("Games")
        whenever(context.getString(R.string.topic_category_news)).thenReturn("News")
        whenever(context.getString(R.string.topic_category_social)).thenReturn("Social")

        classifier = PackageManagerClassifier(context)
        placer = HeuristicScreenPlacer(Point(4, 5))
    }

    @Test
    fun generateScreenWithSingleTopic() = runBlocking {
        // Setup items
        val item1 = createItem(1, "Game 1")
        val item2 = createItem(2, "Game 2")
        val item3 = createItem(3, "Game 3")
        val item4 = createItem(4, "News 1")
        val items = listOf(item1, item2, item3, item4)

        // Mock LauncherApps behavior for items
        mockAppCategory(item1, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item2, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item3, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item4, ApplicationInfo.CATEGORY_NEWS)

        // Define a template with 3 pre-defined slots
        val templateItems =
            listOf(
                TemplateItem(
                    CellAndSpan(0, 0, 1, 1),
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                ),
                TemplateItem(
                    CellAndSpan(1, 0, 1, 1),
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                ),
                TemplateItem(
                    CellAndSpan(2, 0, 1, 1),
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                ),
            )
        val templates = listOf(Template(Point(4, 5), templateItems))

        generator = ScreenGenerator(classifier, placer, templates, items)

        // Generate for "Games" topic
        val screens = generator.generate(listOf(setOf("Games")))

        assertEquals("Should have 1 screen", 1, screens.size)

        val gridSize = Point(4, 5)
        val expected =
            listOf(
                CellAndSpan(0, 0, 1, 1) to 1, // Game 1
                CellAndSpan(1, 0, 1, 1) to 2, // Game 2
                CellAndSpan(2, 0, 1, 1) to 3, // Game 3
            )
        GeneratorTestHelper.verifyLayout(screens[0], gridSize, expected)
    }

    @Test
    fun generateScreenWithMultipleTopics() = runBlocking {
        // Setup items
        val item1 = createItem(1, "Game 1")
        val item2 = createItem(2, "News 1")
        val item3 = createItem(3, "Social 1")
        val items = listOf(item1, item2, item3)

        // Mock LauncherApps behavior for items
        mockAppCategory(item1, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item2, ApplicationInfo.CATEGORY_NEWS)
        mockAppCategory(item3, ApplicationInfo.CATEGORY_SOCIAL)

        // Provide enough slots in the template
        val templateItems =
            listOf(
                TemplateItem(
                    CellAndSpan(0, 0, 1, 1),
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                ),
                TemplateItem(
                    CellAndSpan(1, 0, 1, 1),
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                ),
            )
        val templates = listOf(Template(Point(4, 5), templateItems))
        generator = ScreenGenerator(classifier, placer, templates, items)

        // Generate for "Games" and "Social" topics
        val screens = generator.generate(listOf(setOf("Games", "Social")))

        assertEquals("Should have 1 screen", 1, screens.size)

        val gridSize = Point(4, 5)
        val expected =
            listOf(
                CellAndSpan(0, 0, 1, 1) to 1, // Game 1
                CellAndSpan(1, 0, 1, 1) to 3, // Social 1
            )
        GeneratorTestHelper.verifyLayout(screens[0], gridSize, expected)
    }

    @Test
    fun generateScreenWithWidgets() = runBlocking {
        // Setup items: 2 games and 1 game-related widget
        val item1 = createItem(1, "Game 1")
        val item2 = createItem(2, "Game 2")
        val widget1 =
            createItem(
                3,
                "Game Widget",
                spanX = 2,
                spanY = 2,
                itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET,
            )
        val items = listOf(item1, item2, widget1)

        // Mock LauncherApps behavior for items
        mockAppCategory(item1, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item2, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(widget1, ApplicationInfo.CATEGORY_GAME)

        // Define a template with space for icons and a 2x2 widget
        val templateItems =
            listOf(
                TemplateItem(
                    CellAndSpan(0, 0, 1, 1),
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                ),
                TemplateItem(
                    CellAndSpan(1, 0, 1, 1),
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                ),
                TemplateItem(
                    CellAndSpan(0, 1, 2, 2),
                    LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET,
                ),
            )
        val templates = listOf(Template(Point(4, 5), templateItems))

        generator = ScreenGenerator(classifier, placer, templates, items)

        // Generate for "Games" topic
        val screens = generator.generate(listOf(setOf("Games")))

        assertEquals("Should have 1 screen", 1, screens.size)

        val gridSize = Point(4, 5)
        val expected =
            listOf(
                CellAndSpan(0, 0, 1, 1) to 1, // Game 1
                CellAndSpan(1, 0, 1, 1) to 2, // Game 2
                CellAndSpan(0, 1, 2, 2) to 3, // Game Widget
            )
        GeneratorTestHelper.verifyLayout(screens[0], gridSize, expected)
    }

    @Test
    fun generateMultipleScreensWithDifferentTopics() = runBlocking {
        val item1 = createItem(1, "Game 1")
        val item2 = createItem(2, "News 1")
        val items = listOf(item1, item2)

        mockAppCategory(item1, ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(item2, ApplicationInfo.CATEGORY_NEWS)

        val template =
            Template(
                Point(4, 5),
                listOf(
                    TemplateItem(
                        CellAndSpan(0, 0, 1, 1),
                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    )
                ),
            )
        // Provide two identical templates
        generator = ScreenGenerator(classifier, placer, listOf(template, template), items)

        val screens = generator.generate(listOf(setOf("Games"), setOf("News")))

        assertEquals(2, screens.size)
        assertEquals(1, screens[0].size)
        assertEquals(1, screens[0][0].id) // Game 1
        assertEquals(1, screens[1].size)
        assertEquals(2, screens[1][0].id) // News 1
    }

    private fun createItem(
        id: Int,
        title: String,
        spanX: Int = 1,
        spanY: Int = 1,
        itemType: Int = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
    ): WorkspaceItemInfo {
        val appIntent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("pkg$id", "cls$id"))
        return WorkspaceItemInfo().apply {
            this.id = id
            this.title = title
            this.intent = appIntent
            this.spanX = spanX
            this.spanY = spanY
            this.itemType = itemType
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
