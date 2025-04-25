package io.github.imonja.grpc.kt.builder.function

import io.github.imonja.grpc.kt.toolkit.import.FunSpecsWithImports

interface FunctionSpecsBuilder<Descriptor> {
    fun build(descriptor: Descriptor): FunSpecsWithImports
}
