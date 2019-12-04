/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.descriptors.data.DescriptorData
import com.intellij.debugger.impl.descriptors.data.DisplayKey
import com.intellij.debugger.impl.descriptors.data.SimpleDisplayKey
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.debugger.ui.impl.watch.MethodsTracker
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.sun.jdi.*
import javaslang.control.Either
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.isSubtype
import javax.swing.Icon

/**
 * Describes coroutine itself in the tree (name: STATE), has children if stacktrace is not empty (state = CREATED)
 */
class CoroutineData(private val state: CoroutineState) : DescriptorData<CoroutineDescriptorImpl>() {

    override fun createDescriptorImpl(project: Project): CoroutineDescriptorImpl {
        return CoroutineDescriptorImpl(state)
    }

    override fun equals(other: Any?) = if (other !is CoroutineData) false else state.name == other.state.name

    override fun hashCode() = state.name.hashCode()

    override fun getDisplayKey(): DisplayKey<CoroutineDescriptorImpl> = SimpleDisplayKey(state.name)
}

class CoroutineDescriptorImpl(val state: CoroutineState) : NodeDescriptorImpl() {
    var suspendContext: SuspendContextImpl? = null
    lateinit var icon: Icon

    override fun getName(): String? {
        return state.name
    }

    @Throws(EvaluateException::class)
    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener): String {
        val name = if (state.thread != null) state.thread.name().substringBefore(" @${state.name}") else ""
        val threadState = if (state.thread != null) DebuggerUtilsEx.getThreadStatusText(state.thread.status()) else ""
        return "${state.name}: ${state.state}${if (name.isNotEmpty()) " on thread \"$name\":$threadState" else ""}"
    }

    override fun isExpandable(): Boolean {
        return state.state != CoroutineState.State.CREATED
    }

    private fun calcIcon() = when {
        state.isSuspended() -> AllIcons.Debugger.ThreadSuspended
        state.isCreated() -> AllIcons.Debugger.ThreadStates.Idle
        else -> AllIcons.Debugger.ThreadRunning
    }

    override fun setContext(context: EvaluationContextImpl?) {
        icon = calcIcon()
    }
}

class CoroutineStackTraceData(state: CoroutineState, proxy: StackFrameProxyImpl, evalContext: EvaluationContextImpl, val frame: StackTraceElement)
    : CoroutineStackData(state, proxy, evalContext) {


    override fun hashCode(): Int {
        return frame.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is CoroutineStackTraceData && frame == other.frame
    }

    override fun createDescriptorImpl(project: Project): NodeDescriptorImpl {
        // check whether last fun is suspend fun
//        val suspendContext =
//            DebuggerManagerEx.getInstanceEx(project).context.suspendContext ?: return EmptyStackFrameDescriptor(
//                frame,
//                proxy
//            )
//        val suspendProxy = suspendContext.frameProxy ?: return EmptyStackFrameDescriptor(
//            frame,
//            proxy
//        )
//        val evalContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)
        val context = ExecutionContext(evalContext, proxy)
        val clazz = context.findClass(frame.className) as ClassType
        val method = clazz.methodsByName(frame.methodName).last {
            val loc = it.location().lineNumber()
            loc < 0 && frame.lineNumber < 0 || loc > 0 && loc <= frame.lineNumber
        } // pick correct method if an overloaded one is given

        val stackFrameDescriptor = createStackFrameDescriptor(method, context)
        return stackFrameDescriptor
    }

    private fun createStackFrameDescriptor(method: Method, context: ExecutionContext): NodeDescriptorImpl {
        // retrieve continuation only if suspend method
        val continuation = if (suspendOrInvokeSuspend(method)) getContinuation(frame, context) else null

        return if (continuation is ObjectReference)
            SuspendStackFrameDescriptor(state,frame, proxy, continuation)
        else
            EmptyStackFrameDescriptor(frame, proxy)
    }

    private fun suspendOrInvokeSuspend(method: Method): Boolean =
        "Lkotlin/coroutines/Continuation;)" in method.signature() ||
                method.name() == "invokeSuspend" &&
                method.signature() == "(Ljava/lang/Object;)Ljava/lang/Object;" // suspend fun or invokeSuspend


    /**
     * Find continuation for the [stackTraceElement]
     * Gets current CoroutineInfo.lastObservedFrame and finds next frames in it until null or needed stackTraceElement is found
     * @return null if matching continuation is not found or is not BaseContinuationImpl
     */
    fun getContinuation(stackTraceElement: StackTraceElement, context: ExecutionContext): ObjectReference? {
        var continuation = state.frame ?: return null
        val baseType = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
        val getTrace = (continuation.type() as ClassType).concreteMethodByName(
            "getStackTraceElement",
            "()Ljava/lang/StackTraceElement;"
        )
        val stackTraceType = context.findClass("java.lang.StackTraceElement") as ClassType
        val getClassName = stackTraceType.concreteMethodByName("getClassName", "()Ljava/lang/String;")
        val getLineNumber = stackTraceType.concreteMethodByName("getLineNumber", "()I")
        val className = {
            val trace = context.invokeMethod(continuation, getTrace, emptyList()) as? ObjectReference
            if (trace != null)
                (context.invokeMethod(trace, getClassName, emptyList()) as StringReference).value()
            else null
        }
        val lineNumber = {
            val trace = context.invokeMethod(continuation, getTrace, emptyList()) as? ObjectReference
            if (trace != null)
                (context.invokeMethod(trace, getLineNumber, emptyList()) as IntegerValue).value()
            else null
        }

        while (continuation.type().isSubtype(baseType)
            && (stackTraceElement.className != className() || stackTraceElement.lineNumber != lineNumber())
        ) {
            // while continuation is BaseContinuationImpl and it's frame equals to the current
            continuation = getNextFrame(continuation, context) ?: return null
        }
        return if (continuation.type().isSubtype(baseType)) continuation else null
    }


    /**
     * Finds previous Continuation for this Continuation (completion field in BaseContinuationImpl)
     * @return null if given ObjectReference is not a BaseContinuationImpl instance or completion is null
     */
    private fun getNextFrame(continuation: ObjectReference, context: ExecutionContext): ObjectReference? {
        val type = continuation.type() as ClassType
        if (!type.isSubtype("kotlin.coroutines.jvm.internal.BaseContinuationImpl")) return null
        val next = type.concreteMethodByName("getCompletion", "()Lkotlin/coroutines/Continuation;")
        return context.invokeMethod(continuation, next, emptyList()) as? ObjectReference
    }
}

