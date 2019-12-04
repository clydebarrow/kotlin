/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script.configuration.cache

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtFile

interface ScriptInputsCalculator {
    fun isApplicable(file: VirtualFile): Boolean
    fun calculate(project: Project, file: VirtualFile, ktFile: KtFile?): CachedConfigurationInputs

    companion object {
        private val DEFAULT = DefaultScriptInputsCalculator()

        val EP: ExtensionPointName<ScriptInputsCalculator> =
            ExtensionPointName.create("org.jetbrains.kotlin.scripting.idea.inputsCalculator")

        fun calculateActual(project: Project, file: VirtualFile, ktFile: KtFile?): CachedConfigurationInputs {
            val stampCalculator = EP.extensionList.firstOrNull { it.isApplicable(file) } ?: DEFAULT
            return stampCalculator.calculate(project, file, ktFile)
        }

        fun withExplicitStamp(project: Project, file: VirtualFile, ktFile: KtFile?, stamp: Long): CachedConfigurationInputs {
            val inputs = calculateActual(project, file, ktFile)
            inputs.stamp = stamp
            return inputs
        }
    }
}

class DefaultScriptInputsCalculator : ScriptInputsCalculator {
    override fun isApplicable(file: VirtualFile): Boolean = true

    override fun calculate(project: Project, file: VirtualFile, ktFile: KtFile?): CachedConfigurationInputs {
        return CachedConfigurationInputs.PsiModificationStamp.get(project, file, ktFile)
    }
}
