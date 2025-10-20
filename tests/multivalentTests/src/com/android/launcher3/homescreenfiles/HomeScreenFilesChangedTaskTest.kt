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

package com.android.launcher3.homescreenfiles

import android.content.ContentResolver.NOTIFY_DELETE
import android.content.ContentResolver.NOTIFY_INSERT
import android.content.ContentResolver.NOTIFY_UPDATE
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.Utilities.qsbOnFirstScreen
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.WorkspaceData.MutableWorkspaceData
import com.android.launcher3.model.data.WorkspaceItemCoordinates
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.ReflectionHelpers
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class HomeScreenFilesChangedTaskTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var modelTaskController: ModelTaskController
    @Mock private lateinit var modelWriter: ModelWriter
    @Mock private lateinit var bgDataModel: BgDataModel
    @Mock private lateinit var bitmap: BitmapInfo
    @Mock private lateinit var allAppsList: AllAppsList
    @Mock private lateinit var iconCache: IconCache
    @Mock private lateinit var idp: InvariantDeviceProfile
    @Mock private lateinit var workspaceItemSpaceFinder: WorkspaceItemSpaceFinder

    private val testUri = Uri.parse("content://media/external_primary/file/1")
    private val testFile =
        HomeScreenFile(
            uri = testUri,
            displayName = "file.png",
            mimeType = "image/png",
            isDirectory = false,
            user = Process.myUserHandle(),
        )
    private val wsData = MutableWorkspaceData()

    @Before
    fun setUp() {
        idp.numSearchContainerColumns = 3

        doAnswer {
                it.getArgument<ItemInfoWithIcon>(0).bitmap =
                    this@HomeScreenFilesChangedTaskTest.bitmap
            }
            .whenever(iconCache)
            .getTitleAndIcon(argThat { x -> x.title == testFile.displayName }, any())

        whenever(modelTaskController.getModelWriter()).thenReturn(modelWriter)
        whenever(bgDataModel.updateAndCollectWorkspaceItemInfos(any(), any(), isNull()))
            .thenCallRealMethod()
        val maybeReservesSpaceForQsb: (ArrayList<WorkspaceItemInfo>) -> Boolean = { addItemsFinal ->
            !qsbOnFirstScreen() ||
                addItemsFinal.any {
                    with(it) {
                        cellX == 0 &&
                            cellY == 0 &&
                            container == CONTAINER_DESKTOP &&
                            screenId == WorkspaceLayoutManager.FIRST_SCREEN_ID &&
                            spanX == idp.numSearchContainerColumns &&
                            spanY == 1
                    }
                }
        }
        whenever(
                workspaceItemSpaceFinder.findSpaceForItem(
                    argThat(maybeReservesSpaceForQsb),
                    any(),
                    any(),
                    any(),
                )
            )
            .thenReturn(WorkspaceItemCoordinates(2, 3, 4))
        ReflectionHelpers.setField(bgDataModel, "itemsIdMap", wsData)
    }

    @Test
    fun addsWorkspaceItemForCreatedFile() {
        val task =
            HomeScreenFilesChangedTask(
                HomeScreenFilesProvider.FileChange(
                    testUri,
                    NOTIFY_INSERT,
                    CompletableFuture.completedFuture(testFile),
                    /*uriAlias=*/ null,
                ),
                iconCache,
                idp,
                workspaceItemSpaceFinder,
            )
        task.execute(modelTaskController, bgDataModel, allAppsList)

        val itemCaptor = argumentCaptor<ItemInfo>()
        verify(modelWriter, times(1))
            .addItemToDatabase(itemCaptor.capture(), eq(CONTAINER_DESKTOP), eq(2), eq(3), eq(4))

        with(itemCaptor.firstValue as ItemInfoWithIcon) {
            assertThat(bitmap).isEqualTo(this@HomeScreenFilesChangedTaskTest.bitmap)
            assertThat(title).isEqualTo("file.png")
            assertThat(itemType).isEqualTo(ITEM_TYPE_FILE_SYSTEM_FILE)
            assertThat(intent).isNotNull()
            assertThat(intent!!.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(intent!!.flags).isEqualTo(HomeScreenFilesUtils.LAUNCH_INTENT_DEFAULT_FLAGS)
            assertThat(intent!!.data).isEqualTo(testUri)
            assertThat(intent!!.type).isEqualTo("image/png")
        }
    }

    @Test
    fun addsWorkspaceItemForMovedFile() {
        val task =
            HomeScreenFilesChangedTask(
                HomeScreenFilesProvider.FileChange(
                    testUri,
                    NOTIFY_UPDATE,
                    CompletableFuture.completedFuture(testFile),
                    /*uriAlias=*/ null,
                ),
                iconCache,
                idp,
                workspaceItemSpaceFinder,
            )
        task.execute(modelTaskController, bgDataModel, allAppsList)

        val itemCaptor = argumentCaptor<ItemInfo>()
        verify(modelWriter, times(1))
            .addItemToDatabase(itemCaptor.capture(), eq(CONTAINER_DESKTOP), eq(2), eq(3), eq(4))

        with(itemCaptor.firstValue as ItemInfoWithIcon) {
            assertThat(bitmap).isEqualTo(this@HomeScreenFilesChangedTaskTest.bitmap)
            assertThat(title).isEqualTo("file.png")
            assertThat(itemType).isEqualTo(ITEM_TYPE_FILE_SYSTEM_FILE)
            assertThat(intent).isNotNull()
            assertThat(intent!!.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(intent!!.flags).isEqualTo(HomeScreenFilesUtils.LAUNCH_INTENT_DEFAULT_FLAGS)
            assertThat(intent!!.data).isEqualTo(testUri)
            assertThat(intent!!.type).isEqualTo("image/png")
        }
    }

    @Test
    fun updatesWorkspaceItemForMovedFile() {
        val uriAlias = Uri.parse("content://test/path/id")
        wsData.modifyItems {
            val item =
                WorkspaceItemInfo().apply {
                    title = ""
                    intent = HomeScreenFilesUtils.buildLaunchIntent(uriAlias)
                }
            put(item.id, item)
        }
        val task =
            HomeScreenFilesChangedTask(
                HomeScreenFilesProvider.FileChange(
                    testUri,
                    NOTIFY_UPDATE,
                    CompletableFuture.completedFuture(testFile),
                    uriAlias,
                ),
                iconCache,
                idp,
                workspaceItemSpaceFinder,
            )
        task.execute(modelTaskController, bgDataModel, allAppsList)

        val itemCaptor = argumentCaptor<ItemInfo>()
        verify(modelWriter, times(1)).updateItemInDatabase(itemCaptor.capture())

        val itemsCaptor = argumentCaptor<List<ItemInfo>>()
        verify(modelTaskController, times(1)).bindUpdatedWorkspaceItems(itemsCaptor.capture())
        assertThat(itemsCaptor.firstValue.size).isEqualTo(1)

        with(itemsCaptor.firstValue[0] as ItemInfoWithIcon) {
            assertThat(this).isEqualTo(itemCaptor.firstValue)
            assertThat(bitmap).isEqualTo(this@HomeScreenFilesChangedTaskTest.bitmap)
            assertThat(title).isEqualTo("file.png")
            assertThat(itemType).isEqualTo(ITEM_TYPE_FILE_SYSTEM_FILE)
            assertThat(intent).isNotNull()
            assertThat(intent!!.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(intent!!.flags).isEqualTo(HomeScreenFilesUtils.LAUNCH_INTENT_DEFAULT_FLAGS)
            assertThat(intent!!.data).isEqualTo(testUri)
            assertThat(intent!!.type).isEqualTo("image/png")
        }
    }

    @Test
    fun updatesWorkspaceItemForUpdatedFile() {
        wsData.modifyItems {
            val item =
                WorkspaceItemInfo().apply {
                    title = "old_file.png"
                    intent = HomeScreenFilesUtils.buildLaunchIntent(testUri, testFile)
                }
            put(item.id, item)
        }
        val task =
            HomeScreenFilesChangedTask(
                HomeScreenFilesProvider.FileChange(
                    testUri,
                    NOTIFY_UPDATE,
                    CompletableFuture.completedFuture(testFile),
                    /*uriAlias=*/ null,
                ),
                iconCache,
                idp,
                workspaceItemSpaceFinder,
            )
        task.execute(modelTaskController, bgDataModel, allAppsList)

        val itemCaptor = argumentCaptor<ItemInfo>()
        verify(modelWriter, times(1)).updateItemInDatabase(itemCaptor.capture())

        val itemsCaptor = argumentCaptor<List<ItemInfo>>()
        verify(modelTaskController, times(1)).bindUpdatedWorkspaceItems(itemsCaptor.capture())
        assertThat(itemsCaptor.firstValue.size).isEqualTo(1)

        with(itemsCaptor.firstValue[0] as ItemInfoWithIcon) {
            assertThat(this).isEqualTo(itemCaptor.firstValue)
            assertThat(bitmap).isEqualTo(this@HomeScreenFilesChangedTaskTest.bitmap)
            assertThat(title).isEqualTo("file.png")
        }
    }

    @Test
    fun deletesWorkspaceItemForDeletedFile() {
        val task =
            HomeScreenFilesChangedTask(
                HomeScreenFilesProvider.FileChange(
                    testUri,
                    NOTIFY_DELETE,
                    CompletableFuture.completedFuture(null),
                    /*uriAlias=*/ null,
                ),
                iconCache,
                idp,
                workspaceItemSpaceFinder,
            )
        task.execute(modelTaskController, bgDataModel, allAppsList)

        verify(modelTaskController, times(1)).deleteAndBindComponentsRemoved(any(), any())
    }
}
