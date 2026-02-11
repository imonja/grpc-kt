package io.github.imonja.grpc.kt.builder.function.impl

import com.google.protobuf.Descriptors.EnumDescriptor
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import io.github.imonja.grpc.kt.builder.function.FunctionSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.import.FunSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.protobufJavaTypeName
import io.github.imonja.grpc.kt.toolkit.protobufKotlinTypeName

/**
 * Builder for creating conversion functions for Kotlin enums.
 */
class EnumConversionFunctionsBuilder : FunctionSpecsBuilder<EnumDescriptor> {

    override fun build(descriptor: EnumDescriptor): FunSpecsWithImports {
        val imports = mutableSetOf<Import>()
        val kotlinType = descriptor.protobufKotlinTypeName
        val javaType = descriptor.protobufJavaTypeName

        val toKotlinProto = FunSpec.builder("toKotlinProto")
            .addModifiers(KModifier.PUBLIC)
            .receiver(javaType)
            .returns(kotlinType)
            .addKdoc("Converts [%T] to [%T]", javaType, kotlinType)
            .beginControlFlow("return when (this)")

        for (value in descriptor.values) {
            toKotlinProto.addStatement("%T.%L -> %T.%L", javaType, value.name, kotlinType, value.name)
        }

        // Handle UNRECOGNIZED
        toKotlinProto.addStatement("%T.UNRECOGNIZED -> %T.UNRECOGNIZED", javaType, kotlinType)

        // Default case for when from java
        toKotlinProto.addStatement("else -> %T.UNRECOGNIZED", kotlinType)

        toKotlinProto.endControlFlow()

        return FunSpecsWithImports(
            funSpecs = listOf(toKotlinProto.build()),
            imports = imports
        )
    }
}
