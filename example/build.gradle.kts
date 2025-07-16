import com.google.protobuf.gradle.id

plugins {
    application
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
}

group = "co.lety"
version = "0.0.1"
description = "merchant-service-grpc-kt"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val pgvVersion = "1.1.0"
val pgdVersion = "1.5.1"
val kotlinGrpcVersion = "1.4.1"
val javaGrpcVersion = "1.71.0"
val protobufVersion = "4.30.2"
val coroutinesVersion = "1.10.2"
val jupiterVersion = "5.11.4"

dependencies {
    // Kotlin dependency
    implementation(kotlin("stdlib-jdk8"))

    // Project-specific gRPC dependencies
    implementation("io.grpc:grpc-stub:$javaGrpcVersion")
    implementation("io.grpc:grpc-protobuf:$javaGrpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$kotlinGrpcVersion")
    implementation("io.grpc:grpc-netty:$javaGrpcVersion")

    // protobuf-kotlin dependencies
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")

    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-java-util:$protobufVersion")

    // pgv dependencies
    implementation("build.buf.protoc-gen-validate:pgv-java-grpc:$pgvVersion")
    implementation("build.buf.protoc-gen-validate:pgv-java-stub:$pgvVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    api(project(":common"))

    // JUnit 5 dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

sourceSets {
    main {
        proto {
            srcDir("$projectDir/proto")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc-java") {
            artifact = "io.grpc:protoc-gen-grpc-java:$javaGrpcVersion"
        }
        id("java-pgv") {
            artifact = "build.buf.protoc-gen-validate:protoc-gen-validate:$pgvVersion"
        }
        id("grpc-docs") {
            artifact = "io.github.pseudomuto:protoc-gen-doc:$pgdVersion"
        }
        id("grpc-kt") {
            path = "$rootDir/generator/build/libs/generator-${project(":generator").version}.jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc-java") {
                    outputSubDir = "java"
                }
                id("grpc-kt") {
                    outputSubDir = "kotlin"
                }
                id("java-pgv") {
                    option("lang=java")
                    outputSubDir = "java-pgv"
                }
                id("grpc-docs") {
                    option("markdown,grpc-docs.md")
                    outputSubDir = "grpc-docs"
                }
            }
        }
    }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    ktlint {
        ignoreFailures.set(true)
    }
}

tasks.named("generateProto") {
    dependsOn(":generator:build")
    finalizedBy(":example:ktlintFormat")
}

tasks.withType<Jar> {
    val filesToInclude = mapOf(
        "$projectDir/proto" to "proto" to "**/*.proto",
        "${layout.buildDirectory.get()}/generated/source/proto/main/grpc-docs" to "docs" to "**/*.md"
    )

    filesToInclude.forEach { (sourcePath, targetDir), pattern ->
        from(sourcePath) {
            include(pattern)
            into(targetDir)
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}

tasks.withType<PublishToMavenLocal>().configureEach {
    enabled = false
}
