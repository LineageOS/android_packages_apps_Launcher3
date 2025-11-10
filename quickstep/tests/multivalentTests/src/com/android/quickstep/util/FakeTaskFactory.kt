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

package com.android.quickstep.util

import android.app.ActivityManager
import android.app.ActivityManager.TaskDescription
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.ComponentName
import android.view.Display.DEFAULT_DISPLAY
import com.android.launcher3.model.data.AppInfo

object FakeTaskFactory {

    @JvmOverloads
    @JvmStatic
    fun newTaskInfo(
        taskId: Int,
        componentName: ComponentName = ComponentName("test", "test"),
        displayId: Int = DEFAULT_DISPLAY,
        windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
        title: String = "Test",
    ) =
        ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.displayId = displayId
            this.baseIntent = AppInfo.makeLaunchIntent(componentName)
            this.baseActivity = componentName
            this.origActivity = componentName
            this.realActivity = componentName
            this.topActivity = componentName
            this.taskDescription = TaskDescription(title)
            this.configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
            this.configuration.windowConfiguration.windowingMode = windowingMode
        }
}
