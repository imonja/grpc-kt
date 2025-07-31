package com.example

import com.example.proto.*
import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

object PartialServerExample {

    private val SERVER_PORT = (60000..65000).random()

    @JvmStatic
    fun main(args: Array<String>) {
        println("Starting Partial Server Example")

        demonstratePartialGrpcService()

        println("Partial Server Example completed")
    }

    /**
     * Demonstrates how to use gRPC services with the PartialServerBuilder.
     */
    fun demonstratePartialGrpcService() = runBlocking {
        println("\n=== Partial gRPC Service Example ===")

        // Define the service implementation functions
        val getPerson: PersonServiceGrpcPartialKt.GetPersonGrpcMethod =
            PersonServiceGrpcPartialKt.GetPersonGrpcMethod { request ->
                println("Partial server received getPerson request for id: ${request.id}")

                // Simulate fetching a person by ID
                val person = PersonKt(
                    name = "Jane Doe",
                    age = 28,
                    hobbies = listOf("Swimming", "Painting"),
                    gender = Person.Gender.FEMALE,
                    address = PersonKt.AddressKt(
                        street = "456 Oak St",
                        city = "New York",
                        country = "USA"
                    )
                )
                GetPersonResponseKt(person = person)
            }

        val deletePerson: PersonServiceGrpcPartialKt.DeletePersonGrpcMethod =
            PersonServiceGrpcPartialKt.DeletePersonGrpcMethod { request ->
                println("Partial server received deletePerson request for id: ${request.id}")
            }

        val listPersons: PersonServiceGrpcPartialKt.ListPersonsGrpcMethod =
            PersonServiceGrpcPartialKt.ListPersonsGrpcMethod { request ->
                println("Partial server received listPersons request with limit: ${request.limit}, offset: ${request.offset}")

                // Simulate fetching a list of persons
                val persons = listOf(
                    PersonKt(name = "David", age = 40, gender = Person.Gender.MALE),
                    PersonKt(name = "Emma", age = 22, gender = Person.Gender.FEMALE),
                    PersonKt(name = "Sam", age = 33, gender = Person.Gender.NON_BINARY)
                )

                // Return a flow of responses
                flow {
                    persons.forEach { person ->
                        println("Partial server sending person: ${person.name}")
                        emit(ListPersonsResponseKt(person = person))
                        delay(100) // Simulate some processing time
                    }
                }
            }

        val updatePerson: PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod =
            PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod { requests ->
                println("Partial server received updatePerson request stream")

                // Process each update request
                UpdatePersonResponseKt(success = true)
            }

        val chatWithPerson: PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod =
            PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod { requests ->
                println("Partial server received chatWithPerson request stream")

                // Echo back each message with a prefix
                flow {
                    requests.map { request ->
                        println("Received chat message: ${request.message}")
                        ChatResponseKt(message = "Partial server received: ${request.message}")
                    }.collect { response ->
                        emit(response)
                        delay(100) // Simulate some processing time
                    }
                }
            }

        val updateContactInfo: PersonServiceGrpcPartialKt.UpdateContactInfoGrpcMethod =
            PersonServiceGrpcPartialKt.UpdateContactInfoGrpcMethod { request ->
                println("Partial server received updateContactInfo request for person: ${request.personId}")
                println(
                    "Contact method: ${when (request.contactInfo?.contactMethod) {
                        is ContactInfoKt.ContactMethod.Email -> "Email: ${request.contactInfo.contactMethod.email}"
                        is ContactInfoKt.ContactMethod.Phone -> "Phone: ${request.contactInfo.contactMethod.phone}"
                        is ContactInfoKt.ContactMethod.Username -> "Username: ${request.contactInfo.contactMethod.username}"
                        null -> "None"
                    }}"
                )

