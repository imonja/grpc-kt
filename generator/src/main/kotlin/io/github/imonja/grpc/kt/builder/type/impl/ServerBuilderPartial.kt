package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.ServiceDescriptor
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.imonja.grpc.kt.builder.function.impl.ToJavaProto
import io.github.imonja.grpc.kt.builder.function.impl.ToKotlinProto
import io.github.imonja.grpc.kt.builder.type.TypeSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.grpcClass
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.import.TypeSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.protobufJavaTypeName
import io.github.imonja.grpc.kt.toolkit.protobufKotlinTypeName
import io.github.imonja.grpc.kt.toolkit.template.TransformTemplateWithImports
import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import kotlin.coroutines.EmptyCoroutineContext

class ServerBuilderPartial : TypeSpecsBuilder<ServiceDescriptor> {

    override fun build(descriptor: ServiceDescriptor): TypeSpecsWithImports {
        val stubs = descriptor.methods.map {
            ServerBuilder().serviceMethodStub(it)
        }
        val methodDescriptors = descriptor.methods

        val objectName = "${descriptor.name}GrpcPartialKt"
        val objectBuilder = TypeSpec.objectBuilder(objectName)
            .addModifiers(KModifier.PUBLIC)

        // Generate interfaces for each service method that handlers will implement
        val interfacesTypeSpecs = generateInterfaceTypeSpecs(stubs)
        objectBuilder.addTypes(interfacesTypeSpecs)

        // Generate a function to create a bindable service
        val createBindableFunSpec = generateCreateBindableFunSpec()
        objectBuilder.addFunction(createBindableFunSpec)

        // Create a coroutine-based partial implementation for PersonService binding
        val (coroutineImplPartialFunSpec, coroutineImports) = generateCoroutineImplPartialFunSpec(
            descriptor,
            stubs,
            methodDescriptors
        )
        objectBuilder.addFunction(coroutineImplPartialFunSpec)

        // Add an extension function to bind service to ServerServiceDefinition.Builder
        val bindFunSpec = generateBindFunSpec()
        objectBuilder.addFunction(bindFunSpec)

        // Collect all imports from the coroutine implementation function
        val allImports = stubs.flatMap { it.imports }.toSet() + coroutineImports + setOf(
            Import("kotlinx.coroutines.flow", listOf("map"))
        )

        return TypeSpecsWithImports(
            typeSpecs = listOf(objectBuilder.build()),
            imports = allImports
        )
    }

    private fun generateInterfaceTypeSpecs(stubs: List<ServerBuilder.MethodStub>): List<TypeSpec> {
        return stubs.map { stub ->
            val method = stub.methodSpec
            val ifaceName = "${method.name.replaceFirstChar { it.uppercase() }}GrpcMethod"

            val methodFun = FunSpec.builder("invoke")
                .addModifiers(KModifier.PUBLIC, KModifier.OPERATOR, KModifier.ABSTRACT)
                .apply {
                    if (KModifier.SUSPEND in method.modifiers) addModifiers(KModifier.SUSPEND)
                }
                .addParameter("request", method.parameters.first().type)
                .returns(method.returnType)
                .build()

            TypeSpec.funInterfaceBuilder(ifaceName)
                .addFunction(methodFun)
                .build()
        }
    }

    private fun generateCreateBindableFunSpec(): FunSpec {
        return FunSpec.builder("createBindableService")
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
    }

    private fun generateCoroutineImplPartialFunSpec(
        descriptor: ServiceDescriptor,
        stubs: List<ServerBuilder.MethodStub>,
        methodDescriptors: List<Descriptors.MethodDescriptor>
    ): Pair<FunSpec, Set<Import>> {
        val coroutineImplPartialFunSpec = FunSpec.builder("${descriptor.name}CoroutineImplPartial")
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
            coroutineImplPartialFunSpec.addParameter(
                ParameterSpec.builder(name, ClassName("", ifaceName))
                    .defaultValue("$ifaceName { request -> throw $defaultImpl }")
                    .build()
            )
        }

        val allImports = mutableSetOf<Import>()

        coroutineImplPartialFunSpec.addCode(
            "return createBindableService(%M()) {\n",
            descriptor.grpcClass.member("getServiceDescriptor")
        )

