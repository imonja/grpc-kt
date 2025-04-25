package io.github.imonja.grpc.kt.toolkit

import com.squareup.kotlinpoet.CodeBlock

fun String.capitalize(): String {
    return this.replaceFirstChar { it.uppercase() }
}

fun String.decapitalize(): String {
    return this.replaceFirstChar { it.lowercase() }
}

fun String.escapeIfNecessary(): String {
    return CodeBlock.of("%N", this).toString()
}
