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

package com.android.quickstep

import android.content.Context
import com.android.extensions.computercontrol.AutomatedPackageListener
import com.android.extensions.computercontrol.ComputerControlExtensions
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import java.util.concurrent.Executor
import javax.inject.Inject

@LauncherAppSingleton
class AutomationRepositoryImpl
@Inject
constructor(
    @ApplicationContext context: Context,
    computerControlExtensions: ComputerControlExtensions?,
    @LightweightBackground(LightweightBackgroundPriority.DATA) packageListenerExecutor: Executor,
) : AutomationRepository {

    private var automatedPackages = mutableListOf<String>()
    private val automatedPackageListener = AutomatedPackageListener { _, packages, _ ->
        automatedPackages.clear()
        automatedPackages.addAll(packages)
    }

    init {
        computerControlExtensions?.registerAutomatedPackageListener(
            context,
            packageListenerExecutor,
            automatedPackageListener,
        )
    }

    override fun isPackageAutomated(packageName: String): Boolean =
        automatedPackages.contains(packageName)
}
