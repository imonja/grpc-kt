package io.github.imonja.grpc.kt.toolkit.template

import com.squareup.kotlinpoet.CodeBlock

@JvmInline
value class TransformTemplate(val value: String) {
    init {
        require(value.count { it == '%' } <= 1) {
            "TransformTemplate must contain single or no placeholder"
        }
        require(value.count { it == '%' } == 0 || value.contains("%L")) {
            "TransformTemplate must only containe the (%L) placeholder"
        }
    }

    fun safeCall(input: CodeBlock): CodeBlock = if (value.contains("%L")) {
        CodeBlock.of(value, input)
    } else {
        CodeBlock.of(value)
    }

    fun safeCall(input: String): CodeBlock = safeCall(CodeBlock.of(input))
}
