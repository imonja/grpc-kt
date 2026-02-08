package com.example

import com.example.proto.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumGenerationTest {

    @Test
    fun `test nested enum conversion - PersonKt GenderKt`() {
        // Kotlin to Java
        assertEquals(Person.Gender.MALE, PersonKt.GenderKt.MALE.toJavaProto())
        assertEquals(Person.Gender.FEMALE, PersonKt.GenderKt.FEMALE.toJavaProto())
        assertEquals(Person.Gender.UNKNOWN, PersonKt.GenderKt.UNKNOWN.toJavaProto())
        assertEquals(Person.Gender.UNRECOGNIZED, PersonKt.GenderKt.UNRECOGNIZED.toJavaProto())

        // Java to Kotlin
        assertEquals(PersonKt.GenderKt.MALE, Person.Gender.MALE.toKotlinProto())
        assertEquals(PersonKt.GenderKt.FEMALE, Person.Gender.FEMALE.toKotlinProto())
        assertEquals(PersonKt.GenderKt.UNKNOWN, Person.Gender.UNKNOWN.toKotlinProto())
        assertEquals(PersonKt.GenderKt.UNRECOGNIZED, Person.Gender.UNRECOGNIZED.toKotlinProto())
    }

    @Test
    fun `test nested enum conversion - ContactInfoKt ContactPreferenceKt`() {
        // Kotlin to Java
        assertEquals(ContactInfo.ContactPreference.EMAIL_ONLY, ContactInfoKt.ContactPreferenceKt.EMAIL_ONLY.toJavaProto())
        assertEquals(ContactInfo.ContactPreference.PHONE_ONLY, ContactInfoKt.ContactPreferenceKt.PHONE_ONLY.toJavaProto())
        assertEquals(ContactInfo.ContactPreference.ANY_METHOD, ContactInfoKt.ContactPreferenceKt.ANY_METHOD.toJavaProto())
        assertEquals(
            ContactInfo.ContactPreference.UNKNOWN_PREFERENCE,
            ContactInfoKt.ContactPreferenceKt.UNKNOWN_PREFERENCE.toJavaProto()
        )

        // Java to Kotlin
        assertEquals(ContactInfoKt.ContactPreferenceKt.EMAIL_ONLY, ContactInfo.ContactPreference.EMAIL_ONLY.toKotlinProto())
        assertEquals(ContactInfoKt.ContactPreferenceKt.PHONE_ONLY, ContactInfo.ContactPreference.PHONE_ONLY.toKotlinProto())
        assertEquals(ContactInfoKt.ContactPreferenceKt.ANY_METHOD, ContactInfo.ContactPreference.ANY_METHOD.toKotlinProto())
        assertEquals(
            ContactInfoKt.ContactPreferenceKt.UNKNOWN_PREFERENCE,
            ContactInfo.ContactPreference.UNKNOWN_PREFERENCE.toKotlinProto()
        )
    }

    @Test
    fun `test UNRECOGNIZED value handling`() {
        // In gRPC/Proto3, if a Java client receives a value it doesn't know, it maps it to UNRECOGNIZED.
        // Our toKotlinProto should also handle this gracefully.

        assertEquals(PersonKt.GenderKt.UNRECOGNIZED, Person.Gender.UNRECOGNIZED.toKotlinProto())
        assertEquals(Person.Gender.UNRECOGNIZED, PersonKt.GenderKt.UNRECOGNIZED.toJavaProto())

        // Test "else" branch in toKotlinProto
        // Person.Gender.forNumber(-1) should return UNRECOGNIZED in Java
        assertEquals(PersonKt.GenderKt.UNRECOGNIZED, Person.Gender.forNumber(-1).toKotlinProto())
    }

    @Test
    fun `test message with enum field serialization`() {
        val original = PersonKt(
            name = "Enum Tester",
            gender = PersonKt.GenderKt.FEMALE
        )

        val javaProto = original.toJavaProto()
        assertEquals(Person.Gender.FEMALE, javaProto.gender)

        val deserialized = javaProto.toKotlinProto()
        assertEquals(PersonKt.GenderKt.FEMALE, deserialized.gender)
        assertEquals(original, deserialized)
    }
}
