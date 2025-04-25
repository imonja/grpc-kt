package io.github.imonja.grpc.kt.toolkit.import

import com.squareup.kotlinpoet.CodeBlock

data class CodeWithImports(
    val code: CodeBlock,
    val imports: Set<Import> = emptySet()
) {
    companion object {
        fun of(code: CodeBlock, imports: Set<Import> = emptySet()) = CodeWithImports(code, imports)

        fun of(code: String, imports: Set<Import> = emptySet()) =
            CodeWithImports(CodeBlock.of(code), imports)
    }
}
