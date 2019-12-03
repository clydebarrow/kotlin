/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.diagnostics.collectors

object FirDiagnosticsCollector {
    val DEFAULT: AbstractDiagnosticCollector by lazy { create() }

    private fun create(): AbstractDiagnosticCollector {
//        val collector = SimpleDiagnosticsCollector()
        val collector = ParallelDiagnosticsCollector(4)
        collector.registerAllComponents()
        return collector
    }
}