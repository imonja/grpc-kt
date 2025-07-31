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
import java.util.concurrent.TimeUnit

/**
 * This class demonstrates how to use the generated Protocol Buffer code.
 * It shows examples of:
 * 1. Creating and manipulating protobuf messages using Kotlin data classes
 * 2. Serializing and deserializing protobuf messages
 * 3. Implementing a gRPC service
 * 4. Creating a gRPC client to interact with the service
 */
object ProtoExample {

    private const val SERVER_PORT = 50051

    @JvmStatic
    fun main(args: Array<String>) {
        println("Starting Proto Example")

        // Example 1: Creating and serializing Person message
        demonstrateProtobufSerialization()

        // Example 2: Oneof message examples
        demonstrateOneofMessages()

        // Example 3: Kotlin keywords demonstration
        demonstrateKotlinKeywords()

        // Example 4: gRPC service interaction
        demonstrateGrpcService()

        println("Proto Example completed")
    }

    /**
     * Demonstrates how to create, serialize, and deserialize Protocol Buffer messages.
     */
    private fun demonstrateProtobufSerialization() {
        println("\n=== Protobuf Serialization Example ===")

        // Create a Person using the Kotlin data class
        val person = PersonKt(
            name = "John Doe",
            age = 30,
            hobbies = listOf("Reading", "Hiking", "Coding"),
            gender = Person.Gender.MALE,
            address = PersonKt.AddressKt(
                street = "123 Main St",
                city = "San Francisco",
                country = "USA"
            )
        )

        println("Created person: $person")

        // Convert to Java protobuf message
        val javaProto = person.toJavaProto()

        // Serialize to binary format
        val bytes = javaProto.toByteArray()
        println("Serialized size: ${bytes.size} bytes")

        // Deserialize from binary format
        val deserializedJavaProto = Person.parseFrom(bytes)
        val deserializedPerson = deserializedJavaProto.toKotlinProto()

        // Verify the deserialized object matches the original
        println("Deserialized person equals original: ${person == deserializedPerson}")

        // Convert to JSON
        val jsonPrinter = JsonFormat.printer().includingDefaultValueFields()
        val json = jsonPrinter.print(javaProto)
        println("JSON representation:\n$json")

        // Parse from JSON
        val jsonParser = JsonFormat.parser()
        val builder = Person.newBuilder()
        jsonParser.merge(json, builder)
        val fromJson = builder.build().toKotlinProto()

        // Verify the parsed object matches the original
        println("Person parsed from JSON equals original: ${person == fromJson}")
    }

