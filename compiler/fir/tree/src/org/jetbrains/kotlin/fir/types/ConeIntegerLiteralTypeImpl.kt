/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types

import org.jetbrains.kotlin.fir.symbols.StandardClassIds
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.types.model.SimpleTypeMarker

class ConeIntegerLiteralTypeImpl : ConeIntegerLiteralType {
    override val possibleTypes: Collection<ConeClassLikeType>

    constructor(value: Long) : super(value) {
        possibleTypes = mutableListOf()

        fun checkBoundsAndAddPossibleType(type: ConeClassLikeType) {
            val (minValue, maxValue) = when (type) {
                BYTE_TYPE -> Byte.MIN_VALUE.toLong() to Byte.MAX_VALUE.toLong()
                SHORT_TYPE -> Short.MIN_VALUE.toLong() to Short.MAX_VALUE.toLong()
                INT_TYPE -> Int.MIN_VALUE.toLong() to Int.MAX_VALUE.toLong()
                LONG_TYPE -> Long.MIN_VALUE to Long.MAX_VALUE
                else -> throw IllegalArgumentException("${type.lookupTag.classId} is not integer type")
            }
            if (value in minValue..maxValue) {
                possibleTypes.add(type)
            }
        }

        fun addSignedPossibleTypes() {
            checkBoundsAndAddPossibleType(INT_TYPE)
            possibleTypes += LONG_TYPE
            checkBoundsAndAddPossibleType(BYTE_TYPE)
            checkBoundsAndAddPossibleType(SHORT_TYPE)
        }

        addSignedPossibleTypes()
        // TODO: add support of unsigned types
    }

    private constructor(value: Long, possibleTypes: Collection<ConeClassLikeType>) : super(value) {
        this.possibleTypes = possibleTypes
    }

    override val supertypes: List<ConeClassLikeType> by lazy {
        listOf(
            NUMBER_TYPE,
            ConeClassLikeTypeImpl(ConeClassLikeLookupTagImpl(StandardClassIds.Comparable), arrayOf(ConeKotlinTypeProjectionOut(this)), false)
        )
    }

    override fun getApproximatedType(expectedType: ConeKotlinType?): ConeClassLikeType {
        return when (expectedType) {
            DOUBLE_TYPE, FLOAT_TYPE -> expectedType as ConeClassLikeType
            null, !in possibleTypes -> possibleTypes.first()
            else -> expectedType as ConeClassLikeType
        }
    }

    companion object {
        private fun createType(classId: ClassId): ConeClassLikeType {
            return ConeClassLikeTypeImpl(ConeClassLikeLookupTagImpl(classId), emptyArray(), false)
        }

        val INT_TYPE = createType(StandardClassIds.Int)
        val LONG_TYPE = createType(StandardClassIds.Long)
        val SHORT_TYPE = createType(StandardClassIds.Short)
        val BYTE_TYPE = createType(StandardClassIds.Byte)
        val DOUBLE_TYPE = createType(StandardClassIds.Double)
        val FLOAT_TYPE = createType(StandardClassIds.Float)

        private val NUMBER_TYPE = createType(StandardClassIds.Number)

        fun findCommonSuperType(types: Collection<SimpleTypeMarker>): SimpleTypeMarker? {
            return findCommonSuperTypeOrIntersectionType(types, Mode.COMMON_SUPER_TYPE)
        }

        fun findIntersectionType(types: Collection<SimpleTypeMarker>): SimpleTypeMarker? {
            return findCommonSuperTypeOrIntersectionType(types, Mode.INTERSECTION_TYPE)
        }

        private enum class Mode {
            COMMON_SUPER_TYPE, INTERSECTION_TYPE
        }

        /**
         * intersection(ILT(types), PrimitiveType) = commonSuperType(ILT(types), PrimitiveType) =
         *      PrimitiveType  in types  -> PrimitiveType
         *      PrimitiveType !in types -> null
         *
         * intersection(ILT(types_1), ILT(types_2)) = ILT(types_1 union types_2)
         *
         * commonSuperType(ILT(types_1), ILT(types_2)) = ILT(types_1 intersect types_2)
         */
        private fun findCommonSuperTypeOrIntersectionType(types: Collection<SimpleTypeMarker>, mode: Mode): SimpleTypeMarker? {
            if (types.isEmpty()) return null
            @Suppress("UNCHECKED_CAST")
            return types.reduce { left: SimpleTypeMarker?, right: SimpleTypeMarker? -> fold(left, right, mode) }
        }

        private fun fold(left: SimpleTypeMarker?, right: SimpleTypeMarker?, mode: Mode): SimpleTypeMarker? {
            if (left == null || right == null) return null
            return when {
                left is ConeIntegerLiteralType && right is ConeIntegerLiteralType ->
                    fold(left, right, mode)

                left is ConeIntegerLiteralType -> fold(left, right)
                right is ConeIntegerLiteralType -> fold(right, left)
                else -> null
            }
        }

        private fun fold(left: ConeIntegerLiteralType, right: ConeIntegerLiteralType, mode: Mode): ConeIntegerLiteralType? {
            val possibleTypes = when (mode) {
                Mode.COMMON_SUPER_TYPE -> left.possibleTypes intersect right.possibleTypes
                Mode.INTERSECTION_TYPE -> left.possibleTypes union right.possibleTypes
            }
            return ConeIntegerLiteralTypeImpl(left.value, possibleTypes)
        }

        private fun fold(left: ConeIntegerLiteralType, right: SimpleTypeMarker): SimpleTypeMarker? =
            if (right in left.possibleTypes) right else null
    }
}

fun ConeKotlinType.approximateIntegerLiteralType(expectedType: ConeKotlinType? = null): ConeKotlinType =
    (this as? ConeIntegerLiteralType)?.getApproximatedType(expectedType) ?: this

fun ConeKotlinType.approximateIntegerLiteralTypeOrNull(expectedType: ConeKotlinType? = null): ConeKotlinType? =
    (this as? ConeIntegerLiteralType)?.getApproximatedType(expectedType)