/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.actions.ThreadDumpAction
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.configurations.DebuggingRunnerData
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.util.messages.MessageBusConnection
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import org.jetbrains.kotlin.idea.KotlinBundle
import java.util.concurrent.atomic.AtomicInteger


class ProjectCoroutineConnectionListener(val project: Project) : XDebuggerManagerListener {
    var connection: MessageBusConnection? = null
    val processCounter = AtomicInteger(0)

    private fun connect() {
        connection = project.messageBus.connect()
        connection?.subscribe(XDebuggerManager.TOPIC, this)
    }

    fun configurationStarting(
        runnerSettings: DebuggingRunnerData,
        configuration: RunConfigurationBase<*>
    ) {
        if (processCounter.compareAndSet(0, 1))
            connect()
        else
            processCounter.incrementAndGet()
    }

    override fun processStarted(debugProcess: XDebugProcess) {
        DebuggerInvocationUtil.swingInvokeLater(project) {
            if (debugProcess is JavaDebugProcess)
                registerCoroutinesPanel(debugProcess.session, debugProcess.debuggerSession)
        }
    }

    override fun processStopped(debugProcess: XDebugProcess) {
        if (processCounter.compareAndSet(1, 0)) {
            connection?.disconnect()
            connection = null
        } else
            processCounter.decrementAndGet()
    }

    /**
     * Adds panel to XDebugSessionTab
     */
    private fun registerCoroutinesPanel(session: XDebugSession, debuggerSession: DebuggerSession): Boolean {
        val ui = session.ui ?: return false
        val panel = CoroutinesPanel(project, debuggerSession.contextManager)
        // evaluation of `debuggerSession.contextManager.toString()` leads to
        // java.lang.Throwable: Assertion failed: Should be invoked in manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke
        // toString() is not allowed here, that is conflicts with debugger only

        val content = ui.createContent(
            CoroutineDebuggerContentInfo.COROUTINE_THREADS_CONTENT,
            panel,
            KotlinBundle.message("debugger.session.tab.coroutine.title"),
            AllIcons.Debugger.ThreadGroup,
            panel)
        content.isCloseable = false
        ui.addContent(content, 0, PlaceInGrid.left, true)
        ui.addListener(object : ContentManagerAdapter() {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.content === content) {
                    if (content.isSelected) {
                        panel.setUpdateEnabled(true)
                        if (panel.isRefreshNeeded) {
                            panel.rebuildIfVisible(DebuggerSession.Event.CONTEXT)
                        }
                    } else {
                        panel.setUpdateEnabled(false)
                    }
                }
            }
        }, content)
        // add coroutine dump button: due to api problem left toolbar is copied, modified and reset to tab
        val runnerContent = (ui.options as RunnerLayoutUiImpl).getData(RunnerContentUi.KEY.name) as RunnerContentUi
        val modifiedActions = runnerContent.getActions(true)
        val pos = modifiedActions.indexOfLast { it is ThreadDumpAction }
        modifiedActions.add(pos + 1, ActionManager.getInstance().getAction("Kotlin.XDebugger.CoroutinesDump"))
        ui.options.setLeftToolbar(DefaultActionGroup(modifiedActions), ActionPlaces.DEBUGGER_TOOLBAR)
        return true
    }
}