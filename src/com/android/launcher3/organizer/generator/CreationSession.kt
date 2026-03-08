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

package com.android.launcher3.organizer.generator

import com.android.launcher3.model.data.ItemInfo

/** A session to handle all aspects of space/folder creation from classification to generation. */
interface CreationSession {
    /**
     * Starts classification of all items.
     *
     * @return a list of [TopicClassifiedItem].
     */
    suspend fun startClassification(): List<TopicClassifiedItem>

    /**
     * Starts generation of screens or folders.
     *
     * @param selectedTopics A list of topics that were selected for generation.
     * @return a list of screens/folders, which are themselves represented by a list of [ItemInfo].
     */
    suspend fun startGeneration(selectedTopics: List<String>): List<List<ItemInfo>>

    /** Cancels a creation session. */
    suspend fun cancelSession()
}
