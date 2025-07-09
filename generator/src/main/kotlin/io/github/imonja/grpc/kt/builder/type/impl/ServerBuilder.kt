package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors
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
import com.squareup.kotlinpoet.asClassName
import io.github.imonja.grpc.kt.builder.function.impl.ToJavaProto
import io.github.imonja.grpc.kt.builder.function.impl.ToKotlinProto
import io.github.imonja.grpc.kt.builder.type.TypeSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.decapitalize
import io.github.imonja.grpc.kt.toolkit.descriptorCode
import io.github.imonja.grpc.kt.toolkit.grpcClass
import io.github.imonja.grpc.kt.toolkit.grpcServiceImplBaseName
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.import.TypeSpecsWithImports
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.ServerCalls
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class ServerBuilder : TypeSpecsBuilder<ServiceDescriptor> {
    override fun build(descriptor: ServiceDescriptor): TypeSpecsWithImports {
        val serviceImplClassName = descriptor.grpcServiceImplBaseName

        val stubs: List<MethodStub> = descriptor.methods.map { serviceMethodStub(it) }
        val `super` = AbstractCoroutineServerImpl::class
        val coroutineContextParameter =
            ParameterSpec.builder("coroutineContext", CoroutineContext::class)
                .defaultValue("%T", EmptyCoroutineContext::class)
                .build()
        val implBuilder = TypeSpec
            .classBuilder(serviceImplClassName)
            .addModifiers(KModifier.ABSTRACT)
            .superclass(`super`)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        coroutineContextParameter
                    )
                    .build()
            )
            .addSuperclassConstructorParameter("%N", coroutineContextParameter)

        var serverServiceDefinitionBuilder =
            CodeBlock.of(
                "%M(%M())",
                ServerServiceDefinition::class.member("builder"),
                descriptor.grpcClass.member("getServiceDescriptor")
            )

        for (stub in stubs) {
            implBuilder.addFunction(stub.methodSpec)
            serverServiceDefinitionBuilder = CodeBlock.of(
                """
          %L
            .addMethod(%L)
                """.trimIndent(),
                serverServiceDefinitionBuilder,
                stub.serverMethodDef
            )
        }

        implBuilder.addFunction(
            FunSpec.builder("bindService")
                .addModifiers(KModifier.OVERRIDE, KModifier.FINAL)
                .returns(ServerServiceDefinition::class)
                .addStatement("return %L.build()", serverServiceDefinitionBuilder)
                .build()
        )

        return TypeSpecsWithImports(
            typeSpecs = listOf(implBuilder.build()),
            imports = stubs.map { it.imports }.flatten().toSet()
        )
    }

    private val typeMapper = ProtoTypeMapper()

    fun serviceMethodStub(method: Descriptors.MethodDescriptor): MethodStub {
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

        val methodBuilder = FunSpec.builder(method.name.decapitalize())
            .addModifiers(KModifier.OPEN)
            .addParameter(requestParam)
            .addStatement(
                "throw %T(%M.withDescription(%S))",
                StatusException::class,
                Status::class.member("UNIMPLEMENTED"),
                "Method ${method.fullName} is unimplemented"
            )

        if (method.options.deprecated) {
            methodBuilder.addAnnotation(
                AnnotationSpec.builder(Deprecated::class)
                    .addMember("%S", "The underlying service method is marked deprecated.")
                    .build()
            )
        }

        if (method.isServerStreaming) {
            methodBuilder.returns(Flow::class.asClassName().parameterizedBy(outputType))
        } else {
            methodBuilder.returns(outputType)
            methodBuilder.addModifiers(KModifier.SUSPEND)
        }

        val methodSpec = methodBuilder.build()

        val smdFactory = if (method.isServerStreaming) {
            if (method.isClientStreaming) BIDI_STREAMING_SMD else SERVER_STREAMING_SMD
        } else {
            if (method.isClientStreaming) CLIENT_STREAMING_SMD else UNARY_SMD
        }

        val (requestTransformTemplate, reqImports) = ToKotlinProto.Companion
            .messageTypeTransformCodeTemplate(method.inputType)
        val (responseTransformTemplate, resImports) = ToJavaProto.Companion
            .messageTypeTransformCodeTemplate(method.outputType)
        imports.addAll(reqImports)
        imports.addAll(resImports)

        val implementationCode = if (method.isServerStreaming) {
            if (method.isClientStreaming) {
                CodeBlock.of(
                    """
                        { requests ->
                            %N(requests.map { %L }).map { %L }
                        }
                    """.trimIndent(),
                    methodSpec,
                    requestTransformTemplate.safeCall("it"),
                    responseTransformTemplate.safeCall("it")
                )
            } else {
                CodeBlock.of(
                    """
                        { request ->
                            %N(%L).map { %L }
                        }
                    """.trimIndent(),
                    methodSpec,
                    requestTransformTemplate.safeCall("request"),
                    responseTransformTemplate.safeCall("it")
                )
            }
        } else {
            if (method.isClientStreaming) {
                CodeBlock.of(
                    """
                        { requests ->
                            %N(requests.map { %L }).let { %L }
                        }
                    """.trimIndent(),
                    methodSpec,
                    requestTransformTemplate.safeCall("it"),
                    responseTransformTemplate.safeCall("it")
                )
            } else {
                CodeBlock.of(
                    """
                        { request ->
                            %N(%L).let { %L }
                        }
                    """.trimIndent(),
                    methodSpec,
                    requestTransformTemplate.safeCall("request"),
                    responseTransformTemplate.safeCall("it")
                )
            }
        }

        val serverMethodDef = CodeBlock.of(
            """
                %M(
                    context = this.context,
                    descriptor = %L,
                    implementation = %L,
                )
            """.trimIndent(),
            smdFactory,
            method.descriptorCode,
            implementationCode
        )

        return MethodStub(methodSpec, serverMethodDef, imports)
    }

    data class MethodStub(
        val methodSpec: FunSpec,
        /**
         * A [CodeBlock] that computes a [io.grpc.ServerMethodDefinition] based on an implementation of
         * the function described in [methodSpec].
         */
        val serverMethodDef: CodeBlock,
        val imports: Set<Import>
    )

    companion object {
        private val UNARY_SMD: MemberName = ServerCalls::class.member("unaryServerMethodDefinition")
        private val CLIENT_STREAMING_SMD: MemberName =
            ServerCalls::class.member("clientStreamingServerMethodDefinition")
        private val SERVER_STREAMING_SMD: MemberName =
            ServerCalls::class.member("serverStreamingServerMethodDefinition")
        private val BIDI_STREAMING_SMD: MemberName =
            ServerCalls::class.member("bidiStreamingServerMethodDefinition")
    }
}
