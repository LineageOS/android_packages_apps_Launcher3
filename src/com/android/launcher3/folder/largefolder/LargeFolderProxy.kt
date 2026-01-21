package com.android.launcher3.folder.largefolder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Rect
import android.util.Log
import android.widget.Toast
import androidx.core.animation.addListener
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.celllayout.ItemConfiguration
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo

/**
 * Created by ch.hu
 * Date: 7/3/25 14:06
 * Description:
 */
class LargeFolderProxy {

    companion object {
        private const val TAG = "LargeFolderProxy"

        private const val CONVERT_DURATION = 300
        const val EXPAND_LT: Int = 1
        const val EXPAND_RT: Int = 2
        const val EXPAND_LB: Int = 3
        const val EXPAND_RB: Int = 4

        @JvmStatic
        fun getInstance(): LargeFolderProxy {
            return LargeFolderProxyHolder.mInstance
        }
    }

    private object LargeFolderProxyHolder {
        @JvmStatic
        var mInstance: LargeFolderProxy = LargeFolderProxy()
    }

    fun convertToFolder(launcher: Launcher, oldFolderIcon: LargeFolderIcon) {
        val workspace = launcher.workspace
        val cellLayout = workspace.getParentCellLayoutForView(oldFolderIcon)
        cellLayout.markCellsAsUnoccupiedForView(oldFolderIcon)
        val info = oldFolderIcon.mInfo
        updateFolderInfo(info, LauncherSettings.Favorites.ITEM_TYPE_FOLDER, info.cellX, info.cellY)
        val newFolderIcon = FolderIcon.inflateIcon(R.layout.folder_icon, launcher, null, info)
        val folder = oldFolderIcon.folder
        folder.folderIcon = newFolderIcon
        newFolderIcon.folder = folder
        workspace.addInScreen(newFolderIcon, info)
        val modelWriter = launcher.modelWriter
        addOrMoveItemInDatabase(modelWriter, info)
        val animatorSet = LargeFolderAnimator(launcher, oldFolderIcon).getAnimator(
            EXPAND_RB,
            false
        )
        animatorSet.setDuration(CONVERT_DURATION.toLong())
        animatorSet.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    newFolderIcon.alpha = 0.0f
                }

