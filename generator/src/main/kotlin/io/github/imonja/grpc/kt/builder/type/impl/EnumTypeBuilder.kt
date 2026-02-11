package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors.EnumDescriptor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
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
        val kotlinType = descriptor.protobufKotlinTypeName
        val javaType = descriptor.protobufJavaTypeName

        val enumBuilder = TypeSpec.enumBuilder(kotlinType)
            .addKdoc(
                "Kotlin enum representation of [%T]",
                javaType
            )
            .addSuperinterface(
                ClassName("io.github.imonja.grpc.kt.common", "ProtoKtEnum").parameterizedBy(javaType)
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec.builder("number", INT).build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("number", INT)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("number")
                    .build()
            )

        // enum constants with explicit numbers
        for (value in descriptor.values) {
            enumBuilder.addEnumConstant(
                value.name,
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("%L", value.number)
                    .build()
            )
        }

        // Add UNRECOGNIZED constant for Proto3 compatibility if not present
        if (descriptor.values.none { it.name == "UNRECOGNIZED" }) {
            enumBuilder.addEnumConstant(
                "UNRECOGNIZED",
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("%L", -1)
                    .build()
            )
        }

        // toJavaProto override
        enumBuilder.addFunction(
            FunSpec.builder("toJavaProto")
                .addModifiers(KModifier.OVERRIDE)
                .returns(javaType)
                .addStatement("return %T.forNumber(number) ?: %T.UNRECOGNIZED", javaType, javaType)
                .build()
        )

        // companion with forNumber
        enumBuilder.addType(
            TypeSpec.companionObjectBuilder()
                .addFunction(
                    FunSpec.builder("forNumber")
                        .addParameter("number", INT)
                        .returns(kotlinType)
                        .addStatement("return entries.firstOrNull { it.number == number } ?: %T.UNRECOGNIZED", kotlinType)
                        .build()
                )
                .build()
        )

        return TypeSpecsWithImports(
            typeSpecs = listOf(enumBuilder.build()),
            imports = emptySet()
        )
    }
}
