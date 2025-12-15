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

import android.graphics.Rect
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DropTarget
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.ArrowPopup.CLOSE_DURATION_U
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.rules.AllTaskbarSandboxModules
import com.android.launcher3.taskbar.rules.SandboxParams
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarSandboxComponent
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.TestUtil.getOnTaskbarUiThread
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TaskbarViewDragDropControllerTest {
    private val TEST_APP = TaskbarViewTestUtil.createAppInfo(0)
    private val TEST_WORKSPACE_ITEM = TaskbarViewTestUtil.createHotseatWorkspaceItem(1)

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
    @get:Rule(order = 1) val animatorTestRule = TaskbarAnimatorTestRule(this)
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    private val activityContext
        get() = taskbarUnitTestRule.activityContext

    @InjectController lateinit var taskbarViewController: TaskbarViewController
    @InjectController lateinit var taskbarDragController: TaskbarDragController
    @InjectController lateinit var taskbarViewDragDropController: TaskbarViewDragDropController

    private val itemInfoCaptor = argumentCaptor<ItemInfo>()

    private val overflowIconRect = Rect(0, 0, 20, 20)

    @Before
    fun setup() {
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)
    }

    @Test
    fun unpinned_onDrop_deletesItemFromDatabase() {
        val dragObject = createDragObject(TEST_WORKSPACE_ITEM)

        taskbarViewDragDropController.unpinDropTarget.onDrop(dragObject, null)

        verify(modelWriter).deleteItemFromDatabase(eq(TEST_WORKSPACE_ITEM), any())
    }

    @Test
    fun pinned_onDropWithNewAppInfo_addOrMoveItemInDatabase() {
        val dragObject = createDragObject(TEST_APP)
        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

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

        val dragObject = createDragObject(TEST_WORKSPACE_ITEM)

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

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

    @Test
    fun pinned_onDrop_reorderLeftToRight_shiftsItemsLeft() {
        // Setup: Items [A(0), B(1), C(2)]
        val itemA = createHotseatItem(0)
        val itemB = createHotseatItem(1)
        val itemC = createHotseatItem(2)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(2, itemC)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag A (Index 0) to Index 2 (Where C is)
        val dragObject = createDragObject(itemA)
        taskbarViewDragDropController.targetPinIndex = 2

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemB), eq(CONTAINER_HOTSEAT), eq(0), eq(0), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemC), eq(CONTAINER_HOTSEAT), eq(1), eq(1), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemA), eq(CONTAINER_HOTSEAT), eq(2), eq(2), any())
    }

    @Test
    fun pinned_onDrop_reorderLeftToRight_shiftStopsAtEmptyItems() {
        // Setup: Items [A(0), B(1), C(3), D(4)]
        val itemA = createHotseatItem(0)
        val itemB = createHotseatItem(1)
        val itemC = createHotseatItem(3)
        val itemD = createHotseatItem(4)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(2, itemC)
        hotseatInfos.append(3, itemD)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag A (Index 0) to Index 3 (Where D is)
        val dragObject = createDragObject(itemA)
        taskbarViewDragDropController.targetPinIndex = 3

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemC), eq(CONTAINER_HOTSEAT), eq(2), eq(2), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemD), eq(CONTAINER_HOTSEAT), eq(3), eq(3), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemA), eq(CONTAINER_HOTSEAT), eq(4), eq(4), any())
        verify(modelWriter, never())
            .addOrMoveItemInDatabase(eq(itemB), eq(CONTAINER_HOTSEAT), any(), any(), any())
    }

    @Test
    fun pinned_onDrop_reorderRightToLeft_shiftsItemsRight() {
        // Setup: Items [A(0), B(1), C(2)]
        val itemA = createHotseatItem(0, 0)
        val itemB = createHotseatItem(1, 1)
        val itemC = createHotseatItem(2, 2)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(2, itemC)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag C (Index 2) to Index 0 (Where A is)
        val dragObject = createDragObject(itemC)
        taskbarViewDragDropController.targetPinIndex = 0

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemA), eq(CONTAINER_HOTSEAT), eq(1), eq(1), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemB), eq(CONTAINER_HOTSEAT), eq(2), eq(2), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemC), eq(CONTAINER_HOTSEAT), eq(0), eq(0), any())
    }

    @Test
    fun pinned_onDrop_reorderRightToLeft_shiftsStopAtEmptyItems() {
        // Setup: Items [A(0), B(1), C(3), D(4)]
        val itemA = createHotseatItem(0, 0)
        val itemB = createHotseatItem(1, 1)
        val itemC = createHotseatItem(3, 2)
        val itemD = createHotseatItem(4, 3)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(2, itemC)
        hotseatInfos.append(3, itemD)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag D (Index 3) to Index 0 (Where A is)
        val dragObject = createDragObject(itemD)
        taskbarViewDragDropController.targetPinIndex = 0

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemA), eq(CONTAINER_HOTSEAT), eq(1), eq(1), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemB), eq(CONTAINER_HOTSEAT), eq(2), eq(2), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemD), eq(CONTAINER_HOTSEAT), eq(0), eq(0), any())
        verify(modelWriter, never())
            .addOrMoveItemInDatabase(eq(itemC), eq(CONTAINER_HOTSEAT), any(), any(), any())
    }

    @Test
    fun pinned_onDrop_insertNewItem_shiftsItemsRight() {
        // Setup: Items [A(0), B(1)]
        val itemA = createHotseatItem(0)
        val itemB = createHotseatItem(1)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag New App to Index 0
        val dragObject = createDragObject(TEST_APP)
        taskbarViewDragDropController.targetPinIndex = 0
        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemA), eq(CONTAINER_HOTSEAT), eq(1), eq(1), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemB), eq(CONTAINER_HOTSEAT), eq(2), eq(2), any())

        // Verify New App inserted
        verify(modelWriter)
            .addOrMoveItemInDatabase(
                itemInfoCaptor.capture(),
                eq(CONTAINER_HOTSEAT),
                eq(0),
                eq(0),
                any(),
            )
        assertThat(itemInfoCaptor.lastValue.targetComponent).isEqualTo(TEST_APP.componentName)
    }

    @Test
    fun pinned_onDrop_insertNewItem_shiftsItemsLeft() {
        // Setup: Items [A(1), B(2)]
        val itemA = createHotseatItem(1)
        val itemB = createHotseatItem(2)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag New App to Index 2
        val dragObject = createDragObject(TEST_APP)
        taskbarViewDragDropController.targetPinIndex = 2
        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemA), eq(CONTAINER_HOTSEAT), eq(0), eq(0), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemB), eq(CONTAINER_HOTSEAT), eq(1), eq(1), any())

        // Verify New App inserted
        verify(modelWriter)
            .addOrMoveItemInDatabase(
                itemInfoCaptor.capture(),
                eq(CONTAINER_HOTSEAT),
                eq(2),
                eq(2),
                any(),
            )
        assertThat(itemInfoCaptor.lastValue.targetComponent).isEqualTo(TEST_APP.componentName)
    }

    fun pinned_onDrop_reorderRightToLeft_shiftItemsLeftIfBlankAvailable() {
        // Setup: Items [A(1), B(2), C(4), D(5)]
        val itemA = createHotseatItem(0)
        val itemB = createHotseatItem(1)
        val itemC = createHotseatItem(4)
        val itemD = createHotseatItem(5)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(2, itemC)
        hotseatInfos.append(3, itemD)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag D (Index 3) to Index 1 (Where B is)
        val dragObject = createDragObject(itemD)
        taskbarViewDragDropController.targetPinIndex = 2

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemA), eq(CONTAINER_HOTSEAT), eq(0), eq(0), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemB), eq(CONTAINER_HOTSEAT), eq(1), eq(1), any())
        verify(modelWriter)
            .addOrMoveItemInDatabase(eq(itemD), eq(CONTAINER_HOTSEAT), eq(2), eq(2), any())
        verify(modelWriter, never())
            .addOrMoveItemInDatabase(eq(itemC), eq(CONTAINER_HOTSEAT), any(), any(), any())
    }

    private fun createHotseatItem(screenId: Int, id: Int = 0): ItemInfo {
        val item = TaskbarViewTestUtil.createHotseatWorkspaceItem(id)
        item.screenId = screenId
        return item
    }

    @Test
    fun onDragOver_pointOnOverflowIcon_openOverflowContainer() {
        val dragObject = createDragObject(TEST_APP)
        val overflowIcon = setUpPinnedOverflow()
        dragViewOntoOverflowIconToOpenContainer(dragObject, overflowIcon)

        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()
    }

    @Test
    fun onDragOver_pointNotOnOverflowIcon_closeOverflowContainer() {
        val dragObject = createDragObject(TEST_APP)
        val overflowIcon = setUpPinnedOverflow()
        dragViewOntoOverflowIconToOpenContainer(dragObject, overflowIcon)
        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()

        // Simulate dragging out of the overflow icon.
        dragObject.x = -100
        runOnTaskbarUiThreadSync {
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragOver(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isTrue()
            taskbarViewDragDropController.overflowContainerAlarm.finishAlarm()
            animatorTestRule.advanceTimeBy(CLOSE_DURATION_U.toLong())
        }
        assertThat(taskbarViewController.isOverflowContainerShowing).isFalse()
    }

    @Test
    fun onDragOver_dragEnterOverflow_cancelsCloseAlarm() {
        taskbarViewDragDropController.addOverflowDropTarget(
            taskbarDragController,
            mock<TaskbarViewDragDropController.PinnedAppsContainerDelegate>(),
        )
        val dragObject = createDragObject(TEST_APP)
        val overflowIcon = setUpPinnedOverflow()
        dragViewOntoOverflowIconToOpenContainer(dragObject, overflowIcon)
        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()

        // Then simulate dragging into the overflow container.
        runOnTaskbarUiThreadSync {
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragExit(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isTrue()
            dragViewIntoOverflowContainer(dragObject)
        }

        // Verify the close alarm is cancelled and the overflow container is still opened.
        assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isFalse()
        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()
    }

    @Test
    fun onDragOver_dragExitOverflow_closeOverflowContainer() {
        taskbarViewDragDropController.addOverflowDropTarget(
            taskbarDragController,
            mock<TaskbarViewDragDropController.PinnedAppsContainerDelegate>(),
        )
        val dragObject = createDragObject(TEST_APP)
        val overflowIcon = setUpPinnedOverflow()
        dragViewOntoOverflowIconToOpenContainer(dragObject, overflowIcon)
        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()

        // Simulate dragging into the overflow container and then dragging out from it.
        runOnTaskbarUiThreadSync {
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragExit(dragObject)
            dragViewIntoOverflowContainer(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending())
                .isFalse()
            requireNotNull(taskbarViewDragDropController.overflowPinningDropTarget)
                .onDragExit(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isTrue()
            taskbarViewDragDropController.overflowContainerAlarm.finishAlarm()
            animatorTestRule.advanceTimeBy(CLOSE_DURATION_U.toLong())
        }

        // Verify the close alarm is run and the overflow container is closed.
        assertThat(taskbarViewController.isOverflowContainerShowing).isFalse()
    }

    private fun createDragObject(info: ItemInfo): DropTarget.DragObject {
        val dragObject = DropTarget.DragObject(context)
        dragObject.dragInfo = info
        dragObject.dragView = mock<DragView>()
        return dragObject
    }

    private fun setUpPinnedOverflow(): TaskbarOverflowView {
        val taskbarView = getOnTaskbarUiThread {
            val view = activityContext.dragLayer.findViewById<TaskbarView>(R.id.taskbar_view)
            view.updateItems(
                createHotseatItems(activityContext.deviceProfile.inv.numShownHotseatIcons + 2),
                emptyList(),
                emptyList(),
            )
            view
        }
        assertThat(taskbarView.taskbarPinnedOverflowView).isNotNull()
        val overflowIcon = getOnTaskbarUiThread {
            val overflowIcon = requireNotNull(taskbarView.taskbarPinnedOverflowView)
            overflowIcon.measure(
                MeasureSpec.makeMeasureSpec(overflowIconRect.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(overflowIconRect.height(), MeasureSpec.EXACTLY),
            )
            overflowIcon.layout(
                overflowIconRect.left,
                overflowIconRect.top,
                overflowIconRect.right,
                overflowIconRect.bottom,
            )
            overflowIcon
        }
        return overflowIcon
    }

    private fun mockDragViewDragRegionToViewBounds(dragObject: DropTarget.DragObject, view: View) {
        val iconBounds = Rect()
        activityContext.dragLayer.getDescendantRectRelativeToSelf(view, iconBounds)
        whenever(dragObject.dragView.dragRegion).thenReturn(iconBounds)
    }

    private fun dragViewOntoOverflowIconToOpenContainer(
        dragObject: DropTarget.DragObject,
        overflowIcon: TaskbarOverflowView,
    ) {
        mockDragViewDragRegionToViewBounds(dragObject, overflowIcon)

        // Simulate dragging on the overflow icon to open the container.
        runOnTaskbarUiThreadSync {
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragEnter(dragObject)
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragOver(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isTrue()
            taskbarViewDragDropController.overflowContainerAlarm.finishAlarm()
        }
    }

    private fun dragViewIntoOverflowContainer(dragObject: DropTarget.DragObject) {
        val overflowContainer =
            AbstractFloatingView.getOpenView<OverflownAppsContainerView<*>>(
                activityContext,
                AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
            )
        assertThat(overflowContainer).isNotNull()
        mockDragViewDragRegionToViewBounds(dragObject, overflowContainer)
        requireNotNull(taskbarViewDragDropController.overflowPinningDropTarget)
            .onDragEnter(dragObject)
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