class CoroutineStackFrameData(state: CoroutineState, proxy: StackFrameProxyImpl, evalContext: EvaluationContextImpl, val frame: StackFrameItem)
    : CoroutineStackData(state, proxy, evalContext) {
    override fun createDescriptorImpl(project: Project): NodeDescriptorImpl = AsyncStackFrameDescriptor(state, frame, proxy)


    override fun hashCode(): Int {
        return frame.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is CoroutineStackFrameData && frame == other.frame
    }
}

abstract class CoroutineStackData(val state: CoroutineState, val proxy: StackFrameProxyImpl, val evalContext: EvaluationContextImpl) : DescriptorData<NodeDescriptorImpl>() {

    /**
     * Returns [EmptyStackFrameDescriptor], [SuspendStackFrameDescriptor]
     * or [AsyncStackFrameDescriptor] according to current frame
     */

    override fun getDisplayKey(): DisplayKey<NodeDescriptorImpl> = SimpleDisplayKey(state)
}

/**
 * Descriptor for suspend functions
 */
class SuspendStackFrameDescriptor(
    val state: CoroutineState,
    val frame: StackTraceElement,
    proxy: StackFrameProxyImpl,
    val continuation: ObjectReference
) :
    StackFrameDescriptorImpl(proxy, MethodsTracker()) {
    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?): String {
        return with(frame) {
            val pack = className.substringBeforeLast(".", "")
            "$methodName:$lineNumber, ${className.substringAfterLast(".")} " +
                    if (pack.isNotEmpty()) "{$pack}" else ""
        }
    }

    override fun isExpandable() = false

    override fun getName(): String {
        return frame.methodName
    }
}

class AsyncStackFrameDescriptor(val state: CoroutineState, val frame: StackFrameItem, proxy: StackFrameProxyImpl) :
    StackFrameDescriptorImpl(proxy, MethodsTracker()) {
    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?): String {
        return with(frame) {
            val pack = path().substringBeforeLast(".", "")
            "${method()}:${line()}, ${path().substringAfterLast(".")} ${if (pack.isNotEmpty()) "{$pack}" else ""}"
        }
    }

    override fun getName(): String {
        return frame.method()
    }

    override fun isExpandable(): Boolean = false
}

/**
 * For the case when no data inside frame is available
 */
class EmptyStackFrameDescriptor(val frame: StackTraceElement, proxy: StackFrameProxyImpl) :
    StackFrameDescriptorImpl(proxy, MethodsTracker()) {
    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?): String {
        return with(frame) {
            val pack = className.substringBeforeLast(".", "")
            "$methodName:$lineNumber, ${className.substringAfterLast(".")} ${if (pack.isNotEmpty()) "{$pack}" else ""}"
        }
    }

    override fun getName() = null
    override fun isExpandable() = false
}

class CreationFramesDescriptor(val frames: List<StackTraceElement>) : NodeDescriptorImpl() {
    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?) = name

    override fun setContext(context: EvaluationContextImpl?) {}
    override fun getName() = "Coroutine creation stack trace"
    override fun isExpandable() = true
}