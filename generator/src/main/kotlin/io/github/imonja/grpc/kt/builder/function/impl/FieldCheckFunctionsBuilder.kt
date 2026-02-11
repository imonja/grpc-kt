package io.github.imonja.grpc.kt.builder.function.impl

import com.google.protobuf.Descriptors.Descriptor
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import io.github.imonja.grpc.kt.builder.function.FunctionSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.import.FunSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.isExplicitlyOptional
import io.github.imonja.grpc.kt.toolkit.isGooglePackageType
import io.github.imonja.grpc.kt.toolkit.isProtoOptional
import io.github.imonja.grpc.kt.toolkit.protobufKotlinTypeName

/**
 * Builder for creating extension functions to check if fields were explicitly set.
 */
class FieldCheckFunctionsBuilder : FunctionSpecsBuilder<Descriptor> {

    /**
     * Builds extension functions for checking if fields were explicitly set.
     */
    override fun build(descriptor: Descriptor): FunSpecsWithImports {
        if (descriptor.isGooglePackageType() || descriptor.options.mapEntry) {
            return FunSpecsWithImports.EMPTY
        }

        val funSpecs = mutableListOf<FunSpec>()
        val imports = mutableSetOf<Import>()
        val generatedType = descriptor.protobufKotlinTypeName

        // Add extension functions for checking if optional fields were explicitly set
        for (field in descriptor.fields) {
            if (field.name in descriptor.realOneofs.flatMap { it.fields }.map { it.name }.toSet() || field.isExtension) {
                continue
            }

            if (field.isProtoOptional) {
                val fieldName = field.jsonName
                val methodName = "has${fieldName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"

                val methodBuilder = FunSpec.builder(methodName)
                    .addModifiers(KModifier.PUBLIC)
                    .receiver(generatedType)
                    .returns(Boolean::class)
                    .addStatement("return this.%N != null", PropertySpec.builder(fieldName, String::class).build())

                if (field.isExplicitlyOptional) {
                    methodBuilder.addKdoc("Checks if the explicitly optional field [%L] was set.", fieldName)
                } else {
                    methodBuilder.addKdoc("Checks if the implicitly optional message field [%L] was set.", fieldName)
                }

                funSpecs.add(methodBuilder.build())
            }
        }

        // Process nested types
        descriptor.nestedTypes.forEach { nestedType ->
            build(nestedType).apply {
                funSpecs.addAll(this.funSpecs)
                imports.addAll(this.imports)
            }
        }

        return FunSpecsWithImports(funSpecs, imports)
    }
}
