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
package com.android.launcher3.deviceprofile

import android.content.res.Resources
import com.android.launcher3.R

data class SysuiProfile(
    // Space required for the bubble bar between the hotseat and the edge of the screen. If there's
    // not enough space, the hotseat will adjust itself for the bubble bar.
    @JvmField val mBubbleBarSpaceThresholdPx: Int = 0,

    // Split staging
    @JvmField var splitPlaceholderInset: Int = 0,
) {
    companion object Factory {
        fun createSysuiProfile(res: Resources): SysuiProfile {
            return SysuiProfile(
                mBubbleBarSpaceThresholdPx =
                    res.getDimensionPixelSize(R.dimen.bubblebar_hotseat_adjustment_threshold),
                splitPlaceholderInset = res.getDimensionPixelSize(R.dimen.split_placeholder_inset),
            )
        }
    }
}
