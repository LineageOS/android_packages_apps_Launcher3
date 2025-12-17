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

package com.android.launcher3.taskbar

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.service.personalcontext.hint.BundleHint
import android.service.personalcontext.hint.ContextHint
import android.service.personalcontext.hint.ContextHintWithSignature
import android.service.personalcontext.hint.ConversationEvent
import android.service.personalcontext.hint.ConversationHint
import android.service.personalcontext.insight.ActionableInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.InsightActionDetails
import android.service.personalcontext.insight.InsightCollection
import android.service.personalcontext.insight.InsightDisplayDetails
import android.view.autofill.AutofillManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.quickstep.cuebar.data.ActionModel
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl.Companion.MA_ACTION_TYPE_NAME
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl.Companion.MR_ACTION_TYPE_NAME
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl.Companion.RENDER_IN_CUE_BAR
import com.android.quickstep.cuebar.logger.AmbientCueLogger
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class AmbientCueRepositoryTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    private val taskbarActivityContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext
    @Mock private lateinit var mockTaskbarContext: TaskbarActivityContext
    @Mock private lateinit var mockAmbientCueLogger: AmbientCueLogger
    @Mock private lateinit var mockBgExecutor: Executor
    @Mock private lateinit var mockUiExecutor: Executor
    @Mock private lateinit var mockAutofillManager: AutofillManager
    @Mock private lateinit var mockDrawable: Drawable
    @Mock private lateinit var mockMutatedDrawable: Drawable

    private lateinit var repositoryImpl: AmbientCueRepositoryImpl
    private lateinit var repository: AmbientCueRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        repositoryImpl = AmbientCueRepositoryImpl(taskbarActivityContext, mockAmbientCueLogger,
            mockBgExecutor, mockUiExecutor)
        `when`(mockTaskbarContext.getSystemService(AutofillManager::class.java))
            .thenReturn(mockAutofillManager)
        `when`(mockDrawable.mutate()).thenReturn(mockMutatedDrawable)
        `when`(mockTaskbarContext.getDrawable(anyInt())).thenReturn(mockDrawable)
        `when`(mockTaskbarContext.resources).thenReturn(
            mock(android.content.res.Resources::class.java))
        doNothing().`when`(mockTaskbarContext).startActivity(any())
        repository = spy(repositoryImpl)
        doAnswer { invocation -> (invocation.getArgument(0) as Runnable).run(); null }
            .`when`(mockBgExecutor).execute(any())
        doAnswer { invocation -> (invocation.getArgument(0) as Runnable).run(); null }
            .`when`(mockUiExecutor).execute(any())
    }

    /** Mocks a ContextInsight with the given ContextHints. */
    private fun mockInsight(vararg hints: ContextHint): ContextInsight {
        val insight = mock(ActionableInsight::class.java)
        val originHintList = hints.map { contextHint ->
            mock(ContextHintWithSignature::class.java).apply {
                `when`(this.contextHint).thenReturn(contextHint)
            }
        }
        `when`(insight.originHints).thenReturn(originHintList.toSet())
        return insight
    }

    /** Mocks the common display details for an insight. */
    private fun mockInsightDisplayDetails(): InsightDisplayDetails {
        val displayDetails = mock(InsightDisplayDetails::class.java)
        val mockIcon = mock(android.graphics.drawable.Icon::class.java)
        `when`(displayDetails.icon).thenReturn(mockIcon)

        val titleSequence = mock(CharSequence::class.java)
        `when`(titleSequence.toString()).thenReturn(TITLE)
        `when`(displayDetails.title).thenReturn(titleSequence)

        val subtitleSequence = mock(CharSequence::class.java)
        `when`(subtitleSequence.toString()).thenReturn(SUBTITLE)
        `when`(displayDetails.subtitle).thenReturn(subtitleSequence)

        return displayDetails
    }

    private fun mockActionableInsight(): ActionableInsight {
        val insight = mock(ActionableInsight::class.java)
        val displayDetails = mockInsightDisplayDetails()
        `when`(insight.displayDetails).thenReturn(displayDetails)
        val actionDetails = mock(InsightActionDetails::class.java)
        `when`(actionDetails.hasActionType(InsightActionDetails.ACTION_TYPE_REMOTE_ACTION))
            .thenReturn(true)
        val actionIntent = mock(Intent::class.java)
        `when`(actionIntent.extras).thenReturn(Bundle())
        `when`(actionDetails.createActionIntent()).thenReturn(actionIntent)
        val remoteAction = mock(android.app.RemoteAction::class.java)
        val remoteActionIntent = mock(android.app.PendingIntent::class.java)
        `when`(actionDetails.remoteAction).thenReturn(remoteAction)
        `when`(remoteAction.actionIntent).thenReturn(remoteActionIntent)
        `when`(insight.actionDetails).thenReturn(actionDetails)
        return insight
    }

    private fun mockDisplayInsight(): DisplayInsight {
        val insight = mock(DisplayInsight::class.java)
        val displayDetails = mockInsightDisplayDetails()
        `when`(insight.details).thenReturn(displayDetails)
        return insight
    }

    /** Asserts the result list contains exactly one ActionModel and returns it. */
    private fun getSingleActionModel(result: List<ActionModel>, expectedActionTypeName: String):
            ActionModel {
        assertThat(result).hasSize(1)
        val actionModel = result.first()
        assertThat(actionModel.actionType).isEqualTo(expectedActionTypeName)
        return actionModel
    }

    @Test
    fun mapInsightToActions_bundleHint_callsMapContextInsightToAction() {
        val bundle = Bundle().apply { putBoolean(RENDER_IN_CUE_BAR, true) }
        val bundleHint = mock(BundleHint::class.java).apply {
            `when`(dataBundle).thenReturn(bundle)
        }
        val insight = mockInsight(bundleHint)
        doReturn(listOf(mock(ActionModel::class.java)))
            .`when`(repository).mapContextInsightToAction(any(), any())

        val result = repository.mapInsightToActions(insight)

        verify(repository).mapContextInsightToAction(insight, bundleHint)
        assertThat(result).isNotEmpty()
    }

    @Test
    fun mapInsightToActions_conversationHint_callsMapContextInsightToAction() {
        val conversationHint = mock(ConversationHint::class.java).apply {
            `when`(conversationEvent).thenReturn(mock(ConversationEvent::class.java))
        }
        val insight = mockInsight(conversationHint)
        doReturn(listOf(mock(ActionModel::class.java)))
            .`when`(repository).mapContextInsightToAction(any(), any())

        val result = repository.mapInsightToActions(insight)

        verify(repository).mapContextInsightToAction(insight, conversationHint)
        assertThat(result).isNotEmpty()
    }

    @Test
    fun mapContextInsightToAction_actionableInsight_remoteAction_createsMAModel() {
        val insight = mockActionableInsight()
        val conversationHint = mock(ConversationHint::class.java).apply {
            `when`(conversationEvent).thenReturn(mock(ConversationEvent::class.java))
        }
        val result = repository.mapContextInsightToAction(insight, conversationHint)
        val actionModel = getSingleActionModel(result, MA_ACTION_TYPE_NAME)

        actionModel.onPerformAction.invoke()

        verify(insight.actionDetails.remoteAction?.actionIntent)?.send(any<Bundle>())
        verify(mockAmbientCueLogger).setFulfilledWithMaStatus()
        verify(mockAmbientCueLogger, never()).setFulfilledWithMrStatus()
    }

    @Test
    fun mapContextInsightToAction_displayInsight_conversationHint_createsMRModel() {
        val insight = mockDisplayInsight()
        val conversationHint = mock(ConversationHint::class.java).apply {
            `when`(conversationEvent).thenReturn(mock(ConversationEvent::class.java))
        }
        val result = repository.mapContextInsightToAction(insight, conversationHint)
        val actionModel = getSingleActionModel(result, MR_ACTION_TYPE_NAME)

        actionModel.onPerformAction.invoke()

        verify(mockAmbientCueLogger).setFulfilledWithMrStatus()
        verify(mockAmbientCueLogger, never()).setFulfilledWithMaStatus()
    }

    @Test
    fun mapInsightToActions_noRenderableHint_returnsEmptyList() {
        val irrelevantHint = mock(ContextHint::class.java)
        val insight = mockInsight(irrelevantHint)
        doReturn(emptyList<ActionModel>())
            .`when`(repository).mapContextInsightToAction(any(), any())

        val result = repository.mapInsightToActions(insight)

        verify(repository, never()).mapContextInsightToAction(any(), any())
        assertThat(result).isEmpty()
    }

    @Test
    fun mapContextInsightToAction_nestedInsightCollection_flattensAndMapsChildren() {
        val actionableInsight = mockActionableInsight()
        val displayInsight = mockDisplayInsight()
        val nestedCollection = InsightCollection.Builder()
            .addInsight(displayInsight)
            .build()
        val rootCollection = InsightCollection.Builder()
            .addInsight(actionableInsight)
            .addInsight(nestedCollection)
            .build()
        val conversationHint = mock(ConversationHint::class.java).apply {
            `when`(conversationEvent).thenReturn(mock(ConversationEvent::class.java))
        }

        val result = repository.mapContextInsightToAction(rootCollection, conversationHint)

        assertThat(result).hasSize(2)
        assertThat(result[0].actionType).isEqualTo(MA_ACTION_TYPE_NAME)
        assertThat(result[1].actionType).isEqualTo(MR_ACTION_TYPE_NAME)
    }

    private companion object {
        const val TITLE = "title"
        const val SUBTITLE = "subtitle"
    }
}
