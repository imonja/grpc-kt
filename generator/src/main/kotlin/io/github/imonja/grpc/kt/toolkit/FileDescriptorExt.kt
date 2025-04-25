package io.github.imonja.grpc.kt.toolkit

import com.google.protobuf.Descriptors.FileDescriptor

val FileDescriptor.javaPackage: String
    get() = when (val javaPackage = this.options.javaPackage) {
        "" -> this.`package`
        else -> javaPackage
    }

val FileDescriptor.kotlinPackage: String
    get() = this.javaPackage
//    get() = this.javaPackage + "." + GRPC_KOTLIN_PACKAGE

val FileDescriptor.shortName: String
    get() = this.name.split('/').last().split('.').first()
