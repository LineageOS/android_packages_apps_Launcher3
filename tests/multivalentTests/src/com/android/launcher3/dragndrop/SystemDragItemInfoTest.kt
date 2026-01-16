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

package com.android.launcher3.dragndrop

import androidx.test.filters.SmallTest
import com.android.launcher3.util.LauncherMultivalentJUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Tests for {@link SystemDragItemInfo}. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class SystemDragItemInfoTest {

    @Test
    fun testEmptyPayloadIsNotAcceptable() {
        assertFalse(SystemDragItemInfo.EmptyPayload.isAcceptable())
    }

    @Test
    fun testUriListPayloadIsAcceptableWhenFullyPopulated() {
        val payload = SystemDragItemInfo.UriListPayload(mock(), listOf(mock()))
        assertTrue(payload.isAcceptable())
    }

    @Test
    fun testUriListPayloadIsNotAcceptableWhenPermissionsAreNull() {
        val payload = SystemDragItemInfo.UriListPayload(null, listOf(mock()))
        assertFalse(payload.isAcceptable())
    }

    @Test
    fun testUriListPayloadIsNotAcceptableWhenUriListIsEmpty() {
        val payload = SystemDragItemInfo.UriListPayload(mock(), emptyList())
        assertFalse(payload.isAcceptable())
    }

    @Test
    fun testUriListPayloadIsNotAcceptableDropWhenUriListIsNull() {
        val payload = SystemDragItemInfo.UriListPayload(mock(), null)
        assertFalse(payload.isAcceptable())
    }
}
