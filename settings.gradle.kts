rootProject.name = "grpc-kt"
include("common")
include("generator")
include("example")

// Include gradle-plugin as composite build for example module to use
includeBuild("gradle-plugin")
