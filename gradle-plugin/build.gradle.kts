import java.util.Properties

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
    id("com.vanniktech.maven.publish") version "0.29.0"
    id("com.gradle.plugin-publish") version "1.3.0"
}

group = "io.github.imonja"
description = "gRPC Kotlin"

// Set a version from releaseVersion property (for tag-based releases)
// or read from root gradle.properties (for SNAPSHOT builds)
if (project.hasProperty("releaseVersion")) {
    version = project.property("releaseVersion") as String
} else {
    // Read version from root gradle.properties
    val rootGradleProperties = file("../gradle.properties")
    if (rootGradleProperties.exists()) {
        val properties = Properties()
        rootGradleProperties.inputStream().use { properties.load(it) }
        version = properties.getProperty("version", "0.0.1-SNAPSHOT")
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.5")
    implementation(gradleApi())
    implementation(localGroovy())
}

// Create a properties file with version during build
val generateVersionProperties = tasks.register("generateVersionProperties") {
    val outputDir = layout.buildDirectory.dir("generated/sources/version")
    val versionFile = outputDir.map { it.file("version.properties") }

    outputs.file(versionFile)

    doLast {
        val outputFile = versionFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText("version=${project.version}")
    }
}

// Make sure the version file is generated before resources processing
tasks.named("processResources") {
    dependsOn(generateVersionProperties)
}

// Make sure the version file is generated before sources jar (if exists)
tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn(generateVersionProperties)
}

// Add generated resources to the source set
sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/sources/version"))
        }
    }
}

gradlePlugin {
    website = "https://github.com/imonja/grpc-kt"
    vcsUrl = "https://github.com/imonja/grpc-kt"

    plugins {
        create("grpc-kt-gradle-plugin") {
            id = "io.github.imonja.grpc-kt-gradle-plugin"
            implementationClass = "io.github.imonja.grpc.kt.plugin.GrpcKtProtobufPlugin"
            displayName = "gRPC Kotlin Gradle Plugin"
            description = "A Gradle plugin that configures protobuf generation with grpc-kt, " +
                "grpc-java, validation, and documentation"
            tags = listOf("grpc", "kotlin", "protobuf", "grpc-kt")
        }
    }
}

// Configure signing for Gradle Plugin Portal
signing {
    if (!version.toString().contains("SNAPSHOT")) {
        val signingKey = findProperty("signingInMemoryKey") as String?
        val signingPassword = findProperty("signingInMemoryKeyPassword") as String?
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

// Disable signing tasks for SNAPSHOT versions
if (version.toString().contains("SNAPSHOT")) {
    tasks.withType<Sign>().configureEach {
        enabled = false
    }
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.WARN
}

// GitHub packages publishing (keep for backward compatibility)
publishing {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/imonja/grpc-kt")
            name = "GitHub"
            credentials {
                username = findProperty("github.name") as String? ?: System.getenv("GITHUB_USERNAME")
                password = findProperty("github.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// Maven Central publishing configuration
configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "grpc-kt-gradle-plugin",
        version = project.version.toString()
    )

    pom {
        name.set("gRPC Kotlin Gradle Plugin")
        description.set(
            "A Gradle plugin that configures protobuf generation with grpc-kt, grpc-java, validation, and documentation"
        )
        url.set("https://github.com/imonja/grpc-kt")
        inceptionYear.set("2025")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("imonja")
                name.set("imonja")
                url.set("https://github.com/imonja")
                organization.set("imonja")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/imonja/grpc-kt.git")
            developerConnection.set("scm:git:ssh://github.com:imonja/grpc-kt.git")
            url.set("https://github.com/imonja/grpc-kt")
        }
    }

    // Only publish to Maven Central for non-SNAPSHOT versions
    if (!version.toString().contains("SNAPSHOT")) {
        publishToMavenCentral(
            host = com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL,
            automaticRelease = true
        )
        signAllPublications()
    }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    filter {
        exclude("**/generated/**")
        include("**/build/generated/**")
    }
}
