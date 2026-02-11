plugins {
    application
    id("io.github.imonja.grpc-kt-gradle-plugin")
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

val jupiterVersion = "5.11.4"

dependencies {
    // Kotlin dependency
    implementation(kotlin("stdlib-jdk8"))

    // Additional gRPC dependencies not included in plugin
    implementation("io.grpc:grpc-netty:1.71.0")

    // Project dependencies
    api(project(":common"))

    // JUnit 5 dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

grpcKtProtobuf {
    sourceDir {
        protoSourceDir = "$projectDir/proto"
    }

    generateSource {
        grpcJavaOutputSubDir = "java"
        grpcKtOutputSubDir = "kotlin"
        javaPgvOutputSubDir = "java-pgv"
        javaPgvLang = "java"
    }

    docs {
        grpcDocsFormat = "markdown"
        grpcDocsFileName = "grpc-docs.md"
        grpcDocsOutputSubDir = "grpc-docs"
    }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    ktlint {
        verbose.set(true)
        outputToConsole.set(true)
        enableExperimentalRules.set(true)
        ignoreFailures.set(true)
        filter {
            include("**/build/generated/sources/proto/**")
        }
    }
}

tasks.named("generateProto") {
    dependsOn(gradle.includedBuild("gradle-plugin").task(":build"))
    finalizedBy(":example:ktlintFormat")
}

tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}

tasks.withType<PublishToMavenLocal>().configureEach {
    enabled = false
}
