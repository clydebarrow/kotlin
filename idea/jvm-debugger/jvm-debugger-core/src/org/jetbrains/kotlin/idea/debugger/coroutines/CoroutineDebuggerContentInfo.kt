/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import org.jetbrains.annotations.NonNls

class CoroutineDebuggerContentInfo {
    companion object {
        val COROUTINE_THREADS_CONTENT = "CoroutineThreadsContent"
        val VARIABLES_CONTENT = "CoroutineVariablesContent"
        val FRAME_CONTENT = "CoroutineFrameContent"
        val WATCHES_CONTENT = "CoroutineWatchesContent"
    }
}

class CoroutineDebuggerActions {
    companion object {
        @NonNls
        val COROUTINE_PANEL_POPUP: String = "Debugger.CoroutinesPanelPopup"
    }
}