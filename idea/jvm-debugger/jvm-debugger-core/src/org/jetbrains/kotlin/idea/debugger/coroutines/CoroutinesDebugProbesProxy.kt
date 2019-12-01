/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.sun.jdi.*
import com.sun.tools.jdi.StringReferenceImpl
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

val SuspendContextImpl.coroutineProbeProxy by coroutineProbeProxy

fun SuspendContextImpl.createEvaluationContext() = EvaluationContextImpl(this, this.frameProxy)

val coroutineProbeProxy
    get() = object : ReadOnlyProperty<SuspendContextImpl, CoroutinesDebugProbesProxy> {
        lateinit var probesProxy: CoroutinesDebugProbesProxy

        override fun getValue(suspendContext: SuspendContextImpl, property: KProperty<*>): CoroutinesDebugProbesProxy {
            if (!::probesProxy.isInitialized)
                probesProxy = CoroutinesDebugProbesProxy(suspendContext)
            return probesProxy
        }
    }

//    private var DebugProcess.references by UserDataProperty(Key.create<ProcessReferences>("COROUTINES_DEBUG_REFERENCES"))

class CoroutinesDebugProbesProxy(val suspendContext: SuspendContextImpl) {
    private val log by logger

    // @TODO refactor to extract initialization logic
    private val executionContext = ExecutionContext(
        EvaluationContextImpl(suspendContext, suspendContext.frameProxy), suspendContext.frameProxy as StackFrameProxyImpl)
    // might want to use inner class but also having to monitor order of fields
    private val refs: ProcessReferences = ProcessReferences(executionContext)

    companion object {
        private const val DEBUG_PACKAGE = "kotlinx.coroutines.debug"
    }

    @Synchronized
    @Suppress("unused")
    fun install() {
        val debugProbes = executionContext.findClass("$DEBUG_PACKAGE.DebugProbes") as ClassType
        val instance = with(debugProbes) { getValue(fieldByName("INSTANCE")) as ObjectReference }
        val install = debugProbes.concreteMethodByName("install", "()V")
        executionContext.invokeMethod(instance, install, emptyList())
    }

    @Synchronized
    @Suppress("unused")
    fun uninstall() {
        val debugProbes = executionContext.findClass("$DEBUG_PACKAGE.DebugProbes") as ClassType
        val instance = with(debugProbes) { getValue(fieldByName("INSTANCE")) as ObjectReference }
        val uninstall = debugProbes.concreteMethodByName("uninstall", "()V")
        executionContext.invokeMethod(instance, uninstall, emptyList())
    }

    /**
     * Invokes DebugProbes from debugged process's classpath and returns states of coroutines
     * Should be invoked on debugger manager thread
     */
    @Synchronized
    fun dumpCoroutines() : CoroutineInfoCache {
        val coroutineInfoCache = CoroutineInfoCache()
        try {
            val infoList = dump()
            coroutineInfoCache.ok(infoList)
        } catch (e: Throwable) {
            log.error("Exception is thrown by calling dumpCoroutines.", e)
            coroutineInfoCache.fail()
        }
        return coroutineInfoCache
    }

//    fun createExecutionContext(
//        suspendContext: SuspendContextImpl,
//        stackFrameProxyImpl: StackFrameProxyImpl
//    ) =
//        ExecutionContext(EvaluationContextImpl(suspendContext, suspendContext.frameProxy), stackFrameProxyImpl)

    private fun dump(): List<CoroutineState> {
        // get dump
        val infoList = executionContext.invokeMethod(refs.instance, refs.dumpMethod, emptyList()) as? ObjectReference
            ?: return emptyList()

        executionContext.keepReference(infoList)
        val size = (executionContext.invokeMethod(infoList, refs.getSize, emptyList()) as IntegerValue).value()

        return List(size) {
            val index = executionContext.vm.mirrorOf(it)
            // `List<CoroutineInfo>.get(index)`
            val elem = executionContext.invokeMethod(infoList, refs.getElement, listOf(index)) as ObjectReference
            val name = getName(elem)
            val state = getState(elem)
            val thread = getLastObservedThread(elem, refs.threadRef)
            CoroutineState(
                name,
                CoroutineState.State.valueOf(state),
                thread,
                getStackTrace(elem),
                elem.getValue(refs.continuation) as? ObjectReference
            )
        }
    }

    private fun getName(
        info: ObjectReference // CoroutineInfo instance
    ): String {
        // equals to `coroutineInfo.context.get(CoroutineName).name`
        val coroutineContextInst = executionContext.invokeMethod(
            info,
            refs.getContext,
            emptyList()
        ) as? ObjectReference ?: throw IllegalArgumentException("Coroutine context must not be null")
        val coroutineName = executionContext.invokeMethod(
            coroutineContextInst,
            refs.getContextElement, listOf(refs.nameKey)
        ) as? ObjectReference
        // If the coroutine doesn't have a given name, CoroutineContext.get(CoroutineName) returns null
        val name = if (coroutineName != null) (executionContext.invokeMethod(
            coroutineName,
            refs.getName,
            emptyList()
        ) as StringReferenceImpl).value() else "coroutine"
        val id = (info.getValue(refs.idField) as LongValue).value()
        return "$name#$id"
    }

