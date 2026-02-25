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

/**
 * Interface for generating structured content containers (e.g., screens or folders) based on
 * specific topics.
 */
interface Generator {
    /**
     * Generates a list of content containers based on groups of topics.
     *
     * @param topicGroups A list where each element is a set of strings representing the desired
     *   themes for a single container. The number of generated containers will match the size of
     *   this list.
     * @return A list of containers, where each container is represented as a [List] of [ItemInfo]s
     *   ready to be persisted or displayed.
     */
    suspend fun generate(topicGroups: List<Set<String>>): List<List<ItemInfo>>
}
