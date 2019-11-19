/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.FirSymbolOwner
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirSimpleFunctionImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirValueParameterImpl
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.impl.FirFunctionCallImpl
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.scope
import org.jetbrains.kotlin.fir.resolve.scopeSessionKey
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.CallableId
import org.jetbrains.kotlin.fir.symbols.ConeClassifierLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.ConeIntegerLiteralType
import org.jetbrains.kotlin.fir.types.ConeIntegerLiteralTypeImpl
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

object FirIntegerLiteralTypeClassifierSymbol : FirClassifierSymbol<FirIntegerLiteralTypeClassifier>() {
    override fun toLookupTag(): ConeClassifierLookupTag {
        TODO("not implemented")
    }
}

object FirIntegerLiteralTypeClassifier : FirDeclaration, FirSymbolOwner<FirIntegerLiteralTypeClassifier> {
    override val source: FirSourceElement?
        get() = TODO("not implemented")
    override val session: FirSession
        get() = TODO("not implemented")
    override val resolvePhase: FirResolvePhase
        get() = TODO("not implemented")

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R {
        TODO("not implemented")
    }

    override fun replaceResolvePhase(newResolvePhase: FirResolvePhase) {
        TODO("not implemented")
    }

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {
        TODO("not implemented")
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        TODO("not implemented")
    }

    override val symbol: AbstractFirBasedSymbol<FirIntegerLiteralTypeClassifier>
        get() = FirIntegerLiteralTypeClassifierSymbol
}

class FirIntegerLiteralTypeScope(private val session: FirSession, private val scopeSession: ScopeSession) : FirScope() {
    companion object {
        val BINARY_OPERATOR_NAMES = FirIntegerOperator.Kind.values().filterNot { it.unary }.map { it.operatorName }
        val UNARY_OPERATOR_NAMES = FirIntegerOperator.Kind.values().filter { it.unary }.map { it.operatorName }
        private val ALL_OPERATORS = FirIntegerOperator.Kind.values().map { it.operatorName to it }.toMap()

        val SCOPE_SESSION_KEY = scopeSessionKey<FirIntegerLiteralTypeScope>()
    }

    private val BINARY_OPERATOR_SYMBOLS = BINARY_OPERATOR_NAMES.map { name ->
        name to FirNamedFunctionSymbol(CallableId(name)).apply {
            createFirFunction(name, this).apply {
                val valueParameterName = Name.identifier("arg")
                valueParameters += FirValueParameterImpl(
                    source = null,
                    session,
                    FirILTTypeRefPlaceHolder(),
                    valueParameterName,
                    FirVariableSymbol(name),
                    defaultValue = null,
                    isCrossinline = false,
                    isNoinline = false,
                    isVararg = false
                )
                resolvePhase = FirResolvePhase.BODY_RESOLVE
            }
        }
    }.toMap()

    private val UNARY_OPERATOR_SYMBOLS = UNARY_OPERATOR_NAMES.map { name ->
        name to FirNamedFunctionSymbol(CallableId(name)).apply { createFirFunction(name, this) }
    }.toMap()

    private fun createFirFunction(name: Name, symbol: FirNamedFunctionSymbol): FirSimpleFunctionImpl = FirIntegerOperator(
        source = null,
        session,
        FirILTTypeRefPlaceHolder(),
        receiverTypeRef = null,
        ALL_OPERATORS.getValue(name),
        FirResolvedDeclarationStatusImpl(Visibilities.PUBLIC, Modality.FINAL),
        symbol
    ).apply {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
    }

    private val DEFAULT_SCOPE = FirCompositeScope(mutableListOf(
        ConeIntegerLiteralTypeImpl.INT_TYPE.scope(session, scopeSession)!!
//        TODO: handle scope of LONG with less priority
//              if we didn' find smth in Int scope or ILT can not be Int we should look int Long scope
//        ConeIntegerLiteralTypeImpl.LONG_TYPE.scope(session, scopeSession)!!
    ))

    override fun processClassifiersByName(name: Name, processor: (FirClassifierSymbol<*>) -> ProcessorAction): ProcessorAction {
        return ProcessorAction.NONE
    }

    override fun processFunctionsByName(name: Name, processor: (FirFunctionSymbol<*>) -> ProcessorAction): ProcessorAction {
        val symbol = BINARY_OPERATOR_SYMBOLS[name]
            ?: UNARY_OPERATOR_SYMBOLS[name]
            ?: return DEFAULT_SCOPE.processFunctionsByName(name, processor)
        return processor(symbol)
    }

    override fun processPropertiesByName(name: Name, processor: (FirCallableSymbol<*>) -> ProcessorAction): ProcessorAction {
        return DEFAULT_SCOPE.processPropertiesByName(name, processor)
    }
}

class FirIntegerOperator(
    source: FirSourceElement?,
    session: FirSession,
    returnTypeRef: FirTypeRef,
    receiverTypeRef: FirTypeRef?,
    val kind: Kind,
    status: FirDeclarationStatus,
    symbol: FirFunctionSymbol<FirSimpleFunction>
) : FirSimpleFunctionImpl(source, session, returnTypeRef, receiverTypeRef, kind.operatorName, status, symbol) {
    enum class Kind(val unary: Boolean, val operatorName: Name) {
        PLUS(false, OperatorNameConventions.PLUS),
        MINUS(false, OperatorNameConventions.MINUS),
        TIMES(false, OperatorNameConventions.TIMES),
        DIV(false, OperatorNameConventions.DIV),
        REM(false, OperatorNameConventions.REM),
        UNARY_PLUS(true, OperatorNameConventions.UNARY_PLUS),
        UNARY_MINUS(true, OperatorNameConventions.UNARY_MINUS)
    }
}

class FirILTTypeRefPlaceHolder : FirResolvedTypeRef() {
    override val source: FirSourceElement? get() = null
    override val annotations: List<FirAnnotationCall> get() = emptyList()

    override var type: ConeIntegerLiteralType = ConeIntegerLiteralTypeImpl(0)

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        return this
    }
}

class FirIntegerOperatorCall(source: FirSourceElement?) : FirFunctionCallImpl(source)