/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.actions.GotoFrameSourceAction
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaExecutionStack
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.debugger.ui.impl.watch.*
import com.intellij.debugger.ui.tree.StackFrameDescriptor
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.DoubleClickListener
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.sun.jdi.ClassType
import org.jetbrains.kotlin.idea.debugger.AsyncStackTraceContext
import org.jetbrains.kotlin.idea.debugger.KotlinCoroutinesAsyncStackTraceProvider
import org.jetbrains.kotlin.idea.debugger.coroutines.command.CoroutineBuildFrameCommand
import org.jetbrains.kotlin.idea.debugger.coroutines.command.CreationBuildCreationFrameCommand
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener

/**
 * Tree of coroutines for [CoroutinesPanel]
 */
class CoroutinesDebuggerTree(project: Project) : ThreadsDebuggerTree(project) {
    private val log by logger

    /**
     * Prepare specific behavior instead of DebuggerTree constructor
     */
//    init {
//        val model = object : TreeBuilder(this) {
//            override fun buildChildren(node: TreeBuilderNode) {
//                val debuggerTreeNode = node as DebuggerTreeNodeImpl
//                if (debuggerTreeNode.descriptor is DefaultNodeDescriptor) {
//                    return
//                }
//
//                node.add(myNodeManager.createMessageNode(MessageDescriptor.EVALUATING))
//                buildNode(debuggerTreeNode)
//            }
//
//            override fun isExpandable(builderNode: TreeBuilderNode): Boolean {
//                return this@CoroutinesDebuggerTree.isExpandable(builderNode as DebuggerTreeNodeImpl)
//            }
//        }
//        model.setRoot(nodeFactory.defaultNode)
//        model.addTreeModelListener(createListener())
//
//        setModel(model)
//        emptyText.text = "Coroutines are not available"
//    }


    // called on every step/frame
    override fun build(context: DebuggerContextImpl) {
        val session = context.debuggerSession
        val command = RefreshCoroutinesTreeCommand(context,this)
        val state = session?.state ?: DebuggerSession.State.DISPOSED

        if (ApplicationManager.getApplication().isUnitTestMode || state in EnumSet.of(DebuggerSession.State.PAUSED, DebuggerSession.State.RUNNING)) {
            showMessage(MessageDescriptor.EVALUATING)
            context.debugProcess!!.managerThread.schedule(command)
        } else {
            showMessage(session?.stateDescription ?: DebuggerBundle.message("status.debug.stopped"))
        }
    }



    /**
     * Add frames inside coroutine (node)
     */
    /*
    private fun buildNode(node: DebuggerTreeNodeImpl) {
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val debugProcess = context.debugProcess
        debugProcess?.managerThread?.schedule(object : SuspendContextCommandImpl(context.suspendContext) {
            override fun contextAction(suspendContext: SuspendContextImpl) {
                val evalContext = debuggerContext.createEvaluationContext() ?: return
                if (node.descriptor is CoroutineDescriptorImpl || node.descriptor is CreationFramesDescriptor) {
                    val children = mutableListOf<DebuggerTreeNodeImpl>()
                    try {
                        addChildren(children, debugProcess, node.descriptor, evalContext)
                    } catch (e: EvaluateException) {
                        children.clear()
                        children.add(myNodeManager.createMessageNode(e.message))
                        log.debug(e)
                    }
                    DebuggerInvocationUtil.swingInvokeLater(project) {
                        node.removeAllChildren()
                        for (debuggerTreeNode in children) {
                            node.add(debuggerTreeNode)
                        }
                        node.childrenChanged(true)
                    }
                }
            }
        })
    }

     */

