package io.github.imonja.grpc.kt.builder.function.impl

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import io.github.imonja.grpc.kt.builder.function.FunctionSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.MAP_ENTRY_VALUE_FIELD_NUMBER
import io.github.imonja.grpc.kt.toolkit.endControlFlowWithComma
import io.github.imonja.grpc.kt.toolkit.escapeIfNecessary
import io.github.imonja.grpc.kt.toolkit.fieldNameToJsonName
import io.github.imonja.grpc.kt.toolkit.import.CodeWithImports
import io.github.imonja.grpc.kt.toolkit.import.FunSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.isGooglePackageType
import io.github.imonja.grpc.kt.toolkit.isKnownPreDefinedType
import io.github.imonja.grpc.kt.toolkit.isProtoOptional
import io.github.imonja.grpc.kt.toolkit.javaFieldName
import io.github.imonja.grpc.kt.toolkit.kotlinPackage
import io.github.imonja.grpc.kt.toolkit.protobufJavaTypeName
import io.github.imonja.grpc.kt.toolkit.protobufKotlinTypeName
import io.github.imonja.grpc.kt.toolkit.template.TransformTemplateWithImports
import io.github.imonja.grpc.kt.toolkit.type.KnownPreDefinedType

class ToKotlinProto : FunctionSpecsBuilder<Descriptor> {
    override fun build(descriptor: Descriptor): FunSpecsWithImports {
        val imports = mutableSetOf<Import>()
        val generatedType = descriptor.protobufKotlinTypeName
        val protoType = descriptor.protobufJavaTypeName
        val functionBuilder = FunSpec.builder("toKotlinProto")
            .receiver(protoType)
            .returns(generatedType)

        functionBuilder.addCode("return %T(", generatedType)

        for (oneOf in descriptor.realOneofs) {
            val oneOfJsonName = fieldNameToJsonName(oneOf.name)
            functionBuilder.beginControlFlow(
                "%L = when (%LCase)",
                oneOfJsonName.escapeIfNecessary(),
                oneOfJsonName
            )
            for (field in oneOf.fields) {
                val dataClassFieldName = field.jsonName
                val protoFieldName = field.javaFieldName
                val (template, downStreamImports) = transformCodeTemplate(field)
                val oneOfDataClassName = ClassName(
                    oneOf.file.kotlinPackage,
                    generatedType.simpleName,
                    oneOfJsonName.capitalize(),
                    field.jsonName.capitalize()
                )
                functionBuilder.addStatement(
                    "%L.%LCase.%L -> %L( %L = %L )".trimIndent(),
                    protoType,
                    oneOfJsonName.capitalize(),
                    field.name.uppercase(),
                    oneOfDataClassName,
                    dataClassFieldName.escapeIfNecessary(),
                    template.safeCall(protoFieldName.escapeIfNecessary())
                )
                imports.addAll(downStreamImports)
            }
            functionBuilder.addStatement(
                "%L.%LCase.%L -> null",
                protoType,
                oneOfJsonName.capitalize(),
                "${oneOf.name.replace("_", "").uppercase()}_NOT_SET".uppercase()
            )
            functionBuilder.addStatement("null -> null")
            functionBuilder.endControlFlowWithComma()
        }

        for (field in descriptor.fields) {
            if (field.name in descriptor.realOneofs.flatMap { it.fields }.map { it.name }.toSet()) {
                continue
            }

            val dataClassFieldName = field.jsonName
            val protoFieldName = field.javaFieldName
            val optional = field.isProtoOptional
            functionBuilder.addCode("%L = ", dataClassFieldName.escapeIfNecessary())
            if (optional) {
                functionBuilder.beginControlFlow("if (has${protoFieldName.capitalize()}())")
            }

            val codeWithImports = if (field.isMapField) {
                val valueField = field.messageType.findFieldByNumber(MAP_ENTRY_VALUE_FIELD_NUMBER)
                val (template, downStreamImports) = transformCodeTemplate(valueField)
                val mapCodeBlock = if (template.value == "%L") {
                    CodeBlock.of("%LMap", protoFieldName)
                } else {
                    CodeBlock.of(
                        "%LMap.mapValues { %L }",
                        protoFieldName,
                        template.safeCall("it.value")
                    )
                }
                CodeWithImports.Companion.of(mapCodeBlock, downStreamImports)
            } else if (field.isRepeated) {
                val (template, downStreamImports) = transformCodeTemplate(field)
                val repeatedCodeBlock = if (template.value == "%L") {
                    CodeBlock.of("%LList", protoFieldName)
                } else {
                    CodeBlock.of("%LList.map { %L }", protoFieldName, template.safeCall("it"))
                }
                CodeWithImports.Companion.of(repeatedCodeBlock, downStreamImports)
            } else {
                val (template, downStreamImports) = transformCodeTemplate(field)
                CodeWithImports.Companion.of(
                    template.safeCall(protoFieldName.escapeIfNecessary()),
                    downStreamImports
                )
            }

            imports.addAll(codeWithImports.imports)
            functionBuilder.addStatement("%L", codeWithImports.code)
            if (optional) {
                functionBuilder.nextControlFlow("else")
                functionBuilder.addStatement("null")
                functionBuilder.endControlFlowWithComma()
            } else {
                functionBuilder.addCode(", ")
            }
        }
        functionBuilder.addCode(")")
        functionBuilder.addKdoc("Converts [%T] to [%T]", protoType, generatedType)

        return FunSpecsWithImports(
            listOf(functionBuilder.build()),
            imports
        )
    }

    // code template that could be used like CodeBlock.of(transformCodeTemplate, fieldName)
    private fun transformCodeTemplate(field: FieldDescriptor): TransformTemplateWithImports {
        return when (field.type) {
            FieldDescriptor.Type.MESSAGE -> {
                messageTypeTransformCodeTemplate(field.messageType)
            }

            else -> {
                TransformTemplateWithImports.Companion.of("%L")
            }
        }
    }

    companion object {
        fun messageTypeTransformCodeTemplate(descriptor: Descriptor): TransformTemplateWithImports {
            return when {
                descriptor.isGooglePackageType() -> {
                    preDefinedTypeTransformCodeTemplate(descriptor)
                }

                else -> TransformTemplateWithImports.Companion.of("%L.toKotlinProto()")
            }
        }

        private fun preDefinedTypeTransformCodeTemplate(
            descriptor: Descriptor
        ): TransformTemplateWithImports {
            return when {
                descriptor.isKnownPreDefinedType() -> {
                    KnownPreDefinedType.Companion.valueOfByDescriptor(descriptor).toKotlinProtoTemplate
                }

                else -> TransformTemplateWithImports.Companion.of("%L")
            }
        }
    }
}
