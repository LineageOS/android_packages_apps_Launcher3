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
package com.android.launcher3.workspacefunctions

import com.android.launcher3.Flags
import com.android.launcher3.appfunctions.workspace.HotseatSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceAppFunctions
import com.android.launcher3.appfunctions.workspace.WorkspaceRepository
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTransaction
import dagger.Binds
import dagger.Module
import dagger.Provides

/** Dagger module for binding workspace functions interfaces. */
@Module
abstract class WorkspaceFunctionsModule {

    /** Binds the concrete implementation of the repository to its interface. */
    @Binds abstract fun bindWorkspaceRepository(impl: WorkspaceRepositoryImpl): WorkspaceRepository

    /**
     * A dummy implementation of [WorkspaceRepository] that returns an empty workspace and throws
     * exceptions on any mutation.
     *
     * This is used when the Kondo planner is not enabled.
     */
    private class DummyWorkspaceRepository : WorkspaceRepository {
        override suspend fun getWorkspace(): WorkspaceSpec {
            return WorkspaceSpec(
                screens = listOf(),
                hotseat = HotseatSpec(listOf()),
                rows = null,
                columns = null,
            )
        }

        override fun newTransaction(): WorkspaceTransaction {
            throw UnsupportedOperationException("Not implemented")
        }
    }

    companion object {

        @Provides
        fun provideWorkspaceAppFunctions(repository: WorkspaceRepository): WorkspaceAppFunctions {
            val kondoPlannerEnabled = Flags.kondoPlanner()
            return WorkspaceAppFunctions(
                if (kondoPlannerEnabled) repository else DummyWorkspaceRepository()
            )
        }
    }
}
