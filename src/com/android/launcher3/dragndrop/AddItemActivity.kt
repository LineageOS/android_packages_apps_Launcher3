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
package com.android.launcher3.dragndrop

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import com.android.launcher3.BaseActivity
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.compose.ComposeFacade.isComposeAvailable
import com.android.launcher3.dagger.LauncherComponentProvider
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.SerializedItemItem
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.WidgetsModel
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.pm.PinRequestHelper
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.views.AbstractSlideInView
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.widget.AddItemWidgetsBottomSheet
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.PendingAddShortcutInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.WidgetCell
import com.android.launcher3.widget.WidgetCell.PreviewReadyListener
import com.android.launcher3.widget.WidgetCellPreview
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widget.WidgetSections
import com.android.launcher3.widgetpicker.WidgetPickerConfig
import java.lang.ref.WeakReference
import java.util.function.Supplier
import kotlin.math.min

/** Activity to show pin widget dialog. */
open class AddItemActivity :
    BaseActivity(),
    View.OnLongClickListener,
    View.OnTouchListener,
    AbstractSlideInView.OnCloseListener,
    PreviewReadyListener,
    OnBackPressedDispatcherOwner,
    OnBackAnimationCallback,
    PinItemAddHandler {
    private val mLastTouchPos = PointF()

    private lateinit var pinItemRequest: LauncherApps.PinItemRequest
    private lateinit var app: LauncherAppState
    private lateinit var idp: InvariantDeviceProfile
    private lateinit var dragLayer: BaseDragLayer<AddItemActivity>
    private lateinit var accessibilityManager: AccessibilityManager

    private var slideInView: AddItemWidgetsBottomSheet? = null
    private var widgetCell: WidgetCell? = null

    // Widget request specific options.
    private var appWidgetHolder: LauncherWidgetHolder? = null
    private var appWidgetManager: WidgetManagerHelper? = null
    private var pendingBindWidgetId = 0
    private var widgetOptions: Bundle? = null

    private var mFinishOnPause = false

    private val showComposeView
        get() = isComposeAvailable() && Flags.enableAppWidgetPickerRefactor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pinItemRequest =
            PinRequestHelper.getPinItemRequest(intent)
                ?: run {
                    finish()
                    return
                }

        app = getInstance(this)
        idp = app.invariantDeviceProfile

        // Use the application context to get the device profile, as in multiwindow-mode, the
        // confirmation activity might be rotated.
        mDeviceProfile = idp.getDeviceProfile(applicationContext)

        if (showComposeView) {
            setContentView(R.layout.add_item_confirmation_activity_compose)
        } else {
            setContentView(R.layout.add_item_confirmation_activity)
        }

        // Set flag to allow activity to draw over navigation and status bar.
        checkNotNull(window)
            .setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
        dragLayer = findViewById(R.id.add_item_drag_layer)
        dragLayer.recreateControllers()
        accessibilityManager =
            checkNotNull(applicationContext.getSystemService(AccessibilityManager::class.java))
        appWidgetManager = WidgetManagerHelper(this)
        appWidgetHolder = LauncherWidgetHolder.newInstance(this)

        if (!showComposeView) {
            widgetCell =
                findViewById<WidgetCell>(R.id.widget_cell).apply {
                    addPreviewReadyListener(this@AddItemActivity)
                }
        }

        val targetApp =
            when (pinItemRequest.requestType) {
                LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> setupShortcut()
                LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> setupWidget()
                else -> null
            }
        if (targetApp == null) {
            // TODO: show error toast?
            finish()
            return
        }
        val info = ApplicationInfoWrapper(this, targetApp.packageName, targetApp.user).getInfo()
        if (info == null) {
            finish()
            return
        }

        if (showComposeView) {
            window?.decorView?.setViewTreeOnBackPressedDispatcherOwner(this)

            LauncherComponentProvider.get(this)
                .widgetPickerComposeWrapper
                .showWidgetsForPinRequest(
                    activity = this,
                    targetApp = targetApp.toPackageUserKey(),
                    pinItemRequest = pinItemRequest,
                    widgetPickerConfig = WidgetPickerConfig(),
                    pinItemAddHandler = this,
                )
            return
        }

        widgetCell?.findViewById<WidgetCellPreview>(R.id.widget_preview_container)?.apply {
            setOnTouchListener(this@AddItemActivity)
            setOnLongClickListener(this@AddItemActivity)
        }

        // savedInstanceState is null when the activity is created the first time (i.e., avoids
        // duplicate logging during rotation)
        if (savedInstanceState == null) {
            logCommand(LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_START)
        }

        // Set the label synchronously instead of via IconCache as this is the first thing
        // user sees
        val widgetAppName = findViewById<TextView>(R.id.widget_appName)
        val section =
            if (targetApp.widgetCategory == WidgetSections.NO_CATEGORY) {
                null
            } else {
                WidgetSections.getWidgetSections(this)[targetApp.widgetCategory]
            }
        widgetAppName.text =
            if (section == null) {
                info.loadLabel(packageManager)
            } else {
                getString(section.mSectionTitle)
            }

        slideInView =
            findViewById<AddItemWidgetsBottomSheet>(R.id.add_item_bottom_sheet).apply {
                addOnCloseListener(this@AddItemActivity)
                show()
            }
        setupNavBarColor()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        mLastTouchPos[motionEvent.x] = motionEvent.y
        return false
    }

    override fun onLongClick(view: View): Boolean {
        val cell = widgetCell ?: return false

        // Find the position of the preview relative to the touch location.
        val img = cell.widgetView
        val appWidgetHostView = cell.appWidgetHostViewPreview

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (img.drawable == null && appWidgetHostView == null) {
            return false
        }

        val bounds: Rect
        // Start home and pass the draw request params
        val listener: PinItemDragListener
        if (appWidgetHostView != null) {
            bounds = Rect()
            appWidgetHostView.getSourceVisualDragBounds(bounds)
            val appWidgetHostViewScale = cell.appWidgetHostViewScale
            val xOffset =
                appWidgetHostView.left - (mLastTouchPos.x * appWidgetHostViewScale).toInt()
            val yOffset = appWidgetHostView.top - (mLastTouchPos.y * appWidgetHostViewScale).toInt()
            bounds.offset(xOffset, yOffset)
            listener =
                PinItemDragListener(
                    pinItemRequest,
                    bounds,
                    appWidgetHostView.measuredWidth,
                    appWidgetHostView.measuredWidth,
                    appWidgetHostViewScale,
                )
        } else {
            bounds = img.bitmapBounds
            bounds.offset(img.left - mLastTouchPos.x.toInt(), img.top - mLastTouchPos.y.toInt())
            listener =
                PinItemDragListener(pinItemRequest, bounds, img.drawable.intrinsicWidth, img.width)
        }

        // Start a system drag and drop. We use a transparent bitmap as preview for system drag
        // as the preview is handled internally by launcher.
        val description = ClipDescription("", arrayOf(listener.mimeType))
        val data = ClipData(description, ClipData.Item(""))
        view.startDragAndDrop(
            data,
            object : View.DragShadowBuilder(view) {
                override fun onDrawShadow(canvas: Canvas) {}

                override fun onProvideShadowMetrics(
                    outShadowSize: Point,
                    outShadowTouchPoint: Point,
                ) {
                    outShadowSize[SHADOW_SIZE] = SHADOW_SIZE
                    outShadowTouchPoint[SHADOW_SIZE / 2] = SHADOW_SIZE / 2
                }
            },
            null,
            View.DRAG_FLAG_GLOBAL,
        )

        val homeIntent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(packageName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Launcher.ACTIVITY_TRACKER.registerCallback(listener, "AddItemActivity.onLongClick")
        startActivity(homeIntent, ApiWrapper.INSTANCE[this].createFadeOutAnimOptions().toBundle())
        logCommand(LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_DRAGGED)
        mFinishOnPause = true
        return false
    }

    override fun onPause() {
        super.onPause()
        if (mFinishOnPause) {
            finish()
        }
    }

    private fun setupShortcut(): PackageItemInfo {
        val shortcutInfo = PinShortcutRequestActivityInfo(pinItemRequest, this)

        if (!showComposeView) {
            checkNotNull(widgetCell).widgetView.tag = PendingAddShortcutInfo(shortcutInfo)
            applyWidgetItemAsync { WidgetItem(shortcutInfo, app.iconCache) }
        }

        return checkNotNull(pinItemRequest.shortcutInfo).let {
            PackageItemInfo(it.getPackage(), it.userHandle)
        }
    }

    private fun setupWidget(): PackageItemInfo? {
        val widgetInfo =
            LauncherAppWidgetProviderInfo.fromProviderInfo(
                this,
                pinItemRequest.getAppWidgetProviderInfo(this),
            )
        if (widgetInfo.minSpanX > idp.numColumns || widgetInfo.minSpanY > idp.numRows) {
            // Cannot add widget
            return null
        }

        widgetCell?.remoteViewsPreview = PinItemDragListener.getPreview(pinItemRequest)

        val pendingInfo =
            PendingAddWidgetInfo(widgetInfo, LauncherSettings.Favorites.CONTAINER_PIN_WIDGETS)
        pendingInfo.spanX = min(idp.numColumns.toDouble(), widgetInfo.spanX.toDouble()).toInt()
        pendingInfo.spanY = min(idp.numRows.toDouble(), widgetInfo.spanY.toDouble()).toInt()
        widgetOptions = pendingInfo.getDefaultSizeOptions(this)

        if (!showComposeView) {
            widgetCell?.apply { widgetView.tag = pendingInfo }
            applyWidgetItemAsync { WidgetItem(widgetInfo, idp, app.iconCache, app.context) }
        }

        return WidgetsModel.newPendingItemInfo(this, widgetInfo.component, widgetInfo.user)
    }

    private fun applyWidgetItemAsync(itemProvider: Supplier<WidgetItem>) {
        widgetCell?.let {
            AddItemAsyncTask(it, itemProvider).executeOnExecutor(Executors.MODEL_EXECUTOR)
        }
    }

    /** Called when the cancel button is clicked. */
    fun onCancelClick(view: View) {
        logCommand(LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_CANCELLED)
        slideInView?.close(/* animate= */ true)
    }

    /** Called when place-automatically button is clicked. */
    fun onPlaceAutomaticallyClick(v: View?) {
        if (pinItemRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val shortcutInfo = checkNotNull(pinItemRequest.shortcutInfo)
            ItemInstallQueue.INSTANCE[this].queueItem(SerializedItemItem(shortcutInfo))
            logCommand(LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_PLACED_AUTOMATICALLY)
            pinItemRequest.accept()
            var label = shortcutInfo.longLabel
            if (TextUtils.isEmpty(label)) {
                label = shortcutInfo.shortLabel
            }
            sendWidgetAddedToScreenAccessibilityEvent(label.toString())
            slideInView?.close(/* animate= */ true)
            return
        }

        appWidgetHolder?.let { widgetHolder ->
            pendingBindWidgetId = widgetHolder.allocateAppWidgetId()
            val widgetProviderInfo = checkNotNull(pinItemRequest.getAppWidgetProviderInfo(this))
            val success =
                checkNotNull(appWidgetManager)
                    .bindAppWidgetIdIfAllowed(
                        pendingBindWidgetId,
                        widgetProviderInfo,
                        widgetOptions,
                    )
            if (success) {
                sendWidgetAddedToScreenAccessibilityEvent(widgetProviderInfo.label)
                acceptWidget(pendingBindWidgetId)
                return
            }

            // request bind widget
            widgetHolder.startBindFlow(
                this,
                pendingBindWidgetId,
                checkNotNull(pinItemRequest.getAppWidgetProviderInfo(this)),
                REQUEST_BIND_APPWIDGET,
            )
        }
    }

    override fun onAddItemClicked() {
        onPlaceAutomaticallyClick(/*view*/ null)
        finish()
    }

    private fun acceptWidget(widgetId: Int) {
        pinItemRequest.getAppWidgetProviderInfo(this)?.let {
            ItemInstallQueue.INSTANCE[this].queueItem(SerializedItemItem(it, widgetId))
        }
        widgetOptions?.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        pinItemRequest.accept(widgetOptions)
        logCommand(LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_PLACED_AUTOMATICALLY)
        slideInView?.close(/* animate= */ true)
    }

    public override fun onDestroy() {
        super.onDestroy()
        // Necessary to destroy the holder to free up possible activity context
        appWidgetHolder?.destroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        logCommand(LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_BACK)
        slideInView?.close(/* animate= */ true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_BIND_APPWIDGET) {
            val widgetId =
                data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingBindWidgetId)
                    ?: pendingBindWidgetId

            if (resultCode == RESULT_OK) {
                acceptWidget(widgetId)
            } else {
                // Simply wait it out.
                appWidgetHolder?.deleteAppWidgetId(widgetId)
                pendingBindWidgetId = -1
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_EXTRA_WIDGET_ID, pendingBindWidgetId)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        pendingBindWidgetId = savedInstanceState.getInt(STATE_EXTRA_WIDGET_ID, pendingBindWidgetId)
    }

    override fun getDragLayer(): BaseDragLayer<*> {
        return dragLayer
    }

    override fun onSlideInViewClosed() {
        finish()
    }

    private fun setupNavBarColor() {
        val isSheetDark =
            (applicationContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        systemUiController?.updateUiState(
            SystemUiController.UI_STATE_BASE_WINDOW,
            if (isSheetDark) SystemUiController.FLAG_DARK_NAV else SystemUiController.FLAG_LIGHT_NAV,
        )
    }

    private fun sendWidgetAddedToScreenAccessibilityEvent(widgetName: String) {
        if (accessibilityManager.isEnabled) {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            event.contentDescription =
                applicationContext.resources.getString(
                    R.string.added_to_home_screen_accessibility_text,
                    widgetName,
                )
            accessibilityManager.sendAccessibilityEvent(event)
        }
    }

    private fun logCommand(command: StatsLogManager.EventEnum) {
        widgetCell?.let {
            statsLogManager.logger().withItemInfo(it.widgetView.tag as ItemInfo).log(command)
        }
    }

    override fun onPreviewAvailable() {
        // Set the preview height based on "the only" widget's preview.
        widgetCell?.let {
            it.setParentAlignedPreviewHeight(it.previewContentHeight)
            it.post { it.requestLayout() }
        }
    }

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() =
            OnBackPressedDispatcher().apply {
                if (Build.VERSION.SDK_INT >= 33) {
                    setOnBackInvokedDispatcher(onBackInvokedDispatcher)
                }
            }

    public override fun registerBackDispatcher() {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            this,
        )
    }

    override fun onBackInvoked() {
        finish()
    }

    companion object {
        private const val SHADOW_SIZE = 10

        private const val REQUEST_BIND_APPWIDGET = 1
        private const val STATE_EXTRA_WIDGET_ID = "state.widget.id"

        private class AddItemAsyncTask(widgetCell: WidgetCell, itemProvider: Supplier<WidgetItem>) :
            AsyncTask<Void, Void, WidgetItem>() {
            private val widgetCellRef = WeakReference(widgetCell)
            private val itemProviderRef = WeakReference(itemProvider)

            override fun doInBackground(vararg voids: Void): WidgetItem {
                return checkNotNull(itemProviderRef.get()).get()
            }

            override fun onPostExecute(item: WidgetItem) {
                widgetCellRef.get()?.applyFromCellItem(item)
            }
        }

        private fun PackageItemInfo.toPackageUserKey() =
            if (widgetCategory != -1) {
                PackageUserKey(widgetCategory, user)
            } else {
                PackageUserKey(packageName, user)
            }
    }
}

/** Interface for handling the add action in the Pin Item flow. */
interface PinItemAddHandler {
    /** Called when the user confirms adding the item (widget or shortcut). */
    fun onAddItemClicked()
}
