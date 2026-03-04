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

import com.android.launcher3.appfunctions.workspace.WorkspaceRepository
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTransaction
import javax.inject.Inject

/**
 * The concrete implementation of the [WorkspaceRepository].
 *
 * This class acts as the bridge between the abstract AppFunctions API and the concrete launcher
 * model. It will use the [LauncherWorkspaceProvider] to read data and the `IModelWriter` (not yet
 * injected) to handle transactions.
 */
class WorkspaceRepositoryImpl
@Inject
constructor(
    private val provider: LauncherWorkspaceProvider,
    private val translator: LauncherWorkspaceTypeTranslator,
) : WorkspaceRepository {

    override suspend fun getWorkspace(): WorkspaceSpec {
        val workspace = provider.getWorkspace()
        return translator.toSpec(workspace)
    }

    override fun newTransaction(): WorkspaceTransaction {
        TODO("Not yet implemented")
    }
}
