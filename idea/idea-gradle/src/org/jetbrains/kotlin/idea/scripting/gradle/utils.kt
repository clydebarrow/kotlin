/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scripting.gradle

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion

fun isGradleKotlinScript(virtualFile: VirtualFile) = virtualFile.name.endsWith(".gradle.kts")

const val minimal_gradle_version_supported = "6.0"

fun kotlinDslScriptsModelImportSupported(currentGradleVersion: String): Boolean {
    return GradleVersion.version(currentGradleVersion) >= GradleVersion.version(minimal_gradle_version_supported)
}

fun useScriptConfigurationFromImportOnly(): Boolean {
    return Registry.`is`("kotlin.gradle.scripts.useIdeaProjectImport", false)
}