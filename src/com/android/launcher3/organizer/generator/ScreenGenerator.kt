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
 * A concrete implementation of [Generator] that produces home screen layouts based on specific
 * topics.
 *
 * It coordinates a three-step process:
 * 1. **Classification:** Uses a [Classifier] to analyze all candidate items.
 * 2. **Filtering:** Selects items matching the user's requested topics and sorts them by confidence
 *    score.
 * 3. **Placement:** Uses a [Placer] and a set of [Template]s to arrange the filtered items into
 *    valid grid positions across multiple screens.
 */
class ScreenGenerator(
    private val classifier: Classifier,
    private val placer: Placer,
    private val templates: List<Template>,
    private val items: List<ItemInfo>,
) : Generator {
    /**
     * Generates a list of screens matching the provided [topicGroups].
     *
     * @param topicGroups A list where each element is a set of strings representing the desired
     *   themes for a single screen.
     * @return A list of screens, where each screen is a list of [ItemInfo] objects with their
     *   on-screen positions populated.
     */
    override suspend fun generate(topicGroups: List<Set<String>>): List<List<ItemInfo>> {
        if (topicGroups.isEmpty()) return emptyList()

        // 1. Classify all items to their best-match topics
        val topicClassifiedItems = classifier.classify(items)

        // 2. Prepare filtered items for each container
        val itemsByContainer =
            topicGroups.map { topics ->
                topicClassifiedItems
                    .filter { topicItem -> topicItem.topic in topics }
                    .sortedByDescending { it.score }
            }

        // 3. Use the Placer to arrange them
        return placer.place(itemsByContainer, templates)
    }
}
