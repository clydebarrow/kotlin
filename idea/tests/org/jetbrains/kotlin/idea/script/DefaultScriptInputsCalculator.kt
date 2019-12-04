/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.configuration.cache.CachedConfigurationInputs
import org.jetbrains.kotlin.idea.core.script.configuration.cache.ScriptInputsCalculator
import org.jetbrains.kotlin.psi.KtFile

class TestScriptInputsCalculator : ScriptInputsCalculator {
    override fun isApplicable(file: VirtualFile): Boolean = true

    override fun calculate(project: Project, file: VirtualFile, ktFile: KtFile?): CachedConfigurationInputs {
        return CachedConfigurationInputs.SourceContentsStamp.get(project, file, ktFile)
    }
}