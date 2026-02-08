package com.example

import com.example.proto.*
import com.google.protobuf.util.JsonFormat
import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * This test class demonstrates how to use the generated Protocol Buffer code.
 */
class ProtoExampleTest {

    private val serverPort = (60_000..65_000).random()
    private var server: io.grpc.Server? = null
    private var channel: io.grpc.ManagedChannel? = null

    @BeforeEach
    fun setup() {
        // Start the gRPC server
        server = ServerBuilder.forPort(serverPort)
            .addService(PersonServiceImpl())
            .build()
            .start()

        // Create a channel to the server
        channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
            .usePlaintext()
            .build()
    }

    @AfterEach
    fun teardown() {
        channel?.shutdown()?.awaitTermination(5, TimeUnit.SECONDS)
        server?.shutdown()?.awaitTermination(5, TimeUnit.SECONDS)
    }

    @Test
    fun `test creating and serializing Person message`() {
        // Create a Person using the Kotlin data class
        val person = PersonKt(
            name = "John Doe",
            age = 30,
            hobbies = listOf("Reading", "Hiking", "Coding"),
            gender = PersonKt.GenderKt.MALE,
            address = PersonKt.AddressKt(
                street = "123 Main St",
                city = "San Francisco",
                country = "USA"
            )
        )

        // Serialize to binary format
        val bytes = person.toByteArray()
        println("Serialized size: ${bytes.size} bytes")

        // Deserialize from binary format
        val deserializedPerson = PersonKt.parseFrom(bytes)

        // Verify the deserialized object matches the original
        assert(person == deserializedPerson) { "Deserialized person should match the original" }

        // Test ByteString serialization/deserialization
        val byteString = person.toByteString()
        val deserializedFromByteString = PersonKt.parseFrom(byteString)
        assert(person == deserializedFromByteString) { "ByteString serialization should work" }

        // Test OutputStream/InputStream serialization/deserialization
        val outputStream = java.io.ByteArrayOutputStream()
        person.writeTo(outputStream)
        val inputStream = java.io.ByteArrayInputStream(outputStream.toByteArray())
        val deserializedFromStream = PersonKt.parseFrom(inputStream)
        assert(person == deserializedFromStream) { "Stream serialization should work" }

        // Convert to JSON (still requires Java proto for now as we use JsonFormat)
        val javaProto = person.toJavaProto()
        val jsonPrinter = JsonFormat.printer().includingDefaultValueFields()
        val json = jsonPrinter.print(javaProto)
        println("JSON representation:\n$json")

        // Parse from JSON
        val jsonParser = JsonFormat.parser()
        val builder = Person.newBuilder()
        jsonParser.merge(json, builder)
        val fromJson = builder.build().toKotlinProto()

        // Verify the parsed object matches the original
        assert(person == fromJson) { "Person parsed from JSON should match the original" }
    }

    @Test
    fun `test creating complex Person message`() {
        val person = PersonKt(
            name = "Jane Smith",
            age = 25,
            hobbies = listOf("Art", "Music", "Photography", "Travel"),
            gender = PersonKt.GenderKt.FEMALE,
            address = PersonKt.AddressKt(
                street = "Park Avenue 45",
                city = "New York",
                country = "USA"
            )
        )

        val bytes = person.toByteArray()
        val deserialized = PersonKt.parseFrom(bytes)

        assert(person == deserialized) { "Complex person should match after serialization" }
        assert(deserialized.hobbies.size == 4) { "Should have 4 hobbies" }
        assert(deserialized.gender == PersonKt.GenderKt.FEMALE) { "Should be female" }
    }