    override fun getBuildNodeCommand(node: DebuggerTreeNodeImpl): DebuggerCommandImpl? {
        return when(val descriptor = node.descriptor) {
            is CoroutineDescriptorImpl ->
                CoroutineBuildFrameCommand(node, descriptor, myNodeManager, debuggerContext)
            is CreationFramesDescriptor ->
                CreationBuildCreationFrameCommand(node, descriptor, myNodeManager, debuggerContext)
            else -> null
        }
    }
    fun installAction(): () -> Unit {
        log.info("installAction called")
        val listener = object : DoubleClickListener() {
            override fun onDoubleClick(e: MouseEvent): Boolean {
                val location = getPathForLocation(e.x, e.y)
                    ?.lastPathComponent as? DebuggerTreeNodeImpl ?: return false
                return selectFrame(location.userObject)
            }
        }
        listener.installOn(this)

        addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && selectionCount == 1) {
                        val selected = selectionModel.selectionPath.lastPathComponent
                        if (selected is DebuggerTreeNodeImpl) selectFrame(selected.userObject)
                    }
                }
            })
        return { listener.uninstall(this) }
    }

    override fun createNodeManager(project: Project): NodeManagerImpl {
        return object : NodeManagerImpl(project, this) {
            override fun getContextKey(frame: StackFrameProxyImpl?): String? {
                return "CoroutinesView"
            }
        }
    }

    fun selectFrame(descriptor: Any): Boolean {
        val dataContext = DataManager.getInstance().getDataContext(this@CoroutinesDebuggerTree)
        val context = DebuggerManagerEx.getInstanceEx(project).context
        when (descriptor) {
            is SuspendStackFrameDescriptor -> {
                buildSuspendStackFrameChildren(descriptor)
                return true
            }
            is AsyncStackFrameDescriptor -> {
                buildAsyncStackFrameChildren(descriptor, context.debugProcess ?: return false)
                return true
            }
            is EmptyStackFrameDescriptor -> {
                buildEmptyStackFrameChildren(descriptor)
                return true
            }
            is StackFrameDescriptor -> {
                GotoFrameSourceAction.doAction(dataContext)
                return true
            }
            else -> return true
        }
    }

    private fun buildSuspendStackFrameChildren(descriptor: SuspendStackFrameDescriptor) {
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val pos = getPosition(descriptor.frame) ?: return
        context.debugProcess?.managerThread?.schedule(object : SuspendContextCommandImpl(context.suspendContext) {
            override fun contextAction() {
                val (stack, stackFrame) = createSyntheticStackFrame(descriptor, pos) ?: return
                val action: () -> Unit = { context.debuggerSession?.xDebugSession?.setCurrentStackFrame(stack, stackFrame) }
                ApplicationManager.getApplication()
                    .invokeLater(action, ModalityState.stateForComponent(this@CoroutinesDebuggerTree))
            }
        })
    }

    private fun buildAsyncStackFrameChildren(descriptor: AsyncStackFrameDescriptor, process: DebugProcessImpl) {
        process.managerThread?.schedule(object : DebuggerCommandImpl() {
            override fun action() {
                val context = DebuggerManagerEx.getInstanceEx(project).context
                val proxy = ThreadReferenceProxyImpl(
                    process.virtualMachineProxy,
                    descriptor.state.thread // is not null because it's a running coroutine
                )
                val executionStack = JavaExecutionStack(proxy, process, false)
                executionStack.initTopFrame()
                val frame = descriptor.frame.createFrame(process)
                DebuggerUIUtil.invokeLater {
                    context.debuggerSession?.xDebugSession?.setCurrentStackFrame(
                        executionStack,
                        frame
                    )
                }
            }
        })
    }

    private fun buildEmptyStackFrameChildren(descriptor: EmptyStackFrameDescriptor) {
        val position = getPosition(descriptor.frame) ?: return
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val suspendContext = context.suspendContext ?: return
        val proxy = suspendContext.thread ?: return
        context.debugProcess?.managerThread?.schedule(object : DebuggerCommandImpl() {
            override fun action() {
                val executionStack =
                    JavaExecutionStack(proxy, context.debugProcess!!, false)
                executionStack.initTopFrame()
                val frame = SyntheticStackFrame(descriptor, emptyList(), position)
                val action: () -> Unit =
                    { context.debuggerSession?.xDebugSession?.setCurrentStackFrame(executionStack, frame) }
                ApplicationManager.getApplication()
                    .invokeLater(action, ModalityState.stateForComponent(this@CoroutinesDebuggerTree))
            }
        })
    }

    private fun getPosition(frame: StackTraceElement): XSourcePosition? {

        val psiFacade = JavaPsiFacade.getInstance(project)
        val psiClass = psiFacade.findClass(
            frame.className.substringBefore("$"), // find outer class, for which psi exists TODO
            GlobalSearchScope.everythingScope(project)
        )
        val classFile = psiClass?.containingFile?.virtualFile
        // to convert to 0-based line number or '-1' to do not move
        val lineNumber = if(frame.lineNumber > 0) frame.lineNumber - 1 else return null
        return XDebuggerUtil.getInstance().createPosition(classFile,  lineNumber)
    }

    /**
     * Should be invoked on manager thread
     */
    private fun createSyntheticStackFrame(
        descriptor: SuspendStackFrameDescriptor,
        pos: XSourcePosition
    ): Pair<XExecutionStack, SyntheticStackFrame>? {
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val suspendContext = context.suspendContext ?: return null
        val proxy = suspendContext.thread ?: return null
        val executionStack = JavaExecutionStack(proxy, suspendContext.debugProcess, false)
        executionStack.initTopFrame()
        val evalContext = context.createEvaluationContext()
        val frameProxy = evalContext?.frameProxy ?: return null
        val execContext = ExecutionContext(evalContext, frameProxy)
        val continuation = descriptor.continuation // guaranteed that it is a BaseContinuationImpl
        val aMethod = (continuation.type() as ClassType).concreteMethodByName(
            "getStackTraceElement",
            "()Ljava/lang/StackTraceElement;"
        )
        val debugMetadataKtType = execContext
            .findClass("kotlin.coroutines.jvm.internal.DebugMetadataKt") as ClassType
        val vars = with(KotlinCoroutinesAsyncStackTraceProvider()) {
            AsyncStackTraceContext(
                execContext,
                aMethod,
                debugMetadataKtType
            ).getSpilledVariables(continuation)
        } ?: return null
        return executionStack to SyntheticStackFrame(descriptor, vars, pos)
    }


    private fun createListener() = object : TreeModelListener {
        override fun treeNodesChanged(event: TreeModelEvent) {
            hideTooltip()
        }

        override fun treeNodesInserted(event: TreeModelEvent) {
            hideTooltip()
        }

        override fun treeNodesRemoved(event: TreeModelEvent) {
            hideTooltip()
        }

        override fun treeStructureChanged(event: TreeModelEvent) {
            hideTooltip()
        }
    }

    override fun isExpandable(node: DebuggerTreeNodeImpl): Boolean {
        val descriptor = node.descriptor
        return if (descriptor is StackFrameDescriptor) false else descriptor.isExpandable
    }
}

class CoroutineInfoCache(val cache: MutableList<CoroutineState> = mutableListOf(), var state: CacheState = CacheState.INIT) {
    fun ok(infoList: List<CoroutineState>) {
        cache.clear()
        cache.addAll(infoList)
        state = CacheState.OK
    }

    fun fail() {
        cache.clear()
        state = CacheState.FAIL
    }

    fun isOk() : Boolean {
        return state == CacheState.OK
    }
}

enum class CacheState() {
    OK,FAIL,INIT
}
