package io.github.imonja.grpc.kt.common

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.BytesValue
import com.google.protobuf.DoubleValue
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.google.protobuf.StringValue
import com.google.protobuf.Timestamp
import com.google.protobuf.UInt32Value
import com.google.protobuf.UInt64Value
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class ExtensionsTests {

    @Test
    fun `test converting Timestamp to LocalDateTime`() {
        val timestamp = Timestamp.newBuilder().setSeconds(1633046400L).build()
        val expectedDateTime = LocalDateTime.of(2021, 10, 1, 0, 0)
        timestamp.toLocalDateTime() shouldBe expectedDateTime
    }

    @Test
    fun `test converting Timestamp to LocalDateTime with custom zone`() {
        val timestamp = Timestamp.newBuilder().setSeconds(1633046400L).build()
        val zone = ZoneId.of("America/New_York")
        val expectedDateTime = LocalDateTime.of(2021, 9, 30, 20, 0)
        timestamp.toLocalDateTime(zone) shouldBe expectedDateTime
    }

    @Test
    fun `test converting LocalDateTime to Timestamp`() {
        val dateTime = LocalDateTime.of(2021, 10, 1, 0, 0)
        val expectedTimestamp = Timestamp.newBuilder().setSeconds(1633046400L).build()
        dateTime.toProtoTimestamp() shouldBe expectedTimestamp
    }

    @Test
    fun `test converting LocalDateTime to Timestamp with custom zone`() {
        val dateTime = LocalDateTime.of(2021, 10, 1, 0, 0)
        val zone = ZoneId.of("America/New_York")
        val expectedTimestamp = Timestamp.newBuilder().setSeconds(1633060800L).build() // 4 hours later in UTC
        dateTime.toProtoTimestamp(zone) shouldBe expectedTimestamp
    }

    @Test
    fun `test converting Instant to Timestamp`() {
        val instant = Instant.ofEpochSecond(1633036800L)
        val expectedTimestamp = Timestamp.newBuilder().setSeconds(1633036800L).build()
        instant.toProtoTimestamp() shouldBe expectedTimestamp
    }

    @Test
    fun `test converting Timestamp to Instant`() {
        val timestamp = Timestamp.newBuilder().setSeconds(1633036800L).build()
        val expectedInstant = Instant.ofEpochSecond(1633036800L)
        timestamp.toInstant() shouldBe expectedInstant
    }

    @Test
    fun `test converting Protobuf Duration to Duration`() {
        val protoDuration = com.google.protobuf.Duration.newBuilder()
            .setSeconds(3600)
            .setNanos(1_000_000)
            .build()
        val expectedDuration = Duration.ofSeconds(3600, 1_000_000)
        protoDuration.toDuration() shouldBe expectedDuration
    }

    @Test
    fun `test converting Duration to Protobuf Duration`() {
        val duration = Duration.ofSeconds(3600, 1_000_000)
        val expectedProtoDuration = com.google.protobuf.Duration.newBuilder()
            .setSeconds(3600)
            .setNanos(1_000_000)
            .build()
        duration.toProtoDuration() shouldBe expectedProtoDuration
    }

    @Test
    fun `test Double to DoubleValue`() {
        val value = 1.23
        val expected = DoubleValue.newBuilder().setValue(1.23).build()
        value.toDoubleValue() shouldBe expected
    }

    @Test
    fun `test Float to FloatValue`() {
        val value = 1.23f
        val expected = FloatValue.newBuilder().setValue(1.23f).build()
        value.toFloatValue() shouldBe expected
    }

    @Test
    fun `test Long to Int64Value`() {
        val value = 123456789L
        val expected = Int64Value.newBuilder().setValue(123456789L).build()
        value.toInt64Value() shouldBe expected
    }

    @Test
    fun `test Long to UInt64Value`() {
        val value = 123456789L
        val expected = UInt64Value.newBuilder().setValue(123456789L).build()
        value.toUInt64Value() shouldBe expected
    }

    @Test
    fun `test Int to Int32Value`() {
        val value = 123456789
        val expected = Int32Value.newBuilder().setValue(123456789).build()
        value.toInt32Value() shouldBe expected
    }

    @Test
    fun `test Int to UInt32Value`() {
        val value = 123456789
        val expected = UInt32Value.newBuilder().setValue(123456789).build()
        value.toUInt32Value() shouldBe expected
    }

    @Test
    fun `test Boolean to BoolValue`() {
        val value = true
        val expected = BoolValue.newBuilder().setValue(true).build()
        value.toBoolValue() shouldBe expected
    }

    @Test
    fun `test String to StringValue`() {
        val value = "Test String"
        val expected = StringValue.newBuilder().setValue("Test String").build()
        value.toStringValue() shouldBe expected
    }

    @Test
    fun `test ByteString to BytesValue`() {
        val value = ByteString.copyFromUtf8("Test ByteString")
        val expected = BytesValue.newBuilder().setValue(value).build()
        value.toBytesValue() shouldBe expected
    }

    @Test
    fun `test Date to ProtoTimestamp conversion`() {
        val date = Date.from(Instant.parse("2023-10-15T10:15:30.00Z"))

        val timestamp = date.toProtoTimestamp()

        timestamp.seconds shouldBe date.time / 1000
        timestamp.nanos shouldBe (date.time % 1000).toInt() * 1_000_000
    }

    @Test
    fun `test ProtoTimestamp to Date conversion`() {
        val seconds = 1683647730L
        val nanos = 500_000_000
        val timestamp = Timestamp.newBuilder()
            .setSeconds(seconds)
            .setNanos(nanos)
            .build()

        val date = timestamp.toDate()

        date.time shouldBe seconds * 1000 + nanos / 1_000_000
    }

    @Test
    fun `test Date to ProtoTimestamp and back to Date`() {
        val originalDate = Date()

        val timestamp = originalDate.toProtoTimestamp()
        val convertedDate = timestamp.toDate()

        convertedDate shouldBe originalDate
    }

    @Test
    fun `test ProtoTimestamp to Date and back to ProtoTimestamp`() {
        val originalTimestamp = Timestamp.newBuilder()
            .setSeconds(1683647730L)
            .setNanos(500_000_000)
            .build()

        val date = originalTimestamp.toDate()
        val convertedTimestamp = date.toProtoTimestamp()

        convertedTimestamp shouldBe originalTimestamp
    }
}
