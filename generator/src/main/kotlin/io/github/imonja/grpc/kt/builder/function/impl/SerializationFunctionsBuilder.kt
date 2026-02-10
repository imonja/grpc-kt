package io.github.imonja.grpc.kt.builder.function.impl

import com.google.protobuf.Descriptors.Descriptor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.imonja.grpc.kt.builder.function.FunctionSpecsBuilder
import io.github.imonja.grpc.kt.toolkit.import.FunSpecsWithImports
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.isGooglePackageType
import io.github.imonja.grpc.kt.toolkit.protobufJavaTypeName
import io.github.imonja.grpc.kt.toolkit.protobufKotlinTypeName

/**
 * Builder for creating serialization and deserialization functions for Kotlin data classes.
 */
class SerializationFunctionsBuilder : FunctionSpecsBuilder<Descriptor> {
    override fun build(descriptor: Descriptor): FunSpecsWithImports {
        if (descriptor.isGooglePackageType() || descriptor.options.mapEntry) {
            return FunSpecsWithImports.EMPTY
        }

        val funSpecs = mutableListOf<FunSpec>()
        val imports = mutableSetOf<Import>()

        val kotlinType = descriptor.protobufKotlinTypeName
        val javaType = descriptor.protobufJavaTypeName

        // writeTo(output: OutputStream)
        val outputStreamClass = ClassName("java.io", "OutputStream")
        funSpecs.add(
            FunSpec.builder("writeTo")
                .receiver(kotlinType)
                .addParameter("output", outputStreamClass)
                .addKdoc("Writes the serialized [%T] to an [%T].", kotlinType, outputStreamClass)
                .addStatement("toJavaProto().writeTo(output)")
                .build()
        )

        val byteStringClass = ClassName("com.google.protobuf", "ByteString")
        // parseFrom(data: ByteArray)
        val companionType = ClassName(kotlinType.packageName, *kotlinType.simpleNames.toTypedArray(), "Companion")
        funSpecs.add(
            FunSpec.builder("parseFrom")
                .receiver(companionType)
                .addParameter("data", ByteArray::class)
                .returns(kotlinType)
                .addKdoc("Deserializes [%T] from a byte array.", kotlinType)
                .addStatement("return %T.parseFrom(data).toKotlinProto()", javaType)
                .build()
        )

        // parseFrom(data: ByteString)
        funSpecs.add(
            FunSpec.builder("parseFrom")
                .receiver(companionType)
                .addParameter("data", byteStringClass)
                .returns(kotlinType)
                .addKdoc("Deserializes [%T] from a [%T].", kotlinType, byteStringClass)
                .addStatement("return %T.parseFrom(data).toKotlinProto()", javaType)
                .build()
        )

        // parseFrom(input: InputStream)
        val inputStreamClass = ClassName("java.io", "InputStream")
        funSpecs.add(
            FunSpec.builder("parseFrom")
                .receiver(companionType)
                .addParameter("input", inputStreamClass)
                .returns(kotlinType)
                .addKdoc("Deserializes [%T] from an [%T].", kotlinType, inputStreamClass)
                .addStatement("return %T.parseFrom(input).toKotlinProto()", javaType)
                .build()
        )

        // javaParser()
        val parserClass = ClassName("com.google.protobuf", "Parser")
        funSpecs.add(
            FunSpec.builder("javaParser")
                .receiver(companionType)
                .returns(parserClass.parameterizedBy(javaType))
                .addKdoc("Returns a [%T] for the Java Protobuf message [%T].", parserClass, javaType)
                .addStatement("return %T.parser()", javaType)
                .build()
        )

        // kotlinParser()
        funSpecs.add(
            FunSpec.builder("kotlinParser")
                .receiver(companionType)
                .returns(parserClass.parameterizedBy(kotlinType))
                .addKdoc("Returns a [%T] for [%T].", parserClass, kotlinType)
                .addStatement("return %T.parser().toKotlinParser { it.toKotlinProto() }", javaType)
                .build()
        )

        imports.add(Import("io.github.imonja.grpc.kt.common", listOf("toKotlinParser")))

        // Process nested types
        descriptor.nestedTypes.forEach { nestedType ->
            build(nestedType).apply {
                funSpecs.addAll(this.funSpecs)
                imports.addAll(this.imports)
            }
        }

        return FunSpecsWithImports(funSpecs, imports)
    }
}
