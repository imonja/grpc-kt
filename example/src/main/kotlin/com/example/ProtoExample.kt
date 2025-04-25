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

        // Example 2: gRPC service interaction
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

            // Example 1: Unary call
            println("\n--- Unary Call Example ---")
            val getPersonRequest = GetPersonRequestKt(id = "123")
            val getPersonResponse = stub.getPerson(getPersonRequest)
            println("Received person: ${getPersonResponse.person.name}, age: ${getPersonResponse.person.age}")

            // Example 2: Server streaming
            println("\n--- Server Streaming Example ---")
            val listRequest = ListPersonsRequestKt(limit = 5, offset = 0)
            println("Requesting persons with limit=${listRequest.limit}, offset=${listRequest.offset}")

            val persons = mutableListOf<PersonKt>()
            stub.listPersons(listRequest).collect { response ->
                persons.add(response.person)
                println("Received person: ${response.person.name}, age: ${response.person.age}")
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
                println("Server processing update for person: ${request.person.name}")
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
    }
}
