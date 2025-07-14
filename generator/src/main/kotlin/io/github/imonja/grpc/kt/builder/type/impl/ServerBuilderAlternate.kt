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
                .addParameter("request", method.parameters[0].type)
                .returns(method.returnType)
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
            FunSpec.builder("createBindableService")
                .addModifiers(KModifier.PRIVATE)
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

        // === PersonServiceCoroutineImplAlternate(...) bind ===
        val implFun = FunSpec.builder("${descriptor.name}CoroutineImplAlternate")
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "FunctionName")
                    .build()
            )
            .addModifiers(KModifier.PUBLIC)
            .returns(BindableService::class)

        stubs.forEach { stub ->
            val name = stub.methodSpec.name
            val ifaceName = "${name.replaceFirstChar { it.uppercase() }}GrpcMethod"
            val defaultImpl = CodeBlock.of(
                "%T(%M.withDescription(%S))",
                StatusException::class,
                Status::class.member("UNIMPLEMENTED"),
                "Method ${name.replaceFirstChar { it.uppercase() }} is unimplemented"
            )
            implFun.addParameter(
                ParameterSpec.builder(name, ClassName("", ifaceName))
                    .defaultValue("$ifaceName { request -> throw $defaultImpl }")
                    .build()
            )
        }

        implFun.addCode("return createBindableService(%M()) {\n", descriptor.grpcClass.member("getServiceDescriptor"))
        stubs.forEach { stub ->
            val name = stub.methodSpec.name
            val methodGetter = descriptor.grpcClass.member("get${name.replaceFirstChar { it.uppercase() }}Method")

            val reqKt = stub.methodSpec.parameters[0].type
            val respKt = stub.methodSpec.returnType

            val reqKtBase = if (reqKt is ParameterizedTypeName && reqKt.rawType.simpleName == "Flow") {
                reqKt.typeArguments[0]
            } else {
                reqKt
            }
            val respKtBase = if (respKt is ParameterizedTypeName && respKt.rawType.simpleName == "Flow") {
                respKt.typeArguments[0]
            } else {
                respKt
            }

            val reqJava = reqKtBase.withoutKtSuffix()
            val respJava = respKtBase

            val isEmptyReturn = respKtBase.copy(nullable = false) == UNIT

            val toJavaProtoExpr = if (isEmptyReturn) {
                "(fun Unit.(): Empty { return Empty.getDefaultInstance() })"
            } else {
                "%T::toJavaProto"
            }

            if (isEmptyReturn) {
                implFun.addCode(
                    """
                    bind(
                        pair = %M() to $name::handle,
                        toKotlinProto = %T::toKotlinProto,
                        toJavaProto = $toJavaProtoExpr
                    )
                    """.trimIndent() + "\n",
                    methodGetter,
                    reqJava
                )
            } else {
                implFun.addCode(
                    """
                    bind(
                        pair = %M() to $name::handle,
                        toKotlinProto = %T::toKotlinProto,
                        toJavaProto = $toJavaProtoExpr
                    )
                    """.trimIndent() + "\n",
                    methodGetter,
                    reqJava,
                    respJava
                )
            }
        }
        implFun.addCode("}")
        objectBuilder.addFunction(implFun.build())

        // === ServerServiceDefinition.Builder.bind(...) extension ===
        val bindFun = FunSpec.builder("bind")
            .addModifiers(KModifier.PUBLIC)
            .receiver(ClassName("io.grpc.ServerServiceDefinition", "Builder"))
            .addTypeVariable(TypeVariableName("ReqT"))
            .addTypeVariable(TypeVariableName("RespT"))
            .addTypeVariable(TypeVariableName("ReqKotlin"))
            .addTypeVariable(TypeVariableName("RespKotlin"))
            .addParameter(
                "pair",
                ClassName("kotlin", "Pair")
                    .parameterizedBy(
                        MethodDescriptor::class.asClassName()
                            .parameterizedBy(TypeVariableName("ReqT"), TypeVariableName("RespT")),
                        ANY
                    )
            )
            .addParameter(
                "toKotlinProto",
                LambdaTypeName.get(TypeVariableName("ReqT"), returnType = TypeVariableName("ReqKotlin"))
            )
            .addParameter(
                "toJavaProto",
                LambdaTypeName.get(TypeVariableName("RespKotlin"), returnType = TypeVariableName("RespT"))
            )
            .addCode(
                CodeBlock.builder()
                    .addStatement("val (descriptor, implementationRaw) = pair")
                    .beginControlFlow("val methodDef = when (descriptor.type)")
                    .add(
                        // --- UNARY (default) case
                        CodeBlock.builder()
                            .addStatement(
                                "%T.UNARY -> %M(",
                                MethodDescriptor.MethodType::class,
                                MemberName("io.grpc.kotlin.ServerCalls", "unaryServerMethodDefinition")
                            )
                            .indent()
                            .addStatement("context = %T,", EmptyCoroutineContext::class)
                            .addStatement("descriptor = descriptor,")
                            .add(
                                CodeBlock.of(
                                    """
                                        implementation = { req ->
                                        val reqKt = toKotlinProto(req as ReqT)
                                        val respKt = (implementationRaw as suspend (ReqKotlin) -> RespKotlin).invoke(reqKt)
                                        toJavaProto(respKt)
                                        }
                                    """.trimMargin()
                                )
                            )
                            .unindent()
                            .addStatement(")")
                            .build()
                    )
                    .add(
                        // --- SERVER_STREAMING case
                        CodeBlock.builder()
                            .addStatement(
                                "%T.SERVER_STREAMING -> %M(",
                                MethodDescriptor.MethodType::class,
                                MemberName("io.grpc.kotlin.ServerCalls", "serverStreamingServerMethodDefinition")
                            )
                            .indent()
                            .addStatement("context = %T,", EmptyCoroutineContext::class)
                            .addStatement("descriptor = descriptor,")
                            .add(
                                CodeBlock.of(
                                    """
                                        implementation = { req ->
                                        val reqKt = toKotlinProto(req as ReqT)
                                        (implementationRaw as (ReqKotlin) -> %T<RespKotlin>)
                                        .invoke(reqKt)
                                        .map(toJavaProto)
                                        }
                                    """.trimMargin(),
                                    ClassName("kotlinx.coroutines.flow", "Flow")
                                )
                            )
                            .unindent()
                            .addStatement(")")
                            .build()
                    )
                    .add(
                        // --- CLIENT_STREAMING case
                        CodeBlock.builder()
                            .addStatement(
                                "%T.CLIENT_STREAMING -> %M(",
                                MethodDescriptor.MethodType::class,
                                MemberName("io.grpc.kotlin.ServerCalls", "clientStreamingServerMethodDefinition")
                            )
                            .indent()
                            .addStatement("context = %T,", EmptyCoroutineContext::class)
                            .addStatement("descriptor = descriptor,")
                            .add(
                                CodeBlock.of(
                                    """
                                        implementation = { reqFlow ->
                                        val adaptedFlow = (reqFlow as %T<ReqT>).map(toKotlinProto)
                                        val resultKt = (implementationRaw as suspend (%T<ReqKotlin>) -> RespKotlin).invoke(adaptedFlow)
                                        toJavaProto(resultKt)
                                        }
                                    """.trimMargin(),
                                    ClassName("kotlinx.coroutines.flow", "Flow"),
                                    ClassName("kotlinx.coroutines.flow", "Flow")
                                )
                            )
                            .unindent()
                            .addStatement(")")
                            .build()
                    )
                    .add(
                        // --- BIDI_STREAMING case
                        CodeBlock.builder()
                            .addStatement(
                                "%T.BIDI_STREAMING -> %M(",
                                MethodDescriptor.MethodType::class,
                                MemberName("io.grpc.kotlin.ServerCalls", "bidiStreamingServerMethodDefinition")
                            )
                            .indent()
                            .addStatement("context = %T,", EmptyCoroutineContext::class)
                            .addStatement("descriptor = descriptor,")
                            .add(
                                CodeBlock.of(
                                    """
                                        implementation = { reqFlow ->
                                        val adaptedFlow = (reqFlow as %T<ReqT>).map(toKotlinProto)
                                        (implementationRaw as (%T<ReqKotlin>) -> %T<RespKotlin>)(adaptedFlow).map(toJavaProto)
                                        }
                                    """.trimMargin(),
                                    ClassName("kotlinx.coroutines.flow", "Flow"),
                                    ClassName("kotlinx.coroutines.flow", "Flow"),
                                    ClassName("kotlinx.coroutines.flow", "Flow")
                                )
                            )
                            .unindent()
                            .addStatement(")")
                            .build()
                    )
                    .addStatement("else -> throw IllegalArgumentException(%S)", "Unsupported method type")
                    .endControlFlow()
                    .addStatement("addMethod(methodDef)")
                    .build()

            )
            .build()

        objectBuilder.addFunction(bindFun)

        return TypeSpecsWithImports(
            typeSpecs = listOf(objectBuilder.build()),
            imports = stubs.flatMap { it.imports }.toSet() + setOf(
                Import("kotlinx.coroutines.flow", listOf("map"))
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
