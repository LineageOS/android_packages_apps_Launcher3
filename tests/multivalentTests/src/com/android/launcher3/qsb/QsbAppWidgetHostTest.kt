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

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.RoboApiWrapper
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.widget.LauncherWidgetHolder
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class QsbAppWidgetHostTest {

    @get:Rule val context = SandboxApplication()
    @get:Rule val widgetsPermission = RoboApiWrapper.grantWidgetBindPermissionRule()
    @get:Rule var mlimitDevicesRule: LimitDevicesRule = LimitDevicesRule()

    private val host = QsbAppWidgetHost(context).apply { deleteHost() }

    fun tearDown() {
        host.deleteHost()
    }

    @Test
    fun getBoundWidgetId_returns_invalid_if_nothing_bound() {
        assertEquals(INVALID_APPWIDGET_ID, host.getBoundWidgetId())
    }

    @Test
    /** Test runs on Robolectric but appWidgetIds has length of 0. */
    @SkipOnDeviceless
    fun getBoundWidgetId_returns_last_bound_widget() {
        host.allocateAppWidgetId()
        host.allocateAppWidgetId()
        host.allocateAppWidgetId()
        val last = host.allocateAppWidgetId()

        assertEquals(last, host.getBoundWidgetId())
        assertThat(host.appWidgetIds).hasLength(1)
    }

    @Test
    /**
     * Test runs on Robolectric but bindAppWidgetIdIfAllowed method return null since they are not
     * implemented in the Robolectric API.
     */
    @SkipOnDeviceless
    fun setActiveWidget_sends_updates() {
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        val widgetId = host.allocateAppWidgetId()
        assertTrue(
            AppWidgetManager.getInstance(context)
                .bindAppWidgetIdIfAllowed(widgetId, widgetInfo.provider)
        )

        var providerUpdated = false
        var viewsUpdated = false
        host.setCallbacks(
            object : QsbAppWidgetHost.Callbacks {
                override fun onProviderChanged(appWidget: AppWidgetProviderInfo?) {
                    providerUpdated = true
                }

                override fun onViewsChanged(views: RemoteViews?) {
                    // Provider should be updated before views
                    assertTrue(providerUpdated)

                    viewsUpdated = true
                }
            }
        )
        host.setActiveWidget(widgetId, widgetInfo)
        assertTrue(viewsUpdated)
        assertEquals(widgetId, host.getActiveWidgetId())

        assertEquals(host.appWidgetIds.toList(), intArrayOf(widgetId).toList())
    }

    @Test
    /**
     * Test runs on Robolectric but bindAppWidgetIdIfAllowed method return null since they are not
     * implemented in the Robolectric API.
     */
    @SkipOnDeviceless
    fun setActiveWidget_multiple_times_has_no_effect() {
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        val widgetId = host.allocateAppWidgetId()
        assertTrue(
            AppWidgetManager.getInstance(context)
                .bindAppWidgetIdIfAllowed(widgetId, widgetInfo.provider)
        )

        var updateReceived = false
        host.setCallbacks(
            object : QsbAppWidgetHost.Callbacks {
                override fun onProviderChanged(appWidget: AppWidgetProviderInfo?) {
                    updateReceived = true
                }

                override fun onViewsChanged(views: RemoteViews?) {
                    updateReceived = true
                }
            }
        )
        host.setActiveWidget(widgetId, widgetInfo)
        assertEquals(widgetId, host.getActiveWidgetId())
        assertTrue(updateReceived)

        updateReceived = false
        host.setActiveWidget(widgetId, widgetInfo)
        assertFalse(updateReceived)
        assertEquals(widgetId, host.getActiveWidgetId())
        assertEquals(host.appWidgetIds.toList(), intArrayOf(widgetId).toList())
    }

    @Test
    /**
     * Test runs on Robolectric but bindAppWidgetIdIfAllowed method return null since they are not
     * implemented in the Robolectric API.
     */
    @SkipOnDeviceless
    fun setActiveWidget_deletes_old_active_widget() {
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        val widgetId1 = host.allocateAppWidgetId()
        assertTrue(
            AppWidgetManager.getInstance(context)
                .bindAppWidgetIdIfAllowed(widgetId1, widgetInfo.provider)
        )

        host.setActiveWidget(widgetId1, widgetInfo)
        assertEquals(widgetId1, host.getActiveWidgetId())

        val widgetId2 = host.allocateAppWidgetId()
        assertTrue(
            AppWidgetManager.getInstance(context)
                .bindAppWidgetIdIfAllowed(widgetId2, widgetInfo.provider)
        )

        host.setActiveWidget(widgetId2, widgetInfo)
        assertEquals(widgetId2, host.getActiveWidgetId())

        assertEquals(host.appWidgetIds.toList(), intArrayOf(widgetId2).toList())
    }

    @Test
    fun host_id_is_non_overlapping() {
        assertNotEquals(QsbAppWidgetHost.HOST_ID, LauncherWidgetHolder.APPWIDGET_HOST_ID)
    }
}
