/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.backend.konan.lower

import org.jetbrains.kotlin.backend.common.AbstractValueUsageTransformer
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.descriptors.explicitParameters
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.backend.konan.*
import org.jetbrains.kotlin.backend.konan.descriptors.target
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.makeNullable

/**
 * Boxes and unboxes values of value types when necessary.
 */
internal class Autoboxing(val context: Context) : FileLoweringPass {

    private val transformer = AutoboxingTransformer(context)

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(transformer)
    }

}

private class AutoboxingTransformer(val context: Context) : AbstractValueUsageTransformer(context.builtIns) {

    val symbols = context.ir.symbols

    // TODO: should we handle the cases when expression type
    // is not equal to e.g. called function return type?


    /**
     * @return type to use for runtime type checks instead of given one (e.g. `IntBox` instead of `Int`)
     */
    private fun getRuntimeReferenceType(type: KotlinType): KotlinType {
        ValueType.values().forEach {
            if (type.notNullableIsRepresentedAs(it)) {
                return getBoxType(it).makeNullableAsSpecified(TypeUtils.isNullableType(type))
            }
        }

        return type
    }

    override fun IrExpression.useInTypeOperator(operator: IrTypeOperator, typeOperand: KotlinType): IrExpression {
        return if (operator == IrTypeOperator.IMPLICIT_COERCION_TO_UNIT ||
                   operator == IrTypeOperator.IMPLICIT_INTEGER_COERCION) {
            this
        } else {
            // Codegen expects the argument of type-checking operator to be an object reference:
            this.useAs(builtIns.nullableAnyType)
        }
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
        super.visitTypeOperator(expression).let {
            // Assume that the transformer doesn't replace the entire expression for simplicity:
            assert (it === expression)
        }

        val newTypeOperand = getRuntimeReferenceType(expression.typeOperand)

        return when (expression.operator) {
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT,
            IrTypeOperator.IMPLICIT_INTEGER_COERCION -> expression

            IrTypeOperator.CAST, IrTypeOperator.IMPLICIT_CAST,
            IrTypeOperator.IMPLICIT_NOTNULL, IrTypeOperator.SAFE_CAST -> {

                val newExpressionType = if (expression.operator == IrTypeOperator.SAFE_CAST) {
                    newTypeOperand.makeNullable()
                } else {
                    newTypeOperand
                }

                IrTypeOperatorCallImpl(expression.startOffset, expression.endOffset,
                        newExpressionType, expression.operator, newTypeOperand,
                        expression.argument).useAs(expression.type)
            }

            IrTypeOperator.INSTANCEOF, IrTypeOperator.NOT_INSTANCEOF -> if (newTypeOperand == expression.typeOperand) {
                // Do not create new expression if nothing changes:
                expression
            } else {
                IrTypeOperatorCallImpl(expression.startOffset, expression.endOffset,
                        expression.type, expression.operator, newTypeOperand, expression.argument)
            }
        }
    }

    private var currentFunctionDescriptor: FunctionDescriptor? = null

    override fun visitFunction(declaration: IrFunction): IrStatement {
        currentFunctionDescriptor = declaration.descriptor
        val result = super.visitFunction(declaration)
        currentFunctionDescriptor = null
        return result
    }

    override fun IrExpression.useAsReturnValue(returnTarget: CallableDescriptor): IrExpression {
        if (returnTarget.isSuspend && returnTarget == currentFunctionDescriptor)
            return this.useAs(context.builtIns.nullableAnyType)
        val returnType = returnTarget.returnType
                ?: return this
        return this.useAs(returnType)
    }

    override fun IrExpression.useAs(type: KotlinType): IrExpression {
        val interop = context.interopBuiltIns
        if (this.isNullConst() && interop.nullableInteropValueTypes.any { type.isRepresentedAs(it) }) {
            return IrCallImpl(startOffset, endOffset, symbols.getNativeNullPtr).uncheckedCast(type)
        }

        val actualType = when (this) {
            is IrCall -> {
                if (this.descriptor.isSuspend) context.builtIns.nullableAnyType
                else this.callTarget.returnType ?: this.type
            }
            is IrGetField -> this.descriptor.original.type

            is IrTypeOperatorCall -> when (this.operator) {
                IrTypeOperator.IMPLICIT_INTEGER_COERCION ->
                    // TODO: is it a workaround for inconsistent IR?
                    this.typeOperand

                else -> this.type
            }

            else -> this.type
        }

        return this.adaptIfNecessary(actualType, type)
    }

    private val IrMemberAccessExpression.target: CallableDescriptor get() = when (this) {
        is IrCall -> this.callTarget
        is IrDelegatingConstructorCall -> this.descriptor.original
        else -> TODO(this.render())
    }

    private val IrCall.callTarget: FunctionDescriptor
        get() = if (superQualifier == null && descriptor.isOverridable) {
            // A virtual call.
            descriptor.original
        } else {
            descriptor.target
        }

    override fun IrExpression.useAsDispatchReceiver(expression: IrMemberAccessExpression): IrExpression {
        return this.useAsArgument(expression.target.dispatchReceiverParameter!!)
    }

    override fun IrExpression.useAsExtensionReceiver(expression: IrMemberAccessExpression): IrExpression {
        return this.useAsArgument(expression.target.extensionReceiverParameter!!)
    }

    override fun IrExpression.useAsValueArgument(expression: IrMemberAccessExpression,
                                                 parameter: ValueParameterDescriptor): IrExpression {

        return this.useAsArgument(expression.target.valueParameters[parameter.index])
    }

    override fun IrExpression.useForField(field: PropertyDescriptor): IrExpression {
        return this.useForVariable(field.original)
    }

    private fun IrExpression.adaptIfNecessary(actualType: KotlinType, expectedType: KotlinType): IrExpression {
        val conversion = symbols.getTypeConversion(actualType, expectedType)
        return if (conversion == null) {
            this
        } else {
            val parameter = conversion.descriptor.explicitParameters.single()
            val argument = this.uncheckedCast(parameter.type)

            IrCallImpl(startOffset, endOffset, conversion).apply {
                addArguments(listOf(parameter to argument))
            }.uncheckedCast(this.type) // Try not to bring new type incompatibilities.
        }
    }

    /**
     * Casts this expression to `type` without changing its representation in generated code.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun IrExpression.uncheckedCast(type: KotlinType): IrExpression {
        // TODO: apply some cast if types are incompatible; not required currently.
        return this
    }

    private val ValueType.shortName
        get() = this.classFqName.shortName()

    private fun getBoxType(valueType: ValueType) =
            context.getInternalClass("${valueType.shortName}Box").defaultType

}
