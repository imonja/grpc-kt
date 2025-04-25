package io.github.imonja.grpc.kt.toolkit

import kotlin.text.iterator

internal const val MAP_ENTRY_KEY_FIELD_NUMBER = 1
internal const val MAP_ENTRY_VALUE_FIELD_NUMBER = 2
internal const val GRPC_JAVA_CLASS_NAME_SUFFIX = "Grpc"
internal const val GRPC_KOTLIN_PACKAGE = "kt"

internal fun fieldNameToJsonName(name: String): String {
    val result = StringBuilder()
    var capitalizeNext = false
    for (ch in name) {
        if (ch == '_') {
            capitalizeNext = true
        } else {
            result.append(if (capitalizeNext) ch.uppercaseChar() else ch)
            capitalizeNext = false
        }
    }
    return result.toString()
}
