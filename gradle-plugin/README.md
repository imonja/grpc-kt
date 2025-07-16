# gRPC Kotlin Protobuf Gradle Plugin

This is a Gradle plugin that encapsulates the protobuf configuration for gRPC Kotlin projects. It automatically configures protobuf generation with multiple generators including grpc-kt, grpc-java, validation, and documentation.

## Usage

To use this plugin in your project, add the following to your `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.imonja.grpc-kt-protobuf")
}
```

## What it does

The plugin automatically:

1. **Applies required plugins**: `java` and `com.google.protobuf`
2. **Adds dependencies**: All necessary gRPC, protobuf, and Kotlin coroutines dependencies
3. **Configures protobuf generation** with multiple generators:
   - `grpc-java`: Java gRPC code generation
   - `grpc-kt`: Kotlin gRPC code generation (using the grpc-kt plugin)
   - `java-pgv`: Protocol Buffer validation
   - `grpc-docs`: Documentation generation
4. **Configures source sets**: Adds generated source directories to the main source set
5. **Versions**: Uses predefined versions for all dependencies

## Proto files

Place your `.proto` files in the `src/main/proto` directory (or `proto/` directory in project root).

## Generated code

The plugin generates code in the following directories:
- Java gRPC code: `build/generated/source/proto/main/java`
- Kotlin gRPC code: `build/generated/source/proto/main/kotlin`
- Validation code: `build/generated/source/proto/main/java-pgv`
- Documentation: `build/generated/source/proto/main/grpc-docs`

## Dependencies added

The plugin automatically adds the following dependencies:
- `io.grpc:grpc-stub`
- `io.grpc:grpc-protobuf`
- `io.grpc:grpc-kotlin-stub`
- `com.google.protobuf:protobuf-kotlin`
- `com.google.protobuf:protobuf-java`
- `com.google.protobuf:protobuf-java-util`
- `build.buf.protoc-gen-validate:pgv-java-grpc`
- `build.buf.protoc-gen-validate:pgv-java-stub`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core`

## Version compatibility

The plugin is designed to work with the grpc-kt project and uses version `1.1.0` of the grpc-kt protoc plugin.

## Publishing

When published to Maven Central, the plugin will be available as:
- **Group ID**: `io.github.imonja`
- **Artifact ID**: `gradle-plugin`
- **Plugin ID**: `io.github.imonja.grpc-kt-protobuf`