package io.github.imonja.grpc.kt.common

import com.google.protobuf.CodedInputStream
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.StringValue
import io.kotest.matchers.shouldBe
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
        kotlinParser.parseFrom(bytes) shouldBe testValue
        kotlinParser.parseFrom(bytes, 0, bytes.size) shouldBe testValue
        kotlinParser.parseFrom(bytes, ExtensionRegistryLite.getEmptyRegistry()) shouldBe testValue
        kotlinParser.parseFrom(
            bytes,
            0,
            bytes.size,
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue

        // ByteString
        kotlinParser.parseFrom(byteString) shouldBe testValue
        kotlinParser.parseFrom(
            byteString,
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue

        // InputStream
        kotlinParser.parseFrom(ByteArrayInputStream(bytes)) shouldBe testValue
        kotlinParser.parseFrom(
            ByteArrayInputStream(bytes),
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue

        // CodedInputStream
        kotlinParser.parseFrom(CodedInputStream.newInstance(bytes)) shouldBe testValue
        kotlinParser.parseFrom(
            CodedInputStream.newInstance(bytes),
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue

        // ByteBuffer
        kotlinParser.parseFrom(ByteBuffer.wrap(bytes)) shouldBe testValue
        kotlinParser.parseFrom(
            ByteBuffer.wrap(bytes),
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue

        // Delimited
        val out = java.io.ByteArrayOutputStream()
        proto.writeDelimitedTo(out)
        val delimitedBytes = out.toByteArray()
        kotlinParser.parseDelimitedFrom(ByteArrayInputStream(delimitedBytes)) shouldBe testValue
        kotlinParser.parseDelimitedFrom(
            ByteArrayInputStream(delimitedBytes),
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue

        // Partial
        kotlinParser.parsePartialFrom(bytes) shouldBe testValue
        kotlinParser.parsePartialFrom(bytes, 0, bytes.size) shouldBe testValue
        kotlinParser.parsePartialFrom(
            bytes,
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue
        kotlinParser.parsePartialFrom(
            bytes,
            0,
            bytes.size,
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue
        kotlinParser.parsePartialFrom(byteString) shouldBe testValue
        kotlinParser.parsePartialFrom(
            byteString,
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue
        kotlinParser.parsePartialFrom(ByteArrayInputStream(bytes)) shouldBe testValue
        kotlinParser.parsePartialFrom(
            ByteArrayInputStream(bytes),
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue
        kotlinParser.parsePartialFrom(CodedInputStream.newInstance(bytes)) shouldBe testValue
        kotlinParser.parsePartialFrom(
            CodedInputStream.newInstance(bytes),
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue

        // Partial Delimited
        kotlinParser.parsePartialDelimitedFrom(ByteArrayInputStream(delimitedBytes)) shouldBe testValue
        kotlinParser.parsePartialDelimitedFrom(
            ByteArrayInputStream(delimitedBytes),
            ExtensionRegistryLite.getEmptyRegistry()
        ) shouldBe testValue
    }
}
