package io.github.imonja.grpc.kt.toolkit

import com.squareup.kotlinpoet.CodeBlock

fun String.capitalize(): String = this.replaceFirstChar { it.uppercase() }

fun String.decapitalize(): String = this.replaceFirstChar { it.lowercase() }

fun String.escapeIfNecessary(): String = CodeBlock.of("%N", this).toString()
