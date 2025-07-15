description = "Provides runtime support for grpc-kt generated code"

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "grpc-kt-common",
        version = project.version.toString()
    )
}
