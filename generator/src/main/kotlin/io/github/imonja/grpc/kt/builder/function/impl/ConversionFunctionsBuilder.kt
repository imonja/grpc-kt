package io.github.imonja.grpc.kt.builder.function.impl

import com.google.protobuf.Descriptors.Descriptor
import com.squareup.kotlinpoet.FunSpec
import io.github.imonja.grpc.kt.toolkit.import.FunSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.isGooglePackageType

/**
 * Builder for creating conversion functions between Protocol Buffer and Kotlin objects.
 */
class ConversionFunctionsBuilder {
    private val toKotlinProto = ToKotlinProto()
    private val toJavaProto = ToJavaProto()
    private val enumConversionFunctionsBuilder = EnumConversionFunctionsBuilder()

    /**
     * Builds conversion functions for a Protocol Buffer message descriptor.
     */
    fun build(descriptor: Descriptor): FunSpecsWithImports {
        if (descriptor.isGooglePackageType() || descriptor.options.mapEntry) {
            return FunSpecsWithImports.EMPTY
        }

        val funSpecs = mutableListOf<FunSpec>()
        val imports = mutableSetOf<Import>()

        // Add toKotlin conversion function
        toKotlinProto.build(descriptor).apply {
            funSpecs.addAll(this.funSpecs)
            imports.addAll(this.imports)
        }

        // Add toJava conversion function
        toJavaProto.build(descriptor).apply {
            funSpecs.addAll(this.funSpecs)
            imports.addAll(this.imports)
        }

        // Process nested types
        descriptor.nestedTypes.forEach { nestedType ->
            build(nestedType).apply {
                funSpecs.addAll(this.funSpecs)
                imports.addAll(this.imports)
            }
        }

        // Process nested enums
        descriptor.enumTypes.forEach { nestedEnum ->
            enumConversionFunctionsBuilder.build(nestedEnum).apply {
                funSpecs.addAll(this.funSpecs)
                imports.addAll(this.imports)
            }
        }

        return FunSpecsWithImports(funSpecs, imports)
    }
}
