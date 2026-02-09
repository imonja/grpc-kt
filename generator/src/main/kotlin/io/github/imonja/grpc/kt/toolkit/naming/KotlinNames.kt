package io.github.imonja.grpc.kt.toolkit.naming

import com.google.protobuf.Descriptors
import com.squareup.kotlinpoet.ClassName
import io.github.imonja.grpc.kt.toolkit.fieldNameToJsonName
import io.github.imonja.grpc.kt.toolkit.kotlinPackage

/**
 * Utility for managing Kotlin-specific naming conventions in the generated code.
 * Centralizes the logic for adding suffixes and formatting names for messages, enums, and oneOf fields.
 */
object KotlinNames {
    /**
     * The default suffix appended to generated Kotlin classes and interfaces to avoid naming conflicts with Java classes.
     */
    const val SUFFIX = "Kt"

    /**
     * Appends the [SUFFIX] to the given [name] if it doesn't already end with it.
     */
    fun withSuffix(name: String): String =
        if (name.endsWith(SUFFIX)) name else name + SUFFIX

    /**
     * Appends the [SUFFIX] to each name in the [names] list using [withSuffix].
     */
    fun withSuffix(names: List<String>): List<String> = names.map { withSuffix(it) }

    /**
     * Returns the Kotlin file name for a message.
     */
    fun messageFileName(simpleName: String): String = "${withSuffix(simpleName)}.kt"

    /**
     * Returns the Kotlin file name for an enum.
     */
    fun enumFileName(simpleName: String): String = "${withSuffix(simpleName)}.kt"

    /**
     * Returns the simple name for a oneOf sealed interface.
     */
    fun oneOfInterfaceSimpleName(oneOfJsonName: String): String =
        withSuffix(oneOfJsonName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })

    /**
     * Returns the simple name for a oneOf variant data class.
     */
    fun oneOfVariantSimpleName(fieldJsonName: String): String =
        withSuffix(fieldJsonName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })

    /**
     * Returns the [ClassName] for a oneOf interface within the parent message.
     */
    fun oneOfInterfaceClassName(
        parent: Descriptors.Descriptor,
        oneOf: Descriptors.OneofDescriptor
    ): ClassName =
        ClassName(
            parent.file.kotlinPackage,
            *(
                parent.fullName
                    .removePrefix(parent.file.`package` + ".")
                    .split('.')
                    .let { withSuffix(it) } + oneOfInterfaceSimpleName(fieldNameToJsonName(oneOf.name))
                ).toTypedArray()
        )

    /**
     * Returns the [ClassName] for a oneOf variant within the parent message.
     */
    fun oneOfVariantClassName(
        parent: Descriptors.Descriptor,
        oneOf: Descriptors.OneofDescriptor,
        field: Descriptors.FieldDescriptor
    ): ClassName =
        ClassName(
            parent.file.kotlinPackage,
            *(
                parent.fullName
                    .removePrefix(parent.file.`package` + ".")
                    .split('.')
                    .let { withSuffix(it) } +
                    listOf(
                        oneOfInterfaceSimpleName(fieldNameToJsonName(oneOf.name)),
                        oneOfVariantSimpleName(field.jsonName)
                    )
                ).toTypedArray()
        )
}