    @Test
    fun `test ContactInfo phone oneof serialization`() {
        val contactInfo = ContactInfoKt(
            name = "Bob",
            contactMethod = ContactInfoKt.ContactMethod.Phone(phone = "+123456789"),
            preference = ContactInfoKt.ContactPreferenceKt.PHONE_ONLY
        )

        val javaProto = contactInfo.toJavaProto()
        assert(javaProto.hasPhone()) { "Java proto should have phone set" }
        assert(javaProto.phone == "+123456789") { "Phone number should match" }

        val deserialized = javaProto.toKotlinProto()
        assert(contactInfo == deserialized) { "Contact info should match after serialization" }
        assert(deserialized.contactMethod is ContactInfoKt.ContactMethod.Phone) { "Contact method should be Phone" }
        assert(deserialized.preference == ContactInfoKt.ContactPreferenceKt.PHONE_ONLY) { "Preference should be phone only" }
    }

    @Test
    fun `test gRPC unary call`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a request
        val request = GetPersonRequestKt(id = "123")

        // Make the call
        val response = stub.getPerson(request)

        // Verify the response
        assert(response.person?.name == "John Doe") { "Expected name to be 'John Doe'" }
        assert(response.person?.age == 30) { "Expected age to be 30" }
    }

    @Test
    fun `test gRPC delete person`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a request
        val request = DeletePersonRequestKt(id = "123")

        // Make the call
        stub.deletePerson(request)

        // No response to verify since it returns Unit, but we can assert that no exception was thrown
        // If we reach this point, the test passes
    }

    @Test
    fun `test gRPC server streaming`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a request
        val request = ListPersonsRequestKt(limit = 5, offset = 0)

        // Make the call and collect responses
        val responses = mutableListOf<PersonKt?>()
        stub.listPersons(request).collect { response ->
            responses.add(response.person)
            println("Received person: ${response.person?.name}, gender: ${response.person?.gender}")
        }

        // Verify we received the expected number of responses and their data
        assert(responses.size == 3) { "Expected to receive 3 persons" }
        assert(responses[0]?.gender == PersonKt.GenderKt.FEMALE) { "First person should be female" }
        assert(responses[1]?.gender == PersonKt.GenderKt.MALE) { "Second person should be male" }
        assert(responses[2]?.gender == PersonKt.GenderKt.NON_BINARY) { "Third person should be non-binary" }
    }

    @Test
    fun `test gRPC client streaming`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a flow of requests
        val requests = flow {
            for (i in 1..3) {
                val person = PersonKt(
                    name = "Person $i",
                    age = 20 + i,
                    hobbies = listOf("Hobby $i"),
                    gender = PersonKt.GenderKt.UNKNOWN,
                    address = PersonKt.AddressKt(
                        street = "$i Main St",
                        city = "City $i",
                        country = "Country $i"
                    )
                )
                emit(UpdatePersonRequestKt(person = person))
                delay(100) // Simulate some processing time
            }
        }

        // Make the call
        val response = stub.updatePerson(requests)

        // Verify the response
        assert(response.success) { "Expected update to be successful" }
    }

    @Test
    fun `test gRPC bidirectional streaming`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a flow of chat requests
        val requests = flow {
            for (i in 1..5) {
                emit(ChatRequestKt(message = "Hello $i from client"))
                delay(100) // Simulate some processing time
            }
        }

        // Launch a coroutine to collect responses
        val responses = mutableListOf<String>()
        val job = launch {
            stub.chatWithPerson(requests).collect { response ->
                responses.add(response.message)
                println("Received: ${response.message}")
            }
        }

        // Wait for the chat to complete
        job.join()

        // Verify we received the expected number of responses
        assert(responses.size == 5) { "Expected to receive 5 chat responses" }
    }

    @Test
    fun `test ContactInfo string oneof serialization`() {
        // Test ContactInfo with email
        val contactInfoEmail = ContactInfoKt(
            name = "Alice Smith",
            contactMethod = ContactInfoKt.ContactMethod.Email(email = "alice@example.com"),
            tags = listOf("customer", "vip"),
            preference = ContactInfoKt.ContactPreferenceKt.EMAIL_ONLY
        )

        // Serialize and deserialize
        val emailBytes = contactInfoEmail.toByteArray()
        val deserializedEmail = ContactInfoKt.parseFrom(emailBytes)
        assert(contactInfoEmail == deserializedEmail) { "Email contact info serialization should work" }

        // Test ContactInfo with phone
        val contactInfoPhone = ContactInfoKt(
            name = "Bob Johnson",
            contactMethod = ContactInfoKt.ContactMethod.Phone(phone = "+1-555-0123"),
            tags = listOf("lead"),
            preference = ContactInfoKt.ContactPreferenceKt.PHONE_ONLY
        )

        val phoneBytes = contactInfoPhone.toByteArray()
        val deserializedPhone = ContactInfoKt.parseFrom(phoneBytes)
        assert(contactInfoPhone == deserializedPhone) { "Phone contact info serialization should work" }

        // Test ContactInfo with username
        val contactInfoUsername = ContactInfoKt(
            name = "Charlie Brown",
            contactMethod = ContactInfoKt.ContactMethod.Username(username = "@charlie_b"),
            tags = listOf("partner"),
            preference = ContactInfoKt.ContactPreferenceKt.ANY_METHOD
        )

        val usernameJavaProto = contactInfoUsername.toJavaProto()
        val usernameBytes = usernameJavaProto.toByteArray()
        val deserializedUsername = ContactInfo.parseFrom(usernameBytes).toKotlinProto()
        assert(contactInfoUsername == deserializedUsername) { "Username contact info serialization should work" }

        // Test ContactInfo with null oneof
        val contactInfoNull = ContactInfoKt(
            name = "David None",
            contactMethod = null,
            tags = listOf("test"),
            preference = ContactInfoKt.ContactPreferenceKt.UNKNOWN_PREFERENCE
        )

        val nullJavaProto = contactInfoNull.toJavaProto()
        val nullBytes = nullJavaProto.toByteArray()
        val deserializedNull = ContactInfo.parseFrom(nullBytes).toKotlinProto()
        assert(contactInfoNull == deserializedNull) { "Null contact info serialization should work" }
    }

    @Test
    fun `test NotificationSettings message oneof serialization`() {
        // Test NotificationSettings with email settings
        val emailSettings = NotificationSettingsKt.EmailSettingsKt(
            emailAddress = "user@example.com",
            dailyDigest = true,
            categories = listOf("orders", "promotions", "news")
        )
        val notificationSettingsEmail = NotificationSettingsKt(
            userId = "user123",
            notificationChannel = NotificationSettingsKt.NotificationChannel.EmailSettings(
                emailSettings = emailSettings
            ),
            notificationsEnabled = true
        )

        val emailNotificationBytes = notificationSettingsEmail.toByteArray()
        val deserializedEmailNotification = NotificationSettingsKt.parseFrom(emailNotificationBytes)
        assert(notificationSettingsEmail == deserializedEmailNotification) { "Email notification settings serialization should work" }

        // Test NotificationSettings with SMS settings
        val smsSettings = NotificationSettingsKt.SmsSettingsKt(
            phoneNumber = "+1-555-0456",
            urgentOnly = true
        )
        val notificationSettingsSms = NotificationSettingsKt(
            userId = "user456",
            notificationChannel = NotificationSettingsKt.NotificationChannel.SmsSettings(
                smsSettings = smsSettings
            ),
            notificationsEnabled = true
        )

        val smsNotificationJavaProto = notificationSettingsSms.toJavaProto()
        val smsNotificationBytes = smsNotificationJavaProto.toByteArray()
        val deserializedSmsNotification = NotificationSettings.parseFrom(smsNotificationBytes).toKotlinProto()
        assert(notificationSettingsSms == deserializedSmsNotification) { "SMS notification settings serialization should work" }

        // Test NotificationSettings with push settings
        val pushSettings = NotificationSettingsKt.PushSettingsKt(
            deviceToken = "abc123def456",
            soundEnabled = true,
            soundName = "notification_sound.wav"
        )
        val notificationSettingsPush = NotificationSettingsKt(
            userId = "user789",
            notificationChannel = NotificationSettingsKt.NotificationChannel.PushSettings(
                pushSettings = pushSettings
            ),
            notificationsEnabled = false
        )

        val pushNotificationJavaProto = notificationSettingsPush.toJavaProto()
        val pushNotificationBytes = pushNotificationJavaProto.toByteArray()
        val deserializedPushNotification = NotificationSettings.parseFrom(pushNotificationBytes).toKotlinProto()
        assert(notificationSettingsPush == deserializedPushNotification) { "Push notification settings serialization should work" }

        // Test NotificationSettings with null oneof
        val notificationSettingsNull = NotificationSettingsKt(
            userId = "user000",
            notificationChannel = null,
            notificationsEnabled = false
        )

        val nullNotificationJavaProto = notificationSettingsNull.toJavaProto()
        val nullNotificationBytes = nullNotificationJavaProto.toByteArray()
        val deserializedNullNotification = NotificationSettings.parseFrom(nullNotificationBytes).toKotlinProto()
        assert(notificationSettingsNull == deserializedNullNotification) { "Null notification settings serialization should work" }
    }

    @Test
    fun `test updateContactInfo gRPC call`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Test with EMAIL preference
        val contactInfoEmail = ContactInfoKt(
            name = "Test User Email",
            contactMethod = ContactInfoKt.ContactMethod.Email(email = "test@example.com"),
            tags = listOf("test", "email"),
            preference = ContactInfoKt.ContactPreferenceKt.EMAIL_ONLY
        )
        val requestEmail = UpdateContactInfoRequestKt(personId = "test_email", contactInfo = contactInfoEmail)
        val responseEmail = stub.updateContactInfo(requestEmail)
        assert(responseEmail.success)

        // Test with PHONE preference
        val contactInfoPhone = ContactInfoKt(
            name = "Test User Phone",
            contactMethod = ContactInfoKt.ContactMethod.Phone(phone = "123-456"),
            tags = listOf("test", "phone"),
            preference = ContactInfoKt.ContactPreferenceKt.PHONE_ONLY
        )
        val requestPhone = UpdateContactInfoRequestKt(personId = "test_phone", contactInfo = contactInfoPhone)
        val responsePhone = stub.updateContactInfo(requestPhone)
        assert(responsePhone.success)
    }

    @Test
    fun `test updateNotificationSettings gRPC call`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create notification settings with push
        val pushSettings = NotificationSettingsKt.PushSettingsKt(
            deviceToken = "test_device_token",
            soundEnabled = false,
            soundName = "silent"
        )
        val notificationSettings = NotificationSettingsKt(
            userId = "test456",
            notificationChannel = NotificationSettingsKt.NotificationChannel.PushSettings(
                pushSettings = pushSettings
            ),
            notificationsEnabled = true
        )

        val request = UpdateNotificationSettingsRequestKt(
            userId = "test456",
            settings = notificationSettings
        )

        val response = stub.updateNotificationSettings(request)
        assert(response.success) { "Expected notification settings update to be successful" }
        assert(response.message == "Test notification settings updated successfully") { "Expected specific response message" }
    }

    @Test
    fun `test Kotlin keyword field serialization`() {
        // Create a schedule request (no longer has 'when' field)
        val scheduleRequest = GetScheduleRequestKt(
            personId = "user123"
        )

        // Test serialization/deserialization
        val requestJavaProto = scheduleRequest.toJavaProto()
        val requestBytes = requestJavaProto.toByteArray()
        val deserializedRequest = GetScheduleRequest.parseFrom(requestBytes).toKotlinProto()
        assert(scheduleRequest == deserializedRequest) { "Schedule request should serialize correctly" }

        // Create a schedule item (now nested in response) and response with timestamp
        val scheduleItem = GetScheduleResponseKt.ScheduleItemKt(
            id = "item1",
            title = "Team Meeting",
            description = "Weekly team sync"
        )

        val scheduleResponse = GetScheduleResponseKt(
            items = listOf(scheduleItem),
            `when` = LocalDateTime.now() // Using backticks for Kotlin keyword
        )

        // Test response serialization/deserialization
        val responseJavaProto = scheduleResponse.toJavaProto()
        val responseBytes = responseJavaProto.toByteArray()
        val deserializedResponse = GetScheduleResponse.parseFrom(responseBytes).toKotlinProto()
        assert(scheduleResponse == deserializedResponse) { "Schedule response with Kotlin keyword should serialize correctly" }

        // Test field access with backticks
        assert(scheduleRequest.personId == "user123") { "Should be able to access personId field" }
        assert(scheduleResponse.`when` != null) { "Should be able to access response 'when' timestamp field with backticks" }
    }

    @Test
    fun `test Kotlin keyword field check functions`() {
        // Test personId field access
        val scheduleRequest = GetScheduleRequestKt(
            personId = "user123"
        )
        assert(scheduleRequest.personId == "user123") { "Should be able to access personId field value" }

        val scheduleRequestEmpty = GetScheduleRequestKt(
            personId = ""
        )
        assert(scheduleRequestEmpty.personId == "") { "Should be able to access empty personId field value" }

        // Test hasWhen() function for response with Kotlin keyword field
        val scheduleResponseWithWhen = GetScheduleResponseKt(
            items = listOf(),
            `when` = LocalDateTime.now() // Using backticks for Kotlin keyword
        )
        assert(scheduleResponseWithWhen.hasWhen()) { "Should have 'when' timestamp field set" }

        val scheduleResponseWithoutWhen = GetScheduleResponseKt(
            items = listOf(),
            `when` = null
        )
        assert(!scheduleResponseWithoutWhen.hasWhen()) { "Should not have 'when' timestamp field set" }
    }

    @Test
    fun `test getSchedule gRPC call with Kotlin keywords`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create request (no longer has 'when' field)
        val request = GetScheduleRequestKt(
            personId = "test789"
        )

        val response = stub.getSchedule(request)

        // Verify response has items and timestamp (with Kotlin keyword field)
        assert(response.items.isNotEmpty()) { "Expected schedule items in response" }
        assert(response.`when` != null) { "Expected timestamp in response using Kotlin keyword 'when'" }

        // Verify item properties (now nested ScheduleItem type)
        val firstItem = response.items.first()
        assert(firstItem.id.isNotEmpty()) { "Expected schedule item to have ID" }
        assert(firstItem.title.isNotEmpty()) { "Expected schedule item to have title" }
        assert(firstItem.description.isNotEmpty()) { "Expected schedule item to have description" }
    }

    @Test
    fun `test TestOptionalField - field not set`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Test scenario 1: Field NOT set
        println("Testing TestOptionalField - field not set")
        val request = TestOptionalFieldRequestKt()
        val response = stub.testOptionalField(request)

        // Should return false for hasField when field is not set
        assert(!response.hasField) { "Expected hasField to be false when field is not set" }
        println("✓ Field not set test passed: hasField = ${response.hasField}")
    }

    @Test
    fun `test TestOptionalField - field set to empty string`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Test scenario 2: Field SET to empty string
        println("Testing TestOptionalField - field set to empty string")
        val request = TestOptionalFieldRequestKt(field = "")
        val response = stub.testOptionalField(request)

        // Should return true for hasField when field is set to empty string
        assert(response.hasField) { "Expected hasField to be true when field is set to empty string" }
        println("✓ Field set to empty string test passed: hasField = ${response.hasField}")
    }

    @Test
    fun `test TestOptionalField - field set to John`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Test scenario 3: Field SET to "John"
        println("Testing TestOptionalField - field set to 'John'")
        val request = TestOptionalFieldRequestKt(field = "John")
        val response = stub.testOptionalField(request)

        // Should return true for hasField when field is set to "John"
        assert(response.hasField) { "Expected hasField to be true when field is set to 'John'" }
        println("✓ Field set to 'John' test passed: hasField = ${response.hasField}")
    }

    @Test
    fun `test TestOptionalField - comprehensive scenarios`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        println("\n=== Comprehensive TestOptionalField Tests ===")

        // Test various scenarios
        val testCases = listOf(
            Triple("Field not set", TestOptionalFieldRequestKt(), false),
            Triple("Field set to empty string", TestOptionalFieldRequestKt(field = ""), true),
            Triple("Field set to 'John'", TestOptionalFieldRequestKt(field = "John"), true),
            Triple("Field set to whitespace", TestOptionalFieldRequestKt(field = " "), true),
            Triple("Field set to null string", TestOptionalFieldRequestKt(field = "null"), true),
            Triple("Field set to long string", TestOptionalFieldRequestKt(field = "This is a very long string to test"), true)
        )

        testCases.forEach { (description, request, expectedHasField) ->
            println("\nTesting: $description")
            val response = stub.testOptionalField(request)

            assert(response.hasField == expectedHasField) { "Expected hasField to be $expectedHasField for case: $description" }

            val fieldValue = if (request.hasField()) request.field else "<not set>"
            println("✓ $description: hasField = ${response.hasField}, field value = '$fieldValue'")
        }

        println("\n=== All comprehensive tests passed! ===")
    }

    @Test
    fun `test TestOptionalField serialization`() {
        // Test serialization/deserialization of TestOptionalFieldRequest with optional field

        // Test case 1: Field not set
        val requestNoField = TestOptionalFieldRequestKt()
        val javaProtoNoField = requestNoField.toJavaProto()
        val bytesNoField = javaProtoNoField.toByteArray()
        val deserializedNoField = TestOptionalFieldRequest.parseFrom(bytesNoField).toKotlinProto()

        assert(requestNoField == deserializedNoField) { "Request with no field should serialize correctly" }
        assert(!deserializedNoField.hasField()) { "Deserialized request should not have field set" }

        // Test case 2: Field set to empty string
        val requestEmptyField = TestOptionalFieldRequestKt(field = "")
        val javaProtoEmptyField = requestEmptyField.toJavaProto()
        val bytesEmptyField = javaProtoEmptyField.toByteArray()
        val deserializedEmptyField = TestOptionalFieldRequest.parseFrom(bytesEmptyField).toKotlinProto()

        assert(requestEmptyField == deserializedEmptyField) { "Request with empty field should serialize correctly" }
        assert(deserializedEmptyField.hasField()) { "Deserialized request should have field set" }
        assert(deserializedEmptyField.field == "") { "Deserialized field should be empty string" }

        // Test case 3: Field set to "John"
        val requestWithField = TestOptionalFieldRequestKt(field = "John")
        val javaProtoWithField = requestWithField.toJavaProto()
        val bytesWithField = javaProtoWithField.toByteArray()
        val deserializedWithField = TestOptionalFieldRequest.parseFrom(bytesWithField).toKotlinProto()

        assert(requestWithField == deserializedWithField) { "Request with field should serialize correctly" }
        assert(deserializedWithField.hasField()) { "Deserialized request should have field set" }
        assert(deserializedWithField.field == "John") { "Deserialized field should be 'John'" }

        // Test TestOptionalFieldResponse
        val responseTrue = TestOptionalFieldResponseKt(hasField = true)
        val javaProtoResponseTrue = responseTrue.toJavaProto()
        val bytesResponseTrue = javaProtoResponseTrue.toByteArray()
        val deserializedResponseTrue = TestOptionalFieldResponse.parseFrom(bytesResponseTrue).toKotlinProto()

        assert(responseTrue == deserializedResponseTrue) { "Response with true should serialize correctly" }
        assert(deserializedResponseTrue.hasField) { "Deserialized response should have hasField true" }

        val responseFalse = TestOptionalFieldResponseKt(hasField = false)
        val javaProtoResponseFalse = responseFalse.toJavaProto()
        val bytesResponseFalse = javaProtoResponseFalse.toByteArray()
        val deserializedResponseFalse = TestOptionalFieldResponse.parseFrom(bytesResponseFalse).toKotlinProto()

        assert(responseFalse == deserializedResponseFalse) { "Response with false should serialize correctly" }
        assert(!deserializedResponseFalse.hasField) { "Deserialized response should have hasField false" }
    }

    /**
     * Implementation of the PersonService for testing.
     */
    private class PersonServiceImpl : PersonServiceGrpcKt.PersonServiceCoroutineImplBase() {
        override suspend fun getPerson(request: GetPersonRequestKt): GetPersonResponseKt {
            // Simulate fetching a person by ID
            val person = PersonKt(
                name = "John Doe",
                age = 30,
                hobbies = listOf("Reading", "Hiking"),
                gender = PersonKt.GenderKt.MALE,
                address = PersonKt.AddressKt(
                    street = "123 Main St",
                    city = "San Francisco",
                    country = "USA"
                )
            )
            return GetPersonResponseKt(person = person)
        }

        override suspend fun deletePerson(request: DeletePersonRequestKt) {
        }

        override fun listPersons(request: ListPersonsRequestKt): Flow<ListPersonsResponseKt> = flow {
            // Simulate fetching a list of persons
            val persons = listOf(
                PersonKt(name = "Alice", age = 25, gender = PersonKt.GenderKt.FEMALE),
                PersonKt(name = "Bob", age = 30, gender = PersonKt.GenderKt.MALE),
                PersonKt(name = "Charlie", age = 35, gender = PersonKt.GenderKt.NON_BINARY)
            )

            // Emit each person as a separate response
            persons.forEach { person ->
                emit(ListPersonsResponseKt(person = person))
                delay(100) // Simulate some processing time
            }
        }

        override suspend fun updatePerson(requests: Flow<UpdatePersonRequestKt>): UpdatePersonResponseKt {
            // Process each update request
            requests.collect { request ->
                println("Updating person: ${request.person?.name}")
                // In a real implementation, you would update the person in a database
            }
            return UpdatePersonResponseKt(success = true)
        }

        override fun chatWithPerson(requests: Flow<ChatRequestKt>): Flow<ChatResponseKt> {
            // Echo back each message with a prefix
            return requests.map { request ->
                println("Received chat message: ${request.message}")
                ChatResponseKt(message = "Server received: ${request.message}")
            }
        }

        override suspend fun updateContactInfo(request: UpdateContactInfoRequestKt): UpdateContactInfoResponseKt {
            println("Test server received updateContactInfo request for person: ${request.personId}")
            return UpdateContactInfoResponseKt(success = true)
        }

        override suspend fun updateNotificationSettings(request: UpdateNotificationSettingsRequestKt): UpdateNotificationSettingsResponseKt {
            println("Test server received updateNotificationSettings request for user: ${request.userId}")
            return UpdateNotificationSettingsResponseKt(
                success = true,
                message = "Test notification settings updated successfully"
            )
        }

        override suspend fun getSchedule(request: GetScheduleRequestKt): GetScheduleResponseKt {
            println("Test server received getSchedule request for person: ${request.personId}")

            // Create sample schedule items (now nested ScheduleItem type)
            val scheduleItems = listOf(
                GetScheduleResponseKt.ScheduleItemKt(
                    id = "test_meeting1",
                    title = "Product Planning",
                    description = "Quarterly product roadmap discussion"
                ),
                GetScheduleResponseKt.ScheduleItemKt(
                    id = "test_break1",
                    title = "Coffee Break",
                    description = "Team coffee break"
                )
            )

            return GetScheduleResponseKt(
                items = scheduleItems,
                `when` = LocalDateTime.now() // Using backticks for Kotlin keyword
            )
        }

        override suspend fun testOptionalField(request: TestOptionalFieldRequestKt): TestOptionalFieldResponseKt {
            println("Test server received testOptionalField request")

            // Check if the optional field is present using hasField() method
            val hasField = request.hasField()
            val fieldValue = if (hasField) request.field else "null"

            println("Test server - Field present: $hasField, field value: '$fieldValue'")

            return TestOptionalFieldResponseKt(hasField = hasField)
        }
    }
}
