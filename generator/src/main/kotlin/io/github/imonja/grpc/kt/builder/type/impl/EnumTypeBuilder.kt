package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors.EnumDescriptor
import com.squareup.kotlinpoet.TypeSpec
import io.github.imonja.grpc.kt.builder.type.TypeSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.import.TypeSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.protobufJavaTypeName
import io.github.imonja.grpc.kt.toolkit.protobufKotlinTypeName

/**
 * Builder for creating Kotlin enum classes from Protocol Buffer enum descriptors.
 */
class EnumTypeBuilder : TypeSpecsBuilder<EnumDescriptor> {

    override fun build(descriptor: EnumDescriptor): TypeSpecsWithImports {
        val className = descriptor.protobufKotlinTypeName
        val enumBuilder = TypeSpec.enumBuilder(className)
            .addKdoc(
                "Kotlin enum representation of [%T]",
                descriptor.protobufJavaTypeName
            )

        for (value in descriptor.values) {
            enumBuilder.addEnumConstant(value.name)
        }

        // Add UNRECOGNIZED constant for Proto3 compatibility if not present
        if (descriptor.values.none { it.name == "UNRECOGNIZED" }) {
            enumBuilder.addEnumConstant("UNRECOGNIZED")
        }

        return TypeSpecsWithImports(
            typeSpecs = listOf(enumBuilder.build()),
            imports = emptySet()
        )
    }
}
