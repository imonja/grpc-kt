import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("com.google.protobuf") version "0.9.5" apply false
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
}

group = "io.github.imonja"
description = "gRPC Kotlin"

// Set version from releaseVersion property (for tag-based releases)
// or from gradle.properties (for SNAPSHOT builds)
if (project.hasProperty("releaseVersion")) {
    version = project.property("releaseVersion") as String
}

mapOf(
    "grpcJavaVersion" to "1.71.0",
    "grpcKotlinVersion" to "1.4.1",
    "protobufVersion" to "4.30.2",
    "coroutinesVersion" to "1.10.2",
    "kotlinPoetVersion" to "2.1.0",
    "jupiterVersion" to "5.11.4"
).forEach({ (key, value) -> ext[key] = value })

fun RepositoryHandler.githubPackages() {
    maven {
        url = uri("https://maven.pkg.github.com/imonja/grpc-kt")
        name = "GitHub"
        credentials {
            username = findProperty("github.username") as String? ?: System.getenv("GITHUB_USERNAME")
            password = findProperty("github.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

repositories {
    mavenCentral()
}

subprojects {
    apply {
        plugin("java")
        plugin("org.jetbrains.kotlin.jvm")
        plugin("com.google.protobuf")
        plugin("maven-publish")
        plugin("org.jlleitschuh.gradle.ktlint")
    }

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    // Common dependencies for all subprojects
    dependencies {
        "implementation"(kotlin("reflect"))

        "implementation"("com.google.protobuf:protobuf-java:${rootProject.ext["protobufVersion"]}")
        "implementation"("com.google.protobuf:protobuf-java-util:${rootProject.ext["protobufVersion"]}")
        "implementation"("io.grpc:grpc-stub:${rootProject.ext["grpcJavaVersion"]}")
        "implementation"("io.grpc:grpc-kotlin-stub:${rootProject.ext["grpcKotlinVersion"]}")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.ext["coroutinesVersion"]}")

        // Common test dependencies
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:${rootProject.ext["jupiterVersion"]}")
        "testImplementation"("org.junit.jupiter:junit-jupiter-engine:${rootProject.ext["jupiterVersion"]}")
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                pom {
                    description.set(project.description)
                }
                from(components["java"])
            }
        }
        repositories {
            githubPackages()
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }

    configure<KtlintExtension> {
        ktlint {
            filter {
                exclude("**/generated/**")
                include("**/build/generated/**")
            }
        }
    }
}
