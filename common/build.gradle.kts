java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        named<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("grpc-kt common")
                artifactId = "grpc-kt-common"
                description.set("provides runtime support for grpc-kt generated code")
            }
        }
    }
}
