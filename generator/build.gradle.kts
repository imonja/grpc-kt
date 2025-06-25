import com.google.protobuf.gradle.id

plugins {
    application
}

java {
    withJavadocJar()
}

application {
    mainClass.set("io.github.imonja.grpc.kt.Executor")
}

dependencies {
    val jacksonVersion = "2.18.3"
    val okHttpVersion = "4.12.0"
    val javaxApiVersion = "1.3.2"
    val dependencyVersion = "3.24.2"

    // Project-specific dependencies
    implementation("com.squareup:kotlinpoet:${rootProject.ext["kotlinPoetVersion"]}")
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation(project(":common"))

    // Project-specific test dependencies
    testImplementation("javax.annotation:javax.annotation-api:$javaxApiVersion")
    testImplementation("org.assertj:assertj-core:$dependencyVersion")
    testImplementation("io.grpc:grpc-protobuf:${rootProject.ext["grpcJavaVersion"]}")
    testImplementation("io.grpc:grpc-inprocess:${rootProject.ext["grpcJavaVersion"]}")
    testImplementation("io.grpc:grpc-testing:${rootProject.ext["grpcJavaVersion"]}")
}

tasks.jar {
    println(application.mainClass.get())
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)

    val runtimeClasspathJars =
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }
    from(runtimeClasspathJars.map { zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${rootProject.ext["protobufVersion"]}"
    }
    plugins {
        id("grpc-java") {
            artifact = "io.grpc:protoc-gen-grpc-java:${rootProject.ext["grpcJavaVersion"]}"
        }
        id("grpc-kotlin") {
            artifact =
                "io.grpc:protoc-gen-grpc-kotlin:${rootProject.ext["grpcKotlinVersion"]}:jdk8@jar"
        }
        id("grpc-kt") {
            path = tasks.jar.get().archiveFile.get().asFile.absolutePath
        }
    }
    generateProtoTasks {
        all().forEach {
            if (it.name.startsWith("generateTestProto")) {
                it.dependsOn("jar")
            }

            it.plugins {
                id("grpc-java")
                id("grpc-kotlin")
                id("grpc-kt")
            }
        }
    }
}

publishing {
    publications {
        named<MavenPublication>("maven") {
            artifactId = "protoc-gen-grpc-kt"

            artifact(tasks.jar) {
                classifier = "jdk8"
            }

            // Если присутствуют withSourcesJar()/withJavadocJar(), можно включить
            // from(components["java"])

            pom {
                name.set("protoc generator grpc kt")
                description.set(
                    "protoc-gen-grpc-kt is a protoc plugin designed to generate " +
                        "Kotlin data classes and gRPC services/stubs from .proto input files."
                )
                url.set("https://github.com/imonja/grpc-kt")

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
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/imonja/grpc-kt.git")
                    developerConnection.set("scm:git:ssh://github.com:imonja/grpc-kt.git")
                    url.set("https://github.com/imonja/grpc-kt")
                }
            }
        }
    }
}
