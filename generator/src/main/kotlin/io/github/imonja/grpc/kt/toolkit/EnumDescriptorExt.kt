package io.github.imonja.grpc.kt.toolkit

import com.google.protobuf.Descriptors.EnumDescriptor
import com.squareup.kotlinpoet.ClassName

val EnumDescriptor.protobufJavaTypeName: ClassName
    get() {
        var simpleNames = this.fullName
            .replace(this.file.`package` + ".", "")
            .split('.')
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

val EnumDescriptor.protobufKotlinTypeName: ClassName
    get() {
        val simpleNames = this.fullName
            .replace(this.file.`package` + ".", "")
            .split('.')
        return ClassName(this.file.kotlinPackage, simpleNames)
    }
