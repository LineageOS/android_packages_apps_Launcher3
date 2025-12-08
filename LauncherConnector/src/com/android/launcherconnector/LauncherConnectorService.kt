package com.android.launcherconnector

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

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.android.launcherconnector.proto.LauncherConnectorGrpc
import io.grpc.Server
import io.grpc.binder.AndroidComponentAddress
import io.grpc.binder.BinderServerBuilder
import io.grpc.binder.IBinderReceiver
import io.grpc.binder.ServerSecurityPolicy
import io.grpc.binder.UntrustedSecurityPolicies
import java.io.IOException

class LauncherConnectorService : Service() {

    private var server: Server? = null
    private val binderReceiver = IBinderReceiver()

    override fun onCreate() {
        super.onCreate()
        // Proper policy to be added by extenders of this service
        val serverSecurityPolicy =
            ServerSecurityPolicy.newBuilder()
                .servicePolicy(
                    LauncherConnectorGrpc.SERVICE_NAME,
                    UntrustedSecurityPolicies.untrustedPublic(),
                )
                .build()
        server =
            BinderServerBuilder.forAddress(AndroidComponentAddress.forContext(this), binderReceiver)
                .addService(LauncherConnectorImpl())
                .securityPolicy(serverSecurityPolicy)
                .build()
        try {
            server?.start()
        } catch (e: IOException) {
            throw IllegalStateException("Failed to start server", e)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return binderReceiver.get()
    }

    override fun onDestroy() {
        server?.shutdownNow()
        super.onDestroy()
    }
}
