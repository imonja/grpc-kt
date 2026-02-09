package com.example

import com.example.proto.GetPersonRequestKt
import com.example.proto.GetPersonResponseKt
import com.example.proto.ListPersonsRequestKt
import com.example.proto.ListPersonsResponseKt
import com.example.proto.PersonKt
import com.example.proto.PersonServiceGrpcKt
import io.github.imonja.grpc.kt.common.grpcMetadata
import io.github.imonja.grpc.kt.common.metadataServerInterceptor
import io.grpc.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

class MetadataGenerationTest {
    private val serverPort = (60_000..65_000).random()
    private var server: Server? = null
    private var channel: ManagedChannel? = null
    private val receivedMetadata = AtomicReference<Metadata>()

    private val TEST_KEY = Metadata.Key.of("test-header", Metadata.ASCII_STRING_MARSHALLER)

    @BeforeEach
    fun setup() {
        val interceptor = object : ServerInterceptor {
            override fun <ReqT : Any?, RespT : Any?> interceptCall(
                call: ServerCall<ReqT, RespT>,
                headers: Metadata,
                next: ServerCallHandler<ReqT, RespT>
            ): ServerCall.Listener<ReqT> {
                receivedMetadata.set(headers)
                return next.startCall(call, headers)
            }
        }

        server = ServerBuilder.forPort(serverPort)
            .addService(
                ServerInterceptors.intercept(
                    PersonServiceImpl(),
                    interceptor,
                    metadataServerInterceptor()
                )
            )
            .build()
            .start()

        channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
            .usePlaintext()
            .build()
    }

    @AfterEach
    fun teardown() {
        channel?.shutdown()?.awaitTermination(5, TimeUnit.SECONDS)
        server?.shutdown()?.awaitTermination(5, TimeUnit.SECONDS)
    }

    @Test
    fun `test unary call with explicit metadata`() = runBlocking {
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)
        val request = GetPersonRequestKt(id = "123")
        val metadata = Metadata()
        metadata.put(TEST_KEY, "test-value")

        stub.getPerson(request, metadata)

        val headers = receivedMetadata.get()
        assertEquals("test-value", headers.get(TEST_KEY))
    }

    @Test
    fun `test server streaming with explicit metadata`() = runBlocking {
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)
        val request = ListPersonsRequestKt(limit = 1)
        val metadata = Metadata()
        metadata.put(TEST_KEY, "streaming-value")

        stub.listPersons(request, metadata).collect { }

        val headers = receivedMetadata.get()
        assertEquals("streaming-value", headers.get(TEST_KEY))
    }

    @Test
    fun `test metadata available in server implementation`(): Unit = runBlocking {
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)
        val request = GetPersonRequestKt(id = "123")
        val metadata = Metadata()
        metadata.put(TEST_KEY, "server-visible-value")

        val response = stub.getPerson(request, metadata)

        assertEquals("server-visible-value", response.person?.name)
    }

    @Test
    fun `test metadata available in server streaming`(): Unit = runBlocking {
        val stub = PersonServiceGrpcKt.PersonServiceCoroutineStub(channel!!)
        val request = ListPersonsRequestKt(limit = 1)
        val metadata = Metadata()
        metadata.put(TEST_KEY, "streaming-server-value")

        val response = stub.listPersons(request, metadata).first()

        assertEquals("streaming-server-value", response.person?.name)
    }

    private class PersonServiceImpl : PersonServiceGrpcKt.PersonServiceCoroutineImplBase() {
        override suspend fun getPerson(request: GetPersonRequestKt): GetPersonResponseKt {
            val metadata = coroutineContext.grpcMetadata
            val value = metadata?.get(Metadata.Key.of("test-header", Metadata.ASCII_STRING_MARSHALLER)) ?: "not-found"
            return GetPersonResponseKt(person = PersonKt(name = value))
        }

        override fun listPersons(request: ListPersonsRequestKt) = kotlinx.coroutines.flow.flow {
            val metadata = currentCoroutineContext().grpcMetadata
            val value = metadata?.get(Metadata.Key.of("test-header", Metadata.ASCII_STRING_MARSHALLER)) ?: "not-found"
            emit(ListPersonsResponseKt(person = PersonKt(name = value)))
        }
    }
}
