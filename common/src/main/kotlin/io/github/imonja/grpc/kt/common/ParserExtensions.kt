package io.github.imonja.grpc.kt.common

import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.Parser
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Adapter to transform a [Parser] of Java Protobuf messages into a [Parser] of Kotlin representation.
 */
fun <T, R> Parser<T>.toKotlinParser(mapper: (T) -> R): Parser<R> {
    val delegate = this
    return object : Parser<R> {
        override fun parseFrom(data: ByteArray): R = mapper(delegate.parseFrom(data))
        override fun parseFrom(data: ByteArray, offset: Int, length: Int): R =
            mapper(delegate.parseFrom(data, offset, length))
        override fun parseFrom(data: ByteString): R = mapper(delegate.parseFrom(data))
        override fun parseFrom(input: InputStream): R = mapper(delegate.parseFrom(input))
        override fun parseFrom(input: CodedInputStream): R = mapper(delegate.parseFrom(input))

        override fun parseFrom(data: ByteArray, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parseFrom(data, extensionRegistry))

        override fun parseFrom(
            data: ByteArray,
            offset: Int,
            length: Int,
            extensionRegistry: ExtensionRegistryLite?
        ): R = mapper(delegate.parseFrom(data, offset, length, extensionRegistry))

        override fun parseFrom(data: ByteString, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parseFrom(data, extensionRegistry))

        override fun parseFrom(input: InputStream, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parseFrom(input, extensionRegistry))

        override fun parseFrom(input: CodedInputStream, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parseFrom(input, extensionRegistry))

        override fun parseFrom(data: ByteBuffer): R = mapper(delegate.parseFrom(data))
        override fun parseFrom(data: ByteBuffer, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parseFrom(data, extensionRegistry))

        override fun parsePartialFrom(data: ByteArray): R = mapper(delegate.parsePartialFrom(data))
        override fun parsePartialFrom(data: ByteArray, offset: Int, length: Int): R =
            mapper(delegate.parsePartialFrom(data, offset, length))
        override fun parsePartialFrom(data: ByteString): R = mapper(delegate.parsePartialFrom(data))
        override fun parsePartialFrom(input: InputStream): R = mapper(delegate.parsePartialFrom(input))
        override fun parsePartialFrom(input: CodedInputStream): R =
            mapper(delegate.parsePartialFrom(input))

        override fun parsePartialFrom(data: ByteArray, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parsePartialFrom(data, extensionRegistry))

        override fun parsePartialFrom(
            data: ByteArray,
            offset: Int,
            length: Int,
            extensionRegistry: ExtensionRegistryLite?
        ): R = mapper(delegate.parsePartialFrom(data, offset, length, extensionRegistry))

        override fun parsePartialFrom(data: ByteString, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parsePartialFrom(data, extensionRegistry))

        override fun parsePartialFrom(input: InputStream, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parsePartialFrom(input, extensionRegistry))

        override fun parsePartialFrom(input: CodedInputStream, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parsePartialFrom(input, extensionRegistry))

        override fun parseDelimitedFrom(input: InputStream): R =
            mapper(delegate.parseDelimitedFrom(input))
        override fun parseDelimitedFrom(input: InputStream, extensionRegistry: ExtensionRegistryLite?): R =
            mapper(delegate.parseDelimitedFrom(input, extensionRegistry))

        override fun parsePartialDelimitedFrom(input: InputStream): R =
            mapper(delegate.parsePartialDelimitedFrom(input))
        override fun parsePartialDelimitedFrom(
            input: InputStream,
            extensionRegistry: ExtensionRegistryLite?
        ): R = mapper(delegate.parsePartialDelimitedFrom(input, extensionRegistry))
    }
}
