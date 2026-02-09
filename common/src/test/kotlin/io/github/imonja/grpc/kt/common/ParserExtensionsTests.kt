package io.github.imonja.grpc.kt.common

import com.google.protobuf.CodedInputStream
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.StringValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class ParserExtensionsTests {
    private val testValue = "test value"
    private val proto = StringValue.newBuilder().setValue(testValue).build()
    private val bytes = proto.toByteArray()
    private val byteString = proto.toByteString()

    @Test
    fun `test toKotlinParser delegates all methods`() {
        val javaParser = StringValue.parser()
        val kotlinParser = javaParser.toKotlinParser { it.value }

        // ByteArray
        assertEquals(testValue, kotlinParser.parseFrom(bytes))
        assertEquals(testValue, kotlinParser.parseFrom(bytes, 0, bytes.size))
        assertEquals(testValue, kotlinParser.parseFrom(bytes, ExtensionRegistryLite.getEmptyRegistry()))
        assertEquals(
            testValue,
            kotlinParser.parseFrom(bytes, 0, bytes.size, ExtensionRegistryLite.getEmptyRegistry())
        )

        // ByteString
        assertEquals(testValue, kotlinParser.parseFrom(byteString))
        assertEquals(
            testValue,
            kotlinParser.parseFrom(byteString, ExtensionRegistryLite.getEmptyRegistry())
        )

        // InputStream
        assertEquals(testValue, kotlinParser.parseFrom(ByteArrayInputStream(bytes)))
        assertEquals(
            testValue,
            kotlinParser.parseFrom(ByteArrayInputStream(bytes), ExtensionRegistryLite.getEmptyRegistry())
        )

        // CodedInputStream
        assertEquals(testValue, kotlinParser.parseFrom(CodedInputStream.newInstance(bytes)))
        assertEquals(
            testValue,
            kotlinParser.parseFrom(CodedInputStream.newInstance(bytes), ExtensionRegistryLite.getEmptyRegistry())
        )

        // ByteBuffer
        assertEquals(testValue, kotlinParser.parseFrom(ByteBuffer.wrap(bytes)))
        assertEquals(
            testValue,
            kotlinParser.parseFrom(ByteBuffer.wrap(bytes), ExtensionRegistryLite.getEmptyRegistry())
        )

        // Delimited
        val out = java.io.ByteArrayOutputStream()
        proto.writeDelimitedTo(out)
        val delimitedBytes = out.toByteArray()
        assertEquals(testValue, kotlinParser.parseDelimitedFrom(ByteArrayInputStream(delimitedBytes)))
        assertEquals(
            testValue,
            kotlinParser.parseDelimitedFrom(ByteArrayInputStream(delimitedBytes), ExtensionRegistryLite.getEmptyRegistry())
        )

        // Partial
        assertEquals(testValue, kotlinParser.parsePartialFrom(bytes))
        assertEquals(testValue, kotlinParser.parsePartialFrom(bytes, 0, bytes.size))
        assertEquals(
            testValue,
            kotlinParser.parsePartialFrom(bytes, ExtensionRegistryLite.getEmptyRegistry())
        )
        assertEquals(
            testValue,
            kotlinParser.parsePartialFrom(bytes, 0, bytes.size, ExtensionRegistryLite.getEmptyRegistry())
        )
        assertEquals(testValue, kotlinParser.parsePartialFrom(byteString))
        assertEquals(
            testValue,
            kotlinParser.parsePartialFrom(byteString, ExtensionRegistryLite.getEmptyRegistry())
        )
        assertEquals(testValue, kotlinParser.parsePartialFrom(ByteArrayInputStream(bytes)))
        assertEquals(
            testValue,
            kotlinParser.parsePartialFrom(ByteArrayInputStream(bytes), ExtensionRegistryLite.getEmptyRegistry())
        )
        assertEquals(
            testValue,
            kotlinParser.parsePartialFrom(CodedInputStream.newInstance(bytes))
        )
        assertEquals(
            testValue,
            kotlinParser.parsePartialFrom(CodedInputStream.newInstance(bytes), ExtensionRegistryLite.getEmptyRegistry())
        )

        // Partial Delimited
        assertEquals(
            testValue,
            kotlinParser.parsePartialDelimitedFrom(ByteArrayInputStream(delimitedBytes))
        )
        assertEquals(
            testValue,
            kotlinParser.parsePartialDelimitedFrom(ByteArrayInputStream(delimitedBytes), ExtensionRegistryLite.getEmptyRegistry())
        )
    }
}
