/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl
import com.intellij.openapi.application.ModalityState
import com.intellij.util.Alarm

class InitCoroutineDebuggerRequest(
    val context: DebuggerContextImpl,
    val myUpdateLabelsAlarm: Alarm,
    val isUpdateEnabled: Boolean = false
) : Runnable {
        override fun run() {
//            var updateScheduled = false
//            try {
//                if (isUpdateEnabled) {
//                    val tree = getCoroutinesTree()
//                    val root = tree.model.root as DebuggerTreeNodeImpl
//                    val process = context.debugProcess
//                    if (process != null) {
//                        process.managerThread.schedule(object : SuspendContextCommandImpl(context.suspendContext) {
//                            override fun contextAction() {
//                                try {
//                                    updateNodeLabels(root)
//                                } finally {
//                                    reschedule()
//                                }
//                            }
//
//                            override fun commandCancelled() {
//                                reschedule()
//                            }
//                        })
//                        updateScheduled = true
//                    }
//                }
//            } finally {
//                if (!updateScheduled) {
//                    reschedule()
//                }
//            }
        }

/*        private fun reschedule() {
            val session = context.debuggerSession
            if (session != null && session.isAttached && !session.isPaused && !myUpdateLabelsAlarm.isDisposed) {
                myUpdateLabelsAlarm.addRequest(
                    this,
                    CoroutinesPanel.LABELS_UPDATE_DELAY_MS, ModalityState.NON_MODAL
                )
            }
        }
*/
}
