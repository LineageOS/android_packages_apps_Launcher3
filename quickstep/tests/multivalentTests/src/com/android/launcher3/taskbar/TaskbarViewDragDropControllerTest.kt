/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.taskbar

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.DropTarget
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.taskbar.rules.AllTaskbarSandboxModules
import com.android.launcher3.taskbar.rules.SandboxParams
import com.android.launcher3.taskbar.rules.TaskbarSandboxComponent
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.IntSparseArrayMap
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TaskbarViewDragDropControllerTest {

    private val modelWriter: ModelWriter = mock()
    private val launcherModel: LauncherModel = mock {
        on { getWriter(any(), any(), any()) } doReturn modelWriter
    }
    private val modelCallbacks: TaskbarModelCallbacks = mock {
        on { hotseatItems } doReturn IntSparseArrayMap()
    }

    @get:Rule(order = 0)
    val context =
        TaskbarWindowSandboxContext.create(
            params =
                SandboxParams(
                    builderBase =
                        DaggerTaskbarViewDragDropControllerComponent.builder()
                            .bindLauncherModel(launcherModel)
                )
        )
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var controller: TaskbarViewDragDropController

    private val itemInfoCaptor = argumentCaptor<ItemInfo>()

    @Test
    fun unpinned_onDrop_deletesItemFromDatabase() {
        controller.setUpCallbacks(modelCallbacks)
        val dragObject = createDragObject(TEST_WORKSPACE_ITEM)

        controller.unpinDropTarget.onDrop(dragObject, null)

        verify(modelWriter).deleteItemFromDatabase(eq(TEST_WORKSPACE_ITEM), any())
    }

    @Test
    fun pinned_onDropWithNewAppInfo_addOrMoveItemInDatabase() {
        val dragObject = createDragObject(TEST_APP)
        controller.setUpCallbacks(modelCallbacks)
        controller.taskbarPinningDropTarget.onDrop(dragObject, null)

        verify(modelWriter)
            .addOrMoveItemInDatabase(
                itemInfoCaptor.capture(),
                eq(CONTAINER_HOTSEAT),
                any(),
                any(),
                any(),
            )
        assertThat(itemInfoCaptor.lastValue.targetComponent).isEqualTo(TEST_APP.componentName)
        assertThat(itemInfoCaptor.lastValue.user).isEqualTo(TEST_APP.user)
    }

    @Test
    fun pinned_onDropWithExistingItem_addOrMoveItemInDatabase() {
        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(1, TEST_WORKSPACE_ITEM)
        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        controller.setUpCallbacks(modelCallbacks)

        val dragObject = createDragObject(TEST_WORKSPACE_ITEM)

        controller.taskbarPinningDropTarget.onDrop(dragObject, null)

        verify(modelWriter)
            .addOrMoveItemInDatabase(
                itemInfoCaptor.capture(),
                eq(CONTAINER_HOTSEAT),
                any(),
                any(),
                any(),
            )
        assertThat(itemInfoCaptor.lastValue.targetComponent)
            .isEqualTo(TEST_WORKSPACE_ITEM.targetComponent)
        assertThat(itemInfoCaptor.lastValue.user).isEqualTo(TEST_WORKSPACE_ITEM.user)
    }

    private fun createDragObject(info: ItemInfo): DropTarget.DragObject {
        val dragObject = DropTarget.DragObject(context)
        dragObject.dragInfo = info
        return dragObject
    }

    private companion object {
        val TEST_APP_COMPONENT = ComponentName("test", "app1")
        val TEST_APP =
            AppInfo().apply {
                componentName = TEST_APP_COMPONENT
                user = UserHandle.of(0)
                intent = Intent().setComponent(TEST_APP_COMPONENT)
                title = "Test App 1"
            }
        val TEST_WORKSPACE_ITEM = TaskbarViewTestUtil.createHotseatWorkspaceItem()
    }
}

@LauncherAppSingleton
@Component(modules = [AllTaskbarSandboxModules::class])
interface TaskbarViewDragDropControllerComponent : TaskbarSandboxComponent {

    @Component.Builder
    interface Builder : TaskbarSandboxComponent.Builder {
        @BindsInstance fun bindLauncherModel(model: LauncherModel): Builder

        override fun build(): TaskbarViewDragDropControllerComponent
    }
}
