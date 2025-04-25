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
import com.google.protobuf.util.Timestamps.fromDate
import com.google.protobuf.util.Timestamps.toMillis
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*

/**
 * Converts a [Timestamp] to a [LocalDateTime] using time zone with default offset.
 */
fun Timestamp.toLocalDateTime(zone: ZoneId = ZoneOffset.UTC): LocalDateTime =
    LocalDateTime.ofInstant(this.toInstant(), zone)

/**
 * Converts a [LocalDateTime] to a [Timestamp].
 */
fun LocalDateTime.toProtoTimestamp(zone: ZoneId = ZoneOffset.UTC): Timestamp =
    this.atZone(zone).toInstant().toProtoTimestamp()

/**
 * Converts a [Timestamp] to an [Instant].
 */
fun Timestamp.toInstant(): Instant =
    Instant.ofEpochSecond(this.seconds, this.nanos.toLong())

/**
 * Converts an [Instant] to a [Timestamp].
 */
fun Instant.toProtoTimestamp(): Timestamp = Timestamp.newBuilder()
    .setSeconds(epochSecond)
    .setNanos(nano)
    .build()

/**
 * Converts a Protobuf [Timestamp] to a [Date].
 */
fun Timestamp.toDate(): Date = Date(toMillis(this))

/**
 * Converts a [Date] to a Protobuf [Timestamp].
 */
fun Date.toProtoTimestamp(): Timestamp = fromDate(this)

/**
 * Converts a Protobuf [Duration] to a [Duration].
 */
fun com.google.protobuf.Duration.toDuration(): Duration =
    Duration.ofSeconds(seconds, nanos.toLong())

/**
 * Converts a [Duration] to a Protobuf [Duration].
 */
fun Duration.toProtoDuration(): com.google.protobuf.Duration =
    com.google.protobuf.Duration.newBuilder()
        .setSeconds(seconds)
        .setNanos(nano)
        .build()

/**
 * Converts a [Double] to a Protobuf [DoubleValue].
 */
fun Double.toDoubleValue(): DoubleValue = DoubleValue.newBuilder().setValue(this).build()

/**
 * Converts a [Float] to a Protobuf [FloatValue].
 */
fun Float.toFloatValue(): FloatValue = FloatValue.newBuilder().setValue(this).build()

/**
 * Converts a [Long] to a Protobuf [Int64Value].
 */
fun Long.toInt64Value(): Int64Value = Int64Value.newBuilder().setValue(this).build()

/**
 * Converts a [Long] to a Protobuf [UInt64Value].
 */
fun Long.toUInt64Value(): UInt64Value = UInt64Value.newBuilder().setValue(this).build()

/**
 * Converts an [Int] to a Protobuf [Int32Value].
 */
fun Int.toInt32Value(): Int32Value = Int32Value.newBuilder().setValue(this).build()

/**
 * Converts an [Int] to a Protobuf [UInt32Value].
 */
fun Int.toUInt32Value(): UInt32Value = UInt32Value.newBuilder().setValue(this).build()

/**
 * Converts a [Boolean] to a Protobuf [BoolValue].
 */
fun Boolean.toBoolValue(): BoolValue = BoolValue.newBuilder().setValue(this).build()

/**
 * Converts a [String] to a Protobuf [StringValue].
 */
fun String.toStringValue(): StringValue = StringValue.newBuilder().setValue(this).build()

/**
 * Converts a [ByteString] to a Protobuf [BytesValue].
 */
fun ByteString.toBytesValue(): BytesValue = BytesValue.newBuilder().setValue(this).build()
