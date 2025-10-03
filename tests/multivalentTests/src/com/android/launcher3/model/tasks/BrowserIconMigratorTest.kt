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

package com.android.launcher3.model.tasks

import android.os.Process
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.SerializedItemItem
import com.android.launcher3.model.WorkspaceItemSerializer
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceChangeEvent.AddEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.UpdateEvent
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.ModelTestExtensions.setModelLayout
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

/** Tests for [BrowserIconMigrator] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BrowserIconMigratorTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock lateinit var evaluator: BrowserMigrationConditionEvaluator

    private val browserPkg = LauncherModelHelper.SETTINGS_PACKAGE
    private val targetPkg = LauncherModelHelper.TEST_PACKAGE

    private val appComponent: TestComponent
        get() = context.appComponent as TestComponent

    private val idp: InvariantDeviceProfile
        get() = appComponent.idp

    @Before
    fun setUp() {
        context.initDaggerComponent(
            DaggerBrowserIconMigratorTest_TestComponent.builder().bindEvaluator(evaluator)
        )

        doAnswer {
                var targetItemInfo: ItemInfo? = null
                TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
                    targetItemInfo =
                        appComponent.serializer.decode(
                            SerializedItemItem(
                                packageName = browserPkg,
                                userHandle = Process.myUserHandle(),
                            )
                        )
                }

                Pair(targetPkg, targetItemInfo!!)
            }
            .whenever(evaluator)
            .evaluateSourceAndTarget()
    }

    @Test
    fun testMigration_targetOnHotseat_browserOnHotseat() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atHotseat(1)
                .putApp(targetPkg, null)
                .atHotseat(0)
                .putApp(browserPkg, null)
        )

        val changes = createMigrator().processItems()

        // Nothing was changed
        assertThat(changes).isEmpty()
    }

    @Test
    fun testMigration_targetOnHotseat_browserOnWorkspace0() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atHotseat(3)
                .putApp(targetPkg, null)
                .atWorkspace(0, -1, 0)
                .putApp(browserPkg, null)
        )

        val changes = createMigrator().processItems()

        // Target moved to bottom-right corner and browser moved to browser's location
        assertThat(changes).hasSize(2)
        (changes[0] as UpdateEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(targetPkg)
            assertThat(container).isEqualTo(CONTAINER_DESKTOP)
            assertThat(screenId).isEqualTo(0)
            assertThat(cellX).isEqualTo(idp.numColumns - 1)
            assertThat(cellY).isEqualTo(idp.numRows - 1)
        }
        (changes[1] as UpdateEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(browserPkg)
            assertThat(container).isEqualTo(CONTAINER_HOTSEAT)
            assertThat(screenId).isEqualTo(3)
        }
    }

    @Test
    fun testMigration_targetOnHotseat_browserOnWorkspace1() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atHotseat(3)
                .putApp(targetPkg, null)
                .atWorkspace(0, -1, 1)
                .putApp(browserPkg, null)
        )

        val changes = createMigrator().processItems()

        // Target moved to bottom-right corner and browser moved to browser's location
        assertThat(changes).hasSize(2)
        (changes[0] as UpdateEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(targetPkg)
            assertThat(container).isEqualTo(CONTAINER_DESKTOP)
            assertThat(screenId).isEqualTo(0)
            assertThat(cellX).isEqualTo(idp.numColumns - 1)
            assertThat(cellY).isEqualTo(idp.numRows - 1)
        }
        (changes[1] as UpdateEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(browserPkg)
            assertThat(container).isEqualTo(CONTAINER_HOTSEAT)
            assertThat(screenId).isEqualTo(3)
        }
    }

    @Test
    fun testMigration_targetOnWorkspace1_browserOnWorkspace0() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atWorkspace(0, -1, 1)
                .putApp(targetPkg, null)
                .atWorkspace(1, -1, 0)
                .putApp(browserPkg, null)
        )

        val changes = createMigrator().processItems()

        // Nothing was changed
        assertThat(changes).isEmpty()
    }

    @Test
    fun testMigration_targetOnWorkspace0_browserOnWorkspace0() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atWorkspace(0, -1, 0)
                .putApp(targetPkg, null)
                .atWorkspace(1, -1, 0)
                .putApp(browserPkg, null)
        )

        val changes = createMigrator().processItems()

        // Nothing was changed
        assertThat(changes).isEmpty()
    }

    @Test
    fun testMigration_targetOnWorkspace0_browserOnHotseat() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atWorkspace(1, -1, 0)
                .putApp(targetPkg, null)
                .atHotseat(1)
                .putApp(browserPkg, null)
        )

        val changes = createMigrator().processItems()

        // Nothing was changed
        assertThat(changes).isEmpty()
    }

    @Test
    fun testMigration_targetOnWorkspace0_noBrowser() {
        context.setModelLayout(LauncherLayoutBuilder().atWorkspace(0, 1, 0).putApp(targetPkg, null))

        val changes = createMigrator().processItems()

        // Browser is added at target location and target is moved to bottom-left corner
        assertThat(changes).hasSize(2)
        (changes[0] as UpdateEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(targetPkg)
            assertThat(container).isEqualTo(CONTAINER_DESKTOP)
            assertThat(screenId).isEqualTo(0)
            assertThat(cellX).isEqualTo(idp.numColumns - 1)
            assertThat(cellY).isEqualTo(idp.numRows - 1)
        }
        (changes[1] as AddEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(browserPkg)
            assertThat(container).isEqualTo(CONTAINER_DESKTOP)
            assertThat(screenId).isEqualTo(0)
            assertThat(cellX).isEqualTo(0)
            assertThat(cellY).isEqualTo(1)
        }
    }

    @Test
    fun testMigration_targetOnHotseat_noBrowser() {
        context.setModelLayout(LauncherLayoutBuilder().atHotseat(1).putApp(targetPkg, null))

        val changes = createMigrator().processItems()

        // Browser is added at target location and target is moved to bottom-left corner
        assertThat(changes).hasSize(2)
        (changes[0] as UpdateEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(targetPkg)
            assertThat(container).isEqualTo(CONTAINER_DESKTOP)
            assertThat(screenId).isEqualTo(0)
            assertThat(cellX).isEqualTo(idp.numColumns - 1)
            assertThat(cellY).isEqualTo(idp.numRows - 1)
        }
        (changes[1] as AddEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(browserPkg)
            assertThat(container).isEqualTo(CONTAINER_HOTSEAT)
            assertThat(screenId).isEqualTo(1)
        }
    }

    @Test
    fun testMigration_targetOnWorkspace1_noBrowser() {
        context.setModelLayout(LauncherLayoutBuilder().atWorkspace(0, 0, 1).putApp(targetPkg, null))

        val changes = createMigrator().processItems()

        // Browser is added at second page, target is unchanged
        assertThat(changes).hasSize(1)
        (changes[0] as AddEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(browserPkg)
            assertThat(container).isEqualTo(CONTAINER_DESKTOP)
            assertThat(screenId).isEqualTo(1)
        }
    }

    @Test
    fun testMigration_noTarget_noBrowser() {
        context.setModelLayout(LauncherLayoutBuilder().atWorkspace(0, 0, 1).putApp(targetPkg, null))

        val changes = createMigrator().processItems()

        // Browser is added at second page, target is unchanged
        assertThat(changes).hasSize(1)
        (changes[0] as AddEvent).items[0].apply {
            assertThat(targetPackage).isEqualTo(browserPkg)
            assertThat(container).isEqualTo(CONTAINER_DESKTOP)
            assertThat(screenId).isEqualTo(1)
        }
    }

    private fun createMigrator(): BrowserIconMigrator =
        appComponent.migratorFactory.createBrowserIconMigrator(
            context.appComponent.testableModelState.homeRepo.workspaceState.value
        )

    @LauncherAppSingleton
    @Component(modules = [AllModulesForTest::class])
    interface TestComponent : LauncherAppComponent {

        val migratorFactory: BrowserIconMigratorFactory
        val serializer: WorkspaceItemSerializer

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindEvaluator(evaluator: BrowserMigrationConditionEvaluator): Builder

            override fun build(): TestComponent
        }
    }
}
