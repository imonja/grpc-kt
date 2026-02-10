package com.example

import com.example.proto.*
import com.example.proto.PersonKt
import com.google.protobuf.Message
import com.google.protobuf.Parser
import org.junit.jupiter.api.Test

class CompatibilityTest {
    fun <V : Message> testParser(parser: Parser<V>) {
        println("Parser is valid for Message")
    }

    @Test
    fun testCompatibility() {
        // This should now compile and pass
        testParser(PersonKt.javaParser())
    }

    @Test
    fun testKotlinParser() {
        val parser = PersonKt.kotlinParser()
        val person = PersonKt(name = "John")
        val bytes = person.toByteArray()
        val parsed = parser.parseFrom(bytes)
        assert(parsed.name == "John")
    }
}
