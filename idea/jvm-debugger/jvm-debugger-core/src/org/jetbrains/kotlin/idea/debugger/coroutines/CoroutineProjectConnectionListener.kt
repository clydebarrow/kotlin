/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

val Project.coroutineConnectionListener by projectListener

val projectListener
    get() = object : ReadOnlyProperty<Project, ProjectCoroutineConnectionListener> {
        lateinit var projectListener: ProjectCoroutineConnectionListener

        override fun getValue(project: Project, property: KProperty<*>): ProjectCoroutineConnectionListener {
            if (!::projectListener.isInitialized)
                projectListener = ProjectCoroutineConnectionListener(project)
            return projectListener
        }
    }

internal fun coroutineDebuggerEnabled() = Registry.`is`("kotlin.debugger.coroutines")

internal fun initializeCoroutineAgent(params: JavaParameters, it: String?) {
    params.vmParametersList?.add("-javaagent:$it")
    params.vmParametersList?.add("-ea")
}
