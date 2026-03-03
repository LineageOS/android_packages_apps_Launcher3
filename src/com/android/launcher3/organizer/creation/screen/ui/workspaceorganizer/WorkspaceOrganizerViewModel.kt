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

package com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherApplication
import com.android.launcher3.LauncherModel
import com.android.launcher3.concurrent.annotations.LightweightBackgroundContext
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.icons.BitmapRenderer
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.repository.HomeScreenRepository
import com.android.launcher3.model.scheduleTransactionSuspending
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** View model used by the [WorkspaceOrganizerActivity] and its composables. */
class WorkspaceOrganizerViewModel
@Inject
constructor(
    private val launcherModel: LauncherModel,
    private val homeScreenRepository: HomeScreenRepository,
    private val modelWriter: IModelWriter,
    @LightweightBackgroundContext(priority = UI)
    private val lightweightBackgroundContext: CoroutineContext,
) : ViewModel() {
    private val _workspacePages = MutableStateFlow<List<WorkspacePage>>(emptyList())
    val workspacePages: StateFlow<List<WorkspacePage>> = _workspacePages.asStateFlow()
    var workspaceOrganizerState: WorkspaceOrganizerState by
        mutableStateOf(WorkspaceOrganizerState())
        private set

    init {
        viewModelScope.launch { loadPages() }
    }

    /**
     * Loads the workspace pages from the [Launcher]'s workspace.
     *
     * This method captures a representation of the current workspace pages as a bitmap and updates
     * the [_workspacePages] state flow.
     *
     * TODO: Make this more efficient by only loading the pages needed and load/unload pages as the
     *   user scrolls.
     */
    private suspend fun loadPages() {
        val pages =
            withContext(lightweightBackgroundContext) {
                val launcher =
                    Launcher.ACTIVITY_TRACKER.getCreatedContext<Launcher>()
                        ?: return@withContext null

                val workspace = launcher.workspace ?: return@withContext null

                val newPages = mutableListOf<WorkspacePage>()
                for (i in 0 until workspace.pageCount) {
                    val page = workspace.getPageAt(i) as? CellLayout ?: continue
                    if (page.width <= 0 || page.height <= 0) continue

                    // Use hardware-accelerated renderer to avoid "Software rendering doesn't
                    // support hardware bitmaps" error when drawing themed icons.
                    val bitmap =
                        BitmapRenderer.createHardwareBitmap(page.width, page.height) { canvas ->
                            page.draw(canvas)
                        }
                    // Copy to software bitmap to ensure compatibility when drawing in other
                    // contexts.
                    val swBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    newPages.add(WorkspacePage(swBitmap, workspace.screenOrder[i]))
                }
                newPages
            }
        pages?.let { _workspacePages.value = it }
    }

    /**
     * Moves the currently selected workspace page by the given [delta].
     *
     * @param delta The number of positions to move the page (positive for right, negative for
     *   left).
     */
    fun moveSelectedWorkspacePage(delta: Int) {
        val originalPages = _workspacePages.value
        val pages = originalPages.toMutableList()
        val currentIndex = workspaceOrganizerState.selectedPage
        val targetIndex = currentIndex + delta
        if (targetIndex in pages.indices) {
            val orderedScreenIds = originalPages.map { it.screenId }
            val page = pages.removeAt(currentIndex)
            pages.add(targetIndex, page)

            // Optimistically update screenIds.
            for (i in pages.indices) {
                pages[i] = pages[i].copy(screenId = orderedScreenIds[i])
            }

            _workspacePages.value = pages
            workspaceOrganizerState = workspaceOrganizerState.copy(selectedPage = targetIndex)

            viewModelScope.launch {
                try {
                    withContext(lightweightBackgroundContext) {
                        modelWriter.scheduleTransactionSuspending {
                            val context =
                                WorkspaceOrganizerTransactionContext(it, homeScreenRepository)
                            context.moveScreen(currentIndex, targetIndex, orderedScreenIds)
                        }
                    }
                    launcherModel.reloadIfActive("workspace-organizer-move-screen")
                } catch (e: Exception) {
                    _workspacePages.value = originalPages
                    workspaceOrganizerState =
                        workspaceOrganizerState.copy(selectedPage = currentIndex)
                }
            }
        }
    }

    /**
     * Removes the currently selected workspace page from the workspace.
     *
     * This operation deletes the screen and all its items from the database.
     */
    fun removeSelectedWorkspacePage() {
        val originalPages = _workspacePages.value
        val pages = originalPages.toMutableList()
        val currentIndex = workspaceOrganizerState.selectedPage
        if (currentIndex in pages.indices) {
            val page = pages.removeAt(currentIndex)

            // Optimistically shift screenIds of subsequent pages down to match deleteScreen logic.
            for (i in currentIndex until pages.size) {
                pages[i] = pages[i].copy(screenId = pages[i].screenId - 1)
            }

            _workspacePages.value = pages
            val newSelectedPage =
                if (pages.isEmpty()) 0 else currentIndex.coerceAtMost(pages.size - 1)
            workspaceOrganizerState = workspaceOrganizerState.copy(selectedPage = newSelectedPage)

            viewModelScope.launch {
                try {
                    withContext(lightweightBackgroundContext) {
                        modelWriter.scheduleTransactionSuspending {
                            val context =
                                WorkspaceOrganizerTransactionContext(it, homeScreenRepository)
                            context.deleteScreen(page.screenId)
                        }
                    }
                    launcherModel.reloadIfActive("workspace-organizer-remove-pages")
                } catch (e: Exception) {
                    _workspacePages.value = originalPages
                    workspaceOrganizerState =
                        workspaceOrganizerState.copy(selectedPage = currentIndex)
                }
            }
        }
    }

    /**
     * Sets the currently selected workspace page to the given [index].
     *
     * @param index The index of the page to select.
     */
    fun setSelectedWorkspacePage(index: Int) {
        val size = workspacePages.value.size
        if (size == 0) return
        workspaceOrganizerState =
            workspaceOrganizerState.copy(selectedPage = index.coerceIn(0, size - 1))
    }

    companion object {
        /** Returns a [ViewModelProvider.Factory] for [WorkspaceOrganizerViewModel]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as LauncherApplication
                val appComponent = application.appComponent
                val launcher = requireNotNull(Launcher.ACTIVITY_TRACKER.getCreatedContext())
                WorkspaceOrganizerViewModel(
                    launcherModel = appComponent.launcherAppState.model,
                    homeScreenRepository = appComponent.homeScreenRepository,
                    modelWriter = launcher.modelWriter,
                    lightweightBackgroundContext =
                        appComponent.productionDispatchers.lightweightBackgroundUiDispatcher,
                )
            }
        }
    }
}
