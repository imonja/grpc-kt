package io.github.imonja.grpc.kt.builder.file.impl

import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.ServiceDescriptor
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.imonja.grpc.kt.builder.file.FileSpecBuilder
import io.github.imonja.grpc.kt.builder.type.TypeSpecsBuilder
import io.github.imonja.grpc.kt.builder.type.impl.ClientBuilder
import io.github.imonja.grpc.kt.builder.type.impl.ServerBuilder
import io.github.imonja.grpc.kt.builder.type.impl.ServerBuilderPartial
import io.github.imonja.grpc.kt.toolkit.addAllImports
import io.github.imonja.grpc.kt.toolkit.addGeneratedFileComments
import io.github.imonja.grpc.kt.toolkit.kotlinPackage

class ServerGrpcSpecBuilder : FileSpecBuilder {

    override fun build(fileDescriptor: Descriptors.FileDescriptor): List<FileSpec> {
        val fileSpecs = mutableListOf<FileSpec>()
        fileDescriptor.services.forEach { service ->
            val generators: List<TypeSpecsBuilder<ServiceDescriptor>> = listOf(
                ServerBuilder(),
                ClientBuilder()
            )
            val results = generators.map {
                it.build(service)
            }
            val grpcClassName = "${service.name}GrpcKt"

            fileSpecs.add(
                FileSpec.builder(fileDescriptor.kotlinPackage, "$grpcClassName.kt")
                    .addType(
                        TypeSpec.objectBuilder(grpcClassName)
                            .apply { results.flatMap { it.typeSpecs }.forEach { addType(it) } }
                            .build()
                    ).apply {
                        addGeneratedFileComments(fileDescriptor.name)

                        if (service.methods.any { it.isServerStreaming || it.isClientStreaming }) {
                            addImport("kotlinx.coroutines.flow", "map")
                        }
                        addAllImports(results.flatMap { it.imports }.toSet())
                    }
                    .build()
            )
        }

        fileDescriptor.services.forEach { service ->
            val generators: List<TypeSpecsBuilder<ServiceDescriptor>> = listOf(
                ServerBuilderPartial()
            )
            val results = generators.map {
                it.build(service)
            }
            val grpcClassName = "${service.name}GrpcPartialKt"

            fileSpecs.add(
                FileSpec.builder(fileDescriptor.kotlinPackage, "$grpcClassName.kt")
                    .apply {
                        results.flatMap { it.typeSpecs }.forEach { addType(it) }
                        addGeneratedFileComments(fileDescriptor.name)

                        if (service.methods.any { it.isServerStreaming || it.isClientStreaming }) {
                            addImport("kotlinx.coroutines.flow", "map")
                        }
                        addAllImports(results.flatMap { it.imports }.toSet())
                    }
                    .build()
            )
        }

        return fileSpecs
    }
}
