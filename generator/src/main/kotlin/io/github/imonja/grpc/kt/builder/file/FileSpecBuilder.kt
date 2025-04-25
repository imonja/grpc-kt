package io.github.imonja.grpc.kt.builder.file

import com.google.protobuf.Descriptors.FileDescriptor
import com.squareup.kotlinpoet.FileSpec

interface FileSpecBuilder {
    fun build(fileDescriptor: FileDescriptor): List<FileSpec>
}
