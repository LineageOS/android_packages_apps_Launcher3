/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.compose

import android.content.Context
import android.view.View
import androidx.compose.ui.platform.ComposeView
import com.android.launcher3.CellLayout
import com.android.launcher3.compose.core.BaseComposeFacade
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widgets.resize.AppWidgetResizeFrameCompose

object ComposeFacade : BaseComposeFacade {
    override fun isComposeAvailable(): Boolean = true

    override fun initComposeView(appContext: Context): View = ComposeView(appContext)

    override fun disposeComposition(view: View) {
        (view as? ComposeView)?.disposeComposition()
    }

    /** Displays a resize frame for the provided widget view. */
    fun showResizeFrame(
        activityContext: ActivityContext,
        widgetView: LauncherAppWidgetHostView,
        cellLayout: CellLayout,
    ) {
        AppWidgetResizeFrameCompose.show(activityContext, widgetView, cellLayout)
    }
}
