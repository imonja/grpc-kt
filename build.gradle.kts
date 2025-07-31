import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("com.google.protobuf") version "0.9.5" apply false
    `maven-publish`
    signing
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
    id("com.vanniktech.maven.publish") version "0.29.0" apply false
}

group = "io.github.imonja"
description = "gRPC Kotlin"

// Set a version from releaseVersion property (for tag-based releases)
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

repositories {
    mavenCentral()
}

subprojects {
    apply {
        plugin("java")
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.jlleitschuh.gradle.ktlint")
        plugin("com.google.protobuf")
        plugin("maven-publish")
        plugin("signing")
        plugin("com.vanniktech.maven.publish")
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

    tasks.withType<Test> {
        useJUnitPlatform()
    }

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

    // Maven Central publishing configuration - only for publishable modules
    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        coordinates(
            groupId = project.group.toString(),
            artifactId = project.name,
            version = project.version.toString()
        )

        pom {
            name.set("grpc-kt")
            description.set(project.description ?: "gRPC Kotlin code generator")
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

    configure<KtlintExtension> {
        ktlint {
            filter {
                exclude("**/generated/**")
                include("**/build/generated/**")
            }
        }
    }
}

tasks.register("ktlintFormatAll") {
    description = "Run ktlintFormat for all modules (grpc-kt, gradle-plugin)"
    group = "formatting"
    dependsOn("ktlintFormat")

    doLast {
        // Run ktlintFormat for gradle-plugin composite build
        gradle.includedBuild("gradle-plugin").task(":ktlintFormat")
    }
}

tasks.register<Copy>("setupHooks") {
    description = "Setup git hooks for development"
    group = "git hooks"
    outputs.upToDateWhen { false }
    from("$rootDir/scripts/pre-commit")
    into("$rootDir/.git/hooks/")

    doLast {
        // Make the hook executable
        file("$rootDir/.git/hooks/pre-commit").setExecutable(true)
        println("✅ Pre-commit hook installed successfully")
        println("   Hook will run ktlintCheck for all modules (generator, common, gradle-plugin)")
        println("   Use 'git commit --no-verify' to skip the hook if needed")
        println("")
        println("💡 Available commands:")
        println("   ./gradlew ktlintFormat     - Auto-fix main modules")
        println("   ./gradlew ktlintFormatAll  - Auto-fix all modules")
    }
}

// Auto-install git hooks on the first run
gradle.taskGraph.whenReady {
    val preCommitHook = file("$rootDir/.git/hooks/pre-commit")
    val sourceHook = file("$rootDir/scripts/pre-commit")

    // Check if we need to install/update the hook
    val needsInstall = !preCommitHook.exists() ||
        !sourceHook.exists() ||
        (
            preCommitHook.exists() && sourceHook.exists() &&
                preCommitHook.readText() != sourceHook.readText()
            )

    if (needsInstall && file("$rootDir/.git").exists() && sourceHook.exists()) {
        println("🔧 Auto-installing git hooks...")
        copy {
            from("$rootDir/scripts/pre-commit")
            into("$rootDir/.git/hooks/")
        }
        file("$rootDir/.git/hooks/pre-commit").setExecutable(true)
        println("✅ Git hooks auto-installed")
    }
}
