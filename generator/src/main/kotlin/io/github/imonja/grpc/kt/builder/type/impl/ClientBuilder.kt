package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors.MethodDescriptor
import com.google.protobuf.Descriptors.ServiceDescriptor
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import io.github.imonja.grpc.kt.builder.function.impl.ToJavaProto
import io.github.imonja.grpc.kt.builder.function.impl.ToKotlinProto
import io.github.imonja.grpc.kt.builder.type.TypeSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.descriptorCode
import io.github.imonja.grpc.kt.toolkit.grpcClass
import io.github.imonja.grpc.kt.toolkit.import.FunSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.import.TypeSpecsWithImports
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls
import io.grpc.kotlin.StubFor
import kotlinx.coroutines.flow.Flow
import java.util.*

class ClientBuilder : TypeSpecsBuilder<ServiceDescriptor> {
    override fun build(descriptor: ServiceDescriptor): TypeSpecsWithImports {
        val imports = mutableSetOf<Import>()
        val className = "${descriptor.name}CoroutineStub"
        val superClassType =
            AbstractCoroutineStub::class.asClassName().parameterizedBy(TypeVariableName(className))
        val typeBuilder = TypeSpec.classBuilder(className)
            .superclass(superClassType)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("channel", Channel::class)
                    .addParameter(
                        ParameterSpec.builder("callOptions", CallOptions::class)
                            .defaultValue("%M", CallOptions::class.member("DEFAULT"))
                            .build()
                    )
                    .addAnnotation(JvmOverloads::class)
                    .build()
            )
            .addSuperclassConstructorParameter("channel")
            .addSuperclassConstructorParameter("callOptions")

        val clientMethodStubs = descriptor.methods.map { clientMethodStub(it) }
        clientMethodStubs.forEach {
            it.funSpecs.forEach { function -> typeBuilder.addFunction(function) }
            imports.addAll(it.imports)
        }
        typeBuilder.addFunction(
            FunSpec.builder("build")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("channel", Channel::class)
                .addParameter("callOptions", CallOptions::class)
                .returns(TypeVariableName(className))
                .addStatement("return %T(channel, callOptions)", TypeVariableName(className))
                .build()
        )
        typeBuilder.addAnnotation(
            AnnotationSpec.builder(StubFor::class)
                .addMember("%T::class", descriptor.grpcClass)
                .build()
        )

        return TypeSpecsWithImports(
            typeSpecs = listOf(typeBuilder.build()),
            imports = imports
        )
    }

    private val typeMapper = ProtoTypeMapper()

    fun clientMethodStub(method: MethodDescriptor): FunSpecsWithImports {
        val imports = mutableSetOf<Import>()

        val inputType = typeMapper
            .getTypeNameAndDefaultValue(method.inputType).first.copy(nullable = false)
        val outputType = typeMapper
            .getTypeNameAndDefaultValue(method.outputType).first.copy(nullable = false)

        val requestParam = if (method.isClientStreaming) {
            ParameterSpec.builder("requests", Flow::class.asClassName().parameterizedBy(inputType))
                .build()
        } else {
            ParameterSpec.builder("request", inputType).build()
        }
        val responseType = if (method.isServerStreaming) {
            Flow::class.asClassName().parameterizedBy(outputType)
        } else {
            outputType
        }

        val clientFactoryMethod = if (method.isServerStreaming) {
            if (method.isClientStreaming) BIDI_STREAMING_CMD else SERVER_STREAMING_CMD
        } else {
            if (method.isClientStreaming) CLIENT_STREAMING_CMD else UNARY_CMD
        }

        val (toKotlinProtoTemplate, toKotlinProtoImports) = ToKotlinProto.Companion.messageTypeTransformCodeTemplate(
            method.outputType
        )
        val (toJavaProtoTemplate, toJavaProtoImports) = ToJavaProto.Companion.messageTypeTransformCodeTemplate(
            method.inputType
        )
        imports.addAll(toKotlinProtoImports)
        imports.addAll(toJavaProtoImports)
        val requestCode = if (method.isClientStreaming) {
            CodeBlock.of(
                "requests.map { %L }",
                toJavaProtoTemplate.safeCall("it")
            )
        } else {
            CodeBlock.of(
                "%L",
                toJavaProtoTemplate.safeCall("request")
            )
        }
        val implementationCode = if (method.isServerStreaming) {
            CodeBlock.of(
                """
                    return %M(
                        channel,
                        %L,
                        %L,
                        callOptions,
                        metadata,
                    ).map { %L }
                """.trimIndent(),
                clientFactoryMethod,
                method.descriptorCode,
                requestCode,
                toKotlinProtoTemplate.safeCall("it")
            )
        } else {
            CodeBlock.of(
                """
                    return %M(
                        channel,
                        %L,
                        %L,
                        callOptions,
                        metadata,
                    ).let { %L }
                """.trimIndent(),
                clientFactoryMethod,
                method.descriptorCode,
                requestCode,
                toKotlinProtoTemplate.safeCall("it")
            )
        }
        val methodBuilder =
            FunSpec.builder(method.name.replaceFirstChar { it.lowercase(Locale.getDefault()) })
                .addParameter(requestParam)
                .returns(responseType)
                .addParameter(
                    ParameterSpec.builder("metadata", Metadata::class)
                        .defaultValue("%L", "Metadata()")
                        .build()
                )
                .addStatement(
                    "%L",
                    implementationCode
                )
                .apply {
                    if (!method.isServerStreaming) {
                        addModifiers(KModifier.SUSPEND)
                    }
                }

        if (method.options.deprecated) {
            methodBuilder.addAnnotation(
                AnnotationSpec.builder(Deprecated::class)
                    .addMember("%S", "The underlying service method is marked deprecated.")
                    .build()
            )
        }

        return FunSpecsWithImports(
            funSpecs = listOf(methodBuilder.build()),
            imports = imports
        )
    }

    companion object {
        private val UNARY_CMD: MemberName = ClientCalls::class.member("unaryRpc")
        private val CLIENT_STREAMING_CMD: MemberName =
            ClientCalls::class.member("clientStreamingRpc")
        private val SERVER_STREAMING_CMD: MemberName =
            ClientCalls::class.member("serverStreamingRpc")
        private val BIDI_STREAMING_CMD: MemberName =
            ClientCalls::class.member("bidiStreamingRpc")
    }
}
