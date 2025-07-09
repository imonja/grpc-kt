package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors.Descriptor
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.imonja.grpc.kt.builder.type.TypeSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.import.TypeSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.isExplicitlyOptional
import io.github.imonja.grpc.kt.toolkit.isGooglePackageType
import io.github.imonja.grpc.kt.toolkit.isProtoOptional
import io.github.imonja.grpc.kt.toolkit.protobufJavaTypeName
import io.github.imonja.grpc.kt.toolkit.protobufKotlinTypeName

/**
 * Builder for creating Kotlin data classes from Protocol Buffer message descriptors.
 */
class DataClassTypeBuilder(
    private val typeMapper: ProtoTypeMapper,
    private val oneOfBuilder: OneOfTypeBuilder
) : TypeSpecsBuilder<Descriptor> {

    override fun build(descriptor: Descriptor): TypeSpecsWithImports {
        if (descriptor.isGooglePackageType() || descriptor.options.mapEntry) {
            return TypeSpecsWithImports.Companion.EMPTY
        }

        val imports = mutableSetOf<Import>()
        val className = descriptor.protobufKotlinTypeName

        val dataClassBuilder = TypeSpec.classBuilder(className)
            .apply {
                this.addKdoc(
                    "Data class as a Kotlin representation of the [%T] protocol buffer message",
                    descriptor.protobufJavaTypeName
                )
                if (descriptor.fields.isNotEmpty()) {
                    addModifiers(KModifier.DATA)
                }
            }

        val constructorBuilder = FunSpec.constructorBuilder()

        // Process nested types
        for (nestedType in descriptor.nestedTypes) {
            build(nestedType).let {
                imports.addAll(it.imports)
                it.typeSpecs.forEach { dataClassBuilder.addType(it) }
            }
        }

        // Process nested enums
        for (nestedEnum in descriptor.enumTypes) {
            val enumJavaTypeName = nestedEnum.protobufJavaTypeName
            imports.add(Import(enumJavaTypeName.packageName, enumJavaTypeName.simpleNames))
        }

        // Process oneofs
        descriptor.realOneofs.forEach { oneOf ->
            val oneOfResult = oneOfBuilder.build(oneOf, descriptor)
            imports.addAll(oneOfResult.imports)
            dataClassBuilder.addType(oneOfResult.typeSpecs.first())

            val oneOfName = oneOfResult.oneOfName
            val oneOfType = oneOfResult.oneOfType

            constructorBuilder.addParameter(
                ParameterSpec.builder(
                    oneOfName,
                    oneOfType.copy(nullable = true)
                ).defaultValue("null").build()
            )

            dataClassBuilder.addProperty(
                PropertySpec.builder(
                    oneOfName,
                    oneOfType.copy(nullable = true)
                ).initializer(oneOfName).build()
            )
        }

        // Process regular fields
        for (field in descriptor.fields) {
            if (
                field.name in descriptor.realOneofs.flatMap { it.fields }.map { it.name }
                    .toSet() || field.isExtension
            ) {
                continue
            }

            val fieldName = field.jsonName
            val (fieldType, default) = typeMapper.mapProtoTypeToKotlinTypeAndDefaultValue(field)
            imports.addAll(default.imports)

            val paramBuilder = ParameterSpec.builder(fieldName, fieldType)
            if (field.options.deprecated) {
                paramBuilder.addAnnotation(
                    AnnotationSpec.builder(Deprecated::class)
                        .addMember(
                            "%S",
                            "The underlying message field is marked deprecated."
                        )
                        .build()
                )
            }
            paramBuilder.defaultValue(default.code)
            constructorBuilder.addParameter(paramBuilder.build())

            val propertyBuilder = PropertySpec.builder(fieldName, fieldType).initializer(fieldName)

            // Add KDoc comments about field optionality
            if (field.isExplicitlyOptional) {
                propertyBuilder.addKdoc("This field was explicitly marked as optional in the proto file.")
            } else if (field.isProtoOptional) {
                propertyBuilder.addKdoc("This field is a message type and is implicitly optional in Proto3.")
            }

            dataClassBuilder.addProperty(propertyBuilder.build())
        }

        dataClassBuilder.primaryConstructor(constructorBuilder.build())
        dataClassBuilder.addType(TypeSpec.companionObjectBuilder().build())

        return TypeSpecsWithImports(
            typeSpecs = listOf(dataClassBuilder.build()),
            imports = imports
        )
    }
}
