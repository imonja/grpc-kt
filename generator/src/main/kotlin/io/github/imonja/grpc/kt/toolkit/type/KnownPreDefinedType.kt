package io.github.imonja.grpc.kt.toolkit.type

import com.google.protobuf.*
import com.google.protobuf.Descriptors.Descriptor
import com.squareup.kotlinpoet.*
import io.github.imonja.grpc.kt.toolkit.import.CodeWithImports
import io.github.imonja.grpc.kt.toolkit.import.Import
import io.github.imonja.grpc.kt.toolkit.template.TransformTemplateWithImports
import java.time.LocalDateTime

private const val GRPC_KT_COMMON_PACKAGE = "io.github.imonja.grpc.kt.common"

enum class KnownPreDefinedType(
    val descriptor: Descriptor,
    val kotlinType: TypeName,
    val defaultValue: CodeWithImports,
    val toKotlinProtoTemplate: TransformTemplateWithImports = TransformTemplateWithImports.of("%L"),
    val toJavaProtoTransformTemplate: TransformTemplateWithImports = TransformTemplateWithImports.of(
        "%L"
    )
) {
    EMPTY(
        Empty.getDescriptor(),
        Unit::class.asTypeName(),
        CodeWithImports.of("Unit"),
        TransformTemplateWithImports.of("Unit"),
        TransformTemplateWithImports.of(
            "Empty.getDefaultInstance()",
            setOf(Import("com.google.protobuf", listOf("Empty")))
        )
    ),
    TIMESTAMP(
        Timestamp.getDescriptor(),
        LocalDateTime::class.asTypeName(),
        CodeWithImports.of(
            "Timestamp.getDefaultInstance().toLocalDateTime()",
            setOf(
                Import("com.google.protobuf", listOf("Timestamp")),
                Import(GRPC_KT_COMMON_PACKAGE, listOf("toLocalDateTime"))
            )
        ),
        TransformTemplateWithImports.of(
            "%L.toLocalDateTime()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toLocalDateTime")))
        ),
        TransformTemplateWithImports.of(
            "%L.toProtoTimestamp()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toProtoTimestamp")))
        )
    ),
    DURATION(
        Duration.getDescriptor(),
        java.time.Duration::class.asTypeName(),
        CodeWithImports.of("java.time.Duration.ZERO"),
        TransformTemplateWithImports.of(
            "%L.toDuration()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toDuration")))
        ),
        TransformTemplateWithImports.of(
            "%L.toProtoDuration()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toProtoDuration")))
        )
    ),
    DOUBLE_VALUE(
        DoubleValue.getDescriptor(),
        DOUBLE.copy(nullable = true),
        CodeWithImports.of("null"),
        TransformTemplateWithImports.of("%L.value"),
        TransformTemplateWithImports.of(
            "%L.toDoubleValue()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toDoubleValue")))
        )
    ),
    FLOAT_VALUE(
        FloatValue.getDescriptor(),
        FLOAT.copy(nullable = true),
        CodeWithImports.of("null"),
        TransformTemplateWithImports.of("%L.value"),
        TransformTemplateWithImports.of(
            "%L.toFloatValue()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toFloatValue")))
        )
    ),
    INT64_VALUE(
        Int64Value.getDescriptor(),
        LONG.copy(nullable = true),
        CodeWithImports.of("null"),
        TransformTemplateWithImports.of("%L.value"),
        TransformTemplateWithImports.of(
            "%L.toInt64Value()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toInt64Value")))
        )
    ),
    UINT64_VALUE(
        UInt64Value.getDescriptor(),
        LONG.copy(nullable = true),
        CodeWithImports.of("null"),
        TransformTemplateWithImports.of("%L.value"),
        TransformTemplateWithImports.of(
            "%L.toUInt64Value()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toUInt64Value")))
        )
    ),
    INT32_VALUE(
        Int32Value.getDescriptor(),
        INT.copy(nullable = true),
        CodeWithImports.of("null"),
        TransformTemplateWithImports.of("%L.value"),
        TransformTemplateWithImports.of(
            "%L.toInt32Value()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toInt32Value")))
        )
    ),
    UINT32_VALUE(
        UInt32Value.getDescriptor(),
        INT.copy(nullable = true),
        CodeWithImports.of("null"),
        TransformTemplateWithImports.of("%L.value"),
        TransformTemplateWithImports.of(
            "%L.toUInt32Value()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toUInt32Value")))
        )
    ),
    BOOL_VALUE(
        BoolValue.getDescriptor(),
        BOOLEAN.copy(nullable = true),
        CodeWithImports.of("null"),
        TransformTemplateWithImports.of("%L.value"),
        TransformTemplateWithImports.of(
            "%L.toBoolValue()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toBoolValue")))
        )
    ),
    STRING_VALUE(
        StringValue.getDescriptor(),
        STRING.copy(nullable = true),
        CodeWithImports.of("null"),
        TransformTemplateWithImports.of("%L.value"),
        TransformTemplateWithImports.of(
            "%L.toStringValue()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toStringValue")))
        )
    ),
    BYTES_VALUE(
        BytesValue.getDescriptor(),
        ByteString::class.asTypeName().copy(nullable = true),
        CodeWithImports.of("null"),
        TransformTemplateWithImports.of("%L.value"),
        TransformTemplateWithImports.of(
            "%L.toBytesValue()",
            setOf(Import(GRPC_KT_COMMON_PACKAGE, listOf("toBytesValue")))
        )
    );

    companion object {
        fun valueOfByDescriptor(descriptor: Descriptor): KnownPreDefinedType {
            return entries.first { it.descriptor.fullName == descriptor.fullName }
        }
    }
}
