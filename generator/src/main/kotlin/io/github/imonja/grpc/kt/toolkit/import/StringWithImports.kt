package io.github.imonja.grpc.kt.toolkit.import

data class StringWithImports(val string: String, val imports: Set<Import> = emptySet()) {
    companion object {
        fun of(string: String, imports: Set<Import> = emptySet()) =
            StringWithImports(string, imports)
    }
}