                UpdateContactInfoResponseKt(success = true)
            }

        val updateNotificationSettings: PersonServiceGrpcPartialKt.UpdateNotificationSettingsGrpcMethod =
            PersonServiceGrpcPartialKt.UpdateNotificationSettingsGrpcMethod { request ->
                println("Partial server received updateNotificationSettings request for user: ${request.userId}")
                println(
                    "Notification channel: ${when (request.settings?.notificationChannel) {
                        is NotificationSettingsKt.NotificationChannel.EmailSettings -> "Email: ${request.settings.notificationChannel.emailSettings?.emailAddress}"
                        is NotificationSettingsKt.NotificationChannel.SmsSettings -> "SMS: ${request.settings.notificationChannel.smsSettings?.phoneNumber}"
                        is NotificationSettingsKt.NotificationChannel.PushSettings -> "Push: ${request.settings.notificationChannel.pushSettings?.deviceToken}"
                        null -> "None"
                    }}"
                )

                UpdateNotificationSettingsResponseKt(success = true, message = "Settings updated successfully")
            }

        val getSchedule: PersonServiceGrpcPartialKt.GetScheduleGrpcMethod =
            PersonServiceGrpcPartialKt.GetScheduleGrpcMethod { request ->
                println("Partial server received getSchedule request for person: ${request.personId}")

                // Create sample schedule items (now nested ScheduleItem type)
                val scheduleItems = listOf(
                    GetScheduleResponseKt.ScheduleItemKt(
                        id = "schedule1",
                        title = "Morning Meeting",
                        description = "Team standup meeting with ${request.personId}"
                    ),
                    GetScheduleResponseKt.ScheduleItemKt(
                        id = "schedule2",
                        title = "Code Review",
                        description = "Review pull request for Kotlin keywords feature"
                    )
                )

                GetScheduleResponseKt(
                    items = scheduleItems,
                    `when` = java.time.LocalDateTime.now()
                )
            }

        val testOptionalField: PersonServiceGrpcPartialKt.TestOptionalFieldGrpcMethod =
            PersonServiceGrpcPartialKt.TestOptionalFieldGrpcMethod { request ->
                println("Partial server received testOptionalField request")

                // Check if the optional field is present using hasField() method
                val hasField = request.hasField()
                val fieldValue = if (hasField) request.field else "null"

                println("Field present: $hasField, field value: '$fieldValue'")

                TestOptionalFieldResponseKt(hasField = hasField)
            }

        // Create the service using the PartialServerBuilder
        val partialService = PersonServiceGrpcPartialKt.PersonServiceCoroutineImplPartial(
            getPerson = getPerson,
            deletePerson = deletePerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson,
            updateContactInfo = updateContactInfo,
            updateNotificationSettings = updateNotificationSettings,
            getSchedule = getSchedule,
            testOptionalField = testOptionalField
        )

        // Start the gRPC server with the partial service
        val server = ServerBuilder.forPort(SERVER_PORT)
            .addService(partialService)
            .intercept(object : ServerInterceptor {
                override fun <ReqT : Any?, RespT : Any?> interceptCall(
                    call: ServerCall<ReqT?, RespT?>?,
                    headers: io.grpc.Metadata?,
                    next: ServerCallHandler<ReqT?, RespT?>?
                ): ServerCall.Listener<ReqT?>? {
                    println("Intercepted call to method: ${call?.methodDescriptor?.fullMethodName}")
                    return next?.startCall(call, headers)
                }
            })
            .build()
            .start()

        println("Partial server started on port $SERVER_PORT")

        // Create a channel to the server
        val channel = ManagedChannelBuilder.forAddress("localhost", SERVER_PORT)
            .usePlaintext()
            .build()

        try {
            // Create a client stub
            val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel)

            // Example 1: Unary call - GetPerson
            println("\n--- Partial Unary Call Example (GetPerson) ---")
            val getPersonRequest = GetPersonRequestKt(id = "456")
            val getPersonResponse = stub.getPerson(getPersonRequest)
            println("Received person: ${getPersonResponse.person?.name}, age: ${getPersonResponse.person?.age}")

            // Example 1.1: Unary call - DeletePerson
            println("\n--- Partial Unary Call Example (DeletePerson) ---")
            val deletePersonRequest = DeletePersonRequestKt(id = "456")
            stub.deletePerson(deletePersonRequest)
            println("Person with id ${deletePersonRequest.id} deleted successfully")

            // Example 2: Server streaming
            println("\n--- Partial Server Streaming Example ---")
            val listRequest = ListPersonsRequestKt(limit = 5, offset = 0)
            println("Requesting persons with limit=${listRequest.limit}, offset=${listRequest.offset}")

            val persons = mutableListOf<PersonKt?>()
            stub.listPersons(listRequest).collect { response ->
                persons.add(response.person)
                println("Received person: ${response.person?.name}, age: ${response.person?.age}")
            }
            println("Received ${persons.size} persons in total")

            // Example 3: Client streaming
            println("\n--- Partial Client Streaming Example ---")
            val updateRequests = flow {
                for (i in 1..3) {
                    val person = PersonKt(
                        name = "Updated Person $i",
                        age = 30 + i,
                        hobbies = listOf("Updated Hobby $i"),
                        gender = Person.Gender.UNKNOWN,
                        address = PersonKt.AddressKt(
                            street = "$i Park Ave",
                            city = "Updated City $i",
                            country = "Updated Country $i"
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
            println("\n--- Partial Bidirectional Streaming Example ---")
            val chatRequests = flow {
                for (i in 1..5) {
                    val message = "Hello $i from partial client"
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

            // Example 5: UpdateContactInfo with string oneof
            println("\n--- Update Contact Info Example (String Oneof) ---")
            val contactInfo = ContactInfoKt(
                name = "John Doe",
                contactMethod = ContactInfoKt.ContactMethod.Email(email = "john@example.com"),
                tags = listOf("customer", "premium"),
                preference = ContactInfo.ContactPreference.EMAIL_ONLY
            )
            val updateContactRequest = UpdateContactInfoRequestKt(
                personId = "person123",
                contactInfo = contactInfo
            )
            val contactResponse = stub.updateContactInfo(updateContactRequest)
            println("Contact info update successful: ${contactResponse.success}")

            // Example 6: UpdateNotificationSettings with message oneof
            println("\n--- Update Notification Settings Example (Message Oneof) ---")
            val emailSettings = NotificationSettingsKt.EmailSettingsKt(
                emailAddress = "notifications@example.com",
                dailyDigest = true,
                categories = listOf("orders", "promotions")
            )
            val notificationSettings = NotificationSettingsKt(
                userId = "user456",
                notificationChannel = NotificationSettingsKt.NotificationChannel.EmailSettings(
                    emailSettings = emailSettings
                ),
                notificationsEnabled = true
            )
            val updateNotificationRequest = UpdateNotificationSettingsRequestKt(
                userId = "user456",
                settings = notificationSettings
            )
            val notificationResponse = stub.updateNotificationSettings(updateNotificationRequest)
            println("Notification settings update successful: ${notificationResponse.success}")
            println("Response message: ${notificationResponse.message}")

            // Example 7: GetSchedule with Kotlin keyword field in response
            println("\n--- Get Schedule Example (Kotlin keyword field) ---")
            val getScheduleRequest = GetScheduleRequestKt(
                personId = "person789"
            )
            val scheduleResponse = stub.getSchedule(getScheduleRequest)
            println("Schedule response received at: ${scheduleResponse.`when`}")
            scheduleResponse.items.forEach { item ->
                println("Schedule item: ${item.id} - ${item.title}: ${item.description}")
            }

            // Example 8: TestOptionalField - Test optional field behavior
            println("\n--- Test Optional Field Example ---")

            // Test 1: Field not set
            println("Test 1: Field not set")
            val requestNoField = TestOptionalFieldRequestKt()
            val responseNoField = stub.testOptionalField(requestNoField)
            println("Has field: ${responseNoField.hasField}")

            // Test 2: Field set to empty string
            println("Test 2: Field set to empty string")
            val requestEmptyField = TestOptionalFieldRequestKt(field = "")
            val responseEmptyField = stub.testOptionalField(requestEmptyField)
            println("Has field: ${responseEmptyField.hasField}")

            // Test 3: Field set to "John"
            println("Test 3: Field set to 'John'")
            val requestWithField = TestOptionalFieldRequestKt(field = "John")
            val responseWithField = stub.testOptionalField(requestWithField)
            println("Has field: ${responseWithField.hasField}")
        } finally {
            // Shutdown the channel and server
            println("\nShutting down partial client and server")
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
