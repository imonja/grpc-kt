package io.github.imonja.grpc.kt.toolkit

import com.google.protobuf.Descriptors.Descriptor
import com.squareup.kotlinpoet.ClassName
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.naming.KotlinNames
import io.github.imonja.grpc.kt.toolkit.type.KnownPreDefinedType

val Descriptor.protobufJavaTypeName: ClassName
    get() {
        var simpleNames = shortNames
        if (!this.file.options.javaMultipleFiles) {
            simpleNames = listOf(
                if (this.file.options.hasJavaOuterClassname()) {
                    this.file.options.javaOuterClassname
                } else {
                    fieldNameToJsonName(this.file.shortName).capitalize()
                }
            ) + simpleNames
        }
        return ClassName(this.file.javaPackage, simpleNames)
    }

val Descriptor.protobufKotlinTypeName: ClassName
    get() {
        return ClassName(this.file.kotlinPackage, shortKotlinNames)
    }

val Descriptor.shortKotlinNames: List<String>
    get() = shortNames.map { it.plus(KotlinNames.SUFFIX) }

val Descriptor.shortNames: List<String>
    get() = this.fullName
        .replace(this.file.`package` + ".", "")
        .split('.')

fun Descriptor.isGooglePackageType(): Boolean {
    return this.file.name.startsWith("google/")
}

fun Descriptor.isPrimitiveWrapperType(): Boolean {
    val primitiveTypes = setOf(
        KnownPreDefinedType.DOUBLE_VALUE.descriptor.fullName,
        KnownPreDefinedType.FLOAT_VALUE.descriptor.fullName,
        KnownPreDefinedType.INT32_VALUE.descriptor.fullName,
        KnownPreDefinedType.INT64_VALUE.descriptor.fullName,
        KnownPreDefinedType.UINT32_VALUE.descriptor.fullName,
        KnownPreDefinedType.UINT64_VALUE.descriptor.fullName,
        KnownPreDefinedType.BOOL_VALUE.descriptor.fullName,
        KnownPreDefinedType.STRING_VALUE.descriptor.fullName,
        KnownPreDefinedType.BYTES_VALUE.descriptor.fullName
    )
    return this.fullName in primitiveTypes
}

fun Descriptor.isKnownPreDefinedType(): Boolean {
    return KnownPreDefinedType.entries.any {
        it.descriptor.fullName == this.fullName
    }
}

val Descriptor.converterImports: Set<Import>
    get() {
        if (this.isGooglePackageType() || this.options.mapEntry) {
            return setOf()
        }
        return setOf(
            this.toJavaProtoImport,
            this.toKotlinProtoImport
        )
    }

val Descriptor.toJavaProtoImport: Import
    get() {
        val packageName = this.file.kotlinPackage + '.' + this.shortNames.first().lowercase()
        return Import(packageName, listOf("toJavaProto"))
    }

val Descriptor.toKotlinProtoImport: Import
    get() {
//            this.file.kotlinPackage + '.' + this.shortNames.first().lowercase(),
        val packageName = this.file.kotlinPackage + '.' + this.shortKotlinNames.first().lowercase()
        return Import(packageName, listOf("toKotlinProto"))
    }
