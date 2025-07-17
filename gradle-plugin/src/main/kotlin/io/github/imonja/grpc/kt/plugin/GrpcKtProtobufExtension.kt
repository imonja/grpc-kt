package io.github.imonja.grpc.kt.plugin

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class GrpcKtProtobufExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project
) {
    val sourceDir: SourceDirConfiguration = objects.newInstance(SourceDirConfiguration::class.java, project)
    val generateSource: GenerateSourceConfiguration = objects.newInstance(GenerateSourceConfiguration::class.java)
    val docs: DocsConfiguration = objects.newInstance(DocsConfiguration::class.java)

    fun sourceDir(action: Action<SourceDirConfiguration>) {
        action.execute(sourceDir)
    }

    fun generateSource(action: Action<GenerateSourceConfiguration>) {
        action.execute(generateSource)
    }

    fun docs(action: Action<DocsConfiguration>) {
        action.execute(docs)
    }
}

open class SourceDirConfiguration @Inject constructor(
    objects: ObjectFactory,
    project: Project
) {
    val protoSourceDir: Property<String> = objects.property(String::class.java)
        .convention("${project.projectDir}/proto")
}

open class GenerateSourceConfiguration @Inject constructor(
    objects: ObjectFactory
) {
    val grpcJavaOutputSubDir: Property<String> = objects.property(String::class.java)
        .convention("java")

    val grpcKtOutputSubDir: Property<String> = objects.property(String::class.java)
        .convention("kotlin")

    val javaPgvOutputSubDir: Property<String> = objects.property(String::class.java)
        .convention("java-pgv")

    val javaPgvLang: Property<String> = objects.property(String::class.java)
        .convention("java")
}

open class DocsConfiguration @Inject constructor(
    objects: ObjectFactory
) {
    val grpcDocsFormat: Property<String> = objects.property(String::class.java)
        .convention("markdown")

    val grpcDocsFileName: Property<String> = objects.property(String::class.java)
        .convention("grpc-docs.md")

    val grpcDocsOutputSubDir: Property<String> = objects.property(String::class.java)
        .convention("grpc-docs")
}
