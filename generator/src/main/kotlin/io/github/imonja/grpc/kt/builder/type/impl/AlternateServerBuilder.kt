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
import io.grpc.kotlin.AbstractCoroutineServerImpl

class AlternateServerBuilder : TypeSpecsBuilder<ServiceDescriptor> {

    // === Utility extension to strip Kt suffix ===
    private fun TypeName.withoutKtSuffix(): TypeName = when (this) {
        is ClassName -> {
            if (simpleName.endsWith("Kt"))
                ClassName(packageName, simpleName.removeSuffix("Kt"))
            else this
        }

        is ParameterizedTypeName -> {
            (rawType.withoutKtSuffix() as ClassName)
                .parameterizedBy(*typeArguments.map { it.withoutKtSuffix() }.toTypedArray())
        }

        else -> this
    }

    override fun build(descriptor: ServiceDescriptor): TypeSpecsWithImports {
        val stubs = descriptor.methods.map {
            ServerBuilder().serviceMethodStub(it)
        }

        val dslObjectName = "${descriptor.name}CoroutineImplAlternate"
        val dslObjectBuilder = TypeSpec.objectBuilder(dslObjectName)

        val topLevelFunInterfaces = mutableListOf<TypeSpec>()

        // Generate fun interfaces per method
        stubs.map { stub ->
            val method = stub.methodSpec
            val functionInterfaceName = "${method.name.replaceFirstChar { it.uppercase() }}GrpcMethod"
            val isSuspend = method.modifiers.contains(KModifier.SUSPEND)

            val methodFunction = FunSpec.builder("handle")
                .addModifiers(KModifier.ABSTRACT)
                .apply { if (isSuspend) addModifiers(KModifier.SUSPEND) }
                .addParameter("request", method.parameters[0].type.withoutKtSuffix())
                .returns(method.returnType.withoutKtSuffix())
                .build()

            val functionInterface = TypeSpec
                .funInterfaceBuilder(functionInterfaceName)
                .addFunction(methodFunction)
                .build()

            topLevelFunInterfaces += functionInterface
        }

        // GrpcBuilderAlternate class
        val grpcBuilderClassName = ClassName("", "GrpcBuilderAlternate")
        val grpcBuilderClass = TypeSpec.classBuilder("GrpcBuilderAlternate")
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("builder", ServerServiceDefinition.Builder::class)
                    .addParameter(
                        ParameterSpec.builder(
                            "methods",
                            MutableList::class.asClassName().parameterizedBy(
                                MethodDescriptor::class.asClassName().parameterizedBy(
                                    WildcardTypeName.producerOf(Any::class.asClassName()),
                                    WildcardTypeName.producerOf(Any::class.asClassName())
                                )
                            )
                        )
                            .defaultValue("mutableListOf()")
                            .build()
                    )

                    .build()
            )
            .addProperty(
                PropertySpec.builder("builder", ServerServiceDefinition.Builder::class)
                    .initializer("builder")
                    .build()
            )
            .addProperty(
                PropertySpec.builder(
                    "methods",
                    MutableList::class.asClassName().parameterizedBy(
                        MethodDescriptor::class.asClassName().parameterizedBy(
                            WildcardTypeName.producerOf(Any::class.asClassName()),
                            WildcardTypeName.producerOf(Any::class.asClassName())
                        )
                    )
                ).initializer("methods").mutable(true).build()
            )
            .addFunction(
                FunSpec.builder("bind")
                    .addTypeVariable(TypeVariableName("ReqT"))
                    .addTypeVariable(TypeVariableName("RespT"))
                    .addParameter(
                        "pair", Pair::class.asClassName().parameterizedBy(
                            MethodDescriptor::class.asClassName()
                                .parameterizedBy(TypeVariableName("ReqT"), TypeVariableName("RespT")),
                            ANY
                        )
                    )
                    .addCode(
                        """
                        val (descriptor, implementation) = pair
                        val methodDef = when (descriptor.type) {
                            MethodDescriptor.MethodType.BIDI_STREAMING ->
                                bidiStreamingServerMethodDefinition(
                                    context = EmptyCoroutineContext,
                                    descriptor = descriptor,
                                    implementation = implementation as (Flow<ReqT>) -> Flow<RespT>
                                )
                            MethodDescriptor.MethodType.SERVER_STREAMING ->
                                serverStreamingServerMethodDefinition(
                                    context = EmptyCoroutineContext,
                                    descriptor = descriptor,
                                    implementation = implementation as (ReqT) -> Flow<RespT>
                                )
                            MethodDescriptor.MethodType.CLIENT_STREAMING ->
                                clientStreamingServerMethodDefinition(
                                    context = EmptyCoroutineContext,
                                    descriptor = descriptor,
                                    implementation = implementation as suspend (Flow<ReqT>) -> RespT
                                )
                            else ->
                                unaryServerMethodDefinition(
                                    context = EmptyCoroutineContext,
                                    descriptor = descriptor,
                                    implementation = implementation as suspend (ReqT) -> RespT
                                )
                        }
                        builder.addMethod(methodDef)
                        methods += descriptor
                        """.trimIndent()
                    )
                    .build()
            )
            .build()

