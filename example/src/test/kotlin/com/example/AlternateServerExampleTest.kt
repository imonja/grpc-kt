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

class PartialServerExampleTest {

    private val serverPort = (60_000..65_000).random()
    private var server: io.grpc.Server? = null
    private var channel: io.grpc.ManagedChannel? = null

    @BeforeEach
    fun setup() {
        // Define the service implementation functions
        val getPerson: PersonServiceGrpcPartialKt.GetPersonGrpcMethod =
            PersonServiceGrpcPartialKt.GetPersonGrpcMethod { request ->
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
                GetPersonResponseKt(person = person)
            }

        val deletePerson: PersonServiceGrpcPartialKt.DeletePersonGrpcMethod =
            PersonServiceGrpcPartialKt.DeletePersonGrpcMethod { request ->
                println("Test server received deletePerson request for id: ${request.id}")
            }

        val listPersons: PersonServiceGrpcPartialKt.ListPersonsGrpcMethod =
            PersonServiceGrpcPartialKt.ListPersonsGrpcMethod { request ->
                println("Test server received listPersons request with limit: ${request.limit}, offset: ${request.offset}")

                // Return test persons
                val persons = listOf(
                    PersonKt(name = "David", age = 40, gender = Person.Gender.MALE),
                    PersonKt(name = "Emma", age = 22, gender = Person.Gender.FEMALE),
                    PersonKt(name = "Sam", age = 33, gender = Person.Gender.NON_BINARY)
                )

                flow {
                    persons.forEach { person ->
                        emit(ListPersonsResponseKt(person = person))
                        delay(100) // Simulate some processing time
                    }
                }
            }

        val updatePerson: PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod =
            PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod { requests ->
                println("Test server received updatePerson request stream")

                // Process each update request
                requests.collect { request ->
                    println("Test server processing update for person: ${request.person?.name}")
                }
                UpdatePersonResponseKt(success = true)
            }

        val chatWithPerson: PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod =
            PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod { requests ->
                println("Test server received chatWithPerson request stream")

                // Echo back each message with a prefix
                requests.map { request ->
                    println("Test server received chat message: ${request.message}")
                    ChatResponseKt(message = "Test server received: ${request.message}")
                }
            }

        // Create the service using the PartialServerBuilder
        val partialService = PersonServiceGrpcPartialKt.PersonServiceCoroutineImplPartial(
            getPerson = getPerson,
            deletePerson = deletePerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson
        )

        // Start the gRPC server with the partial service
        server = ServerBuilder.forPort(serverPort)
            .addService(partialService)
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
    fun `test partial server unary call`() = runBlocking {
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
    fun `test partial server delete person`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a request
        val request = DeletePersonRequestKt(id = "456")

        // Make the call
        stub.deletePerson(request)

        // No response to verify since it returns Unit, but we can assert that no exception was thrown
        // If we reach this point, the test passes
    }

    @Test
    fun `test partial server streaming`() = runBlocking {
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
    fun `test partial client streaming`() = runBlocking {
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
    fun `test partial bidirectional streaming`() = runBlocking {
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
    fun `test structure of PartialServerExample`() {
        // This test verifies the structure of the PartialServerExample without making gRPC calls

        // Verify that the PartialServerExample object exists
        assert(PartialServerExample != null) { "PartialServerExample should exist" }

        // Verify that the demonstratePartialGrpcService method exists
        val method = PartialServerExample::class.java.getDeclaredMethod("demonstratePartialGrpcService")
        assert(method != null) { "demonstratePartialGrpcService method should exist" }

        // Verify that the SERVER_PORT constant exists and is in valid range
        val serverPortField = PartialServerExample::class.java.getDeclaredField("SERVER_PORT")
        serverPortField.isAccessible = true
        val serverPort = serverPortField.get(PartialServerExample) as Int
        assert(serverPort in 60000..65000) { "SERVER_PORT should be in range 60000-65000, but was $serverPort" }
    }

    @Test
    fun `test creation of partial service`() {
        // This test verifies that we can create the partial service without making gRPC calls

        // Define the service implementation functions
        val getPerson: PersonServiceGrpcPartialKt.GetPersonGrpcMethod =
            PersonServiceGrpcPartialKt.GetPersonGrpcMethod { request ->
                GetPersonResponseKt(person = PersonKt(name = "Test Person", age = 30))
            }

        val deletePerson: PersonServiceGrpcPartialKt.DeletePersonGrpcMethod =
            PersonServiceGrpcPartialKt.DeletePersonGrpcMethod { request ->
                com.google.protobuf.Empty.getDefaultInstance()
            }

        val listPersons: PersonServiceGrpcPartialKt.ListPersonsGrpcMethod =
            PersonServiceGrpcPartialKt.ListPersonsGrpcMethod { request ->
                flow {
                    emit(ListPersonsResponseKt(person = PersonKt(name = "Test Person", age = 30)))
                }
            }

        val updatePerson: PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod =
            PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod { requests ->
                UpdatePersonResponseKt(success = true)
            }

        val chatWithPerson: PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod =
            PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod { requests ->
                requests.map { request ->
                    ChatResponseKt(message = "Test response")
                }
            }

        // Create the service using the PartialServerBuilder
        val partialService = PersonServiceGrpcPartialKt.PersonServiceCoroutineImplPartial(
            getPerson = getPerson,
            deletePerson = deletePerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson
        )

        // Verify that the service was created successfully
        assert(partialService != null) { "Partial service should be created successfully" }
    }

    @Test
    fun `test server and client setup without making calls`() {
        // This test verifies that we can set up the gRPC server and client without making gRPC calls

        // Define the service implementation functions
        val getPerson: PersonServiceGrpcPartialKt.GetPersonGrpcMethod =
            PersonServiceGrpcPartialKt.GetPersonGrpcMethod { request ->
                GetPersonResponseKt(person = PersonKt(name = "Test Person", age = 30))
            }

        val deletePerson: PersonServiceGrpcPartialKt.DeletePersonGrpcMethod =
            PersonServiceGrpcPartialKt.DeletePersonGrpcMethod { request ->
                com.google.protobuf.Empty.getDefaultInstance()
            }

        val listPersons: PersonServiceGrpcPartialKt.ListPersonsGrpcMethod =
            PersonServiceGrpcPartialKt.ListPersonsGrpcMethod { request ->
                flow {
                    emit(ListPersonsResponseKt(person = PersonKt(name = "Test Person", age = 30)))
                }
            }

        val updatePerson: PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod =
            PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod { requests ->
                UpdatePersonResponseKt(success = true)
            }

        val chatWithPerson: PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod =
            PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod { requests ->
                requests.map { request ->
                    ChatResponseKt(message = "Test response")
                }
            }

        // Create the service using the PartialServerBuilder
        val partialService = PersonServiceGrpcPartialKt.PersonServiceCoroutineImplPartial(
            getPerson = getPerson,
            deletePerson = deletePerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson
        )

        // Start the gRPC server with the partial service
        val testPort = 50054
        val server = ServerBuilder.forPort(testPort)
            .addService(partialService)
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
        val getPerson: PersonServiceGrpcPartialKt.GetPersonGrpcMethod =
            PersonServiceGrpcPartialKt.GetPersonGrpcMethod { request ->
                println("[DEBUG_LOG] Server received getPerson request for id: ${request.id}")
                GetPersonResponseKt(person = PersonKt(name = "Simple Test Person", age = 25))
            }

        // Create empty implementations for the other methods
        val deletePerson: PersonServiceGrpcPartialKt.DeletePersonGrpcMethod =
            PersonServiceGrpcPartialKt.DeletePersonGrpcMethod { request ->
                com.google.protobuf.Empty.getDefaultInstance()
            }

        val listPersons: PersonServiceGrpcPartialKt.ListPersonsGrpcMethod =
            PersonServiceGrpcPartialKt.ListPersonsGrpcMethod { request ->
                flow { }
            }

        val updatePerson: PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod =
            PersonServiceGrpcPartialKt.UpdatePersonGrpcMethod { requests ->
                UpdatePersonResponseKt(success = true)
            }

        val chatWithPerson: PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod =
            PersonServiceGrpcPartialKt.ChatWithPersonGrpcMethod { requests ->
                flow { }
            }

        // Create the service using the PartialServerBuilder
        val partialService = PersonServiceGrpcPartialKt.PersonServiceCoroutineImplPartial(
            getPerson = getPerson,
            deletePerson = deletePerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson
        )

        // Start the gRPC server with the partial service
        val testPort = 50055
        val server = ServerBuilder.forPort(testPort)
            .addService(partialService)
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
    fun `test direct use of PartialServerExample`() {
        // This test directly uses the PartialServerExample.demonstratePartialGrpcService method
        // to see if our changes fixed the issue

        // We're just checking that the method doesn't throw an exception
        // We don't need to verify any specific behavior
        try {
            println("[DEBUG_LOG] Starting direct use of PartialServerExample")
            PartialServerExample.demonstratePartialGrpcService()
            println("[DEBUG_LOG] Completed direct use of PartialServerExample")
            // If we get here, the test passed
            assert(true)
        } catch (e: Exception) {
            // If we get an exception, the test failed
            println("[DEBUG_LOG] Exception during direct use of PartialServerExample: ${e.message}")
            e.printStackTrace()
            assert(false) { "Exception during direct use of PartialServerExample: ${e.message}" }
        }
    }
}
