/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import java.io.FileDescriptor
import java.io.PrintWriter

/** Service connected by system-UI for handling touch interaction. */
class TouchInteractionService : Service() {

    private lateinit var handler: TouchInteractionHandler

    override fun onCreate() {
        super.onCreate()
        handler = TouchInteractionHandler(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) =
        handler.onConfigurationChanged(newConfig)

    override fun onBind(intent: Intent): IBinder = handler.binder

    override fun onDestroy() {
        handler.onDestroy()
        super.onDestroy()
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<String>) =
        handler.dump(fd, writer, args)
}
