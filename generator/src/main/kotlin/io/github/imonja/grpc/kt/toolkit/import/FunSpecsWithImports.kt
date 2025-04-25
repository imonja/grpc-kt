package io.github.imonja.grpc.kt.toolkit.import

import com.squareup.kotlinpoet.FunSpec

data class FunSpecsWithImports(val funSpecs: List<FunSpec>, val imports: Set<Import> = emptySet()) {
    companion object {
        val EMPTY = FunSpecsWithImports(emptyList())
    }
}
