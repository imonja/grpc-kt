package io.github.imonja.grpc.kt.builder.function.impl

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import io.github.imonja.grpc.kt.toolkit.MAP_ENTRY_VALUE_FIELD_NUMBER
import io.github.imonja.grpc.kt.toolkit.escapeIfNecessary
import io.github.imonja.grpc.kt.toolkit.fieldNameToJsonName
import io.github.imonja.grpc.kt.toolkit.import.CodeWithImports
import io.github.imonja.grpc.kt.toolkit.import.FunSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.isGooglePackageType
import io.github.imonja.grpc.kt.toolkit.isKnownPreDefinedType
import io.github.imonja.grpc.kt.toolkit.isProtoOptional
import io.github.imonja.grpc.kt.toolkit.javaFieldName
import io.github.imonja.grpc.kt.toolkit.naming.KotlinNames
import io.github.imonja.grpc.kt.toolkit.protobufJavaTypeName
import io.github.imonja.grpc.kt.toolkit.protobufKotlinTypeName
import io.github.imonja.grpc.kt.toolkit.template.TransformTemplateWithImports
import io.github.imonja.grpc.kt.toolkit.type.KnownPreDefinedType

class ToJavaProto {

    fun build(descriptor: Descriptor, isOverride: Boolean): FunSpecsWithImports {
        val imports = mutableSetOf<Import>()
        val generatedType = descriptor.protobufKotlinTypeName
        val protoType = descriptor.protobufJavaTypeName
        val functionBuilder = FunSpec.builder("toJavaProto")
            .addModifiers(KModifier.PUBLIC)
            .returns(protoType)

        if (isOverride) {
            functionBuilder.addModifiers(KModifier.OVERRIDE)
        } else {
            functionBuilder.receiver(generatedType)
        }

        functionBuilder.addCode("return %T.newBuilder()\n", protoType)

        functionBuilder.beginControlFlow(".apply")

        // reference to the outer receiver inside apply {}
        val outerThisRef = if (isOverride) "this@${generatedType.simpleName}" else "this@toJavaProto"

        for (oneOf in descriptor.realOneofs) {
            val oneOfJsonName = fieldNameToJsonName(oneOf.name)
            functionBuilder.beginControlFlow("when (%L)", oneOfJsonName.escapeIfNecessary())
            for (field in oneOf.fields) {
                val oneOfFieldDataClassName = KotlinNames.oneOfVariantClassName(descriptor, oneOf, field)
                functionBuilder.beginControlFlow("is %L ->", oneOfFieldDataClassName)
                val (template, downStreamImports) = transformCodeTemplateForOneOf(field)
                functionBuilder.addStatement(
                    "set${field.javaFieldName.capitalize()}(%L)",
                    CodeBlock.of(
                        "%L",
                        template.safeCall(
                            CodeBlock.of(
                                "%L.%L",
                                oneOfJsonName.escapeIfNecessary(),
                                field.jsonName.escapeIfNecessary()
                            )
                        )
                    )
                )

                functionBuilder.endControlFlow()
                imports.addAll(downStreamImports)
            }
            functionBuilder.addStatement("null -> {}")
            functionBuilder.endControlFlow()
        }

        for (field in descriptor.fields) {
            if (field.name in descriptor.realOneofs.flatMap { it.fields }.map { it.name }.toSet()) {
                continue
            }
            val fieldName = "$outerThisRef.${field.jsonName.escapeIfNecessary()}"
            val optional = field.isProtoOptional
            if (optional) {
                functionBuilder.beginControlFlow("if ($fieldName != null)")
            }

            val codeWithImports = if (field.isMapField) {
                val valueField = field.messageType.findFieldByNumber(MAP_ENTRY_VALUE_FIELD_NUMBER)
                val (template, downStreamImports) = transformCodeTemplate(valueField)
                val mapCodeBlock = if (template.value == "%L") {
                    CodeBlock.of("%L", fieldName)
                } else {
                    CodeBlock.of("%L.mapValues { %L }", fieldName, template.safeCall("it.value"))
                }
                CodeWithImports.of(mapCodeBlock, downStreamImports)
            } else if (field.isRepeated) {
                val (template, downStreamImports) = transformCodeTemplate(field)
                val repeatedCodeBlock = if (template.value == "%L") {
                    CodeBlock.of("%L", fieldName)
                } else {
                    CodeBlock.of("%L.map { %L }", fieldName, template.safeCall("it"))
                }
                CodeWithImports.of(repeatedCodeBlock, downStreamImports)
            } else {
                val (template, downStreamImports) = transformCodeTemplate(field)
                CodeWithImports.of(
                    template.safeCall(fieldName),
                    downStreamImports
                )
            }
            val accessorMethodName = when {
                field.isMapField -> "putAll${field.javaFieldName.capitalize()}"
                field.isRepeated -> "addAll${field.javaFieldName.capitalize()}"
                else -> "set${field.javaFieldName.capitalize()}"
            }

            imports.addAll(codeWithImports.imports)
            functionBuilder.addCode(
                CodeBlock.of(
                    "$accessorMethodName(%L)\n",
                    codeWithImports.code
                )
            )
            if (optional) {
                functionBuilder.endControlFlow()
            }
        }

        functionBuilder.endControlFlow()
        functionBuilder.addCode(".build()")
        functionBuilder.addKdoc("Converts [%T] to [%T]", generatedType, protoType)

        return FunSpecsWithImports(listOf(functionBuilder.build()), imports)
    }

    private fun transformCodeTemplate(field: FieldDescriptor): TransformTemplateWithImports {
        return when (field.type) {
            FieldDescriptor.Type.MESSAGE -> {
                messageTypeTransformCodeTemplate(field.messageType)
            }

            FieldDescriptor.Type.ENUM -> {
                TransformTemplateWithImports.of("%L.toJavaProto()")
            }

            else -> TransformTemplateWithImports.of("%L")
        }
    }

    private fun transformCodeTemplateForOneOf(field: FieldDescriptor): TransformTemplateWithImports {
        return when (field.type) {
            FieldDescriptor.Type.MESSAGE -> {
                messageTypeTransformCodeTemplateForOneOf(field.messageType)
            }

            FieldDescriptor.Type.ENUM -> {
                TransformTemplateWithImports.of("%L.toJavaProto()")
            }

            else -> TransformTemplateWithImports.of("%L")
        }
    }

    companion object {
        fun messageTypeTransformCodeTemplate(descriptor: Descriptor): TransformTemplateWithImports {
            return when {
                descriptor.isGooglePackageType() -> preDefinedTypeTransformCodeTemplate(descriptor)

                else -> TransformTemplateWithImports.of("%L.toJavaProto()")
            }
        }

        fun messageTypeTransformCodeTemplateForOneOf(descriptor: Descriptor): TransformTemplateWithImports {
            return when {
                descriptor.isGooglePackageType() -> preDefinedTypeTransformCodeTemplate(descriptor)
                else -> TransformTemplateWithImports.of("%L?.toJavaProto()")
            }
        }

        private fun preDefinedTypeTransformCodeTemplate(descriptor: Descriptor): TransformTemplateWithImports {
            return when {
                descriptor.isKnownPreDefinedType() -> KnownPreDefinedType.valueOfByDescriptor(
                    descriptor
                ).toJavaProtoTransformTemplate

                else -> TransformTemplateWithImports.of("%L")
            }
        }
    }
}
