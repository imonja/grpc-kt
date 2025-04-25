package io.github.imonja.grpc.kt.toolkit

import com.google.protobuf.Descriptors.FieldDescriptor
import java.lang.reflect.Method

// Cache the hasOptionalKeyword method to avoid repeated reflection lookups
private val hasOptionalKeywordMethod: Method by lazy {
    val method = FieldDescriptor::class.java.getDeclaredMethod("hasOptionalKeyword")
    method.isAccessible = true
    method
}

// Use reflection to call the package-private hasOptionalKeyword() method
private fun FieldDescriptor.hasOptionalKeywordReflective(): Boolean {
    return try {
        hasOptionalKeywordMethod.invoke(this) as Boolean
    } catch (e: Exception) {
        // If reflection fails, fall back to false
        false
    }
}

val FieldDescriptor.isProtoOptional: Boolean
    get() = when {
        hasOptionalKeywordReflective() -> true
        isRepeated || isMapField -> false
        type != FieldDescriptor.Type.MESSAGE -> false
        else -> messageType.isPrimitiveWrapperType()
    }

val FieldDescriptor.javaFieldName: String
    get() {
        val jsonName = this.jsonName
        /**
         * protobuf-java escapes special fields in order to avoid name clashes with Java/Protobuf keywords
         * @see https://github.com/protocolbuffers/protobuf/blob/2cf94fafe39eeab44d3ab83898aabf03ff930d7a/java/core/src/main/java/com/google/protobuf/DescriptorMessageInfoFactory.java#L629C1-L648
         */
        return if (protobufJavaSpecialFieldNames.contains(jsonName.capitalize())) {
            jsonName + "_"
        } else {
            jsonName
        }
    }

/**
 * @see https://github.com/protocolbuffers/protobuf/blob/2cf94fafe39eeab44d3ab83898aabf03ff930d7a/java/core/src/main/java/com/google/protobuf/DescriptorMessageInfoFactory.java#L72
 */
private val protobufJavaSpecialFieldNames = setOf(
    // java.lang.Object:
    "Class",
    // com.google.protobuf.MessageLiteOrBuilder:
    "DefaultInstanceForType",
    // com.google.protobuf.MessageLite:
    "ParserForType",
    "SerializedSize",
    // com.google.protobuf.MessageOrBuilder:
    "AllFields",
    "DescriptorForType",
    "InitializationErrorString",
    "UnknownFields",
    // obsolete. kept for backwards compatibility of generated code
    "CachedSize"
)
