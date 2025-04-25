package io.github.imonja.grpc.kt.toolkit

import com.google.protobuf.Descriptors.ServiceDescriptor
import com.squareup.kotlinpoet.ClassName

val ServiceDescriptor.grpcServiceImplBaseName: ClassName
    get() {
        return ClassName(this.file.kotlinPackage, "${this.name}CoroutineImplBase")
    }

val ServiceDescriptor.grpcClientStubName: ClassName
    get() {
        return ClassName(this.file.kotlinPackage, "${this.name}CoroutineStub")
    }

val ServiceDescriptor.grpcClass: ClassName
    get() {
        return ClassName(this.file.javaPackage, this.name + GRPC_JAVA_CLASS_NAME_SUFFIX)
    }
