package io.github.imonja.grpc.kt.builder.type

import io.github.imonja.grpc.kt.toolkit.import.TypeSpecsWithImports

interface TypeSpecsBuilder<Descriptor> {
    fun build(descriptor: Descriptor): TypeSpecsWithImports
}
