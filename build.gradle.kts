import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("com.google.protobuf") version "0.9.5" apply false
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
}

group = "io.github.imonja"
description = "gRPC Kotlin"

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

fun RepositoryHandler.gitlabRepository(urlPackages: String) {
    maven {
        url = uri(urlPackages)
        name = "GitLab"
        // ~/.gradle/gradle.properties
        credentials(HttpHeaderCredentials::class) {
            name = findProperty("gitlab.name") as String?
                ?: throw GradleException("GitLab username not found")
            value = findProperty("gitlab.token") as String?
                ?: throw GradleException("GitLab password not found")
        }
        authentication {
            create("header", HttpHeaderAuthentication::class)
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

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
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
            }
        }
        repositories {
            gitlabRepository("https://gitlab.com/api/v4/projects/54280949/packages/maven")
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        ktlint {
            filter {
                exclude("**/generated/**")
                include("**/build/generated/**")
            }
        }
    }
}
