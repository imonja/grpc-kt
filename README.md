[![License](https://img.shields.io/github/license/imonja/grpc-kt)](LICENSE)
[![Release](https://img.shields.io/github/v/release/imonja/grpc-kt)](https://github.com/imonja/grpc-kt/releases)

# grpc-kt

grpc-kt is a protoc plugin for generating Kotlin data classes and gRPC service/stub code from Protocol Buffer (.proto) definitions. It provides a more Kotlin-idiomatic way to work with Protocol Buffers and gRPC in Kotlin projects.

## Features

- **Kotlin Data Classes**: Generates Kotlin data classes from Protocol Buffer messages with appropriate types and default values
- **gRPC Service/Stub Code**: Generates Kotlin-friendly gRPC service and client code
- **Flexible Service Implementation**: Supports both traditional (class-based) and functional interface approaches for implementing gRPC services
- **Coroutines Support**: Uses Kotlin Coroutines for asynchronous operations and streaming
- **Validation Support**: Compatible with Protocol Buffer validation rules via protoc-gen-validate
- **Documentation Generation**: Automatically generates protocol buffer documentation
- **Comprehensive Type Mapping**: Properly maps Protocol Buffer types to Kotlin types
- **Gradle Plugin**: Simplifies project setup and configuration
- **Support for Protocol Buffer Features**:
    - Oneofs (mapped to sealed interfaces)
    - Maps
    - Repeated fields
    - Optional fields
    - Nested types
    - Enums

## Project Structure

grpc-kt consists of four main modules:

- **common**: Provides runtime support for the generated code
- **example**: Contains example proto files and usage demonstrations
- **generator**: The main code generation module (the protoc plugin)
- **gradle-plugin**: A Gradle plugin that simplifies protobuf configuration

## Installation

### Gradle

> **Note**: Artifacts are published to both GitHub Packages and Maven Central.
> For GitHub Packages, you'll need to add the repository configuration.
> Maven Central is the recommended source for public consumption.

#### Option 1: Using the Gradle Plugin (Recommended)

The easiest way to use grpc-kt is with the Gradle plugin, which automatically configures all dependencies and protobuf generation:

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    id("io.github.imonja.grpc-kt-protobuf") version "1.1.0"
}

// The plugin automatically adds all necessary dependencies including grpc-kt-common
```

Place your `.proto` files in `proto/` directory in your project root (or `src/main/proto`).

#### Option 2: Manual Configuration

If you prefer manual configuration, add the following to your `build.gradle.kts` file:

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    id("com.google.protobuf") version "0.9.5"
}

dependencies {
    // grpc-kt runtime
    implementation("io.github.imonja:grpc-kt-common:1.1.0")

    // Other gRPC dependencies
    implementation("io.grpc:grpc-stub:1.71.0")
    implementation("io.grpc:grpc-protobuf:1.71.0")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("com.google.protobuf:protobuf-kotlin:4.30.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.30.2"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.71.0"
        }
        id("grpc-kt") {
            artifact = "io.github.imonja:protoc-gen-grpc-kt:1.1.0:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc") {
                    outputSubDir = "java"
                }
                id("grpc-kt") {
                    outputSubDir = "kotlin"
                }
            }
        }
    }
}
```

## Usage

### Define Protocol Buffer Messages

Create your `.proto` files as usual:

```protobuf
syntax = "proto3";

package example;

option java_package = "com.example.proto";
option java_multiple_files = true;

message Person {
    string name = 1;
    int32 age = 2;
    repeated string hobbies = 3;

    enum Gender {
        UNKNOWN = 0;
        MALE = 1;
        FEMALE = 2;
        NON_BINARY = 3;
    }

    Gender gender = 4;

    message Address {
        string street = 1;
        string city = 2;
        string country = 3;
    }

    Address address = 5;
}
```

### Define gRPC Services

```protobuf
syntax = "proto3";

package example;

option java_package = "com.example.proto";
option java_multiple_files = true;

import "model.proto";

service PersonService {
    rpc GetPerson(GetPersonRequest) returns (GetPersonResponse) {}
    rpc ListPersons(ListPersonsRequest) returns (stream ListPersonsResponse) {}
    rpc UpdatePerson(stream UpdatePersonRequest) returns (UpdatePersonResponse) {}
    rpc ChatWithPerson(stream ChatRequest) returns (stream ChatResponse) {}
}

message GetPersonRequest {
    string id = 1;
}

message GetPersonResponse {
    Person person = 1;
}

message ListPersonsRequest {
    int32 limit = 1;
    int32 offset = 2;
}

message ListPersonsResponse {
    Person person = 1;
}

message UpdatePersonRequest {
    Person person = 1;
}

message UpdatePersonResponse {
    bool success = 1;
}

message ChatRequest {
    string message = 1;
}

message ChatResponse {
    string message = 1;
}
```

### Use Generated Kotlin Code

After running the Gradle build, grpc-kt will generate Kotlin data classes and gRPC service/stub code:

```kotlin
val person = PersonKt(
    name = "John Doe",
    age = 30,
    hobbies = listOf("Reading", "Coding"),
    gender = Person.Gender.MALE,
    address = PersonKt.AddressKt(
        street = "123 Main St",
        city = "Anytown",
        country = "USA"
    )
)

val javaProto = person.toJavaProto()
val kotlinProto = javaProto.toKotlinProto()

val channel = ManagedChannelBuilder.forAddress("localhost", 8080)
    .usePlaintext()
    .build()

val client = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel)

val response = client.getPerson(GetPersonRequestKt(id = "123"))

client.listPersons(ListPersonsRequestKt(limit = 10, offset = 0))
    .collect { response ->
        println("Received person: ${response.person.name}")
    }

val updateResponse = client.updatePerson(
    flow {
        emit(UpdatePersonRequestKt(person = person))
        emit(UpdatePersonRequestKt(person = person.copy(age = 31)))
    }
)

client.chatWithPerson(
    flow {
        emit(ChatRequestKt(message = "Hello"))
        emit(ChatRequestKt(message = "How are you?"))
    }
).collect { response ->
    println("Received message: ${response.message}")
}
```

### Implementing a gRPC Service

#### Traditional Approach

The traditional approach involves extending the generated base class:

```kotlin
class PersonServiceImpl : PersonServiceGrpcKt.PersonServiceCoroutineImplBase() {
    override suspend fun getPerson(request: GetPersonRequestKt): GetPersonResponseKt {
        return GetPersonResponseKt(
            person = PersonKt(name = "John Doe", age = 30)
        )
    }

    override fun listPersons(request: ListPersonsRequestKt): Flow<ListPersonsResponseKt> {
        return flow {
            repeat(request.limit) {
                emit(ListPersonsResponseKt(
                    person = PersonKt(name = "Person $it", age = 20 + it)
                ))
            }
        }
    }

    override suspend fun updatePerson(requests: Flow<UpdatePersonRequestKt>): UpdatePersonResponseKt {
        requests.collect { request ->
            println("Updating person: ${request.person.name}")
        }
        return UpdatePersonResponseKt(success = true)
    }

    override fun chatWithPerson(requests: Flow<ChatRequestKt>): Flow<ChatResponseKt> {
        return requests.map { request ->
            ChatResponseKt(message = "Echo: ${request.message}")
        }
    }
}
```

#### Alternative Approach with Functional Interfaces

grpc-kt also provides an alternative way to implement gRPC services using functional interfaces, which allows for more modular and flexible service implementation:

```kotlin
// Define the service implementation functions
val getPerson: PersonServiceGrpcAlternateKt.GetPersonGrpcMethod =
    PersonServiceGrpcAlternateKt.GetPersonGrpcMethod { request ->
        println("Received getPerson request for id: ${request.id}")
        GetPersonResponseKt(
            person = PersonKt(name = "John Doe", age = 30)
        ).toJavaProto()
    }

val listPersons: PersonServiceGrpcAlternateKt.ListPersonsGrpcMethod =
    PersonServiceGrpcAlternateKt.ListPersonsGrpcMethod { request ->
        println("Received listPersons request with limit: ${request.limit}")
        flow {
            repeat(request.limit) {
                emit(ListPersonsResponseKt(
                    person = PersonKt(name = "Person $it", age = 20 + it)
                ).toJavaProto())
                delay(100) // Simulate some processing time
            }
        }
    }

val updatePerson: PersonServiceGrpcAlternateKt.UpdatePersonGrpcMethod =
    PersonServiceGrpcAlternateKt.UpdatePersonGrpcMethod { requests ->
        requests.collect { request ->
            println("Updating person: ${request.person?.name}")
        }
        UpdatePersonResponseKt(success = true).toJavaProto()
    }

val chatWithPerson: PersonServiceGrpcAlternateKt.ChatWithPersonGrpcMethod =
    PersonServiceGrpcAlternateKt.ChatWithPersonGrpcMethod { requests ->
        flow {
            requests.map { request ->
                ChatResponseKt(message = "Echo: ${request.message}").toJavaProto()
            }.collect { response ->
                emit(response)
                delay(100) // Simulate some processing time
            }
        }
    }

// Create the service using the AlternateServerBuilder
val alternateService = PersonServiceGrpcAlternateKt.PersonServiceCoroutineImplAlternate(
    getPerson = getPerson,
    listPersons = listPersons,
    updatePerson = updatePerson,
    chatWithPerson = chatWithPerson
)

// Start the gRPC server with the alternate service
val server = ServerBuilder.forPort(8080)
    .addService(alternateService)
    .build()
    .start()
```

This approach has several advantages:
- Each method can be implemented separately, making the code more modular
- Methods can be easily swapped or mocked for testing
- Implementation can be provided as lambda functions or method references
- No need to create a class that extends a base class

## Gradle Plugin Configuration

When using the Gradle plugin, you can customize the configuration:

```kotlin
grpcKtProtobuf {
    sourceDir {
        protoSourceDir.set("custom/proto/dir")  // Default: "${project.projectDir}/proto"
    }
    
    generateSource {
        grpcJavaOutputSubDir.set("java")        // Default: "java"
        grpcKtOutputSubDir.set("kotlin")        // Default: "kotlin"
        javaPgvOutputSubDir.set("java-pgv")     // Default: "java-pgv"
        javaPgvLang.set("java")                 // Default: "java"
    }
    
    docs {
        grpcDocsFormat.set("markdown")          // Default: "markdown"
        grpcDocsFileName.set("api-docs.md")     // Default: "grpc-docs.md"
        grpcDocsOutputSubDir.set("docs")        // Default: "grpc-docs"
    }
}
```

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/imonja/grpc-kt.git
   ```

2. Build the project:
   ```bash
   ./gradlew build
   ```

3. Install to local Maven repository:
   ```bash
   ./gradlew publishToMavenLocal
   ```

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for full terms.

It includes original work from [krotoDC](https://github.com/mscheong01/krotoDC),  
with substantial modifications by [@ym](https://github.com/ym).

## Acknowledgements

This project is based on [krotoDC](https://github.com/mscheong01/krotoDC),  
originally developed by [@mscheong01](https://github.com/mscheong01) and licensed under the Apache License, Version 2.0.

Significant modifications and new features have been introduced by [@imonja](https://github.com/imonja),  
including enhancements for coroutine handling, Kotlin idioms, and broader protobuf feature support.

## Contributing

Contributions are welcome!  
Please see the [CONTRIBUTING.md](CONTRIBUTING.md) guide before submitting a Pull Request.
