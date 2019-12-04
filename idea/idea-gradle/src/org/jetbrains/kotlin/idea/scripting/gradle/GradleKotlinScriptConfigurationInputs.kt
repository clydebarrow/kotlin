/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scripting.gradle

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.configuration.cache.CachedConfigurationInputs
import org.jetbrains.kotlin.idea.core.script.configuration.cache.ScriptInputsCalculator
import org.jetbrains.kotlin.psi.KtFile

class GradleKotlinScriptConfigurationInputs(override var stamp: Long) : CachedConfigurationInputs {
    override fun isUpToDate(project: Project, file: VirtualFile, ktFile: KtFile?): Boolean {
        val actualStamp = ScriptInputsCalculator.calculateActual(project, file, ktFile)
        return actualStamp == this
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other) || this.stamp == (other as? GradleKotlinScriptConfigurationInputs)?.stamp
    }

    override fun hashCode(): Int {
        return stamp.hashCode()
    }

    override fun toString(): String {
        return "Gradle(crc=$stamp)"
    }
}