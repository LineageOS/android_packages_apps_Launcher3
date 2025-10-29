/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.model

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.CallbackTask
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.Utilities
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.model.BgDataModel.Callbacks
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.provider.LauncherDbUtils.itemIdMatch
import com.android.launcher3.util.ContentWriter
import com.android.launcher3.util.Executors
import com.android.launcher3.util.ItemInfoMatcher
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.widget.LauncherWidgetHolder
import java.util.function.Predicate
import java.util.function.Supplier

/** Class for handling model updates. */
class ModelWriter(
    private val context: Context,
    private val model: LauncherModel,
    private val bgDataModel: BgDataModel,
    private val verifyChanges: Boolean,
    private val cellPosMapper: CellPosMapper,
    private val owner: Callbacks?,
) {
    private val uiExecutor: LooperExecutor = Executors.MAIN_EXECUTOR

    // Keep track of delete operations that occur when an Undo option is present; we may not commit.
    private val deleteRunnables: MutableList<ModelTask> = ArrayList()
    private var preparingToUndo = false

    /** Updates the location properties of the item */
    private fun updateItemInfoProps(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        val modelPos = cellPosMapper.mapPresenterToModel(cellX, cellY, screenId, container)
        item.container = container
        item.cellX = modelPos.cellX
        item.cellY = modelPos.cellY
        item.screenId = modelPos.screenId
    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container></container>, screen, cellX, cellY>
     */
    fun addOrMoveItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        if (item.id == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(item, container, screenId, cellX, cellY)
        } else {
            // From somewhere else
            moveItemInDatabase(item, container, screenId, cellX, cellY)
        }
    }

    private fun checkItemInfoLocked(
        itemId: Int,
        item: ItemInfo,
        stackTrace: Array<StackTraceElement>,
    ) {
        val modelItem = bgDataModel.itemsIdMap[itemId]
        if (modelItem != null && item !== modelItem) {
            // check all the data is consistent
            if (
                !Utilities.IS_DEBUG_DEVICE &&
                    !FeatureFlags.IS_STUDIO_BUILD &&
                    modelItem is WorkspaceItemInfo &&
                    item is WorkspaceItemInfo
            ) {
                if (
                    modelItem.title.toString() == item.title.toString() &&
                        modelItem.getIntent().filterEquals(item.getIntent()) &&
                        modelItem.id == item.id &&
                        modelItem.itemType == item.itemType &&
                        modelItem.container == item.container &&
                        modelItem.screenId == item.screenId &&
                        modelItem.cellX == item.cellX &&
                        modelItem.cellY == item.cellY &&
                        modelItem.spanX == item.spanX &&
                        modelItem.spanY == item.spanY
                ) {
                    // For all intents and purposes, this is the same object
                    return
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            val e =
                RuntimeException(
                    "item: $item modelItem: $modelItem Error: ItemInfo passed to checkItemInfo doesn't match original"
                )
            e.stackTrace = stackTrace
            throw e
        }
    }

    /** Move an item in the DB to a new <container></container>, screen, cellX, cellY> */
    fun moveItemInDatabase(item: ItemInfo, container: Int, screenId: Int, cellX: Int, cellY: Int) {
        updateItemInfoProps(item, container, screenId, cellX, cellY)
        notifyItemModified(item)

        enqueueDeleteRunnable(
            UpdateItemRunnable(item) {
                ContentWriter(context)
                    .put(Favorites.CONTAINER, item.container)
                    .put(Favorites.CELLX, item.cellX)
                    .put(Favorites.CELLY, item.cellY)
                    .put(Favorites.RANK, item.rank)
                    .put(Favorites.SCREEN, item.screenId)
            }
        )
    }

    /**
     * Move items in the DB to a new <container></container>, screen, cellX, cellY>. We assume that
     * the cellX, cellY have already been updated on the ItemInfos.
     */
    fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int) {
        notifyOtherCallbacks { it.bindItemsUpdated(items.toSet()) }
        val contentValues =
            items.map { item ->
                updateItemInfoProps(item, container, screen, item.cellX, item.cellY)
                ContentValues().apply {
                    put(Favorites.CONTAINER, item.container)
                    put(Favorites.CELLX, item.cellX)
                    put(Favorites.CELLY, item.cellY)
                    put(Favorites.RANK, item.rank)
                    put(Favorites.SCREEN, item.screenId)
                }
            }
        enqueueDeleteRunnable(UpdateItemsRunnable(items, contentValues))
    }

    /**
     * Move and/or resize item in the DB to a new <container></container>, screen, cellX, cellY,
     * spanX, spanY>
     */
    fun modifyItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ) {
        updateItemInfoProps(item, container, screenId, cellX, cellY)
        item.spanX = spanX
        item.spanY = spanY
        notifyItemModified(item)
        UpdateItemRunnable(item) {
                ContentWriter(context)
                    .put(Favorites.CONTAINER, item.container)
                    .put(Favorites.CELLX, item.cellX)
                    .put(Favorites.CELLY, item.cellY)
                    .put(Favorites.RANK, item.rank)
                    .put(Favorites.SPANX, item.spanX)
                    .put(Favorites.SPANY, item.spanY)
                    .put(Favorites.SCREEN, item.screenId)
            }
            .executeOnModelThread()
    }

    /** Update an item to the database in a specified container. */
    fun updateItemInDatabase(item: ItemInfo) {
        notifyItemModified(item)
        UpdateItemRunnable(item) {
                val writer = ContentWriter(context)
                item.onAddToDatabase(writer)
                writer
            }
            .executeOnModelThread()
    }

    fun notifyItemModified(item: ItemInfo) = notifyOtherCallbacks {
        it.bindItemsUpdated(setOf(item))
    }

    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    fun addItemToDatabase(item: ItemInfo, container: Int, screenId: Int, cellX: Int, cellY: Int) {
        updateItemInfoProps(item, container, screenId, cellX, cellY)
        addItemsToDatabase(listOf(item))
    }

    /** Add provided items to the database. Also assigns an ID to each item. */
    fun addItemsToDatabase(items: List<ItemInfo>) {
        items.forEach { it.id = model.modelDbController.generateNewItemId() }
        notifyOtherCallbacks { it.bindItemsAdded(items) }

        val verifier = ModelVerifier()
        val stackTrace = Throwable().stackTrace
        newModelTask {
                // Write the item on background thread, as some properties might have been updated
                // in
                // the background.
                for (item in items) {
                    val writer = ContentWriter(context)
                    item.onAddToDatabase(writer)
                    writer.put(Favorites._ID, item.id)
                    model.modelDbController.insert(writer.getValues(context))
                }
                synchronized(bgDataModel) {
                    for (item in items) {
                        checkItemInfoLocked(item.id, item, stackTrace)
                    }
                    bgDataModel.addItems(context, items, owner)
                    verifier.verifyModel()
                }
            }
            .executeOnModelThread()
    }

    /** Removes the specified item from the database */
    fun deleteItemFromDatabase(item: ItemInfo, reason: String?) {
        deleteItemsFromDatabase(listOf(item), reason)
    }

    /** Removes all the items from the database matching {@param matcher}. */
    fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?) {
        deleteItemsFromDatabase(bgDataModel.itemsIdMap.filter { matcher.test(it) }, reason)
    }

    /** Removes the specified items from the database */
    fun deleteItemsFromDatabase(items: Collection<ItemInfo>, reason: String?) {
        val verifier = ModelVerifier()
        Log.d(
            TAG,
            "removing items from db ${items.map { it.targetPackage ?: "" }}. Reason: [${reason ?: "unknown"}]",
        )
        notifyDelete(items)
        enqueueDeleteRunnable(
            newModelTask {
                for (item in items) {
                    model.modelDbController.delete(itemIdMatch(item.id), null)
                }
                bgDataModel.removeItem(context, items, owner)
                verifier.verifyModel()
            }
        )
    }

    /** Remove the specified folder and all its contents from the database. */
    fun deleteCollectionAndContentsFromDatabase(info: CollectionInfo) {
        val verifier = ModelVerifier()
        notifyDelete(setOf(info))

        enqueueDeleteRunnable(
            newModelTask {
                model.modelDbController.delete(Favorites.CONTAINER + "=" + info.id, null)
                model.modelDbController.delete(Favorites._ID + "=" + info.id, null)

                val itemsToDelete = info.getContents() + info
                bgDataModel.removeItem(context, itemsToDelete, owner)
                verifier.verifyModel()
            }
        )
    }

    /** Deletes the widget info and the widget id. */
    fun deleteWidgetInfo(
        info: LauncherAppWidgetInfo,
        holder: LauncherWidgetHolder?,
        reason: String?,
    ) {
        notifyDelete(setOf(info))
        if (holder != null && !info.isCustomWidget && info.isWidgetIdAllocated) {
            // Deleting an app widget ID is a void call but writes to disk before returning
            // to the caller...
            enqueueDeleteRunnable(newModelTask { holder.deleteAppWidgetId(info.appWidgetId) })
        }
        deleteItemFromDatabase(info, reason)
    }

    private fun notifyDelete(items: Collection<ItemInfo>) = notifyOtherCallbacks {
        it.bindWorkspaceComponentsRemoved(ItemInfoMatcher.ofItems(items))
    }

    /**
     * Delete operations tracked using [.enqueueDeleteRunnable] will only be called if
     * [.commitDelete] is called. Note that one of [.commitDelete] or [.abortDelete] MUST be called
     * after this method, or else all delete operations will remain uncommitted indefinitely.
     */
    fun prepareToUndoDelete() {
        if (!preparingToUndo) {
            check(!(deleteRunnables.isNotEmpty() && FeatureFlags.IS_STUDIO_BUILD)) {
                "There are still uncommitted delete operations!"
            }
            deleteRunnables.clear()
            preparingToUndo = true
        }
    }

    /**
     * If [.prepareToUndoDelete] has been called, we store the Runnable to be run when
     * [.commitDelete] is called (or abandoned if [.abortDelete] is called). Otherwise, we run the
     * Runnable immediately.
     */
    private fun enqueueDeleteRunnable(r: ModelTask) {
        if (preparingToUndo) {
            deleteRunnables.add(r)
        } else {
            r.executeOnModelThread()
        }
    }

    fun commitDelete() {
        preparingToUndo = false
        deleteRunnables.forEach { it.executeOnModelThread() }
        deleteRunnables.clear()
    }

    /** Aborts a previous delete operation pending commit */
    fun abortDelete() {
        preparingToUndo = false
        deleteRunnables.clear()
        // We do a full reload here instead of just a rebind because Folders change their internal
        // state when dragging an item out, which clobbers the rebind unless we load from the DB.
        model.forceReload()
    }

    private fun notifyOtherCallbacks(task: CallbackTask) {
        // If the call is happening from a model, it will take care of updating the callbacks
        owner ?: return
        uiExecutor.execute { model.callbacks.forEach { if (it !== owner) task.execute(it) } }
    }

    private inner class UpdateItemRunnable(
        private val item: ItemInfo,
        private val writer: Supplier<ContentWriter>,
    ) : UpdateItemBaseRunnable() {
        private val itemId = item.id

        override fun runImpl() {
            model.modelDbController.update(
                writer.get().getValues(context),
                itemIdMatch(itemId),
                null,
            )
            updateItemArrays(item, itemId)
            bgDataModel.updateItems(listOf(item), owner)
        }
    }

    private inner class UpdateItemsRunnable(
        private val items: List<ItemInfo>,
        private val values: List<ContentValues>,
    ) : UpdateItemBaseRunnable() {
        override fun runImpl() {
            try {
                model.modelDbController.newTransaction().use { t ->
                    items.zip(values).forEach { (item, value) ->
                        val itemId = item.id
                        model.modelDbController.update(value, itemIdMatch(itemId), null)
                        updateItemArrays(item, itemId)
                    }
                    t.commit()
                    bgDataModel.updateItems(items, owner)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private abstract inner class UpdateItemBaseRunnable : ModelTask() {
        private val stackTrace: Array<StackTraceElement> = Throwable().stackTrace
        private val verifier = ModelVerifier()

        protected fun updateItemArrays(item: ItemInfo, itemId: Int) {
            // Lock on mBgLock *after* the db operation
            synchronized(bgDataModel) {
                checkItemInfoLocked(itemId, item, stackTrace)
                if (
                    item.container != Favorites.CONTAINER_DESKTOP &&
                        item.container != Favorites.CONTAINER_HOTSEAT
                ) {
                    // Item is in a collection, make sure this collection exists
                    if (bgDataModel.itemsIdMap[item.container] !is CollectionInfo) {
                        // An items container is being set to a that of an item which is not in
                        // the list of collections.
                        Log.e(
                            TAG,
                            "item: $item container being set to: ${item.container}, not in the list of collections",
                        )
                    }
                }
                verifier.verifyModel()
            }
        }
    }

    private abstract inner class ModelTask : Runnable {
        private val loadId = bgDataModel.lastLoadId

        override fun run() {
            if (loadId != model.lastLoadId) {
                Log.d(TAG, "Model changed before the task could execute")
                return
            }
            runImpl()
        }

        fun executeOnModelThread() {
            Executors.MODEL_EXECUTOR.execute(this)
        }

        abstract fun runImpl()
    }

    private fun newModelTask(r: Runnable): ModelTask {
        return object : ModelTask() {
            override fun runImpl() {
                r.run()
            }
        }
    }

    /** Utility class to verify model updates are propagated properly to the callback. */
    inner class ModelVerifier internal constructor() {
        private val startId: Int = bgDataModel.lastBindId

        fun verifyModel() {
            if (!verifyChanges || !model.hasCallbacks()) {
                return
            }

            val executeId = bgDataModel.lastBindId

            uiExecutor.post {
                if (bgDataModel.lastBindId > executeId) {
                    // Model was already bound after job was executed.
                    return@post
                }
                if (executeId == startId) {
                    // Bound model has not changed during the job
                    return@post
                }

                // Bound model was changed between submitting the job and executing the job
                model.rebindCallbacks()
            }
        }
    }

    companion object {
        private const val TAG = "ModelWriter"
    }
}
