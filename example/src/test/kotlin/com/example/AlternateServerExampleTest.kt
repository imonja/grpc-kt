package com.example

import com.example.proto.*
import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * This test class demonstrates how to use the AlternateServerBuilder approach for gRPC services.
 *
 * Note: The tests that make actual gRPC calls are currently failing with UNKNOWN errors.
 * This is because the AlternateServerBuilder does not properly handle the conversion
 * between Kotlin and Java types in the bind method. As a workaround, you can use the regular server implementation
 * (PersonServiceGrpcKt.PersonServiceCoroutineImplBase) instead of the alternate server implementation
 * (PersonServiceGrpcKt.PersonServiceCoroutineImplAlternate.PersonServiceGrpcService).
 *
 * Example:
 * ```
 * // Create the service using the regular server implementation
 * val alternateService = object : PersonServiceGrpcKt.PersonServiceCoroutineImplBase() {
 *     override suspend fun getPerson(request: GetPersonRequestKt): GetPersonResponseKt {
 *         // Implementation
 *     }
 *
 *     // Other methods
 * }
 * ```
 *
 * The tests that don't make gRPC calls (structure tests, service creation tests, and server/client setup tests)
 * are passing successfully.
 */
class AlternateServerExampleTest {

    private val serverPort = 50053
    private var server: io.grpc.Server? = null
    private var channel: io.grpc.ManagedChannel? = null

    @BeforeEach
    fun setup() {
        // Define the service implementation functions
        val getPerson: PersonServiceGrpcKt.GetPersonGrpcMethod =
            PersonServiceGrpcKt.GetPersonGrpcMethod { request ->
                println("Test server received getPerson request for id: ${request.id}")

                // Return a test person
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
                GetPersonResponseKt(person = person).toJavaProto()
            }

        val listPersons: PersonServiceGrpcKt.ListPersonsGrpcMethod =
            PersonServiceGrpcKt.ListPersonsGrpcMethod { request ->
                println("Test server received listPersons request with limit: ${request.limit}, offset: ${request.offset}")

                // Return test persons
                val persons = listOf(
                    PersonKt(name = "David", age = 40, gender = Person.Gender.MALE),
                    PersonKt(name = "Emma", age = 22, gender = Person.Gender.FEMALE),
                    PersonKt(name = "Sam", age = 33, gender = Person.Gender.NON_BINARY)
                )

                flow {
                    persons.forEach { person ->
                        emit(ListPersonsResponseKt(person = person).toJavaProto())
                        delay(100) // Simulate some processing time
                    }
                }
            }

        val updatePerson: PersonServiceGrpcKt.UpdatePersonGrpcMethod =
            PersonServiceGrpcKt.UpdatePersonGrpcMethod { requests ->
                println("Test server received updatePerson request stream")

                // Process each update request
                requests.collect { request ->
                    println("Test server processing update for person: ${request.person?.name}")
                }
                UpdatePersonResponseKt(success = true).toJavaProto()
            }

        val chatWithPerson: PersonServiceGrpcKt.ChatWithPersonGrpcMethod =
            PersonServiceGrpcKt.ChatWithPersonGrpcMethod { requests ->
                println("Test server received chatWithPerson request stream")

                // Echo back each message with a prefix
                requests.map { request ->
                    println("Test server received chat message: ${request.message}")
                    ChatResponseKt(message = "Test server received: ${request.message}").toJavaProto()
                }
            }

        // Create the service using the AlternateServerBuilder
        val alternateService = PersonServiceGrpcKt.PersonServiceCoroutineImplAlternate.PersonServiceGrpcService(
            getPerson = getPerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson
        )

        // Start the gRPC server with the alternate service
        server = ServerBuilder.forPort(serverPort)
            .addService(alternateService)
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
    fun `test alternate server unary call`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a request
        val request = GetPersonRequestKt(id = "456")

        // Make the call
        val response = stub.getPerson(request)

        // Verify the response
        assert(response.person?.name == "Jane Doe") { "Expected name to be 'Jane Doe'" }
        assert(response.person?.age == 28) { "Expected age to be 28" }
        assert(response.person?.gender == Person.Gender.FEMALE) { "Expected gender to be FEMALE" }
        assert(response.person?.address?.city == "New York") { "Expected city to be 'New York'" }
    }

    @Test
    fun `test alternate server streaming`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a request
        val request = ListPersonsRequestKt(limit = 5, offset = 0)

        // Make the call and collect responses
        val responses = mutableListOf<PersonKt?>()
        stub.listPersons(request).collect { response ->
            responses.add(response.person)
            println("Received person: ${response.person?.name}")
        }

