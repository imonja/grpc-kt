# Protocol Buffers and gRPC Example

This example demonstrates how to use the generated Protocol Buffer code and gRPC services in Kotlin.

## Overview

The example shows:

1. **Creating and manipulating protobuf messages** using Kotlin data classes
2. **Serializing and deserializing** protobuf messages to binary format
3. **Converting protobuf messages to and from JSON**
4. **Implementing a gRPC service** with all four types of RPC methods:
   - Unary (getPerson)
   - Server streaming (listPersons)
   - Client streaming (updatePerson)
   - Bidirectional streaming (chatWithPerson)
5. **Creating a gRPC client** to interact with the service

## Project Structure

- `proto/` - Contains the Protocol Buffer definition files
  - `model.proto` - Defines the data models (Person, Address)
  - `service.proto` - Defines the gRPC service (PersonService)
- `src/main/kotlin/com/example/` - Contains the example code
  - `ProtoExample.kt` - Main class with examples of using the generated code

## Generated Code

The Protocol Buffer compiler generates several types of code:

1. **Java classes** - Standard Protocol Buffer classes for Java
2. **Kotlin data classes** - Kotlin-friendly wrappers around the Java classes
3. **gRPC service interfaces** - For implementing servers and clients

The Kotlin data classes provide a more idiomatic way to work with Protocol Buffers in Kotlin, with features like:
- Data classes with default parameters
- Extension functions for conversion between Java and Kotlin types
- Coroutine-based APIs for gRPC services

## Running the Example

To run the example:

```bash
./gradlew :example:run
```

This will execute the `ProtoExample` main class, which demonstrates:
- Creating and serializing a Person message
- Setting up a gRPC server and client
- Performing various types of gRPC calls

## Key Concepts

### Creating Protocol Buffer Messages

```kotlin
// Create using Kotlin data class
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

// Convert to Java protobuf message
val javaProto = person.toJavaProto()
```

### Serializing and Deserializing

```kotlin
// Serialize to binary
val bytes = javaProto.toByteArray()

// Deserialize from binary
val deserializedJavaProto = Person.parseFrom(bytes)
val deserializedPerson = deserializedJavaProto.toKotlinProto()
```

### JSON Conversion

```kotlin
// Convert to JSON
val jsonPrinter = JsonFormat.printer().includingDefaultValueFields()
val json = jsonPrinter.print(javaProto)

// Parse from JSON
val jsonParser = JsonFormat.parser()
val builder = Person.newBuilder()
jsonParser.merge(json, builder)
val fromJson = builder.build().toKotlinProto()
```

### Implementing a gRPC Service

```kotlin
class PersonServiceImpl : PersonServiceGrpcKt.PersonServiceCoroutineImplBase() {
    override suspend fun getPerson(request: GetPersonRequestKt): GetPersonResponseKt {
        // Implementation
    }
    
    override fun listPersons(request: ListPersonsRequestKt): Flow<ListPersonsResponseKt> = flow {
        // Implementation
    }
    
    // Other methods...
}
```

### Creating a gRPC Client

```kotlin
val channel = ManagedChannelBuilder.forAddress("localhost", SERVER_PORT)
    .usePlaintext()
    .build()
    
val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel)

// Make unary call
val response = stub.getPerson(GetPersonRequestKt(id = "123"))

// Make streaming call
stub.listPersons(ListPersonsRequestKt(limit = 5, offset = 0)).collect { response ->
    // Process each response
}
```