                override fun onAnimationEnd(animation: Animator) {
                    newFolderIcon.alpha = 1.0f
                    oldFolderIcon.removeListeners()
                    cellLayout.removeView(oldFolderIcon)
                    cellLayout.markCellsAsOccupiedForView(newFolderIcon)
                }
            })
        animatorSet.start()
    }

    fun convertToLargeFolder(launcher: Launcher, oldFolderIcon: FolderIcon) {
        val workspace = launcher.workspace
        val cellLayout = workspace.getParentCellLayoutForView(oldFolderIcon)
        cellLayout.markCellsAsUnoccupiedForView(oldFolderIcon)

        val solution = findExpandSolution(
            cellLayout, oldFolderIcon,
            oldFolderIcon.mInfo.cellX, oldFolderIcon.mInfo.cellY
        )

        if (!solution.isSolution) {
            Log.w(TAG, "Find expand solution error.")
            Toast.makeText(
                launcher,
                launcher.getString(R.string.out_of_space),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val expandType = findExpandType(
            oldFolderIcon.mInfo.cellX, oldFolderIcon.mInfo.cellY,
            solution.cellX, solution.cellY
        )

        // Remove old FolderIcon
        cellLayout.removeView(oldFolderIcon)

        val info = oldFolderIcon.mInfo
        updateFolderInfo(
            info,
            LauncherSettings.Favorites.ITEM_TYPE_LARGE_FOLDER,
            solution.cellX,
            solution.cellY
        )

        val newFolderIcon = LargeFolderIcon.inflateIcon(R.layout.large_folder_icon, launcher, info)
        val folder = oldFolderIcon.folder

        folder.largeFolderIcon = newFolderIcon
        newFolderIcon.folder = folder

        workspace.addInScreen(newFolderIcon, info)
        addOrMoveItemInDatabase(launcher.modelWriter, info)

        // Play expand animation
        LargeFolderAnimator(launcher, newFolderIcon)
            .getAnimator(expandType, true)
            .apply {
                addListener(onEnd = {
                    cellLayout.markCellsAsOccupiedForView(newFolderIcon)
                })
                duration = CONVERT_DURATION.toLong()
                start()
            }

        // Move other affected items
        val changedMap = solution.getChangedMap()
        for ((view, cellAndSpan) in changedMap) {
            // Skip original folder (already replaced)
            if (view == oldFolderIcon || cellAndSpan == null) continue

            // Animate to new position
            cellLayout.animateChildToPosition(
                view,
                cellAndSpan.cellX,
                cellAndSpan.cellY,
                CONVERT_DURATION,
                0,
                true,
                true
            )

            // Update database position
            (view.tag as? ItemInfo)?.let { itemInfo ->
                itemInfo.cellX = cellAndSpan.cellX
                itemInfo.cellY = cellAndSpan.cellY
                addOrMoveItemInDatabase(launcher.modelWriter, itemInfo)
            } ?: run {
                Log.e(TAG, "Move Item In Database Error (missing ItemInfo):\n$view\n${view.tag}")
            }
        }
    }

    fun findExpandSolution(
        cellLayout: CellLayout, dragView: FolderIcon?,
        cellX: Int, cellY: Int
    ): ItemConfiguration {
        val cellBounds = Rect()
        cellLayout.cellToRect(cellX, cellY, 1, 1, cellBounds)
        val expandCenters = arrayOf(
            intArrayOf(cellBounds.left, cellBounds.top),
            intArrayOf(cellBounds.right, cellBounds.top),
            intArrayOf(cellBounds.left, cellBounds.bottom),
            intArrayOf(cellBounds.right, cellBounds.bottom)
        )
        var changedCount = 1000
        var bestSolution = ItemConfiguration()
        val mDirectionVector = IntArray(2)
        for (i in expandCenters.indices) {
            val solution = cellLayout.findReorderSolution(
                expandCenters[i][0],
                expandCenters[i][1], 2, 2, 2, 2, mDirectionVector, dragView, true
            )
            if (solution.isSolution && changedCount > solution.getChangedMap().size) {
                changedCount = solution.getChangedMap().size
                bestSolution = solution
            }
        }
        return bestSolution
    }

    fun findExpandType(oldCellX: Int, oldCellY: Int, newCellX: Int, newCellY: Int): Int {
        if (newCellX < oldCellX) {
            if (newCellY < oldCellY) {
                return EXPAND_LT
            }
            return EXPAND_LB
        } else if (newCellY < oldCellY) {
            return EXPAND_RT
        } else {
            return EXPAND_RB
        }
    }

    private fun addOrMoveItemInDatabase(modelWriter: ModelWriter, itemInfo: ItemInfo) {
        modelWriter.addOrMoveItemInDatabase(
            itemInfo, itemInfo.container, itemInfo.screenId,
            itemInfo.cellX, itemInfo.cellY
        )
    }

    fun updateFolderInfo(
        folderInfo: FolderInfo, itemType: Int, cellX: Int,
        cellY: Int
    ): FolderInfo {
        folderInfo.itemType = itemType
        folderInfo.cellX = cellX
        folderInfo.cellY = cellY
        when (itemType) {
            LauncherSettings.Favorites.ITEM_TYPE_FOLDER -> {
                folderInfo.spanX = 1
                folderInfo.spanY = 1
                folderInfo.minSpanX = 1
                folderInfo.minSpanY = 1
            }

            LauncherSettings.Favorites.ITEM_TYPE_LARGE_FOLDER -> {
                folderInfo.spanX = 2
                folderInfo.spanY = 2
                folderInfo.minSpanX = 2
                folderInfo.minSpanY = 2
            }
        }
        return folderInfo
    }

    fun getSwitchLabelResId(tag: Any): Int {
        val info: ItemInfo = tag as ItemInfo
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_LARGE_FOLDER) {
            return R.string.folder_switch_small
        }
        return R.string.folder_switch_large
    }

    fun getSwitchIconResId(tag: Any): Int {
        val info: ItemInfo = tag as ItemInfo
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_LARGE_FOLDER) {
            return R.drawable.folder_switch_small
        }
        return R.drawable.folder_switch_large
    }
}
