package io.github.imonja.grpc.kt.toolkit.import

import com.squareup.kotlinpoet.TypeSpec

data class TypeSpecsWithImports(
    val typeSpecs: List<TypeSpec> = emptyList(),
    val imports: Set<Import> = emptySet()
) {
    companion object {
        val EMPTY = TypeSpecsWithImports()
    }
}
