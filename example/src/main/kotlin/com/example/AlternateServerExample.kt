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

    private const val SERVER_PORT = 50052

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

        // Create the service using the PartialServerBuilder
        val partialService = PersonServiceGrpcPartialKt.PersonServiceCoroutineImplPartial(
            getPerson = getPerson,
            deletePerson = deletePerson,
            listPersons = listPersons,
            updatePerson = updatePerson,
            chatWithPerson = chatWithPerson
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
        } finally {
            // Shutdown the channel and server
            println("\nShutting down partial client and server")
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
