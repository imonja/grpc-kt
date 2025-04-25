package io.github.imonja.grpc.kt.toolkit

import com.google.protobuf.Descriptors.MethodDescriptor
import com.squareup.kotlinpoet.CodeBlock

val MethodDescriptor.descriptorCode: CodeBlock
    get() = CodeBlock.of(
        "%T.%L()",
        service.grpcClass,
        "get" + this.name.capitalize() + "Method"
    )
