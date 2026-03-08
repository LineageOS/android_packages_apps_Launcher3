/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.dagger;

import com.android.launcher3.concurrent.ExecutorsModule;
import com.android.launcher3.organizer.generator.GeneratorModule;
import com.android.launcher3.util.dagger.LauncherExecutorsModule;
import com.android.launcher3.widgetpicker.LauncherWidgetPickerModule;
import com.android.launcher3.workspacefunctions.WorkspaceFunctionsModule;

import dagger.Module;

@Module(
        includes = {
                WindowManagerProxyModule.class,
                ApiWrapperModule.class,
                PluginManagerWrapperModule.class,
                StaticObjectModule.class,
                WidgetModule.class,
                AppModule.class,
                ProductionAppModule.class,
                PerDisplayModule.class,
                ExecutorsModule.class,
                LauncherExecutorsModule.class,
                LauncherWidgetPickerModule.class,
                LauncherModelModule.class,
                SettingsModule.class,
                HomeScreenFilesModule.class,
                DesktopModule.class,
                AutomationModule.class,
                TaskOverlayModule.class,
                WorkspaceFunctionsModule.class,
                GeneratorModule.class,
        },
        subcomponents = ActivityContextComponent.class)
public class LauncherAppModule {
}