    /**
     * Demonstrates how to work with oneof fields in Protocol Buffer messages.
     */
    private fun demonstrateOneofMessages() {
        println("\n=== Oneof Messages Example ===")

        // Example 1: ContactInfo with string oneof
        println("\n--- ContactInfo (String Oneof) ---")

        // Create ContactInfo with email
        val contactInfoEmail = ContactInfoKt(
            name = "Alice Smith",
            contactMethod = ContactInfoKt.ContactMethod.Email(email = "alice@example.com"),
            tags = listOf("customer", "vip"),
            preference = ContactInfo.ContactPreference.EMAIL_ONLY
        )
        println("ContactInfo with email: $contactInfoEmail")

        // Create ContactInfo with phone
        val contactInfoPhone = ContactInfoKt(
            name = "Bob Johnson",
            contactMethod = ContactInfoKt.ContactMethod.Phone(phone = "+1-555-0123"),
            tags = listOf("lead"),
            preference = ContactInfo.ContactPreference.PHONE_ONLY
        )
        println("ContactInfo with phone: $contactInfoPhone")

        // Create ContactInfo with username
        val contactInfoUsername = ContactInfoKt(
            name = "Charlie Brown",
            contactMethod = ContactInfoKt.ContactMethod.Username(username = "@charlie_b"),
            tags = listOf("partner"),
            preference = ContactInfo.ContactPreference.ANY_METHOD
        )
        println("ContactInfo with username: $contactInfoUsername")

        // Demonstrate serialization/deserialization with oneof
        val emailJavaProto = contactInfoEmail.toJavaProto()
        val emailBytes = emailJavaProto.toByteArray()
        val deserializedEmail = ContactInfo.parseFrom(emailBytes).toKotlinProto()
        println("Email contact info serialization works: ${contactInfoEmail == deserializedEmail}")

        // Example 2: NotificationSettings with message oneof
        println("\n--- NotificationSettings (Message Oneof) ---")

        // Create NotificationSettings with email settings
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
        println("NotificationSettings with email: $notificationSettingsEmail")

        // Create NotificationSettings with SMS settings
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
        println("NotificationSettings with SMS: $notificationSettingsSms")

        // Create NotificationSettings with push settings
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
        println("NotificationSettings with push: $notificationSettingsPush")

        // Demonstrate serialization/deserialization with message oneof
        val emailNotificationJavaProto = notificationSettingsEmail.toJavaProto()
        val emailNotificationBytes = emailNotificationJavaProto.toByteArray()
        val deserializedEmailNotification = NotificationSettings.parseFrom(emailNotificationBytes).toKotlinProto()
        println("Email notification settings serialization works: ${notificationSettingsEmail == deserializedEmailNotification}")

        // Demonstrate accessing oneof fields
        println("\n--- Accessing Oneof Fields ---")
        println(
            "Contact method type: ${when (contactInfoEmail.contactMethod) {
                is ContactInfoKt.ContactMethod.Email -> "Email: ${contactInfoEmail.contactMethod.email}"
                is ContactInfoKt.ContactMethod.Phone -> "Phone: ${contactInfoEmail.contactMethod.phone}"
                is ContactInfoKt.ContactMethod.Username -> "Username: ${contactInfoEmail.contactMethod.username}"
                null -> "No contact method set"
            }}"
        )

        println(
            "Notification channel type: ${when (notificationSettingsEmail.notificationChannel) {
                is NotificationSettingsKt.NotificationChannel.EmailSettings -> "Email notifications to: ${notificationSettingsEmail.notificationChannel.emailSettings?.emailAddress}"
                is NotificationSettingsKt.NotificationChannel.SmsSettings -> "SMS notifications to: ${notificationSettingsEmail.notificationChannel.smsSettings?.phoneNumber}"
                is NotificationSettingsKt.NotificationChannel.PushSettings -> "Push notifications to device: ${notificationSettingsEmail.notificationChannel.pushSettings?.deviceToken}"
                null -> "No notification channel set"
            }}"
        )
    }

    /**
     * Demonstrates handling of Kotlin keywords in protobuf fields
     */
    private fun demonstrateKotlinKeywords() {
        println("\n=== Kotlin Keywords Example ===")

        // Create a schedule request (no longer has 'when' field)
        val scheduleRequest = GetScheduleRequestKt(
            personId = "user123"
        )
        println("Created schedule request: $scheduleRequest")
        println("Person ID: ${scheduleRequest.personId}")

        // Create a schedule item (now nested in response)
        val scheduleItem = GetScheduleResponseKt.ScheduleItemKt(
            id = "item1",
            title = "Team Meeting",
            description = "Weekly team sync"
        )

        // Create a schedule response with 'when' field (Kotlin keyword)
        val scheduleResponse = GetScheduleResponseKt(
            items = listOf(scheduleItem),
            `when` = java.time.LocalDateTime.now() // Using backticks for Kotlin keyword
        )
        println("Created schedule response: $scheduleResponse")
        println("Response timestamp (when field): ${scheduleResponse.`when`}")

        // Demonstrate serialization/deserialization with Kotlin keywords
        val requestJavaProto = scheduleRequest.toJavaProto()
        val requestBytes = requestJavaProto.toByteArray()
        val deserializedRequest = GetScheduleRequest.parseFrom(requestBytes).toKotlinProto()
        println("Schedule request serialization works: ${scheduleRequest == deserializedRequest}")

        val responseJavaProto = scheduleResponse.toJavaProto()
        val responseBytes = responseJavaProto.toByteArray()
        val deserializedResponse = GetScheduleResponse.parseFrom(responseBytes).toKotlinProto()
        println("Schedule response serialization works: ${scheduleResponse == deserializedResponse}")

        // Show how to check if field is set using generated helper functions
        println("Person ID: '${scheduleRequest.personId}'")
        println("Has timestamp when field set: ${scheduleResponse.hasWhen()}")
    }

    /**
     * Demonstrates how to use gRPC services with the generated code.
     */
    private fun demonstrateGrpcService() = runBlocking {
        println("\n=== gRPC Service Example ===")

        // Start the gRPC server
        val server = ServerBuilder.forPort(SERVER_PORT)
            .addService(PersonServiceImpl())
            .build()
            .start()

        println("Server started on port $SERVER_PORT")

        // Create a channel to the server
        val channel = ManagedChannelBuilder.forAddress("localhost", SERVER_PORT)
            .usePlaintext()
            .build()

        try {
            // Create a client stub
            val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel)

            // Example 1.1: Unary call - GetPerson
            println("\n--- Unary Call Example (GetPerson) ---")
            val getPersonRequest = GetPersonRequestKt(id = "123")
            val getPersonResponse = stub.getPerson(getPersonRequest)
            println("Received person: ${getPersonResponse.person?.name}, age: ${getPersonResponse.person?.age}")

            // Example 1.2: Unary call - DeletePerson
            println("\n--- Unary Call Example (DeletePerson) ---")
            val deletePersonRequest = DeletePersonRequestKt(id = "123")
            stub.deletePerson(deletePersonRequest)
            println("Person with id ${deletePersonRequest.id} deleted successfully")

            // Example 2: Server streaming
            println("\n--- Server Streaming Example ---")
            val listRequest = ListPersonsRequestKt(limit = 5, offset = 0)
            println("Requesting persons with limit=${listRequest.limit}, offset=${listRequest.offset}")

            val persons = mutableListOf<PersonKt?>()
            stub.listPersons(listRequest).collect { response ->
                persons.add(response.person)
                println("Received person: ${response.person?.name}, age: ${response.person?.age}")
            }
            println("Received ${persons.size} persons in total")

            // Example 3: Client streaming
            println("\n--- Client Streaming Example ---")
            val updateRequests = flow {
                for (i in 1..3) {
                    val person = PersonKt(
                        name = "Person $i",
                        age = 20 + i,
                        hobbies = listOf("Hobby $i"),
                        gender = Person.Gender.UNKNOWN,
                        address = PersonKt.AddressKt(
                            street = "$i Main St",
                            city = "City $i",
                            country = "Country $i"
                        )
                    )
                    println("Sending update for person: ${person.name}")
                    emit(UpdatePersonRequestKt(person = person))
                    delay(100) // Simulate some processing time
                }
            }

            val updateResponse = stub.updatePerson(updateRequests)
            println("Update successful: ${updateResponse.success}")

            // Example 4: Bidirectional streaming
            println("\n--- Bidirectional Streaming Example ---")
            val chatRequests = flow {
                for (i in 1..5) {
                    val message = "Hello $i from client"
                    println("Sending: $message")
                    emit(ChatRequestKt(message = message))
                    delay(100) // Simulate some processing time
                }
            }

            val job = launch {
                stub.chatWithPerson(chatRequests).collect { response ->
                    println("Received: ${response.message}")
                }
            }

            // Wait for the chat to complete
            job.join()

            // Example 5: UpdateContactInfo with oneof
            println("\n--- UpdateContactInfo Example ---")
            val contactInfo = ContactInfoKt(
                name = "Service User",
                contactMethod = ContactInfoKt.ContactMethod.Email(email = "service@example.com"),
                tags = listOf("service", "automated"),
                preference = ContactInfo.ContactPreference.EMAIL_ONLY
            )
            val updateContactRequest = UpdateContactInfoRequestKt(
                personId = "service123",
                contactInfo = contactInfo
            )
            val contactResponse = stub.updateContactInfo(updateContactRequest)
            println("Contact info update successful: ${contactResponse.success}")

            // Example 6: UpdateNotificationSettings with message oneof
            println("\n--- UpdateNotificationSettings Example ---")
            val pushSettings = NotificationSettingsKt.PushSettingsKt(
                deviceToken = "service_device_token",
                soundEnabled = false,
                soundName = "silent"
            )
            val notificationSettings = NotificationSettingsKt(
                userId = "service456",
                notificationChannel = NotificationSettingsKt.NotificationChannel.PushSettings(
                    pushSettings = pushSettings
                ),
                notificationsEnabled = true
            )
            val updateNotificationRequest = UpdateNotificationSettingsRequestKt(
                userId = "service456",
                settings = notificationSettings
            )
            val notificationResponse = stub.updateNotificationSettings(updateNotificationRequest)
            println("Notification settings update successful: ${notificationResponse.success}")
            println("Response message: ${notificationResponse.message}")

            // Example 7: GetSchedule with Kotlin keyword field in response
            println("\n--- GetSchedule Example (Kotlin keywords) ---")
            val getScheduleRequest = GetScheduleRequestKt(
                personId = "service789"
            )
            val scheduleResponse = stub.getSchedule(getScheduleRequest)
            println("Schedule response received at: ${scheduleResponse.`when`}") // Using backticks for Kotlin keyword
            println("Number of schedule items: ${scheduleResponse.items.size}")
            scheduleResponse.items.forEach { item ->
                println("Schedule item: ${item.id} - ${item.title}")
                println("  Description: ${item.description}")
            }
        } finally {
            // Shutdown the channel and server
            println("\nShutting down client and server")
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    /**
     * Implementation of the PersonService for demonstration.
     */
    private class PersonServiceImpl : PersonServiceGrpcKt.PersonServiceCoroutineImplBase() {
        override suspend fun getPerson(request: GetPersonRequestKt): GetPersonResponseKt {
            println("Server received getPerson request for id: ${request.id}")

            // Simulate fetching a person by ID
            val person = PersonKt(
                name = "John Doe",
                age = 30,
                hobbies = listOf("Reading", "Hiking"),
                gender = Person.Gender.MALE,
                address = PersonKt.AddressKt(
                    street = "123 Main St",
                    city = "San Francisco",
                    country = "USA"
                )
            )
            return GetPersonResponseKt(person = person)
        }

        override suspend fun deletePerson(request: DeletePersonRequestKt) {
            println("Server received deletePerson request for id: ${request.id}")

            // Simulate deleting a person by ID
            // No return value needed as the method returns Unit
        }

        override fun listPersons(request: ListPersonsRequestKt): Flow<ListPersonsResponseKt> = flow {
            println("Server received listPersons request with limit: ${request.limit}, offset: ${request.offset}")

            // Simulate fetching a list of persons
            val persons = listOf(
                PersonKt(name = "Alice", age = 25, gender = Person.Gender.FEMALE),
                PersonKt(name = "Bob", age = 30, gender = Person.Gender.MALE),
                PersonKt(name = "Charlie", age = 35, gender = Person.Gender.NON_BINARY)
            )

            // Emit each person as a separate response
            persons.forEach { person ->
                println("Server sending person: ${person.name}")
                emit(ListPersonsResponseKt(person = person))
                delay(100) // Simulate some processing time
            }
        }

        override suspend fun updatePerson(requests: Flow<UpdatePersonRequestKt>): UpdatePersonResponseKt {
            println("Server received updatePerson request stream")

            // Process each update request
            requests.collect { request ->
                println("Server processing update for person: ${request.person?.name}")
                // In a real implementation, you would update the person in a database
            }
            return UpdatePersonResponseKt(success = true)
        }

        override fun chatWithPerson(requests: Flow<ChatRequestKt>): Flow<ChatResponseKt> {
            println("Server received chatWithPerson request stream")

            // Echo back each message with a prefix
            return requests.map { request ->
                println("Server received chat message: ${request.message}")
                ChatResponseKt(message = "Server received: ${request.message}")
            }
        }

        override suspend fun updateContactInfo(request: UpdateContactInfoRequestKt): UpdateContactInfoResponseKt {
            println("Server received updateContactInfo request for person: ${request.personId}")
            println("Contact info: ${request.contactInfo}")
            println(
                "Contact method: ${when (request.contactInfo?.contactMethod) {
                    is ContactInfoKt.ContactMethod.Email -> "Email: ${request.contactInfo.contactMethod.email}"
                    is ContactInfoKt.ContactMethod.Phone -> "Phone: ${request.contactInfo.contactMethod.phone}"
                    is ContactInfoKt.ContactMethod.Username -> "Username: ${request.contactInfo.contactMethod.username}"
                    null -> "None"
                }}"
            )

            return UpdateContactInfoResponseKt(success = true)
        }

        override suspend fun updateNotificationSettings(request: UpdateNotificationSettingsRequestKt): UpdateNotificationSettingsResponseKt {
            println("Server received updateNotificationSettings request for user: ${request.userId}")
            println("Settings: ${request.settings}")
            println(
                "Notification channel: ${when (request.settings?.notificationChannel) {
                    is NotificationSettingsKt.NotificationChannel.EmailSettings -> "Email: ${request.settings.notificationChannel.emailSettings?.emailAddress}"
                    is NotificationSettingsKt.NotificationChannel.SmsSettings -> "SMS: ${request.settings.notificationChannel.smsSettings?.phoneNumber}"
                    is NotificationSettingsKt.NotificationChannel.PushSettings -> "Push: ${request.settings.notificationChannel.pushSettings?.deviceToken}"
                    null -> "None"
                }}"
            )

            return UpdateNotificationSettingsResponseKt(success = true, message = "Notification settings updated successfully")
        }

        override suspend fun getSchedule(request: GetScheduleRequestKt): GetScheduleResponseKt {
            println("Server received getSchedule request for person: ${request.personId}")

            // Create sample schedule items (now nested ScheduleItem type)
            val scheduleItems = listOf(
                GetScheduleResponseKt.ScheduleItemKt(
                    id = "meeting1",
                    title = "Product Planning",
                    description = "Quarterly product roadmap discussion"
                ),
                GetScheduleResponseKt.ScheduleItemKt(
                    id = "break1",
                    title = "Coffee Break",
                    description = "Team coffee break"
                )
            )

            return GetScheduleResponseKt(
                items = scheduleItems,
                `when` = java.time.LocalDateTime.now() // Using backticks for Kotlin keyword
            )
        }
    }
}
