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

package com.android.launcher3.taskbar

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.view.View
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ResolvedTargetInfo
import com.android.launcher3.util.Executors.IMMEDIATE_EXECUTOR
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.TaskView
import com.android.systemui.shared.recents.model.Task
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Wraps [RecentsView] and allow taskbar to post action on mainExecutor since recents is rendered on
 * main thread.
 */
class RecentsViewInteractor(private val recentsView: RecentsView<*, *>) {

    fun hasSameRecentsView(recentsView: RecentsView<*, *>) = this.recentsView === recentsView

    // We can use IMMEDIATE_EXECUTOR if enableTaskbarUiThread() is not turned on because caller
    // is already on main thread.
    private val mainExecutor: Executor =
        if (enableTaskbarUiThread()) MAIN_EXECUTOR else IMMEDIATE_EXECUTOR

    // TODO(b/404636836): return FutureRunnableList to allow caller add Runnable to be executed
    // when recentsView.launchRunningDesktopTaskView() returns a RunnableList
    fun launchRunningDesktopTaskView() = recentsView.launchRunningDesktopTaskView()

    // TODO(b/404636836): pass callback executor param and return SafeClosable
    fun addSideTaskLaunchCallback(callback: RunnableList?) {
        mainExecutor.execute { recentsView.addSideTaskLaunchCallback(callback) }
    }

    // TODO(b/404636836): pass callback executor param and return SafeClosable
    fun setTaskLaunchListener(taskLaunchListener: RecentsView.TaskLaunchListener?) {
        mainExecutor.execute { recentsView.setTaskLaunchListener(taskLaunchListener) }
    }

    // TODO(b/404636836): pass callback executor param and return SafeClosable
    fun setTaskLaunchCancelledRunnable(onTaskLaunchCancelledRunnable: Runnable?) {
        mainExecutor.execute {
            recentsView.setTaskLaunchCancelledRunnable(onTaskLaunchCancelledRunnable)
        }
    }

    // TODO(b/404636836): Pass Consumer<View> to post actions on found task view to main thread.
    fun getTaskViewByTaskId(taskId: Int) = recentsView.getTaskViewByTaskId(taskId)

    fun handleAppPairLaunchInApp(launchingIconView: AppPairIcon, itemInfos: List<ItemInfo>) {
        mainExecutor.execute {
            recentsView.splitSelectController
                ?.appPairsController
                ?.handleAppPairLaunchInApp(launchingIconView, itemInfos)
        }
    }

    fun findLastActiveTasksAndRunCallback(
        resolvedTargetInfos: List<ResolvedTargetInfo>?,
        findExactPairMatch: Boolean,
        callback: Consumer<Array<Task>>,
    ) {
        mainExecutor.execute {
            recentsView.splitSelectController?.findLastActiveTasksAndRunCallback(
                resolvedTargetInfos,
                findExactPairMatch,
                callback,
            )
        }
    }

    fun initiateSplitSelect(taskContainer: SplitConfigurationOptions.SplitSelectSource) {
        mainExecutor.execute { recentsView.initiateSplitSelect(taskContainer) }
    }

    fun confirmSplitSelect(
        containerTaskView: TaskView?,
        task: Task?,
        drawable: Drawable?,
        secondView: View,
        thumbnail: Bitmap?,
        intent: Intent?,
        user: UserHandle?,
        itemInfo: ItemInfo?,
    ) {
        mainExecutor.execute {
            recentsView.confirmSplitSelect(
                containerTaskView,
                task,
                drawable,
                secondView,
                thumbnail,
                intent,
                user,
                itemInfo,
            )
        }
    }

    fun switchToScreenshot(onFinishRunnable: Runnable) {
        mainExecutor.execute { recentsView.switchToScreenshot(onFinishRunnable) }
    }

    fun finishRecentsAnimation(toHome: Boolean, shouldPip: Boolean, onFinishComplete: Runnable?) {
        mainExecutor.execute {
            recentsView.finishRecentsAnimation(toHome, shouldPip, onFinishComplete)
        }
    }

    fun launchAppPair(appPairIcon: AppPairIcon, cuj: Int) {
        mainExecutor.execute {
            recentsView.splitSelectController?.appPairsController?.launchAppPair(appPairIcon, cuj)
        }
    }

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("RecentsUiState.isSplitSelectionActiveRef.value()"),
    )
    fun isSplitSelectionActive() = recentsView.isSplitSelectionActive
}
