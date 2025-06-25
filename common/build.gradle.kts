java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        named<MavenPublication>("maven") {
            artifactId = "grpc-kt-common"

            pom {
                name.set("grpc-kt common")
                description.set("Provides runtime support for grpc-kt generated code")
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
