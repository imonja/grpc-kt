package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.imonja.grpc.kt.builder.type.TypeSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.decapitalize
import io.github.imonja.grpc.kt.toolkit.grpcClass
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.import.TypeSpecsWithImports
import kotlinx.coroutines.flow.Flow

class AlternateServerBuilder : TypeSpecsBuilder<Descriptors.ServiceDescriptor> {
    override fun build(descriptor: Descriptors.ServiceDescriptor): TypeSpecsWithImports {
        val typeSpecs = mutableListOf<TypeSpec>()
        val imports = mutableSetOf<Import>()
        val typeMapper = ProtoTypeMapper()
        val mutableListClassName = ClassName("kotlin.collections", "MutableList")

        // Create the alternate service object
        val alternateServiceObject =
            TypeSpec.objectBuilder("${descriptor.name}CoroutineImplAlternate")

        // Generate typealiases for each method as interface types
        descriptor.methods.forEach { method ->
            val inputType = typeMapper
                .getTypeNameAndDefaultValue(method.inputType).first.copy(nullable = false)
            val outputType = typeMapper
                .getTypeNameAndDefaultValue(method.outputType).first.copy(nullable = false)

            val methodName = method.name
            val typeAliasName = "${methodName.capitalize()}GrpcMethod"

            // Add typealias as a nested interface
            val typeAliasTypeSpec = if (method.isServerStreaming) {
                TypeSpec.interfaceBuilder(typeAliasName)
                    .addSuperinterface(
                        Function1::class.asClassName().parameterizedBy(
                            inputType,
                            Flow::class.asClassName().parameterizedBy(outputType)
                        )
                    )
                    .addModifiers(KModifier.FUN)
                    .build()
            } else {
                TypeSpec.interfaceBuilder(typeAliasName)
                    .addSuperinterface(
                        Function1::class.asClassName().parameterizedBy(
                            inputType,
                            outputType
                        )
                    )
                    .addModifiers(KModifier.FUN)
                    .build()
            }

            alternateServiceObject.addType(typeAliasTypeSpec)
        }

        // Add GrpcBuilder class
        val grpcBuilderClass = TypeSpec.classBuilder("GrpcBuilder")
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("builder", ClassName("io.grpc", "ServerServiceDefinition.Builder"))
                    .addParameter(
                        "methods",
                        mutableListClassName.parameterizedBy(
                            ClassName("io.grpc", "MethodDescriptor").parameterizedBy(STAR, STAR)
                        )
                    )
                    .build()
            )
            .addProperty(
                PropertySpec.builder(
                    "builder",
                    ClassName("io.grpc", "ServerServiceDefinition.Builder"),
                    KModifier.PRIVATE
                ).initializer("builder").build()
            )
            .addProperty(
                PropertySpec.builder(
                    "methods",
                    mutableListClassName.parameterizedBy(
                        ClassName("io.grpc", "MethodDescriptor").parameterizedBy(STAR, STAR)
                    ),
                    KModifier.PRIVATE
                ).initializer("methods").build()
            )
            .addFunction(
                FunSpec.builder("bind")
                    .addModifiers(KModifier.PUBLIC)
                    .addTypeVariable(TypeVariableName("ReqT"))
                    .addTypeVariable(TypeVariableName("RespT"))
                    .addParameter(
                        "pair",
                        Pair::class.asClassName().parameterizedBy(
                            ClassName("io.grpc", "MethodDescriptor")
                                .parameterizedBy(TypeVariableName("ReqT"), TypeVariableName("RespT")),
                            Function1::class.asClassName().parameterizedBy(STAR, STAR)
                        )
                    )
                    .addCode(
                        """
                        unaryServerMethodDefinition(
                            context = EmptyCoroutineContext,
                            descriptor = pair.first,
                            implementation = { request ->
                                throw StatusException(UNIMPLEMENTED.withDescription("Not implemented yet: ${'$'}{pair.first.fullMethodName}"))
                            }
                        ).apply {
                            builder.addMethod(this)
                            methods += pair.first
                        }
                        """.trimIndent()
                    )
                    .build()
            )
            .build()

        alternateServiceObject.addType(grpcBuilderClass)

        // No need for a separate bind function

        // Add GrpcService function
        val grpcServiceFunction = FunSpec.builder("GrpcService")
            .addParameter("serviceDescriptor", ClassName("io.grpc", "ServiceDescriptor"))
            .addParameter(
                "fun1",
                ClassName("kotlin", "Function1")
                    .parameterizedBy(
                        ClassName("", "GrpcBuilder"),
                        ClassName("kotlin", "Unit")
                    )
            )
            .returns(ClassName("io.grpc", "BindableService"))
            .addCode(
                """
                    return object : io.grpc.kotlin.AbstractCoroutineServerImpl() {
                        override fun bindService(): io.grpc.ServerServiceDefinition {
                            return io.grpc.ServerServiceDefinition.builder(serviceDescriptor).build()
                        }
                    }
                """.trimIndent()
            )
            .build()

        alternateServiceObject.addFunction(grpcServiceFunction)

        // Add service function
        val serviceName = descriptor.name
        val serviceFunction = FunSpec.builder("${serviceName}GrpcService")
            .apply {
                descriptor.methods.forEach { method ->
                    val methodName = method.name
                    val typeAliasName = "${methodName.capitalize()}GrpcMethod"
                    addParameter(
                        "${methodName.decapitalize()}GrpcMethod",
                        ClassName("", typeAliasName)
                    )
                }
            }
            .returns(ClassName("io.grpc", "BindableService"))
            .addCode(
                CodeBlock.builder()
                    .add("return GrpcService(${descriptor.grpcClass.canonicalName}.getServiceDescriptor()) { builder ->\n")
                    .apply {
                        descriptor.methods.forEach { method ->
                            val methodName = method.name
                            add(
                                "builder.bind(${descriptor.grpcClass.canonicalName}" +
                                    ".get${methodName}Method() to ${methodName.decapitalize()}GrpcMethod)\n"
                            )
                        }
                    }
                    .add("}")
                    .build()
            )
            .build()

        alternateServiceObject.addFunction(serviceFunction)

        typeSpecs.add(alternateServiceObject.build())

        // Add imports
        imports.add(Import("io.grpc.kotlin", listOf("AbstractCoroutineServerImpl")))
        imports.add(Import("kotlin.coroutines", listOf("EmptyCoroutineContext")))

        if (descriptor.methods.any { it.isServerStreaming }) {
            imports.add(Import("kotlinx.coroutines.flow", listOf("Flow")))
        }

        return TypeSpecsWithImports(typeSpecs, imports)
    }
}
