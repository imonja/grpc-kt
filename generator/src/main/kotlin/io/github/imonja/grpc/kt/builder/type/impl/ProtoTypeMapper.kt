package io.github.imonja.grpc.kt.builder.type.impl

import com.google.protobuf.Descriptors
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.imonja.grpc.kt.toolkit.*
import io.github.imonja.grpc.kt.toolkit.import.CodeWithImports
import io.github.imonja.grpc.kt.toolkit.type.KnownPreDefinedType

/**
 * Utility class for mapping Protocol Buffer types to Kotlin types.
 */
class ProtoTypeMapper {

    /**
     * Maps a Protocol Buffer field to a Kotlin type and default value.
     */
    fun mapProtoTypeToKotlinTypeAndDefaultValue(field: Descriptors.FieldDescriptor): Pair<TypeName, CodeWithImports> {
        var (poetType, defaultValue) = when (
            field.javaType ?: throw IllegalStateException("Field $field does not have a java type")
        ) {
            Descriptors.FieldDescriptor.JavaType.INT -> Pair(INT, CodeWithImports.of("0"))
            Descriptors.FieldDescriptor.JavaType.LONG -> Pair(LONG, CodeWithImports.of("0L"))
            Descriptors.FieldDescriptor.JavaType.FLOAT -> Pair(FLOAT, CodeWithImports.of("0.0f"))
            Descriptors.FieldDescriptor.JavaType.DOUBLE -> Pair(DOUBLE, CodeWithImports.of("0.0"))
            Descriptors.FieldDescriptor.JavaType.BOOLEAN -> Pair(
                BOOLEAN,
                CodeWithImports.of("false")
            )

            Descriptors.FieldDescriptor.JavaType.STRING -> Pair(STRING, CodeWithImports.of("\"\""))
            Descriptors.FieldDescriptor.JavaType.BYTE_STRING -> Pair(
                ClassName("com.google.protobuf", "ByteString"),
                CodeWithImports.of("com.google.protobuf.ByteString.EMPTY")
            )

            Descriptors.FieldDescriptor.JavaType.ENUM -> {
                val enumType = field.enumType
                    ?: throw IllegalStateException("Enum field $field does not have an enum type")
                Pair(
                    enumType.protobufKotlinTypeName,
                    CodeWithImports.of(
                        CodeBlock.of(
                            "%T.${enumType.values[0].name}",
                            enumType.protobufKotlinTypeName
                        )
                    )
                )
            }

            Descriptors.FieldDescriptor.JavaType.MESSAGE -> {
                if (field.isMapField) {
                    val keyType = field.messageType.findFieldByNumber(MAP_ENTRY_KEY_FIELD_NUMBER)
                        ?.let { this.mapProtoTypeToKotlinTypeAndDefaultValue(it).first }
                        ?: throw IllegalStateException("Map field $field does not have a key field")
                    val valueType =
                        field.messageType.findFieldByNumber(MAP_ENTRY_VALUE_FIELD_NUMBER)
                            ?.let { this.mapProtoTypeToKotlinTypeAndDefaultValue(it).first }
                            ?: throw IllegalStateException("Map field $field does not have a value field")
                    val type = MAP.parameterizedBy(
                        keyType.copy(nullable = false),
                        valueType.copy(nullable = false)
                    )
                    Pair(type, CodeWithImports.of("mapOf()"))
                } else {
                    getTypeNameAndDefaultValue(field.messageType)
                }
            }
        }
        if (field.isRepeated && !field.isMapField) {
            poetType = LIST.parameterizedBy(poetType.copy(nullable = false))
            defaultValue = CodeWithImports.of("listOf()")
        }
        return if (field.isProtoOptional) {
            Pair(poetType.copy(nullable = true), CodeWithImports.of("null"))
        } else {
            Pair(poetType.copy(nullable = false), defaultValue)
        }
    }

    /**
     * Gets the type name and default value for a message descriptor.
     */
    fun getTypeNameAndDefaultValue(descriptor: Descriptors.Descriptor): Pair<TypeName, CodeWithImports> {
        val generatedTypeName = descriptor.protobufKotlinTypeName
        if (descriptor.isGooglePackageType()) {
            return getTypeNameAndDefaultValueForPreDefinedTypes(descriptor)
        }
        return Pair(
            generatedTypeName,
            CodeWithImports.of("${generatedTypeName.canonicalName}()")
        )
    }

    /**
     * Gets the type name and default value for predefined types.
     */
    private fun getTypeNameAndDefaultValueForPreDefinedTypes(
        descriptor: Descriptors.Descriptor
    ): Pair<TypeName, CodeWithImports> {
        return if (descriptor.isKnownPreDefinedType()) {
            KnownPreDefinedType.valueOfByDescriptor(descriptor).let {
                it.kotlinType to it.defaultValue
            }
        } else {
            Pair(
                descriptor.protobufJavaTypeName,
                CodeWithImports.of("${descriptor.protobufJavaTypeName.canonicalName}.getDefaultInstance()")
            )
        }
    }
}
