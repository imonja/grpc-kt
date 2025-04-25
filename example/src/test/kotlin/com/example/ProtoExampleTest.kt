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
import java.util.concurrent.TimeUnit

/**
 * This test class demonstrates how to use the generated Protocol Buffer code.
 */
class ProtoExampleTest {

    private val serverPort = 50051
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
            gender = Person.Gender.MALE,
            address = PersonKt.AddressKt(
                street = "123 Main St",
                city = "San Francisco",
                country = "USA"
            )
        )

        // Convert to Java protobuf message
        val javaProto = person.toJavaProto()

        // Serialize to binary format
        val bytes = javaProto.toByteArray()
        println("Serialized size: ${bytes.size} bytes")

        // Deserialize from binary format
        val deserializedJavaProto = Person.parseFrom(bytes)
        val deserializedPerson = deserializedJavaProto.toKotlinProto()

        // Verify the deserialized object matches the original
        assert(person == deserializedPerson) { "Deserialized person should match the original" }

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
        assert(person == fromJson) { "Person parsed from JSON should match the original" }
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
        assert(response.person.name == "John Doe") { "Expected name to be 'John Doe'" }
        assert(response.person.age == 30) { "Expected age to be 30" }
    }

    @Test
    fun `test gRPC server streaming`() = runBlocking {
        // Create a client stub
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)

        // Create a request
        val request = ListPersonsRequestKt(limit = 5, offset = 0)

        // Make the call and collect responses
        val responses = mutableListOf<PersonKt>()
        stub.listPersons(request).collect { response ->
            responses.add(response.person)
            println("Received person: ${response.person.name}")
        }

        // Verify we received the expected number of responses
        assert(responses.size == 3) { "Expected to receive 3 persons" }
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
                    gender = Person.Gender.UNKNOWN,
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
                gender = Person.Gender.MALE,
                address = PersonKt.AddressKt(
                    street = "123 Main St",
                    city = "San Francisco",
                    country = "USA"
                )
            )
            return GetPersonResponseKt(person = person)
        }

        override fun listPersons(request: ListPersonsRequestKt): Flow<ListPersonsResponseKt> = flow {
            // Simulate fetching a list of persons
            val persons = listOf(
                PersonKt(name = "Alice", age = 25, gender = Person.Gender.FEMALE),
                PersonKt(name = "Bob", age = 30, gender = Person.Gender.MALE),
                PersonKt(name = "Charlie", age = 35, gender = Person.Gender.NON_BINARY)
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
                println("Updating person: ${request.person.name}")
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
    }
}
