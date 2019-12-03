/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines.command

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.debugger.ui.impl.watch.*
import org.jetbrains.kotlin.idea.debugger.KotlinCoroutinesAsyncStackTraceProvider
import org.jetbrains.kotlin.idea.debugger.coroutines.*

class CoroutineBuildFrameCommand(
    node: DebuggerTreeNodeImpl,
    val descriptor: CoroutineDescriptorImpl,
    nodeManager: NodeManagerImpl,
    debuggerContext: DebuggerContextImpl
) : BuildCoroutineNodeCommand(node, debuggerContext, nodeManager) {

    override fun threadAction() {
        val debugProcess = debuggerContext.debugProcess ?: return
        val evalContext = debuggerContext.createEvaluationContext() ?: return
        val creationStackTraceSeparator = "\b\b\b" // the "\b\b\b" is used for creation stacktrace separator in kotlinx.coroutines

        when (descriptor.state.state) {
            CoroutineState.State.RUNNING -> {
                if (renderRunningCoroutine(debugProcess, evalContext)) return
            }
            CoroutineState.State.SUSPENDED -> {
                if (renderSuspendedCoroutine(creationStackTraceSeparator, evalContext)) return
            }
            else -> {
            }

        }
        val trace = descriptor.state.stackTrace
        val index = trace.indexOfFirst { it.className.startsWith(creationStackTraceSeparator) }
        myChildren.add(myNodeManager.createNode(CreationFramesDescriptor(trace.subList(index + 1, trace.size)), evalContext))
        updateUI(true)
    }

    private fun renderSuspendedCoroutine(
        creationStackTraceSeparator: String,
        evalContext: EvaluationContextImpl
    ): Boolean {
        val threadProxy = debuggerContext.suspendContext?.thread ?: return true
        val proxy = threadProxy.forceFrames().first()
        // the thread is paused on breakpoint - it has at least one frame
        for (it in descriptor.state.stackTrace) {
            if (it.className.startsWith(creationStackTraceSeparator)) break
            myChildren.add(createCoroutineFrameDescriptor(descriptor, evalContext, it, proxy))
        }
        return false
    }

    private fun renderRunningCoroutine(
        debugProcess: DebugProcessImpl,
        evalContext: EvaluationContextImpl
    ): Boolean {
        if (descriptor.state.thread == null) {
            myChildren.add(myNodeManager.createMessageNode("Frames are not available"))
            return true
        }
        val proxy = ThreadReferenceProxyImpl(
            debugProcess.virtualMachineProxy,
            descriptor.state.thread
        )
        val frames = proxy.forceFrames()
        var i = frames.lastIndex
        while (i > 0 && frames[i].location().method().name() != "resumeWith") i--
        // if i is less than 0, wait, what?
        for (frame in 0..--i) {
            myChildren.add(createFrameDescriptor(descriptor, evalContext, frames[frame]))
        }
        if (i > 0) { // add async stack trace if there are frames after invokeSuspend
            val async = KotlinCoroutinesAsyncStackTraceProvider().getAsyncStackTrace(
                JavaStackFrame(StackFrameDescriptorImpl(frames[i - 1], MethodsTracker()), true),
                evalContext.suspendContext
            )
            async?.forEach { myChildren.add(createAsyncFrameDescriptor(descriptor, evalContext, it, frames[0])) }
        }
        for (frame in i + 2..frames.lastIndex) {
            myChildren.add(createFrameDescriptor(descriptor, evalContext, frames[frame]))
        }
        return false
    }

    private fun createFrameDescriptor(
        descriptor: NodeDescriptorImpl,
        evalContext: EvaluationContext,
        frame: StackFrameProxyImpl
    ): DebuggerTreeNodeImpl {
        return myNodeManager.createNode(
            myNodeManager.getStackFrameDescriptor(descriptor, frame),
            evalContext
        )
    }

    private fun createCoroutineFrameDescriptor(
        descriptor: CoroutineDescriptorImpl,
        evalContext: EvaluationContext,
        frame: StackTraceElement,
        proxy: StackFrameProxyImpl,
        parent: NodeDescriptorImpl? = null
    ): DebuggerTreeNodeImpl {
        return myNodeManager.createNode(
            myNodeManager.getDescriptor(
                parent,
                CoroutineStackFrameData(descriptor.state, frame, proxy)
            ), evalContext
        )
    }

    private fun createAsyncFrameDescriptor(
        descriptor: CoroutineDescriptorImpl,
        evalContext: EvaluationContext,
        frame: StackFrameItem,
        proxy: StackFrameProxyImpl
    ): DebuggerTreeNodeImpl {
        return myNodeManager.createNode(
            myNodeManager.getDescriptor(
                descriptor,
                CoroutineStackFrameData(descriptor.state, frame, proxy)
            ), evalContext
        )
    }
}
