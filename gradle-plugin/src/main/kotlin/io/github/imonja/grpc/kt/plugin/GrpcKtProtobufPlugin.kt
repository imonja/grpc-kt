package io.github.imonja.grpc.kt.plugin

import com.google.protobuf.gradle.ProtobufPlugin
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies

class GrpcKtProtobufPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply required plugins
        project.plugins.apply(JavaPlugin::class)
        project.plugins.apply(ProtobufPlugin::class)

        // Create the extension
        val extension = project.extensions.create<GrpcKtProtobufExtension>("grpcKtProtobuf", project)

        // Configure versions
        val pgvVersion = "1.1.0"
        val pgdVersion = "1.5.1"
        val kotlinGrpcVersion = "1.4.1"
        val javaGrpcVersion = "1.71.0"
        val protobufVersion = "4.30.2"
        val coroutinesVersion = "1.10.2"

        // Add dependencies
        project.dependencies {
            add("implementation", "io.github.imonja:grpc-kt-common:${getPluginVersion()}")
            add("implementation", "io.grpc:grpc-stub:$javaGrpcVersion")
            add("implementation", "io.grpc:grpc-protobuf:$javaGrpcVersion")
            add("implementation", "io.grpc:grpc-kotlin-stub:$kotlinGrpcVersion")
            add("implementation", "com.google.protobuf:protobuf-kotlin:$protobufVersion")
            add("implementation", "com.google.protobuf:protobuf-java:$protobufVersion")
            add("implementation", "com.google.protobuf:protobuf-java-util:$protobufVersion")
            add("implementation", "build.buf.protoc-gen-validate:pgv-java-grpc:$pgvVersion")
            add("implementation", "build.buf.protoc-gen-validate:pgv-java-stub:$pgvVersion")
            add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        }

        // Configure protobuf
        project.configure<com.google.protobuf.gradle.ProtobufExtension> {
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
                    // Check if we're in a multi-project build with a generator module
                    val generatorProject = project.rootProject.findProject(":generator")
                    if (generatorProject != null) {
                        // Use a local generator jar for development
                        path = "${project.rootDir}/generator/build/libs/generator-${project.rootProject.version}.jar"
                    } else {
                        // Use the published artifact with the same version as this plugin
                        artifact = "io.github.imonja:protoc-gen-grpc-kt:${getPluginVersion()}:jdk8@jar"
                    }
                }
            }
            generateProtoTasks {
                all().forEach {
                    it.plugins {
                        id("grpc-java") {
                            outputSubDir = extension.generateSource.grpcJavaOutputSubDir.get()
                        }
                        id("grpc-kt") {
                            outputSubDir = extension.generateSource.grpcKtOutputSubDir.get()
                        }
                        id("java-pgv") {
                            option("lang=${extension.generateSource.javaPgvLang.get()}")
                            outputSubDir = extension.generateSource.javaPgvOutputSubDir.get()
                        }
                        id("grpc-docs") {
                            option(
                                "${extension.docs.grpcDocsFormat.get()}, ${extension.docs.grpcDocsFileName.get()}"
                            )
                            outputSubDir = extension.docs.grpcDocsOutputSubDir.get()
                        }
                    }
                }
            }
        }

        // Configure sourceSets for proto files
        project.afterEvaluate {
            // Configure a proto source directory
            val protoSourceDir = extension.sourceDir.protoSourceDir.get()
            project.configure<org.gradle.api.tasks.SourceSetContainer> {
                named("main") {
                    java {
                        srcDir(
                            "${project.layout.buildDirectory.get()}/generated/source/proto/main/${
                            extension.generateSource.grpcJavaOutputSubDir.get()
                            }"
                        )
                        srcDir(
                            "${project.layout.buildDirectory.get()}/generated/source/proto/main/${
                            extension.generateSource.grpcKtOutputSubDir.get()
                            }"
                        )
                        srcDir(
                            "${project.layout.buildDirectory.get()}/generated/source/proto/main/${
                            extension.generateSource.javaPgvOutputSubDir.get()
                            }"
                        )
                    }
                    proto {
                        srcDir(protoSourceDir)
                    }
                }
            }

            // Add dependency on generator build if we're using local generator
            val generatorProject = project.rootProject.findProject(":generator")
            if (generatorProject != null) {
                project.tasks.named("generateProto") {
                    dependsOn("${generatorProject.path}:build")
                }
            }
        }
    }

    private fun getPluginVersion(): String {
        // Get version from plugin's resources or manifest
        val pluginVersion = this::class.java.`package`.implementationVersion
        return if (pluginVersion != null && pluginVersion != "unspecified") {
            pluginVersion
        } else {
            // Fallback to reading from gradle.properties or default
            "1.1.0"
        }
    }
}
