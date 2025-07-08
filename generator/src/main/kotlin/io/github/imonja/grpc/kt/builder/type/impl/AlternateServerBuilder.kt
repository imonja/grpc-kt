package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors.ServiceDescriptor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.github.imonja.grpc.kt.builder.type.TypeSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.grpcClass
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.import.TypeSpecsWithImports
import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.ServerCalls
import kotlin.Unit
import kotlin.coroutines.EmptyCoroutineContext

class AlternateServerBuilder : TypeSpecsBuilder<ServiceDescriptor> {
    override fun build(descriptor: ServiceDescriptor): TypeSpecsWithImports {
        val stubs: List<ServerBuilder.MethodStub> = descriptor.methods.map {
            ServerBuilder().serviceMethodStub(it)
        }

        // Create the DSL-style builder object
        val dslObjectName = "${descriptor.name}CoroutineImplAlternate"
        val dslObjectBuilder = TypeSpec.objectBuilder(dslObjectName)

        // Add function interfaces for each method
        val functionInterfaces = stubs.map { stub ->
            val method = stub.methodSpec
            val functionInterfaceName = "${method.name.capitalize()}GrpcMethod"

            // Determine if this method should be a suspend function
            val isSuspend = method.modifiers.contains(KModifier.SUSPEND)

            val functionInterface = TypeSpec.funInterfaceBuilder(functionInterfaceName)
                .addSuperinterface(
                    if (!isSuspend) {
                        Function1::class.asClassName().parameterizedBy(
                            method.parameters[0].type,
                            method.returnType
                        )
                    } else {
                        // For suspend functions, we need to use SuspendFunction1
                        ClassName("kotlin.coroutines", "SuspendFunction1").parameterizedBy(
                            method.parameters[0].type,
                            method.returnType
                        )
                    }
                )
                .build()

            dslObjectBuilder.addType(functionInterface)

            functionInterfaceName
        }

        // Add GrpcBuilder class
        val grpcBuilderClassName = ClassName("", "GrpcBuilder")
        val grpcBuilderClass = TypeSpec.classBuilder("GrpcBuilder")
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("builder", ServerServiceDefinition.Builder::class)
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
                    List::class.asClassName()
                        .parameterizedBy(
                            MethodDescriptor::class.asClassName()
                                .parameterizedBy(
                                    com.squareup.kotlinpoet.WildcardTypeName.producerOf(Any::class.asClassName()),
                                    com.squareup.kotlinpoet.WildcardTypeName.producerOf(Any::class.asClassName())
                                )
                        )
                )
                    .initializer("mutableListOf()")
                    .mutable(true)
                    .build()
            )
            .addFunction(
                FunSpec.builder("bind")
                    .addTypeVariable(com.squareup.kotlinpoet.TypeVariableName.invoke("ReqT"))
                    .addTypeVariable(com.squareup.kotlinpoet.TypeVariableName.invoke("RespT"))
                    .addParameter(
                        "pair",
                        Pair::class.asClassName().parameterizedBy(
                            MethodDescriptor::class.asClassName()
                                .parameterizedBy(
                                    com.squareup.kotlinpoet.TypeVariableName.invoke("ReqT"),
                                    com.squareup.kotlinpoet.TypeVariableName.invoke("RespT")
                                ),
                            Any::class.asClassName()
                        )
                    )
                    .addCode(
                        """
                        val (descriptor, implementation) = pair
                        val methodDef = when (descriptor.type) {
                            %T.BIDI_STREAMING -> {
                                %M(
                                    context = %T,
                                    descriptor = descriptor,
                                    implementation = implementation as Function1<Flow<ReqT>, Flow<RespT>>
                                )
                            }
                            %T.SERVER_STREAMING -> {
                                %M(
                                    context = %T,
                                    descriptor = descriptor,
                                    implementation = implementation as Function1<ReqT, Flow<RespT>>
                                )
                            }
                            %T.CLIENT_STREAMING -> {
                                %M(
                                    context = %T,
                                    descriptor = descriptor,
                                    implementation = implementation as kotlin.coroutines.SuspendFunction1<Flow<ReqT>, RespT>
                                )
                            }
                            else -> {
                                %M(
                                    context = %T,
                                    descriptor = descriptor,
                                    implementation = implementation as kotlin.coroutines.SuspendFunction1<ReqT, RespT>
                                )
                            }
                        }
                        builder.addMethod(methodDef)
                        methods += descriptor
                        """.trimIndent(),
                        MethodDescriptor.MethodType::class,
                        ServerCalls::class.member("bidiStreamingServerMethodDefinition"),
                        EmptyCoroutineContext::class,
                        MethodDescriptor.MethodType::class,
                        ServerCalls::class.member("serverStreamingServerMethodDefinition"),
                        EmptyCoroutineContext::class,
                        MethodDescriptor.MethodType::class,
                        ServerCalls::class.member("clientStreamingServerMethodDefinition"),
                        EmptyCoroutineContext::class,
                        ServerCalls::class.member("unaryServerMethodDefinition"),
                        EmptyCoroutineContext::class
                    )
                    .build()
            )
            .build()

        dslObjectBuilder.addType(grpcBuilderClass)

        // Add GrpcService function
        val grpcServiceFunction = FunSpec.builder("GrpcService")
            .addParameter("serviceDescriptor", io.grpc.ServiceDescriptor::class)
            .addParameter(
                ParameterSpec.builder(
                    "builderFn",
                    Function1::class.asClassName()
                        .parameterizedBy(
                            grpcBuilderClassName,
                            Unit::class.asClassName()
                        )
                )
                    .build()
            )
            .returns(BindableService::class)
            .addCode(
                """
                return object : %T() {
                    override fun bindService() = %T.builder(serviceDescriptor).apply {
                        val b = GrpcBuilder(this)
                        builderFn(b)
                        serviceDescriptor.methods
                            .filterNot { m -> b.methods.contains(m) }
                            .forEach {
                                addMethod(
                                    %M(
                                        context = %T,
                                        descriptor = it,
                                        implementation = { throw IllegalStateException("Not implemented: ${'$'}{it}") }
                                    )
                                )
                            }
                    }.build()
                }
                """.trimIndent(),
                AbstractCoroutineServerImpl::class,
                ServerServiceDefinition::class,
                ServerCalls::class.member("unaryServerMethodDefinition"),
                EmptyCoroutineContext::class
            )
            .build()

        dslObjectBuilder.addFunction(grpcServiceFunction)

        // Add a service function
        val serviceFunction = FunSpec.builder("${descriptor.name}GrpcService")
            .returns(BindableService::class)

        // Add parameters for each method
        stubs.forEachIndexed { index, stub ->
            val method = stub.methodSpec
            val functionInterfaceName = functionInterfaces[index]
            val parameterType = ClassName("", dslObjectName).nestedClass(functionInterfaceName)

            serviceFunction.addParameter(
                ParameterSpec.builder(method.name, parameterType)
                    .build()
            )
        }

        // Add function body
        serviceFunction.addCode(
            """
            return GrpcService(%M()) { builder ->
            """.trimIndent(),
            descriptor.grpcClass.member("getServiceDescriptor")
        )

        // Add bind statements for each method
        stubs.forEachIndexed { index, stub ->
            val method = stub.methodSpec
            serviceFunction.addCode(
                """
                    builder.bind(%M() to ${method.name})
                """.trimIndent(),
                descriptor.grpcClass.member("get${method.name.capitalize()}Method")
            )

            // Add a newline between bind statements, except for the last one
            if (index < stubs.size - 1) {
                serviceFunction.addCode("\n")
            }
        }

        serviceFunction.addCode(
            """
            }
            """.trimIndent()
        )

        dslObjectBuilder.addFunction(serviceFunction.build())

        return TypeSpecsWithImports(
            typeSpecs = listOf(dslObjectBuilder.build()),
            imports = stubs.flatMap { it.imports }.toSet() + setOf(Import("kotlin", listOf("to")))
        )
    }
}
