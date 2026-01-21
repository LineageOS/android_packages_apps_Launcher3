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

package com.android.quickstep.recents.domain.usecase

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.domain.model.FullscreenPosition
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Test for [GetDesktopTaskFullscreenPositionUseCase] */
@RunWith(AndroidJUnit4::class)
class GetDesktopTaskFullscreenPositionUseCaseTest {

    private val useCase: GetDesktopTaskFullscreenPositionUseCase =
        GetDesktopTaskFullscreenPositionUseCase()

    @Test
    fun test_emptyTasks_returnsEmptyList() {
        val tasks = emptyList<Task>()

        val result = useCase.invoke(tasks)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_singleTask_returnsNotObscured() {
        val singleTask = createTask(id = 1, appBounds = Rect(0, 0, 10, 10))
        val tasks = listOf(singleTask)

        val result = useCase.invoke(tasks)

        assertThat(result)
            .containsExactly(
                FullscreenPosition(taskId = 1, bounds = Rect(0, 0, 10, 10), isObscured = false)
            )
    }

    @Test
    fun test_smallerTaskAboveLargerTask_returnsNoneObscured() {
        val task1 = createTask(id = 1, appBounds = Rect(50, 50, 150, 150))
        val task2 = createTask(id = 2, appBounds = Rect(0, 0, 200, 200))
        // Tasks at the front of the list are higher in the z-order.
        val tasks = listOf(task1, task2)

        val result = useCase.invoke(tasks)

        assertThat(result)
            .containsExactly(
                FullscreenPosition(taskId = 1, bounds = Rect(50, 50, 150, 150), isObscured = false),
                FullscreenPosition(taskId = 2, bounds = Rect(0, 0, 200, 200), isObscured = false),
            )
            .inOrder()
    }

    @Test
    fun test_largerTaskAboveSmallerTask_returnsSmallerObscured() {
        val task1 = createTask(id = 1, appBounds = Rect(0, 0, 200, 200))
        val task2 = createTask(id = 2, appBounds = Rect(50, 50, 150, 150))
        // Tasks at the front of the list are higher in the z-order.
        val tasks = listOf(task1, task2)

        val result = useCase.invoke(tasks)

        assertThat(result)
            .containsExactly(
                FullscreenPosition(taskId = 1, bounds = Rect(0, 0, 200, 200), isObscured = false),
                FullscreenPosition(taskId = 2, bounds = Rect(50, 50, 150, 150), isObscured = true),
            )
            .inOrder()
    }

    @Test
    fun test_minimizedTask_returnsObscured() {
        // Task 1 completely envelops Task 2, but is minimized.
        val task1 = createTask(id = 1, appBounds = Rect(0, 0, 200, 200), isMinimized = true)
        val task2 = createTask(id = 2, appBounds = Rect(50, 50, 150, 150))
        // Tasks at the front of the list are higher in the z-order.
        val tasks = listOf(task1, task2)

        val result = useCase.invoke(tasks)

        assertThat(result)
            .containsExactly(
                FullscreenPosition(taskId = 1, bounds = Rect(0, 0, 200, 200), isObscured = true),
                FullscreenPosition(taskId = 2, bounds = Rect(50, 50, 150, 150), isObscured = false),
            )
            .inOrder()
    }

    @Test
    fun test_successiveOverlappingTasks_returnsMultipleObscured() {
        val task1 = createTask(id = 1, appBounds = Rect(0, 0, 300, 300))
        val task2 = createTask(id = 2, appBounds = Rect(0, 0, 200, 200))
        val task3 = createTask(id = 3, appBounds = Rect(0, 0, 100, 100))
        // Tasks at the front of the list are higher in the z-order.
        val tasks = listOf(task1, task2, task3)

        val result = useCase.invoke(tasks)

        assertThat(result)
            .containsExactly(
                FullscreenPosition(taskId = 1, bounds = Rect(0, 0, 300, 300), isObscured = false),
                FullscreenPosition(taskId = 2, bounds = Rect(0, 0, 200, 200), isObscured = true),
                FullscreenPosition(taskId = 3, bounds = Rect(0, 0, 100, 100), isObscured = true),
            )
            .inOrder()
    }

    @Test
    fun test_partiallyOverlappingTasks_returnsNoneObscured() {
        val task1 = createTask(id = 1, appBounds = Rect(0, 0, 100, 100))
        val task2 = createTask(id = 2, appBounds = Rect(50, 0, 150, 100))
        // Tasks at the front of the list are higher in the z-order.
        val tasks = listOf(task1, task2)

        val result = useCase.invoke(tasks)

        assertThat(result)
            .containsExactly(
                FullscreenPosition(taskId = 1, bounds = Rect(0, 0, 100, 100), isObscured = false),
                FullscreenPosition(taskId = 2, bounds = Rect(50, 0, 150, 100), isObscured = false),
            )
            .inOrder()
    }

    @Test
    fun test_complexOverlappingRegionObscuresTask_returnsObscured() {
        val obscuredTask = createTask(id = 0, appBounds = Rect(100, 100, 200, 200))

        // Construct a complex region that completely overlaps the obscuredTask.
        val task1 = createTask(id = 1, appBounds = Rect(90, 90, 160, 210))
        val task2 = createTask(id = 2, appBounds = Rect(140, 90, 210, 160))
        val task3 = createTask(id = 3, appBounds = Rect(140, 140, 210, 210))

        // Tasks at the front of the list are higher in the z-order.
        val tasks = listOf(task1, task2, task3, obscuredTask)

        val result = useCase.invoke(tasks)

        assertThat(result)
            .contains(
                FullscreenPosition(taskId = 0, bounds = Rect(100, 100, 200, 200), isObscured = true)
            )
    }

    @Test
    fun test_cornersObscuredButCenterNotObscured_returnsNoneObscured() {
        // Construct a layout where all four corners of a window (task5) are covered, but the center
        // is not.
        val task1 = createTask(id = 1, appBounds = Rect(0, 0, 50, 50))
        val task2 = createTask(id = 2, appBounds = Rect(150, 0, 200, 50))
        val task3 = createTask(id = 3, appBounds = Rect(0, 150, 50, 200))
        val task4 = createTask(id = 4, appBounds = Rect(150, 150, 200, 200))
        val task5 = createTask(id = 5, appBounds = Rect(25, 25, 175, 175))

        // Tasks at the front of the list are higher in the z-order.
        val tasks = listOf(task1, task2, task3, task4, task5)

        val result = useCase.invoke(tasks)

        assertThat(result.last())
            .isEqualTo(
                FullscreenPosition(taskId = 5, bounds = Rect(25, 25, 175, 175), isObscured = false)
            )
    }

    private fun createTask(id: Int, appBounds: Rect, isMinimized: Boolean = false) =
        Task().apply {
            key =
                TaskKey(
                    id,
                    0,
                    Intent(),
                    ComponentName("", ""),
                    /* userId */ 0,
                    /* lastActiveTime */ 0,
                )
            this.appBounds = appBounds
            this.key.numActivities = 1
            this.isMinimized = isMinimized
        }
}
