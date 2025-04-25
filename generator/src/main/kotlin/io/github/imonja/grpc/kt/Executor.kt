package io.github.imonja.grpc.kt

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import java.io.IOException

object Executor {
    @JvmStatic
    fun main(args: Array<String>) {
        buildCode(parseRequest())
    }

    /**
     * Parses the [CodeGeneratorRequest] from the standard input.
     *
     * @return Parsed [CodeGeneratorRequest].
     * @throws IOException If an error occurs during parsing.
     */
    private fun parseRequest(): CodeGeneratorRequest {
        return try {
            CodeGeneratorRequest.parseFrom(System.`in`)
        } catch (e: Throwable) {
            throw IOException("Error occurred while parsing CodeGeneratorRequest for Generator", e)
        }
    }

    /**
     * Generates code using [CodeBuilder] based on the provided [request].
     *
     * @param request The parsed [CodeGeneratorRequest].
     * @throws IOException If an error occurs during code generation.
     */
    private fun buildCode(request: CodeGeneratorRequest) {
        try {
            CodeBuilder.buildCode(request)
        } catch (e: Throwable) {
            throw IOException("Error occurred while generating code through Generator", e)
        }
    }
}
