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

package com.android.launcher3.deviceprofile

// Remaining hotseat properties
//    int numShownHotseatIcons - updates multiple times
//    int hotseatCellHeightPx - updates multiple times
//    int mHotseatColumnSpan - updates multiple times
//    int mHotseatWidthPx - updates multiple times
//    int hotseatBarSizePx - updates multiple times
//    int hotseatQsbWidth - updates multiple times
//    int hotseatBorderSpace - updates multiple times

data class HotseatProfile(
    val areNavButtonsInline: Boolean,
    val navButtonsLayoutWidthPx: Int,
    val inlineNavButtonsEndSpacingPx: Int,
    val barEndOffset: Int,
    val springLoadedBarTopMarginPx: Int,
    val barEdgePaddingPx: Int,
    val barWorkspaceSpacePx: Int,
    val qsbHeight: Int,
    val qsbShadowHeight: Int,
    val qsbVisualHeight: Int,
    val minIconSpacePx: Int,
    val minQsbWidthPx: Int,
    val maxIconSpacePx: Int,
    val barBottomSpacePx: Int,
    val qsbSpace: Int,
) {

    companion object Factory {

        fun createHotseatProfile(
            hotseatProfileInitialValues: HotseatProfileInitialValues
        ): HotseatProfile {
            return HotseatProfile(
                areNavButtonsInline = hotseatProfileInitialValues.areNavButtonsInline,
                navButtonsLayoutWidthPx = hotseatProfileInitialValues.navButtonsLayoutWidthPx,
                inlineNavButtonsEndSpacingPx =
                    hotseatProfileInitialValues.inlineNavButtonsEndSpacingPx,
                barEndOffset = hotseatProfileInitialValues.barEndOffset,
                springLoadedBarTopMarginPx = hotseatProfileInitialValues.springLoadedBarTopMarginPx,
                barEdgePaddingPx = hotseatProfileInitialValues.barEdgePaddingPx,
                barWorkspaceSpacePx = hotseatProfileInitialValues.barWorkspaceSpacePx,
                qsbHeight = hotseatProfileInitialValues.qsbHeight,
                qsbShadowHeight = hotseatProfileInitialValues.qsbShadowHeight,
                qsbVisualHeight = hotseatProfileInitialValues.qsbVisualHeight,
                minIconSpacePx = hotseatProfileInitialValues.minIconSpacePx,
                minQsbWidthPx = hotseatProfileInitialValues.minQsbWidthPx,
                maxIconSpacePx = hotseatProfileInitialValues.maxIconSpacePx,
                barBottomSpacePx = hotseatProfileInitialValues.barBottomSpacePx,
                qsbSpace = hotseatProfileInitialValues.qsbSpace,
            )
        }
    }
}
