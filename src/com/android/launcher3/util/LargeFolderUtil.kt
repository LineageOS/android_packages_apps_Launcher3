package com.android.launcher3.util

import com.android.launcher3.icons.GraphicsUtils
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Path
import android.text.TextUtils
import android.util.Log
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo

/**
 * Created by ch.hu
 * Date: 11/24/25 15:11
 * Description:
 */
class LargeFolderUtil {

    private object LargeFolderUtilHolder {
        @JvmStatic
        var mInstance: LargeFolderUtil = LargeFolderUtil()
    }

    companion object {

        @JvmStatic
        fun getInstance(): LargeFolderUtil {
            return LargeFolderUtilHolder.mInstance
        }
    }

    private val TAG = "LargeFolderUtil"

    fun getIconShapePath(context: Context, iconSize: Int): Path {
        return ThemeManager.INSTANCE.get(context).iconState.iconShape.getPath()
    }

    fun filterUninstallApp(context: Context, folderInfo: FolderInfo): List<ItemInfo> {
        val contents = folderInfo.getContents()
        val delItems = contents.stream().filter {
            it != null && it.isDisabled && isAppUninstalled(context, it)
        }.toList()
        contents.removeAll(delItems.toSet())
        return delItems
    }

    private fun isAppUninstalled(context: Context, info: ItemInfo?): Boolean {
        if (info != null && info.isDisabled) {
            val pkg = if (TextUtils.isEmpty(info.targetPackage)) "" else info.targetPackage!!
            if (!isAppInstalled(context, pkg)) {
                Log.d(
                    TAG,
                    "isAppUninstalled " + (info.title as Any?) + " is not installed! ,screenId: " + info.screenId + " ,container: " + info.container
                )
                return true
            }
            return false
        }
        return false
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }
}