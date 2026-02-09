package com.example

import com.example.proto.*
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EnumGenerationTest {

    @Test
    fun `test nested enum conversion - PersonKt GenderKt`() {
        // Kotlin to Java
        PersonKt.GenderKt.MALE.toJavaProto() shouldBe Person.Gender.MALE
        PersonKt.GenderKt.FEMALE.toJavaProto() shouldBe Person.Gender.FEMALE
        PersonKt.GenderKt.UNKNOWN.toJavaProto() shouldBe Person.Gender.UNKNOWN
        PersonKt.GenderKt.UNRECOGNIZED.toJavaProto() shouldBe Person.Gender.UNRECOGNIZED

        // Java to Kotlin
        Person.Gender.MALE.toKotlinProto() shouldBe PersonKt.GenderKt.MALE
        Person.Gender.FEMALE.toKotlinProto() shouldBe PersonKt.GenderKt.FEMALE
        Person.Gender.UNKNOWN.toKotlinProto() shouldBe PersonKt.GenderKt.UNKNOWN
        Person.Gender.UNRECOGNIZED.toKotlinProto() shouldBe PersonKt.GenderKt.UNRECOGNIZED
    }

    @Test
    fun `test nested enum conversion - ContactInfoKt ContactPreferenceKt`() {
        // Kotlin to Java
        ContactInfoKt.ContactPreferenceKt.EMAIL_ONLY.toJavaProto() shouldBe ContactInfo.ContactPreference.EMAIL_ONLY
        ContactInfoKt.ContactPreferenceKt.PHONE_ONLY.toJavaProto() shouldBe ContactInfo.ContactPreference.PHONE_ONLY
        ContactInfoKt.ContactPreferenceKt.ANY_METHOD.toJavaProto() shouldBe ContactInfo.ContactPreference.ANY_METHOD
        ContactInfoKt.ContactPreferenceKt.UNKNOWN_PREFERENCE.toJavaProto() shouldBe ContactInfo.ContactPreference.UNKNOWN_PREFERENCE

        // Java to Kotlin
        ContactInfo.ContactPreference.EMAIL_ONLY.toKotlinProto() shouldBe ContactInfoKt.ContactPreferenceKt.EMAIL_ONLY
        ContactInfo.ContactPreference.PHONE_ONLY.toKotlinProto() shouldBe ContactInfoKt.ContactPreferenceKt.PHONE_ONLY
        ContactInfo.ContactPreference.ANY_METHOD.toKotlinProto() shouldBe ContactInfoKt.ContactPreferenceKt.ANY_METHOD
        ContactInfo.ContactPreference.UNKNOWN_PREFERENCE.toKotlinProto() shouldBe ContactInfoKt.ContactPreferenceKt.UNKNOWN_PREFERENCE
    }

    @Test
    fun `test UNRECOGNIZED value handling`() {
        // In gRPC/Proto3, if a Java client receives a value it doesn't know, it maps it to UNRECOGNIZED.
        // Our toKotlinProto should also handle this gracefully.

        Person.Gender.UNRECOGNIZED.toKotlinProto() shouldBe PersonKt.GenderKt.UNRECOGNIZED
        PersonKt.GenderKt.UNRECOGNIZED.toJavaProto() shouldBe Person.Gender.UNRECOGNIZED

        // Test "else" branch in toKotlinProto
        // In Java Protobuf, forNumber() returns null for unknown values in some cases,
        // but getting the field from a message returns UNRECOGNIZED for unknown values.
        val unknownJava = Person.newBuilder().setGenderValue(-1).build().gender
        unknownJava shouldBe Person.Gender.UNRECOGNIZED
        unknownJava.toKotlinProto() shouldBe PersonKt.GenderKt.UNRECOGNIZED
    }

    @Test
    fun `test message with enum field serialization`() {
        val original = PersonKt(
            name = "Enum Tester",
            gender = PersonKt.GenderKt.FEMALE
        )

        val javaProto = original.toJavaProto()
        javaProto.gender shouldBe Person.Gender.FEMALE

        val deserialized = javaProto.toKotlinProto()
        deserialized.gender shouldBe PersonKt.GenderKt.FEMALE
        deserialized shouldBe original
    }
}
