/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.analyzer.KotlinModificationTrackerService
import org.jetbrains.kotlin.analyzer.TrackableModuleInfo
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo

interface FirIdeResolveStateService {
    companion object {
        fun getInstance(project: Project): FirIdeResolveStateService =
            ServiceManager.getService(project, FirIdeResolveStateService::class.java)!!
    }

    val fallbackModificationTracker: ModificationTracker?

    fun getResolveState(moduleInfo: IdeaModuleInfo): FirResolveState
}

private class ModuleData(val state: FirResolveState, val modificationTracker: ModificationTracker?) {
    val modificationCount: Long = modificationTracker?.modificationCount ?: Long.MIN_VALUE

    fun isOutOfDate(): Boolean {
        val currentModCount = modificationTracker?.modificationCount
        return currentModCount != null && currentModCount > modificationCount
    }
}

class FirIdeResolveStateServiceImpl(val project: Project) : FirIdeResolveStateService {
    private val stateCache = mutableMapOf<IdeaModuleInfo, ModuleData>()

    private fun createResolveState(): FirResolveState {
        val provider = FirProjectSessionProvider(project)
        return FirResolveStateImpl(provider)
    }

    private fun createModuleData(moduleInfo: IdeaModuleInfo): ModuleData {
        val state = createResolveState()
        val module = (moduleInfo as? ModuleSourceInfo)?.module
        val modificationTracker = (module as? TrackableModuleInfo)?.createModificationTracker() ?: fallbackModificationTracker
        return ModuleData(state, modificationTracker)
    }

    override fun getResolveState(moduleInfo: IdeaModuleInfo): FirResolveState {
        var moduleData = stateCache.getOrPut(moduleInfo) {
            createModuleData(moduleInfo)
        }
        if (moduleData.isOutOfDate()) {
            stateCache.remove(moduleInfo)
            moduleData = createModuleData(moduleInfo)
            stateCache[moduleInfo] = moduleData
        }
        return moduleData.state
    }

    override val fallbackModificationTracker: ModificationTracker? =
        KotlinModificationTrackerService.getInstance(project).outOfBlockModificationTracker
}