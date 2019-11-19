/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.impl.FirResolvedNamedReferenceImpl
import org.jetbrains.kotlin.fir.resolve.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.calls.ConeInferenceContext
import org.jetbrains.kotlin.fir.resolve.calls.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.resultType
import org.jetbrains.kotlin.fir.resolvedTypeFromPrototype
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.impl.FirIntegerOperator
import org.jetbrains.kotlin.fir.scopes.impl.FirIntegerOperatorCall
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.CompositeTransformResult
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.compose
import org.jetbrains.kotlin.types.AbstractTypeChecker

class IntegerLiteralTypeApproximationTransformer(
    private val symbolProvider: FirSymbolProvider,
    private val inferenceContext: ConeInferenceContext
) : FirTransformer<ConeKotlinType?>() {
    override fun <E : FirElement> transformElement(element: E, data: ConeKotlinType?): CompositeTransformResult<E> {
        return element.compose()
    }

    override fun <T> transformConstExpression(
        constExpression: FirConstExpression<T>,
        data: ConeKotlinType?
    ): CompositeTransformResult<FirStatement> {
        val type = constExpression.resultType.coneTypeSafe<ConeIntegerLiteralType>() ?: return constExpression.compose()
        val approximatedType = type.getApproximatedType(data)
        constExpression.resultType = constExpression.resultType.resolvedTypeFromPrototype(approximatedType)
        val kind = approximatedType.toConstKind() as FirConstKind<T>
        constExpression.replaceKind(kind)
        return constExpression.compose()
    }

    override fun transformFunctionCall(functionCall: FirFunctionCall, data: ConeKotlinType?): CompositeTransformResult<FirStatement> {
        functionCall.transformArguments(this, data)
        val operator = functionCall.toResolvedCallableSymbol()?.fir as? FirIntegerOperator ?: return functionCall.compose()
        val argumentType = functionCall.arguments.firstOrNull()?.resultType?.coneTypeUnsafe<ConeClassLikeType>()
        val receiverClassId = functionCall.dispatchReceiver.typeRef.coneTypeUnsafe<ConeClassLikeType>().lookupTag.classId
        val scope = symbolProvider.getClassDeclaredMemberScope(receiverClassId)!!
        var resultSymbol: FirFunctionSymbol<*>? = null
        scope.processFunctionsByName(operator.name) { symbol ->
            if (operator.kind.unary) {
                resultSymbol = symbol
                return@processFunctionsByName ProcessorAction.STOP
            }
            val function = symbol.fir
            val valueParameterType = function.valueParameters.first().returnTypeRef.coneTypeUnsafe<ConeClassLikeType>()
            if (AbstractTypeChecker.isSubtypeOf(inferenceContext, argumentType!!, valueParameterType)) {
                resultSymbol = symbol
                return@processFunctionsByName ProcessorAction.STOP
            }
            ProcessorAction.NEXT
        }
        functionCall.resultType = resultSymbol!!.fir.returnTypeRef
        return functionCall.transformCalleeReference(StoreCalleeReference, FirResolvedNamedReferenceImpl(null, operator.name, resultSymbol!!)).compose()
    }
}

fun ConeClassLikeType.toConstKind(): FirConstKind<*> {
    return when (this) {
        ConeIntegerLiteralTypeImpl.INT_TYPE -> FirConstKind.Int
        ConeIntegerLiteralTypeImpl.LONG_TYPE -> FirConstKind.Long
        ConeIntegerLiteralTypeImpl.SHORT_TYPE -> FirConstKind.Short
        ConeIntegerLiteralTypeImpl.BYTE_TYPE -> FirConstKind.Byte
        ConeIntegerLiteralTypeImpl.DOUBLE_TYPE -> FirConstKind.Double
        ConeIntegerLiteralTypeImpl.FLOAT_TYPE -> FirConstKind.Float
        else -> throw IllegalStateException()
    }
}

class IntegerOperatorsTypeUpdater(val approximator: IntegerLiteralTypeApproximationTransformer) : FirTransformer<Nothing?>() {
    override fun <E : FirElement> transformElement(element: E, data: Nothing?): CompositeTransformResult<E> {
        return element.compose()
    }

    override fun transformFunctionCall(functionCall: FirFunctionCall, data: Nothing?): CompositeTransformResult<FirStatement> {
        val function: FirCallableDeclaration<*> = when (val reference = functionCall.calleeReference) {
            is FirNamedReferenceWithCandidate -> reference.candidateSymbol
            is FirResolvedNamedReference -> return functionCall.compose()//reference.resolvedSymbol
            else -> null
        }?.fir as? FirCallableDeclaration<*> ?: return functionCall.compose()

        if (function !is FirIntegerOperator) {
            val expectedType = function.receiverTypeRef?.coneTypeSafe<ConeKotlinType>()
            return functionCall.transformExplicitReceiver(approximator, expectedType).compose()
        }
        val receiverValue = functionCall.explicitReceiver!!.typeRef.coneTypeSafe<ConeIntegerLiteralType>()?.value ?: return functionCall.compose()
        val kind = function.kind
        val resultValue = when {
            kind.unary -> when (kind) {
                FirIntegerOperator.Kind.UNARY_PLUS -> receiverValue
                FirIntegerOperator.Kind.UNARY_MINUS -> -receiverValue
                else -> throw IllegalStateException()
            }
            else -> {
                val argumentType = functionCall.arguments.first().typeRef.coneTypeUnsafe<ConeKotlinType>()
                when (argumentType) {
                    is ConeIntegerLiteralType -> {
                        val argumentValue = argumentType.value
                        val divisionByZero = argumentValue == 0L
                        when (kind) {
                            FirIntegerOperator.Kind.PLUS -> receiverValue + argumentValue
                            FirIntegerOperator.Kind.MINUS -> receiverValue - argumentValue
                            FirIntegerOperator.Kind.TIMES -> receiverValue * argumentValue
                            // TODO: maybe add some error reporting (e.g. in userdata)
                            FirIntegerOperator.Kind.DIV -> if (divisionByZero) receiverValue else receiverValue / argumentValue
                            FirIntegerOperator.Kind.REM -> if (divisionByZero) receiverValue else receiverValue % argumentValue
                            else -> throw IllegalStateException()
                        }
                    }
                    else -> {
                        val expectedType = when (argumentType) {
                            ConeIntegerLiteralTypeImpl.FLOAT_TYPE,
                            ConeIntegerLiteralTypeImpl.DOUBLE_TYPE,
                            ConeIntegerLiteralTypeImpl.LONG_TYPE -> argumentType
                            else -> ConeIntegerLiteralTypeImpl.INT_TYPE
                        }
                        functionCall.transformExplicitReceiver(approximator, expectedType)
                        functionCall.replaceTypeRef(functionCall.resultType.resolvedTypeFromPrototype(expectedType))
                        return functionCall.compose()
                    }
                }
            }
        }
        functionCall.replaceTypeRef(functionCall.resultType.resolvedTypeFromPrototype(ConeIntegerLiteralTypeImpl(resultValue)))
        return functionCall.toOperatorCall().compose()
    }
}

private fun FirFunctionCall.toOperatorCall(): FirIntegerOperatorCall {
    if (this is FirIntegerOperatorCall) return this
    return FirIntegerOperatorCall(source).also {
        it.typeRef = typeRef
        it.annotations += annotations
        it.safe = safe
        it.typeArguments += typeArguments
        it.explicitReceiver = explicitReceiver
        it.dispatchReceiver = dispatchReceiver
        it.extensionReceiver = extensionReceiver
        it.arguments += arguments
        it.calleeReference = calleeReference
    }
}