        // Verify we received the expected number of responses
        assert(responses.size == 3) { "Expected to receive 3 persons" }

        // Verify the content of the responses
        assert(responses[0]?.name == "David") { "Expected first person to be 'David'" }
        assert(responses[1]?.name == "Emma") { "Expected second person to be 'Emma'" }
        assert(responses[2]?.name == "Sam") { "Expected third person to be 'Sam'" }
    }

    @Test
    fun `test alternate client streaming`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a flow of requests
        val requests = flow {
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

        // Make the call
        val response = stub.updatePerson(requests)

        // Verify the response
        assert(response.success) { "Expected update to be successful" }
    }

    @Test
    fun `test alternate bidirectional streaming`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a flow of chat requests
        val requests = flow {
            for (i in 1..5) {
                val message = "Hello $i from test client"
                println("Sending: $message")
                emit(ChatRequestKt(message = message))
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

        // Verify the content of the responses
        for (i in 1..5) {
            assert(responses[i - 1].contains("Hello $i from test client")) {
                "Expected response $i to contain 'Hello $i from test client'"
            }
        }
    }

    @Test
    fun `test structure of AlternateServerExample`() {
        // This test verifies the structure of the AlternateServerExample without making gRPC calls

        // Verify that the AlternateServerExample object exists
        assert(AlternateServerExample != null) { "AlternateServerExample should exist" }

        // Verify that the demonstrateAlternateGrpcService method exists
        val method = AlternateServerExample::class.java.getDeclaredMethod("demonstrateAlternateGrpcService")
        assert(method != null) { "demonstrateAlternateGrpcService method should exist" }

        // Verify that the SERVER_PORT constant exists and has the expected value
        val serverPortField = AlternateServerExample::class.java.getDeclaredField("SERVER_PORT")
        serverPortField.isAccessible = true
        val serverPort = serverPortField.get(AlternateServerExample) as Int
        assert(serverPort == 50052) { "SERVER_PORT should be 50052" }
    }

    @Test
    fun `test creation of alternate service`() {
        // This test verifies that we can create the alternate service without making gRPC calls

        // Define the service implementation functions
        val getPerson: PersonServiceGrpcKt.GetPersonGrpcMethod =
            PersonServiceGrpcKt.GetPersonGrpcMethod { request ->
                GetPersonResponseKt(person = PersonKt(name = "Test Person", age = 30)).toJavaProto()
            }

        val listPersons: PersonServiceGrpcKt.ListPersonsGrpcMethod =
            PersonServiceGrpcKt.ListPersonsGrpcMethod { request ->
                flow {
                    emit(ListPersonsResponseKt(person = PersonKt(name = "Test Person", age = 30)).toJavaProto())
                }
            }

        val updatePerson: PersonServiceGrpcKt.UpdatePersonGrpcMethod =
            PersonServiceGrpcKt.UpdatePersonGrpcMethod { requests ->
                UpdatePersonResponseKt(success = true).toJavaProto()
            }

        val chatWithPerson: PersonServiceGrpcKt.ChatWithPersonGrpcMethod =
            PersonServiceGrpcKt.ChatWithPersonGrpcMethod { requests ->
                requests.map { request ->
                    ChatResponseKt(message = "Test response").toJavaProto()
                }
            }

        // Create the service using the AlternateServerBuilder
        val alternateService = PersonServiceGrpcKt.PersonServiceCoroutineImplAlternate.PersonServiceGrpcService(
            getPerson = getPerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson
        )

        // Verify that the service was created successfully
        assert(alternateService != null) { "Alternate service should be created successfully" }
    }

    @Test
    fun `test server and client setup without making calls`() {
        // This test verifies that we can set up the gRPC server and client without making gRPC calls

        // Define the service implementation functions
        val getPerson: PersonServiceGrpcKt.GetPersonGrpcMethod =
            PersonServiceGrpcKt.GetPersonGrpcMethod { request ->
                GetPersonResponseKt(person = PersonKt(name = "Test Person", age = 30)).toJavaProto()
            }

        val listPersons: PersonServiceGrpcKt.ListPersonsGrpcMethod =
            PersonServiceGrpcKt.ListPersonsGrpcMethod { request ->
                flow {
                    emit(ListPersonsResponseKt(person = PersonKt(name = "Test Person", age = 30)).toJavaProto())
                }
            }

        val updatePerson: PersonServiceGrpcKt.UpdatePersonGrpcMethod =
            PersonServiceGrpcKt.UpdatePersonGrpcMethod { requests ->
                UpdatePersonResponseKt(success = true).toJavaProto()
            }

        val chatWithPerson: PersonServiceGrpcKt.ChatWithPersonGrpcMethod =
            PersonServiceGrpcKt.ChatWithPersonGrpcMethod { requests ->
                requests.map { request ->
                    ChatResponseKt(message = "Test response").toJavaProto()
                }
            }

        // Create the service using the AlternateServerBuilder
        val alternateService = PersonServiceGrpcKt.PersonServiceCoroutineImplAlternate.PersonServiceGrpcService(
            getPerson = getPerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson
        )

        // Start the gRPC server with the alternate service
        val testPort = 50054
        val server = ServerBuilder.forPort(testPort)
            .addService(alternateService)
            .build()
            .start()

        try {
            // Create a channel to the server
            val channel = ManagedChannelBuilder.forAddress("localhost", testPort)
                .usePlaintext()
                .build()

            try {
                // Create a client stub
                val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel)

                // Verify that the stub was created successfully
                assert(stub != null) { "Client stub should be created successfully" }

                // We don't actually make any gRPC calls here
            } finally {
                // Shutdown the channel
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            }
        } finally {
            // Shutdown the server
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `test simple unary call`() = runBlocking {
        // This test makes a very simple unary gRPC call to see if that works

        // Define the service implementation functions - only implement getPerson for this test
        val getPerson: PersonServiceGrpcKt.GetPersonGrpcMethod =
            PersonServiceGrpcKt.GetPersonGrpcMethod { request ->
                println("[DEBUG_LOG] Server received getPerson request for id: ${request.id}")
                GetPersonResponseKt(person = PersonKt(name = "Simple Test Person", age = 25)).toJavaProto()
            }

        // Create empty implementations for the other methods
        val listPersons: PersonServiceGrpcKt.ListPersonsGrpcMethod =
            PersonServiceGrpcKt.ListPersonsGrpcMethod { request ->
                flow { }
            }

        val updatePerson: PersonServiceGrpcKt.UpdatePersonGrpcMethod =
            PersonServiceGrpcKt.UpdatePersonGrpcMethod { requests ->
                UpdatePersonResponseKt(success = true).toJavaProto()
            }

        val chatWithPerson: PersonServiceGrpcKt.ChatWithPersonGrpcMethod =
            PersonServiceGrpcKt.ChatWithPersonGrpcMethod { requests ->
                flow { }
            }

        // Create the service using the AlternateServerBuilder
        val alternateService = PersonServiceGrpcKt.PersonServiceCoroutineImplAlternate.PersonServiceGrpcService(
            getPerson = getPerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson
        )

        // Start the gRPC server with the alternate service
        val testPort = 50055
        val server = ServerBuilder.forPort(testPort)
            .addService(alternateService)
            .build()
            .start()

        println("[DEBUG_LOG] Server started on port $testPort")

        try {
            // Create a channel to the server
            val channel = ManagedChannelBuilder.forAddress("localhost", testPort)
                .usePlaintext()
                .build()

            try {
                // Create a client stub
                val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel)

                // Make a simple unary call
                println("[DEBUG_LOG] Making unary call")
                val request = GetPersonRequestKt(id = "simple-test")
                val response = stub.getPerson(request)

                // Verify the response
                println("[DEBUG_LOG] Received response: ${response.person?.name}, age: ${response.person?.age}")
                assert(response.person?.name == "Simple Test Person") { "Expected name to be 'Simple Test Person'" }
                assert(response.person?.age == 25) { "Expected age to be 25" }
            } finally {
                // Shutdown the channel
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            }
        } finally {
            // Shutdown the server
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `test direct use of AlternateServerExample`() {
        // This test directly uses the AlternateServerExample.demonstrateAlternateGrpcService method
        // to see if our changes fixed the issue

        // We're just checking that the method doesn't throw an exception
        // We don't need to verify any specific behavior
        try {
            println("[DEBUG_LOG] Starting direct use of AlternateServerExample")
            AlternateServerExample.demonstrateAlternateGrpcService()
            println("[DEBUG_LOG] Completed direct use of AlternateServerExample")
            // If we get here, the test passed
            assert(true)
        } catch (e: Exception) {
            // If we get an exception, the test failed
            println("[DEBUG_LOG] Exception during direct use of AlternateServerExample: ${e.message}")
            e.printStackTrace()
            assert(false) { "Exception during direct use of AlternateServerExample: ${e.message}" }
        }
    }
}
