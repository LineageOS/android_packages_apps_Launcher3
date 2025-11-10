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

package com.android.launcher3.dagger

import com.android.app.displaylib.PerDisplayRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.function.Consumer

/** A repository that provide object based on [PerDisplayComponent] */
class PerDisplayComponentRepository<T>
@AssistedInject
constructor(
    @Assisted override val debugName: String,
    @Assisted private val objectGetter: (PerDisplayComponent) -> T,
    private val perDisplayComponentRepository: PerDisplayRepository<PerDisplayComponent>,
) : PerDisplayRepository<T> {
    override fun get(displayId: Int): T? =
        perDisplayComponentRepository[displayId]?.let { objectGetter(it) }

    override fun getOrDefault(displayId: Int): T =
        objectGetter(perDisplayComponentRepository.getOrDefault(displayId))

    override fun forEach(createIfAbsent: Boolean, action: Consumer<T>) {
        perDisplayComponentRepository.forEach(createIfAbsent) { action.accept(objectGetter(it)) }
    }

    @AssistedFactory
    interface Factory<T> {
        fun create(
            debugName: String,
            objectGetter: (PerDisplayComponent) -> T,
        ): PerDisplayComponentRepository<T>
    }
}
