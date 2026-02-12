package com.example

import com.example.proto.*
import com.google.protobuf.util.JsonFormat
import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
    fun `test Person DSL builder`() {
        val person = PersonKt {
            name = "DSL User"
            age = 25
            hobbies = listOf("Coding", "Gaming")
            gender = PersonKt.GenderKt.MALE
            address = PersonKt.AddressKt {
                street = "DSL Street"
                city = "Builder City"
                country = "Kotlin"
            }
        }

        person.name shouldBe "DSL User"
        person.age shouldBe 25
        person.hobbies.size shouldBe 2
        person.gender shouldBe PersonKt.GenderKt.MALE
        person.address?.city shouldBe "Builder City"

        // Verify it matches manual construction
        val manual = PersonKt(
            name = "DSL User",
            age = 25,
            hobbies = listOf("Coding", "Gaming"),
            gender = PersonKt.GenderKt.MALE,
            address = PersonKt.AddressKt(
                street = "DSL Street",
                city = "Builder City",
                country = "Kotlin"
            )
        )
        person shouldBe manual
    }

    @Test
    fun `test ContactInfo DSL with oneof`() {
        val contact = ContactInfoKt {
            name = "Oneof User"
            contactMethod = ContactInfoKt.ContactMethodKt.EmailKt(email = "dsl@example.com")
            preference = ContactInfoKt.ContactPreferenceKt.EMAIL_ONLY
        }

        contact.name shouldBe "Oneof User"
        contact.contactMethod.shouldBeInstanceOf<ContactInfoKt.ContactMethodKt.EmailKt>()
        (contact.contactMethod as ContactInfoKt.ContactMethodKt.EmailKt).email shouldBe "dsl@example.com"
    }

    @Test
    fun `test GetScheduleResponse DSL with keyword field`() {
        val now = LocalDateTime.now()
        val response = GetScheduleResponseKt {
            items = listOf(
                GetScheduleResponseKt.ScheduleItemKt {
                    id = "1"
                    title = "DSL Task"
                }
            )
            `when` = now
        }

        response.items.size shouldBe 1
        response.`when` shouldBe now
        response.items[0].title shouldBe "DSL Task"
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
        deserializedPerson shouldBe person

        // Test ByteString serialization/deserialization
        val byteString = person.toByteString()
        val deserializedFromByteString = PersonKt.parseFrom(byteString)
        deserializedFromByteString shouldBe person

        // Test OutputStream/InputStream serialization/deserialization
        val outputStream = java.io.ByteArrayOutputStream()
        person.writeTo(outputStream)
        val inputStream = java.io.ByteArrayInputStream(outputStream.toByteArray())
        val deserializedFromStream = PersonKt.parseFrom(inputStream)
        deserializedFromStream shouldBe person

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
        fromJson shouldBe person
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

        deserialized shouldBe person
        deserialized.hobbies.size shouldBe 4
        deserialized.gender shouldBe PersonKt.GenderKt.FEMALE
    }

    @Test
    fun `test ContactInfo phone oneof serialization`() {
        val contactInfo = ContactInfoKt(
            name = "Bob",
            contactMethod = ContactInfoKt.ContactMethodKt.PhoneKt(phone = "+123456789"),
            preference = ContactInfoKt.ContactPreferenceKt.PHONE_ONLY
        )

        val javaProto = contactInfo.toJavaProto()
        javaProto.hasPhone() shouldBe true
        javaProto.phone shouldBe "+123456789"

        val deserialized = javaProto.toKotlinProto()
        deserialized shouldBe contactInfo
        deserialized.contactMethod.shouldBeInstanceOf<ContactInfoKt.ContactMethodKt.PhoneKt>()
        deserialized.preference shouldBe ContactInfoKt.ContactPreferenceKt.PHONE_ONLY
    }

    @Test
    fun `test gRPC unary call`(): Unit = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a request
        val request = GetPersonRequestKt(id = "123")

        // Make the call
        val response = stub.getPerson(request)

        // Verify the response
        response.person?.name shouldBe "John Doe"
        response.person?.age shouldBe 30
    }

    @Test
    fun `test gRPC delete person`(): Unit = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a request
        val request = DeletePersonRequestKt(id = "123")

        // Make the call
        stub.deletePerson(request)
    }

    @Test
    fun `test gRPC server streaming`(): Unit = runBlocking {
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
        responses.size shouldBe 3
        responses[0]?.gender shouldBe PersonKt.GenderKt.FEMALE
        responses[1]?.gender shouldBe PersonKt.GenderKt.MALE
        responses[2]?.gender shouldBe PersonKt.GenderKt.NON_BINARY
    }

    @Test
    fun `test gRPC client streaming`(): Unit = runBlocking {
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
        response.success shouldBe true
    }

    @Test
    fun `test gRPC bidirectional streaming`(): Unit = runBlocking {
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
        responses.size shouldBe 5
    }

    @Test
    fun `test ContactInfo string oneof serialization`() {
        // Test ContactInfo with email
        val contactInfoEmail = ContactInfoKt(
            name = "Alice Smith",
            contactMethod = ContactInfoKt.ContactMethodKt.EmailKt(email = "alice@example.com"),
            tags = listOf("customer", "vip"),
            preference = ContactInfoKt.ContactPreferenceKt.EMAIL_ONLY
        )

        // Serialize and deserialize
        val emailBytes = contactInfoEmail.toByteArray()
        val deserializedEmail = ContactInfoKt.parseFrom(emailBytes)
        deserializedEmail shouldBe contactInfoEmail

        // Test ContactInfo with phone
        val contactInfoPhone = ContactInfoKt(
            name = "Bob Johnson",
            contactMethod = ContactInfoKt.ContactMethodKt.PhoneKt(phone = "+1-555-0123"),
            tags = listOf("lead"),
            preference = ContactInfoKt.ContactPreferenceKt.PHONE_ONLY
        )

        val phoneBytes = contactInfoPhone.toByteArray()
        val deserializedPhone = ContactInfoKt.parseFrom(phoneBytes)
        deserializedPhone shouldBe contactInfoPhone

        // Test ContactInfo with username
        val contactInfoUsername = ContactInfoKt(
            name = "Charlie Brown",
            contactMethod = ContactInfoKt.ContactMethodKt.UsernameKt(username = "@charlie_b"),
            tags = listOf("partner"),
            preference = ContactInfoKt.ContactPreferenceKt.ANY_METHOD
        )

        val usernameJavaProto = contactInfoUsername.toJavaProto()
        val usernameBytes = usernameJavaProto.toByteArray()
        val deserializedUsername = ContactInfo.parseFrom(usernameBytes).toKotlinProto()
        deserializedUsername shouldBe contactInfoUsername

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
        deserializedNull shouldBe contactInfoNull
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
            notificationChannel = NotificationSettingsKt.NotificationChannelKt.EmailSettingsKt(
                emailSettings = emailSettings
            ),
            notificationsEnabled = true
        )

        val emailNotificationBytes = notificationSettingsEmail.toByteArray()
        val deserializedEmailNotification = NotificationSettingsKt.parseFrom(emailNotificationBytes)
        deserializedEmailNotification shouldBe notificationSettingsEmail

        // Test NotificationSettings with SMS settings
        val smsSettings = NotificationSettingsKt.SmsSettingsKt(
            phoneNumber = "+1-555-0456",
            urgentOnly = true
        )
        val notificationSettingsSms = NotificationSettingsKt(
            userId = "user456",
            notificationChannel = NotificationSettingsKt.NotificationChannelKt.SmsSettingsKt(
                smsSettings = smsSettings
            ),
            notificationsEnabled = true
        )

        val smsNotificationJavaProto = notificationSettingsSms.toJavaProto()
        val smsNotificationBytes = smsNotificationJavaProto.toByteArray()
        val deserializedSmsNotification = NotificationSettings.parseFrom(smsNotificationBytes).toKotlinProto()
        deserializedSmsNotification shouldBe notificationSettingsSms

        // Test NotificationSettings with push settings
        val pushSettings = NotificationSettingsKt.PushSettingsKt(
            deviceToken = "abc123def456",
            soundEnabled = true,
            soundName = "notification_sound.wav"
        )
        val notificationSettingsPush = NotificationSettingsKt(
            userId = "user789",
            notificationChannel = NotificationSettingsKt.NotificationChannelKt.PushSettingsKt(
                pushSettings = pushSettings
            ),
            notificationsEnabled = false
        )

        val pushNotificationJavaProto = notificationSettingsPush.toJavaProto()
        val pushNotificationBytes = pushNotificationJavaProto.toByteArray()
        val deserializedPushNotification = NotificationSettings.parseFrom(pushNotificationBytes).toKotlinProto()
        deserializedPushNotification shouldBe notificationSettingsPush

        // Test NotificationSettings with null oneof
        val notificationSettingsNull = NotificationSettingsKt(
            userId = "user000",
            notificationChannel = null,
            notificationsEnabled = false
        )

        val nullNotificationJavaProto = notificationSettingsNull.toJavaProto()
        val nullNotificationBytes = nullNotificationJavaProto.toByteArray()
        val deserializedNullNotification = NotificationSettings.parseFrom(nullNotificationBytes).toKotlinProto()
        deserializedNullNotification shouldBe notificationSettingsNull
    }

    @Test
    fun `test updateContactInfo gRPC call`(): Unit = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Test with EMAIL preference
        val contactInfoEmail = ContactInfoKt(
            name = "Test User Email",
            contactMethod = ContactInfoKt.ContactMethodKt.EmailKt(email = "test@example.com"),
            tags = listOf("test", "email"),
            preference = ContactInfoKt.ContactPreferenceKt.EMAIL_ONLY
        )
        val requestEmail = UpdateContactInfoRequestKt(personId = "test_email", contactInfo = contactInfoEmail)
        val responseEmail = stub.updateContactInfo(requestEmail)
        responseEmail.success shouldBe true

        // Test with PHONE preference
        val contactInfoPhone = ContactInfoKt(
            name = "Test User Phone",
            contactMethod = ContactInfoKt.ContactMethodKt.PhoneKt(phone = "123-456"),
            tags = listOf("test", "phone"),
            preference = ContactInfoKt.ContactPreferenceKt.PHONE_ONLY
        )
        val requestPhone = UpdateContactInfoRequestKt(personId = "test_phone", contactInfo = contactInfoPhone)
        val responsePhone = stub.updateContactInfo(requestPhone)
        responsePhone.success shouldBe true
    }

    @Test
    fun `test updateNotificationSettings gRPC call`(): Unit = runBlocking {
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
            notificationChannel = NotificationSettingsKt.NotificationChannelKt.PushSettingsKt(
                pushSettings = pushSettings
            ),
            notificationsEnabled = true
        )

        val request = UpdateNotificationSettingsRequestKt(
            userId = "test456",
            settings = notificationSettings
        )

        val response = stub.updateNotificationSettings(request)
        response.success shouldBe true
        response.message shouldBe "Test notification settings updated successfully"
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
        deserializedRequest shouldBe scheduleRequest

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
        deserializedResponse shouldBe scheduleResponse

        // Test field access with backticks
        scheduleRequest.personId shouldBe "user123"
        scheduleResponse.`when` shouldNotBe null
    }

    @Test
    fun `test Kotlin keyword field check functions`() {
        // Test personId field access
        val scheduleRequest = GetScheduleRequestKt(
            personId = "user123"
        )
        scheduleRequest.personId shouldBe "user123"

        val scheduleRequestEmpty = GetScheduleRequestKt(
            personId = ""
        )
        scheduleRequestEmpty.personId shouldBe ""

        // Test hasWhen() function for response with Kotlin keyword field
        val scheduleResponseWithWhen = GetScheduleResponseKt(
            items = listOf(),
            `when` = LocalDateTime.now() // Using backticks for Kotlin keyword
        )
        scheduleResponseWithWhen.hasWhen() shouldBe true

        val scheduleResponseWithoutWhen = GetScheduleResponseKt(
            items = listOf(),
            `when` = null
        )
        scheduleResponseWithoutWhen.hasWhen() shouldBe false
    }

    @Test
    fun `test getSchedule gRPC call with Kotlin keywords`(): Unit = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create request (no longer has 'when' field)
        val request = GetScheduleRequestKt(
            personId = "test789"
        )

        val response = stub.getSchedule(request)

        // Verify response has items and timestamp (with Kotlin keyword field)
        response.items.isNotEmpty() shouldBe true
        response.`when` shouldNotBe null

        // Verify item properties (now nested ScheduleItem type)
        val firstItem = response.items.first()
        firstItem.id.isNotEmpty() shouldBe true
        firstItem.title.isNotEmpty() shouldBe true
        firstItem.description.isNotEmpty() shouldBe true
    }

    @Test
    fun `test TestOptionalField - field not set`(): Unit = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Test scenario 1: Field NOT set
        println("Testing TestOptionalField - field not set")
        val request = TestOptionalFieldRequestKt()
        val response = stub.testOptionalField(request)

        // Should return false for hasField when field is not set
        response.hasField shouldBe false
        println("✓ Field not set test passed: hasField = ${response.hasField}")
    }

    @Test
    fun `test TestOptionalField - field set to empty string`(): Unit = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Test scenario 2: Field SET to empty string
        println("Testing TestOptionalField - field set to empty string")
        val request = TestOptionalFieldRequestKt(field = "")
        val response = stub.testOptionalField(request)

        // Should return true for hasField when field is set to empty string
        response.hasField shouldBe true
        println("✓ Field set to empty string test passed: hasField = ${response.hasField}")
    }

    @Test
    fun `test TestOptionalField - field set to John`(): Unit = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Test scenario 3: Field SET to "John"
        println("Testing TestOptionalField - field set to 'John'")
        val request = TestOptionalFieldRequestKt(field = "John")
        val response = stub.testOptionalField(request)

        // Should return true for hasField when field is set to "John"
        response.hasField shouldBe true
        println("✓ Field set to 'John' test passed: hasField = ${response.hasField}")
    }

    @Test
    fun `test TestOptionalField - comprehensive scenarios`(): Unit = runBlocking {
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

            response.hasField shouldBe expectedHasField

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

        deserializedNoField shouldBe requestNoField
        deserializedNoField.hasField() shouldBe false

        // Test case 2: Field set to empty string
        val requestEmptyField = TestOptionalFieldRequestKt(field = "")
        val javaProtoEmptyField = requestEmptyField.toJavaProto()
        val bytesEmptyField = javaProtoEmptyField.toByteArray()
        val deserializedEmptyField = TestOptionalFieldRequest.parseFrom(bytesEmptyField).toKotlinProto()

        deserializedEmptyField shouldBe requestEmptyField
        deserializedEmptyField.hasField() shouldBe true
        deserializedEmptyField.field shouldBe ""

        // Test case 3: Field set to "John"
        val requestWithField = TestOptionalFieldRequestKt(field = "John")
        val javaProtoWithField = requestWithField.toJavaProto()
        val bytesWithField = javaProtoWithField.toByteArray()
        val deserializedWithField = TestOptionalFieldRequest.parseFrom(bytesWithField).toKotlinProto()

        deserializedWithField shouldBe requestWithField
        deserializedWithField.hasField() shouldBe true
        deserializedWithField.field shouldBe "John"

        // Test TestOptionalFieldResponse
        val responseTrue = TestOptionalFieldResponseKt(hasField = true)
        val javaProtoResponseTrue = responseTrue.toJavaProto()
        val bytesResponseTrue = javaProtoResponseTrue.toByteArray()
        val deserializedResponseTrue = TestOptionalFieldResponse.parseFrom(bytesResponseTrue).toKotlinProto()

        deserializedResponseTrue shouldBe responseTrue
        deserializedResponseTrue.hasField shouldBe true

        val responseFalse = TestOptionalFieldResponseKt(hasField = false)
        val javaProtoResponseFalse = responseFalse.toJavaProto()
        val bytesResponseFalse = javaProtoResponseFalse.toByteArray()
        val deserializedResponseFalse = TestOptionalFieldResponse.parseFrom(bytesResponseFalse).toKotlinProto()

        deserializedResponseFalse shouldBe responseFalse
        deserializedResponseFalse.hasField shouldBe false
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

        override suspend fun updateNotificationSettings(
            request: UpdateNotificationSettingsRequestKt
        ): UpdateNotificationSettingsResponseKt {
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

    @Test
    fun `test message parser`() {
        val person = PersonKt(
            name = "Parser User",
            age = 40,
            gender = PersonKt.GenderKt.MALE
        )
        val bytes = person.toByteArray()

        // Use the generated parser
        val parser = PersonKt.parser()
        val deserialized = parser.parseFrom(bytes)

        deserialized shouldBe person

        // Test with ContactInfo (has oneof)
        val contact = ContactInfoKt(
            name = "Contact Parser",
            contactMethod = ContactInfoKt.ContactMethodKt.EmailKt(email = "parser@example.com")
        )
        val contactBytes = contact.toByteArray()
        val contactParser = ContactInfoKt.parser()
        val deserializedContact = contactParser.parseFrom(contactBytes)

        deserializedContact shouldBe contact
    }
}
