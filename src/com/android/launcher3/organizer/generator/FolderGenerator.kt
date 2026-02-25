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
 * A concrete implementation of [Generator] that produces folders based on specific topics.
 *
 * It coordinates a three-step process:
 * 1. **Classification:** Uses a [Classifier] to analyze all candidate items.
 * 2. **Filtering:** Selects items matching the user's requested topics and sorts them by confidence
 *    score for each group.
 * 3. **Placement:** Uses a [FolderPlacer] to arrange the filtered items into folders by assigning
 *    sequential ranks.
 */
class FolderGenerator(
    private val classifier: Classifier,
    private val folderPlacer: FolderPlacer,
    private val items: List<ItemInfo>,
) : Generator {
    /**
     * Generates a list of folders matching the provided [topicGroups].
     *
     * @param topicGroups A list where each element is a set of strings representing the desired
     *   themes for a single folder.
     * @return A list of folders, where each folder is represented as a [List] of [ItemInfo] objects
     *   with their positions in the folder populated.
     */
    override suspend fun generate(topicGroups: List<Set<String>>): List<List<ItemInfo>> {
        if (topicGroups.isEmpty()) return emptyList()

        // 1. Classify all items to their best-match topics
        val topicClassifiedItems = classifier.classify(items)

        // 2. Filter to keep only items matching the requested topics and sort by score for each
        // group
        return topicGroups.map { topics ->
            val filteredItems =
                topicClassifiedItems
                    .asSequence()
                    .filter { topicItem -> topicItem.topic in topics }
                    .sortedByDescending { it.score }
                    .toList()

            // 3. Use the FolderPlacer to arrange them into a folder
            folderPlacer.place(filteredItems)
        }
    }
}