        stubs.zip(methodDescriptors).forEach { (stub, methodDescriptor) ->
            val name = stub.methodSpec.name
            val methodGetter = descriptor.grpcClass.member("get${name.replaceFirstChar { it.uppercase() }}Method")

            val respKt = stub.methodSpec.returnType

            val respKtBase = if (respKt is ParameterizedTypeName && respKt.rawType.simpleName == "Flow") {
                respKt.typeArguments[0]
            } else {
                respKt
            }

            val isEmptyReturn = respKtBase.copy(nullable = false) == UNIT

            // Get transform templates from centralized builders
            val (requestTransformTemplate, reqImports) = ToKotlinProto
                .messageTypeTransformCodeTemplate(methodDescriptor.inputType)

            val (responseTransformTemplate, resImports) = if (isEmptyReturn) {
                TransformTemplateWithImports.of("{ Empty.getDefaultInstance() }")
            } else {
                ToJavaProto.messageTypeTransformCodeTemplate(methodDescriptor.outputType)
            }

            allImports.addAll(reqImports)
            allImports.addAll(resImports)

            // Generate function references for bind method
            val requestFunctionRef = if (requestTransformTemplate.value == "%L.toKotlinProto()") {
                CodeBlock.of("{ %L }", requestTransformTemplate.safeCall("this"))
            } else {
                CodeBlock.of("{ %L }", requestTransformTemplate.safeCall("this"))
            }

            val responseFunctionRef = if (isEmptyReturn) {
                responseTransformTemplate.safeCall("Unit")
            } else if (responseTransformTemplate.value == "%L.toJavaProto()") {
                CodeBlock.of("{ %L }", responseTransformTemplate.safeCall("this"))
            } else {
                CodeBlock.of("{ %L }", responseTransformTemplate.safeCall("this"))
            }

            // Get the input and output type names for the bind call
            // Java protobuf types (without Kt suffix) for ReqT, RespT
            val inputJavaTypeName = methodDescriptor.inputType.protobufJavaTypeName
            val outputJavaTypeName = methodDescriptor.outputType.protobufJavaTypeName

            // Kotlin types (with Kt suffix) for ReqKotlin, RespKotlin
            val inputKtTypeName = methodDescriptor.inputType.protobufKotlinTypeName
            val outputKtTypeName = if (isEmptyReturn) {
                UNIT
            } else {
                methodDescriptor.outputType.protobufKotlinTypeName
            }

            coroutineImplPartialFunSpec.addCode(
                """
                    bind<%T, %T, %T, %T>(
                        pair = %M() to $name::invoke,
                        toKotlinProto = %L,
                        toJavaProto = %L
                    )
                """.trimIndent() + "\n",
                inputJavaTypeName,
                outputJavaTypeName,
                inputKtTypeName,
                outputKtTypeName,
                methodGetter,
                requestFunctionRef,
                responseFunctionRef
            )
        }
        coroutineImplPartialFunSpec.addCode("}")
        return coroutineImplPartialFunSpec.build() to allImports
    }

    private fun generateBindFunSpec(): FunSpec {
        val bindFunSpec = FunSpec.builder("bind")
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "UNCHECKED_CAST")
                    .build()
            )
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
                            .add(generateUnaryImplementation())
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
                            .add(generateServerStreamingImplementation())
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
                            .add(generateClientStreamingImplementation())
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
                            .add(generateBidiStreamingImplementation())
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
        return bindFunSpec
    }

    private fun generateUnaryImplementation(): CodeBlock {
        return CodeBlock.builder()
            .addStatement("implementation = { req ->")
            .indent()
            .addStatement("val reqKt = toKotlinProto(req)")
            .addStatement("val respKt = (implementationRaw as suspend (ReqKotlin) -> RespKotlin).invoke(reqKt)")
            .addStatement("toJavaProto(respKt)")
            .unindent()
            .addStatement("}")
            .build()
    }

    private fun generateServerStreamingImplementation(): CodeBlock {
        return CodeBlock.builder()
            .addStatement("implementation = { req ->")
            .indent()
            .addStatement("val reqKt = toKotlinProto(req)")
            .addStatement(
                "(implementationRaw as (ReqKotlin) -> %T<RespKotlin>)",
                ClassName("kotlinx.coroutines.flow", "Flow")
            )
            .indent()
            .addStatement(".invoke(reqKt)")
            .addStatement(".map(toJavaProto)")
            .unindent()
            .unindent()
            .addStatement("}")
            .build()
    }

    private fun generateClientStreamingImplementation(): CodeBlock {
        return CodeBlock.builder()
            .addStatement("implementation = { reqFlow ->")
            .indent()
            .addStatement("val adaptedFlow = reqFlow.map(toKotlinProto)")
            .addStatement(
                "val resultKt = (implementationRaw as suspend (%T<ReqKotlin>) -> RespKotlin).invoke(adaptedFlow)",
                ClassName("kotlinx.coroutines.flow", "Flow")
            )
            .addStatement("toJavaProto(resultKt)")
            .unindent()
            .addStatement("}")
            .build()
    }

    private fun generateBidiStreamingImplementation(): CodeBlock {
        return CodeBlock.builder()
            .addStatement("implementation = { reqFlow ->")
            .indent()
            .addStatement("val adaptedFlow = reqFlow.map(toKotlinProto)")
            .addStatement(
                "(implementationRaw as (%T<ReqKotlin>) -> %T<RespKotlin>)(adaptedFlow).map(toJavaProto)",
                ClassName("kotlinx.coroutines.flow", "Flow"),
                ClassName("kotlinx.coroutines.flow", "Flow")
            )
            .unindent()
            .addStatement("}")
            .build()
    }
}
