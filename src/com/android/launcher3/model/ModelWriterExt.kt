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
package com.android.launcher3.model

import androidx.annotation.WorkerThread
import com.android.launcher3.util.Preconditions
import java.util.function.Consumer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Schedules a block of code to be executed within a single database transaction
 * on the background model thread, suspending until the transaction is complete.
 *
 * This provides a more modern and convenient API for clients using Kotlin coroutines.
 *
 * @param block The block of code to execute within the transaction, receiving a
 *   [TransactionContext] handle for performing mutations.
 * @return [Unit] if the transaction completes successfully.
 * @throws RuntimeException if the scheduled transaction fails to complete.
 */
@WorkerThread
suspend fun IModelWriter.scheduleTransactionSuspending(
    block: (TransactionContext) -> Unit
) {
    Preconditions.assertNonUiThread()
    return suspendCancellableCoroutine { continuation ->
        scheduleTransaction(
            onComplete = { success ->
                if (success) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        RuntimeException("The scheduled transaction failed to complete.")
                    )
                }
            },
            block = Consumer { block(it) }
        )
    }
}
