[![License](https://img.shields.io/github/license/imonja/grpc-kt)](LICENSE)
[![Release](https://img.shields.io/github/v/release/imonja/grpc-kt)](https://github.com/imonja/grpc-kt/releases)

# grpc-kt

grpc-kt is a protoc plugin for generating Kotlin data classes and gRPC service/stub code from Protocol Buffer (.proto) definitions. It provides a more Kotlin-idiomatic way to work with Protocol Buffers and gRPC in Kotlin projects.

## Features

- **Kotlin Data Classes**: Generates Kotlin data classes from Protocol Buffer messages with idiomatic types and default values.
- **Automatic Type Mapping**:
    - `google.protobuf.Timestamp` ↔ `java.time.LocalDateTime`
    - `google.protobuf.Duration` ↔ `java.time.Duration`
    - `google.protobuf.*Value` (Wrappers) ↔ Nullable Kotlin types (e.g., `String?`, `Int?`)
- **gRPC Service/Stub Code**: Generates Kotlin-friendly gRPC service and client code.
- **Coroutines & Flow**: Full support for unary, client-streaming, server-streaming, and bidirectional-streaming calls using Kotlin Coroutines and `Flow`.
- **Flexible Service Implementation**: Supports both traditional (class-based) and functional interface (partial) approaches for implementing gRPC services.
- **Metadata Support**: Easily access gRPC metadata within coroutines.
- **Validation Support**: Built-in support for Protocol Buffer validation rules via `protoc-gen-validate` (PGV).
- **Documentation Generation**: Automatically generates API documentation in Markdown or other formats.
- **Gradle Plugin**: A powerful plugin that simplifies project setup by automatically configuring dependencies and code generation.

## Project Structure

grpc-kt consists of several modules:

- **common**: Runtime support, metadata utilities, and type conversion extensions.
- **generator**: The main code generation module (the protoc plugin).
- **gradle-plugin**: Gradle plugin for easy integration.
- **example**: Usage examples and integration tests.

## Installation

### Gradle Plugin (Recommended)

The easiest way to use grpc-kt is with our Gradle plugin. It automatically configures `protoc`, all necessary gRPC/Protobuf dependencies, and generation tasks.

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    id("io.github.imonja.grpc-kt-gradle-plugin") version "x.x.x"
}
```

The plugin automatically adds the following dependencies to your project:
- `grpc-kt-common`
- `grpc-stub`, `grpc-protobuf`, `grpc-kotlin-stub`
- `protobuf-kotlin`, `protobuf-java`, `protobuf-java-util`
- `kotlinx-coroutines-core`
- `pgv-java-grpc`, `pgv-java-stub` (for validation)

### Manual Configuration

If you need more control, you can configure the plugin manually. See the [Manual Configuration Guide](docs/manual-setup.md) (or refer to the full `build.gradle.kts` in this repo).

## Usage

### 1. Define Protocol Buffer Messages

Create your `.proto` files in `src/main/proto` or `proto/`:

```protobuf
syntax = "proto3";

package example;

option java_package = "com.example.proto";
option java_multiple_files = true;

import "google/protobuf/timestamp.proto";
import "google/protobuf/wrappers.proto";

message Person {
    string name = 1;
    int32 age = 2;
    repeated string hobbies = 3;
    
    enum Gender {
        UNKNOWN = 0;
        MALE = 1;
        FEMALE = 2;
    }
    Gender gender = 4;
    
    // Mapped to java.time.LocalDateTime
    google.protobuf.Timestamp created_at = 5;
    
    // Mapped to String? (nullable)
    google.protobuf.StringValue nickname = 6;
}
```

### 2. Define gRPC Services

```protobuf
service PersonService {
    rpc GetPerson(GetPersonRequest) returns (GetPersonResponse) {}
    rpc ListPersons(ListPersonsRequest) returns (stream ListPersonsResponse) {}
}
```

### 3. Use Generated Kotlin Code

grpc-kt generates Kotlin data classes with the `Kt` suffix to avoid conflicts with Java-generated classes.

```kotlin
import java.time.LocalDateTime

val person = PersonKt(
    name = "John Doe",
    age = 30,
    gender = PersonKt.GenderKt.MALE,
    createdAt = LocalDateTime.now(),
    nickname = "JD", // StringValue mapped to String?
    hobbies = listOf("Coding", "Music")
)

// Conversion to/from Java Protobuf
val javaProto = person.toJavaProto()
val kotlinProto = javaProto.toKotlinProto()

// Field check (matches hasFieldName() in Java)
if (person.hasNickname()) {
    println(person.nickname)
}
```

### 4. Implementing a gRPC Service

#### Traditional Approach

```kotlin
class PersonServiceImpl : PersonServiceGrpcKt.PersonServiceCoroutineImplBase() {
    override suspend fun getPerson(request: GetPersonRequestKt): GetPersonResponseKt {
        return GetPersonResponseKt(person = PersonKt(name = "John"))
    }
}
```

#### Partial Implementation (Functional)

You can implement only the methods you need using functional interfaces. Unimplemented methods will return `UNIMPLEMENTED` status.

```kotlin
val getPerson = PersonServiceGrpcPartialKt.GetPersonGrpcMethod { request ->
    GetPersonResponseKt(person = PersonKt(name = "John"))
}

val service = PersonServiceGrpcPartialKt.PersonServiceCoroutineImplPartial(
    getPerson = getPerson
)
```

### 5. Accessing Metadata

Use `coroutineContext.grpcMetadata` to access gRPC headers:

```kotlin
import io.github.imonja.grpc.kt.common.grpcMetadata

class MyService : MyServiceGrpcKt.MyServiceCoroutineImplBase() {
    override suspend fun myMethod(request: MyRequestKt): MyResponseKt {
        val metadata = coroutineContext.grpcMetadata
        val userAgent = metadata?.get(Metadata.Key.of("user-agent", Metadata.ASCII_STRING_MARSHALLER))
        return MyResponseKt()
    }
}

// Don't forget to add the interceptor to your server
val server = ServerBuilder.forPort(8080)
    .addService(ServerInterceptors.intercept(myService, metadataServerInterceptor()))
    .build()
```

## Gradle Plugin Configuration

Customize the plugin via the `grpcKtProtobuf` extension:

```kotlin
grpcKtProtobuf {
    sourceDir {
        protoSourceDir.set("custom/proto") // Default: projectDir/proto
    }
    
    generateSource {
        grpcKtOutputSubDir.set("kotlin")   // Default: kotlin
        // ... other sub-dirs
    }
    
    docs {
        grpcDocsFormat.set("markdown")     // Default: markdown
        grpcDocsFileName.set("api.md")     // Default: grpc-docs.md
    }
}
```

## Acknowledgements

This project is based on [krotoDC](https://github.com/mscheong01/krotoDC), originally developed by [@mscheong01](https://github.com/mscheong01).
Significant enhancements for Coroutines, Kotlin idioms, and modern Protobuf features were introduced by [@imonja](https://github.com/imonja).

## Contributing

Contributions are welcome! Please see the [CONTRIBUTING.md](CONTRIBUTING.md) guide before submitting a Pull Request.

## License

Licensed under the [Apache License 2.0](LICENSE).
