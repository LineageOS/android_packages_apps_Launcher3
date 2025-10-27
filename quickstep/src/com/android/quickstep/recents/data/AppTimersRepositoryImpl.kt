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

package com.android.quickstep.recents.data

import android.content.pm.LauncherApps
import android.os.UserHandle
import com.android.launcher3.concurrent.annotations.Background
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * An [AppTimersRepository] that uses [LauncherApps] service to get information about app timers.
 */
class AppTimersRepositoryImpl
@Inject
constructor(
    private val dataSource: LauncherApps,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : AppTimersRepository {

    /** Returns the remaining time on the app usage timer set by the user. */
    override suspend fun getRemainingDuration(
        packageName: String,
        userHandle: UserHandle,
    ): Duration? =
        withContext(backgroundDispatcher) {
            val appUsageLimit =
                dataSource.getAppUsageLimit(packageName, userHandle) ?: return@withContext null

            Duration.ofMillis(appUsageLimit.usageRemaining)
        }
}