    private fun getState(
        info: ObjectReference // CoroutineInfo instance
    ): String {
        // equals to `stringState = coroutineInfo.state.toString()`
        val state = executionContext.invokeMethod(info, refs.getState, emptyList()) as ObjectReference
        return (executionContext.invokeMethod(state, refs.toString, emptyList()) as StringReferenceImpl).value()
    }

    private fun getLastObservedThread(
        info: ObjectReference, // CoroutineInfo instance
        threadRef: Field // reference to lastObservedThread
    ): ThreadReference? = info.getValue(threadRef) as? ThreadReference

    /**
     * Returns list of stackTraceElements for the given CoroutineInfo's [ObjectReference]
     */
    private fun getStackTrace(
        info: ObjectReference
    ): List<StackTraceElement> {
        val frameList = executionContext.invokeMethod(info, refs.lastObservedStackTrace, emptyList()) as ObjectReference
        val mergedFrameList = executionContext.invokeMethod(
            refs.debugProbesImpl,
            refs.enhanceStackTraceWithThreadDump, listOf(info, frameList)
        ) as ObjectReference
        val size = (executionContext.invokeMethod(mergedFrameList, refs.getSize, emptyList()) as IntegerValue).value()

        val list = ArrayList<StackTraceElement>()
        for (it in size - 1 downTo 0) {
            val frame = executionContext.invokeMethod(
                mergedFrameList, refs.getElement,
                listOf(executionContext.vm.virtualMachine.mirrorOf(it))
            ) as ObjectReference
            val clazz = (frame.getValue(refs.className) as? StringReference)?.value()
            list.add(
                0, // add in the beginning
                StackTraceElement(
                    clazz,
                    (frame.getValue(refs.methodName) as? StringReference)?.value(),
                    (frame.getValue(refs.fileName) as? StringReference)?.value(),
                    (frame.getValue(refs.line) as IntegerValue).value()
                )
            )
        }
        return list
    }

    /**
     * @TODO refactor later
     * Holds ClassTypes, Methods, ObjectReferences and Fields for a particular jvm
     */
    private class ProcessReferences(executionContext: ExecutionContext) {
        // kotlinx.coroutines.debug.DebugProbes instance and methods
        val debugProbes = executionContext.findClass("$DEBUG_PACKAGE.DebugProbes") as ClassType
        val probesImplType = executionContext.findClass("$DEBUG_PACKAGE.internal.DebugProbesImpl") as ClassType
        val debugProbesImpl = with(probesImplType) { getValue(fieldByName("INSTANCE")) as ObjectReference }
        val enhanceStackTraceWithThreadDump: Method = probesImplType
            .methodsByName("enhanceStackTraceWithThreadDump").single()

        val dumpMethod: Method = debugProbes.concreteMethodByName("dumpCoroutinesInfo", "()Ljava/util/List;")
        val instance = with(debugProbes) { getValue(fieldByName("INSTANCE")) as ObjectReference }

        // CoroutineInfo
        val info = executionContext.findClass("$DEBUG_PACKAGE.CoroutineInfo") as ClassType
        val getState: Method = info.concreteMethodByName("getState", "()Lkotlinx/coroutines/debug/State;")
        val getContext: Method = info.concreteMethodByName("getContext", "()Lkotlin/coroutines/CoroutineContext;")
        val idField: Field = info.fieldByName("sequenceNumber")
        val lastObservedStackTrace: Method = info.methodsByName("lastObservedStackTrace").single()
        val coroutineContext = executionContext.findClass("kotlin.coroutines.CoroutineContext") as InterfaceType
        val getContextElement: Method = coroutineContext.methodsByName("get").single()
        val coroutineName = executionContext.findClass("kotlinx.coroutines.CoroutineName") as ClassType
        val getName: Method = coroutineName.methodsByName("getName").single()
        val nameKey = coroutineName.getValue(coroutineName.fieldByName("Key")) as ObjectReference
        val toString: Method = (executionContext.findClass("java.lang.Object") as ClassType)
            .concreteMethodByName("toString", "()Ljava/lang/String;")

        val threadRef: Field = info.fieldByName("lastObservedThread")
        val continuation: Field = info.fieldByName("lastObservedFrame")

        // Methods for list
        val listType = executionContext.findClass("java.util.List") as InterfaceType
        val getSize: Method = listType.methodsByName("size").single()
        val getElement: Method = listType.methodsByName("get").single()
        val element = executionContext.findClass("java.lang.StackTraceElement") as ClassType

        // for StackTraceElement
        val methodName: Field = element.fieldByName("methodName")
        val className: Field = element.fieldByName("declaringClass")
        val fileName: Field = element.fieldByName("fileName")
        val line: Field = element.fieldByName("lineNumber")
    }
}