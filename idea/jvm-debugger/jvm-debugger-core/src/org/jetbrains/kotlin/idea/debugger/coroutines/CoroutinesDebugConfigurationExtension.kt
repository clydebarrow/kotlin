/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.DebuggingRunnerData
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings

/**
 * Installs coroutines debug agent and coroutines tab if `kotlinx-coroutines-debug` dependency is found
 */
@Suppress("IncompatibleAPI")
class CoroutinesDebugConfigurationExtension : RunConfigurationExtension() {
    private val log by logger

    override fun isApplicableFor(configuration: RunConfigurationBase<*>) = coroutineDebuggerEnabled()

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T,
        params: JavaParameters?,
        runnerSettings: RunnerSettings?
    ) {
        if (runnerSettings is DebuggingRunnerData && configuration is RunConfigurationBase<*>) {
            val configurationName = configuration.type.id
            try {
                if (!gradleConfiguration(configurationName)) { // gradle test logic in KotlinGradleCoroutineDebugProjectResolver
                    val kotlinxCoroutinesClassPathLib = params?.classPath?.pathList?.first { it.contains("kotlinx-coroutines-debug") }
                    initializeCoroutineAgent(params!!, kotlinxCoroutinesClassPathLib)
                }
                configuration.project.coroutineConnectionListener.configurationStarting(runnerSettings, configuration)
            } catch (e: NoSuchElementException) {
                log.warn("'kotlinx-coroutines-debug' not found in classpath. Coroutine debugger disabled.")
            }
        }
    }

    // @TODO if that can be improved?
    fun gradleConfiguration(configurationName: String) =
        "GradleRunConfiguration".equals(configurationName) || "KotlinGradleRunConfiguration".equals(configurationName)
}
