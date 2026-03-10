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

package com.android.quickstep.cuebar.logger

import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.RenderToken
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.PublishedContextInsight
import android.service.personalcontext.insight.interaction.InsightEvent
import com.android.personalcontext.ace.common.FlatIndexUtils.flatIndexOf
import com.android.personalcontext.ace.common.PackedIntUtils.packValue
import com.android.personalcontext.ace.common.wrappers.wrap

/** Logger for Ambient Cue Ace insights. */
class AmbientCueAceLogger(private val personalContextManager: PersonalContextManager?) {
    var lastPublishedInsight: PublishedContextInsight? = null
        private set
    var lastRenderToken: RenderToken? = null
        private set

    fun onInsightReceived(insight: PublishedContextInsight, token: RenderToken) {
        lastPublishedInsight = insight
        lastRenderToken = token
    }

    fun reportCloseEvent() {
        val publishedInsight = lastPublishedInsight
        val token = lastRenderToken
        if (publishedInsight != null && token != null) {
            val rootInsight = publishedInsight.insight
            val flatIndex = publishedInsight.wrap().flatIndexOf(rootInsight)
            val packedEvent = InsightEvent.EVENT_USER_DISMISS.packValue(flatIndex)
            personalContextManager?.reportInsightEvent(
                publishedInsight,
                packedEvent,
                token
            )
        }
    }

    fun reportInsightEvent(
        childInsight: ContextInsight,
        event: Int
    ) {
        val publishedInsight = lastPublishedInsight
        val token = lastRenderToken
        if (publishedInsight != null && token != null) {
            val flatIndex = publishedInsight.wrap().flatIndexOf(childInsight)
            val packedEvent = event.packValue(flatIndex)
            personalContextManager?.reportInsightEvent(publishedInsight, packedEvent, token)
        }
    }
}
