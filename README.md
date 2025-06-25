# grpc-kt

grpc-kt is a protoc plugin for generating Kotlin data classes and gRPC service/stub code from Protocol Buffer (.proto) definitions. It provides a more Kotlin-idiomatic way to work with Protocol Buffers and gRPC in Kotlin projects.

## Features

- **Kotlin Data Classes**: Generates Kotlin data classes from Protocol Buffer messages with appropriate types and default values
- **gRPC Service/Stub Code**: Generates Kotlin-friendly gRPC service and client code
- **Coroutines Support**: Uses Kotlin Coroutines for asynchronous operations and streaming
- **Validation Support**: Compatible with Protocol Buffer validation rules
- **Comprehensive Type Mapping**: Properly maps Protocol Buffer types to Kotlin types
- **Support for Protocol Buffer Features**:
    - Oneofs (mapped to sealed interfaces)
    - Maps
    - Repeated fields
    - Optional fields
    - Nested types
    - Enums

## Project Structure

grpc-kt consists of three main modules:

- **common**: Provides runtime support for the generated code
- **example**: Contains example proto files and usage
- **generator**: The main code generation module (the protoc plugin)

## Installation

### Gradle

Add the following to your `build.gradle.kts` file:

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    id("com.google.protobuf") version "0.9.5"
}

dependencies {
    // grpc-kt runtime
    implementation("io.github.imonja:grpc-kt-common:1.0.0")

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
            artifact = "io.github.imonja:protoc-gen-grpc-kt:1.0.0:jdk8@jar"
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
