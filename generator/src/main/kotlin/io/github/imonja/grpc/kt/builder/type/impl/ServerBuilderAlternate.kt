package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors.ServiceDescriptor
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.imonja.grpc.kt.builder.type.TypeSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.grpcClass
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.import.TypeSpecsWithImports
import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import kotlin.coroutines.EmptyCoroutineContext

class ServerBuilderAlternate : TypeSpecsBuilder<ServiceDescriptor> {

    override fun build(descriptor: ServiceDescriptor): TypeSpecsWithImports {
        val stubs = descriptor.methods.map {
            ServerBuilder().serviceMethodStub(it)
        }

        val objectName = "${descriptor.name}GrpcAlternateKt"
        val objectBuilder = TypeSpec.objectBuilder(objectName)
            .addModifiers(KModifier.PUBLIC)

        // === fun interfaces ===
        stubs.forEach { stub ->
            val method = stub.methodSpec
            val ifaceName = "${method.name.replaceFirstChar { it.uppercase() }}GrpcMethod"

            val methodFun = FunSpec.builder("handle")
                .addModifiers(KModifier.ABSTRACT)
                .apply {
                    if (method.modifiers.contains(KModifier.SUSPEND)) addModifiers(KModifier.SUSPEND)
                }
                .addParameter("request", method.parameters[0].type.withoutKtSuffix())
                .returns(method.returnType.withoutKtSuffix())
                .build()

            objectBuilder.addType(
                TypeSpec.funInterfaceBuilder(ifaceName)
                    .addModifiers(KModifier.PUBLIC)
                    .addFunction(methodFun)
                    .build()
            )
        }

        // === GrpcService(...) ===
        objectBuilder.addFunction(
            FunSpec.builder("GrpcService")
                .addModifiers(KModifier.PUBLIC)
                .addParameter(
                    "serviceDescriptor",
                    ClassName("io.grpc", "ServiceDescriptor")
                )
                .addParameter(
                    "builderFn",
                    LambdaTypeName.get(
                        receiver = ClassName("io.grpc.ServerServiceDefinition", "Builder"),
                        returnType = UNIT
                    )
                )
                .returns(BindableService::class)
                .addCode(
                    """
                    return object : %T() {
                        override fun bindService() = %T.builder(serviceDescriptor).apply {
                            builderFn()
                        }.build()
                    }
                    """.trimIndent(),
                    AbstractCoroutineServerImpl::class,
                    ServerServiceDefinition::class
                )
                .build()
        )

        // === PersonServiceCoroutineImplAlternate(...) ===
        val implFun = FunSpec.builder("${descriptor.name}CoroutineImplAlternate")
            .addModifiers(KModifier.PUBLIC)
            .returns(BindableService::class)

        stubs.forEach { stub ->
            val method = stub.methodSpec
            val name = method.name
            val nameUpperCase = name.replaceFirstChar { it.uppercase() }
            val ifaceName = "${nameUpperCase}GrpcMethod"
            val defaultImpl = CodeBlock.of(
                """
                    %T(%M.withDescription(%S))
                """.trimIndent(),
                StatusException::class,
                Status::class.member("UNIMPLEMENTED"),
                "Method $nameUpperCase is unimplemented"
            )

            implFun.addParameter(
                ParameterSpec.builder(name, ClassName("", ifaceName))
                    .defaultValue("$ifaceName { request -> throw $defaultImpl }")
                    .build()
            )
        }

        implFun.addCode("return GrpcService(%M()) {\n", descriptor.grpcClass.member("getServiceDescriptor"))
        stubs.forEach { stub ->
            val name = stub.methodSpec.name
            val methodGetter = descriptor.grpcClass.member("get${name.replaceFirstChar { it.uppercase() }}Method")
            implFun.addCode("    bind(%M() to $name::handle)\n", methodGetter)
        }
        implFun.addCode("}")

        objectBuilder.addFunction(implFun.build())

        // === ServerServiceDefinition.Builder.bind(...) extension ===
        val bindFun = FunSpec.builder("bind")
            .addModifiers(KModifier.PUBLIC)
            .receiver(ClassName("io.grpc.ServerServiceDefinition", "Builder"))
            .addTypeVariable(TypeVariableName("ReqT"))
            .addTypeVariable(TypeVariableName("RespT"))
            .addParameter(
                "pair",
                ClassName("kotlin", "Pair")
                    .parameterizedBy(
                        MethodDescriptor::class.asClassName().parameterizedBy(
                            TypeVariableName("ReqT"),
                            TypeVariableName("RespT")
                        ),
                        ANY
                    )
            )
            .addCode(
                CodeBlock.builder()
                    // --- Step 1: Extract descriptor and implementation
                    .addStatement("val (descriptor, implementation) = pair")
                    // --- Step 2: Build methodDef based on method type
                    .beginControlFlow("val methodDef = when (descriptor.type)")
                    // --- BIDI_STREAMING case
                    .addStatement(
                        "%T.BIDI_STREAMING -> %M(",
                        MethodDescriptor.MethodType::class,
                        MemberName("io.grpc.kotlin.ServerCalls", "bidiStreamingServerMethodDefinition")
                    )
                    .indent()
                    .addStatement("context = %T,", EmptyCoroutineContext::class)
                    .addStatement("descriptor = descriptor,")
                    .addStatement("implementation = implementation as (Flow<ReqT>) -> Flow<RespT>")
                    .unindent()
                    .addStatement(")")
                    // --- SERVER_STREAMING case
                    .addStatement(
                        "%T.SERVER_STREAMING -> %M(",
                        MethodDescriptor.MethodType::class,
                        MemberName("io.grpc.kotlin.ServerCalls", "serverStreamingServerMethodDefinition")
                    )
                    .indent()
                    .addStatement("context = %T,", EmptyCoroutineContext::class)
                    .addStatement("descriptor = descriptor,")
                    .addStatement("implementation = implementation as (ReqT) -> Flow<RespT>")
                    .unindent()
                    .addStatement(")")
                    // --- CLIENT_STREAMING case
                    .addStatement(
                        "%T.CLIENT_STREAMING -> %M(",
                        MethodDescriptor.MethodType::class,
                        MemberName("io.grpc.kotlin.ServerCalls", "clientStreamingServerMethodDefinition")
                    )
                    .indent()
                    .addStatement("context = %T,", EmptyCoroutineContext::class)
                    .addStatement("descriptor = descriptor,")
                    .addStatement("implementation = implementation as suspend (Flow<ReqT>) -> RespT")
                    .unindent()
                    .addStatement(")")
                    // --- UNARY (default) case
                    .addStatement(
                        "else -> %M(",
                        MemberName("io.grpc.kotlin.ServerCalls", "unaryServerMethodDefinition")
                    )
                    .indent()
                    .addStatement("context = %T,", EmptyCoroutineContext::class)
                    .addStatement("descriptor = descriptor,")
                    .addStatement("implementation = implementation as suspend (ReqT) -> RespT")
                    .unindent()
                    .addStatement(")")
                    .endControlFlow()
                    // --- Step 3: Register method in server
                    .addStatement("addMethod(methodDef)")
                    .build()
            )
            .build()

        objectBuilder.addFunction(bindFun)

        return TypeSpecsWithImports(
            typeSpecs = listOf(objectBuilder.build()),
            imports = stubs.flatMap { it.imports }.toSet() + setOf(
                Import("kotlinx.coroutines.flow", listOf("Flow")),
                Import("kotlin.coroutines", listOf("EmptyCoroutineContext")),
                Import("kotlin", listOf("to"))
            )
        )
    }

    private fun TypeName.withoutKtSuffix(): TypeName = when (this) {
        is ClassName -> {
            if (simpleName.endsWith("Kt")) {
                ClassName(packageName, simpleName.removeSuffix("Kt"))
            } else {
                this
            }
        }

        is ParameterizedTypeName -> {
            (rawType.withoutKtSuffix() as ClassName)
                .parameterizedBy(*typeArguments.map { it.withoutKtSuffix() }.toTypedArray())
        }

        else -> this
    }
}
