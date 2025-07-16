configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "grpc-kt-common",
        version = project.version.toString()
    )
    pom {
        name.set("grpc-kt-common")
        description.set("Provides runtime support for grpc-kt generated code")
    }
}
