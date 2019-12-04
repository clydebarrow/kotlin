/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script.configuration.cache

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.DataInputOutputUtil.readSeq
import com.intellij.util.io.DataInputOutputUtil.writeSeq
import org.jetbrains.kotlin.idea.core.script.configuration.loader.ScriptConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.configuration.loader.ScriptConfigurationLoadingContext
import org.jetbrains.kotlin.idea.core.script.debug
import org.jetbrains.kotlin.idea.core.util.cachedFileAttribute
import org.jetbrains.kotlin.idea.core.util.readObject
import org.jetbrains.kotlin.idea.core.util.writeObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper.FromCompilationConfiguration
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.jvm.impl.toClassPathOrEmpty

internal class ScriptConfigurationFileAttributeCache(
    val project: Project
) : ScriptConfigurationLoader {
    /**
     * todo(KT-34444): this should be changed to storing all roots in the persistent file cache
     */
    override fun loadDependencies(
        isFirstLoad: Boolean,
        ktFile: KtFile,
        scriptDefinition: ScriptDefinition,
        context: ScriptConfigurationLoadingContext
    ): Boolean {
        if (!isFirstLoad) return false

        val virtualFile = ktFile.originalFile.virtualFile
        val fromFs = load(virtualFile) ?: return false

        val inputs = ScriptInputsCalculator.withExplicitStamp(project, virtualFile, ktFile, fromFs.stamp)

        context.saveNewConfiguration(
            virtualFile,
            ScriptConfigurationSnapshot(
                inputs,
                fromFs.reports,
                FromCompilationConfiguration(KtFileScriptSource(ktFile), fromFs.configuration)
            )
        )
        return inputs.isUpToDate(ktFile.project, virtualFile, ktFile)
    }

    private fun load(
        virtualFile: VirtualFile
    ): ScriptConfigurationSnapshotForFS? {
        val configurationSnapshot = virtualFile.scriptConfigurationSnapshot ?: return null
        debug(virtualFile) { "configuration from fileAttributes = $configurationSnapshot" }

        val configuration = configurationSnapshot.configuration ?: return null

        if (!areDependenciesValid(virtualFile, configuration)) {
            save(virtualFile, null)
            return null
        }

        return configurationSnapshot
    }

    private fun areDependenciesValid(file: VirtualFile, configuration: ScriptCompilationConfiguration): Boolean {
        val classpath = configuration.get(ScriptCompilationConfiguration.dependencies).toClassPathOrEmpty()
        return classpath.all {
            if (it.exists()) {
                true
            } else {
                debug(file) {
                    "classpath root saved to file attribute doesn't exist: ${it.path}"
                }
                false
            }
        }
    }

    fun save(file: VirtualFile, value: ScriptConfigurationSnapshot?) {
        if (value == null) {
            file.scriptConfigurationSnapshot = null
        } else {
            file.scriptConfigurationSnapshot = ScriptConfigurationSnapshotForFS(
                value.inputs.stamp,
                value.reports,
                value.configuration?.configuration
            )
        }
    }
}

private class ScriptConfigurationSnapshotForFS(
    val stamp: Long,
    val reports: List<ScriptDiagnostic>,
    val configuration: ScriptCompilationConfiguration?
)

private var VirtualFile.scriptConfigurationSnapshot: ScriptConfigurationSnapshotForFS? by cachedFileAttribute(
    name = "kotlin-script-configuration-snapshot",
    version = 1,
    read = {
        val stamp = readLong()

        val reports: List<ScriptDiagnostic> = readSeq(this) {
            readObject<ScriptDiagnostic>()
        }.filterNotNull()

        val configuration: ScriptCompilationConfiguration? = readObject()
        ScriptConfigurationSnapshotForFS(
            stamp,
            reports,
            configuration
        )
    },
    write = {
        val stamp = it.stamp
        writeLong(stamp)

        writeSeq(this, it.reports.toList()) { diagnostic ->
            writeObject(diagnostic)
        }

        writeObject(it.configuration)
    }
)
