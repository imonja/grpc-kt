package io.github.imonja.grpc.kt

import com.google.protobuf.Descriptors
import com.google.protobuf.compiler.PluginProtos
import com.squareup.kotlinpoet.FileSpec
import io.github.imonja.grpc.kt.builder.file.impl.DataClassBuilder
import io.github.imonja.grpc.kt.builder.file.impl.ServerGrpcSpecBuilder

object CodeBuilder {

    private val subGenerators = listOf(
        ServerGrpcSpecBuilder(),
        DataClassBuilder()
    )

    /**
     * Generates Kotlin code from the given [request].
     */
    fun buildCode(request: PluginProtos.CodeGeneratorRequest) {
        val fileNameToDescriptor = mutableMapOf<String, Descriptors.FileDescriptor>()
        request.protoFileList.toList()
            .forEach { file ->
                val deps = file.dependencyList.map { dep ->
                    fileNameToDescriptor[dep]
                        ?: throw IllegalStateException("Dependency $dep not found for file ${file.name}")
                }
                fileNameToDescriptor[file.name] =
                    Descriptors.FileDescriptor.buildFrom(file, deps.toTypedArray())
            }

        fileNameToDescriptor.filterNot { (fileName, _) ->
            fileName.startsWith("google/") ||
                fileName.startsWith("validate/") ||
                fileName.startsWith("protobuf/") ||
                fileName.startsWith("googleapis/") ||
                fileName.startsWith("grpc/")
        }.forEach { (_, descriptor) ->
            val responseBuilder = PluginProtos.CodeGeneratorResponse.newBuilder()
                .setSupportedFeatures(PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL_VALUE.toLong())
            responseBuilder.addAllFile(
                kotlinPoetFileSpecToCodeGeneratorResponseFile(
                    subGenerators.flatMap { it.build(descriptor) }
                )
            ).build().writeTo(System.out)
        }
    }

    /**
     * Converts a list of [FileSpec] to a list of [PluginProtos.CodeGeneratorResponse.File].
     */
    private fun kotlinPoetFileSpecToCodeGeneratorResponseFile(
        fileSpecs: List<FileSpec>
    ): List<PluginProtos.CodeGeneratorResponse.File> {
        return fileSpecs.map { fileSpec ->
            PluginProtos.CodeGeneratorResponse.File.newBuilder().apply {
                name = "${fileSpec.packageName.replace('.', '/')}/${fileSpec.name}"
                content = fileSpec.toString()
            }.build()
        }
    }
}
