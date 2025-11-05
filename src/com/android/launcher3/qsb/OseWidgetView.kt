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

package com.android.launcher3.qsb

import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Process.myUserHandle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.RunnableList
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.views.OptionsPopupView.OptionItem
import com.android.launcher3.widget.LauncherAppWidgetHostView

/**
 * Renders the On-device search engine's widget [RemoteViews] based on [AppWidgetProviderInfo] by
 * listening to OSE changes through [OseWidgetManager]
 */
class OseWidgetView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    LauncherAppWidgetHostView(context) {

    private val oseWidgetManager = context.appComponent.oseWidgetManager
    @VisibleForTesting var closeActions = RunnableList()
    private val activityContext: ActivityContext = ActivityContext.lookupContext(context)

    init {
        activityContext.appWidgetHolder?.onViewCreationCallback?.accept(this)
        setOnLongClickListener { onWidgetLongClick(it) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedToWindow()
    }

    @VisibleForTesting
    fun attachedToWindow() {
        closeActions.executeAllAndClear()
        // We use INVALID_APPWIDGET_ID because appWidgetId is not tracked in OseWidgetView. Instead
        // it is managed by OseWidgetManager and QsbAppWidgetHost.

        closeActions.add(
            oseWidgetManager.providerInfo.forEach(activityContext.uiExecutor) {
                setAppWidget(INVALID_APPWIDGET_ID, it)
                // We will get valid updateAppWidget remoteview call from OseWidgetManager again.
                // This is only for resetting the remoteviews using a broken remote view.
                updateAppWidget(RemoteViews(context.packageName, 0))
                tag = getTagInfo(it)
                Log.i(TAG, "setAppWidget providerInfo= " + it)
            }::close
        )
        closeActions.add(
            oseWidgetManager.views.forEach(activityContext.uiExecutor) {
                updateAppWidget(it)
                Log.i(TAG, "updateAppWidget view= " + it)
            }::close
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachedFromWindow()
    }

    @VisibleForTesting
    fun detachedFromWindow() {
        closeActions.executeAllAndClear()
    }

    override fun shouldDelayChildPressedState(): Boolean {
        // Delay the ripple effect on the widget view when swiping up from home screen
        // to go to all apps.
        return true
    }

    override fun getErrorView(): View {
        val oseManager = context.appComponent.getOseManager()
        val oseInfo = oseManager.oseInfo.value
        val osePkg: String? =
            when {
                oseInfo.isOseConfigured -> oseInfo.pkg
                else -> null
            }
        val appInfo =
            osePkg?.let {
                val componentKey = ComponentKey(ComponentName(osePkg, ""), myUserHandle())
                activityContext.activityComponent.appsStore
                    .getApp(componentKey, AppInfo.PACKAGE_KEY_COMPARATOR)
                    ?.clone()
            }
        return appInfo?.run { showOseBubbleTextLayout(this, oseInfo.supportsSearchIntent) }
            ?: showDefaultOseLayout()
    }

    fun showOseBubbleTextLayout(appInfo: AppInfo, launchSearchIntent: Boolean): View {
        appInfo.title = context.resources.getString(R.string.abandoned_search)
        return View.inflate(context, R.layout.ose_default_bubbletext_layout, null).apply {
            val btv = this as BubbleTextView
            btv.applyFromApplicationInfo(appInfo)
            setOnClickIntent(
                when {
                    // Launch search intent.
                    launchSearchIntent ->
                        Intent(Intent.ACTION_SEARCH).setPackage(appInfo.targetPackage)
                    // Launch main activity
                    else -> appInfo.intent
                }
            )
        }
    }

    fun showDefaultOseLayout(): View =
        View.inflate(context, R.layout.ose_default_layout, null).apply {
            // Since we don't have a valid appInfo, just open the default browser
            // Set the data to a blank page uri
            setOnClickIntent(Intent(Intent.ACTION_VIEW).setData("http://".toUri()))
        }

    fun View.setOnClickIntent(intent: Intent) = setOnClickListener {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (intent.action == Intent.ACTION_VIEW) {
            // Browser Intent and set the default browser package.
            val resolveInfo =
                runCatching {
                        context.packageManager.resolveActivity(
                            intent,
                            PackageManager.MATCH_DEFAULT_ONLY,
                        )
                    }
                    .getOrNull()
            resolveInfo?.activityInfo?.packageName.apply { intent.setPackage(this) }
        }
        activityContext.startActivitySafely(
            this@OseWidgetView,
            intent,
            this@OseWidgetView.tag as? ItemInfo,
        )
    }

    @VisibleForTesting
    fun onWidgetLongClick(view: View): Boolean {
        val oseWidgetOptionsProvider =
            activityContext.activityComponent.getOseWidgetOptionsProvider()
        val optionItems = oseWidgetOptionsProvider.getOptionItems()
        if (optionItems.isEmpty()) return false

        val bounds =
            RectF(Utilities.getViewBounds(this)).apply {
                left = centerX()
                right = centerX()
            }
        showOptionsPopup(bounds, optionItems)
        return true
    }

    @VisibleForTesting
    fun showOptionsPopup(bounds: RectF, optionItems: List<OptionItem>) {
        OptionsPopupView.showNoReturn(activityContext, bounds, optionItems, true)
    }

    private class QsbItemInfo : ItemInfo() {

        override fun getStableId() = STABLE_ID
    }

    companion object {
        private const val TAG = "OseWidgetView"

        private val STABLE_ID = Object()

        private fun getTagInfo(provider: AppWidgetProviderInfo?): ItemInfo {
            val info =
                provider?.let { LauncherAppWidgetInfo(INVALID_APPWIDGET_ID, it.provider) }
                    ?: QsbItemInfo()
            info.id = R.id.search_container_hotseat
            info.container = Favorites.CONTAINER_HOTSEAT
            return info
        }
    }
}
