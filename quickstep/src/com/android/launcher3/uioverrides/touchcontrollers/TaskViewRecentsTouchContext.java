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

package com.android.launcher3.uioverrides.touchcontrollers;

import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.statemanager.BaseState;

/** Interface providing context about the RecentsView state to a {@link TaskViewTouchController}. */
public interface TaskViewRecentsTouchContext {

    /**
     * Returns whether the RecentsViewContainer is in a state where TaskViews are interactive for
     * touch
     */
    default boolean isTaskViewInteractive() {
        return getContainerState().isTaskViewInteractive();
    }

    /** Returns the state that the recents view container is currently in. */
    BaseState<?> getContainerState();

    /** Runs when a user controlled animation is created. */
    default void onUserControlledAnimationCreated(AnimatorPlaybackController animController) {
    }
}
