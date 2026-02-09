package io.github.imonja.grpc.kt.builder.file.impl

import com.google.protobuf.Descriptors.FileDescriptor
import com.squareup.kotlinpoet.FileSpec
import io.github.imonja.grpc.kt.builder.file.FileSpecBuilder
import io.github.imonja.grpc.kt.builder.function.impl.ConversionFunctionsBuilder
import io.github.imonja.grpc.kt.builder.function.impl.EnumConversionFunctionsBuilder
import io.github.imonja.grpc.kt.builder.function.impl.FieldCheckFunctionsBuilder
import io.github.imonja.grpc.kt.builder.function.impl.SerializationFunctionsBuilder
import io.github.imonja.grpc.kt.builder.type.impl.DataClassTypeBuilder
import io.github.imonja.grpc.kt.builder.type.impl.EnumTypeBuilder
import io.github.imonja.grpc.kt.builder.type.impl.OneOfTypeBuilder
import io.github.imonja.grpc.kt.builder.type.impl.ProtoTypeMapper
import io.github.imonja.grpc.kt.toolkit.addAllImports
import io.github.imonja.grpc.kt.toolkit.addGeneratedFileComments
import io.github.imonja.grpc.kt.toolkit.kotlinPackage
import io.github.imonja.grpc.kt.toolkit.naming.KotlinNames

/**
 * Builder for creating Kotlin data classes from Protocol Buffer file descriptors.
 */
class DataClassBuilder : FileSpecBuilder {
    private val typeMapper = ProtoTypeMapper()
    private val oneOfBuilder = OneOfTypeBuilder(typeMapper)
    private val dataClassTypeBuilder = DataClassTypeBuilder(typeMapper, oneOfBuilder)
    private val enumTypeBuilder = EnumTypeBuilder()
    private val conversionFunctionsBuilder = ConversionFunctionsBuilder()
    private val enumConversionFunctionsBuilder = EnumConversionFunctionsBuilder()
    private val fieldCheckFunctionsBuilder = FieldCheckFunctionsBuilder()
    private val serializationFunctionsBuilder = SerializationFunctionsBuilder()

    override fun build(fileDescriptor: FileDescriptor): List<FileSpec> {
        val fileSpecs = mutableMapOf<String, FileSpec>()
        for (messageType in fileDescriptor.messageTypes) {
            val fileSpecBuilder = FileSpec.builder(
                fileDescriptor.kotlinPackage,
                KotlinNames.messageFileName(messageType.name)
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

            // Add serialization functions
            val serializationFunSpecsWithImports = serializationFunctionsBuilder.build(messageType)
            if (serializationFunSpecsWithImports.funSpecs.isNotEmpty()) {
                fileSpecBuilder.addFunctions(serializationFunSpecsWithImports.funSpecs)
                fileSpecBuilder.addAllImports(serializationFunSpecsWithImports.imports)
            }

            if (fileSpecBuilder.members.isNotEmpty()) {
                fileSpecs[messageType.fullName] = fileSpecBuilder.build()
            }
        }

        for (enumType in fileDescriptor.enumTypes) {
            val fileSpecBuilder = FileSpec.builder(
                fileDescriptor.kotlinPackage,
                KotlinNames.enumFileName(enumType.name)
            )

            // Add enum type specs
            val typeSpecsWithImports = enumTypeBuilder.build(enumType)
            typeSpecsWithImports.typeSpecs.forEach { fileSpecBuilder.addType(it) }

            fileSpecBuilder.addGeneratedFileComments(fileDescriptor.name)
            fileSpecBuilder.addAllImports(typeSpecsWithImports.imports)

            // Add conversion functions
            val funSpecsWithImports = enumConversionFunctionsBuilder.build(enumType)
            if (funSpecsWithImports.funSpecs.isNotEmpty()) {
                fileSpecBuilder.addFunctions(funSpecsWithImports.funSpecs)
                fileSpecBuilder.addAllImports(funSpecsWithImports.imports)
            }

            if (fileSpecBuilder.members.isNotEmpty()) {
                fileSpecs[enumType.fullName] = fileSpecBuilder.build()
            }
        }

        return fileSpecs.values.toList()
    }
}
