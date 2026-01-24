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

package com.android.launcher3.taskbar

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.BadTokenException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.taskbar.SafeViewManager.Companion.BACKOFF_DELAYS_MS
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SafeViewManagerTest {

    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock lateinit var windowManager: WindowManager
    @Mock lateinit var rootLayout: View

    private lateinit var handler: Handler
    private val postedMessages = mutableListOf<Pair<Message, Long>>()

    private lateinit var viewManager: SafeViewManager

    private val params = WindowManager.LayoutParams()

    @Before
    fun setup() {
        handler = spy(Handler(Looper.getMainLooper()))
        doAnswer {
                postedMessages.add(it.getArgument<Message>(0) to it.getArgument(1))
                true
            }
            .whenever(handler)
            .sendMessageDelayed(any(), any())

        viewManager =
            SafeViewManager(
                windowManager = windowManager,
                rootLayout = rootLayout,
                handler = handler,
            )
    }

    @Test
    fun addView_adds_view_to_wm() {
        viewManager.addView(params)
        verify(windowManager, times(1)).addView(rootLayout, params)
    }

    @Test
    fun removeView_only_called_after_add_view() {
        viewManager.removeView()
        verify(windowManager, never()).removeViewImmediate(any())

        viewManager.addView(params)
        viewManager.removeView()

        verify(windowManager, times(1)).removeViewImmediate(any())

        viewManager.removeView()
        viewManager.removeView()
        viewManager.removeView()
        // No new calls
        verify(windowManager, times(1)).removeViewImmediate(any())
    }

    @Test
    fun addView_keeps_retrying() {
        doThrow(BadTokenException()).whenever(windowManager).addView(any(), any())

        viewManager.addView(params)
        verify(windowManager, times(1)).addView(rootLayout, params)

        // Verify scheduled task to add again
        for (i in 1..BACKOFF_DELAYS_MS.size) {
            assertThat(postedMessages).hasSize(i)

            val msg = postedMessages[i - 1]
            assertThat(msg.second).isEqualTo(BACKOFF_DELAYS_MS[i - 1])
            msg.first.callback.run()
            verify(windowManager, times(i + 1)).addView(rootLayout, params)
        }
    }

    @Test
    fun removeView_cancels_pending_messages() {
        doThrow(BadTokenException()).whenever(windowManager).addView(any(), any())

        viewManager.addView(params)
        verify(windowManager, times(1)).addView(rootLayout, params)

        viewManager.removeView()
        verify(windowManager, never()).removeViewImmediate(any())
        verify(handler, atLeastOnce()).removeCallbacksAndMessages(any())
    }
}
