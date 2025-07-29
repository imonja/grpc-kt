package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.OneofDescriptor
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.github.imonja.grpc.kt.toolkit.fieldNameToJsonName
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.kotlinPackage
import io.github.imonja.grpc.kt.toolkit.shortNames

/**
 * Builder for creating Kotlin sealed interfaces for Protocol Buffer oneOf fields.
 */
class OneOfTypeBuilder(private val typeMapper: ProtoTypeMapper) {

    /**
     * Result of building a oneOf interface, including the interface type spec, imports,
     * and information needed to add the oneOf property to the parent data class.
     */
    data class OneOfResult(
        val typeSpecs: List<TypeSpec>,
        val imports: Set<Import>,
        val oneOfName: String,
        val oneOfType: TypeName
    )

    /**
     * Builds a sealed interface for a Protocol Buffer oneOf field.
     */
    fun build(oneOf: OneofDescriptor, parentDescriptor: Descriptor): OneOfResult {
        val imports = mutableSetOf<Import>()
        val oneOfJsonName = fieldNameToJsonName(oneOf.name)
        val interfaceSimpleName = oneOfJsonName.capitalize()
        val interfaceClassName = ClassName(
            oneOf.file.kotlinPackage,
            *parentDescriptor.shortNames.toMutableList().apply {
                add(interfaceSimpleName)
            }.toTypedArray()
        )

        val builder = TypeSpec.interfaceBuilder(interfaceClassName)
            .addModifiers(KModifier.SEALED)

        oneOf.fields.forEach { field ->
            val (type, default) = typeMapper.mapProtoTypeToKotlinTypeAndDefaultValue(field)
            imports.addAll(default.imports)

            builder.addType(
                TypeSpec.classBuilder(field.jsonName.capitalize())
                    .addModifiers(KModifier.DATA)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter(
                                ParameterSpec.builder(field.jsonName, type)
                                    .defaultValue(default.code)
                                    .build()
                            )
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder(field.jsonName, type)
                            .initializer(field.jsonName).build()
                    )
                    .apply {
                        if (field.options.deprecated) {
                            addAnnotation(
                                AnnotationSpec.builder(Deprecated::class)
                                    .addMember(
                                        "%S",
                                        "The underlying field is marked deprecated."
                                    )
                                    .build()
                            )
                        }
                    }
                    .addSuperinterface(ClassName("", interfaceSimpleName))
                    .build()
            )
        }

        return OneOfResult(
            listOf(builder.build()),
            imports,
            oneOfJsonName,
            ClassName("", interfaceSimpleName)
        )
    }
}
