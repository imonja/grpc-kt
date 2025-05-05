package io.github.imonja.grpc.kt.builder.file.impl

import com.google.protobuf.Descriptors.FileDescriptor
import com.squareup.kotlinpoet.FileSpec
import io.github.imonja.grpc.kt.builder.file.FileSpecBuilder
import io.github.imonja.grpc.kt.builder.function.impl.ConversionFunctionsBuilder
import io.github.imonja.grpc.kt.builder.function.impl.FieldCheckFunctionsBuilder
import io.github.imonja.grpc.kt.builder.type.impl.DataClassTypeBuilder
import io.github.imonja.grpc.kt.builder.type.impl.OneOfTypeBuilder
import io.github.imonja.grpc.kt.builder.type.impl.ProtoTypeMapper
import io.github.imonja.grpc.kt.toolkit.addAllImports
import io.github.imonja.grpc.kt.toolkit.addGeneratedFileComments
import io.github.imonja.grpc.kt.toolkit.kotlinPackage

/**
 * Builder for creating Kotlin data classes from Protocol Buffer file descriptors.
 */
class DataClassBuilder : FileSpecBuilder {
    private val typeMapper = ProtoTypeMapper()
    private val oneOfBuilder = OneOfTypeBuilder(typeMapper)
    private val dataClassTypeBuilder = DataClassTypeBuilder(typeMapper, oneOfBuilder)
    private val conversionFunctionsBuilder = ConversionFunctionsBuilder()
    private val fieldCheckFunctionsBuilder = FieldCheckFunctionsBuilder()

    override fun build(fileDescriptor: FileDescriptor): List<FileSpec> {
        val fileSpecs = mutableMapOf<String, FileSpec>()
        for (messageType in fileDescriptor.messageTypes) {
            val fileSpecBuilder = FileSpec.builder(
                fileDescriptor.kotlinPackage,
                "${messageType.name}Kt.kt"
            )

            // Add data class type specs
            val typeSpecsWithImports = dataClassTypeBuilder.build(messageType)
            typeSpecsWithImports.typeSpecs.forEach { fileSpecBuilder.addType(it) }

            fileSpecBuilder.addGeneratedFileComments(fileDescriptor.name)
            fileSpecBuilder.addAllImports(typeSpecsWithImports.imports)

            // Add conversion functions
            val funSpecsWithImports = conversionFunctionsBuilder.build(messageType)
            if (funSpecsWithImports.funSpecs.isNotEmpty()) {
                fileSpecBuilder.addFunctions(funSpecsWithImports.funSpecs)
                fileSpecBuilder.addAllImports(funSpecsWithImports.imports)
            }

            // Add field check functions
            val fieldCheckFunSpecsWithImports = fieldCheckFunctionsBuilder.build(messageType)
            if (fieldCheckFunSpecsWithImports.funSpecs.isNotEmpty()) {
                fileSpecBuilder.addFunctions(fieldCheckFunSpecsWithImports.funSpecs)
                fileSpecBuilder.addAllImports(fieldCheckFunSpecsWithImports.imports)
            }

            if (fileSpecBuilder.members.isNotEmpty()) {
                fileSpecs[messageType.fullName] = fileSpecBuilder.build()
            }
        }
        return fileSpecs.values.toList()
    }
}
