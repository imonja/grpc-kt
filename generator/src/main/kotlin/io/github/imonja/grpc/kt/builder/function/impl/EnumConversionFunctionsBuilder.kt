package io.github.imonja.grpc.kt.builder.function.impl

import com.google.protobuf.Descriptors.EnumDescriptor
import com.squareup.kotlinpoet.FunSpec
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

        val toJavaProto = FunSpec.builder("toJavaProto")
            .receiver(kotlinType)
            .returns(javaType)
            .addKdoc("Converts [%T] to [%T]", kotlinType, javaType)
            .beginControlFlow("return when (this)")

        val toKotlinProto = FunSpec.builder("toKotlinProto")
            .receiver(javaType)
            .returns(kotlinType)
            .addKdoc("Converts [%T] to [%T]", javaType, kotlinType)
            .beginControlFlow("return when (this)")

        for (value in descriptor.values) {
            toJavaProto.addStatement("%T.%L -> %T.%L", kotlinType, value.name, javaType, value.name)
            toKotlinProto.addStatement("%T.%L -> %T.%L", javaType, value.name, kotlinType, value.name)
        }

        // Handle UNRECOGNIZED
        toJavaProto.addStatement("%T.UNRECOGNIZED -> %T.UNRECOGNIZED", kotlinType, javaType)
        toKotlinProto.addStatement("%T.UNRECOGNIZED -> %T.UNRECOGNIZED", javaType, kotlinType)

        // Default case for when from java
        toKotlinProto.addStatement("else -> %T.UNRECOGNIZED", kotlinType)

        toJavaProto.endControlFlow()
        toKotlinProto.endControlFlow()

        return FunSpecsWithImports(
            funSpecs = listOf(toJavaProto.build(), toKotlinProto.build()),
            imports = imports
        )
    }
}