        dslObjectBuilder.addType(grpcBuilderClass)

        // GrpcServiceAlternate(factory) function
        val grpcServiceFunction = FunSpec.builder("GrpcServiceAlternate")
            .addParameter("serviceDescriptor", ClassName("io.grpc", "ServiceDescriptor"))
            .addParameter(
                "builderFn",
                LambdaTypeName.get(receiver = grpcBuilderClassName, returnType = UNIT)
            )
            .returns(BindableService::class)
            .addCode(
                """
                return object : %T() {
                    override fun bindService() = %T.builder(serviceDescriptor).apply {
                        val b = GrpcBuilderAlternate(this)
                        builderFn(b)
                        serviceDescriptor.methods
                            .filterNot { m -> b.methods.contains(m) }
                            .forEach {
                                addMethod(
                                    unaryServerMethodDefinition(
                                        context = EmptyCoroutineContext,
                                        descriptor = it,
                                        implementation = { throw IllegalStateException("Not implemented: ${'$'}it") }
                                    )
                                )
                            }
                    }.build()
                }
                """.trimIndent(),
                AbstractCoroutineServerImpl::class,
                ServerServiceDefinition::class
            )
            .build()

        dslObjectBuilder.addFunction(grpcServiceFunction)

        // GrpcServiceAlternate(...) factory function
        val serviceFunction = FunSpec.builder("${descriptor.name}GrpcServiceAlternate")
            .returns(BindableService::class)

        stubs.forEach { stub ->
            val method = stub.methodSpec
            val aliasName = "${method.name.replaceFirstChar { it.uppercase() }}GrpcMethod"
            serviceFunction.addParameter(method.name, ClassName("", aliasName))
        }

        serviceFunction.addCode("return GrpcServiceAlternate(%M()) {\n", descriptor.grpcClass.member("getServiceDescriptor"))

        stubs.forEachIndexed { index, stub ->
            val method = stub.methodSpec
            val methodGetter = descriptor.grpcClass.member("get${method.name.replaceFirstChar { it.uppercase() }}Method")
            serviceFunction.addCode("    bind(%M() to ${method.name}::handle)\n", methodGetter)
        }

        serviceFunction.addCode("}\n")
        dslObjectBuilder.addFunction(serviceFunction.build())

        return TypeSpecsWithImports(
            typeSpecs = topLevelFunInterfaces + listOf(dslObjectBuilder.build()),
            imports = stubs.flatMap { it.imports }.toSet() + setOf(
                Import("kotlinx.coroutines.flow", listOf("Flow")),
                Import("kotlin.coroutines", listOf("EmptyCoroutineContext")),
                Import("kotlin", listOf("to")),
                Import("io.grpc.kotlin.ServerCalls", listOf("bidiStreamingServerMethodDefinition")),
                Import("io.grpc.kotlin.ServerCalls", listOf("serverStreamingServerMethodDefinition")),
                Import("io.grpc.kotlin.ServerCalls", listOf("clientStreamingServerMethodDefinition")),
                Import("io.grpc.kotlin.ServerCalls", listOf("unaryServerMethodDefinition")),
            )
        )
    }
}
